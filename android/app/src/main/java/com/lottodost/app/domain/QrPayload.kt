package com.lottodost.app.domain

import java.net.URI

/**
 * What a scanned QR/barcode contains. Dear tickets' codes are not documented; the QR printed on
 * the Lottery Sambad sheet (18-10-2026) is a WhatsApp channel link. So: use it as a ticket only
 * when it holds a ticket number, and never open a link without showing where it goes (QR codes
 * are a common vehicle for "claim your prize" fraud).
 */
sealed interface QrPayload {
    data class TicketText(val text: String) : QrPayload
    data class Link(val url: String, val host: String) : QrPayload
    data object Other : QrPayload

    companion object {
        fun classify(raw: String): QrPayload {
            val text = raw.trim()
            if (looksLikeUrl(text)) {
                val host = runCatching { URI(text).host }.getOrNull()?.lowercase()?.removePrefix("www.")
                return if (host.isNullOrBlank()) Other else Link(text, host)
            }
            return if (TicketParser.fromOcr(text).tickets.isNotEmpty()) TicketText(text) else Other
        }

        private fun looksLikeUrl(s: String) = s.startsWith("http://", true) || s.startsWith("https://", true)
    }
}
