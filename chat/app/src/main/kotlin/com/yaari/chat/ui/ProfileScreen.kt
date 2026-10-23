package com.yaari.chat.ui

import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Add
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yaari.chat.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaari.chat.data.Products
import com.yaari.chat.domain.Countries
import com.yaari.chat.domain.ExitPolicy
import com.yaari.chat.domain.Validation
import com.yaari.chat.domain.flagOf
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Post
import com.yaari.chat.model.PostVisibility
import com.yaari.chat.model.Profile
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.Cosmetic
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import com.yaari.chat.domain.Handle
import java.text.DateFormat
import java.util.Date
import com.yaari.chat.model.HandleCheck
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.rounded.CheckCircle

// ---- My profile (the "Me" tab) -------------------------------------------------------------------

@Composable
fun MyProfileTab(vm: AppViewModel, s: UiState, onSettings: () -> Unit, onEdit: () -> Unit, onNewPost: () -> Unit, onDetails: () -> Unit, onLooks: () -> Unit) {
    val me = s.me ?: return
    val friends = s.friends.count { it.state == FriendState.FRIENDS }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    me.name, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                VerifiedMark(me, Modifier.padding(start = 6.dp), size = 20.dp)
                if (s.premium) Icon(Icons.Rounded.WorkspacePremium, stringResource(R.string.plan_premium), Modifier.padding(start = 8.dp).size(20.dp), tint = Brand.Saffron)
            }
            CircleIconButton(Icons.Outlined.Settings, stringResource(R.string.settings), onSettings, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PostGrid(me.posts, emptyMine = onNewPost, onOpen = { vm.feed.openDetail(it.copy(author = it.author ?: me.copy(posts = emptyList()))) }) {
            ProfileHeader(
                me, stats = listOf(me.posts.size to pluralLabel(R.plurals.posts_label, me.posts.size), friends to pluralLabel(R.plurals.friends_label, friends)),
                onLooks = onLooks,
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton(stringResource(R.string.edit_profile), onEdit, Modifier.weight(1f))
                SoftButton(
                    stringResource(R.string.new_post), onNewPost, Modifier.weight(1f), accent = true,
                    leading = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = Brand.OnSaffron) },
                )
            }
            val social by vm.social.state.collectAsStateWithLifecycle()
            val trust by vm.trust.state.collectAsStateWithLifecycle()
            // Until verified: the invitation, prominent (or a quiet line while checking / before it opens).
            VerifyCard(vm, Modifier.padding(top = 16.dp))
            trust.mine?.let { MannerCard(it.trust, Modifier.padding(horizontal = 16.dp).padding(top = 16.dp), mine = true) }
            Spacer(Modifier.height(16.dp))
            if (me.style.isEmpty) StyleNudge(onDetails, Modifier.padding(bottom = 8.dp))
            PromptCards(me, Modifier.padding(bottom = 8.dp))
            InterestsSection(me, mine = true, onEdit = { vm.social.editInterests(true) })
            PhotosSection(me.photos, editable = true, busy = social.photoBusy, onAdd = { vm.social.addPhoto(it, me.photos.size) }, onRemove = vm.social::removePhoto)
            if (!s.premium) PlusBanner(s, onClick = { vm.showPaywall(Paywall.GENERAL) })
        }
    }
}

@Composable
private fun pluralLabel(id: Int, n: Int) = pluralStringResource(id, n)

/** Yaari Plus advert on my profile: a flat tonal card. */
@Composable
private fun PlusBanner(s: UiState, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp).pressScale(interaction).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer).clickable(interaction, indication = ripple(), role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(stringResource(if (s.entitlements.genderPass) R.string.plan_pass else R.string.premium_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                stringResource(R.string.premium_summary, ExitPolicy.PREMIUM_LOCK_MS / 1000.0), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2,
            )
        }
        Text(
            stringResource(R.string.upgrade), color = Brand.OnSaffron, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(Brand.Saffron).padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

// ---- Someone else's profile ----------------------------------------------------------------------

/** Opened from a chat, a room or Friends/Recent; shown over whatever is underneath. */
@Composable
fun ProfileScreen(vm: AppViewModel, p: Profile, s: UiState) {
    SecureScreen()
    BackHandler(onBack = vm::closeProfile)
    var menu by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var unfriend by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(false) }
    val state = s.friendState(p.id)
    val inChat = s.chat?.match?.partner?.id == p.id
    val canMessage = !inChat && (state == FriendState.FRIENDS || s.recents.any { it.partner.id == p.id })
    var editingContacts by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), vm::closeProfile)
                Text(p.nameWithFlag(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(menu, { menu = false }) {
                        if (state == FriendState.FRIENDS) DropdownMenuItem({ Text(stringResource(R.string.unfriend)) }, { menu = false; unfriend = true })
                        DropdownMenuItem({ Text(stringResource(R.string.block)) }, { menu = false; blocking = true })
                        DropdownMenuItem({ Text(stringResource(R.string.report_block), color = MaterialTheme.colorScheme.error) }, { menu = false; reporting = true })
                    }
                }
            }
            PostGrid(p.posts, emptyMine = null, onOpen = { vm.feed.openDetail(it.copy(author = it.author ?: p.copy(posts = emptyList()))) }) {
                val country = p.country?.let { iso -> Countries.byIso(iso)?.name() ?: iso }
                ProfileHeader(
                    p,
                    stats = listOfNotNull(
                        p.posts.size to pluralLabel(R.plurals.posts_label, p.posts.size),
                        p.age to stringResource(R.string.age),
                    ),
                    extra = country?.let { "${flagOf(p.country)} $it" },
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (state) {
                        FriendState.NONE -> SoftButton(stringResource(R.string.add_friend), { vm.addFriend(p) }, Modifier.weight(1f), accent = true)
                        FriendState.INCOMING -> SoftButton(stringResource(R.string.accept_request), { vm.answerFriend(p, true) }, Modifier.weight(1f), accent = true)
                        FriendState.REQUESTED -> SoftButton(stringResource(R.string.requested), { vm.removeFriend(p) }, Modifier.weight(1f))
                        FriendState.FRIENDS -> SoftButton(stringResource(R.string.friends_check), { unfriend = true }, Modifier.weight(1f))
                    }
                    if (canMessage) SoftButton(stringResource(R.string.message), { vm.openDm(p.id) }, Modifier.weight(1f))
                }
                if (state == FriendState.FRIENDS) FriendContactsSection(vm, p, onEdit = { editingContacts = true }, Modifier.padding(top = 8.dp))
                trustOf(p)?.let { MannerCard(it, Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) }
                Spacer(Modifier.height(16.dp))
                PromptCards(p, Modifier.padding(bottom = 8.dp))
                InterestsSection(p, mine = false, onEdit = {}, shared = p.interests.intersect(s.me?.interests.orEmpty().toSet()))
                PhotosSection(p.photos, editable = false)
            }
        }
    }
    if (reporting) ReportDialog(onDismiss = { reporting = false }, actions = listOf(stringResource(R.string.report_block))) { reason, _ ->
        reporting = false
        vm.report(p.id, reason)
    }
    if (blocking) BlockDialog(p, onDismiss = { blocking = false }) { blocking = false; vm.block(p) }
    // Sharing with no contacts saved yet starts here; saving shares them.
    if (editingContacts) {
        ContactsEditorScreen(vm, onDone = { editingContacts = false }, onSaved = { if (it.isNotEmpty()) vm.contacts.share(p.id, p.name, true) })
    }
    if (unfriend) {
        AlertDialog(
            onDismissRequest = { unfriend = false },
            title = { Text(stringResource(R.string.unfriend_title, p.name)) },
            confirmButton = { TextButton({ unfriend = false; vm.removeFriend(p) }) { Text(stringResource(R.string.unfriend), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ unfriend = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// ---- Shared pieces -------------------------------------------------------------------------------

/**
 * The hero card, in the person's frame when they wear one: the photo in its ring with the numbers beside it, then the
 * big plain name, the decorated @ID under it, the status, details, bio and languages. [onLooks]: mine, with the Looks
 * button.
 */
@Composable
private fun ProfileHeader(p: Profile, stats: List<Pair<Int, String>>, extra: String? = null, onLooks: (() -> Unit)? = null) {
    val frame = Cosmetic.of(lookOf(p.id)?.frame)
    FramedCard(frame, Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 4.dp, bottom = 16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FramedAvatar(p, 96.dp, frame)
                Row(Modifier.weight(1f).padding(start = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    stats.forEach { (n, label) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RollingNumber(n, MaterialTheme.typography.titleLarge)
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp))
                VerifiedMark(p, Modifier.padding(start = 6.dp), size = 20.dp)
                if (onLooks != null) {
                    Spacer(Modifier.weight(1f))
                    SoftButton(
                        stringResource(R.string.looks), onLooks, Modifier.padding(start = 8.dp), height = 36.dp,
                        leading = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = Brand.Saffron) },
                    )
                }
            }
            LookName(p.id, size = 15.sp)
            // ID verified people: what the document says, locked.
            IdentityLine(p, Modifier.padding(vertical = 4.dp))
            StatusLine(p, Modifier.padding(vertical = 2.dp))
            Text(
                listOfNotNull(p.subtitle(), extra).joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (p.bio.isNotBlank()) Text(p.bio, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            LanguagesRow(p, Modifier.padding(top = 8.dp))
        }
    }
}

/** Header content, a grid icon bar, then the 3-column grid; tapping a post opens it full screen ([onOpen]). */
@Composable
private fun PostGrid(posts: List<Post>, emptyMine: (() -> Unit)?, onOpen: (Post) -> Unit, header: @Composable () -> Unit) {
    LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { Column { header() } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 16.dp)) {
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.GridOn, null, Modifier.size(24.dp))
                    Box(Modifier.align(Alignment.BottomCenter).width(120.dp).height(2.dp).background(MaterialTheme.colorScheme.onSurface))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (posts.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (emptyMine != null) {
                    Box(Modifier.clickable(onClick = emptyMine)) {
                        EmptyState(Icons.Outlined.CameraAlt, stringResource(R.string.share_first_title), stringResource(R.string.share_first))
                    }
                } else {
                    EmptyState(Icons.Outlined.CameraAlt, stringResource(R.string.no_posts_title), stringResource(R.string.no_posts))
                }
            }
        }
        items(posts, key = { it.id }) { post -> PostTile(post) { onOpen(post) } }
    }
}

@Composable
private fun PostTile(post: Post, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.aspectRatio(1f).background(Brand.SaffronSoft)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (post.imageUri != null) {
            PostImage(post.imageUri, post.text.ifEmpty { null }, Modifier.fillMaxSize())
        } else {
            Text(
                post.text, color = Brand.Text, maxLines = 4, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            )
        }
        if (post.likes > 0 || post.comments > 0) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarkIcon(Mark.HEART, Color.White, size = 12.dp)
                Text("${post.likes}", Modifier.padding(start = 3.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                if (post.comments > 0) {
                    MarkIcon(Mark.COMMENT, Color.White, Modifier.padding(start = 6.dp), size = 12.dp)
                    Text("${post.comments}", Modifier.padding(start = 3.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** "Block X? You won't see each other or be matched again." */
@Composable
fun BlockDialog(p: Profile, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.block_title, p.name)) },
        text = { Text(stringResource(R.string.block_text)) },
        confirmButton = { TextButton(onConfirm) { Text(stringResource(R.string.block), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * The overflow (⋮) for a person in a list or chat header: Block (confirmed first) and, when [onReport] is given,
 * Report & block with a reason.
 */
@Composable
fun PersonMenu(
    p: Profile, onBlock: () -> Unit, onReport: ((ReportReason) -> Unit)?, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var open by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    Box {
        CircleIconButton(Icons.Rounded.MoreVert, stringResource(R.string.more), { open = true }, tint = tint)
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.block)) }, { open = false; blocking = true })
            if (onReport != null) {
                DropdownMenuItem({ Text(stringResource(R.string.report_block), color = MaterialTheme.colorScheme.error) }, { open = false; reporting = true })
            }
        }
    }
    if (blocking) BlockDialog(p, onDismiss = { blocking = false }) { blocking = false; onBlock() }
    if (reporting && onReport != null) {
        ReportDialog(onDismiss = { reporting = false }, actions = listOf(stringResource(R.string.report_block))) { reason, _ ->
            reporting = false
            onReport(reason)
        }
    }
}

/** Reason picker. [actions] are button labels; the chosen index is passed back. */
@Composable
fun ReportDialog(onDismiss: () -> Unit, actions: List<String>, onConfirm: (reason: ReportReason, action: Int) -> Unit) {
    var reason by remember { mutableStateOf(ReportReason.NUDITY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_title)) },
        text = {
            Column {
                ReportReason.entries.forEach { r ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { reason = r }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(reason == r, { reason = r })
                        Text(stringResource(r.label))
                    }
                }
                Text(stringResource(R.string.report_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton({ onConfirm(reason, 0) }) { Text(actions[0], color = MaterialTheme.colorScheme.error) } },
        dismissButton = {
            if (actions.size > 1) TextButton({ onConfirm(reason, 1) }) { Text(actions[1], color = MaterialTheme.colorScheme.error) }
            else TextButton(onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

// ---- Edit profile & new post -----------------------------------------------------------------------

/** Top bar used by the full-screen editors: Cancel · Title · Done. */
@Composable
internal fun EditorBar(title: String, onCancel: () -> Unit, done: String, doneEnabled: Boolean, onDone: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onCancel) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onDone, enabled = doneEnabled) { Text(done, style = MaterialTheme.typography.labelLarge) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun EditProfileScreen(
    vm: AppViewModel, me: Profile, onDone: () -> Unit, onDetails: () -> Unit = {}, onLooks: () -> Unit = {}, onContacts: () -> Unit = {},
) {
    BackHandler(onBack = onDone)
    var name by rememberSaveable { mutableStateOf(me.name) }
    var handle by rememberSaveable { mutableStateOf(me.handle.orEmpty()) }
    var city by rememberSaveable { mutableStateOf(me.city) }
    var bio by rememberSaveable { mutableStateOf(me.bio) }
    var photo by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) photo = it.toString() }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    val field by vm.looks.handle.collectAsStateWithLifecycle()
    val shop by vm.looks.shop.collectAsStateWithLifecycle()
    // When the ID may change again for free (shop().handle_next): a different ID before then takes a paid ID change
    // (a new spelling doesn't).
    LaunchedEffect(Unit) { vm.looks.loadShop() }
    DisposableEffect(Unit) { onDispose { vm.looks.clearHandle() } }
    val next = shop.shop?.handleNext?.takeIf { it > System.currentTimeMillis() }
    val passes = shop.shop?.handlePasses ?: 0
    val waiting = next != null && passes < 1 && !Handle.normalize(handle).equals(me.handle.orEmpty(), ignoreCase = true)
    val problem = vm.looks.problem(field?.takeIf { it.text == handle })?.text()
    val cooldown = next?.let {
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
        if (passes > 0) stringResource(R.string.handle_pass_use, passes, date) else stringResource(R.string.handle_next, date)
    }
    val activity = LocalActivity.current
    val price = vm.state.collectAsStateWithLifecycle().value.prices[Products.HANDLE_CHANGE]
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            EditorBar(stringResource(R.string.edit_profile), onDone, stringResource(R.string.done), name.isNotBlank() && problem == null && !waiting) {
                vm.saveProfile(name, city, bio, photo, handle, onDone)
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(me.copy(photoUri = photo ?: me.photoUri, name = name.ifBlank { me.name }), 96.dp, Modifier.clip(CircleShape).clickable { pick() })
                    TextButton(::pick) { Text(stringResource(R.string.change_photo)) }
                }
                Field(name, { name = it.take(Validation.MAX_NAME) }, stringResource(R.string.name))
                HandleInput(handle, { handle = it; vm.looks.checkHandle(it, me.handle) }, field, problem ?: cooldown?.takeIf { waiting }, note = cooldown)
                if (waiting && activity != null) {
                    SoftButton(
                        listOfNotNull(stringResource(R.string.handle_pass_buy), price).joinToString(" · "),
                        { vm.buyLook(activity, Products.HANDLE_CHANGE) }, accent = true, enabled = shop.busy == null,
                    )
                }
                Field(city, { city = it.take(30) }, stringResource(R.string.city))
                Field(bio, { bio = it.take(Validation.MAX_BIO) }, stringResource(R.string.bio), single = false, counter = "${bio.length}/${Validation.MAX_BIO}")
                EditorLink(stringResource(R.string.looks), stringResource(R.string.looks_sub), onLooks) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(22.dp), tint = Brand.Saffron)
                }
                EditorLink(stringResource(R.string.profile_details), stringResource(R.string.details_nudge), onDetails) {
                    MarkIcon(Mark.PALETTE, MaterialTheme.colorScheme.primaryText, size = 22.dp)
                }
                EditorLink(stringResource(R.string.contacts_title), stringResource(R.string.contacts_note), onContacts) {
                    Icon(Icons.Rounded.ContactPhone, null, Modifier.size(22.dp), tint = Brand.Saffron)
                }
                // Locked on the server too: gender and age can't be changed after sign-up.
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val fromId = isVerified(me)
                    if (fromId) MarkIcon(Mark.VERIFIED, Brand.Saffron, size = 22.dp)
                    else Icon(Icons.Outlined.Lock, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.padding(start = 16.dp)) {
                        val country = me.country?.let { iso -> Countries.byIso(iso)?.name() ?: iso }
                        Text(listOfNotNull(stringResource(me.gender.label), me.age.toString(), country).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(if (fromId) R.string.locked_from_id else R.string.locked_fields),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** A row in Edit profile that opens another editor: its mark, title and a line about it. */
@Composable
private fun EditorLink(title: String, sub: String, onClick: () -> Unit, mark: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mark()
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The ID field: "@" before it, a spinner while the server checks it, a tick when it is free, and under it what is
 * wrong ([problem]), else [note], else the rules.
 */
@Composable
fun HandleInput(value: String, onChange: (String) -> Unit, field: HandleField?, problem: String?, note: String? = null) {
    val free = field?.text == value && field.result in setOf(HandleCheck.OK, HandleCheck.MINE)
    OutlinedTextField(
        value, { onChange(it.take(Handle.MAX * 2)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.handle_label)) },
        prefix = { Text("@") }, singleLine = true, isError = problem != null, shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        trailingIcon = {
            when {
                field?.text == value && field.checking -> CircularProgressIndicator(Modifier.size(18.dp), color = Brand.Saffron, strokeWidth = 2.dp)
                free -> Icon(Icons.Rounded.CheckCircle, stringResource(R.string.handle_available), tint = Brand.Online)
            }
        },
        supportingText = { Text(problem ?: note ?: stringResource(R.string.handle_hint)) },
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedBorderColor = Brand.Saffron,
            focusedLabelColor = MaterialTheme.colorScheme.primaryText, unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = Brand.Saffron, focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
fun Field(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, single: Boolean = true, counter: String? = null,
    keyboard: KeyboardOptions = KeyboardOptions.Default, error: String? = null, placeholder: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = single, minLines = if (single) 1 else 3,
        keyboardOptions = keyboard,
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        isError = error != null, leadingIcon = leading, placeholder = placeholder?.let { { Text(it) } },
        supportingText = error?.let { { Text(it) } } ?: counter?.let { { Text(it, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) } },
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedBorderColor = Brand.Saffron,
            focusedLabelColor = MaterialTheme.colorScheme.primaryText, unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = Brand.Saffron, focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/** Full-screen composer, like Instagram's. */
@Composable
fun NewPostScreen(onCancel: () -> Unit, onPost: (String, String?, PostVisibility) -> Unit) {
    BackHandler(onBack = onCancel)
    var text by rememberSaveable { mutableStateOf("") }
    var visibility by rememberSaveable { mutableStateOf(PostVisibility.FRIENDS) }
    var uri by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) uri = it.toString() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            EditorBar(stringResource(R.string.new_post), onCancel, stringResource(R.string.share), text.isNotBlank() || uri != null) { onPost(text, uri, visibility) }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (uri != null) AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.CameraAlt, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.add_photo), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Field(text, { text = it.take(Post.MAX_TEXT) }, stringResource(R.string.write_something), single = false)
                VisibilityChooser(visibility) { visibility = it }
                Spacer(Modifier.width(1.dp))
            }
        }
    }
}
