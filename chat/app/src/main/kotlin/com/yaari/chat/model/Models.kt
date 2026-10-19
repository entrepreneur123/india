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

/** What a user room is about; the server picks the emoji from it. */
enum class RoomCategory(val code: String, val emoji: String, @StringRes val label: Int) {
    CHAT("chat", "💬", R.string.cat_chat),
    FRIENDS("friends", "🤝", R.string.cat_friends),
    LANGUAGE("language", "🗣️", R.string.cat_language),
    MUSIC("music", "🎧", R.string.cat_music),
    MOVIES("movies", "🎬", R.string.cat_movies),
    GAMING("gaming", "🎮", R.string.cat_gaming),
    SPORTS("sports", "⚽", R.string.cat_sports),
    STUDY("study", "🎓", R.string.cat_study),
    TRAVEL("travel", "✈️", R.string.cat_travel),
    NIGHT("night", "🌙", R.string.cat_night);

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code } ?: CHAT
    }
}

/** Who may enter a user room. */
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

/**
 * A room; [online] counts people who had it open in the last 90 s. Official rooms have no owner and no conditions.
 * User rooms carry their conditions; [code] is known only for my own rooms, and [refusal] is the server's reason
 * why I can't enter (room_closed, room_gender, room_age, room_full, not_allowed), null when I may.
 */
data class Room(
    val id: String, val title: String, val emoji: String, val online: Int,
    val official: Boolean = true,
    val mine: Boolean = false,
    val ownerName: String? = null,
    val category: RoomCategory = RoomCategory.CHAT,
    val maxMembers: Int? = null,
    val genderRule: GenderRule = GenderRule.ANY,
    val minAge: Int = MIN_AGE,
    val maxAge: Int = MAX_AGE,
    val isPrivate: Boolean = false,
    val code: String? = null,
    val refusal: String? = null,
) {
    val hasAgeLimit get() = minAge > MIN_AGE || maxAge < MAX_AGE

    companion object {
        const val MIN_AGE = 18
        const val MAX_AGE = 100
        const val MIN_MEMBERS = 2
        const val MAX_MEMBERS = 50
        const val MIN_TITLE = 2
        const val MAX_TITLE = 40
        const val CODE_LENGTH = 6
    }
}

/** A room the user is opening. The server validates every field again. */
data class NewRoom(
    val title: String, val category: RoomCategory, val maxMembers: Int, val genderRule: GenderRule,
    val minAge: Int, val maxAge: Int, val isPrivate: Boolean,
)

data class CreatedRoom(val roomId: String, val code: String)

/** [sender] carries only the public card (name, age, gender, flag, photo); posts load on tap. */
data class RoomMessage(val id: Long, val sender: Profile, val fromMe: Boolean, val text: String, val at: Long)
