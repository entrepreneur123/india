package com.lottodost.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.lottodost.app.data.DayFeed
import com.lottodost.app.data.Draw
import com.lottodost.app.data.IndexFeed
import com.lottodost.app.data.RemoteConfig
import com.lottodost.app.data.ResultFeed
import com.lottodost.app.data.ResultRepository
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZonedDateTime
import com.lottodost.app.domain.DrawSchedule

/**
 * Renders the real screens on the JVM with real result feeds captured from the pipeline
 * (18 Oct 2026 + the 19 Sep 2026 bumper day) and drives the ticket check flow.
 * PNGs land in app/build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi", application = android.app.Application::class)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val json = Json { ignoreUnknownKeys = true }
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private val latest: ResultFeed = json.decodeFromString(res("feed_latest.json"))
    private val bumperDay: DayFeed = json.decodeFromString(res("day_2026-09-19.json"))
    private val index: IndexFeed = json.decodeFromString(res("feed_index.json"))
    private val config: RemoteConfig = json.decodeFromString(res("feed_config.json"))
    private val allDraws: Map<String, Draw> = (latest.draws + bumperDay.draws).associateBy { it.id }
    private val state = ResultRepository.State(draws = allDraws, index = index, config = config,
        lastCheckedMs = ZonedDateTime.of(2026, 10, 18, 20, 58, 0, 0, DrawSchedule.IST).toInstant().toEpochMilli())
    private val evening = ZonedDateTime.of(2026, 10, 18, 21, 0, 0, 0, DrawSchedule.IST)
    private val wish = allDraws.getValue("2026-10-18_NL1PM")
    private val ganesh = allDraws.values.first { it.special && it.hasNumbers }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            LottoTheme { androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { content() } }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun results() = shot("01_results") { ResultsScreen(state, evening, {}, {}, {}) }

    @Test fun drawDetail() = shot("02_detail") { DrawDetailScreen(wish, onBack = {}) }

    @Test fun checkWinningTicket() {
        compose.setContent { LottoTheme { CheckScreen(state.drawList, index.upcoming, "2026-10-18", null, {}, {}, {}, {}, {}) } }
        // 3rd prize of Dear Wish 18-10-2026 includes 1873 -> any ticket ending in 1873 wins ₹500.
        compose.onNodeWithText("Ticket number").performTextInput("45B 21873")
        compose.onNodeWithText("Check").performClick()
        compose.onNodeWithText("Congratulations! You won").assertExists()
        compose.onNodeWithText("3rd Prize · ₹500").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/03_check_win.png")
    }

    @Test fun checkFromScan() {
        compose.setContent {
            LottoTheme { CheckScreen(state.drawList, index.upcoming, "2026-10-18", "DEAR\nWISH SUNDAY\n18/10/26\n74D 7O698", {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("74D 70698").assertExists() // OCR 'O' corrected to 0, shown for confirmation
        compose.onNodeWithText("Check").performClick()
        compose.onNodeWithText("1st Prize · ₹1,00,00,000").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/04_scan_first_prize.png")
    }

    @Test fun bumperTicketWins() {
        compose.setContent { LottoTheme { CheckScreen(state.drawList, index.upcoming, "2026-10-18", null, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Ticket number").performTextInput("A 801021")
        compose.onNodeWithText("Check").performClick()
        compose.onNodeWithText("1st Prize · ₹3,00,00,000").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/05_bumper_win.png")
    }

    @Test fun bumperDetail() = shot("06_bumper_detail") { DrawDetailScreen(ganesh, onBack = {}) }

    @Test fun liveDraw() {
        // 13:04 IST, before the 1 PM sheet is out: card shows LIVE + live-draw button.
        val noon = ZonedDateTime.of(2026, 10, 18, 13, 4, 0, 0, DrawSchedule.IST)
        val s = state.copy(draws = allDraws.filterKeys { !it.startsWith("2026-10-18") })
        shot("07_live") { ResultsScreen(s, noon, {}, {}, {}) }
        compose.onNodeWithText("LIVE").assertExists()
    }

    @Test fun guide() = shot("08_guide") { InfoScreen(state, evening, {}) }

    @Test fun languagePicker() = shot("09_language") { LanguageStep {} }

    @Config(qualifiers = "hi")
    @Test fun resultsHindi() = shot("10_results_hi") { ResultsScreen(state, evening, {}, {}, {}) }

    @Config(qualifiers = "bn")
    @Test fun detailBengali() = shot("11_detail_bn") { DrawDetailScreen(wish, onBack = {}) }

    @Config(qualifiers = "ne")
    @Test fun resultsNepali() = shot("16_results_ne") { ResultsScreen(state, evening, {}, {}, {}) }

    @Config(qualifiers = "pa")
    @Test fun guidePunjabi() = shot("17_guide_pa") { InfoScreen(state, evening, {}) }

    @Config(qualifiers = "mr")
    @Test fun checkMarathi() = shot("18_check_mr") { CheckScreen(state.drawList, index.upcoming, "2026-10-18", null, {}, {}, {}, {}, {}) }

    @Config(qualifiers = "w320dp-h640dp-mdpi")
    @Test fun smallPhone() = shot("12_small_320dp") { ResultsScreen(state, evening, {}, {}, {}) }

    @Config(qualifiers = "+night")
    @Test fun darkMode() = shot("13_dark") { ResultsScreen(state, evening, {}, {}, {}) }

    @Config(fontScale = 1.5f)
    @Test fun largeFont() = shot("14_font_150") { CheckScreen(state.drawList, index.upcoming, "2026-10-18", null, {}, {}, {}, {}, {}) }

    @Config(fontScale = 2.0f, qualifiers = "w360dp-h780dp-xhdpi")
    @Test fun hugeFontResults() = shot("15_font_200") { ResultsScreen(state, evening, {}, {}, {}) }

    @Test fun bulkCheck() {
        compose.setContent { LottoTheme { CheckScreen(state.drawList, index.upcoming, "2026-10-18", null, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Many tickets").performClick()
        // The newest draw (18-10 8 PM) is pre-selected.
        compose.onNodeWithText("Ticket numbers").performTextInput("45B 20700-20799")
        compose.onNodeWithText("Check").performClick()
        // Independently computed from the 18-10 8 PM sheet: 20759 (3rd, ₹500), 20733 and 20736 (5th, ₹120 each).
        compose.onNodeWithText("Checked: 100 · Won: 3 · Total: ₹740").assertExists()
        compose.onNodeWithText("45B 20759").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/19_bulk.png")
    }

    @Test fun numberHistory() {
        val history = com.lottodost.app.data.HistoryFeed(2, "2026-10-18T15:59:00+00:00", 90, latest.draws.filter { it.hasNumbers })
        compose.setContent { LottoTheme { HistoryScreen(history, false, {}, {}) } }
        compose.onNodeWithText("Ticket number").performTextInput("1873")
        compose.onNodeWithText("Search").performClick()
        // In the 7-day fixture, 1873 is only in the 18-10 1 PM 3rd prize.
        compose.onNodeWithText("Won 1 time in the last 90 days").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/20_history.png")
    }

    @Test fun shareImageRenders() {
        val bmp = ShareImage.render(ganesh, { "Prize $it" }, "footer", "Sat, 19 Sep · 6:00 PM")
        assert(bmp.width == 1080 && bmp.height in 1000..6000) { "${bmp.width}x${bmp.height}" }
        val weekly = ShareImage.render(wish, { "Prize $it" }, "footer", "Sun, 18 Oct · 1:00 PM")
        assert(weekly.height in 800..3000) { "${weekly.height}" }
    }
}
