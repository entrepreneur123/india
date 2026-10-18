package com.yaari.chat.model

enum class Gender(val label: String) { MALE("Male"), FEMALE("Female"), OTHER("Other") }

/** Who the user wants to be matched with. Anything but [ANY] is a Premium feature. */
enum class Target(val label: String) {
    ANY("Everyone"), MALE("Men"), FEMALE("Women");

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

enum class VideoState { NONE, REQUESTING, IN_CALL }

data class ChatSession(
    val partner: Profile,
    /** Monotonic clock (elapsedRealtime) when the match started; drives the exit lock. */
    val startedAtMs: Long,
    val messages: List<Message> = emptyList(),
    val partnerTyping: Boolean = false,
    val partnerLeft: Boolean = false,
    val video: VideoState = VideoState.NONE,
)

data class RecentMatch(val partner: Profile, val metAt: Long, val dm: List<Message> = emptyList())
