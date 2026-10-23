package com.yaari.chat.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.core.content.edit
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** A short chime when someone joins or leaves the voice room (me included); Settings turns it off. As the web's. */
interface RoomSounds {
    var on: Boolean
    fun play(join: Boolean)

    /** In memory (tests and previews): remembers what played. */
    class Memory(override var on: Boolean = true) : RoomSounds {
        val played = mutableListOf<Boolean>()
        override fun play(join: Boolean) { played += join }
    }

    /**
     * The phone: kept with the app's other settings, played as in-call signalling so it mixes with the room's
     * voices on the call's own output (speaker, earpiece or headset) instead of opening a separate media stream.
     */
    class Device(context: Context) : RoomSounds {
        private val prefs = context.applicationContext.getSharedPreferences("yaari_settings", Context.MODE_PRIVATE)
        private val tracks = HashMap<Boolean, AudioTrack>()

        override var on: Boolean
            get() = runCatching { prefs.getBoolean(KEY, true) }.getOrDefault(true)
            set(value) = prefs.edit { putBoolean(KEY, value) }

        override fun play(join: Boolean) {
            runCatching {
                val t = tracks.getOrPut(join) { track(chime(if (join) JOIN else LEAVE)) }
                t.stop()
                t.reloadStaticData()
                t.play()
            }
        }

        private fun track(pcm: ShortArray) = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
            .apply { write(pcm, 0, pcm.size) }
    }

    companion object {
        const val RATE = 22_050
        private const val KEY = "room_sounds"
        private val JOIN = doubleArrayOf(660.0, 880.0)
        private val LEAVE = doubleArrayOf(700.0, 520.0)

        /** A soft two-note chime (16-bit mono): each note fades in fast and out slowly. The web's chimeWav samples. */
        fun chime(notes: DoubleArray, noteMs: Int = 90, gain: Double = 0.22): ShortArray {
            val per = (RATE * noteMs / 1000.0).roundToInt()
            return ShortArray(per * notes.size) { i ->
                val t = (i % per).toDouble() / per
                val env = min(1.0, t * 20) * (1 - t).pow(2)
                (sin(2 * PI * notes[i / per] * i / RATE) * env * gain * 32_767).roundToInt().toShort()
            }
        }

        /** Someone else came in (true), left (false), or nothing worth a sound (null). The web's crowdChange. */
        fun change(before: List<String>, after: List<String>, meId: String): Boolean? {
            val was = before.toSet()
            val now = after.toSet()
            return when {
                after.any { it != meId && it !in was } -> true
                before.any { it != meId && it !in now } -> false
                else -> null
            }
        }
    }
}
