package com.yaari.chat.ui

import com.yaari.chat.R
import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.FirstRun
import com.yaari.chat.data.PhotoCheck
import com.yaari.chat.model.Badges
import com.yaari.chat.model.InboxItem
import com.yaari.chat.model.Interest
import com.yaari.chat.model.Profile
import com.yaari.chat.model.ProfilePhoto
import com.yaari.chat.model.ProfileStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the first run shows now: the interests question, then the tour (each once). */
enum class FirstRunStep { INTERESTS, TOUR }

/**
 * Notifications, unread counts, people online, interests, profile photos, room invites and the first run.
 * [inbox] is null until loaded; [fresh]: what was unread when Notifications opened (it keeps the "New" highlight while
 * open). [busy]: the item whose button is in flight. [askNotifications]: the moment to ask for the notifications
 * permission has come (Android 13+; the screen asks, then calls [SocialController.notificationsAsked]).
 */
data class SocialState(
    val badges: Badges = Badges(),
    val inboxOpen: Boolean = false,
    val inbox: List<InboxItem>? = null,
    val inboxFailed: Boolean = false,
    val fresh: Set<Long>? = null,
    val busy: Long? = null,
    val online: Int? = null,
    val firstRun: FirstRunStep? = null,
    val tourCard: Int = 0,
    val editingInterests: Boolean = false,
    val savingInterests: Boolean = false,
    val photoBusy: Boolean = false,
    /** Friends invited from the room I'm in: room id to user ids. */
    val invited: Map<String, Set<String>> = emptyMap(),
    val inviting: String? = null,
    val askNotifications: Boolean = false,
    /** My profile's details are being saved. */
    val savingStyle: Boolean = false,
) {
    val interestsShown get() = editingInterests || firstRun == FirstRunStep.INTERESTS
    fun isNew(i: InboxItem) = fresh?.contains(i.id) ?: !i.read
}

/**
 * Owned by [AppViewModel]; plain coroutines on its scope (no timers: the screens poll the badges every 30 s while
 * visible, so tests drive everything directly). [onMe]: my profile changed (interests, photos).
 */
class SocialController(
    private val backend: Backend,
    private val scope: CoroutineScope,
    private val notice: (UiText) -> Unit,
    private val explain: (Throwable) -> UiText,
    private val photos: PhotoCheck,
    private val firstRun: FirstRun,
    /** Push is set up in this build: only then is the notifications permission worth asking for. */
    private val pushEnabled: Boolean,
    private val onMe: (Profile) -> Unit,
) {
    private val _state = MutableStateFlow(SocialState())
    val state: StateFlow<SocialState> = _state.asStateFlow()

    private var badgesJob: Job? = null
    private var inboxJob: Job? = null

    /** Signed in: the first run, if anything of it is still to see. */
    fun start(me: Profile) {
        _state.value = SocialState(firstRun = nextStep(me.interests.isNotEmpty()))
        refreshBadges()
    }

    fun reset() {
        badgesJob?.cancel()
        inboxJob?.cancel()
        _state.value = SocialState()
    }

    private fun nextStep(hasInterests: Boolean): FirstRunStep? = when {
        !hasInterests && !firstRun.done(FirstRun.Step.INTERESTS) -> FirstRunStep.INTERESTS
        !firstRun.done(FirstRun.Step.TOUR) -> FirstRunStep.TOUR
        else -> null
    }

    // ---- unread counts ----

    /** The newest request wins; failures keep the last counts (the next poll tries again). */
    fun refreshBadges() {
        badgesJob?.cancel()
        badgesJob = scope.launch {
            runCatching { backend.badges() }.onSuccess { b -> _state.update { it.copy(badges = b) } }
        }
    }

    /**
     * Follows my notifications while collected (the screen collects it while signed in): every arrival refreshes the
     * counts, and the open Notifications. A dropped subscription ends quietly; polling covers the gap.
     */
    suspend fun follow() {
        try {
            backend.inboxSignals().collect {
                refreshBadges()
                if (_state.value.inboxOpen) loadInbox()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Unit
        }
    }

    fun refreshOnline() {
        scope.launch { runCatching { backend.onlineCount() }.onSuccess { n -> _state.update { it.copy(online = n) } } }
    }

    // ---- Notifications ----

    /** Opening marks everything read; what was new stays highlighted until it closes (as the web). */
    fun openInbox() {
        _state.update { it.copy(inboxOpen = true, fresh = null, inboxFailed = false) }
        loadInbox()
    }

    fun closeInbox() = _state.update { it.copy(inboxOpen = false, fresh = null, busy = null) }

    fun loadInbox() {
        inboxJob?.cancel()
        inboxJob = scope.launch {
            try {
                val items = backend.inbox()
                val first = _state.value.fresh == null
                val unread = items.filter { !it.read }.map { it.id }
                _state.update { s ->
                    s.copy(inbox = items, inboxFailed = false, fresh = if (first && s.inboxOpen) unread.toSet() else s.fresh)
                }
                if (first && unread.isNotEmpty() && _state.value.inboxOpen) {
                    runCatching { backend.markInboxRead() }
                    refreshBadges()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(inboxFailed = true) }
            }
        }
    }

    /** Runs an item's button ([busy] meanwhile); failures become a notice. */
    fun itemAction(item: InboxItem, block: suspend () -> Unit) {
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = item.id) }
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    /** An invite: in through it (private rooms too), then [open] the room. A room that has ended says so. */
    fun joinInvite(item: InboxItem, open: (String) -> Unit) = itemAction(item) {
        val roomId = backend.joinInvite(item.id)
        closeInbox()
        open(roomId)
    }

    // ---- room invites ----

    fun invite(roomId: String, friend: Profile) {
        if (_state.value.inviting != null) return
        _state.update { it.copy(inviting = friend.id) }
        scope.launch {
            try {
                backend.inviteToRoom(roomId, friend.id)
                _state.update { s -> s.copy(invited = s.invited + (roomId to (s.invited[roomId].orEmpty() + friend.id))) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
            } finally {
                _state.update { it.copy(inviting = null) }
            }
        }
    }

    // ---- interests ----

    fun editInterests(open: Boolean) = _state.update { it.copy(editingInterests = open) }

    /** 3 to 5 tags (the server checks again). In the first run, the tour comes next. */
    fun saveInterests(tags: Set<Interest>) {
        if (tags.size !in Interest.MIN..Interest.MAX || _state.value.savingInterests) return
        _state.update { it.copy(savingInterests = true) }
        scope.launch {
            try {
                val me = backend.setInterests(Interest.entries.filter { it in tags })
                onMe(me)
                notice(UiText(R.string.interests_saved))
                afterInterests()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
            } finally {
                _state.update { it.copy(savingInterests = false) }
            }
        }
    }

    /** Skip (first run) or close (editing): asked once either way. */
    fun skipInterests() = afterInterests()

    private fun afterInterests() {
        if (_state.value.firstRun == FirstRunStep.INTERESTS) firstRun.mark(FirstRun.Step.INTERESTS)
        _state.update { s ->
            s.copy(editingInterests = false, firstRun = if (s.firstRun == FirstRunStep.INTERESTS) nextStep(hasInterests = true) else s.firstRun)
        }
    }

    // ---- the tour ----

    fun nextCard() {
        if (_state.value.tourCard >= TOUR_CARDS - 1) return endTour()
        _state.update { it.copy(tourCard = it.tourCard + 1) }
    }

    fun endTour() {
        firstRun.mark(FirstRun.Step.TOUR)
        _state.update { it.copy(firstRun = null, tourCard = 0) }
    }

    // ---- photos ----

    /** Shrunk and checked for nudity on this phone first (as room photos); one that can't be checked isn't added. */
    fun addPhoto(uri: String, current: Int) {
        if (_state.value.photoBusy) return
        if (current >= ProfilePhoto.MAX) return notice(UiText(R.string.err_not_allowed))
        _state.update { it.copy(photoBusy = true) }
        scope.launch {
            try {
                val jpeg = try {
                    photos.shrink(uri).also { if (photos.nude(it)) return@launch notice(UiText(R.string.photos_nude)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: BackendException) {
                    return@launch notice(explain(e))
                } catch (_: Exception) {
                    return@launch notice(UiText(R.string.err_network))
                }
                onMe(backend.addProfilePhoto(jpeg))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
            } finally {
                _state.update { it.copy(photoBusy = false) }
            }
        }
    }

    /** Saves my profile's details ([style], as the editor shows them) all at once; [done] runs once they are saved. */
    fun saveStyle(style: ProfileStyle, done: () -> Unit = {}) {
        if (_state.value.savingStyle) return
        if (!style.valid()) return notice(UiText(R.string.style_bad))
        _state.update { it.copy(savingStyle = true) }
        scope.launch {
            try {
                onMe(backend.setProfileStyle(style))
                notice(UiText(R.string.style_saved))
                done()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(if ((e as? BackendException)?.code == "bad_request") UiText(R.string.style_bad) else explain(e))
            } finally {
                _state.update { it.copy(savingStyle = false) }
            }
        }
    }

    fun removePhoto(id: String) {
        scope.launch {
            try {
                onMe(backend.removeProfilePhoto(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
            }
        }
    }

    // ---- the notifications permission ----

    /** I have a friend now: their messages and invites can reach the phone, so ask once (when push is set up). */
    fun friendAdded() {
        if (!pushEnabled || firstRun.done(FirstRun.Step.NOTIFICATIONS) || _state.value.askNotifications) return
        _state.update { it.copy(askNotifications = true) }
    }

    fun notificationsAsked() {
        firstRun.mark(FirstRun.Step.NOTIFICATIONS)
        _state.update { it.copy(askNotifications = false) }
    }

    companion object {
        const val TOUR_CARDS = 3
        /** The badges are asked for this often while the app is on screen (it also tells the server I'm here). */
        const val BADGES_MS = 30_000L
        const val ONLINE_MS = 15_000L
    }
}
