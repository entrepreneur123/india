package com.yaari.chat.ui

import com.yaari.chat.R
import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.PhotoCheck
import com.yaari.chat.model.LudoAction
import com.yaari.chat.model.LudoGame
import com.yaari.chat.model.LudoStatus
import com.yaari.chat.model.GenderRule
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.RoomLeader
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceAudioState
import com.yaari.chat.model.VoiceEvent
import com.yaari.chat.model.VoiceLine
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
    val expanded: Boolean = true,
    /** The host asked to leave: their room would carry on without them, so leaving ends it (after this confirm). */
    val confirmEnd: Boolean = false,
    val ending: Boolean = false,
    /** The room's Ludo game (lobby, playing, or a result from the last few minutes), as the server last said. */
    val ludo: LudoGame? = null,
    /** The stage is shown instead of the game (anyone can switch back). */
    val ludoStage: Boolean = false,
    /** The id of a result I dismissed ("Back to the room"). */
    val ludoDismissed: String? = null,
    val confirmLudoClose: Boolean = false,
    /** The room's Ludo winners (games rooms), null until loaded. */
    val leaders: List<RoomLeader>? = null,
    /** A photo is being checked and sent. */
    val photoBusy: Boolean = false,
    /** A song request whose title is being looked up (its video id). */
    val adding: String? = null,
) {
    val me get() = room?.me
    val isHost get() = me?.role == VoiceRole.HOST
    val isMod get() = me?.isMod == true
    /** Raised hands a host or co-host should see. */
    val hands get() = if (isMod) room?.audience?.count { it.hand } ?: 0 else 0

    /** A game is up for this room: open, or a result not dismissed yet. */
    val ludoOn get() = ludo != null && (ludo.status.open || (ludo.status == LudoStatus.FINISHED && ludoDismissed != ludo.id))
    /** The game shown in place of the stage, or null. */
    val ludoShown get() = if (ludoOn && !ludoStage) ludo else null
    /** A game is on but the stage is shown: a way back to it is offered. */
    val ludoHidden get() = ludoOn && ludoStage
    /** My colour (0-3) in the game, or -1 when I watch. */
    val ludoSeat get() = room?.meId?.let { id -> ludo?.players?.indexOf(id) } ?: -1

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
    /** Wall clock (Ludo deadlines are on it). */
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
    private var reactionSeq = 0L
    private var ludoTimeout: Job? = null
    /** I'm closing the game myself (no "the game was closed" notice then). */
    private var closingLudo = false

    // ---- the list ----

    /** All rooms or one category (kept while the app runs, like the web). */
    fun filter(category: RoomCategory?) = _list.update { it.copy(filter = category) }

    /** The newest request wins. */
    fun refreshList() {
        listJob?.cancel()
        listJob = scope.launch {
            try {
                val rooms = backend.voiceRooms()
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
        task {
            val made = backend.createVoiceRoom(spec.copy(title = title, announcement = spec.announcement.trim()))
            done()
            if (r.isPrivate) _list.update { it.copy(created = CreatedNotice(title, made.code)) }
            open(made.id)
            refreshList()
        }
    }

    fun dismissCreated() = _list.update { it.copy(created = null) }

    /** Enters a room with the code its host shared (the only way into a private room). */
    fun joinCode(input: String, done: () -> Unit = {}) {
        val code = input.trim().uppercase()
        if (code.length != VoiceRoom.CODE_LENGTH || !code.all { it in 'A'..'Z' || it in '0'..'9' }) return notice(UiText(R.string.err_room_code))
        task {
            val id = try {
                backend.joinVoiceCode(code)
            } catch (e: BackendException) {
                if (e.code == "not_found") return@task notice(UiText(R.string.err_room_code_unknown))
                throw e
            }
            done()
            open(id)
            refreshList()
        }
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

    /** Enters [roomId] full screen (or brings it back up when I'm already in it). One room at a time. */
    fun open(roomId: String) {
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
                                launch { followLudo(roomId) }
                                if (e.room.category == RoomCategory.GAMES) launch { loadLeaders(roomId) }
                            }
                        }
                        is VoiceEvent.Levels -> _levels.value = e.levels
                        is VoiceEvent.Audio -> update(roomId) { it.copy(audio = e.audio) }
                        is VoiceEvent.Line -> update(roomId) { it.copy(lines = merge(it.lines, listOf(e.line))) }
                        is VoiceEvent.Reaction -> launch { float(roomId, e.userId, e.kind) }
                        VoiceEvent.Ended -> {
                            if (_state.value?.ending != true) notice(UiText(R.string.voice_ended))
                            gone(roomId, leave = false)
                        }
                        VoiceEvent.Removed -> {
                            notice(UiText(R.string.voice_removed))
                            gone(roomId, leave = false)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Couldn't get in (the room ended, I was removed, offline): back to where I was.
                notice(explain(e))
                gone(roomId, leave = false)
            }
        }
    }

    private suspend fun loadLines(roomId: String) {
        try {
            val history = backend.voiceLines(roomId)
            update(roomId) { it.copy(lines = merge(it.lines, history)) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            update(roomId) { it.copy(lines = it.lines ?: emptyList()) }
        }
    }

    /** The room's Ludo leaderboard (again after each finished game). A failed load keeps what was shown. */
    private suspend fun loadLeaders(roomId: String) {
        try {
            val list = backend.voiceLeaders(roomId)
            update(roomId) { it.copy(leaders = list) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            update(roomId) { it.copy(leaders = it.leaders ?: emptyList()) }
        }
    }

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
        ludoTimeout?.cancel()
        ludoTimeout = null
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

    /** "Leave": the host's room would carry on without them, so the host confirms ending it instead. */
    fun leave() {
        val s = _state.value ?: return
        if (s.isHost) _state.update { it?.copy(confirmEnd = true, expanded = true) } else close()
    }

    fun dismissEnd() = _state.update { it?.copy(confirmEnd = false) }

    /** Leaves quietly (anyone, the host included: the server gives the host 2 minutes to come back). */
    fun close() {
        val s = _state.value ?: return
        gone(s.roomId, leave = true)
    }

    /** Host only, after the confirm: everyone leaves and the room closes. */
    fun end() {
        val s = _state.value ?: return
        if (!s.isHost || s.ending) return
        update(s.roomId) { it.copy(ending = true) }
        scope.launch {
            try {
                backend.endVoice(s.roomId)
                notice(UiText(R.string.voice_ended_by_you))
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

    fun send(text: String, onFailed: () -> Unit = {}) {
        val s = _state.value ?: return
        val t = text.trim().take(VoiceRoom.MAX_TEXT)
        if (t.isEmpty()) return
        scope.launch {
            try {
                val line = backend.sendVoice(s.roomId, t)
                update(s.roomId) { it.copy(lines = merge(it.lines, listOf(line))) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
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

    /** Host: the title and the pinned announcement (empty removes it). */
    fun saveSettings(title: String, announcement: String, done: () -> Unit = {}) {
        val s = _state.value ?: return
        val t = title.trim()
        if (t.length !in VoiceRoom.MIN_TITLE..VoiceRoom.MAX_TITLE) return notice(UiText(R.string.err_room_title))
        scope.launch {
            try {
                backend.updateVoiceRoom(s.roomId, t, announcement.trim().take(VoiceRoom.MAX_ANNOUNCEMENT))
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
            act(VoiceAction.AddTrack(videoId, title), UiText(if (s.isHost) R.string.voice_added else R.string.voice_suggested), done)
        }
    }

    /** One vote per song; again takes it back. */
    fun vote(itemId: String, on: Boolean) = act(VoiceAction.Vote(itemId, on))

    // ---- Ludo in the room ----

    /** The room's game, followed while I'm in the room (its rows are readable by people in the room only). */
    private suspend fun followLudo(roomId: String) {
        try {
            setLudo(roomId, backend.ludoGame(roomId))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The follow below re-reads it.
        }
        while (true) {
            try {
                backend.ludoGames(roomId).collect { setLudo(roomId, it) }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                delay(LUDO_RETRY_MS)
            }
        }
    }

    /** A newer version of the same game, or another game unless that would replace an open one with an old result. */
    internal fun setLudo(roomId: String, g: LudoGame?) {
        if (g == null) return
        val s = _state.value?.takeIf { it.roomId == roomId } ?: return
        val prev = s.ludo
        val take = prev == null || (if (g.id == prev.id) g.version > prev.version else g.status.open || !prev.status.open)
        if (!take) return
        // A new game comes up for everyone.
        _state.value = s.copy(ludo = g, ludoStage = if (prev?.id != g.id) false else s.ludoStage)
        // A game the host closed: the stage is back, with a word for those who didn't close it.
        if (g.status == LudoStatus.CANCELLED && prev?.id == g.id && prev.status.open && !closingLudo) notice(UiText(R.string.ludo_closed))
        // A game won: the leaderboard has a new line.
        if (g.status == LudoStatus.FINISHED && (prev?.id != g.id || prev.status != LudoStatus.FINISHED) && s.room?.category == RoomCategory.GAMES) {
            scope.launch { loadLeaders(roomId) }
        }
        scheduleLudoTimeout(roomId)
    }

    /**
     * The turn's time is up and the player hasn't moved: players and hosts ask the server to play it (it checks the
     * clock and the version, so the first call wins and the others change nothing). Clocks differ a little, so a
     * refused call is tried again shortly, a few times.
     */
    private fun scheduleLudoTimeout(roomId: String) {
        ludoTimeout?.cancel()
        ludoTimeout = null
        val s = _state.value ?: return
        val g = s.ludo ?: return
        val deadline = g.deadline
        if (g.status != LudoStatus.PLAYING || deadline == null || (s.ludoSeat < 0 && !s.isMod)) return
        ludoTimeout = scope.launch {
            repeat(LUDO_TIMEOUT_TRIES) {
                delay(timeoutDelay(deadline, wallClock(), random.nextLong(LUDO_TIMEOUT_JITTER_MS)))
                val got = try {
                    backend.ludoAction(roomId, g.id, LudoAction.Timeout(g.version))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (got != null && got.version != g.version) return@launch setLudo(roomId, got)
            }
        }
    }

    /** Does [action] in the room's game; [done] gets whether it worked. A stale screen re-reads the game. */
    fun ludo(action: LudoAction, done: (Boolean) -> Unit = {}) {
        val s = _state.value ?: return done(false)
        val gameId = if (action == LudoAction.Open) null else s.ludo?.id
        scope.launch {
            val ok = try {
                setLudo(s.roomId, backend.ludoAction(s.roomId, gameId, action))
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                when (val code = (e as? BackendException)?.code) {
                    "stale" -> runCatching { backend.ludoGame(s.roomId) }.getOrNull()?.let { setLudo(s.roomId, it) }
                    else -> notice(ludoError(code) ?: explain(e))
                }
                false
            }
            done(ok)
        }
    }

    /** The game button: back to the game, or (hosts and co-hosts) a new one. */
    fun ludoButton() {
        val s = _state.value ?: return
        if (s.ludoOn) return showLudo(true)
        if (!s.isMod) return
        ludo(LudoAction.Open) { ok -> if (ok) showLudo(true) }
    }

    /** The game (true) or the stage (false) in the top half of the room. */
    fun showLudo(game: Boolean) = _state.update { it?.copy(ludoStage = !game) }

    /** "Back to the room" on a result. */
    fun dismissLudo() = _state.update { s -> s?.copy(ludoDismissed = s.ludo?.id) }

    fun askCloseLudo(show: Boolean) = _state.update { it?.copy(confirmLudoClose = show) }

    /** Host or co-host, after the confirm: the game ends for everyone and the stage comes back. */
    fun closeLudo() {
        closingLudo = true
        ludo(LudoAction.Close) {
            closingLudo = false
            askCloseLudo(false)
        }
    }

    companion object {
        const val LUDO_RETRY_MS = 5_000L
        const val LUDO_TIMEOUT_TRIES = 8
        /** Spread the timeout calls of everyone in the room a little (the first one wins). */
        const val LUDO_TIMEOUT_GRACE_MS = 700L
        const val LUDO_TIMEOUT_JITTER_MS = 1_300L

        /** How long to wait before asking the server to play a turn that ends at [deadline] (wall clock). */
        fun timeoutDelay(deadline: Long, now: Long, jitter: Long) = maxOf(0L, deadline - now) + LUDO_TIMEOUT_GRACE_MS + jitter

        /** The game's own refusals, in the reader's language; null for the rest. */
        fun ludoError(code: String?): UiText? = when (code) {
            "not_your_turn" -> UiText(R.string.ludo_e_not_your_turn)
            "seat_taken" -> UiText(R.string.ludo_e_seat_taken)
            "game_full" -> UiText(R.string.ludo_e_game_full)
            "need_players" -> UiText(R.string.ludo_e_need_players)
            else -> null
        }

        const val REACTION_MS = 2_600L
        const val MAX_REACTIONS = 12
        /** A long session keeps the latest lines (the server keeps none for long: rooms are not recorded). */
        const val MAX_LINES = 300
        /** Above this level a person counts as speaking (their ring shows). */
        const val SPEAKING = 0.12f
        /** The player is put back in step when it is this far (seconds) from the room's position. */
        const val DRIFT_S = 2.5f
        /** How often the list refreshes while it is on screen. */
        const val LIST_REFRESH_MS = 15_000L

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
