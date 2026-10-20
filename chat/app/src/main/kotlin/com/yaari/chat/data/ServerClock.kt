package com.yaari.chat.data

/**
 * The server's clock minus this phone's, from the reply with the shortest round trip (the shared music position
 * depends on it: a phone whose clock is a few seconds off would otherwise play a few seconds off). The web's noteClock.
 */
class ServerClock {
    /** Server time minus local time, in ms. */
    var offset = 0L
        private set
    private var bestRtt = Long.MAX_VALUE / 4

    /** A reply that carried the server's time [serverNow], for a request sent at [sent] and answered at [received] (local ms). */
    @Synchronized
    fun note(serverNow: Long, sent: Long, received: Long) {
        val rtt = received - sent
        if (rtt < 0 || rtt > bestRtt + SLACK_MS) {
            bestRtt += AGE_MS // a better sample is welcome again after a while
            return
        }
        bestRtt = rtt
        offset = serverNow - (sent + rtt / 2)
    }

    /** A server timestamp on this phone's clock. */
    fun toLocal(serverMs: Long) = serverMs - offset

    private companion object {
        const val SLACK_MS = 150L
        const val AGE_MS = 20L
    }
}
