package com.yaari.chat.live

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.yaari.chat.AppLocale
import com.yaari.chat.BuildConfig
import com.yaari.chat.data.AppForeground
import com.yaari.chat.data.Backend
import com.yaari.chat.data.awaitVisible
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.Entitlements
import com.yaari.chat.data.Match
import com.yaari.chat.data.MatchEvent
import com.yaari.chat.data.NewProfile
import com.yaari.chat.data.OrphanMatchException
import com.yaari.chat.data.PartnerWatch
import com.yaari.chat.data.VideoStatus
import com.yaari.chat.data.VideoToken
import com.yaari.chat.model.CreatedRoom
import com.yaari.chat.model.Friend
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Gender
import com.yaari.chat.model.GenderRule
import com.yaari.chat.model.Media
import com.yaari.chat.model.MediaKind
import com.yaari.chat.model.Message
import com.yaari.chat.model.NewRoom
import com.yaari.chat.model.Post
import com.yaari.chat.model.Profile
import com.yaari.chat.model.RecentMatch
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.Room
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.RoomMessage
import com.yaari.chat.model.Target
import com.yaari.chat.data.ServerClock
import com.yaari.chat.model.AudioStatus
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.SpeakMode
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceAudioState
import com.yaari.chat.model.VoiceEvent
import com.yaari.chat.model.VoiceLine
import com.yaari.chat.model.VoiceMember
import com.yaari.chat.model.VoiceQueueItem
import com.yaari.chat.model.VoiceReaction
import com.yaari.chat.model.VoiceRole
import com.yaari.chat.model.VoiceRoom
import com.yaari.chat.model.VoiceRoomSummary
import com.yaari.chat.model.VoiceSeat
import com.yaari.chat.model.VoiceSystemEvent
import com.yaari.chat.model.VoiceTrack
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
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

    @Serializable private data class ProfileRow(
        val id: String, val name: String, val gender: String, @SerialName("birth_date") val birthDate: String,
        val city: String = "India", val bio: String = "", @SerialName("photo_path") val photoPath: String? = null,
        val country: String? = null,
    )
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
    @Serializable private data class RoomRow(
        val id: String, val title: String, val emoji: String, val online: Int,
        val official: Boolean = true, val mine: Boolean = false, @SerialName("owner_name") val ownerName: String? = null,
        val category: String? = null, @SerialName("max_members") val maxMembers: Int? = null,
        @SerialName("gender_rule") val genderRule: String? = null, @SerialName("min_age") val minAge: Int = Room.MIN_AGE,
        @SerialName("max_age") val maxAge: Int = Room.MAX_AGE, @SerialName("is_private") val isPrivate: Boolean = false,
        val code: String? = null, val refusal: String? = null,
    )
    @Serializable private data class CreatedRoomRow(@SerialName("room_id") val roomId: String, @SerialName("room_code") val roomCode: String)
    @Serializable private data class RoomMessageRow(
        val id: Long, @SerialName("room_id") val roomId: String, val sender: String,
        @SerialName("sender_name") val senderName: String, @SerialName("sender_gender") val senderGender: String,
        @SerialName("sender_age") val senderAge: Int, @SerialName("sender_photo") val senderPhoto: String? = null,
        @SerialName("sender_country") val senderCountry: String? = null, val body: String,
        @SerialName("created_at") val createdAt: String,
    )
    @Serializable private data class NewRoomMessageRow(@SerialName("room_id") val roomId: String, val body: String)
    @Serializable private data class PostRow(
        val id: String, @SerialName("user_id") val userId: String, val body: String,
        @SerialName("image_path") val imagePath: String? = null, @SerialName("created_at") val createdAt: String,
    )
    @Serializable private data class NewPostRow(val body: String, @SerialName("image_path") val imagePath: String?)
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
        toProfile(CardRow(row.id, row.name, row.gender, age, row.city, row.bio, row.photoPath, row.country), posts(row.id))
    }

    override suspend fun createProfile(p: NewProfile): Profile = call {
        val photo = p.photoUri?.let { upload(it.toUri(), "avatar") }
        // Country is set by the server from the verified phone number.
        client.postgrest["profiles"].insert(
            NewProfileRow(uid, p.name, p.gender.name.lowercase(), p.birthDate.toString(), p.city, p.bio, photo),
        )
        myProfile() ?: throw BackendException("network")
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
            Match(id, toProfile(c.copy(photoPath = null), emptyList()).copy(photoUri = photo))
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
        toProfile(c, posts(userId))
    }

    private suspend fun posts(userId: String): List<Post> =
        client.postgrest["posts"].select {
            filter { eq("user_id", userId) }
            order("created_at", Order.DESCENDING)
            limit(60)
        }.decodeList<PostRow>().map { Post(it.id, it.body, it.imagePath?.let { p -> signed(p) }, ts(it.createdAt).toEpochMilli()) }

    override suspend fun addPost(text: String, imageUri: String?): Post = call {
        val path = imageUri?.let { upload(it.toUri(), "posts") }
        val row = client.postgrest["posts"].insert(NewPostRow(text, path)) { select() }.decodeSingle<PostRow>()
        Post(row.id, row.body, imageUri, ts(row.createdAt).toEpochMilli())
    }

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

    // ---- rooms ----

    override suspend fun rooms(): List<Room> = call {
        client.postgrest.rpc("room_list2").decodeList<RoomRow>().map {
            Room(
                it.id, it.title, it.emoji, it.online, official = it.official, mine = it.mine, ownerName = it.ownerName,
                category = RoomCategory.of(it.category), maxMembers = it.maxMembers, genderRule = GenderRule.of(it.genderRule),
                minAge = it.minAge, maxAge = it.maxAge, isPrivate = it.isPrivate, code = it.code, refusal = it.refusal,
            )
        }
    }

    override suspend fun createRoom(room: NewRoom): CreatedRoom = call {
        val row = client.postgrest.rpc("create_room", buildJsonObject {
            put("p_title", room.title)
            put("p_category", room.category.code)
            put("p_max", room.maxMembers)
            put("p_gender", room.genderRule.code)
            put("p_min_age", room.minAge)
            put("p_max_age", room.maxAge)
            put("p_private", room.isPrivate)
        }).decodeList<CreatedRoomRow>().firstOrNull() ?: throw BackendException("network")
        CreatedRoom(row.roomId, row.roomCode)
    }

    /** A wrong code comes back as null (the server counts it; 10 misses in 10 minutes → slow_down). */
    override suspend fun joinRoomCode(code: String): String = call {
        json.decodeFromString<String?>(client.postgrest.rpc("join_room_code", buildJsonObject { put("p_code", code) }).data.trim())
    } ?: throw BackendException("not_found")

    override suspend fun closeRoom(roomId: String) = call { client.postgrest.rpc("close_room", buildJsonObject { put("p_room", roomId) }); Unit }

    override suspend fun joinRoom(roomId: String) = call { client.postgrest.rpc("join_room", buildJsonObject { put("p_room", roomId) }); Unit }

    override suspend fun leaveRoom(roomId: String) = call { client.postgrest.rpc("leave_room", buildJsonObject { put("p_room", roomId) }); Unit }

    override suspend fun roomHistory(roomId: String, afterId: Long?): List<RoomMessage> = call {
        val me = uid
        if (afterId != null) { // re-sync: only what came after, oldest first
            return@call client.postgrest["room_messages"].select {
                filter {
                    eq("room_id", roomId)
                    gt("id", afterId)
                }
                order("id", Order.ASCENDING)
                limit(200)
            }.decodeList<RoomMessageRow>().map { toRoomMessage(it, me) }
        }
        client.postgrest["room_messages"].select {
            filter { eq("room_id", roomId) }
            order("id", Order.DESCENDING) // the newest 100, shown oldest first
            limit(100)
        }.decodeList<RoomMessageRow>().asReversed().map { toRoomMessage(it, me) }
    }

    override fun roomEvents(roomId: String, afterId: Long?): Flow<RoomMessage> = channelFlow {
        val me = uid
        val channel = client.channel("room-$roomId-${UUID.randomUUID()}")
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>("public") {
            table = "room_messages"
            filter("room_id", FilterOperator.EQ, roomId)
        }
        launch { inserts.collect { a -> runCatching { toRoomMessage(a.decodeRecord<RoomMessageRow>(), me) }.onSuccess { send(it) } } }
        // Joined (again, after a dropped socket): re-sync at once.
        val resync = Channel<Unit>(Channel.CONFLATED)
        launch { channel.joined().collect { up -> if (up) resync.trySend(Unit) } }
        try {
            channel.subscribe()
            // Re-sync every 15 s, only messages newer than the newest seen (the app de-duplicates by id), only while
            // the app is on screen.
            var cursor = afterId
            while (true) {
                withTimeoutOrNull(ROOM_RESYNC_MS) { resync.receive() }
                AppForeground.visible.awaitVisible()
                runCatching {
                    val newer = roomHistory(roomId, cursor)
                    newer.forEach { send(it) }
                    newer.maxOfOrNull { it.id }?.let { id -> cursor = maxOf(id, cursor ?: id) }
                }
            }
        } finally {
            withContext(NonCancellable) { runCatching { client.realtime.removeChannel(channel) } }
        }
    }

    override suspend fun sendRoom(roomId: String, text: String): RoomMessage = call {
        val row = client.postgrest["room_messages"].insert(NewRoomMessageRow(roomId, text)) { select() }.decodeSingle<RoomMessageRow>()
        toRoomMessage(row, uid)
    }

    /** A photo the viewer may no longer see (that person left the room long ago) shows the initial instead. */
    private suspend fun toRoomMessage(r: RoomMessageRow, me: String) = RoomMessage(
        id = r.id,
        sender = Profile(
            id = r.sender, name = r.senderName,
            gender = runCatching { Gender.valueOf(r.senderGender.uppercase()) }.getOrDefault(Gender.OTHER),
            age = r.senderAge, city = "", bio = "", photoUri = r.senderPhoto?.let { p -> runCatching { signed(p) }.getOrNull() },
            colorSeed = r.sender.hashCode(), country = r.senderCountry,
        ),
        fromMe = r.sender == me, text = r.body, at = ts(r.createdAt).toEpochMilli(),
    )

    // ---- voice rooms (017_voice_rooms.sql; audio through LiveKit, VoiceAudio.kt) ----

    @Serializable private data class VoiceCardRow(
        val id: String, val name: String, val gender: String = "other", val age: Int = 0, val city: String? = null, val bio: String? = null,
        @SerialName("photo_path") val photoPath: String? = null, val country: String? = null,
    )
    @Serializable private data class VoiceListRow(
        val id: String, val title: String, val category: String, val mine: Boolean = false, val host: VoiceCardRow? = null,
        val speakers: List<VoiceCardRow> = emptyList(), val listeners: Int = 0, @SerialName("now_playing") val nowPlaying: String? = null,
    )
    @Serializable private data class VoiceMusicRow(
        val id: String, val videoId: String, val title: String = "", val by: String = "", val startedAt: Long, val pausedAt: Long? = null,
    )
    @Serializable private data class VoiceQueueRow(val id: String, val videoId: String, val title: String = "", val by: String = "", val suggested: Boolean = false)
    @Serializable private data class VoiceMemberRow(
        val id: String, val name: String, val gender: String = "other", val age: Int = 0, val city: String? = null, val bio: String? = null,
        @SerialName("photo_path") val photoPath: String? = null, val country: String? = null,
        val role: String, val seat: Int? = null, val muted: Boolean = true, val hand: Boolean = false,
    )
    /** voice_state(): the room, the people present with their cards, and the server clock (ms). */
    @Serializable private data class VoiceStateRow(
        val id: String, val title: String, val category: String, val mode: String, val host: String, val rev: Long, val now: Long,
        val locked: List<Int> = emptyList(), val music: VoiceMusicRow? = null, val queue: List<VoiceQueueRow> = emptyList(),
        val members: List<VoiceMemberRow> = emptyList(),
    )
    @Serializable private data class VoiceMessageRow(
        val id: Long, @SerialName("room_id") val roomId: String, val sender: String, val kind: String, val body: String? = null,
        val payload: JsonObject? = null, @SerialName("sender_name") val senderName: String,
        @SerialName("sender_gender") val senderGender: String? = null, @SerialName("sender_age") val senderAge: Int? = null,
        @SerialName("sender_photo") val senderPhoto: String? = null, @SerialName("sender_country") val senderCountry: String? = null,
        @SerialName("created_at") val createdAt: String,
    )
    @Serializable private data class VoiceRevRow(val rev: Long = 0, @SerialName("ended_at") val endedAt: String? = null)

    /** What the voice methods need from the room being followed ([voiceSession]). */
    private class VoiceLive(val refresh: () -> Unit, val mic: suspend (Boolean) -> Unit) {
        @Volatile var room: VoiceRoom? = null
    }

    private val voiceLive = ConcurrentHashMap<String, VoiceLive>()
    private val serverClock = ServerClock()
    /** Fire-and-forget requests (voice-sync) that must not die with the screen that started them. */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun voiceRooms(): List<VoiceRoomSummary> = call {
        val rows = client.postgrest.rpc("voice_list").decodeList<VoiceListRow>()
        signMany(rows.flatMap { r -> listOf(r.host?.photoPath) + r.speakers.map { it.photoPath } })
        rows.mapNotNull { r ->
            val host = r.host ?: return@mapNotNull null
            VoiceRoomSummary(
                r.id, r.title, RoomCategory.of(r.category), card(host), r.speakers.map(::card), r.listeners,
                r.nowPlaying?.ifEmpty { null }, r.mine,
            )
        }.sortedByDescending { it.mine }
    }

    override suspend fun createVoiceRoom(room: NewVoiceRoom): String = call {
        json.decodeFromString<String>(client.postgrest.rpc("create_voice_room", buildJsonObject {
            put("p_title", room.title)
            put("p_category", room.category.code)
            put("p_mode", room.mode.code)
        }).data.trim())
    }

    override suspend fun leaveVoice(roomId: String) = call { client.postgrest.rpc("leave_voice", buildJsonObject { put("p_room", roomId) }); Unit }

    override suspend fun endVoice(roomId: String) = call {
        client.postgrest.rpc("end_voice_room", buildJsonObject { put("p_room", roomId) })
        voiceSync(roomId) // LiveKit closes the room for everyone still connected
    }

    override suspend fun voiceLines(roomId: String): List<VoiceLine> = call {
        client.postgrest["voice_messages"].select {
            filter {
                eq("room_id", roomId)
                isIn("kind", listOf("text", "system"))
            }
            order("id", Order.DESCENDING) // the newest 100, shown oldest first
            limit(100)
        }.decodeList<VoiceMessageRow>().asReversed().let { toVoiceLines(it) }
    }

    private suspend fun voiceLinesAfter(roomId: String, afterId: Long): List<VoiceLine> =
        client.postgrest["voice_messages"].select {
            filter {
                eq("room_id", roomId)
                isIn("kind", listOf("text", "system"))
                gt("id", afterId)
            }
            order("id", Order.ASCENDING)
            limit(200)
        }.decodeList<VoiceMessageRow>().let { toVoiceLines(it) }

    override suspend fun sendVoice(roomId: String, text: String): VoiceLine = call {
        val row = client.postgrest.rpc("voice_send", buildJsonObject {
            put("p_room", roomId)
            put("p_text", text)
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
            // The server decides: the host's track plays or queues, anyone else's waits for the host.
            is VoiceAction.AddTrack -> rpc("voice_queue_add") { put("p_video", action.videoId); put("p_title", "") }
            is VoiceAction.Suggest -> rpc("voice_queue_add") { put("p_video", action.videoId); put("p_title", "") }
            is VoiceAction.Approve -> rpc("voice_queue_approve") { put("p_item", action.itemId); put("p_accept", action.accept) }
            is VoiceAction.Play -> rpc("voice_play_item") { put("p_item", action.itemId) }
            is VoiceAction.RemoveItem -> rpc("voice_queue_remove") { put("p_item", action.itemId) }
            VoiceAction.Pause -> rpc("voice_pause")
            VoiceAction.Resume -> rpc("voice_resume")
            is VoiceAction.Skip -> rpc("voice_skip") { (action.trackId ?: live?.room?.track?.id)?.let { put("p_current", it) } }
        }
        live?.refresh?.invoke() // don't wait for the realtime round trip
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
        val refresh = Channel<Unit>(Channel.CONFLATED)
        val permissionsChanged = Channel<Unit>(Channel.CONFLATED)
        var retryAudio: () -> Unit = {}

        // Ended or removed: said once; the collector stops following then.
        suspend fun finish(e: VoiceEvent) {
            if (over) return
            over = true
            audio?.disconnect()
            audio = null
            send(e)
        }
        suspend fun failed(e: Throwable) {
            when ((e as? BackendException)?.code) {
                "room_closed", "not_found" -> finish(VoiceEvent.Ended)
                "voice_removed" -> finish(VoiceEvent.Removed)
            }
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
            if (over || audioJob != null) return
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

        suspend fun apply(state: VoiceStateRow, sent: Long) {
            serverClock.note(state.now, sent, System.currentTimeMillis())
            val room = toVoiceRoom(state, me)
            if (over) return
            rev = maxOf(rev, state.rev)
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
            if (m != null && audio == null) connectAudio()
            send(VoiceEvent.RoomChanged(room))
        }

        suspend fun fetchState() {
            if (over) return
            try {
                val sent = System.currentTimeMillis()
                apply(call { client.postgrest.rpc("voice_state", buildJsonObject { put("p_room", roomId) }).decodeAs<VoiceStateRow>() }, sent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
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

        val live = VoiceLive(refresh = { refresh.trySend(Unit) }, mic = { on -> mic(on) })
        voiceLive[roomId] = live

        retryAudio = {
            launch {
                // A weak network gets a few quick tries, then one every 30 s; the room itself (chat, seats) carries on.
                delay(minOf(30_000L, 2_000L shl minOf(audioTries++, 4)))
                connectAudio()
            }
        }

        val channel = client.channel("voice-$roomId-${UUID.randomUUID()}")
        val roomChanges = channel.postgresChangeFlow<PostgresAction.Update>("public") {
            table = "voice_rooms"
            filter("id", FilterOperator.EQ, roomId)
        }
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>("public") {
            table = "voice_messages"
            filter("room_id", FilterOperator.EQ, roomId)
        }
        try {
            // Enter first: a refusal (ended, removed, blocked) fails the flow before anything else starts.
            val sent = System.currentTimeMillis()
            val state = call { client.postgrest.rpc("join_voice", buildJsonObject { put("p_room", roomId) }).decodeAs<VoiceStateRow>() }
            apply(state, sent)

            launch {
                roomChanges.collect { a ->
                    val row = runCatching { a.decodeRecord<VoiceRevRow>() }.getOrNull() ?: return@collect
                    if (row.endedAt != null) finish(VoiceEvent.Ended) else if (row.rev != rev) refresh.trySend(Unit)
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
                    try {
                        val now = call { client.postgrest.rpc("voice_heartbeat", buildJsonObject { put("p_room", roomId) }).data.trim().toLongOrNull() }
                        if (now != null && now != rev) refresh.trySend(Unit)
                        // The host re-checks LiveKit against the room every minute (anyone a sync missed).
                        if (++beats % 3 == 0 && live.room?.hostId == me) voiceSync(roomId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed(e)
                    }
                }
            }
            // Re-sync after a dropped socket and every 10 s while on screen: missed changes and lines.
            val resync = Channel<Unit>(Channel.CONFLATED)
            launch { channel.joined().collect { up -> if (up) resync.trySend(Unit) } }
            channel.subscribe()
            while (true) {
                withTimeoutOrNull(RESYNC_MS) { resync.receive() }
                AppForeground.visible.awaitVisible()
                runCatching {
                    val row = client.postgrest["voice_rooms"].select { filter { eq("id", roomId) } }.decodeSingleOrNull<VoiceRevRow>()
                    if (row?.endedAt != null) finish(VoiceEvent.Ended) else if (row != null && row.rev != rev) refresh.trySend(Unit)
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
            seats = List(VoiceRoom.SEATS) { VoiceSeat(it, it in s.locked) },
            track = s.music?.let {
                VoiceTrack(it.id, it.videoId, it.title, it.by, serverClock.toLocal(it.startedAt), it.pausedAt?.let(serverClock::toLocal))
            },
            queue = s.queue.map { VoiceQueueItem(it.id, it.videoId, it.title, it.by, it.suggested) },
        )
    }

    private suspend fun toVoiceLines(rows: List<VoiceMessageRow>): List<VoiceLine> {
        val me = uid
        signMany(rows.filter { it.kind == "text" }.map { it.senderPhoto })
        return rows.mapNotNull { r ->
            val at = ts(r.createdAt).toEpochMilli()
            if (r.kind == "text") {
                VoiceLine.Text(
                    r.id, card(VoiceCardRow(r.sender, r.senderName, r.senderGender ?: "other", r.senderAge ?: 0, null, null, r.senderPhoto, r.senderCountry)),
                    r.sender == me, r.body.orEmpty(), at,
                )
            } else {
                val p = r.payload
                val event = VoiceSystemEvent.of(p?.get("event")?.jsonPrimitive?.content) ?: return@mapNotNull null
                VoiceLine.System(
                    r.id, event, p?.get("name")?.jsonPrimitive?.content ?: r.senderName, at,
                    seat = p?.get("seat")?.jsonPrimitive?.content?.toIntOrNull(),
                    title = p?.get("title")?.jsonPrimitive?.content,
                )
            }
        }
    }

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

    // ---- mapping & media ----

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
        private const val BUCKET = "media"
        private const val MAX_PHOTO_PX = 1280
        private const val MAX_VIDEO_BYTES = 25 * 1024 * 1024
        private const val REQUEST_TIMEOUT_MS = 15_000L
        private const val UPLOAD_TIMEOUT_MS = 120_000L
        private const val RESYNC_MS = 10_000L
        private const val ROOM_RESYNC_MS = 15_000L
        private const val REJOIN_SETTLE_MS = 500L
        private const val HEARTBEAT_MS = 20_000L
        // Most specific first: a message is matched by the first code it contains.
        private val CODES = listOf(
            "room_closed", "room_gender", "room_age", "room_full", "room_title", "room_size", "bad_request",
            "exit_locked", "premium_required", "banned", "under_18", "slow_down", "not_allowed", "not_found",
            "video_not_configured", "billing_not_configured", "already_redeemed", "wrong_account",
            "voice_removed", "mic_denied", "queue_full",
        )
    }
}

/**
 * Postgres timestamps arrive as "2026-11-18T10:02:25.972799+00:00". Instant.parse only accepts a trailing "Z"
 * on older Java runtimes (Android's own and the desugared one), so the offset form must go through OffsetDateTime.
 */
internal fun ts(value: String): Instant = OffsetDateTime.parse(value).toInstant()
