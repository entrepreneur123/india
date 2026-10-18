package com.lottodost.app.domain

import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

/**
 * Daily draw slots (IST), verified against 35 days of result sheets (Sep-Oct 2026) and the
 * official calendar. The 6 PM "Dear" draw is run by Sikkim State Lotteries, not Nagaland.
 * Names change per weekday; the feed's name wins, these are for the guide and countdowns.
 */
object DrawSchedule {
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")

    data class Slot(
        val id: String,
        val drawTime: LocalTime,
        val state: String,
        val label: String,
        val format: String,
        val price: Int,
        val weekdayNames: List<String>, // Monday..Sunday
    ) {
        /** Printed game name for a weekday (1 = Monday), e.g. "DEAR LAXMI IRON". */
        fun gameName(isoDayOfWeek: Int): String =
            (if (id == "NLLAXMI") "DEAR LAXMI " else "DEAR ") + weekdayNames[isoDayOfWeek - 1].uppercase()
    }

    val SLOTS = listOf(
        Slot("NL1PM", LocalTime.of(13, 0), "Nagaland", "Dear 1 PM", "D5", 7,
            listOf("Rise", "Shine", "Spark", "Star", "Victory", "Vision", "Wish")),
        Slot("SK6PM", LocalTime.of(18, 0), "Sikkim", "Dear 6 PM", "D5", 7,
            listOf("Legend", "Prestige", "Regal", "Supreme", "Crown", "Elite", "Empire")),
        Slot("NL8PM", LocalTime.of(20, 0), "Nagaland", "Dear 8 PM", "D5", 7,
            listOf("Clover", "Destiny", "Dream", "Fame", "Horizon", "Lucky", "Magic")),
        Slot("NLLAXMI", LocalTime.of(20, 0), "Nagaland", "Dear Laxmi 8 PM", "D4", 12,
            listOf("Gold", "Silver", "Diamond", "Pearl", "Platinum", "Iron", "Bronze")),
    )

    fun slot(id: String): Slot? = SLOTS.firstOrNull { it.id == id }

    /** The text result sheet usually appears 7-10 minutes after the draw. */
    private const val PUBLISH_DELAY_MIN = 10L
    /** Spread background wake-ups so a million phones do not hit the CDN in the same second. */
    const val BACKGROUND_JITTER_S = 360L
    /** How long after a draw we keep polling for its result while the app is open. */
    private const val WAIT_WINDOW_MIN = 90L
    private const val DELAYED_AFTER_MIN = 40L

    fun nextCheckAt(now: ZonedDateTime, random: Random = Random.Default): ZonedDateTime {
        val today = now.toLocalDate()
        val candidates = SLOTS.map { it.drawTime }.distinct().sorted().flatMap { t ->
            listOf(today, today.plusDays(1)).map { d -> d.atTime(t).plusMinutes(PUBLISH_DELAY_MIN).atZone(IST) }
        }
        val base = candidates.filter { it.isAfter(now) }.minOrNull()!!
        return base.plusSeconds(random.nextLong(0, BACKGROUND_JITTER_S))
    }

    /** A 5-digit slot drawn within the last hour whose result we are still waiting for. */
    fun recentlyDrawnSlot(now: ZonedDateTime): String? {
        val t = now.toLocalTime()
        return SLOTS.filter { it.format == "D5" }.lastOrNull { s ->
            !t.isBefore(s.drawTime) && t.isBefore(s.drawTime.plusMinutes(60))
        }?.id
    }

    /**
     * Foreground polling: while a draw has happened but its result is not in yet, poll every
     * ~30 s with jitter (so open apps do not synchronise); otherwise do not poll at all.
     * Returns null when nothing is pending.
     */
    fun pollDelayMs(now: ZonedDateTime, hasResult: (slotId: String) -> Boolean, random: Random = Random.Default): Long? {
        val t = now.toLocalTime()
        val pending = SLOTS.filter { it.format == "D5" }.any { s ->
            !t.isBefore(s.drawTime) && t.isBefore(s.drawTime.plusMinutes(WAIT_WINDOW_MIN)) && !hasResult(s.id)
        }
        return if (pending) 30_000L + random.nextLong(-8_000, 8_000) else null
    }

    sealed interface Status {
        data class Upcoming(val minutesLeft: Long) : Status
        data object Live : Status     // draw is happening (first ~10 minutes)
        data object Drawing : Status  // draw has happened, result not published yet
        data object Delayed : Status  // still no result 40+ minutes after the draw (usually 7-10)
        data object Out : Status
    }

    fun status(slot: Slot, now: ZonedDateTime, hasResult: Boolean): Status {
        if (hasResult) return Status.Out
        val drawAt = now.toLocalDate().atTime(slot.drawTime).atZone(IST)
        return when {
            now.isBefore(drawAt) -> Status.Upcoming(Duration.between(now, drawAt).toMinutes() + 1)
            now.isBefore(drawAt.plusMinutes(PUBLISH_DELAY_MIN)) -> Status.Live
            now.isBefore(drawAt.plusMinutes(DELAYED_AFTER_MIN)) -> Status.Drawing
            else -> Status.Delayed
        }
    }
}
