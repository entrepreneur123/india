package com.yaari.chat.data

import com.yaari.chat.domain.ExitPolicy
import com.yaari.chat.domain.Validation
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Media
import com.yaari.chat.model.Message
import com.yaari.chat.model.Post
import com.yaari.chat.model.Profile
import com.yaari.chat.model.RecentMatch
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.Target
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
) : Backend {
    private suspend fun wait(ms: Long) {
        if (!instant) delay(ms)
    }

    private sealed interface Outgoing {
        data class Text(val text: String?) : Outgoing
        data object VideoRequest : Outgoing
    }

    private class FakeMatch(val partner: Profile, val startedAt: Long) {
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

    private fun me() = phone?.let { accounts[it] } ?: throw BackendException("not_signed_in")
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
        val me = Profile("me-" + UUID.randomUUID(), p.name, p.gender, age, p.city, p.bio, p.photoUri, ph.hashCode())
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

    override fun events(matchId: String): Flow<MatchEvent> = flow {
        val m = match(matchId)
        for (out in m.outgoing) {
            when (out) {
                is Outgoing.Text -> {
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

    override suspend fun history(matchId: String) = match(matchId).messages.toList()

    override suspend fun send(matchId: String, text: String?, media: Media?): Message {
        val m = match(matchId)
        if (m.partner.id in blocked) throw BackendException("blocked")
        val msg = Message(nextId++, true, text, media, clock())
        m.messages += msg
        m.outgoing.trySend(Outgoing.Text(text)) // after the chat ends this is a DM; the partner still answers
        return msg
    }

    override suspend fun leave(matchId: String) {
        val m = match(matchId)
        if (m.ended) return
        if (clock() - m.startedAt < ExitPolicy.lockMs(entitlements.premium)) throw BackendException("exit_locked")
        m.ended = true
    }

    override suspend fun report(partnerId: String, reason: ReportReason, matchId: String?) {
        blocked += partnerId
        matches.values.filter { it.partner.id == partnerId }.forEach { it.ended = true }
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

    override suspend fun recents(): List<RecentMatch> =
        matches.entries.reversed().distinctBy { it.value.partner.id }
            .filter { it.value.partner.id !in blocked }
            .map { (id, m) -> RecentMatch(id, m.partner, m.startedAt, m.messages.lastOrNull()?.let { it.text ?: "[media]" }) }

    override suspend fun profile(userId: String): Profile? {
        if (userId in blocked) return null
        if (userId == phone?.let { accounts[it] }?.id) return me()
        return matches.values.firstOrNull { it.partner.id == userId }?.partner
    }

    override suspend fun addPost(text: String, imageUri: String?): Post {
        val ph = phone ?: throw BackendException("not_signed_in")
        val post = Post(UUID.randomUUID().toString(), text, imageUri, clock())
        accounts[ph] = me().let { it.copy(posts = listOf(post) + it.posts) }
        return post
    }

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

        private fun profile(id: Int, name: String, g: Gender, age: Int, city: String, bio: String, vararg posts: String) =
            Profile(
                id = "p$id", name = name, gender = g, age = age, city = city, bio = bio, colorSeed = id,
                posts = posts.mapIndexed { i, t -> Post("p$id-$i", t, createdAt = 1_760_000_000_000L - i * 86_400_000L) },
            )

        val POOL = listOf(
            profile(1, "Priya", Gender.FEMALE, 22, "Mumbai", "Chai, books & sunsets 🌅", "Marine Drive evenings", "New cafe find!", "Monsoon mood"),
            profile(2, "Rahul", Gender.MALE, 25, "Delhi", "Cricket and coding", "Weekend match 🏏", "Office view"),
            profile(3, "Ananya", Gender.FEMALE, 24, "Bengaluru", "Dancer. Dog mom 🐶", "Rehearsal day", "Bruno turned 3!"),
            profile(4, "Arjun", Gender.MALE, 21, "Pune", "Engineering student, gym rat", "Leg day", "Sinhagad trek"),
            profile(5, "Sneha", Gender.FEMALE, 27, "Hyderabad", "Biryani is a personality", "Best biryani in town", "Charminar at night"),
            profile(6, "Vikram", Gender.MALE, 29, "Jaipur", "Photographer 📷", "Golden hour at Amer", "Street portraits"),
            profile(7, "Meera", Gender.FEMALE, 23, "Kolkata", "Rabindra sangeet & rosogolla", "Durga Puja vibes", "College fest"),
            profile(8, "Karan", Gender.MALE, 26, "Chandigarh", "Bhangra + bikes", "Ride to Kasauli", "Sunday lassi"),
            profile(9, "Aisha", Gender.FEMALE, 20, "Lucknow", "Poetry & kebabs", "A new sher I wrote", "Hazratganj walk"),
            profile(10, "Rohan", Gender.MALE, 24, "Ahmedabad", "Startup dreams", "Pitch day!", "Navratri garba"),
            profile(11, "Kavya", Gender.FEMALE, 25, "Chennai", "Carnatic music, filter coffee", "Marina beach morning"),
            profile(12, "Aditya", Gender.MALE, 22, "Indore", "Foodie. Poha every day", "Sarafa night market"),
        )
    }
}
