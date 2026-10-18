package com.lottodost.app.domain

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Daily draw slots (IST), verified against the official calendar and lotterysambad.com for
 * October 2026. The 6 PM "Dear" draw is run by Sikkim State Lotteries, not Nagaland.
 * Names change per weekday and come from the result feed; this only drives countdowns
 * and background checks.
 */
object DrawSchedule {
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")

    data class Slot(val id: String, val drawTime: LocalTime, val state: String, val label: String, val format: String)

    val SLOTS = listOf(
        Slot("NL1PM", LocalTime.of(13, 0), "Nagaland", "Dear 1 PM", "D5"),
        Slot("SK6PM", LocalTime.of(18, 0), "Sikkim", "Dear 6 PM", "D5"),
        Slot("NL8PM", LocalTime.of(20, 0), "Nagaland", "Dear 8 PM", "D5"),
        Slot("NLLAXMI", LocalTime.of(20, 0), "Nagaland", "Dear Laxmi 8 PM", "D4"),
    )

    fun slot(id: String): Slot? = SLOTS.firstOrNull { it.id == id }

    /** The text result sheet usually appears 7-10 minutes after the draw. */
    private const val PUBLISH_DELAY_MIN = 10L

    fun nextCheckAt(now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate()
        val candidates = SLOTS.map { it.drawTime }.distinct().sorted().flatMap { t ->
            listOf(today, today.plusDays(1)).map { d -> d.atTime(t).plusMinutes(PUBLISH_DELAY_MIN).atZone(IST) }
        }
        return candidates.filter { it.isAfter(now) }.minOrNull()!!
    }

    /** A 5-digit slot drawn within the last hour whose result we are still waiting for. */
    fun recentlyDrawnSlot(now: ZonedDateTime): String? {
        val t = now.toLocalTime()
        return SLOTS.filter { it.format == "D5" }.lastOrNull { s ->
            !t.isBefore(s.drawTime) && t.isBefore(s.drawTime.plusMinutes(60))
        }?.id
    }

    sealed interface Status {
        data class Upcoming(val minutesLeft: Long) : Status
        data object Drawing : Status  // draw has happened, result not published yet
        data object Out : Status
    }

    fun status(slot: Slot, now: ZonedDateTime, hasResult: Boolean): Status {
        if (hasResult) return Status.Out
        val drawAt = now.toLocalDate().atTime(slot.drawTime).atZone(IST)
        val mins = java.time.Duration.between(now, drawAt).toMinutes()
        return if (now.isBefore(drawAt)) Status.Upcoming(mins + 1) else Status.Drawing
    }
}
