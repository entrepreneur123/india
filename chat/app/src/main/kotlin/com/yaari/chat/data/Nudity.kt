package com.yaari.chat.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.FloatBuffer

// On-device nudity check for the partner's video in a call, the same as the web (web/lib/nudity.ts): the nsfwjs
// MobileNetV2 (224 px) runs on the phone (live flavor: TensorFlow Lite, live/assets); frames never leave it.
//
// A frame is "nude" when Porn + Hentai >= 0.6 ("Sexy" alone, e.g. swimwear or a bare chest, is not). The video is
// hidden after 2 nude frames in a row and shown again after 4 clean ones, so one odd frame doesn't flicker it.

/** What the call screen does with the partner's video: blurred while [CHECKING], shown when [CLEAR], covered when [HIDDEN]. */
enum class Shield { CHECKING, CLEAR, HIDDEN }

/** The nsfwjs MobileNetV2: input 224 x 224 RGB in [0, 1], output 5 probabilities (softmax). */
object Nsfw {
    const val SIZE = 224
    /** Output order: Drawing, Hentai, Neutral, Porn, Sexy. */
    const val CLASSES = 5
    const val HENTAI = 1
    const val PORN = 3
    const val NUDE_SCORE = 0.6f

    fun isNude(probabilities: FloatArray): Boolean = probabilities[HENTAI] + probabilities[PORN] >= NUDE_SCORE

    /**
     * Writes a square I420 image ([size] x [size], e.g. a frame already cropped and scaled to [SIZE]) into [out] as
     * the model's input: RGB floats in [0, 1] (nsfwjs divides by 255), row by row, turned upright by [rotation]
     * (0, 90, 180 or 270: the clockwise turn the frame needs on screen, as in WebRTC). BT.601 limited range, like
     * libyuv / the WebRTC renderers. [out] is rewound and filled from 0.
     */
    fun i420ToInput(
        size: Int, rotation: Int,
        y: ByteBuffer, strideY: Int, u: ByteBuffer, strideU: Int, v: ByteBuffer, strideV: Int,
        out: FloatBuffer,
    ) {
        out.rewind()
        val last = size - 1
        for (oy in 0 until size) {
            for (ox in 0 until size) {
                // Source pixel for upright (ox, oy).
                val sx: Int
                val sy: Int
                when (rotation) {
                    90 -> { sx = oy; sy = last - ox }
                    180 -> { sx = last - ox; sy = last - oy }
                    270 -> { sx = last - oy; sy = ox }
                    else -> { sx = ox; sy = oy }
                }
                val c = 1.164f * ((y.get(sy * strideY + sx).toInt() and 0xFF) - 16)
                val chroma = (sy shr 1)
                val d = (u.get(chroma * strideU + (sx shr 1)).toInt() and 0xFF) - 128
                val e = (v.get(chroma * strideV + (sx shr 1)).toInt() and 0xFF) - 128
                out.put(channel(c + 1.596f * e))
                out.put(channel(c - 0.392f * d - 0.813f * e))
                out.put(channel(c + 2.017f * d))
            }
        }
        out.rewind()
    }

    private fun channel(value: Float): Float = value.coerceIn(0f, 255f) / 255f

    /** Writes [SIZE] x [SIZE] ARGB pixels (Bitmap.getPixels) into [out] as the model's input. [out] is rewound and filled from 0. */
    fun argbToInput(pixels: IntArray, out: FloatBuffer) {
        out.rewind()
        for (p in pixels) {
            out.put(((p shr 16) and 0xFF) / 255f)
            out.put(((p shr 8) and 0xFF) / 255f)
            out.put((p and 0xFF) / 255f)
        }
        out.rewind()
    }

    /**
     * A photo is not sent when the whole picture (squeezed to a square) or its centre square scores as nude, as on the
     * web (lib/nudity.ts classifyImage). [centre] is looked at only when the whole picture is clean.
     */
    suspend fun isNudePhoto(whole: suspend () -> FloatArray, centre: suspend () -> FloatArray): Boolean =
        isNude(whole()) || isNude(centre())
}

/** Counts samples into hide / show verdicts, like web/lib/nudity.ts. */
class NudityVerdicts {
    private var nudeRun = 0
    private var cleanRun = 0

    /** Null until the first verdict. */
    var hidden: Boolean? = null
        private set

    /** One sample. Returns the new verdict (true = hide) when there is one, the first clean sample included. */
    fun add(nude: Boolean): Boolean? {
        nudeRun = if (nude) nudeRun + 1 else 0
        cleanRun = if (nude) 0 else cleanRun + 1
        val next = when {
            hidden != true && nudeRun >= HIDE_AFTER -> true
            hidden == null && cleanRun >= 1 -> false
            hidden == true && cleanRun >= SHOW_AFTER -> false
            else -> return null
        }
        hidden = next
        return next
    }

    companion object {
        const val HIDE_AFTER = 2
        const val SHOW_AFTER = 4
    }
}

/** One look at the partner's latest frame: true = nude, false = clean, null = no frame to look at right now. */
fun interface NudityCheck {
    suspend fun nude(): Boolean?
}

const val NUDITY_SAMPLE_MS = 1200L
/** A phone too slow (or unable) to run the model still gets the call: the start-up blur lifts after this. */
const val NUDITY_START_BLUR_MS = 8000L

/**
 * Watches the partner's video until cancelled, reporting every [Shield] change to [onShield] (starting from
 * [Shield.CHECKING]). [open] loads the model; if it throws, the video is shown (fail open), as on the web. Samples
 * every 1.2 s, or 4 x the time a check took on a slow phone so the call stays smooth; skips while the app is in the
 * background. A check that throws is a dropped frame.
 */
suspend fun watchNudity(
    open: suspend () -> NudityCheck,
    onShield: (Shield) -> Unit,
    visible: StateFlow<Boolean> = AppForeground.visible,
    now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    coroutineScope {
        var shield = Shield.CHECKING
        fun set(next: Shield) {
            if (next != shield) { shield = next; onShield(next) }
        }
        val slow = launch {
            delay(NUDITY_START_BLUR_MS)
            if (shield == Shield.CHECKING) set(Shield.CLEAR)
        }
        val check = try {
            open()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            slow.cancel()
            set(Shield.CLEAR)
            return@coroutineScope
        }
        val verdicts = NudityVerdicts()
        while (true) {
            val started = now()
            if (visible.value) {
                val nude = try {
                    check.nude()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null // a dropped frame; try the next one
                }
                if (nude != null) verdicts.add(nude)?.let { set(if (it) Shield.HIDDEN else Shield.CLEAR) }
            }
            delay(maxOf(NUDITY_SAMPLE_MS, (now() - started) * 4))
        }
    }
}
