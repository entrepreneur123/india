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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
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
import com.yaari.chat.domain.flagOf
import com.yaari.chat.model.VoiceRole
import com.yaari.chat.model.VoiceRoom
import com.yaari.chat.model.YouTube
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.runtime.Stable
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import kotlinx.coroutines.Job
import kotlin.math.abs
import androidx.compose.animation.core.animate
import androidx.core.content.edit
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.material.icons.rounded.VerticalAlignBottom

// ---- A voice room: full screen, white like the rest of the app ---------------------------------------------------
// Stage on top, chat below, controls at the bottom; the web's components/screens/voice/*.

/**
 * The room over everything while expanded. The back gesture minimises it to the bar over the tabs (still in the room,
 * like the chevron), never leaves; only Leave does. Its handler lives here, composed as long as the app is, so a back
 * press right after the room opens (before its screen has composed) minimises it too instead of closing the app.
 */
@Composable
fun VoiceLayer(vm: AppViewModel) {
    val s by vm.voice.state.collectAsStateWithLifecycle()
    // Registered again each time the room comes up, so it goes before any screen underneath that has its own (the
    // newest handler is asked first).
    val up = s?.expanded == true
    key(s?.roomId, up) { BackHandler(enabled = up, onBack = vm.voice::minimise) }
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

/** The room's text colour by default (the room has no Surface of its own). */
@Composable
fun RoomTheme(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)

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
    data object People : VoiceSheet
}

/**
 * A room, full screen. Every room has the chat (text, replies, icebreakers, photos, polls), the stage
 * and shared music; the category decides what comes first (as the web's voice-room.tsx):
 *  - talk: the chat, under a slim strip of the people on the stage;
 *  - music: the shared player with the song requests by vote.
 * It takes every touch (nothing reaches the tabs under it). The back gesture ([VoiceLayer]) and the chevron minimise it
 * to the bar over the tabs: still in the room, audio and music on.
 */
@Composable
fun VoiceRoomScreen(vm: AppViewModel, s: VoiceState) {
    val room = s.room
    val me = s.me
    var sheet by remember { mutableStateOf<VoiceSheet?>(null) }
    var stageOpen by rememberSaveable { mutableStateOf(false) }
    var pollSheet by rememberSaveable { mutableStateOf(false) }
    val levels = vm.voice.levels.collectAsStateWithLifecycle()
    val list by vm.voice.list.collectAsStateWithLifecycle()
    val actor: VoiceActor = { a, ok, done -> vm.voice.act(a, ok, done) }
    val topic = room?.category?.topic() ?: Topic.CHAT
    Box(Modifier.fillMaxSize().blockTouches().background(MaterialTheme.colorScheme.background)) {
        if (room == null || me == null) {
            Joining(vm.voice::minimise)
            return@Box
        }
        var barHeight by remember { mutableIntStateOf(0) }
        var tray by remember { mutableStateOf(false) }
        // Someone in the room: their room card (stage, mute, report). Someone whose line is still in the chat after they
        // left: their profile.
        val person: (String) -> Unit = { id ->
            if (room.member(id) != null) sheet = VoiceSheet.Person(id)
            else s.lines?.firstNotNullOfOrNull { l -> l.senderOf(id) }?.let(vm::openProfile)
        }
        val kind = room.category
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            // A tablet or a phone on its side: the room on the left, the chat beside it. A phone: the chat below.
            val wide = maxWidth >= WIDE
            Column(Modifier.fillMaxSize()) {
                Header(
                    room, wide, onMinimise = vm.voice::minimise, onPeople = { sheet = VoiceSheet.People }, onInvite = { sheet = VoiceSheet.Invite },
                    onMore = { sheet = VoiceSheet.Options }, onLeave = vm.voice::leave,
                )
                room.announcement?.let { Announcement(it, Brand.Saffron, onEdit = if (s.isHost) ({ sheet = VoiceSheet.Settings }) else null) }
                if (s.offline) OfflineBanner()
                AudioNotice(s)
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val height = maxHeight
                    val heightPx = constraints.maxHeight
                    val width = maxWidth
                    val shared = s.shownStage
                    val people: @Composable () -> Unit = {
                        // The speakers ("On stage", their seats) and the listeners ("Listening"), each in its own block.
                        StageSection(
                            room, levels, s.reactions, stageOpen, { stageOpen = !stageOpen }, person,
                            onEmpty = { seat -> vm.voice.tapSeat(seat) { sheet = VoiceSheet.Seat(it) } },
                        )
                        ListenersStrip(room, s.hands) { sheet = VoiceSheet.Audience() }
                    }
                    val top: @Composable () -> Unit = {
                        when (kind) {
                            RoomCategory.MUSIC -> {
                                MusicHero(room, s.canDj, s.adding, active = s.expanded, onQueue = { sheet = VoiceSheet.Queue }, act = { a -> vm.voice.act(a) }, vote = vm.voice::vote)
                                people()
                            }
                            RoomCategory.TALK -> {
                                people()
                                // Music in a talk room: the slim "listen" bar, the player, or the now-playing strip.
                                room.track?.let { t ->
                                    MusicPanel(
                                        t, room.queue, s.canDj, listening = s.listen, open = s.musicOpen, active = s.expanded,
                                        onListen = vm.voice::listen, onOpenChange = vm.voice::musicPanel, onQueue = { sheet = VoiceSheet.Queue },
                                    ) { a -> vm.voice.act(a) }
                                }
                            }
                        }
                    }
                    val links = LocalLinks.current
                    val context = LocalContext.current
                    val chat: @Composable (Modifier) -> Unit = { m ->
                        VoiceChat(
                            s.lines, m, onIcebreaker = if (kind == RoomCategory.TALK) vm.voice::icebreaker else null, onPerson = person,
                            chat = RoomChat(
                                roomId = room.id, myId = room.meId, myName = me.profile.name, polls = s.polls, names = room.members.map { it.profile.name },
                                onReply = vm.voice::reply,
                                onVote = vm.voice::votePoll, onClosePoll = vm.voice::closePoll, canClose = vm.voice::canClose,
                                roles = room.members.filter { it.isMod }.associate { it.profile.id to it.role },
                                typing = s.typers,
                                // A page opened from the room's chat: the host and co-hosts may open it together.
                                onLink = { url ->
                                    if (YouTube.videoId(url) != null) openLink(context, links, url)
                                    else openRoomPage(context, links, url, share = if (room.canShare) { u, t -> vm.voice.shareLink(u, t) } else null)
                                },
                                onRequestSong = { id -> vm.voice.addSong(id) },
                            ),
                        )
                    }
                    // The room's part: what comes first in this category, then a page someone shares (it takes what is left).
                    val split = rememberRoomSplit(kind, wide)
                    val roomPart: @Composable (Modifier) -> Unit = { m ->
                        Column(m) {
                            Column(
                                // A phone's top block is only as tall as what's in it (up to its share), so a share bigger than the
                                // room leaves no empty band: the chat takes the rest. While dragged it follows the finger exactly.
                                Modifier.fillMaxWidth().then(if (shared != null) Modifier.heightIn(max = height * 0.32f) else Modifier.weight(1f, fill = wide || split.dragging))
                                    .verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) { top() }
                            shared?.let { SharedPagePanel(it, room.canShare, onClose = vm.voice::hideStage, onStop = vm.voice::stopSharing, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp).fillMaxWidth().weight(1f)) }
                        }
                    }
                    if (wide) {
                        // The chat column: 300 dp up to 60 % of the width, dragged on the splitter.
                        val min = (300.dp / width).coerceAtMost(MAX_CHAT)
                        val chatWidth = width * split.value.coerceIn(min, MAX_CHAT)
                        Row(Modifier.fillMaxSize()) {
                            roomPart(Modifier.weight(1f).fillMaxHeight())
                            Splitter(split, min, width)
                            chat(Modifier.width(chatWidth).fillMaxHeight())
                        }
                    } else {
                        // The top block takes the chosen share of the height; folded (chat only), a one-line strip of the speakers.
                        val folded = height * split.value < FOLD
                        Column(Modifier.fillMaxSize()) {
                            if (folded) FoldedStage(room, levels, onOpen = split::toggle, onPerson = person)
                            else roomPart(
                                Modifier.fillMaxWidth()
                                    .then(if (split.dragging || shared != null) Modifier.height(height * split.value) else Modifier.heightIn(max = height * split.value))
                                    .onSizeChanged { split.shown = it.height.toFloat() / heightPx },
                            )
                            DragHandle(split, folded, height)
                            chat(Modifier.weight(1f))
                        }
                    }
                    // Over the whole room, not inside the stage's clipped card (where a folded stage cut them off at once):
                    // they rise from just above the controls, on phones and wide screens alike.
                    ReactionLayer(s.reactions, Modifier.matchParentSize())
                }
                ControlBar(
                    vm, s, me, Modifier.onSizeChanged { barHeight = it.height },
                    onRequests = { sheet = VoiceSheet.Requests }, onMusic = { sheet = VoiceSheet.Queue }, onReactions = { tray = !tray },
                    onPoll = { pollSheet = true },
                )
            }
        }
        if (tray) {
            Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { tray = false })
            ReactionTray(
                Modifier.align(Alignment.BottomStart).navigationBarsPadding().imePadding()
                    .padding(start = 12.dp, bottom = with(LocalDensity.current) { barHeight.toDp() } + 8.dp),
            ) { vm.voice.act(VoiceAction.React(it)) }
        }
        VoiceSheets(vm, s, room, sheet, actor, onSheet = { sheet = it })
        if (pollSheet && s.isMod) PollSheet(s.pollBusy, onDismiss = { pollSheet = false }) { q, o -> vm.voice.createPoll(q, o) { pollSheet = false } }
        if (s.confirmEnd) EndDialog(s.heir?.profile?.name, s.ending, onCancel = vm.voice::dismissEnd, onLeave = vm.voice::close, onEnd = vm.voice::end)
        list.created?.let {
            RoomCreatedDialog(room, it.code, onInvite = { vm.voice.dismissCreated(); sheet = VoiceSheet.Invite }, onDone = vm.voice::dismissCreated)
        }
    }
}

/**
 * Takes every touch that reaches it, so nothing falls through to the screen underneath (the tabs under a room: a tap on
 * an empty part of the room used to switch the hidden tab). Its children still get their touches first.
 */
fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
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

/**
 * The title, the category and how many are here (a tap lists everyone: the People sheet), Invite (with its label on
 * wide screens), the options and Leave.
 */
@Composable
private fun Header(
    room: VoiceRoom, wide: Boolean, onMinimise: () -> Unit, onPeople: () -> Unit, onInvite: () -> Unit, onMore: () -> Unit, onLeave: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.voice_minimise), onMinimise, iconSize = 28.dp)
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                room.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TopicChip(room.category.topic(), stringResource(room.category.label), height = 24.dp)
                Row(
                    Modifier.padding(start = 4.dp).clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.room_people), onClick = onPeople)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Group, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.rooms_here, room.members.size, room.rules.maxMembers), Modifier.padding(start = 4.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                    Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, Modifier.padding(start = 4.dp).size(10.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (wide) {
            SoftButton(
                stringResource(R.string.invite_button), onInvite, Modifier.padding(end = 4.dp), height = 40.dp,
                leading = { Icon(Icons.Outlined.PersonAddAlt, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) },
            )
        } else {
            CircleIconButton(Icons.Outlined.PersonAddAlt, stringResource(R.string.invite_button), onInvite)
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

/**
 * The speakers' block: "On stage · N" and, folded, the people on the stage in a strip with their speaking rings (or,
 * with nobody there, why there's no sound); unfolded, every seat (a host or co-host manages the empty ones from here).
 */
@Composable
private fun StageSection(
    room: VoiceRoom, levels: State<Map<String, Float>>, reactions: List<FloatingReaction>, open: Boolean, onToggle: () -> Unit,
    onPerson: (String) -> Unit, onEmpty: (Int) -> Unit,
) {
    val stage = room.members.filter { it.seat != null }.sortedBy { it.seat }
    val stageName = stringResource(R.string.voice_stage)
    Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))) {
        val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
        val seats = stringResource(if (open) R.string.room_hide_seats else R.string.room_show_seats)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClickLabel = seats, onClick = onToggle)
                .semantics { stateDescription = if (open) "expanded" else "collapsed" }.padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Mic, null, Modifier.size(16.dp), tint = Brand.Saffron)
            Text(
                stringResource(R.string.room_on_stage_n, stage.size), Modifier.padding(start = 6.dp).weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleSmall, maxLines = 1,
            )
            Text(seats, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.padding(start = 2.dp).size(18.dp).graphicsLayer { rotationZ = turn }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Stage(room, levels, reactions, small = true, onPerson = onPerson, onEmpty = onEmpty)
        }
        if (!open) StageStrip(stage, levels, stageName, onPerson)
    }
}

/** The people on the stage, small, with their speaking rings; or, with nobody there, why there's no sound. */
@Composable
private fun StageStrip(stage: List<VoiceMember>, levels: State<Map<String, Float>>, stageName: String, onPerson: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
    }
}

/** My connection dropped (heartbeats failing or realtime down): a slim line until it is back. */
@Composable
private fun OfflineBanner() {
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)).padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WifiOff, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
        Text(
            stringResource(R.string.voice_reconnecting), Modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A face whose connection dropped (045) is dimmed with this badge; the server takes them out 7 s later unless they're back. */
const val LOST_ALPHA = 0.4f

@Composable
fun LostBadge(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(2.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.error),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.WifiOff, null, Modifier.size(size * 0.5f), tint = MaterialTheme.colorScheme.onError) }
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

// ---- the split between the room and the chat ----

/**
 * Where a room's split sits, remembered per category and form factor on this phone ([value]): on a phone, the share of
 * the height the top block takes (0: chat only); on a wide screen, the share of the width the chat takes.
 */
@Stable
class RoomSplit(
    private val prefs: SharedPreferences, private val key: String, private val default: Float, private val wide: Boolean,
    private val scope: CoroutineScope,
) {
    var value by mutableFloatStateOf(prefs.getFloat(key, default))
        private set
    /** A finger is on the handle (the top block then takes exactly [value], not just up to it). */
    var dragging by mutableStateOf(false)
        private set
    private var moving: Job? = null

    /** A phone's top block as drawn (a share; less than [value] when what's in it is shorter). Drags start from here. */
    var shown: Float? = null

    /** A drag or an arrow key moved it by [delta] (a share), within [min]..[max]. */
    fun drag(delta: Float, min: Float, max: Float) {
        moving?.cancel()
        val from = if (dragging || wide) value else shown?.coerceAtMost(value) ?: value
        dragging = true
        value = (from + delta).coerceIn(min, max)
    }

    /** A drag ended: a phone's block snaps to chat only, 30, 50 or 70 %; then it is remembered. */
    fun settle() {
        dragging = false
        if (wide) save(value) else moveTo(SNAPS.minBy { abs(it - value) })
    }

    /** Double tap on the handle: chat only, or back to the room's default. */
    fun toggle() = moveTo(if (value == 0f) default else 0f)

    private fun moveTo(to: Float) {
        moving?.cancel()
        moving = scope.launch {
            animate(value, to, animationSpec = tween(220, easing = FastOutSlowInEasing)) { v, _ -> value = v }
            save(to)
        }
    }

    fun save(v: Float) = prefs.edit { putFloat(key, v) }

    companion object {
        val SNAPS = listOf(0f, 0.3f, 0.5f, 0.7f)
    }
}

/**
 * The split of a [kind] of room on this [wide] or phone layout. Talk rooms are the chat's (the top block about 30 % of
 * a phone's height), music rooms the player's (about 55 %).
 */
@Composable
private fun rememberRoomSplit(kind: RoomCategory, wide: Boolean): RoomSplit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(kind, wide) {
        val default = when (kind) {
            RoomCategory.TALK -> if (wide) 0.55f else 0.3f
            RoomCategory.MUSIC -> if (wide) 0.4f else 0.55f
        }
        val prefs = context.getSharedPreferences(SPLIT_PREFS, Context.MODE_PRIVATE)
        RoomSplit(prefs, "${kind.code}_${if (wide) "wide" else "phone"}", default, wide, scope)
    }
}

/** A phone's handle between the top block and the chat: a 36 × 4 pill in a 24 dp strip; dragged, or double-tapped. */
@Composable
private fun DragHandle(split: RoomSplit, folded: Boolean, height: Dp) {
    val px = with(LocalDensity.current) { height.toPx() }
    val label = stringResource(R.string.room_resize)
    val toggle = stringResource(if (folded) R.string.room_show_room else R.string.room_chat_only)
    Box(
        Modifier.fillMaxWidth().height(24.dp)
            .draggable(rememberDraggableState { dy -> split.drag(dy / px, 0f, MAX_TOP) }, Orientation.Vertical, onDragStopped = { split.settle() })
            .pointerInput(split) { detectTapGestures(onDoubleTap = { split.toggle() }) }
            .semantics {
                contentDescription = label
                onClick(toggle) { split.toggle(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp, 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
    }
}

/**
 * A wide screen's splitter between the room and the chat column: dragged with a finger or the mouse (a resize
 * pointer), or with ←/→ by 32 px when focused; TalkBack reads and sets it as a range.
 */
@Composable
private fun Splitter(split: RoomSplit, min: Float, width: Dp) {
    val px = with(LocalDensity.current) { width.toPx() }
    val step = 32f / px
    val label = stringResource(R.string.room_resize)
    val focus = remember { MutableInteractionSource() }
    val focused by focus.collectIsFocusedAsState()
    Box(
        Modifier.width(12.dp).fillMaxHeight()
            .pointerHoverIcon(PointerIcon(android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW))
            .draggable(rememberDraggableState { dx -> split.drag(-dx / px, min, MAX_CHAT) }, Orientation.Horizontal, onDragStopped = { split.settle() })
            .onKeyEvent { e ->
                val delta = when (e.key) {
                    Key.DirectionLeft -> step
                    Key.DirectionRight -> -step
                    else -> return@onKeyEvent false
                }
                if (e.type == KeyEventType.KeyDown) {
                    split.drag(delta, min, MAX_CHAT)
                    split.settle()
                }
                true
            }
            .focusable(interactionSource = focus)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(split.value.coerceIn(min, MAX_CHAT), min..MAX_CHAT)
                setProgress { v ->
                    split.drag(v - split.value, min, MAX_CHAT)
                    split.settle()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.size(4.dp, 36.dp).clip(CircleShape).background(if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
    }
}

/** The top block folded away (chat only): one line with the speakers' faces and how many listen; a tap opens it again. */
@Composable
private fun FoldedStage(room: VoiceRoom, levels: State<Map<String, Float>>, onOpen: () -> Unit, onPerson: (String) -> Unit) {
    val stage = room.members.filter { it.seat != null }.sortedBy { it.seat }
    Row(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(FOLD - 8.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.room_show_room), onClick = onOpen).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Mic, null, Modifier.size(16.dp), tint = Brand.Saffron)
        Row(Modifier.padding(start = 8.dp).weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            if (stage.isEmpty()) {
                Text(stringResource(R.string.room_stage_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            stage.forEach { m ->
                val name = m.profile.name
                Box(
                    Modifier.padding(end = 8.dp).size(32.dp).clickable(role = Role.Button) { onPerson(m.profile.id) }.semantics { contentDescription = name },
                ) {
                    SpeakingRing(levels, m.profile.id, 32.dp)
                    Avatar(m.profile, 32.dp)
                }
            }
        }
        Icon(Icons.Outlined.Headphones, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${room.audience.size}", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
    val state = when {
        member.lost -> stringResource(R.string.voice_lost)
        member.muted -> stringResource(R.string.voice_mic_off)
        else -> null
    }
    val label = listOfNotNull(member.profile.name, role, state).joinToString(", ")
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
            if (!member.lost) SpeakingRing(levels, member.profile.id, px)
            Avatar(member.profile, px, Modifier.pressScale(interaction, 0.95f).alpha(if (member.lost) LOST_ALPHA else 1f))
            if (member.lost) LostBadge(badge, Modifier.align(Alignment.TopEnd).offset(2.dp, (-2).dp))
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
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                member.profile.name, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = (if (wide) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium).copy(fontWeight = FontWeight.SemiBold),
            )
            // Everyone on the stage at once: one batched trust() lookup.
            VerifiedMark(member.profile, Modifier.padding(start = 3.dp), size = if (wide) 15.dp else 12.dp)
            if (member.role == VoiceRole.HOST) Text(flagOf(member.profile.country), Modifier.padding(start = 3.dp), fontSize = if (wide) 14.sp else 12.sp)
        }
        if (member.lost) LostLabel() else role?.let { RoleBadge(it) }
    }
}

@Composable
fun LostLabel(modifier: Modifier = Modifier) = Text(
    stringResource(R.string.voice_lost), modifier.padding(top = 2.dp), color = MaterialTheme.colorScheme.error, fontSize = 10.sp, lineHeight = 14.sp,
    fontWeight = FontWeight.Bold, fontFamily = Jakarta, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
)

@Composable
fun RoleBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.padding(top = 2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 6.dp, vertical = 1.dp),
        color = MaterialTheme.colorScheme.primaryText, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Jakarta,
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

/**
 * Reactions float up over the room (X Spaces), up to half its height; with animations off they only fade in and out in
 * place. It takes no touches.
 */
@Composable
private fun ReactionLayer(reactions: List<FloatingReaction>, modifier: Modifier) {
    val reduce = LocalReducedMotion.current
    BoxWithConstraints(modifier) {
        val width = maxWidth
        val top = (maxHeight * 0.5f).coerceIn(80.dp, 320.dp).value
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
                val rise = if (reduce) 0f else top * FastOutSlowInEasing.transform(p)
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

/** The listeners' block, apart from the stage: "Listening · N", their faces, raised hands; a tap lists them all. */
@Composable
private fun ListenersStrip(room: VoiceRoom, hands: Int, onClick: () -> Unit) {
    val audience = room.audience
    Row(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Headphones, null, Modifier.padding(end = 6.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.voice_listening_n, audience.size), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        val shown = audience.take(7)
        FaceStack(shown.map { it.profile }, 26.dp, MaterialTheme.colorScheme.background, Modifier.padding(start = 12.dp).weight(1f), dim = shown.filter { it.lost }.map { it.profile.id }.toSet())
        if (hands > 0) {
            GlyphIcon(Glyph.HAND, size = 16.dp)
            Text("$hands", Modifier.padding(start = 4.dp, end = 8.dp), color = MaterialTheme.colorScheme.primaryText, style = MaterialTheme.typography.labelLarge)
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
 * The same controls in every room: the composer (the quote of the line I answer, attachments: a photo, and a poll for
 * hosts and co-hosts; an icebreaker, text with @mentions), reactions, the music, the hands raised (hosts and
 * co-hosts), and the main button: on the stage "Leave stage" and the microphone, else "Join voice" or "Raise hand".
 */
@Composable
private fun ControlBar(
    vm: AppViewModel, s: VoiceState, me: VoiceMember, modifier: Modifier,
    onRequests: () -> Unit, onMusic: () -> Unit, onReactions: () -> Unit, onPoll: () -> Unit,
) {
    val mic = rememberMic(vm)
    val room = s.room ?: return
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        VoiceComposer(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp), s.photoBusy,
            onPhoto = vm.voice::sendPhoto, onIcebreaker = vm.voice::icebreaker,
            replyTo = s.replyTo, myId = room.meId, onCancelReply = vm.voice::cancelReply,
            people = room.members.filter { it.profile.id != room.meId }.map { it.profile },
            onPoll = if (s.isMod) onPoll else null, onTyping = vm.voice::typing,
        ) { text, failed -> vm.voice.send(text, failed) }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(stringResource(R.string.voice_react), onReactions) {
                Icon(Icons.Outlined.AddReaction, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(BAR_GAP))
            RoundButton(stringResource(R.string.voice_music), onMusic) { TopicIcon(Topic.MUSIC, size = 22.dp) }
            if (s.isMod && s.hands > 0) {
                Spacer(Modifier.width(BAR_GAP))
                Box {
                    RoundButton(stringResource(R.string.voice_requests_n, s.hands), onRequests) { GlyphIcon(Glyph.HAND, size = 22.dp) }
                    Text(
                        "${s.hands}", Modifier.align(Alignment.TopEnd).offset(2.dp, (-2).dp).clip(CircleShape).background(MaterialTheme.colorScheme.background)
                            .padding(2.dp).clip(CircleShape).background(Brand.SaffronStrong).padding(horizontal = 5.dp),
                        color = Brand.OnSaffron, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Jakarta,
                    )
                }
            }
            Spacer(Modifier.weight(1f).widthIn(min = BAR_GAP))
            if (me.seat != null) {
                // Down to the listeners, next to the mic (the host too: "Join voice" brings them back).
                val leave = remember { MutableInteractionSource() }
                Row(
                    Modifier.height(48.dp).widthIn(max = 168.dp).pressScale(leave, 0.96f).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(leave, indication = ripple(), role = Role.Button) { vm.voice.act(VoiceAction.Unseat) }
                        .padding(start = 10.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.VerticalAlignBottom, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.voice_leave_stage), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                val bg by animateColorAsState(if (me.muted) MaterialTheme.colorScheme.surfaceContainerHighest else Brand.Saffron, label = "mic")
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier.size(56.dp).pressScale(interaction, 0.94f).clip(CircleShape).background(bg)
                        .clickable(interaction, indication = ripple(), role = Role.Switch) { mic(me.muted) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (me.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, stringResource(if (me.muted) R.string.voice_unmute else R.string.voice_mute),
                        Modifier.size(26.dp), tint = if (me.muted) MaterialTheme.colorScheme.onSurface else Brand.OnSaffron,
                    )
                }
            } else {
                val interaction = remember { MutableInteractionSource() }
                // The host, co-hosts and anyone in an open room take a seat; elsewhere a hand goes up.
                val joins = VoiceController.joinsDirectly(room)
                val raised = me.hand && !joins
                Row(
                    Modifier.height(48.dp).pressScale(interaction, 0.96f).clip(CircleShape)
                        .background(if (raised) MaterialTheme.colorScheme.primaryContainer else Brand.Saffron)
                        .then(if (raised) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape) else Modifier)
                        .clickable(interaction, indication = ripple(), role = Role.Button, onClick = vm.voice::joinVoice)
                        .padding(start = 14.dp, end = 18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (raised) GlyphIcon(Glyph.HAND, size = 20.dp)
                    else Icon(if (joins) Icons.Rounded.Mic else Icons.Rounded.PanTool, null, Modifier.size(20.dp), tint = Brand.OnSaffron)
                    Text(
                        stringResource(if (joins) R.string.room_join_voice else if (raised) R.string.voice_hand_raised else R.string.voice_raise_hand),
                        color = if (raised) MaterialTheme.colorScheme.primaryText else Brand.OnSaffron,
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
private fun EndDialog(heir: String?, busy: Boolean, onCancel: () -> Unit, onLeave: () -> Unit, onEnd: () -> Unit) {
    val end = @Composable { TextButton(onEnd, enabled = !busy) { Text(stringResource(R.string.voice_end_confirm), color = MaterialTheme.colorScheme.error) } }
    val cancel = @Composable { TextButton(onCancel) { Text(stringResource(R.string.cancel)) } }
    // With a co-host here, leaving hands them the room (the server's voice_hand_over); ending it is the second choice.
    if (heir == null) AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.voice_end_title)) },
        text = { Text(stringResource(R.string.voice_end_body)) },
        confirmButton = end,
        dismissButton = cancel,
    ) else AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.voice_hand_over_title)) },
        text = { Text(stringResource(R.string.voice_hand_over_body, heir)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onLeave, enabled = !busy) { Text(stringResource(R.string.voice_hand_over_leave, heir), textAlign = TextAlign.End) }
                end()
                cancel()
            }
        },
    )
}

/** From this width the chat sits beside the room instead of under it. */
private val WIDE = 600.dp

/** On wide screens the chat column is at most this share of the width (and at least 300 dp). */
private const val MAX_CHAT = 0.6f

/** On phones the top block takes at most this share of the height (dragging past the 70 % snap point). */
private const val MAX_TOP = 0.78f

/** A phone's top block shorter than this folds to the one-line strip (chat only). */
private val FOLD = 56.dp

/** The rooms' splits on this phone (SharedPreferences), per category and form factor. */
private const val SPLIT_PREFS = "yaari_room_split"

/** Between the round buttons of the control bar (a host has four, and the bar must fit a 393 dp phone). */
private val BAR_GAP = 6.dp

// ---- the room bar: the room minimised, above the tabs ----

/**
 * The room while I look around the rest of the app (WhatsApp's ongoing-call bar, X Spaces' dock): above the tabs on
 * every tab, with the room's title, that it is live, who talks or what plays, my microphone when I'm on the stage, and
 * Leave (only Leave leaves). A tap opens the room again.
 */
@Composable
fun VoicePill(vm: AppViewModel, modifier: Modifier = Modifier) {
    val s by vm.voice.state.collectAsStateWithLifecycle()
    val levels = vm.voice.levels.collectAsStateWithLifecycle()
    var last by remember { mutableStateOf(s) }
    s?.let { last = it }
    AnimatedVisibility(
        s != null && !s!!.expanded, modifier,
        enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(), exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
    ) {
        val state = s ?: last ?: return@AnimatedVisibility
        RoomTheme { RoomBar(vm, state, levels) }
    }
}

@Composable
private fun RoomBar(vm: AppViewModel, state: VoiceState, levels: State<Map<String, Float>>) {
    val room = state.room
    val loudest by remember { derivedStateOf { VoiceController.loudest(levels.value) } }
    val speaker = loudest?.let { room?.member(it) }
    val face = speaker ?: room?.member(room.hostId)
    val me = room?.me
    val mic = rememberMic(vm)
    val title = room?.title ?: stringResource(R.string.voice_joining)
    val region = stringResource(R.string.voice_pill, title)
    val open = stringResource(R.string.voice_expand)
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp).semantics { paneTitle = region }.fillMaxWidth()
            .shadow(10.dp, shape).clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClickLabel = open, onClick = vm.voice::expand)
                .semantics { contentDescription = open }.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (face != null) {
                    SpeakingRing(levels, face.profile.id, 44.dp)
                    Avatar(face.profile, 44.dp)
                } else {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        TopicIcon(room?.category?.topic() ?: Topic.CHAT, size = 24.dp)
                    }
                }
            }
            Column(Modifier.padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(CircleShape).background(Brand.SaffronStrong).padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LiveBars(color = Brand.OnSaffron, height = 9.dp)
                        Text(
                            stringResource(R.string.voice_bar_live).uppercase(), color = Brand.OnSaffron, fontSize = 10.sp, lineHeight = 13.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, fontFamily = Jakarta,
                        )
                    }
                    Text(
                        title, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                val track = room?.track
                Text(
                    when {
                        room == null -> stringResource(R.string.voice_bar_joining)
                        speaker != null -> stringResource(R.string.voice_is_speaking, speaker.profile.name)
                        state.playing && track != null -> stringResource(R.string.voice_bar_playing, YouTube.songOf(track.title).ifEmpty { stringResource(R.string.voice_yt_video) })
                        else -> stringResource(R.string.voice_listening, room.audience.size)
                    },
                    Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (me?.seat != null) {
            CircleIconButton(
                if (me.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, stringResource(if (me.muted) R.string.voice_unmute else R.string.voice_mute),
                { mic(me.muted) }, background = if (me.muted) MaterialTheme.colorScheme.surfaceContainerHighest else Brand.Saffron,
                tint = if (me.muted) MaterialTheme.colorScheme.onSurface else Brand.OnSaffron,
            )
        }
        CircleIconButton(
            Icons.AutoMirrored.Rounded.Logout, stringResource(R.string.voice_leave), vm.voice::leave,
            background = MaterialTheme.colorScheme.error.copy(alpha = 0.16f), tint = MaterialTheme.colorScheme.error,
        )
    }
}

// ---- the random chat's bar: the chat minimised, above the tabs (same place and look as the room's) ----

/**
 * The random chat while I look around the tabs: the partner's face and name, "typing…" or a dot for a new message,
 * a tap to go back, and End chat (the chat's own exit, so its lock still holds).
 */
@Composable
fun ChatBar(vm: AppViewModel, s: UiState, modifier: Modifier = Modifier) {
    val chat = s.chat
    var last by remember { mutableStateOf(chat) }
    chat?.let { last = it }
    AnimatedVisibility(
        chat != null && s.chatMinimised, modifier,
        enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(), exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
    ) {
        val c = chat ?: last ?: return@AnimatedVisibility
        val partner = c.match.partner
        val color = Brand.Saffron
        val shape = RoundedCornerShape(22.dp)
        val open = stringResource(R.string.chat_open)
        RoomTheme {
            Row(
                Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp).fillMaxWidth()
                    .shadow(10.dp, shape).clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClickLabel = open, onClick = vm::expandChat)
                        .semantics { contentDescription = open }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp)) {
                        Avatar(partner, 44.dp)
                        if (s.chatUnread) {
                            Box(
                                Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(2.dp).clip(CircleShape).background(Brand.Saffron),
                            )
                        }
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            stringResource(R.string.chat_bar_with, partner.name), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!c.partnerLeft) {
                                PresenceDot(6.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                when {
                                    c.partnerLeft -> stringResource(R.string.partner_left, partner.name)
                                    c.partnerTyping -> stringResource(R.string.typing)
                                    s.chatUnread -> stringResource(R.string.chat_bar_new)
                                    else -> stringResource(R.string.chat_bar_tap)
                                },
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (c.partnerTyping || s.chatUnread) color else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (s.chatUnread) FontWeight.SemiBold else null,
                            )
                        }
                    }
                }
                CircleIconButton(
                    Icons.Rounded.CallEnd, stringResource(R.string.chat_end), vm::endChatFromBar,
                    background = MaterialTheme.colorScheme.error.copy(alpha = 0.16f), tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
