package com.yaari.chat

import com.yaari.chat.data.Backend
import com.yaari.chat.model.Target as MatchTarget
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.Billing
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.Match
import com.yaari.chat.data.Products
import com.yaari.chat.data.VideoStatus
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Gender
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.Target
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.Onboarding
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
import java.time.LocalDate
import kotlin.random.Random

/** Makes the scripted partners deterministic: every "dice roll" out of 100 returns [roll], delays are minimal, picks are first. */
class ControlledRandom(var roll: Int = 50) : Random() {
    override fun nextBits(bitCount: Int) = 0
    override fun nextInt(until: Int) = if (until == 100) roll else 0
    override fun nextLong(from: Long, until: Long) = from
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var now = 1_000_000L
    private val dice = ControlledRandom()
    private val today = LocalDate.of(2026, 10, 18)
    private val backend = FakeBackend(dice, clock = { now }, today = { today })

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = AppViewModel(backend, DemoBilling(), clock = { now }, today = { today })

    private val india = Countries.byIso("IN")!!

    private val adult = ProfileDraft(name = "Dev", gender = Gender.MALE, day = "1", month = "1", year = "2000", agreed = true)

    private fun TestScope.signedIn(phone: String = "9876543210", make: () -> AppViewModel = { vm() }): AppViewModel {
        val vm = make()
        advanceUntilIdle()
        vm.requestOtp(india, phone)
        advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP)
        advanceUntilIdle()
        vm.createProfile(adult)
        advanceUntilIdle()
        assertNotNull(vm.state.value.me)
        return vm
    }

    private fun TestScope.matched(vm: AppViewModel) {
        vm.startMatching()
        advanceTimeBy(5_000)
        runCurrent()
        assertNotNull("should be matched", vm.state.value.chat)
    }

    @Test fun rejectsInvalidPhoneAndWrongOtp() = runTest(dispatcher) {
        val vm = vm()
        vm.requestOtp(india, "12345")
        assertNotNull(vm.state.value.onboarding.error)
        vm.requestOtp(india, "9876543210")
        advanceUntilIdle()
        assertEquals(Onboarding.Step.OTP, vm.state.value.onboarding.step)
        vm.verifyOtp("000000")
        advanceUntilIdle()
        assertEquals(UiText(R.string.err_otp_wrong), vm.state.value.onboarding.error)
    }

    @Test fun under18CannotRegister() = runTest(dispatcher) {
        val vm = vm()
        vm.requestOtp(india, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(adult.copy(year = "2008", month = "10", day = "19")); advanceUntilIdle()
        assertEquals(UiText(R.string.err_under_18), vm.state.value.onboarding.error)
        assertNull(vm.state.value.me)
        vm.createProfile(adult.copy(year = "2008", month = "10", day = "18")); advanceUntilIdle()
        assertNotNull("exactly 18 today is allowed", vm.state.value.me)
    }

    @Test fun samePhoneLogsBackIntoTheSameAccount() = runTest(dispatcher) {
        val first = signedIn().state.value.me!!
        backend.signOut()
        val again = vm()
        advanceUntilIdle()
        again.requestOtp(india, "+91 98765 43210"); advanceUntilIdle()
        again.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        assertEquals(first, again.state.value.me)
    }

    @Test fun appRestoresTheSignedInAccountOnStart() = runTest(dispatcher) {
        val first = signedIn().state.value.me!!
        val restarted = vm()
        advanceUntilIdle()
        assertEquals(first, restarted.state.value.me)
        assertFalse(restarted.state.value.booting)
    }

    @Test fun genderChoiceNeedsPremiumOrPass() = runTest(dispatcher) {
        val vm = signedIn()
        vm.setTarget(Target.FEMALE)
        assertEquals(Target.ANY, vm.state.value.target)
        assertNotNull(vm.state.value.paywall)
        // The server must refuse it too, whatever the app does.
        val refused = runCatching { backend.findMatch(Target.FEMALE) }.exceptionOrNull()
        assertEquals("premium_required", (refused as BackendException).code)

        backend.redeem(Products.GENDER_PASS, "t1")
        val withPass = vm() // entitlements are read from the server on start
        advanceUntilIdle()
        withPass.setTarget(Target.FEMALE)
        assertEquals(Target.FEMALE, withPass.state.value.target)
        assertFalse("a gender pass is not Premium", withPass.state.value.premium)
        matched(withPass)
        assertEquals(Gender.FEMALE, withPass.state.value.chat!!.match.partner.gender)
    }

    @Test fun freeExitIsLockedForFiveSeconds() = runTest(dispatcher) {
        val vm = signedIn()
        matched(vm)
        now += 4_999
        vm.leave(next = false); advanceUntilIdle()
        assertNotNull("exit must stay locked before 5 s", vm.state.value.chat)
        val locked = runCatching { backend.leave(vm.state.value.chat!!.match.id) }.exceptionOrNull()
        assertEquals("server enforces the lock too", "exit_locked", (locked as BackendException).code)
        now += 1
        val partner = vm.state.value.chat!!.match.partner
        vm.leave(next = false); advanceUntilIdle()
        assertNull(vm.state.value.chat)
        assertEquals(partner.id, vm.state.value.recents.single().partner.id)
    }

    @Test fun premiumExitUnlocksAfter200msAndNextSkipsLastPartner() = runTest(dispatcher) {
        backend.redeem(Products.PREMIUM, "p1")
        val vm = signedIn()
        assertTrue(vm.state.value.premium)
        matched(vm)
        val first = vm.state.value.chat!!.match.partner.id
        now += 199
        vm.leave(next = true); advanceUntilIdle()
        assertEquals(first, vm.state.value.chat!!.match.partner.id)
        now += 1
        vm.leave(next = true)
        advanceTimeBy(5_000); runCurrent()
        assertNotEquals("never rematched with the person just left", first, vm.state.value.chat!!.match.partner.id)
    }

    @Test fun reportAndLeaveIsInstantAndBlocks() = runTest(dispatcher) {
        val vm = signedIn()
        matched(vm)
        val partner = vm.state.value.chat!!.match.partner
        vm.reportAndLeave(ReportReason.NUDITY, next = false); advanceUntilIdle()
        assertNull("report works inside the exit lock", vm.state.value.chat)
        assertTrue(vm.state.value.recents.none { it.partner.id == partner.id })
        assertNull("blocked profile is hidden", backend.profile(partner.id))
    }

    @Test fun partnerRepliesThenLeaves() = runTest(dispatcher) {
        val vm = signedIn()
        matched(vm)
        vm.sendText("hello")
        advanceTimeBy(10); runCurrent()
        assertTrue(vm.state.value.chat!!.partnerTyping)
        advanceTimeBy(5_000); runCurrent()
        val texts = vm.state.value.chat!!.messages.map { it.text }
        assertEquals("hello", texts.first())
        assertEquals(2, texts.size)
        dice.roll = 0
        vm.sendText("still there?")
        advanceTimeBy(5_000); runCurrent()
        assertTrue(vm.state.value.chat!!.partnerLeft)
        assertEquals("no exit lock once the partner has gone", 0L, vm.exitRemainingMs())
    }

    @Test fun lateReplyDoesNotLeakIntoNextChat() = runTest(dispatcher) {
        backend.redeem(Products.PREMIUM, "p1")
        val vm = signedIn()
        matched(vm)
        vm.sendText("hello")
        now += 200
        advanceTimeBy(100); runCurrent()
        vm.leave(next = true) // before the reply arrives
        advanceTimeBy(5_000); runCurrent()
        assertTrue("new chat starts empty", vm.state.value.chat!!.messages.isEmpty())
    }

    @Test fun videoNeedsPremiumAndAcceptance() = runTest(dispatcher) {
        val vm = signedIn()
        matched(vm)
        vm.requestVideo(); advanceUntilIdle()
        assertEquals(VideoStatus.NONE, vm.state.value.chat!!.video)
        assertNotNull(vm.state.value.paywall)
        assertEquals("premium_required", (runCatching { backend.requestVideo(vm.state.value.chat!!.match.id) }.exceptionOrNull() as BackendException).code)
    }

    @Test fun premiumVideoCallIsAccepted() = runTest(dispatcher) {
        backend.redeem(Products.PREMIUM, "p1")
        val vm = signedIn()
        matched(vm)
        vm.requestVideo()
        advanceTimeBy(100); runCurrent()
        assertEquals(VideoStatus.REQUESTED_BY_ME, vm.state.value.chat!!.video)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(VideoStatus.ACCEPTED, vm.state.value.chat!!.video)
        vm.endVideo(); advanceTimeBy(100); runCurrent()
        assertEquals(VideoStatus.NONE, vm.state.value.chat!!.video)
    }

    @Test fun incomingVideoCallCanBeAccepted() = runTest(dispatcher) {
        val vm = signedIn() // free users can accept; only asking needs Premium
        matched(vm)
        vm.sendText("can we do a video call?")
        advanceTimeBy(5_000); runCurrent()
        assertEquals(VideoStatus.REQUESTED_BY_PARTNER, vm.state.value.chat!!.video)
        vm.answerVideo(true); advanceTimeBy(100); runCurrent()
        assertEquals(VideoStatus.ACCEPTED, vm.state.value.chat!!.video)
    }

    @Test fun messageSomeoneFromRecent() = runTest(dispatcher) {
        val vm = signedIn()
        matched(vm)
        now += 5_000
        val partner = vm.state.value.chat!!.match.partner
        vm.leave(next = false); advanceUntilIdle()
        vm.openDm(partner.id)
        advanceTimeBy(100); runCurrent()
        vm.sendDm("nice talking!")
        advanceTimeBy(5_000); runCurrent()
        val texts = vm.state.value.dm!!.messages.map { it.text }
        assertEquals("nice talking!", texts.first())
        assertEquals(2, texts.size)
    }

    @Test fun deletingTheAccountStartsOver() = runTest(dispatcher) {
        val vm = signedIn()
        matched(vm)
        now += 5_000
        vm.deleteAccount(); advanceUntilIdle()
        assertNull(vm.state.value.me)
        assertNull(vm.state.value.chat)
        assertEquals(Onboarding.Step.PHONE, vm.state.value.onboarding.step)
        // The same number can sign up again, as a brand-new account.
        vm.requestOtp(india, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        assertEquals(Onboarding.Step.PROFILE, vm.state.value.onboarding.step)
    }

    @Test fun cancellingWhileTheRequestIsInFlightEndsThatMatch() = runTest(dispatcher) {
        val vm = signedIn()
        vm.startMatching()
        advanceTimeBy(100); runCurrent() // the server is still answering (900 ms)
        vm.cancelMatching()
        advanceTimeBy(1_000); runCurrent() // the server answers with a match nobody will see
        now += 6_000 // the exit lock has passed by the time abandon() runs
        advanceTimeBy(10_000); runCurrent()
        assertNull("no chat was opened", vm.state.value.chat)
        assertFalse(vm.state.value.searching)
        // The abandoned match was ended, so searching again works (no "exit_locked").
        vm.startMatching()
        advanceTimeBy(5_000); runCurrent()
        assertNotNull(vm.state.value.chat)
        assertNull(vm.state.value.notice)
    }

    @Test fun restartingTheSearchKeepsOnlyOneLoop() = runTest(dispatcher) {
        val vm = signedIn()
        vm.startMatching()
        advanceTimeBy(100); runCurrent()
        vm.cancelMatching()
        vm.startMatching() // second search while the first request is still in flight
        now += 6_000
        advanceTimeBy(10_000); runCurrent()
        assertNotNull("the newest search opened a chat", vm.state.value.chat)
        assertFalse(vm.state.value.searching)
    }

    @Test fun leavingAChatThatNoLongerExistsStillCloses() = runTest(dispatcher) {
        val gone = object : Backend by backend {
            override suspend fun leave(matchId: String) = throw BackendException("not_found")
            override suspend fun report(partnerId: String, reason: ReportReason, matchId: String?) = throw BackendException("not_found")
        }
        val vm = AppViewModel(gone, DemoBilling(), clock = { now }, today = { today })
        advanceUntilIdle()
        vm.requestOtp(india, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(adult); advanceUntilIdle()
        matched(vm)
        now += 5_000
        vm.leave(next = false); advanceUntilIdle()
        assertNull(vm.state.value.chat)
        matched(vm)
        vm.reportAndLeave(ReportReason.HARASSMENT, next = false); advanceUntilIdle()
        assertNull(vm.state.value.chat)
    }

    @Test fun purchasesCompletedLaterAreRedeemedOnceAndConsumed() = runTest(dispatcher) {
        val finished = mutableListOf<String>()
        val billing = object : Billing {
            override suspend fun prices() = emptyMap<String, String>()
            override suspend fun buy(activity: android.app.Activity, productId: String, accountId: String): String? = null
            override suspend fun finish(productId: String, token: String) { finished += token }
            override suspend fun ownedPurchases() = listOf(Products.GENDER_PASS to "upi-pending-token")
        }
        val vm = AppViewModel(backend, billing, clock = { now }, today = { today })
        advanceUntilIdle()
        vm.requestOtp(india, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(adult); advanceUntilIdle()
        assertTrue("pass granted at sign-in", vm.state.value.entitlements.genderPass)
        vm.restorePurchases(); advanceUntilIdle() // e.g. the app comes back to the foreground
        assertEquals("redeemed and consumed exactly once", listOf("upi-pending-token"), finished)
    }

    @Test fun aMatchHandedBackToTheNewSearchIsNotEnded() = runTest(dispatcher) {
        // The server returns the same open match to the old (cancelled) request and to the new search.
        val partner = FakeBackend.POOL.first()
        val left = mutableListOf<String>()
        val sameMatch = object : Backend by backend {
            override suspend fun findMatch(target: Target): Match {
                delay(900)
                return Match("M", partner)
            }
            override fun events(matchId: String, live: Boolean, afterId: Long?) = kotlinx.coroutines.flow.emptyFlow<com.yaari.chat.data.MatchEvent>()
            override suspend fun history(matchId: String, afterId: Long?) = emptyList<com.yaari.chat.model.Message>()
            override suspend fun leave(matchId: String) { left += matchId }
        }
        val vm = AppViewModel(sameMatch, DemoBilling(), clock = { now }, today = { today })
        advanceUntilIdle()
        vm.requestOtp(india, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(adult); advanceUntilIdle()
        vm.startMatching()
        advanceTimeBy(100); runCurrent()
        vm.cancelMatching()
        vm.startMatching()
        advanceTimeBy(10_000); runCurrent()
        assertEquals("M", vm.state.value.chat?.match?.id)
        assertTrue("the chat on screen must not be ended by the old search", left.isEmpty())
    }

    @Test fun anOldSearchErrorDoesNotStopTheNewSearch() = runTest(dispatcher) {
        var calls = 0
        val flaky = object : Backend by backend {
            override suspend fun findMatch(target: Target): Match? {
                delay(900)
                if (++calls == 1) throw BackendException("network")
                return backend.findMatch(target)
            }
        }
        val vm = AppViewModel(flaky, DemoBilling(), clock = { now }, today = { today })
        advanceUntilIdle()
        vm.requestOtp(india, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(adult); advanceUntilIdle()
        vm.startMatching()
        advanceTimeBy(100); runCurrent()
        vm.cancelMatching()
        vm.startMatching() // the first request fails after this
        advanceTimeBy(10_000); runCurrent()
        assertNotNull("the new search still found someone", vm.state.value.chat)
        assertNull("no error from the old search", vm.state.value.notice)
    }

    @Test fun newPostAppearsOnMyProfile() = runTest(dispatcher) {
        val vm = signedIn()
        vm.addPost("First day ✨", null); advanceUntilIdle()
        assertEquals("First day ✨", vm.state.value.me!!.posts.single().text)
    }

    // ---- Global sign-up, friends, rooms, profile editing ----

    @Test fun signsUpFromAnotherCountryWithItsFlag() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.requestOtp(Countries.byIso("KR")!!, "010-1234-5678"); advanceUntilIdle()
        assertEquals("821012345678", vm.state.value.onboarding.phone)
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(adult); advanceUntilIdle()
        assertEquals("KR", vm.state.value.me!!.country)
    }

    @Test fun addFriendFromRecentThenMessageThem() = runTest(dispatcher) {
        val vm = signedIn()
        matched(vm)
        val partner = vm.state.value.chat!!.match.partner
        now += 5_000
        vm.leave(next = false); advanceUntilIdle()
        vm.addFriend(partner); advanceUntilIdle()
        assertEquals(FriendState.REQUESTED, vm.state.value.friendState(partner.id))
        vm.selectTab(com.yaari.chat.ui.Tab.RECENT); advanceUntilIdle() // the scripted partner accepts
        assertEquals(FriendState.FRIENDS, vm.state.value.friendState(partner.id))
        vm.openDm(partner.id); advanceUntilIdle()
        assertEquals(partner.id, vm.state.value.dm!!.recent.partner.id)
    }

    @Test fun incomingRequestCanBeAcceptedOrDeclined() = runTest(dispatcher) {
        val vm = signedIn()
        val asking = vm.state.value.friends.single { it.state == FriendState.INCOMING }.profile
        assertEquals(1, vm.state.value.incomingRequests)
        vm.answerFriend(asking, accept = true); advanceUntilIdle()
        assertEquals(FriendState.FRIENDS, vm.state.value.friendState(asking.id))
        // A friend never met in random chat gets a private thread, which stays out of Recent.
        vm.openDm(asking.id); advanceUntilIdle()
        assertNotNull(vm.state.value.dm)
        vm.closeDm(); advanceUntilIdle()
        assertTrue(vm.state.value.recents.none { it.partner.id == asking.id })
    }

    @Test fun strangersCannotBeAdded() = runTest(dispatcher) {
        val vm = signedIn()
        vm.addFriend(FakeBackend.POOL.last()); advanceUntilIdle()
        assertEquals(UiText(R.string.err_not_allowed), vm.state.value.notice)
        assertEquals(FriendState.NONE, vm.state.value.friendState(FakeBackend.POOL.last().id))
    }

    @Test fun reportingAFriendRemovesThem() = runTest(dispatcher) {
        val vm = signedIn()
        val asking = vm.state.value.friends.single().profile
        vm.report(asking.id, ReportReason.HARASSMENT); advanceUntilIdle()
        assertTrue(vm.state.value.friends.isEmpty())
        vm.selectTab(com.yaari.chat.ui.Tab.RECENT); advanceUntilIdle()
        assertTrue("still gone after a refresh", vm.state.value.friends.isEmpty())
    }

    @Test fun roomsChatReportAndLeave() = runTest(dispatcher) {
        val vm = signedIn()
        vm.selectTab(com.yaari.chat.ui.Tab.ROOMS); advanceUntilIdle()
        val lounge = vm.state.value.rooms.first()
        vm.enterRoom(lounge)
        advanceTimeBy(100); runCurrent() // rooms never go idle (presence beat, chatter): step the clock instead
        val history = vm.state.value.room!!.messages
        assertTrue(history.isNotEmpty())
        vm.sendRoom("Hi everyone?")
        advanceTimeBy(3_000); runCurrent()
        val msgs = vm.state.value.room!!.messages
        assertTrue(msgs.any { it.fromMe && it.text == "Hi everyone?" })
        assertTrue("someone answered", msgs.last().let { !it.fromMe && it.id > msgs.first { m -> m.fromMe }.id })
        // People in the room can be seen, added and reported
        val someone = history.first().sender
        vm.openProfile(someone); advanceTimeBy(100); runCurrent()
        assertNotNull(vm.state.value.viewing)
        vm.report(someone.id, ReportReason.HARASSMENT); advanceTimeBy(100); runCurrent()
        assertTrue(vm.state.value.room!!.messages.none { it.sender.id == someone.id })
        vm.leaveRoom(); advanceTimeBy(100); runCurrent()
        assertNull(vm.state.value.room)
    }

    @Test fun editProfileKeepsGenderAndAge() = runTest(dispatcher) {
        val vm = signedIn()
        val before = vm.state.value.me!!
        var closed = false
        vm.saveProfile("  ", "", "", null) { closed = true }; advanceUntilIdle()
        assertEquals(UiText(R.string.err_name), vm.state.value.notice)
        assertFalse(closed)
        vm.saveProfile("Devraj", "Pune", "Hello world", null) { closed = true }; advanceUntilIdle()
        val after = vm.state.value.me!!
        assertTrue(closed)
        assertEquals("Devraj" to "Hello world", after.name to after.bio)
        assertEquals(before.gender to before.age, after.gender to after.age)
    }

    @Test fun roomMessageAfterLapsedPresenceRejoinsAndSends() = runTest(dispatcher) {
        var joins = 0
        var refusedOnce = false
        val lapsing = object : Backend by backend {
            override suspend fun joinRoom(roomId: String) { joins++; backend.joinRoom(roomId) }
            override suspend fun sendRoom(roomId: String, text: String) =
                if (!refusedOnce) { refusedOnce = true; throw BackendException("blocked") } else backend.sendRoom(roomId, text)
        }
        val vm = AppViewModel(lapsing, DemoBilling(), clock = { now }, today = { today })
        advanceUntilIdle()
        vm.requestOtp(india, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(adult); advanceUntilIdle()
        vm.selectTab(com.yaari.chat.ui.Tab.ROOMS); advanceUntilIdle()
        vm.enterRoom(vm.state.value.rooms.first()); advanceTimeBy(100); runCurrent()
        vm.sendRoom("still here"); advanceTimeBy(100); runCurrent()
        assertTrue(vm.state.value.room!!.messages.any { it.fromMe && it.text == "still here" })
        assertEquals(2, joins)
        assertNull(vm.state.value.notice)
        vm.leaveRoom(); advanceTimeBy(100); runCurrent()
    }

    @Test fun roomThatCannotBeJoinedClosesAgain() = runTest(dispatcher) {
        val banned = object : Backend by backend {
            override suspend fun joinRoom(roomId: String) = throw BackendException("banned")
        }
        val vm = AppViewModel(banned, DemoBilling(), clock = { now }, today = { today })
        advanceUntilIdle()
        vm.requestOtp(india, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(adult); advanceUntilIdle()
        vm.selectTab(com.yaari.chat.ui.Tab.ROOMS); advanceUntilIdle()
        vm.enterRoom(vm.state.value.rooms.first()); advanceUntilIdle()
        assertNull(vm.state.value.room)
        assertEquals(UiText(R.string.err_banned), vm.state.value.notice)
    }
    // ---- Block (without reporting) --------------------------------------------------------------

    /** Records which of block / report reached the server. */
    private val calls = mutableListOf<String>()
    private val spy = object : Backend by backend {
        override suspend fun block(userId: String) { calls += "block"; backend.block(userId) }
        override suspend fun report(partnerId: String, reason: ReportReason, matchId: String?) { calls += "report"; backend.report(partnerId, reason, matchId) }
    }

    @Test fun blockingInARandomChatEndsItWithoutAReport() = runTest(dispatcher) {
        val vm = signedIn { AppViewModel(spy, DemoBilling(), clock = { now }, today = { today }) }
        matched(vm)
        val partner = vm.state.value.chat!!.match.partner
        vm.block(partner); advanceUntilIdle()
        assertNull("block works inside the exit lock and closes the chat", vm.state.value.chat)
        assertEquals(listOf("block"), calls)
        assertTrue(vm.state.value.recents.none { it.partner.id == partner.id })
        assertNull("blocked profile is hidden", backend.profile(partner.id))
        assertEquals(UiText(R.string.msg_blocked, partner.name), vm.state.value.notice)
    }

    @Test fun blockingFromRecentClosesTheThreadAndRemovesTheRow() = runTest(dispatcher) {
        val vm = signedIn()
        matched(vm)
        now += 5_000
        val partner = vm.state.value.chat!!.match.partner
        vm.leave(next = false); advanceUntilIdle()
        vm.openDm(partner.id); advanceTimeBy(100); runCurrent()
        vm.openProfile(partner); advanceTimeBy(100); runCurrent()
        vm.block(partner); advanceTimeBy(100); runCurrent()
        assertNull(vm.state.value.dm)
        assertNull(vm.state.value.viewing)
        assertTrue(vm.state.value.recents.none { it.partner.id == partner.id })
        vm.refreshRecents(); advanceUntilIdle()
        assertTrue("still gone after a refresh", vm.state.value.recents.none { it.partner.id == partner.id })
    }

    @Test fun blockingAFriendRemovesThem() = runTest(dispatcher) {
        val vm = signedIn { AppViewModel(spy, DemoBilling(), clock = { now }, today = { today }) }
        val friend = vm.state.value.friends.single().profile
        vm.block(friend); advanceUntilIdle()
        assertTrue(vm.state.value.friends.isEmpty())
        vm.selectTab(com.yaari.chat.ui.Tab.RECENT); advanceUntilIdle()
        assertTrue("still gone after a refresh", vm.state.value.friends.isEmpty())
        assertEquals(listOf("block"), calls)
    }

    // ---- Background: polling stops while the app is not on screen ---------------------------------

    @Test fun matchingPollsOnlyWhileTheAppIsOnScreen() = runTest(dispatcher) {
        val visible = MutableStateFlow(true)
        var polls = 0
        val counting = object : Backend by backend {
            override suspend fun findMatch(target: MatchTarget): Match? { polls++; return null }
        }
        val vm = signedIn { AppViewModel(counting, DemoBilling(), clock = { now }, today = { today }, foreground = visible) }
        vm.startMatching()
        advanceTimeBy(5_000); runCurrent()
        val onScreen = polls
        assertTrue("polls every 2 s on screen", onScreen >= 2)
        visible.value = false
        advanceTimeBy(2_100); runCurrent() // a poll already waiting on its delay may still run once
        val atPause = polls
        advanceTimeBy(60_000); runCurrent()
        assertEquals("no requests for a minute in the background", atPause, polls)
        assertTrue("the search itself is kept", vm.state.value.searching)
        visible.value = true
        runCurrent()
        assertEquals("back on screen: polls again at once", atPause + 1, polls)
        vm.cancelMatching(); advanceTimeBy(3_000); runCurrent()
    }

    @Test fun roomHeartbeatPausesInTheBackgroundAndBeatsOnReturn() = runTest(dispatcher) {
        val visible = MutableStateFlow(true)
        var beats = 0
        val counting = object : Backend by backend {
            override suspend fun joinRoom(roomId: String) { beats++; backend.joinRoom(roomId) }
        }
        val vm = signedIn { AppViewModel(counting, DemoBilling(), clock = { now }, today = { today }, foreground = visible) }
        vm.selectTab(com.yaari.chat.ui.Tab.ROOMS); advanceUntilIdle()
        vm.enterRoom(vm.state.value.rooms.first())
        advanceTimeBy(100); runCurrent()
        assertEquals("joined once", 1, beats)
        advanceTimeBy(AppViewModel.ROOM_BEAT_MS); runCurrent()
        assertEquals("one beat after 30 s", 2, beats)
        visible.value = false
        advanceTimeBy(AppViewModel.ROOM_BEAT_MS * 4); runCurrent()
        assertEquals("no beats in the background", 2, beats)
        visible.value = true
        runCurrent()
        assertEquals("beats at once when back", 3, beats)
        vm.leaveRoom(); advanceTimeBy(100); runCurrent()
    }
}
