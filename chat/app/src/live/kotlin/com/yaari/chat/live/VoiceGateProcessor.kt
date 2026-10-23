package com.yaari.chat.live

import com.yaari.chat.data.VoiceGate
import io.livekit.android.audio.AudioProcessorInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** [VoiceGate] on LiveKit's microphone, after WebRTC's own processing (capture post-processing). */
internal class VoiceGateProcessor : AudioProcessorInterface {
    private val gate = VoiceGate()
    override fun isEnabled() = true
    override fun getName() = "yaari_voice_gate"
    override fun initializeAudioProcessing(sampleRateHz: Int, numChannels: Int) = Unit
    override fun resetAudioProcessing(newRate: Int) = Unit
    // A direct buffer over WebRTC's float samples (native byte order; a ByteBuffer reads big-endian by default).
    override fun processAudio(numBands: Int, numFrames: Int, buffer: ByteBuffer) = gate.process(buffer.order(ByteOrder.nativeOrder()).asFloatBuffer())
}
