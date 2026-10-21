package com.yaari.chat

import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.Match
import com.yaari.chat.data.MatchEvent
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Media
import com.yaari.chat.model.Message
import com.yaari.chat.model.RecentMatch
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.VoiceLine
import com.yaari.chat.model.Target
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/**
 * Weak networks and quick navigation: the search survives failed requests, match signals, failed sends,
 * partner disconnects reported by the backend, and fast taps between screens. Virtual time (the ViewModel's clock is
 * the test scheduler's), so a minute of failures runs in milliseconds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeakNetworkTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 18)
    private val dice = ControlledRandom()
    private val fake = FakeBackend(dice, clock = { dispatcher.scheduler.currentTime + 1_000_000L }, today = { today })

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.signedIn(backend: Backend): AppViewModel {
        val vm = AppViewModel(backend, DemoBilling(), clock = { currentTime }, today = { today })
        advanceUntilIdle()
        vm.requestOtp(Countries.byIso("IN")!!, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(ProfileDraft(name = "Dev", gender = Gender.MALE, day = "1", month = "1", year = "2000", agreed = true))
        advanceUntilIdle()
        assertNotNull(vm.state.value.me)
        return vm
    }

    private fun TestScope.step(ms: Long = 100) {
        advanceTimeBy(ms); runCurrent()
    }

    /** Every attempt fails like a dropped or timed-out request; [attempts] records when (virtual ms). */
    private class Offline(base: Backend, private val error: () -> Exception = { IOException("timeout") }) : Backend by base {
        val attempts = mutableListOf<Long>()
        var now: () -> Long = { 0 }
        var failing = true
        override suspend fun findMatch(target: Target): Match? {
            attempts += now()
            if (failing) throw error()
            return null
        }
    }

    // ---- 1. Transient errors keep the search going --------------------------------------------------------------

    @Test fun transientErrorsKeepSearchingWithBackoffThenStopAfterAMinute() = runTest(dispatcher) {
        val offline = Offline(fake)
        offline.now = { currentTime }
        val vm = signedIn(offline)
        val start = currentTime
        vm.startMatching()
        step(10)
        assertTrue(vm.state.value.searching)
        assertTrue("shows reconnecting while the last attempt failed", vm.state.value.reconnecting)
        step(59_000)
        assertTrue("still searching after 59 s of failures", vm.state.value.searching)
        assertNull("no error yet", vm.state.value.notice)
        // Backoff 2 s, 4 s, 8 s, then every 10 s.
        assertEquals(listOf(0L, 2_000, 6_000, 14_000, 24_000, 34_000, 44_000, 54_000), offline.attempts.map { it - start })
        step(6_000) // the attempt at 64 s fails too: more than a minute of nothing but failures
        assertFalse("stops after 60 s of continuous failure", vm.state.value.searching)
        assertFalse(vm.state.value.reconnecting)
        assertEquals(UiText(R.string.err_network), vm.state.value.notice)
        val count = offline.attempts.size
        step(30_000)
        assertEquals("no more attempts once stopped", count, offline.attempts.size)
    }

    @Test fun aSearchThatRecoversFindsSomeoneAndClearsReconnecting() = runTest(dispatcher) {
        var calls = 0
        val flaky = object : Backend by fake {
            override suspend fun findMatch(target: Target): Match? {
                if (++calls <= 3) throw BackendException("network") // a 5xx or timeout, mapped by the live backend
                return fake.findMatch(target)
            }
        }
        val vm = signedIn(flaky)
        vm.startMatching()
        step(10)
        assertTrue(vm.state.value.reconnecting)
        step(2_000 + 4_000 + 100) // two more failures
        assertTrue(vm.state.value.reconnecting)
        step(8_000 + 3_000) // the fourth attempt answers, the fake pairs within 3 s
        assertNotNull("matched once the network came back", vm.state.value.chat)
        assertFalse(vm.state.value.reconnecting)
        assertNull(vm.state.value.notice)
    }

    @Test fun ruleErrorsStopTheSearchAtOnce() = runTest(dispatcher) {
        for ((code, text) in listOf("banned" to R.string.err_banned, "premium_required" to R.string.err_premium, "not_signed_in" to R.string.err_not_signed_in)) {
            val refused = Offline(fake) { BackendException(code) }
            val vm = signedIn(refused)
            vm.startMatching()
            step(10)
            assertFalse("$code stops at once", vm.state.value.searching)
            assertFalse(vm.state.value.reconnecting)
            assertEquals(UiText(text), vm.state.value.notice)
            step(30_000)
            assertEquals("$code: one attempt only", 1, refused.attempts.size)
        }
    }

    @Test fun exitLockedKeepsWaitingWithoutReconnecting() = runTest(dispatcher) {
        val locked = Offline(fake) { BackendException("exit_locked") }
        val vm = signedIn(locked)
        vm.startMatching()
        step(10_000)
        assertTrue(vm.state.value.searching)
        assertFalse("the server answered: not a network problem", vm.state.value.reconnecting)
        assertTrue("polls every 2 s", locked.attempts.size >= 5)
        vm.cancelMatching(); step()
    }

    // ---- 2. Instant match for the waiting side ------------------------------------------------------------------

    @Test fun aMatchSignalPollsAtOnce() = runTest(dispatcher) {
        val signals = MutableSharedFlow<Unit>()
        val polls = mutableListOf<Long>()
        val waiting = object : Backend by fake {
            override suspend fun findMatch(target: Target): Match? { polls += currentTime; return null }
            override fun matchSignals(): Flow<Unit> = signals
        }
        val vm = signedIn(waiting)
        val start = currentTime
        vm.startMatching()
        step(500)
        assertEquals(1, polls.size)
        assertEquals("listening while searching", 1, signals.subscriptionCount.value)
        signals.emit(Unit)
        runCurrent()
        assertEquals("polled at once, not after the 2 s delay", listOf(0L, 500L), polls.map { it - start })
        step(2_100)
        assertEquals("then every 2 s again", 3, polls.size)
        vm.cancelMatching(); step(2_100)
        assertEquals("stops listening when the search ends", 0, signals.subscriptionCount.value)
    }

    // ---- 3. Partner gone (from the backend's presence rules) ------------------------------------------------------

    @Test fun partnerLeftFromTheBackendShowsTheEndedChat() = runTest(dispatcher) {
        val flags = mutableListOf<Boolean>()
        val dropping = object : Backend by fake {
            override fun events(matchId: String, live: Boolean, afterId: Long?): Flow<MatchEvent> {
                flags += live
                return flow {
                    delay(12_000) // e.g. the partner never connected
                    emit(MatchEvent.PartnerLeft)
                }
            }
        }
        val vm = signedIn(dropping)
        vm.startMatching()
        step(3_000)
        assertNotNull(vm.state.value.chat)
        assertEquals("a random chat is watched as live", listOf(true), flags)
        assertFalse(vm.state.value.chat!!.partnerLeft)
        step(12_000)
        val chat = vm.state.value.chat!!
        assertTrue("the chat shows as ended", chat.partnerLeft)
        assertEquals("no exit lock once the partner has gone", 0L, vm.exitRemainingMs())
        vm.leave(next = false); step()
        assertNull(vm.state.value.chat)
    }

    @Test fun friendThreadsAreNotWatchedAsLive() = runTest(dispatcher) {
        val flags = mutableListOf<Boolean>()
        val spy = object : Backend by fake {
            override fun events(matchId: String, live: Boolean, afterId: Long?): Flow<MatchEvent> {
                flags += live
                return emptyFlow()
            }
        }
        val vm = signedIn(spy)
        val asking = vm.state.value.friends.single().profile
        vm.answerFriend(asking, accept = true); advanceUntilIdle()
        vm.openDm(asking.id); advanceUntilIdle()
        assertNotNull(vm.state.value.dm)
        assertEquals(listOf(false), flags)
    }

    // ---- 4. Incremental re-sync ---------------------------------------------------------------------------------

    @Test fun theChatLoadsHistoryOnceAndHandsTheNewestIdToTheResync() = runTest(dispatcher) {
        val loads = mutableListOf<Long?>()
        val after = mutableListOf<Long?>()
        val spy = object : Backend by fake {
            override suspend fun history(matchId: String, afterId: Long?): List<Message> {
                loads += afterId
                return listOf(Message(41, false, "hi", null, 0), Message(42, false, "there?", null, 0))
            }
            override fun events(matchId: String, live: Boolean, afterId: Long?): Flow<MatchEvent> {
                after += afterId
                return emptyFlow()
            }
        }
        val vm = signedIn(spy)
        vm.startMatching()
        step(3_000)
        assertEquals("one full load", listOf<Long?>(null), loads)
        assertEquals(listOf<Long?>(42), after)
        assertEquals(listOf(41L, 42L), vm.state.value.chat!!.messages.map { it.id })
    }

    // ---- 6. A failed send keeps the text ------------------------------------------------------------------------

    @Test fun aFailedSendPutsTheTextBack() = runTest(dispatcher) {
        var down = true
        val flaky = object : Backend by fake {
            override suspend fun send(matchId: String, text: String?, media: Media?): Message =
                if (down) throw IOException("timeout") else fake.send(matchId, text, media)
            override suspend fun sendVoice(roomId: String, text: String, replyTo: Long?): VoiceLine =
                if (down) throw BackendException("network") else fake.sendVoice(roomId, text, replyTo)
        }
        val vm = signedIn(flaky)
        vm.startMatching(); step(3_000)
        var restored = 0
        vm.sendText("hello?") { restored++ }
        step()
        assertEquals("the composer gets the text back", 1, restored)
        assertEquals(UiText(R.string.err_network), vm.state.value.notice)
        assertTrue(vm.state.value.chat!!.messages.isEmpty())
        down = false
        vm.sendText("hello?") { restored++ }
        step()
        assertEquals("a send that worked does not restore", 1, restored)
        assertEquals("hello?", vm.state.value.chat!!.messages.single().text)

        // Rooms: the same.
        vm.reportAndLeave(ReportReason.HARASSMENT, next = false); step()
        down = true
        vm.selectTab(Tab.ROOMS); step()
        vm.voice.open("v-chai"); step(1_000)
        var roomRestored = false
        vm.voice.send("anyone here?") { roomRestored = true }
        step()
        assertTrue(roomRestored)
        val lines = vm.voice.state.value?.lines.orEmpty()
        vm.voice.close(); step()
        assertTrue(lines.none { it is VoiceLine.Text && it.fromMe })
    }

    // ---- 8. Rapid navigation ------------------------------------------------------------------------------------

    @Test fun doubleTapOnNextLeavesOnceAndSearchesOnce() = runTest(dispatcher) {
        val leaves = mutableListOf<String>()
        var searches = 0
        val spy = object : Backend by fake {
            override suspend fun leave(matchId: String) { delay(300); leaves += matchId; fake.leave(matchId) }
            override suspend fun findMatch(target: Target): Match? { searches++; return fake.findMatch(target) }
        }
        val vm = signedIn(spy)
        vm.startMatching(); step(3_000)
        val first = vm.state.value.chat!!.match.id
        step(5_000) // past the exit lock
        searches = 0
        vm.leave(next = true)
        vm.leave(next = true) // second tap while the first leave is on its way
        step(400)
        assertEquals(listOf(first), leaves)
        assertTrue(vm.state.value.searching)
        step(3_000)
        assertNotEquals(first, vm.state.value.chat!!.match.id)
        assertEquals("one search loop", 1, searches)
    }

    @Test fun aLateLeaveAnswerDoesNotCloseTheNextChat() = runTest(dispatcher) {
        val spy = object : Backend by fake {
            override suspend fun leave(matchId: String) { delay(5_000); fake.leave(matchId) } // very slow network
        }
        val vm = signedIn(spy)
        vm.startMatching(); step(3_000)
        val first = vm.state.value.chat!!.match.id
        step(5_000)
        vm.leave(next = true) // slow
        step(100)
        vm.reportAndLeave(ReportReason.HARASSMENT, next = true) // then report: instant, opens the next search
        step(3_000)
        val second = vm.state.value.chat!!.match.id
        assertNotEquals(first, second)
        step(5_000) // the slow leave finally answers
        assertEquals("the new chat stays open", second, vm.state.value.chat?.match?.id)
        assertFalse("and no second search starts", vm.state.value.searching)
    }

    @Test fun aFriendThreadLookedUpDuringASearchDoesNotPopUp() = runTest(dispatcher) {
        val slow = object : Backend by fake {
            override suspend fun friendThread(userId: String): String { delay(2_000); return fake.friendThread(userId) }
        }
        val vm = signedIn(slow)
        val friend = vm.state.value.friends.single().profile
        vm.answerFriend(friend, accept = true); advanceUntilIdle()
        vm.openDm(friend.id) // the thread lookup is slow...
        step()
        vm.startMatching() // ...and the user starts searching meanwhile
        step(2_500)
        assertNull("no thread opened over the search or chat", vm.state.value.dm)
        assertTrue(vm.state.value.searching || vm.state.value.chat != null)
    }

    @Test fun quickTabSwitchingKeepsTheNewestRecents() = runTest(dispatcher) {
        var calls = 0
        val slowFirst = object : Backend by fake {
            override suspend fun recents(): List<RecentMatch> {
                val n = ++calls
                delay(if (n == 1) 3_000 else 100)
                return if (n == 1) listOf(RecentMatch("old", FakeBackend.POOL[0], 0)) else emptyList()
            }
        }
        val vm = signedIn(slowFirst)
        vm.selectTab(Tab.RECENT)
        step(10)
        vm.selectTab(Tab.ROOMS)
        vm.selectTab(Tab.RECENT)
        step(5_000)
        assertTrue("the older, slower answer does not overwrite the newer one", vm.state.value.recents.isEmpty())
    }

    @Test fun cancellingTwiceOrWhenNotSearchingIsHarmless() = runTest(dispatcher) {
        var cancels = 0
        val spy = object : Backend by fake {
            override suspend fun cancelSearch() { cancels++; throw IOException("offline") }
        }
        val vm = signedIn(spy)
        vm.cancelMatching(); step()
        assertEquals("nothing to cancel", 0, cancels)
        vm.startMatching(); step(10)
        vm.cancelMatching(); vm.cancelMatching() // back press and Cancel at once
        step(3_000)
        assertFalse(vm.state.value.searching)
        assertNull("an offline cancel shows no error", vm.state.value.notice)
        assertNull(vm.state.value.chat)
    }

    // ---- language for matching --------------------------------------------------------------------------------

    @Test fun matchLanguageIsTheAppsOrThePhonesOrEnglish() {
        assertEquals("hi", AppLocale.matchLanguageOf("hi", java.util.Locale.US))
        assertEquals("pt-br", AppLocale.matchLanguageOf("pt-BR", java.util.Locale.US))
        assertEquals("vi", AppLocale.matchLanguageOf("", java.util.Locale.forLanguageTag("vi-VN")))
        assertEquals("bn", AppLocale.matchLanguageOf("", java.util.Locale.forLanguageTag("bn-BD")))
        assertEquals("pt-br", AppLocale.matchLanguageOf("", java.util.Locale.forLanguageTag("pt-BR")))
        assertEquals("en", AppLocale.matchLanguageOf("", java.util.Locale.forLanguageTag("ta-IN")))
        assertEquals("en", AppLocale.matchLanguageOf("", null))
    }
}
