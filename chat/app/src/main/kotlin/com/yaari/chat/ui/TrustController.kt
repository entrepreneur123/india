package com.yaari.chat.ui

import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.FirstRun
import com.yaari.chat.model.KycResult
import com.yaari.chat.model.MannerTag
import com.yaari.chat.model.MyTrust
import com.yaari.chat.model.Profile
import com.yaari.chat.model.Trust
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the identity check stands on the "Verify your identity" screen. */
enum class VerifyPhase {
    /** Not verified: the explanation and Start. */
    START,
    /** Asking the server for the provider's page. */
    OPENING,
    /** The provider's page is open (browser); checked when the app comes back. */
    WAITING,
    CHECKING,
    /** The provider is still deciding (a person may review the document). */
    PENDING,
    VERIFIED, DECLINED, UNREADABLE, DUPLICATE, MINOR,
    /** No identity provider yet (kyc_unavailable): "coming soon". */
    UNAVAILABLE,
    /** 5 tries a day (slow_down). */
    LIMIT,
    /** Couldn't reach the server or the provider (network, kyc_failed). */
    ERROR,
}

/** Why the screen opened: from my profile, after sign-up (skippable unless required), or a refusal (verify_required). */
enum class VerifyOrigin { PROFILE, ONBOARDING, REQUIRED }

data class VerifyUi(val phase: VerifyPhase, val origin: VerifyOrigin, val required: Boolean)

/** The manners sheet after a random chat: how it felt, and up to three tags. */
data class RateUi(val matchId: String, val partner: Profile, val good: Boolean? = null, val tags: Set<MannerTag> = emptySet(), val busy: Boolean = false)

data class TrustState(
    /** My own badges; null until loaded (or when it can't be read). */
    val mine: MyTrust? = null,
    /** The verify screen, when open. */
    val verify: VerifyUi? = null,
    /** The provider's page to open now (the screen opens it, then calls [TrustController.opened]). */
    val launch: String? = null,
    val rating: RateUi? = null,
    /** find_match2 said manner_low: the sheet that explains why random chat is closed. */
    val mannerLow: Boolean = false,
    /** The provider isn't set up yet (seen once on this phone): the screens stop inviting so loudly. */
    val comingSoon: Boolean = false,
)

/**
 * Real identities and manners (035): my badges and the identity check, everyone else's badges (one batched trust()
 * lookup for whatever is on screen), the manners sheet after a random chat and the manner_low sheet. Owned by
 * [AppViewModel]; plain coroutines on its scope.
 */
class TrustController(
    private val backend: Backend,
    private val scope: CoroutineScope,
    private val firstRun: FirstRun,
    /** My profile again (gender, birth date and country now come from my ID). */
    private val onVerified: () -> Unit,
    private val notice: (UiText) -> Unit,
) {
    private val _state = MutableStateFlow(TrustState(comingSoon = firstRun.done(FirstRun.Step.KYC_SOON) && firstRun !is FirstRun.Seen))
    val state: StateFlow<TrustState> = _state.asStateFlow()

    /** Badges of the people shown so far (by id). Separate from [state] so lists only follow what they show. */
    private val _people = MutableStateFlow<Map<String, Trust>>(emptyMap())
    val people: StateFlow<Map<String, Trust>> = _people.asStateFlow()

    private val loadedAt = mutableMapOf<String, Long>()
    private val wanted = linkedSetOf<String>()
    private var batchJob: Job? = null
    private var mineJob: Job? = null
    /** Chats rated or skipped on this phone (the server's list covers the rated ones of the last day). */
    private val asked = mutableSetOf<String>()

    fun reset() {
        batchJob?.cancel()
        mineJob?.cancel()
        wanted.clear()
        loadedAt.clear()
        asked.clear()
        _people.value = emptyMap()
        _state.value = TrustState(comingSoon = _state.value.comingSoon)
    }

    // ---- my badges ----

    /** Signed in, back in the foreground, after a rating changed something. Failures keep what was shown. */
    fun refreshMine(then: (MyTrust) -> Unit = {}) {
        mineJob?.cancel()
        mineJob = scope.launch {
            runCatching { backend.myTrust() }.onSuccess { m ->
                _state.update { it.copy(mine = m) }
                _people.update { it + (m.trust.id to m.trust) }
                then(m)
            }
        }
    }

    /** After sign-up: the identity check once on this phone, or until done when the server requires it. */
    fun afterSignUp() = refreshMine { m ->
        if (m.verified) return@refreshMine
        if (m.required || (!firstRun.done(FirstRun.Step.VERIFY) && !_state.value.comingSoon)) {
            firstRun.mark(FirstRun.Step.VERIFY)
            open(VerifyOrigin.ONBOARDING)
        }
    }

    val verified get() = _state.value.mine?.verified == true

    // ---- the verify screen ----

    fun open(origin: VerifyOrigin = VerifyOrigin.PROFILE) {
        val mine = _state.value.mine
        val phase = when {
            mine?.verified == true -> VerifyPhase.VERIFIED
            else -> when (mine?.kyc?.result) {
                KycResult.PENDING -> VerifyPhase.PENDING
                KycResult.DECLINED -> VerifyPhase.DECLINED
                KycResult.UNREADABLE -> VerifyPhase.UNREADABLE
                KycResult.DUPLICATE -> VerifyPhase.DUPLICATE
                KycResult.MINOR -> VerifyPhase.MINOR
                else -> VerifyPhase.START
            }
        }
        _state.update { it.copy(verify = VerifyUi(phase, origin, mine?.required == true || origin == VerifyOrigin.REQUIRED)) }
        if (mine == null) refreshMine { m -> if (m.verified) setPhase(VerifyPhase.VERIFIED) }
    }

    /** verify_required from the server: the screen, not a toast. */
    fun required() {
        if (_state.value.verify == null) open(VerifyOrigin.REQUIRED)
    }

    /** Skippable unless the server requires verification and the check can actually be done now. */
    fun canClose(v: VerifyUi) = !(v.required && v.origin == VerifyOrigin.ONBOARDING) ||
        v.phase in setOf(VerifyPhase.UNAVAILABLE, VerifyPhase.VERIFIED, VerifyPhase.DUPLICATE, VerifyPhase.MINOR, VerifyPhase.LIMIT)

    fun close() = _state.update { it.copy(verify = null, launch = null) }

    private fun setPhase(p: VerifyPhase) = _state.update { s -> s.copy(verify = s.verify?.copy(phase = p)) }

    /** Start (or try again): the provider's page opens in a Custom Tab; the result is read when the app returns. */
    fun start() {
        val v = _state.value.verify ?: return
        if (v.phase in setOf(VerifyPhase.OPENING, VerifyPhase.CHECKING)) return
        setPhase(VerifyPhase.OPENING)
        scope.launch {
            try {
                val url = backend.kycStart()
                _state.update { s -> s.copy(verify = s.verify?.copy(phase = VerifyPhase.WAITING), launch = url) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                when ((e as? BackendException)?.code) {
                    "already_verified" -> {
                        setPhase(VerifyPhase.VERIFIED)
                        refreshMine()
                    }
                    "kyc_unavailable" -> {
                        firstRun.mark(FirstRun.Step.KYC_SOON)
                        _state.update { s -> s.copy(comingSoon = true, verify = s.verify?.copy(phase = VerifyPhase.UNAVAILABLE)) }
                    }
                    "slow_down" -> setPhase(VerifyPhase.LIMIT)
                    else -> setPhase(VerifyPhase.ERROR)
                }
            }
        }
    }

    /** The screen opened [launch] (or couldn't: then the check runs at once, which says "pending" or "none"). */
    fun opened() = _state.update { it.copy(launch = null) }

    /** Back in the app while the provider's page was open, or "Check again". */
    fun resumed() {
        val v = _state.value.verify
        if (v != null && v.phase in setOf(VerifyPhase.WAITING, VerifyPhase.PENDING) && _state.value.launch == null) check()
        else if (_state.value.mine != null) refreshMine()
    }

    fun check() {
        if (_state.value.verify?.phase == VerifyPhase.CHECKING) return
        setPhase(VerifyPhase.CHECKING)
        scope.launch {
            val phase = try {
                when (backend.kycCheck()) {
                    KycResult.VERIFIED -> VerifyPhase.VERIFIED
                    KycResult.PENDING -> VerifyPhase.PENDING
                    KycResult.DECLINED -> VerifyPhase.DECLINED
                    KycResult.UNREADABLE -> VerifyPhase.UNREADABLE
                    KycResult.DUPLICATE -> VerifyPhase.DUPLICATE
                    KycResult.MINOR -> VerifyPhase.MINOR
                    KycResult.NONE -> VerifyPhase.START
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                when ((e as? BackendException)?.code) {
                    "kyc_unavailable" -> VerifyPhase.UNAVAILABLE
                    else -> VerifyPhase.PENDING // the next check (or the provider's webhook) decides
                }
            }
            setPhase(phase)
            if (phase == VerifyPhase.VERIFIED) {
                refreshMine()
                onVerified()
            }
        }
    }

    // ---- everyone else's badges ----

    /**
     * Badges for these people: one trust() request for everything asked within [BATCH_MS] (a room's seats, a list),
     * at most 100 at a time; known ones are asked again after [STALE_MS]. Failures are asked again next time.
     */
    fun want(ids: Collection<String>, now: Long = System.currentTimeMillis()) {
        val fresh = ids.filter { id -> id.isNotBlank() && (loadedAt[id]?.let { now - it > STALE_MS } ?: true) && id !in wanted }
        if (fresh.isEmpty()) return
        wanted += fresh
        if (batchJob?.isActive == true) return
        batchJob = scope.launch {
            delay(BATCH_MS)
            while (wanted.isNotEmpty()) {
                val batch = wanted.take(100)
                val got = runCatching { backend.trust(batch) }.getOrNull()
                val at = System.currentTimeMillis()
                wanted.removeAll(batch.toSet())
                if (got != null) {
                    batch.forEach { loadedAt[it] = at }
                    _people.update { m -> m + got.associateBy { it.id } }
                }
            }
        }
    }

    /** What a feed card already knows (post and comment authors carry the badge). */
    fun known(p: Profile) {
        if (p.verified && _people.value[p.id]?.verified != true) _people.update { it + (p.id to (it[p.id]?.copy(verified = true) ?: Trust(p.id, verified = true))) }
    }

    // ---- manners ----

    /**
     * A random chat ended (I left it, or it ended and I closed it): ask how it was, when both of us wrote and it wasn't
     * rated (or skipped) before.
     */
    fun chatEnded(c: ChatState) {
        val id = c.match.id
        if (id in asked || _state.value.mine?.rated?.contains(id) == true) return
        if (c.messages.none { it.fromMe } || c.messages.none { !it.fromMe }) return
        asked += id
        _state.update { it.copy(rating = RateUi(id, c.match.partner)) }
    }

    fun rateGood(good: Boolean) = _state.update { s ->
        s.copy(rating = s.rating?.let { r -> if (r.good == good) r else r.copy(good = good, tags = emptySet()) })
    }

    fun toggleTag(tag: MannerTag) = _state.update { s ->
        s.copy(rating = s.rating?.let { r ->
            when {
                r.good != tag.good -> r
                tag in r.tags -> r.copy(tags = r.tags - tag)
                r.tags.size >= MannerTag.MAX -> r
                else -> r.copy(tags = r.tags + tag)
            }
        })
    }

    fun skipRating() = _state.update { it.copy(rating = null) }

    fun submitRating() {
        val r = _state.value.rating ?: return
        val good = r.good ?: return
        if (r.busy) return
        _state.update { it.copy(rating = r.copy(busy = true)) }
        scope.launch {
            try {
                backend.rateChat(r.matchId, good, r.tags.toList())
                notice(UiText(com.yaari.chat.R.string.rate_thanks))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Already rated, too late or not allowed: nothing to fix; offline: says so.
                if ((e as? BackendException)?.code !in setOf("already_rated", "too_late", "not_allowed", "not_found")) {
                    notice(UiText(com.yaari.chat.R.string.err_network))
                }
            }
            _state.update { it.copy(rating = null) }
            loadedAt.remove(r.partner.id)
            refreshMine()
        }
    }

    fun showMannerLow() {
        _state.update { it.copy(mannerLow = true) }
        refreshMine()
    }

    fun dismissMannerLow() = _state.update { it.copy(mannerLow = false) }

    companion object {
        const val BATCH_MS = 120L
        const val STALE_MS = 10 * 60_000L
    }
}
