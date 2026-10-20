package com.yaari.chat.model

import java.net.URI

// ---- Voice rooms (X Spaces-style party rooms: a stage of seats, an audience, chat and shared music) ------------
// Same shapes as the web (chat/web/lib/backend.ts); the server is chat/server/migrations/017_voice_rooms.sql.

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

data class VoiceQueueItem(
    val id: String, val videoId: String, val title: String, val addedBy: String,
    /** A listener's suggestion the host has not approved yet. */
    val pending: Boolean,
)

data class VoiceRoomSummary(
    val id: String, val title: String, val category: RoomCategory, val host: Profile,
    /** Up to 4 people on the stage, the host first. */
    val speakers: List<Profile>,
    val listeners: Int, val nowPlaying: String?, val mine: Boolean,
)

data class VoiceRoom(
    val id: String, val title: String, val category: RoomCategory, val mode: SpeakMode,
    val hostId: String, val meId: String,
    val members: List<VoiceMember>,
    val seats: List<VoiceSeat>,
    val track: VoiceTrack?,
    val queue: List<VoiceQueueItem>,
) {
    fun member(userId: String) = members.firstOrNull { it.profile.id == userId }
    val me get() = member(meId)
    val audience get() = members.filter { it.seat == null }
    fun bySeat(seat: Int) = members.firstOrNull { it.seat == seat }
    fun locked(seat: Int) = seats.getOrNull(seat)?.locked == true

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
    }
}

data class NewVoiceRoom(val title: String, val category: RoomCategory, val mode: SpeakMode)

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
}

enum class AudioStatus { CONNECTING, CONNECTED, RECONNECTING, FAILED }

/** The room's live audio (live server only; the demo has none). [interrupted]: another app (a phone call) took the audio. */
data class VoiceAudioState(val status: AudioStatus, val interrupted: Boolean = false)

sealed interface VoiceEvent {
    data class RoomChanged(val room: VoiceRoom) : VoiceEvent
    data class Audio(val audio: VoiceAudioState) : VoiceEvent
    /** Audio level 0..1 per user id, several times a second (absent = silent). */
    data class Levels(val levels: Map<String, Float>) : VoiceEvent
    data class Line(val line: VoiceLine) : VoiceEvent
    data class Reaction(val userId: String, val kind: VoiceReaction) : VoiceEvent
    data object Ended : VoiceEvent
    data object Removed : VoiceEvent
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
    // music: the host (DJ) adds and controls; everyone else suggests
    data class AddTrack(val videoId: String) : VoiceAction
    data class Suggest(val videoId: String) : VoiceAction
    data class Approve(val itemId: String, val accept: Boolean) : VoiceAction
    data class Play(val itemId: String) : VoiceAction
    data class RemoveItem(val itemId: String) : VoiceAction
    data object Pause : VoiceAction
    data object Resume : VoiceAction
    /** [trackId]: the track being skipped (a player that ended); nothing happens if another one already plays. */
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
}
