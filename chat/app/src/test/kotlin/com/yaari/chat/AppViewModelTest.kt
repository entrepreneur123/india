package com.yaari.chat

import com.yaari.chat.data.Backend
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.PartnerEvent
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Profile
import com.yaari.chat.model.Target
import com.yaari.chat.model.VideoState
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.Onboarding
import com.yaari.chat.ui.ProfileDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** Deterministic backend: partners always reply after 1 s, video is always accepted. */
private class ScriptedBackend : Backend {
    private val real = FakeBackend()
    var event: PartnerEvent = PartnerEvent.Reply("hi")
    override suspend fun sendOtp(phone: String) = real.sendOtp(phone)
    override suspend fun verifyOtp(phone: String, code: String) = real.verifyOtp(phone, code)
    override fun accountFor(phone: String) = real.accountFor(phone)
    override fun register(phone: String, profile: Profile) = real.register(phone, profile)
    override suspend fun findPartner(me: Profile, target: Target, exclude: Set<String>): Profile? {
        delay(500)
        return FakeBackend.POOL.firstOrNull { target.accepts(it.gender) && it.id !in exclude }
    }
    override suspend fun partnerReact(partner: Profile, incoming: String?, canLeave: Boolean): PartnerEvent {
        delay(1000)
        return event
    }
    override suspend fun answerVideoRequest(partner: Profile): Boolean {
        delay(1000)
        return true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var now = 0L
    private val backend = ScriptedBackend()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = AppViewModel(backend, clock = { now }, wallClock = { now }, today = { LocalDate.of(2026, 10, 18) })

    private val adult = ProfileDraft(name = "Dev", gender = Gender.MALE, day = "1", month = "1", year = "2000", agreed = true)

    private fun TestScope.signedIn(phone: String = "9876543210"): AppViewModel {
        val vm = vm()
        vm.requestOtp(phone)
        advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP)
        advanceUntilIdle()
        assertNull(vm.createProfile(adult))
        return vm
    }

    private fun TestScope.matched(vm: AppViewModel) {
        vm.startMatching()
        advanceUntilIdle()
        assertNotNull(vm.state.value.session)
    }

    @Test fun rejectsInvalidPhoneAndWrongOtp() = runTest(dispatcher) {
        val vm = vm()
        vm.requestOtp("12345")
        assertNotNull(vm.state.value.onboarding.error)
        vm.requestOtp("9876543210")
        advanceUntilIdle()
        assertEquals(Onboarding.Step.OTP, vm.state.value.onboarding.step)
        vm.verifyOtp("000000")
        advanceUntilIdle()
        assertEquals("Wrong code. Try again.", vm.state.value.onboarding.error)
    }

    @Test fun under18CannotRegister() = runTest(dispatcher) {
        val vm = vm()
        vm.requestOtp("9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        assertEquals("Yaari is only for adults (18+)", vm.createProfile(adult.copy(year = "2008", month = "10", day = "19")))
        assertNull(vm.state.value.me)
        assertNull(vm.createProfile(adult.copy(year = "2008", month = "10", day = "18")))
    }

    @Test fun samePhoneLogsIntoTheSameAccount() = runTest(dispatcher) {
        val first = signedIn().state.value.me!!
        val again = vm()
        again.requestOtp("+91 98765 43210"); advanceUntilIdle()
        again.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        assertEquals(first, again.state.value.me)
    }

    @Test fun genderTargetNeedsPremium() = runTest(dispatcher) {
        val vm = signedIn()
        vm.setTarget(Target.FEMALE)
        assertEquals(Target.ANY, vm.state.value.target)
        assertNotNull(vm.state.value.paywall)
        vm.setPremium(true)
        vm.setTarget(Target.FEMALE)
        matched(vm)
        assertEquals(Gender.FEMALE, vm.state.value.session!!.partner.gender)
    }

    @Test fun freeExitIsLockedForFiveSecondsThenOffersRecents() = runTest(dispatcher) {
        val vm = signedIn()
        matched(vm)
        now += 4_999
        vm.leave(next = false)
        assertNotNull("exit must stay locked before 5 s", vm.state.value.session)
        now += 1
        val partner = vm.state.value.session!!.partner
        vm.leave(next = false)
        assertNull(vm.state.value.session)
        assertEquals(partner.id, vm.state.value.recents.single().partner.id)
    }

    @Test fun premiumExitUnlocksAfter200ms() = runTest(dispatcher) {
        val vm = signedIn()
        vm.setPremium(true)
        matched(vm)
        now += 199
        vm.leave(next = true)
        assertNotNull(vm.state.value.session)
        now += 1
        vm.leave(next = true)
        assertTrue("next match starts searching", vm.state.value.searching)
        advanceUntilIdle()
        val next = vm.state.value.session!!.partner
        assertFalse("never rematched with the person just left", next.id == vm.state.value.recents.first().partner.id)
    }

    @Test fun reportAndLeaveIsInstantAndBlocks() = runTest(dispatcher) {
        val vm = signedIn()
        matched(vm)
        val partner = vm.state.value.session!!.partner
        vm.reportAndLeave("Nudity or sexual content", next = false)
        assertNull(vm.state.value.session)
        assertTrue(partner.id in vm.state.value.blocked)
        assertTrue(vm.state.value.recents.isEmpty())
    }

    @Test fun lateReplyDoesNotLeakIntoNextChat() = runTest(dispatcher) {
        val vm = signedIn()
        vm.setPremium(true)
        matched(vm)
        vm.sendText("hello")
        now += 200
        vm.leave(next = true) // before the 1 s reply arrives
        advanceUntilIdle()
        val session = vm.state.value.session!!
        assertTrue("new chat starts empty", session.messages.isEmpty())
    }

    @Test fun partnerReplyAndPartnerLeaving() = runTest(dispatcher) {
        val vm = signedIn()
        matched(vm)
        vm.sendText("hello")
        advanceTimeBy(500)
        assertTrue(vm.state.value.session!!.partnerTyping)
        advanceUntilIdle()
        assertEquals(listOf("hello", "hi"), vm.state.value.session!!.messages.map { it.text })
        backend.event = PartnerEvent.Left
        vm.sendText("still there?")
        advanceUntilIdle()
        assertTrue(vm.state.value.session!!.partnerLeft)
        assertEquals("no exit lock once the partner has gone", 0L, vm.exitRemainingMs())
    }

    @Test fun videoNeedsPremiumAndPartnerAcceptance() = runTest(dispatcher) {
        val vm = signedIn()
        matched(vm)
        vm.requestVideo()
        assertEquals(VideoState.NONE, vm.state.value.session!!.video)
        assertNotNull(vm.state.value.paywall)
        vm.setPremium(true)
        vm.requestVideo()
        assertEquals(VideoState.REQUESTING, vm.state.value.session!!.video)
        advanceUntilIdle()
        assertEquals(VideoState.IN_CALL, vm.state.value.session!!.video)
    }

    @Test fun dmWithRecentMatch() = runTest(dispatcher) {
        val vm = signedIn()
        matched(vm)
        now += 5_000
        val partner = vm.state.value.session!!.partner
        vm.leave(next = false)
        vm.openDm(partner.id)
        vm.sendDm("nice talking!")
        advanceUntilIdle()
        assertEquals(listOf("nice talking!", "hi"), vm.state.value.recents.single().dm.map { it.text })
    }
}
