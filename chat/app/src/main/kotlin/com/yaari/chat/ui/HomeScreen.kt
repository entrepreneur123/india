package com.yaari.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaari.chat.R
import com.yaari.chat.domain.flagOf
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
            Sub.ROOM -> s.room?.let { key(it.room.id) { RoomScreen(vm, it) } } ?: Box(Modifier.fillMaxSize())
            Sub.NONE -> Tabs(vm, s, me) { screen = it }
        }
    }
}

@Composable
private fun Tabs(vm: AppViewModel, s: UiState, me: Profile, open: (Sub) -> Unit) {
    val dark = s.tab == Tab.RANDOM
    val bg by animateColorAsState(if (dark) Brand.Night else MaterialTheme.colorScheme.background, tween(300), label = "bg")
    Column(Modifier.fillMaxSize().background(bg)) {
        Box(Modifier.weight(1f)) {
        AnimatedContent(
            s.tab, Modifier.fillMaxSize(),
            transitionSpec = { (fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(210, delayMillis = 90), initialScale = 0.96f)) togetherWith fadeOut(tween(90)) },
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
            // The voice room I'm in, minimised: it keeps playing while I look around.
            VoicePill(vm, Modifier.align(Alignment.BottomCenter))
        }
        BottomBar(s, me, dark, bg, vm::selectTab)
    }
}

/** Material shared-axis X: the new screen slides a short way in from the side while the old one fades out. */
fun pushPop(forward: Boolean): ContentTransform {
    val spec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
    val shift = { w: Int -> w / 10 }
    return if (forward) {
        (slideInHorizontally(spec) { shift(it) } + fadeIn(tween(210, delayMillis = 90))) togetherWith (slideOutHorizontally(spec) { -shift(it) } + fadeOut(tween(90)))
    } else {
        (slideInHorizontally(spec) { -shift(it) } + fadeIn(tween(210, delayMillis = 90))) togetherWith (slideOutHorizontally(spec) { shift(it) } + fadeOut(tween(90)))
    }
}

private enum class Sub { NONE, SETTINGS, EDIT, NEW_POST, ROOM }

// ---- Bottom bar: Material 3 navigation bar (Rooms · Random · Recent · Me) with a tonal pill on the selected tab ----

@Composable
private fun BottomBar(s: UiState, me: Profile, dark: Boolean, bg: Color, onSelect: (Tab) -> Unit) {
    val line = if (dark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.background(bg).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(line))
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            NavItem(s.tab == Tab.ROOMS, dark, R.string.tab_rooms, onClick = { onSelect(Tab.ROOMS) }) { color ->
                Icon(if (s.tab == Tab.ROOMS) Icons.Rounded.Forum else Icons.Outlined.Forum, null, Modifier.size(24.dp), tint = color)
            }
            NavItem(s.tab == Tab.RANDOM, dark, R.string.tab_random, onClick = { onSelect(Tab.RANDOM) }) { color ->
                Icon(Icons.Rounded.Shuffle, null, Modifier.size(24.dp), tint = color)
            }
            NavItem(s.tab == Tab.RECENT, dark, R.string.tab_recent, badge = s.incomingRequests, onClick = { onSelect(Tab.RECENT) }) { color ->
                Icon(if (s.tab == Tab.RECENT) Icons.Rounded.History else Icons.Outlined.History, null, Modifier.size(24.dp), tint = color)
            }
            // Your own photo is the profile tab.
            NavItem(s.tab == Tab.ME, dark, R.string.tab_me, onClick = { onSelect(Tab.ME) }) { Avatar(me, 24.dp) }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    selected: Boolean, dark: Boolean, label: Int, badge: Int = 0, onClick: () -> Unit, icon: @Composable (Color) -> Unit,
) {
    val tick = rememberTick()
    val interaction = remember { MutableInteractionSource() }
    val pill by animateColorAsState(
        when {
            !selected -> Color.Transparent
            dark -> Color.White.copy(alpha = 0.14f)
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        tween(150), label = "pill",
    )
    val pillWidth by animateDpAsState(if (selected) 64.dp else 40.dp, tween(200, easing = FastOutSlowInEasing), label = "pillW")
    Column(
        Modifier.weight(1f).fillMaxHeight()
            .clickable(interaction, indication = null, role = Role.Tab) { if (!selected) tick(); onClick() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.width(pillWidth).height(32.dp).clip(CircleShape).background(pill))
            icon(navColor(selected, dark))
            if (badge > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = (-2).dp).height(16.dp).widthIn(min = 16.dp)
                        .clip(CircleShape).background(Brand.Pink).padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("$badge", color = Color.White, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = Jakarta) }
            }
        }
        Text(
            stringResource(label), Modifier.padding(top = 4.dp), color = navColor(selected, dark), style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun navColor(selected: Boolean, dark: Boolean) = when {
    dark -> if (selected) Color.White else Color.White.copy(alpha = 0.72f)
    else -> if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
}

/** The light tabs' header: a title and the settings icon on the right (Material small top app bar). */
@Composable
fun TabHeader(title: String, onSettings: () -> Unit, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
        CircleIconButton(Icons.Outlined.Settings, stringResource(R.string.settings), onSettings, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Random chat: the dark, camera-like lobby ------------------------------------------------------

@Composable
private fun RandomTab(vm: AppViewModel, s: UiState, onSettings: () -> Unit) {
    val me = s.me ?: return
    Box(Modifier.fillMaxSize().aurora()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Wordmark(size = 24)
                Spacer(Modifier.weight(1f))
                PlanChip(s, onClick = { vm.showPaywall(Paywall.GENERAL) })
                CircleIconButton(Icons.Outlined.Settings, stringResource(R.string.settings), onSettings, tint = Color.White.copy(alpha = 0.85f))
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(160.dp).brandRing(), contentAlignment = Alignment.Center) {
                    Avatar(me, 144.dp)
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    me.nameAgeFlag(), color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.lobby_title), color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                TargetSelector(s.target, s.entitlements.canChooseGender, vm::setTarget)
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    stringResource(R.string.start_chatting), vm::startMatching,
                    leading = { Icon(Icons.Rounded.Shuffle, null, Modifier.size(20.dp), tint = Color.White) },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.lobby_rules), color = Color.White.copy(alpha = 0.66f),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PlanChip(s: UiState, onClick: () -> Unit) {
    val premium = s.premium
    Row(
        Modifier.heightIn(min = 48.dp).clip(CircleShape).clickable(enabled = !premium, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.height(32.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.WorkspacePremium, null, tint = Brand.PinkOnDark, modifier = Modifier.size(16.dp))
            Text(
                stringResource(if (premium) R.string.plan_badge_plus else R.string.get_plus), color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Everyone / Women / Men as one segmented pill with a sliding white thumb; paid choices carry a lock until unlocked. */
@Composable
private fun TargetSelector(target: Target, unlocked: Boolean, onSelect: (Target) -> Unit) {
    val tick = rememberTick()
    BoxWithConstraints(Modifier.fillMaxWidth().clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).padding(4.dp)) {
        val w = maxWidth / Target.entries.size
        val x by animateDpAsState(w * target.ordinal, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = "thumb")
        Box(Modifier.offset { IntOffset(x.roundToPx(), 0) }.width(w).height(46.dp).clip(CircleShape).background(Color.White))
        Row(Modifier.fillMaxWidth()) {
            Target.entries.forEach { t ->
                val selected = t == target
                val color by animateColorAsState(if (selected) Brand.Night else Color.White.copy(alpha = 0.7f), label = "c")
                val interaction = remember { MutableInteractionSource() }
                Row(
                    Modifier.weight(1f).height(46.dp).pressScale(interaction).clip(CircleShape)
                        .clickable(interaction, indication = null, role = Role.RadioButton) { tick(); onSelect(t) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (t.isPaid && !unlocked) {
                        Icon(Icons.Rounded.Lock, stringResource(R.string.premium), Modifier.size(13.dp), tint = color)
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        stringResource(t.label), color = color, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Flags circling the matching radar: the world is on the other side. */
private val OrbitFlags = listOf("IN", "MX", "KR", "TR", "BR", "JP", "NG", "FR")

@Composable
fun MatchingScreen(vm: AppViewModel, s: UiState) {
    BackHandler { vm.cancelMatching() }
    val me = s.me ?: return
    Box(Modifier.fillMaxSize().background(Brand.Night).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Pill(
                stringResource(R.string.looking_for, stringResource(s.target.label)),
                Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp),
                background = Color.White.copy(alpha = 0.07f), color = Color.White.copy(alpha = 0.8f),
                leading = { Icon(Icons.Outlined.Public, null, Modifier.size(15.dp), tint = Color.White.copy(alpha = 0.8f)) },
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Radar(300.dp)
                Canvas(Modifier.size(276.dp)) {
                    drawCircle(
                        Color.White.copy(alpha = 0.10f), radius = size.minDimension / 2,
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))),
                    )
                }
                Orbiting(OrbitFlags.size, 138.dp, turnMs = 28_000) { i ->
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Brand.Ink2).border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(flagOf(OrbitFlags[i]), fontSize = 21.sp) }
                }
                val breathe by rememberLoop(1600, from = 1f, to = 1.04f, reverse = true, easing = FastOutSlowInEasing, still = 1f)
                Box(
                    Modifier.graphicsLayer { scaleX = breathe; scaleY = breathe }.size(112.dp).clip(CircleShape).background(Brand.Night),
                    contentAlignment = Alignment.Center,
                ) { Avatar(me, 104.dp) }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.finding), color = Color.White, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp), textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.matching_tip),
                    color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                )
                // The last attempt failed (weak network); the search keeps going. Fixed height, so nothing jumps.
                Box(Modifier.height(28.dp), contentAlignment = Alignment.BottomCenter) {
                    androidx.compose.animation.AnimatedVisibility(s.reconnecting, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
                        Text(
                            stringResource(R.string.matching_reconnecting), color = Brand.PinkOnDark,
                            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier.fillMaxWidth().height(56.dp).pressScale(interaction, 0.97f).clip(CircleShape).background(Color.White.copy(alpha = 0.04f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable(interaction, indication = null, role = Role.Button) { vm.cancelMatching() },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.cancel), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

