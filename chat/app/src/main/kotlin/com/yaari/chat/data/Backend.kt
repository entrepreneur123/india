package com.yaari.chat.data

import com.yaari.chat.model.Friend
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Media
import com.yaari.chat.model.Message
import com.yaari.chat.model.Post
import com.yaari.chat.model.Profile
import com.yaari.chat.model.RecentMatch
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.Target
import com.yaari.chat.model.LudoAction
import com.yaari.chat.model.LudoGame
import com.yaari.chat.model.CreatedVoiceRoom
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.RoomLeader
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceEvent
import com.yaari.chat.model.VoiceLine
import com.yaari.chat.model.VoiceRoomSummary
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class Match(val id: String, val partner: Profile)

enum class VideoStatus { NONE, REQUESTED_BY_ME, REQUESTED_BY_PARTNER, ACCEPTED, DECLINED }

sealed interface MatchEvent {
    data class NewMessage(val message: Message) : MatchEvent
    data class Typing(val on: Boolean) : MatchEvent
    data class Video(val status: VideoStatus) : MatchEvent
    data object PartnerLeft : MatchEvent
    /** The partner has read my messages up to and including id [upto] (only ever grows). */
    data class Read(val upto: Long) : MatchEvent
}

data class Entitlements(val premium: Boolean = false, val genderPass: Boolean = false) {
    val canChooseGender get() = premium || genderPass
}

data class NewProfile(
    val name: String, val gender: Gender, val birthDate: LocalDate,
    val city: String, val bio: String, val photoUri: String?,
)

data class VideoToken(val url: String, val token: String)

/** Server-side rule violations; [code] matches the database error (exit_locked, premium_required, banned, ...). */
class BackendException(val code: String) : Exception(code)

/** The server paired us ([matchId]) but the match can't be shown; the caller ends it once the exit lock allows. */
class OrphanMatchException(val matchId: String) : Exception("orphan_match")

object Products {
    const val PREMIUM = "yaari_premium_monthly"
    const val GENDER_PASS = "gender_pass_24h"
}

/**
 * Everything the app needs from the server. `demo` builds use [FakeBackend]; `live` builds use Supabase
 * (server/migrations) with LiveKit video. The server re-checks every rule the app enforces.
 */
interface Backend {
    /** [phone] is the full international number in digits, country code first ("919876543210"). */
    suspend fun sendOtp(phone: String)
    suspend fun verifyOtp(phone: String, code: String): Boolean
    suspend fun isSignedIn(): Boolean
    /** Null until the profile is created. */
    suspend fun myProfile(): Profile?
    suspend fun createProfile(p: NewProfile): Profile
    suspend fun entitlements(): Entitlements
    suspend fun redeem(productId: String, purchaseToken: String): Entitlements
    suspend fun signOut()
    /** Permanently deletes the account, profile, posts, chats and media (Google Play requirement). */
    suspend fun deleteAccount()
    /** A funnel step reached, for the team's diagnostics (log_events). Best effort, never throws; none in the demo. */
    fun track(step: Funnel) {}

    /** One matching attempt. Null = queued; call again every couple of seconds. */
    suspend fun findMatch(target: Target): Match?
    suspend fun cancelSearch()
    /**
     * Emits when the server may have just paired me (someone matched me while I was waiting), so the search polls at
     * once instead of after its delay. Best effort: polling stays the source of truth. Collect while searching.
     */
    fun matchSignals(): Flow<Unit>
    /**
     * Live updates for one chat. Collect while the chat is on screen. [live]: a random chat (not a friend thread),
     * where the partner's connection is watched and a partner who never connects or drops for good ends the chat
     * ([MatchEvent.PartnerLeft]). [afterId]: the newest message id already shown; the re-syncs fetch only newer ones.
     */
    fun events(matchId: String, live: Boolean, afterId: Long? = null): Flow<MatchEvent>
    /** The newest 200 messages, oldest first; with [afterId], up to 200 messages newer than it (for re-syncs). */
    suspend fun history(matchId: String, afterId: Long? = null): List<Message>
    /** "I am typing" signal to the other person; arrives as [MatchEvent.Typing]. Best effort. */
    suspend fun sendTyping(matchId: String)
    suspend fun send(matchId: String, text: String?, media: Media?): Message
    /** I have read the partner's messages up to id [upto]; arrives on their side as [MatchEvent.Read]. Best effort. */
    suspend fun markRead(matchId: String, upto: Long)
    suspend fun leave(matchId: String)
    /** Always allowed: blocks the person and ends any open chat with them. */
    suspend fun report(partnerId: String, reason: ReportReason, matchId: String?)
    /**
     * Block without reporting (Play UGC policy): never matched or shown to each other again, any open chat between
     * us ends, and a friendship is removed. Always allowed.
     */
    suspend fun block(userId: String)

    suspend fun requestVideo(matchId: String)
    suspend fun answerVideo(matchId: String, accept: Boolean)
    suspend fun endVideo(matchId: String)
    /** Null when the build has no real video (demo). */
    suspend fun videoToken(matchId: String): VideoToken?
    /**
     * The on-device check hid the partner's video in this call ([watchNudity]): tells the safety team. Once per call,
     * best effort (never throws, never blocks the call).
     */
    suspend fun flagNudity(matchId: String)

    suspend fun recents(): List<RecentMatch>
    /** Someone's profile with posts; null if not visible (never matched, or blocked). */
    suspend fun profile(userId: String): Profile?
    suspend fun addPost(text: String, imageUri: String?): Post
    /** Gender and birth date never change. [newPhotoUri] is a picked image, or null to keep the current photo. */
    suspend fun updateProfile(name: String, city: String, bio: String, newPhotoUri: String?): Profile

    /** Friends and pending requests either way. */
    suspend fun friends(): List<Friend>
    /** Sends a request, or accepts theirs if they already asked. Returns the new state. */
    suspend fun requestFriend(userId: String): FriendState
    suspend fun answerFriend(userId: String, accept: Boolean)
    /** Unfriend, or withdraw my request. */
    suspend fun removeFriend(userId: String)
    /** The chat to message a friend in (their last chat, or a new private one). */
    suspend fun friendThread(userId: String): String

    // ---- rooms (017_voice_rooms.sql, 019_unified_rooms.sql; audio through LiveKit in the live build, and only
    // while someone is on the stage) ----

    /** Live rooms I may see (private ones only when mine or entered with the code): mine first, then the server's order. */
    suspend fun voiceRooms(): List<VoiceRoomSummary>
    /**
     * Opens a room with me as host, not on the stage yet (ending my previous one). Refused with room_title, room_size,
     * bad_request, slow_down.
     */
    suspend fun createVoiceRoom(room: NewVoiceRoom): CreatedVoiceRoom
    /**
     * Finds a live room by its 6-character code (any case) and enters it; returns its id. not_found for a wrong code
     * (10 misses in 10 minutes and code entry pauses: slow_down), or the room's refusal.
     */
    suspend fun joinVoiceCode(code: String): String
    /** Host only: the title and the pinned announcement (empty removes it). */
    suspend fun updateVoiceRoom(roomId: String, title: String, announcement: String)
    /**
     * Enters a room (as a listener, or as the host of my own) and follows it while collected: the first event is
     * [VoiceEvent.RoomChanged]; then every change, chat line, reaction, audio level and the end. Fails at once with
     * room_closed, not_found, voice_removed, not_allowed or the room's rules (room_gender, room_age, room_full).
     * Cancelling stops following; [leaveVoice] leaves.
     */
    fun voiceSession(roomId: String): Flow<VoiceEvent>
    suspend fun leaveVoice(roomId: String)
    /** Host only: everyone is moved out and the room closes. */
    suspend fun endVoice(roomId: String)
    /** The newest 100 chat and system lines, oldest first (only readable after entering). */
    suspend fun voiceLines(roomId: String): List<VoiceLine>
    suspend fun sendVoice(roomId: String, text: String): VoiceLine
    /**
     * Sends a photo: [jpegUri] is the picked image already shrunk to 1280 px JPEG and checked for nudity on this phone
     * ([PhotoCheck]). 5 a minute (slow_down).
     */
    suspend fun sendVoicePhoto(roomId: String, jpegUri: String): VoiceLine
    /** Posts a conversation starter (one per room every 20 s: slow_down). */
    suspend fun voiceIcebreaker(roomId: String): VoiceLine
    /** The room's Ludo winners, top 5. */
    suspend fun voiceLeaders(roomId: String): List<RoomLeader>
    /** A YouTube video's real title (oEmbed, 5 s at most); "" when it can't be looked up. Never throws. */
    suspend fun videoTitle(videoId: String): String
    /** Throws not_allowed when my role may not do it; mic_denied when the microphone can't be used. */
    suspend fun voiceAction(roomId: String, action: VoiceAction)
    /** Reported through its host (the report keeps what the host wrote in it). */
    suspend fun reportVoiceRoom(roomId: String, reason: ReportReason)

    // ---- Ludo in a voice room (018_ludo.sql; rules in domain/Ludo.kt) ----

    /** The room's game: the one open, else one that ended in the last 5 minutes (its result), else null. */
    suspend fun ludoGame(roomId: String): LudoGame?
    /** Every change to the room's games (a new game too) while collected; re-reads after a dropped connection. */
    fun ludoGames(roomId: String): Flow<LudoGame>
    /**
     * Does [action] in the room's game [gameId] (null to open one) and returns the game as it now is. Refused with
     * not_allowed, stale (the screen showed an older version), not_your_turn, seat_taken, game_full, need_players.
     */
    suspend fun ludoAction(roomId: String, gameId: String?, action: LudoAction): LudoGame?
}
