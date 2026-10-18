package com.lottodost.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.lottodost.app.data.Draw
import java.io.File

/**
 * Renders a result as a PNG "sheet" for WhatsApp (users share result images, not links)
 * and opens the share sheet. Pure android.graphics: no extra dependency.
 */
object ShareImage {
    private const val W = 1080
    private const val PAD = 48f

    fun render(
        draw: Draw, title: (rank: String) -> String, footer: String, dateTime: String,
        stateLabel: String = draw.state,
        drawNoLabel: (String) -> String = { "Draw No. $it" },
    ): Bitmap {
        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 46f; typeface = Typeface.DEFAULT_BOLD }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f }
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x41, 0x00, 0x01); textSize = 36f; typeface = Typeface.DEFAULT_BOLD }
        val prize = Paint(head).apply { color = Color.rgb(0xB3, 0x26, 0x1E); textAlign = Paint.Align.RIGHT }
        val first = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xB3, 0x26, 0x1E); textSize = 120f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 34f; typeface = Typeface.MONOSPACE }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 28f; textAlign = Paint.Align.CENTER }

        // Column width per tier from its widest number: 4-digit tiers fit 8 per row.
        fun colW(t: com.lottodost.app.data.Tier) = num.measureText(t.numbers.maxByOrNull { it.length } ?: "0000") + 28f
        fun cols(t: com.lottodost.app.data.Tier) = ((W - 2 * PAD) / colW(t)).toInt().coerceAtLeast(1)
        val lineH = 46f
        val headerH = 230f
        var h = headerH + 200f
        for (t in draw.tiers.drop(1)) h += 70f + ((t.numbers.size + cols(t) - 1) / cols(t)) * lineH + 20f
        h += 110f

        // RGB_565 halves memory (a 500-number bumper sheet is ~1080x3300): safe on 2 GB phones.
        val bmp = Bitmap.createBitmap(W, h.toInt(), Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0xFF, 0xF8, 0xF4))
        c.drawRect(0f, 0f, W.toFloat(), headerH, Paint().apply { color = Color.rgb(0xB3, 0x26, 0x1E) })
        c.drawText(ellipsize(draw.name, name, W - 2 * PAD), PAD, 90f, name)
        c.drawText("$dateTime · $stateLabel", PAD, 150f, sub)
        draw.drawNo?.let { c.drawText(drawNoLabel(it.toString()), PAD, 200f, sub) }

        var y = headerH + 60f
        c.drawText(title(draw.tiers.first().rank) + " · " + formatRupees(draw.tiers.first().prize), PAD, y, head)
        y += 130f
        c.drawText(draw.firstPrize.orEmpty(), W / 2f, y, first)
        y += 50f
        for (t in draw.tiers.drop(1)) {
            y += 50f
            c.drawText(title(t.rank), PAD, y, head)
            c.drawText(formatRupees(t.prize), W - PAD, y, prize)
            y += 20f
            val cw = colW(t)
            val n = cols(t)
            t.numbers.forEachIndexed { i, number ->
                if (i % n == 0) y += lineH
                c.drawText(number, PAD + (i % n) * cw, y, num)
            }
            y += 20f
        }
        c.drawText(footer, W / 2f, h - 50f, small)
        return bmp
    }

    private fun ellipsize(s: String, p: Paint, max: Float): String {
        if (p.measureText(s) <= max) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    fun share(ctx: Context, bmp: Bitmap, fileName: String, text: String) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the latest image
        val f = File(dir, fileName)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".share", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
