package com.yaari.chat

import com.yaari.chat.data.NUDITY_SAMPLE_MS
import com.yaari.chat.data.NUDITY_START_BLUR_MS
import com.yaari.chat.data.Nsfw
import com.yaari.chat.data.NudityCheck
import com.yaari.chat.data.NudityVerdicts
import com.yaari.chat.data.Shield
import com.yaari.chat.data.watchNudity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.FloatBuffer

/** The nudity check of the partner's video (same rules and timings as web/lib/nudity.ts and video-call.tsx). */
@OptIn(ExperimentalCoroutinesApi::class)
class NudityTest {
    // ---- verdicts ----

    @Test fun timingsMatchTheWeb() {
        assertEquals(1200L, NUDITY_SAMPLE_MS)
        assertEquals(8000L, NUDITY_START_BLUR_MS)
        assertEquals(2, NudityVerdicts.HIDE_AFTER)
        assertEquals(4, NudityVerdicts.SHOW_AFTER)
    }

    @Test fun pornPlusHentaiOfAtLeast60PercentIsNude() {
        // Drawing, Hentai, Neutral, Porn, Sexy
        assertTrue(Nsfw.isNude(floatArrayOf(0.1f, 0.3f, 0f, 0.3f, 0.3f)))
        assertTrue(Nsfw.isNude(floatArrayOf(0f, 0f, 0f, 0.95f, 0.05f)))
        assertFalse(Nsfw.isNude(floatArrayOf(0f, 0.29f, 0.11f, 0.3f, 0.3f)))
        assertFalse("swimwear / a bare chest alone is not nude", Nsfw.isNude(floatArrayOf(0f, 0f, 0.01f, 0.01f, 0.98f)))
        assertFalse(Nsfw.isNude(floatArrayOf(0.05f, 0f, 0.95f, 0f, 0f)))
    }

    @Test fun hiddenAfterTwoNudeSamplesInARow() {
        val v = NudityVerdicts()
        assertNull(v.add(true))
        assertEquals(true, v.add(true))
        assertNull("already hidden", v.add(true))
        assertEquals(true, v.hidden)
    }

    @Test fun oneOddFrameDoesNotHide() {
        val v = NudityVerdicts()
        assertEquals("the first clean sample is a verdict (lifts the start-up blur)", false, v.add(false))
        repeat(5) {
            assertNull(v.add(true))
            assertNull(v.add(false))
        }
        assertEquals(false, v.hidden)
    }

    @Test fun shownAgainAfterFourCleanSamples() {
        val v = NudityVerdicts()
        v.add(true); v.add(true)
        repeat(3) { assertNull(v.add(false)) }
        assertNull("a nude sample restarts the count", v.add(true))
        repeat(3) { assertNull(v.add(false)) }
        assertEquals(false, v.add(false))
        assertEquals(false, v.hidden)
        assertNull(v.add(true))
        assertEquals("hidden again", true, v.add(true))
    }

    // ---- the watch loop (virtual time: fake clock and fake classifier) ----

    private val shields = mutableListOf<Pair<Long, Shield>>()
    private val visible = MutableStateFlow(true)

    /** Starts a watch whose check takes [inferenceMs] and answers from [answers] (then null = no frame). */
    private fun TestScope.watch(answers: List<Boolean?>, inferenceMs: Long = 50, opens: Boolean = true): MutableList<Long> {
        val checkedAt = mutableListOf<Long>()
        val queue = ArrayDeque(answers)
        val check = NudityCheck {
            checkedAt += testScheduler.currentTime
            delay(inferenceMs)
            queue.removeFirstOrNull()
        }
        backgroundScope.launch {
            watchNudity(
                open = { if (opens) check else error("no model") },
                onShield = { shields += testScheduler.currentTime to it },
                visible = visible,
                now = { testScheduler.currentTime },
            )
        }
        runCurrent()
        return checkedAt
    }

    private fun TestScope.wait(ms: Long) {
        advanceTimeBy(ms); runCurrent()
    }

    @Test fun theFirstCleanFrameLiftsTheStartBlur() = runTest {
        watch(listOf(false))
        wait(49)
        assertEquals(emptyList<Pair<Long, Shield>>(), shields)
        wait(1)
        assertEquals(listOf(50L to Shield.CLEAR), shields)
        wait(60_000)
        assertEquals("no more changes", 1, shields.size)
    }

    @Test fun samples1200msAfterEachCheck() = runTest {
        val fast = watch(List(10) { false }, inferenceMs = 50)
        wait(5_000)
        assertEquals(listOf(0L, 1250L, 2500L, 3750L, 5000L), fast)
    }

    @Test fun aSlowPhoneSamplesLessOften() = runTest {
        val slow = watch(List(10) { false }, inferenceMs = 500)
        wait(6_100)
        assertEquals("500 ms check + 4 x 500 ms", listOf(0L, 2500L, 5000L), slow)
    }

    @Test fun noVerdictWithin8sShowsTheVideoAnyway() = runTest {
        watch(emptyList()) // no frame, or every frame dropped
        wait(NUDITY_START_BLUR_MS - 1)
        assertEquals(emptyList<Pair<Long, Shield>>(), shields)
        wait(1)
        assertEquals(listOf(NUDITY_START_BLUR_MS to Shield.CLEAR), shields)
    }

    @Test fun aModelThatCannotLoadFailsOpenAtOnce() = runTest {
        val checks = watch(listOf(true, true), opens = false)
        assertEquals(listOf(0L to Shield.CLEAR), shields)
        wait(60_000)
        assertEquals(1, shields.size)
        assertEquals(emptyList<Long>(), checks)
    }

    @Test fun nudityHidesAndFourCleanFramesShowAgain() = runTest {
        watch(listOf(true, true, false, false, false, false))
        wait(1_299)
        assertEquals("one nude frame is not enough", emptyList<Pair<Long, Shield>>(), shields)
        wait(1)
        assertEquals(listOf(1300L to Shield.HIDDEN), shields)
        wait(3 * 1300)
        assertEquals("3 clean frames are not enough", 1, shields.size)
        wait(1300)
        assertEquals(listOf(1300L to Shield.HIDDEN, 6300L to Shield.CLEAR), shields)
    }

    @Test fun hiddenEvenAfterTheStartBlurTimedOut() = runTest {
        watch(List(7) { null } + listOf(true, true))
        wait(NUDITY_START_BLUR_MS)
        assertEquals(listOf(NUDITY_START_BLUR_MS to Shield.CLEAR), shields)
        wait(3_000)
        assertEquals(Shield.HIDDEN, shields.last().second)
    }

    @Test fun nothingIsCheckedInTheBackground() = runTest {
        visible.value = false
        val checks = watch(List(10) { false })
        wait(NUDITY_START_BLUR_MS)
        assertEquals(emptyList<Long>(), checks)
        assertEquals("fails open on time", listOf(NUDITY_START_BLUR_MS to Shield.CLEAR), shields)
        visible.value = true
        wait(1200)
        assertEquals(1, checks.size)
    }

    @Test fun aCheckThatThrowsIsADroppedFrame() = runTest {
        var n = 0
        backgroundScope.launch {
            watchNudity(
                open = { NudityCheck { if (n++ == 0) error("dropped") else true } },
                onShield = { shields += testScheduler.currentTime to it },
                visible = visible,
                now = { testScheduler.currentTime },
            )
        }
        wait(3 * 1200)
        assertEquals(listOf(2400L to Shield.HIDDEN), shields)
    }

    // ---- frame to model input (the real model needs Android's native TFLite library, not on the JVM) ----

    private fun i420(size: Int, y: (Int, Int) -> Int, u: Int, v: Int): Triple<ByteBuffer, ByteBuffer, ByteBuffer> {
        val stride = size + 8 // padded rows, like decoder buffers
        val yb = ByteBuffer.allocateDirect(stride * size)
        for (row in 0 until size) for (col in 0 until size) yb.put(row * stride + col, y(col, row).toByte())
        val c = (size + 1) / 2
        val ub = ByteBuffer.allocateDirect(stride * c).also { b -> repeat(stride * c) { b.put(it, u.toByte()) } }
        val vb = ByteBuffer.allocateDirect(stride * c).also { b -> repeat(stride * c) { b.put(it, v.toByte()) } }
        return Triple(yb, ub, vb)
    }

    private fun rgb(size: Int, rotation: Int, y: (Int, Int) -> Int, u: Int = 128, v: Int = 128): FloatArray {
        val (yb, ub, vb) = i420(size, y, u, v)
        val out = FloatBuffer.allocate(size * size * 3)
        Nsfw.i420ToInput(size, rotation, yb, size + 8, ub, size + 8, vb, size + 8, out)
        assertEquals(0, out.position())
        return FloatArray(out.capacity()).also { out.get(it) }
    }

    @Test fun pixelsAreRgbIn0To1() {
        val white = rgb(Nsfw.SIZE, 0, { _, _ -> 235 })
        assertEquals(Nsfw.SIZE * Nsfw.SIZE * 3, white.size)
        assertTrue(white.all { it in 0.99f..1f })
        assertTrue(rgb(4, 0, { _, _ -> 16 }).all { it == 0f })
        // BT.601 red: Y 81, U 90, V 240.
        val red = rgb(4, 0, { _, _ -> 81 }, u = 90, v = 240)
        assertEquals(1f, red[0], 0.02f)
        assertEquals(0f, red[1], 0.02f)
        assertEquals(0f, red[2], 0.02f)
        assertTrue("clamped", red.all { it in 0f..1f })
    }

    @Test fun framesAreTurnedUpright() {
        val n = 4
        val src = { x: Int, y: Int -> 16 + 10 * (y * n + x) } // every pixel distinct, gray
        fun at(px: FloatArray, x: Int, y: Int) = px[(y * n + x) * 3]
        fun value(x: Int, y: Int) = ((src(x, y) - 16) * 1.164f).coerceIn(0f, 255f) / 255f
        val r0 = rgb(n, 0, src)
        val r90 = rgb(n, 90, src)
        val r180 = rgb(n, 180, src)
        val r270 = rgb(n, 270, src)
        for (y in 0 until n) for (x in 0 until n) {
            assertEquals(value(x, y), at(r0, x, y), 1e-4f)
            // Turning clockwise by 90: the source's top-left corner ends up top-right.
            assertEquals(value(x, y), at(r90, n - 1 - y, x), 1e-4f)
            assertEquals(value(x, y), at(r180, n - 1 - x, n - 1 - y), 1e-4f)
            assertEquals(value(x, y), at(r270, y, n - 1 - x), 1e-4f)
        }
    }

    @Test fun theLiveBuildShipsTheModel() {
        val src = File("src").takeIf { it.isDirectory } ?: File("app/src")
        val model = File(src, "live/assets/nsfw_mobilenet_v2.tflite")
        assertTrue(model.isFile)
        val head = model.inputStream().use { it.readNBytes(8) }
        assertEquals("a TFLite flatbuffer", "TFL3", String(head, 4, 4))
        assertTrue("keep the APK small: ${model.length()} bytes", model.length() < 3_200_000)
        assertTrue(File(src, "live/res/raw/nsfw_model_license.txt").readText().contains("MIT License"))
    }
}
