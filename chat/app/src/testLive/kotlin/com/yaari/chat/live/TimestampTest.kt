package com.yaari.chat.live

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TimestampTest {
    // The exact form PostgREST returns for timestamptz; Instant.parse rejects it on Android's java.time.
    @Test fun parsesPostgresOffsetForm() =
        assertEquals(Instant.parse("2026-11-18T10:02:25.972799Z"), ts("2026-11-18T10:02:25.972799+00:00"))

    @Test fun parsesZuluAndOtherOffsets() {
        assertEquals(Instant.parse("2026-11-18T10:02:25Z"), ts("2026-11-18T10:02:25Z"))
        assertEquals(Instant.parse("2026-11-18T04:32:25Z"), ts("2026-11-18T10:02:25+05:30"))
    }
}
