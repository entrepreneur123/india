package com.yaari.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yaari.chat.R
import com.yaari.chat.model.Profile
import com.yaari.chat.model.Target

/** Signed-in home: four tabs (Rooms, Random, Recent, Me) and the screens they open full-screen. */
@Composable
fun HomeScreen(vm: AppViewModel, s: UiState) {
    var screen by rememberSaveable { mutableStateOf(Sub.NONE) }
    val me = s.me ?: return
    when (screen) {
        Sub.SETTINGS -> return SettingsScreen(vm, s, onBack = { screen = Sub.NONE }, onEditProfile = { screen = Sub.EDIT })
        Sub.EDIT -> return EditProfileScreen(vm, me, onDone = { screen = Sub.NONE })
        Sub.NEW_POST -> return NewPostScreen(onCancel = { screen = Sub.NONE }) { text, uri ->
            vm.addPost(text, uri)
            screen = Sub.NONE
        }
        Sub.NONE -> Unit
    }
    s.room?.let { return RoomScreen(vm, it) }

    val dark = s.tab == Tab.RANDOM
    Column(Modifier.fillMaxSize().background(if (dark) Brand.Night else MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f)) {
            when (s.tab) {
                Tab.ROOMS -> RoomsTab(vm, s, onSettings = { screen = Sub.SETTINGS })
                Tab.RANDOM -> RandomTab(vm, s, onSettings = { screen = Sub.SETTINGS })
                Tab.RECENT -> RecentTab(vm, s, onSettings = { screen = Sub.SETTINGS })
                Tab.ME -> MyProfileTab(
                    vm, s, onSettings = { screen = Sub.SETTINGS }, onEdit = { screen = Sub.EDIT }, onNewPost = { screen = Sub.NEW_POST },
                )
            }
        }
        BottomBar(s, me, dark, vm::selectTab)
    }
}

private enum class Sub { NONE, SETTINGS, EDIT, NEW_POST }

@Composable
private fun BottomBar(s: UiState, me: Profile, dark: Boolean, onSelect: (Tab) -> Unit) {
    val bg = if (dark) Brand.Night else MaterialTheme.colorScheme.background
    Column(Modifier.background(bg).navigationBarsPadding()) {
        HorizontalDivider(color = if (dark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
            NavItem(s.tab == Tab.ROOMS, dark, Icons.Filled.Groups, Icons.Outlined.Groups, R.string.tab_rooms) { onSelect(Tab.ROOMS) }
            NavItem(s.tab == Tab.RANDOM, dark, Icons.Filled.Shuffle, Icons.Outlined.Shuffle, R.string.tab_random) { onSelect(Tab.RANDOM) }
            NavItem(s.tab == Tab.RECENT, dark, Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, R.string.tab_recent, badge = s.incomingRequests) {
                onSelect(Tab.RECENT)
            }
            // Instagram-style: your own photo is the profile tab.
            val selected = s.tab == Tab.ME
            Column(
                Modifier.weight(1f).clickable(role = Role.Tab, onClick = { onSelect(Tab.ME) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(28.dp).then(
                        if (selected) Modifier.border(2.dp, if (dark) Color.White else MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                    ),
                    contentAlignment = Alignment.Center,
                ) { Avatar(me, 24.dp) }
                NavLabel(R.string.tab_me, selected, dark)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    selected: Boolean, dark: Boolean, on: ImageVector, off: ImageVector, label: Int, badge: Int = 0, onClick: () -> Unit,
) {
    Column(Modifier.weight(1f).clickable(role = Role.Tab, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        val tint = when {
            dark -> if (selected) Color.White else Color.White.copy(alpha = 0.55f)
            else -> if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        }
        BadgedBox(badge = { if (badge > 0) Badge(containerColor = Brand.Pink) { Text("$badge") } }) {
            Icon(if (selected) on else off, null, Modifier.size(28.dp), tint = tint)
        }
        NavLabel(label, selected, dark)
    }
}

@Composable
private fun NavLabel(label: Int, selected: Boolean, dark: Boolean) {
    val color = when {
        dark -> if (selected) Color.White else Color.White.copy(alpha = 0.55f)
        else -> if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        stringResource(label), color = color, style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
    )
}

/** The light tabs' header: a big title and the settings gear on the right. */
@Composable
fun TabHeader(title: String, onSettings: () -> Unit, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        actions()
        IconButton(onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings), Modifier.size(26.dp)) }
    }
}

// ---- Random chat: the dark, camera-like lobby (Azar-style) ---------------------------------------

@Composable
private fun RandomTab(vm: AppViewModel, s: UiState, onSettings: () -> Unit) {
    val me = s.me ?: return
    Box(Modifier.fillMaxSize().nightGlow()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark()
            Spacer(Modifier.weight(1f))
            PlanChip(s, onClick = { vm.showPaywall(Paywall.GENERAL) })
            IconButton(onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings), tint = Color.White, modifier = Modifier.size(26.dp)) }
        }
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 32.dp).padding(bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Pulse(me, 132.dp, speedMs = 2600)
            Spacer(Modifier.height(28.dp))
            Text(stringResource(R.string.lobby_title), color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.lobby_sub), color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TargetSelector(s.target, s.entitlements.canChooseGender, vm::setTarget)
            GradientButton(stringResource(R.string.start_chatting), vm::startMatching, height = 58.dp)
            Text(
                stringResource(R.string.lobby_rules), color = Color.White.copy(alpha = 0.45f),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Near-black with a warm glow behind the center, sized to the screen. */
fun Modifier.nightGlow() = drawBehind {
    drawRect(Brand.Night)
    drawRect(
        Brush.radialGradient(
            listOf(Color(0xFF3A1626), Color.Transparent),
            center = Offset(size.width / 2, size.height * 0.42f), radius = size.maxDimension * 0.6f,
        ),
    )
}

@Composable
private fun PlanChip(s: UiState, onClick: () -> Unit) {
    val premium = s.premium
    Row(
        Modifier.clip(CircleShape).background(if (premium) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .then(if (premium) Modifier else Modifier.border(1.dp, Brand.gradient, CircleShape))
            .clickable(enabled = !premium, onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Filled.WorkspacePremium, null, tint = Brand.Orange, modifier = Modifier.size(16.dp))
        Text(
            stringResource(if (premium) R.string.plan_badge_plus else R.string.get_plus), color = Color.White,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        )
    }
}

/** Everyone / Women / Men as one segmented pill; the paid choices carry a lock until unlocked. */
@Composable
private fun TargetSelector(target: Target, unlocked: Boolean, onSelect: (Target) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(Brand.NightCard).padding(4.dp)) {
        Target.entries.forEach { t ->
            val selected = t == target
            Row(
                Modifier.weight(1f).height(40.dp).clip(CircleShape)
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(t) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (t.isPaid && !unlocked) {
                    Icon(Icons.Filled.Lock, stringResource(R.string.premium), Modifier.size(14.dp), tint = if (selected) Color.Black else Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    stringResource(t.label), color = if (selected) Color.Black else Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

/** An avatar with rings that keep spreading out, like a radar. */
@Composable
private fun Pulse(me: Profile, size: androidx.compose.ui.unit.Dp, speedMs: Int) {
    val t = rememberInfiniteTransition(label = "pulse")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(speedMs, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Box(Modifier.size(size * 2.2f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.toPx() / 2
            val max = this.size.minDimension / 2
            for (i in 0 until 3) {
                val p = (phase + i / 3f) % 1f
                drawCircle(
                    brush = Brush.linearGradient(listOf(Brand.Pink, Brand.Orange)),
                    radius = base + (max - base) * p,
                    alpha = (1f - p) * 0.55f,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        Avatar(me, size, ring = true)
    }
}

@Composable
fun MatchingScreen(vm: AppViewModel, s: UiState) {
    BackHandler { vm.cancelMatching() }
    val me = s.me ?: return
    val t = rememberInfiniteTransition(label = "dots")
    val breathe by t.animateFloat(0.92f, 1.04f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "breathe")
    Box(Modifier.fillMaxSize().nightGlow().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.align(Alignment.Center).padding(bottom = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.scale(breathe)) { Pulse(me, 112.dp, speedMs = 1400) }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.finding), color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Pill(
                stringResource(R.string.looking_for, stringResource(s.target.label)),
                background = Color.White.copy(alpha = 0.12f), color = Color.White,
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.matching_tip),
                color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            )
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .clickable(role = Role.Button) { vm.cancelMatching() },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.cancel), color = Color.White, style = MaterialTheme.typography.titleMedium) }
        }
    }
}
