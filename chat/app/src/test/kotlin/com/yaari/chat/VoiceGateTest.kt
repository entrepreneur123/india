package com.yaari.chat

import com.yaari.chat.data.VoiceGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.sin

/** The microphone gate (VoiceGate): speech goes out, a quiet room's hiss and echo leftovers don't. */
class VoiceGateTest {
    /** A 10 ms frame at 48 kHz: a 300 Hz tone of [rms] on the 16-bit scale, as WebRTC hands it over (native floats). */
    private fun frame(rms: Double): FloatBuffer {
        val b = ByteBuffer.allocateDirect(480 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (i in 0 until 480) b.put(i, (sin(2 * PI * 300 * i / 48_000) * rms * Math.sqrt(2.0)).toFloat())
        return b
    }
    private fun FloatBuffer.silent() = (0 until limit()).all { get(it) == 0f }

    @Test fun aQuietRoomsHissNeverGoesOut() {
        val gate = VoiceGate()
        repeat(200) { assertTrue(frame(100.0).also(gate::process).silent()) } // -50 dBFS, two seconds
    }

    @Test fun speechGoesOutAndThePauseAfterItClosesAfter400ms() {
        val gate = VoiceGate()
        repeat(50) { assertTrue(!frame(3_000.0).also(gate::process).silent()) } // -21 dBFS: talking
        // A short pause between words stays open; 400 ms of quiet closes it.
        val after = (1..60).map { frame(100.0).also(gate::process).silent() }
        assertEquals(List(39) { false } + List(21) { true }, after)
        assertTrue(!frame(3_000.0).also(gate::process).silent()) // talking again opens it at once
    }

    @Test fun inBetweenKeepsTheGateAsItWas() {
        val gate = VoiceGate()
        repeat(100) { assertTrue(frame(230.0).also(gate::process).silent()) } // between -46 and -40 dBFS: still closed
        frame(3_000.0).also(gate::process)
        repeat(100) { assertTrue(!frame(230.0).also(gate::process).silent()) } // and open stays open
    }
}
