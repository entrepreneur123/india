package com.yaari.chat.data

import com.yaari.chat.model.InboxKind

/**
 * The Firebase project the live build pushes through (FCM). Empty values (the default: gradle properties
 * FIREBASE_APP_ID, FIREBASE_API_KEY, FIREBASE_PROJECT_ID, FIREBASE_SENDER_ID) switch push off: no Firebase code runs
 * and no token is registered. See docs/LAUNCH_GUIDE.md section 4-2.
 */
data class PushConfig(val appId: String, val apiKey: String, val projectId: String, val senderId: String) {
    val enabled get() = listOf(appId, apiKey, projectId, senderId).all { it.isNotBlank() }

    companion object {
        val OFF = PushConfig("", "", "", "")
    }
}

/** The notification channels (the server's `channel`); Android 13+ lets people mute each one. */
enum class PushChannel(val id: String) { MESSAGES("messages"), SOCIAL("social"), ROOMS("rooms") }

/**
 * One push (server: functions/push, data only): the kind, ids and the sender's name, never message text. The app
 * writes the sentence in its own language ([com.yaari.chat.PushNotifier]).
 */
data class PushMessage(
    val kind: InboxKind, val inboxId: Long?, val actorId: String?, val actorName: String, val count: Int,
    val matchId: String?, val roomId: String?,
) {
    val channel get() = when (kind) {
        InboxKind.MESSAGE -> PushChannel.MESSAGES
        InboxKind.FRIEND_REQUEST, InboxKind.FRIEND_ACCEPT -> PushChannel.SOCIAL
        InboxKind.ROOM_INVITE -> PushChannel.ROOMS
    }

    /** Where tapping it goes: the chat, the room (through the invite), else Notifications. */
    val link: DeepLink get() = when {
        kind == InboxKind.MESSAGE && matchId != null -> DeepLink.Thread(matchId, actorId, actorName)
        kind == InboxKind.ROOM_INVITE && inboxId != null && roomId != null -> DeepLink.Invite(inboxId, roomId)
        else -> DeepLink.Inbox
    }

    /** One notification per chat, person or room (as the server's collapse key): a newer one replaces it. */
    val key get() = "${kind.code}:${matchId ?: roomId ?: actorId ?: inboxId}"

    companion object {
        /** Null for anything that is not one of ours (an unknown kind, no name). */
        fun parse(data: Map<String, String>): PushMessage? {
            val kind = InboxKind.of(data["kind"]) ?: return null
            val name = data["actor_name"]?.trim().orEmpty().take(40).ifEmpty { return null }
            return PushMessage(
                kind, data["id"]?.toLongOrNull(), data["actor"]?.ifBlank { null }, name, data["n"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                data["match"]?.ifBlank { null }, data["room"]?.ifBlank { null },
            )
        }
    }
}

/** This phone's FCM token (the live build's Firebase Messaging). */
interface PushTokens {
    /** Null when there is none (no Play services, offline). */
    suspend fun token(): String?
}

/** Phone push for the signed-in person: registered after sign-in, unregistered at sign-out. */
interface Push {
    val enabled: Boolean
    suspend fun register()
    suspend fun unregister()

    /** No push (the demo, tests, and the live build until Firebase is configured). */
    object Off : Push {
        override val enabled = false
        override suspend fun register() = Unit
        override suspend fun unregister() = Unit
    }
}

/**
 * Registers the token with the app's language and the phone's time zone (quiet hours are local). With push off
 * ([PushConfig.enabled] false) it never asks for a token: Firebase stays untouched.
 */
class PushRegistrar(
    private val config: PushConfig,
    private val tokens: () -> PushTokens,
    private val backend: Backend,
    private val locale: () -> String,
    private val timeZone: () -> String,
) : Push {
    private var registered: String? = null

    override val enabled get() = config.enabled

    override suspend fun register() {
        if (!enabled) return
        val token = tokens().token() ?: return
        backend.registerDevice(token, locale(), timeZone())
        registered = token
    }

    /** A new token from FCM while signed in. */
    suspend fun renewed(token: String) {
        if (!enabled || !backend.isSignedIn()) return
        backend.registerDevice(token, locale(), timeZone())
        registered = token
    }

    override suspend fun unregister() {
        if (!enabled) return
        val token = registered ?: tokens().token() ?: return
        backend.unregisterDevice(token)
        registered = null
    }
}
