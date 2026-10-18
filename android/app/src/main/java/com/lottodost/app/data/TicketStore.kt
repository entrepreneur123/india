package com.lottodost.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Tickets the user saved; checked automatically when their draw's result appears. */
@Serializable
data class SavedTicket(
    val number: String,      // as Ticket.display()
    val date: String,        // draw date yyyy-MM-dd
    val slot: String,        // NL1PM, SK6PM, NL8PM, NLLAXMI
    val addedAt: Long,
    val notified: Boolean = false,
) {
    val key: String get() = "${date}_${slot}_$number"
}

class TicketStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("tickets", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _tickets = MutableStateFlow(read())
    val tickets: StateFlow<List<SavedTicket>> = _tickets.asStateFlow()

    private fun read(): List<SavedTicket> =
        runCatching { json.decodeFromString<List<SavedTicket>>(prefs.getString("list", "[]")!!) }
            .getOrDefault(emptyList())

    @Synchronized
    private fun write(list: List<SavedTicket>) {
        // Keep the list bounded: tickets older than the 7-day feed can no longer be checked.
        val trimmed = list.sortedByDescending { it.addedAt }.take(200)
        prefs.edit().putString("list", json.encodeToString(trimmed)).apply()
        _tickets.value = trimmed
    }

    fun add(t: SavedTicket) = write(_tickets.value.filterNot { it.key == t.key } + t)
    fun remove(t: SavedTicket) = write(_tickets.value.filterNot { it.key == t.key })
    fun markNotified(keys: Set<String>) =
        write(_tickets.value.map { if (it.key in keys) it.copy(notified = true) else it })

    companion object {
        @Volatile private var instance: TicketStore? = null
        fun get(context: Context): TicketStore =
            instance ?: synchronized(this) {
                instance ?: TicketStore(context.applicationContext).also { instance = it }
            }
    }
}
