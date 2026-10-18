package com.lottodost.app.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lottodost.app.R

enum class Tab(val label: Int, val icon: Int) {
    Results(R.string.tab_results, R.drawable.ic_tab_results),
    Check(R.string.tab_check, R.drawable.ic_tab_check),
    Tickets(R.string.tab_tickets, R.drawable.ic_tab_tickets),
    More(R.string.tab_more, R.drawable.ic_tab_more),
}

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val tickets by vm.tickets.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Results) }
    var openDrawId by rememberSaveable { mutableStateOf<String?>(null) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var scannedText by remember { mutableStateOf<String?>(null) }

    val draws = state.feed?.draws.orEmpty()
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
            Tab.Results -> ResultsScreen(state, onOpen = { openDrawId = it.id }, onRefresh = vm::refresh, modifier = m)
            Tab.Check -> CheckScreen(
                draws = draws,
                scannedText = scannedText,
                onScannedConsumed = { scannedText = null },
                onScan = { scanning = true },
                onSave = vm::save,
                onOpenDraw = { openDrawId = it.id },
                modifier = m,
            )
            Tab.Tickets -> TicketsScreen(tickets, draws, onRemove = vm::remove, onOpenDraw = { openDrawId = it.id }, modifier = m)
            Tab.More -> MoreScreen(modifier = m)
        }
    }

    FirstRunDisclaimer()
}

/** Shown once: we are not the government and sell nothing (Play "government information" policy). */
@Composable
private fun FirstRunDisclaimer() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("app", Context.MODE_PRIVATE) }
    var show by remember { mutableStateOf(!prefs.getBoolean("disclaimer_ok", false)) }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    if (!show) return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.disclaimer_title)) },
        text = { Text(stringResource(R.string.disclaimer_body)) },
        confirmButton = {
            TextButton(onClick = {
                prefs.edit().putBoolean("disclaimer_ok", true).apply()
                show = false
                if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text(stringResource(R.string.disclaimer_ok)) }
        },
    )
}
