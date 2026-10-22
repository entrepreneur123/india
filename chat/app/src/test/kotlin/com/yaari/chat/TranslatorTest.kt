package com.yaari.chat

import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.TranslatePrefs
import com.yaari.chat.domain.TranslateKind
import com.yaari.chat.domain.Translation
import com.yaari.chat.ui.LineView
import com.yaari.chat.ui.Translator
import com.yaari.chat.ui.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** The chat translator: asks once per line and language, four at a time, and handles the server's refusals. */
@OptIn(ExperimentalCoroutinesApi::class)
class TranslatorTest {
    private val asked = mutableListOf<Triple<TranslateKind, Long, String>>()
    private val notices = mutableListOf<UiText>()
    private var answer: suspend (TranslateKind, Long, String) -> Translation = { _, id, target -> Translation("[$target] line $id", same = false) }
    private var now = 0L

    private val server = object : Backend by FakeBackend(instant = true) {
        override suspend fun translate(kind: TranslateKind, id: Long, target: String): Translation {
            asked += Triple(kind, id, target)
            return answer(kind, id, target)
        }
    }

    /** On the test's clock but outside it (as the ViewModel's scope): a request that never ends doesn't hold the test. */
    private fun TestScope.translator(prefs: TranslatePrefs = TranslatePrefs.Memory()) =
        Translator(server, CoroutineScope(StandardTestDispatcher(testScheduler)), { notices += it }, prefs, clock = { now })
            .apply { followLocale(Locale.ENGLISH) }

    private val hindi = "आप कहाँ से हो?"

    @Test fun eachLineIsAskedOnceAndRemembered() = runTest {
        val tr = translator()
        repeat(3) { tr.autoTranslate(TranslateKind.CHAT, 7, hindi, fromMe = false, chat = "m1") } // recomposition, scrolling back
        advanceUntilIdle()
        assertEquals(1, asked.size)
        val v = tr.view(TranslateKind.CHAT, 7, hindi, fromMe = false)
        assertEquals(LineView.State.TRANSLATED, v.state)
        assertEquals("[en] line 7", v.text)
        // Another language is another request; back to English comes from memory.
        tr.choose("ja")
        tr.autoTranslate(TranslateKind.CHAT, 7, hindi, fromMe = false, chat = "m1")
        advanceUntilIdle()
        tr.choose(null)
        tr.autoTranslate(TranslateKind.CHAT, 7, hindi, fromMe = false, chat = "m1")
        advanceUntilIdle()
        assertEquals(listOf("en", "ja"), asked.map { it.third })
        // Same id in a room is a different line.
        tr.autoTranslate(TranslateKind.ROOM, 7, hindi, fromMe = false, chat = "r1")
        advanceUntilIdle()
        assertEquals(3, asked.size)
    }

    @Test fun mineEmojiAndMyLanguageAreLeftAlone() = runTest {
        val tr = translator()
        tr.autoTranslate(TranslateKind.CHAT, 1, hindi, fromMe = true, chat = "m1")
        tr.autoTranslate(TranslateKind.CHAT, 2, "😂😂", fromMe = false, chat = "m1")
        tr.autoTranslate(TranslateKind.CHAT, 3, "https://example.com", fromMe = false, chat = "m1")
        tr.autoTranslate(TranslateKind.CHAT, 4, "Where are you from?", fromMe = false, chat = "m1")
        advanceUntilIdle()
        assertTrue(asked.isEmpty())
        assertFalse(tr.view(TranslateKind.CHAT, 1, hindi, fromMe = true).canTranslate)
        assertFalse(tr.view(TranslateKind.CHAT, 2, "😂😂", fromMe = false).canTranslate)
        // My language can still be translated by hand (the menu); auto off leaves foreign lines alone.
        assertTrue(tr.view(TranslateKind.CHAT, 4, "Where are you from?", fromMe = false).canTranslate)
        tr.switchAuto(false)
        tr.autoTranslate(TranslateKind.CHAT, 5, hindi, fromMe = false, chat = "m1")
        advanceUntilIdle()
        assertTrue(asked.isEmpty())
        assertEquals(LineView.State.ORIGINAL, tr.view(TranslateKind.CHAT, 5, hindi, fromMe = false).state)
    }

    @Test fun atMostFourAtATime() = runTest {
        val release = CompletableDeferred<Unit>()
        var running = 0
        var most = 0
        answer = { _, id, _ ->
            running++
            most = maxOf(most, running)
            try {
                release.await()
            } finally {
                running--
            }
            Translation("t$id", same = false)
        }
        val tr = translator()
        (1L..10L).forEach { tr.autoTranslate(TranslateKind.ROOM, it, hindi, fromMe = false, chat = "r1") }
        runCurrent()
        assertEquals(Translator.MAX_PARALLEL, most)
        assertEquals(4, asked.size)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(10, asked.size)
        assertEquals(4, most)
        assertEquals("t10", tr.view(TranslateKind.ROOM, 10, hindi, fromMe = false).text)
    }

    @Test fun leavingAChatCancelsItsRequests() = runTest {
        val never = CompletableDeferred<Translation>()
        var cancelled = 0
        answer = { _, _, _ ->
            try {
                never.await()
            } catch (e: CancellationException) {
                cancelled++
                throw e
            }
        }
        val tr = translator()
        tr.autoTranslate(TranslateKind.CHAT, 1, hindi, fromMe = false, chat = "m1")
        tr.autoTranslate(TranslateKind.ROOM, 2, hindi, fromMe = false, chat = "r1")
        runCurrent()
        tr.retain(setOf("r1"))
        runCurrent()
        assertEquals(1, cancelled)
        // Back in that chat, the line is asked again.
        answer = { _, id, _ -> Translation("again $id", same = false) }
        tr.autoTranslate(TranslateKind.CHAT, 1, hindi, fromMe = false, chat = "m1")
        advanceUntilIdle()
        assertEquals("again 1", tr.view(TranslateKind.CHAT, 1, hindi, fromMe = false).text)
    }

    @Test fun notSwitchedOnYetSaysSoOnceAndHides() = runTest {
        answer = { _, _, _ -> throw BackendException("translate_unavailable") }
        val tr = translator()
        tr.autoTranslate(TranslateKind.CHAT, 1, hindi, fromMe = false, chat = "m1")
        tr.autoTranslate(TranslateKind.CHAT, 2, hindi, fromMe = false, chat = "m1")
        advanceUntilIdle()
        assertFalse(tr.available)
        assertEquals(listOf(UiText(R.string.translate_unavailable)), notices)
        tr.autoTranslate(TranslateKind.CHAT, 3, hindi, fromMe = false, chat = "m1")
        advanceUntilIdle()
        assertTrue(asked.size <= 2)
        val v = tr.view(TranslateKind.CHAT, 3, hindi, fromMe = false)
        assertEquals(LineView.State.ORIGINAL, v.state)
        assertFalse(v.canTranslate) // no menu item either
        assertTrue(tr.auto) // the setting itself is kept for when it is switched on
    }

    @Test fun showOriginalAndAlreadyInMyLanguage() = runTest {
        val tr = translator()
        tr.autoTranslate(TranslateKind.CHAT, 1, hindi, fromMe = false, chat = "m1")
        advanceUntilIdle()
        tr.showOriginal(TranslateKind.CHAT, 1)
        assertEquals(hindi, tr.view(TranslateKind.CHAT, 1, hindi, fromMe = false).text)
        tr.autoTranslate(TranslateKind.CHAT, 1, hindi, fromMe = false, chat = "m1") // stays original
        assertEquals(LineView.State.ORIGINAL, tr.view(TranslateKind.CHAT, 1, hindi, fromMe = false).state)
        tr.translate(TranslateKind.CHAT, 1, "m1") // from the menu again: from memory
        assertEquals(LineView.State.TRANSLATED, tr.view(TranslateKind.CHAT, 1, hindi, fromMe = false).state)
        assertEquals(1, asked.size)
        // The server says it is already English: nothing extra shows and the menu stops offering it.
        answer = { _, _, _ -> Translation("", same = true) }
        tr.translate(TranslateKind.CHAT, 2, "m1")
        assertEquals(LineView.State.LOADING, tr.view(TranslateKind.CHAT, 2, "Hi there you", fromMe = false).state)
        advanceUntilIdle()
        val v = tr.view(TranslateKind.CHAT, 2, "Hi there you", fromMe = false)
        assertEquals(LineView.State.ORIGINAL, v.state)
        assertEquals("Hi there you", v.text)
        assertFalse(v.canTranslate)
    }

    @Test fun refusalsFromTheMenuAreExplainedAndCanBeRetried() = runTest {
        answer = { _, _, _ -> throw BackendException("slow_down") }
        val tr = translator()
        tr.translate(TranslateKind.ROOM, 1, "r1")
        advanceUntilIdle()
        assertEquals(listOf(UiText(R.string.err_slow_down)), notices)
        // Auto rests for a while after slow_down (its own failures are silent).
        tr.autoTranslate(TranslateKind.ROOM, 2, hindi, fromMe = false, chat = "r1")
        advanceUntilIdle()
        assertEquals(1, asked.size)
        now += Translator.REST_MS
        answer = { _, _, _ -> throw BackendException("cannot_translate") }
        tr.translate(TranslateKind.ROOM, 1, "r1")
        advanceUntilIdle()
        assertEquals(UiText(R.string.translate_cannot), notices.last())
        assertTrue(tr.view(TranslateKind.ROOM, 1, hindi, fromMe = false).canTranslate)
        tr.autoTranslate(TranslateKind.ROOM, 3, hindi, fromMe = false, chat = "r1")
        advanceUntilIdle()
        assertEquals(3, asked.size)
        assertEquals(2, notices.size) // auto's failure said nothing
    }

    @Test fun settingsAreKeptAndTargetFollowsTheAppLanguage() = runTest {
        val prefs = TranslatePrefs.Memory()
        val tr = translator(prefs)
        assertEquals("en", tr.target)
        tr.followLocale(Locale.forLanguageTag("pt-BR"))
        assertEquals("pt-BR", tr.target)
        tr.choose("ko")
        tr.switchAuto(false)
        assertEquals("ko", prefs.target)
        assertFalse(prefs.auto)
        tr.followLocale(Locale.forLanguageTag("vi"))
        assertEquals("ko", tr.target)
        tr.choose("xx") // not a target: back to the app language
        assertEquals(null, prefs.target)
        assertEquals("vi", tr.target)
        assertEquals("ko", translator(TranslatePrefs.Memory(target = "ko")).target)
        assertEquals("en", translator(TranslatePrefs.Memory(target = "klingon")).target)
    }
}
