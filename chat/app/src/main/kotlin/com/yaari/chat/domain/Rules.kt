package com.yaari.chat.domain

import java.time.LocalDate
import java.time.Period

object ExitPolicy {
    const val FREE_LOCK_MS = 5_000L
    const val PREMIUM_LOCK_MS = 200L

    fun lockMs(premium: Boolean) = if (premium) PREMIUM_LOCK_MS else FREE_LOCK_MS

    /** Time left before the normal exit (X) unlocks. "Report & leave" never waits. */
    fun remainingMs(premium: Boolean, startedAtMs: Long, nowMs: Long): Long {
        val lock = lockMs(premium)
        return (lock - (nowMs - startedAtMs)).coerceIn(0L, lock)
    }
}

object Validation {
    const val MIN_AGE = 18
    const val MAX_AGE = 100
    const val MAX_NAME = 30
    const val MAX_BIO = 150

    /** Returns the 10-digit Indian mobile number, or null. Accepts +91 / 91 / 0 prefixes, spaces and dashes. */
    fun indianMobile(input: String): String? {
        val digits = input.filter(Char::isDigit)
        val local = when {
            digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
            digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
            else -> digits
        }
        return local.takeIf { it.length == 10 && it[0] in '6'..'9' }
    }

    /**
     * The full international number in digits ("919876543210"), or null. Spaces, dashes, a pasted "+<code>"
     * and the national trunk "0" are accepted. India keeps its exact mobile rule; elsewhere the E.164 length
     * limit applies (the SMS step rejects numbers that don't exist).
     */
    fun phone(dial: String, input: String): String? {
        if (dial == "91") return indianMobile(input)?.let { "91$it" }
        var digits = input.filter(Char::isDigit)
        if (input.trim().startsWith("+") && digits.startsWith(dial)) digits = digits.drop(dial.length)
        digits = digits.trimStart('0')
        val full = dial + digits
        return full.takeIf { digits.length >= 6 && full.length <= 15 }
    }

    fun birthDate(day: String, month: String, year: String): LocalDate? = runCatching {
        LocalDate.of(year.trim().toInt(), month.trim().toInt(), day.trim().toInt())
    }.getOrNull()

    fun age(birth: LocalDate, today: LocalDate): Int = Period.between(birth, today).years
}
