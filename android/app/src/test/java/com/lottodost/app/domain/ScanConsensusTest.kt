package com.lottodost.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanConsensusTest {
    @Test fun oneFrameIsNotEnough() {
        val c = ScanConsensus()
        assertNull(c.offer("74D 70698"))
        assertEquals("74D 70698", c.offer("74D 70698"))
    }

    @Test fun aMisreadFrameDoesNotWin() {
        val c = ScanConsensus()
        assertNull(c.offer("74D 70698"))
        assertNull(c.offer("74D 70696"))      // blurred frame: 8 read as 6
        assertEquals("74D 70698", c.offer("74D 70698"))   // 2 of the last 3 agree
    }

    @Test fun commonOcrSlipsStillCountAsTheSameNumber() {
        val c = ScanConsensus()
        assertNull(c.offer("74D 70698"))
        assertEquals("74D 7O69B", c.offer("74D 7O69B"))   // O and B where digits must be: read as 0 and 8
    }

    @Test fun framesWithoutANumberAreIgnored() {
        val c = ScanConsensus()
        assertNull(c.offer("DEAR WISH SUNDAY"))
        assertNull(c.offer("40C 12345"))
        assertNull(c.offer("blank"))
        assertEquals("40C 12345", c.offer("40C 12345"))
    }
}
