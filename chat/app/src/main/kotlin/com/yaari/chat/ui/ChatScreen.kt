package com.yaari.chat.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yaari.chat.R
import com.yaari.chat.data.VideoStatus
import com.yaari.chat.domain.ExitPolicy
import com.yaari.chat.model.Media
import com.yaari.chat.model.MediaKind
import com.yaari.chat.model.Message
import com.yaari.chat.model.Profile
import kotlinx.coroutines.delay

@Composable
fun ChatRoomScreen(vm: AppViewModel, chat: ChatState, premium: Boolean) {
    SecureScreen()
    val partner = chat.match.partner
    var exitDialog by remember { mutableStateOf(false) }
    var reportDialog by remember { mutableStateOf(false) }
    var lockLeft by remember { mutableLongStateOf(vm.exitRemainingMs()) }
    LaunchedEffect(chat.startedAtMs, premium, chat.partnerLeft) {
        while (true) {
            lockLeft = vm.exitRemainingMs()
            if (lockLeft <= 0) break
            delay(minOf(lockLeft, 100L))
        }
    }
    BackHandler { if (lockLeft <= 0) exitDialog = true }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        ChatBar(
            partner,
            subtitle = if (chat.partnerTyping) stringResource(R.string.typing) else stringResource(R.string.chat_subtitle, partner.subtitle()),
            onProfile = { vm.openProfile(partner) },
            navigation = null,
        ) {
            val calling = chat.video == VideoStatus.REQUESTED_BY_ME
            IconButton(
                { vm.requestVideo() },
                enabled = !chat.partnerLeft && chat.video !in setOf(VideoStatus.REQUESTED_BY_ME, VideoStatus.REQUESTED_BY_PARTNER),
            ) {
                if (calling) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Videocam, stringResource(R.string.video), Modifier.size(26.dp))
            }
            IconButton({ reportDialog = true }) { Icon(Icons.Outlined.Flag, stringResource(R.string.report_and_leave), tint = MaterialTheme.colorScheme.error) }
            // Normal exit: locked for 5 s (free) / 0.2 s (Premium) after the match starts.
            ExitButton(lockLeft, ExitPolicy.lockMs(premium)) { exitDialog = true }
        }
        Messages(chat.messages, Modifier.weight(1f), hint = stringResource(R.string.say_hi_to, partner.name))
        if (chat.partnerLeft) {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.partner_left, partner.name), style = MaterialTheme.typography.titleSmall)
                GradientButton(stringResource(R.string.next_match), { vm.leave(next = true) })
                TextButton({ vm.leave(next = false) }) { Text(stringResource(R.string.back_to_lobby), color = MaterialTheme.colorScheme.onSurface) }
            }
        } else {
            Composer(onText = vm::sendText, onMedia = vm::sendMedia, onTyping = vm::typing)
        }
    }

    if (exitDialog) {
        AlertDialog(
            onDismissRequest = { exitDialog = false },
            title = { Text(stringResource(R.string.leave_title)) },
            text = { Text(stringResource(R.string.leave_text, partner.name)) },
            confirmButton = { TextButton({ exitDialog = false; vm.leave(next = true) }) { Text(stringResource(R.string.next_match), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ exitDialog = false; vm.leave(next = false) }) { Text(stringResource(R.string.back_to_lobby)) } },
        )
    }
    if (reportDialog) {
        ReportDialog(onDismiss = { reportDialog = false }, actions = listOf(stringResource(R.string.report_next), stringResource(R.string.report_lobby))) { reason, action ->
            reportDialog = false
            vm.reportAndLeave(reason, next = action == 0)
        }
    }
    if (chat.video == VideoStatus.REQUESTED_BY_PARTNER && !chat.partnerLeft) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Avatar(partner, 64.dp) },
            title = { Text(stringResource(R.string.video_incoming_title, partner.name), textAlign = TextAlign.Center) },
            text = { Text(stringResource(R.string.video_incoming_text), textAlign = TextAlign.Center) },
            confirmButton = { TextButton({ vm.answerVideo(true) }) { Text(stringResource(R.string.accept), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ vm.answerVideo(false) }) { Text(stringResource(R.string.decline)) } },
        )
    }
}

/** X that shows the seconds left while the exit lock runs, with a ring that empties. */
@Composable
private fun ExitButton(lockLeft: Long, lockMs: Long, onClick: () -> Unit) {
    IconButton(onClick, enabled = lockLeft <= 0) {
        if (lockLeft > 0) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { lockLeft.toFloat() / lockMs.coerceAtLeast(1) }, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
                Text("${(lockLeft + 999) / 1000}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Icon(Icons.Filled.Close, stringResource(R.string.leave), Modifier.size(26.dp))
        }
    }
}

/** Avatar, name with flag and a subtitle; tapping it opens the profile. */
@Composable
private fun ChatBar(partner: Profile, subtitle: String, onProfile: () -> Unit, navigation: (() -> Unit)?, actions: @Composable () -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = if (navigation == null) 12.dp else 0.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigation?.let { IconButton(it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }
            Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onProfile), verticalAlignment = Alignment.CenterVertically) {
                Avatar(partner, 40.dp)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(partner.nameWithFlag(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun DmScreen(vm: AppViewModel, dm: DmState) {
    val r = dm.recent
    SecureScreen()
    BackHandler { vm.closeDm() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        ChatBar(r.partner, r.partner.subtitle(), onProfile = { vm.openProfile(r.partner) }, navigation = vm::closeDm) {}
        Messages(dm.messages, Modifier.weight(1f), hint = stringResource(R.string.say_hi_to, r.partner.name))
        Composer(onText = vm::sendDm, onMedia = null, onTyping = vm::typing)
    }
}

@Composable
private fun Messages(messages: List<Message>, modifier: Modifier, hint: String) {
    val list = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex) }
    if (messages.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    LazyColumn(modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(messages, key = { it.id }) { m ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = if (m.fromMe) Alignment.CenterEnd else Alignment.CenterStart) {
                Bubble(m)
            }
        }
    }
}

/** Pill-shaped input with a photo button and a round gradient send button. */
@Composable
fun Composer(onText: (String) -> Unit, onMedia: ((Media) -> Unit)?, onTyping: () -> Unit, maxLength: Int = AppViewModel.MAX_TEXT) {
    var text by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && onMedia != null) {
            val video = context.contentResolver.getType(uri)?.startsWith("video") == true
            onMedia(Media(uri.toString(), if (video) MediaKind.VIDEO else MediaKind.IMAGE))
        }
    }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = if (onMedia != null) 4.dp else 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onMedia != null) {
                IconButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                    Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.send_media), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.weight(1f).padding(vertical = 13.dp)) {
                if (text.isEmpty()) Text(stringResource(R.string.message_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    text, { text = it.take(maxLength); if (it.isNotBlank()) onTyping() }, Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), maxLines = 5,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        val enabled = text.isNotBlank()
        Box(
            Modifier.size(46.dp).clip(CircleShape)
                .then(if (enabled) Modifier.background(Brand.gradient) else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
                .clickable(enabled = enabled) { onText(text); text = "" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send, stringResource(R.string.send), Modifier.size(20.dp),
                tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
