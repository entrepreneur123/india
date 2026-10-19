package com.yaari.chat.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import com.yaari.chat.model.Profile
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WorkspacePremium
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val t = rememberInfiniteTransition(label = "boot")
                val a by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
                Wordmark(Modifier.graphicsLayer { alpha = a; scaleX = 0.96f + 0.04f * a; scaleY = 0.96f + 0.04f * a }, size = 48)
            }
            return@Surface
        }
        if (me == null) {
            OnboardingScreen(vm, s.onboarding)
            return@Surface
        }
        val chat = s.chat
        val dm = s.dm
        val top = when {
            chat?.video == VideoStatus.ACCEPTED && !chat.partnerLeft -> Top.VIDEO
            chat != null -> Top.CHAT
            s.searching -> Top.SEARCHING
            dm != null -> Top.DM
            else -> Top.HOME
        }
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                top,
                transitionSpec = {
                    when {
                        // Searching -> chat: the match "lands" (zoom), the reveal plays inside the chat screen.
                        targetState == Top.CHAT && initialState == Top.SEARCHING ->
                            (fadeIn(tween(250)) + scaleIn(tween(350), initialScale = 1.08f)) togetherWith fadeOut(tween(200))
                        targetState == Top.VIDEO || initialState == Top.VIDEO -> fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                        targetState == Top.SEARCHING || initialState == Top.SEARCHING -> fadeIn(tween(250)) togetherWith fadeOut(tween(250))
                        targetState == Top.HOME -> pushPop(forward = false)
                        else -> pushPop(forward = true)
                    }
                },
                label = "top",
            ) { t ->
                // Each branch reads the latest state; during an exit animation it may already be gone.
                when (t) {
                    Top.VIDEO -> s.chat?.let { c -> VideoCallScreen(vm, c.match.partner, me) }
                    Top.CHAT -> s.chat?.let { c -> ChatRoomScreen(vm, c, s.premium) }
                    Top.SEARCHING -> MatchingScreen(vm, s)
                    Top.DM -> s.dm?.let { d -> DmScreen(vm, d) }
                    Top.HOME -> HomeScreen(vm, s)
                }
            }
            // Profiles slide up over whatever is open, so the chat underneath (and its draft) stays alive.
            var lastViewed by remember { mutableStateOf<Profile?>(null) }
            s.viewing?.let { lastViewed = it }
            AnimatedVisibility(
                s.viewing != null,
                enter = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(260)) { it / 3 } + fadeOut(tween(200)),
            ) { lastViewed?.let { p -> ProfileScreen(vm, s.viewing ?: p, s) } }
        }
    }
    s.paywall?.let { reason -> PaywallSheet(vm, reason, s.prices) }
}

private enum class Top { HOME, SEARCHING, CHAT, VIDEO, DM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaywallSheet(vm: AppViewModel, paywall: Paywall, prices: Map<String, String>) {
    val activity = LocalActivity.current
    ModalBottomSheet(onDismissRequest = vm::dismissPaywall, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Brand.gradient).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(30.dp))
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
        Icon(Icons.Rounded.CheckCircle, null, tint = Brand.Pink, modifier = Modifier.size(22.dp))
        Text(text, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
