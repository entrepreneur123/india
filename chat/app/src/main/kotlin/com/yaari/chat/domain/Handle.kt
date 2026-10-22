package com.yaari.chat.domain

import com.yaari.chat.model.HandleCheck
import java.text.Normalizer
import java.util.Locale

/**
 * IDs ("handles", 040_handles_looks.sql): the server's handle_valid, handle_reserved and name_plain, code point for code
 * point, so a field can say what is wrong while the person types. The server stays the judge (handle_check, set_handle).
 * Strings are walked by code point, never by char: the fancy-font letters (𝓛𝓲𝓼𝓪) and emoji live outside the BMP.
 */
object Handle {
    const val MIN = 2
    const val MAX = 24

    private val SPECIALS = setOf(33, 35, 36, 37, 38, 40, 41, 42, 43, 44, 45, 46, 47, 58, 59, 61, 63, 91, 93, 94, 95, 123, 124, 125, 126)
    private val IPA_LETTERS = setOf(595, 596, 599, 601, 603)
    private val RESERVED = setOf("support", "staff", "help", "team", "system", "security", "root", "null", "undefined")
    private val RESERVED_START = listOf("yaari", "admin", "official", "moderator")

    private enum class Kind { DIGIT, LATIN, GREEK, CYRILLIC, LETTER, SPECIAL, NONE }

    private fun kind(c: Int): Kind = when {
        c in 48..57 -> Kind.DIGIT
        c in 65..90 || c in 97..122 -> Kind.LATIN
        c in SPECIALS -> Kind.SPECIAL
        // Latin with accents (Latin-1 letters, Extended-A/B, Vietnamese & co.) and the few IPA letters real alphabets use
        (c in 192..591 && c != 215 && c != 247) || c in 7680..7935 || c in IPA_LETTERS -> Kind.LATIN
        c in 902..974 && c != 903 -> Kind.GREEK
        c in 1024..1279 -> Kind.CYRILLIC
        c in 1329..1366 || c in 1377..1415 -> Kind.LETTER // Armenian
        c in 1488..1514 -> Kind.LETTER // Hebrew
        c in 1568..1610 || c in 1632..1641 || c in 1649..1747 || c in 1776..1785 -> Kind.LETTER // Arabic, Urdu, Persian
        c in 2304..3583 -> Kind.LETTER // Devanagari .. Sinhala
        c in 3584..3839 -> Kind.LETTER // Thai, Lao
        c in 4096..4255 -> Kind.LETTER // Myanmar
        c in 4304..4351 -> Kind.LETTER // Georgian
        c in 4608..4954 || c in 4969..4988 -> Kind.LETTER // Ethiopic
        c in 6016..6109 || c in 6112..6121 -> Kind.LETTER // Khmer
        c in 12353..12438 || c in 12445..12447 -> Kind.LETTER // Hiragana
        c in 12449..12538 || c in 12540..12543 -> Kind.LETTER // Katakana
        c in 12593..12686 -> Kind.LETTER // Hangul jamo
        c in 13312..19903 || c in 19968..40959 -> Kind.LETTER // CJK
        c in 44032..55203 -> Kind.LETTER // Hangul
        else -> Kind.NONE
    }

    private fun codePoints(s: String): List<Int> {
        val out = ArrayList<Int>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s.codePointAt(i)
            out += c
            i += Character.charCount(c)
        }
        return out
    }

    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    /** What set_handle and handle_check do first: spaces off both ends (btrim), then NFC. */
    fun normalize(raw: String): String = nfc(raw.trim(' '))

    /**
     * handle_valid: [p] in NFC, 2–24 code points, each a letter of a real script, a digit or an allowed ASCII special,
     * at least one letter or digit, and at most one of Latin, Greek and Cyrillic.
     */
    fun valid(p: String?): Boolean {
        if (p == null || p != nfc(p)) return false
        val cps = codePoints(p)
        if (cps.size !in MIN..MAX) return false
        val kinds = cps.map(::kind)
        if (Kind.NONE in kinds) return false
        val word = kinds.any { it != Kind.SPECIAL }
        val alphabets = listOf(Kind.LATIN, Kind.GREEK, Kind.CYRILLIC).count { it in kinds }
        return word && alphabets <= 1
    }

    /** handle_reserved: staff look-alikes nobody may take. */
    fun reserved(p: String): Boolean {
        val low = p.lowercase(Locale.ROOT)
        return RESERVED_START.any { low.startsWith(it) } || low in RESERVED
    }

    /**
     * name_plain: names keep emoji, but not the fancy-font letters (a free font) nor stacks of accents (zalgo). [p] in
     * NFC (the server normalises a name before it checks it).
     */
    fun plainName(p: String): Boolean {
        if (p != nfc(p)) return false
        var marks = 0
        for (c in codePoints(p)) {
            val fancy = c in 119808..120831 || // Mathematical Alphanumeric Symbols (𝓛𝓲𝓼𝓪, 𝐋𝐢𝐬𝐚, 𝕃𝕚𝕤𝕒)
                c in 65281..65376 || // fullwidth (Ｌｉｓａ)
                c in 7424..7615 || // small capitals, modifier letters (ʟɪsᴀ, ᴸⁱˢᵃ)
                (c in 592..767 && c !in IPA_LETTERS) || // IPA letters (upside-down text), modifier letters
                c in 8304..8351 || // superscripts, subscripts
                c in 8448..8527 || // letterlike (ℒ ℓ ℐ)
                c in 9312..9471 || // circled / parenthesised (ⓛⓘⓢⓐ)
                c in 127232..127461 || // enclosed alphanumerics (🄻🄸🅂🄰), not the flag letters after them
                c in 42784..43007 || // Latin Extended-D (ꜱ small capitals)
                c in 5024..5119 || c in 43888..43967 || // Cherokee (Ꮮ Ꭵ Ꮥ Ꭺ)
                c in 5120..5759 // Canadian syllabics (ᒪ ᔕ ᗩ)
            if (fancy) return false
            val mark = c in 768..879 || c in 6832..6911 || c in 7616..7679 || c in 8400..8447 || c in 65056..65071
            marks = if (mark) marks + 1 else 0
            if (marks > 1) return false
        }
        return true
    }

    /**
     * What a field can tell before asking the server, in handle_check's order: [HandleCheck.INVALID], [HandleCheck.MINE]
     * (exactly my ID, [current]), [HandleCheck.RESERVED]; null when only the server knows (free or taken).
     */
    fun local(raw: String, current: String?): HandleCheck? {
        val h = normalize(raw)
        return when {
            !valid(h) -> HandleCheck.INVALID
            h == current -> HandleCheck.MINE
            reserved(h) -> HandleCheck.RESERVED
            else -> null
        }
    }

    /**
     * An ID to offer at sign-up from [name]: its letters and digits (spaces become "_", at most 14), then "_" and
     * [number] (4 digits). Names whose letters can't make an ID fall back to their plain Latin letters (as the server's
     * free IDs), else "user".
     */
    fun suggest(name: String, number: Int): String {
        val tail = "_" + number.toString().padStart(4, '0').takeLast(4)
        val kept = StringBuilder()
        for (c in codePoints(nfc(name.trim()))) {
            when {
                Character.isWhitespace(c) -> if (kept.isNotEmpty() && !kept.endsWith("_")) kept.append('_')
                kind(c) !in setOf(Kind.NONE, Kind.SPECIAL) || c == '.'.code || c == '_'.code -> kept.appendCodePoint(c)
            }
        }
        val own = codePoints(kept.toString().trim('_', '.')).take(14).joinToString("") { String(Character.toChars(it)) }.trim('_', '.')
        val plain = name.lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }.take(14).trim('.', '_')
        return listOf(own, plain).filter { it.isNotEmpty() }.map { it + tail }.firstOrNull { valid(it) && !reserved(it) } ?: "user$tail"
    }
}
