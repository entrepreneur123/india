package com.yaari.chat.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yaari.chat.R
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.flagOf
import com.yaari.chat.model.Comment
import com.yaari.chat.model.FeedTab
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Post
import com.yaari.chat.model.PostVisibility
import com.yaari.chat.model.Profile
import kotlinx.coroutines.launch

// ---- The feed: Friends and Discover, post cards with hearts and comments, one post full screen, the comments sheet --
// Cards are big and rounded (Instagram's rhythm, ShareChat's colour): short text-only posts sit on their author's
// accent gradient, photos from people who aren't my friends stay behind "Tap to view" (the chat's rule for others'
// photos), and every mark is Yaari's own drawing.

/** "4 min. ago" */
fun timeAgo(at: Long): String =
    DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

/** The Feed tab: a header with New post and the bell, Friends / Discover, then the cards. */
@Composable
fun FeedScreen(vm: AppViewModel, s: UiState, social: SocialState, onSettings: () -> Unit, onNewPost: () -> Unit) {
    val feed by vm.feed.state.collectAsStateWithLifecycle()
    val me = s.me ?: return
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_feed), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
            NewPostButton(onNewPost)
            BellButton(social.badges.inbox, vm.social::openInbox)
            CircleIconButton(Icons.Outlined.Settings, stringResource(R.string.settings), onSettings, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PillTabs(
            listOf(stringResource(R.string.friends), stringResource(R.string.feed_discover)), FeedTab.entries.indexOf(feed.tab),
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) { vm.feed.select(FeedTab.entries[it]) }
        val page = feed.page()
        val posts = page.posts
        PullToRefreshBox(page.loading && posts != null, onRefresh = { vm.feed.refresh() }, Modifier.fillMaxSize()) {
            when {
                posts == null && page.failed -> FeedMessage(
                    Mark.FEED, stringResource(R.string.feed_load_failed), stringResource(R.string.err_network),
                    stringResource(R.string.common_try_again), { vm.feed.refresh() },
                )
                posts == null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { repeat(3) { SkeletonCard() } }
                posts.isEmpty() -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ComposerCard(me, onNewPost)
                    if (feed.tab == FeedTab.FRIENDS) {
                        FeedMessage(
                            Mark.FRIENDS, stringResource(R.string.feed_empty_friends_title), stringResource(R.string.feed_empty_friends),
                            stringResource(R.string.feed_go_discover), { vm.feed.select(FeedTab.DISCOVER) },
                        )
                    } else {
                        FeedMessage(
                            Mark.GLOBE, stringResource(R.string.feed_empty_discover_title), stringResource(R.string.feed_empty_discover),
                            stringResource(R.string.new_post), onNewPost,
                        )
                    }
                }
                else -> {
                    val list = rememberLazyListState()
                    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 4 } == true } }
                    LaunchedEffect(nearEnd, posts.size) { if (nearEnd) vm.feed.loadMore() }
                    LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(bottom = 24.dp)) {
                        item(key = "compose") { ComposerCard(me, onNewPost) }
                        items(posts, key = { it.id }) { post -> PostCard(vm, s, post, Modifier.animateItem()) }
                        item(key = "foot") {
                            Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                                when {
                                    page.loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Brand.Pink)
                                    page.failed -> TextButton({ vm.feed.loadMore() }) { Text(stringResource(R.string.common_try_again)) }
                                    page.end -> Text(stringResource(R.string.feed_end), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The brand "+" disc in headers: write a new post. */
@Composable
fun NewPostButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).pressScale(interaction, 0.92f).clip(CircleShape).background(Brand.logoGradient), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, stringResource(R.string.new_post), Modifier.size(22.dp), tint = Color.White)
        }
    }
}

/** Two or more segments on a grey track, a white thumb sliding under the chosen one (as Recent's). */
@Composable
fun PillTabs(labels: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp)) {
        val w = maxWidth / labels.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "seg")
        Box(
            Modifier.offset { IntOffset(x.roundToPx(), 0) }.width(w).height(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Row {
            labels.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).height(40.dp).clip(CircleShape)
                        .selectable(i == selected, remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                        color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** "What's on your mind?" with my avatar: opens the composer. */
@Composable
private fun ComposerCard(me: Profile, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).clickable(role = Role.Button, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentAvatar(me, 44.dp, gap = MaterialTheme.colorScheme.surfaceContainer)
        Text(
            stringResource(R.string.feed_compose), Modifier.weight(1f).padding(horizontal = 12.dp),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            MarkIcon(Mark.IMAGE, Color(0xFF22B8A6), size = 22.dp)
        }
    }
}

/** An empty or failed list: a tinted mark, the words and one action. */
@Composable
private fun FeedMessage(mark: Mark, title: String, body: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            MarkIcon(mark, Brand.Pink, size = 34.dp)
        }
        Text(title, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        SoftButton(action, onAction, Modifier.padding(top = 20.dp), accent = true)
    }
}

@Composable
private fun SkeletonCard() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).shimmer())
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(140.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                Box(Modifier.width(80.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            }
        }
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(20.dp)).shimmer())
    }
}

// ---- a post -----------------------------------------------------------------------------------------------------

/**
 * A post as a card: author row (accent ring, name, age, flag, time, who sees it), the text, the photo (double tap
 * likes it), hearts and comments, and the overflow (delete mine, report others').
 */
@Composable
fun PostCard(vm: AppViewModel, s: UiState, post: Post, modifier: Modifier = Modifier, flat: Boolean = false) {
    val me = s.me
    val author = post.author ?: me ?: return
    val mine = author.id == me?.id
    val trusted = mine || s.friendState(author.id) == FriendState.FRIENDS
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier.fillMaxWidth().padding(horizontal = if (flat) 0.dp else 12.dp, vertical = 8.dp)
            .then(if (flat) Modifier else Modifier.clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val open = { if (!mine) vm.openProfile(author) }
            AccentAvatar(author, 44.dp, Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = open), gap = MaterialTheme.colorScheme.surface)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp).clickable(role = Role.Button, onClick = open)) {
                Text(
                    listOfNotNull(author.name + if (author.age > 0) ", ${author.age}" else "", flagOf(author.country).ifEmpty { null }).joinToString(" "),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(timeAgo(post.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Text(" · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val vis = stringResource(if (post.visibility == PostVisibility.PUBLIC) R.string.visibility_public else R.string.visibility_friends)
                    MarkIcon(
                        if (post.visibility == PostVisibility.PUBLIC) Mark.GLOBE else Mark.FRIENDS, MaterialTheme.colorScheme.onSurfaceVariant,
                        Modifier.semantics { contentDescription = vis }, filled = post.visibility != PostVisibility.PUBLIC, size = 14.dp,
                    )
                }
            }
            PostMenu(vm, post, mine)
        }
        val text = post.text.trim()
        val tile = post.imageUri == null && text.length in 1..TILE_MAX
        when {
            tile -> TextTile(text, author, onDoubleTap = { vm.feed.like(post) })
            text.isNotEmpty() -> Text(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), style = MaterialTheme.typography.bodyLarge)
        }
        post.imageUri?.let { uri -> PostPhoto(post, uri, trusted, onDoubleTap = { vm.feed.like(post) }) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            HeartButton(post.liked, post.likes) { vm.feed.toggleLike(post) }
            val commentsLabel = pluralStringResource(R.plurals.post_comments, post.comments, post.comments)
            Row(
                Modifier.height(48.dp).clip(CircleShape).clickable(role = Role.Button) { vm.feed.openComments(post) }
                    .semantics(mergeDescendants = true) { contentDescription = commentsLabel }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarkIcon(Mark.COMMENT, MaterialTheme.colorScheme.onSurface, filled = false, size = 24.dp)
                if (post.comments > 0) Text("${post.comments}", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (post.comments > 0) {
            Text(
                pluralStringResource(R.plurals.post_view_comments, post.comments, post.comments),
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp).clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button) { vm.feed.openComments(post) },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** Short text-only posts: big white words on the author's gradient. */
private const val TILE_MAX = 140

@Composable
private fun TextTile(text: String, author: Profile, onDoubleTap: () -> Unit) {
    val colors = accentColors(author.style.accent)
    val pop = rememberHeartPop()
    Box(
        Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp).fillMaxWidth().heightIn(min = 180.dp).clip(RoundedCornerShape(22.dp)).background(colors.brush)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap(); pop.play() }) }
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = Color.White, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = if (text.length < 60) 26.sp else 21.sp, lineHeight = if (text.length < 60) 34.sp else 29.sp, fontWeight = FontWeight.Bold),
        )
        pop.Overlay()
    }
}

/** A post's photo: others' (not my friends') wait behind "Tap to view"; a double tap likes it with a big heart. */
@Composable
private fun PostPhoto(post: Post, uri: String, trusted: Boolean, onDoubleTap: () -> Unit) {
    var revealed by rememberSaveable(post.id) { mutableStateOf(trusted) }
    val pop = rememberHeartPop()
    val shown = revealed || trusted
    val tint = accentColors(post.author?.style?.accent)
    Box(
        // Hidden, it is a short tinted panel (nothing of the photo is loaded); shown, a tall 4:5 frame.
        Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp).fillMaxWidth().aspectRatio(if (shown) 0.8f else 1.6f).clip(RoundedCornerShape(22.dp))
            .background(if (shown) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xFF2E2E34))
            .then(if (shown) Modifier else Modifier.background(Brush.linearGradient(listOf(tint.start.copy(alpha = 0.45f), tint.end.copy(alpha = 0.25f))))),
        contentAlignment = Alignment.Center,
    ) {
        if (shown) {
            PostImage(uri, post.text.ifEmpty { null }, Modifier.fillMaxSize().pointerInput(post.id) { detectTapGestures(onDoubleTap = { onDoubleTap(); pop.play() }) })
            pop.Overlay()
        } else {
            // Nothing of the photo is loaded until it is tapped.
            Column(
                Modifier.fillMaxSize().clickable(role = Role.Button) { revealed = true },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Visibility, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Text(stringResource(R.string.tap_to_view), Modifier.padding(top = 10.dp), color = Color.White, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.photo), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A photo from the server, a file just picked, or one of the demo's drawn pictures. */
@Composable
fun PostImage(uri: String, description: String?, modifier: Modifier = Modifier) {
    if (uri.startsWith("demo://")) DemoArt(uri, modifier.semantics { if (description != null) contentDescription = description })
    else AsyncImage(uri, description, modifier, contentScale = ContentScale.Crop)
}

/** The big heart that pops over a photo on a double tap. */
private class HeartPop(val scale: Animatable<Float, *>, val play: () -> Unit) {
    @Composable
    fun Overlay() {
        val v = scale.value
        if (v <= 0f) return
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.HEART, Modifier.graphicsLayer { scaleX = v; scaleY = v; alpha = (v.coerceAtMost(1f)) }, size = 96.dp)
        }
    }
}

@Composable
private fun rememberHeartPop(): HeartPop {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(0f) }
    return remember {
        HeartPop(scale) {
            scope.launch {
                scale.snapTo(0.4f)
                scale.animateTo(1.15f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
                scale.animateTo(1f, tween(120))
                kotlinx.coroutines.delay(350)
                scale.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
            }
        }
    }
}

/** The heart: an outline, or the red two-tone heart that pops when it turns on; the count beside it. */
@Composable
fun HeartButton(liked: Boolean, likes: Int, onToggle: () -> Unit) {
    val scale = remember { Animatable(1f) }
    var last by remember { mutableStateOf(liked) }
    LaunchedEffect(liked) {
        if (liked && !last) {
            scale.snapTo(0.6f)
            scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
        last = liked
    }
    val label = stringResource(R.string.post_like)
    val count = pluralStringResource(R.plurals.post_likes, likes, likes)
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.height(48.dp).clip(CircleShape)
            .selectable(liked, interaction, indication = null, role = Role.Checkbox, onClick = onToggle)
            .semantics(mergeDescendants = true) { contentDescription = label; stateDescription = count }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }.pressScale(interaction, 0.85f)) {
            if (liked) GlyphIcon(Glyph.HEART, size = 26.dp) else MarkIcon(Mark.HEART, MaterialTheme.colorScheme.onSurface, filled = false, size = 26.dp)
        }
        if (likes > 0) Box(Modifier.padding(start = 6.dp)) { RollingNumber(likes, MaterialTheme.typography.labelLarge) }
    }
}

/** The post's overflow: delete (mine, confirmed first) or report (others'). */
@Composable
private fun PostMenu(vm: AppViewModel, post: Post, mine: Boolean) {
    var open by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    Box {
        CircleIconButton(Icons.Rounded.MoreVert, stringResource(R.string.more), { open = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        DropdownMenu(open, { open = false }) {
            if (mine) DropdownMenuItem({ Text(stringResource(R.string.post_delete), color = MaterialTheme.colorScheme.error) }, { open = false; deleting = true })
            else DropdownMenuItem({ Text(stringResource(R.string.post_report), color = MaterialTheme.colorScheme.error) }, { open = false; reporting = true })
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.post_delete_title)) },
            text = { Text(stringResource(R.string.post_delete_text)) },
            confirmButton = { TextButton({ deleting = false; vm.feed.delete(post) }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (reporting) {
        ReportDialog(onDismiss = { reporting = false }, actions = listOf(stringResource(R.string.post_report))) { reason, _ ->
            reporting = false
            vm.feed.report(post, reason)
        }
    }
}

// ---- one post full screen, and the comments sheet ------------------------------------------------------------------

/** The open post (from a profile's grid or Notifications) and the comments sheet, over the screens. */
@Composable
fun FeedLayer(vm: AppViewModel, s: UiState) {
    val feed by vm.feed.state.collectAsStateWithLifecycle()
    var last by remember { mutableStateOf<Post?>(null) }
    feed.detail?.let { last = it }
    AnimatedVisibility(
        feed.detail != null,
        enter = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(260)) { it / 3 } + fadeOut(tween(200)),
    ) { last?.let { p -> PostDetailScreen(vm, s, feed.detail ?: p) } }
    feed.thread?.let { CommentsSheet(vm, s, it) }
}

@Composable
private fun PostDetailScreen(vm: AppViewModel, s: UiState, post: Post) {
    BackHandler(onBack = vm.feed::closeDetail)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), vm.feed::closeDetail)
                Text(stringResource(R.string.post_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                PostCard(vm, s, post)
                if (post.comments == 0) {
                    TextButton({ vm.feed.openComments(post) }, Modifier.padding(horizontal = 12.dp)) { Text(stringResource(R.string.comments_add)) }
                }
            }
        }
    }
}

/** Comments, oldest first, with replies; my comment at the bottom (Instagram's sheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(vm: AppViewModel, s: UiState, t: CommentThread) {
    val me = s.me ?: return
    ModalBottomSheet(
        onDismissRequest = vm.feed::closeComments, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.comments_title), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                CircleIconButton(Icons.Rounded.Close, stringResource(R.string.close), vm.feed::closeComments)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 4.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val list = t.comments
                when {
                    list == null && t.failed -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.comments_load_failed), Modifier.padding(top = 32.dp), style = MaterialTheme.typography.titleSmall)
                        TextButton({ vm.feed.loadComments() }) { Text(stringResource(R.string.common_try_again)) }
                    }
                    list == null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        repeat(3) {
                            Row {
                                Box(Modifier.size(36.dp).clip(CircleShape).shimmer())
                                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.width(100.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                                    Box(Modifier.width(200.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                                }
                            }
                        }
                    }
                    list.isEmpty() -> Column(Modifier.fillMaxWidth().padding(top = 40.dp, start = 32.dp, end = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            MarkIcon(Mark.COMMENT, Brand.Pink, size = 30.dp)
                        }
                        Text(stringResource(R.string.comments_empty_title), Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.comments_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(list, key = { it.id }) { c -> CommentRow(vm, c, Modifier.animateItem()) }
                        if (t.more) item(key = "more") {
                            TextButton({ vm.feed.loadComments(more = true) }, Modifier.padding(start = 12.dp)) { Text(stringResource(R.string.comments_more)) }
                        }
                    }
                }
            }
            t.replyTo?.let { r ->
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 20.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.comment_replying, r.author.name), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    CircleIconButton(Icons.Rounded.Close, stringResource(R.string.comment_reply_cancel), { vm.feed.replyTo(null) }, iconSize = 18.dp)
                }
            }
            CommentComposer(me, t, onSend = { text, done -> vm.feed.send(text, done) })
        }
    }
}

@Composable
private fun CommentRow(vm: AppViewModel, c: Comment, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    val reply = c.replyTo != null
    val openAuthor = { if (!c.mine) { vm.feed.closeComments(); vm.openProfile(c.author) } }
    Row(modifier.fillMaxWidth().padding(start = if (reply) 60.dp else 16.dp, end = 4.dp, top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.Top) {
        AccentAvatar(c.author, if (reply) 30.dp else 38.dp, Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = openAuthor), gap = MaterialTheme.colorScheme.surface, ring = 1.5.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.author.name, Modifier.weight(1f, fill = false).clickable(role = Role.Button, onClick = openAuthor),
                    style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("  " + timeAgo(c.at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            c.replyName?.let {
                Text(stringResource(R.string.comment_in_reply, it), style = MaterialTheme.typography.bodySmall, color = accentColors(c.author.style.accent).ink)
            }
            Text(c.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallAction(stringResource(R.string.comment_reply)) { vm.feed.replyTo(c) }
                if (c.canRemove) SmallAction(stringResource(R.string.comment_remove)) { vm.feed.remove(c) }
            }
        }
        if (!c.mine) {
            Box {
                CircleIconButton(Icons.Rounded.MoreVert, stringResource(R.string.more), { menu = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant, iconSize = 18.dp)
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.comment_report), color = MaterialTheme.colorScheme.error) }, { menu = false; reporting = true })
                }
            }
        }
    }
    if (reporting) {
        ReportDialog(onDismiss = { reporting = false }, actions = listOf(stringResource(R.string.comment_report))) { reason, _ ->
            reporting = false
            vm.feed.report(c, reason)
        }
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    Text(
        text, Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
    )
}

/** My avatar, a rounded field and the send mark. */
@Composable
private fun CommentComposer(me: Profile, t: CommentThread, onSend: (String, () -> Unit) -> Unit) {
    var text by rememberSaveable(t.post.id) { mutableStateOf("") }
    val can = text.isNotBlank() && !t.sending
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentAvatar(me, 36.dp, gap = MaterialTheme.colorScheme.surface, ring = 1.5.dp)
        val hint = t.replyTo?.let { stringResource(R.string.comment_hint_reply, it.author.name) } ?: stringResource(R.string.comment_hint)
        Box(
            Modifier.weight(1f).padding(horizontal = 10.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 11.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                text, { text = it.take(Comment.MAX) }, Modifier.fillMaxWidth().semantics { contentDescription = hint },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 4,
                cursorBrush = SolidColor(Brand.Pink), keyboardOptions = KeyboardOptions.Default,
            )
        }
        val interaction = remember { MutableInteractionSource() }
        val sendLabel = stringResource(R.string.send)
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .clickable(interaction, indication = null, enabled = can, role = Role.Button) { onSend(text) { text = "" } }
                .semantics { contentDescription = sendLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(40.dp).pressScale(interaction, 0.9f).clip(CircleShape)
                    .background(if (can) Brand.Pink else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                MarkIcon(Mark.SEND, if (can) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp)
            }
        }
    }
}

// ---- the composer's audience ---------------------------------------------------------------------------------------

/** Friends / Everyone as two cards: the drawn mark, the name and who that is. */
@Composable
fun VisibilityChooser(value: PostVisibility, onChange: (PostVisibility) -> Unit) {
    Column {
        Text(stringResource(R.string.visibility_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Triple(PostVisibility.FRIENDS, R.string.visibility_friends, R.string.visibility_friends_sub),
                Triple(PostVisibility.PUBLIC, R.string.visibility_public, R.string.visibility_public_sub),
            ).forEach { (v, title, sub) ->
                val on = v == value
                val shape = RoundedCornerShape(20.dp)
                Column(
                    Modifier.weight(1f).clip(shape)
                        .then(if (on) Modifier.background(Brand.Pink.copy(alpha = 0.07f)).border(2.dp, Brand.Pink, shape) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape))
                        .selectable(on, role = Role.RadioButton) { onChange(v) }.padding(14.dp),
                ) {
                    MarkIcon(if (v == PostVisibility.PUBLIC) Mark.GLOBE else Mark.FRIENDS, if (on) Brand.Pink else MaterialTheme.colorScheme.onSurfaceVariant, size = 26.dp)
                    Text(stringResource(title), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---- the demo's drawn pictures (no network in the demo) ------------------------------------------------------------

/** A drawn scene for the demo's posts and covers ([FakeBackend.ART_SUNSET] and friends); unknown keys get the sunset. */
@Composable
fun DemoArt(key: String, modifier: Modifier = Modifier) {
    when (key) {
        FakeBackend.ART_CITY -> Canvas(modifier) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2B2E83), Color(0xFF8A4FD8), Color(0xFFFF8BA7))))
            drawCircle(Color(0xFFFFE3A3), radius = w * 0.08f, center = Offset(w * 0.75f, h * 0.28f))
            val blocks = listOf(0.00f to 0.55f, 0.12f to 0.42f, 0.26f to 0.6f, 0.38f to 0.35f, 0.52f to 0.5f, 0.66f to 0.4f, 0.8f to 0.58f, 0.9f to 0.47f)
            blocks.forEach { (x, top) ->
                drawRect(Color(0xFF1B1D4F), Offset(w * x, h * top), androidx.compose.ui.geometry.Size(w * 0.13f, h * (1 - top)))
                for (r in 0 until 6) for (c in 0 until 2) {
                    if ((r + c + (x * 10).toInt()) % 3 == 0) drawRect(
                        Color(0xFFFFD27A), Offset(w * (x + 0.025f + c * 0.05f), h * (top + 0.05f + r * 0.07f)), androidx.compose.ui.geometry.Size(w * 0.025f, h * 0.03f),
                    )
                }
            }
        }
        FakeBackend.ART_HILLS -> Canvas(modifier) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFB75E), Color(0xFFED8F03).copy(alpha = 0.9f), Color(0xFFFFE0B2))))
            drawCircle(Color(0xFFFFF4D6), radius = w * 0.1f, center = Offset(w * 0.3f, h * 0.36f))
            fun hill(y: Float, peak: Float, color: Color) = drawPath(
                Path().apply {
                    moveTo(0f, h * y)
                    quadraticTo(w * peak, h * (y - 0.28f), w, h * (y + 0.04f))
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                },
                color,
            )
            hill(0.62f, 0.62f, Color(0xFFC0582B))
            hill(0.74f, 0.3f, Color(0xFF8E3B1F))
            hill(0.86f, 0.7f, Color(0xFF5E2614))
        }
        FakeBackend.ART_LIGHTS -> Canvas(modifier) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0F1033), Color(0xFF3A1C71))))
            val colors = listOf(Color(0xFFFF4F8B), Color(0xFFFFC94D), Color(0xFF4DD9FF), Color(0xFFB66BFF))
            for (i in 0 until 26) {
                val x = (i * 37 % 100) / 100f
                val y = (i * 53 % 100) / 100f
                drawCircle(colors[i % colors.size].copy(alpha = 0.55f), radius = w * (0.03f + (i % 5) * 0.012f), center = Offset(w * x, h * (0.15f + y * 0.7f)))
            }
            drawRect(Color(0xFF0B0B22), Offset(0f, h * 0.84f), androidx.compose.ui.geometry.Size(w, h * 0.16f))
        }
        else -> DemoSnap(modifier)
    }
}
