package com.yaari.chat.ui

import android.app.Activity
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaari.chat.R
import com.yaari.chat.data.AppForeground
import com.yaari.chat.data.Backend
import com.yaari.chat.data.awaitVisible
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.Billing
import com.yaari.chat.data.Entitlements
import com.yaari.chat.data.Match
import com.yaari.chat.data.MatchEvent
import com.yaari.chat.data.NewProfile
import com.yaari.chat.data.OrphanMatchException
import com.yaari.chat.data.PhotoCheck
import com.yaari.chat.data.Products
import com.yaari.chat.data.Funnel
import com.yaari.chat.data.VideoStatus
import com.yaari.chat.data.VideoToken
import com.yaari.chat.data.VoiceBus
import com.yaari.chat.data.DeepLink
import com.yaari.chat.data.FirstRun
import com.yaari.chat.data.LinkBus
import com.yaari.chat.data.Push
import com.yaari.chat.data.RoomSounds
import com.yaari.chat.data.TranslatePrefs
import com.yaari.chat.data.MatchPrefs
import com.yaari.chat.model.MANNERS
import com.yaari.chat.model.InboxItem
import com.yaari.chat.domain.Countries
import com.yaari.chat.domain.Country
import com.yaari.chat.domain.ExitPolicy
import com.yaari.chat.domain.Handle
import com.yaari.chat.domain.Validation
import com.yaari.chat.model.Friend
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Cosmetic
import com.yaari.chat.model.Media
import com.yaari.chat.model.Message
import com.yaari.chat.model.PersonRow
import com.yaari.chat.model.Profile
import com.yaari.chat.model.RecentMatch
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.Target
import com.yaari.chat.model.Post
import com.yaari.chat.model.PostVisibility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

enum class Tab { ROOMS, RANDOM, RECENT, ME }

/** Why the Premium sheet opened; [reason] is shown above the offer. */
enum class Paywall(@StringRes val reason: Int?) {
    GENERAL(null),
    GENDER(R.string.premium_needed_gender),
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
    /** The ID asked for (empty: keep the free one the server gives). */
    val handle: String = "",
)

data class ChatState(
    val match: Match,
    /** Monotonic clock (elapsedRealtime) when the match opened here; the server's own clock starts earlier. */
    val startedAtMs: Long,
    val messages: List<Message> = emptyList(),
    val partnerTyping: Boolean = false,
    val partnerLeft: Boolean = false,
    val video: VideoStatus = VideoStatus.NONE,
    /** The call asked for (or on) is a video call; false: voice only, the camera off until turned on. */
    val callVideo: Boolean = true,
    /** The partner has read my messages up to this id (double blue tick). */
    val partnerRead: Long = 0,
)

/**
 * [partnerTyping]: their "typing" signal, for 4 s after the last one (the backend's events say when it stops). [video],
 * [callVideo]: a call in the thread, as in [ChatState].
 */
data class DmState(
    val recent: RecentMatch, val messages: List<Message> = emptyList(), val partnerRead: Long = 0, val partnerTyping: Boolean = false,
    val video: VideoStatus = VideoStatus.NONE, val callVideo: Boolean = true,
)

/** A call that was accepted: in [matchId] with [partner]; [video] false: it began as a voice call. */
data class Call(val matchId: String, val partner: Profile, val video: Boolean)

data class UiState(
    val booting: Boolean = true,
    val onboarding: Onboarding = Onboarding(),
    val me: Profile? = null,
    val entitlements: Entitlements = Entitlements(),
    val prices: Map<String, String> = emptyMap(),
    val tab: Tab = Tab.RANDOM,
    val target: Target = Target.ANY,
    /** The countries to meet (043, free; empty = anywhere), remembered on this phone. */
    val countries: List<String> = emptyList(),
    val searching: Boolean = false,
    /** Searching, but the last attempt failed (weak or no network): the search keeps retrying. */
    val reconnecting: Boolean = false,
    val chat: ChatState? = null,
    val viewing: Profile? = null,
    val dm: DmState? = null,
    val recents: List<RecentMatch> = emptyList(),
    val friends: List<Friend> = emptyList(),
    /** [friends] has been loaded once (until then nobody's friend state is known). */
    val friendsLoaded: Boolean = false,
    val paywall: Paywall? = null,
    val notice: UiText? = null,
    /** The random chat is open but minimised to its bar over the tabs (still on; a tap brings it back). */
    val chatMinimised: Boolean = false,
    /** Id of the partner's newest message seen before the chat was minimised (newer ones light the bar's dot). */
    val chatSeen: Long = 0,
) {
    /** The minimised chat has a message from the partner I haven't seen. */
    val chatUnread get() = chat != null && chatMinimised && chat.messages.any { !it.fromMe && it.id > chatSeen }
    val premium get() = entitlements.premium

    fun friendState(userId: String) = friends.firstOrNull { it.profile.id == userId }?.state ?: FriendState.NONE
    val incomingRequests get() = friends.count { it.state == FriendState.INCOMING }

    /**
     * The thread on screen can't go on (chat_open, 040): its chat has ended and we aren't friends. The live random chat
     * (also reachable from Recent while minimised) is always open.
     */
    val dmClosed get() = dm != null && friendsLoaded && friendState(dm.recent.partner.id) != FriendState.FRIENDS &&
        !(chat?.match?.id == dm.recent.matchId && !chat.partnerLeft)

    /** The chat on screen: a friend's thread over a minimised random chat (or with none), else the random chat. */
    val shownMatch get() = if (dm != null && (chat == null || chatMinimised)) dm.recent.matchId else chat?.match?.id

    /** The call on, once accepted: the random chat's, else the friend thread's. */
    val call get() = chat?.takeIf { it.video == VideoStatus.ACCEPTED && !it.partnerLeft }?.let { Call(it.match.id, it.match.partner, it.callVideo) }
        ?: dm?.takeIf { it.video == VideoStatus.ACCEPTED }?.let { Call(it.recent.matchId, it.recent.partner, it.callVideo) }

    /** The call's state in the chat or thread [matchId]. */
    fun callIn(matchId: String) = chat?.takeIf { it.match.id == matchId }?.video ?: dm?.takeIf { it.recent.matchId == matchId }?.video
}

class AppViewModel(
    private val backend: Backend,
    private val billing: Billing,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val today: () -> LocalDate = LocalDate::now,
    /** On screen or not: polling pauses in the background (see [AppForeground]). */
    private val foreground: StateFlow<Boolean> = AppForeground.visible,
    /** Wall clock (how long the app was in the background). */
    wallClock: () -> Long = System::currentTimeMillis,
    /** Room and profile photos: shrunk and checked for nudity on this phone (the live build's model; none in the demo). */
    photos: PhotoCheck = PhotoCheck.None,
    /** First-run steps already seen on this phone (tests: all of them, so nothing pops up). */
    firstRun: FirstRun = FirstRun.Seen,
    /** Phone push (FCM): registered after sign-in, off until Firebase is configured. */
    private val push: Push = Push.Off,
    /** Room links and tapped notifications waiting to be opened. */
    private val links: LinkBus = LinkBus.app,
    /** Chat translation settings (Settings > Translation); in memory for tests. */
    translatePrefs: TranslatePrefs = TranslatePrefs.Memory(),
    /** The countries to meet in random chat; in memory for tests. */
    private val matchPrefs: MatchPrefs = MatchPrefs.Memory(),
    /** The voice room's join and leave chime; silent in memory for tests. */
    roomSounds: RoomSounds = RoomSounds.Memory(),
) : ViewModel() {

    private val _state = MutableStateFlow(UiState(countries = matchPrefs.countries))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var matchJob: Job? = null
    private var chatJob: Job? = null
    private var dmJob: Job? = null

    /** Rooms: the list on the Rooms tab and the room I'm in (full screen or minimised to the pill). */
    val voice = VoiceController(backend, viewModelScope, ::notice, ::explain, photos, wallClock = wallClock, sounds = roomSounds)

    /** Link cards in every chat (link-meta), remembered per address while the app runs. */
    val linkCards = LinkCards(backend, viewModelScope)

    /** Chat lines in my language (edge function translate), remembered while the app runs. */
    val translator = Translator(backend, viewModelScope, ::notice, translatePrefs, clock)

    /** Notifications, unread counts, interests, photos, invites and the first run. */
    val social = SocialController(backend, viewModelScope, ::notice, ::explain, photos, firstRun, push.enabled) { me ->
        _state.update { it.copy(me = me) }
    }

    /** The feed, hearts and comments; a post's new counts reach my profile and the open one. */
    val feed = FeedController(
        backend, viewModelScope, ::notice, ::explain,
        onPost = { id, change -> _state.update { s -> s.copy(me = s.me?.patchPost(id, change), viewing = s.viewing?.patchPost(id, change)) } },
        onRemoved = { id ->
            _state.update { s ->
                s.copy(me = s.me?.let { m -> m.copy(posts = m.posts.filterNot { it.id == id }) }, viewing = s.viewing?.let { v -> v.copy(posts = v.posts.filterNot { it.id == id }) })
            }
        },
    )

    /** IDs and looks (040): everyone's decorated ID, my shop, the ID field's live check. */
    val looks = LooksController(backend, viewModelScope, ::notice, ::explain)

    /** Messenger contacts friends exchange (044). */
    val contacts = ContactsController(backend, viewModelScope, ::notice, ::explain)

    /** ID verification, everyone's badges, manners after a random chat (035). */
    val trust = TrustController(
        backend, viewModelScope, firstRun,
        onVerified = { viewModelScope.launch { runCatching { backend.myProfile() }.getOrNull()?.let { me -> _state.update { it.copy(me = me) } } } },
        notice = ::notice,
    )

    private fun Profile.patchPost(id: String, change: (Post) -> Post) =
        if (posts.none { it.id == id }) this else copy(posts = posts.map { if (it.id == id) change(it) else it })

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
        watchReads()
        followLinks()
        // Translations still on their way for a chat or room I left are dropped.
        viewModelScope.launch {
            combine(_state, voice.state) { s, v -> setOfNotNull(s.chat?.match?.id, s.dm?.recent?.matchId, v?.roomId) }
                .distinctUntilChanged()
                .collect(translator::retain)
        }
        // The first friend: their messages and invites can now reach the phone (the permission is asked once).
        viewModelScope.launch {
            _state.map { s -> s.friends.any { it.state == FriendState.FRIENDS } }.distinctUntilChanged().collect { if (it) social.friendAdded() }
        }
        voice.gate = ::beforeRoom
        // A host starting a random chat: the room ends first (as its Leave does); the search starts once it is gone.
        viewModelScope.launch {
            voice.state.collect { v ->
                if (!searchAfterRoom) return@collect
                if (v == null) {
                    searchAfterRoom = false
                    startMatching()
                } else if (!v.ending) {
                    searchAfterRoom = false // ending failed (offline): the room stays, nothing starts
                }
            }
        }
        // The voice room notification's buttons.
        viewModelScope.launch {
            VoiceBus.requests.collect { r ->
                when (r) {
                    VoiceBus.Request.OPEN -> voice.expand()
                    VoiceBus.Request.LEAVE -> voice.close()
                    VoiceBus.Request.END -> voice.leave()
                    VoiceBus.Request.END_CALL -> endVideo()
                }
            }
        }
    }

    /**
     * Read ticks: the newest message from the partner counts as read while its chat is on screen (no profile over it)
     * and the app is in the foreground. Sent again only when that message changes; the backend drops repeats.
     */
    private fun watchReads() = viewModelScope.launch {
        combine(_state, foreground) { s, visible -> if (visible && s.viewing == null) s.newestIncoming() else null }
            .distinctUntilChanged()
            .collect { read -> if (read != null) social.readChat(read.first, read.second) }
    }

    /** (match id, id of the partner's newest message) of the chat on screen (a friend's thread over a minimised random chat), or null. */
    private fun UiState.newestIncoming(): Pair<String, Long>? {
        val d = dm?.takeIf { chat == null || chatMinimised }
        if (d != null) return d.messages.lastOrNull { !it.fromMe }?.let { d.recent.matchId to it.id }
        val c = chat ?: return null
        return c.messages.lastOrNull { !it.fromMe }?.let { c.match.id to it.id }
    }

    /** [newAccount]: just signed up (the identity check is offered once). */
    private suspend fun afterSignIn(newAccount: Boolean = false) {
        _state.value.me?.let(social::start)
        if (newAccount) trust.afterSignUp() else trust.refreshMine()
        viewModelScope.launch { runCatching { push.register() } }
        refreshFriends()
        refreshEntitlements()
        runCatching { billing.prices() }.onSuccess { p -> _state.update { it.copy(prices = p) } }
        restorePurchases()
    }

    private val redeemedTokens = mutableSetOf<String>()

    /**
     * Purchases made offline, on another install, paid later (UPI "pending"), or whose server reply was
     * lost are verified by the server here; passes are consumed only after the server granted them.
     * Looks stay in Play's list for good (never consumed): only one the server doesn't count as mine yet is sent.
     * Called at sign-in and whenever the app comes back to the foreground.
     */
    fun restorePurchases() {
        if (_state.value.me == null) return
        viewModelScope.launch {
            val purchases = runCatching { billing.ownedPurchases() }.getOrDefault(emptyList()).filter { it.second !in redeemedTokens }
            val owned = if (purchases.any { Cosmetic.isLook(it.first) }) looks.owned() else emptySet()
            purchases.forEach { (product, token) ->
                val look = Cosmetic.isLook(product)
                if (look && (owned == null || product in owned)) {
                    if (owned != null) redeemedTokens += token
                    return@forEach
                }
                runCatching { backend.redeem(product, token) }.onSuccess { e ->
                    redeemedTokens += token
                    applyEntitlements(e)
                    if (!look) runCatching { billing.finish(product, token) }
                    if (look || product == Products.HANDLE_CHANGE) looks.loadShop()
                }
            }
        }
    }

    /**
     * Back in the foreground: payments completed meanwhile (UPI), a refunded or cancelled purchase (the server ends it,
     * 025_play_refunds.sql: Plus features go at once) and the unread counts.
     */
    fun resumed() {
        if (_state.value.me == null) return
        restorePurchases()
        viewModelScope.launch { refreshEntitlements() }
        if (_state.value.dm != null) viewModelScope.launch { refreshFriends() } // befriended meanwhile: the thread goes on
        social.refreshBadges()
        trust.resumed() // back from the identity check's page: its result
    }

    private suspend fun refreshEntitlements() {
        runCatching { backend.entitlements() }.onSuccess(::applyEntitlements)
    }

    private fun applyEntitlements(e: Entitlements) = _state.update {
        it.copy(entitlements = e, target = if (e.canChooseGender) it.target else Target.ANY)
    }

    /** The refusals that open a screen or sheet instead ([explain]) never also show as a toast. */
    private fun notice(text: UiText) {
        if (text.id in SHOWN_ELSEWHERE) return
        _state.update { it.copy(notice = text) }
    }

    private fun explain(e: Throwable) = UiText(
        when ((e as? BackendException)?.code) {
            // Verification required: the "Verify your identity" screen; manners too low for random chat: why.
            "verify_required" -> R.string.verify_required_short.also { trust.required() }
            "manner_low" -> R.string.manner_low_short.also { trust.showMannerLow() }
            "exit_locked" -> R.string.err_exit_locked
            "premium_required" -> R.string.err_premium
            "banned" -> R.string.err_banned
            "under_18" -> R.string.err_under_18
            "slow_down" -> R.string.err_slow_down
            "blocked" -> R.string.err_blocked
            "not_allowed" -> R.string.err_not_allowed
            "not_signed_in" -> R.string.err_not_signed_in
            "video_not_configured" -> R.string.err_video_off
            "too_large" -> R.string.err_too_large
            "not_found" -> R.string.err_not_found
            "room_closed" -> R.string.err_room_closed
            "room_gender" -> R.string.err_room_gender
            "room_age" -> R.string.err_room_age
            "room_full" -> R.string.err_room_full
            "room_title" -> R.string.err_room_title
            "room_size" -> R.string.err_room_size
            "bad_request" -> R.string.err_room_settings
            "voice_removed" -> R.string.voice_removed
            "mic_denied" -> R.string.err_mic_denied
            "queue_full" -> R.string.err_queue_full
            "bad_media" -> R.string.err_bad_media
            "handle_invalid" -> R.string.err_handle_invalid
            "handle_reserved" -> R.string.err_handle_reserved
            "handle_taken" -> R.string.err_handle_taken
            "handle_cooldown" -> R.string.err_handle_cooldown
            "name_invalid" -> R.string.err_name_invalid
            "not_owned" -> R.string.err_not_owned
            "sticker_not_owned" -> R.string.err_sticker_not_owned
            "not_friends" -> R.string.err_not_allowed
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

    /** What is wrong with the ID [handle] ([current]: mine), as far as the phone and the last check know; null: nothing. */
    private fun handleProblem(handle: String, current: String?): UiText? {
        val checked = looks.handle.value?.takeIf { Handle.normalize(it.text) == handle }?.result
        return looks.problem(HandleField(handle, Handle.local(handle, current) ?: checked))
    }

    fun createProfile(d: ProfileDraft) {
        val name = d.name.trim()
        val birth = Validation.birthDate(d.day, d.month, d.year)
        val age = birth?.let { Validation.age(it, today()) }
        val handle = d.handle.takeIf { it.isNotBlank() }?.let(Handle::normalize)
        val handleError = handle?.let { handleProblem(it, null) }
        val error = when {
            name.isEmpty() -> UiText(R.string.err_name)
            name.length > Validation.MAX_NAME -> UiText(R.string.err_name_long)
            !Handle.plainName(Handle.normalize(name)) -> UiText(R.string.err_name_invalid)
            handleError != null -> handleError
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
                val made = backend.createProfile(NewProfile(name, d.gender!!, birth!!, d.city.trim(), d.bio.trim(), d.photoUri))
                // The profile comes with a free ID; the chosen one is asked for now. Taken in between (or offline): the
                // free one stays, and Edit profile can change it at once.
                val me = if (handle == null || handle == made.handle) made else try {
                    made.copy(handle = backend.setHandle(handle))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    notice(UiText(R.string.handle_kept, made.handle.orEmpty()))
                    made
                }
                looks.clearHandle()
                _state.update { it.copy(me = me, onboarding = Onboarding()) }
                afterSignIn(newAccount = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(onboarding = it.onboarding.copy(busy = false, error = explain(e))) }
            }
        }
    }

    // ---- Lobby, Premium ---------------------------------------------------------------------

    /** A tab: the room I'm in, if open full screen, goes down to the bar over the tabs (still in it). */
    fun selectTab(tab: Tab) {
        if (voice.state.value?.expanded == true) voice.minimise()
        if (_state.value.chat != null) minimiseChat()
        _state.update { it.copy(tab = tab) }
        when (tab) {
            Tab.RECENT -> { refreshRecents(); viewModelScope.launch { refreshFriends() }; social.refreshBadges() }
            Tab.ROOMS -> voice.refreshList()
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

    fun setCountries(isos: List<String>) {
        val c = isos.distinct().take(MatchPrefs.MAX)
        matchPrefs.countries = c
        _state.update { it.copy(countries = c) }
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
            if (productId == Products.PREMIUM) backend.track(Funnel.PLUS_PURCHASED)
            billing.finish(productId, token)
            _state.update { it.copy(paywall = null, notice = UiText(R.string.msg_purchase_done)) }
        }
    }

    /**
     * A look from the shop: Google Play's sheet, then the server's check (verify-purchase), which makes it mine for good
     * (and worn, if it is the first of its kind). Never consumed.
     */
    fun buyLook(activity: Activity, item: String) {
        val me = _state.value.me ?: return
        if (looks.shop.value.busy != null) return
        looks.buying(item)
        viewModelScope.launch {
            try {
                val token = billing.buy(activity, item, me.id)
                if (token == null) {
                    restorePurchases() // a payment that completed outside the sheet is picked up here
                    return@launch
                }
                backend.redeem(item, token)
                redeemedTokens += token
                val bought = Cosmetic.of(item)
                if (bought == null) runCatching { billing.finish(item, token) } // an ID change is used up, then bought again
                notice(
                    when {
                        bought == null -> UiText(R.string.handle_pass_bought)
                        bought.kind.wearable -> UiText(R.string.look_bought)
                        else -> UiText(R.string.item_bought, UiText(bought.label))
                    },
                )
                looks.loadShop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
            } finally {
                looks.buying(null)
            }
        }
    }

    // ---- Matching -----------------------------------------------------------------------------

    // ---- One live session at a time ----------------------------------------------------------
    // A voice room, or a random chat (and its search): entering one leaves the other, without asking, and says so.
    // The server does the same (find_match2 leaves rooms; joining or opening a room ends the random chat), so a room
    // or chat the server ended this way just closes here.

    /** The host chose a random chat: the search starts when the room has ended. */
    private var searchAfterRoom = false

    /**
     * Before entering another room ([VoiceController.gate]): a random chat ends ("Ended your chat with …"), a search
     * stops quietly.
     */
    private fun beforeRoom(proceed: () -> Unit) {
        val s = _state.value
        val c = s.chat
        when {
            c != null -> {
                endChatForRoom(c)
                if (!c.partnerLeft) notice(UiText(R.string.live_ended_chat, c.match.partner.name))
                proceed()
            }
            s.searching -> {
                cancelMatching()
                proceed()
            }
            else -> proceed()
        }
    }

    /** Closes the random chat at once and tells the server (which also ends it when the room is entered). */
    private fun endChatForRoom(c: ChatState) {
        closeChat(c.match.id)
        viewModelScope.launch { runCatching { backend.leave(c.match.id) } }
    }

    // ---- The random chat, minimised -------------------------------------------------------------

    /** The chat goes down to its bar over the tabs (still on, as a room does). */
    fun minimiseChat() {
        val c = _state.value.chat ?: return
        if (_state.value.chatMinimised) return
        _state.update { it.copy(chatMinimised = true, chatSeen = c.messages.filter { m -> !m.fromMe }.maxOfOrNull { m -> m.id } ?: 0) }
    }

    fun expandChat() = _state.update { if (it.chat != null) it.copy(chatMinimised = false, dm = null, viewing = null) else it }

    /** The bar's end button: the chat's own exit (the exit lock still holds; then it says to wait). */
    fun endChatFromBar() {
        if (_state.value.chat == null) return
        if (exitRemainingMs() > 0) return notice(UiText(R.string.err_exit_locked))
        leave(next = false)
    }

    /**
     * Start chatting. From inside a room, the room is left first, as its Leave does (the host's goes to a co-host, else
     * ends), and that is said ("Left “…”"). A chat already on (minimised) just comes back.
     */
    fun startMatching() {
        val s = _state.value
        if (s.chat != null) return expandChat()
        if (s.me == null || s.searching) return
        voice.state.value?.let { room ->
            notice(UiText(R.string.live_left_room, room.room?.title.orEmpty()))
            if (room.isHost && room.heir == null) {
                searchAfterRoom = true
                voice.end(quiet = true)
            } else {
                voice.close()
                startMatchingNow()
            }
            return
        }
        startMatchingNow()
    }

    private fun startMatchingNow() {
        val s = _state.value
        if (s.me == null || s.searching || s.chat != null) return
        _state.update { it.copy(searching = true, reconnecting = false, dm = null, viewing = null) }
        dmJob?.cancel()
        val gen = ++searchGeneration
        fun current() = gen == searchGeneration && _state.value.searching
        fun reconnecting(on: Boolean) = _state.update { if (current() && it.reconnecting != on) it.copy(reconnecting = on) else it }
        matchJob = viewModelScope.launch {
            // Someone matched me while I was waiting: poll at once instead of after the delay. Polling stays the fallback.
            val signals = Channel<Unit>(Channel.CONFLATED)
            val listener = launch { listenForMatches(signals) }
            // Weak networks: failed attempts are retried with a growing pause; only a minute of nothing but failures
            // (on screen) ends the search.
            var failingSince: Long? = null
            var failures = 0
            try {
                while (isActive && current()) {
                    // In the background the search holds still (no requests every 2 s) and resumes on return.
                    if (!foreground.value) {
                        foreground.awaitVisible()
                        failingSince = null // time off screen is not time spent failing
                    }
                    if (!current()) break
                    var failed: Exception? = null
                    // Not cancellable: if the server created a match, we must learn about it (see abandon()).
                    val m = try {
                        withContext(NonCancellable) { backend.findMatch(s.target, s.countries) }
                    } catch (e: OrphanMatchException) {
                        abandon(e.matchId) // paired, but the partner's profile couldn't load: end it and keep looking
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val code = (e as? BackendException)?.code
                        when {
                            !current() -> null // stopped meanwhile; handled just below
                            // A chat we just left (or abandoned) still holds the exit lock for a moment: wait it out.
                            code == "exit_locked" -> null
                            code in STOP_CODES -> throw e
                            else -> null.also { failed = e } // network, timeout, server hiccup: try again
                        }
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
                    val error = failed
                    if (error == null) {
                        failingSince = null
                        failures = 0
                        reconnecting(false)
                    } else {
                        val now = clock()
                        val since = failingSince ?: now.also { failingSince = it }
                        if (now - since >= GIVE_UP_MS) throw error
                        failures++
                        reconnecting(true)
                    }
                    nextAttempt(signals, if (error == null) POLL_MS else retryMs(failures))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == searchGeneration) { // an old search's error must not stop the new one
                    _state.update { it.copy(searching = false, reconnecting = false) }
                    notice(explain(e))
                }
            } finally {
                listener.cancel()
                if (gen == searchGeneration) _state.update { if (it.reconnecting) it.copy(reconnecting = false) else it }
            }
        }
    }

    /** Waits [ms] before the next attempt, or less when a match signal arrives. */
    private suspend fun nextAttempt(signals: ReceiveChannel<Unit>, ms: Long) {
        withTimeoutOrNull(ms) { signals.receive() }
    }

    /** Forwards the backend's match signals while the search runs; a dropped subscription is retried, polling covers the gap. */
    private suspend fun listenForMatches(signals: SendChannel<Unit>) {
        while (true) {
            try {
                backend.matchSignals().collect { signals.trySend(Unit) }
                return // a backend without signals (demo) just ends the flow
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                delay(SIGNALS_RETRY_MS)
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
        if (!_state.value.searching) return
        _state.update { it.copy(searching = false, reconnecting = false) }
        // Offline this fails; no error for that: the server drops a queue spot that is not refreshed within seconds.
        viewModelScope.launch { runCatching { backend.cancelSearch() } }
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
        chatJob?.cancel()
        dmJob?.cancel()
        _state.update { it.copy(searching = false, reconnecting = false, chat = ChatState(m, clock()), chatMinimised = false, dm = null, viewing = null) }
        viewModelScope.launch { refreshEntitlements() } // the exit lock depends on Premium, which may have expired
        chatJob = viewModelScope.launch {
            var warned = false
            var newest: Long? = null
            // Reconnects on its own after a dropped connection; the history merge fills any gap. One full history
            // load, then only newer messages (here after a reconnect, and in the re-syncs inside events()).
            while (isActive) {
                runCatching { backend.history(m.id, newest) }.onSuccess { h ->
                    h.maxOfOrNull { it.id }?.let { id -> newest = maxOf(id, newest ?: id) }
                    updateChat(m.id) { c -> c.copy(messages = merge(c.messages, h)) }
                }
                try {
                    backend.events(m.id, live = true, afterId = newest).collect { e -> applyChatEvent(m.id, e) }
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

    private fun applyChatEvent(matchId: String, e: MatchEvent) {
        // A call request (or the call itself) brings a minimised chat back up.
        if (e is MatchEvent.Video && (e.status == VideoStatus.REQUESTED_BY_PARTNER || e.status == VideoStatus.ACCEPTED)) {
            _state.update { if (it.chat?.match?.id == matchId) it.copy(chatMinimised = false) else it }
        }
        applyChatChange(matchId, e)
    }

    private fun applyChatChange(matchId: String, e: MatchEvent) = updateChat(matchId) { c ->
        when (e) {
            is MatchEvent.NewMessage -> c.copy(messages = merge(c.messages, listOf(e.message)), partnerTyping = false)
            is MatchEvent.Typing -> c.copy(partnerTyping = e.on)
            MatchEvent.PartnerLeft -> c.copy(partnerLeft = true, partnerTyping = false, video = VideoStatus.NONE)
            is MatchEvent.Read -> c.copy(partnerRead = maxOf(c.partnerRead, e.upto))
            is MatchEvent.Video -> {
                declined(c.video, e, c.match.partner)
                c.copy(video = e.status, callVideo = e.video)
            }
        }
    }

    /** My call [was] asked for and [e] says the partner declined it. */
    private fun declined(was: VideoStatus, e: MatchEvent.Video, partner: Profile) {
        if (e.status == VideoStatus.DECLINED && was == VideoStatus.REQUESTED_BY_ME) notice(UiText(R.string.msg_declined, partner.name))
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

    /** [onFailed]: the message did not reach the server, so the composer puts the text back (the error shows as a notice). */
    fun sendText(text: String, onFailed: () -> Unit = {}) {
        val t = text.trim().take(MAX_TEXT)
        val c = _state.value.chat ?: return
        if (t.isEmpty() || c.partnerLeft) return
        send(onFailed) { backend.send(c.match.id, t, null).also { m -> updateChat(c.match.id) { it.copy(messages = merge(it.messages, listOf(m))) } } }
    }

    /** Like [act], and on failure also [onFailed]. */
    private fun send(onFailed: () -> Unit, block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notice(explain(e))
            onFailed()
        }
    }

    private var lastTypingSentMs = Long.MIN_VALUE / 2
    private var lastTypingTo: String? = null

    /**
     * Called on every keystroke; tells the other person at most every 3 s. A friend's thread open over a minimised
     * random chat is the one being typed in.
     */
    fun typing() {
        val id = _state.value.shownMatch ?: return
        val now = clock()
        if (id == lastTypingTo && now - lastTypingSentMs < TYPING_EVERY_MS) return
        lastTypingSentMs = now
        lastTypingTo = id
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
        if (!leaving.add(c.match.id)) return // a second tap while the first is on its way
        act {
            try {
                gone { backend.leave(c.match.id) }
            } finally {
                leaving.remove(c.match.id)
            }
            val ended = _state.value.chat?.takeIf { it.match.id == c.match.id } ?: c
            // Only the chat that was left closes, and only once: a late answer must not close (or restart) a newer one.
            if (closeChat(c.match.id)) {
                if (MANNERS) trust.chatEnded(ended) // how was it? (both wrote; once)
                if (next) startMatching()
            }
        }
    }

    /** Matches whose leave request is in flight. */
    private val leaving = mutableSetOf<String>()

    /** Safety exit: never locked. Blocks the partner and removes them from Recent. */
    fun reportAndLeave(reason: ReportReason, next: Boolean) {
        val c = _state.value.chat ?: return
        act {
            gone { backend.report(c.match.partner.id, reason, c.match.id) }
            val closed = closeChat(c.match.id)
            notice(UiText(R.string.msg_reported_long))
            if (closed && next) startMatching()
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

    /** Closes the chat [matchId] if it is still the one open; false when it was already closed (or replaced). */
    private fun closeChat(matchId: String): Boolean {
        if (_state.value.chat?.match?.id != matchId) return false
        chatJob?.cancel()
        _state.update { it.copy(chat = null, chatMinimised = false, viewing = null) }
        refreshRecents()
        return true
    }

    /** A [video] or voice call (free) in the chat on screen: the random chat, or a friend's thread that goes on. */
    fun requestCall(video: Boolean) {
        val s = _state.value
        val id = s.shownMatch ?: return
        val over = s.chat?.takeIf { it.match.id == id }?.partnerLeft ?: s.dmClosed
        if (over || s.callIn(id) in setOf(VideoStatus.REQUESTED_BY_ME, VideoStatus.REQUESTED_BY_PARTNER, VideoStatus.ACCEPTED)) return
        act {
            backend.requestCall(id, video)
            setCall(id, VideoStatus.REQUESTED_BY_ME, video)
        }
    }

    /** The partner's call asked for in the chat on screen. */
    fun answerVideo(accept: Boolean) {
        val id = _state.value.shownMatch ?: return
        act {
            backend.answerVideo(id, accept)
            setCall(id, if (accept) VideoStatus.ACCEPTED else VideoStatus.NONE)
        }
    }

    /** Ends the call, or takes back my call request in the chat on screen. */
    fun endVideo() {
        val s = _state.value
        val id = s.call?.matchId ?: s.shownMatch?.takeIf { s.callIn(it) == VideoStatus.REQUESTED_BY_ME } ?: return
        setCall(id, VideoStatus.NONE)
        act { backend.endVideo(id) }
    }

    /** The call's state in the random chat and the friend's thread showing [matchId] (the same chat may be both). */
    private fun setCall(matchId: String, status: VideoStatus, video: Boolean? = null) {
        updateChat(matchId) { it.copy(video = status, callVideo = video ?: it.callVideo) }
        updateDmState(matchId) { it.copy(video = status, callVideo = video ?: it.callVideo) }
    }

    suspend fun videoToken(): VideoToken? {
        val id = _state.value.call?.matchId ?: return null
        return try {
            backend.videoToken(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notice(explain(e))
            null
        }
    }

    /** The nudity check hid the partner's video (the call screen asks once per call). Best effort, no notice. */
    fun flagNudity() {
        val id = _state.value.call?.matchId ?: return
        viewModelScope.launch {
            try {
                backend.flagNudity(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Unit
            }
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

    // Finding people by ID and the block list (042): the sheets ask as they show them.
    suspend fun findByHandle(query: String) = backend.searchHandles(query)
    suspend fun blocks() = backend.blocks()

    /** Someone found by ID: their card once it has loaded (found people may be viewed for a day). */
    fun openProfile(p: PersonRow) = act { backend.profile(p.id)?.let { full -> _state.update { it.copy(viewing = full) } } }

    fun unblock(p: PersonRow, done: (ok: Boolean) -> Unit) = viewModelScope.launch {
        val ok = runCatching { backend.unblock(p.id) }.onFailure { notice(explain(it)) }.isSuccess
        if (ok) notice(UiText(R.string.unblocked, p.name))
        done(ok)
    }

    /** Report from a profile or Recent: blocks and removes the person everywhere. */
    fun report(partnerId: String, reason: ReportReason) {
        val c = _state.value.chat
        if (c?.match?.partner?.id == partnerId) {
            reportAndLeave(reason, next = false)
            return
        }
        act {
            backend.report(partnerId, reason, null)
            forget(partnerId, UiText(R.string.msg_reported))
        }
    }

    /**
     * Block without reporting. Like a report it closes the chat, thread or profile with that person and drops them
     * from Recent, Friends and the room's chat.
     */
    fun block(person: Profile) {
        val c = _state.value.chat
        act {
            if (c?.match?.partner?.id == person.id) {
                gone { backend.block(person.id) }
                closeChat(c.match.id)
            } else {
                backend.block(person.id)
            }
            contacts.ended(person.id)
            forget(person.id, UiText(R.string.msg_blocked, person.name))
        }
    }

    /** Removes every trace of [partnerId] from what is on screen. */
    private fun forget(partnerId: String, message: UiText) {
        voice.forget(partnerId)
        feed.forget(partnerId)
        dmJob?.takeIf { _state.value.dm?.recent?.partner?.id == partnerId }?.cancel()
        _state.update { s ->
            s.copy(
                recents = s.recents.filterNot { it.partner.id == partnerId },
                friends = s.friends.filterNot { it.profile.id == partnerId },
                viewing = s.viewing?.takeIf { it.id != partnerId },
                dm = s.dm?.takeIf { it.recent.partner.id != partnerId },
                notice = message,
            )
        }
    }

    private var recentsJob: Job? = null

    /** The newest request wins: quick tab switching neither stacks requests nor lets an older answer overwrite a newer one. */
    fun refreshRecents() {
        recentsJob?.cancel()
        recentsJob = act {
            val list = backend.recents()
            _state.update { it.copy(recents = list) }
        }
    }

    /** Settings › Recent chats: takes [ids] off my recents (hide_recents); [done] gets whether it worked. */
    fun hideRecents(ids: List<String>, done: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = try {
            backend.hideRecents(ids.distinct())
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notice(explain(e))
            false
        }
        if (ok) {
            _state.update { s -> s.copy(recents = s.recents.filterNot { it.partner.id in ids }) }
            notice(UiText(R.string.recents_deleted))
            refreshRecents()
        }
        done(ok)
    }

    /** Message someone from Recent, or a friend (their chat, or a new private one the server opens). */
    fun openDm(partnerId: String) {
        val s = _state.value
        s.recents.firstOrNull { it.partner.id == partnerId }?.let { return openThread(it) }
        val friend = s.friends.firstOrNull { it.profile.id == partnerId && it.state == FriendState.FRIENDS } ?: return
        act {
            val thread = friend.threadId ?: backend.friendThread(partnerId)
            // A search or chat started while the thread was looked up wins: no thread pops up over it later.
            if (_state.value.searching || _state.value.chat != null) return@act
            openThread(RecentMatch(thread, friend.profile, 0))
        }
    }

    private fun openThread(r: RecentMatch) {
        // A room open full screen goes down to its bar: the chat would open under it, out of sight.
        voice.minimise()
        _state.update { it.copy(dm = DmState(r), viewing = null) }
        viewModelScope.launch { refreshFriends() } // whether it goes on (dmClosed) depends on being friends now
        dmJob?.cancel()
        dmJob = viewModelScope.launch {
            val h = runCatching { backend.history(r.matchId) }.getOrNull()
            h?.let { updateDm(r.matchId) { old -> merge(old, it) } }
            runCatching {
                backend.events(r.matchId, live = false, afterId = h?.maxOfOrNull { it.id })
                    .collect { e ->
                        when (e) {
                            is MatchEvent.NewMessage -> updateDmState(r.matchId) {
                                it.copy(messages = merge(it.messages, listOf(e.message)), partnerTyping = it.partnerTyping && e.message.fromMe)
                            }
                            is MatchEvent.Read -> updateDmState(r.matchId) { it.copy(partnerRead = maxOf(it.partnerRead, e.upto)) }
                            is MatchEvent.Typing -> updateDmState(r.matchId) { it.copy(partnerTyping = e.on) }
                            is MatchEvent.Video -> updateDmState(r.matchId) {
                                declined(it.video, e, it.recent.partner)
                                it.copy(video = e.status, callVideo = e.video)
                            }
                            else -> Unit
                        }
                    }
            }
        }
    }

    fun closeDm() {
        dmJob?.cancel()
        _state.update { it.copy(dm = null) }
        refreshRecents()
        social.refreshBadges() // what was read there
    }

    // ---- Notifications, links -----------------------------------------------------------------

    /** Reply / Message: the chat with them (a friend's thread the server opens when there is none yet). */
    fun inboxChat(item: InboxItem) = social.itemAction(item) {
        val thread = item.matchId ?: backend.friendThread(item.actor.id)
        social.closeInbox()
        openThread(RecentMatch(thread, item.actor, 0))
    }

    fun inboxAnswer(item: InboxItem, accept: Boolean) = social.itemAction(item) {
        backend.answerFriend(item.actor.id, accept)
        applyAnswer(item.actor, accept)
        if (accept) notice(UiText(R.string.msg_now_friends, item.actor.name))
        social.loadInbox()
        social.refreshBadges()
    }

    /**
     * A friend's call ringing anywhere in the app ([SocialState.ring]): accepting opens their thread with the call on
     * (the call screen comes up); declining says no. Either way the server reads its notification.
     */
    fun answerRing(accept: Boolean) {
        val ring = social.state.value.ring ?: return
        val id = ring.item.matchId ?: return
        social.clearRing()
        if (accept) openThread(RecentMatch(id, ring.item.actor, 0))
        act {
            backend.answerVideo(id, accept)
            if (accept) setCall(id, VideoStatus.ACCEPTED, ring.video)
        }
    }

    fun inboxJoin(item: InboxItem) = voice.guard(item.roomId) { social.joinInvite(item, voice::openNow) }

    /** A like or comment on my post: the post full screen with its comments. */
    fun inboxPost(item: InboxItem) {
        val id = item.post?.id ?: return
        social.closeInbox()
        feed.openPost(id)
    }

    /** Links wait until I'm signed in (a room link survives signing up first). */
    private fun followLinks() = viewModelScope.launch {
        combine(links.pending, _state.map { !it.booting && it.me != null }.distinctUntilChanged()) { link, ready -> link.takeIf { ready } }
            .collect { link ->
                if (link == null) return@collect
                links.taken(link)
                openLink(link)
            }
    }

    private fun openLink(link: DeepLink) {
        when (link) {
            is DeepLink.Room -> voice.open(link.id)
            is DeepLink.Code -> voice.joinCode(link.code)
            is DeepLink.Invite -> voice.guard { act { voice.openNow(backend.joinInvite(link.inboxId)) } }
            is DeepLink.Thread -> {
                val placeholder = Profile(link.actorId.orEmpty(), link.actorName, Gender.OTHER, 0, "", "", colorSeed = link.actorId.hashCode())
                openThread(RecentMatch(link.matchId, placeholder, 0))
                val id = link.actorId ?: return
                act {
                    val p = backend.profile(id) ?: return@act
                    updateDmState(link.matchId) { it.copy(recent = it.recent.copy(partner = p)) }
                }
            }
            DeepLink.Inbox -> social.openInbox()
        }
    }

    fun sendDm(text: String, onFailed: () -> Unit = {}) {
        val t = text.trim().take(MAX_TEXT)
        val dm = _state.value.dm ?: return
        if (t.isEmpty()) return
        val partner = dm.recent.partner
        viewModelScope.launch {
            try {
                backend.send(dm.recent.matchId, t, null).also { m -> updateDm(dm.recent.matchId) { merge(it, listOf(m)) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Refused by row-level security while we aren't friends: the chat ended (chat_open), not a block.
                val ended = (e as? BackendException)?.code == "blocked" && _state.value.friendState(partner.id) != FriendState.FRIENDS
                notice(if (ended) UiText(R.string.dm_ended, partner.name) else explain(e))
                if (ended) refreshFriends()
                onFailed()
            }
        }
    }

    private fun updateDm(matchId: String, change: (List<Message>) -> List<Message>) = updateDmState(matchId) { it.copy(messages = change(it.messages)) }

    private fun updateDmState(matchId: String, change: (DmState) -> DmState) = _state.update {
        val dm = it.dm
        if (dm == null || dm.recent.matchId != matchId) it else it.copy(dm = change(dm))
    }

    fun signOut() = act {
        searchGeneration++
        _state.update { it.copy(searching = false) }
        matchJob?.join() // a match request in flight finishes first (it may hand back a match to end)
        abandonJobs.toList().forEach { it.join() } // end those matches while still signed in
        runCatching { push.unregister() } // no pushes for this account on this phone any more
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
        voice.close()
        social.reset()
        feed.reset()
        trust.reset()
        looks.reset()
        contacts.reset()
        matchJob?.cancel()
        chatJob?.cancel()
        dmJob?.cancel()
        _state.value = UiState(booting = false)
    }

    /** Shares a post with friends or everyone; it shows on my profile at once. */
    fun addPost(text: String, imageUri: String?, visibility: PostVisibility = PostVisibility.FRIENDS) {
        val t = text.trim().take(Post.MAX_TEXT)
        if (t.isEmpty() && imageUri == null) return
        act {
            val post = backend.addPost(t, imageUri, visibility)
            _state.update { s -> s.copy(me = s.me?.let { it.copy(posts = listOf(post) + it.posts) }) }
            notice(UiText(R.string.msg_posted))
        }
    }

    /**
     * Edit my profile. Gender and birth date can't change (the server refuses too). A new ID ([handle]) is asked for
     * first: when the server refuses it (taken, too soon), nothing is saved and it says why.
     */
    fun saveProfile(name: String, city: String, bio: String, newPhotoUri: String?, handle: String, done: () -> Unit) {
        val n = name.trim()
        val current = _state.value.me?.handle
        val h = Handle.normalize(handle)
        val newHandle = h.takeIf { it != current }
        val error = when {
            n.isEmpty() -> UiText(R.string.err_name)
            n.length > Validation.MAX_NAME -> UiText(R.string.err_name_long)
            !Handle.plainName(Handle.normalize(n)) -> UiText(R.string.err_name_invalid)
            bio.length > Validation.MAX_BIO -> UiText(R.string.err_bio_long)
            newHandle != null -> handleProblem(newHandle, current)
            else -> null
        }
        if (error != null) return notice(error)
        act {
            if (newHandle != null) {
                val stored = backend.setHandle(newHandle)
                _state.update { s -> s.copy(me = s.me?.copy(handle = stored)) }
                _state.value.me?.let { looks.handleSet(it.id, stored) }
            }
            val me = backend.updateProfile(n, city.trim().take(30), bio.trim(), newPhotoUri)
            _state.update { it.copy(me = me) }
            looks.clearHandle()
            done()
        }
    }

    // ---- Friends ----------------------------------------------------------------------------

    private suspend fun refreshFriends() {
        runCatching { backend.friends() }.onSuccess { list -> _state.update { it.copy(friends = list, friendsLoaded = true) } }
    }

    /** The friend states shown (a room's People sheet) are asked again. */
    fun reloadFriends() {
        viewModelScope.launch { refreshFriends() }
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
        applyAnswer(p, accept)
        social.refreshBadges()
    }

    private fun applyAnswer(p: Profile, accept: Boolean) {
        _state.update { s ->
            if (accept && s.friends.none { it.profile.id == p.id }) return@update s.copy(friends = listOf(Friend(p, FriendState.FRIENDS)) + s.friends)
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
        contacts.ended(p.id)
        _state.update { s -> s.copy(friends = s.friends.filterNot { it.profile.id == p.id }) }
    }

    companion object {
        const val POLL_MS = 2_000L
        /** A search that has failed for this long (on screen) without a single answer stops with the network notice. */
        const val GIVE_UP_MS = 60_000L
        private const val SIGNALS_RETRY_MS = 10_000L
        /** Rule refusals end the search at once; everything else except exit_locked is retried. */
        private val STOP_CODES = setOf("premium_required", "banned", "no_profile", "not_signed_in", "bad_target", "verify_required", "manner_low")
        private val SHOWN_ELSEWHERE = setOf(R.string.verify_required_short, R.string.manner_low_short)

        /** Pause before retry number [failures] (1, 2, ...): 2 s, 4 s, 8 s, then 10 s each. */
        fun retryMs(failures: Int): Long = when (failures) {
            1 -> 2_000L
            2 -> 4_000L
            3 -> 8_000L
            else -> 10_000L
        }
        const val MAX_TEXT = 1000
        const val TYPING_EVERY_MS = 3_000L
        const val RECONNECT_MS = 3_000L
    }
}
