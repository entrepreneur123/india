package com.yaari.chat.model

import androidx.annotation.StringRes
import com.yaari.chat.R

// ---- Profiles people make their own, posts with likes and comments, the feed (server: 033_profiles_feed.sql) ----

/** A profile's colour (profiles.accent): tints the cover, the header and the avatar ring. Null is Yaari's own pink. */
enum class Accent(val code: String, @StringRes val label: Int) {
    ROSE("rose", R.string.accent_rose), VIOLET("violet", R.string.accent_violet), OCEAN("ocean", R.string.accent_ocean),
    MINT("mint", R.string.accent_mint), SUNSET("sunset", R.string.accent_sunset), GOLD("gold", R.string.accent_gold),
    BERRY("berry", R.string.accent_berry), SLATE("slate", R.string.accent_slate);

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code }
    }
}

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

/**
 * What a profile looks like beyond the basics. [cover]: the cover photo to show (a signed URL, or the demo's file);
 * [coverId]: where it is stored ("<id>/cover/<file>"), sent back unchanged when the cover stays.
 */
data class ProfileStyle(
    val cover: String? = null,
    val coverId: String? = null,
    val accent: Accent? = null,
    val status: String? = null,
    val languages: List<SpokenLang> = emptyList(),
    val prompts: List<ProfilePrompt> = emptyList(),
) {
    val isEmpty get() = cover == null && accent == null && status.isNullOrBlank() && languages.isEmpty() && prompts.isEmpty()

    /** What the server accepts (it checks again): a status of 1–60, up to 6 languages, up to 3 different questions answered. */
    fun valid() = (status == null || status.trim().length in 1..MAX_STATUS) && languages.size <= SpokenLang.MAX &&
        prompts.size <= ProfilePrompt.MAX && prompts.map { it.q }.toSet().size == prompts.size &&
        prompts.all { it.answer.trim().length in 1..ProfilePrompt.MAX_ANSWER }

    companion object {
        const val MAX_STATUS = 60
    }
}

/** Who sees a post: friends and people I chatted with (the default), or everyone (and Discover). */
enum class PostVisibility(val code: String) {
    FRIENDS("friends"), PUBLIC("public");

    companion object {
        fun of(code: String?) = if (code == "public") PUBLIC else FRIENDS
    }
}

/** The feed's two lists. */
enum class FeedTab(val code: String) { FRIENDS("friends"), DISCOVER("discover") }

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
