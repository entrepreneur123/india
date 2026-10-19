package com.yaari.chat.ui

import android.app.Activity
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaari.chat.R
import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.Billing
import com.yaari.chat.data.Entitlements
import com.yaari.chat.data.Match
import com.yaari.chat.data.MatchEvent
import com.yaari.chat.data.NewProfile
import com.yaari.chat.data.OrphanMatchException
import com.yaari.chat.data.VideoStatus
import com.yaari.chat.data.VideoToken
import com.yaari.chat.domain.Countries
import com.yaari.chat.domain.Country
import com.yaari.chat.domain.ExitPolicy
import com.yaari.chat.domain.Validation
import com.yaari.chat.model.Friend
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Media
import com.yaari.chat.model.Message
import com.yaari.chat.model.Profile
import com.yaari.chat.model.RecentMatch
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.Room
import com.yaari.chat.model.RoomMessage
import com.yaari.chat.model.Target
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class Tab { ROOMS, RANDOM, RECENT, ME }

/** Why the Premium sheet opened; [reason] is shown above the offer. */
enum class Paywall(@StringRes val reason: Int?) {
    GENERAL(null),
    GENDER(R.string.premium_needed_gender),
    VIDEO(R.string.premium_needed_video),
}

/** [phone] is the full international number in digits once entered ("919876543210"). */
data class Onboarding(val step: Step = Step.PHONE, val phone: String = "", val busy: Boolean = false, val error: UiText? = null) {
    enum class Step { PHONE, OTP, PROFILE }
}

data class ProfileDraft(
    val name: String = "",
    val gender: Gender? = null,
    val day: String = "",
    val month: String = "",
    val year: String = "",
    val city: String = "",
    val bio: String = "",
    val photoUri: String? = null,
    val agreed: Boolean = false,
)

data class ChatState(
    val match: Match,
    /** Monotonic clock (elapsedRealtime) when the match opened here; the server's own clock starts earlier. */
    val startedAtMs: Long,
    val messages: List<Message> = emptyList(),
    val partnerTyping: Boolean = false,
    val partnerLeft: Boolean = false,
    val video: VideoStatus = VideoStatus.NONE,
)

data class DmState(val recent: RecentMatch, val messages: List<Message> = emptyList())

data class RoomState(val room: Room, val messages: List<RoomMessage> = emptyList(), val loading: Boolean = true)

data class UiState(
    val booting: Boolean = true,
    val onboarding: Onboarding = Onboarding(),
    val me: Profile? = null,
    val entitlements: Entitlements = Entitlements(),
    val prices: Map<String, String> = emptyMap(),
    val tab: Tab = Tab.RANDOM,
    val target: Target = Target.ANY,
    val searching: Boolean = false,
    val chat: ChatState? = null,
    val viewing: Profile? = null,
    val dm: DmState? = null,
    val recents: List<RecentMatch> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val rooms: List<Room> = emptyList(),
    val room: RoomState? = null,
    val paywall: Paywall? = null,
    val notice: UiText? = null,
) {
    val premium get() = entitlements.premium

    fun friendState(userId: String) = friends.firstOrNull { it.profile.id == userId }?.state ?: FriendState.NONE
    val incomingRequests get() = friends.count { it.state == FriendState.INCOMING }
}

class AppViewModel(
    private val backend: Backend,
    private val billing: Billing,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var matchJob: Job? = null
    private var chatJob: Job? = null
    private var dmJob: Job? = null
    private var roomJob: Job? = null

    init {
        viewModelScope.launch {
            val signedIn = runCatching { backend.isSignedIn() }.getOrDefault(false)
            val me = if (signedIn) runCatching { backend.myProfile() }.getOrNull() else null
            _state.update {
                when {
                    !signedIn -> it.copy(booting = false)
                    me == null -> it.copy(booting = false, onboarding = Onboarding(Onboarding.Step.PROFILE))
                    else -> it.copy(booting = false, me = me)
                }
            }
            if (me != null) afterSignIn()
        }
    }

    private suspend fun afterSignIn() {
        refreshFriends()
        refreshEntitlements()
        runCatching { billing.prices() }.onSuccess { p -> _state.update { it.copy(prices = p) } }
        restorePurchases()
    }

    private val redeemedTokens = mutableSetOf<String>()

    /**
     * Purchases made offline, on another install, paid later (UPI "pending"), or whose server reply was
     * lost are verified by the server here; passes are consumed only after the server granted them.
     * Called at sign-in and whenever the app comes back to the foreground.
     */
    fun restorePurchases() {
        if (_state.value.me == null) return
        viewModelScope.launch {
            runCatching { billing.ownedPurchases() }.getOrDefault(emptyList()).forEach { (product, token) ->
                if (token in redeemedTokens) return@forEach
                runCatching { backend.redeem(product, token) }.onSuccess { e ->
                    redeemedTokens += token
                    applyEntitlements(e)
                    runCatching { billing.finish(product, token) }
                }
            }
        }
    }

    private suspend fun refreshEntitlements() {
        runCatching { backend.entitlements() }.onSuccess(::applyEntitlements)
    }

    private fun applyEntitlements(e: Entitlements) = _state.update {
        it.copy(entitlements = e, target = if (e.canChooseGender) it.target else Target.ANY)
    }

    private fun notice(text: UiText) = _state.update { it.copy(notice = text) }

    private fun explain(e: Throwable) = UiText(
        when ((e as? BackendException)?.code) {
            "exit_locked" -> R.string.err_exit_locked
            "premium_required" -> R.string.err_premium
            "banned" -> R.string.err_banned
            "under_18" -> R.string.err_under_18
            "slow_down" -> R.string.err_slow_down
            "blocked" -> R.string.err_blocked
            "not_allowed" -> R.string.err_not_allowed
            "video_not_configured" -> R.string.err_video_off
            "too_large" -> R.string.err_too_large
            "not_found" -> R.string.err_not_found
            else -> R.string.err_network
        },
    )

    /** Runs [block]; failures become a notice instead of a crash. */
    private fun act(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notice(explain(e))
        }
    }

    // ---- Onboarding -------------------------------------------------------------------------

    fun requestOtp(country: Country, input: String) {
        val phone = Validation.phone(country.dial, input)
        if (phone == null) {
            _state.update { it.copy(onboarding = it.onboarding.copy(error = UiText(R.string.err_phone))) }
            return
        }
        _state.update { it.copy(onboarding = Onboarding(Onboarding.Step.PHONE, phone, busy = true)) }
        viewModelScope.launch {
            val ok = runCatching { backend.sendOtp(phone) }.isSuccess
            _state.update {
                it.copy(onboarding = if (ok) it.onboarding.copy(step = Onboarding.Step.OTP, busy = false)
                else it.onboarding.copy(busy = false, error = UiText(R.string.err_otp_send)))
            }
        }
    }

    fun verifyOtp(code: String) {
        val phone = _state.value.onboarding.phone
        _state.update { it.copy(onboarding = it.onboarding.copy(busy = true, error = null)) }
        viewModelScope.launch {
            val ok = runCatching { backend.verifyOtp(phone, code.trim()) }.getOrDefault(false)
            if (!ok) {
                _state.update { it.copy(onboarding = it.onboarding.copy(busy = false, error = UiText(R.string.err_otp_wrong))) }
                return@launch
            }
            val existing = runCatching { backend.myProfile() }.getOrNull()
            _state.update {
                if (existing != null) it.copy(me = existing, onboarding = Onboarding())
                else it.copy(onboarding = it.onboarding.copy(step = Onboarding.Step.PROFILE, busy = false))
            }
            if (existing != null) afterSignIn()
        }
    }

    fun backToPhone() = _state.update { it.copy(onboarding = Onboarding()) }

    fun createProfile(d: ProfileDraft) {
        val name = d.name.trim()
        val birth = Validation.birthDate(d.day, d.month, d.year)
        val age = birth?.let { Validation.age(it, today()) }
        val error = when {
            name.isEmpty() -> UiText(R.string.err_name)
            name.length > Validation.MAX_NAME -> UiText(R.string.err_name_long)
            d.gender == null -> UiText(R.string.err_gender)
            birth == null || age == null || age > Validation.MAX_AGE -> UiText(R.string.err_birth)
            age < Validation.MIN_AGE -> UiText(R.string.err_under_18)
            d.bio.length > Validation.MAX_BIO -> UiText(R.string.err_bio_long)
            !d.agreed -> UiText(R.string.err_rules)
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(onboarding = it.onboarding.copy(error = error)) }
            return
        }
        _state.update { it.copy(onboarding = it.onboarding.copy(busy = true, error = null)) }
        viewModelScope.launch {
            try {
                val me = backend.createProfile(NewProfile(name, d.gender!!, birth!!, d.city.trim().ifEmpty { "India" }, d.bio.trim(), d.photoUri))
                _state.update { it.copy(me = me, onboarding = Onboarding()) }
                afterSignIn()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(onboarding = it.onboarding.copy(busy = false, error = explain(e))) }
            }
        }
    }

    // ---- Lobby, Premium ---------------------------------------------------------------------

    fun selectTab(tab: Tab) {
        _state.update { it.copy(tab = tab) }
        when (tab) {
            Tab.RECENT -> { refreshRecents(); viewModelScope.launch { refreshFriends() } }
            Tab.ROOMS -> refreshRooms()
            else -> Unit
        }
    }

    fun setTarget(target: Target) {
        if (target.isPaid && !_state.value.entitlements.canChooseGender) {
            showPaywall(Paywall.GENDER)
            return
        }
        _state.update { it.copy(target = target) }
    }

    fun showPaywall(reason: Paywall) = _state.update { it.copy(paywall = reason) }

    fun dismissPaywall() = _state.update { it.copy(paywall = null) }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    /** Google Play purchase, then server-side verification. The entitlement only comes from the server. */
    fun buy(activity: Activity, productId: String) {
        val me = _state.value.me ?: return
        act {
            val token = billing.buy(activity, productId, me.id)
            if (token == null) {
                restorePurchases() // a payment that completed outside the sheet is picked up here
                return@act
            }
            applyEntitlements(backend.redeem(productId, token))
            redeemedTokens += token
            billing.finish(productId, token)
            _state.update { it.copy(paywall = null, notice = UiText(R.string.msg_purchase_done)) }
        }
    }

    // ---- Matching -----------------------------------------------------------------------------

    fun startMatching() {
        val s = _state.value
        if (s.me == null || s.searching || s.chat != null) return
        _state.update { it.copy(searching = true, dm = null, viewing = null) }
        dmJob?.cancel()
        if (s.room != null) leaveRoom()
        val gen = ++searchGeneration
        fun current() = gen == searchGeneration && _state.value.searching
        matchJob = viewModelScope.launch {
            try {
                while (isActive && current()) {
                    // Not cancellable: if the server created a match, we must learn about it (see abandon()).
                    val m = try {
                        withContext(NonCancellable) { backend.findMatch(s.target) }
                    } catch (e: OrphanMatchException) {
                        abandon(e.matchId) // paired, but the partner's profile couldn't load: end it and keep looking
                        null
                    } catch (e: BackendException) {
                        // A chat we just left (or abandoned) still holds the exit lock for a moment: wait it out.
                        if (e.code != "exit_locked" || !current()) throw e
                        null
                    }
                    if (!current()) { // cancelled (or restarted) while the request was in flight
                        if (m != null) abandon(m.id) else if (!_state.value.searching) runCatching { backend.cancelSearch() }
                        return@launch
                    }
                    if (m != null) {
                        abandoned -= m.id // the server can hand back a match an older search gave up on: keep it
                        openChat(m)
                        return@launch
                    }
                    delay(POLL_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == searchGeneration) { // an old search's error must not stop the new one
                    _state.update { it.copy(searching = false) }
                    notice(explain(e))
                }
            }
        }
    }

    /** Each search has a number; only the newest one may open a chat. */
    private var searchGeneration = 0

    /** Matches the user never saw, waiting to be ended once the exit lock allows. */
    private val abandoned = mutableSetOf<String>()
    private val abandonJobs = mutableListOf<Job>()

    /** The loop notices on its next turn; a request already in flight still completes (see startMatching). */
    fun cancelMatching() {
        _state.update { it.copy(searching = false) }
        act { backend.cancelSearch() }
    }

    /** End a match the user never saw, so the other person isn't left waiting, unless the user opened it after all. */
    private fun abandon(matchId: String) {
        abandoned += matchId
        abandonJobs += viewModelScope.launch {
            delay(ExitPolicy.lockMs(_state.value.premium) + 500)
            if (abandoned.remove(matchId) && _state.value.chat?.match?.id != matchId) {
                runCatching { backend.leave(matchId) }
            }
        }
        abandonJobs.removeAll { it.isCompleted }
    }

    private fun openChat(m: Match) {
        _state.update { it.copy(searching = false, chat = ChatState(m, clock())) }
        viewModelScope.launch { refreshEntitlements() } // the exit lock depends on Premium, which may have expired
        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            var warned = false
            // Reconnects on its own after a dropped connection; the history merge fills any gap.
            while (isActive) {
                runCatching { backend.history(m.id) }.onSuccess { h -> updateChat(m.id) { c -> c.copy(messages = merge(c.messages, h)) } }
                try {
                    backend.events(m.id).collect { e -> applyChatEvent(m.id, e) }
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!warned) notice(UiText(R.string.msg_connection_lost))
                    warned = true
                    delay(RECONNECT_MS)
                }
            }
        }
    }

    private fun applyChatEvent(matchId: String, e: MatchEvent) = updateChat(matchId) { c ->
        when (e) {
            is MatchEvent.NewMessage -> c.copy(messages = merge(c.messages, listOf(e.message)), partnerTyping = false)
            is MatchEvent.Typing -> c.copy(partnerTyping = e.on)
            MatchEvent.PartnerLeft -> c.copy(partnerLeft = true, partnerTyping = false, video = VideoStatus.NONE)
            is MatchEvent.Video -> {
                if (e.status == VideoStatus.DECLINED && c.video == VideoStatus.REQUESTED_BY_ME) {
                    notice(UiText(R.string.msg_declined, c.match.partner.name))
                }
                c.copy(video = e.status)
            }
        }
    }

    private fun updateChat(matchId: String, change: (ChatState) -> ChatState) = _state.update {
        val c = it.chat
        if (c == null || c.match.id != matchId) it else it.copy(chat = change(c))
    }

    private fun merge(old: List<Message>, new: List<Message>): List<Message> {
        if (new.isEmpty()) return old
        val ids = old.mapTo(HashSet()) { it.id }
        val added = new.filter { it.id !in ids }
        return if (added.isEmpty()) old else (old + added).sortedBy { it.id }
    }

    // ---- Chat -------------------------------------------------------------------------------

    fun exitRemainingMs(): Long {
        val s = _state.value
        val c = s.chat ?: return 0
        if (c.partnerLeft) return 0
        return ExitPolicy.remainingMs(s.premium, c.startedAtMs, clock())
    }

    fun sendText(text: String) {
        val t = text.trim().take(MAX_TEXT)
        val c = _state.value.chat ?: return
        if (t.isEmpty() || c.partnerLeft) return
        act { backend.send(c.match.id, t, null).also { m -> updateChat(c.match.id) { it.copy(messages = merge(it.messages, listOf(m))) } } }
    }

    private var lastTypingSentMs = Long.MIN_VALUE / 2

    /** Called on every keystroke; tells the other person at most every 3 s. */
    fun typing() {
        val id = _state.value.chat?.match?.id ?: _state.value.dm?.recent?.matchId ?: return
        val now = clock()
        if (now - lastTypingSentMs < TYPING_EVERY_MS) return
        lastTypingSentMs = now
        viewModelScope.launch { runCatching { backend.sendTyping(id) } }
    }

    fun sendMedia(media: Media) {
        val c = _state.value.chat ?: return
        if (c.partnerLeft) return
        act { backend.send(c.match.id, null, media).also { m -> updateChat(c.match.id) { it.copy(messages = merge(it.messages, listOf(m))) } } }
    }

    /** Normal exit (X / back). Ignored while the exit lock is running; the server checks it again. */
    fun leave(next: Boolean) {
        val c = _state.value.chat ?: return
        if (exitRemainingMs() > 0) return
        act {
            gone { backend.leave(c.match.id) }
            closeChat()
            if (next) startMatching()
        }
    }

    /** Safety exit: never locked. Blocks the partner and removes them from Recent. */
    fun reportAndLeave(reason: ReportReason, next: Boolean) {
        val c = _state.value.chat ?: return
        act {
            gone { backend.report(c.match.partner.id, reason, c.match.id) }
            closeChat()
            notice(UiText(R.string.msg_reported_long))
            if (next) startMatching()
        }
    }

    /** "not_found" means the chat no longer exists (the other person deleted their account): treat as done. */
    private suspend fun gone(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: BackendException) {
            if (e.code != "not_found") throw e
        }
    }

    private fun closeChat() {
        chatJob?.cancel()
        _state.update { it.copy(chat = null, viewing = null) }
        refreshRecents()
    }

    fun requestVideo() {
        val s = _state.value
        val c = s.chat ?: return
        if (!s.premium) {
            showPaywall(Paywall.VIDEO)
            return
        }
        if (c.partnerLeft || c.video in setOf(VideoStatus.REQUESTED_BY_ME, VideoStatus.ACCEPTED)) return
        act {
            backend.requestVideo(c.match.id)
            updateChat(c.match.id) { it.copy(video = VideoStatus.REQUESTED_BY_ME) }
        }
    }

    fun answerVideo(accept: Boolean) {
        val c = _state.value.chat ?: return
        act {
            backend.answerVideo(c.match.id, accept)
            updateChat(c.match.id) { it.copy(video = if (accept) VideoStatus.ACCEPTED else VideoStatus.NONE) }
        }
    }

    fun endVideo() {
        val c = _state.value.chat ?: return
        updateChat(c.match.id) { it.copy(video = VideoStatus.NONE) }
        act { backend.endVideo(c.match.id) }
    }

    suspend fun videoToken(): VideoToken? {
        val c = _state.value.chat ?: return null
        return try {
            backend.videoToken(c.match.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notice(explain(e))
            null
        }
    }

    // ---- Profiles, Recent, DMs --------------------------------------------------------------

    fun openProfile(p: Profile) {
        _state.update { it.copy(viewing = p) }
        act {
            val full = backend.profile(p.id) ?: return@act
            _state.update { if (it.viewing?.id == p.id) it.copy(viewing = full) else it }
        }
    }

    fun closeProfile() = _state.update { it.copy(viewing = null) }

    /** Report from a profile or Recent: blocks and removes the person everywhere. */
    fun report(partnerId: String, reason: ReportReason) {
        val c = _state.value.chat
        if (c?.match?.partner?.id == partnerId) {
            reportAndLeave(reason, next = false)
            return
        }
        act {
            backend.report(partnerId, reason, null)
            dmJob?.takeIf { _state.value.dm?.recent?.partner?.id == partnerId }?.cancel()
            _state.update { s ->
                s.copy(
                    recents = s.recents.filterNot { it.partner.id == partnerId },
                    friends = s.friends.filterNot { it.profile.id == partnerId },
                    room = s.room?.let { r -> r.copy(messages = r.messages.filterNot { it.sender.id == partnerId }) },
                    viewing = s.viewing?.takeIf { it.id != partnerId },
                    dm = s.dm?.takeIf { it.recent.partner.id != partnerId },
                    notice = UiText(R.string.msg_reported),
                )
            }
        }
    }

    fun refreshRecents() = act {
        val list = backend.recents()
        _state.update { it.copy(recents = list) }
    }

    /** Message someone from Recent, or a friend (their chat, or a new private one the server opens). */
    fun openDm(partnerId: String) {
        val s = _state.value
        s.recents.firstOrNull { it.partner.id == partnerId }?.let { return openThread(it) }
        val friend = s.friends.firstOrNull { it.profile.id == partnerId && it.state == FriendState.FRIENDS } ?: return
        act {
            val thread = friend.threadId ?: backend.friendThread(partnerId)
            openThread(RecentMatch(thread, friend.profile, 0))
        }
    }

    private fun openThread(r: RecentMatch) {
        _state.update { it.copy(dm = DmState(r), viewing = null) }
        dmJob?.cancel()
        dmJob = viewModelScope.launch {
            runCatching { backend.history(r.matchId) }.onSuccess { h -> updateDm(r.matchId) { merge(it, h) } }
            runCatching {
                backend.events(r.matchId).collect { e -> if (e is MatchEvent.NewMessage) updateDm(r.matchId) { merge(it, listOf(e.message)) } }
            }
        }
    }

    fun closeDm() {
        dmJob?.cancel()
        _state.update { it.copy(dm = null) }
        refreshRecents()
    }

    fun sendDm(text: String) {
        val t = text.trim().take(MAX_TEXT)
        val dm = _state.value.dm ?: return
        if (t.isEmpty()) return
        act { backend.send(dm.recent.matchId, t, null).also { m -> updateDm(dm.recent.matchId) { merge(it, listOf(m)) } } }
    }

    private fun updateDm(matchId: String, change: (List<Message>) -> List<Message>) = _state.update {
        val dm = it.dm
        if (dm == null || dm.recent.matchId != matchId) it else it.copy(dm = dm.copy(messages = change(dm.messages)))
    }

    fun signOut() = act {
        searchGeneration++
        _state.update { it.copy(searching = false) }
        matchJob?.join() // a match request in flight finishes first (it may hand back a match to end)
        abandonJobs.toList().forEach { it.join() } // end those matches while still signed in
        backend.signOut()
        resetToStart()
    }

    /** Irreversible; the confirmation dialog is in the settings screen. */
    fun deleteAccount() = act {
        backend.deleteAccount()
        resetToStart()
        notice(UiText(R.string.msg_deleted))
    }

    private fun resetToStart() {
        matchJob?.cancel()
        chatJob?.cancel()
        dmJob?.cancel()
        roomJob?.cancel()
        _state.value = UiState(booting = false)
    }

    fun addPost(text: String, imageUri: String?) {
        val t = text.trim().take(500)
        if (t.isEmpty() && imageUri == null) return
        act {
            val post = backend.addPost(t, imageUri)
            _state.update { s -> s.copy(me = s.me?.let { it.copy(posts = listOf(post) + it.posts) }) }
        }
    }

    /** Edit my profile. Gender and birth date can't change (the server refuses too). */
    fun saveProfile(name: String, city: String, bio: String, newPhotoUri: String?, done: () -> Unit) {
        val n = name.trim()
        val error = when {
            n.isEmpty() -> UiText(R.string.err_name)
            n.length > Validation.MAX_NAME -> UiText(R.string.err_name_long)
            bio.length > Validation.MAX_BIO -> UiText(R.string.err_bio_long)
            else -> null
        }
        if (error != null) return notice(error)
        act {
            val me = backend.updateProfile(n, city.trim().take(30), bio.trim(), newPhotoUri)
            _state.update { it.copy(me = me) }
            done()
        }
    }

    // ---- Friends ----------------------------------------------------------------------------

    private suspend fun refreshFriends() {
        runCatching { backend.friends() }.onSuccess { list -> _state.update { it.copy(friends = list) } }
    }

    /** Add friend, or accept their request. */
    fun addFriend(p: Profile) = act {
        val state = backend.requestFriend(p.id)
        _state.update { s ->
            val others = s.friends.filterNot { it.profile.id == p.id }
            val old = s.friends.firstOrNull { it.profile.id == p.id }
            s.copy(friends = listOf(Friend(p, state, old?.threadId)) + others)
        }
        notice(UiText(if (state == FriendState.FRIENDS) R.string.msg_now_friends else R.string.msg_request_sent, p.name))
    }

    fun answerFriend(p: Profile, accept: Boolean) = act {
        backend.answerFriend(p.id, accept)
        _state.update { s ->
            s.copy(friends = s.friends.mapNotNull { f ->
                when {
                    f.profile.id != p.id -> f
                    accept -> f.copy(state = FriendState.FRIENDS)
                    else -> null
                }
            })
        }
    }

    /** Unfriend, or cancel a request I sent. */
    fun removeFriend(p: Profile) = act {
        backend.removeFriend(p.id)
        _state.update { s -> s.copy(friends = s.friends.filterNot { it.profile.id == p.id }) }
    }

    // ---- Public rooms -----------------------------------------------------------------------

    fun refreshRooms() = act {
        val list = backend.rooms()
        _state.update { it.copy(rooms = list) }
    }

    fun enterRoom(room: Room) {
        if (_state.value.room?.room?.id == room.id) return
        roomJob?.cancel()
        _state.update { it.copy(room = RoomState(room)) }
        roomJob = viewModelScope.launch {
            try {
                backend.joinRoom(room.id)
                val history = backend.roomHistory(room.id)
                updateRoom(room.id) { it.copy(messages = mergeRoom(it.messages, history), loading = false) }
                launch {
                    while (isActive) { // stay listed as present; the server forgets you 90 s after the last beat
                        delay(ROOM_BEAT_MS)
                        runCatching { backend.joinRoom(room.id) }
                    }
                }
                backend.roomEvents(room.id).collect { m -> updateRoom(room.id) { it.copy(messages = mergeRoom(it.messages, listOf(m))) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Couldn't get in (suspended, room removed, offline): back to the list instead of an empty room.
                _state.update { s -> if (s.room?.room?.id == room.id) s.copy(room = null) else s }
                notice(explain(e))
            }
        }
    }

    fun leaveRoom() {
        val r = _state.value.room ?: return
        roomJob?.cancel()
        _state.update { it.copy(room = null) }
        viewModelScope.launch { runCatching { backend.leaveRoom(r.room.id) } }
        refreshRooms()
    }

    fun sendRoom(text: String) {
        val t = text.trim().take(MAX_ROOM_TEXT)
        val r = _state.value.room ?: return
        if (t.isEmpty()) return
        act {
            val m = try {
                backend.sendRoom(r.room.id, t)
            } catch (e: BackendException) {
                // Presence lapsed (phone asleep, network gap > 90 s): the server refuses until we rejoin. Rejoin once, retry.
                if (e.code != "blocked") throw e
                backend.joinRoom(r.room.id)
                backend.sendRoom(r.room.id, t)
            }
            updateRoom(r.room.id) { it.copy(messages = mergeRoom(it.messages, listOf(m))) }
        }
    }

    private fun updateRoom(roomId: String, change: (RoomState) -> RoomState) = _state.update {
        val r = it.room
        if (r == null || r.room.id != roomId) it else it.copy(room = change(r))
    }

    /** Keeps the newest [MAX_ROOM_MESSAGES]; blocked senders never come back through a re-sync. */
    private fun mergeRoom(old: List<RoomMessage>, new: List<RoomMessage>): List<RoomMessage> {
        val ids = old.mapTo(HashSet()) { it.id }
        val added = new.filter { it.id !in ids }
        if (added.isEmpty()) return old
        return (old + added).sortedBy { it.id }.takeLast(MAX_ROOM_MESSAGES)
    }

    companion object {
        const val POLL_MS = 2_000L
        const val ROOM_BEAT_MS = 30_000L
        const val MAX_ROOM_TEXT = 500
        const val MAX_ROOM_MESSAGES = 300
        const val MAX_TEXT = 1000
        const val TYPING_EVERY_MS = 3_000L
        const val RECONNECT_MS = 3_000L
    }
}
