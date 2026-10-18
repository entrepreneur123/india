package com.lottodost.app.domain

import com.lottodost.app.data.Draw
import com.lottodost.app.data.Tier

/** Prize rules, mirrored from the result sheets (see pipeline/lottery_pipeline/model.py). */
object Matcher {

    data class Win(val tier: Tier, val needsFullNumber: Boolean = false)

    data class Check(
        val draw: Draw,
        val wins: List<Win>,          // best (highest prize) first
        val skippedTiers: List<Tier>, // tiers this input could not decide (e.g. only 4 digits typed)
    ) {
        val best: Win? get() = wins.firstOrNull()
    }

    private fun compatible(ticket: Ticket, draw: Draw): Boolean = when (draw.format) {
        "D5" -> ticket.format == Ticket.Format.D5 || ticket.format == Ticket.Format.DIGITS && ticket.digits.length <= 5
        "D4" -> ticket.format == Ticket.Format.D4 || ticket.format == Ticket.Format.DIGITS && ticket.digits.length == 4
        "B6" -> ticket.format == Ticket.Format.B6 || ticket.format == Ticket.Format.DIGITS
        else -> false
    }

    fun check(ticket: Ticket, draw: Draw): Check? {
        if (!draw.hasNumbers || !compatible(ticket, draw)) return null
        val wins = mutableListOf<Win>()
        val skipped = mutableListOf<Tier>()
        val n = ticket.digits.length
        val full = ticket.format != Ticket.Format.DIGITS
        for (tier in draw.tiers) {
            when (tier.match) {
                "FULL" -> {
                    val first = tier.numbers.firstOrNull() ?: continue
                    when {
                        full -> if (ticket.display() == first) wins += Win(tier)
                        n == 5 && ticket.digits == first.takeLast(5) ->
                            // Same 5 digits: 1st prize or consolation depending on serial/series.
                            wins += Win(tier, needsFullNumber = true)
                        n < 5 -> skipped += tier
                    }
                }
                "CONS" -> {
                    if (n < 5) { skipped += tier; continue }
                    val isFirst = full && draw.tiers.first().numbers.firstOrNull() == ticket.display()
                    if (!isFirst && ticket.digits in tier.numbers) wins += Win(tier)
                }
                "EXACT" -> when {
                    full -> if (ticket.display() in tier.numbers) wins += Win(tier)
                    n == 6 -> if (tier.numbers.any { it.takeLast(6) == ticket.digits }) wins += Win(tier, needsFullNumber = true)
                    else -> skipped += tier
                }
                "LAST5" -> {
                    if (n < 5) { skipped += tier; continue }
                    if (ticket.digits.takeLast(5) in tier.numbers) wins += Win(tier)
                }
                "LAST4" -> if (ticket.digits.takeLast(4) in tier.numbers) wins += Win(tier)
            }
        }
        return Check(draw, wins.sortedByDescending { it.tier.prize }, skipped)
    }

    /** Check one ticket against many draws; draws with a win come first, then newest. */
    fun checkAll(ticket: Ticket, draws: List<Draw>): List<Check> =
        draws.mapNotNull { check(ticket, it) }
            .sortedWith(compareByDescending<Check> { it.best?.tier?.prize ?: -1L }.thenByDescending { it.draw.date + it.draw.time })
}
