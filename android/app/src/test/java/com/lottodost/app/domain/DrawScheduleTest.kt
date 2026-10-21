package com.lottodost.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.random.Random

class DrawScheduleTest {
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 10, 18, h, m, 0, 0, DrawSchedule.IST)
    private val onePm = DrawSchedule.slot("NL1PM")!!

    @Test fun statusTimeline() {
        assertEquals(DrawSchedule.Status.Upcoming(61), DrawSchedule.status(onePm, at(12, 0), false))
        assertEquals(DrawSchedule.Status.Live, DrawSchedule.status(onePm, at(13, 5), false))
        assertEquals(DrawSchedule.Status.Drawing, DrawSchedule.status(onePm, at(13, 20), false))
        assertEquals(DrawSchedule.Status.Delayed, DrawSchedule.status(onePm, at(13, 45), false))
        assertEquals(DrawSchedule.Status.Out, DrawSchedule.status(onePm, at(13, 12), true))
    }

    @Test fun backgroundChecksAreSpreadOverSixMinutes() {
        val rnd = Random(42)
        val offsets = (1..2000).map {
            Duration.between(at(13, 10), DrawSchedule.nextCheckAt(at(12, 0), rnd)).seconds
        }
        assertTrue(offsets.all { it in 0 until DrawSchedule.BACKGROUND_JITTER_S })
        // Roughly uniform: no single second holds more than 1 % of devices.
        assertTrue(offsets.groupingBy { it }.eachCount().values.max() < 20)
    }

    @Test fun nextCheckRollsToTomorrowAfterLastDraw() {
        val next = DrawSchedule.nextCheckAt(at(23, 0), Random(1))
        assertEquals(19, next.dayOfMonth)
        assertEquals(13, next.hour)
    }

    @Test fun backgroundCheckCoversKeralaToo() {
        val next = DrawSchedule.nextCheckAt(at(14, 0), Random(1))   // after the 1 PM check, before 6 PM
        assertEquals(16, next.hour)
        assertTrue(next.minute in 15..21)
        assertEquals("KL", DrawSchedule.recentlyDrawnSlot(at(16, 20)))
        assertEquals("NL1PM", DrawSchedule.recentlyDrawnSlot(at(13, 20)))
        assertNull(DrawSchedule.recentlyDrawnSlot(at(17, 40)))
        // An open app keeps checking for Kerala's result between 16:00 and 17:30, and stops once it is in.
        assertTrue(DrawSchedule.pollDelayMs(at(16, 10), { false }) != null)
        assertNull(DrawSchedule.pollDelayMs(at(16, 10), { it == "KL" }))
    }

    @Test fun pollingOnlyWhileAResultIsDue() {
        assertNull(DrawSchedule.pollDelayMs(at(11, 0), { false }))
        val d = DrawSchedule.pollDelayMs(at(13, 5), { false }, Random(3))!!
        assertTrue(d in 22_000L..38_000L)
        assertNull(DrawSchedule.pollDelayMs(at(13, 5), { it == "NL1PM" }))
        assertNull("stop after the 90-minute window", DrawSchedule.pollDelayMs(at(14, 40), { false }))
    }

    @Test fun gameNamesPerWeekday() {
        assertEquals("DEAR WISH", onePm.gameName(7))
        assertEquals("DEAR LAXMI IRON", DrawSchedule.slot("NLLAXMI")!!.gameName(6))
        assertEquals("DEAR EMPIRE", DrawSchedule.slot("SK6PM")!!.gameName(7))
    }
}
