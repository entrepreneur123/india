package com.yaari.chat.data

import com.yaari.chat.domain.Ludo
import com.yaari.chat.domain.LudoEngine
import com.yaari.chat.model.LudoAction
import com.yaari.chat.model.LudoEvent
import com.yaari.chat.model.LudoGame
import com.yaari.chat.model.LudoOut
import com.yaari.chat.model.LudoStatus
import com.yaari.chat.model.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Demo Ludo (a port of the web's lib/mockLudo.ts): the server's rules (domain/Ludo.kt) played on the phone. People in
 * my room join my games and play legal moves at a human pace; the "Ludo night" room always has a game on to watch.
 * [ambient]: bots act and turns time out by themselves (off in tests, which step the game through the hooks).
 */
class FakeLudo(
    private val voice: Rooms,
    private val me: () -> Profile?,
    private val random: Random,
    private val clock: () -> Long,
    private val wait: suspend (Long) -> Unit,
    private val ambient: Boolean,
) {
    /** What the game needs to know about a voice room (FakeVoice). */
    interface Rooms {
        fun peek(roomId: String): RoomView?
        /** A game in the room ended with this winner (the room's leaderboard). */
        fun won(roomId: String, userId: String, name: String) {}
    }

    data class RoomView(val members: List<Person>, val hostId: String, val mine: Boolean)
    data class Person(val profile: Profile, val mod: Boolean, val seat: Int?)

    private class Full(var game: LudoGame, var sixes: Int = 0, var misses: List<Int> = listOf(0, 0, 0, 0))

    private class Table {
        var full: Full? = null
        val subs = mutableSetOf<(LudoGame) -> Unit>()
        /** The newest follower's scope: the bots' timers run on it, only while someone follows the game. */
        var scope: CoroutineScope? = null
        val timers = mutableSetOf<Job>()
        /** People about to take a colour in my lobby. */
        val joiners = ArrayDeque<Profile>()
    }

    private val tables = mutableMapOf<String, Table>()
    private var seq = 0

    private fun uid() = "g-" + (seq++).toString(36) + random.nextInt(1_000_000).toString(36)
    /** Die values queued by a test ([loadDice]), used before random ones. */
    private val queued = ArrayDeque<Int>()

    private fun die() = queued.removeFirstOrNull() ?: (1 + random.nextInt(6))
    private fun jitter(min: Long, max: Long) = random.nextLong(min, max)

    private fun table(roomId: String): Table = tables.getOrPut(roomId) {
        Table().also { if (roomId == LUDO_ROOM) botGame(roomId, it, MID_GAME) }
    }

    private fun fresh(roomId: String) = LudoGame(
        id = uid(), roomId = roomId, status = LudoStatus.LOBBY, players = listOf(null, null, null, null),
        names = listOf(null, null, null, null), out = listOf(false, false, false, false), tokens = List(16) { -1 },
        turn = null, dice = null, roll = null, deadline = null, winner = null, events = emptyList(), version = 0, endedAt = null,
    )

    /** Four people on the stage of the room playing each other. */
    private fun botGame(roomId: String, t: Table, tokens: List<Int>? = null) {
        val room = voice.peek(roomId) ?: return
        var g = fresh(roomId)
        val players = g.players.toMutableList()
        val names = g.names.toMutableList()
        room.members.filter { it.seat != null }.sortedBy { it.seat }.take(4).forEachIndexed { s, m ->
            players[s] = m.profile.id
            names[s] = m.profile.name
        }
        g = g.copy(players = players, names = names)
        val e = LudoEngine(g, now = clock)
        e.start()
        if (tokens != null) {
            e.tokens.indices.forEach { e.tokens[it] = tokens[it] }
            e.turn = 2
        }
        t.full = Full(e.game(g.version).copy(events = emptyList()), e.sixes, e.misses)
    }

    private fun publish(t: Table, f: Full, events: List<LudoEvent>) {
        val before = f.game.status
        f.game = f.game.copy(events = events, version = f.game.version + 1)
        val g = f.game
        if (g.status == LudoStatus.FINISHED && before != LudoStatus.FINISHED) {
            g.winner?.let { w -> g.players[w]?.let { voice.won(g.roomId, it, g.names[w].orEmpty()) } }
        }
        t.full = f
        val snap = f.game
        t.subs.toList().forEach { it(snap) }
        schedule(t)
    }

    private fun later(t: Table, ms: Long, fn: () -> Unit) {
        val scope = t.scope ?: return
        lateinit var job: Job
        job = scope.launch {
            delay(ms)
            t.timers -= job
            if (t.subs.isNotEmpty()) fn()
        }
        t.timers += job
    }

    private fun clear(t: Table) {
        t.timers.toList().forEach { it.cancel() }
        t.timers.clear()
    }

    private fun isBot(id: String?) = id != null && id != me()?.id

    /** What happens next on its own: a bot's roll or move, the clock running out, a bot-only game starting over. */
    private fun schedule(t: Table) {
        clear(t)
        val f = t.full ?: return
        if (!ambient || t.subs.isEmpty()) return
        val next = next(t, f) ?: return
        later(t, next.first) { next.second() }
    }

    /** The next thing that happens by itself, and after how long; null when it waits for me. */
    private fun next(t: Table, f: Full): Pair<Long, () -> Unit>? {
        val g = f.game
        if (g.status == LudoStatus.FINISHED && g.roomId == LUDO_ROOM) {
            return 9_000L to {
                botGame(g.roomId, t)
                t.full?.let { n ->
                    n.game = n.game.copy(version = g.version)
                    publish(t, n, listOf(LudoEvent.Start(n.game.turn!!)))
                }
            }
        }
        if (g.status == LudoStatus.LOBBY && t.joiners.isNotEmpty()) {
            return 900L to {
                val p = t.joiners.removeFirst()
                val s = g.players.indexOf(null)
                val present = voice.peek(g.roomId)?.members?.any { it.profile.id == p.id } == true
                if (s < 0 || p.id in g.players || !present) schedule(t)
                else publish(t, Full(g.copy(players = g.players.with(s, p.id), names = g.names.with(s, p.name)), f.sixes, f.misses), emptyList())
            }
        }
        if (g.status != LudoStatus.PLAYING || g.turn == null) return null
        if (isBot(g.players[g.turn])) {
            // Rolling takes a moment's thought; picking a token a little less.
            return (if (g.dice == null) jitter(900, 1_500) else jitter(700, 1_100)) to {
                step(t) { e -> if (e.dice == null) e.roll(die(), false) else e.move(choose(e)) }
            }
        }
        val deadline = g.deadline ?: return null
        return maxOf(0L, deadline - clock()) + 400 to { step(t) { e -> e.timeout(::die) } }
    }

    private fun step(t: Table, fn: (LudoEngine) -> Unit) {
        val f = t.full ?: return
        val e = LudoEngine(f.game, f.sixes, f.misses, clock)
        fn(e)
        publish(t, Full(e.game(f.game.version), e.sixes, e.misses), e.events.toList())
    }

    /** A bot's pick: a capture, then home, then out of the base, then a safe square, else the token furthest on. */
    private fun choose(e: LudoEngine): Int {
        val s = e.turn!!
        val d = e.dice!!
        fun score(k: Int): Double {
            val from = e.tokens[s * 4 + k]
            val to = if (from == -1) 0 else from + d
            val sq = Ludo.square(s, to)
            var v = to / 10.0
            if (sq != null && !Ludo.isSafe(sq)) {
                for (o in 0..3) {
                    if (o == s || e.players[o] == null || e.out[o]) continue
                    for (j in 0..3) if (Ludo.square(o, e.tokens[o * 4 + j]) == sq) v += 20
                }
            }
            if (to == Ludo.HOME) v += 12
            if (from == -1) v += 8
            if (sq != null && Ludo.isSafe(sq)) v += 3
            return v + random.nextDouble()
        }
        return Ludo.legal(e.tokens, s, d).maxBy(::score)
    }

    // ---- the Backend methods ----

    /** The room's open game for the rooms list (lobby or playing), without starting anything. */
    fun openStatus(roomId: String): LudoStatus? =
        (if (roomId == LUDO_ROOM) table(roomId) else tables[roomId])?.full?.game?.status?.takeIf { it.open }

    suspend fun game(roomId: String): LudoGame? {
        wait(200)
        val g = table(roomId).full?.game ?: return null
        val recent = g.status != LudoStatus.FINISHED || clock() - (g.endedAt ?: 0L) < 300_000L
        return g.takeIf { it.status != LudoStatus.CANCELLED && recent }
    }

    fun follow(roomId: String): Flow<LudoGame> = channelFlow {
        val t = table(roomId)
        val sub: (LudoGame) -> Unit = { trySend(it) }
        t.subs += sub
        t.scope = this
        schedule(t)
        awaitClose {
            t.subs -= sub
            if (t.scope === this) t.scope = null
            if (t.subs.isEmpty()) clear(t)
            // Leaving the room is a forfeit, as on the server.
            val mine = me()?.id
            val g = t.full?.game
            if (mine != null && g != null && g.status.open && mine in g.players && voice.peek(roomId)?.members?.none { it.profile.id == mine } != false) {
                step(t) { e -> e.forfeit(g.players.indexOf(mine), LudoOut.LEFT) }
            }
        }
    }

    suspend fun act(roomId: String, gameId: String?, a: LudoAction): LudoGame? {
        val me = me() ?: throw BackendException("not_allowed")
        val room = voice.peek(roomId) ?: throw BackendException("not_allowed")
        val member = room.members.firstOrNull { it.profile.id == me.id } ?: throw BackendException("not_allowed")
        val t = table(roomId)
        wait(if (a is LudoAction.Roll) 250 else 150)
        fun need(ok: Boolean, code: String = "not_allowed") {
            if (!ok) throw BackendException(code)
        }
        val current = t.full

        if (a == LudoAction.Open) {
            need(member.mod)
            current?.game?.takeIf { it.status.open }?.let { return it }
            var n = fresh(roomId)
            val players = n.players.toMutableList()
            val names = n.names.toMutableList()
            // Play again: whoever played keeps their colour.
            val prev = current?.game
            if (prev?.status == LudoStatus.FINISHED) {
                for (s in 0..3) if (room.members.any { it.profile.id == prev.players[s] }) {
                    players[s] = prev.players[s]
                    names[s] = prev.names[s]
                }
            }
            if (me.id !in players) players.indexOf(null).takeIf { it >= 0 }?.let { s ->
                players[s] = me.id
                names[s] = me.name
            }
            n = n.copy(players = players, names = names, version = prev?.version ?: 0)
            t.joiners.clear()
            val f = Full(n)
            publish(t, f, emptyList())
            if (room.mine) botsJoin(t, room)
            return f.game
        }

        need(current != null && current.game.id == gameId, "not_found")
        val f = current!!
        val g = f.game
        val e = LudoEngine(g, f.sixes, f.misses, clock)
        val seat = g.players.indexOf(me.id)
        when (a) {
            is LudoAction.Join -> {
                need(g.status == LudoStatus.LOBBY)
                val s = a.seat ?: g.players.indexOf(null)
                need(s >= 0, "game_full")
                if (seat == s) return g
                need(g.players[s] == null, "seat_taken")
                if (seat >= 0) {
                    e.players[seat] = null
                    e.names[seat] = null
                }
                e.players[s] = me.id
                e.names[s] = me.name
            }
            LudoAction.Leave -> if (seat >= 0) e.forfeit(seat, LudoOut.QUIT)
            LudoAction.Start -> {
                need(member.mod && g.status == LudoStatus.LOBBY)
                need(Ludo.activeSeats(g.players, g.out).size >= 2, "need_players")
                e.start()
            }
            LudoAction.Close -> {
                need(member.mod)
                if (g.status.open) {
                    e.status = LudoStatus.CANCELLED
                    e.dice = null
                    e.deadline = null
                    e.endedAt = clock()
                }
            }
            is LudoAction.Roll, is LudoAction.Move -> {
                need((if (a is LudoAction.Roll) a.version else (a as LudoAction.Move).version) == g.version, "stale")
                need(g.status == LudoStatus.PLAYING && seat >= 0 && !g.out[seat])
                need(g.turn == seat, "not_your_turn")
                e.misses[seat] = 0
                if (a is LudoAction.Roll) {
                    need(g.dice == null, "stale")
                    e.roll(die(), false)
                } else {
                    val token = (a as LudoAction.Move).token
                    need(g.dice != null && token in Ludo.legal(g, g.dice))
                    e.move(token)
                }
            }
            is LudoAction.Timeout -> {
                if (a.version != g.version || g.status != LudoStatus.PLAYING || (g.deadline ?: Long.MAX_VALUE) > clock()) return g
                e.timeout(::die)
            }
            LudoAction.Open -> Unit
        }
        val n = Full(e.game(g.version), e.sixes, e.misses)
        if (n.game.copy(events = g.events) == g) return g // nothing changed: no new version
        publish(t, n, e.events.toList())
        return n.game
    }

    /** In my room, people on the stage (then the audience) take the free colours, one after another. */
    private fun botsJoin(t: Table, room: RoomView) {
        val mine = me()?.id
        val people = room.members.filter { it.seat != null } + room.members.filter { it.seat == null }
        t.joiners.clear()
        t.joiners += people.filter { it.profile.id != mine }.take(3).map { it.profile }
        schedule(t)
    }

    /** [userId] left the room: out of its game, as on the server. */
    fun left(roomId: String, userId: String) {
        val t = tables[roomId] ?: return
        val g = t.full?.game ?: return
        if (g.status.open && userId in g.players) step(t) { e -> e.forfeit(g.players.indexOf(userId), LudoOut.LEFT) }
    }

    // ---- hooks for tests and screenshots (with [ambient] off nothing happens by itself) ----

    /** Does at once what would happen next by itself (a person joining, a bot's roll or move, a timeout). */
    fun step(roomId: String) {
        val t = tables[roomId] ?: return
        val f = t.full ?: return
        if (f.game.status == LudoStatus.PLAYING && !isBot(f.game.players[f.game.turn ?: return])) return step(t) { e -> e.timeout(::die) }
        next(t, f)?.second?.invoke()
    }

    /** People in the room (stage first) are queued to take the free colours of my lobby. */
    fun queueJoiners(roomId: String) {
        val t = tables[roomId] ?: return
        voice.peek(roomId)?.let { botsJoin(t, it) }
    }

    /** Sets the board as given (tokens, whose turn, a roll waiting), as a new version with no events. */
    fun arrange(roomId: String, tokens: List<Int>, turn: Int, dice: Int? = null, roll: Int? = dice) {
        val t = tables[roomId] ?: return
        val f = t.full ?: return
        val g = f.game.copy(tokens = tokens, turn = turn, dice = dice, roll = roll ?: f.game.roll, deadline = clock() + Ludo.TURN_MS)
        publish(t, Full(g, 0, f.misses), emptyList())
    }

    /** The next die values (tests): consumed before the random ones. */
    fun loadDice(vararg values: Int) {
        queued += values.toList()
    }

    companion object {
        /** The demo room with a game always on, to watch. */
        const val LUDO_ROOM = "v-ludo"

        /** The game the "Ludo night" room is in the middle of when someone walks in. */
        private val MID_GAME = listOf(14, 30, -1, -1, 5, -1, -1, -1, 56, 56, 52, 50, 11, 33, -1, -1)

        private fun <T> List<T>.with(i: Int, v: T) = toMutableList().also { it[i] = v }
    }
}
