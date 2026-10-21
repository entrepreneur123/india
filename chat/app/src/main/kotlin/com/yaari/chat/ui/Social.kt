package com.yaari.chat.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.yaari.chat.AppLocale
import com.yaari.chat.R
import com.yaari.chat.data.DeepLink
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.InboxItem
import com.yaari.chat.model.InboxKind
import com.yaari.chat.model.Interest
import com.yaari.chat.model.Profile
import com.yaari.chat.model.ProfilePhoto
import com.yaari.chat.model.RoomLang
import com.yaari.chat.model.VoiceRoom

// ---- Notifications, interests, photos, invites and the first-run tour (as the web's components/social) ----------

/** A count on a brand disc ("99+" above 99); nothing for 0. */
@Composable
fun CountBadge(n: Int, modifier: Modifier = Modifier) {
    if (n <= 0) return
    Box(
        modifier.height(18.dp).widthIn(min = 18.dp).clip(CircleShape).background(Brand.Pink).padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (n > 99) "99+" else "$n", color = Color.White, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Jakarta)
    }
}

/** The bell that opens Notifications, with the unread count. */
@Composable
fun BellButton(count: Int, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val label = if (count > 0) stringResource(R.string.inbox_open_unread, count) else stringResource(R.string.inbox_open)
    Box {
        CircleIconButton(Icons.Outlined.Notifications, label, onClick, tint = tint)
        CountBadge(count, Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp))
    }
}

/** "Mon 4 min. ago" style relative time. */
private fun ago(at: Long) =
    DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

/** Notifications: friend requests and acceptances, friends' messages (one line per chat) and room invites. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxSheet(vm: AppViewModel, s: SocialState) {
    ModalBottomSheet(
        onDismissRequest = vm.social::closeInbox, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.inbox_title), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                CircleIconButton(Icons.Rounded.Close, stringResource(R.string.close), vm.social::closeInbox)
            }
            val items = s.inbox
            when {
                items == null && s.inboxFailed -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(Icons.Outlined.People, stringResource(R.string.inbox_load_failed), stringResource(R.string.err_network))
                    SoftButton(stringResource(R.string.common_try_again), vm.social::loadInbox, outlined = true)
                }
                items == null -> Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(4) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(CircleShape).shimmer())
                            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.width(180.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                                Box(Modifier.width(72.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                            }
                        }
                    }
                }
                items.isEmpty() -> EmptyState(Icons.Outlined.People, stringResource(R.string.inbox_empty), stringResource(R.string.inbox_empty_body))
                else -> {
                    val newer = items.filter(s::isNew)
                    val older = items.filterNot(s::isNew)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                        if (newer.isNotEmpty()) {
                            item(key = "new") { GroupLabel(stringResource(R.string.inbox_new)) }
                            items(newer, key = { it.id }) { InboxRow(vm, it, highlight = true, busy = s.busy) }
                        }
                        if (older.isNotEmpty()) {
                            item(key = "earlier") { GroupLabel(stringResource(R.string.inbox_earlier)) }
                            items(older, key = { it.id }) { InboxRow(vm, it, highlight = false, busy = s.busy) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(), Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.6.sp,
    )
}

/** The sentence of an item, in the app's language. */
@Composable
fun inboxText(i: InboxItem): String {
    val name = i.actor.name
    return when (i.kind) {
        InboxKind.MESSAGE -> if (i.count > 1) pluralStringResource(R.plurals.inbox_messages, i.count, name, i.count) else stringResource(R.string.inbox_message_one, name)
        InboxKind.FRIEND_REQUEST -> stringResource(if (i.pending) R.string.inbox_request else R.string.inbox_request_done, name)
        InboxKind.FRIEND_ACCEPT -> stringResource(R.string.inbox_accept, name)
        InboxKind.ROOM_INVITE -> stringResource(R.string.inbox_invite, name, i.roomTitle.orEmpty())
    }
}

@Composable
private fun InboxRow(vm: AppViewModel, i: InboxItem, highlight: Boolean, busy: Long?) {
    val working = busy == i.id
    Row(
        Modifier.fillMaxWidth().then(if (highlight) Modifier.background(Brand.Pink.copy(alpha = 0.06f)) else Modifier)
            .clickable(role = Role.Button) { vm.social.closeInbox(); vm.openProfile(i.actor) }
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(i.actor, 44.dp)
            if (i.kind == InboxKind.ROOM_INVITE) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(22.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface).padding(2.dp).clip(CircleShape).background(Brand.Pink),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Headphones, null, Modifier.size(12.dp), tint = Color.White) }
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(inboxText(i), style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                if (i.kind == InboxKind.ROOM_INVITE && !i.roomOpen) stringResource(R.string.inbox_invite_ended) else ago(i.at),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
        when {
            i.kind == InboxKind.FRIEND_REQUEST && i.pending -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(
                    Icons.Rounded.Close, stringResource(R.string.decline), { vm.inboxAnswer(i, false) }, enabled = !working, size = 36.dp, iconSize = 18.dp,
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).size(36.dp),
                )
                SoftButton(
                    stringResource(R.string.accept), { vm.inboxAnswer(i, true) }, accent = true, enabled = !working, height = 36.dp,
                    leading = { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Color.White) },
                )
            }
            i.kind == InboxKind.MESSAGE || i.kind == InboxKind.FRIEND_ACCEPT -> SoftButton(
                stringResource(if (i.kind == InboxKind.MESSAGE) R.string.inbox_reply else R.string.message), { vm.inboxChat(i) },
                enabled = !working, height = 36.dp,
                leading = { Icon(Icons.AutoMirrored.Outlined.Chat, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface) },
            )
            i.kind == InboxKind.ROOM_INVITE && i.roomOpen -> SoftButton(stringResource(R.string.inbox_join), { vm.inboxJoin(i) }, accent = true, enabled = !working, height = 36.dp)
            else -> Unit
        }
    }
}

// ---- interests ----

/** Interest chips; [shared] ones are highlighted ("You both like"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestChips(tags: List<Interest>, modifier: Modifier = Modifier, shared: Set<Interest> = emptySet()) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { t ->
            val on = t in shared
            Text(
                stringResource(t.label),
                Modifier.clip(CircleShape).background(if (on) Brand.Pink.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge, color = if (on) Brand.Pink else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Pick 3 to 5; asked once after sign-up (skippable) and from my profile. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InterestsSheet(vm: AppViewModel, me: Profile, s: SocialState) {
    var picked by rememberSaveable { mutableStateOf(me.interests.map { it.code }.toSet()) }
    val first = s.firstRun == FirstRunStep.INTERESTS
    ModalBottomSheet(
        onDismissRequest = vm.social::skipInterests, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.interests_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(
                        stringResource(R.string.interests_body), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CircleIconButton(Icons.Rounded.Close, stringResource(R.string.close), vm.social::skipInterests)
            }
            FlowRow(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Interest.entries.forEach { t ->
                    val on = t.code in picked
                    val full = !on && picked.size >= Interest.MAX
                    Row(
                        Modifier.height(44.dp).clip(CircleShape)
                            .then(if (on) Modifier.background(Brand.Pink) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                            .toggleable(on, enabled = !full, role = Role.Checkbox) { picked = if (on) picked - t.code else picked + t.code }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (on) Icon(Icons.Rounded.Check, null, Modifier.padding(end = 6.dp).size(16.dp), tint = Color.White)
                        Text(
                            stringResource(t.label), style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                            color = when {
                                on -> Color.White
                                full -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.interests_count, picked.size), Modifier.padding(top = 20.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton(
                    stringResource(if (first) R.string.interests_skip else R.string.cancel), vm.social::skipInterests, Modifier.weight(1f),
                    height = 52.dp, outlined = true,
                )
                PrimaryButton(
                    stringResource(R.string.interests_save), { vm.social.saveInterests(Interest.of(picked).toSet()) }, Modifier.weight(1f),
                    enabled = picked.size in Interest.MIN..Interest.MAX && !s.savingInterests, height = 52.dp,
                )
            }
        }
    }
}

/** Interests on a profile: mine with "Edit interests" (or a prompt to add them), someone else's with what we share. */
@Composable
fun InterestsSection(p: Profile, mine: Boolean, onEdit: () -> Unit, shared: Set<Interest> = emptySet()) {
    if (!mine && p.interests.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.interests_label), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            if (mine) {
                Text(
                    stringResource(if (p.interests.isEmpty()) R.string.interests_add else R.string.interests_edit),
                    Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onEdit).padding(horizontal = 8.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (p.interests.isNotEmpty()) InterestChips(p.interests, Modifier.padding(top = 4.dp), shared)
        if (!mine && shared.isNotEmpty()) {
            Text(
                stringResource(R.string.interests_common), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall,
                color = Brand.Pink,
            )
        }
    }
}

// ---- photos ----

/**
 * Up to 4 more photos as square tiles. Mine: an "Add photo" tile (each photo is shrunk and checked on this phone first)
 * and a remove button on each; tapping a photo opens it full screen.
 */
@Composable
fun PhotosSection(photos: List<ProfilePhoto>, editable: Boolean, busy: Boolean = false, onAdd: (String) -> Unit = {}, onRemove: (String) -> Unit = {}) {
    if (!editable && photos.isEmpty()) return
    var open by remember { mutableStateOf<Int?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { onAdd(it.toString()) } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.photos_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            photos.forEachIndexed { i, p ->
                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    AsyncImage(
                        p.url, stringResource(R.string.photos_view, i + 1, photos.size),
                        Modifier.fillMaxSize().clickable(role = Role.Image) { open = i }, contentScale = ContentScale.Crop,
                    )
                    if (editable) {
                        Box(Modifier.align(Alignment.TopEnd)) {
                            CircleIconButton(
                                Icons.Rounded.Close, stringResource(R.string.photos_remove), { onRemove(p.id) },
                                background = Color.Black.copy(alpha = 0.55f), tint = Color.White, size = 28.dp, iconSize = 16.dp,
                            )
                        }
                    }
                }
            }
            if (editable && photos.size < ProfilePhoto.MAX) {
                val line = MaterialTheme.colorScheme.outline
                Column(
                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                        .drawBehind {
                            drawRoundRect(
                                line, cornerRadius = CornerRadius(12.dp.toPx()),
                                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                            )
                        }
                        .clickable(enabled = !busy, role = Role.Button) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(if (busy) R.string.photos_checking else R.string.photos_add), Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
            repeat((ProfilePhoto.MAX - photos.size - if (editable) 1 else 0).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
        }
        if (editable) {
            Text(
                stringResource(R.string.photos_hint), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    open?.let { i ->
        photos.getOrNull(i)?.let { p ->
            AlertDialog(
                onDismissRequest = { open = null },
                confirmButton = {},
                text = {
                    AsyncImage(
                        p.url, stringResource(R.string.photos_view, i + 1, photos.size), Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit,
                    )
                },
            )
        }
    }
}

// ---- room language, search ----

/** A room's language as the language's own name (as the web's chip). */
fun roomLangName(tag: String?): String? = RoomLang.of(tag)?.let { AppLocale.nameOf(it) }

@Composable
fun RoomLangChip(tag: String?, modifier: Modifier = Modifier) {
    val name = roomLangName(tag) ?: return
    Text(
        name, modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
    )
}

/** A language picker as an outlined field with a menu; [anyLabel] adds "All languages" (null value). */
@Composable
fun LanguageSelect(value: String?, onChange: (String?) -> Unit, modifier: Modifier = Modifier, anyLabel: String? = null, height: Dp = 48.dp) {
    var open by remember { mutableStateOf(false) }
    val label = roomLangName(value) ?: anyLabel ?: ""
    val a11y = stringResource(R.string.rooms_lang)
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(height).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(role = Role.DropdownList) { open = true }.semantics { contentDescription = "$a11y: $label" }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Translate, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(open, { open = false }) {
            if (anyLabel != null) DropdownMenuItem({ Text(anyLabel) }, { open = false; onChange(null) })
            RoomLang.tags.forEach { tag -> DropdownMenuItem({ Text(roomLangName(tag).orEmpty()) }, { open = false; onChange(tag) }) }
        }
    }
}

/** The app's language as a room language (a new room speaks it by default). */
@Composable
fun appRoomLang(): String {
    val tag = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]?.toLanguageTag().orEmpty()
    return RoomLang.tags.firstOrNull { tag.startsWith(it, ignoreCase = true) } ?: RoomLang.tags.firstOrNull { tag.substringBefore('-') == it.substringBefore('-') } ?: "en"
}

// ---- invites and links ----

/** The room's link to share (WhatsApp...) or copy. */
fun shareRoomLink(context: Context, room: VoiceRoom) {
    val url = DeepLink.roomLink(room.id, room.code, room.rules.isPrivate)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.invite_share_text, room.title) + "\n" + url)
    runCatching { context.startActivity(Intent.createChooser(send, null)) }.onFailure {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("yaari", url))
        Toast.makeText(context, context.getString(R.string.invite_copied), Toast.LENGTH_SHORT).show()
    }
}

/** Invite friends into the room I'm in (their Notifications), and share its link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteSheet(vm: AppViewModel, room: VoiceRoom, friends: List<Profile>, s: SocialState, onClose: () -> Unit) {
    val context = LocalContext.current
    val here = room.members.map { it.profile.id }.toSet()
    val list = friends.filter { it.id !in here }
    val sent = s.invited[room.id].orEmpty()
    val canLink = !room.rules.isPrivate || room.code != null
    ModalBottomSheet(
        onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Box(Modifier.padding(horizontal = 24.dp)) { SheetTitle(stringResource(R.string.invite_title), stringResource(R.string.invite_desc), onClose) }
            if (canLink) {
                SoftButton(
                    stringResource(R.string.invite_copy_link), { shareRoomLink(context, room) }, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    height = 48.dp, outlined = true,
                    leading = { Icon(Icons.Rounded.Link, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) },
                )
                if (room.rules.isPrivate) {
                    Text(
                        stringResource(R.string.invite_private_note), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (list.isEmpty()) {
                Text(
                    stringResource(R.string.invite_none), Modifier.fillMaxWidth().padding(24.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 8.dp)) {
                    items(list, key = { it.id }) { f ->
                        val done = f.id in sent
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(f, 40.dp)
                            Text(
                                f.name, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            SoftButton(
                                stringResource(if (done) R.string.invite_sent else R.string.invite_button), { vm.social.invite(room.id, f) },
                                accent = !done, enabled = !done && s.inviting == null, height = 36.dp,
                                leading = if (done) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface) }) else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Friends I can invite (accepted friends). */
fun UiState.acceptedFriends() = friends.filter { it.state == FriendState.FRIENDS }.map { it.profile }

// ---- the first-run tour ----

private val TourIcons: List<ImageVector> = listOf(Icons.Rounded.Shuffle, Icons.Outlined.Forum, Icons.Outlined.Group)
private val TourTitles = listOf(R.string.tour_1_title, R.string.tour_2_title, R.string.tour_3_title)
private val TourBodies = listOf(R.string.tour_1_body, R.string.tour_2_body, R.string.tour_3_body)

/** Three cards about the app, once; Skip ends it at any card. */
@Composable
fun TourDialog(vm: AppViewModel, card: Int) {
    val last = card >= SocialController.TOUR_CARDS - 1
    AlertDialog(
        onDismissRequest = vm.social::endTour,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        title = null,
        text = {
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.tour_step, card + 1, SocialController.TOUR_CARDS).uppercase(), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
                )
                AnimatedContent(card, transitionSpec = {
                    (slideInHorizontally(tween(180)) { it / 10 } + fadeIn(tween(180))) togetherWith (slideOutHorizontally(tween(180)) { -it / 10 } + fadeOut(tween(120)))
                }, label = "tour") { c ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.padding(top = 20.dp).size(80.dp).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Icon(TourIcons[c], null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                        Text(
                            stringResource(TourTitles[c]), Modifier.padding(top = 20.dp).semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(TourBodies[c]), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        )
                    }
                }
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(SocialController.TOUR_CARDS) { i ->
                        Box(
                            Modifier.height(6.dp).width(if (i == card) 20.dp else 6.dp).clip(CircleShape)
                                .background(if (i == card) Brand.Pink else MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton(stringResource(R.string.tour_skip), vm.social::endTour, Modifier.weight(1f), height = 52.dp, outlined = false)
                PrimaryButton(stringResource(if (last) R.string.tour_done else R.string.tour_next), vm.social::nextCard, Modifier.weight(1f), height = 52.dp)
            }
        },
    )
}

// ---- the notifications permission (Android 13+), asked after the first friend ----

@Composable
fun NotificationsPermission(ask: Boolean, done: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { done() }
    LaunchedEffect(ask) {
        if (!ask) return@LaunchedEffect
        val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needed) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else done()
    }
}
