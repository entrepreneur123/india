package com.lottodost.app.domain

import com.lottodost.app.data.Draw
import com.lottodost.app.data.Tier
import com.lottodost.app.ui.formatRupees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Numbers from the real Dear Wish Sunday 1 PM draw, 18 Oct 2026 (lotterysambad.com MN181026). */
class MatcherTest {
    private val wish = Draw(
        id = "2026-10-18_NL1PM", slot = "NL1PM", date = "2026-10-18", time = "13:00", state = "Nagaland",
        name = "DEAR WISH SUNDAY WEEKLY LOTTERY", drawNo = 50, format = "D5", price = 7,
        tiers = listOf(
            Tier("1", 1_00_00_000, "FULL", listOf("74D 70698")),
            Tier("CONS", 1_000, "CONS", listOf("70698")),
            Tier("2", 10_000, "LAST5", listOf("15172", "15525", "15647", "29446", "69776", "72660", "75208", "79785", "86453", "89495")),
            Tier("3", 500, "LAST4", listOf("0889", "1873", "1964", "4182", "4788", "4957", "6981", "6995", "7733", "9783")),
            Tier("4", 250, "LAST4", listOf("0114", "0536", "1672", "4618", "4777", "5674", "6565", "7814", "8303", "8738")),
            Tier("5", 120, "LAST4", listOf("0056", "0119", "1025")),
        ),
    )
    private val laxmi = Draw(
        id = "2026-10-17_NLLAXMI", slot = "NLLAXMI", date = "2026-10-17", time = "20:00", state = "Nagaland",
        name = "DEAR LAXMI IRON SATURDAY WEEKLY LOTTERY", drawNo = 5, format = "D4", price = 12,
        tiers = listOf(
            Tier("1", 10_000, "LAST4", listOf("1668")),
            Tier("2", 5_000, "LAST4", listOf("5201")),
            Tier("3", 1_000, "LAST4", listOf("2274", "4051", "6397", "7159", "9216")),
            Tier("4", 500, "LAST4", listOf("1063", "4761", "4839", "5445", "7031")),
            Tier("5", 100, "LAST4", listOf("0472", "8601")),
        ),
    )

    private fun best(input: String, d: Draw = wish) = Matcher.check(TicketParser.parseManual(input)!!, d)?.best

    @Test fun firstPrizeExact() {
        val w = best("74D 70698")!!
        assertEquals("1", w.tier.rank)
        assertEquals(false, w.needsFullNumber)
    }

    @Test fun firstPrizeTypedWithoutSpaceOrLowercase() = assertEquals("1", best("74d70698")!!.tier.rank)

    @Test fun consolationForOtherSerialOrSeries() {
        assertEquals("CONS", best("75D 70698")!!.tier.rank)
        assertEquals("CONS", best("74E 70698")!!.tier.rank)
    }

    @Test fun onlyDigitsOfFirstPrizeAsksForFullNumber() {
        val c = Matcher.check(TicketParser.parseManual("70698")!!, wish)!!
        assertTrue(c.best!!.needsFullNumber)
        assertEquals(listOf("1", "CONS"), c.wins.map { it.tier.rank })
    }

    @Test fun secondPrizeNeedsAll5Digits() {
        assertEquals("2", best("40A 15172")!!.tier.rank)
        assertNull(best("5172"))          // 4 digits do not win 2nd prize
    }

    @Test fun lowerTiersUseLast4() {
        assertEquals("3", best("99L 10889")!!.tier.rank)
        assertEquals("4", best("0114")!!.tier.rank)
        assertEquals("5", best("34A 51025")!!.tier.rank)
    }

    @Test fun noPrize() = assertNull(best("34A 00000"))

    @Test fun fourDigitInputReportsSkippedTiers() {
        val c = Matcher.check(TicketParser.parseManual("1234")!!, wish)!!
        assertEquals(listOf("1", "CONS", "2"), c.skippedTiers.map { it.rank })
    }

    @Test fun laxmiFourDigit() {
        assertEquals("1", best("1668", laxmi)!!.tier.rank)
        assertEquals("2", best("12 5201", laxmi)!!.tier.rank)
        // A 5-digit Dear ticket is never checked against the 4-digit Laxmi draw.
        assertNull(Matcher.check(TicketParser.parseManual("51668")!!, laxmi))
    }

    @Test fun pdfOnlyDrawIsNotChecked() {
        val pdfOnly = laxmi.copy(status = "PDF_ONLY", tiers = emptyList())
        assertNull(Matcher.check(TicketParser.parseManual("1668")!!, pdfOnly))
    }

    @Test fun invalidInputs() {
        listOf("", "123", "1234567", "33A 12345", "40F 12345", "40I 12345", "ABCDE", "A 12345").forEach {
            assertNull(it, TicketParser.parseManual(it))
        }
    }

    // Real Dear Ganesh Chaturthi Bumper 2026 (Sikkim, 19-09-2026, lotterysambad GH190926).
    private val ganesh = Draw(
        id = "2026-09-19_SK_GANESH_BUMPER_2026", slot = "SK_GANESH_BUMPER_2026", date = "2026-09-19", time = "18:00",
        state = "Sikkim", name = "DEAR GANESH CHATURTHI BUMPER 2026 LOTTERY", format = "B6", price = 500, special = true,
        tiers = listOf(
            Tier("1", 3_00_00_000, "EXACT", listOf("A 801021")),
            Tier("2", 10_00_000, "EXACT", listOf("A 981936", "B 900028")),
            Tier("3", 5_00_000, "EXACT", listOf("A 875696", "A 928252")),
            Tier("4", 9_000, "LAST4", listOf("0199", "0424")),
            Tier("7", 1_000, "LAST4", listOf("0019", "0033")),
        ),
    )
    // Real Punjab State Dear 200 Monthly (03-10-2026, MT031026): 4th prize on the last 5 digits.
    private val punjab = Draw(
        id = "2026-10-03_PB", slot = "PB_DEAR200_MONTHLY_2026_10", date = "2026-10-03", time = "18:00",
        state = "Punjab", name = "PUNJAB STATE DEAR 200 MONTHLY LOTTERY", format = "B6", price = 200, special = true,
        tiers = listOf(
            Tier("1", 1_50_00_000, "EXACT", listOf("B 831461")),
            Tier("4", 9_000, "LAST5", listOf("16110", "17006")),
            Tier("5", 8_000, "LAST4", listOf("5713", "8337")),
        ),
    )

    @Test fun bumperExactAndLowerTiers() {
        assertEquals("1", best("A 801021", ganesh)!!.tier.rank)
        assertEquals("1", best("a801021", ganesh)!!.tier.rank)
        assertNull(best("B 801021", ganesh))                 // same digits, other series: no consolation here
        assertEquals("2", best("B 900028", ganesh)!!.tier.rank)
        assertEquals("4", best("C 120199", ganesh)!!.tier.rank)
        assertEquals("7", best("0033", ganesh)!!.tier.rank)
    }

    @Test fun bumperSixDigitsOnlyAsksForSeries() {
        val w = best("801021", ganesh)!!
        assertEquals("1", w.tier.rank)
        assertTrue(w.needsFullNumber)
    }

    @Test fun bumperTicketIsNotCheckedAgainstWeeklyDraws() {
        assertNull(Matcher.check(TicketParser.parseManual("A 801021")!!, wish))
        assertNull(Matcher.check(TicketParser.parseManual("74D 70698")!!, ganesh))
    }

    @Test fun monthlyLast5Tier() {
        assertEquals("4", best("A 516110", punjab)!!.tier.rank)
        assertEquals("5", best("Z 105713", punjab)!!.tier.rank)
    }

    @Test fun checkAllPutsWinsFirst() {
        val checks = Matcher.checkAll(TicketParser.parseManual("1873")!!, listOf(laxmi, wish, ganesh))
        assertEquals("NL1PM", checks.first().draw.slot)
        assertEquals(3, checks.size)
    }

    @Test fun ocrReadsBumperTicketOnlyOnBumperTickets() {
        assertEquals("A 801021", TicketParser.fromOcr("DEAR GANESH CHATURTHI BUMPER\nA 8O1021").tickets.first().display())
        assertTrue(TicketParser.fromOcr("SOME TEXT A 801021").tickets.isEmpty())
    }

    @Test fun ocrExtractsTicketAndDate() {
        val text = "DEAR\nWISH SUNDAY\nWEEKLY LOTTERY\nDraw Date 18/10/26\n74D 7O698\nMRP 7"
        val r = TicketParser.fromOcr(text)
        assertEquals("74D 70698", r.tickets.first().display())
        assertEquals("2026-10-18", r.date)
        assertTrue("WISH" in r.words)
    }

    @Test fun ocrIgnoresUnusedSeriesLetters() {
        assertTrue(TicketParser.fromOcr("40F 12345").tickets.isEmpty())
        assertNotNull(TicketParser.fromOcr("40 B 12345").tickets.firstOrNull())
    }

    @Test fun matchingWorksUnderBengaliAndHindiLocales() {
        val saved = java.util.Locale.getDefault()
        try {
            for (tag in listOf("bn-IN", "hi-IN", "ar")) {
                java.util.Locale.setDefault(java.util.Locale.forLanguageTag(tag))
                assertEquals(tag, "74D 70698", TicketParser.parseManual("74D70698")!!.display())
                assertEquals(tag, "1", best("74D 70698")!!.tier.rank)
                assertEquals(tag, "2026-10-18", TicketParser.fromOcr("18/10/26 74D 70698").date)
                assertEquals(tag, "1:00 PM", com.lottodost.app.ui.formatTime("13:00"))
            }
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }

    @Test fun nativeDigitsAreAccepted() {
        assertEquals("74D 70698", TicketParser.parseManual("৭৪D ৭০৬৯৮")!!.display())
        assertEquals("1873", TicketParser.parseManual("१८७३")!!.display())
        assertEquals("A 801021", TicketParser.parseManual("A ৮০১০২১")!!.display())
    }

    @Test fun rupeesUseIndianGrouping() {
        assertEquals("₹1,00,00,000", formatRupees(1_00_00_000))
        assertEquals("₹10,000", formatRupees(10_000))
        assertEquals("₹120", formatRupees(120))
        assertEquals("8:00 PM", com.lottodost.app.ui.formatTime("20:00"))
        assertEquals("garbage", com.lottodost.app.ui.formatTime("garbage"))   // external data never crashes the UI
    }
}
