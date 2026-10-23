package com.yaari.chat

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.yaari.chat.data.Backend
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Friend
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Gender
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.RecentMatch
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The host's leave confirm with a co-host, Settings › Recent chats, and the friend requests I sent. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class FixScreensTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 18)
    private val fake = FakeBackend(kotlin.random.Random(11), today = { today }, instant = true)
    private val people = FakeBackend.POOL.take(4)
    private val backend = object : Backend by fake {
        override suspend fun recents() = people.mapIndexed { i, p -> RecentMatch("m$i", p, System.currentTimeMillis() - (i + 1) * 3_600_000L) }
        override suspend fun friends() = listOf(Friend(people[0], FriendState.REQUESTED), Friend(people[2], FriendState.REQUESTED))
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("/tmp/claude-0/-home-user-india/ddb0ac4a-dcc2-550f-b595-fb27e8220261/scratchpad/shots/fix-android-$name.png")
    }

    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(text: String) = compose.waitUntil(5_000) { has(text) }
    private fun click(text: String) = compose.onAllNodes(hasText(text, substring = true))[0].performClick()

    private fun signIn(): AppViewModel {
        val vm = AppViewModel(backend, DemoBilling(), today = { today })
        compose.setContent { YaariTheme { YaariApp(vm, online = MutableStateFlow(true)) } }
        waitFor("Mobile number")
        compose.runOnUiThread { vm.requestOtp(Countries.byIso("IN")!!, "9876543210") }
        waitFor("6-digit code")
        compose.runOnUiThread { vm.verifyOtp(FakeBackend.DEMO_OTP) }
        waitFor("Create your profile")
        compose.runOnUiThread { vm.createProfile(ProfileDraft("Asha", Gender.FEMALE, "1", "1", "2000", "Mumbai", "", agreed = true)) }
        waitFor("Meet someone new")
        return vm
    }

    @Test fun theHostLeavingWithACohostHandsTheRoomOver() {
        val vm = signIn()
        compose.runOnUiThread { vm.voice.create(NewVoiceRoom("Chai pe charcha", RoomCategory.TALK)) }
        compose.waitUntil(5_000) { vm.voice.state.value?.isHost == true }
        val id = vm.voice.state.value!!.roomId
        compose.runOnUiThread { fake.voice.raiseHands(id, 1) }
        compose.waitUntil(5_000) { vm.voice.state.value?.room?.audience?.any { it.hand } == true }
        val cohost = vm.voice.state.value!!.room!!.audience.first { it.hand }.profile
        compose.runOnUiThread { vm.voice.act(VoiceAction.Cohost(cohost.id, true)) }
        compose.waitUntil(5_000) { vm.voice.state.value?.heir != null }
        compose.onAllNodes(hasContentDescription("Leave"))[0].performClick()
        waitFor("${cohost.name} becomes host")
        shot("host-leave-cohost")
        click("${cohost.name} becomes host")
        compose.waitUntil(5_000) { vm.voice.state.value == null }
        assertTrue("the room stays", fake.voice.isOpen(id))
        assertEquals(cohost.id, fake.voice.hostOf(id))
    }

    @Test fun recentChatsInSettings() {
        val vm = signIn()
        compose.onAllNodes(hasContentDescription("Settings"))[0].performClick()
        compose.onNode(hasText("Recent chats")).performScrollTo().performClick()
        waitFor(people[1].name)
        click(people[1].name)
        click(people[3].name)
        waitFor("Delete (2)")
        shot("recents-delete")
        click("Delete (2)")
        waitFor("Delete from Recent?")
        shot("recents-confirm")
        assertTrue(vm.state.value.recents.isNotEmpty())
    }

    @Test fun sentFriendRequests() {
        val vm = signIn()
        compose.runOnUiThread { vm.selectTab(Tab.RECENT) }
        waitFor("Sent")
        compose.onNode(hasText("Sent")).performClick()
        waitFor(people[2].name)
        shot("sent-requests")
    }
}
