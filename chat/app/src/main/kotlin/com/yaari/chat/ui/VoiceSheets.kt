package com.yaari.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.RemoveModerator
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaari.chat.R
import com.yaari.chat.domain.flagOf
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceMember
import com.yaari.chat.model.VoiceRole
import com.yaari.chat.model.VoiceRoom
import com.yaari.chat.model.FriendState
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.heading

/** A tap target with a spoken label (icon-only rows and buttons). */
fun Modifier.tapTarget(label: String, onClick: () -> Unit) = clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label }

/** Title, an optional line under it, and the close button: the top of every room sheet. */
@Composable
fun SheetTitle(title: String, sub: String?, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(top = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            sub?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
        }
        CircleIconButton(Icons.Rounded.Close, stringResource(R.string.close), onClose, Modifier.padding(start = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { content() }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (danger) color else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.padding(start = 18.dp).weight(1f), color = color, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun ageCity(m: VoiceMember) = listOf(m.profile.age.takeIf { it > 0 }?.toString(), m.profile.city.ifBlank { null }).filterNotNull().joinToString(" · ") +
    flagOf(m.profile.country).let { if (it.isEmpty()) "" else " $it" }

/** A person in a room's lists: face, name, badges, and their decorated @ID (age and city until it is known). */
@Composable
private fun PersonLine(m: VoiceMember, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Avatar(m.profile, 40.dp, Modifier.alpha(if (m.lost) LOST_ALPHA else 1f))
                if (m.lost) LostBadge(18.dp, Modifier.align(Alignment.TopEnd).offset(3.dp, (-3).dp))
            }
            Column(Modifier.padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(m.profile.name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    VerifiedMark(m.profile, size = 15.dp)
                    when (m.role) {
                        VoiceRole.HOST -> RoleBadge(stringResource(R.string.voice_host))
                        VoiceRole.COHOST -> RoleBadge(stringResource(R.string.voice_cohost))
                        else -> Unit
                    }
                    if (m.hand) GlyphIcon(Glyph.HAND, size = 16.dp)
                }
                if (m.lost) LostLabel()
                else LookName(m.profile.id, size = 12.sp) {
                    Text(ageCity(m), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        trailing()
    }
}

/** Every sheet the room can open; [sheet] says which one is up. */
@Composable
fun VoiceSheets(vm: AppViewModel, s: VoiceState, room: VoiceRoom, sheet: VoiceSheet?, act: VoiceActor, onSheet: (VoiceSheet?) -> Unit) {
    val close = { onSheet(null) }
    val context = LocalContext.current
    val me = room.me ?: return
    when (sheet) {
        is VoiceSheet.Person -> {
            val target = room.member(sheet.userId)
            if (target != null) PersonSheet(vm, room, me, target, act, close) else LaunchedEffect(sheet) { close() } // they left
        }
        VoiceSheet.Requests -> RequestsSheet(room, act, onPerson = { onSheet(VoiceSheet.Person(it)) }, onClose = close)
        is VoiceSheet.Audience -> AudienceSheet(room, sheet.inviteSeat, act, onPerson = { onSheet(VoiceSheet.Person(it)) }, onClose = close)
        is VoiceSheet.Seat -> SeatSheet(room, sheet.seat, act, onInvite = { onSheet(VoiceSheet.Audience(it)) }, onClose = close)
        VoiceSheet.Queue -> QueueSheet(room.track, room.queue, s.canDj, s.adding, act, vm.voice::addSong, vm.voice::vote, close)
        VoiceSheet.Settings -> SettingsSheet(room, onSave = { title, note, lang -> vm.voice.saveSettings(title, note, lang, close) }, onClose = close)
        VoiceSheet.Invite -> {
            val app by vm.state.collectAsStateWithLifecycle()
            val social by vm.social.state.collectAsStateWithLifecycle()
            InviteSheet(vm, room, app.acceptedFriends(), social, close)
        }
        VoiceSheet.People -> PeopleSheet(vm, room, onPerson = { onSheet(VoiceSheet.Person(it)) }, onClose = close)
        VoiceSheet.Options -> RoomSheet(close) {
            Column(Modifier.padding(bottom = 16.dp)) {
                Box(Modifier.padding(horizontal = 24.dp)) { SheetTitle(stringResource(R.string.voice_options), null, close) }
                Spacer(Modifier.height(8.dp))
                if (!room.rules.isPrivate || room.code != null) {
                    SheetRow(Icons.Rounded.Link, stringResource(R.string.voice_share)) {
                        close()
                        copyLink(context, room)
                    }
                }
                // The code is the host's, for a private room (friends are invited, others get the link).
                room.code?.takeIf { s.isHost && room.rules.isPrivate }?.let { code ->
                    SheetRow(Icons.Rounded.ContentCopy, stringResource(R.string.room_copy, code)) {
                        close()
                        copyCode(context, code)
                    }
                }
                if (s.isHost) SheetRow(Icons.Outlined.Tune, stringResource(R.string.room_settings)) { onSheet(VoiceSheet.Settings) }
                if (!s.isHost) SheetRow(Icons.Outlined.Flag, stringResource(R.string.voice_report_room)) { onSheet(VoiceSheet.ReportRoom) }
                SheetRow(Icons.AutoMirrored.Rounded.Logout, stringResource(if (!s.isHost) R.string.voice_leave_quietly else if (s.heir == null) R.string.voice_end_confirm else R.string.voice_leave), danger = true) {
                    close()
                    vm.voice.leave()
                }
            }
        }
        VoiceSheet.ReportRoom -> RoomSheet(close) {
            Column(Modifier.padding(bottom = 16.dp)) {
                Box(Modifier.padding(horizontal = 24.dp)) { SheetTitle(stringResource(R.string.voice_report_room), stringResource(R.string.voice_report_room_desc), close) }
                Spacer(Modifier.height(8.dp))
                listOf(
                    Triple(ReportReason.HARASSMENT, R.string.voice_report_harassment, R.string.voice_report_harassment_detail),
                    Triple(ReportReason.NUDITY, R.string.voice_report_nudity, R.string.voice_report_nudity_detail),
                    Triple(ReportReason.MINOR, R.string.voice_report_minor, R.string.voice_report_minor_detail),
                    Triple(ReportReason.OTHER, R.string.voice_report_other, R.string.voice_report_other_detail),
                ).forEach { (reason, label, detail) ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { vm.voice.reportRoom(reason, close) }.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(label), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp))
                            Text(stringResource(detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        null -> Unit
    }
}

/**
 * Tapping a person: their card, and the host's and co-hosts' tools (Clubhouse), only those the server would allow
 * (036): host and co-hosts invite up, mute, move to the listeners and remove (a co-host only listeners and speakers);
 * the host makes and removes co-hosts.
 */
@Composable
private fun PersonSheet(vm: AppViewModel, room: VoiceRoom, me: VoiceMember, target: VoiceMember, act: VoiceActor, onClose: () -> Unit) {
    var kick by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    val self = target.profile.id == room.meId
    val mod = me.isMod && !self && target.role != VoiceRole.HOST
    val run = { a: VoiceAction, ok: UiText? -> act(a, ok) { done -> if (done) onClose() } }
    val app by vm.state.collectAsStateWithLifecycle()
    val friend = app.friendState(target.profile.id)
    if (!kick && !report) {
        RoomSheet(onClose) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(target.profile, 72.dp)
                    Text(
                        if (self) "${target.profile.name} (${stringResource(R.string.voice_you)})" else target.profile.name,
                        Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                    )
                    LookName(target.profile.id, Modifier.padding(top = 2.dp), size = 15.sp)
                    Text(ageCity(target), Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        when (target.role) {
                            VoiceRole.HOST -> RoleBadge(stringResource(R.string.voice_host))
                            VoiceRole.COHOST -> RoleBadge(stringResource(R.string.voice_cohost))
                            else -> Unit
                        }
                        Text(
                            when (target.seat) {
                                null -> stringResource(R.string.voice_in_audience)
                                0 -> stringResource(R.string.voice_host_seat)
                                else -> stringResource(R.string.voice_seat, target.seat)
                            },
                            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (!self) SheetRow(Icons.Outlined.PersonOutline, stringResource(R.string.voice_view_profile)) { onClose(); vm.openProfile(target.profile) }
                if (!self) {
                    when (friend) {
                        FriendState.NONE -> SheetRow(Icons.Outlined.PersonAddAlt, stringResource(R.string.add_friend)) { vm.addFriend(target.profile) }
                        FriendState.INCOMING -> SheetRow(Icons.Outlined.PersonAddAlt, stringResource(R.string.accept_request)) { vm.answerFriend(target.profile, true) }
                        FriendState.REQUESTED -> SheetRow(Icons.Rounded.Check, stringResource(R.string.requested), enabled = false) {}
                        FriendState.FRIENDS -> Unit
                    }
                }
                if (self && target.seat != null) {
                    SheetRow(Icons.Rounded.Download, stringResource(R.string.voice_leave_stage)) { run(VoiceAction.Unseat, null) }
                }
                if (room.canInvite(target)) {
                    SheetRow(Icons.Outlined.PersonAdd, stringResource(R.string.voice_invite_speak), enabled = room.freeSeats > 0) {
                        run(VoiceAction.Invite(target.profile.id), UiText(R.string.voice_invited, target.profile.name))
                    }
                }
                if (mod && target.seat != null && !target.muted) {
                    SheetRow(Icons.Rounded.MicOff, stringResource(R.string.voice_mute_them)) { run(VoiceAction.Mute(target.profile.id), null) }
                }
                if (room.canMoveToAudience(target)) {
                    SheetRow(Icons.Rounded.Download, stringResource(R.string.voice_to_audience)) { run(VoiceAction.ToAudience(target.profile.id), null) }
                }
                if (room.canSetCohost(target)) {
                    val co = target.role == VoiceRole.COHOST
                    SheetRow(if (co) Icons.Outlined.RemoveModerator else Icons.Outlined.Shield, stringResource(if (co) R.string.voice_remove_cohost else R.string.voice_make_cohost)) {
                        run(VoiceAction.Cohost(target.profile.id, !co), null)
                    }
                }
                if (room.canKick(target)) SheetRow(Icons.Outlined.PersonOff, stringResource(R.string.voice_kick), danger = true) { kick = true }
                if (!self) SheetRow(Icons.Outlined.Flag, stringResource(R.string.voice_report), danger = true) { report = true }
            }
        }
    }
    if (kick) {
        AlertDialog(
            onDismissRequest = { kick = false },
            title = { Text(stringResource(R.string.voice_kick_title, target.profile.name)) },
            text = { Text(stringResource(R.string.voice_kick_body)) },
            confirmButton = {
                TextButton({ kick = false; run(VoiceAction.Kick(target.profile.id), null) }) {
                    Text(stringResource(R.string.voice_kick_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ kick = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (report) {
        ReportDialog(onDismiss = { report = false }, actions = listOf(stringResource(R.string.report_block))) { reason, _ ->
            report = false
            onClose()
            vm.report(target.profile.id, reason)
        }
    }
}

/**
 * Everyone in the room (the stage first, then the listeners), with search by name, city or ID. Each row: the face, the
 * name and decorated @ID, the role, and the friend button as it stands; a tap opens their card in the room.
 */
@Composable
private fun PeopleSheet(vm: AppViewModel, room: VoiceRoom, onPerson: (String) -> Unit, onClose: () -> Unit) {
    val app by vm.state.collectAsStateWithLifecycle()
    val looks by vm.looks.people.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.reloadFriends() } // the buttons show where each friendship stands now
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    fun shown(m: VoiceMember) = q.isEmpty() || listOfNotNull(m.profile.name, m.profile.city, looks[m.profile.id]?.handle).any { it.lowercase().contains(q) }
    val stage = room.members.filter { it.seat != null }.sortedBy { it.seat }.filter(::shown)
    val listeners = room.audience.filter(::shown)
    RoomSheet(onClose) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Box(Modifier.padding(horizontal = 24.dp)) {
                SheetTitle(pluralStringResource(R.plurals.room_people_n, room.members.size, room.members.size), null, onClose)
            }
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), singleLine = true,
                placeholder = { Text(stringResource(R.string.voice_search_people)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, cursorColor = MaterialTheme.colorScheme.primary),
            )
            val row: @Composable (VoiceMember) -> Unit = { m ->
                PersonLine(m, { onPerson(m.profile.id) }) {
                    if (m.profile.id != room.meId) {
                        FriendButton(
                            app.friendState(m.profile.id), onAdd = { vm.addFriend(m.profile) }, onAccept = { vm.answerFriend(m.profile, true) },
                            onWithdraw = { vm.removeFriend(m.profile) },
                        )
                    }
                }
            }
            if (stage.isNotEmpty()) {
                GroupTitle(stringResource(R.string.room_on_stage_n, stage.size))
                stage.forEach { row(it) }
            }
            if (listeners.isNotEmpty()) {
                GroupTitle(stringResource(R.string.voice_listening_n, listeners.size))
                listeners.forEach { row(it) }
            }
            if (stage.isEmpty() && listeners.isEmpty()) {
                Text(
                    stringResource(R.string.voice_no_match), Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text, Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Where a friendship stands, as a button: Add friend, Requested (a tap withdraws it, as on profiles), Accept, Friends. */
@Composable
private fun FriendButton(state: FriendState, onAdd: () -> Unit, onAccept: () -> Unit, onWithdraw: () -> Unit) {
    when (state) {
        FriendState.NONE -> SoftButton(
            stringResource(R.string.add_friend), onAdd, accent = true, height = 36.dp,
            leading = { Icon(Icons.Outlined.PersonAddAlt, null, Modifier.size(16.dp), tint = Brand.OnSaffron) },
        )
        FriendState.REQUESTED -> SoftButton(stringResource(R.string.requested), onWithdraw, height = 36.dp)
        FriendState.INCOMING -> SoftButton(stringResource(R.string.accept), onAccept, accent = true, height = 36.dp)
        FriendState.FRIENDS -> SoftButton(
            stringResource(R.string.friends), {}, enabled = false, height = 36.dp,
            leading = { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Brand.Online) },
        )
    }
}

/** The raised hands, for the host and co-hosts. */
@Composable
private fun RequestsSheet(room: VoiceRoom, act: VoiceActor, onPerson: (String) -> Unit, onClose: () -> Unit) {
    val hands = room.audience.filter { it.hand }
    val full = room.freeSeats == 0
    RoomSheet(onClose) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Box(Modifier.padding(horizontal = 24.dp)) {
                SheetTitle(stringResource(R.string.voice_requests), stringResource(if (full) R.string.voice_stage_full else R.string.voice_requests_desc), onClose)
            }
            Spacer(Modifier.height(8.dp))
            if (hands.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        GlyphIcon(Glyph.HAND, size = 28.dp)
                    }
                    Text(stringResource(R.string.voice_no_requests), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            hands.forEach { m ->
                PersonLine(m, { onPerson(m.profile.id) }) {
                    CircleIconButton(Icons.Rounded.Close, stringResource(R.string.voice_decline_name, m.profile.name), { act(VoiceAction.Answer(m.profile.id, false), null) {} })
                    val label = stringResource(R.string.voice_accept_name, m.profile.name)
                    SoftButton(
                        stringResource(R.string.voice_accept), { act(VoiceAction.Answer(m.profile.id, true), null) {} },
                        Modifier.semantics { contentDescription = label }, accent = true, enabled = !full,
                        leading = { Icon(Icons.Rounded.Mic, null, Modifier.size(18.dp), tint = Brand.OnSaffron) },
                    )
                }
            }
        }
    }
}

/** Everyone listening, with search. Opened from an empty seat by a host, it invites someone to that seat. */
@Composable
private fun AudienceSheet(room: VoiceRoom, inviteSeat: Int?, act: VoiceActor, onPerson: (String) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val audience = room.audience
    val q = query.trim().lowercase()
    val shown = audience.filter { q.isEmpty() || it.profile.name.lowercase().contains(q) || it.profile.city.lowercase().contains(q) }
        // raised hands first, then me, then everyone else
        .sortedWith(compareByDescending<VoiceMember> { it.hand }.thenByDescending { it.profile.id == room.meId })
    RoomSheet(onClose) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Box(Modifier.padding(horizontal = 24.dp)) {
                SheetTitle(
                    if (inviteSeat != null) stringResource(R.string.voice_invite_to, inviteSeat) else stringResource(R.string.voice_listening_n, audience.size),
                    null, onClose,
                )
            }
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), singleLine = true,
                placeholder = { Text(stringResource(R.string.voice_search_people)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, cursorColor = MaterialTheme.colorScheme.primary),
            )
            if (shown.isEmpty()) {
                Text(
                    stringResource(if (audience.isEmpty()) R.string.voice_no_listeners else R.string.voice_no_match), Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            shown.forEach { m ->
                PersonLine(m, { onPerson(m.profile.id) }) {
                    if (inviteSeat != null && m.profile.id != room.meId) {
                        SoftButton(stringResource(R.string.voice_invite), {
                            act(VoiceAction.Invite(m.profile.id, inviteSeat), UiText(R.string.voice_invited, m.profile.name)) { ok -> if (ok) onClose() }
                        })
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, Modifier.padding(end = 8.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** A host taps an empty seat: invite someone to it, or lock / unlock it. */
@Composable
private fun SeatSheet(room: VoiceRoom, seat: Int, act: VoiceActor, onInvite: (Int) -> Unit, onClose: () -> Unit) {
    val locked = room.locked(seat)
    RoomSheet(onClose) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Box(Modifier.padding(horizontal = 24.dp)) { SheetTitle(stringResource(R.string.voice_seat, seat), null, onClose) }
            Spacer(Modifier.height(8.dp))
            SheetRow(Icons.Outlined.PersonAdd, stringResource(R.string.voice_invite_to, seat), enabled = !locked) { onInvite(seat) }
            SheetRow(if (locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, stringResource(if (locked) R.string.voice_unlock_seat else R.string.voice_lock_seat)) {
                act(VoiceAction.Lock(seat, !locked), null) { ok -> if (ok) onClose() }
            }
        }
    }
}

/** Host: the room's title and its pinned announcement. */
@Composable
private fun SettingsSheet(room: VoiceRoom, onSave: (String, String, String?) -> Unit, onClose: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(room.title) }
    var note by rememberSaveable { mutableStateOf(room.announcement.orEmpty()) }
    var lang by rememberSaveable { mutableStateOf(room.lang) }
    RoomSheet(onClose) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            SheetTitle(stringResource(R.string.room_settings), stringResource(R.string.room_settings_desc), onClose)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                title, { if (it.length <= VoiceRoom.MAX_TITLE) title = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.create_name)) }, shape = RoundedCornerShape(12.dp),
                supportingText = { Text("${title.length}/${VoiceRoom.MAX_TITLE}") },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                note, { if (it.length <= VoiceRoom.MAX_ANNOUNCEMENT) note = it }, Modifier.fillMaxWidth(), minLines = 3, maxLines = 5,
                label = { Text(stringResource(R.string.create_announcement)) }, shape = RoundedCornerShape(12.dp),
                placeholder = { Text(stringResource(R.string.create_announcement_placeholder)) },
                supportingText = { Text(stringResource(R.string.create_announcement_hint) + "  ${note.length}/${VoiceRoom.MAX_ANNOUNCEMENT}") },
            )
            Spacer(Modifier.height(8.dp))
            LanguageSelect(lang, { lang = it }, Modifier.fillMaxWidth(), height = 52.dp)
            Spacer(Modifier.height(16.dp))
            PrimaryButton(stringResource(R.string.save), { onSave(title, note, lang.takeIf { it != room.lang }) }, enabled = title.trim().length >= VoiceRoom.MIN_TITLE)
        }
    }
}
