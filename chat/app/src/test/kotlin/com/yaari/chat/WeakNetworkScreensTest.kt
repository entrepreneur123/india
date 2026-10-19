package com.yaari.chat

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.Match
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Media
import com.yaari.chat.model.Message
import com.yaari.chat.model.Target
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The offline banner, "Reconnecting…" while searching, and a failed message coming back into the composer. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class WeakNetworkScreensTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 18)
    private val fake = FakeBackend(ControlledRandom(), today = { today }, instant = true)
    private var searchDown = false
    private var sendDown = false
    private val flaky = object : Backend by fake {
        override suspend fun findMatch(target: Target): Match? =
            if (searchDown) throw BackendException("network") else fake.findMatch(target)
        override suspend fun send(matchId: String, text: String?, media: Media?): Message =
            if (sendDown) throw BackendException("network") else fake.send(matchId, text, media)
    }
    private val vm = AppViewModel(flaky, DemoBilling(), today = { today })
    private val online = MutableStateFlow(true)

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/net_$name.png")
    }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun gone(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isEmpty() }

    @Test fun weakNetworkScreens() {
        compose.setContent { YaariTheme { YaariApp(vm, online) } }
        compose.runOnUiThread { vm.requestOtp(Countries.byIso("IN")!!, "9876543210") }
        waitFor("We sent a 6-digit code")
        compose.runOnUiThread { vm.verifyOtp(FakeBackend.DEMO_OTP) }
        waitFor("Create your profile")
        compose.runOnUiThread { vm.createProfile(ProfileDraft("Dev", Gender.MALE, "1", "1", "2000", "Mumbai", "", agreed = true)) }
        waitFor("Meet someone new")

        // No network: a slim banner on top; the lobby stays usable.
        online.value = false
        waitFor("No internet connection")
        shot("01_offline_lobby")
        online.value = true
        gone("No internet connection")

        // A search whose requests fail keeps going and says so.
        searchDown = true
        compose.onNodeWithText("Start chatting").performClick()
        waitFor("Reconnecting")
        shot("02_search_reconnecting")
        searchDown = false
        waitFor("View profile") // the next attempt (after 2 s) finds someone

        // A message that could not be sent comes back into the composer.
        sendDown = true
        compose.onNode(hasSetTextAction()).performTextInput("Are you there?")
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction() and hasText("Are you there?")).fetchSemanticsNodes().isNotEmpty() }
        online.value = false
        waitFor("No internet connection")
        shot("03_chat_offline_draft_kept")
        online.value = true
        sendDown = false
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(5_000) { vm.state.value.chat?.messages?.any { it.text == "Are you there?" } == true }
        compose.onNodeWithText("Are you there?").assertExists()
    }
}
