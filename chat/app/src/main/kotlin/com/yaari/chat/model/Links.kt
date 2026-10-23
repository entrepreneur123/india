package com.yaari.chat.model

import java.net.URI

/** A web page's card in the chat (edge function link-meta, 036). Any part may be missing. */
/** [frameable]: the page lets other sites show it in a frame (link-meta), so a room can open it together (the web shows it beside the chat). */
data class LinkPreview(
    val url: String, val title: String?, val description: String?, val image: String?, val site: String?, val frameable: Boolean = false,
)

/** A link found in a message: where it is in the text and the address it opens. */
data class FoundLink(val start: Int, val end: Int, val url: String)

/** Links in chat messages: only http(s) addresses are links; anything else stays plain text. */
object Links {
    /** Longest address the server shares or previews (voice_share_link, link-meta). */
    const val MAX_URL = 500

    private val CANDIDATE = Regex("""(?i)\bhttps?://[^\s<>"'`]+""")
    /** Left off the end of an address: sentence punctuation that is almost never part of it. */
    private const val TRAILING = ".,!?;:'\")]}*_"

    /** The http(s) links in [text], in order (punctuation after an address is not part of it). */
    fun find(text: String): List<FoundLink> {
        if (!text.contains("http", ignoreCase = true)) return emptyList()
        return CANDIDATE.findAll(text).mapNotNull { m ->
            var end = m.range.last + 1
            while (end > m.range.first && text[end - 1] in TRAILING) {
                // A closing bracket stays when the address opened one itself (Wikipedia's "Foo_(bar)").
                val c = text[end - 1]
                val open = when (c) { ')' -> '('; ']' -> '['; '}' -> '{'; else -> null }
                if (open != null) {
                    val part = text.substring(m.range.first, end)
                    if (part.count { it == open } >= part.count { it == c }) break
                }
                end--
            }
            val url = text.substring(m.range.first, end)
            if (isWeb(url)) FoundLink(m.range.first, end, url) else null
        }.toList()
    }

    /** An http(s) address with a host: the only kind the in-app viewer loads. */
    fun isWeb(url: String): Boolean {
        if (url.length > 2_000 || url.any { it.isWhitespace() || it.code < 0x20 }) return false
        val u = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = u.scheme?.lowercase() ?: return false
        return (scheme == "http" || scheme == "https") && !u.host.isNullOrEmpty() && u.rawUserInfo == null
    }

    /** What the server accepts as a shared page (voice_share_link): https, a dotted host, up to 500 characters. */
    fun shareable(url: String): Boolean {
        if (url.length > MAX_URL || !isWeb(url)) return false
        val u = URI(url)
        return u.scheme.equals("https", ignoreCase = true) && u.host.contains('.') && u.port == -1
    }

    /** The host to show for [url] ("example.com"), or the address itself. */
    fun host(url: String): String = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull()?.ifEmpty { null } ?: url

    /** The first link worth a card: not a YouTube video (those keep their own card), or null. */
    fun firstPreviewable(text: String): String? = find(text).firstOrNull { YouTube.videoId(it.url) == null }?.url
}
