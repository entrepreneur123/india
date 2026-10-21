package com.yaari.chat

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The main screens in Hindi: Devanagari renders and long words fit the buttons (screenshots for review). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "hi-w393dp-h852dp-xxhdpi")
class HindiScreensTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 18)
    private val backend = FakeBackend(ControlledRandom(), today = { today }, instant = true)
    private val vm = AppViewModel(backend, DemoBilling(), today = { today })

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/hi_$name.png")
    }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun hindiScreens() {
        compose.setContent { YaariTheme { YaariApp(vm) } }
        waitFor("मोबाइल नंबर")
        shot("01_phone")
        compose.runOnUiThread { vm.requestOtp(Countries.byIso("IN")!!, "9876543210") }
        waitFor("6 अंकों का कोड")
        compose.runOnUiThread { vm.verifyOtp(FakeBackend.DEMO_OTP) }
        waitFor("अपनी प्रोफ़ाइल बनाएँ")
        shot("02_profile")
        compose.runOnUiThread { vm.createProfile(ProfileDraft("Dev", Gender.MALE, "1", "1", "2000", "Mumbai", "Chai lover", agreed = true)) }
        waitFor("किसी नए से मिलें")
        shot("03_lobby")
        compose.onNodeWithText("लड़कियाँ").performClick()
        waitFor("Plus चाहिए")
        shot("04_paywall")
        compose.onNode(androidx.compose.ui.test.hasContentDescription("अभी नहीं")).performClick()
        compose.onNodeWithText("चैट शुरू करें").performClick()
        waitFor("प्रोफ़ाइल देखें")
        shot("05_chat")
        compose.onNode(androidx.compose.ui.test.hasContentDescription("रिपोर्ट करके निकलें")).performClick()
        waitFor("इस व्यक्ति की रिपोर्ट करें")
        shot("06_report")
        compose.onNodeWithText("रिपोर्ट करें, लॉबी").performClick()
        waitFor("किसी नए से मिलें")
        compose.onAllNodes(androidx.compose.ui.test.hasText("रूम"))[0].performClick()
        waitFor("अभी लाइव") // voice rooms first
        shot("07_rooms")
        compose.runOnUiThread { vm.voice.open("v-chai") }
        compose.waitUntil(5_000) { vm.voice.state.value?.lines != null }
        compose.runOnUiThread { vm.voice.icebreaker() }
        compose.waitUntil(5_000) { vm.voice.state.value!!.lines!!.any { it is com.yaari.chat.model.VoiceLine.Icebreaker && it.fromMe } }
        shot("07b_talk_room")
        compose.runOnUiThread { vm.voice.close() }
        compose.waitUntil(5_000) { vm.voice.state.value == null }
        compose.onAllNodes(androidx.compose.ui.test.hasText("हाल के"))[0].performClick()
        waitFor("रिक्वेस्ट (1)")
        shot("08_friends")
        compose.onNodeWithText("मैं").performClick()
        waitFor("प्रोफ़ाइल बदलें")
        shot("09_me")
        compose.onNode(androidx.compose.ui.test.hasContentDescription("सेटिंग्स")).performClick()
        waitFor("शिकायत अधिकारी")
        shot("10_settings")
    }
}
