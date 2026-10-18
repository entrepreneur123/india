package com.lottodost.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lottodost.app.data.ResultRepository
import com.lottodost.app.data.SavedTicket
import com.lottodost.app.data.TicketStore
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ResultRepository.get(app)
    private val store = TicketStore.get(app)
    val state = repo.state
    val tickets = store.tickets

    init {
        viewModelScope.launch {
            repo.loadCache()   // instant first paint from disk
            repo.refresh()
        }
    }

    fun refresh() { viewModelScope.launch { repo.refresh() } }
    fun save(t: SavedTicket) = store.add(t)
    fun remove(t: SavedTicket) = store.remove(t)
}
