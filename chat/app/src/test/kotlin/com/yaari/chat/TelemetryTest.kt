package com.yaari.chat

import com.yaari.chat.data.CrashReports
import com.yaari.chat.data.Funnel
import com.yaari.chat.data.Telemetry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Crash reports on disk until the next launch, and the funnel events, batched within the server's limits (021). */
@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val batches = mutableListOf<String>()
    private var failing = false

    private fun TestScope.telemetry(dir: File = tmp.root, background: MutableStateFlow<Boolean>? = null) =
        Telemetry(dir, "android-0.8.0", backgroundScope, { b -> if (failing) throw java.io.IOException("offline"); batches += b }, background)

    @Test fun anUncaughtCrashIsSavedAndHandedOn() {
        val dir = File(tmp.root, "reports")
        var handedOn: Throwable? = null
        val handler = CrashReports.Handler(dir, "android-0.8.0") { _, e -> handedOn = e }
        val crash = IllegalStateException("seat 3 for 919876543210", RuntimeException("cause"))
        handler.uncaughtException(Thread("main"), crash)

        assertSame(crash, handedOn)
        val saved = CrashReports.saved(dir).single().readText()
        assertTrue(saved, saved.startsWith("{\"kind\":\"error\",\"name\":\"crash:IllegalStateException/RuntimeException\""))
        assertTrue(saved, "\"platform\":\"android\"" in saved && "\"version\":\"android-0.8.0\"" in saved && "\"route\":\"main\"" in saved)
        assertTrue(saved, "seat 3 for # / cause" in saved)
        assertFalse(saved, "919876543210" in saved) // no phone numbers
        assertTrue(saved, "\"stack\":\"java.lang.IllegalStateException" in saved && "\\n\\tat " in saved)
    }

    @Test fun aCrashLoopKeepsOnlyTheNewestReports() {
        repeat(8) { CrashReports.save(tmp.root, "{\"kind\":\"error\",\"name\":\"crash:$it\"}") }
        val left = CrashReports.saved(tmp.root)
        assertEquals(CrashReports.MAX_SAVED, left.size)
        assertTrue(left.last().readText().contains("crash:7"))
    }

    @Test fun savedReportsAreSentOnTheNextLaunchAndThenDeleted() = runTest {
        CrashReports.save(tmp.root, CrashReports.report(RuntimeException("boom"), "main", "android-0.8.0"))
        failing = true
        telemetry().sendSaved(); runCurrent()
        assertEquals(1, CrashReports.saved(tmp.root).size) // offline: kept for the next launch

        failing = false
        telemetry().sendSaved(); runCurrent()
        assertTrue(batches.single().startsWith("[{\"kind\":\"error\",\"name\":\"crash:RuntimeException\""))
        assertTrue(CrashReports.saved(tmp.root).isEmpty())
    }

    @Test fun eventsGoInBatchesOfTwentyAMinuteLater() = runTest {
        val t = telemetry()
        t.track(Funnel.ROOM_JOINED)
        t.track(Funnel.CHAT_MESSAGE_SENT)
        t.track(Funnel.CHAT_MESSAGE_SENT) // only the first message of a run counts
        runCurrent()
        assertTrue(batches.isEmpty())
        advanceTimeBy(Telemetry.FLUSH_DELAY_MS + 1); runCurrent()
        assertEquals(
            "[{\"kind\":\"event\",\"name\":\"room_joined\",\"version\":\"android-0.8.0\",\"platform\":\"android\"}," +
                "{\"kind\":\"event\",\"name\":\"chat_message_sent\",\"version\":\"android-0.8.0\",\"platform\":\"android\"}]",
            batches.single(),
        )

        batches.clear()
        repeat(20) { t.track(Funnel.MATCH_FOUND) }
        runCurrent()
        assertEquals(1, batches.size) // 20 go at once, the server's limit per call
        assertEquals(20, batches[0].split("match_found").size - 1)
        repeat(5) { t.track(Funnel.MATCH_FOUND) }
        runCurrent()
        assertEquals(1, batches.size)
        advanceTimeBy(Telemetry.FLUSH_DELAY_MS + 1); runCurrent()
        assertEquals(5, batches[1].split("match_found").size - 1)
    }

    @Test fun offlineEventsWaitAndTheBackgroundSendsThem() = runTest {
        val visible = MutableStateFlow(true)
        val t = telemetry(background = visible)
        runCurrent()
        failing = true
        t.track(Funnel.ROOM_CREATED)
        advanceTimeBy(Telemetry.FLUSH_DELAY_MS + 1); runCurrent()
        assertEquals(1, t.pending.size)
        failing = false
        visible.value = false; runCurrent() // the app went to the background
        assertTrue(batches.single().contains("room_created"))
        assertTrue(t.pending.isEmpty())
    }

    @Test fun aRunSendsAtMostItsShare() = runTest {
        val t = telemetry()
        repeat((Telemetry.MAX_PER_RUN + 50) / 10) {
            repeat(10) { t.track(Funnel.MATCH_FOUND) }
            runCurrent()
        }
        advanceTimeBy(Telemetry.FLUSH_DELAY_MS + 1); runCurrent()
        assertEquals(Telemetry.MAX_PER_RUN, batches.sumOf { it.split("match_found").size - 1 })
    }
}
