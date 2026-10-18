package com.lottodost.app.domain

import java.util.Locale

/**
 * A ticket number as printed. Dear 5-digit tickets look like "39C 06366":
 * serial 34-99, series letter (A B C D E G H J K L; F and I are not used), 5 digits.
 * Users may also type only the digits (4 or 5), which still checks the lower tiers.
 */
// All formatting uses Locale.ROOT: a Bengali/Hindi default locale would otherwise print
// native digits ("৭৪D") and silently break comparisons with the feed.
data class Ticket(
    val serial: Int?,
    val series: Char?,
    val digits: String,
) {
    init {
        require((digits.length == 4 || digits.length == 5) && digits.all(Char::isDigit))
    }

    val isFull: Boolean get() = serial != null && series != null && digits.length == 5

    fun display(): String = when {
        isFull -> "%02d%c %s".format(Locale.ROOT, serial, series, digits)
        serial != null -> "%02d %s".format(Locale.ROOT, serial, digits)
        else -> digits
    }

    companion object {
        const val SERIES_LETTERS = "ABCDEGHJKL"
    }
}

object TicketParser {
    private val FULL = Regex("""^(\d{2})\s*([A-Z])\s*(\d{5})$""")
    private val SERIAL_D4 = Regex("""^(\d{2})\s+(\d{4})$""")
    private val DIGITS = Regex("""^\d{4,5}$""")

    /** Parses what the user typed. Returns null when it is not a checkable number. */
    fun parseManual(raw: String): Ticket? {
        // Devanagari/Bengali keyboards type native digits (०-९, ০-৯): normalise to ASCII.
        val ascii = raw.map { c -> if (c.isDigit() && c !in '0'..'9') '0' + Character.getNumericValue(c) else c }.joinToString("")
        val s = ascii.trim().uppercase().replace(Regex("""[-_./]"""), " ").replace(Regex("""\s+"""), " ")
        FULL.matchEntire(s)?.let { m ->
            val serial = m.groupValues[1].toInt()
            val series = m.groupValues[2][0]
            if (serial !in 34..99 || series !in Ticket.SERIES_LETTERS) return null
            return Ticket(serial, series, m.groupValues[3])
        }
        SERIAL_D4.matchEntire(s)?.let { m -> return Ticket(m.groupValues[1].toInt(), null, m.groupValues[2]) }
        val compact = s.replace(" ", "")
        FULL.matchEntire(compact)?.let { return parseManual(compact) }
        if (DIGITS.matches(compact)) return Ticket(null, null, compact)
        return null
    }

    // OCR confusions, applied only where the ticket layout requires a digit.
    private val toDigit = mapOf('O' to '0', 'Q' to '0', 'D' to '0', 'I' to '1', 'L' to '1', '|' to '1',
        'S' to '5', 'Z' to '2', 'B' to '8', 'G' to '6', 'T' to '7')
    // ...and where it requires a series letter. Kept minimal: every mapping here is a guess the
    // user confirms on screen before anything is checked.
    private val toLetter = mapOf('8' to 'B', '6' to 'G', '0' to 'D', '4' to 'A')

    private val OCR_FULL = Regex("""(?<![0-9A-Z])([0-9OIlSZBGT|]{2})\s?([A-Z0-9])\s?([0-9OIlSZBGTDQ|]{5})(?![0-9])""")
    private val OCR_DATE = Regex("""(?<!\d)(\d{1,2})[./-](\d{1,2})[./-](\d{2}|\d{4})(?!\d)""")

    data class OcrResult(val tickets: List<Ticket>, val date: String?, val words: Set<String>)

    /** Extracts candidate tickets (most likely first), a draw date (yyyy-MM-dd) and words. */
    fun fromOcr(text: String): OcrResult {
        val upper = text.uppercase()
        val tickets = LinkedHashSet<Ticket>()
        for (m in OCR_FULL.findAll(upper)) {
            val serial = m.groupValues[1].map { toDigit[it] ?: it }.joinToString("")
            val seriesRaw = m.groupValues[2][0]
            val series = if (seriesRaw.isLetter()) seriesRaw else toLetter[seriesRaw] ?: continue
            val digits = m.groupValues[3].map { toDigit[it] ?: it }.joinToString("")
            if (!serial.all(Char::isDigit) || !digits.all(Char::isDigit)) continue
            if (serial.toInt() !in 34..99 || series !in Ticket.SERIES_LETTERS) continue
            tickets += Ticket(serial.toInt(), series, digits)
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
