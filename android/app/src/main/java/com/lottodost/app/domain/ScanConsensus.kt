package com.lottodost.app.domain

/**
 * Live camera frames are read many times a second and one blurred frame can turn an 8 into a B.
 * A number is accepted only once it is read the same way in 2 of the last 3 frames that had one.
 */
class ScanConsensus(private val needed: Int = 2, private val window: Int = 3) {
    private val recent = ArrayDeque<String>()

    /** Returns the frame text to use when this frame confirms a number, else null. */
    fun offer(text: String): String? {
        val best = TicketParser.fromOcr(text).tickets.firstOrNull()?.display() ?: return null
        recent.addLast(best)
        while (recent.size > window) recent.removeFirst()
        return if (recent.count { it == best } >= needed) text else null
    }
}
