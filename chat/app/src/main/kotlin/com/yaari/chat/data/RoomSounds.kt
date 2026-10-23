package com.yaari.chat.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
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
     * The phone: kept with the app's other settings. While the room's voice is connected (the phone in call mode) the
     * chime plays as the voices do, on their output and at their volume; signalling or sonification sounds can then
     * go to the earpiece or a muted stream. Otherwise it plays as an ordinary app sound.
     */
    class Device(context: Context) : RoomSounds {
        private val prefs = context.applicationContext.getSharedPreferences("yaari_settings", Context.MODE_PRIVATE)
        private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
        private val pcm = HashMap<Boolean, ShortArray>()
        private var playing: AudioTrack? = null

        override var on: Boolean
            get() = runCatching { prefs.getBoolean(KEY, true) }.getOrDefault(true)
            set(value) = prefs.edit { putBoolean(KEY, value) }

        /**
         * A new track for every chime: replaying a finished static track (stop, reloadStaticData, play) stays silent on
         * some phones from the second time on. The one before is released here (it has long finished: 180 ms).
         */
        override fun play(join: Boolean) {
            runCatching {
                playing?.release()
                val call = audio?.mode == AudioManager.MODE_IN_COMMUNICATION
                playing = track(pcm.getOrPut(join) { chime(if (join) JOIN else LEAVE) }, call).apply { play() }
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
