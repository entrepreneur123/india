package com.lottodost.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.lottodost.app.data.ResultFeed
import com.lottodost.app.data.ResultRepository
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screens on the JVM with a real result feed captured from the pipeline
 * (18 Oct 2026) and drives the ticket check flow. PNGs land in app/build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi", application = android.app.Application::class)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val feed: ResultFeed = Json { ignoreUnknownKeys = true }.decodeFromString(
        javaClass.classLoader!!.getResource("latest_sample.json")!!.readText())
    private val wish = feed.draws.first { it.slot == "NL1PM" && it.hasNumbers }

    @Test fun results() {
        compose.setContent { LottoTheme { ResultsScreen(ResultRepository.State(feed = feed), {}, {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/01_results.png")
    }

    @Test fun drawDetail() {
        compose.setContent { LottoTheme { DrawDetailScreen(wish, onBack = {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/02_detail.png")
    }

    @Test fun checkWinningTicket() {
        compose.setContent {
            LottoTheme { CheckScreen(feed.draws, null, {}, {}, {}, {}) }
        }
        // 3rd prize of Dear Wish 18-10-2026 includes 1873 -> any ticket ending in 1873 wins ₹500.
        compose.onNodeWithText("Ticket number").performTextInput("45B 21873")
        compose.onNodeWithText("Check").performClick()
        compose.onNodeWithText("Congratulations! You won").assertExists()
        compose.onNodeWithText("3rd Prize · ₹500").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/03_check_win.png")
    }

    @Test fun checkFromScan() {
        compose.setContent {
            LottoTheme {
                CheckScreen(feed.draws, "DEAR\nWISH SUNDAY\n18/10/26\n74D 7O698", {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("74D 70698").assertExists() // OCR 'O' corrected to 0, shown for confirmation
        compose.onNodeWithText("Check").performClick()
        compose.onNodeWithText("1st Prize · ₹1,00,00,000").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/04_scan_first_prize.png")
    }

    @Config(qualifiers = "hi")
    @Test fun resultsHindi() {
        compose.setContent { LottoTheme { ResultsScreen(ResultRepository.State(feed = feed), {}, {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/05_results_hi.png")
    }

    @Config(qualifiers = "bn")
    @Test fun detailBengali() {
        compose.setContent { LottoTheme { DrawDetailScreen(wish, onBack = {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/06_detail_bn.png")
    }
}
