package com.yaari.chat.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.yaari.chat.R
import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.TranslatePrefs
import com.yaari.chat.domain.ForeignText
import com.yaari.chat.domain.TranslateKind
import com.yaari.chat.domain.TranslateTargets
import com.yaari.chat.domain.Translation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Locale

/** How one chat line shows: its text (translated or not) and what its menu offers. */
data class LineView(val text: String, val state: State, val canTranslate: Boolean) {
    enum class State { ORIGINAL, LOADING, TRANSLATED }

    val translated get() = state == State.TRANSLATED
}

/**
 * Chat translation for random chats, friend messages and room chat (edge function translate). Lives in the
 * ViewModel, so results survive rotation and screens coming and going: each (kind, id, target) is asked once, at most
 * [MAX_PARALLEL] at a time, and remembered (the newest [MAX_CACHED]). A line is translated when I ask from its menu,
 * or by itself when [auto] is on and it looks written in another language ([ForeignText]); my own lines never are.
 * Requests of a chat I left are cancelled ([retain]). A 503 (no translation on the server yet) switches it all off
 * for this run and says so once.
 */
@Stable
class Translator(
    private val backend: Backend,
    private val scope: CoroutineScope,
    private val notice: (UiText) -> Unit,
    private val prefs: TranslatePrefs = TranslatePrefs.Memory(),
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private data class Key(val kind: TranslateKind, val id: Long, val target: String)

    private sealed interface Result {
        data object Loading : Result
        data class Done(val t: Translation) : Result
        data class Failed(val code: String) : Result
    }

    /** Translate incoming lines in another language by themselves (Settings). */
    var auto by mutableStateOf(prefs.auto)
        private set

    /** The language chosen in Settings; null follows the app's language ([appTarget]). */
    var chosen by mutableStateOf(prefs.target?.takeIf(TranslateTargets::isValid))
        private set

    /** The app's language as a target (set from the screen's locale, so it follows a language change). */
    var appTarget by mutableStateOf("en")
        private set

    /** False once the server said translation isn't switched on (503): no menu item, no auto, no setting. */
    var available by mutableStateOf(true)
        private set

    val target: String get() = chosen ?: appTarget

    private val results = mutableStateMapOf<Key, Result>()
    /** Done results, oldest first (the cache's eviction order). */
    private val done = ArrayDeque<Key>()
    /** Per line: true = I asked for the translation, false = I asked for the original. Absent: [auto] decides. */
    private val choice = mutableStateMapOf<Pair<TranslateKind, Long>, Boolean>()
    private val jobs = HashMap<Key, Pair<Job, String>>()
    private val gate = Semaphore(MAX_PARALLEL)
    /** After a slow_down, auto translation rests until then. */
    private var restUntil = Long.MIN_VALUE
    private var toldUnavailable = false

    fun switchAuto(on: Boolean) {
        auto = on
        prefs.auto = on
    }

    /** [code] = null: follow the app's language. */
    fun choose(code: String?) {
        chosen = code?.takeIf(TranslateTargets::isValid)
        prefs.target = chosen
    }

    fun followLocale(locale: Locale?) {
        val t = TranslateTargets.fromLocale(locale)
        if (t != appTarget) appTarget = t
    }

    /** A line of someone else's that was saved on the server and has words in it. */
    private fun eligible(id: Long, text: String?, fromMe: Boolean) = !fromMe && id >= 0 && text != null && ForeignText.hasWords(text)

    private fun wantsAuto(text: String) = available && auto && ForeignText.looksForeign(text, target)

    /** What the line shows now (reads Compose state: the line recomposes when its translation arrives). */
    fun view(kind: TranslateKind, id: Long, text: String, fromMe: Boolean): LineView {
        if (!eligible(id, text, fromMe)) return LineView(text, LineView.State.ORIGINAL, canTranslate = false)
        val r = results[Key(kind, id, target)]
        val wanted = choice[kind to id] ?: wantsAuto(text)
        val same = r is Result.Done && r.t.same
        return when {
            wanted && r is Result.Done && !r.t.same -> LineView(r.t.text, LineView.State.TRANSLATED, canTranslate = false)
            wanted && r == Result.Loading && choice[kind to id] == true -> LineView(text, LineView.State.LOADING, canTranslate = false)
            else -> LineView(text, LineView.State.ORIGINAL, canTranslate = available && !same && r != Result.Loading)
        }
    }

    /** From the line's effect: translates it by itself when that applies. Safe to call again (asks the server once). */
    fun autoTranslate(kind: TranslateKind, id: Long, text: String, fromMe: Boolean, chat: String) {
        if (!eligible(id, text, fromMe) || choice[kind to id] == false || !wantsAuto(text)) return
        if (clock() < restUntil) return
        request(Key(kind, id, target), chat, manual = false)
    }

    /** "Translate" from the line's menu. [chat]: the conversation it is in (match or room id). */
    fun translate(kind: TranslateKind, id: Long, chat: String) {
        if (!available || id < 0) return
        choice[kind to id] = true
        request(Key(kind, id, target), chat, manual = true)
    }

    /** "Show original": stays original (auto leaves it alone) until translated again from the menu. */
    fun showOriginal(kind: TranslateKind, id: Long) {
        choice[kind to id] = false
    }

    /** Only these conversations are open: requests for the others are cancelled (asked again if they come back). */
    fun retain(open: Set<String>) {
        jobs.filterValues { (_, chat) -> chat !in open }.forEach { (key, value) ->
            value.first.cancel()
            jobs.remove(key)
            if (results[key] == Result.Loading) results.remove(key)
        }
    }

    private fun request(key: Key, chat: String, manual: Boolean) {
        when (results[key]) {
            Result.Loading, is Result.Done -> return
            is Result.Failed -> if (!manual) return // auto never retries; the menu does
            null -> Unit
        }
        results[key] = Result.Loading
        val job = scope.launch {
            try {
                val t = gate.withPermit { backend.translate(key.kind, key.id, key.target) }
                store(key, Result.Done(t))
            } catch (e: CancellationException) {
                if (results[key] == Result.Loading) results.remove(key)
                throw e
            } catch (e: Exception) {
                failed(key, (e as? BackendException)?.code ?: "translate_failed", manual)
            } finally {
                if (jobs[key]?.first === coroutineContext[Job]) jobs.remove(key)
            }
        }
        if (job.isActive) jobs[key] = job to chat
    }

    private fun store(key: Key, r: Result.Done) {
        results[key] = r
        done.remove(key)
        done.addLast(key)
        while (done.size > MAX_CACHED) results.remove(done.removeFirst())
    }

    private fun failed(key: Key, code: String, manual: Boolean) {
        if (code == "translate_unavailable") {
            switchOff()
            if (!toldUnavailable || manual) notice(UiText(R.string.translate_unavailable))
            toldUnavailable = true
            return
        }
        results[key] = Result.Failed(code)
        if (code == "slow_down") restUntil = clock() + REST_MS
        if (manual) {
            choice.remove(key.kind to key.id) // back to the original; the menu can try again
            notice(
                UiText(
                    when (code) {
                        "slow_down" -> R.string.err_slow_down
                        "cannot_translate" -> R.string.translate_cannot
                        "not_found" -> R.string.err_not_found
                        "network" -> R.string.err_network
                        else -> R.string.translate_failed
                    },
                ),
            )
        }
    }

    private fun switchOff() {
        available = false
        jobs.values.forEach { it.first.cancel() }
        jobs.clear()
        results.keys.filter { results[it] == Result.Loading }.forEach(results::remove)
    }

    companion object {
        const val MAX_PARALLEL = 4
        const val MAX_CACHED = 500
        const val REST_MS = 30_000L
    }
}

/** The app's translator for chat lines; null in screenshot tests and previews (lines show as they are). */
val LocalTranslator = staticCompositionLocalOf<Translator?> { null }

/** A line's translation and its menu actions; null when it can't have one (mine, no words, no translator). */
class LineTranslation(val view: LineView, val translate: () -> Unit, val showOriginal: () -> Unit)

/**
 * The translation of one incoming line of [chat] (match or room id). Starts the automatic translation from an
 * effect, once per line and target: recomposition, scrolling back or rotating never asks the server again.
 */
@Composable
fun lineTranslation(kind: TranslateKind, id: Long, text: String?, fromMe: Boolean, chat: String): LineTranslation? {
    val tr = LocalTranslator.current ?: return null
    if (text == null || fromMe || id < 0 || !ForeignText.hasWords(text)) return null
    val target = tr.target
    LaunchedEffect(tr, kind, id, target, tr.auto, tr.available) { tr.autoTranslate(kind, id, text, fromMe = false, chat) }
    return LineTranslation(tr.view(kind, id, text, fromMe = false), { tr.translate(kind, id, chat) }, { tr.showOriginal(kind, id) })
}
