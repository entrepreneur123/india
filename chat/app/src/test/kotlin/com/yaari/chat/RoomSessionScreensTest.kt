package com.yaari.chat

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceLine
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.PLAYER_TAG
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 0.10.0 through the real UI: a room can't be tapped through to the hidden tabs; back and the tabs take it down to the
 * bar above the tabs (still in it, music on); the two "one live session" confirms; the music panel collapsing to its
 * strip while the one player keeps playing; replies, @mentions and polls.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class RoomSessionScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 10, 18)
    private val backend = FakeBackend(kotlin.random.Random(11), today = { today }, instant = true)
    private val vm = AppViewModel(backend, DemoBilling(), today = { today })

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/p3_$name.png")
    }

    /** Dialogs, menus and sheets are windows of their own: the whole screen. */
    private fun screen(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/p3_$name.png")
    }

    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(text: String) = compose.waitUntil(5_000) { has(text) }
    private fun click(text: String) = compose.onAllNodes(hasText(text, substring = true))[0].performClick()
    /** A tab of the bottom bar (its exact label). */
    private fun tab(label: String) = compose.onAllNodes(hasText(label)).let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
    private fun player() = compose.onAllNodes(hasTestTag(PLAYER_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun signIn() {
        compose.setContent { YaariTheme { YaariApp(vm, online = MutableStateFlow(true)) } }
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
        compose.waitForIdle()
    }

    private fun back() {
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test fun theTabsWorkFromARoomAndTheBarKeepsMeIn() {
        signIn()
        open("v-cricket")
        // The tabs are under the room: a tap where "Random" is lands on the room, not on the hidden tab.
        compose.onRoot().performTouchInput { this.click(Offset(width * 0.37f, height - 108f)) }
        compose.waitForIdle()
        assertEquals(Tab.ROOMS, vm.state.value.tab)
        assertEquals(true, vm.voice.state.value?.expanded)

        // Back takes the room down to the bar above the tabs: still in it.
        back()
        assertEquals(false, vm.voice.state.value?.expanded)
        waitFor("LIVE")
        assertTrue(has("Cricket talk"))
        shot("01_bar_over_rooms")

        tab("Me")
        compose.waitUntil(5_000) { vm.state.value.tab == Tab.ME }
        assertNotNull("still in the room", vm.voice.state.value)
        assertTrue(backend.voice.isIn("v-cricket", vm.state.value.me!!.id))
        waitFor("LIVE")
        shot("02_bar_over_me")
        tab("Recent")
        compose.waitUntil(5_000) { vm.state.value.tab == Tab.RECENT }
        tab("Random")
        compose.waitUntil(5_000) { vm.state.value.tab == Tab.RANDOM }
        waitFor("LIVE")
        shot("03_bar_over_random")

        // A tap on the bar opens the room again; Leave is the only way out.
        compose.onNode(hasContentDescription("Open room")).performClick()
        compose.waitUntil(5_000) { vm.voice.state.value?.expanded == true }
        back()
        compose.waitUntil(5_000) { vm.voice.state.value?.expanded == false }
        compose.onAllNodes(hasContentDescription("Leave"))[0].performClick()
        compose.waitUntil(5_000) { vm.voice.state.value == null }
        assertFalse(has("LIVE"))
    }

    @Test fun oneLiveSessionAtATime() {
        signIn()
        open("v-cricket")
        back()
        tab("Random")
        compose.waitUntil(5_000) { vm.state.value.tab == Tab.RANDOM }
        // Start chatting from inside a room: the room is left (no question), the search starts.
        click("Start chatting")
        compose.waitUntil(5_000) { vm.voice.state.value == null }
        assertFalse(has("LIVE"))
        compose.waitUntil(15_000) { vm.state.value.chat != null }
        compose.waitForIdle()
        shot("04_chat_with_minimise")

        // A tab (or the chevron) takes the chat down to its bar: still on.
        compose.onNode(hasContentDescription("Minimise chat")).performClick()
        compose.waitUntil(5_000) { vm.state.value.chatMinimised }
        val partner = vm.state.value.chat!!.match.partner.name
        waitFor("Chatting with $partner")
        tab("Rooms")
        compose.waitUntil(5_000) { vm.state.value.tab == Tab.ROOMS }
        shot("05_chat_bar_over_rooms")
        compose.onNode(hasContentDescription("Open chat")).performClick()
        compose.waitUntil(5_000) { !vm.state.value.chatMinimised }
        compose.onNode(hasContentDescription("Minimise chat")).performClick()
        compose.waitUntil(5_000) { vm.state.value.chatMinimised }

        // Entering a room ends the random chat (no question either).
        compose.runOnUiThread { vm.voice.open("v-chai") }
        compose.waitUntil(5_000) { vm.state.value.chat == null }
        compose.waitUntil(5_000) { vm.voice.state.value?.room != null }
        assertFalse(has("Chatting with"))
    }

    @Test fun collapsingTheMusicKeepsItPlaying() {
        signIn()
        compose.runOnUiThread { vm.voice.create(NewVoiceRoom("Sunday chai and songs", RoomCategory.TALK)) }
        compose.waitUntil(5_000) { vm.voice.state.value?.room != null }
        compose.runOnUiThread { vm.voice.act(VoiceAction.AddTrack("BddP6PYo2gs", "Kesariya – Brahmāstra · Arijit Singh")) }
        compose.waitUntil(5_000) { vm.voice.state.value?.room?.track != null }
        waitFor("Paused for you")
        assertFalse("no player before Listen", player())
        // The exact button: "Listening · N" (the listeners' block, always shown now) also contains "Listen".
        compose.onAllNodes(hasText("Listen"))[0].performClick()
        compose.waitUntil(5_000) { vm.voice.state.value?.playing == true }
        compose.waitUntil(5_000) { player() }
        shot("06_music_open")

        compose.onNode(hasContentDescription("Hide player")).performScrollTo().performClick()
        waitFor("Playing for you")
        assertEquals(false, vm.voice.state.value?.musicOpen)
        assertEquals(true, vm.voice.state.value?.playing)
        assertTrue("the one player is still there, playing", player())
        shot("07_music_strip")

        back()
        waitFor("LIVE")
        assertTrue("minimised: still playing", player())
        assertEquals(true, vm.voice.state.value?.playing)
        waitFor("Playing: Kesariya")
        shot("08_bar_with_music")

        compose.onNode(hasContentDescription("Open room")).performClick()
        compose.waitUntil(5_000) { vm.voice.state.value?.expanded == true }
        compose.onNode(hasContentDescription("Show player")).performClick()
        compose.waitUntil(5_000) { vm.voice.state.value?.musicOpen == true }
        assertEquals(1, compose.onAllNodes(hasTestTag(PLAYER_TAG), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun repliesAndMentions() {
        signIn()
        open("v-cricket")
        val room = vm.voice.state.value!!.room!!
        val other = room.members.first { it.seat == null && it.profile.id != room.meId }.profile
        compose.runOnUiThread { backend.voice.say("v-cricket", other.id, "Who is opening today?") }
        waitFor("Who is opening today?")
        val line = vm.voice.state.value!!.lines!!.last { it is VoiceLine.Text && it.text == "Who is opening today?" }

        // Long-press: Reply.
        compose.onAllNodes(hasText("Who is opening today?"))[0].performTouchInput { longClick() }
        waitFor("Reply")
        screen("09_line_menu")
        click("Reply")
        compose.waitUntil(5_000) { vm.voice.state.value?.replyTo?.id == line.id }
        waitFor("Replying to ${other.name}")

        // "@" offers the people present.
        compose.onNode(input()).performTextInput("@" + other.name.take(2))
        compose.waitForIdle()
        shot("10_mention_and_quote")
        // Pick them from the list (the last node with the name: the picker sits above the composer).
        compose.onAllNodes(hasText(other.name)).let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        compose.waitForIdle()
        compose.onNode(input()).performTextInput("Rohit and Gill")
        compose.onNode(hasContentDescription("Send")).performClick()
        compose.waitUntil(5_000) { (vm.voice.state.value!!.lines!!.last() as? VoiceLine.Text)?.let { it.fromMe && it.reply?.id == line.id } == true }
        assertNull(vm.voice.state.value!!.replyTo)
        val sent = vm.voice.state.value!!.lines!!.last() as VoiceLine.Text
        assertTrue(sent.text, sent.text.startsWith("@${other.name} "))

        // Someone mentions me: their line stands out; their reply quotes my line.
        compose.runOnUiThread { backend.voice.say("v-cricket", other.id, "@Asha agreed, what a pair", replyTo = sent.id) }
        compose.waitUntil(5_000) { (vm.voice.state.value!!.lines!!.last() as? VoiceLine.Text)?.text == "@Asha agreed, what a pair" }
        compose.waitForIdle()
        waitFor("agreed, what a pair")
        compose.waitForIdle()
        shot("11_reply_and_mention")

        // The quote, tapped, finds the original.
        compose.onAllNodes(hasContentDescription("Reply to ${other.name}: Who is opening today?", substring = true))[0].performClick()
        compose.waitForIdle()
    }

    @Test fun pollCardStates() {
        signIn()
        open("v-chai")
        waitFor("Chai or coffee?")
        compose.onAllNodes(hasText("Chai or coffee?"))[0].performScrollTo()
        waitFor("Tap an answer to vote")
        shot("12_poll_open")
        click("Coffee")
        val id = vm.voice.state.value!!.lines!!.filterIsInstance<VoiceLine.Poll>().single().pollId
        compose.waitUntil(5_000) { vm.voice.state.value?.polls?.get(id)?.mine == 1 }
        waitFor("Tap another answer to change your vote")
        waitFor("%")
        shot("13_poll_voted")
        click("Depends on the mood")
        compose.waitUntil(5_000) { vm.voice.state.value?.polls?.get(id)?.mine == 2 }
        compose.runOnUiThread { backend.voice.closeFromHost(id) }
        waitFor("Closed")
        assertEquals(2, vm.voice.state.value!!.polls[id]!!.mine)
        shot("14_poll_closed")
        compose.runOnUiThread { vm.voice.close() }
        compose.waitUntil(5_000) { vm.voice.state.value == null }

        // A host asks one from the composer's "+".
        compose.runOnUiThread { vm.voice.create(NewVoiceRoom("Movie night picks", RoomCategory.TALK)) }
        compose.waitUntil(5_000) { vm.voice.state.value?.room != null && vm.voice.state.value?.lines != null }
        compose.onNode(hasContentDescription("Add to the chat")).performClick()
        waitFor("Poll")
        screen("15_attach_menu")
        click("Poll")
        waitFor("Ask the room")
        val fields = compose.onAllNodes(androidx.compose.ui.test.hasSetTextAction() and !hasText("Search by room name") and !hasContentDescription("Say something…"))
        fields[0].performTextInput("Which film tonight?")
        fields[1].performTextInput("Sholay")
        fields[2].performTextInput("DDLJ")
        click("Add an answer")
        compose.waitForIdle()
        compose.onAllNodes(androidx.compose.ui.test.hasSetTextAction() and !hasText("Search by room name") and !hasContentDescription("Say something…"))[3].performTextInput("3 Idiots")
        screen("16_poll_sheet")
        compose.onAllNodes(hasText("Ask")).let { it[it.fetchSemanticsNodes().size - 1] }.performScrollTo().performClick()
        compose.waitUntil(5_000) { vm.voice.state.value!!.lines!!.any { it is VoiceLine.Poll && it.fromMe } }
        waitFor("Close poll")
        shot("17_poll_mine")
    }
}
