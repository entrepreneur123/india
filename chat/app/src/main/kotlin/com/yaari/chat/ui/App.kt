package com.yaari.chat.ui

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaari.chat.R
import com.yaari.chat.data.Products
import com.yaari.chat.data.VideoStatus
import com.yaari.chat.domain.ExitPolicy

@Composable
fun YaariApp(vm: AppViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(s.notice) {
        s.notice?.let {
            Toast.makeText(context, it.get(context), Toast.LENGTH_SHORT).show()
            vm.consumeNotice()
        }
    }
    Surface(Modifier.fillMaxSize()) {
        val me = s.me
        if (s.booting) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Wordmark(size = 44) }
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
                s.searching -> MatchingScreen(vm, s)
                dm != null -> DmScreen(vm, dm)
                else -> HomeScreen(vm, s)
            }
            // Profiles open on top, so the chat underneath (and its draft) stays alive.
            s.viewing?.let { p -> ProfileScreen(vm, p, s) }
        }
    }
    s.paywall?.let { reason -> PaywallSheet(vm, reason, s.prices) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaywallSheet(vm: AppViewModel, paywall: Paywall, prices: Map<String, String>) {
    val activity = LocalActivity.current
    ModalBottomSheet(onDismissRequest = vm::dismissPaywall, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Brand.gradient).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Text(stringResource(R.string.premium_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            paywall.reason?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Benefit(stringResource(R.string.premium_benefit_gender))
                Benefit(stringResource(R.string.premium_benefit_video))
                Benefit(stringResource(R.string.premium_benefit_skip, ExitPolicy.PREMIUM_LOCK_MS / 1000.0, ExitPolicy.FREE_LOCK_MS / 1000))
            }
            GradientButton(
                stringResource(R.string.premium_buy, prices[Products.PREMIUM] ?: stringResource(R.string.premium_buy_default)),
                { activity?.let { vm.buy(it, Products.PREMIUM) } },
            )
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { activity?.let { vm.buy(it, Products.GENDER_PASS) } },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.pass_buy, prices[Products.GENDER_PASS] ?: stringResource(R.string.pass_buy_default)),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            TextButton(vm::dismissPaywall, Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.not_now), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.billing_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Benefit(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, null, tint = Brand.Pink, modifier = Modifier.size(22.dp))
        Text(text, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
