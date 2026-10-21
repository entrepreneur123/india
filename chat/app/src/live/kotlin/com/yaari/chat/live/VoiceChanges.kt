package com.yaari.chat.live

import com.yaari.chat.model.VoicePoll
import com.yaari.chat.model.VoiceRoom
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// A room's state as voice_state() sends it, and the changes to it that come over Realtime Broadcast (020_scale_fixes.sql:
// private topic "voice:<room>", event "v"). Applied here the way the web app applies them (lib/supabaseBackend.ts).
// 029 adds polls ({t: 'p', poll}) to the same numbered stream: they take a number but change nothing in the state.

@Serializable internal data class VoiceMusicRow(
    val id: String, val videoId: String, val title: String = "", val by: String = "", val startedAt: Long, val pausedAt: Long? = null,
)

@Serializable internal data class VoiceQueueRow(
    val id: String, val videoId: String, val title: String = "", val by: String = "", val votes: Int = 0, val voted: Boolean = false,
)

/** [ha], [j]: when the hand went up and when they came in (server ms), for the order voice_state uses. */
@Serializable internal data class VoiceMemberRow(
    val id: String, val name: String, val gender: String = "other", val age: Int = 0, val city: String? = null, val bio: String? = null,
    @SerialName("photo_path") val photoPath: String? = null, val country: String? = null,
    val role: String, val seat: Int? = null, val muted: Boolean = true, val hand: Boolean = false,
    val ha: Long? = null, val j: Long? = null,
)

/** voice_state(): the room, the people present with their cards, the server clock (ms) and the room's change number. */
@Serializable internal data class VoiceStateRow(
    val id: String, val title: String, val category: String, val mode: String, val host: String, val rev: Long, val now: Long,
    val locked: List<Int> = emptyList(), val music: VoiceMusicRow? = null, val queue: List<VoiceQueueRow> = emptyList(),
    val members: List<VoiceMemberRow> = emptyList(),
    val announcement: String? = null, val max: Int = VoiceRoom.DEFAULT_MEMBERS, val gender: String? = null,
    val minAge: Int = VoiceRoom.MIN_AGE, val maxAge: Int = VoiceRoom.MAX_AGE, val private: Boolean = false, val code: String? = null,
    val seq: Long? = null,
    /** 027: the room's language (not part of the change events: a settings change re-reads the state). */
    val lang: String? = null,
)

/** The room's own part of the state (no members; `voted` is each client's own, the code is the host's). */
@Serializable internal data class VoiceRoomPart(
    val title: String, val category: String, val mode: String, val host: String, val locked: List<Int> = emptyList(),
    val music: VoiceMusicRow? = null, val queue: List<VoiceQueueRow> = emptyList(), val announcement: String? = null,
    val max: Int = VoiceRoom.DEFAULT_MEMBERS, val gender: String? = null, val minAge: Int = VoiceRoom.MIN_AGE,
    val maxAge: Int = VoiceRoom.MAX_AGE, val private: Boolean = false, val ended: Boolean = false,
)

/** voice_poll_json(): a poll's question, answers and counts; [mine] is null over Broadcast (each app keeps its own). */
@Serializable internal data class VoicePollRow(
    val id: Long, val question: String = "", val options: List<String> = emptyList(), val counts: List<Int> = emptyList(),
    val total: Int = 0, val closed: Boolean = false, val by: String = "", val mine: Int? = null,
) {
    fun toPoll() = VoicePoll(id, question, options, List(options.size) { counts.getOrElse(it) { 0 } }, total, closed, by, mine)
}

/** One change to a room. [seq] null: sent without a number (see ChangeOrder.Step.APPLY_RECHECK). */
internal sealed interface VoiceChange {
    val seq: Long?

    data class Member(override val seq: Long?, val member: VoiceMemberRow) : VoiceChange
    data class Gone(override val seq: Long?, val id: String) : VoiceChange
    data class Room(override val seq: Long?, val room: VoiceRoomPart) : VoiceChange
    data class Vote(override val seq: Long?, val item: String, val user: String, val on: Boolean, val votes: Int) : VoiceChange
    /** A poll's new counts (or it closed); the room's state stays as it is. */
    data class Poll(override val seq: Long?, val poll: VoicePoll) : VoiceChange

    companion object {
        /** A Broadcast payload; null when it is not one this app knows (a newer server). */
        fun parse(json: Json, o: JsonObject): VoiceChange? = runCatching {
            val seq = o["seq"]?.jsonPrimitive?.longOrNull
            when (o["t"]?.jsonPrimitive?.contentOrNull) {
                "m" -> if (o["present"]?.jsonPrimitive?.booleanOrNull == true) Member(seq, json.decodeFromJsonElement(VoiceMemberRow.serializer(), o))
                else Gone(seq, o["id"]!!.jsonPrimitive.content)
                "r" -> Room(seq, json.decodeFromJsonElement(VoiceRoomPart.serializer(), o))
                "v" -> Vote(
                    seq, o["item"]!!.jsonPrimitive.content, o["user"]!!.jsonPrimitive.content,
                    o["on"]!!.jsonPrimitive.booleanOrNull == true, o["votes"]!!.jsonPrimitive.content.toInt(),
                )
                "p" -> Poll(seq, json.decodeFromJsonElement(VoicePollRow.serializer(), o["poll"]!!).toPoll())
                else -> null
            }
        }.getOrNull()
    }
}

/** voice_state's member order: the stage by seat, then raised hands (oldest first), then by arrival. */
internal val stageOrder: Comparator<VoiceMemberRow> =
    compareBy<VoiceMemberRow> { it.seat ?: 99 }.thenBy { if (it.hand) it.ha ?: 0L else Long.MAX_VALUE }.thenBy { it.j ?: 0L }

/** The state with one change applied. My own votes stay mine ([me]); the code stays the host's (the full state has it). */
internal fun VoiceStateRow.apply(c: VoiceChange, me: String): VoiceStateRow = when (c) {
    is VoiceChange.Member -> copy(members = (members.filter { it.id != c.member.id } + c.member).sortedWith(stageOrder))
    is VoiceChange.Gone -> copy(members = members.filter { it.id != c.id })
    is VoiceChange.Room -> {
        val voted = queue.filter { it.voted }.map { it.id }.toSet()
        val r = c.room
        copy(
            title = r.title, category = r.category, mode = r.mode, host = r.host, locked = r.locked, music = r.music,
            announcement = r.announcement, max = r.max, gender = r.gender, minAge = r.minAge, maxAge = r.maxAge, private = r.private,
            queue = r.queue.map { it.copy(voted = it.id in voted) },
        )
    }
    is VoiceChange.Vote -> copy(
        queue = queue.map { q -> if (q.id != c.item) q else q.copy(votes = c.votes, voted = if (c.user == me) c.on else q.voted) },
    )
    is VoiceChange.Poll -> this
}
