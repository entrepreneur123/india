package com.yaari.chat.data

import com.yaari.chat.model.Gender
import com.yaari.chat.model.Post
import com.yaari.chat.model.Profile
import com.yaari.chat.model.Target
import kotlinx.coroutines.delay
import kotlin.random.Random

/** In-memory stand-in for the server, with scripted partners. Delays are virtual under runTest. */
class FakeBackend(private val random: Random = Random.Default) : Backend {
    private val accounts = mutableMapOf<String, Profile>()

    override suspend fun sendOtp(phone: String) = delay(600)

    override suspend fun verifyOtp(phone: String, code: String): Boolean {
        delay(400)
        return code == DEMO_OTP
    }

    override fun accountFor(phone: String) = accounts[phone]

    override fun register(phone: String, profile: Profile) {
        check(phone !in accounts) { "This number already has an account" }
        accounts[phone] = profile
    }

    override suspend fun findPartner(me: Profile, target: Target, exclude: Set<String>): Profile? {
        delay(random.nextLong(900, 2200))
        return POOL.filter { target.accepts(it.gender) && it.id != me.id && it.id !in exclude }
            .randomOrNull(random)
    }

    override suspend fun partnerReact(partner: Profile, incoming: String?, canLeave: Boolean): PartnerEvent {
        delay(random.nextLong(700, 1800))
        val roll = random.nextInt(100)
        return when {
            canLeave && roll < 6 -> PartnerEvent.Left
            roll < 15 -> PartnerEvent.Silent
            incoming?.contains('?') == true -> PartnerEvent.Reply(ANSWERS.random(random))
            else -> PartnerEvent.Reply(REPLIES.random(random))
        }
    }

    override suspend fun answerVideoRequest(partner: Profile): Boolean {
        delay(random.nextLong(1500, 3000))
        return random.nextInt(100) < 70
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
