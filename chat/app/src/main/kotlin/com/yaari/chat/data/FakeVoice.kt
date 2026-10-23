package com.yaari.chat.data

import com.yaari.chat.model.CreatedVoiceRoom
import com.yaari.chat.model.Gender
import com.yaari.chat.model.GenderRule
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.Links
import com.yaari.chat.model.RoomLang
import com.yaari.chat.model.RoomStage
import com.yaari.chat.model.RoomQuery
import com.yaari.chat.model.Profile
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.RoomRules
import com.yaari.chat.model.SpeakMode
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceEvent
import com.yaari.chat.model.VoiceLine
import com.yaari.chat.model.VoiceLost
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
import com.yaari.chat.model.byVotes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Demo rooms (a port of the web's lib/mockVoice.ts): a few live rooms with people talking (audio levels), chat,
 * icebreakers, photos, replies, polls, reactions, raised hands and song requests with votes. It applies the
 * server's rules (017_voice_rooms.sql, 019_unified_rooms.sql: who may come in, private codes, rate limits). The
 * simulation runs only while a room is followed ([session]), on the collector's dispatcher (virtual time in tests).
 * [ambient]: people talk, chat, react and come and go by themselves (off in tests, which drive the room through the
 * hooks at the bottom instead).
 */
class FakeVoice(
    private val me: () -> Profile,
    private val random: Random,
    private val clock: () -> Long,
    private val wait: suspend (Long) -> Unit,
    private val ambient: Boolean,
) {
    private class Member(val profile: Profile, var role: VoiceRole, var seat: Int?, var muted: Boolean, var hand: Boolean, var lost: Boolean = false)

    private class SimRoom(
        val id: String, var title: String, val category: RoomCategory, val mode: SpeakMode, var hostId: String,
        val members: MutableList<Member>, val locked: BooleanArray, var mine: Boolean,
        val rules: RoomRules, var announcement: String?, val code: String, var lang: String? = null,
    ) {
        var track: VoiceTrack? = null
        /** The page shared with everyone (036). */
        var stage: RoomStage? = null
        val queue = mutableListOf<VoiceQueueItem>()
        /** I came in with the code once (a private room then lets me back by its id). */
        var admitted = false
        var lastIcebreaker = Long.MIN_VALUE / 2
        val lines = mutableListOf<VoiceLine>()
        val subs = mutableSetOf<(VoiceEvent) -> Unit>()
        var scope: CoroutineScope? = null
        var ended = false
        var talkers = setOf<String>()
        var levels = mapOf<String, Float>()
        /** Fake people who raised a hand while I host (so the queue doesn't grow forever). */
        var handsRaised = 0
    }

    private val rooms = linkedMapOf<String, SimRoom>()

    /** A poll (029): who answered what; [closed] once a newer one opened or it was closed. */
    private class SimPoll(
        val id: Long, val roomId: String, val by: String, val question: String, val options: List<String>, val at: Long,
        val votes: LinkedHashMap<String, Int> = linkedMapOf(), var closed: Boolean = false,
    )

    private val polls = linkedMapOf<Long, SimPoll>()
    private var pollSeq = 1L
    private var lineSeq = 1L
    private var personSeq = 0
    private var uidSeq = 0
    private val sendTimes = ArrayDeque<Long>()
    private val photoTimes = ArrayDeque<Long>()

    init {
        seed("v-bolly", "Late night Bollywood songs", RoomCategory.MUSIC, SpeakMode.APPROVAL, "Priya", 5, 34,
            Music("BddP6PYo2gs", 74_000, listOf("Umqb9KENgmk", "284Ov7ysmfA", "VAdGW7QDJiU"), listOf(4, 7, 2)),
            announcement = "Requests welcome: paste a YouTube link, vote for the next song.", lang = "hi")
        seed("v-chai", "Chai pe charcha", RoomCategory.TALK, SpeakMode.APPROVAL, "Meera", 0, 14, announcement = "Be kind. No phone numbers in the chat.", lang = "hi")
        seed("v-cricket", "Cricket talk: IND vs AUS", RoomCategory.TALK, SpeakMode.APPROVAL, "Vikram", 6, 48, lang = "en")
        seed("v-adda", "Adda: Kolkata nights", RoomCategory.TALK, SpeakMode.OPEN, "Arnab", 4, 12, lang = "bn")
        seed("v-lofi", "Lo-fi study together", RoomCategory.MUSIC, SpeakMode.OPEN, "Kavya", 2, 17, Music("lTRiuFIWV54", 480_000, emptyList(), emptyList()), lang = "en")
        seed("v-gym", "Gym bros: fitness talk", RoomCategory.TALK, SpeakMode.OPEN, "Harsh", 2, 6,
            rules = RoomRules(maxMembers = 30, genderRule = GenderRule.MALE, minAge = 18, maxAge = 30), lang = "hi")
        // The chai room's host asked the room something (fixed answers: the demo's dice stay as they were).
        rooms.getValue("v-chai").let { r ->
            val host = r.members.first { it.profile.id == r.hostId }.profile
            val p = SimPoll(pollSeq++, r.id, host.id, "Chai or coffee?", listOf("Chai, always", "Coffee", "Depends on the mood"), clock())
            r.members.drop(1).take(9).forEachIndexed { i, m -> p.votes[m.profile.id] = listOf(0, 0, 2, 0, 1, 0, 2, 0, 1)[i] }
            polls[p.id] = p
            r.lines += VoiceLine.Poll(lineSeq++, host, false, p.id, p.question, p.options, clock() - 20_000)
        }
    }

    private class Music(val playing: String, val ago: Long, val queue: List<String>, val votes: List<Int>)

    private fun uid() = "q" + (uidSeq++).toString(36) + random.nextInt(1_000_000).toString(36)
    private fun <T> pick(list: List<T>) = list[random.nextInt(list.size)]
    private fun jitter(min: Long, max: Long) = if (max <= min) min else random.nextLong(min, max)

    /** A new demo person: the given name, or the next one from the list (with an initial once names repeat). */
    private fun person(given: String? = null): Profile {
        val n = personSeq++
        val first = given ?: NAMES[n % NAMES.size]
        return Profile(
            id = "v_${first.lowercase()}_$n",
            name = if (given != null || n < NAMES.size) first else "$first ${"ABCDGKMPRS"[n % 10]}.",
            gender = if (n % 2 == 1) Gender.MALE else Gender.FEMALE,
            age = 19 + (n * 7) % 14, city = CITIES[n % CITIES.size], bio = BIOS[n % BIOS.size], colorSeed = n, country = "IN",
        )
    }

    /** A room's code: its own dice (seeded by the room id), so tests with fixed dice still get distinct, realistic codes. */
    private fun code(roomId: String): String {
        var seed = roomId.hashCode()
        while (true) {
            val dice = Random(seed++)
            val c = String(CharArray(VoiceRoom.CODE_LENGTH) { CODE_CHARS[dice.nextInt(CODE_CHARS.length)] })
            if (rooms.values.none { it.code == c }) return c
        }
    }

    private fun seed(
        id: String, title: String, category: RoomCategory, mode: SpeakMode, hostName: String, speakers: Int, listeners: Int,
        music: Music? = null, rules: RoomRules = ANYONE, announcement: String? = null, lang: String? = null,
    ) {
        val people = listOf(person(hostName)) + List(maxOf(speakers, 1) + listeners - 1) { person() }
        val members = people.mapIndexed { i, p ->
            Member(
                p,
                role = when {
                    i == 0 -> VoiceRole.HOST
                    i == 1 && speakers > 2 -> VoiceRole.COHOST
                    i < speakers -> VoiceRole.SPEAKER
                    else -> VoiceRole.LISTENER
                },
                seat = if (i < speakers) i else null,
                // A few speakers sit muted, as in any real room.
                muted = if (i >= speakers) true else i > 1 && i % 3 == 0,
                hand = i >= speakers && i % 11 == 4,
            )
        }
        val r = SimRoom(id, title, category, mode, people[0].id, members.toMutableList(),
            BooleanArray(VoiceRoom.SEATS) { it == 8 && mode == SpeakMode.APPROVAL }, mine = false, rules, announcement, code(id), lang)
        if (music != null) {
            r.track = track(music.playing, people[0].name, clock() - music.ago)
            music.queue.forEachIndexed { i, v -> r.queue += VoiceQueueItem(uid(), v, TRACKS[v]?.first.orEmpty(), people[i + 1].name, music.votes.getOrElse(i) { 0 }) }
        }
        val now = clock()
        for (i in 0 until 7) {
            val sender = pick(people)
            val at = now - (7 - i) * 45_000L
            if (i == 2) r.lines += VoiceLine.System(lineSeq++, VoiceSystemEvent.JOINED, pick(people).name, at)
            if (i == 3 && category == RoomCategory.TALK) r.lines += VoiceLine.Icebreaker(lineSeq++, 7, pick(people).name, false, at)
            if (i == 5 && id == "v-chai") r.lines += VoiceLine.Photo(lineSeq++, people[1], false, SNAP, at)
            r.lines += VoiceLine.Text(lineSeq++, sender, false, chatLine(category, sender), at)
        }
        rooms[id] = r
    }

    private fun track(videoId: String, addedBy: String, startedAt: Long = clock()) =
        VoiceTrack(uid(), videoId, TRACKS[videoId]?.first.orEmpty(), addedBy, startedAt, null)

    private fun chatLine(category: RoomCategory, sender: Profile): String {
        val list = if (random.nextFloat() < 0.8f) LINES.getValue(category) else ANY
        return pick(list).replace("{city}", sender.city)
    }

    private fun get(roomId: String): SimRoom {
        val r = rooms[roomId]
        if (r == null || r.ended) throw BackendException("room_closed")
        return r
    }

    private fun meIn(r: SimRoom): Member? = runCatching { me() }.getOrNull()?.let { m -> r.members.firstOrNull { it.profile.id == m.id } }

    /** Same rules as the server's voice_refusal(): the host always may; gender, age, full. */
    private fun refusal(r: SimRoom): String? {
        val me = runCatching { me() }.getOrNull() ?: return null
        if (r.mine) return null
        return when {
            !r.rules.genderRule.accepts(me.gender) -> "room_gender"
            me.age < r.rules.minAge || me.age > r.rules.maxAge -> "room_age"
            meIn(r) == null && r.members.size >= r.rules.maxMembers -> "room_full"
            else -> null
        }
    }

    private fun snapshot(r: SimRoom) = VoiceRoom(
        r.id, r.title, r.category, r.mode, r.hostId, runCatching { me().id }.getOrDefault(""),
        r.members.map { VoiceMember(it.profile, it.role, it.seat, it.muted, it.hand, it.lost) },
        List(VoiceRoom.SEATS) { VoiceSeat(it, r.locked[it]) }, r.track, r.queue.toList(),
        r.rules, r.announcement, if (r.mine) r.code else null, r.lang, r.stage,
    )

    private fun emit(r: SimRoom, e: VoiceEvent) = r.subs.toList().forEach { it(e) }
    private fun changed(r: SimRoom) = emit(r, VoiceEvent.RoomChanged(snapshot(r)))

    private fun system(r: SimRoom, event: VoiceSystemEvent, name: String, seat: Int? = null, title: String? = null) {
        val line = VoiceLine.System(lineSeq++, event, name, clock(), seat, title)
        r.lines += line
        emit(r, VoiceEvent.Line(line))
    }

    private fun freeSeat(r: SimRoom, preferred: Int? = null): Int? {
        val taken = r.members.mapNotNull { it.seat }.toSet()
        fun ok(i: Int) = i in 1 until VoiceRoom.SEATS && i !in taken && !r.locked[i]
        if (preferred != null && ok(preferred)) return preferred
        return (1 until VoiceRoom.SEATS).firstOrNull(::ok)
    }

    private fun seatMember(r: SimRoom, m: Member, seat: Int) {
        m.seat = seat
        m.hand = false
        m.muted = true // everyone comes up muted and unmutes when ready (Clubhouse)
        if (m.role == VoiceRole.LISTENER) m.role = VoiceRole.SPEAKER
        system(r, VoiceSystemEvent.SEAT, m.profile.name, seat = seat)
    }

    /** Profiles of demo people, for the profile screen. */
    fun profileOf(id: String): Profile? = rooms.values.firstNotNullOfOrNull { r -> r.members.firstOrNull { it.profile.id == id }?.profile }

    /** A text line's words by its id, in any room (the demo's translator). */
    fun lineText(id: Long): String? = rooms.values.firstNotNullOfOrNull { r -> (r.lines.firstOrNull { it.id == id } as? VoiceLine.Text)?.text }

    fun hostOf(roomId: String) = rooms[roomId]?.hostId
    fun titleOf(roomId: String?) = rooms[roomId]?.title
    fun isOpen(roomId: String?) = rooms[roomId]?.ended == false
    /** [userId] is in the room right now. */
    fun isIn(roomId: String, userId: String) = rooms[roomId]?.takeIf { !it.ended }?.members?.any { it.profile.id == userId } == true

    /** An invite lets me in even when the room is private (the server's join_invite). */
    fun admit(roomId: String) {
        get(roomId).admitted = true
    }

    /** Live rooms (the server's voice_list3): mine first, then the most people present; [query] narrows them. */
    suspend fun list(query: RoomQuery = RoomQuery()): List<VoiceRoomSummary> {
        wait(450)
        val me = runCatching { me() }.getOrNull()
        val words = query.text.trim()
        val lang = RoomLang.of(query.lang)
        return rooms.values.filter { !it.ended && (!it.rules.isPrivate || it.mine || it.admitted) }
            .filter { (words.isEmpty() || it.title.contains(words, ignoreCase = true)) && (lang == null || it.lang == lang) }
            .sortedWith(compareByDescending<SimRoom> { it.mine }.thenByDescending { it.members.size }).map { r ->
                val host = r.members.first { it.profile.id == r.hostId }.profile
                VoiceRoomSummary(
                    r.id, r.title, r.category, host,
                    speakers = r.members.filter { it.seat != null }.sortedBy { it.seat }.take(4).map { it.profile },
                    listeners = r.members.count { it.seat == null && it.profile.id != me?.id },
                    nowPlaying = r.track?.title?.ifEmpty { null }, mine = r.mine,
                    members = r.members.size,
                    rules = r.rules, announcement = r.announcement, code = if (r.mine) r.code else null, refusal = refusal(r),
                    lang = r.lang,
                )
            }
    }

    suspend fun create(n: NewVoiceRoom): CreatedVoiceRoom {
        wait(500)
        val me = me()
        val title = n.title.trim()
        if (title.length !in VoiceRoom.MIN_TITLE..VoiceRoom.MAX_TITLE) throw BackendException("room_title")
        if (n.rules.maxMembers !in VoiceRoom.MIN_MEMBERS..VoiceRoom.MAX_MEMBERS) throw BackendException("room_size")
        rooms.values.filter { it.mine && !it.ended }.forEach { end(it) } // one live room per host
        val id = "v-" + uid()
        // A couple of people are already in, so the host isn't alone. The host comes in off the stage: the room is a
        // chat room until someone joins the voice.
        val early = List(3) { person() }
        val members = mutableListOf(Member(me, VoiceRole.HOST, null, muted = true, hand = false))
        early.forEach { members += Member(it, VoiceRole.LISTENER, null, muted = true, hand = false) }
        val r = SimRoom(
            id, title, n.category, SpeakMode.APPROVAL, me.id, members, BooleanArray(VoiceRoom.SEATS), mine = true,
            n.rules, n.announcement.trim().take(VoiceRoom.MAX_ANNOUNCEMENT).ifEmpty { null }, code(id), RoomLang.of(n.lang) ?: "en",
        )
        rooms[id] = r
        return CreatedVoiceRoom(id, r.code)
    }

    suspend fun joinCode(code: String): String {
        wait(350)
        val r = rooms.values.firstOrNull { !it.ended && it.code == code.trim().uppercase() } ?: throw BackendException("not_found")
        refusal(r)?.let { throw BackendException(it) }
        r.admitted = true
        return r.id
    }

    suspend fun settings(roomId: String, title: String, announcement: String, lang: String? = null) {
        val r = get(roomId)
        if (!r.mine) throw BackendException("not_allowed")
        val t = title.trim()
        if (t.length !in VoiceRoom.MIN_TITLE..VoiceRoom.MAX_TITLE) throw BackendException("room_title")
        wait(200)
        r.title = t
        r.announcement = announcement.trim().take(VoiceRoom.MAX_ANNOUNCEMENT).ifEmpty { null }
        RoomLang.of(lang)?.let { r.lang = it }
        changed(r)
    }

    private fun post(r: SimRoom, line: VoiceLine): VoiceLine {
        r.lines += line
        emit(r, VoiceEvent.Line(line))
        return line
    }

    suspend fun icebreaker(roomId: String): VoiceLine {
        val r = get(roomId)
        val me = me()
        if (meIn(r) == null) throw BackendException("not_allowed")
        if (clock() - r.lastIcebreaker < ICEBREAKER_MS) throw BackendException("slow_down")
        wait(150)
        r.lastIcebreaker = clock()
        val used = r.lines.takeLast(60).filterIsInstance<VoiceLine.Icebreaker>().map { it.prompt }.toSet()
        val free = (1..VoiceRoom.PROMPTS).filter { it !in used }
        val line = post(r, VoiceLine.Icebreaker(lineSeq++, pick(free.ifEmpty { listOf(1) }), me.name, true, clock()))
        // Someone answers a moment later.
        later(r, jitter(2_500, 4_000)) {
            others(r).takeIf { it.isNotEmpty() }?.let { post(r, VoiceLine.Text(lineSeq++, pick(it).profile, false, pick(ANSWERS), clock())) }
        }
        return line
    }

    suspend fun photo(roomId: String, url: String): VoiceLine {
        val r = get(roomId)
        val me = me()
        if (meIn(r) == null) throw BackendException("not_allowed")
        val now = clock()
        while (photoTimes.isNotEmpty() && now - photoTimes.first() >= 60_000) photoTimes.removeFirst()
        if (photoTimes.size >= 5) throw BackendException("slow_down")
        photoTimes += now
        wait(400)
        return post(r, VoiceLine.Photo(lineSeq++, me, true, url, now))
    }

    /** Enters the room and follows it; the simulation runs while collected. */
    fun session(roomId: String): Flow<VoiceEvent> = channelFlow {
        wait(350)
        val r = get(roomId)
        val me = me()
        if (r.rules.isPrivate && !r.mine && !r.admitted && meIn(r) == null) throw BackendException("not_found")
        refusal(r)?.let { throw BackendException(it) }
        val sub: (VoiceEvent) -> Unit = { trySend(it) }
        r.subs += sub
        r.scope = this
        if (meIn(r) == null) {
            r.members += Member(me, VoiceRole.LISTENER, null, muted = true, hand = false)
            system(r, VoiceSystemEvent.JOINED, me.name)
        }
        send(VoiceEvent.RoomChanged(snapshot(r)))
        if (ambient) start(r, this)
        awaitClose {
            r.subs -= sub
            if (r.scope === this) r.scope = null
            r.talkers = emptySet()
            r.levels = emptyMap()
        }
    }

    suspend fun leave(roomId: String) {
        val r = rooms[roomId] ?: return
        val me = runCatching { me() }.getOrNull() ?: return
        if (r.ended) return
        wait(100)
        if (r.mine) return // my own room stays live until I end it
        r.members.removeAll { it.profile.id == me.id }
        changed(r)
    }

    suspend fun endRoom(roomId: String) {
        val r = get(roomId)
        if (!r.mine) throw BackendException("not_allowed")
        wait(300)
        end(r)
    }

    private fun end(r: SimRoom) {
        r.ended = true
        emit(r, VoiceEvent.Ended)
        r.subs.clear()
    }

    suspend fun lines(roomId: String): List<VoiceLine> {
        wait(300)
        return get(roomId).lines.toList()
    }

    /** voice_send2: [replyTo] quotes a text, photo or poll line of this room (anything else is sent without a quote). */
    suspend fun send(roomId: String, text: String, replyTo: Long? = null): VoiceLine {
        val r = get(roomId)
        val me = me()
        val t = text.trim()
        if (t.isEmpty() || t.length > 500) throw BackendException("bad_request")
        val now = clock()
        while (sendTimes.isNotEmpty() && now - sendTimes.first() >= 5_000) sendTimes.removeFirst()
        if (sendTimes.size >= 5) throw BackendException("slow_down")
        sendTimes += now
        wait(120)
        val line = VoiceLine.Text(lineSeq++, me, true, t, now, quote(r, replyTo))
        r.lines += line
        emit(r, VoiceEvent.Line(line))
        return line
    }

    private fun quote(r: SimRoom, replyTo: Long?): VoiceReply? = replyTo?.let { id -> r.lines.firstOrNull { it.id == id }?.let(VoiceReply::of) }

    // ---- polls (029) ----

    private fun json(p: SimPoll, me: String?) = VoicePoll(
        p.id, p.question, p.options, List(p.options.size) { i -> p.votes.values.count { it == i } }, p.votes.size, p.closed, p.by,
        me?.let { p.votes[it] },
    )

    /** Everyone in the room sees the new counts (each app keeps its own answer: mine is null). */
    private fun pollChanged(p: SimPoll) {
        rooms[p.roomId]?.let { emit(it, VoiceEvent.PollChanged(json(p, null))) }
    }

    suspend fun createPoll(roomId: String, question: String, options: List<String>): VoiceLine {
        val r = get(roomId)
        val o = options.map { it.trim() }.filter { it.isNotEmpty() }
        if (!VoicePoll.valid(question, o)) throw BackendException("bad_request")
        val me = meIn(r) ?: throw BackendException("not_allowed")
        if (me.role != VoiceRole.HOST && me.role != VoiceRole.COHOST) throw BackendException("not_allowed")
        if (polls.values.count { it.roomId == roomId && clock() - it.at < 10 * 60_000L } >= 5) throw BackendException("slow_down")
        wait(200)
        return ask(r, me.profile, question.trim(), o, fromMe = true)
    }

    private fun ask(r: SimRoom, by: Profile, question: String, options: List<String>, fromMe: Boolean): VoiceLine {
        polls.values.filter { it.roomId == r.id && !it.closed }.forEach { it.closed = true; pollChanged(it) }
        val p = SimPoll(pollSeq++, r.id, by.id, question, options, clock())
        polls[p.id] = p
        val line = post(r, VoiceLine.Poll(lineSeq++, by, fromMe, p.id, question, options, clock()))
        // The room answers over the next seconds.
        others(r).filter { it.profile.id != by.id }.shuffled(random).take(3).forEachIndexed { i, m ->
            later(r, 1_500L + i * 1_700L) {
                if (!p.closed) {
                    p.votes[m.profile.id] = random.nextInt(options.size)
                    pollChanged(p)
                }
            }
        }
        return line
    }

    suspend fun votePoll(pollId: Long, choice: Int): VoicePoll {
        val p = polls[pollId]
        val me = runCatching { me() }.getOrNull()
        val r = p?.let { rooms[it.roomId] }
        if (p == null || me == null || r == null || r.ended || r.members.none { it.profile.id == me.id }) throw BackendException("not_found")
        if (p.closed) throw BackendException("poll_closed")
        if (choice !in p.options.indices) throw BackendException("bad_request")
        wait(120)
        p.votes[me.id] = choice
        pollChanged(p)
        return json(p, me.id)
    }

    suspend fun closePoll(pollId: Long): VoicePoll {
        val p = polls[pollId] ?: throw BackendException("not_found")
        val me = me()
        val m = rooms[p.roomId]?.members?.firstOrNull { it.profile.id == me.id }
        if (p.by != me.id && m?.role != VoiceRole.HOST && m?.role != VoiceRole.COHOST) throw BackendException("not_allowed")
        wait(120)
        p.closed = true
        pollChanged(p)
        return json(p, me.id)
    }

    suspend fun pollsOf(ids: List<Long>): List<VoicePoll> {
        wait(150)
        val me = runCatching { me().id }.getOrNull()
        return ids.take(50).mapNotNull { polls[it] }.filter { p -> rooms[p.roomId]?.members?.any { it.profile.id == me } == true }
            .sortedBy { it.id }.map { json(it, me) }
    }

    // ---- the simulation: runs only while someone follows the room ----

    private fun every(scope: CoroutineScope, min: Long, max: Long, fn: () -> Unit) = scope.launch {
        while (isActive) {
            delay(jitter(min, max))
            fn()
        }
    }

    private fun later(r: SimRoom, ms: Long, fn: () -> Unit): Job? = r.scope?.launch {
        delay(ms)
        if (!r.ended) fn()
    }

    private fun others(r: SimRoom): List<Member> {
        val me = runCatching { me().id }.getOrNull()
        return r.members.filter { it.profile.id != me }
    }

    private fun start(r: SimRoom, scope: CoroutineScope) {
        // Who is talking: one or two unmuted people on the stage, changing every few seconds.
        val rotate = {
            val stage = others(r).filter { it.seat != null && !it.muted && !it.lost }.shuffled(random)
            r.talkers = stage.take(if (random.nextFloat() < 0.3f) 2 else 1).map { it.profile.id }.toSet()
            if (random.nextFloat() < 0.15f) r.talkers = emptySet() // a pause in the conversation
        }
        rotate()
        every(scope, 1_800, 4_500, rotate)
        every(scope, LEVEL_MS, LEVEL_MS) {
            // Speech is bursty: mostly mid-level with syllable peaks and short gaps.
            val levels = buildMap {
                for (id in r.talkers) {
                    val prev = r.levels[id] ?: 0f
                    val next = if (random.nextFloat() < 0.18f) 0f else (prev * 0.4f + (0.25f + random.nextFloat() * 0.75f) * 0.6f).coerceIn(0.15f, 1f)
                    if (next > 0f) put(id, Math.round(next * 100) / 100f)
                }
            }
            r.levels = levels
            emit(r, VoiceEvent.Levels(levels))
        }
        every(scope, 3_500, 7_500) {
            // They type for a moment first (the room's typing indicator), then the line arrives.
            val sender = pick(others(r)).profile
            emit(r, VoiceEvent.Typing(sender.id, sender.name))
            later(r, 1_500) {
                val line = VoiceLine.Text(lineSeq++, sender, false, chatLine(r.category, sender), clock())
                r.lines += line
                emit(r, VoiceEvent.Line(line))
            }
        }
        every(scope, 2_500, 6_000) {
            val from = if (random.nextFloat() < 0.6f) others(r).filter { it.seat != null } else others(r)
            if (from.isNotEmpty()) emit(r, VoiceEvent.Reaction(pick(from).profile.id, pick(VoiceReaction.entries)))
        }
        // People come and go.
        every(scope, 9_000, 16_000) {
            val audience = others(r).filter { it.seat == null }
            if (random.nextFloat() < 0.25f && audience.size > 4) {
                val gone = pick(audience)
                r.members -= gone
                system(r, VoiceSystemEvent.LEFT, gone.profile.name)
            } else {
                val p = person()
                r.members += Member(p, VoiceRole.LISTENER, null, muted = true, hand = false)
                system(r, VoiceSystemEvent.JOINED, p.name)
            }
            changed(r)
        }
        // Now and then someone on the stage loses their connection (045): back within the 7 s, or out of the room.
        every(scope, 25_000, 40_000) {
            val m = others(r).filter { it.seat != null && it.role != VoiceRole.HOST && !it.lost }.takeIf { it.isNotEmpty() }?.let(::pick)
            if (m != null) lose(r, m, back = random.nextFloat() < 0.6f, backMs = jitter(2_500, 6_000))
        }
        // Open rooms: now and then someone takes a free seat.
        if (r.mode == SpeakMode.OPEN && !r.mine) {
            every(scope, 14_000, 24_000) {
                val seat = freeSeat(r)
                val audience = others(r).filter { it.seat == null }
                if (seat == null || audience.isEmpty()) return@every
                val m = pick(audience)
                seatMember(r, m, seat)
                m.muted = false
                changed(r)
            }
        }
        // Hosting: hands go up in the audience, so the requests queue has something in it.
        if (r.mine) {
            val raise = {
                val audience = others(r).filter { it.seat == null && !it.hand }
                if (audience.isNotEmpty() && r.handsRaised < 4) {
                    pick(audience).hand = true
                    r.handsRaised++
                    changed(r)
                }
            }
            later(r, 4_000, raise)
            later(r, 6_500, raise)
            every(scope, 18_000, 30_000, raise)
        } else {
            // A host that isn't me plays the queue on: the next track starts when this one would have ended.
            every(scope, 4_000, 4_000) {
                val t = r.track
                val seconds = t?.let { TRACKS[it.videoId]?.second } ?: 0
                if (t != null && seconds > 0 && t.pausedAt == null && clock() - t.startedAt > seconds * 1000L) skip(r)
            }
        }
    }

    /** [m]'s connection drops: shown as lost, then back after [backMs] or out of the room after the server's 7 s. */
    private fun lose(r: SimRoom, m: Member, back: Boolean, backMs: Long = 3_000) {
        m.lost = true
        r.talkers -= m.profile.id
        changed(r)
        later(r, if (back) backMs else LOST_OUT_MS) {
            if (m !in r.members) return@later
            if (back) m.lost = false
            else {
                r.members -= m
                system(r, VoiceSystemEvent.LEFT, m.profile.name)
            }
            changed(r)
        }
    }

    /** The most voted request plays next (ties: the oldest). */
    private fun skip(r: SimRoom) {
        val best = r.queue.byVotes().firstOrNull()
        if (best == null) {
            r.track = null
        } else {
            val next = best.also { r.queue.remove(it) }
            r.track = track(next.videoId, next.addedBy).copy(title = next.title)
            system(r, VoiceSystemEvent.TRACK, next.addedBy, title = next.title)
        }
        changed(r)
    }

    // ---- actions ----

    suspend fun act(roomId: String, a: VoiceAction) {
        val r = get(roomId)
        val me = meIn(r) ?: throw BackendException("not_allowed")
        wait(if (a is VoiceAction.React) 0 else 150)
        val isHost = me.role == VoiceRole.HOST
        val isMod = isHost || me.role == VoiceRole.COHOST
        val userId = when (a) {
            is VoiceAction.Invite -> a.userId
            is VoiceAction.Answer -> a.userId
            is VoiceAction.ToAudience -> a.userId
            is VoiceAction.Mute -> a.userId
            is VoiceAction.Cohost -> a.userId
            is VoiceAction.Kick -> a.userId
            else -> null
        }
        val target = userId?.let { id -> r.members.firstOrNull { it.profile.id == id } }
        fun need(ok: Boolean) {
            if (!ok) throw BackendException("not_allowed")
        }

        when (a) {
            is VoiceAction.Hand -> {
                need(me.seat == null)
                me.hand = a.up
                // Someone else's room: the host brings me up after a few seconds.
                if (a.up && !r.mine) {
                    later(r, jitter(3_500, 6_000)) {
                        val seat = freeSeat(r)
                        if (me.hand && me.seat == null && seat != null) {
                            seatMember(r, me, seat)
                            changed(r)
                        }
                    }
                }
            }
            is VoiceAction.Mic -> {
                need(me.seat != null)
                me.muted = !a.on
            }
            is VoiceAction.TakeSeat -> when {
                isHost && me.seat == null -> {
                    me.seat = 0 // the host joins the voice
                    me.hand = false
                    me.muted = true
                }
                me.seat != null -> {
                    need(!isHost && freeSeat(r, a.seat) == a.seat)
                    me.seat = a.seat // moving to another seat on the stage
                }
                r.mode == SpeakMode.OPEN || isMod -> {
                    need(freeSeat(r, a.seat) == a.seat)
                    seatMember(r, me, a.seat)
                }
                else -> return act(roomId, VoiceAction.Hand(true))
            }
            VoiceAction.Unseat -> {
                need(me.seat != null)
                me.seat = null
                me.muted = true
                if (me.role == VoiceRole.SPEAKER) me.role = VoiceRole.LISTENER
                if (!isHost) system(r, VoiceSystemEvent.AUDIENCE, me.profile.name)
            }
            is VoiceAction.React -> {
                emit(r, VoiceEvent.Reaction(me.profile.id, a.kind))
                return
            }
            is VoiceAction.Invite -> {
                need(isMod && target != null && target.seat == null)
                need(freeSeat(r, a.seat) != null)
                // The invited person says yes a moment later.
                later(r, 1_200) {
                    val s = freeSeat(r, a.seat)
                    if (target!!.seat == null && s != null && target in r.members) {
                        seatMember(r, target, s)
                        later(r, 1_500) {
                            target.muted = false
                            changed(r)
                        }
                        changed(r)
                    }
                }
            }
            is VoiceAction.Answer -> {
                need(isMod && target != null)
                if (a.accept) {
                    val seat = freeSeat(r)
                    need(seat != null)
                    seatMember(r, target!!, seat!!)
                    later(r, 1_500) {
                        target.muted = false
                        changed(r)
                    }
                } else {
                    target!!.hand = false
                }
            }
            is VoiceAction.ToAudience -> {
                need(isMod && target != null && target.seat != null && moderates(me, target))
                target!!.seat = null
                if (target.role == VoiceRole.SPEAKER) target.role = VoiceRole.LISTENER
                system(r, VoiceSystemEvent.AUDIENCE, target.profile.name)
            }
            is VoiceAction.Mute -> {
                need(isMod && target != null && target.role != VoiceRole.HOST)
                target!!.muted = true
            }
            is VoiceAction.Lock -> {
                need(isMod && a.seat in 1 until VoiceRoom.SEATS && r.members.none { it.seat == a.seat })
                r.locked[a.seat] = a.locked
            }
            is VoiceAction.Cohost -> {
                need(isHost && target != null && target.role != VoiceRole.HOST)
                target!!.role = if (a.on) VoiceRole.COHOST else if (target.seat == null) VoiceRole.LISTENER else VoiceRole.SPEAKER
                if (a.on) system(r, VoiceSystemEvent.COHOST, target.profile.name)
            }
            is VoiceAction.Kick -> {
                // 036: the host removes anyone but themself; a co-host only listeners and speakers.
                need(isMod && target != null && moderates(me, target))
                r.members -= target!!
                system(r, VoiceSystemEvent.REMOVED, target.profile.name)
            }
            is VoiceAction.AddTrack -> {
                val title = a.title.ifEmpty { TRACKS[a.videoId]?.first.orEmpty() }
                if (isMod && r.track == null) {
                    r.track = track(a.videoId, me.profile.name).copy(title = title)
                    system(r, VoiceSystemEvent.TRACK, me.profile.name, title = title)
                } else {
                    if (r.queue.size >= 30) throw BackendException("queue_full")
                    if (!isMod && r.queue.count { it.addedBy == me.profile.name } >= 3) throw BackendException("slow_down")
                    val item = VoiceQueueItem(uid(), a.videoId, title, me.profile.name)
                    r.queue += item
                    // People in the room like it: a couple of votes arrive.
                    for (ms in listOf(2_200L, 4_800L)) {
                        later(r, ms) {
                            val i = r.queue.indexOfFirst { it.id == item.id }
                            if (i >= 0) {
                                r.queue[i] = r.queue[i].copy(votes = r.queue[i].votes + 1)
                                changed(r)
                            }
                        }
                    }
                }
            }
            is VoiceAction.Vote -> {
                val i = r.queue.indexOfFirst { it.id == a.itemId }
                if (i < 0) throw BackendException("not_found")
                val q = r.queue[i]
                if (q.voted == a.on) return
                r.queue[i] = q.copy(voted = a.on, votes = q.votes + if (a.on) 1 else -1)
            }
            is VoiceAction.Play -> {
                need(isMod)
                val i = r.queue.indexOfFirst { it.id == a.itemId }
                need(i >= 0)
                val item = r.queue.removeAt(i)
                r.track = track(item.videoId, item.addedBy).copy(title = item.title)
                system(r, VoiceSystemEvent.TRACK, me.profile.name, title = item.title)
            }
            is VoiceAction.RemoveItem -> {
                need(isMod)
                r.queue.removeAll { it.id == a.itemId }
            }
            VoiceAction.Pause -> {
                val t = r.track
                need(isMod && t != null && t.pausedAt == null)
                r.track = t!!.copy(pausedAt = clock())
            }
            VoiceAction.Resume -> {
                val t = r.track
                need(isMod && t?.pausedAt != null)
                r.track = t!!.copy(startedAt = t.startedAt + clock() - t.pausedAt!!, pausedAt = null)
            }
            is VoiceAction.Skip -> {
                need(isMod)
                if (a.trackId != null && r.track?.id != a.trackId) return // that track already moved on
                skip(r)
                return
            }
            // 036: voice_seek. Paused songs stay paused at the new place.
            is VoiceAction.Seek -> {
                val t = r.track
                need(isMod && t != null && a.ms in 0..86_400_000L)
                val at = t!!.pausedAt ?: clock()
                r.track = t.copy(startedAt = at - a.ms)
            }
            // 036: voice_share_link, the same checks as the server's.
            is VoiceAction.ShareLink -> {
                need(isMod)
                val url = a.url
                if (url != null && (!Links.shareable(url) || (a.title?.length ?: 0) > 200)) throw BackendException("bad_request")
                r.stage = url?.let { RoomStage(it, a.title?.trim()?.ifEmpty { null }, me.profile.name, me.profile.id, clock()) }
            }
        }
        changed(r)
    }

    /** 036: the host acts on anyone but the host; a co-host only on listeners and speakers. */
    private fun moderates(me: Member, target: Member) =
        target.role != VoiceRole.HOST && (me.role == VoiceRole.HOST || (me.role == VoiceRole.COHOST && target.role != VoiceRole.COHOST))

    // ---- hooks for tests and screenshots (the ambient simulation is off there) ----

    /** [count] people in the audience raise a hand. */
    fun raiseHands(roomId: String, count: Int) {
        val r = rooms[roomId] ?: return
        others(r).filter { it.seat == null && !it.hand }.take(count).forEach { it.hand = true }
        changed(r)
    }

    /** People talking right now (user id to level 0..1), as LiveKit would report them. */
    fun speak(roomId: String, levels: Map<String, Float>) {
        val r = rooms[roomId] ?: return
        r.levels = levels
        emit(r, VoiceEvent.Levels(levels))
    }

    fun react(roomId: String, userId: String, kind: VoiceReaction) {
        rooms[roomId]?.let { emit(it, VoiceEvent.Reaction(userId, kind)) }
    }

    /** Someone in the room writes a line ([replyTo]: answering that line). */
    fun say(roomId: String, userId: String, text: String, replyTo: Long? = null) {
        val r = rooms[roomId] ?: return
        val sender = r.members.firstOrNull { it.profile.id == userId }?.profile ?: return
        val line = VoiceLine.Text(lineSeq++, sender, false, text, clock(), quote(r, replyTo))
        r.lines += line
        emit(r, VoiceEvent.Line(line))
    }

    /** The room's host asks a poll (someone else's room). Returns the poll's id. */
    fun hostAsks(roomId: String, question: String, options: List<String>): Long? {
        val r = rooms[roomId] ?: return null
        val host = r.members.firstOrNull { it.profile.id == r.hostId }?.profile ?: return null
        return (ask(r, host, question, options, fromMe = false) as VoiceLine.Poll).pollId
    }

    /** Someone in the room answers a poll. */
    fun answer(pollId: Long, userId: String, choice: Int) {
        val p = polls[pollId]?.takeIf { !it.closed && choice in it.options.indices } ?: return
        p.votes[userId] = choice
        pollChanged(p)
    }

    /** The poll's host closes it. */
    fun closeFromHost(pollId: Long) {
        val p = polls[pollId] ?: return
        p.closed = true
        pollChanged(p)
    }

    /** Someone in the room types in the chat (the room's typing broadcast). */
    fun typing(roomId: String, userId: String, name: String? = null) {
        val r = rooms[roomId] ?: return
        val who = r.members.firstOrNull { it.profile.id == userId }?.profile?.name ?: name ?: return
        emit(r, VoiceEvent.Typing(userId, who))
    }

    /** The room's host gives me (or anyone) a role: co-host powers in someone else's room. */
    fun setRole(roomId: String, userId: String, role: VoiceRole) {
        val r = rooms[roomId] ?: return
        r.members.firstOrNull { it.profile.id == userId }?.role = role
        changed(r)
    }

    /** Someone else (the room's host) shares a page with everyone. */
    fun shareFromHost(roomId: String, url: String?, title: String? = null) {
        val r = rooms[roomId] ?: return
        val host = r.members.firstOrNull { it.profile.id == r.hostId }?.profile ?: return
        r.stage = url?.let { RoomStage(it, title, host.name, host.id, clock()) }
        changed(r)
    }

    /** The room's host ends it (someone else's room). */
    fun hostEnds(roomId: String) {
        rooms[roomId]?.let { end(it) }
    }

    /** The host removes me. */
    fun kickMe(roomId: String) {
        val r = rooms[roomId] ?: return
        val me = runCatching { me().id }.getOrNull() ?: return
        r.members.removeAll { it.profile.id == me }
        emit(r, VoiceEvent.Removed)
    }

    /** The server no longer lets me be in the room ([code]: room_full, banned, ...), as a heartbeat would say. */
    fun refuseMe(roomId: String, code: String) {
        val r = rooms[roomId] ?: return
        val me = runCatching { me().id }.getOrNull() ?: return
        r.members.removeAll { it.profile.id == me }
        emit(r, VoiceEvent.Refused(code))
    }

    /** Someone's connection drops ([back]: they're back 3 s later; else out after 7 s). */
    fun lose(roomId: String, userId: String, back: Boolean) {
        val r = rooms[roomId] ?: return
        r.members.firstOrNull { it.profile.id == userId }?.let { lose(r, it, back) }
    }

    /** My own connection drops ([VoiceEvent.Connection]), as failing heartbeats would show it. */
    fun dropMe(roomId: String) {
        rooms[roomId]?.let { emit(it, VoiceEvent.Connection(lost = true)) }
    }

    /** My connection is back after [awayMs]: a short gap carries on; past [VoiceLost.GONE_MS] the server had taken me out. */
    fun backAfter(roomId: String, awayMs: Long) {
        val r = rooms[roomId] ?: return
        if (!VoiceLost.droppedOut(0, awayMs)) return emit(r, VoiceEvent.Connection(lost = false))
        val me = runCatching { me().id }.getOrNull() ?: return
        r.members.removeAll { it.profile.id == me }
        emit(r, VoiceEvent.Dropped)
    }

    companion object {
        private const val LEVEL_MS = 150L
        /** A lost connection takes them out of the room this long after it shows (the server's 7 s). */
        private const val LOST_OUT_MS = 7_000L
        /** One icebreaker per room every 20 s (the server's rule). */
        const val ICEBREAKER_MS = 20_000L
        private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val ANYONE = RoomRules(maxMembers = 120)
        /** A holiday snap someone shared in the demo: drawn by the app (ui DemoSnap), so it needs no network. */
        const val SNAP = "demo://snap"
        /** Replies after an icebreaker, so the card starts a conversation in the demo too. */
        private val ANSWERS = listOf("ooh good one", "mine is easy: Goa 🌊", "haha let me think", "chai, always chai", "my nani’s kitchen", "this is fun")

        private val NAMES = listOf(
            "Priya", "Rohan", "Sneha", "Vikram", "Meera", "Aditya", "Kavya", "Ishaan", "Nisha", "Rahul", "Pooja", "Siddharth",
            "Farhan", "Zoya", "Dev", "Tanvi", "Aarav", "Diya", "Kunal", "Ritika", "Manish", "Shreya", "Harsh", "Anjali", "Yash",
            "Simran", "Varun", "Neha", "Aman", "Ira", "Karthik", "Lakshmi", "Arnab", "Moumita", "Gurpreet", "Fatima", "Joel",
            "Tenzin", "Sana", "Abhishek",
        )
        private val CITIES = listOf(
            "Mumbai", "Delhi", "Bengaluru", "Pune", "Kolkata", "Jaipur", "Lucknow", "Hyderabad", "Chennai", "Indore", "Patna",
            "Ahmedabad", "Chandigarh", "Kochi", "Guwahati", "Bhopal",
        )
        private val BIOS = listOf(
            "Chai, cricket and old Hindi songs.",
            "Engineer by day, bathroom singer by night.",
            "Here to practise English and make friends.",
            "Night owl. Arijit fan forever.",
            "Preparing for exams, one lo-fi playlist at a time.",
        )

        /** Real videos, so the player works in the demo; the titles stand in for what the server would store. */
        val TRACKS = mapOf(
            "BddP6PYo2gs" to ("Kesariya – Brahmāstra · Arijit Singh" to 268),
            "Umqb9KENgmk" to ("Tum Hi Ho – Aashiqui 2 · Arijit Singh" to 262),
            "284Ov7ysmfA" to ("Channa Mereya – ADHM · Arijit Singh" to 289),
            "VAdGW7QDJiU" to ("Chaleya – Jawan · Arijit Singh, Shilpa Rao" to 200),
            "g0eO74UmRBs" to ("Kal Ho Naa Ho – Title Track · Sonu Nigam" to 322),
            "lTRiuFIWV54" to ("1 A.M Study Session · lofi hip hop" to 0), // an hour long: the demo never moves on
        )

        private val LINES = mapOf(
            RoomCategory.MUSIC to listOf(
                "Arijit ka koi bhi gaana, bas 🔥", "next Kal Ho Naa Ho please 🙏", "volume ekdum perfect hai", "who added this? legend",
                "goosebumps every single time", "late night + Arijit = therapy", "Priya di please play Tum Hi Ho", "this chorus 😭❤️",
            ),
            RoomCategory.TALK to listOf(
                "Bumrah ka over lethal tha", "Kohli 100 today, mark my words", "DRS was clearly out!", "what a catch by Jadeja 🔥",
                "How do you say “I’m bored” in Hindi?", "is it “in the bus” or “on the bus”?", "chai or coffee, final answer?",
                "Sunday plans anyone?", "mera vote Goa ke liye", "best street food in {city}?", "haha same here",
            ),
        )
        private val ANY = listOf("hello everyone 👋", "nice room yaar", "hi from {city}!", "first time in a voice room 😄", "mic quality is great")
    }
}
