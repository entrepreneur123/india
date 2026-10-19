package com.yaari.chat.live

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.yaari.chat.BuildConfig
import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.Entitlements
import com.yaari.chat.data.Match
import com.yaari.chat.data.MatchEvent
import com.yaari.chat.data.NewProfile
import com.yaari.chat.data.OrphanMatchException
import com.yaari.chat.data.VideoStatus
import com.yaari.chat.data.VideoToken
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Media
import com.yaari.chat.model.MediaKind
import com.yaari.chat.model.Message
import com.yaari.chat.model.Post
import com.yaari.chat.model.Profile
import com.yaari.chat.model.RecentMatch
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.Target
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
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours

/** Live server: Supabase project "yaari-chat" (Mumbai). All rules are re-checked in Postgres (server/migrations). */
class SupabaseBackend(private val app: Application, persistSession: Boolean = true) : Backend {
    private val client = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
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

    @Serializable private data class ProfileRow(
        val id: String, val name: String, val gender: String, @SerialName("birth_date") val birthDate: String,
        val city: String = "India", val bio: String = "", @SerialName("photo_path") val photoPath: String? = null,
    )
    @Serializable private data class CardRow(
        val id: String, val name: String, val gender: String, val age: Int, val city: String, val bio: String,
        @SerialName("photo_path") val photoPath: String? = null,
    )
    @Serializable private data class RecentRow(
        @SerialName("match_id") val matchId: String, @SerialName("met_at") val metAt: String,
        @SerialName("last_body") val lastBody: String? = null,
        val id: String, val name: String, val gender: String, val age: Int, val city: String, val bio: String,
        @SerialName("photo_path") val photoPath: String? = null,
    )
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

    /** Maps database/function errors to the rule codes the app understands. */
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: RestException) {
        val text = listOfNotNull(e.message, e.error, e.description).joinToString(" ")
        throw BackendException(if ("row-level security" in text) "blocked" else CODES.firstOrNull { it in text } ?: "network")
    }

    // ---- account ----

    override suspend fun sendOtp(phone: String) = call { client.auth.signInWith(OTP) { this.phone = "+91$phone" } }

    override suspend fun verifyOtp(phone: String, code: String): Boolean = runCatching {
        client.auth.verifyPhoneOtp(type = OtpType.Phone.SMS, phone = "+91$phone", token = code)
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
        toProfile(CardRow(row.id, row.name, row.gender, age, row.city, row.bio, row.photoPath), posts(row.id))
    }

    override suspend fun createProfile(p: NewProfile): Profile = call {
        val photo = p.photoUri?.let { upload(it.toUri(), "avatar") }
        client.postgrest["profiles"].insert(
            ProfileRow(uid, p.name, p.gender.name.lowercase(), p.birthDate.toString(), p.city, p.bio, photo),
        )
        myProfile() ?: throw BackendException("network")
    }

    override suspend fun entitlements(): Entitlements = call {
        val row = client.postgrest.rpc("my_entitlements").decodeList<EntRow>().firstOrNull() ?: return@call Entitlements()
        val now = Instant.now()
        Entitlements(
            premium = row.premiumUntil?.let { Instant.parse(it).isAfter(now) } == true,
            genderPass = row.genderUntil?.let { Instant.parse(it).isAfter(now) } == true,
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

    override suspend fun findMatch(target: Target): Match? = call {
        val raw = client.postgrest.rpc("find_match", buildJsonObject { put("p_target", target.name.lowercase()) }).data.trim()
        if (raw == "null" || raw.isEmpty()) return@call null
        val id = json.decodeFromString<String>(raw)
        try {
            val m = matchRow(id) ?: throw BackendException("not_found")
            val partnerId = if (m.userA == uid) m.userB else m.userA
            Match(id, card(partnerId) ?: throw BackendException("not_found"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The server already paired us; the app ends that chat (it knows whether the user opened it).
            throw OrphanMatchException(id)
        }
    }

    override suspend fun cancelSearch() = call { client.postgrest.rpc("cancel_search"); Unit }

    /** Null when the match is gone (the other person deleted their account). */
    private suspend fun matchRow(id: String) =
        client.postgrest["matches"].select { filter { eq("id", id) } }.decodeSingleOrNull<MatchRow>()

    private fun videoOf(m: MatchRow, me: String) = when (m.videoStatus) {
        "requested" -> if (m.videoRequestedBy == me) VideoStatus.REQUESTED_BY_ME else VideoStatus.REQUESTED_BY_PARTNER
        "accepted" -> VideoStatus.ACCEPTED
        "declined" -> VideoStatus.DECLINED
        else -> VideoStatus.NONE
    }

    override fun events(matchId: String): Flow<MatchEvent> = channelFlow {
        val me = uid
        var lastVideo: VideoStatus? = null
        var leftSent = false
        suspend fun onMatch(m: MatchRow?) {
            if (m == null) { // deleted: treat as the partner leaving
                if (!leftSent) {
                    leftSent = true
                    send(MatchEvent.PartnerLeft)
                }
                return
            }
            if (m.endedAt != null && m.endedBy != me && !leftSent) {
                leftSent = true
                send(MatchEvent.PartnerLeft)
            }
            val v = videoOf(m, me)
            if (v != lastVideo) {
                lastVideo = v
                send(MatchEvent.Video(v))
            }
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
        // One bad record (or a failed media link) must not stop the chat: skip it, the 10 s re-sync retries.
        launch { inserts.collect { a -> runCatching { toMessage(a.decodeRecord<MessageRow>(), me) }.onSuccess { send(MatchEvent.NewMessage(it)) } } }

        // Typing signals ride a broadcast channel shared by the two participants (not stored anywhere).
        val typingChannel = client.channel("typing-$matchId")
        typingChannels[matchId] = typingChannel
        var typingOff: Job? = null
        launch {
            typingChannel.broadcastFlow<JsonObject>("typing").collect { msg ->
                if (msg["from"]?.jsonPrimitive?.content == me) return@collect
                send(MatchEvent.Typing(true))
                typingOff?.cancel()
                typingOff = launch { delay(4_000); send(MatchEvent.Typing(false)) }
            }
        }
        launch { updates.collect { a -> runCatching { a.decodeRecord<MatchRow>() }.onSuccess { onMatch(it) } } }
        try {
            channel.subscribe(blockUntilSubscribed = true)
            typingChannel.subscribe()
            // Safety net if the realtime socket drops on a weak network: re-sync every 10 s (the app de-duplicates).
            while (true) {
                runCatching {
                    onMatch(matchRow(matchId))
                    history(matchId).forEach { send(MatchEvent.NewMessage(it)) }
                }
                delay(10_000)
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

    override suspend fun history(matchId: String): List<Message> = call {
        val me = uid
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

    override suspend fun leave(matchId: String) = call { client.postgrest.rpc("leave_match", buildJsonObject { put("p_match", matchId) }); Unit }

    override suspend fun report(partnerId: String, reason: ReportReason, matchId: String?) = call {
        client.postgrest.rpc("report_user", buildJsonObject {
            put("p_reported", partnerId)
            put("p_reason", reason.code)
            matchId?.let { put("p_match", it) }
        })
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

    // ---- profiles ----

    override suspend fun recents(): List<RecentMatch> = call {
        client.postgrest.rpc("recent_cards").decodeList<RecentRow>().map { r ->
            RecentMatch(
                r.matchId, toProfile(CardRow(r.id, r.name, r.gender, r.age, r.city, r.bio, r.photoPath), emptyList()),
                Instant.parse(r.metAt).toEpochMilli(), r.lastBody,
            )
        }
    }

    override suspend fun profile(userId: String): Profile? = call {
        val c = client.postgrest.rpc("profile_card", buildJsonObject { put("uid", userId) }).decodeList<CardRow>().firstOrNull()
            ?: return@call null
        toProfile(c, posts(userId))
    }

    private suspend fun card(userId: String): Profile? =
        client.postgrest.rpc("profile_card", buildJsonObject { put("uid", userId) }).decodeList<CardRow>().firstOrNull()?.let { toProfile(it, posts(userId)) }

    private suspend fun posts(userId: String): List<Post> =
        client.postgrest["posts"].select {
            filter { eq("user_id", userId) }
            order("created_at", Order.DESCENDING)
            limit(60)
        }.decodeList<PostRow>().map { Post(it.id, it.body, it.imagePath?.let { p -> signed(p) }, Instant.parse(it.createdAt).toEpochMilli()) }

    override suspend fun addPost(text: String, imageUri: String?): Post = call {
        val path = imageUri?.let { upload(it.toUri(), "posts") }
        val row = client.postgrest["posts"].insert(NewPostRow(text, path)) { select() }.decodeSingle<PostRow>()
        Post(row.id, row.body, imageUri, Instant.parse(row.createdAt).toEpochMilli())
    }

    // ---- mapping & media ----

    private suspend fun toProfile(c: CardRow, posts: List<Post>) = Profile(
        id = c.id, name = c.name, gender = Gender.valueOf(c.gender.uppercase()), age = c.age, city = c.city, bio = c.bio,
        photoUri = c.photoPath?.let { signed(it) }, colorSeed = c.id.hashCode(), posts = posts,
    )

    private suspend fun toMessage(r: MessageRow, me: String) = Message(
        id = r.id, fromMe = r.sender == me, text = r.body,
        media = r.mediaPath?.let { Media(signed(it), if (r.mediaKind == "video") MediaKind.VIDEO else MediaKind.IMAGE) },
        at = Instant.parse(r.createdAt).toEpochMilli(),
    )

    private suspend fun signed(path: String): String {
        val now = System.currentTimeMillis()
        signedUrls[path]?.let { (url, reuseUntil) -> if (now < reuseUntil) return url }
        val url = client.storage.from(BUCKET).createSignedUrl(path, 12.hours)
        signedUrls[path] = url to now + 11 * 3_600_000L
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
        client.storage.from(BUCKET).upload(path, bytes) { contentType = type }
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
        private val CODES = listOf(
            "exit_locked", "premium_required", "banned", "under_18", "slow_down", "not_allowed", "not_found",
            "video_not_configured", "billing_not_configured", "already_redeemed", "wrong_account",
        )
    }
}
