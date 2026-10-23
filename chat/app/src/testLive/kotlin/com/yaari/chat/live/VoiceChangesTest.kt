package com.yaari.chat.live

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Broadcast payloads of 020_scale_fixes.sql (voice_member_json, voice_room_event, voice_vote_event) and 029 (polls) applied to a state. */
class VoiceChangesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun change(text: String) = VoiceChange.parse(json, json.parseToJsonElement(text).jsonObject)

    private fun member(id: String, seat: Int? = null, hand: Boolean = false, ha: Long? = null, j: Long = 0) =
        VoiceMemberRow(id, id.uppercase(), role = if (seat == 0) "host" else "listener", seat = seat, hand = hand, ha = ha, j = j)

    private val state = VoiceStateRow(
        id = "r1", title = "Chai", category = "talk", mode = "hand", host = "h", rev = 9, now = 0, seq = 4, code = "ABC234",
        members = listOf(member("h", seat = 0, j = 1), member("a", j = 2), member("b", j = 3)),
        queue = listOf(VoiceQueueRow("q1", "v1", votes = 2, voted = true), VoiceQueueRow("q2", "v2", votes = 0)),
    )

    @Test fun aMemberComingInOrUpTakesTheirPlaceInVoiceStatesOrder() {
        val up = change(
            """{"t":"m","id":"b","present":true,"role":"listener","seat":null,"muted":true,"hand":true,"ha":50,"j":3,
               "name":"B","gender":"female","age":24,"city":"Pune","bio":null,"photo_path":null,"country":"IN","seq":5}""",
        )!!
        assertEquals(5L, up.seq)
        val s = state.apply(up, "me")
        // The stage, then raised hands, then by arrival.
        assertEquals(listOf("h", "b", "a"), s.members.map { it.id })
        assertEquals("female", s.members[1].gender)

        val seated = state.apply(VoiceChange.Member(6, member("c", seat = 2, j = 9)), "me")
        assertEquals(listOf("h", "c", "a", "b"), seated.members.map { it.id })
    }

    @Test fun theSharedPageComesWithTheRoomsChangesAndStaysWhenAnEventLacksIt() {
        val base = """"t":"r","title":"Chai","category":"talk","mode":"hand","host":"h","locked":[],"music":null,"queue":[],"max":50,"private":false,"ended":false"""
        val shared = change("""{$base,"stage":{"url":"https://example.com/a","title":"A","by":"Meera","byId":"h","at":77},"seq":5}""")!!
        val s = state.apply(shared, "me")
        assertEquals(VoiceStageRow("https://example.com/a", "A", "Meera", "h", 77), s.stage)
        // An event from a server without the key (before 037) keeps it; an explicit null stops it.
        assertEquals(s.stage, s.apply(change("""{$base,"seq":6}""")!!, "me").stage)
        assertNull(s.apply(change("""{$base,"stage":null,"seq":7}""")!!, "me").stage)
    }

    @Test fun aLostConnectionComesWithTheMember() {
        val base = """{"t":"m","id":"a","present":true,"role":"speaker","seat":2,"muted":false,"hand":false,"name":"A","seq":5"""
        val lost = state.apply(change("$base,\"lost\":true}")!!, "me")
        assertEquals(true, lost.members.first { it.id == "a" }.lost)
        // Back (or a server before 045, without the key): not lost.
        assertEquals(false, lost.apply(change(base.replace("\"seq\":5", "\"seq\":6") + "}")!!, "me").members.first { it.id == "a" }.lost)
    }

    @Test fun aMemberGoing() {
        val gone = change("""{"t":"m","id":"a","present":false,"seq":5}""")!!
        assertEquals(listOf("h", "b"), state.apply(gone, "me").members.map { it.id })
    }

    @Test fun theRoomsSettingsKeepMyVotesAndTheCode() {
        val r = change(
            """{"t":"r","title":"Chai 2","category":"music","mode":"open","host":"h","locked":[7],
               "music":{"id":"q1","videoId":"v1","title":"Song","by":"A","startedAt":1000,"pausedAt":null},
               "queue":[{"id":"q2","videoId":"v2","title":"","by":"B","votes":1},{"id":"q1","videoId":"v1","title":"","by":"A","votes":3}],
               "announcement":"hi","max":50,"gender":"female","minAge":20,"maxAge":30,"private":true,"ended":false,"seq":5}""",
        ) as VoiceChange.Room
        val s = state.apply(r, "me")
        assertEquals("Chai 2", s.title)
        assertEquals("open", s.mode)
        assertEquals(listOf(7), s.locked)
        assertEquals("Song", s.music!!.title)
        assertEquals(listOf("q2" to false, "q1" to true), s.queue.map { it.id to it.voted })
        assertEquals(listOf(1, 3), s.queue.map { it.votes })
        assertEquals("ABC234", s.code)
        assertEquals(50, s.max)
        assertTrue(s.private)
        assertEquals(state.members, s.members)
    }

    @Test fun votesCountForEveryoneAndMarkOnlyMine() {
        val theirs = state.apply(change("""{"t":"v","item":"q2","user":"x","on":true,"votes":1,"seq":5}""")!!, "me")
        assertEquals(1 to false, theirs.queue[1].let { it.votes to it.voted })
        val mine = theirs.apply(change("""{"t":"v","item":"q1","user":"me","on":false,"votes":1,"seq":6}""")!!, "me")
        assertEquals(1 to false, mine.queue[0].let { it.votes to it.voted })
    }

    @Test fun unknownOrBrokenPayloadsAreIgnoredAndANullNumberStaysNull() {
        assertNull(change("""{"t":"z","seq":5}"""))
        assertNull(change("""{"t":"v","item":"q1"}"""))
        assertNull(change("""{"t":"m","id":"a","present":false,"seq":null}""")!!.seq)
        assertTrue(change("""{"t":"r","title":"x","category":"talk","mode":"hand","host":"h","ended":true}""").let { it is VoiceChange.Room && it.room.ended })
        // A poll without its poll object.
        assertNull(change("""{"t":"p","seq":6}"""))
    }

    /** 029: a poll's counts come numbered with the room's other changes; the state itself stays as it is. */
    @Test fun aPollChangeTakesItsNumberAndLeavesTheStateAlone() {
        val c = change(
            """{"t":"p","seq":5,"poll":{"id":42,"question":"Tea or coffee?","options":["Tea","Coffee"],"counts":[3,1],
               "total":4,"closed":false,"by":"h","mine":null}}""",
        ) as VoiceChange.Poll
        assertEquals(5L, c.seq)
        assertEquals(42L, c.poll.id)
        assertEquals(listOf(3, 1), c.poll.counts)
        assertEquals(75, c.poll.percent(0))
        assertNull(c.poll.mine)
        assertEquals(state, state.apply(c, "me"))
        // Counts shorter than the options (an older row): the missing ones are 0.
        val short = change("""{"t":"p","seq":6,"poll":{"id":1,"question":"Q","options":["a","b","c"],"counts":[1],"total":1,"closed":true,"by":"h"}}""") as VoiceChange.Poll
        assertEquals(listOf(1, 0, 0), short.poll.counts)
        assertTrue(short.poll.closed)
    }
}
