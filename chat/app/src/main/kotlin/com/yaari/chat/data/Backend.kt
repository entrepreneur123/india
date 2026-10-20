package com.yaari.chat.data

import com.yaari.chat.model.Friend
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Gender
import com.yaari.chat.model.CreatedRoom
import com.yaari.chat.model.Media
import com.yaari.chat.model.Message
import com.yaari.chat.model.NewRoom
import com.yaari.chat.model.Post
import com.yaari.chat.model.Profile
import com.yaari.chat.model.RecentMatch
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.Room
import com.yaari.chat.model.RoomMessage
import com.yaari.chat.model.Target
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

    /** Official rooms first, then live public user rooms, plus open rooms I own or am in. */
    suspend fun rooms(): List<Room>
    /**
     * Enter a room, and stay listed as present: call again every 30 s while it is open. Refused with
     * room_closed, room_gender, room_age, room_full or not_found (private rooms are entered with their code first).
     */
    suspend fun joinRoom(roomId: String)
    /** Opens a user room (closing my previous open one); I am already in it. */
    suspend fun createRoom(room: NewRoom): CreatedRoom
    /** Finds a room by its 6-character code (any case) and enters it; returns the room id. */
    suspend fun joinRoomCode(code: String): String
    /** Owner only: nobody can enter or write any more. */
    suspend fun closeRoom(roomId: String)
    suspend fun leaveRoom(roomId: String)
    /** The newest 100 messages, oldest first; with [afterId], up to 200 messages newer than it (for re-syncs). */
    suspend fun roomHistory(roomId: String, afterId: Long? = null): List<RoomMessage>
    /** New messages in a room while it is open. [afterId]: the newest message id already shown. */
    fun roomEvents(roomId: String, afterId: Long? = null): Flow<RoomMessage>
    suspend fun sendRoom(roomId: String, text: String): RoomMessage
}
