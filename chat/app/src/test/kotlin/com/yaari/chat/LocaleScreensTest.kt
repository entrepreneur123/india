package com.yaari.chat

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
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

/**
 * The main screens in Portuguese (Brazil), Vietnamese and Bengali on a narrow 360 dp phone (the common low-end
 * width), so long words have to fit buttons, tabs and chips. Texts come from the resources of the locale under test.
 */
abstract class LocaleScreens(private val prefix: String) {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 18)
    private val backend = FakeBackend(ControlledRandom(), today = { today }, instant = true)
    private val vm = AppViewModel(backend, DemoBilling(), today = { today })
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${prefix}_$name.png")
    }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun tap(text: String) = compose.onAllNodes(hasText(text))[0].performClick()

    @Test fun screens() {
        compose.setContent { YaariTheme { YaariApp(vm) } }
        waitFor(s(R.string.mobile_number))
        shot("01_phone")
        compose.runOnUiThread { vm.requestOtp(Countries.byIso("IN")!!, "9876543210") }
        waitFor(s(R.string.otp_title))
        compose.runOnUiThread { vm.verifyOtp(FakeBackend.DEMO_OTP) }
        waitFor(s(R.string.create_profile))
        shot("02_profile")
        compose.runOnUiThread { vm.createProfile(ProfileDraft("Dev", Gender.MALE, "1", "1", "2000", "Mumbai", "Chai lover", agreed = true)) }
        waitFor(s(R.string.lobby_title))
        shot("03_lobby")
        tap(s(R.string.target_women))
        waitFor(s(R.string.premium_needed_gender))
        shot("04_paywall")
        compose.onNode(hasContentDescription(s(R.string.not_now))).performClick()
        tap(s(R.string.start_chatting))
        waitFor(s(R.string.chat_subtitle, "").trim(' ', '·'))
        shot("05_chat")
        compose.onNode(hasContentDescription(s(R.string.report_and_leave))).performClick()
        waitFor(s(R.string.report_title))
        shot("06_report")
        tap(s(R.string.report_lobby))
        waitFor(s(R.string.lobby_title))
        tap(s(R.string.tab_rooms))
        waitFor(s(R.string.voice_live_now)) // voice rooms first
        shot("07_rooms")
        tap(s(R.string.tab_recent))
        waitFor(s(R.string.friend_requests, 1))
        shot("08_friends")
        tap(s(R.string.tab_me))
        waitFor(s(R.string.edit_profile))
        shot("09_me")
        compose.onNode(hasContentDescription(s(R.string.settings))).performClick()
        waitFor(s(R.string.grievance_officer))
        shot("10_settings")
        tap(s(R.string.language))
        waitFor("Tiếng Việt")
        shot("11_language")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h780dp-xxhdpi")
class PortugueseScreensTest : LocaleScreens("pt")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "vi-w360dp-h780dp-xxhdpi")
class VietnameseScreensTest : LocaleScreens("vi")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "bn-w360dp-h780dp-xxhdpi")
class BengaliScreensTest : LocaleScreens("bn")
