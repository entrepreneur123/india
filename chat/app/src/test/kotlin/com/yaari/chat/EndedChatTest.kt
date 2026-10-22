package com.yaari.chat

import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Gender
import com.yaari.chat.ui.AppViewModel
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** 0.15.0: a random chat that ended goes on only between friends (chat_open); the history stays readable. */
@OptIn(ExperimentalCoroutinesApi::class)
class EndedChatTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 22)
    private var now = 1_000_000L
    private val fake = FakeBackend(ControlledRandom(), clock = { now }, today = { today }, voiceAmbient = false)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.signedIn(): AppViewModel {
        val vm = AppViewModel(fake, DemoBilling(), clock = { now }, today = { today })
        advanceUntilIdle()
        vm.requestOtp(Countries.byIso("IN")!!, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(ProfileDraft("Dev", Gender.MALE, "1", "1", "2000", agreed = true)); advanceUntilIdle()
        return vm
    }

    /** A random chat where both wrote, then ended; its thread opened from Recent. */
    private fun TestScope.endedThread(vm: AppViewModel): String {
        vm.startMatching()
        advanceTimeBy(5_000); runCurrent()
        val chat = vm.state.value.chat!!
        // Live, the chat is open to both, friends or not (the thread from Recent too).
        vm.sendText("hi!")
        advanceTimeBy(5_000); runCurrent()
        assertTrue(vm.state.value.chat!!.messages.size >= 1)
        now += 5_000
        vm.leave(next = false); advanceUntilIdle()
        vm.openDm(chat.match.partner.id); advanceUntilIdle()
        return chat.match.partner.id
    }

    @Test fun anEndedChatWithSomeoneNotAFriendHasNoComposer() = runTest(dispatcher) {
        val vm = signedIn()
        val partner = endedThread(vm)
        val s = vm.state.value
        assertNotNull(s.dm)
        assertEquals(FriendState.NONE, s.friendState(partner))
        assertTrue("the bar instead of the composer", s.dmClosed)
        assertTrue("the history stays readable", s.dm!!.messages.any { it.text == "hi!" })
    }

    @Test fun becomingFriendsBringsTheComposerBack() = runTest(dispatcher) {
        val vm = signedIn()
        val partner = endedThread(vm)
        vm.addFriend(vm.state.value.dm!!.recent.partner); advanceUntilIdle()
        assertEquals(FriendState.REQUESTED, vm.state.value.friendState(partner))
        assertTrue("requested: still waiting", vm.state.value.dmClosed)
        // They accept while I'm away (the demo's people do on the next look); back on screen it is checked again.
        vm.resumed(); advanceUntilIdle()
        assertEquals(FriendState.FRIENDS, vm.state.value.friendState(partner))
        assertFalse(vm.state.value.dmClosed)
        vm.sendDm("friends now!"); advanceUntilIdle()
        assertTrue(vm.state.value.dm!!.messages.any { it.text == "friends now!" })
    }

    @Test fun acceptingTheirRequestOpensItAtOnce() = runTest(dispatcher) {
        val vm = signedIn()
        val partner = endedThread(vm)
        // They asked first (here: the server's state as if they had); my answer makes us friends.
        fake.asksToBeFriends(partner)
        vm.reloadFriends(); advanceUntilIdle()
        assertEquals(FriendState.INCOMING, vm.state.value.friendState(partner))
        assertTrue(vm.state.value.dmClosed)
        vm.answerFriend(vm.state.value.dm!!.recent.partner, true); advanceUntilIdle()
        assertFalse(vm.state.value.dmClosed)
    }

    @Test fun aRefusedLineSaysTheChatEndedNotABlock() = runTest(dispatcher) {
        val vm = signedIn()
        val partner = endedThread(vm)
        val name = vm.state.value.dm!!.recent.partner.name
        var restored = false
        // The screen shows the bar, but a stale composer could still try: the server refuses (row-level security).
        vm.sendDm("still there?") { restored = true }; advanceUntilIdle()
        assertTrue("the text goes back into the field", restored)
        assertEquals(UiText(R.string.dm_ended, name), vm.state.value.notice)
        assertEquals(FriendState.NONE, vm.state.value.friendState(partner))
    }

    @Test fun theLiveChatIsNeverClosed() = runTest(dispatcher) {
        val vm = signedIn()
        vm.startMatching()
        advanceTimeBy(5_000); runCurrent()
        val partner = vm.state.value.chat!!.match.partner
        vm.minimiseChat()
        vm.refreshRecents(); advanceUntilIdle()
        vm.openDm(partner.id); advanceUntilIdle()
        assertNotNull(vm.state.value.dm)
        assertFalse("still live: open to both", vm.state.value.dmClosed)
    }
}
