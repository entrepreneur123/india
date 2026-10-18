package com.lottodost.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DrawDetailScreen(draw: Draw, onBack: () -> Unit) {
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

    fun shareImage() {
        val bmp = ShareImage.render(draw, { titles[it] ?: it }, footer, dateTime, stateLabel) { String.format(java.util.Locale.ROOT, drawNoFmt, it) }
        ShareImage.share(ctx, bmp, "${draw.id}.png", "${draw.name} — $dateTime\n$footer")
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
                Text(draw.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(draw.drawNo?.let { stringResource(R.string.draw_meta, stateName(draw.state), it.toString(), draw.price) }
                    ?: stringResource(R.string.draw_meta_special, stateName(draw.state), draw.price),
                    style = MaterialTheme.typography.bodyMedium)
                when {
                    draw.ocr -> Banner(stringResource(R.string.ocr_caution))
                    draw.verified -> Banner(stringResource(R.string.verified_long))
                }
                Button(onClick = ::shareImage, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                    Text(stringResource(R.string.share_whatsapp))
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(6) },
                    label = { Text(stringResource(R.string.find_in_result)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(draw.tiers, key = { it.rank }) { tier ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row {
                            Text(tierTitle(tier), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(formatRupees(tier.prize), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        matchHint(tier)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 8.dp)) {
                            val big = tier.match == "FULL" || tier.match == "EXACT"
                            tier.numbers.forEach { n ->
                                // Same rules as Matcher: last-4 tiers compare 4 digits, others need the full digits.
                                val hit = when (tier.match) {
                                    "LAST4" -> q.length >= 4 && n == q.takeLast(4)
                                    "LAST5" -> q.length >= 5 && n == q.takeLast(5)
                                    "CONS" -> q.length == 5 && n == q
                                    else -> q.length >= 5 && n.filter { it in '0'..'9' }.endsWith(q)
                                }
                                Surface(
                                    color = if (hit) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Text(n, fontFamily = FontFamily.Monospace, fontSize = if (big) 24.sp else 17.sp,
                                        fontWeight = if (hit || big) FontWeight.Black else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                }
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    draw.officialPdf?.let { url ->
                        OutlinedButton(onClick = { openUrl(ctx, url) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.open_official_pdf))
                        }
                    }
                    draw.sheetUrl?.let { url ->
                        OutlinedButton(onClick = { openUrl(ctx, url) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.open_sheet))
                        }
                    }
                    Text(stringResource(if (draw.state == "Nagaland") R.string.claim_info else R.string.claim_other_state),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
