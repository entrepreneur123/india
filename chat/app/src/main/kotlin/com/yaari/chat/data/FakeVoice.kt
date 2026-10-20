package com.yaari.chat.data

import com.yaari.chat.model.Gender
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.Profile
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.SpeakMode
import com.yaari.chat.model.VoiceAction
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Demo voice rooms (a port of the web's lib/mockVoice.ts): a few live rooms with people talking (audio levels), chat,
 * reactions, raised hands and a shared YouTube track. It applies the server's rules (017_voice_rooms.sql). The
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
    private class Member(val profile: Profile, var role: VoiceRole, var seat: Int?, var muted: Boolean, var hand: Boolean)

    private class SimRoom(
        val id: String, val title: String, val category: RoomCategory, val mode: SpeakMode, var hostId: String,
        val members: MutableList<Member>, val locked: BooleanArray, var mine: Boolean,
    ) {
        var track: VoiceTrack? = null
        val queue = mutableListOf<VoiceQueueItem>()
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
    private var lineSeq = 1L
    private var personSeq = 0
    private var uidSeq = 0
    private val sendTimes = ArrayDeque<Long>()

    init {
        seed("v-bolly", "Late night Bollywood songs", RoomCategory.MUSIC, SpeakMode.APPROVAL, "Priya", 5, 34,
            Music("BddP6PYo2gs", 74_000, listOf("Umqb9KENgmk", "284Ov7ysmfA", "VAdGW7QDJiU")))
        seed("v-cricket", "Cricket talk: IND vs AUS", RoomCategory.SPORTS, SpeakMode.APPROVAL, "Vikram", 7, 58)
        seed("v-english", "Hindi–English practice", RoomCategory.LANGUAGE, SpeakMode.OPEN, "Meera", 4, 21)
        seed("v-lofi", "Lo-fi study together", RoomCategory.STUDY, SpeakMode.OPEN, "Kavya", 3, 17, Music("lTRiuFIWV54", 480_000, emptyList()))
    }

    private class Music(val playing: String, val ago: Long, val queue: List<String>)

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

    private fun seed(
        id: String, title: String, category: RoomCategory, mode: SpeakMode, hostName: String, speakers: Int, listeners: Int,
        music: Music? = null,
    ) {
        val people = listOf(person(hostName)) + List(speakers + listeners - 1) { person() }
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
            BooleanArray(VoiceRoom.SEATS) { it == 8 && mode == SpeakMode.APPROVAL }, mine = false)
        if (music != null) {
            r.track = track(music.playing, people[0].name, clock() - music.ago)
            music.queue.forEachIndexed { i, v -> r.queue += VoiceQueueItem(uid(), v, TRACKS[v]?.first.orEmpty(), people[i + 1].name, pending = false) }
        }
        val now = clock()
        for (i in 0 until 7) {
            val sender = pick(people)
            val at = now - (7 - i) * 45_000L
            if (i == 2) r.lines += VoiceLine.System(lineSeq++, VoiceSystemEvent.JOINED, pick(people).name, at)
            r.lines += VoiceLine.Text(lineSeq++, sender, false, chatLine(category, sender), at)
        }
        rooms[id] = r
    }

    private fun track(videoId: String, addedBy: String, startedAt: Long = clock()) =
        VoiceTrack(uid(), videoId, TRACKS[videoId]?.first.orEmpty(), addedBy, startedAt, null)

    private fun chatLine(category: RoomCategory, sender: Profile): String {
        val list = if (random.nextFloat() < 0.8f) LINES[category] ?: ANY else ANY
        return pick(list).replace("{city}", sender.city)
    }

    private fun get(roomId: String): SimRoom {
        val r = rooms[roomId]
        if (r == null || r.ended) throw BackendException("room_closed")
        return r
    }

    private fun meIn(r: SimRoom): Member? = runCatching { me() }.getOrNull()?.let { m -> r.members.firstOrNull { it.profile.id == m.id } }

    private fun snapshot(r: SimRoom) = VoiceRoom(
        r.id, r.title, r.category, r.mode, r.hostId, runCatching { me().id }.getOrDefault(""),
        r.members.map { VoiceMember(it.profile, it.role, it.seat, it.muted, it.hand) },
        List(VoiceRoom.SEATS) { VoiceSeat(it, r.locked[it]) }, r.track, r.queue.toList(),
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

    fun hostOf(roomId: String) = rooms[roomId]?.hostId

    suspend fun list(): List<VoiceRoomSummary> {
        wait(450)
        val me = runCatching { me() }.getOrNull()
        return rooms.values.filter { !it.ended }.sortedByDescending { it.mine }.map { r ->
            val host = r.members.first { it.profile.id == r.hostId }.profile
            VoiceRoomSummary(
                r.id, r.title, r.category, host,
                speakers = r.members.filter { it.seat != null }.sortedBy { it.seat }.take(4).map { it.profile },
                listeners = r.members.count { it.seat == null && it.profile.id != me?.id },
                nowPlaying = r.track?.title?.ifEmpty { null }, mine = r.mine,
            )
        }
    }

    suspend fun create(n: NewVoiceRoom): String {
        wait(500)
        val me = me()
        val title = n.title.trim()
        if (title.length !in VoiceRoom.MIN_TITLE..VoiceRoom.MAX_TITLE) throw BackendException("room_title")
        rooms.values.filter { it.mine && !it.ended }.forEach { end(it) } // one live room per host
        val id = "v-" + uid()
        // A couple of people are already waiting to listen, so the host isn't alone.
        val early = List(3) { person() }
        val members = mutableListOf(Member(me, VoiceRole.HOST, 0, muted = false, hand = false))
        early.forEach { members += Member(it, VoiceRole.LISTENER, null, muted = true, hand = false) }
        rooms[id] = SimRoom(id, title, n.category, n.mode, me.id, members, BooleanArray(VoiceRoom.SEATS), mine = true)
        return id
    }

    /** Enters the room and follows it; the simulation runs while collected. */
    fun session(roomId: String): Flow<VoiceEvent> = channelFlow {
        wait(350)
        val r = get(roomId)
        val me = me()
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

    suspend fun send(roomId: String, text: String): VoiceLine {
        val r = get(roomId)
        val me = me()
        val now = clock()
        while (sendTimes.isNotEmpty() && now - sendTimes.first() >= 5_000) sendTimes.removeFirst()
        if (sendTimes.size >= 5) throw BackendException("slow_down")
        sendTimes += now
        wait(120)
        val line = VoiceLine.Text(lineSeq++, me, true, text.trim(), now)
        r.lines += line
        emit(r, VoiceEvent.Line(line))
        return line
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
            val stage = others(r).filter { it.seat != null && !it.muted }.shuffled(random)
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
            val sender = pick(others(r)).profile
            val line = VoiceLine.Text(lineSeq++, sender, false, chatLine(r.category, sender), clock())
            r.lines += line
            emit(r, VoiceEvent.Line(line))
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

    private fun skip(r: SimRoom) {
        val i = r.queue.indexOfFirst { !it.pending }
        if (i < 0) {
            r.track = null
        } else {
            val next = r.queue.removeAt(i)
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
                need(me.seat != null && !isHost)
                me.seat = null
                if (me.role == VoiceRole.SPEAKER) me.role = VoiceRole.LISTENER
                system(r, VoiceSystemEvent.AUDIENCE, me.profile.name)
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
                need(isMod && target != null && target.seat != null && target.role != VoiceRole.HOST)
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
                need(isHost && target != null && target.role != VoiceRole.HOST)
                r.members -= target!!
                system(r, VoiceSystemEvent.REMOVED, target.profile.name)
            }
            is VoiceAction.AddTrack -> {
                need(isHost)
                val title = TRACKS[a.videoId]?.first.orEmpty()
                if (r.track == null) {
                    r.track = track(a.videoId, me.profile.name)
                    system(r, VoiceSystemEvent.TRACK, me.profile.name, title = title)
                } else {
                    if (r.queue.size >= 30) throw BackendException("queue_full")
                    r.queue += VoiceQueueItem(uid(), a.videoId, title, me.profile.name, pending = false)
                }
            }
            is VoiceAction.Suggest -> {
                if (r.queue.size >= 30) throw BackendException("queue_full")
                val item = VoiceQueueItem(uid(), a.videoId, TRACKS[a.videoId]?.first.orEmpty(), me.profile.name, pending = true)
                r.queue += item
                // The demo host likes every suggestion.
                later(r, 3_000) {
                    val i = r.queue.indexOfFirst { it.id == item.id }
                    if (i >= 0) {
                        r.queue[i] = r.queue[i].copy(pending = false)
                        changed(r)
                    }
                }
            }
            is VoiceAction.Approve -> {
                need(isHost)
                val i = r.queue.indexOfFirst { it.id == a.itemId }
                need(i >= 0)
                if (a.accept) r.queue[i] = r.queue[i].copy(pending = false) else r.queue.removeAt(i)
            }
            is VoiceAction.Play -> {
                need(isHost)
                val i = r.queue.indexOfFirst { it.id == a.itemId }
                need(i >= 0)
                val item = r.queue.removeAt(i)
                r.track = track(item.videoId, item.addedBy).copy(title = item.title)
                system(r, VoiceSystemEvent.TRACK, me.profile.name, title = item.title)
            }
            is VoiceAction.RemoveItem -> {
                need(isHost)
                r.queue.removeAll { it.id == a.itemId }
            }
            VoiceAction.Pause -> {
                val t = r.track
                need(isHost && t != null && t.pausedAt == null)
                r.track = t!!.copy(pausedAt = clock())
            }
            VoiceAction.Resume -> {
                val t = r.track
                need(isHost && t?.pausedAt != null)
                r.track = t!!.copy(startedAt = t.startedAt + clock() - t.pausedAt!!, pausedAt = null)
            }
            is VoiceAction.Skip -> {
                need(isHost)
                if (a.trackId != null && r.track?.id != a.trackId) return // that track already moved on
                skip(r)
                return
            }
        }
        changed(r)
    }

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

    /** Someone in the room writes a line. */
    fun say(roomId: String, userId: String, text: String) {
        val r = rooms[roomId] ?: return
        val sender = r.members.firstOrNull { it.profile.id == userId }?.profile ?: return
        val line = VoiceLine.Text(lineSeq++, sender, false, text, clock())
        r.lines += line
        emit(r, VoiceEvent.Line(line))
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

    companion object {
        private const val LEVEL_MS = 150L

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
            RoomCategory.LANGUAGE to listOf(
                "How do you say “I’m bored” in Hindi?", "main thoda thoda English bolta hoon", "is it “in the bus” or “on the bus”?",
                "practice makes perfect 💪", "can someone correct my sentence?", "मुझे English बोलने में डर लगता है", "Meera ma’am explains so well",
            ),
            RoomCategory.SPORTS to listOf(
                "Bumrah ka over lethal tha", "Kohli 100 today, mark my words", "Head ka wicket chahiye jaldi", "DRS was clearly out!",
                "IND will chase this easily", "what a catch by Jadeja 🔥", "Gill looking in form today",
            ),
            RoomCategory.STUDY to listOf(
                "45 min focus, 10 min break?", "UPSC prep day 112 ✍️", "anyone doing NEET this year?", "this beat is so calm",
                "back after chai ☕", "Pomodoro #3 done", "library vibes at home",
            ),
        )
        private val ANY = listOf("hello everyone 👋", "nice room yaar", "hi from {city}!", "first time in a voice room 😄", "mic quality is great")
    }
}
