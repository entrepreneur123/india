package com.lottodost.app.domain

import com.lottodost.app.data.Draw
import com.lottodost.app.data.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bundle / range checking against the real Dear Wish Sunday 1 PM draw, 18 Oct 2026. */
class BulkTest {
    private val wish = Draw(
        id = "2026-10-18_NL1PM", slot = "NL1PM", date = "2026-10-18", time = "13:00", state = "Nagaland",
        name = "DEAR WISH SUNDAY WEEKLY LOTTERY", drawNo = 50, format = "D5", price = 7,
        tiers = listOf(
            Tier("1", 1_00_00_000, "FULL", listOf("74D 70698")),
            Tier("CONS", 1_000, "CONS", listOf("70698")),
            Tier("2", 10_000, "LAST5", listOf("15172", "15525", "15647", "29446", "69776", "72660", "75208", "79785", "86453", "89495")),
            Tier("3", 500, "LAST4", listOf("0889", "1873", "1964", "4182", "4788", "4957", "6981", "6995", "7733", "9783")),
            Tier("4", 250, "LAST4", listOf("0114", "0536", "1672", "4618", "4777", "5674", "6565", "7814", "8303", "8738")),
        ),
    )

    @Test fun rangeWithSerialExpandsAndKeepsLeadingZeros() {
        val p = Bulk.parse("39C 06300-06399")
        assertEquals(100, p.tickets.size)
        assertEquals("39C 06300", p.tickets.first().display())
        assertEquals("39C 06399", p.tickets.last().display())
        assertTrue(p.invalid.isEmpty())
    }

    @Test fun digitRangeIsNotMistakenForAPrefix() {
        // "06300-06399" must not be read as serial "06" + "300-06399".
        val p = Bulk.parse("06300-06399")
        assertEquals(100, p.tickets.size)
        assertEquals("06300", p.tickets.first().display())
    }

    @Test fun mixedListNativeDigitsAndDuplicates() {
        val p = Bulk.parse("74D 70698\n१८७३, 1873 ; 4182 0114\nhello")
        assertEquals(listOf("74D 70698", "1873", "4182", "0114"), p.tickets.map { it.display() })
        assertEquals(listOf("HELLO"), p.invalid)
    }

    @Test fun badRangesAreRejectedNotGuessed() {
        assertEquals(listOf("06399-06300"), Bulk.parse("06399-06300").invalid)  // reversed
        assertEquals(listOf("6300-06399"), Bulk.parse("6300-06399").invalid)    // different lengths
        val big = Bulk.parse("00000-99999")
        assertTrue(big.tooMany)
        assertTrue(big.tickets.isEmpty())
    }

    @Test fun totalsSureWinsOnly() {
        // 45B 21800-21899 contains 21873 (3rd prize, last 4 = 1873): ₹500, nothing else in range.
        val r = Bulk.check(Bulk.parse("45B 21800-21899").tickets, wish)
        assertEquals(100, r.checked)
        assertEquals(listOf("45B 21873"), r.winners.map { it.ticket.display() })
        assertEquals(500L, r.total)

        // Digits-only 70698: consolation is sure (₹1,000), the 1st prize needs the full number.
        val d = Bulk.check(Bulk.parse("70698").tickets, wish)
        assertEquals(1_000L, d.total)
        assertTrue(d.winners.single().maybeHigher)

        val full = Bulk.check(Bulk.parse("74D 70698\n75D 70698").tickets, wish)
        assertEquals(1_00_00_000L + 1_000L, full.total)
        assertFalse(full.winners.any { it.maybeHigher })
    }
}
