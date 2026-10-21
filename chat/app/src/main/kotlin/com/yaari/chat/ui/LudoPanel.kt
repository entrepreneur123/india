package com.yaari.chat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaari.chat.R
import com.yaari.chat.domain.Ludo
import com.yaari.chat.model.Gender
import com.yaari.chat.model.LudoAction
import com.yaari.chat.model.LudoEvent
import com.yaari.chat.model.LudoGame
import com.yaari.chat.model.LudoOut
import com.yaari.chat.model.LudoStatus
import com.yaari.chat.model.Profile
import com.yaari.chat.model.RoomLeader
import com.yaari.chat.model.VoiceRoom

// ---- Ludo in a voice room (the web's components/screens/voice/ludo/ludo-panel.tsx) ------------------------------
// The lobby (colours, join, start), the board with the four players around it, and the result. Everyone in the room
// sees it; people without a colour watch. The voice and the chat carry on around it.

private val COLOUR_NAMES = listOf(R.string.ludo_red, R.string.ludo_green, R.string.ludo_yellow, R.string.ludo_blue)

/** The board's palette for the surface it sits on (the voice room is always dark; the light one is for day screens). */
@Composable
fun ludoPalette() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) LudoPalette.Night else LudoPalette.Light

/** A colour's name; in the middle of a sentence it is written in lower case ("a green token"). */
@Composable
private fun colourName(seat: Int, inSentence: Boolean = false): String {
    val word = stringResource(COLOUR_NAMES[seat])
    val locale = LocalConfiguration.current.locales[0]
    return if (inSentence) word.lowercase(locale) else word
}

/** Everything the panel can do, as the room screen wires it (VoiceController). */
class LudoHandlers(
    val act: (LudoAction, (Boolean) -> Unit) -> Unit,
    val showStage: () -> Unit,
    val askClose: () -> Unit,
    val dismiss: () -> Unit,
    val person: (String) -> Unit,
)

@Composable
fun LudoPanel(room: VoiceRoom, game: LudoGame, isMod: Boolean, levels: State<Map<String, Float>>, boardMax: Dp, on: LudoHandlers) {
    val me = room.meId
    val palette = ludoPalette()
    val reduce = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val play = remember { LudoPlayback(game) }
    play.feel = { f -> haptics.performHapticFeedback(if (f == LudoFeel.CAPTURE) HapticFeedbackType.LongPress else HapticFeedbackType.SegmentTick) }
    val still by rememberUpdatedState(reduce)
    LaunchedEffect(Unit) { play.play { still } }
    LaunchedEffect(game) { play.offer(game) }

    val title = stringResource(R.string.ludo_title)
    Column(Modifier.fillMaxWidth().semantics { paneTitle = title }) {
        Toolbar(room, game, isMod, levels, on)
        if (game.status == LudoStatus.LOBBY) {
            Lobby(room, game, isMod, palette, on)
        } else {
            Board(room, game, play, isMod, palette, boardMax, on)
        }
        // What just happened, for TalkBack (rolls too, which the banner leaves out).
        val said = play.news?.let { newsText(it, room) }.orEmpty()
        Text(said, Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = said }, color = Color.Transparent, fontSize = 1.sp)
    }
}

/** Someone's name in the game: the person in the room, else the name they sat down with, else the colour. */
@Composable
private fun nameOf(room: VoiceRoom, g: LudoGame, seat: Int): String =
    g.players[seat]?.let { room.member(it)?.profile?.name } ?: g.names[seat] ?: colourName(seat)

/** Someone who sat down, as a profile for the avatar (photo when they're still in the room). */
private fun personOf(room: VoiceRoom, g: LudoGame, seat: Int): Profile? {
    val id = g.players[seat] ?: return null
    return room.member(id)?.profile ?: Profile(id, g.names[seat] ?: "?", Gender.OTHER, 0, "", "", colorSeed = id.hashCode())
}

@Composable
private fun newsText(n: LudoNews, room: VoiceRoom): String {
    val e = n.event
    val who = nameOf(room, n.game, e.s)
    val mine = n.game.players[e.s] == room.meId
    return when (e) {
        is LudoEvent.Start -> stringResource(R.string.ludo_started, who)
        is LudoEvent.Roll -> if (mine) stringResource(R.string.ludo_rolled_you, e.d) else stringResource(R.string.ludo_rolled, who, e.d)
        is LudoEvent.Move ->
            if (e.cap.isNotEmpty()) stringResource(R.string.ludo_captured, who, colourName(e.cap[0].first, inSentence = true))
            else if (e.to == Ludo.HOME) stringResource(R.string.ludo_home, who) else ""
        is LudoEvent.Pass -> stringResource(R.string.ludo_no_move, who)
        is LudoEvent.Triple6 -> stringResource(R.string.ludo_triple, who)
        is LudoEvent.Timeout -> stringResource(R.string.ludo_timed_out, who)
        is LudoEvent.Out -> stringResource(if (e.why == LudoOut.TIMEOUTS) R.string.ludo_out_timeouts else R.string.ludo_out_left, who)
        is LudoEvent.Win -> if (mine) stringResource(R.string.ludo_you_win) else stringResource(R.string.ludo_wins, who)
    }
}

/** The people on the stage, small, with their speaking rings (the room goes on while the board is up), and the game's tools. */
@Composable
private fun Toolbar(room: VoiceRoom, game: LudoGame, isMod: Boolean, levels: State<Map<String, Float>>, on: LudoHandlers) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            room.members.filter { it.seat != null }.sortedBy { it.seat }.forEach { m ->
                Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable(role = Role.Button) { on.person(m.profile.id) }
                        .semantics { contentDescription = m.profile.name },
                    contentAlignment = Alignment.Center,
                ) {
                    SpeakingRing(levels, m.profile.id, 30.dp)
                    Avatar(m.profile, 30.dp)
                }
            }
        }
        if (game.seatOf(room.meId) < 0 && game.status != LudoStatus.LOBBY) {
            Text(
                stringResource(R.string.ludo_watching), Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        CircleIconButton(Icons.Outlined.Groups, stringResource(R.string.ludo_show_stage), on.showStage, iconSize = 22.dp)
        if (isMod && game.status != LudoStatus.FINISHED) CircleIconButton(Icons.Rounded.Close, stringResource(R.string.ludo_close), on.askClose)
    }
}

// ---- the game ----

@Composable
private fun Board(room: VoiceRoom, game: LudoGame, play: LudoPlayback, isMod: Boolean, palette: LudoPalette, boardMax: Dp, on: LudoHandlers) {
    val view = play.view
    val reduce = LocalReducedMotion.current
    val mySeat = game.seatOf(room.meId)
    val seated = mySeat >= 0 && !game.out[mySeat]
    val myTurn = seated && game.status == LudoStatus.PLAYING && game.turn == mySeat && !play.busy
    val canRoll = myTurn && game.dice == null && !play.rolling
    val movable = if (play.busy) emptySet() else Ludo.movable(game, mySeat)
    var acting by remember { mutableStateOf(false) }
    val roll = {
        if (canRoll && !acting) {
            acting = true
            play.rollPressed()
            on.act(LudoAction.Roll(game.version)) { ok ->
                acting = false
                if (!ok) play.rollFailed()
            }
        }
    }
    val move = { idx: Int -> if (idx in movable) on.act(LudoAction.Move(idx % 4, game.version)) {} }

    // A capture, a token home, three sixes...: the banner shows it for a moment.
    var flash by remember { mutableStateOf<LudoNews?>(null) }
    LaunchedEffect(play.news) { play.news?.takeIf { it.show }?.let { flash = it } }
    LaunchedEffect(flash) {
        if (flash != null) {
            kotlinx.coroutines.delay(2_400)
            flash = null
        }
    }

    val names = (0..3).map { nameOf(room, view, it) }
    val tokenViews = play.tokens.mapIndexed { idx, pos ->
        val seat = idx / 4
        val colour = colourName(seat)
        LudoTokenView(
            idx, pos,
            when {
                pos < 0 -> stringResource(R.string.ludo_token_base, colour, idx % 4 + 1)
                pos == Ludo.HOME -> stringResource(R.string.ludo_token_home, colour, idx % 4 + 1)
                else -> stringResource(R.string.ludo_token, colour, idx % 4 + 1, Ludo.stepsHome(pos))
            },
            movable = idx in movable, motion = play.motion[idx],
        )
    }
    val inPlay = (0..3).map { view.players[it] != null && !view.out[it] }
    val chip = @Composable { seat: Int, right: Boolean, modifier: Modifier ->
        val active = view.status == LudoStatus.PLAYING && view.turn == seat && view.players[seat] != null
        val isMe = view.players[seat] == room.meId
        val status = when {
            view.out[seat] -> stringResource(R.string.ludo_out)
            active && isMe -> stringResource(if (game.dice != null && !play.busy) R.string.ludo_pick else R.string.ludo_your_turn)
            isMe -> stringResource(R.string.ludo_you)
            else -> colourName(seat)
        }
        val diceLabel = if (isMe && canRoll) stringResource(R.string.ludo_roll)
        else stringResource(R.string.ludo_turn_of, names[seat]) + (play.dice?.let { ", " + stringResource(R.string.ludo_dice_shows, it) } ?: "")
        PlayerChip(
            modifier, right, palette.seats[seat], personOf(room, view, seat), view.players[seat]?.let { names[seat] }, status, view.out[seat], active, isMe,
            deadline = if (active && !play.busy) view.deadline else null,
            onPerson = view.players[seat]?.takeIf { room.member(it) != null }?.let { id -> { on.person(id) } },
        ) {
            if (active) LudoDice(play.dice, play.rolling, 30.dp, palette.seats[seat], enabled = isMe && canRoll, label = diceLabel, onRoll = roll)
        }
    }

    val winner = if (view.status == LudoStatus.FINISHED) view.winner else null
    val turnSeat = if (view.status == LudoStatus.PLAYING) view.turn else null
    val banner: Pair<String, Color?> = when {
        flash != null -> newsText(flash!!, room) to MaterialTheme.colorScheme.onSurface
        turnSeat == null -> "" to null
        turnSeat == mySeat ->
            "${stringResource(R.string.ludo_your_turn)} · ${stringResource(if (game.dice != null && !play.busy) R.string.ludo_pick else R.string.ludo_roll)}" to palette.seats[turnSeat]
        else -> stringResource(R.string.ludo_turn_of, names[turnSeat]) to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.widthIn(max = boardMax), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chip(1, false, Modifier.weight(1f))
                chip(2, true, Modifier.weight(1f))
            }
            Box {
                LudoBoard(
                    tokenViews, inPlay, turnSeat, palette, stringResource(R.string.ludo_board),
                    Modifier.fillMaxWidth().shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(RoundedCornerShape(18.dp)),
                    onToken = move,
                )
                if (view.status == LudoStatus.FINISHED && !play.busy) {
                    Result(
                        room, view, winner?.let { personOf(room, view, it) }, winner?.let { names[it] }, winner?.let { palette.seats[it] },
                        youWon = winner != null && view.players[winner] == room.meId, isMod = isMod, on = on,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
            // The turn banner: what just happened for a moment, else whose turn it is. Off the board, so it never
            // hides a token; one fixed line, so nothing below it moves.
            AnimatedContent(
                banner, Modifier.fillMaxWidth().height(20.dp).clearAndSetSemantics {},
                transitionSpec = {
                    if (reduce) fadeIn(tween(180)) togetherWith fadeOut(tween(180))
                    else (slideInVertically(tween(180)) { it / 2 } + fadeIn(tween(180))) togetherWith (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(180)))
                },
                label = "banner",
            ) { (text, colour) ->
                Text(
                    text, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = colour ?: Color.Unspecified, fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = Jakarta,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chip(0, false, Modifier.weight(1f))
                chip(3, true, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PlayerChip(
    modifier: Modifier, right: Boolean, colour: Color, person: Profile?, name: String?, status: String, out: Boolean,
    active: Boolean, mine: Boolean, deadline: Long?, onPerson: (() -> Unit)?, dice: @Composable () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val bg = MaterialTheme.colorScheme.background
    Row(
        modifier.height(48.dp).graphicsLayer { alpha = if (out) 0.45f else 1f },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (right) Arrangement.spacedBy(8.dp, Alignment.End) else Arrangement.spacedBy(8.dp),
    ) {
        val avatar = @Composable {
            Box(
                Modifier.size(44.dp).then(if (onPerson != null) Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onPerson) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (deadline != null) TurnRing(deadline, colour, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), Modifier.fillMaxSize())
                if (person != null) {
                    Box(
                        Modifier.size(34.dp).then(
                            if (active) Modifier.drawBehind { drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = 0.6f), Color.Transparent)), size.minDimension * 0.8f) }
                            else Modifier,
                        ),
                    ) { Avatar(person, 34.dp) }
                } else {
                    Box(
                        Modifier.size(34.dp).drawBehind {
                            val w = 1.5.dp.toPx()
                            drawCircle(colour.copy(alpha = 0.55f), size.minDimension / 2 - w / 2, style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                        },
                    )
                }
                Box(
                    Modifier.align(if (right) Alignment.BottomStart else Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(bg).padding(2.dp)
                        .clip(CircleShape).background(colour),
                )
            }
        }
        val text = @Composable {
            Column(Modifier.weight(1f, fill = false), horizontalAlignment = if (right) Alignment.End else Alignment.Start) {
                Text(
                    name ?: "—", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = Jakarta, color = if (name == null) muted else Color.Unspecified,
                )
                Text(
                    status, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, lineHeight = 14.sp, fontFamily = Jakarta,
                    fontWeight = if (active && mine) FontWeight.SemiBold else FontWeight.Normal, color = if (active && mine) colour else muted,
                )
            }
        }
        if (right) {
            dice()
            text()
            avatar()
        } else {
            avatar()
            text()
            dice()
        }
    }
}

// ---- the lobby ----

@Composable
private fun Lobby(room: VoiceRoom, game: LudoGame, isMod: Boolean, palette: LudoPalette, on: LudoHandlers) {
    var busy by remember { mutableStateOf(false) }
    val run = { a: LudoAction ->
        busy = true
        on.act(a) { busy = false }
    }
    val mySeat = game.seatOf(room.meId)
    val count = game.players.count { it != null }
    val card = MaterialTheme.colorScheme.surfaceContainer
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(card)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LudoBoard(
            List(16) { LudoTokenView(it, -1, "", false, null) }, game.players.map { it != null }, null, palette, stringResource(R.string.ludo_board),
            Modifier.width(148.dp).shadow(10.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).clearAndSetSemantics {},
        )
        Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopicIcon(Topic.GAMING, size = 22.dp)
                Text(stringResource(R.string.ludo_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            Text(
                stringResource(R.string.ludo_hint), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in listOf(0 to 1, 2 to 3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (seat in listOf(row.first, row.second)) {
                            SeatTile(room, game, seat, palette, card, busy, Modifier.weight(1f)) { run(LudoAction.Join(seat)) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isMod) SoftButton(stringResource(R.string.ludo_start), { run(LudoAction.Start) }, accent = true, enabled = count >= 2 && !busy, height = 44.dp)
                if (mySeat >= 0) SoftButton(stringResource(R.string.ludo_leave_seat), { run(LudoAction.Leave) }, enabled = !busy, height = 44.dp)
                val note = when {
                    count < 2 -> stringResource(R.string.ludo_need_two)
                    !isMod -> stringResource(R.string.ludo_wait_host)
                    else -> ""
                }
                Text(
                    note, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SeatTile(room: VoiceRoom, game: LudoGame, seat: Int, palette: LudoPalette, card: Color, busy: Boolean, modifier: Modifier, onJoin: () -> Unit) {
    val c = palette.seats[seat]
    val shape = RoundedCornerShape(16.dp)
    val person = personOf(room, game, seat)
    val free = person == null
    val joinAs = stringResource(R.string.ludo_join_as, colourName(seat, inSentence = true))
    Row(
        modifier.height(56.dp).clip(shape).background(mix(c, palette.wash, card)).border(1.dp, c.copy(alpha = 0.3f), shape)
            .then(if (free) Modifier.clickable(enabled = !busy, role = Role.Button, onClick = onJoin).semantics { contentDescription = joinAs } else Modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (person != null) {
                Avatar(person, 34.dp)
                Box(Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(card).padding(2.dp).clip(CircleShape).background(c))
            } else {
                Box(
                    Modifier.size(34.dp).drawBehind {
                        val w = 1.5.dp.toPx()
                        drawCircle(c, size.minDimension / 2 - w / 2, style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = c) }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                person?.name ?: stringResource(R.string.ludo_join), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = Jakarta, color = if (free) c else Color.Unspecified,
            )
            Text(
                if (game.players[seat] == room.meId) stringResource(R.string.ludo_you) else colourName(seat), maxLines = 1,
                fontSize = 11.sp, lineHeight = 14.sp, fontFamily = Jakarta, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- the result ----

/** The first player home: the game is over (short sessions). Hosts can start another with the same colours. */
@Composable
private fun Result(
    room: VoiceRoom, game: LudoGame, winner: Profile?, winnerName: String?, colour: Color?, youWon: Boolean, isMod: Boolean,
    on: LudoHandlers, modifier: Modifier,
) {
    val reduce = LocalReducedMotion.current
    val c = colour ?: Brand.Pink
    val title = when {
        youWon -> stringResource(R.string.ludo_you_win)
        winnerName != null -> stringResource(R.string.ludo_wins, winnerName)
        else -> stringResource(R.string.ludo_title)
    }
    var shown by remember(game.id) { mutableStateOf(false) }
    LaunchedEffect(game.id) { shown = true }
    Box(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.6f)).semantics { paneTitle = title }.padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(shown, enter = if (reduce) fadeIn(tween(200)) else fadeIn(tween(250)) + scaleIn(initialScale = 0.9f)) {
            Column(
                Modifier.widthIn(max = 300.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                    .drawBehind { drawRect(Brush.radialGradient(listOf(c.copy(alpha = 0.26f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width / 2, 0f), radius = size.width * 0.75f)) }
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Trophy(44.dp)
                if (winner != null) {
                    Box(
                        Modifier.padding(top = 8.dp).size(72.dp).drawBehind {
                            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.55f), Color.Transparent)), size.minDimension * 0.62f)
                            drawCircle(c, size.minDimension / 2 - 1.5.dp.toPx(), style = Stroke(3.dp.toPx()))
                        },
                        contentAlignment = Alignment.Center,
                    ) { Avatar(winner, 60.dp) }
                }
                Text(title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.ludo_ends_note), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                if (isMod) {
                    PrimaryButton(stringResource(R.string.ludo_play_again), { on.act(LudoAction.Open) {} }, height = 44.dp)
                    Spacer(Modifier.height(8.dp))
                }
                SoftButton(stringResource(R.string.ludo_back), on.dismiss, Modifier.fillMaxWidth(), height = 44.dp)
            }
        }
    }
}

/** Over the room: closing the game ends it for everyone. */
@Composable
fun LudoCloseDialog(onCancel: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.ludo_close_title)) },
        text = { Text(stringResource(R.string.ludo_close_body)) },
        confirmButton = { TextButton(onClose) { Text(stringResource(R.string.ludo_close_confirm), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A game is on but the stage is shown: a way back to it. */
@Composable
fun LudoBackPill(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        SoftButton(
            "${stringResource(R.string.ludo_in_progress)} · ${stringResource(R.string.ludo_back_to_game)}", onClick, height = 36.dp,
            leading = { TopicIcon(Topic.GAMING, size = 18.dp) },
        )
    }
}

/** A games room with no game on: the board, the four colours, and (hosts and co-hosts) the button to open one. */
@Composable
fun StartLudoCard(canStart: Boolean, onStart: () -> Unit) {
    val palette = ludoPalette()
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LudoBoard(
            List(16) { LudoTokenView(it, -1, "", false, null) }, listOf(true, true, true, true), null, palette, stringResource(R.string.ludo_board),
            Modifier.width(176.dp).shadow(10.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).clearAndSetSemantics {},
        )
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TopicIcon(Topic.GAMING, size = 24.dp)
            Text(stringResource(R.string.games_start), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        }
        Text(
            stringResource(R.string.games_start_body), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Row(Modifier.padding(top = 12.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..3).forEach { seat ->
                Row(
                    Modifier.height(28.dp).clip(CircleShape).background(palette.seats[seat].copy(alpha = 0.16f)).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(palette.seats[seat]))
                    Text(colourName(seat), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (canStart) {
            PrimaryButton(stringResource(R.string.games_start), onStart, height = 48.dp)
        } else {
            Text(
                stringResource(R.string.games_wait_host), Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Gold, silver and bronze for the top three. */
private val MEDALS = listOf(Color(0xFFF2C14E), Color(0xFFC5CCD6), Color(0xFFD89A63))

/** The room's Ludo winners, top 5 (null while loading); my own line is highlighted. */
@Composable
fun Leaderboard(leaders: List<RoomLeader>?, meId: String) {
    val title = stringResource(R.string.games_leaders)
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)).padding(16.dp),
    ) {
        Row(Modifier.semantics { heading() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TopicIcon(Topic.GAMING, size = 20.dp)
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
        when {
            leaders == null -> repeat(3) { Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(36.dp).clip(RoundedCornerShape(12.dp)).shimmer()) }
            leaders.isEmpty() -> Text(
                stringResource(R.string.games_no_wins), Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Column(Modifier.padding(top = 6.dp)) {
                leaders.forEachIndexed { i, l ->
                    val rank = stringResource(R.string.games_rank, i + 1)
                    val wins = pluralStringResource(R.plurals.games_wins, l.wins, l.wins)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .then(if (l.profile.id == meId) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) else Modifier)
                            .semantics(mergeDescendants = true) { contentDescription = "$rank, ${l.profile.name}, $wins" }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).then(if (i < 3) Modifier.background(MEDALS[i]) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${i + 1}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (i < 3) Color(0xFF2B1A00) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Avatar(l.profile, 30.dp, Modifier.padding(start = 12.dp))
                        Text(
                            l.profile.name, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(wins, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
