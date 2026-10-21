package com.yaari.chat.ui

import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.semantics.heading
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.semantics.stateDescription
import com.yaari.chat.model.RoomCategory
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaari.chat.Flavor
import com.yaari.chat.R
import com.yaari.chat.model.AudioStatus
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceLine
import com.yaari.chat.model.VoiceMember
import com.yaari.chat.model.VoiceReaction
import com.yaari.chat.model.VoiceRole
import com.yaari.chat.model.VoiceRoom
import kotlinx.coroutines.launch

// ---- A voice room: full screen and always dark (party rooms are a night-time thing, and the stage reads better) ---
// Stage on top, chat below, controls at the bottom; the web's components/screens/voice/*.

/** The room over everything while expanded; the back gesture minimises it to the pill (still in the room). */
@Composable
fun VoiceLayer(vm: AppViewModel) {
    val s by vm.voice.state.collectAsStateWithLifecycle()
    var last by remember { mutableStateOf(s) }
    s?.let { last = it }
    AnimatedVisibility(
        s?.expanded == true,
        enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 8 } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220)) { it / 8 } + fadeOut(tween(180)),
    ) {
        (s ?: last)?.let { RoomTheme { VoiceRoomScreen(vm, it) } }
    }
}

/** The room's colours: always dark, with light text by default (the room has no Surface of its own). */
@Composable
fun RoomTheme(content: @Composable () -> Unit) = NightTheme {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
}

/** The room's link (/r/<id>, or /r/<code> for a private room): the app opens it when installed, else the web app. */
internal fun copyLink(context: Context, room: VoiceRoom) {
    val link = com.yaari.chat.data.DeepLink.roomLink(room.id, room.code, room.rules.isPrivate)
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Yaari", link))
    Toast.makeText(context, context.getString(R.string.voice_link_copied), Toast.LENGTH_SHORT).show()
}

/** Which sheet is open over the room. */
sealed interface VoiceSheet {
    data class Person(val userId: String) : VoiceSheet
    data object Requests : VoiceSheet
    data class Audience(val inviteSeat: Int? = null) : VoiceSheet
    data class Seat(val seat: Int) : VoiceSheet
    data object Queue : VoiceSheet
    data object Options : VoiceSheet
    data object Settings : VoiceSheet
    data object ReportRoom : VoiceSheet
    data object Invite : VoiceSheet
}

/**
 * A room, full screen and always dark. Every room has the chat (text, icebreakers, photos), the stage, shared music and
 * Ludo; the category decides what comes first (as the web's voice-room.tsx):
 *  - talk: the chat, under a slim strip of the people on the stage;
 *  - music: the shared player with the song requests by vote;
 *  - games: Ludo (or a card to start it) with the room's leaderboard.
 */
@Composable
fun VoiceRoomScreen(vm: AppViewModel, s: VoiceState) {
    BackHandler(onBack = vm.voice::minimise)
    val context = LocalContext.current
    val room = s.room
    val me = s.me
    var sheet by remember { mutableStateOf<VoiceSheet?>(null) }
    var musicOpen by rememberSaveable { mutableStateOf(false) }
    var stageOpen by rememberSaveable { mutableStateOf(false) }
    val levels = vm.voice.levels.collectAsStateWithLifecycle()
    val list by vm.voice.list.collectAsStateWithLifecycle()
    val actor: VoiceActor = { a, ok, done -> vm.voice.act(a, ok, done) }
    val topic = room?.category?.topic() ?: Topic.CHAT
    val glow = topicColor(topic).copy(alpha = 0.24f)
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).drawBehind {
            // One soft glow in the room's category colour.
            drawRect(Brush.radialGradient(listOf(glow, Color.Transparent), Offset(size.width / 2, 0f), size.width * 0.75f))
        },
    ) {
        if (room == null || me == null) {
            Joining(vm.voice::minimise)
            return@Box
        }
        var barHeight by remember { mutableIntStateOf(0) }
        var tray by remember { mutableStateOf(false) }
        val person: (String) -> Unit = { sheet = VoiceSheet.Person(it) }
        val kind = room.category
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Header(room, onMinimise = vm.voice::minimise, onMore = { sheet = VoiceSheet.Options }, onLeave = vm.voice::leave)
            room.announcement?.let { Announcement(it, topicColor(topic), onEdit = if (s.isHost) ({ sheet = VoiceSheet.Settings }) else null) }
            AudioNotice(s)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val game = s.ludoShown
                // The game takes the top of the room; the chat goes on under it. The board is as big as fits.
                val boardMax = minOf(maxWidth - 24.dp, maxHeight - MIN_CHAT - LUDO_CHROME).coerceAtLeast(200.dp)
                val topMax = when {
                    game != null -> maxHeight - MIN_CHAT
                    kind == RoomCategory.TALK -> maxHeight * 0.45f
                    else -> maxHeight * 0.62f
                }
                val gameView: (@Composable () -> Unit)? = when {
                    game != null -> ({
                        LudoPanel(
                            room, game, s.isMod, levels, boardMax,
                            LudoHandlers(
                                act = { a, done -> vm.voice.ludo(a, done) }, showStage = { vm.voice.showLudo(false) },
                                askClose = { vm.voice.askCloseLudo(true) }, dismiss = vm.voice::dismissLudo, person = person,
                            ),
                        )
                    })
                    s.ludoHidden -> ({ LudoBackPill { vm.voice.showLudo(true) } })
                    else -> null
                }
                val stage: @Composable () -> Unit = {
                    Column(Modifier.fillMaxWidth()) {
                        StageStrip(room, levels, s.reactions, stageOpen, { stageOpen = !stageOpen }, person)
                        AnimatedVisibility(stageOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            Column {
                                Stage(room, levels, emptyList(), small = true, onPerson = person, onEmpty = { seat -> vm.voice.tapSeat(seat) { sheet = VoiceSheet.Seat(it) } })
                                ListenersStrip(room, s.hands) { sheet = VoiceSheet.Audience() }
                            }
                        }
                    }
                }
                // Music outside a music room: the slim "listen" bar (the player opens on a tap).
                val musicBar: @Composable () -> Unit = {
                    room.track?.let { t ->
                        if (kind != RoomCategory.MUSIC) MusicPanel(t, room.queue, s.isHost, musicOpen, { musicOpen = it }, { sheet = VoiceSheet.Queue }) { a -> vm.voice.act(a) }
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = topMax).verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (kind) {
                            RoomCategory.MUSIC -> {
                                gameView?.invoke()
                                MusicHero(room, s.isHost, s.adding, onQueue = { sheet = VoiceSheet.Queue }, act = { a -> vm.voice.act(a) }, vote = vm.voice::vote)
                                stage()
                            }
                            RoomCategory.GAMES -> {
                                gameView?.invoke() ?: StartLudoCard(s.isMod) { vm.voice.ludoButton() }
                                Leaderboard(s.leaders, room.meId)
                                musicBar()
                                stage()
                            }
                            RoomCategory.TALK -> {
                                stage()
                                musicBar()
                                gameView?.invoke()
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    VoiceChat(s.lines, Modifier.weight(1f), onIcebreaker = if (kind == RoomCategory.TALK) vm.voice::icebreaker else null, onPerson = person)
                }
            }
            ControlBar(
                vm, s, me, Modifier.onSizeChanged { barHeight = it.height },
                onRequests = { sheet = VoiceSheet.Requests }, onMusic = { sheet = VoiceSheet.Queue }, onReactions = { tray = !tray },
            )
        }
        if (tray) {
            Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { tray = false })
            ReactionTray(
                Modifier.align(Alignment.BottomStart).navigationBarsPadding().imePadding()
                    .padding(start = 12.dp, bottom = with(LocalDensity.current) { barHeight.toDp() } + 8.dp),
            ) { vm.voice.act(VoiceAction.React(it)) }
        }
        VoiceSheets(vm, s, room, sheet, actor, onSheet = { sheet = it })
        if (s.confirmEnd) EndDialog(s.ending, onCancel = vm.voice::dismissEnd, onEnd = vm.voice::end)
        if (s.confirmLudoClose) LudoCloseDialog(onCancel = { vm.voice.askCloseLudo(false) }, onClose = vm.voice::closeLudo)
        list.created?.let { RoomCreatedDialog(it.title, it.code, vm.voice::dismissCreated) }
    }
}

/** Shown until the room's state arrives: the header and the shape of the stage. */
@Composable
private fun Joining(onMinimise: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.voice_minimise), onMinimise, iconSize = 28.dp)
            Box(Modifier.size(180.dp, 20.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        }
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).clip(CircleShape).shimmer())
            repeat(2) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(4) { Box(Modifier.size(54.dp).clip(CircleShape).shimmer()) }
                }
            }
            Text(stringResource(R.string.voice_joining), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Header(room: VoiceRoom, onMinimise: () -> Unit, onMore: () -> Unit, onLeave: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.voice_minimise), onMinimise, iconSize = 28.dp)
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                room.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TopicChip(room.category.topic(), stringResource(room.category.label), height = 24.dp)
                Icon(Icons.Outlined.Group, null, Modifier.padding(start = 10.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.rooms_here, room.members.size, room.rules.maxMembers), Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
        }
        CircleIconButton(Icons.Rounded.MoreVert, stringResource(R.string.voice_more), onMore)
        CircleIconButton(Icons.AutoMirrored.Rounded.Logout, stringResource(R.string.voice_leave), onLeave, tint = MaterialTheme.colorScheme.error)
    }
}

/** The host's announcement, pinned under the header (the host edits it from here). */
@Composable
private fun Announcement(text: String, color: Color, onEdit: (() -> Unit)?) {
    val label = stringResource(R.string.room_announcement)
    Row(
        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.14f)).border(1.dp, color.copy(alpha = 0.26f), RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = if (onEdit != null) 2.dp else 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.PushPin, null, Modifier.padding(top = 2.dp).size(15.dp), tint = color)
        Text(
            text, Modifier.weight(1f).padding(start = 8.dp).semantics { contentDescription = "$label: $text" },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp), maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        if (onEdit != null) {
            CircleIconButton(Icons.Outlined.Edit, stringResource(R.string.room_settings), onEdit, Modifier.offset(y = (-6).dp), size = 32.dp, iconSize = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The people on the stage, small, with their speaking rings; or, with nobody there, why there's no sound. */
@Composable
private fun StageStrip(
    room: VoiceRoom, levels: State<Map<String, Float>>, reactions: List<FloatingReaction>, open: Boolean, onToggle: () -> Unit, onPerson: (String) -> Unit,
) {
    val stage = room.members.filter { it.seat != null }.sortedBy { it.seat }
    val stageName = stringResource(R.string.voice_stage)
    Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (stage.isNotEmpty()) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).semantics { paneTitle = stageName }) {
                    stage.forEach { m -> Box(Modifier.width(68.dp)) { SeatPerson(m, 40.dp, levels, null) { onPerson(m.profile.id) } } }
                }
            } else {
                Row(Modifier.weight(1f).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MicOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        stringResource(R.string.room_stage_empty), Modifier.padding(start = 10.dp), fontSize = 12.sp, lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Jakarta,
                    )
                }
            }
            val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
            Row(
                Modifier.padding(start = 8.dp).height(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable(role = Role.Button, onClick = onToggle).semantics { stateDescription = if (open) "expanded" else "collapsed" }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (stage.isNotEmpty()) stringResource(R.string.room_on_stage, stage.size) else stageName,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                )
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.padding(start = 2.dp).size(18.dp).graphicsLayer { rotationZ = turn })
            }
        }
        if (!open) ReactionLayer(reactions, Modifier.matchParentSize())
    }
}

/** Only when the audio needs a word: reconnecting, can't connect, or paused for a phone call. */
@Composable
private fun AudioNotice(s: VoiceState) {
    val a = s.audio ?: return
    val text = when {
        a.interrupted -> R.string.voice_audio_paused
        a.status == AudioStatus.RECONNECTING -> R.string.voice_audio_reconnecting
        a.status == AudioStatus.FAILED -> R.string.voice_audio_failed
        else -> return
    }
    Text(
        stringResource(text), Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---- the stage ----

@Composable
private fun Stage(
    room: VoiceRoom, levels: State<Map<String, Float>>, reactions: List<FloatingReaction>, small: Boolean,
    onPerson: (String) -> Unit, onEmpty: (Int) -> Unit,
) {
    val hostPx = if (small) 56.dp else 72.dp
    val seatPx = if (small) 44.dp else 54.dp
    // The newest reaction of each person shows on their seat while it floats.
    val latest = reactions.associateBy { it.userId }
    val stage = stringResource(R.string.voice_stage)
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (small) 8.dp else 16.dp, bottom = 8.dp).semantics { paneTitle = stage }) {
        Column(Modifier.fillMaxWidth()) {
            room.bySeat(0)?.let { host ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SeatPerson(host, hostPx, levels, latest[host.profile.id], wide = true) { onPerson(host.profile.id) }
                }
            }
            Spacer(Modifier.height(if (small) 4.dp else 12.dp))
            (1 until VoiceRoom.SEATS).chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = if (small) 2.dp else 6.dp)) {
                    row.forEach { seat ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            val who = room.bySeat(seat)
                            if (who != null) SeatPerson(who, seatPx, levels, latest[who.profile.id]) { onPerson(who.profile.id) }
                            else EmptySeat(seat, seatPx, room.locked(seat)) { onEmpty(seat) }
                        }
                    }
                }
            }
        }
        ReactionLayer(reactions, Modifier.matchParentSize())
    }
}

@Composable
private fun roleLabel(m: VoiceMember) = when (m.role) {
    VoiceRole.HOST -> stringResource(R.string.voice_host)
    VoiceRole.COHOST -> stringResource(R.string.voice_cohost)
    else -> null
}

@Composable
private fun SeatPerson(member: VoiceMember, px: Dp, levels: State<Map<String, Float>>, reaction: FloatingReaction?, wide: Boolean = false, onClick: () -> Unit) {
    val role = roleLabel(member)
    val label = listOfNotNull(member.profile.name, role, if (member.muted) stringResource(R.string.voice_mic_off) else null).joinToString(", ")
    val badge = maxOf(18.dp, px * 0.34f)
    val interaction = remember { MutableInteractionSource() }
    Column(
        // Not clipped: the speaking halo reaches past the seat.
        Modifier.then(if (wide) Modifier.width(128.dp) else Modifier.fillMaxWidth())
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(px)) {
            SpeakingRing(levels, member.profile.id, px)
            Avatar(member.profile, px, Modifier.pressScale(interaction, 0.95f))
            if (member.muted) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(2.dp, 2.dp).size(badge).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background).padding(2.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.MicOff, null, Modifier.size(badge * 0.56f), tint = MaterialTheme.colorScheme.onSurface) }
            }
            key(reaction?.key) {
                if (reaction != null) {
                    val pop = remember { Animatable(0.4f) }
                    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(6.dp, (-4).dp).size(badge + 4.dp).scale(pop.value).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background).padding(2.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) { GlyphIcon(reaction.kind.glyph(), size = badge * 0.78f) }
                }
            }
        }
        Text(
            member.profile.name, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = (if (wide) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium).copy(fontWeight = FontWeight.SemiBold),
        )
        role?.let { RoleBadge(it) }
    }
}

@Composable
fun RoleBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.padding(top = 2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 6.dp, vertical = 1.dp),
        color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Jakarta,
    )
}

/**
 * The ring around someone talking: it grows and brightens with their audio level (X Spaces / party apps). With
 * animations off it is a plain ring that shows while they speak.
 */
@Composable
fun SpeakingRing(levels: State<Map<String, Float>>, id: String, px: Dp) {
    val reduce = LocalReducedMotion.current
    val target by remember(id) { derivedStateOf { levels.value[id] ?: 0f } }
    val level by animateFloatAsState(target, if (reduce) tween(0) else tween(150, easing = LinearEasing), label = "level")
    val ring = MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(px).drawBehind {
            val on = level > VoiceController.SPEAKING
            if (!on) return@drawBehind
            val r = size.minDimension / 2
            if (!reduce) drawCircle(ring.copy(alpha = 0.25f * level), radius = (r + 4.dp.toPx()) * (1.06f + level * 0.16f))
            drawCircle(
                ring.copy(alpha = if (reduce) 1f else 0.55f + level * 0.45f),
                radius = (r + 3.dp.toPx()) * (if (reduce) 1f else 1f + level * 0.05f), style = Stroke(2.dp.toPx()),
            )
        },
    )
}

@Composable
private fun EmptySeat(seat: Int, px: Dp, locked: Boolean, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val line = MaterialTheme.colorScheme.outline
    val label = stringResource(if (locked) R.string.voice_seat_locked else R.string.voice_seat_empty, seat)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(px).then(
                if (locked) Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                else Modifier.drawBehind {
                    val w = 1.5.dp.toPx()
                    drawCircle(line, radius = size.minDimension / 2 - w / 2, style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (locked) Icons.Rounded.Lock else Icons.Rounded.Add, null, Modifier.size(px * if (locked) 0.38f else 0.42f), tint = muted)
        }
        Text(stringResource(R.string.voice_seat, seat), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1)
    }
}

/** Reactions float up over the stage (X Spaces); with animations off they only fade in and out in place. */
@Composable
private fun ReactionLayer(reactions: List<FloatingReaction>, modifier: Modifier) {
    val reduce = LocalReducedMotion.current
    BoxWithConstraints(modifier) {
        val width = maxWidth
        reactions.forEach { r ->
            key(r.key) {
                val t = remember { Animatable(0f) }
                LaunchedEffect(Unit) { t.animateTo(1f, tween(2_400, easing = LinearEasing)) }
                val p = t.value
                val alpha = when {
                    p < 0.12f -> p / 0.12f
                    p > 0.8f -> (1f - p) / 0.2f
                    else -> 1f
                }
                val rise = if (reduce) 0f else 220f * FastOutSlowInEasing.transform(p)
                val scale = if (reduce) 1f else when {
                    p < 0.15f -> 0.6f + (p / 0.15f) * 0.55f
                    p < 0.7f -> 1.15f - ((p - 0.15f) / 0.55f) * 0.15f
                    else -> 1f - ((p - 0.7f) / 0.3f) * 0.1f
                }
                val sway = if (reduce) 0f else (kotlin.math.sin(p * 9f) * 8f)
                Box(
                    Modifier.align(Alignment.BottomStart).offset(x = width * r.x - 15.dp + sway.dp, y = -rise.dp - 8.dp)
                        .graphicsLayer { this.alpha = alpha.coerceIn(0f, 1f); scaleX = scale; scaleY = scale },
                ) { GlyphIcon(r.kind.glyph(), size = 30.dp) }
            }
        }
    }
}

@Composable
private fun ListenersStrip(room: VoiceRoom, hands: Int, onClick: () -> Unit) {
    val audience = room.audience
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.voice_listening_n, audience.size), style = MaterialTheme.typography.titleSmall, maxLines = 1)
        FaceStack(audience.take(7).map { it.profile }, 26.dp, MaterialTheme.colorScheme.background, Modifier.padding(start = 12.dp).weight(1f))
        if (hands > 0) {
            GlyphIcon(Glyph.HAND, size = 16.dp)
            Text("$hands", Modifier.padding(start = 4.dp, end = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- the controls ----

/** The microphone needs the permission in the live build (asked the first time I unmute, not before). */
@Composable
private fun rememberMic(vm: AppViewModel): (Boolean) -> Unit {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.voice.act(VoiceAction.Mic(true)) else vm.voice.say(UiText(R.string.err_mic_denied))
    }
    return { on ->
        val missing = Flavor.voiceMicPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        if (on && missing) ask.launch(Manifest.permission.RECORD_AUDIO) else vm.voice.act(VoiceAction.Mic(on))
    }
}

/**
 * The same controls in every room: the composer (photo, icebreaker, text), reactions, the music and Ludo, the hands
 * raised (hosts and co-hosts), and the main button: the microphone on the stage, else "Join voice" or "Raise hand".
 */
@Composable
private fun ControlBar(
    vm: AppViewModel, s: VoiceState, me: VoiceMember, modifier: Modifier,
    onRequests: () -> Unit, onMusic: () -> Unit, onReactions: () -> Unit,
) {
    val mic = rememberMic(vm)
    val room = s.room ?: return
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        VoiceComposer(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp), s.photoBusy,
            onPhoto = vm.voice::sendPhoto, onIcebreaker = vm.voice::icebreaker,
        ) { text, failed -> vm.voice.send(text, failed) }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(stringResource(R.string.voice_react), onReactions) {
                Icon(Icons.Outlined.AddReaction, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(BAR_GAP))
            RoundButton(stringResource(R.string.voice_music), onMusic) { TopicIcon(Topic.MUSIC, size = 22.dp) }
            // Ludo: hosts and co-hosts open a game; anyone goes back to the one on.
            if (s.isMod || s.ludoOn) {
                Spacer(Modifier.width(BAR_GAP))
                RoundButton(stringResource(if (s.ludoShown != null || s.ludoHidden) R.string.ludo_back_to_game else R.string.ludo_play), vm.voice::ludoButton) {
                    TopicIcon(Topic.GAMING, size = 22.dp)
                }
            }
            if (s.isMod && s.hands > 0) {
                Spacer(Modifier.width(BAR_GAP))
                Box {
                    RoundButton(stringResource(R.string.voice_requests_n, s.hands), onRequests) { GlyphIcon(Glyph.HAND, size = 22.dp) }
                    Text(
                        "${s.hands}", Modifier.align(Alignment.TopEnd).offset(2.dp, (-2).dp).clip(CircleShape).background(MaterialTheme.colorScheme.background)
                            .padding(2.dp).clip(CircleShape).background(Brand.Pink).padding(horizontal = 5.dp),
                        color = Color.White, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Jakarta,
                    )
                }
            }
            Spacer(Modifier.weight(1f).widthIn(min = BAR_GAP))
            if (me.seat != null) {
                val bg by animateColorAsState(if (me.muted) MaterialTheme.colorScheme.surfaceContainerHighest else Brand.Pink, label = "mic")
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier.size(56.dp).pressScale(interaction, 0.94f).clip(CircleShape).background(bg)
                        .clickable(interaction, indication = ripple(), role = Role.Switch) { mic(me.muted) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (me.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, stringResource(if (me.muted) R.string.voice_unmute else R.string.voice_mute),
                        Modifier.size(26.dp), tint = if (me.muted) MaterialTheme.colorScheme.onSurface else Color.White,
                    )
                }
            } else {
                val interaction = remember { MutableInteractionSource() }
                // The host, co-hosts and anyone in an open room take a seat; elsewhere a hand goes up.
                val joins = VoiceController.joinsDirectly(room)
                val raised = me.hand && !joins
                Row(
                    Modifier.height(48.dp).pressScale(interaction, 0.96f).clip(CircleShape)
                        .background(if (raised) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurface)
                        .then(if (raised) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape) else Modifier)
                        .clickable(interaction, indication = ripple(), role = Role.Button, onClick = vm.voice::joinVoice)
                        .padding(start = 14.dp, end = 18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (joins) Icon(Icons.Rounded.Mic, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.background)
                    else GlyphIcon(Glyph.HAND, size = 20.dp)
                    Text(
                        stringResource(if (joins) R.string.room_join_voice else if (raised) R.string.voice_hand_raised else R.string.voice_raise_hand),
                        color = if (raised) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Five reactions in a tray above the button; it stays open for a burst of taps (X Spaces). */
@Composable
private fun ReactionTray(modifier: Modifier, onReact: (VoiceReaction) -> Unit) {
    Row(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val names = mapOf(
            VoiceReaction.HEART to R.string.voice_r_heart, VoiceReaction.LAUGH to R.string.voice_r_laugh, VoiceReaction.FIRE to R.string.voice_r_fire,
            VoiceReaction.CLAP to R.string.voice_r_clap, VoiceReaction.WOW to R.string.voice_r_wow,
        )
        VoiceReaction.entries.forEach { kind ->
            val interaction = remember { MutableInteractionSource() }
            val name = stringResource(names.getValue(kind))
            Box(
                Modifier.size(44.dp).pressScale(interaction, 0.82f).clip(CircleShape)
                    .clickable(interaction, indication = ripple(), role = Role.Button) { onReact(kind) }
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(kind.glyph(), size = 26.dp) }
        }
    }
}

@Composable
private fun EndDialog(busy: Boolean, onCancel: () -> Unit, onEnd: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.voice_end_title)) },
        text = { Text(stringResource(R.string.voice_end_body)) },
        confirmButton = { TextButton(onEnd, enabled = !busy) { Text(stringResource(R.string.voice_end_confirm), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Between the round buttons of the control bar (a host has five, and the bar must fit a 393 dp phone). */
private val BAR_GAP = 6.dp

/** The chat keeps at least this much room under the game. */
private val MIN_CHAT = 96.dp

/** The game's toolbar, the players above and below the board, and the turn line. */
private val LUDO_CHROME = 190.dp

// ---- the pill: the room minimised over the tabs ----

/** The room while browsing the rest of the app: who is talking, my mic, back to the room, leave. */
@Composable
fun VoicePill(vm: AppViewModel, modifier: Modifier = Modifier) {
    val s by vm.voice.state.collectAsStateWithLifecycle()
    val levels = vm.voice.levels.collectAsStateWithLifecycle()
    val state = s
    val room = state?.room
    AnimatedVisibility(
        state != null && !state.expanded && room != null, modifier,
        enter = slideInVertically { it / 2 } + fadeIn(), exit = slideOutVertically { it / 2 } + fadeOut(),
    ) {
        if (state == null || room == null) return@AnimatedVisibility
        RoomTheme {
            val loudest by remember { derivedStateOf { VoiceController.loudest(levels.value) } }
            val speaker = loudest?.let(room::member)
            val face = speaker ?: room.member(room.hostId)
            val me = room.me
            val mic = rememberMic(vm)
            val region = stringResource(R.string.voice_pill, room.title)
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics { paneTitle = region }.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = vm.voice::expand).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    face?.let {
                        Box(Modifier.size(40.dp)) {
                            SpeakingRing(levels, it.profile.id, 40.dp)
                            Avatar(it.profile, 40.dp)
                        }
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(room.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveBars(height = 11.dp)
                            Text(
                                speaker?.let { stringResource(R.string.voice_is_speaking, it.profile.name) }
                                    ?: stringResource(R.string.voice_listening, room.audience.size),
                                Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (me?.seat != null) {
                    CircleIconButton(
                        if (me.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, stringResource(if (me.muted) R.string.voice_unmute else R.string.voice_mute),
                        { mic(me.muted) }, background = if (me.muted) MaterialTheme.colorScheme.surfaceContainerHighest else Brand.Pink,
                        tint = if (me.muted) MaterialTheme.colorScheme.onSurface else Color.White,
                    )
                }
                CircleIconButton(Icons.Rounded.ExpandLess, stringResource(R.string.voice_expand), vm.voice::expand)
                CircleIconButton(Icons.AutoMirrored.Rounded.Logout, stringResource(R.string.voice_leave), vm.voice::leave, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
