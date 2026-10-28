package com.lottodost.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lottodost.app.data.ResultRepository
import com.lottodost.app.data.SavedTicket
import com.lottodost.app.data.TicketStore
import com.lottodost.app.domain.DrawSchedule
import com.lottodost.app.push.Push
import com.lottodost.app.widget.ResultsWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ResultRepository.get(app)
    private val store = TicketStore.get(app)
    val state = repo.state
    val tickets = store.tickets

    init {
        viewModelScope.launch {
            repo.loadCache()   // instant first paint from disk
            if (repo.refresh()) ResultsWidget.update(app)
        }
    }

    /** IST "now", corrected by the server clock (many phones in the field have a wrong clock). */
    fun now(): ZonedDateTime = ZonedDateTime.now(DrawSchedule.IST).plusNanos(state.value.clockOffsetMs * 1_000_000)

    fun refresh() { viewModelScope.launch { if (repo.refresh()) ResultsWidget.update(getApplication()) } }

    fun loadDay(date: String) {
        viewModelScope.launch { repo.loadDay(date, now().toLocalDate().toString()) }
    }

    /**
     * Runs while the app is visible: polls today.json every ~30 s (jittered) only while a draw
     * has happened and its result is not in yet. Otherwise it just re-checks the schedule.
     */
    suspend fun pollWhileVisible() {
        while (true) {
            val now = now()
            val today = now.toLocalDate().toString()
            val draws = state.value.draws.values
            val wait = DrawSchedule.pollDelayMs(now, { slot -> draws.any { it.slot == slot && it.date == today && it.status == "OK" } })
            if (wait == null) {
                delay(60_000)
            } else {
                delay(wait)
                if (repo.refresh(ResultRepository.Mode.LIGHT)) ResultsWidget.update(getApplication())
            }
        }
    }

    fun loadHistory() { viewModelScope.launch { repo.loadHistory() } }

    fun save(t: SavedTicket) {
        store.add(t)
        Push.sync(getApplication())   // wake this phone for that draw's result even if its alert is off
    }
    fun remove(t: SavedTicket) = store.remove(t)
}
