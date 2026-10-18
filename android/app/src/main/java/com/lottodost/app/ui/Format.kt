package com.lottodost.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** ₹ with Indian digit grouping: 10000000 -> "₹1,00,00,000". */
fun formatRupees(amount: Long): String {
    val s = amount.toString()
    if (s.length <= 3) return "₹$s"
    val last3 = s.takeLast(3)
    val rest = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
    return "₹$rest,$last3"
}

/** Short human label used on big prize chips: 1 Crore, 10 Thousand... */
fun prizeShort(amount: Long): String = when {
    amount >= 1_00_00_000 && amount % 1_00_00_000 == 0L -> "₹${amount / 1_00_00_000} Crore"
    amount >= 1_00_000 && amount % 1_00_000 == 0L -> "₹${amount / 1_00_000} Lakh"
    else -> formatRupees(amount)
}

fun formatDate(iso: String, locale: Locale = Locale.getDefault()): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE, d MMM", locale)) }.getOrDefault(iso)

/** "13:00" -> "1:00 PM" (Indian users read 12-hour times). */
fun formatTime(hhmm: String): String {
    // Feed data is external: never crash on an unexpected value, just show it as is.
    val parts = hhmm.split(":").mapNotNull { it.toIntOrNull() }
    if (parts.size != 2 || parts[0] !in 0..23 || parts[1] !in 0..59) return hhmm
    val (h, m) = parts
    val suffix = if (h >= 12) "PM" else "AM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(Locale.ROOT, h12, m, suffix)
}

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // no browser / PDF viewer installed; nothing sensible to do
    }
}

fun shareText(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
