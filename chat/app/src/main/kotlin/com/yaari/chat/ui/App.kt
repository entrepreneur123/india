package com.yaari.chat.ui

import com.yaari.chat.VoiceService

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.yaari.chat.R
import com.yaari.chat.data.Connectivity
import com.yaari.chat.data.AppForeground
import com.yaari.chat.data.awaitVisible
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import com.yaari.chat.data.Products
import com.yaari.chat.data.VideoStatus
import com.yaari.chat.domain.ExitPolicy

/** [online]: false shows the offline banner above everything (see [Connectivity]). */
@Composable
fun YaariApp(vm: AppViewModel, online: StateFlow<Boolean> = Connectivity.online) {
    val s by vm.state.collectAsStateWithLifecycle()
    val connected by online.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(s.notice) {
        s.notice?.let {
            Toast.makeText(context, it.get(context), Toast.LENGTH_SHORT).show()
            vm.consumeNotice()
        }
    }
    // Every name on screen can show its verified seal (one batched trust() lookup for all of them).
    CompositionLocalProvider(LocalTrust provides vm.trust) {
        Box(Modifier.fillMaxSize()) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    // Slim and informative only: every screen stays usable, and the requests retry by themselves.
                    // The banner takes the status bar area, so the screen below does not pad for it a second time.
                    if (!connected) OfflineBanner()
                    Box(Modifier.weight(1f).then(if (connected) Modifier else Modifier.consumeWindowInsets(WindowInsets.statusBars))) {
                        Screens(vm, s)
                    }
                }
            }
            // "Verify your identity" over everything, the manners sheets.
            s.me?.takeIf { !s.booting }?.let { me -> TrustLayer(vm, me) }
        }
        s.paywall?.let { reason -> PaywallSheet(vm, reason, s.prices) }
        s.me?.takeIf { !s.booting }?.let { me -> SocialLayer(vm, me) }
    }
}

/**
 * Signed in: the unread counts (asked every 30 s while on screen, and at once when a notification arrives over
 * realtime), Notifications, the interests question and the tour (first run), and the notifications permission.
 */
@Composable
private fun SocialLayer(vm: AppViewModel, me: Profile) {
    val social by vm.social.state.collectAsStateWithLifecycle()
    LaunchedEffect(me.id) {
        launch { vm.social.follow() }
        while (true) {
            AppForeground.visible.awaitVisible()
            vm.social.refreshBadges()
            delay(SocialController.BADGES_MS)
        }
    }
    if (social.inboxOpen) InboxSheet(vm, social)
    if (social.interestsShown) InterestsSheet(vm, me, social)
    else if (social.firstRun == FirstRunStep.TOUR) TourDialog(vm, social.tourCard)
    NotificationsPermission(social.askNotifications, vm.social::notificationsAsked)
}

@Composable
private fun OfflineBanner() {
    Row(
        Modifier.fillMaxWidth().background(Brand.Ink2).statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CloudOff, null, Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.8f))
        Text(
            stringResource(R.string.offline_banner), Modifier.padding(start = 8.dp), color = Color.White,
            style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Screens(vm: AppViewModel, s: UiState) {
    val me = s.me
    if (s.booting) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val t = rememberInfiniteTransition(label = "boot")
            val a by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
            Wordmark(Modifier.graphicsLayer { alpha = a; scaleX = 0.96f + 0.04f * a; scaleY = 0.96f + 0.04f * a }, size = 48)
        }
        return
    }
    if (me == null) {
        OnboardingScreen(vm, s.onboarding)
        return
    }
    val chat = s.chat
    val dm = s.dm
    val top = when {
        chat?.video == VideoStatus.ACCEPTED && !chat.partnerLeft -> Top.VIDEO
        // Minimised, the chat is the bar over the tabs (still on).
        chat != null && !s.chatMinimised -> Top.CHAT
        s.searching -> Top.SEARCHING
        dm != null -> Top.DM
        else -> Top.HOME
    }
    val voice by vm.voice.state.collectAsStateWithLifecycle()
    // A room open full screen covers the screens: they are out of reach for TalkBack too (and the tests).
    val covered = voice?.expanded == true
    val player = remember { RoomPlayer() }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            top, if (covered) Modifier.clearAndSetSemantics { } else Modifier,
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
                // Keyed by match: a new chat never inherits the last one's draft, dialogs or exit sheet.
                Top.CHAT -> s.chat?.let { c -> key(c.match.id) { ChatRoomScreen(vm, c, s.premium) } }
                Top.SEARCHING -> MatchingScreen(vm, s)
                Top.DM -> s.dm?.let { d -> DmScreen(vm, d) }
                Top.HOME -> HomeScreen(vm, s)
            }
        }
        // A voice room full screen over everything but profiles (minimised, it is the bar over the tabs), and the
        // room's one music player (over its frame in the room, else out of sight, still playing).
        CompositionLocalProvider(LocalRoomPlayer provides player) {
            VoiceLayer(vm)
            RoomPlayerHost(vm)
        }
        VoiceService.Follow(vm)
        // A post opened full screen (a profile's grid, Notifications) and the comments sheet; profiles go over them.
        FeedLayer(vm, s)
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

private enum class Top { HOME, SEARCHING, CHAT, VIDEO, DM }

/** "yaari Plus": perks, two plans to choose between, one call to action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaywallSheet(vm: AppViewModel, paywall: Paywall, prices: Map<String, String>) {
    val activity = LocalActivity.current
    var plan by rememberSaveable { mutableStateOf(Products.PREMIUM) }
    ModalBottomSheet(
        onDismissRequest = vm::dismissPaywall, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Wordmark(size = 28, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            " " + stringResource(R.string.premium),
                            style = TextStyle(color = Brand.Pink, fontSize = 28.sp, fontFamily = Jakarta, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
                        )
                    }
                    Text(
                        stringResource(paywall.reason ?: R.string.premium_tagline), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp),
                    )
                }
                CircleIconButton(Icons.Rounded.Close, stringResource(R.string.not_now), vm::dismissPaywall, Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Benefit(Icons.Rounded.Group, stringResource(R.string.premium_benefit_gender))
                Benefit(Icons.Rounded.Videocam, stringResource(R.string.premium_benefit_video))
                Benefit(Icons.Rounded.Bolt, stringResource(R.string.premium_benefit_skip, ExitPolicy.PREMIUM_LOCK_MS / 1000.0, ExitPolicy.FREE_LOCK_MS / 1000))
            }
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanCard(
                    stringResource(R.string.plan_monthly), stringResource(R.string.plan_monthly_sub), prices[Products.PREMIUM],
                    selected = plan == Products.PREMIUM,
                ) { plan = Products.PREMIUM }
                PlanCard(
                    stringResource(R.string.plan_pass_title), stringResource(R.string.plan_pass_sub), prices[Products.GENDER_PASS],
                    selected = plan == Products.GENDER_PASS,
                ) { plan = Products.GENDER_PASS }
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                if (plan == Products.PREMIUM) stringResource(R.string.premium_buy, prices[Products.PREMIUM] ?: stringResource(R.string.premium_buy_default))
                else stringResource(R.string.pass_buy, prices[Products.GENDER_PASS] ?: stringResource(R.string.pass_buy_default)),
                { activity?.let { vm.buy(it, plan) } },
            )
            Text(
                stringResource(R.string.billing_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Text(text, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

/** A plan as a radio card; the chosen one gets a brand outline, a light brand tint and a filled radio dot. */
@Composable
private fun PlanCard(title: String, sub: String, price: String?, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().pressScale(interaction, 0.98f).clip(shape)
            .then(if (selected) Modifier.background(Brand.Pink.copy(alpha = 0.06f)).border(2.dp, Brand.Pink, shape) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape))
            .selectable(selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .then(if (selected) Modifier.background(Brand.Pink) else Modifier.border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White)) }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        price?.let {
            Text(
                it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 8.dp).widthIn(max = 120.dp),
            )
        }
    }
}
