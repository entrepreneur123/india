package com.lottodost.app.domain

import java.net.URI

/**
 * Remote config and feeds come from mirrors we do not fully control (CDNs). Whatever they say,
 * the app only fetches results from our own hosts and only opens links to the lottery's and our
 * own sites, so a tampered config cannot point people at a phishing page or a UPI "fee".
 */
object Trust {
    private val MIRROR_HOSTS = setOf(
        "lottodost.com", "www.lottodost.com", "entrepreneur123.github.io", "lottodost.pages.dev", "cdn.jsdelivr.net", "raw.githubusercontent.com",
    )
    private val LINK_HOSTS = MIRROR_HOSTS + setOf(
        "nagalandlotteries.com", "lotterysambad.com", "play.google.com", "youtube.com", "youtu.be",
    )
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    private val ID = Regex("""[A-Za-z0-9_-]{1,64}""")

    private fun uri(url: String): URI? = runCatching { URI(url.trim()) }.getOrNull()?.takeIf { it.host != null }

    /** http(s) only: never intent:, upi:, file:, javascript: and the like. */
    fun isWebLink(url: String): Boolean = uri(url)?.scheme?.lowercase() in setOf("http", "https")

    private fun httpsHost(url: String): String? =
        uri(url)?.takeIf { it.scheme.equals("https", ignoreCase = true) }?.host?.lowercase()

    fun isMirror(url: String): Boolean = httpsHost(url) in MIRROR_HOSTS

    fun isTrustedLink(url: String): Boolean =
        httpsHost(url)?.let { h -> LINK_HOSTS.any { h == it || h.endsWith(".$it") } } == true

    fun isDate(s: String) = DATE.matches(s)
    fun isId(s: String) = ID.matches(s)
}
