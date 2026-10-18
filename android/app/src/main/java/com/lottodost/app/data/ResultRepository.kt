package com.lottodost.app.data

import android.annotation.SuppressLint
import android.content.Context
import com.lottodost.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Offline-first result store. The last good feed is kept on disk so the app opens instantly
 * (and works on a train with no signal); refresh() does a conditional GET (ETag) so an
 * unchanged feed costs a few hundred bytes. Android's HttpURLConnection gzips transparently.
 */
// Holds only the application context, so the singleton cannot leak an Activity.
@SuppressLint("StaticFieldLeak")
class ResultRepository private constructor(private val context: Context) {

    data class State(
        val feed: ResultFeed? = null,
        val refreshing: Boolean = false,
        val offline: Boolean = false,
        val lastCheckedMs: Long = 0,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile = File(context.filesDir, "latest.json")
    private val prefs = context.getSharedPreferences("feed", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun loadCache() = withContext(Dispatchers.IO) {
        if (_state.value.feed != null) return@withContext
        val feed = runCatching { json.decodeFromString<ResultFeed>(cacheFile.readText()) }.getOrNull()
        _state.update { it.copy(feed = feed, lastCheckedMs = prefs.getLong("checked", 0)) }
    }

    /** Returns true when new data arrived. Never throws for network problems. */
    suspend fun refresh(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            loadCache()
            _state.update { it.copy(refreshing = true) }
            var changed = false
            var ok = false
            for (url in BuildConfig.RESULT_URLS) {
                try {
                    changed = fetch(url)
                    ok = true
                    break
                } catch (_: IOException) {
                    // try next mirror
                } catch (_: IllegalArgumentException) {
                    // malformed JSON from a mirror: try next one, keep cached data
                }
            }
            val now = System.currentTimeMillis()
            if (ok) prefs.edit().putLong("checked", now).apply()
            _state.update { it.copy(refreshing = false, offline = !ok, lastCheckedMs = if (ok) now else it.lastCheckedMs) }
            changed
        }
    }

    private fun fetch(url: String): Boolean {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            useCaches = false
            // Only revalidate when we actually hold a copy; otherwise a 304 would leave us empty.
            if (_state.value.feed != null) {
                prefs.getString("etag:$url", null)?.let { setRequestProperty("If-None-Match", it) }
            }
        }
        try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> return false
                HttpURLConnection.HTTP_OK -> Unit
                else -> throw IOException("HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.use { it.readBytes() }.decodeToString()
            val feed = json.decodeFromString<ResultFeed>(body)
            val old = _state.value.feed
            // Never replace newer data with an older (stale CDN) copy.
            if (old != null && feed.generatedAt < old.generatedAt) return false
            val tmp = File(cacheFile.path + ".tmp")
            tmp.writeText(body)
            tmp.renameTo(cacheFile)
            prefs.edit().putString("etag:$url", conn.getHeaderField("ETag")).apply()
            _state.update { it.copy(feed = feed) }
            return old?.draws?.map { it.id to it.status } != feed.draws.map { it.id to it.status }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        @Volatile private var instance: ResultRepository? = null
        fun get(context: Context): ResultRepository =
            instance ?: synchronized(this) {
                instance ?: ResultRepository(context.applicationContext).also { instance = it }
            }
    }
}
