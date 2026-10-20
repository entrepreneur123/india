package com.yaari.chat

import com.yaari.chat.domain.Ludo
import com.yaari.chat.domain.LudoEngine
import com.yaari.chat.model.LudoEvent
import com.yaari.chat.model.LudoGame
import com.yaari.chat.model.LudoStatus
import com.yaari.chat.ui.LudoMotion
import com.yaari.chat.ui.LudoPlayback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Ludo's board geometry and rules (the server's, 018_ludo.sql), and how the screen plays a change back. */
@OptIn(ExperimentalCoroutinesApi::class)
class LudoRulesTest {
    /** Next to each other: side by side, or across a corner of the cross (the track turns diagonally there). */
    private fun adjacent(a: Ludo.Pt, b: Ludo.Pt) = maxOf(abs(a.x - b.x), abs(a.y - b.y)) == 1f

    private fun game(
        tokens: List<Int> = List(16) { -1 }, turn: Int? = 0, dice: Int? = null, status: LudoStatus = LudoStatus.PLAYING,
        players: List<String?> = listOf("a", "b", "c", "d"), version: Int = 1, events: List<LudoEvent> = emptyList(),
    ) = LudoGame(
        "g1", "r", status, players, players, List(4) { false }, tokens, turn, dice, dice, deadline = 0L, winner = null,
        events = events, version = version, endedAt = null,
    )

    // ---- geometry ----

    @Test fun trackIsAClosedLoopOf52SquaresOnTheCross() {
        assertEquals(52, Ludo.TRACK.size)
        assertEquals(52, Ludo.TRACK.toSet().size)
        Ludo.TRACK.indices.forEach { i -> assertTrue("$i", adjacent(Ludo.TRACK[i], Ludo.TRACK[(i + 1) % 52])) }
        // Every square is on the cross (the middle three rows or columns), never in a base or the centre.
        Ludo.TRACK.forEach { (x, y) ->
            assertTrue(x in 0f..14f && y in 0f..14f)
            assertTrue((x in 6f..8f) != (y in 6f..8f))
        }
        assertEquals(Ludo.Pt(6f, 13f), Ludo.TRACK[0]) // red starts bottom-left of the cross, going up
    }

    @Test fun eachColourIsRedTurnedAQuarterClockwise() {
        for (seat in 1..3) for (p in listOf(-1, 0, 7, 33, 50, 51, 55)) for (k in 0..3) {
            val turned = Ludo.turn(Ludo.spot(0, p, k), seat)
            val spot = Ludo.spot(seat, p, k)
            assertEquals("seat $seat pos $p", turned.x, spot.x, 1e-4f)
            assertEquals("seat $seat pos $p", turned.y, spot.y, 1e-4f)
        }
        // Green's base is top-left, yellow's top-right, blue's bottom-right.
        assertTrue(Ludo.spot(1, -1, 0).let { it.x < 6 && it.y < 6 })
        assertTrue(Ludo.spot(2, -1, 0).let { it.x > 9 && it.y < 6 })
        assertTrue(Ludo.spot(3, -1, 0).let { it.x > 9 && it.y > 9 })
        // Start squares are 13 apart on the shared track.
        assertEquals(Ludo.TRACK[13].x + 0.5f, Ludo.spot(1, 0, 0).x, 0f)
    }

    @Test fun homeColumnLeadsFromTheLastTrackSquareToTheCentre() {
        assertEquals((9..13).reversed().map { Ludo.Pt(7f, it.toFloat()) }, Ludo.homeColumn(0))
        for (seat in 0..3) {
            val path = (50..55).map { Ludo.spot(seat, it, 0) }
            path.zipWithNext().forEach { (a, b) -> assertTrue("seat $seat", adjacent(a, b)) }
            // Home: inside the centre square, on the colour's side.
            val home = Ludo.spot(seat, Ludo.HOME, 0)
            assertTrue(home.x in 6f..9f && home.y in 6f..9f)
        }
    }

    @Test fun pathsGoHopByHop() {
        assertEquals(listOf(Ludo.spot(2, 0, 1)), Ludo.path(2, 1, -1, 0)) // out of the base: straight to the start
        val p = Ludo.path(0, 0, 3, 9)
        assertEquals(6, p.size)
        assertTrue(adjacent(Ludo.spot(0, 3, 0), p.first()))
        p.zipWithNext().forEach { (a, b) -> assertTrue(adjacent(a, b)) }
        assertEquals(Ludo.spot(0, 9, 0), p.last())
        // Across the corner of the cross (wrapping 51 -> 0 on the shared track for green).
        assertTrue(Ludo.path(1, 0, 36, 42).zipWithNext().all { (a, b) -> adjacent(a, b) })
    }

    @Test fun safeSquaresAreTheStartsAndTheStars() {
        assertEquals(listOf(0, 8, 13, 21, 26, 34, 39, 47), (0 until 52).filter(Ludo::isSafe))
        assertEquals(13, Ludo.square(1, 0))
        assertEquals(11, Ludo.square(1, 50)) // green's last track square, two before its start
        assertNull(Ludo.square(0, -1))
        assertNull(Ludo.square(0, 51))
    }

    @Test fun tokensOnOneSquareStandSideBySideSmaller() {
        val tokens = List(16) { -1 }.toMutableList()
        tokens[0] = 5
        tokens[1] = 5
        tokens[2] = Ludo.HOME
        val l = Ludo.layout(tokens)
        val at = Ludo.spot(0, 5, 0)
        assertEquals(at.x - 0.2f, l[0].x, 1e-4f)
        assertEquals(at.x + 0.2f, l[1].x, 1e-4f)
        assertEquals(0.7f, l[0].scale)
        assertEquals(0.62f, l[2].scale)
        assertEquals(1f, l[3].scale) // alone in its well
        // Never bigger than a pawn, never off the board.
        assertTrue(l.all { it.scale <= 1f && it.x in 0f..15f && it.y in 0f..15f })
    }

    // ---- rules ----

    @Test fun legalMovesAndWhatICanTap() {
        val g = game(tokens = listOf(-1, 3, 53, Ludo.HOME) + List(12) { -1 }, turn = 0, dice = 4)
        assertEquals(listOf(1), Ludo.legal(g, 4)) // base needs a 6; 53 + 4 overshoots home
        assertEquals(listOf(0, 1), Ludo.legal(g, 6))
        assertEquals(listOf(1, 2), Ludo.legal(g, 3)) // exactly home
        assertEquals(setOf(1), Ludo.movable(g, 0))
        assertEquals(emptySet<Int>(), Ludo.movable(g, 1)) // not my turn
        assertEquals(emptySet<Int>(), Ludo.movable(g.copy(dice = null), 0)) // roll first
        assertEquals(setOf(4 * 2 + 0, 4 * 2 + 1), Ludo.movable(game(tokens = List(8) { -1 } + listOf(10, 20) + List(6) { -1 }, turn = 2, dice = 2), 2))
        // Any token out of the base is the same move: played without asking.
        assertEquals(0, Ludo.onlyMove(List(16) { -1 }, 0, 6))
        assertNull(Ludo.onlyMove(listOf(-1, 3) + List(14) { -1 }, 0, 6))
        assertEquals(56 - 50, Ludo.stepsHome(50))
    }

    private fun engine(g: LudoGame, now: Long = 0L) = LudoEngine(g, now = { now })

    @Test fun captureSendsHomeButNotOnASafeSquare() {
        // Green's token on track square 10 (green's position 49); red moves from 7 to 10.
        val tokens = MutableList(16) { -1 }
        tokens[0] = 7
        tokens[4] = 49
        val e = engine(game(tokens = tokens, turn = 0, dice = 3))
        e.move(0)
        assertEquals(-1, e.tokens[4])
        assertEquals(LudoEvent.Move(0, 0, 7, 10, listOf(Triple(1, 0, 49))), e.events.single())
        assertEquals(0, e.turn) // a capture gives another roll
        // On a star (square 8) nobody is captured.
        val safe = MutableList(16) { -1 }
        safe[0] = 5
        safe[4] = 47 // green 47 -> square 8
        val s = engine(game(tokens = safe, turn = 0, dice = 3))
        s.move(0)
        assertEquals(47, s.tokens[4])
        assertEquals(1, s.turn)
    }

    @Test fun threeSixesEndTheTurnAndAWinEndsTheGame() {
        val e = engine(game(tokens = listOf(10) + List(15) { -1 }, turn = 0), now = 5)
        e.roll(6, false); e.move(0)
        e.roll(6, false); e.move(0)
        e.roll(6, false)
        assertEquals(LudoEvent.Triple6(0), e.events.last())
        assertEquals(1, e.turn)
        assertEquals(22, e.tokens[0]) // the third six didn't move
        assertEquals(5 + Ludo.TURN_MS, e.deadline)

        val w = engine(game(tokens = listOf(55, 56, 56, 56) + List(12) { -1 }, turn = 0))
        w.roll(1, false) // the only move: played at once
        assertEquals(LudoStatus.FINISHED, w.status)
        assertEquals(0, w.winner)
        assertEquals(LudoEvent.Win(0), w.events.last())
    }

    @Test fun threeTimeoutsInARowPutAPlayerOut() {
        var g = game(players = listOf("a", "b", null, null), tokens = listOf(5) + List(15) { -1 }, turn = 0)
        var misses = listOf(0, 0, 0, 0)
        repeat(2) {
            val e = LudoEngine(g, misses = misses, now = { 0L })
            e.timeout { 2 }
            assertEquals(LudoEvent.Timeout(0), e.events.first())
            misses = e.misses
            g = e.game().copy(turn = 0)
        }
        val e = LudoEngine(g, misses = misses, now = { 0L })
        e.timeout { 2 }
        assertTrue(e.out[0])
        assertEquals(LudoStatus.FINISHED, e.status) // one player left: they win
        assertEquals(1, e.winner)
    }

    // ---- playback ----

    @Test fun playbackHopsSquareBySquareThenSlidesTheCapturedHome() = runTest {
        val start = game(tokens = listOf(7) + List(3) { -1 } + listOf(49) + List(11) { -1 }, turn = 0, version = 1)
        val play = LudoPlayback(start) { testScheduler.currentTime }
        val job = launch { play.play { false } }
        val next = start.copy(
            version = 2, tokens = listOf(10) + List(15) { -1 }, roll = 3,
            events = listOf(LudoEvent.Roll(0, 3), LudoEvent.Move(0, 0, 7, 10, listOf(Triple(1, 0, 49)))),
        )
        play.offer(next)
        runCurrent()
        assertTrue(play.busy && play.rolling)
        advanceTimeBy(LudoPlayback.ROLL_MS + 1)
        assertFalse(play.rolling)
        assertEquals(3, play.dice)
        // Then one square at a time, about every 135 ms.
        advanceTimeBy(420)
        val seen = mutableListOf(play.tokens[0])
        repeat(3) {
            advanceTimeBy(LudoPlayback.HOP_MS)
            seen += play.tokens[0]
        }
        assertEquals(listOf(8, 9, 10, 10), seen)
        assertEquals(-1, play.tokens[4])
        assertEquals(LudoMotion.SLIDE, play.motion[4])
        advanceTimeBy(1_000)
        assertFalse(play.busy)
        assertEquals(next, play.view)
        assertTrue(play.news!!.show)

        // A jump of several versions is shown as it is; a new game snaps.
        play.offer(next.copy(version = 5, tokens = List(16) { 0 }, events = listOf(LudoEvent.Pass(0))))
        advanceTimeBy(10)
        assertEquals(List(16) { 0 }, play.tokens)
        play.offer(game().copy(id = "g2", version = 1))
        assertEquals("g2", play.view.id)
        assertFalse(play.busy)
        job.cancel()
    }
}
