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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yaari.chat.data.Entitlements
import com.yaari.chat.model.Post
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.Profile

/** Someone else's profile, opened from a chat or from Recent. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(p: Profile, inChat: Boolean, onBack: () -> Unit, onMessage: (() -> Unit)?, onReport: (ReportReason) -> Unit) {
    SecureScreen()
    BackHandler(onBack = onBack)
    var reporting by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(p.name) },
                    navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    actions = { TextButton({ reporting = true }) { Text("Report", color = MaterialTheme.colorScheme.error) } },
                )
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                ProfileContent(p, isMe = false, footer = {
                    if (onMessage != null && !inChat) Button(onMessage, Modifier.fillMaxWidth().padding(16.dp)) { Text("Message") }
                })
            }
        }
    }
    if (reporting) ReportDialog(onDismiss = { reporting = false }, actions = listOf("Report & block")) { reason, _ ->
        reporting = false
        onReport(reason)
    }
}

/** Reason picker. [actions] are button labels; the chosen index is passed back. */
@Composable
fun ReportDialog(onDismiss: () -> Unit, actions: List<String>, onConfirm: (reason: ReportReason, action: Int) -> Unit) {
    var reason by remember { mutableStateOf(ReportReason.NUDITY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report this person") },
        text = {
            Column {
                ReportReason.entries.forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { reason = r }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(reason == r, { reason = r })
                        Text(r.label)
                    }
                }
                Text("They will be blocked and our team is alerted.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ onConfirm(reason, 0) }) { Text(actions[0], color = MaterialTheme.colorScheme.error) } },
        dismissButton = {
            if (actions.size > 1) TextButton({ onConfirm(reason, 1) }) { Text(actions[1], color = MaterialTheme.colorScheme.error) }
            else TextButton(onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun ProfileContent(
    p: Profile,
    isMe: Boolean,
    entitlements: Entitlements = Entitlements(),
    onUpgrade: () -> Unit = {},
    onNewPost: () -> Unit = {},
    onSettings: () -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    var open by remember { mutableStateOf<Post?>(null) }
    LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Avatar(p, 88.dp)
                    Column {
                        Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(p.subtitle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 4.dp))
                            Text("Phone verified", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (p.posts.size == 1) "1 post" else "${p.posts.size} posts", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (p.bio.isNotBlank()) Text(p.bio)
                if (isMe) {
                    OutlinedButton(onSettings, Modifier.fillMaxWidth()) { Text("Settings & safety") }
                    OutlinedButton(onNewPost, Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null)
                        Text("New post")
                    }
                }
            }
        }
        if (isMe) item(span = { GridItemSpan(maxLineSpan) }) { PremiumCard(entitlements, onUpgrade) }
        items(p.posts, key = { it.id }) { post -> PostTile(post, p.colorSeed) { open = post } }
        item(span = { GridItemSpan(maxLineSpan) }) { footer() }
    }
    open?.let { post ->
        AlertDialog(onDismissRequest = { open = null }, confirmButton = { TextButton({ open = null }) { Text("Close") } }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                post.imageUri?.let { AsyncImage(it, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
                if (post.text.isNotEmpty()) Text(post.text)
            }
        })
    }
}

@Composable
private fun PostTile(post: Post, seed: Int, onClick: () -> Unit) {
    Box(Modifier.aspectRatio(1f).background(seedColor(seed + post.id.hashCode())).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (post.imageUri != null) {
            AsyncImage(post.imageUri, post.text, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(post.text, color = Color.White, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Full-screen composer, like Instagram's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPostScreen(onCancel: () -> Unit, onPost: (String, String?) -> Unit) {
    BackHandler(onBack = onCancel)
    var text by rememberSaveable { mutableStateOf("") }
    var uri by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) uri = it.toString() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New post") },
                navigationIcon = { IconButton(onCancel) { Icon(Icons.Filled.Close, "Cancel") } },
                actions = { TextButton({ onPost(text, uri) }, enabled = text.isNotBlank() || uri != null) { Text("Share") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            uri?.let { AsyncImage(it, null, Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Crop) }
            OutlinedButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth()) {
                Text(if (uri == null) "Add photo" else "Change photo")
            }
            OutlinedTextField(text, { text = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Write something") }, minLines = 4)
        }
    }
}
