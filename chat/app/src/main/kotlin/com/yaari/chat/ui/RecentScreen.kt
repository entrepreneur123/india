package com.yaari.chat.ui

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaari.chat.R
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Badges
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.widthIn
import com.yaari.chat.model.Profile
import com.yaari.chat.model.Stickers

@Composable
fun RecentTab(vm: AppViewModel, s: UiState, social: SocialState, onSettings: () -> Unit) {
    // 0 = Friends, 1 = Matches (people met in random chat), 2 = Sent (my friend requests still waiting)
    var page by rememberSaveable { mutableIntStateOf(if (s.incomingRequests > 0 || s.recents.isEmpty()) 0 else 1) }
    val b = social.badges
    val unreadFriends = s.friends.count { it.state == FriendState.FRIENDS && b.unread(it.threadId) > 0 }
    val unreadMatches = s.recents.count { b.unread(it.matchId) > 0 }
    Column(Modifier.fillMaxSize()) {
        var finding by rememberSaveable { mutableStateOf(false) }
        TabHeader(stringResource(R.string.tab_recent), onSettings) {
            CircleIconButton(Icons.Outlined.PersonSearch, stringResource(R.string.find_by_id), { finding = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            BellButton(b.inbox, vm.social::openInbox)
        }
        if (finding) FindSheet(vm) { finding = false }
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
        ) {
            // A white thumb slides under the chosen segment.
            val third = maxWidth / 3
            val x by animateDpAsState(third * page, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "seg")
            Box(
                Modifier.offset { IntOffset(x.roundToPx(), 0) }.width(third).height(40.dp)
                    .clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Row {
                Segment(stringResource(R.string.friends), page == 0, Modifier.weight(1f), count = s.incomingRequests + unreadFriends) { page = 0 }
                Segment(stringResource(R.string.recent_matches), page == 1, Modifier.weight(1f), count = unreadMatches) { page = 1 }
                Segment(stringResource(R.string.sent_tab), page == 2, Modifier.weight(1f)) { page = 2 }
            }
        }
        AnimatedContent(page, transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInHorizontally { it / 6 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 6 * dir } + fadeOut())
        }, label = "page") { p ->
            when (p) {
                0 -> FriendsList(vm, s, b)
                1 -> RecentList(vm, s, b)
                else -> SentList(vm, s)
            }
        }
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, modifier: Modifier, count: Int = 0, onClick: () -> Unit) {
    Row(
        modifier.height(40.dp).clip(CircleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CountBadge(count, Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun FriendsList(vm: AppViewModel, s: UiState, b: Badges) {
    val incoming = s.friends.filter { it.state == FriendState.INCOMING }
    val friends = s.friends.filter { it.state == FriendState.FRIENDS }
    if (incoming.isEmpty() && friends.isEmpty()) {
        EmptyState(Icons.Outlined.People, stringResource(R.string.friends_empty_title), stringResource(R.string.friends_empty))
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (incoming.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.friend_requests, incoming.size)) }
            items(incoming, key = { "in-" + it.profile.id }) { f ->
                RequestCard(f.profile, Modifier.animateItem(), onOpen = { vm.openProfile(f.profile) }, onAnswer = { vm.answerFriend(f.profile, it) })
            }
        }
        if (friends.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.friends_count, friends.size)) }
            items(friends, key = { "f-" + it.profile.id }) { f ->
                PersonRow(
                    modifier = Modifier.animateItem(), p = f.profile, subtitle = f.profile.genderCity(), unread = b.unread(f.threadId),
                    onOpen = { vm.openDm(f.profile.id) }, onAvatar = { vm.openProfile(f.profile) },
                ) {
                    CircleIconButton(Icons.AutoMirrored.Outlined.Chat, stringResource(R.string.message), { vm.openDm(f.profile.id) }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    PersonMenu(f.profile, onBlock = { vm.block(f.profile) }, onReport = { vm.report(f.profile.id, it) })
                }
            }
        }
    }
}

/** Friend requests I sent that wait for an answer (friend_list: requested); cancelling withdraws one (remove_friend). */
@Composable
private fun SentList(vm: AppViewModel, s: UiState) {
    val sent = s.friends.filter { it.state == FriendState.REQUESTED }
    if (sent.isEmpty()) {
        EmptyState(Icons.Outlined.PersonAddAlt, stringResource(R.string.sent_empty_title), stringResource(R.string.sent_empty))
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        items(sent, key = { it.profile.id }) { f ->
            PersonRow(modifier = Modifier.animateItem(), p = f.profile, subtitle = f.profile.genderCity(), onOpen = { vm.openProfile(f.profile) }) {
                SoftButton(stringResource(R.string.cancel_request), { vm.removeFriend(f.profile) }, height = 36.dp, shape = CircleShape)
            }
        }
    }
}

/** A friend request on a soft grey card: Decline (outlined) and Accept (filled brand colour). */
@Composable
private fun RequestCard(p: Profile, modifier: Modifier, onOpen: () -> Unit, onAnswer: (Boolean) -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).clickable(role = Role.Button, onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Avatar(p, 48.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.nameAgeFlag(), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                VerifiedMark(p, Modifier.padding(start = 4.dp), size = 16.dp)
            }
            SecondLine(p, p.genderCity(), MaterialTheme.typography.bodySmall)
        }
        SoftButton(stringResource(R.string.decline), { onAnswer(false) }, height = 36.dp, shape = CircleShape, outlined = true)
        SoftButton(stringResource(R.string.accept), { onAnswer(true) }, height = 36.dp, shape = CircleShape, accent = true)
    }
}

@Composable
private fun RecentList(vm: AppViewModel, s: UiState, b: Badges) {
    if (s.recents.isEmpty()) {
        EmptyState(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.recent_empty_title), stringResource(R.string.recent_empty))
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        items(s.recents, key = { it.partner.id }) { r ->
            val state = s.friendState(r.partner.id)
            PersonRow(
                modifier = Modifier.animateItem(), p = r.partner, subtitle = r.lastText?.let { Stickers.preview(it, stringResource(R.string.chat_sticker)) } ?: r.partner.genderCity(), time = r.metAt,
                unread = b.unread(r.matchId),
                onOpen = { vm.openDm(r.partner.id) }, onAvatar = { vm.openProfile(r.partner) },
            ) {
                FriendAction(state, onAdd = { vm.addFriend(r.partner) })
                PersonMenu(r.partner, onBlock = { vm.block(r.partner) }, onReport = { vm.report(r.partner.id, it) })
            }
        }
    }
}

/** Compact add-friend control for list rows. */
@Composable
private fun FriendAction(state: FriendState, onAdd: () -> Unit) {
    when (state) {
        FriendState.FRIENDS -> Icon(Icons.Rounded.Check, stringResource(R.string.friends), Modifier.padding(12.dp), tint = Brand.Online)
        FriendState.REQUESTED -> Pill(stringResource(R.string.requested))
        FriendState.INCOMING -> SoftButton(stringResource(R.string.accept), onAdd, accent = true, height = 36.dp, shape = CircleShape)
        FriendState.NONE -> CircleIconButton(
            Icons.Outlined.PersonAddAlt, stringResource(R.string.add_friend), onAdd,
            background = MaterialTheme.colorScheme.primaryContainer, tint = MaterialTheme.colorScheme.onPrimaryContainer, iconSize = 20.dp,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primaryText,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun PersonRow(
    p: Profile, subtitle: String, onOpen: () -> Unit, onAvatar: () -> Unit = onOpen, time: Long? = null, modifier: Modifier = Modifier,
    unread: Int = 0, actions: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier.fillMaxWidth().clickable(interaction, indication = ripple(), role = Role.Button, onClick = onOpen)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(p, 48.dp, Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onAvatar))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p.nameWithFlag(), style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                VerifiedMark(p, Modifier.padding(start = 4.dp), size = 16.dp)
                if (time != null && time > 0) {
                    Text(
                        "  " + DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
            }
            SecondLine(p, subtitle, MaterialTheme.typography.bodyMedium)
        }
        if (unread > 0) {
            val label = stringResource(R.string.unread_count, unread)
            CountBadge(unread, Modifier.semantics { contentDescription = label })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** A person's second line in the lists: their decorated @ID (once known), then [text] (the last message, or details). */
@Composable
private fun SecondLine(p: Profile, text: String, style: TextStyle) {
    val look = lookOf(p.id)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (look != null && look.handle.isNotEmpty()) {
            LookName(look, Modifier.widthIn(max = 168.dp), size = style.fontSize)
            Text(" · ", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text, Modifier.weight(1f, fill = false), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
