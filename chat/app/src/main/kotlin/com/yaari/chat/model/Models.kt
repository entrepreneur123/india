package com.yaari.chat.model

import androidx.annotation.StringRes
import com.yaari.chat.R

enum class Gender(@StringRes val label: Int) { MALE(R.string.gender_male), FEMALE(R.string.gender_female), OTHER(R.string.gender_other) }

/** Who the user wants to be matched with. Anything but [ANY] is a Premium feature. */
enum class Target(@StringRes val label: Int) {
    ANY(R.string.target_any), MALE(R.string.target_men), FEMALE(R.string.target_women);

    val isPaid get() = this != ANY

    fun accepts(gender: Gender) = when (this) {
        ANY -> true
        MALE -> gender == Gender.MALE
        FEMALE -> gender == Gender.FEMALE
    }
}

data class Post(val id: String, val text: String, val imageUri: String? = null, val createdAt: Long)

data class Profile(
    val id: String,
    val name: String,
    val gender: Gender,
    val age: Int,
    val city: String,
    val bio: String,
    val photoUri: String? = null,
    val colorSeed: Int = 0,
    val posts: List<Post> = emptyList(),
    /** ISO country from the verified phone number (set by the server); null for test accounts. */
    val country: String? = null,
    /** 3-5 tags, or none (024_discovery_profiles.sql); empty when not loaded for this card. */
    val interests: List<Interest> = emptyList(),
    /** Up to [ProfilePhoto.MAX] more photos. */
    val photos: List<ProfilePhoto> = emptyList(),
)

enum class MediaKind { IMAGE, VIDEO }

data class Media(val uri: String, val kind: MediaKind)

data class Message(val id: Long, val fromMe: Boolean, val text: String? = null, val media: Media? = null, val at: Long)

data class RecentMatch(val matchId: String, val partner: Profile, val metAt: Long, val lastText: String? = null)

enum class ReportReason(val code: String, @StringRes val label: Int) {
    NUDITY("nudity", R.string.reason_nudity),
    MINOR("minor", R.string.reason_minor),
    HARASSMENT("harassment", R.string.reason_harassment),
    FAKE("fake", R.string.reason_fake),
    OTHER("other", R.string.reason_other),
}

enum class FriendState { NONE, REQUESTED, INCOMING, FRIENDS }

/** A friend or a pending request. [threadId] is the chat to continue in, if one exists yet. */
data class Friend(val profile: Profile, val state: FriendState, val threadId: String? = null)

/**
 * A room's category (019_unified_rooms.sql): every room has the chat, the stage, music and Ludo; the category sets its
 * layout and signature feature. Rooms from before 019 (and anything unknown) are talk rooms.
 */
enum class RoomCategory(val code: String, @StringRes val label: Int, @StringRes val body: Int) {
    TALK("talk", R.string.cat_talk, R.string.cat_talk_body),
    MUSIC("music", R.string.cat_music, R.string.cat_music_body),
    GAMES("games", R.string.cat_games, R.string.cat_games_body);

    companion object {
        fun of(code: String?) = when (code) {
            "music" -> MUSIC
            "games", "gaming" -> GAMES
            else -> TALK
        }
    }
}

/** Who may enter a room. */
enum class GenderRule(val code: String) {
    ANY("any"), FEMALE("female"), MALE("male");

    fun accepts(g: Gender) = when (this) {
        ANY -> true
        FEMALE -> g == Gender.FEMALE
        MALE -> g == Gender.MALE
    }

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code } ?: ANY
    }
}
