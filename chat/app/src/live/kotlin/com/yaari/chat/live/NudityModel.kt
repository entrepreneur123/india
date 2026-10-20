package com.yaari.chat.live

import android.content.Context
import com.yaari.chat.data.Nsfw
import com.yaari.chat.data.NudityCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import livekit.org.webrtc.VideoFrame
import livekit.org.webrtc.VideoSink
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Hands the partner's next video frame, when asked, from the WebRTC thread to the nudity check. Added to the remote
 * video track next to the renderer; [onFrame] only takes a reference, all the work happens on the caller's thread.
 * A held frame can stall a hardware decoder, so it is held only until [NudityModel] has scaled it down.
 */
class FrameGrabber : VideoSink {
    private val lock = Any()
    private var wanted = false
    private var frame: VideoFrame? = null
    private val arrived = Channel<Unit>(Channel.CONFLATED)

    override fun onFrame(frame: VideoFrame) {
        synchronized(lock) {
            if (!wanted || this.frame != null) return
            frame.retain()
            this.frame = frame
            wanted = false
        }
        arrived.trySend(Unit)
    }

    /** The next frame, retained (the caller releases it), or null if none comes within [timeoutMs] (camera off). */
    suspend fun next(timeoutMs: Long): VideoFrame? {
        synchronized(lock) { wanted = true }
        try {
            withTimeoutOrNull(timeoutMs) {
                while (synchronized(lock) { frame == null }) arrived.receive()
            }
        } catch (e: CancellationException) {
            take()?.release()
            throw e
        }
        return take() // also a frame that came just as the wait timed out
    }

    private fun take(): VideoFrame? = synchronized(lock) {
        wanted = false
        frame.also { frame = null }
    }
}

/**
 * The nsfwjs MobileNetV2 on TensorFlow Lite (CPU, XNNPACK), from assets/nsfw_mobilenet_v2.tflite (2.9 MB, int8 weights).
 * [nude] looks at the centre square of the next frame from [frames], like the person sees it (crop to fill). Loading
 * and inference run on a background thread; one instance serves one call, then [close].
 */
class NudityModel private constructor(private val interpreter: Interpreter, private val frames: FrameGrabber) : NudityCheck, Closeable {
    private val input = ByteBuffer.allocateDirect(Nsfw.SIZE * Nsfw.SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    private val pixels = input.asFloatBuffer()
    private val output = Array(1) { FloatArray(Nsfw.CLASSES) }

    override suspend fun nude(): Boolean? {
        val frame = frames.next(FRAME_WAIT_MS) ?: return null
        val rotation = frame.rotation
        // withContext returns only once the block is done, even when cancelled, so [close] never races a run.
        return withContext(Dispatchers.Default) {
            // The decoder's frame is scaled to 224 px first (on the GPU for hardware-decoded video), then let go.
            val i420 = try {
                val buffer = frame.buffer
                val side = minOf(buffer.width, buffer.height)
                val square = buffer.cropAndScale(
                    (buffer.width - side) / 4 * 2, (buffer.height - side) / 4 * 2, side, side, Nsfw.SIZE, Nsfw.SIZE,
                )
                try { square.toI420() } finally { square.release() }
            } finally {
                frame.release()
            } ?: return@withContext null
            try {
                Nsfw.i420ToInput(
                    Nsfw.SIZE, rotation,
                    i420.dataY, i420.strideY, i420.dataU, i420.strideU, i420.dataV, i420.strideV, pixels,
                )
            } finally {
                i420.release()
            }
            input.rewind()
            interpreter.run(input, output)
            Nsfw.isNude(output[0])
        }
    }

    override fun close() = interpreter.close()

    companion object {
        private const val MODEL = "nsfw_mobilenet_v2.tflite"
        /** A partner sending at least 4 frames a second is looked at; waiting longer would slow the sampling. */
        private const val FRAME_WAIT_MS = 250L

        /** Loads the model; the caller closes it. Throws if the model can't load on this phone (the video then shows). */
        suspend fun open(context: Context, frames: FrameGrabber): NudityModel {
            val model = withContext(Dispatchers.IO + NonCancellable) {
                // Stored uncompressed in the APK (AGP's default for .tflite), so it is mapped, not copied into memory.
                val file = context.assets.openFd(MODEL).use { fd ->
                    FileInputStream(fd.fileDescriptor).use { it.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength) }
                }
                // Two threads: leaves the other cores to the call itself (video decoding and encoding).
                NudityModel(Interpreter(file, Interpreter.Options().setNumThreads(2)), frames)
            }
            try {
                currentCoroutineContext().ensureActive() // the call ended while loading
            } catch (e: CancellationException) {
                model.close()
                throw e
            }
            return model
        }
    }
}
