package com.lottodost.app.domain

import com.lottodost.app.data.Draw
import com.lottodost.app.data.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kerala KR-772 of 17-10-2026, as published by the pipeline (9th prize unconfirmed that day). */
class KeralaMatchTest {
    private val kr = Draw(
        id = "2026-10-17_KL", slot = "KL", date = "2026-10-17", time = "15:00", state = "Kerala",
        name = "KARUNYA KR-772", drawNo = 772, format = "B6", price = 0, lottery = "kerala",
        tiers = listOf(
            Tier("1", 1_00_00_000, "EXACT", listOf("KG 709965")),
            Tier("CONS", 5_000, "EXACT", listOf("KA 709965", "KB 709965")),
            Tier("2", 25_00_000, "EXACT", listOf("KA 724739")),
            Tier("4", 5_000, "LAST4", listOf("0945", "1969")),
            Tier("9", 100, "LAST4", listOf("9865"), confirmed = false),
        ),
    )

    private fun best(raw: String) = Matcher.check(TicketParser.parseManual(raw)!!, kr)?.best

    @Test fun fullTicketWins() {
        assertEquals("1", best("KG 709965")!!.tier.rank)
        assertEquals("1", best("kg709965")!!.tier.rank)          // typed without space, lower case
        assertEquals("CONS", best("KB 709965")!!.tier.rank)
        assertEquals("2", best("KA 724739")!!.tier.rank)
    }

    @Test fun sixDigitsAloneAskForTheSeries() {
        val w = best("709965")!!
        assertTrue(w.needsFullNumber)
    }

    @Test fun lastFourDigitsAndUnconfirmedTier() {
        assertEquals("4", best("KX 111969")!!.tier.rank)
        val ninth = best("KX 129865")!!
        assertEquals("9", ninth.tier.rank)
        assertFalse(ninth.tier.confirmed)
        assertEquals(null, best("KX 120000"))
    }
}
