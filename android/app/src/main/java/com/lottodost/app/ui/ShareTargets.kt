package com.lottodost.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import java.io.File

/**
 * Direct share to the apps people in India actually pass results around on, in this order.
 * Only apps installed on the phone are offered (their own icon and name come from the system);
 * "More" opens the normal Android share sheet.
 */
object ShareTargets {
    val KNOWN = listOf(
        "com.whatsapp",               // WhatsApp
        "com.whatsapp.w4b",           // WhatsApp Business (ticket sellers)
        "org.telegram.messenger",     // Telegram
        "com.facebook.katana",        // Facebook
        "com.facebook.orca",          // Messenger
        "com.instagram.android",      // Instagram
        "in.mohalla.sharechat",       // ShareChat
        "com.twitter.android",        // X
        "org.thoughtcrime.securesms", // Signal
    )

    data class App(val pkg: String, val label: String, val icon: Bitmap?)

    fun installed(ctx: Context): List<App> {
        val pm = ctx.packageManager
        return KNOWN.mapNotNull { pkg ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                if (!info.enabled) return@mapNotNull null
                val icon = runCatching { pm.getApplicationIcon(info).toBitmap(96, 96) }.getOrNull()
                App(pkg, pm.getApplicationLabel(info).toString(), icon)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    /** Writes the result image to the shared cache folder and returns a content:// URI for it. */
    fun imageUri(ctx: Context, bmp: Bitmap, fileName: String): Uri {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the latest image
        val f = File(dir, fileName)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".share", f)
    }

    /** Image plus caption to one app; falls back to the system share sheet if that app refuses. */
    fun sendImage(ctx: Context, uri: Uri, text: String, pkg: String?) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(if (pkg != null) Intent(send).setPackage(pkg) else Intent.createChooser(send, null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** SMS carries text only, so send the short version (1st prize + link to the full result). */
    fun sendSms(ctx: Context, text: String) {
        val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(sms)
        } catch (_: ActivityNotFoundException) {
            shareText(ctx, text)
        }
    }
}
