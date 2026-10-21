package com.yaari.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.FakeLudo
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.model.LudoAction
import com.yaari.chat.model.LudoStatus
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.LudoHandlers
import com.yaari.chat.ui.LudoPanel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.VoiceState
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Ludo in a voice room through the real UI (the demo's game, stepped through the FakeLudo hooks): the lobby, my turn
 * (roll, then pick), watching a game, the result, the board by day, and frames in the middle of a move (a token must
 * never be drawn oversized or off its square).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class LudoScreensTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 18)
    private val backend = FakeBackend(kotlin.random.Random(11), today = { today }, instant = true)
    private val vm = AppViewModel(backend, DemoBilling(), today = { today })
    private val ludo get() = backend.voice.ludo
    private val s get() = vm.voice.state.value!!

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /** Shown on its own on a day surface instead of the app (the room itself is always dark). */
    private val dayPanel = mutableStateOf<VoiceState?>(null)

    private fun waitFor(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasText(text, substring = true) or hasContentDescription(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun signIn() {
        compose.setContent {
            YaariTheme {
                val day = dayPanel.value
                if (day == null) YaariApp(vm, online = kotlinx.coroutines.flow.MutableStateFlow(true))
                else Box(Modifier.background(MaterialTheme.colorScheme.background).padding(vertical = 8.dp)) {
                    LudoPanel(
                        day.room!!, day.ludo!!, day.isMod, remember { mutableStateOf(emptyMap<String, Float>()) }, 380.dp,
                        LudoHandlers({ _, _ -> }, {}, {}, {}, {}),
                    )
                }
            }
        }
        waitFor("Mobile number")
        compose.runOnUiThread { vm.requestOtp(Countries.byIso("IN")!!, "9876543210") }
        waitFor("6-digit code")
        compose.runOnUiThread { vm.verifyOtp(FakeBackend.DEMO_OTP) }
        waitFor("Create your profile")
        compose.runOnUiThread { vm.createProfile(ProfileDraft("Asha", Gender.FEMALE, "1", "1", "2000", "Mumbai", "", agreed = true)) }
        waitFor("Meet someone new")
        compose.runOnUiThread { vm.selectTab(Tab.ROOMS) }
        waitFor("Late night Bollywood songs")
    }

    /** My room, a lobby, and three people seated with me. */
    private fun hostLobby(): String {
        signIn()
        compose.runOnUiThread { vm.voice.create(NewVoiceRoom("Ludo night in", RoomCategory.GAMES)) }
        compose.waitUntil(5_000) { vm.voice.state.value?.room != null }
        compose.onNode(hasContentDescription("Play Ludo")).performClick()
        compose.waitUntil(5_000) { vm.voice.state.value?.ludoShown != null }
        val id = s.roomId
        repeat(2) {
            compose.runOnUiThread { ludo.step(id) }
            compose.waitForIdle()
        }
        compose.waitUntil(5_000) { s.ludo!!.players.count { it != null } == 3 }
        return id
    }

    private fun start(id: String) {
        compose.runOnUiThread { vm.voice.ludo(LudoAction.Start) }
        compose.waitUntil(5_000) { s.ludo?.status == LudoStatus.PLAYING }
    }

    /** Sets the board (FakeLudo) and waits until the screen has it. */
    private fun arrange(id: String, tokens: List<Int>, turn: Int, dice: Int? = null) {
        val v = s.ludo!!.version
        compose.runOnUiThread { ludo.arrange(id, tokens, turn = turn, dice = dice) }
        settled(v + 1)
    }

    /** Waits until the screen has played the server's change (dice, hops) and settled. */
    private fun settled(version: Int) {
        compose.waitUntil(8_000) { s.ludo!!.version >= version }
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()
    }

    @Test fun lobbyYourTurnAndResult() {
        val id = hostLobby()
        waitFor("Start game")
        shot("ludo_01_lobby")

        start(id)
        // My turn: a few tokens out, the die waiting for me (it glows), the ring counting down.
        val tokens = listOf(4, 17, -1, -1, 10, -1, -1, -1, 30, 2, -1, -1, -1, -1, -1, -1)
        arrange(id, tokens, turn = 0)
        waitFor("Roll the dice")
        shot("ludo_02_your_turn_roll")

        // A 6 is waiting: three tokens may move (they pulse), the banner says to pick.
        arrange(id, tokens, turn = 0, dice = 6)
        waitFor("Pick a token")
        shot("ludo_03_your_turn_pick")

        // Moving one: frames in the middle of the hops, then the capture of green's token (10 squares on: square 10).
        arrange(id, listOf(4, 17, -1, -1, 49, -1, -1, -1, 30, 2, -1, -1, -1, -1, -1, -1), turn = 0, dice = 6)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { vm.voice.ludo(LudoAction.Move(0, s.ludo!!.version)) }
        compose.waitUntil(5_000) { s.ludo!!.tokens[0] == 10 }
        val board = compose.onNode(hasContentDescription("Ludo board"))
        for (i in 1..6) {
            compose.mainClock.advanceTimeBy(if (i == 1) 16 else 70)
            board.captureRoboImage("build/outputs/roborazzi/ludo_06_move_frame_$i.png")
        }
        compose.mainClock.advanceTimeBy(700)
        board.captureRoboImage("build/outputs/roborazzi/ludo_06_move_frame_7_capture.png")
        compose.mainClock.autoAdvance = true
        assertEquals(-1, s.ludo!!.tokens[4])

        // The result: my last token comes home.
        arrange(id, listOf(55, 56, 56, 56, 10, 3, -1, -1, 30, 2, -1, -1, -1, -1, -1, -1), turn = 0)
        compose.runOnUiThread {
            ludo.loadDice(1)
            vm.voice.ludo(LudoAction.Roll(s.ludo!!.version))
        }
        compose.waitUntil(5_000) { s.ludo!!.status == LudoStatus.FINISHED }
        compose.mainClock.advanceTimeBy(4_000)
        waitFor("You win!")
        shot("ludo_05_result")
        assertTrue(compose.onAllNodes(hasText("Play again")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun watchingAGame() {
        signIn()
        compose.runOnUiThread { vm.voice.open(FakeLudo.LUDO_ROOM) }
        compose.waitUntil(5_000) { vm.voice.state.value?.ludoShown != null }
        waitFor("You’re watching")
        shot("ludo_04_watching")
        // The stage and back.
        compose.onNode(hasContentDescription("Show the stage")).performClick()
        waitFor("Back to the game")
        shot("ludo_04b_stage_with_game_on")
    }

    /** The board on a day surface (the room itself is always dark): the light palette. */
    @Test fun boardByDay() {
        val id = hostLobby()
        start(id)
        arrange(id, listOf(4, 17, -1, 56, 10, -1, -1, -1, 30, 2, 52, -1, -1, -1, -1, -1), turn = 2)
        compose.runOnUiThread { dayPanel.value = s }
        shot("ludo_07_board_light")
    }
}

/** The day palette at night qualifiers is the same room; this checks the board in Hindi (labels, banner). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "hi-w393dp-h852dp-xxhdpi")
class LudoScreensHindiTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 18)
    private val backend = FakeBackend(kotlin.random.Random(11), today = { today }, instant = true)
    private val vm = AppViewModel(backend, DemoBilling(), today = { today })

    @Test fun watchingInHindi() {
        compose.setContent { YaariTheme { YaariApp(vm, online = kotlinx.coroutines.flow.MutableStateFlow(true)) } }
        compose.waitForIdle()
        compose.runOnUiThread { vm.requestOtp(Countries.byIso("IN")!!, "9876543210") }
        compose.waitForIdle()
        compose.runOnUiThread { vm.verifyOtp(FakeBackend.DEMO_OTP) }
        compose.waitForIdle()
        compose.runOnUiThread { vm.createProfile(ProfileDraft("Asha", Gender.FEMALE, "1", "1", "2000", "Mumbai", "", agreed = true)) }
        compose.waitForIdle()
        compose.runOnUiThread { vm.voice.open(FakeLudo.LUDO_ROOM) }
        compose.waitUntil(5_000) { vm.voice.state.value?.ludoShown != null }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("आप देख रहे हैं", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/ludo_08_watching_hi.png")
    }
}
