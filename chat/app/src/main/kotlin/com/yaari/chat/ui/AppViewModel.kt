package com.yaari.chat.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaari.chat.data.Backend
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.PartnerEvent
import com.yaari.chat.domain.ExitPolicy
import com.yaari.chat.domain.Validation
import com.yaari.chat.model.ChatSession
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Media
import com.yaari.chat.model.Message
import com.yaari.chat.model.Post
import com.yaari.chat.model.Profile
import com.yaari.chat.model.RecentMatch
import com.yaari.chat.model.Target
import com.yaari.chat.model.VideoState
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

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

data class UiState(
    val onboarding: Onboarding = Onboarding(),
    val me: Profile? = null,
    val premium: Boolean = false,
    val tab: Tab = Tab.CHAT,
    val target: Target = Target.ANY,
    val searching: Boolean = false,
    val session: ChatSession? = null,
    val viewing: Profile? = null,
    val dmPartnerId: String? = null,
    val recents: List<RecentMatch> = emptyList(),
    val blocked: Set<String> = emptySet(),
    val paywall: String? = null,
    val notice: String? = null,
)

class AppViewModel(
    private val backend: Backend = FakeBackend(),
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var nextId = 0L
    private var matchJob: Job? = null
    private var lastPartnerId: String? = null

    /** Parent of every coroutine tied to the current chat, so leaving cancels late replies. */
    private var sessionJob = newSessionJob()

    private fun newSessionJob() = SupervisorJob(viewModelScope.coroutineContext[Job])

    private fun message(fromMe: Boolean, text: String? = null, media: Media? = null) =
        Message(nextId++, fromMe, text, media, wallClock())

    // ---- Onboarding -------------------------------------------------------------------------

    fun requestOtp(input: String) {
        val phone = Validation.indianMobile(input)
        if (phone == null) {
            _state.update { it.copy(onboarding = it.onboarding.copy(error = "Enter a valid 10-digit Indian mobile number")) }
            return
        }
        _state.update { it.copy(onboarding = Onboarding(Onboarding.Step.PHONE, phone, busy = true)) }
        viewModelScope.launch {
            backend.sendOtp(phone)
            _state.update { it.copy(onboarding = it.onboarding.copy(step = Onboarding.Step.OTP, busy = false)) }
        }
    }

    fun verifyOtp(code: String) {
        val phone = _state.value.onboarding.phone
        _state.update { it.copy(onboarding = it.onboarding.copy(busy = true, error = null)) }
        viewModelScope.launch {
            if (!backend.verifyOtp(phone, code.trim())) {
                _state.update { it.copy(onboarding = it.onboarding.copy(busy = false, error = "Wrong code. Try again.")) }
                return@launch
            }
            val existing = backend.accountFor(phone)
            _state.update {
                if (existing != null) it.copy(me = existing, onboarding = Onboarding())
                else it.copy(onboarding = it.onboarding.copy(step = Onboarding.Step.PROFILE, busy = false))
            }
        }
    }

    fun backToPhone() = _state.update { it.copy(onboarding = Onboarding()) }

    /** Returns an error message, or null after the account is created. */
    fun createProfile(d: ProfileDraft): String? {
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
        if (error != null) return error
        val phone = _state.value.onboarding.phone
        val me = Profile(
            id = "me-" + UUID.randomUUID(), name = name, gender = d.gender!!, age = age!!,
            city = d.city.trim().ifEmpty { "India" }, bio = d.bio.trim(), photoUri = d.photoUri, colorSeed = phone.hashCode(),
        )
        try {
            backend.register(phone, me)
        } catch (e: IllegalStateException) {
            return e.message
        }
        _state.update { it.copy(me = me, onboarding = Onboarding()) }
        return null
    }

    // ---- Lobby ------------------------------------------------------------------------------

    fun selectTab(tab: Tab) = _state.update { it.copy(tab = tab) }

    fun setTarget(target: Target) {
        if (target.isPaid && !_state.value.premium) {
            _state.update { it.copy(paywall = "Choose who you meet") }
            return
        }
        _state.update { it.copy(target = target) }
    }

    /** Prototype stand-in for Google Play Billing. */
    fun setPremium(on: Boolean) = _state.update {
        it.copy(premium = on, paywall = null, target = if (on) it.target else Target.ANY)
    }

    fun showPaywall(feature: String) = _state.update { it.copy(paywall = feature) }

    fun dismissPaywall() = _state.update { it.copy(paywall = null) }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    fun startMatching() {
        val s = _state.value
        val me = s.me ?: return
        if (s.searching || s.session != null) return
        _state.update { it.copy(searching = true, dmPartnerId = null, viewing = null) }
        matchJob = viewModelScope.launch {
            val exclude = s.blocked + listOfNotNull(lastPartnerId)
            val partner = backend.findPartner(me, s.target, exclude)
            _state.update {
                if (partner == null) it.copy(searching = false, notice = "No one is online for \"${s.target.label}\" right now")
                else it.copy(searching = false, session = ChatSession(partner, clock()))
            }
        }
    }

    fun cancelMatching() {
        matchJob?.cancel()
        _state.update { it.copy(searching = false) }
    }

    // ---- Chat -------------------------------------------------------------------------------

    fun exitRemainingMs(): Long {
        val s = _state.value
        val session = s.session ?: return 0
        if (session.partnerLeft) return 0
        return ExitPolicy.remainingMs(s.premium, session.startedAtMs, clock())
    }

    fun sendText(text: String) {
        val t = text.trim()
        if (t.isNotEmpty()) send(message(true, text = t.take(1000)))
    }

    fun sendMedia(media: Media) = send(message(true, media = media))

    private fun send(msg: Message) {
        val session = _state.value.session ?: return
        if (session.partnerLeft) return
        updateSession(session.partner.id) { it.copy(messages = it.messages + msg) }
        viewModelScope.launch(sessionJob) {
            updateSession(session.partner.id) { it.copy(partnerTyping = true) }
            val event = backend.partnerReact(session.partner, msg.text, canLeave = true)
            updateSession(session.partner.id) {
                when (event) {
                    is PartnerEvent.Reply -> it.copy(partnerTyping = false, messages = it.messages + message(false, event.text))
                    PartnerEvent.Left -> it.copy(partnerTyping = false, partnerLeft = true, video = VideoState.NONE)
                    PartnerEvent.Silent -> it.copy(partnerTyping = false)
                }
            }
        }
    }

    /** Applies [change] only if the chat with [partnerId] is still the open one. */
    private fun updateSession(partnerId: String, change: (ChatSession) -> ChatSession) = _state.update {
        val s = it.session
        if (s == null || s.partner.id != partnerId) it else it.copy(session = change(s))
    }

    /** Normal exit (X / back). Ignored while the exit lock is running. */
    fun leave(next: Boolean) {
        if (_state.value.session == null || exitRemainingMs() > 0) return
        endSession(keepInRecents = true)
        if (next) startMatching()
    }

    /** Safety exit: never locked, blocks the partner and drops them from recents. */
    fun reportAndLeave(reason: String, next: Boolean) {
        val partner = _state.value.session?.partner ?: return
        report(partner.id, reason)
        endSession(keepInRecents = false)
        if (next) startMatching()
    }

    private fun endSession(keepInRecents: Boolean) {
        val session = _state.value.session ?: return
        sessionJob.cancel()
        sessionJob = newSessionJob()
        lastPartnerId = session.partner.id
        _state.update { s ->
            val old = s.recents.firstOrNull { it.partner.id == session.partner.id }
            val recents = if (!keepInRecents) s.recents
            else listOf(RecentMatch(session.partner, wallClock(), old?.dm.orEmpty())) +
                s.recents.filterNot { it.partner.id == session.partner.id }
            s.copy(session = null, viewing = null, recents = recents.take(MAX_RECENTS))
        }
    }

    fun requestVideo() {
        val s = _state.value
        val session = s.session ?: return
        if (!s.premium) {
            _state.update { it.copy(paywall = "Video calls") }
            return
        }
        if (session.partnerLeft || session.video != VideoState.NONE) return
        updateSession(session.partner.id) { it.copy(video = VideoState.REQUESTING) }
        viewModelScope.launch(sessionJob) {
            val accepted = backend.answerVideoRequest(session.partner)
            updateSession(session.partner.id) { it.copy(video = if (accepted) VideoState.IN_CALL else VideoState.NONE) }
            if (!accepted) _state.update { it.copy(notice = "${session.partner.name} declined the video call") }
        }
    }

    fun endVideo() {
        val id = _state.value.session?.partner?.id ?: return
        updateSession(id) { it.copy(video = VideoState.NONE) }
    }

    // ---- Profiles, recents, DMs -------------------------------------------------------------

    fun openProfile(p: Profile) = _state.update { it.copy(viewing = p) }

    fun closeProfile() = _state.update { it.copy(viewing = null) }

    /** Blocks and removes the user everywhere. In v1 this also files a moderation ticket. */
    fun report(partnerId: String, @Suppress("UNUSED_PARAMETER") reason: String) = _state.update { s ->
        s.copy(
            blocked = s.blocked + partnerId,
            recents = s.recents.filterNot { it.partner.id == partnerId },
            viewing = s.viewing?.takeIf { it.id != partnerId },
            dmPartnerId = s.dmPartnerId?.takeIf { it != partnerId },
            notice = "Reported and blocked. You won't be matched again.",
        )
    }

    fun openDm(partnerId: String) = _state.update { it.copy(dmPartnerId = partnerId, viewing = null) }

    fun closeDm() = _state.update { it.copy(dmPartnerId = null) }

    fun sendDm(text: String) {
        val t = text.trim()
        val id = _state.value.dmPartnerId ?: return
        if (t.isEmpty()) return
        updateDm(id) { it + message(true, text = t.take(1000)) }
        val partner = _state.value.recents.firstOrNull { it.partner.id == id }?.partner ?: return
        viewModelScope.launch {
            val event = backend.partnerReact(partner, t, canLeave = false)
            if (event is PartnerEvent.Reply) updateDm(id) { it + message(false, event.text) }
        }
    }

    private fun updateDm(partnerId: String, change: (List<Message>) -> List<Message>) = _state.update { s ->
        s.copy(recents = s.recents.map { if (it.partner.id == partnerId) it.copy(dm = change(it.dm)) else it })
    }

    fun addPost(text: String, imageUri: String?) {
        val t = text.trim()
        if (t.isEmpty() && imageUri == null) return
        _state.update { s ->
            val me = s.me ?: return@update s
            val post = Post(UUID.randomUUID().toString(), t.take(500), imageUri, wallClock())
            s.copy(me = me.copy(posts = listOf(post) + me.posts))
        }
    }

    companion object {
        const val MAX_RECENTS = 50
    }
}
