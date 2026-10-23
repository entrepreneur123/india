package com.yaari.chat

import com.yaari.chat.domain.Handle
import com.yaari.chat.model.HandleCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phone's copy of the ID and name rules (040_handles_looks.sql): the same valid and invalid examples as the server's
 * own test (server/test_handles_looks.sql), so the two can't drift apart unnoticed.
 */
class HandleTest {
    @Test fun validIds() {
        listOf(
            "L._.1sA", "lisa", "ab", "priya_99", "राहुल", "রাহুল", "ਰਾਹੁਲ", "ராகுல்", "ರಾಹುಲ್", "രാഹുൽ", "محمد", "علی۱۲", "דני", "Ελένη",
            "Иван", "ნინო", "Հայկ", "สมชาย", "မောင်မောင်", "さくら", "カタカナ", "小明", "지민", "ㅋㅋㅋ", "Nguyễn", "Şükrü", "Ədalət", "Ọlá",
            "a!#$%&()*+,-./", "x:;=?[]^_{|}~", "राहुल_99", "abcdefghijklmnopqrstuvwx",
        ).forEach { assertTrue("valid: $it", Handle.valid(it)) }
    }

    @Test fun invalidIds() {
        listOf(
            null, "", "a", "지", "abcdefghijklmnopqrstuvwxy", "li sa", "lisa😀", "😀😀😀", "𝓛𝓲𝓼𝓪", "Ｌｉｓａ", "ʟɪsᴀ", "ⓛⓘⓢⓐ", "ℒisa",
            "lisᵃ", "ꜱhiv", "ᏞᎥᏕᎪ", "ᒪIᔕᗩ", "ʇxǝʇ", "li@sa", "li\"sa", "li'sa", "li<sa", "li\\sa", "li`sa", "...", "!!!",
            "аdmin", "pаypal", "Ελa", "lis‍a", "lis​a", "Nguyễn",
        ).forEach { assertFalse("invalid: $it", Handle.valid(it)) }
    }

    @Test fun lengthCountsCodePointsNotChars() {
        // 12 supplementary-plane CJK characters are 24 chars but only 12 code points; they are not in the allowed CJK block.
        assertFalse(Handle.valid("𠀀".repeat(2)))
        // 24 code points of Hangul are fine, 25 are not.
        assertTrue(Handle.valid("가".repeat(24)))
        assertFalse(Handle.valid("가".repeat(25)))
    }

    @Test fun reservedIds() {
        assertTrue(Handle.reserved("Yaari_Official"))
        assertTrue(Handle.reserved("admin.1"))
        assertTrue(Handle.reserved("Support"))
        assertFalse(Handle.reserved("helpful"))
        assertFalse(Handle.reserved("teamwork"))
    }

    @Test fun plainNames() {
        listOf("Lisa", "Lisa 💖", "राहुल शर्मा", "Nguyễn Văn A", "محمد علی", "지민", "🇮🇳 Priya", "Zoë", "José")
            .forEach { assertTrue("plain name: $it", Handle.plainName(it)) }
        listOf("𝓛𝓲𝓼𝓪", "𝐋𝐢𝐬𝐚", "Ｌｉｓａ", "ʟɪsᴀ", "ⓛⓘⓢⓐ", "🄻🄸🅂🄰", "ℒisa", "ᏞᎥᏕᎪ", "ᒪIᔕᗩ", "ʇxǝʇ", "L̃̂isa")
            .forEach { assertFalse("fancy name: $it", Handle.plainName(it)) }
    }

    @Test fun inputIsTrimmedAndComposedFirst() {
        assertEquals("L._.1sA", Handle.normalize(" L._.1sA "))
        assertEquals("José", Handle.normalize("José"))
        assertTrue(Handle.valid(Handle.normalize("José")))
    }

    @Test fun whatTheFieldKnowsBeforeAsking() {
        assertEquals(HandleCheck.INVALID, Handle.local("li sa", null))
        assertEquals(HandleCheck.RESERVED, Handle.local("yaari.team", null))
        assertEquals(HandleCheck.MINE, Handle.local(" lisa_4821 ", "lisa_4821"))
        assertNull("a new spelling of mine: only the server can say", Handle.local("Lisa_4821", "lisa_4821"))
        assertNull(Handle.local("L._.1sA", "lisa_4821"))
    }

    @Test fun suggestionsFromTheName() {
        assertEquals("Priya_Sharma_0042", Handle.suggest("Priya Sharma", 42))
        assertEquals("राहुल_4821", Handle.suggest("  राहुल  ", 4821))
        // Fancy letters and emoji are left out; what is left must still be a valid ID.
        assertEquals("Lisa_0007", Handle.suggest("Lisa 💖", 7))
        assertEquals("user_1234", Handle.suggest("😀😀", 1234))
        // Mixed alphabets can't be one ID: the plain Latin letters, as the server's free IDs.
        assertEquals("priya_0001", Handle.suggest("Priya Иван", 1))
        assertEquals("user_0002", Handle.suggest("Admin", 2))
        assertTrue(Handle.valid(Handle.suggest("Nguyễn Văn An Nguyễn Văn An", 99)))
    }
}
