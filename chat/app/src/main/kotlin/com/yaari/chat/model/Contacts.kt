package com.yaari.chat.model

// ---- Messenger contacts friends exchange (server: 044_friend_contacts.sql) -----------------------------------------
// I keep mine once and choose each friend who sees them; unfriending or blocking ends that both ways. Same shapes as
// the web (chat/web/lib/contacts.ts).

/** The apps, in the editor's order. [brand]: the name everywhere (never translated); null: the phone, a string resource. */
enum class ContactApp(val code: String, val brand: String?, val number: Boolean = false) {
    WHATSAPP("whatsapp", "WhatsApp", number = true), PHONE("phone", null, number = true), TELEGRAM("telegram", "Telegram"),
    INSTAGRAM("instagram", "Instagram"), LINE("line", "LINE"), KAKAOTALK("kakaotalk", "KakaoTalk"), WECHAT("wechat", "WeChat"),
    SNAPCHAT("snapchat", "Snapchat"), DISCORD("discord", "Discord");

    /** Like contacts_clean: what the server keeps ("" for nothing), or null when it refuses it (contact_invalid). */
    fun clean(raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty()) return ""
        if (number) return v.replace(NUMBER_JUNK, "").takeIf { NUMBER.matches(it) }
        return v.trimStart('@').takeIf { HANDLE.matches(it) }
    }

    /**
     * Where tapping a friend's contact [v] (as the server keeps it: URL-safe) goes; null: no public link (KakaoTalk,
     * WeChat, Discord), so it is copied.
     */
    fun link(v: String): String? = when (this) {
        PHONE -> "tel:$v"
        WHATSAPP -> "https://wa.me/${v.removePrefix("+")}"
        TELEGRAM -> "https://t.me/$v"
        INSTAGRAM -> "https://instagram.com/$v"
        LINE -> "https://line.me/ti/p/~$v"
        SNAPCHAT -> "https://snapchat.com/add/$v"
        KAKAOTALK, WECHAT, DISCORD -> null
    }

    companion object {
        private val NUMBER_JUNK = Regex("""[\s().-]""")
        private val NUMBER = Regex("""\+?[0-9]{6,16}""")
        private val HANDLE = Regex("""[A-Za-z0-9._-]{1,40}""")

        fun of(code: String?) = entries.firstOrNull { it.code == code }

        /** Known apps with values only, in order. */
        fun of(raw: Map<String, String?>?): Map<ContactApp, String> =
            entries.mapNotNull { a -> raw?.get(a.code)?.takeIf { it.isNotEmpty() }?.let { a to it } }.toMap()
    }
}

/** What I keep, and the friends I show it to. */
data class MyContacts(val contacts: Map<ContactApp, String> = emptyMap(), val sharedWith: Set<String> = emptySet())

/** A friend's side: their contacts when they show them to me (null: they don't), and whether I show mine. */
data class FriendContacts(val contacts: Map<ContactApp, String>?, val mineShared: Boolean)
