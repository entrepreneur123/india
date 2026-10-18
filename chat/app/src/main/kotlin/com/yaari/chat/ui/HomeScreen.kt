package com.yaari.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yaari.chat.domain.ExitPolicy
import com.yaari.chat.model.Target
import androidx.activity.compose.BackHandler
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(vm: AppViewModel, s: UiState) {
    var composing by rememberSaveable { mutableStateOf(false) }
    if (composing) {
        NewPostScreen(onCancel = { composing = false }) { text, uri ->
            vm.addPost(text, uri)
            composing = false
        }
        return
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(s.tab == Tab.CHAT, { vm.selectTab(Tab.CHAT) }, { Icon(Icons.Filled.Home, null) }, label = { Text("Chat") })
                NavigationBarItem(s.tab == Tab.RECENT, { vm.selectTab(Tab.RECENT) }, { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("Recent") })
                NavigationBarItem(s.tab == Tab.ME, { vm.selectTab(Tab.ME) }, { Icon(Icons.Filled.Person, null) }, label = { Text("Me") })
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (s.tab) {
                Tab.CHAT -> Lobby(vm, s)
                Tab.RECENT -> Recents(vm, s)
                Tab.ME -> s.me?.let { me ->
                    ProfileContent(me, isMe = true, premium = s.premium, onPremium = vm::setPremium, onNewPost = { composing = true })
                }
            }
        }
    }
}

@Composable
private fun Lobby(vm: AppViewModel, s: UiState) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Meet someone new", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Who do you want to meet?", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Target.entries.forEach { t ->
                FilterChip(
                    s.target == t, { vm.setTarget(t) }, label = { Text(t.label) },
                    leadingIcon = if (t.isPaid && !s.premium) ({ Icon(Icons.Filled.Lock, "Premium", Modifier.height(16.dp)) }) else null,
                )
            }
        }
        Button({ vm.startMatching() }, Modifier.fillMaxWidth().height(56.dp)) { Text("Start chatting", style = MaterialTheme.typography.titleMedium) }
        Text(
            "Be kind. Nudity, harassment or minors = permanent ban.\nYou can always report & leave instantly.",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!s.premium) {
            Card(Modifier.fillMaxWidth().clickable { vm.showPaywall("Yaari Premium") }) {
                Column(Modifier.padding(16.dp)) {
                    Text("Yaari Premium", fontWeight = FontWeight.Bold)
                    Text("Choose gender · Video calls · Skip in ${ExitPolicy.PREMIUM_LOCK_MS / 1000.0}s", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun MatchingScreen(vm: AppViewModel, target: Target) {
    BackHandler { vm.cancelMatching() }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text("Finding someone for you…", style = MaterialTheme.typography.titleMedium)
        Text("Looking for: ${target.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton({ vm.cancelMatching() }) { Text("Cancel") }
    }
}

@Composable
private fun Recents(vm: AppViewModel, s: UiState) {
    if (s.recents.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("People you meet will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
    LazyColumn(Modifier.fillMaxSize()) {
        items(s.recents, key = { it.partner.id }) { r ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.openProfile(r.partner) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avatar(r.partner, 52.dp)
                Column(Modifier.weight(1f)) {
                    Text(r.partner.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        r.dm.lastOrNull()?.text ?: "Met at ${fmt.format(Date(r.metAt))} · ${r.partner.subtitle()}",
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton({ vm.openDm(r.partner.id) }) { Text("Message") }
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun PremiumRow(premium: Boolean, onPremium: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Premium (prototype switch)", fontWeight = FontWeight.SemiBold)
            Text("Replaced by Google Play Billing in v1", style = MaterialTheme.typography.bodySmall)
        }
        Switch(premium, onPremium)
    }
}
