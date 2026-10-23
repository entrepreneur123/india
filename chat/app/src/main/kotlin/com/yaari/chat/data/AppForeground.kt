package com.yaari.chat.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Whether the app is on screen. Polling loops (matching every 2 s, chat re-sync 10 s, room re-sync 15 s,
 * room heartbeat 30 s) wait here while the app is in the background, so a phone in a pocket spends
 * no mobile data or battery on them. MainActivity sets it from onStart / onStop. Starts visible, so
 * code without an activity (tests) never waits.
 */
object AppForeground {
    private val state = MutableStateFlow(true)
    val visible: StateFlow<Boolean> = state.asStateFlow()

    fun set(visible: Boolean) {
        state.value = visible
    }
}

/** Suspends until [visible] is true; returns at once when it already is. */
suspend fun StateFlow<Boolean>.awaitVisible() {
    if (!value) first { it }
}
