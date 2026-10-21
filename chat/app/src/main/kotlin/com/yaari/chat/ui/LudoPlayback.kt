package com.yaari.chat.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yaari.chat.domain.Ludo
import com.yaari.chat.model.LudoEvent
import com.yaari.chat.model.LudoGame
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

/** What just happened, to announce (and, when [show], to put in the banner for a moment). */
data class LudoNews(val key: Int, val event: LudoEvent, val game: LudoGame, val show: Boolean)

/** A moment worth a haptic tick. */
enum class LudoFeel { DICE, CAPTURE }

/**
 * Plays the server's changes on screen (the web's useLudoPlay): each new version's events in order (the die
 * tumbling, a token hopping square by square, a captured one sliding home), then the new state. A jump of more than
 * one version, or a new game, is shown as it is. Plain Kotlin on Compose state: the screen offers it every game it
 * gets and runs [play]; the tests drive it with virtual time.
 */
class LudoPlayback(game: LudoGame, private val now: () -> Long = System::currentTimeMillis) {
    var view by mutableStateOf(game)
        private set
    var tokens by mutableStateOf(game.tokens)
        private set
    var motion by mutableStateOf<Map<Int, LudoMotion>>(emptyMap())
        private set
    var dice by mutableStateOf(game.roll)
        private set
    var rolling by mutableStateOf(false)
        private set
    /** Something is being played: the board isn't the server's state yet (no taps, no clock). */
    var busy by mutableStateOf(false)
        private set
    var news by mutableStateOf<LudoNews?>(null)
        private set

    /** Haptics and the like, set by the screen. */
    var feel: (LudoFeel) -> Unit = {}

    private val queue = ArrayDeque<LudoGame>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var lastId = game.id
    private var lastVersion = game.version
    private var gen = 0
    private var pressed = 0L
    private var seq = 0

    /** A game from the server (or the same one again). */
    fun offer(g: LudoGame) {
        if (g.id != lastId) {
            gen++
            queue.clear()
            lastId = g.id
            lastVersion = g.version
            busy = false
            snap(g)
            return
        }
        if (g.version <= lastVersion) return
        queue += if (g.version == lastVersion + 1) g else g.copy(events = emptyList())
        lastVersion = g.version
        wake.trySend(Unit)
    }

    /** Pressing the die starts the tumble at once; the server's number lands it. */
    fun rollPressed() {
        pressed = now()
        rolling = true
    }

    fun rollFailed() {
        rolling = false
    }

    private fun snap(g: LudoGame) {
        tokens = g.tokens
        motion = emptyMap()
        view = g
        dice = g.roll ?: dice
        rolling = false
    }

    private fun say(e: LudoEvent, g: LudoGame, show: Boolean) {
        news = LudoNews(++seq, e, g, show)
    }

    /** Plays what arrives, for as long as the screen shows the game. [reduce]: animations are off. */
    suspend fun play(reduce: () -> Boolean) {
        for (x in wake) {
            while (queue.isNotEmpty()) {
                val my = gen
                val g = queue.removeFirst()
                busy = true
                playOne(g, reduce(), my)
                if (gen != my) continue
                snap(g)
                busy = queue.isNotEmpty()
            }
        }
    }

    private suspend fun playOne(g: LudoGame, still: Boolean, my: Int) {
        val cur = tokens.toMutableList()
        for (e in g.events) {
            if (gen != my) return
            when (e) {
                is LudoEvent.Roll -> {
                    rolling = true
                    val since = if (pressed > 0) now() - pressed else 0L
                    delay(if (still) 0 else maxOf(ROLL_MIN_MS, ROLL_MS - since))
                    pressed = 0
                    if (gen != my) return
                    dice = e.d
                    rolling = false
                    feel(LudoFeel.DICE)
                    say(e, g, show = false)
                    delay(if (still) 150 else 420)
                }
                is LudoEvent.Move -> {
                    val idx = e.s * 4 + e.t
                    val steps = if (e.f < 0) listOf(0) else if (still) listOf(e.to) else (e.f + 1..e.to).toList()
                    for (p in steps) {
                        if (gen != my) return
                        cur[idx] = p
                        tokens = cur.toList()
                        motion = mapOf(idx to LudoMotion.HOP)
                        delay(if (still) 0 else if (e.f < 0) 200 else HOP_MS)
                    }
                    if (e.cap.isNotEmpty()) {
                        e.cap.forEach { (s, k, _) -> cur[s * 4 + k] = -1 }
                        tokens = cur.toList()
                        motion = e.cap.associate { (s, k, _) -> s * 4 + k to LudoMotion.SLIDE }
                        feel(LudoFeel.CAPTURE)
                    }
                    if (e.cap.isNotEmpty() || e.to == Ludo.HOME) say(e, g, show = true)
                    delay(if (e.cap.isNotEmpty() && !still) 480 else 120)
                }
                is LudoEvent.Pass, is LudoEvent.Triple6 -> {
                    say(e, g, show = e is LudoEvent.Triple6)
                    delay(if (still) 300 else 650)
                }
                is LudoEvent.Win -> say(e, g, show = false)
                else -> say(e, g, show = true)
            }
        }
    }

    companion object {
        /** A roll shows the tumble for this long from the press (at least [ROLL_MIN_MS] once the number is known). */
        const val ROLL_MS = 640L
        const val ROLL_MIN_MS = 280L
        /** One square of a move. */
        const val HOP_MS = 135L
    }
}
