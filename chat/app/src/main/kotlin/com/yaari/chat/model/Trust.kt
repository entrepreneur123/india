package com.yaari.chat.model

import androidx.annotation.StringRes
import com.yaari.chat.R

// ---- Real identities and manners (server: 035_identity_manners.sql, functions/kyc) ----------------------------------
// One person, one account: an ID document + liveness selfie verifies one account only; gender, date of birth and
// country then come from the document and can't be changed. After a random chat both can rate each other once.

/**
 * Manners are off for now (043: the server no longer closes random chat for a low score). Scores, ratings and the
 * manner_low sheet stay in the code but are neither asked for nor shown; set to true to bring them back.
 */
const val MANNERS = false

/** A rating's tags: good ones are shown on profiles, bad ones only count (rate_chat's lists). */
enum class MannerTag(val code: String, val good: Boolean, @StringRes val label: Int) {
    KIND("kind", true, R.string.tag_kind),
    FUNNY("funny", true, R.string.tag_funny),
    RESPECTFUL("respectful", true, R.string.tag_respectful),
    GOOD_LISTENER("good_listener", true, R.string.tag_good_listener),
    INTERESTING("interesting", true, R.string.tag_interesting),
    RUDE("rude", false, R.string.tag_rude),
    SEXUAL("sexual", false, R.string.tag_sexual),
    SPAM("spam", false, R.string.tag_spam),
    FAKE("fake", false, R.string.tag_fake),
    CREEPY("creepy", false, R.string.tag_creepy);

    companion object {
        /** At most this many per rating. */
        const val MAX = 3

        fun of(code: String?) = entries.firstOrNull { it.code == code }
        fun of(good: Boolean) = entries.filter { it.good == good }
    }
}

/** How a manner score reads: below 20 random chat closes (manner_low). */
enum class MannerLevel(val min: Int, @StringRes val label: Int, val color: Long) {
    NEEDS_WORK(0, R.string.manner_needs_work, 0xFFE5484D),
    LOW(20, R.string.manner_low_level, 0xFFF76B15),
    GOOD(40, R.string.manner_good, 0xFF30A46C),
    GREAT(70, R.string.manner_great, 0xFF0090FF),
    EXCELLENT(90, R.string.manner_excellent, 0xFF8E4EC6);

    companion object {
        fun of(score: Int): MannerLevel = entries.last { score >= it.min }
    }
}

data class TagCount(val tag: MannerTag, val n: Int)

/**
 * Someone's badges (trust_json): ID [verified], [manner] 0–100 over the last 90 days from [ratings] ratings, and their
 * top good [tags]. With fewer than [MIN_RATINGS] ratings the score isn't shown ("New here").
 */
data class Trust(
    val id: String, val verified: Boolean = false, val manner: Int = 50, val ratings: Int = 0, val tags: List<TagCount> = emptyList(),
) {
    val rated get() = ratings >= MIN_RATINGS
    val level get() = MannerLevel.of(manner)

    /** The good tags only (the server keeps only those; an older or odd row can't show a bad one). */
    val goodTags get() = tags.filter { it.tag.good }

    companion object {
        const val MIN_RATINGS = 3
        /** Below this, find_match2 refuses with manner_low. */
        const val MANNER_FLOOR = 20
    }
}

/** What the kyc Edge Function and kyc_apply decide. */
enum class KycResult(val code: String) {
    VERIFIED("verified"), PENDING("pending"), DECLINED("declined"), UNREADABLE("unreadable"), MINOR("minor"),
    DUPLICATE("duplicate"), NONE("none");

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code }
    }
}

/** My latest verification attempt (kyc_sessions): the provider's [status] and our [result] once decided. */
data class KycAttempt(val status: String, val result: KycResult?, val at: Long? = null)

/**
 * my_trust(): my own badges, whether the server requires verification ([required]: app_flags), my latest attempt, and
 * the random chats I rated in the last day ([rated], so the sheet doesn't ask twice).
 */
data class MyTrust(
    val trust: Trust, val required: Boolean = false, val kyc: KycAttempt? = null, val rated: Set<String> = emptySet(),
) {
    val verified get() = trust.verified
}
