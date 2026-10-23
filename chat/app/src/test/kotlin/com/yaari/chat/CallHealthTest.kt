package com.yaari.chat

import com.yaari.chat.data.PartnerNotice
import com.yaari.chat.data.Signal
import com.yaari.chat.data.cameraWanted
import com.yaari.chat.data.partnerNotice
import com.yaari.chat.data.signalBars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallHealthTest {
    @Test fun partnerNoticeMostSeriousFirst() {
        assertNull(partnerNotice(Signal.EXCELLENT, videoPaused = false, left = false))
        assertNull(partnerNotice(Signal.UNKNOWN, videoPaused = false, left = false))
        assertEquals(PartnerNotice.WEAK, partnerNotice(Signal.POOR, videoPaused = false, left = false))
        assertEquals(PartnerNotice.PAUSED, partnerNotice(Signal.POOR, videoPaused = true, left = false))
        assertEquals(PartnerNotice.LOST, partnerNotice(Signal.LOST, videoPaused = true, left = false))
        assertEquals(PartnerNotice.LOST, partnerNotice(Signal.GOOD, videoPaused = false, left = true))
    }

    @Test fun bars() {
        assertEquals(listOf(3, 2, 1, 0, null), listOf(Signal.EXCELLENT, Signal.GOOD, Signal.POOR, Signal.LOST, Signal.UNKNOWN).map(::signalBars))
    }

    @Test fun cameraOffInBackgroundOrWithoutPermission() {
        assertTrue(cameraWanted(buttonOn = true, permitted = true, foreground = true))
        assertFalse(cameraWanted(buttonOn = true, permitted = true, foreground = false))
        assertFalse(cameraWanted(buttonOn = true, permitted = false, foreground = true))
        assertFalse(cameraWanted(buttonOn = false, permitted = true, foreground = true))
    }
}
