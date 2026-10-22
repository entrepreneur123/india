package com.yaari.chat.domain

import java.util.Locale

/** A country users can sign up from. Must match the server's dial_codes table (migration 007). */
data class Country(val iso: String, val dial: String) {
    /** The name in the phone's language ("India", "भारत", "인도"). */
    fun name(locale: Locale = Locale.getDefault()): String = Locale.Builder().setRegion(iso).build().getDisplayCountry(locale)
    val flag get() = flagOf(iso)
}

object Countries {
    val all: List<Country> = listOf(
        "US" to "1", "EG" to "20", "ZA" to "27", "GR" to "30", "NL" to "31", "BE" to "32", "FR" to "33", "ES" to "34",
        "IT" to "39", "RO" to "40", "CH" to "41", "GB" to "44", "SE" to "46", "NO" to "47", "PL" to "48", "DE" to "49",
        "PE" to "51", "MX" to "52", "AR" to "54", "BR" to "55", "CL" to "56", "CO" to "57", "MY" to "60", "AU" to "61",
        "ID" to "62", "PH" to "63", "NZ" to "64", "SG" to "65", "TH" to "66", "JP" to "81", "KR" to "82", "VN" to "84",
        "TR" to "90", "IN" to "91", "PK" to "92", "LK" to "94", "MA" to "212", "GH" to "233", "NG" to "234", "KE" to "254",
        "PT" to "351", "IE" to "353", "UA" to "380", "HK" to "852", "BD" to "880", "TW" to "886", "KW" to "965",
        "SA" to "966", "AE" to "971", "QA" to "974", "NP" to "977", "CA" to "1",
    ).map { (iso, dial) -> Country(iso, dial) }

    /**
     * The first of [regions] we support (the SIM's country, the network's, then the phone's language region), else
     * the United States (+1, first in the list). Only a pre-selection: the picker lists every country. No regions: the
     * phone's language region.
     */
    fun default(vararg regions: String?): Country =
        (if (regions.isEmpty()) arrayOf(Locale.getDefault().country) else regions)
            .firstNotNullOfOrNull { r -> r?.takeIf { it.isNotBlank() }?.let { byIso(it.uppercase()) } } ?: all.first()

    fun byIso(iso: String) = all.firstOrNull { it.iso == iso }
}

/** 🇮🇳 from "IN": two regional-indicator letters. Empty for anything that is not two letters A-Z. */
fun flagOf(iso: String?): String {
    if (iso == null || iso.length != 2 || !iso.all { it in 'A'..'Z' }) return ""
    return iso.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}
