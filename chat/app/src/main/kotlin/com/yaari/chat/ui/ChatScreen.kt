package com.yaari.chat.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaari.chat.R
import com.yaari.chat.data.VideoStatus
import com.yaari.chat.domain.Countries
import com.yaari.chat.domain.ExitPolicy
import com.yaari.chat.model.Media
import com.yaari.chat.model.MediaKind
import com.yaari.chat.model.Message
import com.yaari.chat.model.Profile
import kotlinx.coroutines.delay

/** The 1:1 random chat: always dark and immersive, like a camera view. */
@Composable
fun ChatRoomScreen(vm: AppViewModel, chat: ChatState, premium: Boolean) = NightTheme { RandomChat(vm, chat, premium) }

@Composable
private fun RandomChat(vm: AppViewModel, chat: ChatState, premium: Boolean) {
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

    Column(Modifier.fillMaxSize().background(Brand.Night).imePadding()) {
        Column {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Down to the bar over the tabs (the chat stays on), as a voice room does.
                CircleIconButton(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.chat_minimise), vm::minimiseChat, tint = Color.White.copy(alpha = 0.85f))
                // Normal exit: locked for 5 s (free) / 0.2 s (Plus) after the match starts.
                ExitButton(lockLeft, ExitPolicy.lockMs(premium)) { exitDialog = true }
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { vm.openProfile(partner) }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(partner, 40.dp)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(
                            partner.nameAgeFlag(), color = Color.White, style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!chat.partnerLeft) {
                                PresenceDot(6.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                if (chat.partnerTyping) stringResource(R.string.typing) else stringResource(R.string.chat_subtitle, partner.city.ifBlank { stringResource(partner.gender.label) }),
                                color = if (chat.partnerTyping) Brand.PinkOnDark else Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                // Report & leave is never locked.
                CircleIconButton(
                    Icons.Outlined.Flag, stringResource(R.string.report_and_leave), { reportDialog = true },
                    tint = Color.White.copy(alpha = 0.8f),
                )
                // Block without reporting (also never locked).
                PersonMenu(partner, onBlock = { vm.block(partner) }, onReport = null, tint = Color.White.copy(alpha = 0.8f))
                VideoButton(
                    calling = chat.video == VideoStatus.REQUESTED_BY_ME,
                    enabled = !chat.partnerLeft && chat.video !in setOf(VideoStatus.REQUESTED_BY_ME, VideoStatus.REQUESTED_BY_PARTNER),
                ) { vm.requestVideo() }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
        }
        val country = partner.country?.let { iso -> Countries.byIso(iso)?.name() ?: iso }
        Messages(
            chat.messages, Modifier.weight(1f), hint = stringResource(R.string.say_hi_to, partner.name), typing = chat.partnerTyping,
            intro = country?.let { stringResource(R.string.matched_from, partner.name, it) }, partnerRead = chat.partnerRead,
            ended = if (chat.partnerLeft) stringResource(R.string.partner_left, partner.name) else null,
        )
        if (chat.partnerLeft) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Brand.Ink2)
                    .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PrimaryButton(stringResource(R.string.next_match), { vm.leave(next = true) })
                TextButton({ vm.leave(next = false) }) { Text(stringResource(R.string.back_to_lobby), color = Color.White) }
            }
        } else {
            Composer(onText = vm::sendText, onMedia = vm::sendMedia, onTyping = vm::typing)
        }
    }

    MatchReveal(chat.match.id, partner)
    if (exitDialog) {
        AlertDialog(
            onDismissRequest = { exitDialog = false },
            containerColor = Brand.Ink2,
            title = { Text(stringResource(R.string.leave_title)) },
            text = { Text(stringResource(R.string.leave_text, partner.name)) },
            confirmButton = { TextButton({ exitDialog = false; vm.leave(next = true) }) { Text(stringResource(R.string.next_match), color = Brand.PinkOnDark) } },
            dismissButton = { TextButton({ exitDialog = false; vm.leave(next = false) }) { Text(stringResource(R.string.back_to_lobby), color = Color.White) } },
        )
    }
    if (reportDialog) {
        ReportDialog(onDismiss = { reportDialog = false }, actions = listOf(stringResource(R.string.report_next), stringResource(R.string.report_lobby))) { reason, action ->
            reportDialog = false
            vm.reportAndLeave(reason, next = action == 0)
        }
    }
    if (chat.video == VideoStatus.REQUESTED_BY_PARTNER && !chat.partnerLeft) {
        IncomingVideoSheet(partner, vm::answerVideo)
    }
}

/**
 * The X, inside a ring that empties while the exit lock runs; the seconds left show in the middle.
 * Disabled until the lock is over (the server checks it again).
 */
@Composable
private fun ExitButton(lockLeft: Long, lockMs: Long, onClick: () -> Unit) {
    val locked = lockLeft > 0
    val fraction = if (locked) lockLeft.toFloat() / lockMs.coerceAtLeast(1) else 0f
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(if (locked) Color.White.copy(alpha = 0.06f) else Color.White, tween(200), label = "fill")
    Box(
        Modifier.size(48.dp).pressScale(interaction, 0.9f)
            .clickable(interaction, indication = null, enabled = !locked, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(40.dp)) {
            val w = 3.dp.toPx()
            val inset = w / 2 + 1.5.dp.toPx()
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            drawCircle(Color.White.copy(alpha = 0.12f), radius = arc.width / 2, style = Stroke(w))
            if (fraction > 0f) {
                drawArc(
                    Brand.PinkOnDark, -90f, 360f * fraction, false,
                    topLeft = Offset(inset, inset), size = arc, style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
        Box(Modifier.size(30.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
            if (locked) {
                Text("${(lockLeft + 999) / 1000}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = Jakarta)
            } else {
                Icon(Icons.Rounded.Close, stringResource(R.string.leave), Modifier.size(17.dp), tint = Brand.Night)
            }
        }
    }
}

/** Round brand-colour camera button; a spinner while our request waits for an answer. */
@Composable
private fun VideoButton(calling: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(48.dp).pressScale(interaction, 0.88f)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(40.dp).alpha(if (enabled || calling) 1f else 0.4f).clip(CircleShape).background(Brand.Pink),
            contentAlignment = Alignment.Center,
        ) {
            if (calling) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Videocam, stringResource(R.string.video), Modifier.size(20.dp), tint = Color.White)
        }
    }
}

/** The partner asks for video: a dark sheet rises over the chat. It has to be answered (back does nothing). */
@Composable
private fun IncomingVideoSheet(partner: Profile, onAnswer: (Boolean) -> Unit) {
    BackHandler {}
    val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(shown, enter = fadeIn(tween(200))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
            )
        }
        AnimatedVisibility(
            shown, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it / 3 } + fadeIn(tween(200)),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Brand.Ink2)
                    .navigationBarsPadding().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(40.dp, 4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)))
                Spacer(Modifier.height(32.dp))
                Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                    Radar(104.dp, rings = 2, ringColor = Brand.PinkOnDark.copy(alpha = 0.5f), stroke = 2.dp)
                    Avatar(partner, 88.dp)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.video_incoming_title, partner.name), color = Color.White,
                    style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.video_incoming_text), color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp),
                )
                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val interaction = remember { MutableInteractionSource() }
                    Row(
                        Modifier.weight(1f).height(56.dp).pressScale(interaction, 0.97f).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                            .clickable(interaction, indication = null, role = Role.Button) { onAnswer(false) },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.CallEnd, null, Modifier.size(19.dp), tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.decline), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    PrimaryButton(
                        stringResource(R.string.accept), { onAnswer(true) }, Modifier.weight(1f),
                        leading = { Icon(Icons.Rounded.Videocam, null, Modifier.size(20.dp), tint = Color.White) },
                    )
                }
            }
        }
    }
}

/** Light header for friend messages (DMs): back, avatar, name with flag and a subtitle. */
@Composable
private fun ChatBar(partner: Profile, subtitle: String, onProfile: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack)
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onProfile).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(partner, 40.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(partner.nameWithFlag(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable
fun DmScreen(vm: AppViewModel, dm: DmState) {
    val r = dm.recent
    SecureScreen()
    BackHandler { vm.closeDm() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        ChatBar(r.partner, r.partner.subtitle(), onProfile = { vm.openProfile(r.partner) }, onBack = vm::closeDm)
        Messages(dm.messages, Modifier.weight(1f), hint = stringResource(R.string.say_hi_to, r.partner.name), partnerRead = dm.partnerRead)
        Composer(onText = vm::sendDm, onMedia = null, onTyping = vm::typing)
    }
}

/**
 * The conversation on the chat wallpaper. Runs of messages from one side sit 2 dp apart (only the first has a tail),
 * 8 dp between runs. [partnerRead]: my messages up to this id show two blue ticks. [ended]: the "… left" note.
 */
@Composable
private fun Messages(
    messages: List<Message>, modifier: Modifier, hint: String, typing: Boolean = false, intro: String? = null,
    partnerRead: Long = 0, ended: String? = null,
) {
    val colors = chatColors()
    val list = rememberLazyListState()
    LaunchedEffect(messages.size, typing, ended) {
        if (messages.isNotEmpty()) list.animateScrollToItem(maxOf(0, list.layoutInfo.totalItemsCount - 1))
    }
    if (messages.isEmpty() && !typing) {
        Column(
            modifier.fillMaxSize().background(colors.wall).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            intro?.let { ChatChip(it) }
            Text(hint, color = colors.meta, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            ended?.let { ChatChip(it, bold = true) }
        }
        return
    }
    LazyColumn(
        modifier.fillMaxSize().background(colors.wall), state = list,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    ) {
        intro?.let { item(key = "intro") { ChatChip(it, Modifier.padding(bottom = 6.dp)) } }
        itemsIndexed(messages, key = { _, m -> m.id }) { i, m ->
            val grouped = i > 0 && messages[i - 1].fromMe == m.fromMe
            Box(
                Modifier.animateItem().fillMaxWidth().padding(top = if (grouped) 2.dp else 8.dp),
                contentAlignment = if (m.fromMe) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.fillMaxWidth(0.82f), contentAlignment = if (m.fromMe) Alignment.CenterEnd else Alignment.CenterStart) {
                    PopIn(m.fromMe) { Bubble(m, tail = !grouped, read = m.fromMe && m.id <= partnerRead) }
                }
            }
        }
        item(key = "typing") {
            AnimatedVisibility(typing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Box(Modifier.padding(top = 8.dp)) { TypingBubble() }
            }
        }
        ended?.let { item(key = "ended") { ChatChip(it, Modifier.padding(top = 20.dp), bold = true) } }
    }
}

/** New bubbles settle in from their corner (scale .96, rise 8 dp, fade) in 180 ms. */
@Composable
internal fun PopIn(fromMe: Boolean, content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(180, easing = EaseOutQuint)) }
    Box(
        Modifier.graphicsLayer {
            val v = p.value
            scaleX = 0.96f + 0.04f * v; scaleY = 0.96f + 0.04f * v
            translationY = (1f - v) * 8.dp.toPx()
            alpha = v.coerceIn(0f, 1f)
            transformOrigin = TransformOrigin(if (fromMe) 1f else 0f, 1f)
        },
    ) { content() }
}

/**
 * The moment of a match: the partner's photo and name fade and settle in (300 ms), then the card fades into the chat.
 * Plays once per match; it never blocks touches.
 */
@Composable
private fun MatchReveal(matchId: String, partner: Profile) {
    var shown by rememberSaveable(matchId) { mutableStateOf(true) }
    // Timed on the frame clock (not delay), so it follows the animation scale and UI tests' virtual time.
    LaunchedEffect(matchId) {
        if (shown) Animatable(0f).animateTo(1f, tween(1300, easing = LinearEasing))
        shown = false
    }
    AnimatedVisibility(shown, enter = fadeIn(tween(1)), exit = fadeOut(tween(250))) {
        val scale = remember { Animatable(0.92f) }
        LaunchedEffect(Unit) { scale.animateTo(1f, tween(300, easing = EaseOutQuint)) }
        Box(Modifier.fillMaxSize().aurora(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
                Box(Modifier.size(144.dp).brandRing(), contentAlignment = Alignment.Center) {
                    Avatar(partner, 128.dp)
                }
                Spacer(Modifier.size(24.dp))
                Text(stringResource(R.string.matched_with), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium)
                Text(partner.nameAgeFlag(), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Text(partner.genderCity(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Photo button, pill-shaped input and a round brand-colour send button, on the chat wallpaper. Follows the screen's
 * colours (light or dark).
 * The field clears at once on send; [onText]'s second argument puts the text back if the message did not go out.
 */
@Composable
fun Composer(
    onText: (String, () -> Unit) -> Unit, onMedia: ((Media) -> Unit)?, onTyping: () -> Unit, maxLength: Int = AppViewModel.MAX_TEXT, hint: String? = null,
) {
    val colors = chatColors()
    var text by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && onMedia != null) {
            val video = context.contentResolver.getType(uri)?.startsWith("video") == true
            onMedia(Media(uri.toString(), if (video) MediaKind.VIDEO else MediaKind.IMAGE))
        }
    }
    Column(Modifier.fillMaxWidth().background(colors.wall)) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (onMedia != null) {
                CircleIconButton(
                    Icons.Outlined.Image, stringResource(R.string.send_media),
                    { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    tint = colors.meta,
                )
            }
            Box(
                Modifier.weight(1f).padding(vertical = 2.dp).heightIn(min = 44.dp).shadow(1.dp, RoundedCornerShape(24.dp))
                    .background(colors.incoming).padding(horizontal = 16.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text(
                        hint ?: stringResource(R.string.message_hint), color = colors.meta,
                        style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    text, { text = it.take(maxLength); if (it.isNotBlank()) onTyping() }, Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                    cursorBrush = SolidColor(Brand.Pink), maxLines = 5,
                )
            }
            Spacer(Modifier.size(8.dp))
            val enabled = text.isNotBlank()
            val interaction = remember { MutableInteractionSource() }
            val tick = rememberTick()
            val bg by animateColorAsState(if (enabled) Brand.Pink else MaterialTheme.colorScheme.surfaceContainerHighest, tween(150), label = "send")
            Box(
                Modifier.size(48.dp).pressScale(interaction).clip(CircleShape).background(bg)
                    .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                        tick()
                        val sent = text
                        text = ""
                        onText(sent) { if (text.isEmpty()) text = sent } // nothing typed meanwhile: restore it
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.send), Modifier.size(20.dp), tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
