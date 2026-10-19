package com.yaari.chat

import com.yaari.chat.data.PartnerWatch
import com.yaari.chat.data.PartnerWatch.Companion.PARTNER_GRACE_MS
import com.yaari.chat.data.PartnerWatch.Companion.PARTNER_JOIN_TIMEOUT_MS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The partner-presence rules of a live random chat (same timings as the web app). */
@OptIn(ExperimentalCoroutinesApi::class)
class PartnerWatchTest {
    private var gone = 0

    private fun TestScope.watch() = PartnerWatch(backgroundScope) { gone++ }

    private fun TestScope.wait(ms: Long) {
        advanceTimeBy(ms); runCurrent()
    }

    @Test fun timingsMatchTheProtocol() {
        assertEquals(12_000L, PARTNER_JOIN_TIMEOUT_MS)
        assertEquals(20_000L, PARTNER_GRACE_MS)
    }

    @Test fun aPartnerWhoNeverConnectsEndsTheChatAfter12s() = runTest {
        val w = watch()
        w.connection(true)
        wait(PARTNER_JOIN_TIMEOUT_MS - 1)
        assertEquals(0, gone)
        wait(1)
        assertEquals(1, gone)
        wait(60_000)
        assertEquals("once", 1, gone)
    }

    @Test fun presenceInTimeKeepsTheChat() = runTest {
        val w = watch()
        w.connection(true)
        wait(5_000)
        w.presence(true)
        wait(120_000)
        assertEquals(0, gone)
    }

    @Test fun aMessageOrTypingFromAnOlderAppCountsAsConnected() = runTest {
        val w = watch()
        w.connection(true)
        wait(11_000)
        w.activity()
        wait(120_000)
        assertEquals("older apps track no presence: never cut off", 0, gone)
    }

    @Test fun aPartnerWhoDropsHas20sToComeBack() = runTest {
        val w = watch()
        w.connection(true)
        w.presence(true)
        w.presence(false)
        wait(19_000)
        w.presence(true) // back in time (e.g. switched from Wi-Fi to mobile data)
        wait(60_000)
        assertEquals(0, gone)
        w.presence(false)
        w.activity() // a late message does not stop the grace timer
        wait(PARTNER_GRACE_MS)
        assertEquals(1, gone)
    }

    @Test fun myOwnDropPausesAndResetsTheTimers() = runTest {
        val w = watch()
        w.connection(true)
        wait(11_000)
        w.connection(false) // my socket dropped: no partner can be seen now
        wait(60_000)
        assertEquals("time offline does not count", 0, gone)
        w.connection(true)
        wait(PARTNER_JOIN_TIMEOUT_MS - 1)
        assertEquals("the join timer starts over", 0, gone)
        wait(1)
        assertEquals(1, gone)
    }

    @Test fun afterMyRejoinThePresenceSyncTellsWhoIsThere() = runTest {
        val w = watch()
        w.connection(true)
        w.presence(true)
        w.connection(false)
        wait(60_000)
        w.connection(true)
        w.presence(true) // the sync right after the rejoin lists the partner
        wait(120_000)
        assertEquals(0, gone)
        w.connection(false)
        w.connection(true) // this time the sync does not list them
        wait(PARTNER_GRACE_MS)
        assertEquals(1, gone)
    }

    @Test fun aPartnerLeavingWhileIAmOfflineIsJudgedAfterMyRejoin() = runTest {
        val w = watch()
        w.connection(true)
        w.presence(true)
        w.connection(false)
        w.presence(false) // stale leave while disconnected: ignored
        wait(PARTNER_GRACE_MS * 3)
        assertEquals(0, gone)
    }
}
