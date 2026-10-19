package com.yaari.chat.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yaari.chat.R
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Profile

@Composable
fun RecentTab(vm: AppViewModel, s: UiState, onSettings: () -> Unit) {
    // 0 = Friends, 1 = Recent (people met in random chat)
    var page by rememberSaveable { mutableIntStateOf(if (s.incomingRequests > 0 || s.recents.isEmpty()) 0 else 1) }
    Column(Modifier.fillMaxSize()) {
        TabHeader(stringResource(R.string.tab_recent), onSettings)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp)) {
            Segment(stringResource(R.string.friends), page == 0, Modifier.weight(1f)) { page = 0 }
            Segment(stringResource(R.string.recent_matches), page == 1, Modifier.weight(1f)) { page = 1 }
        }
        if (page == 0) FriendsList(vm, s) else RecentList(vm, s)
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(36.dp).clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun FriendsList(vm: AppViewModel, s: UiState) {
    val incoming = s.friends.filter { it.state == FriendState.INCOMING }
    val friends = s.friends.filter { it.state == FriendState.FRIENDS }
    val sent = s.friends.filter { it.state == FriendState.REQUESTED }
    if (s.friends.isEmpty()) {
        EmptyState(Icons.Outlined.People, stringResource(R.string.friends_empty_title), stringResource(R.string.friends_empty))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (incoming.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.friend_requests, incoming.size)) }
            items(incoming, key = { "in-" + it.profile.id }) { f ->
                PersonRow(f.profile, subtitle = f.profile.subtitle(), onOpen = { vm.openProfile(f.profile) }) {
                    SoftButton(stringResource(R.string.accept), { vm.answerFriend(f.profile, true) }, accent = true)
                    SoftButton(stringResource(R.string.decline), { vm.answerFriend(f.profile, false) })
                }
            }
        }
        if (friends.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.friends_count, friends.size)) }
            items(friends, key = { "f-" + it.profile.id }) { f ->
                PersonRow(f.profile, subtitle = f.profile.subtitle(), onOpen = { vm.openDm(f.profile.id) }, onAvatar = { vm.openProfile(f.profile) }) {
                    IconButton({ vm.openDm(f.profile.id) }) { Icon(Icons.AutoMirrored.Outlined.Chat, stringResource(R.string.message)) }
                }
            }
        }
        if (sent.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.requests_sent)) }
            items(sent, key = { "out-" + it.profile.id }) { f ->
                PersonRow(f.profile, subtitle = f.profile.subtitle(), onOpen = { vm.openProfile(f.profile) }) {
                    SoftButton(stringResource(R.string.cancel_request), { vm.removeFriend(f.profile) })
                }
            }
        }
    }
}

@Composable
private fun RecentList(vm: AppViewModel, s: UiState) {
    if (s.recents.isEmpty()) {
        EmptyState(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.recent_empty_title), stringResource(R.string.recent_empty))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(s.recents, key = { it.partner.id }) { r ->
            val state = s.friendState(r.partner.id)
            PersonRow(
                r.partner, subtitle = r.lastText ?: r.partner.subtitle(), time = r.metAt,
                onOpen = { vm.openDm(r.partner.id) }, onAvatar = { vm.openProfile(r.partner) },
            ) { FriendAction(state, onAdd = { vm.addFriend(r.partner) }) }
        }
    }
}

/** Compact add-friend control for list rows. */
@Composable
private fun FriendAction(state: FriendState, onAdd: () -> Unit) {
    when (state) {
        FriendState.FRIENDS -> Icon(Icons.Outlined.Check, stringResource(R.string.friends), Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        FriendState.REQUESTED -> Pill(stringResource(R.string.requested))
        FriendState.INCOMING -> SoftButton(stringResource(R.string.accept), onAdd, accent = true)
        FriendState.NONE -> IconButton(onAdd) { Icon(Icons.Outlined.PersonAddAlt, stringResource(R.string.add_friend), tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
private fun PersonRow(
    p: Profile, subtitle: String, onOpen: () -> Unit, onAvatar: () -> Unit = onOpen, time: Long? = null,
    actions: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(p, 54.dp, Modifier.clip(CircleShape).clickable(onClick = onAvatar))
        Column(Modifier.weight(1f)) {
            Text(p.nameWithFlag(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row {
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (time != null && time > 0) {
                    Text(
                        " · " + DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}
