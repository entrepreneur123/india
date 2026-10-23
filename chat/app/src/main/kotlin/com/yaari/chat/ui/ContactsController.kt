package com.yaari.chat.ui

import com.yaari.chat.R
import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.model.ContactApp
import com.yaari.chat.model.FriendContacts
import com.yaari.chat.model.MyContacts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Messenger contacts (044). [mine]: null until loaded; [of]: each friend's side once loaded (a null value: we aren't
 * friends, so nothing shows); [refused]: the app the server (or the same check here) refused on save; [busy]: a save
 * ("") or the friend whose sharing is changing.
 */
data class ContactsUi(
    val mine: MyContacts? = null,
    val of: Map<String, FriendContacts?> = emptyMap(),
    val refused: ContactApp? = null,
    val busy: String? = null,
)

/** Owned by [AppViewModel]; plain coroutines on its scope. */
class ContactsController(
    private val backend: Backend,
    private val scope: CoroutineScope,
    private val notice: (UiText) -> Unit,
    private val explain: (Throwable) -> UiText,
) {
    private val _state = MutableStateFlow(ContactsUi())
    val state: StateFlow<ContactsUi> = _state.asStateFlow()

    fun reset() {
        _state.value = ContactsUi()
    }

    fun loadMine() = scope.launch {
        runCatching { backend.myContacts() }.onSuccess { m -> _state.update { it.copy(mine = m) } }
    }

    /** A friend's side, and mine (whether I have any to share). */
    fun load(userId: String) = scope.launch {
        if (_state.value.mine == null) loadMine()
        runCatching { backend.contactsOf(userId) }.onSuccess { c -> _state.update { it.copy(of = it.of + (userId to c)) } }
    }

    fun clearRefused() = _state.update { it.copy(refused = null) }

    /** Saves mine (each field checked here first, as the server does); [done] gets them as stored. */
    fun save(values: Map<ContactApp, String>, done: (Map<ContactApp, String>) -> Unit = {}) {
        values.entries.firstOrNull { (app, v) -> app.clean(v) == null }?.let { bad -> _state.update { it.copy(refused = bad.key) }; return }
        _state.update { it.copy(busy = "", refused = null) }
        scope.launch {
            try {
                val stored = backend.setContacts(values)
                _state.update { it.copy(mine = (it.mine ?: MyContacts()).copy(contacts = stored)) }
                notice(UiText(R.string.contacts_saved))
                done(stored)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val app = ContactApp.of((e as? BackendException)?.takeIf { it.code == "contact_invalid" }?.detail)
                if (app != null) _state.update { it.copy(refused = app) } else notice(explain(e))
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    /** Shows my contacts to a friend ([name] for the notice), or stops. No longer friends: their section goes. */
    fun share(userId: String, name: String, on: Boolean) {
        _state.update { it.copy(busy = userId) }
        scope.launch {
            try {
                backend.shareContacts(userId, on)
                _state.update { s ->
                    val mine = s.mine?.let { m -> m.copy(sharedWith = if (on) m.sharedWith + userId else m.sharedWith - userId) }
                    s.copy(mine = mine, of = s.of + (userId to s.of[userId]?.copy(mineShared = on)))
                }
                notice(UiText(if (on) R.string.contacts_shared else R.string.contacts_stopped, name))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if ((e as? BackendException)?.code == "not_friends") ended(userId)
                notice(explain(e))
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    /** Unfriended or blocked: sharing ended both ways on the server. */
    fun ended(userId: String) = _state.update { s ->
        s.copy(mine = s.mine?.let { it.copy(sharedWith = it.sharedWith - userId) }, of = s.of + (userId to null))
    }
}
