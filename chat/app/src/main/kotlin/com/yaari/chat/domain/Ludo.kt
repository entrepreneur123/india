package com.yaari.chat.domain

import com.yaari.chat.model.LudoEvent
import com.yaari.chat.model.LudoGame
import com.yaari.chat.model.LudoOut
import com.yaari.chat.model.LudoStatus

/**
 * Ludo's rules and board geometry, the same as the server's (chat/server/migrations/018_ludo.sql) and the web's
 * lib/ludo.ts: the screens use them for legal-token hints and the hop-by-hop paths, the demo to play whole games.
 */
object Ludo {
    const val HOME = 56
    const val TURN_MS = 15_000L
    /** The board is a 15 x 15 grid; a cell is 1 unit. */
    const val GRID = 15f

    /** The shared track square of a token at position [p] of [seat], or null in the base, home column or home. */
    fun square(seat: Int, p: Int): Int? = if (p in 0..50) (seat * 13 + p) % 52 else null

    /** The 4 start squares and the 4 stars 8 squares on. */
    fun isSafe(sq: Int) = sq % 13 == 0 || sq % 13 == 8

    // ---- geometry: red's base bottom-left, play goes clockwise ----

    /** A point in board units (x across, y down). */
    data class Pt(val x: Float, val y: Float)

    /** The 52 track cells (column, row), from red's start square. */
    val TRACK: List<Pt> = buildList {
        fun run(c: Int, r: Int, dc: Int, dr: Int, n: Int) = repeat(n) { add(Pt((c + dc * it).toFloat(), (r + dr * it).toFloat())) }
        run(6, 13, 0, -1, 5)
        run(5, 8, -1, 0, 6)
        run(0, 7, 0, 0, 1)
        run(0, 6, 1, 0, 6)
        run(6, 5, 0, -1, 6)
        run(7, 0, 0, 0, 1)
        run(8, 0, 0, 1, 6)
        run(9, 6, 1, 0, 6)
        run(14, 7, 0, 0, 1)
        run(14, 8, -1, 0, 6)
        run(8, 9, 0, 1, 6)
        run(7, 14, 0, 0, 1)
        run(6, 14, 0, 0, 1)
    }

    /** The start squares (track indices) of the four colours. */
    val STARTS = setOf(0, 13, 26, 39)

    /** A quarter turn clockwise around the board's centre, [times] times. */
    fun turn(p: Pt, times: Int): Pt {
        var q = p
        repeat(Math.floorMod(times, 4)) { q = Pt(GRID - q.y, q.x) }
        return q
    }

    private fun centre(c: Pt) = Pt(c.x + 0.5f, c.y + 0.5f)

    /** The top-left corners of a colour's home column cells (red: rows 13 to 9 of the middle column), turned. */
    fun homeColumn(seat: Int): List<Pt> = (0..4).map { i ->
        val c = turn(centre(Pt(7f, 13f - i)), seat)
        Pt(c.x - 0.5f, c.y - 0.5f)
    }

    /** Where token k waits in the base: four wells in red's corner, turned for the others. */
    val BASE_WELLS = listOf(Pt(2f, 11f), Pt(4f, 11f), Pt(2f, 13f), Pt(4f, 13f))

    /** The centre of a token's spot, in board units. */
    fun spot(seat: Int, p: Int, k: Int): Pt = when {
        p < 0 -> turn(BASE_WELLS[k], seat)
        p <= 50 -> centre(TRACK[(seat * 13 + p) % 52])
        p < HOME -> centre(homeColumn(seat)[p - 51])
        // Home: the colour's triangle in the centre, the tokens side by side.
        else -> turn(Pt(6.9f + (k % 2) * 1.2f, 8.15f + (k / 2) * 0.55f), seat)
    }

    /** Every spot a move passes through, the last one where it lands (hop by hop). */
    fun path(seat: Int, k: Int, from: Int, to: Int): List<Pt> =
        if (from < 0) listOf(spot(seat, 0, k)) else (from + 1..to).map { spot(seat, it, k) }

    /**
     * Where each token is drawn and how big (1 = a full pawn): tokens sharing a square stand side by side, smaller;
     * tokens home sit in their triangle. [positions]: the 16 positions shown.
     */
    fun layout(positions: List<Int>): List<Placed> {
        val keys = positions.mapIndexed { idx, p ->
            val at = spot(idx / 4, p, idx % 4)
            if (p == HOME) "h$idx" else "%.2f,%.2f".format(java.util.Locale.ROOT, at.x, at.y)
        }
        val groups = keys.indices.groupBy { keys[it] }
        return positions.mapIndexed { idx, p ->
            val at = spot(idx / 4, p, idx % 4)
            if (p == HOME) return@mapIndexed Placed(at.x, at.y, 0.62f)
            val g = groups.getValue(keys[idx])
            if (g.size == 1) return@mapIndexed Placed(at.x, at.y, 1f)
            val i = g.indexOf(idx)
            val (dx, dy) = if (g.size == 2) listOf(-0.2f to 0f, 0.2f to 0f)[i]
            else listOf(-0.2f to -0.2f, 0.2f to -0.2f, -0.2f to 0.2f, 0.2f to 0.2f)[i % 4]
            Placed(at.x + dx, at.y + dy, 0.7f)
        }
    }

    data class Placed(val x: Float, val y: Float, val scale: Float)

    // ---- rules ----

    /** Tokens (0-3) the player to move may move with die [d]. */
    fun legal(tokens: List<Int>, turn: Int?, d: Int): List<Int> {
        if (turn == null) return emptyList()
        return (0..3).filter { k ->
            val p = tokens[turn * 4 + k]
            (p == -1 && d == 6) || (p >= 0 && p + d <= HOME)
        }
    }

    fun legal(g: LudoGame, d: Int) = legal(g.tokens, g.turn, d)

    /** Legal tokens that are all the same move (any token out of the base): played without asking. */
    fun onlyMove(tokens: List<Int>, turn: Int?, d: Int): Int? {
        val l = legal(tokens, turn, d)
        if (l.isEmpty() || turn == null) return null
        return if (l.map { tokens[turn * 4 + it] }.toSet().size == 1) l[0] else null
    }

    fun activeSeats(players: List<String?>, out: List<Boolean>) = (0..3).filter { players[it] != null && !out[it] }

    /** The tokens (global index seat * 4 + k) I may tap now: my turn, a roll waiting, legal with it. */
    fun movable(g: LudoGame, mySeat: Int): Set<Int> {
        if (mySeat < 0 || g.status != LudoStatus.PLAYING || g.out[mySeat] || g.turn != mySeat) return emptySet()
        val d = g.dice ?: return emptySet()
        return legal(g, d).map { mySeat * 4 + it }.toSet()
    }

    /** Squares a token still has to go (for TalkBack): 56 from its start square, home column included. */
    fun stepsHome(p: Int) = HOME - p
}

/**
 * The game engine for the demo backend, the server's rules step for step. It works on a mutable copy; [events]
 * collects what happened, [sixes] counts sixes in the current turn, [misses] the turns each player missed in a row.
 */
class LudoEngine(game: LudoGame, var sixes: Int = 0, misses: List<Int> = listOf(0, 0, 0, 0), private val now: () -> Long) {
    var status = game.status
    val players = game.players.toMutableList()
    val names = game.names.toMutableList()
    val out = game.out.toMutableList()
    val tokens = game.tokens.toMutableList()
    var turn = game.turn
    var dice = game.dice
    var roll = game.roll
    var deadline = game.deadline
    var winner = game.winner
    var endedAt = game.endedAt
    val misses = misses.toMutableList()
    val events = mutableListOf<LudoEvent>()
    private val base = game

    fun game(version: Int = base.version) = base.copy(
        status = status, players = players.toList(), names = names.toList(), out = out.toList(), tokens = tokens.toList(),
        turn = turn, dice = dice, roll = roll, deadline = deadline, winner = winner, events = events.toList(), version = version,
        endedAt = endedAt,
    )

    private fun nextTurn() {
        for (i in 1..4) {
            val s = ((turn ?: 3) + i) % 4
            if (players[s] != null && !out[s]) {
                turn = s
                break
            }
        }
        dice = null
        sixes = 0
        deadline = now() + Ludo.TURN_MS
    }

    private fun finish(w: Int?) {
        status = LudoStatus.FINISHED
        winner = w
        dice = null
        deadline = null
        endedAt = now()
        if (w != null) events += LudoEvent.Win(w)
    }

    fun start() {
        status = LudoStatus.PLAYING
        turn = 3
        nextTurn()
        events += LudoEvent.Start(turn!!)
    }

    fun forfeit(seat: Int, why: LudoOut) {
        if (status == LudoStatus.LOBBY) {
            players[seat] = null
            names[seat] = null
            return
        }
        if (status != LudoStatus.PLAYING || players[seat] == null || out[seat]) return
        out[seat] = true
        for (k in 0..3) tokens[seat * 4 + k] = -1
        events += LudoEvent.Out(seat, why)
        val left = Ludo.activeSeats(players, out)
        if (left.size < 2) return finish(left.firstOrNull())
        if (turn == seat) nextTurn()
    }

    fun move(k: Int) {
        val seat = turn!!
        val d = dice!!
        val f = tokens[seat * 4 + k]
        val to = if (f == -1) 0 else f + d
        tokens[seat * 4 + k] = to
        val cap = mutableListOf<Triple<Int, Int, Int>>()
        val sq = Ludo.square(seat, to)
        if (sq != null && !Ludo.isSafe(sq)) {
            for (s in 0..3) {
                if (s == seat || players[s] == null || out[s]) continue
                for (j in 0..3) {
                    val p = tokens[s * 4 + j]
                    if (Ludo.square(s, p) == sq) {
                        cap += Triple(s, j, p)
                        tokens[s * 4 + j] = -1
                    }
                }
            }
        }
        events += LudoEvent.Move(seat, k, f, to, cap)
        dice = null
        if ((0..3).all { tokens[seat * 4 + it] == Ludo.HOME }) return finish(seat)
        if (d == 6 || cap.isNotEmpty() || to == Ludo.HOME) {
            if (d != 6) sixes = 0 // a bonus roll for a capture or home starts a new run of sixes
            deadline = now() + Ludo.TURN_MS
            return
        }
        nextTurn()
    }

    fun roll(d: Int, auto: Boolean) {
        roll = d
        events += LudoEvent.Roll(turn!!, d)
        sixes = if (d == 6) sixes + 1 else 0
        if (sixes >= 3) {
            events += LudoEvent.Triple6(turn!!)
            return nextTurn()
        }
        val l = Ludo.legal(tokens, turn, d)
        if (l.isEmpty()) {
            events += LudoEvent.Pass(turn!!)
            return nextTurn()
        }
        dice = d
        val only = Ludo.onlyMove(tokens, turn, d)
        if (auto || only != null) return move(only ?: l[0])
        deadline = now() + Ludo.TURN_MS
    }

    /** The turn's time ran out: rolled and moved for the player; the third time in a row they are out. */
    fun timeout(die: () -> Int) {
        val s = turn!!
        events += LudoEvent.Timeout(s)
        misses[s] += 1
        if (misses[s] >= 3) return forfeit(s, LudoOut.TIMEOUTS)
        if (dice == null) return roll(die(), true)
        move(Ludo.legal(tokens, turn, dice!!)[0])
    }
}
