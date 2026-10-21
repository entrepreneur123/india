package com.yaari.chat.live

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.Nsfw
import com.yaari.chat.data.PhotoCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Room photos on this phone (live build): shrunk to a 1280 px JPEG in the cache, then looked at by the call check's
 * model (nsfwjs MobileNetV2 on TensorFlow Lite, [NudityModel]) before anything is uploaded. The whole picture (squeezed
 * to a square) and its centre square are classified, as on the web. The model loads with the first photo and stays.
 */
class PhotoModel(private val app: Application) : PhotoCheck {
    private val lock = Mutex()
    private var interpreter: Interpreter? = null
    private val input = ByteBuffer.allocateDirect(Nsfw.SIZE * Nsfw.SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    private val pixels = IntArray(Nsfw.SIZE * Nsfw.SIZE)
    private val output = Array(1) { FloatArray(Nsfw.CLASSES) }

    override suspend fun shrink(uri: String): String = withContext(Dispatchers.IO) {
        val source = uri.toUri()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        app.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw BackendException("bad_media")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PhotoCheck.MAX_PX) sample *= 2
        val bmp = app.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw BackendException("bad_media")
        val k = PhotoCheck.MAX_PX.toFloat() / maxOf(bmp.width, bmp.height)
        val sized = if (k < 1f) bmp.scale((bmp.width * k).toInt(), (bmp.height * k).toInt()) else bmp
        val dir = File(app.cacheDir, "room-photos").apply { mkdirs() }
        // Earlier photos (sent or refused) go: only the newest is kept.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { sized.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        file.toUri().toString()
    }

    override suspend fun nude(jpegUri: String): Boolean = withContext(Dispatchers.Default) {
        lock.withLock {
            val model = interpreter ?: NudityModel.interpreter(app).also { interpreter = it }
            val bmp = app.contentResolver.openInputStream(jpegUri.toUri())?.use { BitmapFactory.decodeStream(it) } ?: throw BackendException("bad_media")
            val side = minOf(bmp.width, bmp.height)
            Nsfw.isNudePhoto(
                whole = { classify(model, bmp.scale(Nsfw.SIZE, Nsfw.SIZE)) },
                centre = { classify(model, Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side).scale(Nsfw.SIZE, Nsfw.SIZE)) },
            )
        }
    }

    /** The model's 5 probabilities for a [Nsfw.SIZE]-pixel square. */
    private fun classify(model: Interpreter, square: Bitmap): FloatArray {
        square.getPixels(pixels, 0, Nsfw.SIZE, 0, 0, Nsfw.SIZE, Nsfw.SIZE)
        Nsfw.argbToInput(pixels, input.asFloatBuffer())
        input.rewind()
        model.run(input, output)
        return output[0].copyOf()
    }
}
