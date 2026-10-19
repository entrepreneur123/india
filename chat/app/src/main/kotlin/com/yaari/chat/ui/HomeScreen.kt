package com.yaari.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.Settings
import com.yaari.chat.domain.flagOf
import kotlin.math.cos
import kotlin.math.sin
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
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.WorkspacePremium
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
    // Pushed screens slide in from the right and back out, like every native app.
    val page = when {
        screen != Sub.NONE -> screen
        s.room != null -> Sub.ROOM
        else -> Sub.NONE
    }
    AnimatedContent(page, transitionSpec = { pushPop(forward = targetState != Sub.NONE) }, label = "page") { p ->
        when (p) {
            Sub.SETTINGS -> SettingsScreen(vm, s, onBack = { screen = Sub.NONE }, onEditProfile = { screen = Sub.EDIT })
            Sub.EDIT -> EditProfileScreen(vm, me, onDone = { screen = Sub.NONE })
            Sub.NEW_POST -> NewPostScreen(onCancel = { screen = Sub.NONE }) { text, uri ->
                vm.addPost(text, uri)
                screen = Sub.NONE
            }
            Sub.ROOM -> s.room?.let { RoomScreen(vm, it) } ?: Box(Modifier.fillMaxSize())
            Sub.NONE -> Tabs(vm, s, me) { screen = it }
        }
    }
}

@Composable
private fun Tabs(vm: AppViewModel, s: UiState, me: Profile, open: (Sub) -> Unit) {
    val dark = s.tab == Tab.RANDOM
    val bg by animateColorAsState(if (dark) Brand.Night else MaterialTheme.colorScheme.background, tween(300), label = "bg")
    Column(Modifier.fillMaxSize().background(bg)) {
        AnimatedContent(
            s.tab, Modifier.weight(1f),
            transitionSpec = { (fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(220, delayMillis = 60), initialScale = 0.98f)) togetherWith fadeOut(tween(90)) },
            label = "tab",
        ) { tab ->
            when (tab) {
                Tab.ROOMS -> RoomsTab(vm, s, onSettings = { open(Sub.SETTINGS) })
                Tab.RANDOM -> RandomTab(vm, s, onSettings = { open(Sub.SETTINGS) })
                Tab.RECENT -> RecentTab(vm, s, onSettings = { open(Sub.SETTINGS) })
                Tab.ME -> MyProfileTab(
                    vm, s, onSettings = { open(Sub.SETTINGS) }, onEdit = { open(Sub.EDIT) }, onNewPost = { open(Sub.NEW_POST) },
                )
            }
        }
        BottomBar(s, me, dark, vm::selectTab)
    }
}

/** Push: new screen slides in from the right over a slightly receding one. Pop: the reverse. */
fun pushPop(forward: Boolean): ContentTransform {
    val spec = tween<IntOffset>(320, easing = FastOutSlowInEasing)
    return if (forward) {
        (slideInHorizontally(spec) { it } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(spec) { -it / 4 } + fadeOut(tween(250)))
    } else {
        (slideInHorizontally(spec) { -it / 4 } + fadeIn(tween(250))) togetherWith (slideOutHorizontally(spec) { it } + fadeOut(tween(200)))
    }
}

private enum class Sub { NONE, SETTINGS, EDIT, NEW_POST, ROOM }

@Composable
private fun BottomBar(s: UiState, me: Profile, dark: Boolean, onSelect: (Tab) -> Unit) {
    val bg = if (dark) Brand.Night else MaterialTheme.colorScheme.background
    Column(Modifier.background(bg).navigationBarsPadding()) {
        HorizontalDivider(color = if (dark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
            NavItem(s.tab == Tab.ROOMS, dark, Icons.Rounded.Groups, Icons.Outlined.Groups, R.string.tab_rooms) { onSelect(Tab.ROOMS) }
            NavItem(s.tab == Tab.RANDOM, dark, Icons.Rounded.Shuffle, Icons.Outlined.Shuffle, R.string.tab_random) { onSelect(Tab.RANDOM) }
            NavItem(s.tab == Tab.RECENT, dark, Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline, R.string.tab_recent, badge = s.incomingRequests) {
                onSelect(Tab.RECENT)
            }
            // Instagram-style: your own photo is the profile tab.
            val selected = s.tab == Tab.ME
            val tick = rememberTick()
            Column(
                Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { if (!selected) tick(); onSelect(Tab.ME) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(28.dp).bounceOn(selected).then(
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
    val tick = rememberTick()
    Column(
        Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { if (!selected) tick(); onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tint = when {
            dark -> if (selected) Color.White else Color.White.copy(alpha = 0.55f)
            else -> if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        }
        BadgedBox(badge = { if (badge > 0) Badge(containerColor = Brand.Pink) { Text("$badge") } }) {
            Icon(if (selected) on else off, null, Modifier.size(28.dp).bounceOn(selected), tint = tint)
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

/** A quick springy pop when something becomes selected (tab icons). */
private fun Modifier.bounceOn(selected: Boolean): Modifier = composed {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            scale.snapTo(0.78f)
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    graphicsLayer { scaleX = scale.value; scaleY = scale.value }
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

/** Flags drifting around you: the world is on the other side of the button. */
private val WorldFlags = listOf("IN", "BR", "KR", "US", "JP", "GB", "AE", "ES", "PH", "DE", "MX", "NG")

@Composable
private fun RandomTab(vm: AppViewModel, s: UiState, onSettings: () -> Unit) {
    val me = s.me ?: return
    Box(Modifier.fillMaxSize().aurora()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark()
            Spacer(Modifier.weight(1f))
            PlanChip(s, onClick = { vm.showPaywall(Paywall.GENERAL) })
            IconButton(onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings), tint = Color.White, modifier = Modifier.size(26.dp)) }
        }
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 32.dp).padding(bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Orbit(me, avatar = 124.dp, turnMs = 40_000, pulseMs = 2600)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.lobby_title), color = Color.White, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.lobby_sub), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TargetSelector(s.target, s.entitlements.canChooseGender, vm::setTarget)
            GradientButton(stringResource(R.string.start_chatting), vm::startMatching, height = 60.dp, shine = true)
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
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.pressScale(interaction).clip(CircleShape).background(Color.White.copy(alpha = if (premium) 0.12f else 0.08f))
            .then(if (premium) Modifier else Modifier.border(1.dp, Brand.gradient, CircleShape))
            .clickable(interaction, indication = null, enabled = !premium, onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Rounded.WorkspacePremium, null, tint = Brand.Orange, modifier = Modifier.size(16.dp))
        Text(
            stringResource(if (premium) R.string.plan_badge_plus else R.string.get_plus), color = Color.White,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        )
    }
}

/** Everyone / Women / Men as one segmented pill with a sliding white thumb; paid choices carry a lock until unlocked. */
@Composable
private fun TargetSelector(target: Target, unlocked: Boolean, onSelect: (Target) -> Unit) {
    val tick = rememberTick()
    BoxWithConstraints(Modifier.fillMaxWidth().clip(CircleShape).background(Color.White.copy(alpha = 0.09f)).padding(4.dp)) {
        val w = maxWidth / Target.entries.size
        val x by animateDpAsState(w * target.ordinal, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = "thumb")
        Box(Modifier.offset { IntOffset(x.roundToPx(), 0) }.width(w).height(42.dp).clip(CircleShape).background(Color.White))
        Row(Modifier.fillMaxWidth()) {
            Target.entries.forEach { t ->
                val selected = t == target
                val color by animateColorAsState(if (selected) Color.Black else Color.White.copy(alpha = 0.85f), label = "c")
                Row(
                    Modifier.weight(1f).height(42.dp).clip(CircleShape)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) { tick(); onSelect(t) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (t.isPaid && !unlocked) {
                        Icon(Icons.Rounded.Lock, stringResource(R.string.premium), Modifier.size(14.dp), tint = color)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(stringResource(t.label), color = color, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * You in the middle with radar rings, and flags of the world slowly circling: alive without being busy.
 * [turnMs] is one full turn of the flags; [pulseMs] one ring.
 */
@Composable
private fun Orbit(me: Profile, avatar: Dp, turnMs: Int, pulseMs: Int) {
    val t = rememberInfiniteTransition(label = "orbit")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(pulseMs, easing = LinearEasing)), label = "pulse")
    val turn by t.animateFloat(0f, 360f, infiniteRepeatable(tween(turnMs, easing = LinearEasing)), label = "turn")
    val float by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")
    Box(Modifier.size(avatar * 2.5f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = avatar.toPx() / 2
            val max = size.minDimension / 2
            for (i in 0 until 3) {
                val p = (phase + i / 3f) % 1f
                drawCircle(
                    brush = Brush.linearGradient(listOf(Brand.Pink, Brand.Orange)), radius = base + (max - base) * p,
                    alpha = (1f - p) * 0.5f, style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        // Flags on two rings turning in opposite directions.
        // Positions are read in the layout phase (lambda offsets), so the animation never recomposes this screen.
        WorldFlags.forEachIndexed { i, iso ->
            val inner = i % 2 == 0
            val radius = if (inner) avatar * 0.92f else avatar * 1.18f
            Box(
                Modifier.offset {
                    val angle = Math.toRadians((i * 360.0 / WorldFlags.size) + if (inner) turn.toDouble() else -turn.toDouble())
                    val r = radius.toPx()
                    IntOffset((r * cos(angle)).toInt(), (r * sin(angle) + float * 3.dp.toPx()).toInt())
                }
                    .size(if (inner) 34.dp else 28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) { Text(flagOf(iso), fontSize = if (inner) 18.sp else 14.sp) }
        }
        Box(Modifier.offset { IntOffset(0, (float * 2.dp.toPx()).toInt()) }) { Avatar(me, avatar, ring = true) }
    }
}

@Composable
fun MatchingScreen(vm: AppViewModel, s: UiState) {
    BackHandler { vm.cancelMatching() }
    val me = s.me ?: return
    Box(Modifier.fillMaxSize().aurora().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.align(Alignment.Center).padding(bottom = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Orbit(me, avatar = 104.dp, turnMs = 7_000, pulseMs = 1200)
            Spacer(Modifier.height(8.dp))
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
                color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            )
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier.fillMaxWidth().height(54.dp).pressScale(interaction).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable(interaction, indication = null, role = Role.Button) { vm.cancelMatching() },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.cancel), color = Color.White, style = MaterialTheme.typography.titleMedium) }
        }
    }
}
