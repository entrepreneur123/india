package com.yaari.chat.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Whether the partner of a random chat is still there, from the chat channel's realtime presence (web and app use
 * the same rules and timings). Time only counts while MY channel is subscribed: when my own connection drops the
 * timers stop, and after the rejoin the presence sync tells the current state.
 *
 * - The partner's presence (or, for older apps without presence, any message or typing signal) is not seen within
 *   [PARTNER_JOIN_TIMEOUT_MS] of my channel subscribing: they never connected.
 * - Their presence leaves and does not come back within [PARTNER_GRACE_MS]: they disconnected.
 *
 * Either way [onGone] runs once. Call everything from one coroutine (the chat's event flow).
 */
class PartnerWatch(private val scope: CoroutineScope, private val onGone: suspend () -> Unit) {
    private var subscribed = false
    /** The partner's presence is in my channel's current state. */
    private var present = false
    /** Their presence was seen at least once (only then can it "leave"). */
    private var presenceSeen = false
    /** Presence, a message or a typing signal was seen at least once. */
    private var seen = false
    private var gone = false
    private var timer: Job? = null

    /** My channel's state: subscribed (and the socket connected) or not. */
    fun connection(up: Boolean) {
        if (gone || up == subscribed) return
        subscribed = up
        stop()
        if (!up) {
            present = false // unknown until the next sync
            return
        }
        when {
            !seen -> start(PARTNER_JOIN_TIMEOUT_MS)
            presenceSeen -> start(PARTNER_GRACE_MS) // the sync right after the rejoin cancels it if they are there
        }
    }

    /** The partner's presence joined ([here]) or left my channel. */
    fun presence(here: Boolean) {
        if (gone || here == present) return
        present = here
        if (here) {
            presenceSeen = true
            seen = true
            stop()
        } else if (subscribed) {
            start(PARTNER_GRACE_MS)
        }
    }

    /** A message or typing signal from the partner: they did connect (older apps send no presence). */
    fun activity() {
        if (gone || seen) return
        seen = true
        if (!presenceSeen) stop() // only the join timer can be running
    }

    private fun stop() {
        timer?.cancel()
        timer = null
    }

    private fun start(ms: Long) {
        stop()
        timer = scope.launch {
            delay(ms)
            gone = true
            timer = null
            onGone()
        }
    }

    companion object {
        const val PARTNER_JOIN_TIMEOUT_MS = 12_000L
        const val PARTNER_GRACE_MS = 20_000L
    }
}
