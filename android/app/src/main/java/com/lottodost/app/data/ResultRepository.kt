package com.lottodost.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.lottodost.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlin.random.Random

/**
 * Offline-first result store over static JSON on several mirrors.
 *
 *  - The last good copy of every file is kept on disk, so the app opens instantly and works offline.
 *  - Conditional GETs (ETag): an unchanged feed costs ~250 bytes.
 *  - Mirrors are tried in order, starting with the last one that worked (sticky), with a short
 *    connect timeout; the list itself can be replaced remotely through config.json.
 *  - 429/503 from every mirror => back off (honouring Retry-After, with jitter) instead of hammering.
 *  - Refreshes are throttled; a draw is only replaced by data from a newer feed.
 */
@SuppressLint("StaticFieldLeak") // holds the application context only
class ResultRepository internal constructor(
    private val dir: File,
    private val prefs: SharedPreferences,
    private val transport: Transport,
    private val defaultMirrors: List<String>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    enum class Mode { LIGHT, FULL }

    data class State(
        val draws: Map<String, Draw> = emptyMap(),
        val index: IndexFeed? = null,
        val config: RemoteConfig? = null,
        val refreshing: Boolean = false,
        val offline: Boolean = false,
        val lastCheckedMs: Long = 0,
        val feedGeneratedAt: String? = null,
        val clockOffsetMs: Long = 0,
        val loadingDay: String? = null,
    ) {
        val drawList: List<Draw>
            get() = draws.values.sortedWith(compareByDescending<Draw> { it.date }.thenByDescending { it.time })
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val generatedAtById = HashMap<String, String>()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private fun file(path: String) = File(dir, path)

    private inline fun <reified T> readCache(path: String): T? =
        runCatching { json.decodeFromString<T>(file(path).readText()) }.getOrNull()

    private fun writeCache(path: String, body: String) {
        val f = file(path)
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeText(body)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    /** Adds draws from a feed; a draw is only replaced by one from a feed at least as new. */
    private fun merge(draws: List<Draw>, generatedAt: String, into: MutableMap<String, Draw>) {
        for (d in draws) {
            val prev = generatedAtById[d.id]
            if (prev == null || generatedAt >= prev) {
                into[d.id] = d
                generatedAtById[d.id] = generatedAt
            }
        }
    }

    suspend fun loadCache() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_state.value.draws.isNotEmpty() || _state.value.lastCheckedMs > 0) return@withLock
            val map = HashMap<String, Draw>()
            var newest: String? = null
            for (path in listOf(LATEST, TODAY)) {
                readCache<ResultFeed>(path)?.let { f ->
                    merge(f.draws, f.generatedAt, map)
                    if (newest == null || f.generatedAt > newest!!) newest = f.generatedAt
                }
            }
            _state.update {
                it.copy(draws = map, index = readCache(INDEX), config = readCache(CONFIG),
                    lastCheckedMs = prefs.getLong(K_CHECKED, 0), feedGeneratedAt = newest,
                    clockOffsetMs = prefs.getLong(K_OFFSET, 0))
            }
        }
    }

    private fun mirrors(): List<String> {
        val all = (_state.value.config?.mirrors.orEmpty() + defaultMirrors).map { it.trimEnd('/') + "/" }.distinct()
        val preferred = prefs.getString(K_MIRROR, null)
        return if (preferred != null && preferred in all) listOf(preferred) + (all - preferred) else all
    }

    private sealed interface Fetch {
        data class Body(val text: String) : Fetch
        data object NotModified : Fetch
        data object Missing : Fetch          // 404 on every mirror (e.g. a day without a file)
        data class Failed(val retryAfterS: Long?) : Fetch
    }

    /**
     * Conditional GET of [path] across mirrors. Never throws for network problems.
     * A 200 whose body fails [valid] (captive-portal HTML, truncated JSON) counts as a broken
     * mirror: the next one is tried and the bad one is not remembered.
     */
    private fun fetch(path: String, cached: Boolean, valid: (String) -> Boolean): Fetch {
        var throttledFor: Long? = null
        var missing = 0
        val list = mirrors()
        for (base in list) {
            val etagKey = "etag:$base$path"
            // Only revalidate when we hold a copy; otherwise a 304 would leave us empty.
            val etag = if (cached) prefs.getString(etagKey, null) else null
            val r = try {
                transport.get(base + path, etag)
            } catch (_: IOException) {
                continue
            }
            r.serverDateMs?.let { prefs.edit().putLong(K_OFFSET, it - clock()).apply() }
            when (r.code) {
                200 -> {
                    val body = r.body ?: continue
                    if (!valid(body)) continue
                    prefs.edit().putString(K_MIRROR, base).putString(etagKey, r.etag).apply()
                    return Fetch.Body(body)
                }
                304 -> {
                    prefs.edit().putString(K_MIRROR, base).apply()
                    return Fetch.NotModified
                }
                404 -> missing++
                429, 503 -> throttledFor = maxOf(throttledFor ?: 0, r.retryAfterS ?: 60)
            }
        }
        return if (missing == list.size) Fetch.Missing else Fetch.Failed(throttledFor)
    }

    /**
     * Refresh from the network. LIGHT fetches only today.json (cheap, used for polling);
     * FULL also revalidates latest/index/config when they are older than their max age.
     * [force] skips the 15 s throttle (not the server-requested backoff).
     * Returns true when new draw data arrived.
     */
    suspend fun refresh(mode: Mode = Mode.FULL, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        loadCache()
        mutex.withLock {
            val now = clock()
            if (now < prefs.getLong(K_BACKOFF, 0)) return@withLock false
            if (!force && now - prefs.getLong(K_CHECKED, 0) < MIN_INTERVAL_MS) return@withLock false
            _state.update { it.copy(refreshing = true) }
            var changed = false
            var throttle: Long? = null

            fun feed(path: String): Boolean {
                var decoded: ResultFeed? = null
                val valid = { body: String ->
                    decoded = try { json.decodeFromString<ResultFeed>(body) } catch (_: SerializationException) { null }
                    decoded != null
                }
                // Revalidate (ETag) only if our copy is readable; a corrupt cache must be re-downloaded.
                when (val f = fetch(path, readCache<ResultFeed>(path) != null, valid)) {
                    is Fetch.Body -> {
                        val parsed = decoded!!
                        writeCache(path, f.text)
                        val map = HashMap(_state.value.draws)
                        val before = map.mapValues { it.value.status to it.value.tiers.size }
                        merge(parsed.draws, parsed.generatedAt, map)
                        if (map.mapValues { it.value.status to it.value.tiers.size } != before) changed = true
                        _state.update { s ->
                            s.copy(draws = map, feedGeneratedAt = listOfNotNull(s.feedGeneratedAt, parsed.generatedAt).max())
                        }
                        return true
                    }
                    Fetch.NotModified -> return true
                    Fetch.Missing -> return false
                    is Fetch.Failed -> {
                        if (f.retryAfterS != null) throttle = f.retryAfterS
                        return false
                    }
                }
            }

            val ok = feed(TODAY)
            if (ok && mode == Mode.FULL) {
                if (stale(K_LATEST_AT, LATEST_MAX_AGE_MS, now) && feed(LATEST)) prefs.edit().putLong(K_LATEST_AT, now).apply()
                if (stale(K_INDEX_AT, INDEX_MAX_AGE_MS, now)) {
                    (fetch(INDEX, readCache<IndexFeed>(INDEX) != null, ::isIndex) as? Fetch.Body)?.let { b ->
                        runCatching { json.decodeFromString<IndexFeed>(b.text) }.getOrNull()?.let { idx ->
                            writeCache(INDEX, b.text)
                            _state.update { it.copy(index = idx) }
                        }
                    }
                    prefs.edit().putLong(K_INDEX_AT, now).apply()
                }
                if (stale(K_CONFIG_AT, CONFIG_MAX_AGE_MS, now)) {
                    (fetch(CONFIG, readCache<RemoteConfig>(CONFIG) != null, ::isConfig) as? Fetch.Body)?.let { b ->
                        runCatching { json.decodeFromString<RemoteConfig>(b.text) }.getOrNull()?.let { cfg ->
                            writeCache(CONFIG, b.text)
                            _state.update { it.copy(config = cfg) }
                        }
                    }
                    prefs.edit().putLong(K_CONFIG_AT, now).apply()
                }
            }
            val edit = prefs.edit()
            if (ok) edit.putLong(K_CHECKED, now)
            val wait = throttle
            if (!ok && wait != null) {
                // Every mirror is overloaded: wait as asked (30 s .. 15 min) plus up to 50 % jitter.
                val ms = wait.coerceIn(30, 900) * 1000
                edit.putLong(K_BACKOFF, now + ms + random.nextLong(0, ms / 2 + 1))
            }
            edit.apply()
            _state.update {
                it.copy(refreshing = false, offline = !ok, lastCheckedMs = if (ok) now else it.lastCheckedMs,
                    clockOffsetMs = prefs.getLong(K_OFFSET, 0))
            }
            changed
        }
    }

    private fun isIndex(body: String) = runCatching { json.decodeFromString<IndexFeed>(body) }.isSuccess
    private fun isConfig(body: String) = runCatching { json.decodeFromString<RemoteConfig>(body) }.isSuccess

    private fun stale(key: String, maxAge: Long, now: Long) = now - prefs.getLong(key, 0) > maxAge

    /** Loads one day of history (cache first; days before yesterday never change once complete). */
    suspend fun loadDay(date: String, today: String) = withContext(Dispatchers.IO) {
        val path = "days/$date.json"
        val cached = readCache<DayFeed>(path)
        if (cached != null) mergeDay(cached)
        val mayChange = date >= LocalDate.parse(today).minusDays(1).toString()
        if (cached != null && !mayChange) return@withContext
        _state.update { it.copy(loadingDay = date) }
        val f = fetch(path, cached != null) { runCatching { json.decodeFromString<DayFeed>(it) }.isSuccess }
        if (f is Fetch.Body) {
            runCatching { json.decodeFromString<DayFeed>(f.text) }.getOrNull()?.let {
                writeCache(path, f.text)
                mergeDay(it)
            }
        }
        _state.update { it.copy(loadingDay = null) }
    }

    private suspend fun mergeDay(day: DayFeed) = mutex.withLock {
        val map = HashMap(_state.value.draws)
        // Day files carry no generatedAt; they only fill gaps, never override feed data.
        for (d in day.draws) if (d.id !in map) { map[d.id] = d; generatedAtById[d.id] = "" }
        _state.update { it.copy(draws = map) }
    }

    companion object {
        const val TODAY = "today.json"
        const val LATEST = "latest.json"
        const val INDEX = "index.json"
        const val CONFIG = "config.json"
        private const val K_CHECKED = "checked"
        private const val K_OFFSET = "clockOffset"
        private const val K_MIRROR = "mirror"
        private const val K_BACKOFF = "backoffUntil"
        private const val K_LATEST_AT = "latestAt"
        private const val K_INDEX_AT = "indexAt"
        private const val K_CONFIG_AT = "configAt"
        const val MIN_INTERVAL_MS = 15_000L
        private const val LATEST_MAX_AGE_MS = 6 * 3600_000L
        private const val INDEX_MAX_AGE_MS = 3600_000L
        private const val CONFIG_MAX_AGE_MS = 6 * 3600_000L

        @Volatile private var instance: ResultRepository? = null
        fun get(context: Context): ResultRepository =
            instance ?: synchronized(this) {
                instance ?: ResultRepository(
                    dir = File(context.applicationContext.filesDir, "feed"),
                    prefs = context.applicationContext.getSharedPreferences("feed", Context.MODE_PRIVATE),
                    transport = UrlConnectionTransport,
                    defaultMirrors = BuildConfig.RESULT_MIRRORS.toList(),
                ).also { instance = it }
            }
    }
}
