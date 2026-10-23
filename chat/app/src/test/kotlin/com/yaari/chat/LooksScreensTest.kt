package com.yaari.chat

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Gender
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 0.15.0 through the real UI: the Looks shop, a framed profile (light and dark), the room's split and its People. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class LooksScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 10, 22)
    private val backend = FakeBackend(kotlin.random.Random(11), today = { today }, instant = true)
    private val vm = AppViewModel(backend, DemoBilling(), today = { today })

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/looks_$name.png")
    }

    /** Sheets are windows of their own: the whole screen. */
    private fun screen(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/looks_$name.png")
    }

    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(text: String) = compose.waitUntil(5_000) { has(text) }
    private fun click(text: String) = compose.onAllNodes(hasText(text))[0].performClick()

    private fun signIn() {
        compose.setContent { YaariTheme { YaariApp(vm, online = MutableStateFlow(true)) } }
        waitFor("Mobile number")
        compose.runOnUiThread { vm.requestOtp(Countries.byIso("IN")!!, "9876543210") }
        waitFor("6-digit code")
        compose.runOnUiThread { vm.verifyOtp(FakeBackend.DEMO_OTP) }
        waitFor("Create your profile")
        compose.runOnUiThread { vm.createProfile(ProfileDraft("Asha", Gender.FEMALE, "1", "1", "2000", "Mumbai", "", agreed = true)) }
        waitFor("Meet someone new")
    }

    @Test fun theShopTriesBuysAndWears() {
        signIn()
        compose.runOnUiThread { vm.selectTab(Tab.ME) }
        waitFor("Edit profile")
        click("Edit profile") // Looks are in Edit profile
        waitFor("Looks")
        compose.onNode(hasText("Looks")).performScrollTo().performClick()
        waitFor("Everyone sees your look")
        waitFor("Crown")
        shot("01_shop_badges")
        click("Frames")
        waitFor("Sakura")
        // Bought in the demo at once, and worn: the card's button says so.
        compose.onAllNodes(hasText("₹", substring = true))[0].performClick()
        compose.waitUntil(5_000) { vm.looks.shop.value.shop?.look?.frame != null }
        waitFor("Wearing")
        shot("02_shop_frame_worn")
    }

    @Test fun aFramedProfile() = framedProfile("03_profile_framed_light")

    // The app is light only: system dark mode changes nothing.
    @Config(qualifiers = "+night")
    @Test fun aFramedProfileAtNight() = framedProfile("04_profile_framed_system_dark")

    private fun framedProfile(name: String) {
        signIn()
        compose.runOnUiThread { vm.openProfile(FakeBackend.POOL.first { it.id == "p1" }) }
        waitFor("@priya.mumbai")
        shot(name)
    }

    @Test fun theRoomSplitSnapsAndIsRemembered() {
        signIn()
        compose.runOnUiThread { vm.voice.open("v-cricket") }
        compose.waitUntil(5_000) { vm.voice.state.value?.room != null && vm.voice.state.value?.lines != null }
        val prefs = compose.activity.getSharedPreferences("yaari_room_split", Context.MODE_PRIVATE)
        shot("05_room_talk_split")
        // Double tap: chat only, remembered for talk rooms on phones.
        compose.onNode(hasContentDescription("Resize the chat")).performTouchInput { doubleClick() }
        compose.waitUntil(5_000) { prefs.getFloat("talk_phone", -1f) == 0f }
        shot("06_room_chat_only")
        // Dragged down, it lets go on the nearest of 30, 50 and 70 %.
        compose.onNode(hasContentDescription("Resize the chat")).performTouchInput { swipeDown(centerY, centerY + 900f) }
        compose.waitUntil(5_000) { prefs.getFloat("talk_phone", -1f) in listOf(0.3f, 0.5f, 0.7f) }
        shot("07_room_dragged")
    }

    @Test fun thePeopleCountOpensPeopleWithFriendButtons() {
        signIn()
        compose.runOnUiThread { vm.voice.open("v-cricket") }
        compose.waitUntil(5_000) { vm.voice.state.value?.room != null }
        compose.waitForIdle()
        compose.onAllNodes(hasText("here", substring = true))[0].performClick()
        waitFor("Search people")
        screen("08_people")
        // The stage comes first: its first person's button asks them.
        val first = vm.voice.state.value!!.room!!.members.filter { it.seat != null }.minBy { it.seat!! }.profile
        assertEquals(FriendState.NONE, vm.state.value.friendState(first.id))
        compose.onAllNodes(hasText("Add friend"))[0].performClick()
        compose.waitUntil(5_000) { vm.state.value.friendState(first.id) == FriendState.REQUESTED }
        waitFor("Requested")
        screen("09_people_requested")
    }
}
