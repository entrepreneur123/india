package com.yaari.chat

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.github.takahirom.roborazzi.captureRoboImage
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.VideoStatus
import com.yaari.chat.model.Target
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme
import org.junit.Assert.assertEquals
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
 * Drives the real app UI on the JVM through every flow and saves screenshots to build/outputs/roborazzi.
 * Fails on any crash or missing screen element.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class AppFlowTest {
    @get:Rule val compose = createComposeRule()

    private var now = 1_000_000L
    private val dice = ControlledRandom()
    private val today = LocalDate.of(2026, 10, 18)
    private val backend = FakeBackend(dice, clock = { now }, today = { today }, instant = true)
    private val vm = AppViewModel(backend, DemoBilling(), clock = { now }, today = { today })

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun waitFor(text: String) = compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun send(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNodeWithContentDescription("Send").performClick()
    }

    @Test fun fullFlow() {
        compose.setContent { YaariTheme { YaariApp(vm) } }

        // 1. Phone + OTP (one number = one account)
        waitFor("Mobile number")
        shot("01_phone")
        field("Mobile number").performTextInput("9876543210")
        compose.onNodeWithText("Send OTP").performClick()
        waitFor("Enter the 6-digit code")
        field("OTP").performTextInput(FakeBackend.DEMO_OTP)
        shot("02_otp")
        compose.onNodeWithText("Verify").performClick()
        waitFor("Create your profile")

        // 2. Profile, 18+ only
        field("Name").performTextInput("Dev")
        compose.onNodeWithText("Male").performClick()
        field("DD").performTextInput("19")
        field("MM").performTextInput("10")
        field("YYYY").performTextInput("2008") // turns 18 tomorrow
        field("City").performTextInput("Mumbai")
        field("Bio").performTextInput("Chai lover, cricket fan")
        compose.onNodeWithText("I am 18 or older", substring = true).performScrollTo().performClick()
        shot("03_profile")
        compose.onNodeWithText("Start").performScrollTo().performClick()
        waitFor("only for adults")
        field("DD").performScrollTo().performTextReplacement("18") // exactly 18 today
        compose.onNodeWithText("Start").performScrollTo().performClick()
        waitFor("Meet someone new")

        // 3. Gender choice: paywall, then the 24 h gender pass
        shot("04_lobby")
        compose.onNodeWithText("Women").performClick()
        waitFor("needs Premium")
        shot("05_paywall")
        compose.onNodeWithText("Only gender choice", substring = true).performClick()
        waitFor("Meet someone new")
        compose.onNodeWithText("Women").performClick()
        compose.waitUntil(5_000) { vm.state.value.target == Target.FEMALE }

        // 4. Random chat; a free user's X shows the 5 s countdown; report & leave is always there
        compose.onNodeWithText("Start chatting").performClick()
        waitFor("View profile")
        shot("06_free_exit_locked")
        send("Hello!")
        waitFor("Hii")
        shot("07_chat")

        // 5. Partner profile, Instagram-style grid, over the chat
        compose.onNodeWithText("View profile", substring = true).performClick()
        waitFor("posts")
        shot("08_partner_profile")
        compose.onNodeWithContentDescription("Back").performClick()

        // 6. Incoming video call (the partner asks), declined
        send("video call?")
        waitFor("wants to video call")
        shot("09_incoming_video")
        compose.onNodeWithText("Decline").performClick()

        // 7. Exit after the lock: two choices
        now += 5_000
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithContentDescription("Leave").performClick()
        waitFor("Leave this chat?")
        shot("10_exit_choice")
        compose.onNodeWithText("Back to lobby").performClick()
        waitFor("Meet someone new")
        assertNull(vm.state.value.chat)

        // 8. Recent + message
        compose.onNodeWithText("Recent").performClick()
        waitFor("Message")
        shot("11_recent")
        compose.onNodeWithText("Message").performClick()
        send("Nice talking to you!")
        compose.waitUntil(5_000) { (vm.state.value.dm?.messages?.size ?: 0) >= 2 }
        shot("12_dm")
        compose.onNodeWithContentDescription("Back").performClick()

        // 9. Buy Premium from the profile tab, then a video call the partner accepts
        compose.onNodeWithText("Me").performClick()
        compose.onNodeWithText("Upgrade").performScrollTo().performClick()
        compose.onNodeWithText("Premium ·", substring = true).performClick()
        waitFor("Premium active")
        compose.onNodeWithText("Chat").performClick()
        compose.onNodeWithText("Start chatting").performClick()
        waitFor("View profile")
        compose.onNodeWithText("Video").performClick()
        compose.waitUntil(5_000) { vm.state.value.chat?.video == VideoStatus.ACCEPTED }
        waitFor("End")
        shot("13_video")
        compose.onNodeWithText("End").performClick()
        waitFor("View profile")

        // 10. Report & leave
        compose.onNodeWithContentDescription("Report and leave").performClick()
        waitFor("Report this person")
        shot("14_report")
        compose.onNodeWithText("Report & lobby").performClick()
        waitFor("Meet someone new")
        assertNull(vm.state.value.chat)

        // 11. My profile with a new post
        compose.onNodeWithText("Me").performClick()
        compose.onNodeWithText("New post").performClick()
        field("Write something").performTextInput("First day on Yaari ✨")
        shot("15_new_post")
        compose.onNodeWithText("Share").performClick()
        waitFor("1 post")
        shot("16_my_profile")
        assertEquals(1, vm.state.value.me!!.posts.size)
        assertTrue(vm.state.value.premium)

        // 12. Settings: rules, grievance officer, account deletion (Google Play requirement)
        compose.onNodeWithText("Settings & safety").performClick()
        waitFor("Grievance Officer")
        shot("17_settings")
        compose.onNodeWithText("Delete account").performScrollTo().performClick()
        waitFor("Delete your account?")
        compose.onNodeWithText("Delete").performClick()
        waitFor("Mobile number")
        assertNull(vm.state.value.me)
    }
}
