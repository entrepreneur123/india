package com.yaari.chat.data

import com.yaari.chat.domain.ExitPolicy
import com.yaari.chat.domain.Validation
import com.yaari.chat.model.ContactApp
import com.yaari.chat.model.Friend
import com.yaari.chat.model.FriendContacts
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Media
import com.yaari.chat.model.Message
import com.yaari.chat.model.PersonRow
import com.yaari.chat.model.Post
import com.yaari.chat.model.Profile
import com.yaari.chat.model.RecentMatch
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.Target
import com.yaari.chat.model.MANNERS
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.LinkPreview
import com.yaari.chat.model.Links
import com.yaari.chat.model.VoiceLine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import com.yaari.chat.model.Badges
import com.yaari.chat.model.InboxItem
import com.yaari.chat.model.InboxKind
import com.yaari.chat.model.Interest
import com.yaari.chat.model.ProfilePhoto
import com.yaari.chat.model.RoomQuery
import com.yaari.chat.model.Comment
import com.yaari.chat.model.Cosmetic
import com.yaari.chat.model.HandleCheck
import com.yaari.chat.model.MyContacts
import com.yaari.chat.model.LikeState
import com.yaari.chat.model.Look
import com.yaari.chat.model.LookItem
import com.yaari.chat.model.LookKind
import com.yaari.chat.model.Shop
import com.yaari.chat.model.Stickers
import com.yaari.chat.model.VIP_NAME
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
import com.yaari.chat.domain.ForeignText
import com.yaari.chat.domain.Handle
import com.yaari.chat.domain.TranslateKind
import com.yaari.chat.domain.TranslateTargets
import com.yaari.chat.domain.Translation
import java.time.LocalDate
import java.util.Locale
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
    /** The demo's people like and comment on my posts by themselves (tests call [reactToPost] instead). */
    private val ambient = voiceAmbient

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
        /** When it ended (rate_chat allows a day after). */
        var endedAt: Long? = null
        var video = VideoStatus.NONE
        /** The call asked for is a video call (false: voice only). */
        var callVideo = true

        fun end(at: Long) {
            if (!ended) endedAt = at
            ended = true
        }
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

    /** Notifications, as the server's triggers write them (023): a friend's messages fold into one unread row per chat. */
    private class Note(
        val id: Long, val kind: InboxKind, var actor: Profile, var at: Long, var read: Boolean = false, var n: Int = 1,
        val matchId: String? = null, val roomId: String? = null, val postId: String? = null,
    )
    private val notes = mutableListOf<Note>()
    private var noteSeq = 1L
    private val noteSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    /** The newest message id I have read, per chat. */
    private val readUpto = mutableMapOf<String, Long>()
    /** Friends I invited to a room: (room id, user id). */
    val invited = mutableListOf<Pair<String, String>>()
    /** The push token this phone registered (null: none, or unregistered). */
    var deviceToken: String? = null
        private set

    // ---- posts, likes and comments (033), as the server keeps them ----

    private class FakePost(
        val id: String, val author: String, val text: String, val image: String?, val at: Long, val visibility: PostVisibility,
        val likes: MutableSet<String> = mutableSetOf(), val comments: MutableList<FakeComment> = mutableListOf(),
    )
    private class FakeComment(val id: Long, val authorId: String, val body: String, val at: Long, val replyTo: Long?, var removed: Boolean = false)

    private val posts = mutableListOf<FakePost>()
    private var commentSeq = 1L
    /** My posts the demo's people have reacted to already (once each). */
    private val reacted = mutableSetOf<String>()
    /** Reports of posts and comments ("post:<id>" / "comment:<id>" to the reason), for tests. */
    val postReports = mutableListOf<Pair<String, ReportReason>>()
    /** The demo people's details (status, languages, prompts). */
    private val styles = STYLES.toMutableMap()

    // ---- identities and manners (035), as the server keeps them ----

    /** The identity provider is set up (else kyc_unavailable, as the live server today). The demo shows the flow. */
    var kycAvailable = true
    /** What the next check finds (tests); the demo's checks pass. */
    var kycOutcome = KycResult.VERIFIED
    /** app_flags.require_verification: unverified people may look around but not match, join, post, comment or befriend. */
    var requireVerification = false
    private val kycStarts = mutableListOf<Long>()
    private var kycLast: KycAttempt? = null

    private class Rating(val matchId: String, val rater: String, val rated: String, val good: Boolean, val tags: List<MannerTag>, val at: Long)
    private val ratings = mutableListOf<Rating>()

    init {
        notify(InboxKind.FRIEND_REQUEST, POOL.first { it.id == "p3" }, at = clock() - 3_600_000L)
        seedPosts()
        seedRatings()
        seedVip()
    }

    /** 046: someone in VIP incognito I chatted with three days ago, who sent a sticker (as a stranger sees them). */
    private fun seedVip() {
        val at = clock() - 3 * DAY
        matches["m-vip"] = FakeMatch(VIP, at).apply {
            end(at + 600_000L)
            messages += Message(nextId++, false, Stickers.body(Cosmetic.STICKER_NAMASTE, 1), null, at + 60_000L)
            readUpto["m-vip"] = messages.last().id // seen back then
        }
    }

    /** The demo's people posted over the last days; some posts are public (anyone opening the profile), with likes and comments. */
    private fun seedPosts() {
        val now = clock()
        val hour = 3_600_000L
        var k = 0
        POOL.forEach { p ->
            p.posts.forEachIndexed { i, post ->
                val public = p.id in PUBLIC_POSTERS && i == 0 || p.id in setOf("p6", "p8") && i == 1
                val at = now - (k++ * 5 + 1) * hour / 2 - i * 20 * hour
                posts += FakePost(post.id, p.id, post.text, post.imageUri, at, if (public) PostVisibility.PUBLIC else PostVisibility.FRIENDS)
            }
        }
        // Likes and comments from the others.
        posts.forEachIndexed { i, fp ->
            POOL.filter { it.id != fp.author }.take((i * 7 + 3) % 9).forEach { fp.likes += it.id }
        }
        fun comment(postId: String, from: String, body: String, replyTo: Long? = null, ago: Long = 20 * 60_000L): Long {
            val id = commentSeq++
            posts.first { it.id == postId }.comments += FakeComment(id, from, body, now - ago, replyTo)
            return id
        }
        val first = comment("p1-0", "p7", "This view is unreal. Saving it for my next trip", ago = 50 * 60_000L)
        comment("p1-0", "p1", "Come in the monsoon, it is even better", replyTo = first, ago = 42 * 60_000L)
        comment("p1-0", "p5", "Golden hour hits different there", ago = 30 * 60_000L)
        comment("p6-0", "p1", "The colours! Which lens?", ago = 3 * hour)
        comment("p8-0", "p2", "Adding this to my list", ago = 5 * hour)
        comment("p8-1", "p4", "Best gyoza in town, I believe it", ago = 7 * hour)
    }

    /** As the server's post_json/comment_json: the author with the verified badge. */
    private fun personOf(id: String): Profile? =
        phone?.let { accounts[it] }?.takeIf { it.id == id }
            ?: POOL.firstOrNull { it.id == id }?.let { it.copy(style = styles[it.id] ?: ProfileStyle(), posts = emptyList(), verified = it.id in VERIFIED) }

    /** The demo's people have been rated by others over the last weeks (the score's 90 days). */
    private fun seedRatings() {
        val now = clock()
        RATED.forEach { (id, seed) ->
            val (good, bad, tags) = seed
            repeat(good) { i ->
                // Each good rating carries up to three of the person's tags, so their counts come out as listed.
                val t = tags.filter { (_, n) -> i < n }.map { it.first }.take(MannerTag.MAX)
                ratings += Rating("seed-$id-g$i", "seed-$i", id, true, t, now - (i + 1) * 7_200_000L)
            }
            repeat(bad) { i -> ratings += Rating("seed-$id-b$i", "seed-b$i", id, false, emptyList(), now - (i + 2) * 9_000_000L) }
        }
    }

    private fun isVerified(id: String) = if (id == meId()) me().verified else id in VERIFIED

    /** trust_json: the Bayesian score over 90 days (a bad rating weighs double), the top 3 good tags. */
    private fun trustOf(id: String): Trust {
        val recent = ratings.filter { it.rated == id && clock() - it.at < 90 * DAY }
        val good = recent.count { it.good }
        val bad = recent.size - good
        val tags = recent.filter { it.good }.flatMap { it.tags }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<MannerTag, Int>> { it.value }.thenBy { it.key.code }).take(3).map { TagCount(it.key, it.value) }
        return Trust(id, isVerified(id), manner(good, bad), recent.size, tags)
    }

    /** The server's require_verified. */
    private fun requireVerified() {
        if (requireVerification && !me().verified) throw BackendException("verify_required")
    }

    /** Tests: others rate me [n] times (eight bad ratings and nothing else bring the score under 20). */
    fun rateMe(good: Boolean, n: Int, tags: List<MannerTag> = emptyList()) {
        val me = meId() ?: return
        repeat(n) { ratings += Rating("test-${UUID.randomUUID()}", "tester", me, good, tags, clock()) }
    }

    override suspend fun kycStart(): String {
        val me = me()
        wait(400)
        if (me.verified) throw BackendException("already_verified")
        if (!kycAvailable) throw BackendException("kyc_unavailable")
        kycStarts.removeAll { clock() - it >= DAY }
        if (kycStarts.size >= 5) throw BackendException("slow_down")
        kycStarts += clock()
        kycLast = KycAttempt("Not Started", null, clock())
        return KYC_URL
    }

    override suspend fun kycCheck(): KycResult {
        me()
        wait(500)
        val last = kycLast ?: return KycResult.NONE
        val result = kycOutcome
        // kyc_apply: an approved document sets the badge (gender, birth date and country would come from it; the
        // demo keeps what was entered at sign-up); someone under 18 is banned.
        if (result == KycResult.VERIFIED) updateMe { it.copy(verified = true) }
        kycLast = last.copy(status = if (result == KycResult.PENDING) "In Review" else "Decided", result = result)
        return result
    }

    override suspend fun myTrust(): MyTrust {
        val me = me()
        return MyTrust(
            trustOf(me.id), requireVerification, kycLast,
            ratings.filter { it.rater == me.id && clock() - it.at < DAY }.mapTo(mutableSetOf()) { it.matchId },
        )
    }

    override suspend fun trust(userIds: List<String>): List<Trust> {
        me()
        return userIds.take(100).distinct().filter { it !in blocked }.map(::trustOf)
    }

    override suspend fun rateChat(matchId: String, good: Boolean, tags: List<MannerTag>) {
        val me = me()
        val m = matches[matchId]?.takeIf { !it.dm } ?: throw BackendException("not_found")
        if (m.endedAt?.let { clock() - it > DAY } == true) throw BackendException("too_late")
        if (m.messages.none { it.fromMe } || m.messages.none { !it.fromMe }) throw BackendException("not_allowed")
        val t = tags.distinct()
        if (t.size > MannerTag.MAX || t.any { it.good != good }) throw BackendException("bad_request")
        if (ratings.any { it.matchId == matchId && it.rater == me.id }) throw BackendException("already_rated")
        ratings += Rating(matchId, me.id, m.partner.id, good, t, clock())
    }

    private fun meId() = phone?.let { accounts[it] }?.id

    /** The server's post_visible: mine; else not blocked, and public, a friend's or someone I chatted with. */
    private fun canSee(p: FakePost): Boolean {
        val me = meId()
        if (p.author == me) return true
        if (p.author in blocked) return false
        return p.visibility == PostVisibility.PUBLIC || friendships[p.author] == FriendState.FRIENDS || matches.values.any { it.partner.id == p.author }
    }

    private fun toPost(p: FakePost, withAuthor: Boolean = true): Post {
        val me = meId()
        return Post(
            p.id, p.text, p.image, p.at, p.visibility, p.likes.size, p.comments.count { !it.removed && it.authorId !in blocked },
            me != null && me in p.likes, if (withAuthor) personOf(p.author) else null,
        )
    }

    private fun postsBy(userId: String): List<Post> = posts.filter { it.author == userId && canSee(it) }.sortedByDescending { it.at }.map { toPost(it, withAuthor = false) }

    private fun page(list: List<FakePost>, before: Post?) =
        list.filter { canSee(it) && (before == null || it.at < before.createdAt) }.sortedByDescending { it.at }.take(PAGE).map { toPost(it) }

    private fun visiblePost(id: String) = posts.firstOrNull { it.id == id }?.takeIf { canSee(it) } ?: throw BackendException("not_found")

    /**
     * Someone likes (and, with [comment], comments on) my post [postId] while I'm elsewhere (the demo, tests): the
     * notification folds into the post's unread line, as the server's.
     */
    fun reactToPost(postId: String, actorId: String, comment: String? = null) {
        val p = posts.firstOrNull { it.id == postId } ?: return
        val actor = personOf(actorId) ?: return
        if (p.likes.add(actorId)) notifyPost(InboxKind.POST_LIKE, actor, p)
        if (comment != null) {
            p.comments += FakeComment(commentSeq++, actorId, comment, clock(), null)
            notifyPost(InboxKind.POST_COMMENT, actor, p)
        }
    }

    private fun notifyPost(kind: InboxKind, actor: Profile, p: FakePost) {
        if (p.author != meId() || actor.id == meId()) return
        val open = notes.firstOrNull { !it.read && it.kind == kind && it.postId == p.id }
        if (open != null) {
            open.n++
            open.actor = actor
            open.at = clock()
        } else {
            notes += Note(noteSeq++, kind, actor, clock(), postId = p.id)
        }
        noteSignals.tryEmit(Unit)
    }

    /** The demo: a couple of people react to each of my posts once, the next time I look around. */
    private fun ambientReactions() {
        if (!ambient) return
        val me = meId() ?: return
        posts.filter { it.author == me && it.id !in reacted && clock() - it.at > 5_000L }.forEach { p ->
            reacted += p.id
            reactToPost(p.id, "p1")
            reactToPost(p.id, "p7", "Love this!")
            reactToPost(p.id, "p5")
        }
    }

    /**
     * Something to tell me (the demo's scripted people, and tests): a friend's message folds into the chat's unread
     * row, a request or acceptance is once per person while unread.
     */
    fun notify(kind: InboxKind, actor: Profile, matchId: String? = null, roomId: String? = null, at: Long = clock()) {
        val open = notes.firstOrNull {
            !it.read && it.kind == kind && when (kind) {
                InboxKind.MESSAGE -> it.matchId == matchId
                InboxKind.FRIEND_REQUEST, InboxKind.FRIEND_ACCEPT -> it.actor.id == actor.id
                InboxKind.ROOM_INVITE, InboxKind.POST_LIKE, InboxKind.POST_COMMENT, InboxKind.CALL -> false
            }
        }
        if (open != null) {
            if (kind == InboxKind.MESSAGE) {
                open.n++
                open.at = at
            }
        } else {
            notes += Note(noteSeq++, kind, actor, at, matchId = matchId, roomId = roomId)
        }
        noteSignals.tryEmit(Unit)
    }

    /** My friend [userId] calls me (053): their thread asks for a [video] or voice call, and it rings in the app. */
    suspend fun friendCalls(userId: String, video: Boolean) {
        val m = match(friendThread(userId))
        m.video = VideoStatus.REQUESTED_BY_PARTNER
        m.callVideo = video
        callNote(m)
    }

    /** As the server's notify_call: any change reads the ringing note; a friend's new ask leaves a fresh one. */
    private fun callNote(m: FakeMatch) {
        val id = matches.entries.first { it.value === m }.key
        readNotes { it.kind == InboxKind.CALL && it.matchId == id }
        if (m.video == VideoStatus.REQUESTED_BY_PARTNER && friendships[m.partner.id] == FriendState.FRIENDS) notify(InboxKind.CALL, m.partner, id)
    }

    override suspend fun callAsked(matchId: String): Boolean? = match(matchId).takeIf { it.video == VideoStatus.REQUESTED_BY_PARTNER }?.callVideo

    /** The partner of [matchId] writes while I'm not looking (tests, screenshots): unread until I read it. */
    fun partnerSays(matchId: String, text: String) {
        val m = match(matchId)
        m.messages += Message(nextId++, false, text, null, clock())
        if (friendships[m.partner.id] == FriendState.FRIENDS) notify(InboxKind.MESSAGE, m.partner, matchId)
    }

    private fun readNotes(which: (Note) -> Boolean) {
        notes.filter { !it.read && which(it) }.forEach { it.read = true }
        noteSignals.tryEmit(Unit)
    }

    /** My profile with my posts as they are now (counts included). */
    private fun me() = (phone?.let { accounts[it] } ?: throw BackendException("not_signed_in")).let { it.copy(posts = postsBy(it.id)) }

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
    override suspend fun myProfile() = phone?.let { accounts[it] }?.let { me() }

    override suspend fun createProfile(p: NewProfile): Profile {
        val ph = phone ?: throw BackendException("not_signed_in")
        if (accounts[ph] != null) throw BackendException("profile_exists")
        val age = Validation.age(p.birthDate, today())
        if (age < Validation.MIN_AGE) throw BackendException("under_18")
        val country = com.yaari.chat.domain.Countries.all.filter { ph.startsWith(it.dial) }.maxByOrNull { it.dial.length }
            ?.let { if (it.dial == "1") "US" else it.iso } // +1 is shared; the server does the same
        if (!Handle.plainName(Handle.normalize(p.name))) throw BackendException("name_invalid")
        val me = Profile(
            "me-" + UUID.randomUUID(), p.name, p.gender, age, p.city, p.bio, p.photoUri, ph.hashCode(), country = country, handle = freeHandle(p.name),
        )
        accounts[ph] = me
        return me
    }

    override suspend fun entitlements() = entitlements

    override suspend fun redeem(productId: String, purchaseToken: String): Entitlements {
        entitlements = when {
            productId == Products.PREMIUM -> entitlements.copy(premium = true)
            productId == Products.GENDER_PASS -> entitlements.copy(genderPass = true)
            Cosmetic.of(productId) != null -> entitlements.also { grantLook(Cosmetic.of(productId)!!) }
            productId == Products.HANDLE_CHANGE -> entitlements.also { handlePasses.merge(me().id, 1, Int::plus) }
            else -> throw BackendException("bad_request")
        }
        return entitlements
    }

    override suspend fun deleteAccount() {
        val ph = phone ?: throw BackendException("not_signed_in")
        accounts[ph]?.let { me -> posts.removeAll { it.author == me.id } }
        accounts.remove(ph)
        matches.clear()
        friendships.clear()
        myContactCard = emptyMap()
        sharedWith.clear()
        entitlements = Entitlements()
        lastPartner = null
        phone = null
    }

    override suspend fun signOut() {
        phone = null
    }

    override suspend fun findMatch(target: Target, countries: List<String>): Match? {
        val me = me()
        requireVerified()
        if (MANNERS && trustOf(me.id).manner < Trust.MANNER_FLOOR) throw BackendException("manner_low")
        if (target.isPaid && !entitlements.canChooseGender) throw BackendException("premium_required")
        matches.values.filter { !it.ended }.forEach { m ->
            if (clock() - m.startedAt < ExitPolicy.lockMs(entitlements.premium)) throw BackendException("exit_locked")
            m.end(clock())
        }
        wait(random.nextLong(900, 2200))
        val partner = POOL.filter {
            target.accepts(it.gender) && (countries.isEmpty() || it.country in countries) && it.id != me.id && it.id !in blocked && it.id != lastPartner
        }
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
                    val said = out.text.orEmpty().lowercase()
                    emit(MatchEvent.Typing(false))
                    when {
                        !m.ended && roll < 6 && m.video == VideoStatus.NONE -> {
                            m.end(clock())
                            emit(MatchEvent.PartnerLeft)
                        }
                        roll < 15 -> Unit
                        callOpen(m) && m.video == VideoStatus.NONE && ("video" in said || "call" in said) -> {
                            m.video = VideoStatus.REQUESTED_BY_PARTNER // the partner asks for a call ("video" in it: a video call)
                            m.callVideo = "video" in said
                            callNote(m)
                            emit(MatchEvent.Video(m.video, m.callVideo))
                        }
                        else -> {
                            val text = if (out.text?.contains('?') == true) ANSWERS.random(random) else REPLIES.random(random)
                            val msg = Message(nextId++, false, text, null, clock())
                            m.messages += msg
                            if (friendships[m.partner.id] == FriendState.FRIENDS) notify(InboxKind.MESSAGE, m.partner, matchId)
                            emit(MatchEvent.NewMessage(msg))
                        }
                    }
                }
                Outgoing.VideoRequest -> {
                    wait(random.nextLong(1500, 3000))
                    if (!callOpen(m) || m.video != VideoStatus.REQUESTED_BY_ME) continue
                    m.video = if (random.nextInt(100) < 70) VideoStatus.ACCEPTED else VideoStatus.DECLINED
                    emit(MatchEvent.Video(m.video, m.callVideo))
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
        // chat_open: an ended chat goes on only between friends (the server's row-level security, which the live
        // backend reports as "blocked").
        if (m.ended && friendships[m.partner.id] != FriendState.FRIENDS) throw BackendException("blocked")
        checkSticker(text)
        val msg = Message(nextId++, true, text, media, clock())
        m.messages += msg
        m.outgoing.trySend(Outgoing.Text(text)) // after the chat ends this is a DM; the partner still answers
        return msg
    }

    /** Scripted partners don't look at read ticks; my unread counts do (and reading clears the chat's notification). */
    override suspend fun markRead(matchId: String, upto: Long) {
        if (upto <= (readUpto[matchId] ?: -1L)) return
        readUpto[matchId] = upto
        readNotes { it.kind == InboxKind.MESSAGE && it.matchId == matchId }
    }

    override suspend fun leave(matchId: String) {
        val m = match(matchId)
        if (m.ended) return
        if (clock() - m.startedAt < ExitPolicy.lockMs(entitlements.premium)) throw BackendException("exit_locked")
        m.end(clock())
    }

    override suspend fun report(partnerId: String, reason: ReportReason, matchId: String?) = block(partnerId)

    override suspend fun block(userId: String) {
        blocked += userId
        unfriend(userId)
        matches.values.filter { it.partner.id == userId }.forEach { it.end(clock()) }
    }

    /** call_open (052): the chat is still on, or we are friends; never with someone blocked. */
    private fun callOpen(m: FakeMatch) = m.partner.id !in blocked && (!m.ended || friendships[m.partner.id] == FriendState.FRIENDS)

    override suspend fun requestCall(matchId: String, video: Boolean) {
        val m = match(matchId)
        if (!callOpen(m) || m.video !in setOf(VideoStatus.NONE, VideoStatus.DECLINED)) throw BackendException("not_allowed")
        m.video = VideoStatus.REQUESTED_BY_ME
        m.callVideo = video
        m.outgoing.trySend(Outgoing.VideoRequest)
    }

    override suspend fun answerVideo(matchId: String, accept: Boolean) {
        val m = match(matchId)
        if (!callOpen(m) || m.video != VideoStatus.REQUESTED_BY_PARTNER) throw BackendException("not_allowed")
        m.video = if (accept) VideoStatus.ACCEPTED else VideoStatus.DECLINED
        callNote(m)
    }

    override suspend fun endVideo(matchId: String) {
        val m = match(matchId)
        m.video = VideoStatus.NONE
        callNote(m)
    }

    override suspend fun videoToken(matchId: String): VideoToken? = null

    override suspend fun flagNudity(matchId: String) = Unit

    override suspend fun recents(): List<RecentMatch> =
        matches.entries.reversed().filter { !it.value.dm }.distinctBy { it.value.partner.id }
            .filter { it.value.partner.id !in blocked && it.value.startedAt > (hiddenRecents[it.value.partner.id] ?: Long.MIN_VALUE) }
            .take(RecentMatch.MAX)
            .map { (id, m) -> RecentMatch(id, m.partner, m.startedAt, m.messages.lastOrNull()?.let { it.text ?: "[media]" }) }

    /** Partner id to when I took them off my recents (048: a chat after that brings them back). */
    private val hiddenRecents = mutableMapOf<String, Long>()

    override suspend fun hideRecents(userIds: List<String>) {
        me()
        if (userIds.size > RecentMatch.HIDE_MAX) throw BackendException("bad_request")
        userIds.forEach { hiddenRecents[it] = clock() }
    }

    override suspend fun profile(userId: String): Profile? {
        if (userId in blocked) return null
        if (userId == phone?.let { accounts[it] }?.id) return me()
        voice.profileOf(userId)?.let { return it } // people met in a voice room
        if (!visible(userId)) return null
        return (POOL.firstOrNull { it.id == userId } ?: VIP.takeIf { it.id == userId })?.let { it.copy(posts = postsBy(it.id), style = styles[it.id] ?: ProfileStyle()) }
    }

    /** The server's can_view: chatted with, a friend or a request, someone in a demo room, or who posts publicly. */
    private fun visible(userId: String) = userId !in blocked &&
        (matches.values.any { it.partner.id == userId } || userId in friendships || voice.profileOf(userId) != null ||
            posts.any { it.author == userId && it.visibility == PostVisibility.PUBLIC })

    /** People met in the demo's rooms, remembered once asked to be friends (they may leave the room). */
    private val met = mutableMapOf<String, Profile>()

    /** A demo person by id: the scripted pool, or someone met in a demo room. */
    private fun personFor(id: String) = POOL.firstOrNull { it.id == id } ?: VIP.takeIf { it.id == id } ?: met[id] ?: voice.profileOf(id)

    override suspend fun addPost(text: String, imageUri: String?, visibility: PostVisibility): Post {
        val me = me()
        requireVerified()
        if (posts.count { it.author == me.id && clock() - it.at < 86_400_000L } >= Post.PER_DAY) throw BackendException("slow_down")
        val p = FakePost(UUID.randomUUID().toString(), me.id, text, imageUri, clock(), visibility)
        posts += p
        return toPost(p, withAuthor = false)
    }

    override suspend fun deletePost(postId: String) {
        val me = me()
        if (!posts.removeAll { it.id == postId && it.author == me.id }) throw BackendException("not_found")
        notes.removeAll { it.postId == postId }
        noteSignals.tryEmit(Unit)
    }

    override suspend fun updateProfile(name: String, city: String, bio: String, newPhotoUri: String?): Profile {
        if (!Handle.plainName(Handle.normalize(name))) throw BackendException("name_invalid")
        wait(300)
        return updateMe { it.copy(name = name, city = city, bio = bio, photoUri = newPhotoUri ?: it.photoUri) }
    }

    // ---- friends: scripted people accept a request the next time the list loads ----

    override suspend fun friends(): List<Friend> {
        me()
        friendships.entries.filter { it.value == FriendState.REQUESTED }.forEach { e ->
            e.setValue(FriendState.FRIENDS)
            personFor(e.key)?.let { notify(InboxKind.FRIEND_ACCEPT, it) }
        }
        return friendships.entries.reversed().filter { it.key !in blocked }.mapNotNull { (id, state) ->
            personFor(id)?.let { p ->
                Friend(p, state, matches.entries.lastOrNull { it.value.partner.id == id }?.key)
            }
        }
    }

    override suspend fun requestFriend(userId: String): FriendState {
        me()
        requireVerified()
        if (!visible(userId)) throw BackendException("not_allowed")
        voice.profileOf(userId)?.let { met[userId] = it }
        val now = when (friendships[userId]) {
            FriendState.INCOMING, FriendState.FRIENDS -> FriendState.FRIENDS
            else -> FriendState.REQUESTED
        }
        friendships[userId] = now
        if (now == FriendState.FRIENDS) readNotes { it.kind == InboxKind.FRIEND_REQUEST && it.actor.id == userId }
        return now
    }

    /** [userId] asks to be my friend (tests): the request waits for my answer, with its notification. */
    fun asksToBeFriends(userId: String) {
        val p = personFor(userId) ?: return
        friendships[userId] = FriendState.INCOMING
        notify(InboxKind.FRIEND_REQUEST, p)
    }

    override suspend fun answerFriend(userId: String, accept: Boolean) {
        if (friendships[userId] != FriendState.INCOMING) throw BackendException("not_found")
        if (accept) friendships[userId] = FriendState.FRIENDS else friendships.remove(userId)
        if (accept) readNotes { it.kind == InboxKind.FRIEND_REQUEST && it.actor.id == userId } else noteSignals.tryEmit(Unit)
    }

    override suspend fun removeFriend(userId: String) {
        unfriend(userId)
    }

    /** As the server: no longer friends (unfriended, blocked) ends contact sharing both ways. */
    private fun unfriend(userId: String) {
        friendships.remove(userId)
        sharedWith -= userId
        theirContacts.remove(userId)
    }

    override suspend fun friendThread(userId: String): String {
        if (friendships[userId] != FriendState.FRIENDS || userId in blocked) throw BackendException("not_allowed")
        matches.entries.lastOrNull { it.value.partner.id == userId }?.let { return it.key }
        val partner = personFor(userId) ?: throw BackendException("not_found")
        val id = UUID.randomUUID().toString()
        matches[id] = FakeMatch(partner, clock(), dm = true).apply { ended = true }
        return id
    }

    // ---- IDs and looks (040), as the server keeps them ----

    /** When each account last chose a different ID (none: still the app-given one, which changes freely). */
    private val handleSetAt = mutableMapOf<String, Long>()
    private val handlePasses = mutableMapOf<String, Int>()
    /** The looks each account owns, in the order they were bought. */
    private val owned = mutableMapOf<String, MutableList<String>>()
    /** What each account wears. */
    private val worn = mutableMapOf<String, Look>()

    /** handle_make: the plain letters of the name and 4 digits ("priya_4821"), or "user_…"; never one that is taken. */
    private fun freeHandle(name: String): String {
        var base = name.lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }.take(14).trim('.', '_')
        if (base.length < 3 || Handle.reserved(base)) base = "user"
        val first = random.nextInt(10_000)
        return (0 until 10_000).map { "${base}_" + ((first + it) % 10_000).toString().padStart(4, '0') }.first { holderOf(it) == null }
    }

    /** Who has [handle] (case-blind): an account, a demo person or someone in a demo room; null when it is free. */
    private fun holderOf(handle: String): String? {
        val low = handle.lowercase(Locale.ROOT)
        accounts.values.filterNotNull().firstOrNull { it.handle?.lowercase(Locale.ROOT) == low }?.let { return it.id }
        return HANDLES.entries.firstOrNull { it.value.lowercase(Locale.ROOT) == low }?.key
    }

    /** The ID of anyone the demo knows: an account, a demo person, or someone in a demo room (made from their name). */
    private fun handleOf(id: String): String? = when {
        id == meId() -> me().handle
        id in HANDLES -> HANDLES[id]
        else -> voice.profileOf(id)?.let { Handle.suggest(it.name, Math.floorMod(id.hashCode(), 10_000)) }
    }

    override suspend fun handleCheck(handle: String): HandleCheck {
        val me = phone?.let { accounts[it] }
        if (phone == null) throw BackendException("not_signed_in")
        val h = Handle.normalize(handle)
        return when {
            !Handle.valid(h) -> HandleCheck.INVALID
            me != null && me.handle == h -> HandleCheck.MINE
            Handle.reserved(h) -> HandleCheck.RESERVED
            holderOf(h).let { it != null && it != me?.id } -> HandleCheck.TAKEN
            else -> HandleCheck.OK
        }
    }

    override suspend fun setHandle(handle: String): String {
        val me = phone?.let { accounts[it] } ?: throw BackendException(if (phone == null) "not_signed_in" else "no_profile")
        val h = Handle.normalize(handle)
        val current = me.handle.orEmpty()
        if (!Handle.valid(h)) throw BackendException("handle_invalid")
        if (current == h) return h
        val respell = current.equals(h, ignoreCase = true)
        if (Handle.reserved(h) && !respell) throw BackendException("handle_reserved")
        if (holderOf(h).let { it != null && it != me.id }) throw BackendException("handle_taken")
        val paid = !respell && handleSetAt[me.id]?.let { clock() - it < HANDLE_COOLDOWN_MS } == true
        if (paid && (handlePasses[me.id] ?: 0) < 1) throw BackendException("handle_cooldown")
        if (paid) handlePasses.merge(me.id, -1, Int::plus)
        if (!respell) handleSetAt[me.id] = clock()
        updateMe { it.copy(handle = h) }
        return h
    }

    private fun myLook(): Look {
        val me = me()
        return (worn[me.id] ?: Look(me.id, "")).copy(handle = me.handle.orEmpty())
    }

    override suspend fun shop(): Shop {
        val me = me()
        return Shop(
            Cosmetic.entries.map { LookItem(it.id, it.kind, PRICES.getValue(it.id)) }, owned[me.id].orEmpty().toSet(), myLook(),
            handleSetAt[me.id]?.let { it + HANDLE_COOLDOWN_MS }?.takeIf { it > clock() }, handlePasses[me.id] ?: 0,
            walls[me.id], me.id in anon,
        )
    }

    /** 046: each account's chat wall, and who is in VIP incognito. */
    private val walls = mutableMapOf<String, String>()
    private val anon = mutableSetOf<String>()

    private fun owns(item: String) = item in owned[me().id].orEmpty()

    override suspend fun setWall(item: String?) {
        val me = me()
        if (item == null) walls -= me.id
        else if (!item.startsWith("wall_") || !owns(item)) throw BackendException("not_owned")
        else walls[me.id] = item
    }

    override suspend fun setAnon(on: Boolean) {
        val me = me()
        if (on && !owns(Cosmetic.VIP_ANON.id)) throw BackendException("not_owned")
        if (on) anon += me.id else anon -= me.id
    }

    /** chat_body_check: a sticker needs its pack (ordinary text never does). */
    private fun checkSticker(text: String?) {
        val st = Stickers.of(text) ?: return
        if (!owns(st.pack)) throw BackendException("sticker_not_owned")
    }

    private fun personRow(id: String) = personOf(id)?.let { p -> handleOf(id)?.let { PersonRow(id, it, p.name, p.photoUri) } }

    override suspend fun searchHandles(query: String): List<PersonRow> {
        val q = Handle.normalize(query.trim().removePrefix("@")).lowercase(Locale.ROOT)
        if (q.isEmpty()) return emptyList()
        return POOL.filter { it.id !in blocked }.mapNotNull { personRow(it.id) }
            .filter { it.handle.lowercase(Locale.ROOT).startsWith(q) }.sortedBy { it.handle.lowercase(Locale.ROOT) }.take(20)
    }

    override suspend fun blocks(): List<PersonRow> = blocked.reversed().mapNotNull(::personRow)

    override suspend fun unblock(userId: String) {
        blocked -= userId
    }

    // ---- messenger contacts (044): mine, and what people show me (Ananya, who has asked to be friends, does) ----

    private var myContactCard = emptyMap<ContactApp, String>()
    private val sharedWith = mutableSetOf<String>()
    private val theirContacts = mutableMapOf("p3" to mapOf(ContactApp.WHATSAPP to "+919812345678", ContactApp.INSTAGRAM to "ananya.dances", ContactApp.DISCORD to "ananya_d"))

    override suspend fun setContacts(contacts: Map<ContactApp, String>): Map<ContactApp, String> {
        me()
        wait(300)
        myContactCard = contacts.mapNotNull { (app, v) ->
            when (val clean = app.clean(v)) {
                null -> throw BackendException("contact_invalid", app.code)
                "" -> null
                else -> app to clean
            }
        }.toMap()
        return myContactCard
    }

    override suspend fun myContacts() = MyContacts(myContactCard, sharedWith.toSet())

    override suspend fun shareContacts(userId: String, on: Boolean) {
        me()
        if (!on) sharedWith -= userId
        else if (friendships[userId] != FriendState.FRIENDS) throw BackendException("not_friends")
        else sharedWith += userId
    }

    override suspend fun contactsOf(userId: String): FriendContacts? =
        if (friendships[userId] != FriendState.FRIENDS) null else FriendContacts(theirContacts[userId], userId in sharedWith)

    override suspend fun equipLook(badge: String?, color: String?, font: String?, frame: String?): Look {
        val me = me()
        val want = listOf(badge, color, font, frame).map { it?.ifEmpty { null } }
        val mine = owned[me.id].orEmpty()
        want.zip(LookKind.entries).forEach { (item, kind) ->
            if (item == null) return@forEach
            if (!item.startsWith(kind.code + "_")) throw BackendException("bad_request")
            if (item !in mine) throw BackendException("not_owned")
        }
        worn[me.id] = Look(me.id, me.handle.orEmpty(), want[0], want[1], want[2], want[3])
        return myLook()
    }

    /** About half of the people in the demo's rooms wear one of the demo looks too. */
    private fun roomLook(id: String) = if (voice.profileOf(id) != null) DEMO_LOOKS.values.toList().getOrNull(Math.floorMod(id.hashCode(), 2 * DEMO_LOOKS.size)) else null

    /** redeem_cosmetic: mine for good; the first look of its kind is worn at once (walls are chosen by hand). */
    private fun grantLook(item: Cosmetic) {
        val me = me()
        val list = owned.getOrPut(me.id) { mutableListOf() }
        if (item.id in list) return
        list += item.id
        val now = myLook()
        if (now.of(item.kind) == null) worn[me.id] = now.with(item.kind, item.id)
    }

    override suspend fun looks(userIds: List<String>): List<Look> {
        me()
        return userIds.take(100).distinct().filter { it !in blocked }.mapNotNull { id ->
            when (id) {
                meId() -> myLook()
                else -> handleOf(id)?.let { h -> (DEMO_LOOKS[id] ?: roomLook(id) ?: Look(id, h)).copy(userId = id, handle = h) }
            }
        }
    }

    // ---- rooms (FakeVoice) ----

    override suspend fun voiceRooms(query: RoomQuery) = voice.list(query).filter { it.host.id !in blocked }
    override suspend fun createVoiceRoom(room: NewVoiceRoom) = requireVerified().let { voice.create(room) }
    override suspend fun joinVoiceCode(code: String) = requireVerified().let { voice.joinCode(code) }
    override suspend fun updateVoiceRoom(roomId: String, title: String, announcement: String, lang: String?) =
        voice.settings(roomId, title, announcement, lang)

    /** The server's invite_to_room: I am in the room, they are my friend. */
    override suspend fun inviteToRoom(roomId: String, userId: String) {
        val me = me()
        if (!voice.isOpen(roomId)) throw BackendException("room_closed")
        if (!voice.isIn(roomId, me.id) || friendships[userId] != FriendState.FRIENDS || userId in blocked) throw BackendException("not_allowed")
        if ((roomId to userId) !in invited) invited += roomId to userId
    }

    override suspend fun joinInvite(inboxId: Long): String {
        requireVerified()
        val n = notes.firstOrNull { it.id == inboxId && it.kind == InboxKind.ROOM_INVITE && it.actor.id !in blocked }
            ?: throw BackendException("not_found")
        readNotes { it.id == inboxId }
        val room = n.roomId ?: throw BackendException("not_found")
        if (!voice.isOpen(room)) throw BackendException("room_closed")
        voice.admit(room)
        return room
    }

    /** A believable, slowly changing number (no dice: tests script the dice). */
    override suspend fun onlineCount() = 1_240 + (clock() / 60_000L % 60).toInt()

    // ---- notifications ----

    override suspend fun inbox(before: Long?): List<InboxItem> {
        me()
        ambientReactions()
        wait(300)
        return notes.filter { it.actor.id !in blocked && (before == null || it.id < before) }.sortedByDescending { it.id }.take(30).map { n ->
            InboxItem(
                n.id, n.kind, n.actor, n.at, n.read, n.n, n.matchId, n.roomId, voice.titleOf(n.roomId), voice.isOpen(n.roomId),
                pending = n.kind == InboxKind.FRIEND_REQUEST && friendships[n.actor.id] == FriendState.INCOMING,
                post = n.postId?.let { id -> posts.firstOrNull { it.id == id } }?.let { PostRef(it.id, it.text.take(80), it.image) },
            )
        }
    }

    override suspend fun markInboxRead(ids: List<Long>?) {
        me()
        readNotes { ids == null || it.id in ids }
    }

    override suspend fun badges(): Badges {
        me()
        ambientReactions()
        return Badges(
            inbox = notes.count { !it.read && it.actor.id !in blocked }.coerceAtMost(99),
            friends = friendships.entries.count { it.value == FriendState.INCOMING && it.key !in blocked },
            chats = matches.entries.filter { it.value.partner.id !in blocked }.associate { (id, m) ->
                id to m.messages.count { !it.fromMe && it.id > (readUpto[id] ?: -1L) }.coerceAtMost(99)
            }.filterValues { it > 0 },
        )
    }

    override fun inboxSignals(): Flow<Unit> = flow {
        emit(Unit) // anything that arrived before listening
        emitAll(noteSignals)
    }

    override suspend fun registerDevice(token: String, locale: String, timeZone: String) {
        me()
        deviceToken = token
    }

    override suspend fun unregisterDevice(token: String) {
        if (deviceToken == token) deviceToken = null
    }

    // ---- profile extras ----

    private fun updateMe(change: (Profile) -> Profile): Profile {
        val ph = phone ?: throw BackendException("not_signed_in")
        return change(me()).also { accounts[ph] = it }
    }

    override suspend fun setInterests(tags: List<Interest>): Profile {
        if (!Interest.valid(tags)) throw BackendException("bad_request")
        wait(250)
        return updateMe { it.copy(interests = Interest.entries.filter { t -> t in tags }) }
    }

    override suspend fun addProfilePhoto(jpegUri: String): Profile {
        if (me().photos.size >= ProfilePhoto.MAX) throw BackendException("not_allowed")
        wait(400)
        return updateMe { it.copy(photos = it.photos + ProfilePhoto(UUID.randomUUID().toString(), jpegUri)) }
    }

    override suspend fun removeProfilePhoto(id: String): Profile = updateMe { it.copy(photos = it.photos.filterNot { p -> p.id == id }) }

    // ---- profile details, likes and comments (the server's 033 rules) ----

    override suspend fun setProfileStyle(style: ProfileStyle): Profile {
        val status = style.status?.trim()?.ifEmpty { null }
        val clean = style.copy(status = status, languages = style.languages.distinct(), prompts = style.prompts.map { it.copy(answer = it.answer.trim()) })
        if (!clean.valid()) throw BackendException("bad_request")
        wait(300)
        return updateMe { it.copy(style = clean) }
    }

    override suspend fun postsOf(userId: String, before: Post?): List<Post> {
        me()
        return page(posts.filter { it.author == userId }, before)
    }

    override suspend fun post(postId: String): Post? {
        me()
        return posts.firstOrNull { it.id == postId }?.takeIf { canSee(it) }?.let { toPost(it) }
    }

    override suspend fun likePost(postId: String, on: Boolean): LikeState {
        val me = me()
        val p = visiblePost(postId)
        val fresh = on && me.id !in p.likes
        if (on) p.likes += me.id else p.likes -= me.id
        if (fresh) notifyPost(InboxKind.POST_LIKE, me, p)
        return LikeState(p.likes.size, on)
    }

    override suspend fun comments(postId: String, after: Long?): List<Comment> {
        val me = me()
        val p = visiblePost(postId)
        wait(250)
        return p.comments.filter { !it.removed && it.authorId !in blocked && (after == null || it.id > after) }.sortedBy { it.id }.take(50)
            .mapNotNull { c -> comment(p, c, me.id) }
    }

    private fun comment(p: FakePost, c: FakeComment, me: String): Comment? {
        val author = personOf(c.authorId) ?: return null
        val replyName = c.replyTo?.let { r -> p.comments.firstOrNull { it.id == r } }?.let { personOf(it.authorId)?.name }
        return Comment(c.id, p.id, c.body, c.at, author, c.replyTo, replyName, c.authorId == me, c.authorId == me || p.author == me)
    }

    override suspend fun addComment(postId: String, body: String, replyTo: Long?): Comment {
        val me = me()
        requireVerified()
        val text = body.trim()
        if (text.length !in 1..Comment.MAX) throw BackendException("bad_request")
        val p = visiblePost(postId)
        val recent = posts.sumOf { fp -> fp.comments.count { it.authorId == me.id && clock() - it.at < 60_000L } }
        if (recent >= COMMENTS_PER_MINUTE) throw BackendException("slow_down")
        val reply = replyTo?.takeIf { r -> p.comments.any { it.id == r && !it.removed } }
        val c = FakeComment(commentSeq++, me.id, text, clock(), reply)
        p.comments += c
        notifyPost(InboxKind.POST_COMMENT, me, p)
        return comment(p, c, me.id)!!
    }

    override suspend fun removeComment(commentId: Long) {
        val me = me()
        val p = posts.firstOrNull { fp -> fp.comments.any { it.id == commentId && !it.removed } } ?: throw BackendException("not_found")
        val c = p.comments.first { it.id == commentId }
        if (c.authorId != me.id && p.author != me.id) throw BackendException("not_found")
        c.removed = true
    }

    override suspend fun reportPost(postId: String, reason: ReportReason) {
        val me = me()
        val p = visiblePost(postId)
        if (p.author == me.id) throw BackendException("not_found")
        postReports += "post:$postId" to reason
    }

    override suspend fun reportComment(commentId: Long, reason: ReportReason) {
        val me = me()
        val p = posts.firstOrNull { fp -> fp.comments.any { it.id == commentId && !it.removed } }?.takeIf { canSee(it) }
            ?: throw BackendException("not_found")
        if (p.comments.first { it.id == commentId }.authorId == me.id) throw BackendException("not_found")
        postReports += "comment:$commentId" to reason
    }
    /** voice_admit refuses unverified people when verification is required (the host of a new room is verified already). */
    override fun voiceSession(roomId: String): Flow<com.yaari.chat.model.VoiceEvent> = flow {
        requireVerified()
        emitAll(voice.session(roomId))
    }
    override suspend fun leaveVoice(roomId: String) = voice.leave(roomId)
    override suspend fun endVoice(roomId: String) = voice.endRoom(roomId)
    override suspend fun voiceLines(roomId: String) = voice.lines(roomId).filter {
        (it !is VoiceLine.Text || it.sender.id !in blocked) && (it !is VoiceLine.Photo || it.sender.id !in blocked)
    }
    override suspend fun sendVoice(roomId: String, text: String, replyTo: Long?) = checkSticker(text).let { voice.send(roomId, text, replyTo) }
    /** The demo keeps the photo on the phone: the line shows it from where it is. */
    override suspend fun sendVoicePhoto(roomId: String, jpegUri: String) = voice.photo(roomId, jpegUri)
    override suspend fun voiceIcebreaker(roomId: String) = voice.icebreaker(roomId)
    override suspend fun createVoicePoll(roomId: String, question: String, options: List<String>) = voice.createPoll(roomId, question, options)
    override suspend fun voicePollVote(pollId: Long, choice: Int) = voice.votePoll(pollId, choice)
    override suspend fun closeVoicePoll(pollId: Long) = voice.closePoll(pollId)
    override suspend fun voicePolls(ids: List<Long>) = voice.pollsOf(ids)
    /** The demo's videos carry their titles; anything else is "YouTube video". */
    override suspend fun videoTitle(videoId: String) = FakeVoice.TRACKS[videoId]?.first.orEmpty()
    override suspend fun voiceAction(roomId: String, action: VoiceAction) = voice.act(roomId, action)
    override suspend fun sendVoiceTyping(roomId: String, name: String) = Unit

    /** The demo makes no network calls: a card from the address itself (a few known sites get a description). */
    override suspend fun linkPreview(url: String): LinkPreview? {
        if (!Links.isWeb(url)) return null
        wait(250)
        val host = Links.host(url)
        val known = DEMO_SITES[host]
        return LinkPreview(url, known?.first ?: host, known?.second, null, host, frameable = host in DEMO_FRAMEABLE)
    }

    /**
     * The demo's translator: a small phrasebook for its own romanised Hindi lines (into English and Hindi). A line
     * already in the target language is "same"; anything else can't be translated without the live server.
     */
    override suspend fun translate(kind: TranslateKind, id: Long, target: String): Translation {
        if (!TranslateTargets.isValid(target)) throw BackendException("bad_request")
        wait(400)
        val text = when (kind) {
            TranslateKind.CHAT -> matches.values.firstNotNullOfOrNull { m -> m.messages.firstOrNull { it.id == id }?.text }
            TranslateKind.ROOM -> voice.lineText(id)
        } ?: throw BackendException("not_found")
        PHRASEBOOK[text]?.get(target)?.let { return Translation(it, same = false) }
        if (!ForeignText.looksForeign(text, target)) return Translation(text, same = true)
        throw BackendException("cannot_translate")
    }

    override suspend fun reportVoiceRoom(roomId: String, reason: ReportReason) {
        val host = voice.hostOf(roomId) ?: throw BackendException("not_found")
        if (host == me().id) throw BackendException("not_allowed")
        block(host)
    }

    companion object {
        const val DEMO_OTP = "123456"
        /** The demo's identity check: nothing to open, the check passes on return (see openVerification). */
        const val KYC_URL = "demo://kyc"
        /** Link cards the demo knows without the network: host to (title, description). */
        private val DEMO_SITES = mapOf(
            "wikipedia.org" to ("Wikipedia" to "The free encyclopedia"),
            "en.wikipedia.org" to ("Wikipedia" to "The free encyclopedia"),
            "cricbuzz.com" to ("Cricbuzz" to "Live cricket scores and news"),
            "yaari-web.vercel.app" to ("Yaari" to "Make friends around the world"),
        )
        /** Sites whose pages may be framed (the others refuse, as most real sites do). */
        private val DEMO_FRAMEABLE = setOf("wikipedia.org", "en.wikipedia.org", "yaari-web.vercel.app")
        private const val DAY = 86_400_000L

        /** The demo people's romanised Hindi, as the live translator would put it (English, Hindi). */
        private val PHRASEBOOK: Map<String, Map<String, String>> = mapOf(
            "Kya haal hai?" to mapOf("en" to "How's it going?", "hi" to "क्या हाल है?"),
            "Arre wah 😄" to mapOf("en" to "Oh wow 😄", "hi" to "अरे वाह 😄"),
            "nice room yaar" to mapOf("en" to "nice room, buddy", "hi" to "बढ़िया रूम है यार"),
            "mera vote Goa ke liye" to mapOf("en" to "my vote goes to Goa", "hi" to "मेरा वोट गोवा के लिए"),
            "volume ekdum perfect hai" to mapOf("en" to "the volume is just perfect", "hi" to "वॉल्यूम एकदम परफ़ेक्ट है"),
            "Arijit ka koi bhi gaana, bas 🔥" to mapOf("en" to "any Arijit song, that's all 🔥", "hi" to "अरिजीत का कोई भी गाना, बस 🔥"),
            "Bumrah ka over lethal tha" to mapOf("en" to "Bumrah's over was lethal", "hi" to "बुमराह का ओवर घातक था"),
            "Kohli 100 today, mark my words" to mapOf("hi" to "कोहली आज 100 मारेगा, लिख के ले लो"),
            "Where are you from?" to mapOf("hi" to "आप कहाँ से हो?", "bn" to "আপনি কোথা থেকে?", "pt-BR" to "De onde você é?", "vi" to "Bạn đến từ đâu?"),
            "What do you do?" to mapOf("hi" to "आप क्या करते हो?", "bn" to "আপনি কী করেন?", "pt-BR" to "O que você faz?", "vi" to "Bạn làm nghề gì?"),
        )

        /** manner_refresh: starts at 50; a bad rating weighs double. */
        fun manner(good: Int, bad: Int) = Math.round(100.0 * (good + 5) / (good + 2 * bad + 10)).toInt()

        /** The demo's ID-verified people. */
        private val VERIFIED = setOf("p1", "p3", "p4", "p5", "p6", "p7", "p9")

        /** Ratings others gave them: (good, bad, good tags with how many ratings carry each). */
        private val RATED: Map<String, Triple<Int, Int, List<Pair<MannerTag, Int>>>> = mapOf(
            "p1" to Triple(24, 1, listOf(MannerTag.KIND to 12, MannerTag.FUNNY to 9, MannerTag.GOOD_LISTENER to 6)),
            "p2" to Triple(3, 2, listOf(MannerTag.FUNNY to 2)),
            "p3" to Triple(40, 0, listOf(MannerTag.RESPECTFUL to 22, MannerTag.KIND to 18, MannerTag.FUNNY to 11)),
            "p5" to Triple(6, 1, listOf(MannerTag.INTERESTING to 4, MannerTag.KIND to 2)),
            "p6" to Triple(10, 2, listOf(MannerTag.INTERESTING to 6, MannerTag.RESPECTFUL to 3)),
            "p7" to Triple(1, 0, listOf(MannerTag.FUNNY to 1)),
            "p8" to Triple(8, 0, listOf(MannerTag.GOOD_LISTENER to 5, MannerTag.KIND to 3)),
            "p9" to Triple(5, 0, listOf(MannerTag.RESPECTFUL to 4)),
            "p10" to Triple(12, 0, listOf(MannerTag.INTERESTING to 7, MannerTag.FUNNY to 4)),
        )
        private const val PAGE = 20
        private const val COMMENTS_PER_MINUTE = 10
        /** Their first post is public; everyone else posts for friends only. */
        private val PUBLIC_POSTERS = setOf("p1", "p2", "p5", "p6", "p7", "p8", "p11")

        /** Drawn pictures for the demo's posts (no network needed): see ui DemoArt. */
        const val ART_SUNSET = "demo://snap"
        const val ART_CITY = "demo://city"
        const val ART_HILLS = "demo://hills"
        const val ART_LIGHTS = "demo://lights"

        /** The demo people's own details, so their profiles show the 033 features. */
        private val STYLES = mapOf(
            "p1" to ProfileStyle(
                status = "Chai first, then everything else", languages = SpokenLang.of(listOf("en", "hi", "mr")),
                prompts = listOf(
                    ProfilePrompt(PromptQ.WEEKEND, "A long walk on Marine Drive, then pav bhaji at midnight"),
                    ProfilePrompt(PromptQ.SONG, "Kesariya. It has been on loop since forever"),
                    ProfilePrompt(PromptQ.TALK, "You have a book recommendation ready"),
                ),
            ),
            "p2" to ProfileStyle(status = "Code by day, football by night", languages = SpokenLang.of(listOf("pt", "en"))),
            "p3" to ProfileStyle(
                status = "Dancing through the week", languages = SpokenLang.of(listOf("en", "kn", "hi")),
                prompts = listOf(ProfilePrompt(PromptQ.LAUGH, "Bruno chasing his own tail at 6 am")),
            ),
            "p5" to ProfileStyle(languages = SpokenLang.of(listOf("en", "pt"))),
            "p6" to ProfileStyle(
                status = "Chasing golden hour", languages = SpokenLang.of(listOf("hi", "en")),
                prompts = listOf(ProfilePrompt(PromptQ.TRAVEL, "Spiti valley, camera fully charged")),
            ),
            "p7" to ProfileStyle(status = "Vinyl collector"),
            "p8" to ProfileStyle(prompts = listOf(ProfilePrompt(PromptQ.FOOD, "A bowl of tonkotsu in a tiny Osaka alley"))),
            "p10" to ProfileStyle(status = "Building something", languages = SpokenLang.of(listOf("hi", "en", "pa"))),
        )

        /** A different ID once every 30 days, sooner with a paid ID change (set_handle). */
        private const val HANDLE_COOLDOWN_MS = 30 * DAY

        /** The demo people's IDs. */
        private val HANDLES = mapOf(
            "p1" to "priya.mumbai", "p2" to "lucas_sp", "p3" to "ananya.dances", "p4" to "minjun", "p5" to "sofia.viaja",
            "p6" to "vikram_clicks", "p7" to "emma_vinyl", "p8" to "kenji_ramen", "p9" to "aisha.poems", "p10" to "rohan_builds",
            "p11" to "maria_sings", "p12" to "daniel.berlin",
        )

        /** What some of the demo people wear (the others have a plain ID). */
        private val DEMO_LOOKS = mapOf(
            "p1" to Look("p1", "", "badge_crown", "color_rose", "font_script", "frame_sakura"),
            "p3" to Look("p3", "", "badge_diamond", "color_aurora", "font_round", "frame_frost"),
            "p6" to Look("p6", "", "badge_bolt", "color_gold", "font_bold", "frame_fire"),
            "p7" to Look("p7", "", "badge_heart", "color_violet", "font_retro", "frame_neon"),
            "p8" to Look("p8", "", "badge_flame", "color_ocean", "font_pixel", "frame_jewel"),
            "p10" to Look("p10", "", "badge_star", "color_rainbow", "font_future", "frame_sand"),
        )

        /** cosmetic_items' prices in rupees. */
        private val PRICES = mapOf(
            "badge_crown" to 99, "badge_diamond" to 149, "badge_flame" to 79, "badge_star" to 79, "badge_heart" to 79, "badge_bolt" to 79,
            "color_gold" to 49, "color_rose" to 49, "color_ocean" to 49, "color_emerald" to 49, "color_violet" to 49,
            "color_sunset" to 99, "color_aurora" to 99, "color_rainbow" to 129,
            "font_script" to 69, "font_retro" to 69, "font_bold" to 69, "font_pixel" to 69, "font_round" to 69, "font_future" to 69,
            "frame_frost" to 199, "frame_fire" to 199, "frame_jewel" to 249, "frame_sand" to 199, "frame_neon" to 149, "frame_sakura" to 149,
            "wall_saffron" to 79, "wall_midnight" to 79, "wall_paisley" to 79, "wall_mehndi" to 79, "wall_stars" to 79, "wall_waves" to 79,
            "sticker_namaste" to 49, "sticker_chai" to 49, "sticker_cricket" to 49, "sticker_love" to 49, "vip_anon" to 299,
        )

        /** 046: someone in VIP incognito, as the server shows them to a stranger. */
        val VIP = Profile("p-vip", VIP_NAME, Gender.FEMALE, 0, "", "")

        private val REPLIES = listOf(
            "Hii 😊", "Where are you from?", "Haha nice", "What do you do?", "Same here!",
            "Kya haal hai?", "I love chai ☕", "Just finished college today", "Nice profile btw",
            "Which music do you like?", "Arre wah 😄", "Cool, tell me more",
        )
        private val ANSWERS = listOf(
            "Yes 🙂", "Not really haha", "I'm from Pune", "Studying engineering",
            "Maybe! You?", "Bollywood all the way", "Cricket obviously 🏏",
        )

        /** The demo's posts that come with a (drawn) picture. */
        private val ART = mapOf("p1-0" to ART_SUNSET, "p6-0" to ART_HILLS, "p8-0" to ART_LIGHTS, "p4-0" to ART_CITY, "p7-0" to ART_CITY)

        private fun profile(id: Int, name: String, g: Gender, age: Int, city: String, country: String, bio: String, vararg posts: String) =
            Profile(
                id = "p$id", name = name, gender = g, age = age, city = city, bio = bio, colorSeed = id, country = country,
                posts = posts.mapIndexed { i, t -> Post("p$id-$i", t, ART["p$id-$i"], createdAt = 1_760_000_000_000L - i * 86_400_000L) },
            )

        /** Some scripted people picked interests (shown on their profiles, "You both like"). */
        private val TAGS = mapOf(
            "p1" to listOf(Interest.MUSIC, Interest.BOOKS, Interest.TRAVEL), "p2" to listOf(Interest.FOOTBALL, Interest.TECH, Interest.GAMING),
            "p3" to listOf(Interest.DANCE, Interest.PETS, Interest.MUSIC, Interest.FITNESS), "p5" to listOf(Interest.TRAVEL, Interest.FOOD, Interest.ART),
            "p6" to listOf(Interest.PHOTOGRAPHY, Interest.TRAVEL, Interest.ART), "p10" to listOf(Interest.BUSINESS, Interest.TECH, Interest.CRICKET),
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
        ).map { it.copy(interests = Interest.entries.filter { t -> t in TAGS[it.id].orEmpty() }) }
    }
}
