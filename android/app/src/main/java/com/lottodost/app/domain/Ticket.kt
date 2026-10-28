package com.lottodost.app.domain

import java.util.Locale

/**
 * A ticket number as printed.
 *  - D5 (Dear weekly):           "39C 06366"  serial 34-99, series A B C D E G H J K L (no F, I), 5 digits
 *  - D4 (Dear Laxmi):            "12 3456"    serial, 4 digits
 *  - B6 (bumper / monthly):      "A 801021"   series letter(s), 6 digits
 *  - digits only (4-6):          "06366"      checks every tier that does not need the prefix
 *
 * All formatting uses Locale.ROOT: a Bengali/Hindi default locale would otherwise print
 * native digits ("৭৪D") and silently break comparisons with the feed.
 */
data class Ticket(
    val prefix: String?,   // "39C", "12", "A" or null
    val digits: String,
) {
    enum class Format { D5, D4, B6, DIGITS }

    init {
        require(digits.length in 4..6 && digits.all { it in '0'..'9' })
    }

    val format: Format
        get() = when {
            prefix == null -> Format.DIGITS
            digits.length == 5 && prefix.length == 3 -> Format.D5
            digits.length == 4 && prefix.length == 2 && prefix.all { it in '0'..'9' } -> Format.D4
            digits.length == 6 && prefix.all { it in 'A'..'Z' } -> Format.B6
            else -> Format.DIGITS
        }

    fun display(): String = if (prefix == null) digits else "$prefix $digits"

    companion object {
        const val SERIES_LETTERS = "ABCDEGHJKL"
    }
}

object TicketParser {
    private val D5 = Regex("""^(\d{2})\s*([A-Z])\s*(\d{5})$""")
    private val D4 = Regex("""^(\d{2})\s+(\d{4})$""")
    private val B6 = Regex("""^([A-Z]{1,2})\s*(\d{6})$""")
    private val DIGITS = Regex("""^\d{4,6}$""")

    /** Parses what the user typed. Returns null when it is not a checkable number. */
    fun parseManual(raw: String): Ticket? {
        // Devanagari/Bengali keyboards type native digits (०-९, ০-৯): normalise to ASCII.
        val ascii = raw.map { c -> if (c.isDigit() && c !in '0'..'9') '0' + Character.getNumericValue(c) else c }
            .joinToString("")
        val s = ascii.trim().uppercase(Locale.ROOT).replace(Regex("""[-_./,]"""), " ").replace(Regex("""\s+"""), " ")
        D5.matchEntire(s)?.let { m ->
            val serial = m.groupValues[1].toInt()
            val series = m.groupValues[2][0]
            if (serial !in 34..99 || series !in Ticket.SERIES_LETTERS) return null
            return Ticket(m.groupValues[1] + series, m.groupValues[3])
        }
        D4.matchEntire(s)?.let { m -> return Ticket(m.groupValues[1], m.groupValues[2]) }
        B6.matchEntire(s)?.let { m -> return Ticket(m.groupValues[1], m.groupValues[2]) }
        val compact = s.replace(" ", "")
        if (compact != s && (D5.matches(compact) || B6.matches(compact))) return parseManual(compact)
        if (DIGITS.matches(compact)) return Ticket(null, compact)
        return null
    }

    // OCR confusions, applied only where the ticket layout requires a digit.
    private val toDigit = mapOf('O' to '0', 'Q' to '0', 'D' to '0', 'I' to '1', 'L' to '1', '|' to '1',
        'S' to '5', 'Z' to '2', 'B' to '8', 'G' to '6', 'T' to '7')
    // ...and where it requires a series letter. Every mapping here is a guess the user
    // confirms on screen before anything is checked.
    private val toLetter = mapOf('8' to 'B', '6' to 'G', '0' to 'D', '4' to 'A')

    private val OCR_D5 = Regex("""(?<![0-9A-Z])([0-9OIlSZBGT|]{2})\s?([A-Z0-9])\s?([0-9OIlSZBGTDQ|]{5})(?![0-9])""")
    private val OCR_B6 = Regex("""(?<![0-9A-Z])([A-Z])\s?([0-9OIlSZBGTDQ|]{6})(?![0-9])""")
    // Dear Laxmi: serial and four digits ("12 3456"), only on tickets that say LAXMI (dates removed first).
    private val OCR_D4 = Regex("""(?<![0-9A-Z])([0-9OIlSZBGT|]{2})\s([0-9OIlSZBGTDQ|]{4})(?![0-9])""")
    // Kerala: two series letters and six digits ("KG 709965"), on tickets that say KERALA.
    private val OCR_KL = Regex("""(?<![0-9A-Z])([A-Z]{2})\s?([0-9OIlSZBGTDQ|]{6})(?![0-9])""")
    private val OCR_DATE = Regex("""(?<!\d)(\d{1,2})[./-](\d{1,2})[./-](\d{2}|\d{4})(?!\d)""")

    data class OcrResult(val tickets: List<Ticket>, val date: String?, val words: Set<String>)

    private fun digitsOf(s: String) = s.map { toDigit[it] ?: it }.joinToString("")

    /** Extracts candidate tickets (most likely first), a draw date (yyyy-MM-dd) and words. */
    fun fromOcr(text: String): OcrResult {
        val upper = text.uppercase(Locale.ROOT)
        val tickets = LinkedHashSet<Ticket>()
        for (m in OCR_D5.findAll(upper)) {
            val serial = digitsOf(m.groupValues[1])
            val seriesRaw = m.groupValues[2][0]
            val series = if (seriesRaw.isLetter()) seriesRaw else toLetter[seriesRaw] ?: continue
            val digits = digitsOf(m.groupValues[3])
            if (!serial.all { it in '0'..'9' } || !digits.all { it in '0'..'9' }) continue
            if (serial.toInt() !in 34..99 || series !in Ticket.SERIES_LETTERS) continue
            tickets += Ticket(serial + series, digits)
        }
        // Bumper tickets ("A 801021") look like any letter+number, so only trust them on bumper tickets.
        if ("BUMPER" in upper || "MONTHLY" in upper) {
            for (m in OCR_B6.findAll(upper)) {
                val digits = digitsOf(m.groupValues[2])
                if (digits.all { it in '0'..'9' }) tickets += Ticket(m.groupValues[1], digits)
            }
        }
        if ("LAXMI" in upper) {
            for (m in OCR_D4.findAll(OCR_DATE.replace(upper, " "))) {
                val serial = digitsOf(m.groupValues[1])
                val digits = digitsOf(m.groupValues[2])
                if (!serial.all { it in '0'..'9' } || !digits.all { it in '0'..'9' }) continue
                if (serial.toInt() in 1..12 && digits.toInt() in 2020..2099) continue   // "10 2026": month and year
                tickets += Ticket(serial, digits)
            }
        }
        if ("KERALA" in upper) {
            for (m in OCR_KL.findAll(upper)) {
                val digits = digitsOf(m.groupValues[2])
                if (digits.all { it in '0'..'9' }) tickets += Ticket(m.groupValues[1], digits)
            }
        }
        val date = OCR_DATE.findAll(upper).mapNotNull { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].let { if (it.length == 2) 2000 + it.toInt() else it.toInt() }
            if (d in 1..31 && mo in 1..12 && y in 2020..2099) "%04d-%02d-%02d".format(Locale.ROOT, y, mo, d) else null
        }.firstOrNull()
        val words = Regex("""[A-Z]{3,}""").findAll(upper).map { it.value }.toSet()
        return OcrResult(tickets.toList(), date, words)
    }
}
