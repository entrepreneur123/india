package com.yaari.chat

import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.FakeLudo
import com.yaari.chat.domain.Countries
import com.yaari.chat.domain.Ludo
import com.yaari.chat.model.Gender
import com.yaari.chat.model.LudoAction
import com.yaari.chat.model.LudoEvent
import com.yaari.chat.model.LudoStatus
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.SpeakMode
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.UiText
import com.yaari.chat.ui.VoiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * Ludo in a voice room through the view model, against the FakeBackend (the server's rules). The clocks follow the
 * test's virtual time, so turn deadlines and the timeout calls run as they would on a phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LudoVoiceTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = { 1_000_000L + dispatcher.scheduler.currentTime }
    private val today = LocalDate.of(2026, 10, 18)
    private val fake = FakeBackend(ControlledRandom(), clock = clock, today = { today }, voiceAmbient = false)
    private val ludo get() = fake.voice.ludo

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.signedIn(): AppViewModel {
        val vm = AppViewModel(fake, DemoBilling(), clock = clock, today = { today }, wallClock = clock)
        advanceUntilIdle()
        vm.requestOtp(Countries.byIso("IN")!!, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(ProfileDraft(name = "Dev", gender = Gender.MALE, day = "1", month = "1", year = "2000", agreed = true)); advanceUntilIdle()
        vm.selectTab(Tab.ROOMS); advanceUntilIdle()
        return vm
    }

    private val AppViewModel.s get() = voice.state.value!!
    private val AppViewModel.game get() = s.ludo!!
    private fun AppViewModel.notice(): UiText? = state.value.notice.also { consumeNotice() }

    /** Settles an action (the demo's small network waits) without running into the turn's deadline. */
    private fun TestScope.settle() = advanceTimeBy(1_500)

    /** My own room with a game open in it and two people seated with me. */
    private fun TestScope.hosting(): AppViewModel {
        val vm = signedIn()
        vm.voice.create(NewVoiceRoom("Ludo with friends", RoomCategory.GAMING, SpeakMode.OPEN)); advanceUntilIdle()
        assertTrue(vm.s.isHost)
        assertNull(vm.s.ludo)
        vm.voice.ludoButton(); advanceUntilIdle()
        val lobby = vm.game
        assertEquals(LudoStatus.LOBBY, lobby.status)
        assertEquals(vm.s.room!!.meId, lobby.players[0]) // the opener takes the first colour
        assertNotNull(vm.s.ludoShown) // up for everyone
        // People in the room take the free colours, one after another.
        ludo.step(vm.s.roomId); advanceUntilIdle()
        ludo.step(vm.s.roomId); advanceUntilIdle()
        assertEquals(3, vm.game.players.count { it != null })
        return vm
    }

    private fun TestScope.started(): AppViewModel {
        val vm = hosting()
        vm.voice.ludo(LudoAction.Start); settle()
        return vm
    }

    @Test fun hostOpensALobbyPeopleJoinAndTheGameStarts() = runTest(dispatcher) {
        val vm = hosting()
        assertEquals(0, vm.s.ludoSeat)
        // Switching colour in the lobby, and the refusal for a taken one.
        vm.voice.ludo(LudoAction.Join(3)); settle()
        assertEquals(3, vm.s.ludoSeat)
        vm.voice.ludo(LudoAction.Join(1)); settle()
        assertEquals(UiText(R.string.ludo_e_seat_taken), vm.notice())
        vm.voice.ludo(LudoAction.Join(0)); settle()

        val v = vm.game.version
        vm.voice.ludo(LudoAction.Start); settle()
        val g = vm.game
        assertEquals(LudoStatus.PLAYING, g.status)
        assertEquals(0, g.turn) // red (me) goes first
        assertEquals(listOf<LudoEvent>(LudoEvent.Start(0)), g.events)
        assertTrue(g.version > v)
        assertTrue(g.deadline!! > clock())
    }

    @Test fun rollingMovingAndWhichTokensICanTap() = runTest(dispatcher) {
        val vm = started()
        val room = vm.s.roomId
        // A 6 takes a token out (any token: the same move, played at once) and gives another roll.
        ludo.loadDice(6)
        vm.voice.ludo(LudoAction.Roll(vm.game.version)); settle()
        assertEquals(0, vm.game.tokens[0])
        assertEquals(0, vm.game.turn)
        assertNull(vm.game.dice)
        assertEquals(listOf(LudoEvent.Roll(0, 6), LudoEvent.Move(0, 0, -1, 0)), vm.game.events)
        assertTrue(Ludo.movable(vm.game, 0).isEmpty()) // roll first

        // Two tokens out and a 4 waiting: both pulse, a tap moves one, the turn goes on.
        ludo.arrange(room, listOf(3, 10, -1, -1) + List(12) { -1 }, turn = 0, dice = 4); settle()
        assertEquals(setOf(0, 1), Ludo.movable(vm.game, vm.s.ludoSeat))
        vm.voice.ludo(LudoAction.Move(1, vm.game.version)); settle()
        assertEquals(14, vm.game.tokens[1])
        assertEquals(1, vm.game.turn)
        assertTrue(Ludo.movable(vm.game, vm.s.ludoSeat).isEmpty())

        // Not my turn: said in my language. A stale screen just re-reads the game (no message).
        vm.voice.ludo(LudoAction.Roll(vm.game.version)); settle()
        assertEquals(UiText(R.string.ludo_e_not_your_turn), vm.notice())
        val before = vm.game.version
        ludo.arrange(room, vm.game.tokens, turn = 0); settle()
        vm.voice.ludo(LudoAction.Roll(before)); settle()
        assertNull(vm.notice())
        assertEquals(before + 1, vm.game.version)
    }

    @Test fun aTurnThatRunsOutIsPlayedByTheServerWhenAsked() = runTest(dispatcher) {
        val vm = started()
        val g = vm.game
        // Nothing before the deadline...
        advanceTimeBy(g.deadline!! - clock() - 100)
        assertEquals(g.version, vm.game.version)
        // ...then, a moment after it, the turn is played for the absent player (rolled and moved, or passed).
        advanceTimeBy(100 + VoiceController.LUDO_TIMEOUT_GRACE_MS + VoiceController.LUDO_TIMEOUT_JITTER_MS + 1_000)
        assertTrue(vm.game.version > g.version)
        assertEquals(LudoEvent.Timeout(0), vm.game.events.first())
        assertEquals(1, vm.game.turn) // a 1 (the test's die) has no move from the base: next player
    }

    @Test fun timeoutDelayWaitsForTheDeadlineAndAGrace() {
        assertEquals(15_000L + 700 + 300, VoiceController.timeoutDelay(deadline = 20_000, now = 5_000, jitter = 300))
        assertEquals(700L, VoiceController.timeoutDelay(deadline = 1_000, now = 5_000, jitter = 0)) // already late
    }

    @Test fun spectatorsWatchAndDoNotCallTimeouts() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open(FakeLudo.LUDO_ROOM); advanceUntilIdle()
        val g = vm.game
        assertEquals(LudoStatus.PLAYING, g.status)
        assertEquals(-1, vm.s.ludoSeat)
        assertNotNull(vm.s.ludoShown) // a game on comes up
        advanceTimeBy(60_000)
        assertEquals(g.version, vm.game.version) // a listener never plays someone's turn

        // The stage and back.
        vm.voice.showLudo(false)
        assertTrue(vm.s.ludoHidden)
        assertNull(vm.s.ludoShown)
        vm.voice.ludoButton()
        assertNotNull(vm.s.ludoShown)
        // Only hosts open or close games.
        vm.voice.ludo(LudoAction.Close); settle()
        assertEquals(UiText(R.string.err_not_allowed), vm.notice())
    }

    @Test fun theWinnerEndsTheGameAndTheHostCanPlayAgain() = runTest(dispatcher) {
        val vm = started()
        val room = vm.s.roomId
        ludo.arrange(room, listOf(55, 56, 56, 56) + List(12) { -1 }, turn = 0); settle()
        ludo.loadDice(1)
        vm.voice.ludo(LudoAction.Roll(vm.game.version)); settle()
        val done = vm.game
        assertEquals(LudoStatus.FINISHED, done.status)
        assertEquals(0, done.winner)
        assertEquals(LudoEvent.Win(0), done.events.last())
        assertTrue(vm.s.ludoOn) // the result stays up...
        vm.voice.dismissLudo()
        assertFalse(vm.s.ludoOn) // ...until "Back to the room"

        // Play again: a new lobby, everyone keeps their colour.
        vm.voice.ludo(LudoAction.Open); settle()
        val again = vm.game
        assertEquals(LudoStatus.LOBBY, again.status)
        assertEquals(done.players, again.players)
        assertTrue(again.id != done.id)
        assertNotNull(vm.s.ludoShown)
    }

    @Test fun closingTheGameBringsTheStageBack() = runTest(dispatcher) {
        val vm = started()
        vm.voice.askCloseLudo(true)
        assertTrue(vm.s.confirmLudoClose)
        vm.voice.closeLudo(); settle()
        assertEquals(LudoStatus.CANCELLED, vm.game.status)
        assertFalse(vm.s.ludoOn)
        assertFalse(vm.s.confirmLudoClose)
        assertNull(vm.notice()) // I closed it: no "the game was closed"
        vm.voice.leave(); advanceUntilIdle() // the host confirms ending the room instead
        assertTrue(vm.s.confirmEnd)
    }
}
