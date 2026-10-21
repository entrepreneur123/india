package com.yaari.chat.model

import java.net.URI

// ---- Voice rooms (X Spaces-style party rooms: a stage of seats, an audience, chat and shared music) ------------
// Same shapes as the web (chat/web/lib/backend.ts); the server is chat/server/migrations/017_voice_rooms.sql and
// 019_unified_rooms.sql: one kind of room, with the chat (text, icebreakers, photos), the stage, music and Ludo.

/** Co-hosts can invite, move and mute people; only the host makes co-hosts, removes people and ends the room. */
enum class VoiceRole(val code: String) {
    HOST("host"), COHOST("cohost"), SPEAKER("speaker"), LISTENER("listener");

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code } ?: LISTENER
    }
}

/** Who may take a seat: listeners raise a hand and the host invites them up, or anyone takes a free seat. */
enum class SpeakMode(val code: String) {
    APPROVAL("approval"), OPEN("open");

    companion object {
        fun of(code: String?) = if (code == "open") OPEN else APPROVAL
    }
}

/** [seat]: 0 is the host's, 1–8 the stage, null in the audience. */
data class VoiceMember(val profile: Profile, val role: VoiceRole, val seat: Int?, val muted: Boolean, val hand: Boolean) {
    val isMod get() = role == VoiceRole.HOST || role == VoiceRole.COHOST
    val onStage get() = seat != null
}

data class VoiceSeat(val index: Int, val locked: Boolean)

/**
 * The shared track. Everyone plays the same YouTube video at the same position: [position] = now − startedAt (or
 * pausedAt − startedAt while paused). Times are on this phone's clock (the server's, corrected by the measured offset).
 */
data class VoiceTrack(
    val id: String, val videoId: String,
    /** Empty when unknown (a pasted link): "YouTube video" is shown. */
    val title: String,
    val addedBy: String, val startedAt: Long, val pausedAt: Long? = null,
) {
    /** Where the track is at [now], in seconds (never negative). */
    fun position(now: Long): Float = maxOf(0L, (pausedAt ?: now) - startedAt) / 1000f
}

/** A song request: anyone adds one and votes once; the host's "next" plays the most voted (ties: the oldest). */
data class VoiceQueueItem(
    val id: String, val videoId: String, val title: String, val addedBy: String,
    val votes: Int = 0,
    /** I voted for it. */
    val voted: Boolean = false,
)

/** The requests in playing order: most voted first, ties by the order they were added (as the server's voice_skip). */
fun List<VoiceQueueItem>.byVotes(): List<VoiceQueueItem> = sortedByDescending { it.votes } // stable: ties keep their order

/** Who may come in (the server checks again: voice_refusal). */
data class RoomRules(
    val maxMembers: Int = VoiceRoom.DEFAULT_MEMBERS,
    val genderRule: GenderRule = GenderRule.ANY,
    val minAge: Int = VoiceRoom.MIN_AGE,
    val maxAge: Int = VoiceRoom.MAX_AGE,
    /** Found only by its code. */
    val isPrivate: Boolean = false,
) {
    val hasAgeLimit get() = minAge > VoiceRoom.MIN_AGE || maxAge < VoiceRoom.MAX_AGE
}

/** The live rooms list: lobby or playing. */
enum class RoomGame { LOBBY, PLAYING }

/**
 * A room on the list. [refusal] is the server's reason why I can't come in (room_closed, room_gender, room_age,
 * room_full, not_allowed), null when I may; [code] is known only for my own room.
 */
data class VoiceRoomSummary(
    val id: String, val title: String, val category: RoomCategory, val host: Profile,
    /** Up to 4 people on the stage, the host first (empty: a chat-only room right now). */
    val speakers: List<Profile>,
    val listeners: Int, val nowPlaying: String?, val mine: Boolean,
    /** People present, me included. */
    val members: Int = speakers.size + listeners,
    val game: RoomGame? = null,
    val rules: RoomRules = RoomRules(),
    val announcement: String? = null,
    val code: String? = null,
    val refusal: String? = null,
)

data class VoiceRoom(
    val id: String, val title: String, val category: RoomCategory, val mode: SpeakMode,
    val hostId: String, val meId: String,
    val members: List<VoiceMember>,
    val seats: List<VoiceSeat>,
    val track: VoiceTrack?,
    val queue: List<VoiceQueueItem>,
    val rules: RoomRules = RoomRules(),
    /** Pinned at the top by the host. */
    val announcement: String? = null,
    /** The room's 6-character code, for its host only. */
    val code: String? = null,
) {
    fun member(userId: String) = members.firstOrNull { it.profile.id == userId }
    val me get() = member(meId)
    val audience get() = members.filter { it.seat == null }
    fun bySeat(seat: Int) = members.firstOrNull { it.seat == seat }
    fun locked(seat: Int) = seats.getOrNull(seat)?.locked == true

    /** Someone is on the stage: only then is there audio (a room nobody speaks in is a chat room). */
    val stageLive get() = members.any { it.seat != null }

    /** The first free, unlocked stage seat (1–8), or null. */
    fun firstFreeSeat(): Int? {
        val taken = members.mapNotNull { it.seat }.toSet()
        return (1 until SEATS).firstOrNull { it !in taken && !locked(it) }
    }

    /** Free, unlocked stage seats (1–8). */
    val freeSeats: Int get() {
        val taken = members.mapNotNull { it.seat }.toSet()
        return seats.count { it.index in 1 until SEATS && !it.locked && it.index !in taken }
    }

    companion object {
        /** Seat 0 is the host's; seats 1–8 are the stage. */
        const val SEATS = 9
        const val MIN_TITLE = 2
        const val MAX_TITLE = 40
        const val MAX_TEXT = 300
        const val MIN_MEMBERS = 2
        const val MAX_MEMBERS = 200
        const val DEFAULT_MEMBERS = 50
        const val MIN_AGE = 18
        const val MAX_AGE = 100
        const val MAX_ANNOUNCEMENT = 200
        const val CODE_LENGTH = 6
        /** Icebreaker prompts are numbered 1..40 (the text is in each app's own language). */
        const val PROMPTS = 40
    }
}

/** A room I'm opening (I host it, off the stage until I join the voice). The server validates every field again. */
data class NewVoiceRoom(val title: String, val category: RoomCategory, val rules: RoomRules = RoomRules(), val announcement: String = "")

data class CreatedVoiceRoom(val id: String, val code: String)

/** A Ludo winner of the room (top 5). */
data class RoomLeader(val profile: Profile, val wins: Int)

/** Reactions are drawn glyphs (ui/TopicIcons.kt), never emoji. */
enum class VoiceReaction(val code: String) {
    HEART("heart"), LAUGH("laugh"), FIRE("fire"), CLAP("clap"), WOW("wow");

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code }
    }
}

enum class VoiceSystemEvent(val code: String) {
    JOINED("joined"), LEFT("left"), SEAT("seat"), AUDIENCE("audience"), COHOST("cohost"), HOST("host"), TRACK("track"), REMOVED("removed");

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code }
    }
}

/** A chat line under the stage. System lines carry a typed payload and are written in the reader's language. */
sealed interface VoiceLine {
    val id: Long
    val at: Long

    data class Text(override val id: Long, val sender: Profile, val fromMe: Boolean, val text: String, override val at: Long) : VoiceLine
    data class System(
        override val id: Long, val event: VoiceSystemEvent, val name: String, override val at: Long,
        val seat: Int? = null, val title: String? = null,
    ) : VoiceLine
    /** A conversation starter: prompt 1..40, shown in the reader's language. */
    data class Icebreaker(override val id: Long, val prompt: Int, val name: String, val fromMe: Boolean, override val at: Long) : VoiceLine
    /** A photo (checked on the sender's phone; shown blurred until tapped). */
    data class Photo(override val id: Long, val sender: Profile, val fromMe: Boolean, val url: String, override val at: Long) : VoiceLine
}

enum class AudioStatus { CONNECTING, CONNECTED, RECONNECTING, FAILED }

/** The room's live audio (live server only; the demo has none). [interrupted]: another app (a phone call) took the audio. */
data class VoiceAudioState(val status: AudioStatus, val interrupted: Boolean = false)

sealed interface VoiceEvent {
    data class RoomChanged(val room: VoiceRoom) : VoiceEvent
    /** Null: no audio connection (nobody is on the stage, so the room is a chat room for now). */
    data class Audio(val audio: VoiceAudioState?) : VoiceEvent
    /** Audio level 0..1 per user id, several times a second (absent = silent). */
    data class Levels(val levels: Map<String, Float>) : VoiceEvent
    data class Line(val line: VoiceLine) : VoiceEvent
    data class Reaction(val userId: String, val kind: VoiceReaction) : VoiceEvent
    data object Ended : VoiceEvent
    data object Removed : VoiceEvent
    /**
     * The server no longer lets me be here (the room filled while I was away, I was banned, the rules changed, ...):
     * [code] says why. Said once; the collector leaves the room with that reason.
     */
    data class Refused(val code: String) : VoiceEvent
}

sealed interface VoiceAction {
    data class Hand(val up: Boolean) : VoiceAction
    data class Mic(val on: Boolean) : VoiceAction
    /** Take this free seat (open rooms, host and co-hosts); in a hand-raise room it raises my hand. */
    data class TakeSeat(val seat: Int) : VoiceAction
    data object Unseat : VoiceAction
    data class React(val kind: VoiceReaction) : VoiceAction
    // host and co-hosts
    data class Invite(val userId: String, val seat: Int? = null) : VoiceAction
    data class Answer(val userId: String, val accept: Boolean) : VoiceAction
    data class ToAudience(val userId: String) : VoiceAction
    data class Mute(val userId: String) : VoiceAction
    data class Lock(val seat: Int, val locked: Boolean) : VoiceAction
    // host only
    data class Cohost(val userId: String, val on: Boolean) : VoiceAction
    data class Kick(val userId: String) : VoiceAction
    // music: anyone adds a song request and votes; the host (DJ) plays, removes and controls
    /** [title]: the video's real title (YouTube oEmbed), empty when it couldn't be looked up. */
    data class AddTrack(val videoId: String, val title: String = "") : VoiceAction
    data class Vote(val itemId: String, val on: Boolean) : VoiceAction
    data class Play(val itemId: String) : VoiceAction
    data class RemoveItem(val itemId: String) : VoiceAction
    data object Pause : VoiceAction
    data object Resume : VoiceAction
    /** The most voted request plays. [trackId]: the track being skipped (a player that ended); nothing happens if another one already plays. */
    data class Skip(val trackId: String? = null) : VoiceAction
}

object YouTube {
    private val ID = Regex("^[\\w-]{11}$")
    private val PATH = Regex("^/(?:shorts|embed|live|v)/([^/?#]+)")

    /** The 11-character video id of a YouTube link (watch?v=, youtu.be/, shorts/, embed/, live/, music.youtube.com). */
    fun videoId(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val uri = runCatching { URI(if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(text)) text else "https://$text") }.getOrNull()
            ?: return null
        val host = (uri.host ?: return null).lowercase().replace(Regex("^(www|m|music)\\."), "")
        val path = uri.rawPath.orEmpty()
        val id = when (host) {
            "youtu.be" -> path.split('/').getOrNull(1)
            "youtube.com", "youtube-nocookie.com" ->
                if (path == "/watch") uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.substringAfter("v=")
                else PATH.find(path)?.groupValues?.get(1)
            else -> null
        }
        return id?.takeIf { ID.matches(it) }
    }

    fun thumbnail(videoId: String) = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"

    /** "Song – Film · Artist" titles carry the artist after the last " · ": the song part. */
    fun songOf(title: String) = if (" · " in title) title.substringBeforeLast(" · ") else title

    /** The artist part of such a title, or "". */
    fun artistOf(title: String) = if (" · " in title) title.substringAfterLast(" · ") else ""
}
