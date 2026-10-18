package com.lottodost.app.domain

import com.lottodost.app.data.Draw
import com.lottodost.app.data.Tier

/** Prize rules, mirrored from the gazette text (see pipeline/lottery_pipeline/model.py). */
object Matcher {

    data class Win(val tier: Tier, val needsFullNumber: Boolean = false)

    data class Check(
        val draw: Draw,
        val wins: List<Win>,          // best (highest prize) first
        val skippedTiers: List<Tier>, // tiers this input could not decide (e.g. only 4 digits typed)
    ) {
        val best: Win? get() = wins.firstOrNull()
    }

    fun check(ticket: Ticket, draw: Draw): Check? {
        if (!draw.hasNumbers) return null
        // A 5-digit ticket cannot belong to a 4-digit (Laxmi) draw.
        if (draw.format == "D4" && ticket.digits.length != 4) return null
        val wins = mutableListOf<Win>()
        val skipped = mutableListOf<Tier>()
        val last4 = ticket.digits.takeLast(4)
        for (tier in draw.tiers) {
            when (tier.match) {
                "FULL" -> {
                    val first = tier.numbers.firstOrNull() ?: continue
                    when {
                        ticket.isFull -> if (ticket.display() == first) wins += Win(tier)
                        ticket.digits.length == 5 && ticket.digits == first.takeLast(5) ->
                            // Same 5 digits: 1st prize or consolation depending on serial/series.
                            wins += Win(tier, needsFullNumber = true)
                        ticket.digits.length < 5 -> skipped += tier
                    }
                }
                "CONS" -> {
                    if (ticket.digits.length < 5) { skipped += tier; continue }
                    val isFirst = ticket.isFull && draw.tiers.first().numbers.firstOrNull() == ticket.display()
                    if (!isFirst && ticket.digits in tier.numbers) wins += Win(tier)
                }
                "LAST5" -> {
                    if (ticket.digits.length < 5) { skipped += tier; continue }
                    if (ticket.digits in tier.numbers) wins += Win(tier)
                }
                "LAST4" -> if (last4 in tier.numbers) wins += Win(tier)
            }
        }
        return Check(draw, wins.sortedByDescending { it.tier.prize }, skipped)
    }

    /** Check one ticket against many draws; draws with a win come first. */
    fun checkAll(ticket: Ticket, draws: List<Draw>): List<Check> =
        draws.mapNotNull { check(ticket, it) }.sortedByDescending { it.best?.tier?.prize ?: -1 }
}
