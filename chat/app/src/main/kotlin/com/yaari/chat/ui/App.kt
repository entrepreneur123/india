package com.yaari.chat.ui

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaari.chat.data.Products
import com.yaari.chat.data.VideoStatus
import com.yaari.chat.domain.ExitPolicy

@Composable
fun YaariApp(vm: AppViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(s.notice) {
        s.notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeNotice()
        }
    }
    Surface(Modifier.fillMaxSize()) {
        val me = s.me
        if (s.booting) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Surface
        }
        if (me == null) {
            OnboardingScreen(vm, s.onboarding)
            return@Surface
        }
        val chat = s.chat
        val dm = s.dm
        Box(Modifier.fillMaxSize()) {
            when {
                chat?.video == VideoStatus.ACCEPTED && !chat.partnerLeft -> VideoCallScreen(vm, chat.match.partner, me)
                chat != null -> ChatRoomScreen(vm, chat, s.premium)
                s.searching -> MatchingScreen(vm, s.target)
                dm != null -> DmScreen(vm, dm)
                else -> HomeScreen(vm, s)
            }
            // Profiles open on top, so the chat underneath (and its draft) stays alive.
            s.viewing?.let { p ->
                ProfileScreen(
                    p, inChat = chat?.match?.partner?.id == p.id, onBack = vm::closeProfile,
                    onMessage = { vm.openDm(p.id) }.takeIf { s.recents.any { it.partner.id == p.id } },
                    onReport = { reason -> vm.report(p.id, reason) },
                )
            }
        }
    }
    s.paywall?.let { feature -> Paywall(vm, feature, s.prices) }
}

@Composable
private fun Paywall(vm: AppViewModel, feature: String, prices: Map<String, String>) {
    val activity = LocalActivity.current
    AlertDialog(
        onDismissRequest = vm::dismissPaywall,
        title = { Text("Yaari Premium") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (feature != "Yaari Premium") Text("$feature needs Premium.")
                Text("• Choose to meet men or women")
                Text("• Video calls (the other person must accept)")
                Text("• Skip to the next person after ${ExitPolicy.PREMIUM_LOCK_MS / 1000.0}s instead of ${ExitPolicy.FREE_LOCK_MS / 1000}s")
                Button(
                    { activity?.let { vm.buy(it, Products.PREMIUM) } },
                    Modifier.fillMaxWidth(),
                ) { Text("Premium · ${prices[Products.PREMIUM] ?: "monthly"}") }
                OutlinedButton(
                    { activity?.let { vm.buy(it, Products.GENDER_PASS) } },
                    Modifier.fillMaxWidth(),
                ) { Text("Only gender choice · ${prices[Products.GENDER_PASS] ?: "24 hours"}") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(vm::dismissPaywall) { Text("Not now") } },
    )
}
