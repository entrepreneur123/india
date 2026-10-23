package com.yaari.chat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import com.yaari.chat.data.Backend
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.TranslateKind
import com.yaari.chat.domain.Translation
import com.yaari.chat.model.Message
import com.yaari.chat.ui.Bubble
import com.yaari.chat.ui.LocalTranslator
import com.yaari.chat.ui.MessageMenu
import com.yaari.chat.ui.Translator
import com.yaari.chat.ui.YaariTheme
import com.yaari.chat.ui.lineTranslation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/** A foreign line translates by itself, says so, and shows its original when asked; also right to left. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-w360dp-h720dp")
class TranslationScreensTest {
    @get:Rule val compose = createComposeRule()

    private var asked = 0
    private val server = object : Backend by FakeBackend(instant = true) {
        override suspend fun translate(kind: TranslateKind, id: Long, target: String): Translation {
            asked++
            return Translation("Where are you from?", same = false)
        }
    }
    private val translator = Translator(server, CoroutineScope(SupervisorJob() + Dispatchers.Main), {}).apply { followLocale(Locale.ENGLISH) }
    private val hindi = "आप कहाँ से हो?"

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun show(direction: LayoutDirection) = compose.setContent {
        YaariTheme {
            CompositionLocalProvider(LocalTranslator provides translator, LocalLayoutDirection provides direction) {
                val m = Message(42, fromMe = false, text = hindi, at = 0)
                val tr = lineTranslation(TranslateKind.CHAT, m.id, m.text, m.fromMe, chat = "m1")
                MessageMenu(m.text, tr) { Bubble(m, translation = tr) }
            }
        }
    }

    @Test fun translatesByItselfAndShowsTheOriginal() {
        show(LayoutDirection.Ltr)
        waitFor("Translated · Show original")
        waitFor("Where are you from?")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/translate_01_translated.png")
        compose.onAllNodes(hasText("Translated · Show original"))[0].performClick()
        waitFor(hindi)
        // Translate again from the long-press menu: from memory, no second request.
        compose.onAllNodes(hasText(hindi))[0].performTouchInput { longClick() }
        compose.onAllNodes(hasText("Translate"))[0].performClick()
        waitFor("Where are you from?")
        assertEquals(1, asked)
    }

    @Test fun rightToLeft() {
        show(LayoutDirection.Rtl)
        waitFor("Translated · Show original")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/translate_02_rtl.png")
    }
}
