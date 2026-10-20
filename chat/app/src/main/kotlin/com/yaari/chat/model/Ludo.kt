package com.yaari.chat.model

// ---- Ludo inside a voice room ----------------------------------------------------------------------------------
// Same shapes as the web (chat/web/lib/backend.ts); the server is chat/server/migrations/018_ludo.sql. Seats 0-3 are
// red, green, yellow and blue, played in that order. Token positions: -1 base, 0-50 track (from the colour's start
// square), 51-55 home column, 56 home.

enum class LudoStatus(val code: String) {
    LOBBY("lobby"), PLAYING("playing"), FINISHED("finished"), CANCELLED("cancelled");

    val open get() = this == LOBBY || this == PLAYING

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code } ?: CANCELLED
    }
}

/** Why a player is out. */
enum class LudoOut(val code: String) {
    LEFT("left"), TIMEOUTS("timeouts"), QUIT("quit");

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code } ?: LEFT
    }
}

/** What the last action did, in order (a roll, the move it made, a capture...): the app animates and announces it. */
sealed interface LudoEvent {
    /** The seat it is about. */
    val s: Int

    data class Start(override val s: Int) : LudoEvent
    data class Roll(override val s: Int, val d: Int) : LudoEvent
    /** [cap]: tokens sent home, as (seat, token, position). */
    data class Move(override val s: Int, val t: Int, val f: Int, val to: Int, val cap: List<Triple<Int, Int, Int>> = emptyList()) : LudoEvent
    data class Pass(override val s: Int) : LudoEvent
    data class Triple6(override val s: Int) : LudoEvent
    data class Timeout(override val s: Int) : LudoEvent
    data class Win(override val s: Int) : LudoEvent
    data class Out(override val s: Int, val why: LudoOut) : LudoEvent
}

data class LudoGame(
    val id: String,
    val roomId: String,
    val status: LudoStatus,
    /** User id per colour (null = free) and the name they sat down with. */
    val players: List<String?>,
    val names: List<String?>,
    val out: List<Boolean>,
    /** 16 positions: tokens[seat * 4 + k]. */
    val tokens: List<Int>,
    val turn: Int?,
    /** A roll waiting for a move (null: the player rolls next). */
    val dice: Int?,
    /** The last value shown on the die. */
    val roll: Int?,
    /** The turn ends then (this phone's wall clock); anyone in the room may then play it for the absent player. */
    val deadline: Long?,
    val winner: Int?,
    val events: List<LudoEvent>,
    /** Bumped by every change; actions carry the one the screen showed. */
    val version: Int,
    val endedAt: Long?,
) {
    fun seatOf(userId: String) = players.indexOf(userId)
}

sealed interface LudoAction {
    // host and co-hosts
    data object Open : LudoAction
    data object Start : LudoAction
    data object Close : LudoAction
    // anyone in the room
    data class Join(val seat: Int? = null) : LudoAction
    data object Leave : LudoAction
    data class Roll(val version: Int) : LudoAction
    data class Move(val token: Int, val version: Int) : LudoAction
    /** The turn's time is up: the server plays it (or does nothing when it was already played). */
    data class Timeout(val version: Int) : LudoAction
}
