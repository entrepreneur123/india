package com.yaari.chat

import com.yaari.chat.data.BackendException
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.Products
import com.yaari.chat.data.VideoStatus
import com.yaari.chat.model.Gender
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.Target
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.Onboarding
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private val adult = ProfileDraft(name = "Dev", gender = Gender.MALE, day = "1", month = "1", year = "2000", agreed = true)

    private fun TestScope.signedIn(phone: String = "9876543210"): AppViewModel {
        val vm = vm()
        advanceUntilIdle()
        vm.requestOtp(phone)
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
        vm.requestOtp("12345")
        assertNotNull(vm.state.value.onboarding.error)
        vm.requestOtp("9876543210")
        advanceUntilIdle()
        assertEquals(Onboarding.Step.OTP, vm.state.value.onboarding.step)
        vm.verifyOtp("000000")
        advanceUntilIdle()
        assertEquals(UiText(R.string.err_otp_wrong), vm.state.value.onboarding.error)
    }

    @Test fun under18CannotRegister() = runTest(dispatcher) {
        val vm = vm()
        vm.requestOtp("9876543210"); advanceUntilIdle()
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
        again.requestOtp("+91 98765 43210"); advanceUntilIdle()
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
        vm.requestOtp("9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        assertEquals(Onboarding.Step.PROFILE, vm.state.value.onboarding.step)
    }

    @Test fun newPostAppearsOnMyProfile() = runTest(dispatcher) {
        val vm = signedIn()
        vm.addPost("First day ✨", null); advanceUntilIdle()
        assertEquals("First day ✨", vm.state.value.me!!.posts.single().text)
    }
}
