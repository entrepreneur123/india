package com.yaari.chat.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaari.chat.R
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.MANNERS
import com.yaari.chat.model.MannerLevel
import com.yaari.chat.model.MannerTag
import com.yaari.chat.model.Profile
import com.yaari.chat.model.Trust
import kotlin.math.cos
import kotlin.math.sin

// ---- Real identities and manners (035): the verified seal, the manners gauge, the "Verify your identity" screen, the
// rating sheet after a random chat and the manner_low sheet. Every glyph is drawn (TopicIcons' marks), no emoji. ----

/** The app's [TrustController], so any name on screen can show its seal (one batched lookup for all of them). */
val LocalTrust = staticCompositionLocalOf<TrustController?> { null }

/** Green for "good", red for "not good": the manners colours (also the gauge's ends). */
private val GoodColor = Color(0xFF30A46C)
private val BadColor = Color(0xFFE5484D)

@Composable
internal fun isNightTheme() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** The verified seal: a pink rosette with a white tick. */
@Composable
fun VerifiedBadge(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val label = stringResource(R.string.id_verified)
    MarkIcon(Mark.VERIFIED, Brand.Pink, modifier.semantics { contentDescription = label }, size = size)
}

/** [p]'s badges: what the card knows, else the batched lookup (asked for while shown). Null when unknown. */
@Composable
fun trustOf(p: Profile): Trust? {
    val c = LocalTrust.current ?: return null
    val people by c.people.collectAsStateWithLifecycle()
    // A card that already carries the badge (feed and comment authors) needs no lookup.
    LaunchedEffect(p.id, p.verified) {
        c.known(p)
        if (!p.verified) c.want(listOf(p.id))
    }
    return people[p.id]
}

@Composable
fun isVerified(p: Profile): Boolean = trustOf(p)?.verified == true || p.verified

/** The seal after a name, when [p] is ID verified. */
@Composable
fun VerifiedMark(p: Profile, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    if (isVerified(p)) VerifiedBadge(modifier, size)
}

/** "ID verified · Female · 24 · India" with a small lock, on profiles of verified people. */
@Composable
fun IdentityLine(p: Profile, modifier: Modifier = Modifier) {
    if (!isVerified(p)) return
    val country = p.country?.let { iso -> Countries.byIso(iso)?.name() ?: iso }
    val parts = listOfNotNull(stringResource(R.string.id_verified), stringResource(p.gender.label), p.age.takeIf { it > 0 }?.toString(), country)
    val ink = if (isNightTheme()) Brand.PinkOnDark else Brand.OnPinkContainer
    Row(
        modifier.clip(CircleShape).background(if (isNightTheme()) Brand.Pink.copy(alpha = 0.18f) else Brand.PinkContainer)
            .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarkIcon(Mark.LOCK, if (isNightTheme()) Brand.PinkOnDark else Brand.Pink, size = 14.dp)
        Text(
            parts.joinToString(" · "), Modifier.padding(start = 6.dp), color = ink,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- manners ---------------------------------------------------------------------------------------------------------

/**
 * A 240° gauge in five colour bands (the levels), lit up to [score] with a knob on it; the score in the middle. With
 * [rated] false the bands stay pale and the middle shows the shield ("New here").
 */
@Composable
fun MannerGauge(score: Int, rated: Boolean, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val target = if (rated) score.coerceIn(0, 100) / 100f else 0f
    val reduced = LocalReducedMotion.current
    val shown by animateFloatAsState(target, if (reduced) tween(0) else tween(900, easing = FastOutSlowInEasing), label = "gauge")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val knobRing = MaterialTheme.colorScheme.surface
    val level = MannerLevel.of(score)
    val cd = if (rated) stringResource(R.string.manner_score_cd, score, stringResource(level.label)) else stringResource(R.string.manner_new)
    Box(modifier.size(size).semantics { contentDescription = cd }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.minDimension * 0.10f
            val inset = w / 2 + this.size.minDimension * 0.06f
            val arc = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
            val start = 150f
            val sweep = 240f
            val bands = MannerLevel.entries
            bands.forEachIndexed { i, b ->
                val lo = b.min / 100f
                val hi = (bands.getOrNull(i + 1)?.min ?: 100) / 100f
                val from = start + lo * sweep + 1.6f
                val len = (hi - lo) * sweep - 3.2f
                drawArc(track, from, len, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
                if (rated && shown > lo) {
                    val lit = (minOf(shown, hi) - lo) * sweep - (if (shown >= hi) 3.2f else 1.6f)
                    if (lit > 0f) drawArc(Color(b.color), from, lit, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
                }
            }
            if (rated) {
                val a = Math.toRadians((start + shown * sweep).toDouble())
                val r = arc.width / 2
                val c = Offset(this.size.width / 2 + r * cos(a).toFloat(), this.size.height / 2 + r * sin(a).toFloat())
                drawCircle(knobRing, w * 0.95f, c)
                drawCircle(Color(MannerLevel.of((shown * 100).toInt()).color), w * 0.6f, c)
            }
        }
        if (rated) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$score", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = (size.value * 0.27f).sp, lineHeight = (size.value * 0.3f).sp))
                Text("/100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            MarkIcon(Mark.SHIELD, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), size = size * 0.34f)
        }
    }
}

/** A good tag as a soft chip: "Kind · 12". */
@Composable
private fun TagChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, modifier.clip(CircleShape).background(color.copy(alpha = if (isNightTheme()) 0.22f else 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (isNightTheme()) lightTone(color) else color.copy(alpha = 1f).let { androidx.compose.ui.graphics.lerp(it, Color.Black, 0.25f) },
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1,
    )
}

/**
 * Someone's manners: the gauge, the level, how many ratings, and their top kind words. [mine]: with the note. Nothing
 * while manners are off ([MANNERS]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MannerCard(t: Trust, modifier: Modifier = Modifier, mine: Boolean = false) {
    if (!MANNERS) return
    val level = t.level
    val tint = if (t.rated) Color(level.color) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = if (isNightTheme()) 0.16f else 0.10f), tint.copy(alpha = 0.03f))))
            .border(1.dp, tint.copy(alpha = 0.16f), RoundedCornerShape(24.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MannerGauge(t.manner, t.rated, size = 92.dp)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(stringResource(R.string.manners_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(if (t.rated) level.label else R.string.manner_new),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = if (t.rated) tint else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (t.rated) pluralStringResource(R.plurals.manner_ratings, t.ratings, t.ratings) else stringResource(R.string.manner_new_body),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val tags = t.goodTags
        if (t.rated && tags.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { TagChip("${stringResource(it.tag.label)} · ${it.n}", GoodColor) }
            }
        }
        if (mine) {
            Text(stringResource(R.string.manner_note), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Great manners" for the match reveal (only once rated, and while manners are on). */
@Composable
fun MannerChip(t: Trust?, modifier: Modifier = Modifier) {
    if (!MANNERS || t == null || !t.rated) return
    val c = Color(t.level.color)
    Row(
        modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarkIcon(Mark.SHIELD, c, size = 16.dp)
        Text(
            stringResource(R.string.manner_chip, stringResource(t.level.label)), Modifier.padding(start = 6.dp),
            color = Color.White, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

// ---- my profile: the invitation ---------------------------------------------------------------------------------------

/** On my profile until I'm verified: a big invitation, or a quiet line while checking / when it isn't open yet. */
@Composable
fun VerifyCard(vm: AppViewModel, modifier: Modifier = Modifier) {
    val t by vm.trust.state.collectAsStateWithLifecycle()
    val mine = t.mine
    if (mine?.verified == true) return
    val interaction = remember { MutableInteractionSource() }
    val pending = mine?.kyc?.result == com.yaari.chat.model.KycResult.PENDING
    if (pending || t.comingSoon) {
        Row(
            modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(role = Role.Button) { vm.trust.open() }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MarkIcon(Mark.CLOCK, if (pending) Color(0xFF0090FF) else MaterialTheme.colorScheme.onSurfaceVariant, size = 22.dp)
            Text(
                stringResource(if (pending) R.string.verify_card_pending else R.string.verify_card_soon), Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val night = isNightTheme()
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).pressScale(interaction).clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    if (night) listOf(Color(0xFF3A0F22), Color(0xFF2A1630)) else listOf(Color(0xFFFFE3EA), Color(0xFFFFF1E6)),
                ),
            )
            .clickable(interaction, indication = ripple(), role = Role.Button) { vm.trust.open() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(if (night) Color.White.copy(alpha = 0.08f) else Color.White), contentAlignment = Alignment.Center) {
            MarkIcon(Mark.VERIFIED, Brand.Pink, size = 34.dp)
        }
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(stringResource(R.string.verify_card_title), style = MaterialTheme.typography.titleMedium, color = if (night) Color.White else Brand.OnPinkContainer)
            Text(
                stringResource(R.string.verify_card_body), style = MaterialTheme.typography.bodySmall,
                color = if (night) Color.White.copy(alpha = 0.75f) else Brand.OnPinkContainer.copy(alpha = 0.8f),
            )
        }
        Text(
            stringResource(R.string.verify_card_action), Modifier.padding(start = 8.dp).clip(CircleShape).background(Brand.Pink).padding(horizontal = 16.dp, vertical = 8.dp),
            color = Color.White, style = MaterialTheme.typography.labelLarge,
        )
    }
}

// ---- the layer over the app: the verify screen, the sheets, opening the provider's page ------------------------------

@Composable
fun TrustLayer(vm: AppViewModel, me: Profile) {
    val t by vm.trust.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(t.launch) {
        val url = t.launch ?: return@LaunchedEffect
        vm.trust.opened()
        // The demo has no provider: its check passes straight away.
        if (url == FakeBackend.KYC_URL) vm.trust.check() else if (!openVerification(context, url)) vm.trust.check()
    }
    var last by remember { mutableStateOf(t.verify) }
    t.verify?.let { last = it }
    AnimatedVisibility(
        t.verify != null,
        enter = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(260)) { it / 4 } + fadeOut(tween(200)),
    ) { last?.let { v -> VerifyScreen(vm, t.verify ?: v, me) } }
    t.rating?.let { RateChatSheet(vm, it) }
    if (MANNERS && t.mannerLow) MannerLowSheet(vm, t.mine?.trust)
}

/** The provider's hosted page in a Chrome Custom Tab (the browser when there is none). False when nothing can open it. */
fun openVerification(context: Context, url: String): Boolean {
    val uri = url.toUri()
    return try {
        CustomTabsIntent.Builder().setShowTitle(true)
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(Color.White.toArgb()).build())
            .build().launchUrl(context, uri)
        true
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.verify_error_body), Toast.LENGTH_SHORT).show()
            false
        }
    }
}

private data class PhaseText(val mark: Mark, val color: Long, val title: Int, val body: Int)

private fun phaseText(p: VerifyPhase): PhaseText? = when (p) {
    VerifyPhase.START, VerifyPhase.OPENING -> null
    VerifyPhase.WAITING -> PhaseText(Mark.SELFIE, 0xFF0090FF, R.string.verify_waiting_title, R.string.verify_waiting_body)
    VerifyPhase.CHECKING, VerifyPhase.PENDING -> PhaseText(Mark.CLOCK, 0xFF0090FF, R.string.verify_pending_title, R.string.verify_pending_body)
    VerifyPhase.VERIFIED -> PhaseText(Mark.VERIFIED, 0xFFE11D58, R.string.verify_done_title, R.string.verify_done_body)
    VerifyPhase.DECLINED -> PhaseText(Mark.ALERT, 0xFFE5484D, R.string.verify_declined_title, R.string.verify_declined_body)
    VerifyPhase.UNREADABLE -> PhaseText(Mark.ID_CARD, 0xFFF76B15, R.string.verify_unreadable_title, R.string.verify_unreadable_body)
    VerifyPhase.DUPLICATE -> PhaseText(Mark.ID_CARD, 0xFFE5484D, R.string.verify_duplicate_title, R.string.verify_duplicate_body)
    VerifyPhase.MINOR -> PhaseText(Mark.ALERT, 0xFFE5484D, R.string.verify_minor_title, R.string.verify_minor_body)
    VerifyPhase.UNAVAILABLE -> PhaseText(Mark.CLOCK, 0xFF8E4EC6, R.string.verify_soon_title, R.string.verify_soon_body)
    VerifyPhase.LIMIT -> PhaseText(Mark.CLOCK, 0xFFF76B15, R.string.verify_limit_title, R.string.verify_limit_body)
    VerifyPhase.ERROR -> PhaseText(Mark.ALERT, 0xFFF76B15, R.string.verify_error_title, R.string.verify_error_body)
}

/**
 * "Verify your identity": what it takes (an ID, a selfie, about 2 minutes), what is kept and why; then where the check
 * stands. Full screen over the app; closable unless the server requires it right after sign-up.
 */
@Composable
fun VerifyScreen(vm: AppViewModel, v: VerifyUi, me: Profile) {
    val closable = vm.trust.canClose(v)
    BackHandler { if (closable) vm.trust.close() }
    val text = phaseText(v.phase)
    val explaining = text == null || v.phase in setOf(VerifyPhase.ERROR, VerifyPhase.LIMIT, VerifyPhase.DECLINED, VerifyPhase.UNREADABLE)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (closable) CircleIconButton(Icons.Rounded.Close, stringResource(R.string.close), vm.trust::close)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VerifyHero(text)
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(text?.title ?: R.string.verify_title), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(text?.body ?: R.string.verify_subtitle), Modifier.padding(top = 8.dp).widthIn(max = 420.dp),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                if (v.phase == VerifyPhase.VERIFIED) IdentityLine(me.copy(verified = true), Modifier.padding(top = 16.dp))
                if (v.required && v.phase != VerifyPhase.VERIFIED) {
                    Text(
                        stringResource(R.string.verify_required_note), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall,
                        color = Brand.Pink, textAlign = TextAlign.Center,
                    )
                }
                if (explaining) {
                    Spacer(Modifier.height(24.dp))
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Step(1, Mark.ID_CARD, Color(0xFF0090FF), R.string.verify_step_id, R.string.verify_step_id_body)
                        Step(2, Mark.SELFIE, Color(0xFF8E4EC6), R.string.verify_step_selfie, R.string.verify_step_selfie_body)
                        Step(3, Mark.VERIFIED, Brand.Pink, R.string.verify_step_badge, R.string.verify_step_badge_body)
                    }
                }
                if (v.phase != VerifyPhase.VERIFIED && v.phase != VerifyPhase.MINOR) {
                    Spacer(Modifier.height(16.dp))
                    InfoCard(Mark.LOCK, GoodColor, R.string.verify_kept_title, R.string.verify_kept_body)
                    if (explaining) {
                        Spacer(Modifier.height(10.dp))
                        InfoCard(Mark.SHIELD, Brand.Pink, R.string.verify_why_title, R.string.verify_why_body)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp, top = 8.dp)) {
                VerifyActions(vm, v, closable)
            }
        }
    }
}

@Composable
private fun VerifyActions(vm: AppViewModel, v: VerifyUi, closable: Boolean) {
    val busy = v.phase == VerifyPhase.OPENING || v.phase == VerifyPhase.CHECKING
    val (label, action) = when (v.phase) {
        VerifyPhase.START -> R.string.verify_start to vm.trust::start
        VerifyPhase.ERROR, VerifyPhase.DECLINED, VerifyPhase.UNREADABLE -> R.string.verify_try_again to vm.trust::start
        VerifyPhase.OPENING -> R.string.verify_opening to {}
        VerifyPhase.CHECKING -> R.string.verify_checking to {}
        VerifyPhase.WAITING -> R.string.verify_finished to vm.trust::check
        VerifyPhase.PENDING -> R.string.verify_check_again to vm.trust::check
        VerifyPhase.VERIFIED, VerifyPhase.DUPLICATE, VerifyPhase.MINOR, VerifyPhase.UNAVAILABLE, VerifyPhase.LIMIT -> R.string.done to vm.trust::close
    }
    val spinner: (@Composable () -> Unit)? = if (busy) {
        { CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp) }
    } else {
        null
    }
    PrimaryButton(stringResource(label), action, enabled = !busy, leading = spinner)
    val later = closable && v.phase in setOf(VerifyPhase.START, VerifyPhase.ERROR, VerifyPhase.DECLINED, VerifyPhase.UNREADABLE, VerifyPhase.WAITING, VerifyPhase.PENDING)
    if (later) {
        TextButton(vm.trust::close, Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(stringResource(R.string.not_now), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The big illustration: the ID card and the selfie with the seal (start), or the state's own mark on a halo. */
@Composable
private fun VerifyHero(text: PhaseText?) {
    val night = isNightTheme()
    Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
        val halo = if (text == null) Brand.Pink else Color(text.color)
        Box(
            Modifier.size(148.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(halo.copy(alpha = if (night) 0.30f else 0.20f), halo.copy(alpha = 0.04f)))),
        )
        if (text == null) {
            Box(Modifier.size(156.dp)) {
                Box(
                    Modifier.align(Alignment.Center).offset(x = (-14).dp, y = (-6).dp).size(92.dp).clip(RoundedCornerShape(28.dp))
                        .background(if (night) Brand.Ink3 else Color.White).border(1.dp, Brand.Pink.copy(alpha = 0.12f), RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center,
                ) { MarkIcon(Mark.ID_CARD, Color(0xFF0090FF), size = 64.dp) }
                Box(
                    Modifier.align(Alignment.Center).offset(x = 34.dp, y = 30.dp).size(60.dp).clip(CircleShape)
                        .background(if (night) Brand.Ink3 else Color.White).border(1.dp, Brand.Pink.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { MarkIcon(Mark.SELFIE, Color(0xFF8E4EC6), size = 38.dp) }
                MarkIcon(Mark.VERIFIED, Brand.Pink, Modifier.align(Alignment.Center).offset(x = 30.dp, y = (-38).dp), size = 40.dp)
            }
        } else {
            Box(
                Modifier.size(104.dp).clip(CircleShape).background(if (night) Brand.Ink3 else Color.White),
                contentAlignment = Alignment.Center,
            ) { MarkIcon(text.mark, Color(text.color), size = 64.dp) }
        }
    }
}

@Composable
private fun Step(n: Int, mark: Mark, color: Color, title: Int, body: Int) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            MarkIcon(mark, color, size = 28.dp)
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text("$n. ${stringResource(title)}", style = MaterialTheme.typography.titleSmall)
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoCard(mark: Mark, color: Color, title: Int, body: Int) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MarkIcon(mark, color, size = 24.dp)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(body), Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- the rating sheet ---------------------------------------------------------------------------------------------------

/** After a random chat: Good / Not good, up to three tags, Submit or Skip. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RateChatSheet(vm: AppViewModel, r: RateUi) {
    ModalBottomSheet(
        onDismissRequest = vm.trust::skipRating, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Avatar(r.partner, 64.dp)
                VerifiedMark(r.partner, Modifier.align(Alignment.BottomEnd), size = 22.dp)
            }
            Text(
                stringResource(R.string.rate_title, r.partner.name), Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.rate_body), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Choice(Mark.SMILE, GoodColor, stringResource(R.string.rate_good), r.good == true, Modifier.weight(1f)) { vm.trust.rateGood(true) }
                Choice(Mark.FROWN, BadColor, stringResource(R.string.rate_bad), r.good == false, Modifier.weight(1f)) { vm.trust.rateGood(false) }
            }
            AnimatedVisibility(r.good != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                val good = r.good ?: true
                val color = if (good) GoodColor else BadColor
                Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                    Text(stringResource(if (good) R.string.rate_tags_good else R.string.rate_tags_bad), style = MaterialTheme.typography.titleSmall)
                    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MannerTag.of(good).forEach { tag ->
                            val on = tag in r.tags
                            val full = !on && r.tags.size >= MannerTag.MAX
                            val bg by animateColorAsState(if (on) color else MaterialTheme.colorScheme.surfaceContainerHigh, label = "tag")
                            Text(
                                stringResource(tag.label),
                                Modifier.clip(CircleShape).background(bg)
                                    .toggleable(on, enabled = !full, role = Role.Checkbox) { vm.trust.toggleTag(tag) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                color = when {
                                    on -> Color.White
                                    full -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    if (!good) {
                        Text(stringResource(R.string.rate_bad_note), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton(stringResource(R.string.rate_submit), vm.trust::submitRating, enabled = r.good != null && !r.busy)
            TextButton(vm.trust::skipRating, Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(stringResource(R.string.rate_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A big face card: Good (green smile) or Not good (red frown). */
@Composable
private fun Choice(mark: Mark, color: Color, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(24.dp)
    val bg by animateColorAsState(if (selected) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer, label = "choice")
    Column(
        modifier.pressScale(interaction, 0.97f).clip(shape).background(bg)
            .border(if (selected) 2.dp else 1.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant, shape)
            .selectable(selected, interactionSource = interaction, indication = ripple(), role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MarkIcon(mark, color, size = 48.dp)
        Text(label, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, color = if (selected) color else MaterialTheme.colorScheme.onSurface)
    }
}

// ---- manner_low ---------------------------------------------------------------------------------------------------------

/** find_match2 refused with manner_low: why random chat is closed for now, and what still works. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MannerLowSheet(vm: AppViewModel, mine: Trust?) {
    ModalBottomSheet(
        onDismissRequest = vm.trust::dismissMannerLow, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (mine != null && mine.rated) MannerGauge(mine.manner, true, size = 120.dp)
            else Box(Modifier.size(96.dp).clip(CircleShape).background(BadColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                MarkIcon(Mark.ALERT, BadColor, size = 56.dp)
            }
            Text(stringResource(R.string.manner_low_title), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.manner_low_body), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            PrimaryButton(stringResource(R.string.manner_low_ok), vm.trust::dismissMannerLow)
        }
    }
}
