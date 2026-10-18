package com.lottodost.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lottodost.app.R
import com.lottodost.app.data.Prefs
import kotlinx.coroutines.delay

enum class Tab(val label: Int, val icon: Int) {
    Results(R.string.tab_results, R.drawable.ic_tab_results),
    Check(R.string.tab_check, R.drawable.ic_tab_check),
    Tickets(R.string.tab_tickets, R.drawable.ic_tab_tickets),
    Info(R.string.tab_info, R.drawable.ic_tab_info),
}

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    var languageChosen by remember { mutableStateOf(Prefs.languageChosen(ctx)) }
    var onboarded by remember { mutableStateOf(Prefs.onboarded(ctx)) }
    if (!languageChosen) {
        LanguageStep { tag ->
            Prefs.setLanguageChosen(ctx)
            languageChosen = true
            if (tag != currentLanguage()) setLanguage(tag) // recreates the activity in the new language
        }
        return
    }
    if (!onboarded) {
        DisclaimerStep { Prefs.setOnboarded(ctx); onboarded = true }
        return
    }

    val state by vm.state.collectAsStateWithLifecycle()
    val tickets by vm.tickets.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Results) }
    var openDrawId by rememberSaveable { mutableStateOf<String?>(null) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var scannedText by remember { mutableStateOf<String?>(null) }

    // Clock for countdowns, corrected to server time; ticks often enough for a minute display.
    val now by produceState(vm.now(), state.clockOffsetMs) {
        while (true) { value = vm.now(); delay(20_000) }
    }

    // While visible: refresh on return and poll only while a result is due.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.refresh()
            vm.pollWhileVisible()
        }
    }

    val draws = state.drawList
    val openDraw = draws.firstOrNull { it.id == openDrawId }

    when {
        scanning -> {
            BackHandler { scanning = false }
            ScanScreen(
                onClose = { scanning = false },
                onText = { text -> scannedText = text; scanning = false; tab = Tab.Check },
            )
            return
        }
        showHistory -> {
            BackHandler { showHistory = false }
            HistoryScreen(state.history, state.historyLoading, onLoad = vm::loadHistory, onBack = { showHistory = false })
            return
        }
        openDraw != null -> {
            BackHandler { openDrawId = null }
            DrawDetailScreen(openDraw, onBack = { openDrawId = null })
            return
        }
    }

    if (tab != Tab.Results) BackHandler { tab = Tab.Results }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (tab) {
            Tab.Results -> ResultsScreen(state, now, onOpen = { openDrawId = it.id }, onRefresh = vm::refresh,
                onLoadDay = vm::loadDay, modifier = m)
            Tab.Check -> CheckScreen(
                draws = draws,
                upcoming = state.index?.upcoming.orEmpty(),
                today = now.toLocalDate().toString(),
                scannedText = scannedText,
                onScannedConsumed = { scannedText = null },
                onScan = { scanning = true },
                onSave = vm::save,
                onOpenDraw = { openDrawId = it.id },
                onOpenHistory = { showHistory = true },
                modifier = m,
            )
            Tab.Tickets -> TicketsScreen(tickets, draws, onRemove = vm::remove, onOpenDraw = { openDrawId = it.id }, modifier = m)
            Tab.Info -> InfoScreen(state, now, onOpenHistory = { showHistory = true }, modifier = m)
        }
    }
}
