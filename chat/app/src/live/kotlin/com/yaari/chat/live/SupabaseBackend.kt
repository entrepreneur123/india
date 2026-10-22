package com.yaari.chat.live

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.yaari.chat.AppLocale
import com.yaari.chat.domain.TranslateKind
import com.yaari.chat.domain.Translation
import com.yaari.chat.BuildConfig
import com.yaari.chat.data.AppForeground
import com.yaari.chat.data.Backend
import com.yaari.chat.data.awaitVisible
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.ChangeOrder
import com.yaari.chat.data.Funnel
import com.yaari.chat.data.Telemetry
import com.yaari.chat.data.roomRefusal
import com.yaari.chat.data.Entitlements
import com.yaari.chat.data.Match
import com.yaari.chat.data.MatchEvent
import com.yaari.chat.data.NewProfile
import com.yaari.chat.data.OrphanMatchException
import com.yaari.chat.data.PartnerWatch
import com.yaari.chat.data.VideoStatus
import com.yaari.chat.data.VideoToken
import com.yaari.chat.model.Friend
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Gender
import com.yaari.chat.model.GenderRule
import com.yaari.chat.model.Media
import com.yaari.chat.model.MediaKind
import com.yaari.chat.model.Message
import com.yaari.chat.model.Post
import com.yaari.chat.model.Profile
import com.yaari.chat.model.RecentMatch
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.Target
import com.yaari.chat.data.ServerClock
import com.yaari.chat.model.AudioStatus
import com.yaari.chat.model.CreatedVoiceRoom
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.RoomRules
import com.yaari.chat.model.SpeakMode
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.LinkPreview
import com.yaari.chat.model.Links
import com.yaari.chat.model.RoomStage
import com.yaari.chat.model.VoiceAudioState
import com.yaari.chat.model.VoiceEvent
import com.yaari.chat.model.VoiceLine
import com.yaari.chat.model.VoiceMember
import com.yaari.chat.model.VoicePoll
import com.yaari.chat.model.VoiceReply
import com.yaari.chat.model.VoiceQueueItem
import com.yaari.chat.model.VoiceReaction
import com.yaari.chat.model.VoiceRole
import com.yaari.chat.model.VoiceRoom
import com.yaari.chat.model.VoiceRoomSummary
import com.yaari.chat.model.VoiceSeat
import com.yaari.chat.model.VoiceSystemEvent
import com.yaari.chat.model.VoiceTrack
import com.yaari.chat.model.Badges
import com.yaari.chat.model.InboxItem
import com.yaari.chat.model.InboxKind
import com.yaari.chat.model.Interest
import com.yaari.chat.model.ProfilePhoto
import com.yaari.chat.model.RoomLang
import com.yaari.chat.model.RoomQuery
import com.yaari.chat.model.Accent
import com.yaari.chat.model.Comment
import com.yaari.chat.model.FeedTab
import com.yaari.chat.model.LikeState
import com.yaari.chat.model.PostRef
import com.yaari.chat.model.PostVisibility
import com.yaari.chat.model.ProfilePrompt
import com.yaari.chat.model.ProfileStyle
import com.yaari.chat.model.PromptQ
import com.yaari.chat.model.SpokenLang
import com.yaari.chat.model.KycAttempt
import com.yaari.chat.model.KycResult
import com.yaari.chat.model.MannerTag
import com.yaari.chat.model.MyTrust
import com.yaari.chat.model.TagCount
import com.yaari.chat.model.Trust
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.addJsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.SettingsCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.LocalDate
import java.time.Period
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/** Live server: Supabase project "yaari-chat" (Mumbai). All rules are re-checked in Postgres (server/migrations). */
class SupabaseBackend(private val app: Application, persistSession: Boolean = true) : Backend {
    private val client = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
        // Release builds write nothing to Logcat: requests, sessions and phone numbers must not end up in device logs.
        defaultLogLevel = if (BuildConfig.DEBUG) LogLevel.WARNING else LogLevel.NONE
        // A request that hangs on a weak network fails (as "network") instead of freezing the search or a send;
        // media uploads get longer (see upload). The realtime socket is not affected.
        requestTimeout = REQUEST_TIMEOUT_MS.milliseconds
        install(Auth) {
            // Explicit storage: does not depend on the androidx-startup initializer having run.
            if (persistSession) {
                val prefs = SharedPreferencesSettings(app.getSharedPreferences("yaari_auth", Context.MODE_PRIVATE))
                sessionManager = SettingsSessionManager(prefs)
                codeVerifierCache = SettingsCodeVerifierCache(prefs)
            } else {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
            }
        }
        install(Postgrest)
        install(Realtime)
        install(Storage)
        install(Functions)
    }
    private val json = Json { ignoreUnknownKeys = true }
    /** Signed media URL and when to stop reusing it (they are valid for 12 h). */
    private val signedUrls = ConcurrentHashMap<String, Pair<String, Long>>()
    private val typingChannels = ConcurrentHashMap<String, RealtimeChannel>()
    /** The newest read position sent per match, so scrolling or re-syncs don't repeat the same request. */
    private val readSent = ConcurrentHashMap<String, Long>()
    /** Fire-and-forget requests (voice-sync, diagnostics) that must not die with the screen that started them. */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Crash reports from the last run and the funnel steps (021: log_events). */
    private val telemetry = Telemetry(reportsDir(app), VERSION, background, ::logEvents, AppForeground.visible).also { it.sendSaved() }

    @Serializable private data class ProfileRow(
        val id: String, val name: String, val gender: String, @SerialName("birth_date") val birthDate: String,
        val city: String = "", val bio: String = "", @SerialName("photo_path") val photoPath: String? = null,
        val country: String? = null, val interests: List<String> = emptyList(), val photos: List<String> = emptyList(),
    )
    /** profile_extras (024): interests and the gallery of people I may see. */
    @Serializable private data class ExtrasRow(val id: String, val interests: List<String>? = null, val photos: List<String>? = null)
    @Serializable private data class InboxRow(
        val id: Long, val kind: String, val n: Int = 1, @SerialName("created_at") val createdAt: String,
        @SerialName("read_at") val readAt: String? = null, @SerialName("match_id") val matchId: String? = null,
        @SerialName("room_id") val roomId: String? = null, @SerialName("room_title") val roomTitle: String? = null,
        @SerialName("room_open") val roomOpen: Boolean = false, val pending: Boolean = false, val actor: VoiceCardRow,
        val post: PostRefRow? = null,
    )
    // ---- 033_profiles_feed.sql ----
    @Serializable private data class PostRefRow(val id: String, val body: String = "", @SerialName("image_path") val imagePath: String? = null)
    @Serializable private data class AuthorRow(
        val id: String, val name: String, val gender: String = "other", val age: Int = 0, val city: String? = null,
        val country: String? = null, @SerialName("photo_path") val photoPath: String? = null, val accent: String? = null,
        val verified: Boolean = false,
    )
    @Serializable private data class FeedPostRow(
        val id: String, val body: String = "", @SerialName("image_path") val imagePath: String? = null,
        @SerialName("created_at") val createdAt: String, val visibility: String = "friends", val likes: Int = 0, val comments: Int = 0,
        val liked: Boolean = false, val author: AuthorRow? = null,
    )
    @Serializable private data class CommentRow(
        val id: Long, @SerialName("post_id") val postId: String, val body: String, @SerialName("created_at") val createdAt: String,
        @SerialName("reply_to") val replyTo: Long? = null, @SerialName("reply_name") val replyName: String? = null,
        val mine: Boolean = false, @SerialName("can_remove") val canRemove: Boolean = false, val author: AuthorRow,
    )
    @Serializable private data class LikeRow(val likes: Int = 0, val liked: Boolean = false)
    @Serializable private data class PromptRow(val q: String, val a: String = "")
    @Serializable private data class StyleRow(
        val id: String, @SerialName("cover_path") val coverPath: String? = null, val accent: String? = null, val status: String? = null,
        val languages: List<String>? = null, val prompts: List<PromptRow>? = null,
    )
    @Serializable private data class BadgesRow(val inbox: Int = 0, val friends: Int = 0, val chats: Map<String, Int> = emptyMap())
    @Serializable private data class NewProfileRow(
        val id: String, val name: String, val gender: String, @SerialName("birth_date") val birthDate: String,
        val city: String, val bio: String, @SerialName("photo_path") val photoPath: String?,
    )
    @Serializable private data class ProfileEdit(val name: String, val city: String, val bio: String)
    @Serializable private data class PhotoEdit(@SerialName("photo_path") val photoPath: String)
    @Serializable private data class CardRow(
        val id: String, val name: String, val gender: String, val age: Int, val city: String, val bio: String,
        @SerialName("photo_path") val photoPath: String? = null, val country: String? = null,
    )
    @Serializable private data class RecentRow(
        @SerialName("match_id") val matchId: String, @SerialName("met_at") val metAt: String,
        @SerialName("last_body") val lastBody: String? = null,
        val id: String, val name: String, val gender: String, val age: Int, val city: String, val bio: String,
        @SerialName("photo_path") val photoPath: String? = null, val country: String? = null,
    )
    @Serializable private data class FriendRow(
        val state: String, @SerialName("match_id") val matchId: String? = null,
        val id: String, val name: String, val gender: String, val age: Int, val city: String, val bio: String,
        @SerialName("photo_path") val photoPath: String? = null, val country: String? = null,
    )
    @Serializable private data class PostRow(
        val id: String, @SerialName("user_id") val userId: String, val body: String,
        @SerialName("image_path") val imagePath: String? = null, @SerialName("created_at") val createdAt: String,
    )
    @Serializable private data class NewPostRow(val body: String, @SerialName("image_path") val imagePath: String?, val visibility: String)
    @Serializable private data class MatchRow(
        val id: String, @SerialName("user_a") val userA: String, @SerialName("user_b") val userB: String,
        @SerialName("ended_at") val endedAt: String? = null, @SerialName("ended_by") val endedBy: String? = null,
        @SerialName("video_status") val videoStatus: String = "none",
        @SerialName("video_requested_by") val videoRequestedBy: String? = null,
        // The last message id each side has read (015_read_receipts.sql).
        @SerialName("read_a") val readA: Long? = null, @SerialName("read_b") val readB: Long? = null,
    )
    @Serializable private data class MessageRow(
        val id: Long, @SerialName("match_id") val matchId: String, val sender: String, val body: String? = null,
        @SerialName("media_path") val mediaPath: String? = null, @SerialName("media_kind") val mediaKind: String? = null,
        @SerialName("created_at") val createdAt: String,
    )
    @Serializable private data class NewMessageRow(
        @SerialName("match_id") val matchId: String, val body: String?,
        @SerialName("media_path") val mediaPath: String?, @SerialName("media_kind") val mediaKind: String?,
    )
    @Serializable private data class EntRow(
        @SerialName("premium_until") val premiumUntil: String? = null, @SerialName("gender_until") val genderUntil: String? = null,
    )
    @Serializable private data class TokenRow(val url: String, val token: String)
    @Serializable private data class TranslateRow(val text: String = "", val same: Boolean = false)
    // ---- 035_identity_manners.sql, functions/kyc ----
    @Serializable private data class KycStartRow(val url: String)
    @Serializable private data class KycCheckRow(val result: String? = null)
    @Serializable private data class TagRow(val tag: String, val n: Int = 0)
    @Serializable private data class TrustRow(
        val id: String, val verified: Boolean = false, val manner: Int = 50, val ratings: Int = 0, val tags: List<TagRow> = emptyList(),
    )
    @Serializable private data class KycRow(val status: String = "", val result: String? = null, val at: String? = null)
    @Serializable private data class MyTrustRow(
        val id: String, val verified: Boolean = false, val manner: Int = 50, val ratings: Int = 0, val tags: List<TagRow> = emptyList(),
        val required: Boolean? = null, val kyc: KycRow? = null, val rated: List<String> = emptyList(),
    )

    private val uid get() = client.auth.currentUserOrNull()?.id ?: throw BackendException("not_signed_in")

    /** Maps database/function errors to the rule codes the app understands; timeouts and dropped connections are "network". */
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: RestException) {
        val text = listOfNotNull(e.message, e.error, e.description).joinToString(" ")
        throw BackendException(if ("row-level security" in text) "blocked" else CODES.firstOrNull { it in text } ?: "network")
    } catch (e: IOException) { // includes HttpRequestTimeoutException and supabase-kt's HttpRequestException
        throw BackendException("network")
    }

    // ---- account ----

    override suspend fun sendOtp(phone: String) = call { client.auth.signInWith(OTP) { this.phone = "+$phone" } }

    override suspend fun verifyOtp(phone: String, code: String): Boolean = runCatching {
        client.auth.verifyPhoneOtp(type = OtpType.Phone.SMS, phone = "+$phone", token = code)
    }.isSuccess

    /** Integration tests only (QA accounts); users sign in with phone OTP. */
    suspend fun signInWithEmailForTest(email: String, password: String) = client.auth.signInWith(Email) {
        this.email = email
        this.password = password
    }

    override suspend fun isSignedIn(): Boolean {
        client.auth.awaitInitialization()
        return client.auth.currentSessionOrNull() != null
    }

    override suspend fun myProfile(): Profile? = call {
        val row = client.postgrest["profiles"].select { filter { eq("id", uid) } }.decodeSingleOrNull<ProfileRow>() ?: return@call null
        val age = Period.between(LocalDate.parse(row.birthDate), LocalDate.now()).years
        val (posts, style) = coroutineScope {
            val posts = async { posts(row.id) }
            val style = async { style(row.id) }
            posts.await() to style.await()
        }
        val p = toProfile(CardRow(row.id, row.name, row.gender, age, row.city, row.bio, row.photoPath, row.country), posts)
        withExtras(p.copy(style = style ?: p.style), row.interests, row.photos)
    }

    /** Cover (signed), colour, status, languages and prompts of someone I may see (profile_style); null if it can't be read. */
    private suspend fun style(userId: String): ProfileStyle? = runCatching {
        val r = client.postgrest.rpc("profile_style", buildJsonObject { putJsonArray("p_users") { add(userId) } })
            .decodeList<StyleRow>().firstOrNull() ?: return@runCatching null
        ProfileStyle(
            cover = r.coverPath?.let { signed(it) }, coverId = r.coverPath, accent = Accent.of(r.accent), status = r.status?.ifBlank { null },
            languages = SpokenLang.of(r.languages),
            prompts = r.prompts.orEmpty().mapNotNull { q -> PromptQ.of(q.q)?.let { ProfilePrompt(it, q.a) } }.distinctBy { it.q }.take(ProfilePrompt.MAX),
        )
    }.getOrNull()

    /** Interests and up to 4 photos (signed in one request). */
    private suspend fun withExtras(p: Profile, interests: List<String>?, photos: List<String>?): Profile {
        val paths = photos.orEmpty().take(ProfilePhoto.MAX)
        signMany(paths)
        return p.copy(interests = Interest.of(interests), photos = paths.mapNotNull { id -> signedUrls[id]?.first?.let { ProfilePhoto(id, it) } })
    }

    private suspend fun extras(userId: String): ExtrasRow? = runCatching {
        client.postgrest.rpc("profile_extras", buildJsonObject { putJsonArray("p_users") { add(userId) } }).decodeList<ExtrasRow>().firstOrNull()
    }.getOrNull()

    override suspend fun createProfile(p: NewProfile): Profile = call {
        val photo = p.photoUri?.let { upload(it.toUri(), "avatar") }
        // Country is set by the server from the verified phone number.
        client.postgrest["profiles"].insert(
            NewProfileRow(uid, p.name, p.gender.name.lowercase(), p.birthDate.toString(), p.city, p.bio, photo),
        )
        (myProfile() ?: throw BackendException("network")).also { telemetry.track(Funnel.SIGNUP_DONE) }
    }

    override suspend fun entitlements(): Entitlements = call {
        val row = client.postgrest.rpc("my_entitlements").decodeList<EntRow>().firstOrNull() ?: return@call Entitlements()
        val now = Instant.now()
        Entitlements(
            premium = row.premiumUntil?.let { ts(it).isAfter(now) } == true,
            genderPass = row.genderUntil?.let { ts(it).isAfter(now) } == true,
        )
    }

    override suspend fun redeem(productId: String, purchaseToken: String): Entitlements = call {
        client.functions.invoke("verify-purchase", body = buildJsonObject {
            put("product_id", productId)
            put("purchase_token", purchaseToken)
        })
        entitlements()
    }

    override suspend fun signOut() = call { client.auth.signOut() }

    override fun track(step: Funnel) = telemetry.track(step)

    /** One batch of diagnostics (a JSON array): log_events signed in, log_events_anon before (one shared daily cap). */
    private suspend fun logEvents(batch: String) {
        client.auth.awaitInitialization()
        val events = json.parseToJsonElement(batch)
        val fn = if (client.auth.currentUserOrNull() != null) "log_events" else "log_events_anon"
        client.postgrest.rpc(fn, buildJsonObject { put("p_events", events) })
    }

    override suspend fun deleteAccount() = call {
        client.functions.invoke("delete-account")
        client.auth.clearSession() // the server already removed the user; only the local session is left
    }

    // ---- matching & chat ----

    /** find_match2 prefers a partner who uses the app in the same language for a few seconds, then anyone. */
    override suspend fun findMatch(target: Target): Match? = call {
        val raw = client.postgrest.rpc("find_match2", buildJsonObject {
            put("p_target", target.name.lowercase())
            put("p_lang", AppLocale.matchLanguage(app))
        }).data.trim()
        if (raw == "null" || raw.isEmpty()) return@call null
        val id = json.decodeFromString<String>(raw)
        try {
            // One request for the partner's card (match_card), no posts: the chat opens fast; the profile screen
            // loads the full profile with posts when opened. A photo link that fails to sign shows the initial.
            val c = client.postgrest.rpc("match_card", buildJsonObject { put("p_match", id) }).decodeList<CardRow>().firstOrNull()
                ?: throw BackendException("not_found")
            val photo = c.photoPath?.let { p -> runCatching { signed(p) }.getOrNull() }
            Match(id, toProfile(c.copy(photoPath = null), emptyList()).copy(photoUri = photo)).also { telemetry.track(Funnel.MATCH_FOUND) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The server already paired us; the app ends that chat (it knows whether the user opened it).
            throw OrphanMatchException(id)
        }
    }

    override suspend fun cancelSearch() = call { client.postgrest.rpc("cancel_search"); Unit }

    /** The waiting side of a new match is always user_a: its row arriving means "poll now". */
    override fun matchSignals(): Flow<Unit> = channelFlow {
        val me = uid
        val channel = client.channel("matches-$me-${UUID.randomUUID()}")
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>("public") {
            table = "matches"
            filter("user_a", FilterOperator.EQ, me)
        }
        launch { inserts.collect { send(Unit) } }
        try {
            channel.subscribe()
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { runCatching { client.realtime.removeChannel(channel) } }
        }
    }

    /**
     * True while this channel is joined over a connected socket. A dropped socket shows as false, and true again once
     * the channel has rejoined. After a reconnect the channel still reads SUBSCRIBED for a moment, until its rejoin
     * starts, so every "true" after the first one has to hold for a moment.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun RealtimeChannel.joined(): Flow<Boolean> {
        var first = true
        return combine(status, client.realtime.status) { c, r -> c == RealtimeChannel.Status.SUBSCRIBED && r == Realtime.Status.CONNECTED }
            .distinctUntilChanged()
            .transformLatest { up ->
                if (up && !first) delay(REJOIN_SETTLE_MS)
                if (up) first = false
                emit(up)
            }
            .distinctUntilChanged()
    }

    /** Null when the match is gone (the other person deleted their account). */
    private suspend fun matchRow(id: String) =
        client.postgrest["matches"].select { filter { eq("id", id) } }.decodeSingleOrNull<MatchRow>()

    private fun videoOf(m: MatchRow, me: String) = when (m.videoStatus) {
        "requested" -> if (m.videoRequestedBy == me) VideoStatus.REQUESTED_BY_ME else VideoStatus.REQUESTED_BY_PARTNER
        "accepted" -> VideoStatus.ACCEPTED
        "declined" -> VideoStatus.DECLINED
        else -> VideoStatus.NONE
    }

    override fun events(matchId: String, live: Boolean, afterId: Long?): Flow<MatchEvent> = channelFlow {
        val me = uid
        var lastVideo: VideoStatus? = null
        var partnerRead = 0L
        var leftSent = false
        suspend fun partnerLeft() {
            if (!leftSent) {
                leftSent = true
                send(MatchEvent.PartnerLeft)
            }
        }
        suspend fun onMatch(m: MatchRow?) {
            if (m == null) return partnerLeft() // deleted: treat as the partner leaving
            if (m.endedAt != null && m.endedBy != me) partnerLeft()
            val v = videoOf(m, me)
            if (v != lastVideo) {
                lastVideo = v
                send(MatchEvent.Video(v))
            }
            val read = (if (m.userA == me) m.readB else m.readA) ?: 0L
            if (read > partnerRead) {
                partnerRead = read
                send(MatchEvent.Read(read))
            }
        }
        // Random chats only: a partner who never connects, or drops and does not come back, ends the chat for both
        // (the server cannot tell; the other side may be gone for good). The leave may be refused (exit lock, already
        // ended): the chat shows as ended either way. All of this runs on the collector's thread.
        val watch = PartnerWatch(this) {
            runCatching { leave(matchId) }
            partnerLeft()
        }

        val channel = client.channel("match-$matchId-${UUID.randomUUID()}")
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>("public") {
            table = "messages"
            filter("match_id", FilterOperator.EQ, matchId)
        }
        val updates = channel.postgresChangeFlow<PostgresAction.Update>("public") {
            table = "matches"
            filter("id", FilterOperator.EQ, matchId)
        }
        // One bad record (or a failed media link) must not stop the chat: skip it, the re-sync retries.
        launch {
            inserts.collect { a ->
                runCatching { toMessage(a.decodeRecord<MessageRow>(), me) }.onSuccess {
                    if (!it.fromMe && live) watch.activity()
                    send(MatchEvent.NewMessage(it))
                }
            }
        }

        // Typing signals ride a broadcast channel shared by the two participants (not stored anywhere). Its presence
        // (key = user id, state {"user": id}) tells whether the partner is connected; the web uses the same topic.
        val typingChannel = client.channel("typing-$matchId") { presence { key = me } }
        typingChannels[matchId] = typingChannel
        var typingOff: Job? = null
        launch {
            typingChannel.broadcastFlow<JsonObject>("typing").collect { msg ->
                if (msg["from"]?.jsonPrimitive?.content == me) return@collect
                if (live) watch.activity()
                send(MatchEvent.Typing(true))
                typingOff?.cancel()
                typingOff = launch { delay(4_000); send(MatchEvent.Typing(false)) }
            }
        }
        // Registered before the join below, so the join asks for presence. One partner key can hold several
        // connections (refs); they are there while at least one is.
        val partnerRefs = mutableMapOf<String, MutableSet<String>>()
        launch(start = CoroutineStart.UNDISPATCHED) {
            typingChannel.presenceChangeFlow().collect { action ->
                action.joins.forEach { (key, p) -> if (key != me) partnerRefs.getOrPut(key) { mutableSetOf() } += p.presenceRef }
                action.leaves.forEach { (key, p) -> if (key != me) partnerRefs[key]?.let { it -= p.presenceRef; if (it.isEmpty()) partnerRefs -= key } }
                if (live) watch.presence(partnerRefs.isNotEmpty())
            }
        }
        // Every (re)join: track my presence again (it does not survive a rejoin), and let the timers run only while
        // joined. A rejoin's presence sync then tells who is there now.
        launch {
            typingChannel.joined().collect { up ->
                if (!up) partnerRefs.clear()
                if (up) runCatching { typingChannel.track(buildJsonObject { put("user", me) }) }
                if (live) watch.connection(up)
            }
        }
        // Back after a dropped connection: re-sync at once instead of at the next tick.
        val resync = Channel<Unit>(Channel.CONFLATED)
        launch { channel.joined().collect { up -> if (up) resync.trySend(Unit) } }
        launch { updates.collect { a -> runCatching { a.decodeRecord<MatchRow>() }.onSuccess { onMatch(it) } } }
        try {
            // Not waiting for the joins: where the socket cannot connect at all, the re-syncs below still work.
            channel.subscribe()
            typingChannel.subscribe()
            // Safety net if the realtime socket drops on a weak network: every 10 s the match row and only the messages
            // newer than the newest seen (the app de-duplicates), only while the app is on screen; back from the
            // background it re-syncs at once.
            var cursor = afterId
            while (true) {
                AppForeground.visible.awaitVisible()
                runCatching {
                    onMatch(matchRow(matchId))
                    val newer = history(matchId, cursor)
                    newer.forEach {
                        if (!it.fromMe && live) watch.activity()
                        send(MatchEvent.NewMessage(it))
                    }
                    newer.maxOfOrNull { it.id }?.let { id -> cursor = maxOf(id, cursor ?: id) }
                }
                withTimeoutOrNull(RESYNC_MS) { resync.receive() }
            }
        } finally {
            withContext(NonCancellable) {
                typingChannels.remove(matchId, typingChannel)
                runCatching { client.realtime.removeChannel(channel) }
                runCatching { client.realtime.removeChannel(typingChannel) }
            }
        }
    }

    override suspend fun sendTyping(matchId: String) {
        val ch = typingChannels[matchId] ?: return
        runCatching { ch.broadcast("typing", buildJsonObject { put("from", uid) }) }
    }

    override suspend fun history(matchId: String, afterId: Long?): List<Message> = call {
        val me = uid
        if (afterId != null) { // re-sync: only what came after, oldest first
            return@call client.postgrest["messages"].select {
                filter {
                    eq("match_id", matchId)
                    gt("id", afterId)
                }
                order("id", Order.ASCENDING)
                limit(200)
            }.decodeList<MessageRow>().map { toMessage(it, me) }
        }
        client.postgrest["messages"].select {
            filter { eq("match_id", matchId) }
            order("id", Order.DESCENDING) // the newest 200, shown oldest first
            limit(200)
        }.decodeList<MessageRow>().asReversed().map { toMessage(it, me) }
    }

    override suspend fun send(matchId: String, text: String?, media: Media?): Message = call {
        val path = media?.let { upload(it.uri.toUri(), "chat", it.kind) }
        val row = client.postgrest["messages"].insert(
            NewMessageRow(matchId, text, path, media?.kind?.name?.lowercase()),
        ) { select() }.decodeSingle<MessageRow>()
        telemetry.track(Funnel.CHAT_MESSAGE_SENT)
        toMessage(row, uid)
    }

    override suspend fun markRead(matchId: String, upto: Long) {
        val sent = readSent[matchId]
        if (sent != null && upto <= sent) return
        readSent[matchId] = upto
        try {
            call { client.postgrest.rpc("mark_read", buildJsonObject { put("p_match", matchId); put("p_upto", upto) }); Unit }
        } catch (e: CancellationException) {
            readSent.remove(matchId, upto)
            throw e
        } catch (e: Exception) {
            readSent.remove(matchId, upto) // forgotten, so the next call tries again
        }
    }

    override suspend fun leave(matchId: String) = call { client.postgrest.rpc("leave_match", buildJsonObject { put("p_match", matchId) }); Unit }

    override suspend fun report(partnerId: String, reason: ReportReason, matchId: String?) = call {
        client.postgrest.rpc("report_user", buildJsonObject {
            put("p_reported", partnerId)
            put("p_reason", reason.code)
            matchId?.let { put("p_match", it) }
        })
        Unit
    }

    /** Server RPC block_user: blocks, ends any open chat between us and removes the friendship. */
    override suspend fun block(userId: String) = call {
        client.postgrest.rpc("block_user", buildJsonObject { put("p_user", userId) })
        Unit
    }

    override suspend fun requestVideo(matchId: String) = call { client.postgrest.rpc("request_video", buildJsonObject { put("p_match", matchId) }); Unit }

    override suspend fun answerVideo(matchId: String, accept: Boolean) = call {
        client.postgrest.rpc("respond_video", buildJsonObject { put("p_match", matchId); put("p_accept", accept) })
        Unit
    }

    override suspend fun endVideo(matchId: String) = call { client.postgrest.rpc("end_video", buildJsonObject { put("p_match", matchId) }); Unit }

    override suspend fun videoToken(matchId: String): VideoToken = call {
        val t = client.functions.invoke("video-token", body = buildJsonObject { put("match_id", matchId) }).body<TokenRow>()
        telemetry.track(Funnel.CALL_STARTED)
        VideoToken(t.url, t.token)
    }

    /** Server RPC flag_nudity (migration 016). Errors are ignored: the call goes on either way. */
    override suspend fun flagNudity(matchId: String) {
        try {
            client.postgrest.rpc("flag_nudity", buildJsonObject { put("p_match", matchId) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Unit
        }
    }

    // ---- profiles ----

    override suspend fun recents(): List<RecentMatch> = call {
        client.postgrest.rpc("recent_list").decodeList<RecentRow>().map { r ->
            RecentMatch(
                r.matchId, toProfile(CardRow(r.id, r.name, r.gender, r.age, r.city, r.bio, r.photoPath, r.country), emptyList()),
                ts(r.metAt).toEpochMilli(), r.lastBody,
            )
        }
    }

    override suspend fun profile(userId: String): Profile? = call {
        val c = client.postgrest.rpc("card", buildJsonObject { put("uid", userId) }).decodeList<CardRow>().firstOrNull()
            ?: return@call null
        coroutineScope {
            val posts = async { posts(userId) }
            val extras = async { extras(userId) }
            val style = async { style(userId) }
            val p = toProfile(c, posts.await()).let { p -> style.await()?.let { p.copy(style = it) } ?: p }
            extras.await()?.let { withExtras(p, it.interests, it.photos) } ?: p
        }
    }

    /** A profile's grid: the newest 50 posts I may see, with their counts (posts_of). */
    private suspend fun posts(userId: String): List<Post> = postsPage(userId, null, 50)

    private suspend fun postsPage(userId: String, before: Post?, limit: Int): List<Post> = toPosts(
        client.postgrest.rpc("posts_of", buildJsonObject {
            put("p_user", userId)
            put("p_before", before?.stamp)
            put("p_limit", limit)
        }).decodeList<FeedPostRow>(),
    )

    override suspend fun addPost(text: String, imageUri: String?, visibility: PostVisibility): Post = call {
        val path = imageUri?.let { upload(it.toUri(), "posts") }
        val row = client.postgrest["posts"].insert(NewPostRow(text, path, visibility.code)) { select() }.decodeSingle<PostRow>()
        Post(row.id, row.body, imageUri, ts(row.createdAt).toEpochMilli(), visibility = visibility, stamp = row.createdAt)
    }

    override suspend fun deletePost(postId: String) = call {
        client.postgrest["posts"].delete { filter { eq("id", postId) } }
        Unit
    }

    // ---- profile style, likes, comments, the feed (033_profiles_feed.sql) ----

    override suspend fun setProfileStyle(style: ProfileStyle, newCoverJpeg: String?): Profile = call {
        val cover = newCoverJpeg?.let { upload(it.toUri(), "cover") } ?: style.coverId
        client.postgrest.rpc("set_profile_style", buildJsonObject {
            put("p_cover_path", cover)
            put("p_accent", style.accent?.code)
            put("p_status", style.status?.trim()?.ifEmpty { null })
            putJsonArray("p_languages") { style.languages.distinct().forEach { add(it.code) } }
            putJsonArray("p_prompts") {
                style.prompts.forEach { pr -> addJsonObject { put("q", pr.q.code); put("a", pr.answer.trim()) } }
            }
        })
        myProfile() ?: throw BackendException("network")
    }

    override suspend fun feed(tab: FeedTab, before: Post?): List<Post> = call {
        toPosts(
            client.postgrest.rpc("feed", buildJsonObject {
                put("p_tab", tab.code)
                put("p_before", before?.stamp)
                put("p_limit", FEED_PAGE)
            }).decodeList<FeedPostRow>(),
        )
    }

    override suspend fun postsOf(userId: String, before: Post?): List<Post> = call { postsPage(userId, before, FEED_PAGE) }

    override suspend fun post(postId: String): Post? = call {
        val raw = client.postgrest.rpc("post_get", buildJsonObject { put("p_post", postId) }).data.trim()
        if (raw.isEmpty() || raw == "null") null else toPosts(listOf(json.decodeFromString<FeedPostRow>(raw))).firstOrNull()
    }

    override suspend fun likePost(postId: String, on: Boolean): LikeState = call {
        val r = client.postgrest.rpc("post_like", buildJsonObject { put("p_post", postId); put("p_on", on) }).decodeAs<LikeRow>()
        LikeState(r.likes, r.liked)
    }

    override suspend fun comments(postId: String, after: Long?): List<Comment> = call {
        val rows = client.postgrest.rpc("comments_list", buildJsonObject {
            put("p_post", postId)
            put("p_after", after)
            put("p_limit", 50)
        }).decodeList<CommentRow>()
        signMany(rows.map { it.author.photoPath })
        rows.map(::toComment)
    }

    override suspend fun addComment(postId: String, body: String, replyTo: Long?): Comment = call {
        val row = client.postgrest.rpc("comment_add", buildJsonObject {
            put("p_post", postId)
            put("p_body", body)
            put("p_reply_to", replyTo)
        }).decodeAs<CommentRow>()
        signMany(listOf(row.author.photoPath))
        toComment(row)
    }

    override suspend fun removeComment(commentId: Long) = call {
        client.postgrest.rpc("comment_remove", buildJsonObject { put("p_id", commentId) })
        Unit
    }

    override suspend fun reportPost(postId: String, reason: ReportReason) = call {
        client.postgrest.rpc("report_post", buildJsonObject { put("p_post", postId); put("p_reason", reason.code) })
        Unit
    }

    override suspend fun reportComment(commentId: Long, reason: ReportReason) = call {
        client.postgrest.rpc("report_comment", buildJsonObject { put("p_id", commentId); put("p_reason", reason.code) })
        Unit
    }

    /** Feed posts with their authors; every photo is signed in one request. */
    private suspend fun toPosts(rows: List<FeedPostRow>): List<Post> {
        signMany(rows.flatMap { listOf(it.imagePath, it.author?.photoPath) })
        return rows.map { r ->
            Post(
                r.id, r.body, r.imagePath?.let { p -> signedUrls[p]?.first ?: runCatching { signed(p) }.getOrNull() },
                ts(r.createdAt).toEpochMilli(), PostVisibility.of(r.visibility), r.likes, r.comments, r.liked,
                r.author?.let(::author), r.createdAt,
            )
        }
    }

    private fun author(a: AuthorRow) = card(VoiceCardRow(a.id, a.name, a.gender, a.age, a.city, null, a.photoPath, a.country))
        .let { it.copy(style = ProfileStyle(accent = Accent.of(a.accent)), verified = a.verified) }

    // ---- real identities and manners (035) ----

    /** The hosted identity check (Didit): the Edge Function makes the session and gives back its page. */
    override suspend fun kycStart(): String = call {
        client.functions.invoke("kyc", body = buildJsonObject { put("action", "start") }).body<KycStartRow>().url
    }

    /** The result is always read from the provider by the Edge Function (never trusted from the app). */
    override suspend fun kycCheck(): KycResult = call {
        val r = client.functions.invoke("kyc", body = buildJsonObject { put("action", "check") }).body<KycCheckRow>()
        KycResult.of(r.result) ?: KycResult.PENDING
    }

    override suspend fun myTrust(): MyTrust = call {
        val r = client.postgrest.rpc("my_trust").decodeAs<MyTrustRow>()
        MyTrust(
            Trust(r.id, r.verified, r.manner, r.ratings, tags(r.tags)), r.required == true,
            r.kyc?.let { k -> KycAttempt(k.status, KycResult.of(k.result), k.at?.let { runCatching { ts(it).toEpochMilli() }.getOrNull() }) },
            r.rated.toSet(),
        )
    }

    override suspend fun trust(userIds: List<String>): List<Trust> = call {
        val ids = userIds.distinct().take(100)
        if (ids.isEmpty()) return@call emptyList()
        client.postgrest.rpc("trust", buildJsonObject { putJsonArray("p_users") { ids.forEach { add(it) } } })
            .decodeList<TrustRow>().map { Trust(it.id, it.verified, it.manner, it.ratings, tags(it.tags)) }
    }

    private fun tags(rows: List<TagRow>) = rows.mapNotNull { t -> MannerTag.of(t.tag)?.let { TagCount(it, t.n) } }

    override suspend fun rateChat(matchId: String, good: Boolean, tags: List<MannerTag>) = call {
        client.postgrest.rpc("rate_chat", buildJsonObject {
            put("p_match", matchId)
            put("p_good", good)
            putJsonArray("p_tags") { tags.take(MannerTag.MAX).forEach { add(it.code) } }
        })
        Unit
    }

    private fun toComment(r: CommentRow) = Comment(
        r.id, r.postId, r.body, ts(r.createdAt).toEpochMilli(), author(r.author), r.replyTo, r.replyName, r.mine, r.canRemove,
    )

    override suspend fun updateProfile(name: String, city: String, bio: String, newPhotoUri: String?): Profile = call {
        val me = uid
        client.postgrest["profiles"].update(ProfileEdit(name, city, bio)) { filter { eq("id", me) } }
        newPhotoUri?.let { uri ->
            val path = upload(uri.toUri(), "avatar")
            client.postgrest["profiles"].update(PhotoEdit(path)) { filter { eq("id", me) } }
        }
        myProfile() ?: throw BackendException("network")
    }

    // ---- friends ----

    override suspend fun friends(): List<Friend> = call {
        client.postgrest.rpc("friend_list").decodeList<FriendRow>().map { f ->
            Friend(
                toProfile(CardRow(f.id, f.name, f.gender, f.age, f.city, f.bio, f.photoPath, f.country), emptyList()),
                when (f.state) {
                    "friends" -> FriendState.FRIENDS
                    "requested" -> FriendState.REQUESTED
                    else -> FriendState.INCOMING
                },
                f.matchId,
            )
        }
    }

    override suspend fun requestFriend(userId: String): FriendState = call {
        val raw = client.postgrest.rpc("request_friend", buildJsonObject { put("p_user", userId) }).data
        if (json.decodeFromString<String>(raw.trim()) == "friends") FriendState.FRIENDS else FriendState.REQUESTED
    }

    override suspend fun answerFriend(userId: String, accept: Boolean) = call {
        client.postgrest.rpc("answer_friend", buildJsonObject { put("p_user", userId); put("p_accept", accept) })
        Unit
    }

    override suspend fun removeFriend(userId: String) = call {
        client.postgrest.rpc("remove_friend", buildJsonObject { put("p_user", userId) })
        Unit
    }

    override suspend fun friendThread(userId: String): String = call {
        json.decodeFromString<String>(client.postgrest.rpc("friend_thread", buildJsonObject { put("p_user", userId) }).data.trim())
    }

    // ---- rooms (017_voice_rooms.sql, 019_unified_rooms.sql; audio through LiveKit, VoiceAudio.kt, only while
    // someone is on the stage) ----

    @Serializable private data class VoiceCardRow(
        val id: String, val name: String, val gender: String = "other", val age: Int = 0, val city: String? = null, val bio: String? = null,
        @SerialName("photo_path") val photoPath: String? = null, val country: String? = null,
    )
    @Serializable private data class VoiceListRow(
        val id: String, val title: String, val category: String, val mine: Boolean = false, val host: VoiceCardRow? = null,
        val speakers: List<VoiceCardRow> = emptyList(), val listeners: Int = 0, val members: Int = 0,
        @SerialName("now_playing") val nowPlaying: String? = null,
        @SerialName("max_members") val maxMembers: Int = VoiceRoom.DEFAULT_MEMBERS, @SerialName("gender_rule") val genderRule: String? = null,
        @SerialName("min_age") val minAge: Int = VoiceRoom.MIN_AGE, @SerialName("max_age") val maxAge: Int = VoiceRoom.MAX_AGE,
        @SerialName("is_private") val isPrivate: Boolean = false, val announcement: String? = null, val code: String? = null,
        val refusal: String? = null, val lang: String? = null,
    )
    @Serializable private data class CreatedVoiceRow(val id: String, val code: String)
    @Serializable private data class VoiceMessageRow(
        val id: Long, @SerialName("room_id") val roomId: String, val sender: String, val kind: String, val body: String? = null,
        val payload: JsonObject? = null, @SerialName("media_path") val mediaPath: String? = null, @SerialName("sender_name") val senderName: String,
        @SerialName("sender_gender") val senderGender: String? = null, @SerialName("sender_age") val senderAge: Int? = null,
        @SerialName("sender_photo") val senderPhoto: String? = null, @SerialName("sender_country") val senderCountry: String? = null,
        @SerialName("created_at") val createdAt: String,
    )
    @Serializable private data class VoiceRevRow(val rev: Long = 0, @SerialName("ended_at") val endedAt: String? = null)
    /** The edge function link-meta (036). */
    @Serializable private data class LinkMetaRow(
        val url: String? = null, val title: String? = null, val description: String? = null, val image: String? = null, val site: String? = null,
        val frameable: Boolean = false,
    )
    @Serializable private data class VoiceSeqRow(val seq: Long = 0)

    /**
     * What the voice methods need from the room being followed ([voiceSession]). [acted]: after my own action, whose
     * change arrives over Broadcast; the state is fetched only while that connection is down.
     */
    private class VoiceLive(val refresh: () -> Unit, val acted: () -> Unit, val mic: suspend (Boolean) -> Unit) {
        @Volatile var room: VoiceRoom? = null
    }

    private val voiceLive = ConcurrentHashMap<String, VoiceLive>()
    /** The room's public typing channel (vtyping-<room>, as the web), while its session runs. */
    private val voiceTyping = ConcurrentHashMap<String, RealtimeChannel>()
    private val serverClock = ServerClock()

    override suspend fun voiceRooms(query: RoomQuery): List<VoiceRoomSummary> = call {
        val rows = client.postgrest.rpc("voice_list3", buildJsonObject {
            put("p_query", query.text.trim().ifEmpty { null })
            put("p_lang", RoomLang.server(query.lang))
        }).decodeList<VoiceListRow>()
        signMany(rows.flatMap { r -> listOf(r.host?.photoPath) + r.speakers.map { it.photoPath } })
        rows.mapNotNull { r ->
            val host = r.host ?: return@mapNotNull null
            VoiceRoomSummary(
                r.id, r.title, RoomCategory.of(r.category), card(host), r.speakers.map(::card), r.listeners,
                r.nowPlaying?.ifEmpty { null }, r.mine, members = r.members,
                rules = RoomRules(r.maxMembers, GenderRule.of(r.genderRule), r.minAge, r.maxAge, r.isPrivate),
                announcement = r.announcement, code = r.code, refusal = r.refusal, lang = RoomLang.of(r.lang),
            )
        }.sortedByDescending { it.mine }
    }

    override suspend fun createVoiceRoom(room: NewVoiceRoom): CreatedVoiceRoom = call {
        val r = room.rules
        val row = client.postgrest.rpc("create_voice_room3", buildJsonObject {
            put("p_title", room.title)
            put("p_category", room.category.code)
            put("p_max", r.maxMembers)
            put("p_gender", r.genderRule.code)
            put("p_min_age", r.minAge)
            put("p_max_age", r.maxAge)
            put("p_private", r.isPrivate)
            put("p_announcement", room.announcement)
            put("p_lang", RoomLang.server(room.lang))
        }).decodeAs<CreatedVoiceRow>()
        telemetry.track(Funnel.ROOM_CREATED)
        CreatedVoiceRoom(row.id, row.code)
    }

    /** A wrong code comes back as null (so the miss is counted: 10 in 10 minutes and code entry pauses). */
    override suspend fun joinVoiceCode(code: String): String = call {
        json.decodeFromString<String?>(client.postgrest.rpc("join_voice_code", buildJsonObject { put("p_code", code) }).data.trim())
    } ?: throw BackendException("not_found")

    override suspend fun updateVoiceRoom(roomId: String, title: String, announcement: String, lang: String?) = call {
        client.postgrest.rpc("voice_settings2", buildJsonObject {
            put("p_room", roomId)
            put("p_title", title)
            put("p_announcement", announcement)
            put("p_lang", RoomLang.server(lang))
        })
        // The language is not in the room's change events: a new language re-reads the whole state.
        voiceLive[roomId]?.let { if (lang != null) it.refresh() else it.acted() }
        Unit
    }

    override suspend fun inviteToRoom(roomId: String, userId: String) = call {
        client.postgrest.rpc("invite_to_room", buildJsonObject { put("p_room", roomId); put("p_user", userId) })
        Unit
    }

    override suspend fun joinInvite(inboxId: Long): String = call {
        json.decodeFromString<String>(client.postgrest.rpc("join_invite", buildJsonObject { put("p_id", inboxId) }).data.trim())
    }

    override suspend fun onlineCount(): Int = call {
        client.postgrest.rpc("online_count").data.trim().toIntOrNull() ?: 0
    }

    // ---- notifications and unread counts (023_notifications.sql) ----

    override suspend fun inbox(before: Long?): List<InboxItem> = call {
        val rows = client.postgrest.rpc("inbox2", buildJsonObject { put("p_before", before); put("p_limit", 30) }).decodeList<InboxRow>()
        signMany(rows.flatMap { listOf(it.actor.photoPath, it.post?.imagePath) })
        rows.mapNotNull { r ->
            InboxItem(
                r.id, InboxKind.of(r.kind) ?: return@mapNotNull null, card(r.actor), ts(r.createdAt).toEpochMilli(), r.readAt != null,
                r.n, r.matchId, r.roomId, r.roomTitle, r.roomOpen, r.pending,
                r.post?.let { PostRef(it.id, it.body, it.imagePath?.let { p -> signedUrls[p]?.first }) },
            )
        }
    }

    override suspend fun markInboxRead(ids: List<Long>?) = call {
        client.postgrest.rpc("inbox_read", buildJsonObject {
            if (ids == null) put("p_ids", null as String?) else putJsonArray("p_ids") { ids.forEach { add(it) } }
        })
        Unit
    }

    override suspend fun badges(): Badges = call {
        val b = client.postgrest.rpc("my_badges").decodeAs<BadgesRow>()
        Badges(b.inbox, b.friends, b.chats)
    }

    /** Inserts and updates of my notifications (RLS: my rows only); one signal once joined, for what came meanwhile. */
    override fun inboxSignals(): Flow<Unit> = channelFlow {
        val me = uid
        val channel = client.channel("inbox-$me-${UUID.randomUUID()}")
        val changes = channel.postgresChangeFlow<PostgresAction>("public") {
            table = "notifications"
            filter("user_id", FilterOperator.EQ, me)
        }
        launch { changes.collect { send(Unit) } }
        try {
            channel.subscribe(blockUntilSubscribed = true)
            send(Unit)
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { runCatching { client.realtime.removeChannel(channel) } }
        }
    }

    override suspend fun registerDevice(token: String, locale: String, timeZone: String) = call {
        client.postgrest.rpc("register_device", buildJsonObject {
            put("p_token", token)
            put("p_platform", "android")
            put("p_locale", locale)
            put("p_tz", timeZone)
        })
        Unit
    }

    override suspend fun unregisterDevice(token: String) = call {
        client.postgrest.rpc("unregister_device", buildJsonObject { put("p_token", token) })
        Unit
    }

    // ---- profile extras (024_discovery_profiles.sql) ----

    override suspend fun setInterests(tags: List<Interest>): Profile = call {
        client.postgrest.rpc("set_interests", buildJsonObject { putJsonArray("p_tags") { tags.forEach { add(it.code) } } })
        myProfile() ?: throw BackendException("network")
    }

    /** Stored like the profile photo (avatar folder: set_photos accepts only those), then the list is set. */
    override suspend fun addProfilePhoto(jpegUri: String): Profile = call {
        val now = myProfile() ?: throw BackendException("network")
        if (now.photos.size >= ProfilePhoto.MAX) throw BackendException("not_allowed")
        val path = upload(jpegUri.toUri(), "avatar")
        setPhotos(now.photos.map { it.id } + path)
    }

    override suspend fun removeProfilePhoto(id: String): Profile = call {
        val now = myProfile() ?: throw BackendException("network")
        setPhotos(now.photos.map { it.id }.filter { it != id })
    }

    private suspend fun setPhotos(paths: List<String>): Profile {
        client.postgrest.rpc("set_photos", buildJsonObject { putJsonArray("p_paths") { paths.forEach { add(it) } } })
        return myProfile() ?: throw BackendException("network")
    }

    override suspend fun sendVoicePhoto(roomId: String, jpegUri: String): VoiceLine = call {
        val path = upload(jpegUri.toUri(), "rooms")
        val row = client.postgrest.rpc("voice_photo", buildJsonObject { put("p_room", roomId); put("p_path", path) }).decodeAs<VoiceMessageRow>()
        toVoiceLines(listOf(row)).first()
    }

    override suspend fun voiceIcebreaker(roomId: String): VoiceLine = call {
        val row = client.postgrest.rpc("voice_icebreaker", buildJsonObject { put("p_room", roomId) }).decodeAs<VoiceMessageRow>()
        toVoiceLines(listOf(row)).first()
    }

    override suspend fun createVoicePoll(roomId: String, question: String, options: List<String>): VoiceLine = call {
        val row = client.postgrest.rpc("voice_poll_create", buildJsonObject {
            put("p_room", roomId)
            put("p_question", question)
            putJsonArray("p_options") { options.forEach { add(it) } }
        }).decodeAs<VoiceMessageRow>()
        toVoiceLines(listOf(row)).firstOrNull() ?: throw BackendException("bad_request")
    }

    override suspend fun voicePollVote(pollId: Long, choice: Int): VoicePoll = call {
        client.postgrest.rpc("voice_poll_vote", buildJsonObject { put("p_poll", pollId); put("p_choice", choice) }).decodeAs<VoicePollRow>().toPoll()
    }

    override suspend fun closeVoicePoll(pollId: Long): VoicePoll = call {
        client.postgrest.rpc("voice_poll_close", buildJsonObject { put("p_poll", pollId) }).decodeAs<VoicePollRow>().toPoll()
    }

    override suspend fun voicePolls(ids: List<Long>): List<VoicePoll> = call {
        if (ids.isEmpty()) return@call emptyList()
        client.postgrest.rpc("voice_polls_get", buildJsonObject { putJsonArray("p_ids") { ids.take(50).forEach { add(it) } } })
            .decodeList<VoicePollRow>().map { it.toPoll() }
    }

    override suspend fun videoTitle(videoId: String): String = youTubeTitle(videoId)

    override suspend fun leaveVoice(roomId: String) = call { client.postgrest.rpc("leave_voice", buildJsonObject { put("p_room", roomId) }); Unit }

    override suspend fun endVoice(roomId: String) = call {
        client.postgrest.rpc("end_voice_room", buildJsonObject { put("p_room", roomId) })
        voiceSync(roomId) // LiveKit closes the room for everyone still connected
    }

    override suspend fun voiceLines(roomId: String): List<VoiceLine> = call {
        client.postgrest["voice_messages"].select {
            filter {
                eq("room_id", roomId)
                isIn("kind", LINE_KINDS)
            }
            order("id", Order.DESCENDING) // the newest 100, shown oldest first
            limit(100)
        }.decodeList<VoiceMessageRow>().asReversed().let { toVoiceLines(it) }
    }

    private suspend fun voiceLinesAfter(roomId: String, afterId: Long): List<VoiceLine> =
        client.postgrest["voice_messages"].select {
            filter {
                eq("room_id", roomId)
                isIn("kind", LINE_KINDS)
                gt("id", afterId)
            }
            order("id", Order.ASCENDING)
            limit(200)
        }.decodeList<VoiceMessageRow>().let { toVoiceLines(it) }

    /** voice_send2 (029): the same row as voice_send, with the answered line quoted in payload.reply. */
    override suspend fun sendVoice(roomId: String, text: String, replyTo: Long?): VoiceLine = call {
        val row = client.postgrest.rpc("voice_send2", buildJsonObject {
            put("p_room", roomId)
            put("p_text", text)
            put("p_reply_to", replyTo)
        }).decodeAs<VoiceMessageRow>()
        toVoiceLines(listOf(row)).first()
    }

    override suspend fun reportVoiceRoom(roomId: String, reason: ReportReason) {
        val host = voiceLive[roomId]?.room?.hostId ?: call {
            client.postgrest.rpc("voice_state", buildJsonObject { put("p_room", roomId) }).decodeAs<VoiceStateRow>().host
        }
        if (host == uid) throw BackendException("not_allowed")
        report(host, reason, null)
    }

    override suspend fun voiceAction(roomId: String, action: VoiceAction) = call {
        val live = voiceLive[roomId]
        suspend fun rpc(fn: String, extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) =
            client.postgrest.rpc(fn, buildJsonObject { put("p_room", roomId); extra() })
        when (action) {
            is VoiceAction.Hand -> rpc("voice_hand") { put("p_up", action.up) }
            is VoiceAction.Mic -> (live ?: throw BackendException("network")).mic(action.on)
            // An open room seats me; a hand-raise room raises my hand instead. Either way LiveKit hears about it.
            is VoiceAction.TakeSeat -> if (rpc("voice_take_seat") { put("p_seat", action.seat) }.data.contains("seated")) voiceSync(roomId, uid)
            VoiceAction.Unseat -> {
                runCatching { live?.mic?.invoke(false) }
                rpc("voice_leave_seat")
                voiceSync(roomId, uid)
            }
            is VoiceAction.React -> {
                rpc("voice_react") { put("p_kind", action.kind.code) }
                return@call // it comes back over realtime, like everyone else's
            }
            is VoiceAction.Invite -> {
                rpc("voice_invite") { put("p_user", action.userId); action.seat?.let { put("p_seat", it) } }
                voiceSync(roomId, action.userId)
            }
            is VoiceAction.Answer -> {
                rpc("voice_answer") { put("p_user", action.userId); put("p_accept", action.accept) }
                if (action.accept) voiceSync(roomId, action.userId)
            }
            is VoiceAction.ToAudience -> {
                rpc("voice_to_audience") { put("p_user", action.userId) }
                voiceSync(roomId, action.userId)
            }
            is VoiceAction.Mute -> {
                rpc("voice_mute_member") { put("p_user", action.userId) }
                voiceSync(roomId, action.userId, mute = true)
            }
            is VoiceAction.Lock -> rpc("voice_lock_seat") { put("p_seat", action.seat); put("p_on", action.locked) }
            is VoiceAction.Cohost -> rpc("voice_set_cohost") { put("p_user", action.userId); put("p_on", action.on) }
            is VoiceAction.Kick -> {
                rpc("voice_kick") { put("p_user", action.userId) }
                voiceSync(roomId, action.userId)
            }
            // The host's track plays at once when nothing does; everything else joins the requests.
            is VoiceAction.AddTrack -> rpc("voice_queue_add") { put("p_video", action.videoId); put("p_title", action.title.take(100)) }
            is VoiceAction.Vote -> rpc("voice_vote") { put("p_item", action.itemId); put("p_on", action.on) }
            is VoiceAction.Play -> rpc("voice_play_item") { put("p_item", action.itemId) }
            is VoiceAction.RemoveItem -> rpc("voice_queue_remove") { put("p_item", action.itemId) }
            VoiceAction.Pause -> rpc("voice_pause")
            VoiceAction.Resume -> rpc("voice_resume")
            is VoiceAction.Skip -> rpc("voice_skip") { (action.trackId ?: live?.room?.track?.id)?.let { put("p_current", it) } }
            is VoiceAction.Seek -> rpc("voice_seek") { put("p_ms", action.ms) }
            is VoiceAction.ShareLink -> {
                rpc("voice_share_link") { put("p_url", action.url); put("p_title", action.title?.take(200)) }
                // 037: everyone gets the change over Broadcast; the sharer also reads the state back at once.
                live?.refresh?.invoke()
                return@call
            }
        }
        live?.acted?.invoke() // its change arrives over Broadcast; fetched only while that is down
    }

    override suspend fun sendVoiceTyping(roomId: String, name: String) {
        val ch = voiceTyping[roomId] ?: return
        runCatching { ch.broadcast("typing", buildJsonObject { put("id", uid); put("name", name) }) }
    }

    /** link-meta (036): fetched by the server (only public https pages), so the phone never loads the page for a card. */
    override suspend fun linkPreview(url: String): LinkPreview? {
        if (!Links.isWeb(url) || url.length > Links.MAX_URL) return null
        return try {
            val r = withTimeoutOrNull(LINK_META_MS) {
                client.functions.invoke("link-meta", body = buildJsonObject { put("url", url) }).body<LinkMetaRow>()
            } ?: return null
            LinkPreview(
                url = r.url?.takeIf(Links::isWeb) ?: url, title = r.title?.trim()?.ifEmpty { null },
                description = r.description?.trim()?.ifEmpty { null }, image = r.image?.takeIf { it.startsWith("https://") },
                site = r.site?.trim()?.ifEmpty { null },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The edge function translate: the server reads the saved message itself (only one I may read), so the phone sends
     * just its id. Errors come back as HTTP status plus a code in the body; the body's code wins (an outage's bare 503
     * is not "switched off").
     */
    override suspend fun translate(kind: TranslateKind, id: Long, target: String): Translation {
        val r = try {
            withTimeoutOrNull(TRANSLATE_MS) {
                client.functions.invoke("translate", body = buildJsonObject {
                    put("kind", kind.code)
                    put("id", id)
                    put("target", target)
                }).body<TranslateRow>()
            } ?: throw BackendException("network")
        } catch (e: RestException) {
            val text = listOfNotNull(e.message, e.error, e.description).joinToString(" ")
            throw BackendException(
                TRANSLATE_CODES.firstOrNull { it in text } ?: when (e.statusCode) {
                    401 -> "not_signed_in"
                    404 -> "not_found"
                    422 -> "cannot_translate"
                    429 -> "slow_down"
                    else -> "translate_failed"
                },
            )
        } catch (e: IOException) {
            throw BackendException("network")
        } catch (e: kotlinx.serialization.SerializationException) {
            throw BackendException("translate_failed")
        }
        if (!r.same && r.text.isBlank()) throw BackendException("translate_failed")
        return Translation(r.text, r.same)
    }

    /** Applies the room's state to LiveKit (edge function voice-sync). Fire and forget: the host re-checks every minute. */
    private fun voiceSync(roomId: String, userId: String? = null, mute: Boolean = false) {
        background.launch {
            runCatching {
                client.functions.invoke("voice-sync", body = buildJsonObject {
                    put("room_id", roomId)
                    userId?.let { put("user_id", it) }
                    if (mute) put("mute", true)
                })
            }
        }
    }

    /**
     * Follows a voice room. Once I'm in: one full state, then every change itself over a private Broadcast topic
     * (020: a member, the room's settings and music, a vote, each numbered), applied here; the full state is fetched
     * again only on a gap in the numbers, a reconnect, or when the heartbeat says the room is further on. Without
     * Broadcast (its channel can't join) the room's `rev` is compared instead, as before 020. Chat lines and
     * reactions come over realtime. A heartbeat every 20 s, also in the background (the foreground service keeps the
     * room alive there); a re-sync every 45 s while realtime is up, every 10 s while it isn't, only while on screen.
     * Any refusal from the server about my being here ends the session with its reason ([roomRefusal]).
     */
    override fun voiceSession(roomId: String): Flow<VoiceEvent> = channelFlow {
        val me = uid
        var rev = -1L
        var over = false
        var beats = 0
        var newest = 0L
        var mine: Pair<Int?, Boolean>? = null // my seat and mute, to notice changes
        var audio: VoiceAudio? = null
        var audioJob: Job? = null
        var audioTries = 0
        /** Someone is on the stage: only then is there audio (voice_grant refuses a token before). */
        var stage = false
        val refresh = Channel<Unit>(Channel.CONFLATED)
        /** Changes come in bursts (someone joins: a card and a line): one render, one photo-signing request. */
        val render = Channel<Unit>(Channel.CONFLATED)
        val permissionsChanged = Channel<Unit>(Channel.CONFLATED)
        // ---- the last full state with every change since applied to it ----
        var base: VoiceStateRow? = null
        val order = ChangeOrder<VoiceChange> { it.seq }
        var fetching = false
        var shown = 0
        var behind: Job? = null
        var broadcastUp = false
        var linesUp = false
        var retryAudio: () -> Unit = {}
        /** The stage emptied: the room goes on without audio until someone takes a seat. */
        var stopAudio: () -> Unit = {}

        // Ended or removed: said once; the collector stops following then.
        suspend fun finish(e: VoiceEvent) {
            if (over) return
            over = true
            audio?.disconnect()
            audio = null
            send(e)
        }
        // Anything the server says about my being here (the heartbeat brings me back after a gap, and the room may
        // have filled, banned me, ...) ends the session with its reason, instead of a room nobody else sees me in.
        suspend fun failed(e: Throwable) {
            roomRefusal((e as? BackendException)?.code)?.let { finish(it) }
        }

        val audioEvents = object : VoiceAudio.Events {
            override fun status(s: AudioStatus) { trySend(VoiceEvent.Audio(VoiceAudioState(s))) }
            override fun interrupted(on: Boolean) { trySend(VoiceEvent.Audio(VoiceAudioState(AudioStatus.CONNECTED, interrupted = on))) }
            override fun levels(levels: Map<String, Float>) { trySend(VoiceEvent.Levels(levels)) }
            override fun permissions(canPublish: Boolean) { permissionsChanged.trySend(Unit) }
            override fun micMutedByServer() { refresh.trySend(Unit) }
            override fun gone(removed: Boolean) {
                audio?.disconnect()
                audio = null
                audioJob = null
                trySend(VoiceEvent.Levels(emptyMap()))
                if (removed) refresh.trySend(Unit) // removed or ended: the state says which
                retryAudio()
            }
        }

        fun connectAudio() {
            if (over || audioJob != null || !stage) return
            audioJob = launch {
                try {
                    val t = call { client.functions.invoke("voice-token", body = buildJsonObject { put("room_id", roomId) }).body<TokenRow>() }
                    val a = VoiceAudio(app, this@channelFlow, audioEvents)
                    try {
                        a.connect(t.url, t.token)
                    } catch (e: Exception) {
                        a.disconnect()
                        throw e
                    }
                    if (!stage || over) { // the stage emptied while connecting
                        a.disconnect()
                        audioJob = null
                        return@launch
                    }
                    audio = a
                    audioTries = 0
                    // Back after a dropped connection while unmuted on a seat: talk again (or show the mic as off).
                    if (mine?.first != null && mine?.second == false) {
                        runCatching { a.setMic(true) }.onFailure {
                            runCatching { client.postgrest.rpc("voice_mic", buildJsonObject { put("p_room", roomId); put("p_on", false) }) }
                            refresh.trySend(Unit)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: LinkageError) {
                    // No WebRTC on this device or build (JVM tests): the room works without audio.
                    audioJob = null
                    send(VoiceEvent.Audio(VoiceAudioState(AudioStatus.FAILED)))
                } catch (e: Exception) {
                    audioJob = null
                    send(VoiceEvent.Audio(VoiceAudioState(AudioStatus.FAILED)))
                    // Not in the room any more (403): the state will say why; anything else is worth another try.
                    if ((e as? BackendException)?.code == "not_allowed") refresh.trySend(Unit)
                    retryAudio()
                }
            }
        }

        suspend fun show(state: VoiceStateRow) {
            val n = ++shown
            val room = toVoiceRoom(state, me)
            if (over || n != shown) return // a newer state was shown meanwhile
            voiceLive[roomId]?.room = room
            val m = room.me
            val before = mine
            mine = m?.let { it.seat to it.muted }
            if (m != null && before != null) {
                // Brought up to a seat: ask LiveKit for the right to talk. Sent back to the audience, or muted by a
                // host: the microphone goes off here too.
                if (before.first == null && m.seat != null) voiceSync(roomId, me)
                if ((m.seat == null && before.first != null) || (m.muted && !before.second)) runCatching { audio?.setMic(false) }
            }
            // Audio only while someone is on the stage: a room nobody talks in is a chat room and costs no LiveKit minutes.
            stage = room.stageLive
            if (m != null && stage && audio == null) connectAudio()
            else if (!stage && (audio != null || audioJob != null)) stopAudio()
            send(VoiceEvent.RoomChanged(room))
        }

        /** A full state: the changes that arrived while it was on its way go on top of it. */
        suspend fun apply(state: VoiceStateRow, sent: Long) {
            serverClock.note(state.now, sent, System.currentTimeMillis())
            rev = maxOf(rev, state.rev)
            val (later, gap) = order.base(state.seq)
            val s = later.fold(state) { acc, c -> acc.apply(c, me) }
            base = s
            if (gap) refresh.trySend(Unit)
            show(s)
        }

        suspend fun fetchState() {
            if (over) return
            try {
                val sent = System.currentTimeMillis()
                fetching = true
                val state = try {
                    call { client.postgrest.rpc("voice_state", buildJsonObject { put("p_room", roomId) }).decodeAs<VoiceStateRow>() }
                } finally {
                    fetching = false
                }
                apply(state, sent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }

        /** The server's number for the room (heartbeat, re-sync): still behind it a moment later, a change was missed. */
        fun serverAt(n: Long) {
            if (!order.behind(n) || behind?.isActive == true) return
            behind = launch {
                delay(ChangeOrder.BEHIND_GRACE_MS)
                if (order.behind(n)) refresh.trySend(Unit)
            }
        }

        suspend fun mic(on: Boolean) {
            if (!on) {
                runCatching { audio?.setMic(false) }
                call { client.postgrest.rpc("voice_mic", buildJsonObject { put("p_room", roomId); put("p_on", false) }) }
                return
            }
            val a = withTimeoutOrNull(8_000) { while (audio == null) delay(100); audio } ?: throw BackendException("network")
            try {
                a.setMic(true)
            } catch (e: BackendException) {
                if (e.code != "not_allowed") throw e
                // LiveKit hasn't heard I'm on a seat yet: ask for it and try once more.
                voiceSync(roomId, me)
                withTimeoutOrNull(4_000) { permissionsChanged.receive() }
                a.setMic(true)
            }
            try {
                call { client.postgrest.rpc("voice_mic", buildJsonObject { put("p_room", roomId); put("p_on", true) }) }
            } catch (e: Exception) {
                runCatching { a.setMic(false) }
                throw e
            }
        }

        val live = VoiceLive(
            refresh = { refresh.trySend(Unit) },
            acted = { if (!broadcastUp) refresh.trySend(Unit) },
            mic = { on -> mic(on) },
        )

        suspend fun heartbeat() {
            if (over) return
            try {
                call { client.postgrest.rpc("voice_heartbeat2", buildJsonObject { put("p_room", roomId) }).data.trim().toLongOrNull() }?.let(::serverAt)
                // The host re-checks LiveKit against the room every minute (anyone a sync missed).
                if (++beats % 3 == 0 && live.room?.hostId == me) voiceSync(roomId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }

        suspend fun onChange(c: VoiceChange) {
            if (over) return
            if (c is VoiceChange.Room && c.room.ended) return finish(VoiceEvent.Ended)
            val b = base
            val step = order.offer(c, ready = b != null && !fetching)
            // A poll is not part of the room's state (a full state doesn't bring it): its counts are shown as they
            // come, also while a state is on its way or after a gap; only a replay is left out.
            if (c is VoiceChange.Poll && step != ChangeOrder.Step.SKIP) send(VoiceEvent.PollChanged(c.poll))
            when (step) {
                ChangeOrder.Step.HOLD, ChangeOrder.Step.SKIP -> Unit
                ChangeOrder.Step.REFETCH -> refresh.trySend(Unit) // missed one
                ChangeOrder.Step.APPLY_RECHECK -> {
                    base = b!!.apply(c, me)
                    render.trySend(Unit)
                    refresh.trySend(Unit)
                }
                ChangeOrder.Step.APPLY -> {
                    // Handed the room: the code is the host's only, the full state has it.
                    if (c is VoiceChange.Room && c.room.host == me && b!!.host != me) refresh.trySend(Unit)
                    // Taken out of the room myself (removed, or dropped while away): a heartbeat brings me back in, or
                    // says why not (voice_removed, room_full, ...).
                    if (c is VoiceChange.Gone && c.id == me) launch { heartbeat() }
                    base = b!!.apply(c, me)
                    render.trySend(Unit)
                }
            }
        }

        stopAudio = {
            audioJob?.cancel()
            audioJob = null
            audio?.disconnect()
            audio = null
            audioTries = 0
            trySend(VoiceEvent.Audio(null))
            trySend(VoiceEvent.Levels(emptyMap()))
        }
        voiceLive[roomId] = live

        retryAudio = {
            if (stage) launch {
                // A weak network gets a few quick tries, then one every 30 s; the room itself (chat, seats) carries on.
                delay(ChangeOrder.audioRetryMs(audioTries++))
                connectAudio()
            }
        }

        val channel = client.channel("voice-$roomId-${UUID.randomUUID()}")
        // The changes: a private topic (RLS on realtime.messages: people who entered the room), joined once I'm in.
        val changes = client.channel("voice:$roomId") { isPrivate = true }
        val changeEvents = changes.broadcastFlow<JsonObject>("v")
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>("public") {
            table = "voice_messages"
            filter("room_id", FilterOperator.EQ, roomId)
        }
        // Typing in the chat: a public broadcast topic shared with the web app ({id, name}), nothing stored.
        val typing = client.channel("vtyping-$roomId")
        val typingEvents = typing.broadcastFlow<JsonObject>("typing")
        try {
            // Enter first: a refusal (ended, removed, blocked) fails the flow before anything else starts.
            val sent = System.currentTimeMillis()
            val state = call { client.postgrest.rpc("join_voice", buildJsonObject { put("p_room", roomId) }).decodeAs<VoiceStateRow>() }
            telemetry.track(Funnel.ROOM_JOINED)
            apply(state, sent)

            launch { changeEvents.collect { o -> VoiceChange.parse(json, o)?.let { onChange(it) } } }
            launch {
                typingEvents.collect { o ->
                    val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@collect
                    val name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty().take(60)
                    if (id != me) send(VoiceEvent.Typing(id, name))
                }
            }
            launch {
                for (x in render) {
                    delay(RENDER_MS)
                    base?.let { show(it) }
                }
            }
            launch {
                inserts.collect { a ->
                    val row = runCatching { a.decodeRecord<VoiceMessageRow>() }.getOrNull() ?: return@collect
                    if (row.kind == "reaction") {
                        // A reaction is a moment: an old one (replayed after a reconnect) is not shown.
                        val kind = VoiceReaction.of(row.payload?.get("kind")?.jsonPrimitive?.content)
                        if (kind != null && System.currentTimeMillis() - ts(row.createdAt).toEpochMilli() < 15_000) send(VoiceEvent.Reaction(row.sender, kind))
                        return@collect
                    }
                    newest = maxOf(newest, row.id)
                    runCatching { toVoiceLines(listOf(row)) }.getOrNull()?.forEach { send(VoiceEvent.Line(it)) }
                }
            }
            // State re-fetches, a little debounced (several changes in a burst cost one request).
            launch {
                for (x in refresh) {
                    delay(150)
                    fetchState()
                }
            }
            // Heartbeat every 20 s, also in the background (the foreground service keeps the room alive there):
            // the server lets go of anyone not seen for 90 s.
            launch {
                while (true) {
                    delay(HEARTBEAT_MS)
                    heartbeat()
                }
            }
            // Re-sync when a connection comes up or goes down, and every 45 s (realtime up) or 10 s (not) while on
            // screen: missed changes and lines.
            val resync = Channel<Unit>(Channel.CONFLATED)
            launch {
                var wasUp = false
                channel.joined().collect { up ->
                    linesUp = up
                    if (up || wasUp) resync.trySend(Unit)
                    if (up) wasUp = true
                }
            }
            launch {
                var wasUp = false
                changes.joined().collect { up ->
                    broadcastUp = up
                    // Back after a drop: changes may have been missed while away.
                    if (up && wasUp) refresh.trySend(Unit)
                    if (up || wasUp) resync.trySend(Unit)
                    if (up) wasUp = true
                }
            }
            channel.subscribe()
            // A topic left over from a session that is still closing would take this one's messages with it.
            client.realtime.subscriptions[changes.topic]?.takeIf { it !== changes }?.let { runCatching { client.realtime.removeChannel(it) } }
            changes.subscribe()
            client.realtime.subscriptions[typing.topic]?.takeIf { it !== typing }?.let { runCatching { client.realtime.removeChannel(it) } }
            voiceTyping[roomId] = typing
            typing.subscribe()
            while (true) {
                withTimeoutOrNull(ChangeOrder.resyncMs(broadcastUp && linesUp)) { resync.receive() }
                AppForeground.visible.awaitVisible()
                runCatching {
                    if (broadcastUp) {
                        client.postgrest["voice_seq"].select { filter { eq("room_id", roomId) } }.decodeSingleOrNull<VoiceSeqRow>()?.let { serverAt(it.seq) }
                    } else {
                        // Without Broadcast: the room's rev, as before 020 (the server still bumps it on every change).
                        val row = client.postgrest["voice_rooms"].select { filter { eq("id", roomId) } }.decodeSingleOrNull<VoiceRevRow>()
                        if (row?.endedAt != null) finish(VoiceEvent.Ended) else if (row != null && row.rev != rev) refresh.trySend(Unit)
                    }
                    if (newest > 0) voiceLinesAfter(roomId, newest).forEach { line ->
                        newest = maxOf(newest, line.id)
                        send(VoiceEvent.Line(line))
                    }
                }
                if (over) break
            }
        } finally {
            withContext(NonCancellable) {
                if (voiceLive[roomId] === live) voiceLive.remove(roomId)
                audio?.disconnect()
                runCatching { client.realtime.removeChannel(channel) }
                // Only while it is still this session's: a newer session for the same room may hold the topic now.
                if (client.realtime.subscriptions[changes.topic] === changes) runCatching { client.realtime.removeChannel(changes) }
                voiceTyping.remove(roomId, typing)
                if (client.realtime.subscriptions[typing.topic] === typing) runCatching { client.realtime.removeChannel(typing) }
            }
        }
    }

    private suspend fun toVoiceRoom(s: VoiceStateRow, me: String): VoiceRoom {
        signMany(s.members.map { it.photoPath })
        return VoiceRoom(
            id = s.id, title = s.title, category = RoomCategory.of(s.category), mode = SpeakMode.of(s.mode), hostId = s.host, meId = me,
            members = s.members.map { m ->
                VoiceMember(
                    card(VoiceCardRow(m.id, m.name, m.gender, m.age, m.city, m.bio, m.photoPath, m.country)),
                    VoiceRole.of(m.role), m.seat, m.muted, m.hand,
                )
            },
            rules = RoomRules(s.max, GenderRule.of(s.gender), s.minAge, s.maxAge, s.private),
            announcement = s.announcement, code = s.code, lang = RoomLang.of(s.lang),
            seats = List(VoiceRoom.SEATS) { VoiceSeat(it, it in s.locked) },
            track = s.music?.let {
                VoiceTrack(it.id, it.videoId, it.title, it.by, serverClock.toLocal(it.startedAt), it.pausedAt?.let(serverClock::toLocal))
            },
            queue = s.queue.map { VoiceQueueItem(it.id, it.videoId, it.title, it.by, it.votes, it.voted) },
            stage = s.stage?.takeIf { Links.isWeb(it.url) }?.let { RoomStage(it.url, it.title, it.by, it.byId, it.at) },
        )
    }

    private suspend fun toVoiceLines(rows: List<VoiceMessageRow>): List<VoiceLine> {
        val me = uid
        // One request signs every face and photo in the batch.
        signMany(rows.filter { it.kind == "text" || it.kind == "photo" }.flatMap { listOf(it.senderPhoto, it.mediaPath) })
        return rows.mapNotNull { r ->
            val at = ts(r.createdAt).toEpochMilli()
            fun sender() = card(VoiceCardRow(r.sender, r.senderName, r.senderGender ?: "other", r.senderAge ?: 0, null, null, r.senderPhoto, r.senderCountry))
            if (r.kind == "text") {
                VoiceLine.Text(r.id, sender(), r.sender == me, r.body.orEmpty(), at, reply(r.payload))
            } else if (r.kind == "poll") {
                val p = r.payload ?: return@mapNotNull null
                val pollId = p["poll_id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                val options = (p["options"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                if (options.size < VoicePoll.MIN_OPTIONS) return@mapNotNull null
                VoiceLine.Poll(r.id, sender(), r.sender == me, pollId, p["question"]?.jsonPrimitive?.contentOrNull.orEmpty(), options, at)
            } else if (r.kind == "photo") {
                // A photo no longer readable (its sender blocked, or I left the room long ago) is left out.
                val url = r.mediaPath?.let { signedUrls[it]?.first } ?: return@mapNotNull null
                VoiceLine.Photo(r.id, sender(), r.sender == me, url, at)
            } else if (r.kind == "icebreaker") {
                val prompt = r.payload?.get("q")?.jsonPrimitive?.content?.toIntOrNull()?.takeIf { it >= 1 } ?: return@mapNotNull null
                VoiceLine.Icebreaker(r.id, prompt, r.senderName, r.sender == me, at)
            } else if (r.kind == "system") {
                val p = r.payload
                val event = VoiceSystemEvent.of(p?.get("event")?.jsonPrimitive?.content) ?: return@mapNotNull null
                VoiceLine.System(
                    r.id, event, p?.get("name")?.jsonPrimitive?.content ?: r.senderName, at,
                    seat = p?.get("seat")?.jsonPrimitive?.content?.toIntOrNull(),
                    title = p?.get("title")?.jsonPrimitive?.content,
                )
            } else {
                null // a kind this app doesn't know (a newer server): left out
            }
        }
    }

    /** payload.reply of a text line (029): the line it answers, as quoted when it was sent; null when absent or unreadable. */
    private fun reply(payload: JsonObject?): VoiceReply? = runCatching {
        val q = payload?.get("reply") as? JsonObject ?: return null
        val kind = VoiceReply.Kind.of(q["kind"]?.jsonPrimitive?.contentOrNull) ?: return null
        VoiceReply(
            q["id"]!!.jsonPrimitive.longOrNull!!, q["sender"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            q["name"]?.jsonPrimitive?.contentOrNull.orEmpty(), kind,
            if (kind == VoiceReply.Kind.PHOTO) null else q["text"]?.jsonPrimitive?.contentOrNull?.take(VoiceReply.MAX_QUOTE),
        )
    }.getOrNull()

    /** A card from a voice room; the photo comes from [signMany] (one request for the whole room), else the initial. */
    private fun card(c: VoiceCardRow) = Profile(
        id = c.id, name = c.name, gender = runCatching { Gender.valueOf(c.gender.uppercase()) }.getOrDefault(Gender.OTHER),
        age = c.age, city = c.city.orEmpty(), bio = c.bio.orEmpty(),
        photoUri = c.photoPath?.let { p -> signedUrls[p]?.first }, colorSeed = c.id.hashCode(), country = c.country,
    )

    /** Signs every photo not signed yet in one request (a voice room shows dozens of faces at once). */
    private suspend fun signMany(paths: List<String?>) {
        val now = System.currentTimeMillis()
        val need = paths.filterNotNull().distinct().filter { (signedUrls[it]?.second ?: 0L) <= now }
        if (need.isEmpty()) return
        runCatching { client.storage.from(BUCKET).createSignedUrls(1.hours, need) }.getOrNull()?.forEach { u ->
            if (u.error == null && u.signedURL.isNotEmpty()) signedUrls[u.path] = u.signedURL to now + 50 * 60_000L
        }
    }

    private suspend fun toProfile(c: CardRow, posts: List<Post>) = Profile(
        id = c.id, name = c.name, gender = Gender.valueOf(c.gender.uppercase()), age = c.age, city = c.city, bio = c.bio,
        photoUri = c.photoPath?.let { signed(it) }, colorSeed = c.id.hashCode(), posts = posts, country = c.country,
    )

    private suspend fun toMessage(r: MessageRow, me: String) = Message(
        id = r.id, fromMe = r.sender == me, text = r.body,
        media = r.mediaPath?.let { Media(signed(it), if (r.mediaKind == "video") MediaKind.VIDEO else MediaKind.IMAGE) },
        at = ts(r.createdAt).toEpochMilli(),
    )

    private suspend fun signed(path: String): String {
        val now = System.currentTimeMillis()
        signedUrls[path]?.let { (url, reuseUntil) -> if (now < reuseUntil) return url }
        val url = client.storage.from(BUCKET).createSignedUrl(path, 1.hours)
        signedUrls[path] = url to now + 50 * 60_000L
        return url
    }

    /** Uploads under "<uid>/<folder>/"; photos are shrunk to 1280 px JPEG to save mobile data. */
    private suspend fun upload(uri: Uri, folder: String, kind: MediaKind = MediaKind.IMAGE): String {
        val (bytes, type, ext) = withContext(Dispatchers.IO) {
            if (kind == MediaKind.IMAGE) Triple(compressImage(uri), ContentType.Image.JPEG, "jpg")
            else {
                val mime = app.contentResolver.getType(uri) ?: "video/mp4"
                val data = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw BackendException("bad_media")
                if (data.size > MAX_VIDEO_BYTES) throw BackendException("too_large")
                Triple(data, ContentType.parse(mime), if (mime == "video/mp4") "mp4" else mime.substringAfter('/'))
            }
        }
        val path = "$uid/$folder/${UUID.randomUUID()}.$ext"
        client.storage.from(BUCKET).upload(path, bytes) {
            contentType = type
            httpOverride { timeout { requestTimeoutMillis = UPLOAD_TIMEOUT_MS } } // up to 25 MB on a slow uplink
        }
        return path
    }

    private fun compressImage(uri: Uri): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PHOTO_PX) sample *= 2
        val bmp = app.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw BackendException("bad_media")
        val scale = MAX_PHOTO_PX.toFloat() / maxOf(bmp.width, bmp.height)
        val sized = if (scale < 1f) bmp.scale((bmp.width * scale).toInt(), (bmp.height * scale).toInt()) else bmp
        return ByteArrayOutputStream().use { out ->
            sized.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
    }

    companion object {
        /** The app version as the diagnostics show it (the web sends "web-..."). */
        const val VERSION = "android-" + BuildConfig.VERSION_NAME
        /** Crash reports waiting for the next launch. */
        fun reportsDir(app: Application) = File(app.filesDir, "reports")

        private const val BUCKET = "media"
        /** Feed and profile pages (feed / posts_of; the server allows up to 50). */
        private const val FEED_PAGE = 20
        /** The chat's lines (reactions come over realtime only). */
        private val LINE_KINDS = listOf("text", "system", "icebreaker", "photo", "poll")
        private const val MAX_PHOTO_PX = 1280
        private const val MAX_VIDEO_BYTES = 25 * 1024 * 1024
        private const val REQUEST_TIMEOUT_MS = 15_000L
        private const val UPLOAD_TIMEOUT_MS = 120_000L
        private const val RESYNC_MS = 10_000L
        private const val RENDER_MS = 60L
        private const val REJOIN_SETTLE_MS = 500L
        private const val HEARTBEAT_MS = 20_000L
        /** link-meta reads the page for up to 6 s itself. */
        private const val LINK_META_MS = 9_000L
        /** The translate function asks a model; a slow answer is given up on (the line stays as it is). */
        private const val TRANSLATE_MS = 20_000L
        private val TRANSLATE_CODES = listOf("translate_unavailable", "cannot_translate", "translate_failed", "slow_down", "not_found")
        // Most specific first: a message is matched by the first code it contains.
        private val CODES = listOf(
            "verify_required", "manner_low", "already_verified", "kyc_unavailable", "kyc_failed", "already_rated", "too_late",
            "not_signed_in", "room_closed", "room_gender", "room_age", "room_full", "room_title", "room_size", "bad_request",
            "exit_locked", "premium_required", "banned", "under_18", "slow_down", "not_allowed", "not_found",
            "video_not_configured", "billing_not_configured", "already_redeemed", "wrong_account",
            "voice_removed", "mic_denied", "queue_full",
            "stale", "not_your_turn", "seat_taken", "game_full", "need_players",
        )
    }
}

/**
 * A YouTube video's real title from its oEmbed (no API key), 5 s at most; "" when it can't be looked up. The server
 * keeps up to 100 characters.
 */
internal suspend fun youTubeTitle(videoId: String, timeoutMs: Int = 5_000): String = withContext(Dispatchers.IO) {
    runCatching {
        val watch = java.net.URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8")
        val c = java.net.URL("https://www.youtube.com/oembed?url=$watch&format=json").openConnection() as java.net.HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        try {
            if (c.responseCode != 200) return@runCatching ""
            val body = c.inputStream.bufferedReader().use { it.readText() }
            (Json.parseToJsonElement(body) as? JsonObject)?.get("title")?.jsonPrimitive?.content?.trim()?.take(100).orEmpty()
        } finally {
            c.disconnect()
        }
    }.getOrDefault("")
}

/**
 * Postgres timestamps arrive as "2026-11-18T10:02:25.972799+00:00". Instant.parse only accepts a trailing "Z"
 * on older Java runtimes (Android's own and the desugared one), so the offset form must go through OffsetDateTime.
 */
internal fun ts(value: String): Instant = OffsetDateTime.parse(value).toInstant()
