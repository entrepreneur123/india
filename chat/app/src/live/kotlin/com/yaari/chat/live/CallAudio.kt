package com.yaari.chat.live

import io.livekit.android.AudioOptions
import io.livekit.android.LiveKitOverrides

/**
 * Echo cancellation for every LiveKit room (voice rooms and 1:1 calls): WebRTC's own canceller (AEC3) and noise
 * suppressor instead of the phone's. LiveKit uses the phone's built-in ones on Android 10+, which on many phones are
 * too weak for loudspeaker audio: the other side hears their own voice come back, ringing. With these off, the
 * echoCancellation / noiseSuppression options of the microphone track turn WebRTC's on.
 */
internal val callAudio = LiveKitOverrides(
    audioOptions = AudioOptions(
        javaAudioDeviceModuleCustomizer = { builder ->
            builder.setUseHardwareAcousticEchoCanceler(false)
            builder.setUseHardwareNoiseSuppressor(false)
        },
    ),
)
