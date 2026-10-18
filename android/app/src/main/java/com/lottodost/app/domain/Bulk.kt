package com.lottodost.app.domain

import com.lottodost.app.data.Draw

/**
 * Many tickets at once, for people who hold a bundle (and for sellers): one ticket or range per
 * line, comma or space separated. "39C 06300-06399" and "06300-06399" expand to every number in
 * the range. Everything is checked against ONE chosen draw, so the total is meaningful.
 */
object Bulk {
    const val MAX_TICKETS = 1000

    data class Parsed(val tickets: List<Ticket>, val invalid: List<String>, val tooMany: Boolean)

    /**
     * [win] is the best prize this ticket is sure to get. [maybeHigher]: the digits also match a prize
     * that needs the full number (e.g. the 1st), which the user must confirm. When only such a prize
     * matches, [sure] is false and the line is not counted in the total.
     */
    data class Line(val ticket: Ticket, val win: Matcher.Win, val maybeHigher: Boolean, val sure: Boolean = true)

    data class Result(val checked: Int, val winners: List<Line>) {
        val total: Long get() = winners.filter { it.sure }.sumOf { it.win.tier.prize }
    }

    // Prefix: "39C" (D5), "A"/"AB" (bumper) or "12 " (Laxmi serial, needs the space to be unambiguous).
    private val RANGE = Regex("""^(?:(\d{2}\s?[A-Z]|[A-Z]{1,2}|\d{2}(?=\s))\s*)?(\d{4,6})\s*(?:-|–|TO)\s*(\d{4,6})$""")

    fun parse(text: String): Parsed {
        val tickets = LinkedHashMap<String, Ticket>()
        val invalid = mutableListOf<String>()
        var tooMany = false
        // Lines first (a range may contain spaces), then comma/semicolon separated items.
        // Devanagari/Bengali/Gurmukhi keyboards type native digits: same normalisation as single tickets.
        val ascii = text.map { c -> if (c.isDigit() && c !in '0'..'9') '0' + Character.getNumericValue(c) else c }.joinToString("")
        val items = ascii.uppercase().lines().flatMap { it.split(',', ';') }.map { it.trim() }.filter { it.isNotEmpty() }
        for (item in items) {
            val r = RANGE.matchEntire(item)
            val expanded: List<Ticket>? = if (r != null) {
                val (prefix, a, b) = r.destructured
                if (a.length != b.length || a > b) null
                else {
                    val from = a.toInt()
                    val to = b.toInt()
                    if (to - from + 1 > MAX_TICKETS) { tooMany = true; emptyList() }
                    else (from..to).map { n ->
                        val digits = n.toString().padStart(a.length, '0')
                        TicketParser.parseManual(if (prefix.isEmpty()) digits else "$prefix $digits")
                    }.takeIf { list -> list.all { it != null } }?.filterNotNull()
                }
            } else {
                // Not a range: a single ticket, or several digit-only numbers separated by spaces.
                TicketParser.parseManual(item)?.let { listOf(it) }
                    ?: item.split(Regex("""\s+""")).map { TicketParser.parseManual(it) }
                        .takeIf { l -> l.size > 1 && l.all { it != null } }?.filterNotNull()
            }
            if (expanded == null) invalid += item else expanded.forEach { tickets.putIfAbsent(it.display(), it) }
            if (tickets.size > MAX_TICKETS) { tooMany = true; break }
        }
        return Parsed(tickets.values.take(MAX_TICKETS), invalid, tooMany)
    }

    /** Best prize per ticket in [draw]; tickets that cannot be checked against it are not counted. */
    fun check(tickets: List<Ticket>, draw: Draw): Result {
        var checked = 0
        val winners = mutableListOf<Line>()
        for (t in tickets) {
            val c = Matcher.check(t, draw) ?: continue
            checked++
            // A digits-only "maybe 1st prize" is not money yet: it needs the full number to decide.
            val sure = c.wins.firstOrNull { !it.needsFullNumber }
            val unsure = c.wins.firstOrNull { it.needsFullNumber }
            when {
                sure != null -> winners += Line(t, sure, maybeHigher = unsure != null && unsure.tier.prize > sure.tier.prize)
                unsure != null -> winners += Line(t, unsure, maybeHigher = true, sure = false)
            }
        }
        return Result(checked, winners.sortedByDescending { it.win.tier.prize })
    }
}
