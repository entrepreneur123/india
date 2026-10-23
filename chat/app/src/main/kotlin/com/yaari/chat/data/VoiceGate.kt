package com.yaari.chat.data

import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * The microphone goes out only while I'm talking, as Discord's voice activity does: a 10 ms frame louder than -40 dBFS
 * opens the gate, and it closes (silence) after 400 ms below -46 dBFS. The hiss of a quiet room and what's left of the
 * room's own voices after echo cancellation are never sent, so they can't build up into a hiss or an echo loop between
 * phones. Samples are WebRTC's (floats on the 16-bit scale, after its echo canceller, noise suppressor and auto gain).
 */
class VoiceGate {
    private var open = false
    private var quietMs = 0

    /** One 10 ms frame, in place. */
    fun process(frame: FloatBuffer) {
        val start = frame.position()
        val n = frame.remaining()
        if (n == 0) return
        var sum = 0.0
        for (i in start until start + n) frame.get(i).let { sum += it * it }
        val rms = sqrt(sum / n)
        when {
            rms >= OPEN -> { open = true; quietMs = 0 }
            rms < CLOSE && open -> { quietMs += FRAME_MS; if (quietMs >= HOLD_MS) open = false }
            else -> quietMs = 0
        }
        if (!open) for (i in start until start + n) frame.put(i, 0f)
    }

    companion object {
        /** -40 dBFS and -46 dBFS on the 16-bit scale. */
        const val OPEN = 328.0
        const val CLOSE = 164.0
        const val HOLD_MS = 400
        const val FRAME_MS = 10
    }
}
