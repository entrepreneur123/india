package com.yaari.chat.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yaari.chat.domain.ExitPolicy
import com.yaari.chat.model.VideoState

@Composable
fun YaariApp(vm: AppViewModel = viewModel()) {
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
        if (me == null) {
            OnboardingScreen(vm, s.onboarding)
            return@Surface
        }
        val session = s.session
        Box(Modifier.fillMaxSize()) {
            when {
                session?.video == VideoState.IN_CALL -> VideoCallScreen(session.partner, me, vm::endVideo)
                session != null -> ChatRoomScreen(vm, session, s.premium)
                s.searching -> MatchingScreen(vm, s.target)
                s.dmPartnerId != null -> DmScreen(vm, s)
                else -> HomeScreen(vm, s)
            }
            // Profiles open on top, so the chat underneath (and its draft) stays alive.
            s.viewing?.let { p ->
                ProfileScreen(
                    p, inChat = session != null, onBack = vm::closeProfile,
                    onMessage = { vm.openDm(p.id) }.takeIf { s.recents.any { it.partner.id == p.id } },
                    onReport = { reason -> if (session?.partner?.id == p.id) vm.reportAndLeave(reason, next = false) else vm.report(p.id, reason) },
                )
            }
        }
    }
    s.paywall?.let { feature ->
        AlertDialog(
            onDismissRequest = vm::dismissPaywall,
            title = { Text("Yaari Premium") },
            text = {
                Column {
                    if (feature != "Yaari Premium") Text("$feature needs Premium.\n")
                    Text("• Choose to meet men or women")
                    Text("• Video calls (the other person must accept)")
                    Text("• Skip to the next person after ${ExitPolicy.PREMIUM_LOCK_MS / 1000.0}s instead of ${ExitPolicy.FREE_LOCK_MS / 1000}s")
                    Text("\nPrototype: no payment is taken.")
                }
            },
            confirmButton = { TextButton({ vm.setPremium(true) }) { Text("Turn on Premium") } },
            dismissButton = { TextButton(vm::dismissPaywall) { Text("Not now") } },
        )
    }
}
