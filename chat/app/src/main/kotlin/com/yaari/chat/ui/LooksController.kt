package com.yaari.chat.ui

import com.yaari.chat.R
import com.yaari.chat.data.Backend
import com.yaari.chat.domain.Handle
import com.yaari.chat.model.HandleCheck
import com.yaari.chat.model.Look
import com.yaari.chat.model.LookKind
import com.yaari.chat.model.Shop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The ID field (sign-up, Edit profile): what is typed and what is known about it ([checking]: asking the server). */
data class HandleField(val text: String, val result: HandleCheck? = null, val checking: Boolean = false)

/**
 * The Looks shop. [shop]: null until loaded ([failed]: that load failed); [trying]: the look shown in the preview (what
 * I wear, with the item last tapped); [busy]: the item being bought or worn.
 */
data class ShopUi(val shop: Shop? = null, val failed: Boolean = false, val trying: Look? = null, val busy: String? = null)

/**
 * IDs and looks (040): everyone's ID and look (one batched looks() lookup for whatever is on screen, as the trust
 * badges), my shop and what I wear, and the live check of an ID being typed. Owned by [AppViewModel]; plain
 * coroutines on its scope.
 */
class LooksController(
    private val backend: Backend,
    private val scope: CoroutineScope,
    private val notice: (UiText) -> Unit,
    private val explain: (Throwable) -> UiText,
) {
    /** IDs and looks of the people shown so far (by id). */
    private val _people = MutableStateFlow<Map<String, Look>>(emptyMap())
    val people: StateFlow<Map<String, Look>> = _people.asStateFlow()

    private val _shop = MutableStateFlow(ShopUi())
    val shop: StateFlow<ShopUi> = _shop.asStateFlow()

    private val _handle = MutableStateFlow<HandleField?>(null)
    val handle: StateFlow<HandleField?> = _handle.asStateFlow()

    private val loadedAt = mutableMapOf<String, Long>()
    private val wanted = linkedSetOf<String>()
    private var batchJob: Job? = null
    private var shopJob: Job? = null
    private var checkJob: Job? = null

    fun reset() {
        batchJob?.cancel()
        shopJob?.cancel()
        checkJob?.cancel()
        wanted.clear()
        loadedAt.clear()
        _people.value = emptyMap()
        _shop.value = ShopUi()
        _handle.value = null
    }

    // ---- everyone's ID and look ----

    /**
     * IDs and looks for these people: one looks() request for everything asked within a frame ([BATCH_MS]: a room's
     * seats, a list, a chat), at most 100 at a time; known ones are asked again after [STALE_MS]. Failures are asked
     * again next time.
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
                val got = runCatching { backend.looks(batch) }.getOrNull()
                val at = System.currentTimeMillis()
                wanted.removeAll(batch.toSet())
                if (got != null) {
                    batch.forEach { loadedAt[it] = at }
                    _people.update { m -> m + got.associateBy { it.userId } }
                }
            }
        }
    }

    /** My own ID or look changed here: everything showing it follows at once. */
    private fun mine(look: Look) {
        loadedAt[look.userId] = System.currentTimeMillis()
        _people.update { it + (look.userId to look) }
    }

    /** My ID is now [handle] (set at sign-up or in Edit profile). */
    fun handleSet(userId: String, handle: String) {
        val old = _people.value[userId] ?: _shop.value.shop?.look ?: Look(userId, handle)
        mine(old.copy(handle = handle))
        _shop.update { s -> s.copy(shop = s.shop?.let { it.copy(look = it.look?.copy(handle = handle)) }, trying = s.trying?.copy(handle = handle)) }
    }

    // ---- the shop ----

    /** The shop came on screen (or a purchase went through): what is on sale, what I own and wear. */
    fun loadShop() {
        shopJob?.cancel()
        _shop.update { it.copy(failed = false) }
        shopJob = scope.launch {
            try {
                val shop = backend.shop()
                _shop.update { s ->
                    // The first load shows what I wear; a reload (after a purchase) keeps what I was trying on.
                    val trying = if (s.shop == null || s.trying == null) shop.look else s.trying
                    s.copy(shop = shop, failed = false, trying = trying)
                }
                shop.look?.let(::mine)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _shop.update { it.copy(failed = true) }
            }
        }
    }

    /** The item I own or could buy, tried in the preview (null: none of that kind). */
    fun tryOn(kind: LookKind, item: String?) = _shop.update { s -> s.copy(trying = s.trying?.with(kind, item)) }

    /** Wears [item] (null: takes that kind off); the other kinds stay as they are. */
    fun wear(kind: LookKind, item: String?) {
        val s = _shop.value
        val worn = s.shop?.look ?: return
        if (s.busy != null) return
        val next = worn.with(kind, item)
        _shop.update { it.copy(busy = item ?: kind.code) }
        scope.launch {
            try {
                val look = backend.equipLook(next.badge, next.color, next.font, next.frame)
                _shop.update { u -> u.copy(shop = u.shop?.copy(look = look), trying = u.trying?.with(kind, item)) }
                mine(look)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
            } finally {
                _shop.update { it.copy(busy = null) }
            }
        }
    }

    /** A purchase is on its way ([item]), or done (null). */
    fun buying(item: String?) = _shop.update { it.copy(busy = item) }

    /** The looks I own, asked of the server now (null when it can't be reached). */
    suspend fun owned(): Set<String>? = try {
        backend.shop().owned
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    // ---- the ID field ----

    /**
     * Someone types an ID ([current]: mine, if I have one): the rules are checked at once on the phone, then the
     * server's handle_check [CHECK_MS] after the last keystroke (free or taken). An empty field asks nothing.
     */
    fun checkHandle(text: String, current: String?) {
        checkJob?.cancel()
        if (text.isBlank()) {
            _handle.value = HandleField(text)
            return
        }
        val local = Handle.local(text, current)
        if (local != null) {
            _handle.value = HandleField(text, local)
            return
        }
        _handle.value = HandleField(text, checking = true)
        checkJob = scope.launch {
            delay(CHECK_MS)
            // Offline: no verdict; saving says what the server thinks.
            val result = try {
                backend.handleCheck(text)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _handle.update { f -> if (f?.text == text) HandleField(text, result) else f }
        }
    }

    fun clearHandle() {
        checkJob?.cancel()
        _handle.value = null
    }

    /** What to say under the ID field about [f], or null when it is fine (or still being checked). */
    fun problem(f: HandleField?): UiText? = when (f?.result) {
        HandleCheck.INVALID -> UiText(R.string.err_handle_invalid)
        HandleCheck.RESERVED -> UiText(R.string.err_handle_reserved)
        HandleCheck.TAKEN -> UiText(R.string.err_handle_taken)
        else -> null
    }

    companion object {
        /** One frame: everything composed together goes in one request. */
        const val BATCH_MS = 16L
        const val STALE_MS = 2 * 60_000L
        /** handle_check after the last keystroke. */
        const val CHECK_MS = 400L
    }
}
