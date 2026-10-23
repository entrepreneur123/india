package com.yaari.chat.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaari.chat.R
import com.yaari.chat.model.Cosmetic
import com.yaari.chat.model.Look
import com.yaari.chat.model.LookItem
import com.yaari.chat.model.LookKind
import com.yaari.chat.model.Profile
import com.yaari.chat.model.Stickers
import com.yaari.chat.model.VIP_NAME
import com.yaari.chat.model.nameAge

// ---- The Looks shop (040, 046): a preview on top (my hero card with what I'm trying on, a chat on the wall, a sticker
// pack, how strangers see me in VIP mode), then a tab per kind. Looks: tapping a card tries it on; what I own is worn
// with Wear (one per kind, None takes it off). Walls: set_wall ("None" is the plain one). Sticker packs are used from a
// chat's sticker button; VIP incognito is a switch (set_anon). The rest is bought through Google Play (the demo: at
// once). ------------------------------------------------------------------------------------------------------------

private val TABS = listOf(
    LookKind.BADGE to R.string.looks_badges, LookKind.COLOR to R.string.looks_colors, LookKind.FONT to R.string.looks_fonts,
    LookKind.FRAME to R.string.looks_frames, LookKind.WALL to R.string.looks_walls, LookKind.STICKER to R.string.looks_stickers,
    LookKind.VIP to R.string.looks_vip,
)

@Composable
fun LooksShopScreen(vm: AppViewModel, s: UiState, me: Profile, start: LookKind = LookKind.BADGE, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val ui by vm.looks.shop.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    var tab by rememberSaveable { mutableStateOf(start) }
    // The wall and the sticker pack being looked at (not tried yet: my wall, the first pack).
    var wallTried by rememberSaveable { mutableStateOf(false) }
    var wallTry by rememberSaveable { mutableStateOf<String?>(null) }
    var packTry by rememberSaveable { mutableStateOf(Cosmetic.STICKER_NAMASTE.id) }
    LaunchedEffect(Unit) { vm.looks.loadShop() }
    val shop = ui.shop
    val trying = ui.trying ?: Look(me.id, me.handle.orEmpty())
    val wall = if (wallTried) wallTry else shop?.wall
    val buy = { item: LookItem -> activity?.let { vm.buyLook(it, item.id) } }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onDone) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
                Text(stringResource(R.string.looks), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyVerticalGrid(
                GridCells.Adaptive(156.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    when (tab) {
                        LookKind.WALL -> Caption(stringResource(R.string.wall_only_you)) { WallPreview(wallOf(wall)) }
                        LookKind.STICKER -> Caption(stringResource(if (shop != null && packTry in shop.owned) R.string.sticker_owned else R.string.sticker_count)) {
                            PackPreview(packTry)
                        }
                        LookKind.VIP -> VipPreview(me)
                        else -> Caption(stringResource(R.string.looks_everyone)) { Preview(me, trying) }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    // Only the kinds on sale (a newer server may sell fewer): all of them until the shop is known.
                    val tabs = TABS.filter { (k, _) -> shop == null || shop.items.any { it.kind == k } }
                    Tabs(tabs.map { stringResource(it.second) }, tabs.indexOfFirst { it.first == tab }) { tab = tabs[it].first }
                }
                val items = shop?.items.orEmpty().filter { it.kind == tab }.mapNotNull { item -> Cosmetic.of(item.id)?.let { item to it } }
                val price: @Composable (LookItem, () -> Unit) -> Unit = { item, tried ->
                    PriceButton(item, s.prices[item.id], ui.busy == item.id, enabled = ui.busy == null) { tried(); buy(item) }
                }
                when {
                    shop == null -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            if (ui.failed) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.looks_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    SoftButton(stringResource(R.string.common_try_again), vm.looks::loadShop, Modifier.padding(top = 12.dp), outlined = true)
                                }
                            } else {
                                Spinner()
                            }
                        }
                    }
                    tab == LookKind.VIP -> items.forEach { (item, _) ->
                        item(key = item.id, span = { GridItemSpan(maxLineSpan) }) {
                            if (item.id in shop.owned) {
                                SwitchItem(
                                    stringResource(R.string.vip_toggle), stringResource(R.string.vip_toggle_body), shop.anon,
                                    Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    enabled = ui.busy == null, onChange = vm.looks::setAnon,
                                )
                            } else {
                                PrimaryButton(s.prices[item.id] ?: "₹${item.priceInr}", { buy(item) }, enabled = ui.busy == null)
                            }
                        }
                    }
                    tab == LookKind.STICKER -> items(items, key = { it.first.id }) { (item, cosmetic) ->
                        ItemCard(stringResource(cosmetic.label), packTry == item.id, { packTry = item.id }, {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Stickers.PACKS.getValue(cosmetic).take(2).forEach { StickerCard(item.id, it, size = 48.dp) }
                            }
                        }) {
                            if (item.id in shop.owned) InUse(stringResource(R.string.looks_owned)) else price(item) { packTry = item.id }
                        }
                    }
                    tab == LookKind.WALL -> {
                        item(key = "none-wall") {
                            ItemCard(stringResource(R.string.looks_none), wall == null, { wallTried = true; wallTry = null }, { WallSwatch(null) }) {
                                when {
                                    ui.busy == LookKind.WALL.code -> Spinner()
                                    shop.wall == null -> InUse(stringResource(R.string.wall_in_use))
                                    else -> SoftButton(stringResource(R.string.wall_remove), { vm.looks.setWall(null) }, height = 36.dp, outlined = true)
                                }
                            }
                        }
                        items(items, key = { it.first.id }) { (item, cosmetic) ->
                            ItemCard(stringResource(cosmetic.label), wall == item.id, { wallTried = true; wallTry = item.id }, { WallSwatch(item.id) }) {
                                when {
                                    item.id !in shop.owned -> price(item) { wallTried = true; wallTry = item.id }
                                    ui.busy == item.id -> Spinner()
                                    shop.wall == item.id -> InUse(stringResource(R.string.wall_in_use))
                                    else -> SoftButton(stringResource(R.string.wall_use), { vm.looks.setWall(item.id) }, height = 36.dp, outlined = true)
                                }
                            }
                        }
                    }
                    else -> {
                        val worn = shop.look?.of(tab)
                        item(key = "none-${tab.code}") {
                            ItemCard(stringResource(R.string.looks_none), trying.of(tab) == null, { vm.looks.tryOn(tab, null) }, { LookSample(trying.with(tab, null), me, tab) }) {
                                WearButton(worn == null, ui.busy == tab.code) { vm.looks.wear(tab, null) }
                            }
                        }
                        items(items, key = { it.first.id }) { (item, cosmetic) ->
                            ItemCard(stringResource(cosmetic.label), trying.of(tab) == item.id, { vm.looks.tryOn(tab, item.id) }, { LookSample(trying.with(tab, item.id), me, tab) }) {
                                if (item.id in shop.owned) WearButton(worn == item.id, ui.busy == item.id) { vm.looks.wear(tab, item.id) }
                                else price(item) { vm.looks.tryOn(tab, item.id) }
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

/** The preview, and the line under it. */
@Composable
private fun Caption(text: String, preview: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        preview()
        Text(
            text, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

/** My hero card as everyone would see it with what I'm trying on. */
@Composable
private fun Preview(me: Profile, look: Look) {
    val frame = Cosmetic.of(look.frame)
    FramedCard(frame, Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            FramedAvatar(me, 80.dp, frame)
            Column(Modifier.padding(start = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(me.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    VerifiedMark(me, Modifier.padding(start = 6.dp), size = 18.dp)
                }
                if (look.handle.isNotEmpty()) LookName(look, Modifier.padding(top = 2.dp), size = 16.sp)
            }
        }
    }
}

/** A chat on this wall (null: the plain one), the way only I will see it. */
@Composable
private fun WallPreview(wall: WallDesign?) {
    val colors = chatColors()
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().height(176.dp).clip(shape).background(colors.wall).chatWall(wall)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
    ) {
        listOf(0.5f to false, 0.4f to true, 0.6f to false).forEach { (w, mine) ->
            Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                BubbleShell(mine, tail = true, Modifier.fillMaxWidth(w).height(32.dp)) {}
            }
        }
    }
}

/** A wall as a shop card's swatch (null: the plain one). */
@Composable
private fun WallSwatch(id: String?) {
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth().height(48.dp).clip(shape).background(chatColors().wall).chatWall(wallOf(id)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape))
}

/** A pack's eight stickers. */
@Composable
private fun PackPreview(pack: String) {
    val art = Cosmetic.of(pack)?.let(Stickers.PACKS::get).orEmpty()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        art.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { row.forEach { StickerCard(pack, it, size = 68.dp) } }
        }
    }
}

/** VIP: how strangers and friends see me, and what incognito hides. */
@Composable
private fun VipPreview(me: Profile) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SeenAs(stringResource(R.string.vip_strangers), Profile(me.id, VIP_NAME, me.gender, 0, "", ""), Modifier.weight(1f))
            SeenAs(stringResource(R.string.vip_friends), me, Modifier.weight(1f))
        }
        Text(stringResource(R.string.vip_what), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SeenAs(label: String, p: Profile, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Avatar(p, 56.dp)
        Text(nameAge(p.name, p.age), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A look on my @ID, or a frame on a small framed photo. */
@Composable
private fun LookSample(look: Look, me: Profile, kind: LookKind) {
    if (kind == LookKind.FRAME) {
        val frame = Cosmetic.of(look.frame)
        FramedCard(frame, Modifier.fillMaxSize(), radius = 16.dp) { FramedAvatar(me, 52.dp, frame, Modifier.align(Alignment.Center)) }
    } else if (look.handle.isNotEmpty()) {
        LookName(look, size = 17.sp)
    }
}

/** One item as a card: its [sample], its name and its button. Tapping the card tries it on (or shows it); [tried] outlines it. */
@Composable
private fun ItemCard(name: String, tried: Boolean, onTry: () -> Unit, sample: @Composable () -> Unit, button: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(if (tried) 2.dp else 1.dp, if (tried) Brand.Saffron else MaterialTheme.colorScheme.outlineVariant, shape)
            .selectable(tried, role = Role.RadioButton, onClick = onTry).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) { sample() }
        Text(name, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.padding(top = 8.dp)) { button() }
    }
}

@Composable
private fun Spinner() {
    Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), color = Brand.Saffron, strokeWidth = 2.dp) }
}

/** What I have on (or own), with a tick. */
@Composable
private fun InUse(label: String) {
    SoftButton(label, {}, height = 36.dp, enabled = false, leading = { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Brand.Online) })
}

/** "Wear", or "Wearing" with a tick for what I have on. */
@Composable
private fun WearButton(wearing: Boolean, busy: Boolean, onWear: () -> Unit) {
    when {
        busy -> Spinner()
        wearing -> InUse(stringResource(R.string.looks_wearing))
        else -> SoftButton(stringResource(R.string.looks_wear), onWear, height = 36.dp, outlined = true)
    }
}

/** The price to buy it: Google Play's local price, or the server's in rupees when Play can't say. */
@Composable
private fun PriceButton(item: LookItem, price: String?, busy: Boolean, enabled: Boolean, onBuy: () -> Unit) {
    if (busy) Spinner() else SoftButton(price ?: "₹${item.priceInr}", onBuy, height = 36.dp, accent = true, enabled = enabled)
}

/** The shop's kinds as a row of chips that scrolls sideways (seven don't fit a phone's width): the chosen one in orange. */
@Composable
private fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            // The chosen kind scrolls into view (the shop opened from a chat's stickers starts on a later tab).
            val view = remember { BringIntoViewRequester() }
            LaunchedEffect(on) { if (on) view.bringIntoView() }
            Box(
                Modifier.bringIntoViewRequester(view).height(36.dp).clip(CircleShape).background(if (on) Brand.Saffron else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .selectable(on, role = Role.Tab) { onSelect(i) }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, color = if (on) Brand.OnSaffron else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
