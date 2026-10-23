package com.yaari.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaari.chat.R
import com.yaari.chat.model.Cosmetic
import com.yaari.chat.model.LookKind
import com.yaari.chat.model.Sticker
import com.yaari.chat.model.StickerArt
import com.yaari.chat.model.Stickers

// ---- Stickers (046): the card each one is drawn as, and the picker beside a chat's text box (packs I own; the others
// locked, with a way to the shop). Sending one is an ordinary message (model/Stickers.kt). -------------------------

/** Each pack's card and caption, in the brand's colours. */
private class Tone(val card: Color, val ring: Color, val ringWidth: Dp, val cap: Color, val capText: Color)

private val TONES = mapOf(
    Cosmetic.STICKER_NAMASTE.id to Tone(Brand.SaffronSoft, Brand.Saffron.copy(alpha = 0.5f), 1.dp, Brand.Saffron, Brand.OnSaffron),
    Cosmetic.STICKER_CHAI.id to Tone(Color.White, Brand.Saffron, 2.dp, Brand.Saffron, Brand.OnSaffron),
    Cosmetic.STICKER_CRICKET.id to Tone(Color.White, Brand.Line, 1.dp, Brand.SaffronDeep, Brand.OnSaffron),
    Cosmetic.STICKER_LOVE.id to Tone(Brand.Saffron, Color.Transparent, 0.dp, Color.White, Brand.SaffronDeep),
)

/** One sticker: a rounded card with the big emoji and its caption (unknown art: a plain sparkle). */
@Composable
fun StickerCard(pack: String, art: StickerArt?, modifier: Modifier = Modifier, size: Dp = 128.dp) {
    val tone = TONES[pack] ?: TONES.getValue(Cosmetic.STICKER_NAMASTE.id)
    val shape = RoundedCornerShape(size * 0.22f)
    val small = size < 56.dp // a shop card's thumbnail: the emoji only
    val label = art?.caption ?: stringResource(R.string.chat_sticker)
    Column(
        modifier.size(size).shadow(2.dp, shape).clip(shape).background(tone.card).border(tone.ringWidth, tone.ring, shape)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(size * 0.06f, Alignment.CenterVertically),
    ) {
        Text(art?.emoji ?: "✨", fontSize = (size.value * if (small) 0.5f else 0.4f).sp, lineHeight = (size.value * if (small) 0.56f else 0.46f).sp)
        if (art != null && !small) {
            // Two lines at most, so longer captions ("Chai pe charcha") still fit a small card.
            val font = maxOf(8.5f, size.value * 0.1f)
            Text(
                art.caption, Modifier.padding(horizontal = size * 0.06f).clip(RoundedCornerShape(font.dp * 0.7f)).background(tone.cap)
                    .padding(horizontal = (font * 0.6f).dp, vertical = (font * 0.2f).dp),
                color = tone.capText, fontSize = font.sp, lineHeight = (font * 1.1f).sp, fontWeight = FontWeight.ExtraBold, fontFamily = Jakarta,
                textAlign = TextAlign.Center, maxLines = 2,
            )
        }
    }
}

/** A sticker message: the card itself, no bubble, with [meta] (time, ticks) in a small pill under it. */
@Composable
fun StickerLine(sticker: Sticker, mine: Boolean, modifier: Modifier = Modifier, size: Dp = 128.dp, meta: @Composable () -> Unit) {
    val colors = chatColors()
    Column(modifier, horizontalAlignment = if (mine) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StickerCard(sticker.pack, sticker.art, size = size)
        Box(Modifier.clip(CircleShape).background(colors.incoming.copy(alpha = 0.85f)).padding(horizontal = 6.dp, vertical = 2.dp)) { meta() }
    }
}

/**
 * The sticker button beside a chat's text box, and its sheet of packs: mine to send ([onSend] gets the message), the
 * others locked with a way to the shop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerButton(
    onSend: (String) -> Unit, modifier: Modifier = Modifier, background: Color = Color.Transparent, tint: Color = chatColors().meta, size: Dp = 40.dp,
) {
    val looks = LocalLooks.current ?: return
    val ui by looks.shop.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { looks.ensureShop() }
    var open by remember { mutableStateOf(false) }
    val title = stringResource(R.string.looks_stickers)
    CircleIconButton(Icons.Outlined.StickyNote2, title, { open = true }, modifier, background = background, tint = tint, size = size, iconSize = 22.dp)
    if (!open) return
    val owned = ui.shop?.owned.orEmpty()
    val packs = Stickers.PACKS.keys.toList()
    var pack by remember { mutableStateOf(packs.firstOrNull { it.id in owned } ?: packs.first()) }
    val mine = pack.id in owned
    ModalBottomSheet(
        onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                packs.forEach { p ->
                    val on = p == pack
                    Row(
                        Modifier.height(40.dp).clip(CircleShape).background(if (on) Brand.SaffronSoft else Color.Transparent)
                            .border(1.dp, if (on) Brand.SaffronDeep else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable(role = Role.Tab) { pack = p }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(Stickers.PACKS.getValue(p).first().emoji, fontSize = 18.sp)
                        Text(
                            stringResource(p.label), style = MaterialTheme.typography.labelLarge,
                            color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (p.id !in owned) Icon(Icons.Rounded.Lock, stringResource(R.string.sticker_locked), Modifier.size(14.dp), tint = Brand.SaffronDeep)
                    }
                }
            }
            Column(Modifier.alpha(if (mine) 1f else 0.45f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Stickers.PACKS.getValue(pack).chunked(4).forEachIndexed { row, arts ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        arts.forEachIndexed { i, art ->
                            StickerCard(
                                pack.id, art, size = 72.dp, modifier =
                                Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = mine, role = Role.Button) {
                                    open = false
                                    onSend(Stickers.body(pack, row * 4 + i + 1))
                                },
                            )
                        }
                    }
                }
            }
            if (ui.shop != null && !mine) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp), tint = Brand.SaffronDeep)
                    Text(stringResource(R.string.sticker_locked), Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    SoftButton(stringResource(R.string.sticker_get_pack), { open = false; looks.openShop(LookKind.STICKER) }, accent = true, height = 36.dp)
                }
            }
        }
    }
}
