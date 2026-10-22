package com.yaari.chat.model

import androidx.annotation.StringRes
import com.yaari.chat.R

// ---- IDs and looks you buy (server: 040_handles_looks.sql) ---------------------------------------------------------
// Every profile has an ID (handle) apart from its name; looks (a badge, the ID's colour and font, a profile frame) are
// bought once in the app (Google Play), owned for good, worn by choice and seen by everyone. Same shapes as the web.

/** handle_check's answer: free, exactly my ID already, against the rules, reserved, or someone else's. */
enum class HandleCheck(val code: String) {
    OK("ok"), MINE("mine"), INVALID("invalid"), RESERVED("reserved"), TAKEN("taken");

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code }
    }
}

enum class LookKind(val code: String) {
    BADGE("badge"), COLOR("color"), FONT("font"), FRAME("frame");

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code }
    }
}

/**
 * The catalogue the apps can draw (cosmetic_items): [id] is also the Google Play product id of a one-time,
 * non-consumable product. The server's list decides what is on sale and its price; an item the app doesn't know
 * (added later) is left out.
 */
enum class Cosmetic(val id: String, val kind: LookKind, @StringRes val label: Int) {
    BADGE_CROWN("badge_crown", LookKind.BADGE, R.string.look_badge_crown),
    BADGE_DIAMOND("badge_diamond", LookKind.BADGE, R.string.look_badge_diamond),
    BADGE_FLAME("badge_flame", LookKind.BADGE, R.string.look_badge_flame),
    BADGE_STAR("badge_star", LookKind.BADGE, R.string.look_badge_star),
    BADGE_HEART("badge_heart", LookKind.BADGE, R.string.look_badge_heart),
    BADGE_BOLT("badge_bolt", LookKind.BADGE, R.string.look_badge_bolt),
    COLOR_GOLD("color_gold", LookKind.COLOR, R.string.look_color_gold),
    COLOR_ROSE("color_rose", LookKind.COLOR, R.string.look_color_rose),
    COLOR_OCEAN("color_ocean", LookKind.COLOR, R.string.look_color_ocean),
    COLOR_EMERALD("color_emerald", LookKind.COLOR, R.string.look_color_emerald),
    COLOR_VIOLET("color_violet", LookKind.COLOR, R.string.look_color_violet),
    COLOR_SUNSET("color_sunset", LookKind.COLOR, R.string.look_color_sunset),
    COLOR_AURORA("color_aurora", LookKind.COLOR, R.string.look_color_aurora),
    COLOR_RAINBOW("color_rainbow", LookKind.COLOR, R.string.look_color_rainbow),
    FONT_SCRIPT("font_script", LookKind.FONT, R.string.look_font_script),
    FONT_RETRO("font_retro", LookKind.FONT, R.string.look_font_retro),
    FONT_BOLD("font_bold", LookKind.FONT, R.string.look_font_bold),
    FONT_PIXEL("font_pixel", LookKind.FONT, R.string.look_font_pixel),
    FONT_ROUND("font_round", LookKind.FONT, R.string.look_font_round),
    FONT_FUTURE("font_future", LookKind.FONT, R.string.look_font_future),
    FRAME_FROST("frame_frost", LookKind.FRAME, R.string.look_frame_frost),
    FRAME_FIRE("frame_fire", LookKind.FRAME, R.string.look_frame_fire),
    FRAME_JEWEL("frame_jewel", LookKind.FRAME, R.string.look_frame_jewel),
    FRAME_SAND("frame_sand", LookKind.FRAME, R.string.look_frame_sand),
    FRAME_NEON("frame_neon", LookKind.FRAME, R.string.look_frame_neon),
    FRAME_SAKURA("frame_sakura", LookKind.FRAME, R.string.look_frame_sakura);

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id }

        /** A look's product id (verify-purchase's rule), as opposed to Plus and the gender pass. */
        fun isLook(productId: String) = LOOK_ID.matches(productId)

        private val LOOK_ID = Regex("^(badge|color|font|frame)_[a-z0-9]{2,20}$")
    }
}

/** An item on sale ([Cosmetic.id]), with the server's price in rupees (the label when Play can't say the local one). */
data class LookItem(val id: String, val kind: LookKind, val priceInr: Int)

/** Someone's ID and what they wear ([userId]: whose); null: nothing of that kind. */
data class Look(
    val userId: String, val handle: String,
    val badge: String? = null, val color: String? = null, val font: String? = null, val frame: String? = null,
) {
    fun of(kind: LookKind) = when (kind) {
        LookKind.BADGE -> badge
        LookKind.COLOR -> color
        LookKind.FONT -> font
        LookKind.FRAME -> frame
    }

    /** The same look with [item] (null: none) worn for [kind]. */
    fun with(kind: LookKind, item: String?) = when (kind) {
        LookKind.BADGE -> copy(badge = item)
        LookKind.COLOR -> copy(color = item)
        LookKind.FONT -> copy(font = item)
        LookKind.FRAME -> copy(frame = item)
    }
}

/**
 * shop(): what is on sale (by kind, in the shop's order), what I own and wear, and [handleNext]: when my ID may change
 * again for free (epoch ms; null: now), and [handlePasses]: paid ID changes left for before then.
 */
data class Shop(val items: List<LookItem>, val owned: Set<String>, val look: Look?, val handleNext: Long? = null, val handlePasses: Int = 0)

/** Someone found by ID, or on my block list (042). */
data class PersonRow(val id: String, val handle: String, val name: String, val photoUri: String?)
