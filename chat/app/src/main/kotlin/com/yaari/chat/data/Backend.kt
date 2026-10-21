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
import com.yaari.chat.model.CreatedVoiceRoom
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.VoicePoll
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceEvent
import com.yaari.chat.model.VoiceLine
import com.yaari.chat.model.VoiceRoomSummary
import com.yaari.chat.model.Badges
import com.yaari.chat.model.InboxItem
import com.yaari.chat.model.Interest
import com.yaari.chat.model.RoomQuery
import com.yaari.chat.model.Comment
import com.yaari.chat.model.FeedTab
import com.yaari.chat.model.LikeState
import com.yaari.chat.model.PostVisibility
import com.yaari.chat.model.ProfileStyle
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
    /** Someone's profile with posts and style; null if not visible (never matched, not a public poster, or blocked). */
    suspend fun profile(userId: String): Profile?
    /** [visibility]: friends (the default) or everyone. 20 a day (slow_down). */
    suspend fun addPost(text: String, imageUri: String?, visibility: PostVisibility = PostVisibility.FRIENDS): Post
    /** My own post only (as before: the posts table's delete rule); its likes and comments go with it. */
    suspend fun deletePost(postId: String)
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

    /**
     * Live rooms I may see (private ones only when mine or entered with the code): mine first, then the most people
     * present. [query] narrows them on the server (title words, the room's language).
     */
    suspend fun voiceRooms(query: RoomQuery = RoomQuery()): List<VoiceRoomSummary>
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
    /** Host only: the title, the pinned announcement (empty removes it) and the language (null keeps it). */
    suspend fun updateVoiceRoom(roomId: String, title: String, announcement: String, lang: String? = null)
    /** Tells a friend about the room I'm in (their Notifications, and a push). Refused with room_closed, not_allowed, slow_down. */
    suspend fun inviteToRoom(roomId: String, userId: String)
    /** Opens an invite ([InboxItem.id]): lets me into the room even when it is private; returns its id (then [voiceSession]). */
    suspend fun joinInvite(inboxId: Long): String
    /** People with the app open right now (a server count, at most 10 s old). */
    suspend fun onlineCount(): Int
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
    /** A chat line (voice_send2); [replyTo]: the line it answers (a text, photo or poll line of this room). */
    suspend fun sendVoice(roomId: String, text: String, replyTo: Long? = null): VoiceLine
    /**
     * Sends a photo: [jpegUri] is the picked image already shrunk to 1280 px JPEG and checked for nudity on this phone
     * ([PhotoCheck]). 5 a minute (slow_down).
     */
    suspend fun sendVoicePhoto(roomId: String, jpegUri: String): VoiceLine
    /** Posts a conversation starter (one per room every 20 s: slow_down). */
    suspend fun voiceIcebreaker(roomId: String): VoiceLine
    /**
     * Host or co-host: asks the room [question] (1–120 characters) with 2–4 [options] (1–40, all different); returns
     * the poll's chat line. Closes the poll open before it. Refused with bad_request, not_allowed, slow_down.
     */
    suspend fun createVoicePoll(roomId: String, question: String, options: List<String>): VoiceLine
    /** My answer ([choice] 0-based; again changes it while the poll is open). Refused with poll_closed, not_found, bad_request. */
    suspend fun voicePollVote(pollId: Long, choice: Int): VoicePoll
    /** The host, a co-host or who asked: no more answers. not_allowed, not_found. */
    suspend fun closeVoicePoll(pollId: Long): VoicePoll
    /** Polls of lines loaded from the history (at most 50 ids; unknown or unreadable ones are left out). */
    suspend fun voicePolls(ids: List<Long>): List<VoicePoll>
    /** A YouTube video's real title (oEmbed, 5 s at most); "" when it can't be looked up. Never throws. */
    suspend fun videoTitle(videoId: String): String
    /** Throws not_allowed when my role may not do it; mic_denied when the microphone can't be used. */
    suspend fun voiceAction(roomId: String, action: VoiceAction)
    /** Reported through its host (the report keeps what the host wrote in it). */
    suspend fun reportVoiceRoom(roomId: String, reason: ReportReason)

    // ---- Notifications and unread counts (023_notifications.sql) ----

    /** My notifications (inbox2: with the post of a like or comment), newest first, 30 at a time ([before]: the oldest id shown). */
    suspend fun inbox(before: Long? = null): List<InboxItem>
    /** Marks these items (null: all of them) read. */
    suspend fun markInboxRead(ids: List<Long>? = null)
    /** Unread counts; asking also tells the server the app is open (no pushes meanwhile). */
    suspend fun badges(): Badges
    /** Emits when one of my notifications arrives or changes (and once connected), while collected. Best effort. */
    fun inboxSignals(): Flow<Unit>
    /** This phone's push token (FCM), with the app's language and time zone (quiet hours are in local time). */
    suspend fun registerDevice(token: String, locale: String, timeZone: String)
    /** No more pushes to this phone (sign-out). */
    suspend fun unregisterDevice(token: String)

    // ---- Profile extras (024_discovery_profiles.sql) ----

    /** None, or [Interest.MIN] to [Interest.MAX] tags (bad_request otherwise); returns my profile. */
    suspend fun setInterests(tags: List<Interest>): Profile
    /** Adds a photo to my profile ([jpegUri]: already shrunk and checked on this phone, [PhotoCheck]); at most 4 (not_allowed). */
    suspend fun addProfilePhoto(jpegUri: String): Profile
    suspend fun removeProfilePhoto(id: String): Profile

    // ---- Profile style, posts' likes and comments, the feed (033_profiles_feed.sql) ----

    /**
     * Sets all of my profile's style at once (set_profile_style). [newCoverJpeg]: a picked cover already shrunk and
     * checked on this phone ([PhotoCheck]), stored in my "cover" folder; null keeps [ProfileStyle.coverId] (null: no
     * cover). bad_request when something is out of bounds. Returns my profile.
     */
    suspend fun setProfileStyle(style: ProfileStyle, newCoverJpeg: String? = null): Profile
    /** Newest first, 20 at a time; [before]: the oldest post already shown. Friends: theirs and mine; Discover: public ones. */
    suspend fun feed(tab: FeedTab, before: Post? = null): List<Post>
    /** One person's posts I may see, newest first ([before] as in [feed]). */
    suspend fun postsOf(userId: String, before: Post? = null): List<Post>
    /** One post with its author and counts; null when it is gone or not for me. */
    suspend fun post(postId: String): Post?
    /** Heart on or off; not_found when the post is gone. */
    suspend fun likePost(postId: String, on: Boolean): LikeState
    /** Oldest first, 50 at a time ([after]: the newest comment id already shown). */
    suspend fun comments(postId: String, after: Long? = null): List<Comment>
    /** 1–300 characters; [replyTo]: the comment it answers. 10 a minute (slow_down); not_found, bad_request. */
    suspend fun addComment(postId: String, body: String, replyTo: Long? = null): Comment
    /** Mine, or any under my post. */
    suspend fun removeComment(commentId: Long)
    suspend fun reportPost(postId: String, reason: ReportReason)
    suspend fun reportComment(commentId: Long, reason: ReportReason)
}
