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
