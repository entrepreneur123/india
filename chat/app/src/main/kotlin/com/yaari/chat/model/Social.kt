package com.yaari.chat.model

import androidx.annotation.StringRes
import com.yaari.chat.R

// ---- Notifications, room search, interests and photos (server: 023_notifications.sql, 024_discovery_profiles.sql) ----
// Same shapes as the web (chat/web/lib/backend.ts).

/** Interest tags; the server has the same 20 (profiles.interests). A profile has none, or 3 to 5. */
enum class Interest(val code: String, @StringRes val label: Int) {
    MUSIC("music", R.string.tag_music), MOVIES("movies", R.string.tag_movies), CRICKET("cricket", R.string.tag_cricket),
    FOOTBALL("football", R.string.tag_football), GAMING("gaming", R.string.tag_gaming), TRAVEL("travel", R.string.tag_travel),
    FOOD("food", R.string.tag_food), FITNESS("fitness", R.string.tag_fitness), BOOKS("books", R.string.tag_books),
    TECH("tech", R.string.tag_tech), ART("art", R.string.tag_art), DANCE("dance", R.string.tag_dance),
    FASHION("fashion", R.string.tag_fashion), PHOTOGRAPHY("photography", R.string.tag_photography), ANIME("anime", R.string.tag_anime),
    PETS("pets", R.string.tag_pets), STUDY("study", R.string.tag_study), BUSINESS("business", R.string.tag_business),
    SPIRITUALITY("spirituality", R.string.tag_spirituality), COMEDY("comedy", R.string.tag_comedy);

    companion object {
        const val MIN = 3
        const val MAX = 5

        /** Known tags only, each once, in the server's order. */
        fun of(codes: Collection<String>?): List<Interest> = entries.filter { it.code in codes.orEmpty() }

        /** A choice the server accepts: none, or [MIN] to [MAX]. */
        fun valid(tags: Collection<Interest>) = tags.toSet().size.let { it == 0 || it in MIN..MAX }
    }
}

/** One more photo on a profile: [id] is the stored file, [url] what to show. */
data class ProfilePhoto(val id: String, val url: String) {
    companion object {
        const val MAX = 4
    }
}

enum class InboxKind(val code: String) {
    MESSAGE("message"), FRIEND_REQUEST("friend_request"), FRIEND_ACCEPT("friend_accept"), ROOM_INVITE("room_invite"),
    /** Likes on my post: one line per post until read, [InboxItem.count] people (the newest is the actor). */
    POST_LIKE("post_like"),
    /** Comments on my post, folded the same way. */
    POST_COMMENT("post_comment"),
    /** A friend asks for a call (053): unread while it rings; answered, declined or ended, it is read. */
    CALL("call");

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code }
    }
}

/**
 * A line in Notifications. [count]: messages folded into a [InboxKind.MESSAGE] item (one per chat), or the likes and
 * comments of a post. [roomOpen]: the room
 * of an invite is still live. [pending]: a friend request still waiting for my answer.
 */
data class InboxItem(
    val id: Long, val kind: InboxKind, val actor: Profile, val at: Long, val read: Boolean,
    val count: Int = 1, val matchId: String? = null, val roomId: String? = null, val roomTitle: String? = null,
    val roomOpen: Boolean = false, val pending: Boolean = false,
    /** The post a like or comment is about. */
    val post: PostRef? = null,
)

/** Unread counts (my_badges): Notifications, friend requests waiting for me, and unread messages per chat (match id). */
data class Badges(val inbox: Int = 0, val friends: Int = 0, val chats: Map<String, Int> = emptyMap()) {
    /**
     * The Recent tab's number: chats with unread messages (as the web). Friend requests are not in it: they are in the
     * bell, which clears when opened, and on the Friends segment; a number that only an answer clears looked stuck.
     */
    val recent get() = chats.values.count { it > 0 }

    fun unread(matchId: String?) = matchId?.let { chats[it] } ?: 0
}

/** Room search: words of the title and the room's language ([RoomLang] tag); empty finds everything. */
data class RoomQuery(val text: String = "", val lang: String? = null) {
    val isEmpty get() = text.isBlank() && lang == null
}

/** The languages a room can speak: the app's five. Tags as the app uses them (pt-BR); the server keeps them lower case. */
object RoomLang {
    val tags = listOf("en", "hi", "bn", "pt-BR", "vi")

    /** The server's value (pt-br) to the app's tag; null when unknown. */
    fun of(server: String?): String? = tags.firstOrNull { it.equals(server, ignoreCase = true) }

    /** The app's tag as the server stores it. */
    fun server(tag: String?): String? = of(tag)?.lowercase()
}
