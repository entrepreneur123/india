package com.yaari.chat.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Requests from the "In a voice room" notification to the app (VoiceService → AppViewModel). */
object VoiceBus {
    enum class Request {
        /** Bring the room up full screen. */
        OPEN,
        /** Leave the room (a listener or speaker). */
        LEAVE,
        /** The host's "Leave": show the room with its "End for everyone?" confirm. */
        END,
    }

    private val _requests = MutableSharedFlow<Request>(extraBufferCapacity = 4)
    val requests: SharedFlow<Request> = _requests.asSharedFlow()

    fun send(r: Request) {
        _requests.tryEmit(r)
    }
}
