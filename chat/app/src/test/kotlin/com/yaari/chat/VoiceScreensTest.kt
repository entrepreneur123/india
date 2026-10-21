package com.yaari.chat

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.VoiceReaction
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Voice rooms through the real UI (the demo's rooms, driven through the FakeVoice hooks): the list, a room as a
 * listener and as the host, music and chat, the create sheet, the pill over the tabs. Light and dark.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class VoiceScreensTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 18)
    private val backend = FakeBackend(kotlin.random.Random(11), today = { today }, instant = true)
    private val vm = AppViewModel(backend, DemoBilling(), today = { today })

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /** Sheets and dialogs are windows of their own: capture the whole screen. */
    private fun screen(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun click(text: String) = compose.onAllNodes(hasText(text, substring = true))[0].performClick()

    private fun signIn() {
        compose.setContent { YaariTheme { YaariApp(vm, online = kotlinx.coroutines.flow.MutableStateFlow(true)) } }
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

    private fun open(roomId: String) {
        compose.runOnUiThread { vm.voice.open(roomId) }
        compose.waitUntil(5_000) { vm.voice.state.value?.room != null && vm.voice.state.value?.lines != null }
    }

    @Test fun listRoomAndPill() {
        signIn()
        // The list: topic chips, who is on stage, what plays, the host, how many listen.
        waitFor("Live now")
        shot("voice_01_list")

        // A room as a listener: the host talking (ring), reactions on seats, raise hand.
        open("v-cricket")
        waitFor("Raise hand")
        val room = vm.voice.state.value!!.room!!
        val host = room.hostId
        val cohost = room.members.first { it.seat == 1 }.profile.id
        compose.runOnUiThread {
            backend.voice.speak("v-cricket", mapOf(host to 0.7f))
            backend.voice.react("v-cricket", cohost, VoiceReaction.HEART)
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(700)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/voice_02_room_listener.png")
        compose.mainClock.autoAdvance = true

        // Raising a hand: the host brings me up in the demo; here the button changes at once.
        click("Raise hand")
        waitFor("Hand raised")

        // Minimised: the pill over the Rooms tab, the room keeps going; my card says "You're here".
        compose.onNode(hasContentDescription("Minimise room")).performClick()
        compose.waitUntil(5_000) { vm.voice.state.value?.expanded == false }
        waitFor("You’re here")
        shot("voice_06_pill_over_rooms")
        compose.onNode(hasContentDescription("Open room")).performClick()
        compose.waitUntil(5_000) { vm.voice.state.value?.expanded == true }
        compose.onNode(hasContentDescription("Leave")).performClick()
        compose.waitUntil(5_000) { vm.voice.state.value == null }
    }

    @Test fun musicAndChat() {
        signIn()
        open("v-bolly")
        waitFor("Kesariya")
        compose.runOnUiThread { backend.voice.say("v-bolly", vm.voice.state.value!!.room!!.members.first { it.seat == null }.profile.id, "this chorus is everything") }
        waitFor("this chorus is everything")
        // Chat from me, as a WhatsApp bubble on the right.
        compose.onNode(hasSetTextAction()).performTextInput("Arijit forever")
        compose.onNode(hasContentDescription("Send")).performClick()
        compose.waitUntil(5_000) { vm.voice.state.value!!.lines!!.any { it is com.yaari.chat.model.VoiceLine.Text && it.fromMe } }
        shot("voice_04_music_chat")
    }

    @Test fun hostWithRequests() {
        signIn()
        compose.runOnUiThread { vm.voice.create(NewVoiceRoom("Sunday chai and gossip", RoomCategory.MUSIC)) }
        compose.waitUntil(5_000) { vm.voice.state.value?.room != null }
        val id = vm.voice.state.value!!.roomId
        compose.runOnUiThread { backend.voice.raiseHands(id, 2) }
        compose.waitUntil(5_000) { vm.voice.state.value?.hands == 2 }
        shot("voice_03a_room_host")
        compose.onNode(hasContentDescription("Requests to speak (2)")).performClick()
        waitFor("Invite someone up")
        screen("voice_03_host_requests")
        click("Invite up")
        compose.waitUntil(5_000) { vm.voice.state.value?.room?.members?.count { it.seat != null } == 1 } // the host is not on the stage
        assertEquals(1, vm.voice.state.value!!.hands)

        // The host's leave ends the room for everyone, after a confirm.
        compose.runOnUiThread { vm.voice.leave() }
        waitFor("End this room for everyone?")
        screen("voice_07_end_confirm")
    }

    @Test fun createSheet() {
        signIn()
        click("Create room")
        waitFor("You’re the host")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Sunday cricket chat")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Be kind. Hindi or English, both welcome.")
        screen("voice_05_create")
        compose.onAllNodes(hasText("Create room")).let { it[it.fetchSemanticsNodes().size - 1] }.performScrollTo().performClick()
        compose.waitUntil(5_000) { vm.voice.state.value?.room != null }
        val room = vm.voice.state.value!!.room!!
        assertNull("the host starts off the stage", room.member(room.meId)?.seat)
        assertEquals("Be kind. Hindi or English, both welcome.", room.announcement)
    }
}

/** The same screens at night. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-night-xxhdpi")
class VoiceScreensNightTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 18)
    private val backend = FakeBackend(kotlin.random.Random(11), today = { today }, instant = true)
    private val vm = AppViewModel(backend, DemoBilling(), today = { today })

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun listCreateAndPillAtNight() {
        compose.setContent { YaariTheme { YaariApp(vm, online = kotlinx.coroutines.flow.MutableStateFlow(true)) } }
        waitFor("Mobile number")
        compose.runOnUiThread { vm.requestOtp(Countries.byIso("IN")!!, "9876543210") }
        waitFor("6-digit code")
        compose.runOnUiThread { vm.verifyOtp(FakeBackend.DEMO_OTP) }
        waitFor("Create your profile")
        compose.runOnUiThread { vm.createProfile(ProfileDraft("Asha", Gender.FEMALE, "1", "1", "2000", "Mumbai", "", agreed = true)) }
        waitFor("Meet someone new")
        compose.runOnUiThread { vm.selectTab(Tab.ROOMS) }
        waitFor("Late night Bollywood songs")
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/voice_01_list_dark.png")

        compose.runOnUiThread { vm.voice.open("v-cricket") }
        compose.waitUntil(5_000) { vm.voice.state.value?.room != null }
        compose.runOnUiThread { vm.voice.minimise() }
        waitFor("You’re here")
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/voice_06_pill_over_rooms_dark.png")

        compose.onAllNodes(hasText("Create room"))[0].performClick()
        waitFor("You’re the host")
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/voice_05_create_dark.png")
    }
}
