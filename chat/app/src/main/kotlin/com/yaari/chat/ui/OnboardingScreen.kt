package com.yaari.chat.ui

import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import android.telephony.TelephonyManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaari.chat.R
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.domain.Country
import com.yaari.chat.domain.Handle
import com.yaari.chat.domain.Validation
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Profile
import kotlin.random.Random

@Composable
fun OnboardingScreen(vm: AppViewModel, state: Onboarding) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state.step) {
            Onboarding.Step.PHONE -> PhoneStep(vm, state)
            Onboarding.Step.OTP -> OtpStep(vm, state)
            Onboarding.Step.PROFILE -> ProfileStep(vm, state.busy)
        }
        state.error?.let { Text(it.text(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium)

@Composable
private fun Lead(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)

/**
 * The country the number most likely has: the SIM's, else the mobile network's, else the phone's language region
 * (people often keep the phone in English (US) wherever they live, so the SIM comes first).
 */
private fun defaultCountry(context: Context): Country {
    val tm = runCatching { context.getSystemService(TelephonyManager::class.java) }.getOrNull()
    return Countries.default(
        runCatching { tm?.simCountryIso }.getOrNull(), runCatching { tm?.networkCountryIso }.getOrNull(), Locale.getDefault().country,
    )
}

@Composable
private fun PhoneStep(vm: AppViewModel, state: Onboarding) {
    var phone by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    var iso by rememberSaveable { mutableStateOf(defaultCountry(context).iso) }
    var picking by remember { mutableStateOf(false) }
    val country = Countries.byIso(iso) ?: defaultCountry(context)
    Wordmark(size = 28)
    Spacer(Modifier.height(16.dp))
    Title(stringResource(R.string.phone_title))
    Lead(stringResource(R.string.phone_sub))
    Spacer(Modifier.height(8.dp))
    // Bottom-aligned: the text field reserves room above its box for the floating label.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        val interaction = remember { MutableInteractionSource() }
        Row(
            Modifier.height(56.dp).clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable(interaction, indication = ripple(), role = Role.DropdownList) { picking = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(country.flag, fontSize = 22.sp)
            Text("+${country.dial}", style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.country), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Field(
            phone, { if (it.length <= 16) phone = it }, stringResource(R.string.mobile_number), Modifier.weight(1f),
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
    }
    Text(stringResource(R.string.phone_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    PrimaryButton(stringResource(R.string.send_otp), { vm.requestOtp(country, phone) }, enabled = !state.busy && phone.isNotBlank())
    if (picking) CountrySheet(onDismiss = { picking = false }) { iso = it.iso; picking = false }
}

/**
 * The countries with a search field: one tap picks one (sign-up). With [chosen], a multi-choice instead (the lobby's
 * countries to meet): ticks in place of the dial codes, at most [max], and [top] / [bottom] around the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CountrySheet(
    onDismiss: () -> Unit, chosen: Set<String>? = null, max: Int = Int.MAX_VALUE,
    top: @Composable () -> Unit = {}, bottom: @Composable () -> Unit = {}, onPick: (Country) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // A to Z; in a multi-choice the ones already chosen first (they don't jump while ticking).
    val sorted = remember { Countries.all.sortedBy { it.name() }.sortedByDescending { chosen?.contains(it.iso) == true } }
    // A multi-choice opens fully, so its buttons under the list are on screen.
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = chosen != null)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            top()
            Field(query, { query = it }, stringResource(R.string.search_country))
            LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                items(sorted.filter { query.isBlank() || it.name().contains(query.trim(), ignoreCase = true) || it.dial.startsWith(query.trim().removePrefix("+")) }) { c ->
                    val on = chosen != null && c.iso in chosen
                    val enabled = chosen == null || on || chosen.size < max
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled) { onPick(c) }
                            .alpha(if (enabled) 1f else 0.4f).padding(vertical = if (chosen == null) 16.dp else 4.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.flag, fontSize = 20.sp)
                        Text(c.name(), Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                        if (chosen == null) Text("+${c.dial}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Checkbox(on, onCheckedChange = null, Modifier.padding(12.dp), colors = CheckboxDefaults.colors(checkedColor = Brand.Saffron))
                    }
                }
            }
            bottom()
        }
    }
}

@Composable
private fun OtpStep(vm: AppViewModel, state: Onboarding) {
    var code by rememberSaveable { mutableStateOf("") }
    BackHandler { vm.backToPhone() }
    CircleIconButton(
        Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.change_number), vm::backToPhone,
        Modifier.offset(x = (-12).dp),
    )
    Title(stringResource(R.string.otp_title))
    Lead(stringResource(R.string.otp_prompt, "+" + state.phone))
    Spacer(Modifier.height(10.dp))
    OtpBoxes(code, stringResource(R.string.otp_label)) { if (it.length <= 6 && it.all(Char::isDigit)) code = it }
    Text(stringResource(R.string.demo_otp, FakeBackend.DEMO_OTP), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        stringResource(R.string.change_number), color = MaterialTheme.colorScheme.primaryText, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { vm.backToPhone() }.padding(vertical = 14.dp),
    )
    Spacer(Modifier.height(8.dp))
    PrimaryButton(stringResource(R.string.verify), { vm.verifyOtp(code) }, enabled = code.length == 6 && !state.busy)
}

/** One hidden text field drawn as six boxes; the next empty box carries the brand focus ring. */
@Composable
private fun OtpBoxes(code: String, label: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicTextField(
        code, onChange, Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = label },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true,
        decorationBox = { _ ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { i ->
                    val digit = code.getOrNull(i)
                    val focused = i == code.length
                    val border by animateColorAsState(
                        when {
                            focused -> Brand.Saffron
                            else -> MaterialTheme.colorScheme.outline
                        },
                        label = "otp",
                    )
                    Box(
                        Modifier.weight(1f).height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(if (focused) 2.dp else 1.dp, border, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (digit != null) Text("$digit", style = MaterialTheme.typography.headlineSmall)
                        else if (focused) {
                            val blink by rememberLoop(1000, from = 1f, to = 0f, still = 1f)
                            Box(Modifier.size(2.dp, 28.dp).clip(CircleShape).background(Brand.Saffron.copy(alpha = if (blink > 0.5f) 1f else 0f)))
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ProfileStep(vm: AppViewModel, busy: Boolean) {
    // Plain remember: a content:// grant from the photo picker does not survive process death anyway.
    var d by remember { mutableStateOf(ProfileDraft()) }
    // The ID follows the name (a suggestion with 4 digits) until it is typed in itself; it is checked as it changes.
    val number = remember { Random.nextInt(10_000) }
    var ownHandle by remember { mutableStateOf(false) }
    val field by vm.looks.handle.collectAsStateWithLifecycle()
    fun handle(h: String) {
        d = d.copy(handle = h)
        vm.looks.checkHandle(h, null)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) d = d.copy(photoUri = uri.toString())
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    val preview = Profile("draft", d.name.ifBlank { "?" }, d.gender ?: Gender.OTHER, 0, "", "", d.photoUri)

    Row(verticalAlignment = Alignment.CenterVertically) {
        val pickLabel = stringResource(if (d.photoUri == null) R.string.add_profile_photo else R.string.change_photo)
        Box(Modifier.clip(CircleShape).clickable(role = Role.Button, onClickLabel = pickLabel) { pick() }.semantics { contentDescription = pickLabel }) {
            if (d.photoUri == null) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.CameraAlt, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Avatar(preview, 84.dp)
            }
        }
        Box(
            Modifier.offset(x = (-30).dp, y = 28.dp).size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(3.dp)
                .clip(CircleShape).background(MaterialTheme.colorScheme.onSurface),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.CameraAlt, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.surface) }
        Column(Modifier.offset(x = (-14).dp)) {
            Text(stringResource(R.string.create_profile), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.create_profile_sub), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Field(d.name, { name ->
        d = d.copy(name = name.take(30))
        if (!ownHandle) handle(if (name.isBlank()) "" else Handle.suggest(name, number))
    }, stringResource(R.string.name))
    HandleInput(d.handle, { ownHandle = true; handle(it) }, field, vm.looks.problem(field?.takeIf { it.text == d.handle })?.text())
    // Fixed after sign-up (the server enforces it too), so they sit together in their own panel.
    Panel {
        FieldLabel(stringResource(R.string.gender))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Gender.entries.forEach { g ->
                val selected = d.gender == g
                val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, label = "g")
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier.weight(1f).height(48.dp).clip(CircleShape).background(bg)
                        .border(1.dp, if (selected) Brand.Saffron else MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable(interaction, indication = ripple(), role = Role.RadioButton) { d = d.copy(gender = g) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(g.label), style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        FieldLabel(stringResource(R.string.date_of_birth))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val num = KeyboardOptions(keyboardType = KeyboardType.Number)
            Field(d.day, { d = d.copy(day = it.filter(Char::isDigit).take(2)) }, "DD", Modifier.weight(1f), keyboard = num)
            Field(d.month, { d = d.copy(month = it.filter(Char::isDigit).take(2)) }, "MM", Modifier.weight(1f), keyboard = num)
            Field(d.year, { d = d.copy(year = it.filter(Char::isDigit).take(4)) }, "YYYY", Modifier.weight(1.4f), keyboard = num)
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Lock, null, Modifier.padding(top = 1.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(R.string.locked_fields), Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Field(d.city, { d = d.copy(city = it.take(30)) }, stringResource(R.string.city))
    Field(d.bio, { d = d.copy(bio = it.take(Validation.MAX_BIO)) }, stringResource(R.string.bio), single = false, counter = "${d.bio.length}/${Validation.MAX_BIO}")
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).toggleable(d.agreed, role = Role.Checkbox) { d = d.copy(agreed = it) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(d.agreed, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Brand.Saffron))
        Text(stringResource(R.string.consent), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
    }
    PrimaryButton(stringResource(R.string.start), { vm.createProfile(d) }, enabled = !busy)
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content,
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
