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
import com.yaari.chat.data.Backend
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.PartnerEvent
import com.yaari.chat.model.Profile
import com.yaari.chat.model.Target
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** No delays and no randomness, so the UI flow is deterministic. */
private class InstantBackend : Backend {
    private val accounts = mutableMapOf<String, Profile>()
    override suspend fun sendOtp(phone: String) = Unit
    override suspend fun verifyOtp(phone: String, code: String) = code == FakeBackend.DEMO_OTP
    override fun accountFor(phone: String) = accounts[phone]
    override fun register(phone: String, profile: Profile) { accounts[phone] = profile }
    override suspend fun findPartner(me: Profile, target: Target, exclude: Set<String>) =
        FakeBackend.POOL.first { target.accepts(it.gender) && it.id !in exclude }
    override suspend fun partnerReact(partner: Profile, incoming: String?, canLeave: Boolean) = PartnerEvent.Reply("Hii 😊 where are you from?")
    override suspend fun answerVideoRequest(partner: Profile) = true
}

/**
 * Drives the real app UI on the JVM through every prototype flow and saves screenshots
 * to build/outputs/roborazzi. Fails on any crash or missing screen element.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class AppFlowTest {
    @get:Rule val compose = createComposeRule()

    private var now = 0L
    private val vm = AppViewModel(InstantBackend(), clock = { now }, wallClock = { 1_760_800_000_000L + now }, today = { LocalDate.of(2026, 10, 18) })

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun waitFor(text: String) = compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    @Test fun fullPrototypeFlow() {
        compose.setContent { YaariTheme { YaariApp(vm) } }

        // 1. Phone + OTP (one number = one account)
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
        assertNotNull(vm.state.value.me)

        // 3. Lobby: gender choice is Premium
        shot("04_lobby")
        compose.onNodeWithText("Women").performClick()
        waitFor("needs Premium")
        shot("05_paywall")
        compose.onNodeWithText("Turn on Premium").performClick()
        compose.onNodeWithText("Women").performClick()
        assertEquals(Target.FEMALE, vm.state.value.target)

        // 4. Random chat with the partner's profile on top
        compose.onNodeWithText("Start chatting").performClick()
        waitFor("View profile")
        compose.onNode(hasSetTextAction()).performTextInput("Hello!")
        compose.onNodeWithContentDescription("Send").performClick()
        waitFor("where are you from")
        shot("06_chat")

        // 5. Partner profile, Instagram-style grid, opened over the chat
        compose.onNodeWithText("View profile", substring = true).performClick()
        waitFor("posts")
        shot("07_partner_profile")
        compose.onNodeWithContentDescription("Back").performClick()

        // 6. Video call (Premium, partner accepts)
        compose.onNodeWithText("Video").performClick()
        waitFor("End")
        shot("08_video")
        compose.onNodeWithText("End").performClick()
        waitFor("View profile")

        // 7. Exit: two choices
        now += 1_000
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithContentDescription("Leave").performClick()
        waitFor("Leave this chat?")
        shot("09_exit_choice")
        compose.onNodeWithText("Back to lobby").performClick()
        waitFor("Meet someone new")
        assertNull(vm.state.value.session)

        // 8. Recent + message
        compose.onNodeWithText("Recent").performClick()
        waitFor("Message")
        shot("10_recent")
        compose.onNodeWithText("Message").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Nice talking to you!")
        compose.onNodeWithContentDescription("Send").performClick()
        waitFor("where are you from")
        shot("11_dm")
        compose.onNodeWithContentDescription("Back").performClick()

        // 9. Free user: X shows a countdown, Report & leave is always available
        vm.setPremium(false)
        compose.onNodeWithText("Chat").performClick()
        compose.onNodeWithText("Start chatting").performClick()
        waitFor("View profile")
        shot("12_free_exit_locked")
        compose.onNodeWithContentDescription("Report and leave").performClick()
        waitFor("Report this person")
        shot("13_report")
        compose.onNodeWithText("Report & lobby").performClick()
        waitFor("Meet someone new")
        assertEquals(1, vm.state.value.blocked.size)

        // 10. My profile with a new post
        compose.onNodeWithText("Me").performClick()
        compose.onNodeWithText("New post").performClick()
        field("Write something").performTextInput("First day on Yaari ✨")
        shot("14_new_post")
        compose.onNodeWithText("Share").performClick()
        waitFor("1 post")
        shot("15_my_profile")
    }
}
