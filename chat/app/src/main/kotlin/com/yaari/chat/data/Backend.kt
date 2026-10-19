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
    /** Live updates for one chat. Collect while the chat is on screen. */
    fun events(matchId: String): Flow<MatchEvent>
    suspend fun history(matchId: String): List<Message>
    /** "I am typing" signal to the other person; arrives as [MatchEvent.Typing]. Best effort. */
    suspend fun sendTyping(matchId: String)
    suspend fun send(matchId: String, text: String?, media: Media?): Message
    suspend fun leave(matchId: String)
    /** Always allowed: blocks the person and ends any open chat with them. */
    suspend fun report(partnerId: String, reason: ReportReason, matchId: String?)

    suspend fun requestVideo(matchId: String)
    suspend fun answerVideo(matchId: String, accept: Boolean)
    suspend fun endVideo(matchId: String)
    /** Null when the build has no real video (demo). */
    suspend fun videoToken(matchId: String): VideoToken?

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
    suspend fun roomHistory(roomId: String): List<RoomMessage>
    /** New messages in a room while it is open. */
    fun roomEvents(roomId: String): Flow<RoomMessage>
    suspend fun sendRoom(roomId: String, text: String): RoomMessage
}
