package com.yaari.chat.data

import com.yaari.chat.model.VoiceEvent

/**
 * Keeping up with a room (020_scale_fixes.sql): after one full state, every change comes over a private Broadcast
 * topic with the room's next change number. [ChangeOrder] decides, for each change as it arrives, whether it is
 * applied, waits for a full state on its way, is old news, or shows that one was missed (then the full state is
 * fetched again). The same rules as the web app (lib/supabaseBackend.ts).
 */
class ChangeOrder<C : Any>(private val seqOf: (C) -> Long?) {
    enum class Step {
        /** Arrived while a full state was on its way: kept until it lands ([base]). */
        HOLD,
        /** Already in the state (a replay, or included in the full state). */
        SKIP,
        /** The next one: apply it. */
        APPLY,
        /** Sent without a number (a rare collision between two rooms): apply it, then check against the server. */
        APPLY_RECHECK,
        /** One was missed: fetch the full state. */
        REFETCH,
    }

    /** The number of the last change in the state shown; -1 before the first full state. */
    var seq = -1L
        private set
    private val held = mutableListOf<C>()

    /** [ready]: a full state is held and none is on its way. */
    fun offer(c: C, ready: Boolean): Step {
        if (!ready) {
            held += c
            return Step.HOLD
        }
        val n = seqOf(c) ?: return Step.APPLY_RECHECK
        if (n <= seq) return Step.SKIP
        if (n != seq + 1) return Step.REFETCH
        seq = n
        return Step.APPLY
    }

    /**
     * A full state numbered [n] arrived (null: a server without numbers). Returns the held changes still to apply on
     * top of it, in order, and whether one is missing between them (then fetch again).
     */
    fun base(n: Long?): Pair<List<C>, Boolean> {
        seq = n ?: -1L
        val later = held.filter { (seqOf(it) ?: -1L) > seq }.distinctBy { seqOf(it) }.sortedBy { seqOf(it) }
        held.clear()
        val out = ArrayList<C>(later.size)
        for (c in later) {
            if (seqOf(c) != seq + 1) return out to true
            seq = seqOf(c)!!
            out += c
        }
        return out to false
    }

    /** The server's number for the room (heartbeat, re-sync) is further on than the state shown. */
    fun behind(serverSeq: Long) = serverSeq > seq

    companion object {
        /** Re-sync (missed changes and lines) while the realtime connection is up, and while it isn't. */
        const val RESYNC_HEALTHY_MS = 45_000L
        const val RESYNC_MS = 10_000L
        /** A heartbeat or re-sync that saw a newer number waits this long for the change itself before fetching. */
        const val BEHIND_GRACE_MS = 3_000L

        fun resyncMs(healthy: Boolean) = if (healthy) RESYNC_HEALTHY_MS else RESYNC_MS

        /** The room's audio after a failed connection: a few quick tries, then one every 30 s. */
        fun audioRetryMs(tries: Int) = minOf(30_000L, 2_000L shl minOf(tries, 4))
    }
}

/** Refusals that mean I may not be in the room any more (heartbeat, re-entry after a gap, or a state fetch). */
val ROOM_REFUSALS = setOf("room_full", "room_gender", "room_age", "not_allowed", "banned", "blocked", "not_signed_in")

/**
 * What an error from the room's server calls means for my being in it: ended, removed, refused with its reason, or
 * null (a network error or anything else: keep trying).
 */
fun roomRefusal(code: String?): VoiceEvent? = when (code) {
    "room_closed", "not_found" -> VoiceEvent.Ended
    "voice_removed" -> VoiceEvent.Removed
    in ROOM_REFUSALS -> VoiceEvent.Refused(code!!)
    else -> null
}
