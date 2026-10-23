package com.yaari.chat.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** A short chime when someone joins or leaves the voice room (me included); Settings turns it off. As the web's. */
interface RoomSounds {
    var on: Boolean

    /** [settling]: the room's voice connection is opening or closing right now (the stage filled or emptied, I left). */
    fun play(join: Boolean, settling: Boolean = false)

    /** In memory (tests and previews): remembers what played. */
    class Memory(override var on: Boolean = true) : RoomSounds {
        val played = mutableListOf<Boolean>()
        val settling = mutableListOf<Boolean>()
        override fun play(join: Boolean, settling: Boolean) {
            played += join
            this.settling += settling
        }
    }

    /**
     * The phone, kept with the app's other settings. Which sound is heard depends on the phone's mode when it plays.
     * The room's voice (LiveKit) holds call mode and the audio focus as a call (USAGE_VOICE_COMMUNICATION, AUDIOFOCUS_GAIN)
     * only while someone is on the stage, and switches it on its own thread a moment after it connects or leaves:
     * - call mode: played as the voices are (USAGE_VOICE_COMMUNICATION), on their speaker or headset at their volume.
     *   Media is muted by the system during a call; a call sound in normal mode goes to the earpiece.
     * - normal mode: an ordinary app sound (USAGE_ASSISTANCE_SONIFICATION) on the speaker; it keeps playing when the
     *   phone goes into call mode under it.
     * While the voice is closing ([settling] in call mode) the chime waits until call mode is over (up to 1.5 s), so a
     * call sound never ends up on the earpiece.
     */
    class Device(context: Context) : RoomSounds {
        private val prefs = context.applicationContext.getSharedPreferences("yaari_settings", Context.MODE_PRIVATE)
        private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
        private val main = Handler(Looper.getMainLooper())
        private val pcm = HashMap<Boolean, ShortArray>()
        /** The chime playing (or played last). */
        @VisibleForTesting internal var playing: AudioTrack? = null

        override var on: Boolean
            get() = runCatching { prefs.getBoolean(KEY, true) }.getOrDefault(true)
            set(value) = prefs.edit { putBoolean(KEY, value) }

        /** A chime still waiting for the voice to close; a newer one replaces it (only the latest step sounds late). */
        private var waiting: Runnable? = null

        override fun play(join: Boolean, settling: Boolean) {
            waiting?.let(main::removeCallbacks)
            waiting = null
            playWhenSettled(join, if (settling) SETTLE_TRIES else 0)
        }

        private fun playWhenSettled(join: Boolean, tries: Int) {
            val call = audio?.mode == AudioManager.MODE_IN_COMMUNICATION
            if (call && tries > 0) {
                waiting = Runnable { playWhenSettled(join, tries - 1) }.also { main.postDelayed(it, SETTLE_STEP_MS) }
                return
            }
            waiting = null
            // A new track for every chime (a finished static track played again stays silent on some phones), freed
            // once it has played; chimes in quick succession each play out in full.
            runCatching {
                val t = track(pcm.getOrPut(join) { chime(if (join) JOIN else LEAVE) }, call)
                playing = t
                t.play()
                main.postDelayed(t::release, RELEASE_MS)
            }
        }

        private fun track(pcm: ShortArray, call: Boolean) = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(if (call) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
            .apply { write(pcm, 0, pcm.size) }

        private companion object {
            const val SETTLE_STEP_MS = 50L
            const val SETTLE_TRIES = 30
            /** Well after the 180 ms chime has played. */
            const val RELEASE_MS = 1_000L
        }
    }

    companion object {
        const val RATE = 22_050
        private const val KEY = "room_sounds"
        /** In: two high notes going up (A5 → D6). Out: two low notes going down (G4 → C4). As the web's. */
        val JOIN = doubleArrayOf(880.0, 1175.0)
        val LEAVE = doubleArrayOf(392.0, 262.0)

        /** A soft two-note chime (16-bit mono): each note fades in fast and out slowly. The web's chimeWav samples. */
        fun chime(notes: DoubleArray, noteMs: Int = 90, gain: Double = 0.22): ShortArray {
            val per = (RATE * noteMs / 1000.0).roundToInt()
            return ShortArray(per * notes.size) { i ->
                val t = (i % per).toDouble() / per
                val env = min(1.0, t * 20) * (1 - t).pow(2)
                (sin(2 * PI * notes[i / per] * i / RATE) * env * gain * 32_767).roundToInt().toShort()
            }
        }

        /**
         * Someone else came into the room or anyone (me too) came up on the stage (true); someone else left or anyone
         * stepped off the stage (false); nothing worth a sound (null). [before]/[after]: everyone, and who is on the
         * stage. The web's crowdChange.
         */
        fun change(before: Pair<List<String>, List<String>>, after: Pair<List<String>, List<String>>, meId: String): Boolean? {
            val (was, wasUp) = before.first.toSet() to before.second.toSet()
            val (now, up) = after.first.toSet() to after.second.toSet()
            return when {
                after.first.any { it != meId && it !in was } || after.second.any { it !in wasUp } -> true
                before.first.any { it != meId && it !in now } || before.second.any { it !in up } -> false
                else -> null
            }
        }
    }
}
