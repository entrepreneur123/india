package com.yaari.chat.ui

import com.yaari.chat.R
import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.IN_RANDOM_CHAT
import com.yaari.chat.data.PhotoCheck
import com.yaari.chat.model.GenderRule
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.RoomQuery
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceAudioState
import com.yaari.chat.model.VoiceEvent
import com.yaari.chat.model.VoiceLine
import com.yaari.chat.model.VoicePoll
import com.yaari.chat.model.VoiceReply
import com.yaari.chat.model.VoiceReaction
import com.yaari.chat.model.VoiceRole
import com.yaari.chat.model.VoiceRoom
import com.yaari.chat.model.VoiceRoomSummary
import com.yaari.chat.model.VoiceSystemEvent
import com.yaari.chat.model.VoiceTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

/** A private room I just opened: its code is shown once, to copy or share. */
data class CreatedNotice(val title: String, val code: String)

/**
 * The rooms list on the Rooms tab. [rooms] is null until the first load; [failed]: that load failed. [filter]: one
 * category, or all (null). [busy]: creating a room or entering a code is in flight.
 */
data class VoiceListState(
    val rooms: List<VoiceRoomSummary>? = null, val failed: Boolean = false, val busy: Boolean = false,
    val filter: RoomCategory? = null, val created: CreatedNotice? = null,
    /** Search words and language (the server narrows the list). */
    val query: RoomQuery = RoomQuery(),
) {
    val shown get() = rooms?.filter { filter == null || it.category == filter }
}

/** A reaction floating up over the stage; [x] is where it starts across the stage (0..1). */
data class FloatingReaction(val key: Long, val userId: String, val kind: VoiceReaction, val x: Float)

/**
 * The voice room I'm in. [room] is null while joining; [lines] null until the chat has loaded. [expanded]: full screen,
 * else the small pill over the tabs (still in the room, still listening, as in X Spaces).
 */
data class VoiceState(
    val roomId: String,
    val room: VoiceRoom? = null,
    val lines: List<VoiceLine>? = null,
    val reactions: List<FloatingReaction> = emptyList(),
    val audio: VoiceAudioState? = null,
    /** My connection dropped (heartbeats failing or realtime down): the room shows "Reconnecting…". */
    val offline: Boolean = false,
    val expanded: Boolean = true,
    /** The host asked to leave: the confirm hands the room to a co-host ([heir]) or ends it for everyone. */
    val confirmEnd: Boolean = false,
    val ending: Boolean = false,
    /** The polls of the chat's poll lines by id, as last counted ([VoicePoll.mine]: my answer, kept here). */
    val polls: Map<Long, VoicePoll> = emptyMap(),
    /** The line my next message answers (the composer's quote bar). */
    val replyTo: VoiceReply? = null,
    /** A poll is being asked. */
    val pollBusy: Boolean = false,
    /**
     * I listen to the room's music outside a music room (tapped "Listen"); it keeps playing while the panel is
     * collapsed and while the room is minimised, until I leave. A music room always plays.
     */
    val listen: Boolean = false,
    /** The music panel is open (the player shows); collapsed it is the now-playing strip. */
    val musicOpen: Boolean = false,
    /** A photo is being checked and sent. */
    val photoBusy: Boolean = false,
    /** A song request whose title is being looked up (its video id). */
    val adding: String? = null,
    /** People typing in the chat right now (user id, in the order they started); each goes 4 s after their last signal. */
    val typing: List<String> = emptyList(),
    /** The shared page I closed for myself (its [com.yaari.chat.model.RoomStage.at]); a new share shows again. */
    val hiddenStage: Long? = null,
) {
    val me get() = room?.me
    val isHost get() = me?.role == VoiceRole.HOST
    val isMod get() = me?.isMod == true
    /** Who takes over if I, the host, leave ([VoiceRoom.nextHost]); null: leaving ends the room. */
    val heir get() = room?.takeIf { isHost }?.nextHost
    /** Music for everyone: the host or a co-host (036). */
    val canDj get() = room?.canDj == true

    /** Who is typing, as the room lists them (never me; someone who left is not shown). */
    val typers get() = room?.let { r -> typing.filter { it != r.meId }.mapNotNull { r.member(it)?.profile?.name } }.orEmpty()

    /** The shared page, unless I closed it. */
    val shownStage get() = room?.stage?.takeIf { it.at != hiddenStage }
    /** Raised hands a host or co-host should see. */
    val hands get() = if (isMod) room?.audience?.count { it.hand } ?: 0 else 0

    /** The room's music plays for me: the one player is alive (shown in the room, or kept playing out of sight). */
    val playing get() = room?.track != null && (listen || room.category == RoomCategory.MUSIC)

    /**
     * The foreground service (and its notification) runs only while there is something to keep alive: the audio
     * connection (someone is on the stage) or the room's music playing.
     */
    val keepAlive get() = audio != null || room?.track?.let { it.pausedAt == null } == true
}

/**
 * Voice rooms: the list, the room I'm in, and everything I can do there. Owned by [AppViewModel]; plain coroutines on
 * its scope, so the tests drive it with virtual time. Audio levels live in their own flow ([levels]): they change ten
 * times a second and only the speaking rings read them.
 */
class VoiceController(
    private val backend: Backend,
    private val scope: CoroutineScope,
    private val notice: (UiText) -> Unit,
    private val explain: (Throwable) -> UiText,
    /** Room photos are shrunk and checked for nudity on this phone before they are sent. */
    private val photos: PhotoCheck = PhotoCheck.None,
    private val random: Random = Random.Default,
    /** Wall clock (how often typing is sent). */
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val _list = MutableStateFlow(VoiceListState())
    val list: StateFlow<VoiceListState> = _list.asStateFlow()

    private val _state = MutableStateFlow<VoiceState?>(null)
    val state: StateFlow<VoiceState?> = _state.asStateFlow()

    private val _levels = MutableStateFlow<Map<String, Float>>(emptyMap())
    val levels: StateFlow<Map<String, Float>> = _levels.asStateFlow()

    private var session: Job? = null
    private var listJob: Job? = null
    /** Each typer's expiry (cancelled by a newer signal or their line). Only touched on the main thread. */
    private val typingOff = mutableMapOf<String, Job>()
    private var lastTypingSent = Long.MIN_VALUE / 2
    private var reactionSeq = 0L

    /**
     * Asked before entering another room (a card, a code, an invite, a link, a new room): one live session at a time,
     * so the app may first end a random chat (with a confirm) or stop searching. [AppViewModel] sets it.
     */
    var gate: (proceed: () -> Unit) -> Unit = { it() }

    /** Runs [proceed] once the way into another room is clear ([gate]); straight away for the room I'm in. */
    fun guard(roomId: String? = null, proceed: () -> Unit) {
        if (roomId != null && _state.value?.roomId == roomId) return proceed()
        gate(proceed)
    }

    // ---- the list ----

    /** All rooms or one category (kept while the app runs, like the web). */
    fun filter(category: RoomCategory?) = _list.update { it.copy(filter = category) }

    /** Searches by title words and the room's language; the list reloads at once. */
    fun search(query: RoomQuery) {
        val q = query.copy(text = query.text.take(MAX_SEARCH))
        if (q == _list.value.query) return
        _list.update { it.copy(query = q) }
        refreshList()
    }

    /** The newest request wins. */
    fun refreshList() {
        listJob?.cancel()
        listJob = scope.launch {
            try {
                val rooms = backend.voiceRooms(_list.value.query)
                _list.update { it.copy(rooms = rooms, failed = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _list.update { it.copy(failed = true) }
            }
        }
    }

    /** Validates what the server validates, then opens the new room with me as host (off the stage). */
    fun create(spec: NewVoiceRoom, done: () -> Unit = {}) {
        val title = spec.title.trim()
        val r = spec.rules
        val problem = when {
            title.length !in VoiceRoom.MIN_TITLE..VoiceRoom.MAX_TITLE -> R.string.err_room_title
            r.maxMembers !in VoiceRoom.MIN_MEMBERS..VoiceRoom.MAX_MEMBERS -> R.string.err_room_size
            r.minAge !in VoiceRoom.MIN_AGE..VoiceRoom.MAX_AGE || r.maxAge !in VoiceRoom.MIN_AGE..VoiceRoom.MAX_AGE || r.minAge > r.maxAge ||
                spec.announcement.trim().length > VoiceRoom.MAX_ANNOUNCEMENT -> R.string.err_room_settings
            else -> null
        }
        if (problem != null) return notice(UiText(problem))
        guard { create2(spec, title, r.isPrivate, done) }
    }

    private fun create2(spec: NewVoiceRoom, title: String, private: Boolean, done: () -> Unit) {
        task {
            val made = backend.createVoiceRoom(spec.copy(title = title, announcement = spec.announcement.trim()))
            done()
            if (private) _list.update { it.copy(created = CreatedNotice(title, made.code)) }
            openNow(made.id)
            refreshList()
        }
    }

    fun dismissCreated() = _list.update { it.copy(created = null) }

    /** Enters a room with the code its host shared (the only way into a private room). */
    fun joinCode(input: String, done: () -> Unit = {}) {
        val code = input.trim().uppercase()
        if (code.length != VoiceRoom.CODE_LENGTH || !code.all { it in 'A'..'Z' || it in '0'..'9' }) return notice(UiText(R.string.err_room_code))
        guard { task {
            val id = try {
                backend.joinVoiceCode(code)
            } catch (e: BackendException) {
                if (e.code == "not_found") return@task notice(UiText(R.string.err_room_code_unknown))
                throw e
            }
            done()
            openNow(id)
            refreshList()
        } }
    }

    /** A room on the list: one I can't enter says why instead of trying (unless I'm already in it). */
    fun enter(room: VoiceRoomSummary) {
        val refusal = room.refusal
        if (refusal != null && _state.value?.roomId != room.id) return notice(refusalText(room))
        open(room.id)
    }

    /** One list action at a time (a double tap opens one room, not two). */
    private fun task(block: suspend () -> Unit) {
        if (_list.value.busy) return
        _list.update { it.copy(busy = true) }
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
            } finally {
                _list.update { it.copy(busy = false) }
            }
        }
    }

    // ---- being in a room ----

    /** Enters [roomId] full screen (or brings it back up when I'm already in it), once [gate] lets me. */
    fun open(roomId: String) = guard(roomId) { openNow(roomId) }

    /** Enters [roomId] full screen (or brings it back up when I'm already in it). One room at a time. */
    fun openNow(roomId: String) {
        val current = _state.value
        if (current?.roomId == roomId) return expand()
        if (current != null) close()
        _state.value = VoiceState(roomId)
        session = scope.launch {
            var loadedLines = false
            try {
                backend.voiceSession(roomId).collect { e ->
                    when (e) {
                        is VoiceEvent.RoomChanged -> {
                            onRoom(e.room)
                            if (!loadedLines) {
                                loadedLines = true
                                launch { loadLines(roomId) }
                            }
                        }
                        is VoiceEvent.Levels -> _levels.value = e.levels
                        is VoiceEvent.Audio -> update(roomId) { it.copy(audio = e.audio) }
                        is VoiceEvent.Typing -> onTyping(roomId, e.userId)
                        is VoiceEvent.Line -> {
                            // Their line arrived: they stopped typing.
                            (e.line as? VoiceLine.Text)?.let { if (!it.fromMe) stopTyping(roomId, it.sender.id) }
                            update(roomId) { it.copy(lines = merge(it.lines, listOf(e.line))) }
                            (e.line as? VoiceLine.Poll)?.let { launch { pollsFor(roomId, listOf(it)) } }
                        }
                        is VoiceEvent.PollChanged -> setPoll(roomId, e.poll, mine = false)
                        is VoiceEvent.Reaction -> launch { float(roomId, e.userId, e.kind) }
                        VoiceEvent.Ended -> {
                            if (_state.value?.ending != true) notice(UiText(R.string.voice_ended))
                            gone(roomId, leave = false)
                        }
                        VoiceEvent.Removed -> {
                            notice(UiText(R.string.voice_removed))
                            gone(roomId, leave = false)
                        }
                        is VoiceEvent.Connection -> update(roomId) { it.copy(offline = e.lost) }
                        VoiceEvent.Dropped -> {
                            notice(UiText(R.string.voice_lost_left))
                            gone(roomId, leave = false)
                        }
                        // The server no longer lets me in (it filled while I was away, I was banned, ...): out, with why.
                        is VoiceEvent.Refused -> {
                            if (e.code != IN_RANDOM_CHAT) notice(refusedText(e.code, _state.value?.room?.rules))
                            gone(roomId, leave = false)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Couldn't get in (the room ended, I was removed, offline): back to where I was. A random chat that took
                // me out of it (the server's one live session) says nothing.
                if ((e as? BackendException)?.code != IN_RANDOM_CHAT) notice(explain(e))
                gone(roomId, leave = false)
            }
        }
    }

    private suspend fun loadLines(roomId: String) {
        try {
            val history = backend.voiceLines(roomId)
            update(roomId) { it.copy(lines = merge(it.lines, history)) }
            pollsFor(roomId, history.filterIsInstance<VoiceLine.Poll>())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            update(roomId) { it.copy(lines = it.lines ?: emptyList()) }
        }
    }

    // ---- typing ----

    /** Someone else in the room is typing: shown until 4 s after their last signal or their line. */
    private fun CoroutineScope.onTyping(roomId: String, userId: String) {
        val room = _state.value?.takeIf { it.roomId == roomId }?.room ?: return
        if (userId == room.meId || room.member(userId) == null) return
        typingOff.remove(userId)?.cancel()
        update(roomId) { s -> if (userId in s.typing) s else s.copy(typing = s.typing + userId) }
        typingOff[userId] = launch {
            delay(TYPING_SHOWN_MS)
            typingOff.remove(userId)
            update(roomId) { s -> s.copy(typing = s.typing - userId) }
        }
    }

    private fun stopTyping(roomId: String, userId: String) {
        typingOff.remove(userId)?.cancel()
        update(roomId) { s -> if (userId in s.typing) s.copy(typing = s.typing - userId) else s }
    }

    /** Called on every keystroke in the room's composer; tells the room at most every 2.5 s. */
    fun typing() {
        val s = _state.value ?: return
        val name = s.me?.profile?.name ?: return
        val now = wallClock()
        if (now - lastTypingSent in 0 until TYPING_EVERY_MS) return
        lastTypingSent = now
        scope.launch { runCatching { backend.sendVoiceTyping(s.roomId, name) } }
    }

    // ---- the shared page (036) ----

    /**
     * Host or co-host: pins [url] next to everyone's chat ([title]: the page's, when known). Only https pages the server
     * accepts; anything else is said at once. [done] when it is shared.
     */
    fun shareLink(url: String, title: String?, done: () -> Unit = {}) {
        val s = _state.value ?: return
        if (s.room?.canShare != true) return notice(UiText(R.string.err_not_allowed))
        if (!com.yaari.chat.model.Links.shareable(url)) return notice(UiText(R.string.link_not_shareable))
        act(VoiceAction.ShareLink(url, title?.trim()?.take(200)?.ifEmpty { null }), UiText(R.string.link_shared)) { ok -> if (ok) done() }
    }

    /** Host or co-host: nobody sees the shared page any more. */
    fun stopSharing() = act(VoiceAction.ShareLink(null))

    /** Closes the shared page on my phone only (until something new is shared). */
    fun hideStage() = _state.update { s -> s?.copy(hiddenStage = s.room?.stage?.at) }

    // ---- polls ----

    /**
     * The counts of poll lines not counted yet: shown at once as asked (no answers yet, open), then as the server
     * counts them (voice_polls_get, 50 at a time). A failed load keeps the placeholder; live changes still arrive.
     */
    private suspend fun pollsFor(roomId: String, lines: List<VoiceLine.Poll>) {
        val known = _state.value?.takeIf { it.roomId == roomId }?.polls ?: return
        val fresh = lines.filter { it.pollId !in known }
        if (fresh.isEmpty()) return
        update(roomId) { s ->
            s.copy(polls = s.polls + fresh.filter { it.pollId !in s.polls }.associate {
                it.pollId to VoicePoll(it.pollId, it.question, it.options, List(it.options.size) { 0 }, 0, closed = false, by = it.sender.id)
            })
        }
        for (ids in fresh.map { it.pollId }.distinct().chunked(MAX_POLLS)) {
            try {
                backend.voicePolls(ids).forEach { setPoll(roomId, it, mine = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Placeholders stay; a vote or a live change brings the counts.
            }
        }
    }

    /** A poll as the server counts it. [mine]: it carries my answer (a broadcast doesn't: the one I know stays). */
    internal fun setPoll(roomId: String, poll: VoicePoll, mine: Boolean) = update(roomId) { s ->
        val old = s.polls[poll.id]
        s.copy(polls = s.polls + (poll.id to if (mine) poll else poll.copy(mine = old?.mine)))
    }

    /** Host or co-host: asks the room (the server's rules are checked here first). [done] when it was asked. */
    fun createPoll(question: String, options: List<String>, done: () -> Unit = {}) {
        val s = _state.value ?: return
        if (s.pollBusy) return
        if (!s.isMod) return notice(UiText(R.string.poll_e_not_allowed))
        val o = options.map { it.trim() }.filter { it.isNotEmpty() }
        if (!VoicePoll.valid(question, o)) return notice(UiText(R.string.poll_e_bad_request))
        update(s.roomId) { it.copy(pollBusy = true) }
        scope.launch {
            try {
                val line = backend.createVoicePoll(s.roomId, question.trim(), o)
                update(s.roomId) { it.copy(lines = merge(it.lines, listOf(line))) }
                (line as? VoiceLine.Poll)?.let { pollsFor(s.roomId, listOf(it)) }
                done()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(pollError(e) ?: explain(e))
            } finally {
                update(s.roomId) { it.copy(pollBusy = false) }
            }
        }
    }

    /** My answer (0-based); tapping another one while the poll is open changes it. */
    fun votePoll(pollId: Long, choice: Int) {
        val s = _state.value ?: return
        val p = s.polls[pollId] ?: return
        if (p.closed || p.mine == choice || choice !in p.options.indices) return
        scope.launch {
            try {
                setPoll(s.roomId, backend.voicePollVote(pollId, choice), mine = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val code = (e as? BackendException)?.code
                // Closed meanwhile: show it closed (the broadcast may still be on its way).
                if (code == "poll_closed") update(s.roomId) { v -> v.copy(polls = v.polls + (pollId to (v.polls[pollId] ?: p).copy(closed = true))) }
                notice(pollError(e) ?: explain(e))
            }
        }
    }

    /** The host, a co-host or who asked: no more answers. */
    fun closePoll(pollId: Long) {
        val s = _state.value ?: return
        scope.launch {
            try {
                setPoll(s.roomId, backend.closeVoicePoll(pollId), mine = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(pollError(e) ?: explain(e))
            }
        }
    }

    /** I may close [poll]: I asked it, or I'm the host or a co-host. */
    fun canClose(poll: VoicePoll) = _state.value?.let { s -> !poll.closed && (s.isMod || poll.by == s.room?.meId) } == true

    // ---- replies ----

    /** My next message answers [line] (the quote bar above the composer); lines that can't be answered are ignored. */
    fun reply(line: VoiceLine) {
        val quote = VoiceReply.of(line) ?: return
        _state.update { it?.copy(replyTo = quote) }
    }

    fun cancelReply() = _state.update { it?.copy(replyTo = null) }

    // ---- music ----

    /** Listen to the room's music (outside a music room), or stop. */
    fun listen(on: Boolean) = _state.update { it?.copy(listen = on, musicOpen = on) }

    /** The music panel open (the player) or collapsed to the now-playing strip; the music plays on either way. */
    fun musicPanel(open: Boolean) = _state.update { it?.copy(musicOpen = open) }

    private fun onRoom(room: VoiceRoom) {
        val before = _state.value?.takeIf { it.roomId == room.id }?.me
        update(room.id) { it.copy(room = room) }
        val now = room.me ?: return
        // Coming up on the stage (invited, or a raised hand accepted) is worth a word: the mic starts muted.
        if (before != null && before.seat == null && now.seat != null && now.role != VoiceRole.HOST) notice(UiText(R.string.voice_on_stage))
    }

    private suspend fun float(roomId: String, userId: String, kind: VoiceReaction) {
        val r = FloatingReaction(++reactionSeq, userId, kind, 0.12f + random.nextFloat() * 0.76f)
        update(roomId) { it.copy(reactions = (it.reactions + r).takeLast(MAX_REACTIONS)) }
        delay(REACTION_MS)
        update(roomId) { s -> s.copy(reactions = s.reactions.filterNot { it.key == r.key }) }
    }

    private fun update(roomId: String, change: (VoiceState) -> VoiceState) = _state.update { s ->
        if (s == null || s.roomId != roomId) s else change(s)
    }

    /** Out of the room: stop following it, and tell the server unless it already let me go. */
    private fun gone(roomId: String, leave: Boolean) {
        if (_state.value?.roomId != roomId) return
        val job = session
        session = null
        typingOff.values.forEach { it.cancel() }
        typingOff.clear()
        _state.value = null
        _levels.value = emptyMap()
        if (leave) scope.launch { runCatching { backend.leaveVoice(roomId) } }
        refreshList()
        job?.cancel()
    }

    /** A word from the room screen (e.g. the microphone permission was refused). */
    fun say(text: UiText) = notice(text)

    fun minimise() = _state.update { it?.copy(expanded = false) }
    fun expand() = _state.update { it?.copy(expanded = true) }

    /** "Leave": the host confirms first (hand the room to a co-host, or end it for everyone); anyone else just leaves. */
    fun leave() {
        val s = _state.value ?: return
        if (s.isHost) _state.update { it?.copy(confirmEnd = true, expanded = true) } else close()
    }

    fun dismissEnd() = _state.update { it?.copy(confirmEnd = false) }

    /** Leaves at once (leave_voice). For the host the server hands the room to a co-host ([VoiceState.heir]), else ends it. */
    fun close() {
        val s = _state.value ?: return
        gone(s.roomId, leave = true)
    }

    /** Host only, after the confirm: everyone leaves and the room closes. [quiet]: the caller says something else. */
    fun end(quiet: Boolean = false) {
        val s = _state.value ?: return
        if (!s.isHost || s.ending) return
        update(s.roomId) { it.copy(ending = true) }
        scope.launch {
            try {
                backend.endVoice(s.roomId)
                if (!quiet) notice(UiText(R.string.voice_ended_by_you))
                gone(s.roomId, leave = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update(s.roomId) { it.copy(ending = false, confirmEnd = false) }
                notice(explain(e))
            }
        }
    }

    /** Does [action] in the room I'm in; [success] is said when it worked. [done] gets whether it worked. */
    fun act(action: VoiceAction, success: UiText? = null, done: (Boolean) -> Unit = {}) {
        val s = _state.value ?: return done(false)
        scope.launch {
            val ok = try {
                backend.voiceAction(s.roomId, action)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
                false
            }
            if (ok) success?.let(notice)
            done(ok)
        }
    }

    /** Raises or lowers my hand; raising says that the host can see it. */
    fun hand(up: Boolean) = act(VoiceAction.Hand(up), if (up) UiText(R.string.voice_hand_sent) else null)

    /**
     * Tapping an empty seat: a host or co-host gets the seat's tools ([manage]); a locked seat says so; someone on
     * the stage or in an open room moves there; anyone else raises a hand.
     */
    fun tapSeat(seat: Int, manage: (Int) -> Unit) {
        val room = _state.value?.room ?: return
        val me = room.me ?: return
        when {
            me.isMod -> manage(seat)
            room.locked(seat) -> notice(UiText(R.string.voice_seat_locked, seat))
            me.seat != null || room.mode == com.yaari.chat.model.SpeakMode.OPEN -> act(VoiceAction.TakeSeat(seat))
            me.hand -> notice(UiText(R.string.voice_hand_sent))
            else -> hand(true)
        }
    }

    /**
     * "Join voice": the host takes seat 0; co-hosts and anyone in an open room the first free seat; everyone else
     * raises (or lowers) a hand.
     */
    fun joinVoice() {
        val room = _state.value?.room ?: return
        val me = room.me ?: return
        if (me.seat != null) return
        val seat = when {
            me.role == VoiceRole.HOST -> 0
            joinsDirectly(room) -> room.firstFreeSeat() ?: return notice(UiText(R.string.voice_stage_full))
            else -> null
        }
        if (seat != null) act(VoiceAction.TakeSeat(seat)) else hand(!me.hand)
    }

    /** A chat line; it answers the line in the quote bar, if any (voice_send2). A failed send puts the quote back. */
    fun send(text: String, onFailed: () -> Unit = {}) {
        val s = _state.value ?: return
        val t = text.trim().take(VoiceRoom.MAX_TEXT)
        if (t.isEmpty()) return
        val quote = s.replyTo
        if (quote != null) update(s.roomId) { it.copy(replyTo = null) }
        scope.launch {
            try {
                val line = backend.sendVoice(s.roomId, t, quote?.id)
                update(s.roomId) { it.copy(lines = merge(it.lines, listOf(line))) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
                if (quote != null) update(s.roomId) { if (it.replyTo == null) it.copy(replyTo = quote) else it }
                onFailed()
            }
        }
    }

    fun reportRoom(reason: ReportReason, done: () -> Unit = {}) {
        val s = _state.value ?: return
        scope.launch {
            try {
                backend.reportVoiceRoom(s.roomId, reason)
                notice(UiText(R.string.voice_reported_room))
                done()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
            }
        }
    }

    /** Someone was reported or blocked: their lines and photos go at once. */
    fun forget(userId: String) = _state.update { s ->
        s?.copy(lines = s.lines?.filterNot { (it is VoiceLine.Text && it.sender.id == userId) || (it is VoiceLine.Photo && it.sender.id == userId) })
    }

    /** Host: the title, the pinned announcement (empty removes it) and the language (null keeps it). */
    fun saveSettings(title: String, announcement: String, lang: String? = null, done: () -> Unit = {}) {
        val s = _state.value ?: return
        val t = title.trim()
        if (t.length !in VoiceRoom.MIN_TITLE..VoiceRoom.MAX_TITLE) return notice(UiText(R.string.err_room_title))
        scope.launch {
            try {
                backend.updateVoiceRoom(s.roomId, t, announcement.trim().take(VoiceRoom.MAX_ANNOUNCEMENT), lang)
                // The language is not part of the room's change events: keep the open room's copy current.
                if (lang != null) update(s.roomId) { v -> v.copy(room = v.room?.copy(lang = lang)) }
                notice(UiText(R.string.room_saved))
                done()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
            }
        }
    }

    /** Posts a conversation starter; the server allows one per room every 20 s, which is said kindly. */
    fun icebreaker() {
        val s = _state.value ?: return
        scope.launch {
            try {
                val line = backend.voiceIcebreaker(s.roomId)
                update(s.roomId) { it.copy(lines = merge(it.lines, listOf(line))) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(if ((e as? BackendException)?.code == "slow_down") UiText(R.string.ib_wait) else explain(e))
            }
        }
    }

    /**
     * A photo picked for the chat: shrunk to 1280 px and checked for nudity on this phone first. One that looks like
     * nudity is not sent (and said so); one that can't be checked isn't sent either.
     */
    fun sendPhoto(uri: String) {
        val s = _state.value ?: return
        if (s.photoBusy) return
        update(s.roomId) { it.copy(photoBusy = true) }
        scope.launch {
            try {
                val jpeg = try {
                    photos.shrink(uri).also { if (photos.nude(it)) return@launch notice(UiText(R.string.err_nude_photo)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: BackendException) {
                    return@launch notice(explain(e))
                } catch (_: Exception) {
                    return@launch notice(UiText(R.string.err_network))
                }
                val line = backend.sendVoicePhoto(s.roomId, jpeg)
                update(s.roomId) { it.copy(lines = merge(it.lines, listOf(line))) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
            } finally {
                update(s.roomId) { it.copy(photoBusy = false) }
            }
        }
    }

    /**
     * A song request (anyone; the host's plays at once when nothing does). Its real title is looked up first, so the
     * queue and the room card show it to everyone. [done] gets whether it was added.
     */
    fun addSong(videoId: String, done: (Boolean) -> Unit = {}) {
        val s = _state.value ?: return done(false)
        if (s.adding != null) return done(false)
        update(s.roomId) { it.copy(adding = videoId) }
        scope.launch {
            val title = backend.videoTitle(videoId)
            update(s.roomId) { it.copy(adding = null) }
            act(VoiceAction.AddTrack(videoId, title), UiText(if (s.isMod) R.string.voice_added else R.string.voice_suggested), done)
        }
    }

    /** One vote per song; again takes it back. */
    fun vote(itemId: String, on: Boolean) = act(VoiceAction.Vote(itemId, on))

    companion object {
        /** The server takes up to 40 characters of search. */
        const val MAX_SEARCH = 40
        /** A mention being typed is at most this long (names are up to 30 characters). */
        const val MAX_MENTION = 30
        /** voice_polls_get takes up to 50 ids a call. */
        const val MAX_POLLS = 50

        /** A poll call's refusals, in the reader's language; null for the rest (network, ...). */
        fun pollError(e: Throwable): UiText? = when ((e as? BackendException)?.code) {
            "poll_closed" -> UiText(R.string.poll_e_closed)
            "not_found" -> UiText(R.string.poll_e_not_found)
            "bad_request" -> UiText(R.string.poll_e_bad_request)
            "slow_down" -> UiText(R.string.poll_e_slow_down)
            "not_allowed" -> UiText(R.string.poll_e_not_allowed)
            else -> null
        }

        /**
         * The mention being typed at the end of [text] ("@" and what follows it, no space yet), or null. "@" counts
         * at the start or after a space, so an e-mail address is not a mention.
         */
        fun mentionQuery(text: String): String? {
            val at = text.lastIndexOf('@')
            if (at < 0 || (at > 0 && !text[at - 1].isWhitespace())) return null
            val q = text.substring(at + 1)
            return q.takeIf { it.length <= MAX_MENTION && '\n' !in it && !it.endsWith(' ') }
        }

        /** [text] with the mention being typed replaced by "@Name ". */
        fun insertMention(text: String, name: String): String {
            val at = text.lastIndexOf('@')
            return if (at < 0) "$text@$name " else text.substring(0, at) + "@" + name + " "
        }

        /** [text] mentions [name] ("@" + the name, any case, not inside another word). */
        fun mentions(text: String, name: String): Boolean {
            if (name.isBlank()) return false
            val needle = "@" + name.trim()
            var i = text.indexOf(needle, ignoreCase = true)
            while (i >= 0) {
                val before = if (i == 0) ' ' else text[i - 1]
                val after = text.getOrNull(i + needle.length) ?: ' '
                if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
                i = text.indexOf(needle, i + 1, ignoreCase = true)
            }
            return false
        }

        const val REACTION_MS = 2_600L
        /** Someone's "typing" shows this long after their last signal (as random chat). */
        const val TYPING_SHOWN_MS = 4_000L
        /** My typing signal goes out at most this often. */
        const val TYPING_EVERY_MS = 2_500L
        const val MAX_REACTIONS = 12
        /** A long session keeps the latest lines (the server keeps none for long: rooms are not recorded). */
        const val MAX_LINES = 300
        /** Above this level a person counts as speaking (their ring shows). */
        const val SPEAKING = 0.12f
        /** The player is put back in step when it is this far (seconds) from the room's position. */
        const val DRIFT_S = 2.5f
        /** How often the list refreshes while it is on screen. */
        const val LIST_REFRESH_MS = 30_000L

        fun merge(old: List<VoiceLine>?, new: List<VoiceLine>): List<VoiceLine> {
            val current = old ?: emptyList()
            val ids = current.mapTo(HashSet()) { it.id }
            val added = new.filter { it.id !in ids }
            if (added.isEmpty()) return old ?: current
            return (current + added).sortedBy { it.id }.takeLast(MAX_LINES)
        }

        /**
         * Where the player should jump to so it plays in step with the room, or null when it is close enough.
         * [playerSeconds]: where the player is now; [now]: this phone's clock.
         */
        fun seekTarget(playerSeconds: Float, track: VoiceTrack, now: Long): Float? {
            val want = track.position(now)
            return if (abs(playerSeconds - want) > DRIFT_S) want else null
        }

        /** The loudest person talking right now (for the pill), or null when nobody is. */
        fun loudest(levels: Map<String, Float>): String? = levels.filterValues { it > SPEAKING }.maxByOrNull { it.value }?.key

        /** A system line in the reader's language, from its typed payload. */
        fun systemText(line: VoiceLine.System): UiText = when (line.event) {
            VoiceSystemEvent.JOINED -> UiText(R.string.voice_sys_joined, line.name)
            VoiceSystemEvent.LEFT -> UiText(R.string.voice_sys_left, line.name)
            VoiceSystemEvent.SEAT -> UiText(R.string.voice_sys_seat, line.name, line.seat ?: 0)
            VoiceSystemEvent.AUDIENCE -> UiText(R.string.voice_sys_audience, line.name)
            VoiceSystemEvent.COHOST -> UiText(R.string.voice_sys_cohost, line.name)
            VoiceSystemEvent.HOST -> UiText(R.string.voice_sys_host, line.name)
            VoiceSystemEvent.TRACK ->
                if (line.title.isNullOrBlank()) UiText(R.string.voice_sys_track_video, line.name) else UiText(R.string.voice_sys_track, line.name, line.title)
            VoiceSystemEvent.REMOVED -> UiText(R.string.voice_sys_removed, line.name)
        }

        /** I take a seat at once (the host, co-hosts, anyone in an open room) rather than raising a hand. */
        fun joinsDirectly(room: VoiceRoom) = room.me?.let { it.isMod || room.mode == com.yaari.chat.model.SpeakMode.OPEN } == true

        /** The 40 icebreaker prompts, in the reader's language (the server stores only the number). */
        private val PROMPTS = intArrayOf(
            R.string.ib_1, R.string.ib_2, R.string.ib_3, R.string.ib_4, R.string.ib_5, R.string.ib_6, R.string.ib_7, R.string.ib_8,
            R.string.ib_9, R.string.ib_10, R.string.ib_11, R.string.ib_12, R.string.ib_13, R.string.ib_14, R.string.ib_15, R.string.ib_16,
            R.string.ib_17, R.string.ib_18, R.string.ib_19, R.string.ib_20, R.string.ib_21, R.string.ib_22, R.string.ib_23, R.string.ib_24,
            R.string.ib_25, R.string.ib_26, R.string.ib_27, R.string.ib_28, R.string.ib_29, R.string.ib_30, R.string.ib_31, R.string.ib_32,
            R.string.ib_33, R.string.ib_34, R.string.ib_35, R.string.ib_36, R.string.ib_37, R.string.ib_38, R.string.ib_39, R.string.ib_40,
        )

        /** The create sheet's size steps, finer for small rooms: 1 up to 10, 5 up to 50, then 10 (as the web). */
        fun sizeUp(n: Int) = minOf(VoiceRoom.MAX_MEMBERS, if (n < 10) n + 1 else if (n < 50) n + 5 else n + 10)

        fun sizeDown(n: Int) = maxOf(VoiceRoom.MIN_MEMBERS, if (n <= 10) n - 1 else if (n <= 50) n - 5 else n - 10)

        /** Prompt [n] (1..40) as a string resource; an unknown number (a newer server) shows the first. */
        fun prompt(n: Int): Int = PROMPTS.getOrElse(n - 1) { PROMPTS[0] }

        /** Why the server sent me out of the room I was in ([VoiceEvent.Refused]), with the room's rules when known. */
        fun refusedText(code: String, rules: com.yaari.chat.model.RoomRules?): UiText = when {
            code == "room_gender" && rules != null && rules.genderRule != GenderRule.ANY ->
                UiText(if (rules.genderRule == GenderRule.MALE) R.string.err_room_men else R.string.err_room_women)
            code == "room_age" && rules != null -> UiText(R.string.err_room_ages, rules.minAge, rules.maxAge)
            else -> UiText(
                when (code) {
                    "room_full" -> R.string.err_room_full
                    "room_gender" -> R.string.err_room_gender
                    "room_age" -> R.string.err_room_age
                    "banned" -> R.string.err_banned
                    "not_signed_in" -> R.string.err_not_signed_in
                    else -> R.string.err_not_allowed
                },
            )
        }

        /** Why I can't enter [room], said with what the room asks for. */
        fun refusalText(room: VoiceRoomSummary): UiText = when (room.refusal) {
            "room_gender" -> UiText(if (room.rules.genderRule == GenderRule.MALE) R.string.err_room_men else R.string.err_room_women)
            "room_age" -> UiText(R.string.err_room_ages, room.rules.minAge, room.rules.maxAge)
            "room_full" -> UiText(R.string.err_room_full)
            "room_closed" -> UiText(R.string.err_room_closed)
            else -> UiText(R.string.err_not_allowed)
        }
    }
}
