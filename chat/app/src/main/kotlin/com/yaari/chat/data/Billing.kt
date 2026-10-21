package com.yaari.chat.data

import android.app.Activity

/** Google Play Billing in `live` builds; [DemoBilling] grants instantly in `demo` builds and tests. */
interface Billing {
    /** Localized price per product id (e.g. "₹149.00"), empty if the store is unreachable. */
    suspend fun prices(): Map<String, String>
    /** Shows the Play purchase sheet. Returns the purchase token, or null if cancelled. */
    suspend fun buy(activity: Activity, productId: String, accountId: String): String?
    /** After the server granted it: consumes one-time items so they can be bought again. */
    suspend fun finish(productId: String, token: String)
    /** Active purchases (product id to token) to re-verify on start. */
    suspend fun ownedPurchases(): List<Pair<String, String>>
}

class DemoBilling : Billing {
    override suspend fun prices() = mapOf(Products.PREMIUM to "₹149 / month (demo)", Products.GENDER_PASS to "₹29 / 24 h (demo)")
    override suspend fun buy(activity: Activity, productId: String, accountId: String) = "demo-$productId"
    override suspend fun finish(productId: String, token: String) = Unit
    override suspend fun ownedPurchases() = emptyList<Pair<String, String>>()
}
