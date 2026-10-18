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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yaari.chat.model.ChatSession
import com.yaari.chat.model.Media
import com.yaari.chat.model.MediaKind
import com.yaari.chat.model.Message
import com.yaari.chat.model.VideoState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(vm: AppViewModel, session: ChatSession, premium: Boolean) {
    SecureScreen()
    var exitDialog by remember { mutableStateOf(false) }
    var reportDialog by remember { mutableStateOf(false) }
    var lockLeft by remember { mutableLongStateOf(vm.exitRemainingMs()) }
    LaunchedEffect(session.startedAtMs, premium, session.partnerLeft) {
        while (true) {
            lockLeft = vm.exitRemainingMs()
            if (lockLeft <= 0) break
            delay(minOf(lockLeft, 100L))
        }
    }
    BackHandler { if (lockLeft <= 0) exitDialog = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(Modifier.clickable { vm.openProfile(session.partner) }, verticalAlignment = Alignment.CenterVertically) {
                        Avatar(session.partner, 40.dp)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(session.partner.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (session.partnerTyping) "typing…" else session.partner.subtitle() + " · View profile",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            )
                        }
                    }
                },
                actions = {
                    TextButton({ vm.requestVideo() }, enabled = !session.partnerLeft && session.video == VideoState.NONE) {
                        Text(if (session.video == VideoState.REQUESTING) "Calling…" else "Video")
                    }
                    IconButton({ reportDialog = true }) { Icon(Icons.Filled.Warning, "Report and leave", tint = MaterialTheme.colorScheme.error) }
                    // Normal exit: locked for 5 s (free) / 0.2 s (Premium) after the match starts.
                    IconButton({ exitDialog = true }, enabled = lockLeft <= 0) {
                        if (lockLeft > 0) Text("${(lockLeft + 999) / 1000}", fontWeight = FontWeight.Bold)
                        else Icon(Icons.Filled.Close, "Leave")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            Messages(session.messages, Modifier.weight(1f))
            if (session.partnerLeft) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${session.partner.name} left the chat")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton({ vm.leave(next = false) }) { Text("Back to lobby") }
                        Button({ vm.leave(next = true) }) { Text("Next match") }
                    }
                }
            } else {
                Composer(onText = vm::sendText, onMedia = vm::sendMedia)
            }
        }
    }

    if (exitDialog) {
        AlertDialog(
            onDismissRequest = { exitDialog = false },
            title = { Text("Leave this chat?") },
            text = { Text("${session.partner.name} will be saved in Recent.") },
            confirmButton = { TextButton({ exitDialog = false; vm.leave(next = true) }) { Text("Next match") } },
            dismissButton = { TextButton({ exitDialog = false; vm.leave(next = false) }) { Text("Back to lobby") } },
        )
    }
    if (reportDialog) {
        ReportDialog(onDismiss = { reportDialog = false }, actions = listOf("Report & next", "Report & lobby")) { reason, action ->
            reportDialog = false
            vm.reportAndLeave(reason, next = action == 0)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmScreen(vm: AppViewModel, s: UiState) {
    val r = s.recents.firstOrNull { it.partner.id == s.dmPartnerId } ?: return
    SecureScreen()
    BackHandler { vm.closeDm() }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(vm::closeDm) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Row(Modifier.clickable { vm.openProfile(r.partner) }, verticalAlignment = Alignment.CenterVertically) {
                        Avatar(r.partner, 36.dp)
                        Text(r.partner.name, Modifier.padding(start = 10.dp))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            Messages(r.dm, Modifier.weight(1f))
            Composer(onText = vm::sendDm, onMedia = null)
        }
    }
}

@Composable
private fun Messages(messages: List<Message>, modifier: Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex) }
    if (messages.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Say hi 👋", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier.fillMaxSize(), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(messages, key = { it.id }) { m ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = if (m.fromMe) Alignment.CenterEnd else Alignment.CenterStart) {
                Bubble(m)
            }
        }
    }
}

@Composable
private fun Composer(onText: (String) -> Unit, onMedia: ((Media) -> Unit)?) {
    var text by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && onMedia != null) {
            val video = context.contentResolver.getType(uri)?.startsWith("video") == true
            onMedia(Media(uri.toString(), if (video) MediaKind.VIDEO else MediaKind.IMAGE))
        }
    }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onMedia != null) {
            IconButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                Icon(Icons.Filled.Add, "Send photo or video")
            }
        }
        OutlinedTextField(text, { text = it.take(1000) }, Modifier.weight(1f), placeholder = { Text("Message") }, maxLines = 4)
        IconButton({ onText(text); text = "" }, enabled = text.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
    }
}
