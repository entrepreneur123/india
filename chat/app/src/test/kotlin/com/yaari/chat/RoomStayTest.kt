package com.yaari.chat

import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.model.GenderRule
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.RoomRules
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.UiText
import com.yaari.chat.ui.VoiceController
import com.yaari.chat.ui.VoiceController.Companion.IDLE_LEAVE_MS
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

/** Staying in a room: the server's refusals (heartbeat, re-entry) and leaving after 30 minutes in the background. */
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

    @Test fun aListenerLeavesAfterThirtyMinutesInTheBackground() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-cricket"); advanceUntilIdle()
        assertNull(vm.voice.state.value!!.me!!.seat)

        // Back after 29 minutes: still in; the count starts again next time.
        background(IDLE_LEAVE_MS - 60_000)
        visible.value = true; runCurrent()
        assertNotNull(vm.voice.state.value)
        background(IDLE_LEAVE_MS - 1)
        assertNotNull(vm.voice.state.value)
        advanceTimeBy(1); runCurrent()
        assertNull(vm.voice.state.value)
        assertEquals(UiText(R.string.voice_idle_left), vm.notice())
    }

    @Test fun aSleepingPhoneLeavesOnTheWayBack() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-cricket"); advanceUntilIdle()
        visible.value = false; runCurrent()
        // The timer was held back (the phone slept), but the wall clock says 31 minutes went by.
        wall += IDLE_LEAVE_MS + 60_000
        advanceTimeBy(60_000); runCurrent()
        assertNotNull(vm.voice.state.value)
        visible.value = true; runCurrent()
        assertNull(vm.voice.state.value)
        assertEquals(UiText(R.string.voice_idle_left), vm.notice())
    }

    @Test fun theHostAndTheStageAreNeverSentAway() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-lofi"); advanceUntilIdle()
        vm.voice.tapSeat(5) { error("listeners don't manage seats") }; advanceUntilIdle()
        assertEquals(5, vm.voice.state.value!!.me!!.seat)
        background(2 * IDLE_LEAVE_MS)
        visible.value = true; runCurrent()
        assertEquals("v-lofi", vm.voice.state.value?.roomId)
        vm.voice.close(); advanceUntilIdle()

        vm.voice.create(NewVoiceRoom("Late night", RoomCategory.TALK)); advanceUntilIdle()
        assertEquals(true, vm.voice.state.value?.isHost)
        background(2 * IDLE_LEAVE_MS)
        visible.value = true; runCurrent()
        assertEquals(true, vm.voice.state.value?.isHost)
    }
}
