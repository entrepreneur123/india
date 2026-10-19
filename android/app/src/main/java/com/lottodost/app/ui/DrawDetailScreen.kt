package com.lottodost.app.ui

import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottodost.app.R
import com.lottodost.app.data.Draw
import com.lottodost.app.data.Tier

@Composable
fun tierTitle(rank: String): String = when (rank) {
    "CONS" -> stringResource(R.string.tier_cons)
    "1" -> stringResource(R.string.tier_1)
    "2" -> stringResource(R.string.tier_2)
    "3" -> stringResource(R.string.tier_3)
    "4" -> stringResource(R.string.tier_4)
    "5" -> stringResource(R.string.tier_5)
    else -> stringResource(R.string.tier_n, rank.toIntOrNull() ?: 0)
}

@Composable
fun tierTitle(tier: Tier): String = tierTitle(tier.rank)

@Composable
fun matchHint(tier: Tier): String? = when (tier.match) {
    "LAST4" -> stringResource(R.string.match_last4)
    "LAST5" -> stringResource(R.string.match_last5)
    "CONS" -> stringResource(R.string.match_cons)
    else -> null
}

/** Share bar: the installed messaging apps first (their own icons), then SMS and the system sheet. */
@Composable
private fun ShareRow(onApp: (String) -> Unit, onSms: () -> Unit, onMore: () -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { ShareTargets.installed(ctx) }
    Eyebrow(stringResource(R.string.share_title))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(apps, key = { it.pkg }) { app ->
            ShareButton(app.label, onClick = { onApp(app.pkg) }) {
                if (app.icon != null) androidx.compose.foundation.Image(app.icon.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.size(40.dp))
            }
        }
        item { ShareButton(stringResource(R.string.share_sms), onClick = onSms) { ShareGlyph("SMS") } }
        item { ShareButton(stringResource(R.string.share_more), onClick = onMore) {
            Icon(painterResource(R.drawable.ic_share), contentDescription = null, modifier = Modifier.size(28.dp)) } }
    }
}

@Composable
private fun ShareButton(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        Modifier.width(72.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { icon() }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ShareGlyph(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** One prize tier as on the sheet: title and amount on a rule, then the numbers in plain columns. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TierBlock(tier: Tier, q: String) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tierTitle(tier), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(formatRupees(tier.prize), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        matchHint(tier)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (!tier.confirmed) Text(stringResource(R.string.tier_unconfirmed), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(top = 4.dp).background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 4.dp))
        val cell = when (tier.numbers.maxOfOrNull { it.length } ?: 4) { in 0..4 -> 52.dp; 5 -> 62.dp; in 6..8 -> 100.dp; else -> 112.dp }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(top = 8.dp)) {
            tier.numbers.forEach { n ->
                // Same rules as Matcher: last-4 tiers compare 4 digits, others need the full digits.
                val hit = when (tier.match) {
                    "LAST4" -> q.length >= 4 && n == q.takeLast(4)
                    "LAST5" -> q.length >= 5 && n == q.takeLast(5)
                    "CONS" -> q.length == 5 && n == q
                    else -> q.length >= 5 && n.filter { it in '0'..'9' }.endsWith(q)
                }
                Text(
                    n, fontFamily = FontFamily.Monospace, fontSize = 17.sp,
                    fontWeight = if (hit) FontWeight.Black else FontWeight.Medium,
                    color = if (hit) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.widthIn(min = cell)
                        .then(if (hit) Modifier.background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.extraSmall) else Modifier)
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawDetailScreen(draw: Draw, onBack: () -> Unit, onOpenSheet: (Draw, Boolean) -> Unit = { _, _ -> }) {
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.filter { it in '0'..'9' }
    val footer = stringResource(R.string.share_footer)
    val drawNoFmt = stringResource(R.string.draw_no)
    val stateLabel = stringResource(R.string.state_lottery, stateName(draw.state))
    val dateTime = formatDate(draw.date) + " · " + formatTime(draw.time)
    val titles = draw.tiers.associate { it.rank to tierTitle(it) }
    val shareText = buildList {
        add("${draw.name} — $dateTime")
        draw.tiers.forEach { add("${titles[it.rank]} (${formatRupees(it.prize)}): ${it.numbers.joinToString(" ")}") }
        add(footer)
    }.joinToString("\n")

    val scope = rememberCoroutineScope()
    val webLink = stringResource(R.string.share_short, draw.name.removeSuffix(" WEEKLY LOTTERY"), dateTime,
        draw.firstPrize.orEmpty(), WEB_URL)
    /** Renders the sheet image off the main thread, then hands it to [pkg] (null: system share sheet). */
    fun shareImage(pkg: String?) {
        scope.launch {
            val uri = withContext(Dispatchers.Default) {
                val bmp = ShareImage.render(draw, { titles[it] ?: it }, footer, dateTime, stateLabel) {
                    String.format(java.util.Locale.ROOT, drawNoFmt, it)
                }
                ShareTargets.imageUri(ctx, bmp, "${draw.id}.png").also { bmp.recycle() }
            }
            ShareTargets.sendImage(ctx, uri, shareText, pkg)
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(dateTime) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
            },
            actions = {
                IconButton(onClick = { shareText(ctx, shareText) }) {
                    Icon(painterResource(R.drawable.ic_share), stringResource(R.string.share_text))
                }
            },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                // Laid out like the printed result sheet people know: title band, then the 1st prize big.
                Slip {
                    val band = if (isDark()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
                    val onBand = if (isDark()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
                    Surface(color = band, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(draw.name, color = onBand, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            // A price we have not verified (0) is left out rather than shown as ₹0.
                            Text(when {
                                draw.price <= 0 -> stateLabel
                                draw.drawNo != null -> stringResource(R.string.draw_meta, stateName(draw.state), draw.drawNo.toString(), draw.price)
                                else -> stringResource(R.string.draw_meta_special, stateName(draw.state), draw.price)
                            }, color = onBand, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    draw.tiers.firstOrNull()?.let { first ->
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Eyebrow(tierTitle(first) + " · " + formatRupees(first.prize), Modifier.weight(1f))
                                if (draw.verified) VerifiedMark()
                            }
                            val hit = q.length >= 5 && first.numbers.firstOrNull().orEmpty().filter { it in '0'..'9' }.endsWith(q)
                            FitNumber(first.numbers.firstOrNull().orEmpty(), 40.sp,
                                if (hit) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                when {
                    draw.ocr -> Banner(stringResource(R.string.ocr_caution))
                    draw.verified -> Banner(stringResource(R.string.verified_long))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShareRow(onApp = { shareImage(it) }, onSms = { ShareTargets.sendSms(ctx, webLink) }, onMore = { shareImage(null) })
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(6) },
                        label = { Text(stringResource(R.string.find_in_result)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Slip {
                    draw.tiers.drop(1).forEachIndexed { i, tier ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TierBlock(tier, q)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    draw.sheetUrl?.let {
                        Button(onClick = { onOpenSheet(draw, false) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.open_sheet))
                        }
                    }
                    draw.officialPdf?.let { url ->
                        OutlinedButton(onClick = { onOpenSheet(draw, true) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.open_official_pdf))
                        }
                        TextButton(onClick = { downloadPdf(ctx, url, (draw.id.takeIf(com.lottodost.app.domain.Trust::isId) ?: "result") + ".pdf", draw.name) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.save_pdf))
                        }
                    }
                    draw.officialSite?.let { url ->
                        OutlinedButton(onClick = { openUrl(ctx, url) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.open_kerala_official))
                        }
                        Text(stringResource(R.string.kerala_source_note), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(if (draw.state == "Nagaland") R.string.claim_info else R.string.claim_other_state),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
