package com.yaari.chat.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Profile

@Composable
fun OnboardingScreen(vm: AppViewModel, state: Onboarding) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Yaari", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        when (state.step) {
            Onboarding.Step.PHONE -> PhoneStep(vm, state)
            Onboarding.Step.OTP -> OtpStep(vm, state)
            Onboarding.Step.PROFILE -> ProfileStep(vm, state.busy)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PhoneStep(vm: AppViewModel, state: Onboarding) {
    var phone by rememberSaveable { mutableStateOf(state.phone) }
    Text("Meet new people across India", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        phone, { if (it.length <= 14) phone = it }, label = { Text("Mobile number") }, prefix = { Text("+91 ") },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
    )
    Button({ vm.requestOtp(phone) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
        if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Send OTP")
    }
    Text(
        "One account per phone number. Yaari is for adults (18+) only.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OtpStep(vm: AppViewModel, state: Onboarding) {
    var code by rememberSaveable { mutableStateOf("") }
    BackHandler { vm.backToPhone() }
    Text("Enter the 6-digit code sent to +91 ${state.phone}", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        code, { if (it.length <= 6 && it.all(Char::isDigit)) code = it }, label = { Text("OTP") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth(),
    )
    Text("Prototype: the code is ${FakeBackend.DEMO_OTP}", style = MaterialTheme.typography.bodySmall)
    Button({ vm.verifyOtp(code) }, enabled = code.length == 6 && !state.busy, modifier = Modifier.fillMaxWidth()) {
        if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Verify")
    }
    TextButton({ vm.backToPhone() }) { Text("Change number") }
}

@Composable
private fun ProfileStep(vm: AppViewModel, busy: Boolean) {
    // Plain remember: a content:// grant from the photo picker does not survive process death anyway.
    var d by remember { mutableStateOf(ProfileDraft()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) d = d.copy(photoUri = uri.toString())
    }
    val preview = Profile("draft", d.name.ifBlank { "?" }, d.gender ?: Gender.OTHER, 0, "", "", d.photoUri)

    Text("Create your profile", style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(preview, 84.dp, Modifier.clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
        Spacer(Modifier.padding(8.dp))
        TextButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Text(if (d.photoUri == null) "Add profile photo" else "Change photo")
        }
    }
    OutlinedTextField(d.name, { d = d.copy(name = it.take(30)) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("Gender")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Gender.entries.forEach { g ->
            FilterChip(d.gender == g, { d = d.copy(gender = g) }, label = { Text(g.label) })
        }
    }
    Text("Date of birth")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val num = KeyboardOptions(keyboardType = KeyboardType.Number)
        OutlinedTextField(d.day, { d = d.copy(day = it.filter(Char::isDigit).take(2)) }, label = { Text("DD") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
        OutlinedTextField(d.month, { d = d.copy(month = it.filter(Char::isDigit).take(2)) }, label = { Text("MM") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
        OutlinedTextField(d.year, { d = d.copy(year = it.filter(Char::isDigit).take(4)) }, label = { Text("YYYY") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1.4f))
    }
    OutlinedTextField(d.city, { d = d.copy(city = it.take(30)) }, label = { Text("City") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(d.bio, { d = d.copy(bio = it.take(150)) }, label = { Text("Bio") }, minLines = 2, modifier = Modifier.fillMaxWidth())
    Row(
        Modifier.toggleable(d.agreed, role = Role.Checkbox) { d = d.copy(agreed = it) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(d.agreed, onCheckedChange = null)
        Text(
            "I am 18 or older and agree to the Community Rules: no nudity, no minors, no harassment. Breaking them means a permanent ban.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Button({ vm.createProfile(d) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Start")
    }
}
