package com.yaari.chat.ui

import android.app.Activity
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.Billing
import com.yaari.chat.data.Entitlements
import com.yaari.chat.data.Match
import com.yaari.chat.data.MatchEvent
import com.yaari.chat.data.NewProfile
import com.yaari.chat.data.VideoStatus
import com.yaari.chat.data.VideoToken
import com.yaari.chat.domain.ExitPolicy
import com.yaari.chat.domain.Validation
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Media
import com.yaari.chat.model.Message
import com.yaari.chat.model.Profile
import com.yaari.chat.model.RecentMatch
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.Target
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Tab { CHAT, RECENT, ME }

data class Onboarding(val step: Step = Step.PHONE, val phone: String = "", val busy: Boolean = false, val error: String? = null) {
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

data class UiState(
    val booting: Boolean = true,
    val onboarding: Onboarding = Onboarding(),
    val me: Profile? = null,
    val entitlements: Entitlements = Entitlements(),
    val prices: Map<String, String> = emptyMap(),
    val tab: Tab = Tab.CHAT,
    val target: Target = Target.ANY,
    val searching: Boolean = false,
    val chat: ChatState? = null,
    val viewing: Profile? = null,
    val dm: DmState? = null,
    val recents: List<RecentMatch> = emptyList(),
    val paywall: String? = null,
    val notice: String? = null,
) {
    val premium get() = entitlements.premium
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
        refreshEntitlements()
        runCatching { billing.prices() }.onSuccess { p -> _state.update { it.copy(prices = p) } }
        // Purchases made while offline or on another install are re-verified by the server.
        runCatching { billing.ownedPurchases() }.getOrDefault(emptyList()).forEach { (product, token) ->
            runCatching { applyEntitlements(backend.redeem(product, token)) }
        }
    }

    private suspend fun refreshEntitlements() {
        runCatching { backend.entitlements() }.onSuccess(::applyEntitlements)
    }

    private fun applyEntitlements(e: Entitlements) = _state.update {
        it.copy(entitlements = e, target = if (e.canChooseGender) it.target else Target.ANY)
    }

    private fun notice(text: String) = _state.update { it.copy(notice = text) }

    private fun explain(e: Throwable): String = when ((e as? BackendException)?.code) {
        "exit_locked" -> "Wait a moment before leaving"
        "premium_required" -> "This needs Premium"
        "banned" -> "Your account is suspended after reports. Contact support."
        "under_18" -> "Yaari is only for adults (18+)"
        "slow_down" -> "You're sending too fast"
        "blocked" -> "You can't message this person"
        "not_allowed" -> "That's not possible right now"
        "video_not_configured" -> "Video calls are not available yet"
        else -> "Network problem. Please try again."
    }

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

    fun requestOtp(input: String) {
        val phone = Validation.indianMobile(input)
        if (phone == null) {
            _state.update { it.copy(onboarding = it.onboarding.copy(error = "Enter a valid 10-digit Indian mobile number")) }
            return
        }
        _state.update { it.copy(onboarding = Onboarding(Onboarding.Step.PHONE, phone, busy = true)) }
        viewModelScope.launch {
            val ok = runCatching { backend.sendOtp(phone) }.isSuccess
            _state.update {
                it.copy(onboarding = if (ok) it.onboarding.copy(step = Onboarding.Step.OTP, busy = false)
                else it.onboarding.copy(busy = false, error = "Couldn't send the code. Try again."))
            }
        }
    }

    fun verifyOtp(code: String) {
        val phone = _state.value.onboarding.phone
        _state.update { it.copy(onboarding = it.onboarding.copy(busy = true, error = null)) }
        viewModelScope.launch {
            val ok = runCatching { backend.verifyOtp(phone, code.trim()) }.getOrDefault(false)
            if (!ok) {
                _state.update { it.copy(onboarding = it.onboarding.copy(busy = false, error = "Wrong code. Try again.")) }
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
            name.isEmpty() -> "Enter your name"
            name.length > Validation.MAX_NAME -> "Name is too long"
            d.gender == null -> "Select your gender"
            birth == null || age == null || age > Validation.MAX_AGE -> "Enter a valid date of birth"
            age < Validation.MIN_AGE -> "Yaari is only for adults (18+)"
            d.bio.length > Validation.MAX_BIO -> "Bio is too long"
            !d.agreed -> "Please accept the community rules"
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
        if (tab == Tab.RECENT) refreshRecents()
    }

    fun setTarget(target: Target) {
        if (target.isPaid && !_state.value.entitlements.canChooseGender) {
            showPaywall("Choose who you meet")
            return
        }
        _state.update { it.copy(target = target) }
    }

    fun showPaywall(feature: String) = _state.update { it.copy(paywall = feature) }

    fun dismissPaywall() = _state.update { it.copy(paywall = null) }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    /** Google Play purchase, then server-side verification. The entitlement only comes from the server. */
    fun buy(activity: Activity, productId: String) {
        val me = _state.value.me ?: return
        act {
            val token = billing.buy(activity, productId, me.id) ?: return@act
            applyEntitlements(backend.redeem(productId, token))
            billing.finish(productId, token)
            _state.update { it.copy(paywall = null, notice = "Purchase complete. Enjoy!") }
        }
    }

    // ---- Matching -----------------------------------------------------------------------------

    fun startMatching() {
        val s = _state.value
        if (s.me == null || s.searching || s.chat != null) return
        _state.update { it.copy(searching = true, dm = null, viewing = null) }
        dmJob?.cancel()
        matchJob = viewModelScope.launch {
            try {
                while (isActive) {
                    val m = backend.findMatch(s.target)
                    if (m != null) {
                        openChat(m)
                        return@launch
                    }
                    delay(POLL_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(searching = false) }
                notice(explain(e))
            }
        }
    }

    fun cancelMatching() {
        matchJob?.cancel()
        _state.update { it.copy(searching = false) }
        act { backend.cancelSearch() }
    }

    private fun openChat(m: Match) {
        _state.update { it.copy(searching = false, chat = ChatState(m, clock())) }
        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            runCatching { backend.history(m.id) }.onSuccess { h -> updateChat(m.id) { c -> c.copy(messages = merge(c.messages, h)) } }
            try {
                backend.events(m.id).collect { e -> applyChatEvent(m.id, e) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice("Connection lost. Reconnecting may need a new match.")
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
                    notice("${c.match.partner.name} declined the video call")
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
            backend.leave(c.match.id)
            closeChat()
            if (next) startMatching()
        }
    }

    /** Safety exit: never locked. Blocks the partner and removes them from Recent. */
    fun reportAndLeave(reason: ReportReason, next: Boolean) {
        val c = _state.value.chat ?: return
        act {
            backend.report(c.match.partner.id, reason, c.match.id)
            closeChat()
            notice("Reported and blocked. You won't be matched again.")
            if (next) startMatching()
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
            showPaywall("Video calls")
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
                    viewing = s.viewing?.takeIf { it.id != partnerId },
                    dm = s.dm?.takeIf { it.recent.partner.id != partnerId },
                    notice = "Reported and blocked.",
                )
            }
        }
    }

    fun refreshRecents() = act {
        val list = backend.recents()
        _state.update { it.copy(recents = list) }
    }

    fun openDm(partnerId: String) {
        val r = _state.value.recents.firstOrNull { it.partner.id == partnerId } ?: return
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

    fun addPost(text: String, imageUri: String?) {
        val t = text.trim().take(500)
        if (t.isEmpty() && imageUri == null) return
        act {
            val post = backend.addPost(t, imageUri)
            _state.update { s -> s.copy(me = s.me?.let { it.copy(posts = listOf(post) + it.posts) }) }
        }
    }

    companion object {
        const val POLL_MS = 2_000L
        const val MAX_TEXT = 1000
    }
}
