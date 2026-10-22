package com.yaari.chat.model

import androidx.annotation.StringRes
import com.yaari.chat.R

// ---- Profile details, posts with likes and comments (server: 033_profiles_feed.sql) ----

/** The languages a profile can list (profiles.languages), each in its own script: nobody needs them translated. */
enum class SpokenLang(val code: String, val native: String) {
    EN("en", "English"), HI("hi", "हिन्दी"), BN("bn", "বাংলা"), TA("ta", "தமிழ்"), TE("te", "తెలుగు"), MR("mr", "मराठी"),
    GU("gu", "ગુજરાતી"), KN("kn", "ಕನ್ನಡ"), ML("ml", "മലയാളം"), PA("pa", "ਪੰਜਾਬੀ"), UR("ur", "اردو"), OR("or", "ଓଡ଼ିଆ"),
    AS("as", "অসমীয়া"), PT("pt", "Português"), VI("vi", "Tiếng Việt");

    companion object {
        const val MAX = 6

        /** Known codes only, each once, in the list's order. */
        fun of(codes: Collection<String>?): List<SpokenLang> = entries.filter { it.code in codes.orEmpty() }
    }
}

/** The questions a profile can answer (profiles.prompts[].q), like Hinge's prompts. */
enum class PromptQ(val code: String, @StringRes val label: Int) {
    WEEKEND("weekend", R.string.prompt_weekend), FOOD("food", R.string.prompt_food), SONG("song", R.string.prompt_song),
    TRAVEL("travel", R.string.prompt_travel), TALK("talk", R.string.prompt_talk), LAUGH("laugh", R.string.prompt_laugh),
    GOAL("goal", R.string.prompt_goal), SECRET("secret", R.string.prompt_secret);

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code }
    }
}

/** One answered question; [answer] is 1–[MAX_ANSWER] characters. */
data class ProfilePrompt(val q: PromptQ, val answer: String) {
    companion object {
        const val MAX = 3
        const val MAX_ANSWER = 120
    }
}

/** A profile's details beyond the basics: a status line, the languages spoken and answered prompts. */
data class ProfileStyle(
    val status: String? = null,
    val languages: List<SpokenLang> = emptyList(),
    val prompts: List<ProfilePrompt> = emptyList(),
) {
    val isEmpty get() = status.isNullOrBlank() && languages.isEmpty() && prompts.isEmpty()

    /** What the server accepts (it checks again): a status of 1–60, up to 6 languages, up to 3 different questions answered. */
    fun valid() = (status == null || status.trim().length in 1..MAX_STATUS) && languages.size <= SpokenLang.MAX &&
        prompts.size <= ProfilePrompt.MAX && prompts.map { it.q }.toSet().size == prompts.size &&
        prompts.all { it.answer.trim().length in 1..ProfilePrompt.MAX_ANSWER }

    companion object {
        const val MAX_STATUS = 60
    }
}

/** Who sees a post: friends and people I chatted with (the default), or everyone. */
enum class PostVisibility(val code: String) {
    FRIENDS("friends"), PUBLIC("public");

    companion object {
        fun of(code: String?) = if (code == "public") PUBLIC else FRIENDS
    }
}

/** A heart's state after liking or unliking (post_like). */
data class LikeState(val likes: Int, val liked: Boolean)

/**
 * A comment under a post, oldest first. [replyTo]/[replyName]: the comment it answers and its author. [canRemove]: mine,
 * or under my post.
 */
data class Comment(
    val id: Long, val postId: String, val body: String, val at: Long, val author: Profile,
    val replyTo: Long? = null, val replyName: String? = null, val mine: Boolean = false, val canRemove: Boolean = false,
) {
    companion object {
        const val MAX = 300
    }
}

/** The post a like or comment notification is about (its text cut to 80 characters). */
data class PostRef(val id: String, val body: String, val imageUri: String? = null)
