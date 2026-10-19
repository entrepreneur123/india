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
import androidx.compose.foundation.layout.height
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
        BottomBar(s, me, dark, bg, vm::selectTab)
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

// ---- Bottom bar: Rooms · (Random) · Recent · Me, with the raised gradient Random button -----------

@Composable
private fun BottomBar(s: UiState, me: Profile, dark: Boolean, bg: Color, onSelect: (Tab) -> Unit) {
    val line = if (dark) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.background(bg).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(line))
        Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            NavItem(s.tab == Tab.ROOMS, dark, Icons.Rounded.Forum, Icons.Outlined.Forum, R.string.tab_rooms) { onSelect(Tab.ROOMS) }
            RandomButton(s.tab == Tab.RANDOM, dark, bg) { onSelect(Tab.RANDOM) }
            NavItem(s.tab == Tab.RECENT, dark, Icons.Rounded.History, Icons.Outlined.History, R.string.tab_recent, badge = s.incomingRequests) {
                onSelect(Tab.RECENT)
            }
            // Instagram-style: your own photo is the profile tab.
            val selected = s.tab == Tab.ME
            val tick = rememberTick()
            val interaction = remember { MutableInteractionSource() }
            Column(
                Modifier.weight(1f).height(56.dp).pressScale(interaction, 0.9f)
                    .clickable(interaction, indication = null, role = Role.Tab) { if (!selected) tick(); onSelect(Tab.ME) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(28.dp).bounceOn(selected).then(
                        if (selected) Modifier.border(2.dp, if (dark) Color.White else MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                    ),
                    contentAlignment = Alignment.Center,
                ) { Avatar(me, 22.dp) }
                NavLabel(R.string.tab_me, selected, dark)
            }
        }
    }
}

@Composable
private fun RowScope.RandomButton(selected: Boolean, dark: Boolean, bg: Color, onClick: () -> Unit) {
    val tick = rememberTick()
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.weight(1f).height(56.dp)
            .clickable(interaction, indication = null, role = Role.Tab) { if (!selected) tick(); onClick() },
    ) {
        // Same slot as the other tabs' icons, so the label lines up with theirs.
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Spacer(Modifier.height(26.dp))
            NavLabel(R.string.tab_random, selected, dark)
        }
        // Raised above the bar, ringed in the bar's own colour so it looks cut out of it.
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-26).dp).pressScale(interaction, 0.9f).size(66.dp)
                .glow(radius = 16.dp, offsetY = 10.dp, inset = 12.dp, alpha = 0.75f)
                .clip(CircleShape).background(bg).padding(5.dp).clip(CircleShape).background(Brand.gradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Shuffle, null, Modifier.size(25.dp).bounceOn(selected), tint = Color.White)
        }
    }
}

@Composable
private fun RowScope.NavItem(
    selected: Boolean, dark: Boolean, on: ImageVector, off: ImageVector, label: Int, badge: Int = 0, onClick: () -> Unit,
) {
    val tick = rememberTick()
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier.weight(1f).height(56.dp).pressScale(interaction, 0.9f)
            .clickable(interaction, indication = null, role = Role.Tab) { if (!selected) tick(); onClick() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(if (selected) on else off, null, Modifier.size(26.dp).bounceOn(selected), tint = navColor(selected, dark))
            if (badge > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp).height(17.dp).padding(0.dp)
                        .clip(CircleShape).background(if (dark) Brand.Night else MaterialTheme.colorScheme.background).padding(1.5.dp)
                        .clip(CircleShape).background(Brand.gradient).padding(horizontal = 4.5.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("$badge", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = Jakarta) }
            }
        }
        NavLabel(label, selected, dark)
    }
}

@Composable
private fun navColor(selected: Boolean, dark: Boolean) = when {
    dark -> if (selected) Color.White else Color.White.copy(alpha = 0.5f)
    else -> if (selected) MaterialTheme.colorScheme.onSurface else Color(0xFF9A9AA5)
}

@Composable
private fun NavLabel(label: Int, selected: Boolean, dark: Boolean, modifier: Modifier = Modifier) {
    Text(
        stringResource(label), modifier.padding(top = 2.dp), color = navColor(selected, dark), style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1,
    )
}

/** The light tabs' header: a big title and the settings gear in a soft circle on the right. */
@Composable
fun TabHeader(title: String, onSettings: () -> Unit, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
        CircleIconButton(Icons.Outlined.Settings, stringResource(R.string.settings), onSettings)
    }
}

// ---- Random chat: the dark, camera-like lobby (Azar-style) ---------------------------------------

@Composable
private fun RandomTab(vm: AppViewModel, s: UiState, onSettings: () -> Unit) {
    val me = s.me ?: return
    Box(Modifier.fillMaxSize().aurora()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Wordmark(color = Color.White)
                Spacer(Modifier.weight(1f))
                PlanChip(s, onClick = { vm.showPaywall(Paywall.GENERAL) })
                CircleIconButton(
                    Icons.Outlined.Settings, stringResource(R.string.settings), onSettings,
                    background = Color.White.copy(alpha = 0.08f), tint = Color.White,
                )
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                val float by rememberLoop(2400, from = -1f, to = 1f, reverse = true, easing = FastOutSlowInEasing, still = 0f)
                Box(
                    Modifier.size(176.dp).graphicsLayer { translationY = float * 3.dp.toPx() }.spinningRing(width = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(170.dp).clip(CircleShape).background(Brand.Night), contentAlignment = Alignment.Center) {
                        Avatar(me, 156.dp)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    me.nameAgeFlag(), color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.lobby_title), color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp)) {
                TargetSelector(s.target, s.entitlements.canChooseGender, vm::setTarget)
                Spacer(Modifier.height(16.dp))
                GradientButton(
                    stringResource(R.string.start_chatting), vm::startMatching, height = 64.dp, shine = true,
                    leading = { Icon(Icons.Rounded.Shuffle, null, Modifier.size(22.dp), tint = Color.White) },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.lobby_rules), color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PlanChip(s: UiState, onClick: () -> Unit) {
    val premium = s.premium
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.pressScale(interaction).clip(CircleShape).background(Color.White.copy(alpha = if (premium) 0.10f else 0.07f))
            .then(if (premium) Modifier else Modifier.border(1.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Brand.Pink, Brand.Orange)), CircleShape))
            .clickable(interaction, indication = null, enabled = !premium, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                Spacer(Modifier.height(32.dp))
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

