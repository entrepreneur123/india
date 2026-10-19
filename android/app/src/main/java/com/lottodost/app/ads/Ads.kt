package com.lottodost.app.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.lottodost.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob, kept quiet: one banner above the tab bar, and at most a few full-screen ads at natural
 * breaks (leaving a result), never at launch, never from a notification, never while a result is
 * loading. Competitor apps lose stars for ad overload; the numbers stay readable without ads.
 * Ads stay off until AdMob ids are configured (gradle properties ADMOB_*), and until consent is
 * settled (Google's UMP: a form only where the law asks for one, e.g. EEA/UK).
 */
object Ads {
    private val initCalled = AtomicBoolean(false)
    var ready by mutableStateOf(false)
        private set

    val configured: Boolean get() = BuildConfig.ADMOB_BANNER.isNotEmpty()

    fun start(activity: Activity) {
        if (!configured) return
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                if (consent.canRequestAds()) init(activity.applicationContext)
            }
        }, { _ -> if (consent.canRequestAds()) init(activity.applicationContext) })
        // A previous session's answer is enough to start now.
        if (consent.canRequestAds()) init(activity.applicationContext)
    }

    private fun init(app: Context) {
        if (initCalled.getAndSet(true)) return
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(app) {}  // off the main thread, as Google recommends (ANRs)
            withContext(Dispatchers.Main) {
                ready = true
                Interstitials.preload(app)
            }
        }
    }
}

/** Anchored adaptive banner; reserves its height so the screen does not jump when the ad arrives. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    if (!Ads.ready) return
    val width = LocalConfiguration.current.screenWidthDp
    val ctx = LocalContext.current
    val size = remember(width) { AdSize.getLargeAnchoredAdaptiveBannerAdSize(ctx, width) }
    Box(modifier.fillMaxWidth().height(size.height.dp)) {
        AndroidView(
            factory = { c ->
                AdView(c).apply {
                    adUnitId = BuildConfig.ADMOB_BANNER
                    setAdSize(size)
                    loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Full-screen ads with hard limits: not in the first 2 minutes, 4 minutes apart, 3 per session. */
object Interstitials {
    private const val FIRST_AFTER_MS = 2 * 60_000L
    private const val GAP_MS = 4 * 60_000L
    private const val PER_SESSION = 3

    private var ad: InterstitialAd? = null
    private var loading = false
    private var sessionStart = 0L  // set when ads start (first preload)
    private var lastShown = 0L
    private var shown = 0

    /** Pure rule, unit-tested. */
    fun allowed(now: Long, start: Long, last: Long, count: Int): Boolean =
        now - start >= FIRST_AFTER_MS && (last == 0L || now - last >= GAP_MS) && count < PER_SESSION

    fun preload(ctx: Context) {
        if (Ads.ready && sessionStart == 0L) sessionStart = SystemClock.elapsedRealtime()
        if (!Ads.ready || ad != null || loading || BuildConfig.ADMOB_INTERSTITIAL.isEmpty() || shown >= PER_SESSION) return
        loading = true
        InterstitialAd.load(ctx, BuildConfig.ADMOB_INTERSTITIAL, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(a: InterstitialAd) { ad = a; loading = false }
                override fun onAdFailedToLoad(e: LoadAdError) { loading = false }
            })
    }

    /** Call at a natural break (the user just closed a result). Does nothing unless every rule allows it. */
    fun atBreak(ctx: Context) {
        val activity = ctx.findActivity() ?: return
        val a = ad ?: return
        val now = SystemClock.elapsedRealtime()
        if (!allowed(now, sessionStart, lastShown, shown)) return
        a.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { ad = null; preload(activity.applicationContext) }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { ad = null }
        }
        ad = null
        lastShown = now
        shown++
        a.show(activity)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
