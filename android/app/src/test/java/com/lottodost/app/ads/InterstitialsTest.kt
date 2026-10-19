package com.lottodost.app.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialsTest {
    private val min = 60_000L

    @Test fun neverInTheFirstTwoMinutes() {
        assertFalse(Interstitials.allowed(now = 119_000, start = 0, last = 0, count = 0))
        assertTrue(Interstitials.allowed(now = 2 * min, start = 0, last = 0, count = 0))
    }

    @Test fun fourMinutesApartAndThreePerSession() {
        assertFalse(Interstitials.allowed(now = 10 * min, start = 0, last = 7 * min, count = 1))
        assertTrue(Interstitials.allowed(now = 11 * min, start = 0, last = 7 * min, count = 1))
        assertFalse(Interstitials.allowed(now = 60 * min, start = 0, last = 20 * min, count = 3))
    }
}
