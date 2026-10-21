package com.yaari.chat.data

import com.yaari.chat.domain.ExitPolicy
import com.yaari.chat.domain.Validation
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
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceLine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

/**
 * In-memory server for the demo build and tests, with scripted partners. It applies the same rules as
 * the real server (exit lock, Premium checks, blocks). Partner reactions run inside [events], so they
 * happen on the collector's dispatcher (virtual time under runTest).
 */
open class FakeBackend(
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = LocalDate::now,
    /** UI tests: no artificial network/partner delays. */
    private val instant: Boolean = false,
    /** Voice rooms talk, chat and react by themselves (the demo); tests drive them through [voice] instead. */
    voiceAmbient: Boolean = !instant,
) : Backend {
    private suspend fun wait(ms: Long) {
        if (!instant) delay(ms)
    }

    private sealed interface Outgoing {
        data class Text(val text: String?) : Outgoing
        data object VideoRequest : Outgoing
    }

    private class FakeMatch(val partner: Profile, val startedAt: Long, val dm: Boolean = false) {
        val messages = mutableListOf<Message>()
        val outgoing = Channel<Outgoing>(Channel.UNLIMITED)
        var ended = false
        var video = VideoStatus.NONE
    }

    private val accounts = mutableMapOf<String, Profile?>() // phone -> profile (null until created)
    private var phone: String? = null
    private var entitlements = Entitlements()
    private val matches = linkedMapOf<String, FakeMatch>()
    private val blocked = mutableSetOf<String>()
    private var lastPartner: String? = null
    private var nextId = 0L
    /** Pool id -> state. One person has already asked, so the demo shows a request. */
    private val friendships = linkedMapOf("p3" to FriendState.INCOMING)

    private fun me() = phone?.let { accounts[it] } ?: throw BackendException("not_signed_in")

    /** The demo's voice rooms (and the hooks tests drive them with). */
    val voice = FakeVoice(::me, random, clock, ::wait, voiceAmbient)
    private fun match(id: String) = matches[id] ?: throw BackendException("not_found")

    override suspend fun sendOtp(phone: String) = wait(500)

    override suspend fun verifyOtp(phone: String, code: String): Boolean {
        wait(300)
        if (code != DEMO_OTP) return false
        this.phone = phone
        accounts.putIfAbsent(phone, null) // one phone number = one account
        return true
    }

    override suspend fun isSignedIn() = phone != null
    override suspend fun myProfile() = phone?.let { accounts[it] }

    override suspend fun createProfile(p: NewProfile): Profile {
        val ph = phone ?: throw BackendException("not_signed_in")
        if (accounts[ph] != null) throw BackendException("profile_exists")
        val age = Validation.age(p.birthDate, today())
        if (age < Validation.MIN_AGE) throw BackendException("under_18")
        val country = com.yaari.chat.domain.Countries.all.filter { ph.startsWith(it.dial) }.maxByOrNull { it.dial.length }
            ?.let { if (it.dial == "1") "US" else it.iso } // +1 is shared; the server does the same
        val me = Profile("me-" + UUID.randomUUID(), p.name, p.gender, age, p.city, p.bio, p.photoUri, ph.hashCode(), country = country)
        accounts[ph] = me
        return me
    }

    override suspend fun entitlements() = entitlements

    override suspend fun redeem(productId: String, purchaseToken: String): Entitlements {
        entitlements = when (productId) {
            Products.PREMIUM -> entitlements.copy(premium = true)
            Products.GENDER_PASS -> entitlements.copy(genderPass = true)
            else -> throw BackendException("bad_request")
        }
        return entitlements
    }

    override suspend fun deleteAccount() {
        val ph = phone ?: throw BackendException("not_signed_in")
        accounts.remove(ph)
        matches.clear()
        friendships.clear()
        entitlements = Entitlements()
        lastPartner = null
        phone = null
    }

    override suspend fun signOut() {
        phone = null
    }

    override suspend fun findMatch(target: Target): Match? {
        val me = me()
        if (target.isPaid && !entitlements.canChooseGender) throw BackendException("premium_required")
        matches.values.filter { !it.ended }.forEach { m ->
            if (clock() - m.startedAt < ExitPolicy.lockMs(entitlements.premium)) throw BackendException("exit_locked")
            m.ended = true
        }
        wait(random.nextLong(900, 2200))
        val partner = POOL.filter { target.accepts(it.gender) && it.id != me.id && it.id !in blocked && it.id != lastPartner }
            .randomOrNull(random) ?: return null
        val id = UUID.randomUUID().toString()
        matches[id] = FakeMatch(partner, clock())
        lastPartner = partner.id
        return Match(id, partner)
    }

    override suspend fun cancelSearch() = Unit

    /** Scripted partners never wait in a queue: every match comes from polling. */
    override fun matchSignals(): Flow<Unit> = emptyFlow()

    /** Scripted partners are always connected; [live] changes nothing here. */
    override fun events(matchId: String, live: Boolean, afterId: Long?): Flow<MatchEvent> = flow {
        val m = match(matchId)
        for (out in m.outgoing) {
            when (out) {
                is Outgoing.Text -> {
                    // The partner opens the chat before answering: my messages so far show as read.
                    m.messages.lastOrNull { it.fromMe }?.let { emit(MatchEvent.Read(it.id)) }
                    emit(MatchEvent.Typing(true))
                    wait(random.nextLong(700, 1800))
                    val roll = random.nextInt(100)
                    emit(MatchEvent.Typing(false))
                    when {
                        !m.ended && roll < 6 && m.video == VideoStatus.NONE -> {
                            m.ended = true
                            emit(MatchEvent.PartnerLeft)
                        }
                        roll < 15 -> Unit
                        !m.ended && out.text?.contains("video", ignoreCase = true) == true && m.video == VideoStatus.NONE -> {
                            m.video = VideoStatus.REQUESTED_BY_PARTNER // partner is Premium and asks for a call
                            emit(MatchEvent.Video(m.video))
                        }
                        else -> {
                            val text = if (out.text?.contains('?') == true) ANSWERS.random(random) else REPLIES.random(random)
                            val msg = Message(nextId++, false, text, null, clock())
                            m.messages += msg
                            emit(MatchEvent.NewMessage(msg))
                        }
                    }
                }
                Outgoing.VideoRequest -> {
                    wait(random.nextLong(1500, 3000))
                    if (m.ended || m.video != VideoStatus.REQUESTED_BY_ME) continue
                    m.video = if (random.nextInt(100) < 70) VideoStatus.ACCEPTED else VideoStatus.DECLINED
                    emit(MatchEvent.Video(m.video))
                }
            }
        }
    }

    override suspend fun sendTyping(matchId: String) = Unit

    override suspend fun history(matchId: String, afterId: Long?): List<Message> {
        val all = match(matchId).messages.toList()
        return if (afterId == null) all.takeLast(200) else all.filter { it.id > afterId }.take(200)
    }

    override suspend fun send(matchId: String, text: String?, media: Media?): Message {
        val m = match(matchId)
        if (m.partner.id in blocked) throw BackendException("blocked")
        val msg = Message(nextId++, true, text, media, clock())
        m.messages += msg
        m.outgoing.trySend(Outgoing.Text(text)) // after the chat ends this is a DM; the partner still answers
        return msg
    }

    /** Scripted partners don't look at read ticks. */
    override suspend fun markRead(matchId: String, upto: Long) = Unit

    override suspend fun leave(matchId: String) {
        val m = match(matchId)
        if (m.ended) return
        if (clock() - m.startedAt < ExitPolicy.lockMs(entitlements.premium)) throw BackendException("exit_locked")
        m.ended = true
    }

    override suspend fun report(partnerId: String, reason: ReportReason, matchId: String?) = block(partnerId)

    override suspend fun block(userId: String) {
        blocked += userId
        friendships.remove(userId)
        matches.values.filter { it.partner.id == userId }.forEach { it.ended = true }
    }

    override suspend fun requestVideo(matchId: String) {
        if (!entitlements.premium) throw BackendException("premium_required")
        val m = match(matchId)
        if (m.ended || m.video !in setOf(VideoStatus.NONE, VideoStatus.DECLINED)) throw BackendException("not_allowed")
        m.video = VideoStatus.REQUESTED_BY_ME
        m.outgoing.trySend(Outgoing.VideoRequest)
    }

    override suspend fun answerVideo(matchId: String, accept: Boolean) {
        val m = match(matchId)
        if (m.video != VideoStatus.REQUESTED_BY_PARTNER) throw BackendException("not_allowed")
        m.video = if (accept) VideoStatus.ACCEPTED else VideoStatus.DECLINED
    }

    override suspend fun endVideo(matchId: String) {
        match(matchId).video = VideoStatus.NONE
    }

    override suspend fun videoToken(matchId: String): VideoToken? = null

    override suspend fun flagNudity(matchId: String) = Unit

    override suspend fun recents(): List<RecentMatch> =
        matches.entries.reversed().filter { !it.value.dm }.distinctBy { it.value.partner.id }
            .filter { it.value.partner.id !in blocked }
            .map { (id, m) -> RecentMatch(id, m.partner, m.startedAt, m.messages.lastOrNull()?.let { it.text ?: "[media]" }) }

    override suspend fun profile(userId: String): Profile? {
        if (userId in blocked) return null
        if (userId == phone?.let { accounts[it] }?.id) return me()
        voice.profileOf(userId)?.let { return it } // people met in a voice room
        if (!visible(userId)) return null
        return POOL.firstOrNull { it.id == userId }
    }

    private fun visible(userId: String) = userId !in blocked &&
        (matches.values.any { it.partner.id == userId } || userId in friendships)

    override suspend fun addPost(text: String, imageUri: String?): Post {
        val ph = phone ?: throw BackendException("not_signed_in")
        val post = Post(UUID.randomUUID().toString(), text, imageUri, clock())
        accounts[ph] = me().let { it.copy(posts = listOf(post) + it.posts) }
        return post
    }

    override suspend fun updateProfile(name: String, city: String, bio: String, newPhotoUri: String?): Profile {
        val ph = phone ?: throw BackendException("not_signed_in")
        wait(300)
        val updated = me().copy(name = name, city = city, bio = bio, photoUri = newPhotoUri ?: me().photoUri)
        accounts[ph] = updated
        return updated
    }

    // ---- friends: scripted people accept a request the next time the list loads ----

    override suspend fun friends(): List<Friend> {
        me()
        friendships.entries.filter { it.value == FriendState.REQUESTED }.forEach { it.setValue(FriendState.FRIENDS) }
        return friendships.entries.reversed().filter { it.key !in blocked }.mapNotNull { (id, state) ->
            POOL.firstOrNull { it.id == id }?.let { p ->
                Friend(p, state, matches.entries.lastOrNull { it.value.partner.id == id }?.key)
            }
        }
    }

    override suspend fun requestFriend(userId: String): FriendState {
        me()
        if (!visible(userId)) throw BackendException("not_allowed")
        val now = when (friendships[userId]) {
            FriendState.INCOMING, FriendState.FRIENDS -> FriendState.FRIENDS
            else -> FriendState.REQUESTED
        }
        friendships[userId] = now
        return now
    }

    override suspend fun answerFriend(userId: String, accept: Boolean) {
        if (friendships[userId] != FriendState.INCOMING) throw BackendException("not_found")
        if (accept) friendships[userId] = FriendState.FRIENDS else friendships.remove(userId)
    }

    override suspend fun removeFriend(userId: String) {
        friendships.remove(userId)
    }

    override suspend fun friendThread(userId: String): String {
        if (friendships[userId] != FriendState.FRIENDS || userId in blocked) throw BackendException("not_allowed")
        matches.entries.lastOrNull { it.value.partner.id == userId }?.let { return it.key }
        val partner = POOL.first { it.id == userId }
        val id = UUID.randomUUID().toString()
        matches[id] = FakeMatch(partner, clock(), dm = true).apply { ended = true }
        return id
    }

    // ---- rooms (FakeVoice) ----

    override suspend fun voiceRooms() = voice.list().filter { it.host.id !in blocked }
    override suspend fun createVoiceRoom(room: NewVoiceRoom) = voice.create(room)
    override suspend fun joinVoiceCode(code: String) = voice.joinCode(code)
    override suspend fun updateVoiceRoom(roomId: String, title: String, announcement: String) = voice.settings(roomId, title, announcement)
    override fun voiceSession(roomId: String) = voice.session(roomId)
    override suspend fun leaveVoice(roomId: String) = voice.leave(roomId)
    override suspend fun endVoice(roomId: String) = voice.endRoom(roomId)
    override suspend fun voiceLines(roomId: String) = voice.lines(roomId).filter {
        (it !is VoiceLine.Text || it.sender.id !in blocked) && (it !is VoiceLine.Photo || it.sender.id !in blocked)
    }
    override suspend fun sendVoice(roomId: String, text: String) = voice.send(roomId, text)
    /** The demo keeps the photo on the phone: the line shows it from where it is. */
    override suspend fun sendVoicePhoto(roomId: String, jpegUri: String) = voice.photo(roomId, jpegUri)
    override suspend fun voiceIcebreaker(roomId: String) = voice.icebreaker(roomId)
    override suspend fun voiceLeaders(roomId: String) = voice.leaders(roomId)
    /** The demo's videos carry their titles; anything else is "YouTube video". */
    override suspend fun videoTitle(videoId: String) = FakeVoice.TRACKS[videoId]?.first.orEmpty()
    override suspend fun voiceAction(roomId: String, action: VoiceAction) = voice.act(roomId, action)

    override suspend fun reportVoiceRoom(roomId: String, reason: ReportReason) {
        val host = voice.hostOf(roomId) ?: throw BackendException("not_found")
        if (host == me().id) throw BackendException("not_allowed")
        block(host)
    }

    // ---- Ludo in a voice room (FakeLudo) ----

    override suspend fun ludoGame(roomId: String) = voice.ludo.game(roomId)
    override fun ludoGames(roomId: String) = voice.ludo.follow(roomId)
    override suspend fun ludoAction(roomId: String, gameId: String?, action: LudoAction) = voice.ludo.act(roomId, gameId, action)

    companion object {
        const val DEMO_OTP = "123456"

        private val REPLIES = listOf(
            "Hii 😊", "Where are you from?", "Haha nice", "What do you do?", "Same here!",
            "Kya haal hai?", "I love chai ☕", "Just finished college today", "Nice profile btw",
            "Which music do you like?", "Arre wah 😄", "Cool, tell me more",
        )
        private val ANSWERS = listOf(
            "Yes 🙂", "Not really haha", "I'm from Pune", "Studying engineering",
            "Maybe! You?", "Bollywood all the way", "Cricket obviously 🏏",
        )

        private fun profile(id: Int, name: String, g: Gender, age: Int, city: String, country: String, bio: String, vararg posts: String) =
            Profile(
                id = "p$id", name = name, gender = g, age = age, city = city, bio = bio, colorSeed = id, country = country,
                posts = posts.mapIndexed { i, t -> Post("p$id-$i", t, createdAt = 1_760_000_000_000L - i * 86_400_000L) },
            )

        val POOL = listOf(
            profile(1, "Priya", Gender.FEMALE, 22, "Mumbai", "IN", "Chai, books & sunsets 🌅", "Marine Drive evenings", "New cafe find!", "Monsoon mood"),
            profile(2, "Lucas", Gender.MALE, 25, "São Paulo", "BR", "Football and coding", "Weekend match ⚽", "Office view"),
            profile(3, "Ananya", Gender.FEMALE, 24, "Bengaluru", "IN", "Dancer. Dog mom 🐶", "Rehearsal day", "Bruno turned 3!"),
            profile(4, "Minjun", Gender.MALE, 23, "Seoul", "KR", "Learning English, teaching Korean", "Han river night", "Best tteokbokki"),
            profile(5, "Sofia", Gender.FEMALE, 27, "Madrid", "ES", "Tapas & travel ✈️", "Retiro park", "Lisbon trip"),
            profile(6, "Vikram", Gender.MALE, 29, "Jaipur", "IN", "Photographer 📷", "Golden hour at Amer", "Street portraits"),
            profile(7, "Emma", Gender.FEMALE, 23, "London", "GB", "Indie music & long walks", "Camden Saturday", "New vinyl!"),
            profile(8, "Kenji", Gender.MALE, 26, "Osaka", "JP", "Ramen hunter 🍜", "Dotonbori lights", "Homemade gyoza"),
            profile(9, "Aisha", Gender.FEMALE, 20, "Dubai", "AE", "Poetry & desert sunsets", "A new poem", "Marina walk"),
            profile(10, "Rohan", Gender.MALE, 24, "Delhi", "IN", "Startup dreams", "Pitch day!", "Diwali lights"),
            profile(11, "Maria", Gender.FEMALE, 25, "Manila", "PH", "Karaoke queen 🎤", "Beach weekend"),
            profile(12, "Daniel", Gender.MALE, 22, "Berlin", "DE", "Techno, bikes, coffee", "Sunday flea market"),
        )
    }
}
