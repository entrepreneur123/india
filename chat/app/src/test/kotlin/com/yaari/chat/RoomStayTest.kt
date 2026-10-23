package com.yaari.chat

import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.model.GenderRule
import com.yaari.chat.model.RoomRules
import com.yaari.chat.model.VoiceLost
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.UiText
import com.yaari.chat.ui.VoiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** Staying in a room: the server's refusals (heartbeat, re-entry), the background (no idle leave), lost connections (045). */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomStayTest {
    private val dispatcher = StandardTestDispatcher()
    private var now = 1_000_000L
    /** The wall clock: moved by hand, so a phone asleep (timers held back) can be played. */
    private var wall = 1_000_000L
    private val visible = MutableStateFlow(true)
    private val today = LocalDate.of(2026, 10, 18)
    private val fake = FakeBackend(ControlledRandom(), clock = { now }, today = { today }, voiceAmbient = false)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.signedIn(): AppViewModel {
        val vm = AppViewModel(fake, DemoBilling(), clock = { now }, today = { today }, foreground = visible, wallClock = { wall })
        advanceUntilIdle()
        vm.requestOtp(Countries.byIso("IN")!!, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(ProfileDraft(name = "Dev", gender = Gender.MALE, day = "1", month = "1", year = "2000", agreed = true)); advanceUntilIdle()
        vm.selectTab(Tab.ROOMS); advanceUntilIdle()
        return vm
    }

    private fun AppViewModel.notice(): UiText? = state.value.notice.also { consumeNotice() }

    /** Hidden for [ms] of timer time, the wall clock moving along. */
    private fun TestScope.background(ms: Long) {
        visible.value = false; runCurrent()
        wall += ms
        advanceTimeBy(ms); runCurrent()
    }

    @Test fun everyRefusalLeavesWithItsReason() = runTest(dispatcher) {
        val vm = signedIn()
        val cases = mapOf(
            "room_full" to UiText(R.string.err_room_full),
            "banned" to UiText(R.string.err_banned),
            "not_allowed" to UiText(R.string.err_not_allowed),
            "blocked" to UiText(R.string.err_not_allowed),
            "not_signed_in" to UiText(R.string.err_not_signed_in),
        )
        for ((code, text) in cases) {
            vm.voice.open("v-cricket"); advanceUntilIdle()
            assertNotNull(code, vm.voice.state.value?.room)
            fake.voice.refuseMe("v-cricket", code); advanceUntilIdle()
            assertNull(code, vm.voice.state.value)
            assertEquals(code, text, vm.notice())
        }
    }

    @Test fun ruleRefusalsSayWhatTheRoomAsksFor() {
        val women = RoomRules(genderRule = GenderRule.FEMALE, minAge = 21, maxAge = 30)
        assertEquals(UiText(R.string.err_room_women), VoiceController.refusedText("room_gender", women))
        assertEquals(UiText(R.string.err_room_men), VoiceController.refusedText("room_gender", women.copy(genderRule = GenderRule.MALE)))
        assertEquals(UiText(R.string.err_room_ages, 21, 30), VoiceController.refusedText("room_age", women))
        // The room not known yet (refused while joining): the plain reason.
        assertEquals(UiText(R.string.err_room_gender), VoiceController.refusedText("room_gender", null))
        assertEquals(UiText(R.string.err_room_age), VoiceController.refusedText("room_age", null))
        assertEquals(UiText(R.string.err_not_allowed), VoiceController.refusedText("something_new", null))
    }

    @Test fun listenersStayInTheBackgroundForHours() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-cricket"); advanceUntilIdle()
        assertNull(vm.voice.state.value!!.me!!.seat)
        // The screen off for three hours (the foreground service keeps the room playing): still in, nothing said.
        background(3 * 60 * 60_000L)
        assertEquals("v-cricket", vm.voice.state.value?.roomId)
        visible.value = true; runCurrent()
        assertEquals("v-cricket", vm.voice.state.value?.roomId)
        assertNull(vm.notice())
    }

    @Test fun aShortDropShowsReconnectingAndCarriesOn() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-cricket"); advanceUntilIdle()
        fake.voice.dropMe("v-cricket"); runCurrent()
        assertEquals(true, vm.voice.state.value?.offline)
        fake.voice.backAfter("v-cricket", VoiceLost.GONE_MS); runCurrent()
        assertEquals(false, vm.voice.state.value?.offline)
        assertEquals("v-cricket", vm.voice.state.value?.roomId)
    }

    @Test fun backAfterMoreThanThirteenSecondsTheRoomClosesWithANotice() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-cricket"); advanceUntilIdle()
        val me = vm.voice.state.value!!.room!!.meId
        fake.voice.dropMe("v-cricket"); runCurrent()
        fake.voice.backAfter("v-cricket", VoiceLost.GONE_MS + 1); advanceUntilIdle()
        assertNull(vm.voice.state.value)
        assertEquals(UiText(R.string.voice_lost_left), vm.notice())
        assertEquals(false, fake.voice.isIn("v-cricket", me))
    }

    @Test fun someoneElsesLostConnectionShowsThenEndsOrComesBack() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-cricket"); advanceUntilIdle()
        fun member(id: String) = vm.voice.state.value!!.room!!.members.firstOrNull { it.profile.id == id }
        val (a, b) = vm.voice.state.value!!.room!!.members.filter { it.seat != null && it.seat != 0 }.take(2).map { it.profile.id }
        fake.voice.lose("v-cricket", a, back = false)
        fake.voice.lose("v-cricket", b, back = true); runCurrent()
        assertEquals(true, member(a)?.lost)
        assertEquals(true, member(b)?.lost)
        advanceTimeBy(3_001); runCurrent()
        assertEquals(false, member(b)?.lost)
        assertEquals(true, member(a)?.lost)
        advanceTimeBy(4_000); runCurrent()
        assertNull(member(a))
    }

    @Test fun theThirteenSecondRule() {
        assertEquals(3_000L, VoiceLost.BEAT_MS)
        assertEquals(false, VoiceLost.droppedOut(1_000, 1_000 + VoiceLost.BEAT_MS))
        assertEquals(false, VoiceLost.droppedOut(1_000, 14_000))
        assertEquals(true, VoiceLost.droppedOut(1_000, 14_001))
    }
}
