package com.yaari.chat

import com.yaari.chat.domain.ExitPolicy
import com.yaari.chat.domain.Validation
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RulesTest {
    @Test fun exitLockFreeIsFiveSeconds() {
        assertEquals(5_000, ExitPolicy.remainingMs(premium = false, startedAtMs = 1_000, nowMs = 1_000))
        assertEquals(1, ExitPolicy.remainingMs(false, 1_000, 5_999))
        assertEquals(0, ExitPolicy.remainingMs(false, 1_000, 6_000))
    }

    @Test fun exitLockPremiumIsPointTwoSeconds() {
        assertEquals(200, ExitPolicy.remainingMs(true, 0, 0))
        assertEquals(0, ExitPolicy.remainingMs(true, 0, 200))
    }

    @Test fun exitLockNeverExceedsLockWhenClockGoesBackwards() {
        assertEquals(5_000, ExitPolicy.remainingMs(false, 10_000, 9_000))
    }

    @Test fun indianMobileNumbers() {
        assertEquals("9876543210", Validation.indianMobile("98765 43210"))
        assertEquals("9876543210", Validation.indianMobile("+91 98765-43210"))
        assertEquals("9876543210", Validation.indianMobile("09876543210"))
        assertNull(Validation.indianMobile("5876543210")) // must start with 6-9
        assertNull(Validation.indianMobile("98765"))
        assertNull(Validation.indianMobile(""))
    }

    @Test fun ageCountsBirthdayOnlyOnceReached() {
        val today = LocalDate.of(2026, 10, 18)
        assertEquals(18, Validation.age(LocalDate.of(2008, 10, 18), today))
        assertEquals(17, Validation.age(LocalDate.of(2008, 10, 19), today))
    }

    @Test fun invalidBirthDates() {
        assertNull(Validation.birthDate("31", "02", "2000"))
        assertNull(Validation.birthDate("", "1", "2000"))
        assertEquals(LocalDate.of(2000, 2, 29), Validation.birthDate("29", "2", "2000"))
    }

    @Test fun targets() {
        assertTrue(Target.ANY.accepts(Gender.OTHER))
        assertTrue(Target.FEMALE.accepts(Gender.FEMALE))
        assertFalse(Target.FEMALE.accepts(Gender.MALE))
        assertFalse(Target.ANY.isPaid)
        assertTrue(Target.MALE.isPaid)
    }
}
