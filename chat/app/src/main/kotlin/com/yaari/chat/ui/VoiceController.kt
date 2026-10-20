package com.yaari.chat.ui

import com.yaari.chat.R
import com.yaari.chat.data.Backend
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.ReportReason
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

/**
 * The Voice list on the Rooms tab. [rooms] is null until the first load; [failed]: that load failed. [voiceTab]: the
 * tab shows voice rooms (the default) rather than chat rooms.
 */
data class VoiceListState(
    val rooms: List<VoiceRoomSummary>? = null, val failed: Boolean = false, val creating: Boolean = false, val voiceTab: Boolean = true,
)

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
) {
    val me get() = room?.me
    val isHost get() = me?.role == VoiceRole.HOST
    val isMod get() = me?.isMod == true
    /** Raised hands a host or co-host should see. */
    val hands get() = if (isMod) room?.audience?.count { it.hand } ?: 0 else 0
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
    private val random: Random = Random.Default,
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

    // ---- the list ----

    /** Voice or Chat on the Rooms tab (kept while the app runs, like the web). */
    fun showVoiceTab(voice: Boolean) = _list.update { it.copy(voiceTab = voice) }

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

    /** Validates what the server validates, then opens the new room with me as host. */
    fun create(spec: NewVoiceRoom, done: () -> Unit = {}) {
        val title = spec.title.trim()
        if (title.length !in VoiceRoom.MIN_TITLE..VoiceRoom.MAX_TITLE) return notice(UiText(R.string.err_room_title))
        if (_list.value.creating) return
        _list.update { it.copy(creating = true) }
        scope.launch {
            try {
                val id = backend.createVoiceRoom(spec.copy(title = title))
                done()
                open(id)
                refreshList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
            } finally {
                _list.update { it.copy(creating = false) }
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

    /** Someone was reported or blocked: their lines go at once. */
    fun forget(userId: String) = _state.update { s ->
        s?.copy(lines = s.lines?.filterNot { it is VoiceLine.Text && it.sender.id == userId })
    }

    companion object {
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
    }
}
