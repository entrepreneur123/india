package com.yaari.chat.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** The steps people reach, counted on the server (the same names as the web app's lib/telemetry.ts). */
enum class Funnel(val code: String) {
    SIGNUP_DONE("signup_done"), MATCH_FOUND("match_found"), CHAT_MESSAGE_SENT("chat_message_sent"),
    ROOM_CREATED("room_created"), ROOM_JOINED("room_joined"), LUDO_STARTED("ludo_started"), CALL_STARTED("call_started"),
    PLUS_PURCHASED("plus_purchased"),
}

/**
 * Crash reports and a few funnel events, sent to our own server (021_diagnostics_admin.sql: log_events signed in,
 * log_events_anon before) in the web's shape: {kind, name, message?, stack?, version, platform: "android", route?}.
 * No third-party service. Events carry only a fixed name; errors their class, message and stack (long digit runs,
 * such as phone numbers, are blanked). Batched (20 a call, the server's limit), sent a minute after the first one,
 * at once when the app goes to the background, and kept for the next try when offline. At most [MAX_PER_RUN] a run,
 * under the server's 200 a day per person. The demo build has none ([Flavor]).
 *
 * [send] posts one batch (a JSON array) and throws when it didn't arrive.
 */
class Telemetry(
    private val dir: File,
    private val version: String,
    private val scope: CoroutineScope,
    private val send: suspend (String) -> Unit,
    background: Flow<Boolean>? = null,
    private val flushDelayMs: Long = FLUSH_DELAY_MS,
) {
    private val queue = ArrayDeque<String>()
    private val seen = HashSet<String>()
    private var accepted = 0
    private var timer: Job? = null
    private val sending = Mutex()

    init {
        background?.let { visible -> scope.launch { visible.collect { if (!it) flush() } } }
    }

    /** A step reached (the first chat message of a run only: it's the first one that counts). */
    fun track(e: Funnel) {
        synchronized(this) {
            if (e == Funnel.CHAT_MESSAGE_SENT && !seen.add(e.code)) return
        }
        push(event(e.code, version))
    }

    /** Sends the crash reports saved by [CrashReports] (the app died before it could), then deletes them. */
    fun sendSaved(): Job = scope.launch {
        val files = CrashReports.saved(dir)
        if (files.isEmpty()) return@launch
        sending.withLock {
            for (chunk in files.chunked(BATCH)) {
                val items = chunk.mapNotNull { f -> runCatching { f.readText().trim() }.getOrNull()?.takeIf { it.startsWith("{") } }
                if (items.isNotEmpty() && runCatching { send(items.joinToString(",", "[", "]")) }.isFailure) return@withLock
                chunk.forEach { it.delete() }
            }
        }
    }

    private fun push(item: String) {
        val now = synchronized(this) {
            if (accepted >= MAX_PER_RUN) return
            accepted++
            queue.addLast(item)
            while (queue.size > MAX_QUEUED) queue.removeFirst()
            if (queue.size >= BATCH) true
            else {
                if (timer?.isActive != true) timer = scope.launch { delay(flushDelayMs); flush() }
                false
            }
        }
        if (now) flush()
    }

    /** Sends everything queued, 20 at a time; what fails stays queued for the next try. */
    fun flush(): Job = scope.launch {
        sending.withLock {
            while (true) {
                val batch = synchronized(this@Telemetry) { List(minOf(BATCH, queue.size)) { queue.removeFirst() } }
                if (batch.isEmpty()) return@withLock
                if (runCatching { send(batch.joinToString(",", "[", "]")) }.isFailure) {
                    synchronized(this@Telemetry) { batch.asReversed().forEach { queue.addFirst(it) } }
                    return@withLock
                }
            }
        }
    }

    /** Queued and not sent yet (tests). */
    internal val pending get() = synchronized(this) { queue.toList() }

    companion object {
        const val BATCH = 20
        const val MAX_PER_RUN = 150
        const val MAX_QUEUED = 100
        const val FLUSH_DELAY_MS = 60_000L

        fun event(name: String, version: String) = json("event", name, null, null, version, null)
    }
}

/**
 * The uncaught-exception handler: writes a compact report to disk (the app is going down, so nothing is sent now)
 * and hands the crash on to the handler that was there before (Android's, which shows the dialog and ends the
 * process). [Telemetry.sendSaved] sends it on the next launch.
 */
object CrashReports {
    /** Kept on disk at most (a crash loop doesn't fill the phone). */
    const val MAX_SAVED = 5

    fun install(dir: File, version: String) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(dir, version, previous))
    }

    class Handler(private val dir: File, private val version: String, private val previous: Thread.UncaughtExceptionHandler?) :
        Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            runCatching { save(dir, report(e, t.name, version)) }
            previous?.uncaughtException(t, e)
        }
    }

    /** One report: the exception's class (and its root cause's), message and stack, cut to the server's sizes. */
    fun report(e: Throwable, thread: String, version: String): String {
        var root = e
        while (root.cause != null && root.cause !== root) root = root.cause!!
        val name = "crash:" + (e::class.java.simpleName.ifEmpty { e::class.java.name }) +
            (if (root !== e) "/" + root::class.java.simpleName else "")
        val message = scrub(listOfNotNull(e.message, root.message.takeIf { root !== e }).joinToString(" / ")).take(2_000)
        val stack = scrub(e.stackTraceToString()).take(4_000)
        return json("error", name.take(100), message.ifEmpty { null }, stack, version, thread.take(200))
    }

    fun save(dir: File, report: String) {
        dir.mkdirs()
        // Named so that sorting by name is oldest first.
        File(dir, String.format(java.util.Locale.ROOT, "crash-%013d-%04d.json", System.currentTimeMillis(), saves.getAndIncrement() % 10_000)).writeText(report)
        saved(dir).dropLast(MAX_SAVED).forEach { it.delete() }
    }

    /** Saved reports, oldest first. */
    fun saved(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && f.name.startsWith("crash-") && f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }

    private val DIGITS = Regex("\\d{7,}")
    private val saves = java.util.concurrent.atomic.AtomicInteger()

    /** Phone numbers and other long digit runs never leave the phone. */
    fun scrub(text: String) = DIGITS.replace(text, "#")
}

private fun json(kind: String, name: String, message: String?, stack: String?, version: String, route: String?) = buildString {
    append("{\"kind\":").append(quote(kind)).append(",\"name\":").append(quote(name))
    message?.let { append(",\"message\":").append(quote(it)) }
    stack?.let { append(",\"stack\":").append(quote(it)) }
    append(",\"version\":").append(quote(version)).append(",\"platform\":\"android\"")
    route?.let { append(",\"route\":").append(quote(it)) }
    append('}')
}

private fun quote(s: String) = buildString(s.length + 2) {
    append('"')
    for (c in s) when (c) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (c < ' ') append(String.format(java.util.Locale.ROOT, "\\u%04x", c.code)) else append(c)
    }
    append('"')
}
