package com.yaari.chat.data

/** LiveKit's ConnectionQuality, without the SDK (the demo build has none). */
enum class Signal { EXCELLENT, GOOD, POOR, LOST, UNKNOWN }

/** What the call screen tells me about my partner, most serious first. The web's lib/rtc.ts partnerNotice. */
enum class PartnerNotice { LOST, PAUSED, WEAK }

/**
 * Their connection dropped (or they left): their picture would otherwise freeze on the last frame. Paused: LiveKit
 * stopped their video because the network can't carry even the smallest layer. Weak: poor connection. Null if fine.
 */
fun partnerNotice(quality: Signal, videoPaused: Boolean, left: Boolean): PartnerNotice? = when {
    left || quality == Signal.LOST -> PartnerNotice.LOST
    videoPaused -> PartnerNotice.PAUSED
    quality == Signal.POOR -> PartnerNotice.WEAK
    else -> null
}

/** Bars for my connection indicator: 3 excellent, 2 good, 1 poor, 0 lost; null while unknown. */
fun signalBars(q: Signal): Int? = when (q) {
    Signal.EXCELLENT -> 3
    Signal.GOOD -> 2
    Signal.POOR -> 1
    Signal.LOST -> 0
    Signal.UNKNOWN -> null
}

/**
 * The camera should run only while I want it, may use it, and the app is on screen: Android takes the camera away
 * from an app in the background, which would leave my partner looking at a frozen frame.
 */
fun cameraWanted(buttonOn: Boolean, permitted: Boolean, foreground: Boolean) = buttonOn && permitted && foreground
