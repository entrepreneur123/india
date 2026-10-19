package com.yaari.chat.live

import android.app.Activity
import android.app.Application
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.yaari.chat.data.Billing
import com.yaari.chat.data.Products
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Google Play Billing. The app never grants anything itself: tokens go to the server (verify-purchase). */
class PlayBilling(app: Application) : Billing {
    /** The product a purchase sheet is open for, and who waits for its token. */
    private var pending: Pair<String, CompletableDeferred<Purchase?>>? = null
    /** Completed purchases nobody was waiting for (e.g. a UPI payment that finished later). */
    private val unclaimed = mutableListOf<Purchase>()

    private val listener = PurchasesUpdatedListener { result, purchases ->
        val done = purchases.orEmpty().filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val waiting = pending
        var delivered = false
        for (p in done) {
            if (!delivered && waiting != null && waiting.first in p.products) {
                waiting.second.complete(p)
                delivered = true
            } else {
                synchronized(unclaimed) { unclaimed += p }
            }
        }
        // Cancelled, failed or still pending: the sheet is closed. A pending payment arrives later as "unclaimed".
        if (!delivered) waiting?.second?.complete(null)
    }

    private val client = BillingClient.newBuilder(app)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private suspend fun ready(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    private suspend fun details(): List<ProductDetails> {
        if (!ready()) return emptyList()
        fun query(id: String, type: String) = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()),
        ).build()
        val subs = client.queryProductDetails(query(Products.PREMIUM, BillingClient.ProductType.SUBS)).productDetailsList.orEmpty()
        val pass = client.queryProductDetails(query(Products.GENDER_PASS, BillingClient.ProductType.INAPP)).productDetailsList.orEmpty()
        return subs + pass
    }

    override suspend fun prices(): Map<String, String> = details().associate { d ->
        d.productId to (
            d.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
                ?: d.oneTimePurchaseOfferDetails?.formattedPrice ?: ""
            )
    }.filterValues { it.isNotEmpty() }

    override suspend fun buy(activity: Activity, productId: String, accountId: String): String? {
        val d = details().firstOrNull { it.productId == productId } ?: return null
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).apply {
            d.subscriptionOfferDetails?.firstOrNull()?.offerToken?.let { setOfferToken(it) }
        }.build()
        val deferred = CompletableDeferred<Purchase?>().also { pending = productId to it }
        val launched = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).setObfuscatedAccountId(accountId).build(),
        )
        if (launched.responseCode != BillingClient.BillingResponseCode.OK) return null
        return try {
            deferred.await()?.purchaseToken
        } finally {
            pending = null
        }
    }

    override suspend fun finish(productId: String, token: String) {
        // Subscriptions are acknowledged by the server; the one-time pass is consumed so it can be bought again.
        if (productId == Products.GENDER_PASS && ready()) {
            client.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(token).build())
        }
    }

    /** Active subscriptions, unconsumed passes (paid but not yet redeemed) and late-arriving purchases. */
    override suspend fun ownedPurchases(): List<Pair<String, String>> {
        val late = synchronized(unclaimed) { unclaimed.toList().also { unclaimed.clear() } }
        if (!ready()) return late.flatMap { p -> p.products.map { it to p.purchaseToken } }
        val owned = listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP).flatMap { type ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()).purchasesList
        }
        return (owned + late).filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .distinctBy { it.purchaseToken }
            .flatMap { p -> p.products.map { it to p.purchaseToken } }
    }
}
