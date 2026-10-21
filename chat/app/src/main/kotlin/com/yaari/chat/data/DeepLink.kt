package com.yaari.chat.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Where a link or a tapped notification takes the app. Room links are the web's: https://yaari-web-indol.vercel.app/r/<id>
 * for a public room and /r/<code> for a private one (the code lets the person in). Notifications carry yaari://
 * links inside the app only (no intent filter opens them from outside).
 */
sealed interface DeepLink {
    data class Room(val id: String) : DeepLink
    data class Code(val code: String) : DeepLink
    /** A room invite from Notifications: [inboxId] lets me in even when the room is private. */
    data class Invite(val inboxId: Long, val roomId: String) : DeepLink
    /** A friend's chat; [actorId] and [actorName] show who it is with until their card has loaded. */
    data class Thread(val matchId: String, val actorId: String?, val actorName: String) : DeepLink
    data object Inbox : DeepLink

    /** The link as a URI string (for a notification's intent). */
    fun uri(): String = when (this) {
        is Room -> WEB + enc(id)
        is Code -> WEB + enc(code)
        is Invite -> "$SCHEME://invite/$inboxId?room=${enc(roomId)}"
        is Thread -> "$SCHEME://thread/${enc(matchId)}?name=${enc(actorName)}" + (actorId?.let { "&actor=${enc(it)}" } ?: "")
        Inbox -> "$SCHEME://inbox"
    }

    companion object {
        const val HOST = "yaari-web-indol.vercel.app"
        const val WEB = "https://$HOST/r/"
        private const val SCHEME = "yaari"
        private val ROOM_ID = Regex("^v-[0-9a-z]+$", RegexOption.IGNORE_CASE)
        private val CODE = Regex("^[A-Z0-9]{6}$", RegexOption.IGNORE_CASE)

        /** The link to share for a room: its code when private (only the host has it), else its id. */
        fun roomLink(id: String, code: String?, isPrivate: Boolean): String = Room(id).let {
            if (isPrivate && code != null) Code(code).uri() else it.uri()
        }

        /** A room's /r/ target ("v-..." id or 6-character code), or null when it is neither. */
        fun ofTarget(target: String): DeepLink? = when {
            ROOM_ID.matches(target) -> Room(target)
            CODE.matches(target) -> Code(target.uppercase())
            else -> null
        }

        /** Parses a room link (also the older ?voice=<id>) or an in-app yaari:// link; null for anything else. */
        fun parse(link: String?): DeepLink? {
            val uri = runCatching { URI(link?.trim() ?: return null) }.getOrNull() ?: return null
            val path = uri.rawPath.orEmpty().trimEnd('/')
            fun query(key: String) = uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("$key=") }?.substringAfter('=')?.let(::dec)
            return when (uri.scheme?.lowercase()) {
                "https", "http" -> {
                    if (uri.host?.lowercase() != HOST) return null
                    val target = Regex("^/r/([^/]+)$").find(path)?.groupValues?.get(1)?.let(::dec)
                    target?.let(::ofTarget) ?: query("voice")?.takeIf { path.isEmpty() }?.let { if (ROOM_ID.matches(it)) Room(it) else null }
                }
                SCHEME -> {
                    val segment = path.trimStart('/')
                    when (uri.host) {
                        "inbox" -> Inbox
                        "invite" -> segment.toLongOrNull()?.let { id -> query("room")?.let { Invite(id, it) } }
                        "thread" -> segment.takeIf { it.isNotEmpty() }?.let { Thread(dec(it), query("actor"), query("name").orEmpty()) }
                        else -> null
                    }
                }
                else -> null
            }
        }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        private fun dec(s: String) = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)
    }
}

/**
 * Links waiting to be opened. MainActivity puts them here (from a VIEW intent or a tapped notification); the app takes
 * one when it can act on it, so a room link survives signing up first (as on the web).
 */
class LinkBus {
    private val state = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = state.asStateFlow()

    fun send(link: DeepLink?) {
        if (link != null) state.value = link
    }

    /** Takes [link] if it is still the one waiting (a newer one stays). */
    fun taken(link: DeepLink) {
        state.compareAndSet(link, null)
    }

    companion object {
        /** The app's one bus (tests make their own). */
        val app = LinkBus()
    }
}
