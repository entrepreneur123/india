package com.lottodost.app.data

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.random.Random

/**
 * Server-instability behaviour at draw time: blocked hosts, overloaded CDNs (429/503),
 * stale mirrors, refresh spam. Uses a scripted transport, no network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ResultRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val a = "https://a.example/v2/"
    private val b = "https://b.example/v2/"
    private var nowMs = 1_760_000_000_000L
    private val calls = mutableListOf<String>()
    private var handler: (String, String?) -> HttpResult = { _, _ -> HttpResult(404) }

    private fun feed(generatedAt: String, vararg ids: String) = """{"schema":2,"generatedAt":"$generatedAt","draws":[""" +
        ids.joinToString(",") { """{"id":"$it","slot":"NL1PM","date":"2026-10-18","time":"13:00","state":"Nagaland",
            "name":"DEAR WISH","format":"D5","price":7,"tiers":[{"rank":"1","prize":10000000,"match":"FULL","numbers":["74D 70698"]}]}""" } +
        "]}"

    private lateinit var repo: ResultRepository

    @Before fun setUp() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("t${System.nanoTime()}", Context.MODE_PRIVATE)
        repo = ResultRepository(tmp.newFolder(), prefs, { url, etag -> calls += url; handler(url, etag) },
            listOf(a, b), clock = { nowMs }, random = Random(1))
    }

    @Test fun blockedPrimaryFallsBackAndBecomesSticky() = runBlocking {
        handler = { url, _ -> if (url.startsWith(a)) throw IOException("connect timed out") else HttpResult(200, feed("2026-10-18T07:40:00+00:00", "d1"), "e1") }
        repo.refresh(ResultRepository.Mode.LIGHT, force = true)
        assertEquals(listOf("${a}today.json", "${b}today.json"), calls)
        assertEquals(setOf("d1"), repo.state.value.draws.keys)
        assertFalse(repo.state.value.offline)
        calls.clear(); nowMs += 60_000
        repo.refresh(ResultRepository.Mode.LIGHT, force = true)
        assertEquals("the mirror that worked is tried first", listOf("${b}today.json"), calls)
    }

    @Test fun etagRevalidationAnd304KeepsData() = runBlocking {
        handler = { _, etag -> if (etag == "e1") HttpResult(304) else HttpResult(200, feed("2026-10-18T07:40:00+00:00", "d1"), "e1") }
        repo.refresh(ResultRepository.Mode.LIGHT, force = true)
        nowMs += 60_000
        val changed = repo.refresh(ResultRepository.Mode.LIGHT, force = true)
        assertFalse(changed)
        assertEquals(setOf("d1"), repo.state.value.draws.keys)
    }

    @Test fun staleMirrorNeverOverridesNewerData() = runBlocking {
        var generated = "2026-10-18T08:00:00+00:00"
        handler = { _, _ -> HttpResult(200, feed(generated, "d1"), null) }
        repo.refresh(ResultRepository.Mode.LIGHT, force = true)
        generated = "2026-10-18T06:00:00+00:00"   // e.g. jsDelivr serving a 12-hour-old copy
        handler = { _, _ ->
            HttpResult(200, """{"schema":2,"generatedAt":"$generated","draws":[{"id":"d1","slot":"NL1PM","date":"2026-10-18",
                "time":"13:00","state":"Nagaland","name":"OLD","format":"D5","price":7,"status":"PDF_ONLY"}]}""", null)
        }
        nowMs += 60_000
        repo.refresh(ResultRepository.Mode.LIGHT, force = true)
        assertEquals("DEAR WISH", repo.state.value.draws["d1"]!!.name)
    }

    @Test fun overloadedEverywhereBacksOffHonouringRetryAfter() = runBlocking {
        handler = { _, _ -> HttpResult(429, retryAfterS = 120) }
        repo.refresh(ResultRepository.Mode.LIGHT, force = true)
        assertTrue(repo.state.value.offline)
        assertEquals(2, calls.size)
        calls.clear()
        nowMs += 100_000                         // still inside 120 s (+ jitter)
        repo.refresh(ResultRepository.Mode.LIGHT, force = true)
        assertEquals("no request while backing off", 0, calls.size)
        nowMs += 200_000                         // 300 s later: past 120 s + max 50 % jitter
        handler = { _, _ -> HttpResult(200, feed("2026-10-18T07:40:00+00:00", "d1"), null) }
        repo.refresh(ResultRepository.Mode.LIGHT, force = true)
        assertEquals(1, calls.size)
        assertFalse(repo.state.value.offline)
    }

    @Test fun refreshSpamIsThrottled() = runBlocking {
        handler = { _, _ -> HttpResult(200, feed("2026-10-18T07:40:00+00:00", "d1"), null) }
        repo.refresh(ResultRepository.Mode.LIGHT, force = true)
        calls.clear()
        repeat(10) { nowMs += 1_000; repo.refresh(ResultRepository.Mode.LIGHT) }   // user pulling every second
        assertEquals(0, calls.size)
        nowMs += ResultRepository.MIN_INTERVAL_MS
        repo.refresh(ResultRepository.Mode.LIGHT)
        assertEquals(1, calls.size)
    }

    @Test fun corruptJsonFromAMirrorIsIgnored() = runBlocking {
        handler = { url, _ -> if (url.startsWith(a)) HttpResult(200, "<html>captive portal</html>") else HttpResult(200, feed("2026-10-18T07:40:00+00:00", "d1"), null) }
        repo.refresh(ResultRepository.Mode.LIGHT, force = true)
        // Public Wi-Fi login pages answer 200 with HTML: skip that mirror, use the next one.
        assertEquals(setOf("d1"), repo.state.value.draws.keys)
        calls.clear(); nowMs += 60_000
        repo.refresh(ResultRepository.Mode.LIGHT, force = true)
        assertEquals("the broken mirror is not remembered as preferred", "${b}today.json", calls.first())
    }

    @Test fun serverClockOffsetIsRecorded() = runBlocking {
        handler = { _, _ -> HttpResult(200, feed("2026-10-18T07:40:00+00:00", "d1"), null, serverDateMs = nowMs + 3_600_000) }
        repo.refresh(ResultRepository.Mode.LIGHT, force = true)
        assertEquals(3_600_000L, repo.state.value.clockOffsetMs) // phone clock 1 h behind
    }

    @Test fun cacheSurvivesRestartOffline() = runBlocking {
        val dir = tmp.newFolder()
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("restart", Context.MODE_PRIVATE)
        val online = ResultRepository(dir, prefs, { _, _ -> HttpResult(200, feed("2026-10-18T07:40:00+00:00", "d1"), null) }, listOf(a))
        online.refresh(ResultRepository.Mode.LIGHT, force = true)
        val offline = ResultRepository(dir, prefs, { _, _ -> throw IOException("airplane mode") }, listOf(a))
        offline.loadCache()
        assertEquals(setOf("d1"), offline.state.value.draws.keys)
    }
}
