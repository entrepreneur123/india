package com.yaari.chat

import com.yaari.chat.domain.ForeignText
import com.yaari.chat.domain.TranslateTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** The translation target for a locale, and which incoming lines are translated by themselves. */
class TranslateTest {
    private fun target(tag: String) = TranslateTargets.fromLocale(Locale.forLanguageTag(tag))

    /** Exactly the codes the translate function takes. */
    @Test fun targetsAreTheServersCodes() {
        val server = ("en hi bn ur pa mr gu ta te kn ml or as ne si pt-BR pt es es-419 fr de it nl pl ro cs sk hu el sv da no fi uk " +
            "ru bg sr hr tr ar fa he sw am ha yo zu vi th id ms fil my km lo ja ko zh-Hans zh-Hant kk uz az ka hy").split(' ')
        assertEquals(server.toSet(), TranslateTargets.codes.toSet())
        assertEquals(server.size, TranslateTargets.codes.size)
        assertTrue(TranslateTargets.names.values.all { it.isNotBlank() })
    }

    @Test fun localesMapToTargets() {
        assertEquals("en", target("en-US"))
        assertEquals("en", target("en-IN"))
        assertEquals("hi", target("hi-IN"))
        assertEquals("bn", target("bn-BD"))
        assertEquals("pt-BR", target("pt-BR"))
        assertEquals("pt-BR", target("pt")) // Android's plain Portuguese is Brazil's
        assertEquals("pt", target("pt-PT"))
        assertEquals("pt", target("pt-AO"))
        assertEquals("es", target("es"))
        assertEquals("es", target("es-ES"))
        assertEquals("es-419", target("es-MX"))
        assertEquals("es-419", target("es-US"))
        assertEquals("es-419", target("es-419"))
        assertEquals("zh-Hans", target("zh-CN"))
        assertEquals("zh-Hans", target("zh"))
        assertEquals("zh-Hant", target("zh-TW"))
        assertEquals("zh-Hant", target("zh-HK"))
        assertEquals("zh-Hant", target("zh-Hant"))
        assertEquals("zh-Hans", target("zh-Hans-HK"))
        assertEquals("he", target("iw"))
        assertEquals("he", target("he-IL"))
        assertEquals("id", target("in"))
        assertEquals("id", target("id-ID"))
        assertEquals("fil", target("tl-PH"))
        assertEquals("fil", target("fil-PH"))
        assertEquals("no", target("nb-NO"))
        assertEquals("ja", target("ja-JP"))
        assertEquals("en", target("xx")) // no such target: English
        assertEquals("en", TranslateTargets.fromLocale(null))
        assertEquals("vi", TranslateTargets.fromTag("vi-VN"))
        assertEquals("en", TranslateTargets.fromTag(""))
    }

    @Test fun otherScriptsAreForeign() {
        assertTrue(ForeignText.looksForeign("नमस्ते, कैसे हो?", "en"))
        assertTrue(ForeignText.looksForeign("Where are you from?", "hi"))
        assertTrue(ForeignText.looksForeign("Привет, как дела?", "pt-BR"))
        assertTrue(ForeignText.looksForeign("مرحبا كيف حالك", "en"))
        assertTrue(ForeignText.looksForeign("안녕하세요", "ja"))
        assertTrue(ForeignText.looksForeign("こんにちは、元気？", "zh-Hans"))
        assertTrue(ForeignText.looksForeign("你今天想去哪里玩", "ja")) // Han without kana: Chinese
        assertFalse(ForeignText.looksForeign("कैसे हो?", "hi"))
        assertFalse(ForeignText.looksForeign("কেমন আছেন?", "bn"))
        assertFalse(ForeignText.looksForeign("お元気ですか", "ja"))
        assertFalse(ForeignText.looksForeign("你好吗", "zh-Hant"))
        assertFalse(ForeignText.looksForeign("Как дела?", "uk")) // same script: can't tell, so no
    }

    @Test fun sameScriptGoesByCommonWords() {
        assertTrue(ForeignText.looksForeign("Kya haal hai?", "en"))
        assertTrue(ForeignText.looksForeign("hola, ¿cómo estás?", "en"))
        assertTrue(ForeignText.looksForeign("Oi, tudo bem com você?", "en"))
        assertTrue(ForeignText.looksForeign("Where are you from?", "pt-BR"))
        assertTrue(ForeignText.looksForeign("What do you do?", "es-419"))
        assertTrue(ForeignText.looksForeign("Bạn đến từ đâu?", "en"))
        assertTrue(ForeignText.looksForeign("Obrigado", "en")) // one telling word alone
        assertFalse(ForeignText.looksForeign("Where are you from?", "en"))
        assertFalse(ForeignText.looksForeign("Oi, tudo bem com você?", "pt-BR"))
        assertFalse(ForeignText.looksForeign("Oi, tudo bem com você?", "pt"))
        assertFalse(ForeignText.looksForeign("hola, ¿cómo estás?", "es-419"))
        assertFalse(ForeignText.looksForeign("Haha nice", "en")) // no telling words: no
        assertFalse(ForeignText.looksForeign("Arijit forever", "fr"))
    }

    /** Romanised Hindi reads fine to Hindi and Urdu speakers; to others it is foreign. */
    @Test fun romanisedHindi() {
        assertFalse(ForeignText.looksForeign("Kya haal hai?", "hi"))
        assertFalse(ForeignText.looksForeign("mera vote Goa ke liye", "ur"))
        assertTrue(ForeignText.looksForeign("mera vote Goa ke liye", "en"))
        assertTrue(ForeignText.looksForeign("Kya haal hai?", "bn"))
    }

    @Test fun emojiLinksAndNumbersAreNeverTranslated() {
        for (text in listOf("😂😂😂", "👍", "12345", "https://example.com/hola-como-estas", "  ", "k", "🔥 100% 🔥", "+91 98765 43210")) {
            assertFalse(text, ForeignText.looksForeign(text, "en"))
            assertFalse(text, ForeignText.looksForeign(text, "hi"))
            assertFalse(text, ForeignText.hasWords(text))
        }
        assertTrue(ForeignText.hasWords("ok"))
        // The link doesn't count; the words around it do.
        assertTrue(ForeignText.looksForeign("देखो https://example.com", "en"))
        assertFalse(ForeignText.looksForeign("see https://example.com/page", "en"))
    }
}
