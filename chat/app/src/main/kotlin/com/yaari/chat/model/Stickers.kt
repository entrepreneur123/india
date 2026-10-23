package com.yaari.chat.model

// ---- Sticker packs (046): a sticker is a whole chat message ":st:<pack>/<n>:" (pack: the item id without "sticker_",
// n 1-8), and the server refuses one from a pack the sender doesn't own (sticker_not_owned). Each is drawn as a card:
// a big emoji and a short caption, the same words for everyone (they are the art). Same as the web (lib/stickers.ts).

data class StickerArt(val emoji: String, val caption: String)

/** A sticker message: its pack's item id, its number and its art (null: a pack this app doesn't know yet). */
data class Sticker(val pack: String, val n: Int, val art: StickerArt?)

object Stickers {
    val PACKS: Map<Cosmetic, List<StickerArt>> = mapOf(
        Cosmetic.STICKER_NAMASTE to art(
            "🙏" to "Namaste", "👋" to "Hello ji!", "😊" to "Kaise ho?", "🌸" to "Swagat hai",
            "🙌" to "Shukriya", "🤗" to "Aao baitho", "🌞" to "Suprabhat", "🌙" to "Shubh ratri",
        ),
        Cosmetic.STICKER_CHAI to art(
            "☕" to "Chai time", "🍵" to "Ek cutting?", "🍪" to "Chai + biscuit", "🌧️" to "Baarish & chai",
            "😌" to "Sukoon", "🍛" to "Kuch khaoge?", "🗣️" to "Chai pe charcha", "💯" to "Ekdum kadak!",
        ),
        Cosmetic.STICKER_CRICKET to art(
            "🏏" to "Shot!", "💥" to "Chhakka!", "⚡" to "Chauka!", "🎯" to "Howzat?",
            "😱" to "Wicket!", "👑" to "Captain", "🔥" to "On fire", "🏆" to "Champions!",
        ),
        Cosmetic.STICKER_LOVE to art(
            "❤️" to "Pyaar", "💕" to "Cutie", "😘" to "Muah!", "💌" to "For you",
            "🌹" to "Gulab", "💞" to "Tum aur main", "💖" to "Dil se", "😍" to "Uff!",
        ),
    )

    private fun art(vararg a: Pair<String, String>) = a.map { StickerArt(it.first, it.second) }

    private val BODY = Regex("^:st:([a-z0-9]{2,20})/([1-8]):$")

    /** The sticker [body] sends, or null for any other text. */
    fun of(body: String?): Sticker? {
        val m = body?.let(BODY::matchEntire) ?: return null
        val pack = "sticker_" + m.groupValues[1]
        val n = m.groupValues[2].toInt()
        return Sticker(pack, n, Cosmetic.of(pack)?.let(PACKS::get)?.get(n - 1))
    }

    /** The message that sends sticker [n] (1-8) of [pack]. */
    fun body(pack: Cosmetic, n: Int) = ":st:${pack.id.removePrefix("sticker_")}/$n:"

    /** A line as a list or quote shows it: a sticker becomes its emoji and [label] ("🙏 Sticker"). */
    fun preview(body: String, label: String) = of(body)?.let { st -> st.art?.let { "${it.emoji} $label" } ?: label } ?: body
}
