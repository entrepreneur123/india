package com.yaari.chat.data

import com.yaari.chat.domain.ExitPolicy
import com.yaari.chat.domain.Validation
import com.yaari.chat.model.CreatedRoom
import com.yaari.chat.model.Friend
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Gender
import com.yaari.chat.model.GenderRule
import com.yaari.chat.model.Media
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
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceLine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
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
    private val roomMessages = mutableMapOf<String, MutableList<RoomMessage>>()
    private val roomOutgoing = mutableMapOf<String, Channel<String>>()
    private val seenInRooms = mutableSetOf<String>()

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
        accounts[ph]?.let { me -> // my rooms go with the account, like the server's cascade
            userRooms.values.removeAll { it.owner.id == me.id }
            userRooms.values.forEach { it.members.remove(me.id) }
        }
        accounts.remove(ph)
        matches.clear()
        friendships.clear()
        roomMessages.values.forEach { list -> list.removeAll { it.fromMe } }
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
        (matches.values.any { it.partner.id == userId } || userId in friendships || userId in seenInRooms)

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

    // ---- rooms: scripted regulars chat among themselves and answer you ----

    /** A room someone opened. [members] are the people in it (scripted people count as present). */
    private class UserRoom(
        val id: String, val owner: Profile, val spec: NewRoom, val code: String, val createdAt: Long,
        val members: MutableSet<String>,
    ) {
        var closed = false
    }

    /** A few rooms opened by the scripted people, so the demo shows every kind of condition. */
    private val userRooms = linkedMapOf<String, UserRoom>().apply {
        fun seed(id: String, owner: Int, spec: NewRoom, code: String, vararg members: Int) {
            val p = POOL.first { it.id == "p$owner" }
            put(id, UserRoom(id, p, spec, code, 0L, members.mapTo(linkedSetOf()) { "p$it" }))
        }
        seed("u-seed-music", 1, NewRoom("Bollywood songs all night", RoomCategory.MUSIC, 12, GenderRule.ANY, 18, 100, false), "BLY7KQ", 1, 6, 10, 7)
        seed("u-seed-chai", 3, NewRoom("Chai & chill, girls only", RoomCategory.FRIENDS, 8, GenderRule.FEMALE, 20, 30, false), "CHA4WZ", 3, 9, 11)
        seed("u-seed-korean", 4, NewRoom("Korean ↔ Hindi practice", RoomCategory.LANGUAGE, 2, GenderRule.ANY, 18, 40, false), "KOR8HN", 4, 2)
    }

    private fun online(r: UserRoom) = r.members.size

    /** Same order of checks as the server's room_refusal(). The owner may always enter. */
    private fun refusal(r: UserRoom, me: Profile): String? = when {
        r.owner.id == me.id -> null
        r.closed -> "room_closed"
        r.owner.id in blocked -> "not_allowed"
        !r.spec.genderRule.accepts(me.gender) -> "room_gender"
        me.age !in r.spec.minAge..r.spec.maxAge -> "room_age"
        me.id !in r.members && online(r) >= r.spec.maxMembers -> "room_full"
        else -> null
    }

    private fun UserRoom.toRoom(me: Profile) = Room(
        id, spec.title, spec.category.emoji, online(this), official = false, mine = owner.id == me.id, ownerName = owner.name,
        category = spec.category, maxMembers = spec.maxMembers, genderRule = spec.genderRule, minAge = spec.minAge,
        maxAge = spec.maxAge, isPrivate = spec.isPrivate, code = code.takeIf { owner.id == me.id }, refusal = refusal(this, me),
    )

    override suspend fun rooms(): List<Room> {
        val me = me()
        val official = ROOMS.mapIndexed { i, (id, title, emoji) ->
            Room(id, title, emoji, online = 4 + (id.hashCode() and 0x7fffffff) % 60 + if (i == 0) 120 else 0)
        }
        val mine = userRooms.values.filter { r ->
            !r.closed && r.owner.id !in blocked && (!r.spec.isPrivate || r.owner.id == me.id || me.id in r.members)
        }.sortedWith(compareByDescending<UserRoom> { online(it) }.thenByDescending { it.createdAt })
        return official + mine.map { it.toRoom(me) }
    }

    override suspend fun joinRoom(roomId: String) {
        val me = me()
        if (ROOMS.none { it.first == roomId }) {
            val r = userRooms[roomId] ?: throw BackendException("not_found")
            // A private room answers like a wrong id until you came in with its code.
            if (r.spec.isPrivate && r.owner.id != me.id && me.id !in r.members) throw BackendException("not_found")
            refusal(r, me)?.let { throw BackendException(it) }
            r.members += me.id
        }
        messagesOf(roomId)
    }

    override suspend fun createRoom(room: NewRoom): CreatedRoom {
        val me = me()
        val title = room.title.trim()
        if (title.length !in Room.MIN_TITLE..Room.MAX_TITLE) throw BackendException("room_title")
        if (room.maxMembers !in Room.MIN_MEMBERS..Room.MAX_MEMBERS) throw BackendException("room_size")
        if (room.minAge !in Room.MIN_AGE..Room.MAX_AGE || room.maxAge !in Room.MIN_AGE..Room.MAX_AGE || room.minAge > room.maxAge) {
            throw BackendException("bad_request")
        }
        if (userRooms.values.count { it.owner.id == me.id && clock() - it.createdAt < 86_400_000L } >= 10) throw BackendException("slow_down")
        userRooms.values.filter { it.owner.id == me.id }.forEach { it.closed = true } // one open room per owner
        // Own dice for codes, so tests with fixed dice still get distinct, realistic codes.
        var seed = title.hashCode() * 31 + userRooms.size
        var code: String
        do {
            val dice = Random(seed++)
            code = (1..Room.CODE_LENGTH).joinToString("") { CODE_CHARS[dice.nextInt(CODE_CHARS.length)].toString() }
        } while (userRooms.values.any { it.code == code })
        val id = "u-" + UUID.randomUUID().toString().replace("-", "").take(16)
        userRooms[id] = UserRoom(id, me, room.copy(title = title), code, clock(), linkedSetOf(me.id))
        return CreatedRoom(id, code)
    }

    override suspend fun joinRoomCode(code: String): String {
        val me = me()
        val r = userRooms.values.firstOrNull { it.code == code.trim().uppercase() } ?: throw BackendException("not_found")
        refusal(r, me)?.let { throw BackendException(it) }
        r.members += me.id
        messagesOf(r.id)
        return r.id
    }

    override suspend fun closeRoom(roomId: String) {
        val r = userRooms[roomId]
        if (r == null || r.owner.id != me().id || r.closed) throw BackendException("not_allowed")
        r.closed = true
    }

    override suspend fun leaveRoom(roomId: String) {
        val me = phone?.let { accounts[it] } ?: return
        userRooms[roomId]?.members?.remove(me.id)
    }

    private fun messagesOf(roomId: String) = roomMessages.getOrPut(roomId) {
        val people = regulars(roomId)
        people.forEach { seenInRooms += it.id }
        if (people.isEmpty()) return@getOrPut mutableListOf() // a room you just opened starts empty
        ROOM_LINES.shuffled(Random(roomId.hashCode())).take(6).mapIndexed { i, line ->
            RoomMessage(nextId++, people[i % people.size], false, line, clock() - (6 - i) * 45_000L)
        }.toMutableList()
    }

    /** Official rooms: four scripted regulars. User rooms: the scripted people inside it (none in a room you opened). */
    private fun regulars(roomId: String) =
        userRooms[roomId]?.let { r -> POOL.filter { it.id in r.members } } ?: POOL.shuffled(Random(roomId.hashCode())).take(4)

    override suspend fun roomHistory(roomId: String, afterId: Long?): List<RoomMessage> {
        val visible = messagesOf(roomId).filter { it.sender.id !in blocked }
        return if (afterId == null) visible.takeLast(100) else visible.filter { it.id > afterId }.take(200)
    }

    override fun roomEvents(roomId: String, afterId: Long?): Flow<RoomMessage> {
        val outgoing = roomOutgoing.getOrPut(roomId) { Channel(Channel.UNLIMITED) }
        val replies = flow {
            for (text in outgoing) {
                wait(random.nextLong(900, 2500))
                roomLine(roomId, if ('?' in text) ANSWERS.random(random) else REPLIES.random(random))?.let { emit(it) }
            }
        }
        val chatter = flow {
            if (instant) return@flow // tests: only answers, so screens are stable
            while (true) {
                delay(random.nextLong(5_000, 11_000))
                roomLine(roomId, ROOM_LINES.random(random))?.let { emit(it) }
            }
        }
        return merge(replies, chatter)
    }

    /** Null when nobody else is there to talk (an empty user room, or a closed one). */
    private fun roomLine(roomId: String, text: String): RoomMessage? {
        val user = userRooms[roomId]
        if (user?.closed == true) return null
        val sender = regulars(roomId).filter { it.id !in blocked }.randomOrNull(random)
            ?: if (user != null) return null else POOL.first()
        return RoomMessage(nextId++, sender, false, text, clock()).also { messagesOf(roomId) += it }
    }

    override suspend fun sendRoom(roomId: String, text: String): RoomMessage {
        val me = me()
        userRooms[roomId]?.let { r ->
            if (r.closed) throw BackendException("room_closed")
            if (me.id !in r.members && r.owner.id != me.id) throw BackendException("blocked") // like the server's RLS refusal
        }
        val msg = RoomMessage(nextId++, me, true, text, clock())
        messagesOf(roomId) += msg
        roomOutgoing.getOrPut(roomId) { Channel(Channel.UNLIMITED) }.trySend(text)
        return msg
    }

    // ---- voice rooms (FakeVoice) ----

    override suspend fun voiceRooms() = voice.list().filter { it.host.id !in blocked }
    override suspend fun createVoiceRoom(room: NewVoiceRoom) = voice.create(room)
    override fun voiceSession(roomId: String) = voice.session(roomId)
    override suspend fun leaveVoice(roomId: String) = voice.leave(roomId)
    override suspend fun endVoice(roomId: String) = voice.endRoom(roomId)
    override suspend fun voiceLines(roomId: String) = voice.lines(roomId).filter { it !is VoiceLine.Text || it.sender.id !in blocked }
    override suspend fun sendVoice(roomId: String, text: String) = voice.send(roomId, text)
    override suspend fun voiceAction(roomId: String, action: VoiceAction) = voice.act(roomId, action)

    override suspend fun reportVoiceRoom(roomId: String, reason: ReportReason) {
        val host = voice.hostOf(roomId) ?: throw BackendException("not_found")
        if (host == me().id) throw BackendException("not_allowed")
        block(host)
    }

    companion object {
        const val DEMO_OTP = "123456"
        /** Room codes: no 0/O or 1/I, like the server. */
        private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        private val REPLIES = listOf(
            "Hii 😊", "Where are you from?", "Haha nice", "What do you do?", "Same here!",
            "Kya haal hai?", "I love chai ☕", "Just finished college today", "Nice profile btw",
            "Which music do you like?", "Arre wah 😄", "Cool, tell me more",
        )
        private val ANSWERS = listOf(
            "Yes 🙂", "Not really haha", "I'm from Pune", "Studying engineering",
            "Maybe! You?", "Bollywood all the way", "Cricket obviously 🏏",
        )

        val ROOMS = listOf(
            Triple("lounge", "Global Lounge", "🌍"), Triple("language", "Language Exchange", "🗣️"),
            Triple("music", "Music", "🎧"), Triple("movies", "Movies & Series", "🎬"), Triple("gaming", "Gaming", "🎮"),
            Triple("sports", "Sports", "⚽"), Triple("night", "Late Night Talks", "🌙"), Triple("memes", "Memes", "😂"),
            Triple("study", "Study & Campus", "🎓"), Triple("travel", "Travel", "✈️"),
        )
        private val ROOM_LINES = listOf(
            "Hello everyone 👋", "Where is everyone from?", "Good morning from here ☀️", "Anyone up for a chat?",
            "Just finished work, finally", "What are you all listening to?", "Haha this room is fun", "Hi from Brazil 🇧🇷",
            "Any movie recommendations?", "It's 2am here lol", "Can someone teach me a word in their language?",
            "Weekend plans?", "Coffee or tea? ☕", "First time here!",
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
