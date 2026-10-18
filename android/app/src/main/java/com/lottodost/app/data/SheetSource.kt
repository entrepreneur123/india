package com.lottodost.app.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Original result sheets (PDF), fetched by the phone from the publisher only when the user opens
 * one, and kept in the app cache. Nothing is re-hosted by us.
 */
object SheetSource {
    private const val SAMBAD_OLD = "lotteryoldresultsdown.php"
    private const val SAMBAD_TODAY = "fetchtoday.php"

    /**
     * Lottery Sambad serves the current day's sheet only from fetchtoday.php and earlier days only
     * from lotteryoldresultsdown.php (checked 18-10-2026: each returns "File Not Uploaded" for the
     * other case). The feed stores the archive URL; pick the one that works for [date] today.
     */
    fun sambadUrl(url: String, date: String, todayIst: String): String =
        if (date == todayIst) url.replace(SAMBAD_OLD, SAMBAD_TODAY) else url.replace(SAMBAD_TODAY, SAMBAD_OLD)

    sealed interface Result {
        data class Ok(val file: File) : Result
        data object NotPublished : Result   // the publisher answered, but not with a PDF yet
        data object Failed : Result          // network
    }

    fun isPdf(head: ByteArray) = head.size >= 4 && head[0] == '%'.code.toByte() && head[1] == 'P'.code.toByte() &&
        head[2] == 'D'.code.toByte() && head[3] == 'F'.code.toByte()

    /** Blocking; call off the main thread. Past sheets never change, so a cached copy is reused. */
    fun load(cacheDir: File, url: String, mayChange: Boolean, userAgent: String): Result {
        val dir = File(cacheDir, "sheets").apply { mkdirs() }
        val f = File(dir, url.hashCode().toUInt().toString(16) + ".pdf")
        if (f.length() > 0 && !mayChange) return Result.Ok(f)
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 6_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", userAgent)
            try {
                if (c.responseCode != 200) return if (f.length() > 0) Result.Ok(f) else Result.NotPublished
                val bytes = c.inputStream.use { it.readBytes() }
                if (!isPdf(bytes)) return if (f.length() > 0) Result.Ok(f) else Result.NotPublished
                val tmp = File(dir, f.name + ".tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
                Result.Ok(f)
            } finally {
                c.disconnect()
            }
        } catch (_: IOException) {
            if (f.length() > 0) Result.Ok(f) else Result.Failed
        }
    }
}
