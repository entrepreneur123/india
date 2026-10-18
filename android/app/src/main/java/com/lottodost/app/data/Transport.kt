package com.lottodost.app.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTP abstraction so the mirror/backoff logic can be tested on the JVM. */
fun interface Transport {
    @Throws(IOException::class)
    fun get(url: String, etag: String?): HttpResult
}

data class HttpResult(
    val code: Int,
    val body: String? = null,
    val etag: String? = null,
    val serverDateMs: Long? = null,
    val retryAfterS: Long? = null,
)

/**
 * HttpURLConnection: no extra dependency, gzip handled transparently on Android.
 * Short connect timeout: some Indian ISPs silently drop packets to blocked hosts
 * (measured: raw.githubusercontent.com on Jio/Airtel/ACT), so fail fast and try a mirror.
 */
object UrlConnectionTransport : Transport {
    override fun get(url: String, etag: String?): HttpResult {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 10_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            if (etag != null) setRequestProperty("If-None-Match", etag)
        }
        try {
            val code = conn.responseCode
            val body = if (code == HttpURLConnection.HTTP_OK) conn.inputStream.use { it.readBytes() }.decodeToString() else null
            return HttpResult(
                code = code,
                body = body,
                etag = conn.getHeaderField("ETag"),
                serverDateMs = conn.getHeaderFieldDate("Date", 0L).takeIf { it > 0 },
                retryAfterS = conn.getHeaderField("Retry-After")?.trim()?.toLongOrNull(),
            )
        } finally {
            conn.disconnect()
        }
    }
}
