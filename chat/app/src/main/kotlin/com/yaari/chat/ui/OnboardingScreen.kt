package com.yaari.chat.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yaari.chat.R
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.domain.Country
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Profile

@Composable
fun OnboardingScreen(vm: AppViewModel, state: Onboarding) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Wordmark(size = 40)
        when (state.step) {
            Onboarding.Step.PHONE -> PhoneStep(vm, state)
            Onboarding.Step.OTP -> OtpStep(vm, state)
            Onboarding.Step.PROFILE -> ProfileStep(vm, state.busy)
        }
        state.error?.let { Text(it.text(), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PhoneStep(vm: AppViewModel, state: Onboarding) {
    var phone by rememberSaveable { mutableStateOf("") }
    var iso by rememberSaveable { mutableStateOf(Countries.default().iso) }
    var picking by remember { mutableStateOf(false) }
    val country = Countries.byIso(iso) ?: Countries.default()
    Text(stringResource(R.string.tagline), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.tagline_sub), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    // Bottom-aligned: the text field reserves room above its box for the floating label.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        Row(
            Modifier.height(56.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .clickable(role = Role.DropdownList) { picking = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${country.flag} +${country.dial}", style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Rounded.ArrowDropDown, stringResource(R.string.country))
        }
        Field(
            phone, { if (it.length <= 16) phone = it }, stringResource(R.string.mobile_number), Modifier.weight(1f),
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
    }
    GradientButton(stringResource(R.string.send_otp), { vm.requestOtp(country, phone) }, enabled = !state.busy && phone.isNotBlank())
    Text(stringResource(R.string.phone_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (picking) CountrySheet(onDismiss = { picking = false }) { iso = it.iso; picking = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountrySheet(onDismiss: () -> Unit, onPick: (Country) -> Unit) {
    var query by remember { mutableStateOf("") }
    val sorted = remember { Countries.all.sortedBy { it.name() } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Field(query, { query = it }, stringResource(R.string.search_country))
            LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                items(sorted.filter { query.isBlank() || it.name().contains(query.trim(), ignoreCase = true) || it.dial.startsWith(query.trim().removePrefix("+")) }) { c ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 14.dp, horizontal = 4.dp)) {
                        Text("${c.flag}  ${c.name()}", Modifier.weight(1f))
                        Text("+${c.dial}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun OtpStep(vm: AppViewModel, state: Onboarding) {
    var code by rememberSaveable { mutableStateOf("") }
    BackHandler { vm.backToPhone() }
    Text(stringResource(R.string.otp_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.otp_prompt, "+" + state.phone), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Field(
        code, { if (it.length <= 6 && it.all(Char::isDigit)) code = it }, stringResource(R.string.otp_label),
        keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
    Text(stringResource(R.string.demo_otp, FakeBackend.DEMO_OTP), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    GradientButton(stringResource(R.string.verify), { vm.verifyOtp(code) }, enabled = code.length == 6 && !state.busy)
    TextButton({ vm.backToPhone() }) { Text(stringResource(R.string.change_number), color = MaterialTheme.colorScheme.onSurface) }
}

@Composable
private fun ProfileStep(vm: AppViewModel, busy: Boolean) {
    // Plain remember: a content:// grant from the photo picker does not survive process death anyway.
    var d by remember { mutableStateOf(ProfileDraft()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) d = d.copy(photoUri = uri.toString())
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    val preview = Profile("draft", d.name.ifBlank { "?" }, d.gender ?: Gender.OTHER, 0, "", "", d.photoUri)

    Text(stringResource(R.string.create_profile), style = MaterialTheme.typography.headlineMedium)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clip(CircleShape).clickable { pick() }) {
            if (d.photoUri == null) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.CameraAlt, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Avatar(preview, 96.dp, ring = true)
            }
        }
        TextButton(::pick) {
            Text(stringResource(if (d.photoUri == null) R.string.add_profile_photo else R.string.change_photo), fontWeight = FontWeight.Bold)
        }
    }
    Field(d.name, { d = d.copy(name = it.take(30)) }, stringResource(R.string.name))
    Text(stringResource(R.string.gender), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Gender.entries.forEach { g ->
            val selected = d.gender == g
            Box(
                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(role = Role.RadioButton) { d = d.copy(gender = g) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(g.label), style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    Text(stringResource(R.string.date_of_birth), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val num = KeyboardOptions(keyboardType = KeyboardType.Number)
        Field(d.day, { d = d.copy(day = it.filter(Char::isDigit).take(2)) }, "DD", Modifier.weight(1f), keyboard = num)
        Field(d.month, { d = d.copy(month = it.filter(Char::isDigit).take(2)) }, "MM", Modifier.weight(1f), keyboard = num)
        Field(d.year, { d = d.copy(year = it.filter(Char::isDigit).take(4)) }, "YYYY", Modifier.weight(1.4f), keyboard = num)
    }
    Field(d.city, { d = d.copy(city = it.take(30)) }, stringResource(R.string.city))
    Field(d.bio, { d = d.copy(bio = it.take(150)) }, stringResource(R.string.bio), single = false)
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).toggleable(d.agreed, role = Role.Checkbox) { d = d.copy(agreed = it) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(d.agreed, onCheckedChange = null)
        Text(stringResource(R.string.consent), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
    }
    GradientButton(stringResource(R.string.start), { vm.createProfile(d) }, enabled = !busy)
}
