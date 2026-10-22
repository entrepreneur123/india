package com.yaari.chat.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.yaari.chat.data.BackendException
import com.yaari.chat.model.AudioStatus
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.DisconnectReason
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.AudioPresets
import io.livekit.android.room.participant.AudioTrackPublishDefaults
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Live audio for a voice room: LiveKit room "voice-<id>" (token from the voice-token edge function). Everyone hears
 * the people on the stage; I send my microphone only while I'm on a seat and unmuted. The web's lib/voice-audio.ts.
 *
 * Audio routing (Bluetooth, wired headset, speaker, earpiece) is LiveKit's AudioSwitch. When another app takes the
 * audio (a phone call), the room goes silent and my microphone off until the call ends ([Events.interrupted]).
 */
class VoiceAudio(private val context: Context, private val scope: CoroutineScope, private val ev: Events) {
    interface Events {
        fun status(s: AudioStatus)
        fun interrupted(on: Boolean)
        /** Audio level 0..1 per identity (user id), ten times a second; silent people are left out. */
        fun levels(levels: Map<String, Float>)
        /** The server changed what I may do (voice-sync after I was moved to or from a seat). */
        fun permissions(canPublish: Boolean)
        /** A host muted my microphone on the server. */
        fun micMutedByServer()
        /** Disconnected for good: removed from the room, the room ended, or the connection was lost. */
        fun gone(removed: Boolean)
    }

    // Call audio mode (MODE_IN_COMMUNICATION: the phone's echo canceller runs), speaker unless a headset is in, WebRTC
    // echo cancellation / noise suppression / auto gain on. Speech: Opus mono capped at 24 kbps (a listener on 4G
    // downloads one stream per speaker), DTX so silence costs nothing, RED so a lost packet is covered by the next.
    private val room: Room = LiveKit.create(
        context.applicationContext,
        options = RoomOptions(
            adaptiveStream = false,
            dynacast = false,
            audioTrackCaptureDefaults = LocalAudioTrackOptions(noiseSuppression = true, echoCancellation = true, autoGainControl = true),
            audioTrackPublishDefaults = AudioTrackPublishDefaults(audioBitrate = AudioPresets.SPEECH.maxBitrate, dtx = true, red = true),
        ),
    )
    private var leaving = false
    private var released = false
    private var micWanted = false
    private val jobs = mutableListOf<Job>()

    private val focus = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            // A phone call (or an alarm) takes the audio for a while and gives it back with AUDIOFOCUS_GAIN. A permanent
            // loss (another app started playing music) is not followed by a GAIN, so muting then would leave the room
            // silent for good: the room keeps playing, as WhatsApp and X Spaces do.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                room.setSpeakerMute(true)
                room.setMicrophoneMute(true)
                ev.interrupted(true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                room.setSpeakerMute(false)
                room.setMicrophoneMute(false)
                ev.interrupted(false)
            }
        }
    }

    val canPublish get() = room.localParticipant.permissions?.canPublish == true

    suspend fun connect(url: String, token: String) {
        jobs += scope.launch {
            room.events.collect { e ->
                when (e) {
                    is RoomEvent.Reconnecting -> ev.status(AudioStatus.RECONNECTING)
                    is RoomEvent.Reconnected -> ev.status(AudioStatus.CONNECTED)
                    is RoomEvent.ParticipantPermissionsChanged ->
                        if (e.participant == room.localParticipant) ev.permissions(e.newPermissions?.canPublish == true)
                    is RoomEvent.TrackMuted -> {
                        // My own mute arrives here too; only a mute while I wanted to talk (the server's) is news.
                        if (e.participant == room.localParticipant && e.publication.source == Track.Source.MICROPHONE && micWanted) {
                            micWanted = false
                            ev.micMutedByServer()
                        }
                    }
                    is RoomEvent.Disconnected -> if (!leaving) {
                        leaving = true
                        stopLoops()
                        ev.status(AudioStatus.FAILED)
                        ev.gone(e.reason in REMOVED)
                    }
                    else -> Unit
                }
            }
        }
        // Speaking rings: LiveKit's own audio levels (the server's for others, the local analyser for me).
        jobs += scope.launch {
            while (isActive) {
                delay(LEVEL_MS)
                val all: List<Participant> = listOf(room.localParticipant) + room.remoteParticipants.values
                ev.levels(all.filter { it.audioLevel > 0.02f }.associate { (it.identity?.value ?: "") to minOf(1f, it.audioLevel) })
            }
        }
        room.audioSwitchHandler?.registerOnAudioFocusChangeListener(focus)
        ev.status(AudioStatus.CONNECTING)
        try {
            room.connect(url, token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!leaving) {
                leaving = true
                stopLoops()
                ev.status(AudioStatus.FAILED)
            }
            throw BackendException("network")
        }
        ev.status(AudioStatus.CONNECTED)
    }

    /** Throws mic_denied (no permission or no microphone) or not_allowed (not on a seat yet). */
    suspend fun setMic(on: Boolean) {
        micWanted = on
        if (on && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micWanted = false
            throw BackendException("mic_denied")
        }
        if (on && !canPublish) {
            micWanted = false
            throw BackendException("not_allowed")
        }
        try {
            room.localParticipant.setMicrophoneEnabled(on)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            micWanted = false
            throw BackendException("mic_denied")
        } catch (e: Exception) {
            micWanted = false
            throw BackendException("not_allowed")
        }
    }

    private fun stopLoops() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        runCatching { room.audioSwitchHandler?.unregisterOnAudioFocusChangeListener(focus) }
    }

    /** Leaves LiveKit and frees the room (also after LiveKit dropped us by itself). */
    fun disconnect() {
        stopLoops()
        if (!leaving) {
            leaving = true
            runCatching { room.disconnect() }
        }
        if (!released) {
            released = true
            runCatching { room.release() }
        }
    }

    private companion object {
        const val LEVEL_MS = 100L
        val REMOVED = setOf(DisconnectReason.PARTICIPANT_REMOVED, DisconnectReason.ROOM_DELETED, DisconnectReason.ROOM_CLOSED)
    }
}
