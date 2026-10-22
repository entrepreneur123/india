package com.yaari.chat.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaari.chat.R
import com.yaari.chat.model.Cosmetic
import com.yaari.chat.model.Look
import com.yaari.chat.model.LookItem
import com.yaari.chat.model.LookKind
import com.yaari.chat.model.Profile

// ---- The Looks shop (040): a live preview of my hero card, then Badges · Colours · Fonts · Frames. Tapping a card
// tries it on; what I own is worn with Wear (one per kind, None takes it off); the rest is bought through Google Play
// (the demo: at once). ------------------------------------------------------------------------------------------

private val TABS = listOf(
    LookKind.BADGE to R.string.looks_badges, LookKind.COLOR to R.string.looks_colors,
    LookKind.FONT to R.string.looks_fonts, LookKind.FRAME to R.string.looks_frames,
)

@Composable
fun LooksShopScreen(vm: AppViewModel, s: UiState, me: Profile, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val ui by vm.looks.shop.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    var tab by rememberSaveable { mutableStateOf(LookKind.BADGE) }
    LaunchedEffect(Unit) { vm.looks.loadShop() }
    val shop = ui.shop
    val trying = ui.trying ?: Look(me.id, me.handle.orEmpty())
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
                item(span = { GridItemSpan(maxLineSpan) }) { Preview(me, trying) }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PillTabs(TABS.map { stringResource(it.second) }, TABS.indexOfFirst { it.first == tab }, Modifier.padding(top = 4.dp)) { tab = TABS[it].first }
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
                                CircularProgressIndicator(Modifier.size(28.dp), color = Brand.Pink, strokeWidth = 2.dp)
                            }
                        }
                    }
                    else -> {
                        val worn = shop.look?.of(tab)
                        item(key = "none-${tab.code}") {
                            ItemCard(
                                stringResource(R.string.looks_none), trying.with(tab, null), me, tab, tried = trying.of(tab) == null,
                                onTry = { vm.looks.tryOn(tab, null) },
                            ) { WearButton(worn == null, ui.busy == tab.code) { vm.looks.wear(tab, null) } }
                        }
                        val items = shop.items.filter { it.kind == tab }.mapNotNull { item -> Cosmetic.of(item.id)?.let { item to it } }
                        items(items, key = { it.first.id }) { (item, cosmetic) ->
                            ItemCard(
                                stringResource(cosmetic.label), trying.with(tab, item.id), me, tab, tried = trying.of(tab) == item.id,
                                onTry = { vm.looks.tryOn(tab, item.id) },
                            ) {
                                if (item.id in shop.owned) WearButton(worn == item.id, ui.busy == item.id) { vm.looks.wear(tab, item.id) }
                                else PriceButton(item, s.prices[item.id], ui.busy == item.id, enabled = ui.busy == null) {
                                    vm.looks.tryOn(tab, item.id)
                                    activity?.let { vm.buyLook(it, item.id) }
                                }
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

/** My hero card as everyone would see it with what I'm trying on. */
@Composable
private fun Preview(me: Profile, look: Look) {
    val frame = Cosmetic.of(look.frame)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        Text(
            stringResource(R.string.looks_everyone), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

/**
 * One item as a card: it previews on my @ID (a frame: on a small framed photo), its name, and its button. Tapping the
 * card tries it on in the preview; [tried] outlines it.
 */
@Composable
private fun ItemCard(name: String, look: Look, me: Profile, kind: LookKind, tried: Boolean, onTry: () -> Unit, button: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(if (tried) 2.dp else 1.dp, if (tried) Brand.Pink else MaterialTheme.colorScheme.outlineVariant, shape)
            .selectable(tried, role = Role.RadioButton, onClick = onTry).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
            if (kind == LookKind.FRAME) {
                val frame = Cosmetic.of(look.frame)
                FramedCard(frame, Modifier.fillMaxSize(), radius = 16.dp) {
                    FramedAvatar(me, 52.dp, frame, Modifier.align(Alignment.Center))
                }
            } else if (look.handle.isNotEmpty()) {
                LookName(look, size = 17.sp)
            }
        }
        Text(name, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.padding(top = 8.dp)) { button() }
    }
}

/** "Wear", or "Wearing" with a tick for what I have on. */
@Composable
private fun WearButton(wearing: Boolean, busy: Boolean, onWear: () -> Unit) {
    if (busy) {
        Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), color = Brand.Pink, strokeWidth = 2.dp) }
    } else if (wearing) {
        SoftButton(
            stringResource(R.string.looks_wearing), {}, height = 36.dp, enabled = false,
            leading = { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Brand.Online) },
        )
    } else {
        SoftButton(stringResource(R.string.looks_wear), onWear, height = 36.dp, outlined = true)
    }
}

/** The price to buy it: Google Play's local price, or the server's in rupees when Play can't say. */
@Composable
private fun PriceButton(item: LookItem, price: String?, busy: Boolean, enabled: Boolean, onBuy: () -> Unit) {
    if (busy) {
        Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), color = Brand.Pink, strokeWidth = 2.dp) }
    } else {
        SoftButton(price ?: "₹${item.priceInr}", onBuy, height = 36.dp, accent = true, enabled = enabled)
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
                        .selectable(i == selected, remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(i) }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
