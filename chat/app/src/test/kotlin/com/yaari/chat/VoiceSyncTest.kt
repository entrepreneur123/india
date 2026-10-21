package com.yaari.chat

import com.yaari.chat.data.ChangeOrder
import com.yaari.chat.data.ChangeOrder.Step
import com.yaari.chat.data.roomRefusal
import com.yaari.chat.model.VoiceEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The numbering of a room's Broadcast changes (020) and what the server's refusals mean, as on the web. */
class VoiceSyncTest {
    /** A change is just its number here (null: sent without one). */
    private data class C(val seq: Long?, val tag: String = "")

    private fun order() = ChangeOrder<C> { it.seq }

    @Test fun changesApplyInOrderAndOldOnesAreSkipped() {
        val o = order()
        assertEquals(emptyList<C>() to false, o.base(5))
        assertEquals(Step.APPLY, o.offer(C(6), ready = true))
        assertEquals(Step.APPLY, o.offer(C(7), ready = true))
        assertEquals(Step.SKIP, o.offer(C(7), ready = true)) // replayed
        assertEquals(Step.SKIP, o.offer(C(3), ready = true))
        assertEquals(7L, o.seq)
    }

    @Test fun aMissedNumberFetchesTheState() {
        val o = order()
        o.base(5)
        assertEquals(Step.REFETCH, o.offer(C(7), ready = true))
        assertEquals(5L, o.seq) // nothing applied: the full state brings it
    }

    @Test fun changesDuringAFetchWaitAndThoseAfterTheStateApply() {
        val o = order()
        o.base(5)
        // A full state is on its way: 6 and 7 wait; the state has 6 in it already.
        assertEquals(Step.HOLD, o.offer(C(7, "b"), ready = false))
        assertEquals(Step.HOLD, o.offer(C(6, "a"), ready = false))
        assertEquals(Step.HOLD, o.offer(C(null), ready = false)) // no number: the state covers it
        assertEquals(listOf(C(7, "b")) to false, o.base(6))
        assertEquals(7L, o.seq)
        // A gap among the waiting ones: what comes before it applies, then the state is fetched again.
        assertEquals(Step.HOLD, o.offer(C(8), ready = false))
        assertEquals(Step.HOLD, o.offer(C(10), ready = false))
        assertEquals(listOf(C(8)) to true, o.base(7))
        assertEquals(8L, o.seq)
    }

    @Test fun aChangeWithoutANumberAppliesAndIsChecked() {
        val o = order()
        o.base(2)
        assertEquals(Step.APPLY_RECHECK, o.offer(C(null), ready = true))
        assertEquals(2L, o.seq)
    }

    @Test fun theHeartbeatsNumberShowsAMissedChange() {
        val o = order()
        assertTrue(o.behind(0)) // before the first state
        o.base(4)
        assertFalse(o.behind(4))
        assertTrue(o.behind(5))
        o.offer(C(5), ready = true)
        assertFalse(o.behind(5))
    }

    @Test fun reSyncSlowsDownWhileRealtimeIsUpAndAudioBacksOff() {
        assertEquals(45_000L, ChangeOrder.resyncMs(healthy = true))
        assertEquals(10_000L, ChangeOrder.resyncMs(healthy = false))
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L), (0..6).map { ChangeOrder.audioRetryMs(it) })
    }

    @Test fun everyRefusalCodeEndsTheSessionWithItsReason() {
        assertEquals(VoiceEvent.Ended, roomRefusal("room_closed"))
        assertEquals(VoiceEvent.Ended, roomRefusal("not_found"))
        assertEquals(VoiceEvent.Removed, roomRefusal("voice_removed"))
        for (code in listOf("room_full", "room_gender", "room_age", "not_allowed", "banned", "blocked", "not_signed_in")) {
            assertEquals(VoiceEvent.Refused(code), roomRefusal(code))
        }
        // A weak network or anything else: keep trying.
        assertNull(roomRefusal("network"))
        assertNull(roomRefusal("slow_down"))
        assertNull(roomRefusal(null))
    }
}
