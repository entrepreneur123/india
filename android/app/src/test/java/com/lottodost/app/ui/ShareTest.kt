package com.lottodost.app.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ShareTest {
    private val app = RuntimeEnvironment.getApplication()

    private fun install(pkg: String, label: String) {
        val info = PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply { packageName = pkg; name = label; nonLocalizedLabel = label; enabled = true }
        }
        shadowOf(app.packageManager).installPackage(info)
    }

    @Test fun offersOnlyInstalledAppsInIndianOrder() {
        install("org.telegram.messenger", "Telegram")
        install("com.whatsapp", "WhatsApp")
        install("com.example.other", "Other")
        assertEquals(listOf("com.whatsapp", "org.telegram.messenger"), ShareTargets.installed(app).map { it.pkg })
    }

    @Test fun imageGoesToTheChosenAppWithCaption() {
        install("com.whatsapp", "WhatsApp")
        val uri = ShareTargets.imageUri(app, Bitmap.createBitmap(10, 10, Bitmap.Config.RGB_565), "x.png")
        ShareTargets.sendImage(app, uri, "DEAR WISH 74D 70698", "com.whatsapp")
        val sent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_SEND, sent.action)
        assertEquals("com.whatsapp", sent.`package`)
        assertEquals("DEAR WISH 74D 70698", sent.getStringExtra(Intent.EXTRA_TEXT))
        assertNotNull(sent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java))
    }

    @Test fun smsCarriesTheShortText() {
        ShareTargets.sendSms(app, "DEAR WISH · 1st Prize: 74D 70698")
        val sent = shadowOf(app).nextStartedActivity
        assertEquals("smsto:", sent.dataString)
        assertEquals("DEAR WISH · 1st Prize: 74D 70698", sent.getStringExtra("sms_body"))
    }
}
