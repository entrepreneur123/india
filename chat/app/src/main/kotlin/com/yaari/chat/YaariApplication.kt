package com.yaari.chat

import android.app.ActivityManager
import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.allowRgb565
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath

/**
 * Image loading sized for 2–3 GB phones: a smaller memory cache on low-RAM devices (and 16-bit bitmaps there),
 * a bounded disk cache so photos seen once are not downloaded again on paid mobile data, and no crossfade.
 * Nothing else runs at startup.
 */
class YaariApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val am = getSystemService(ActivityManager::class.java)
        // memoryClass is the per-app heap in MB: 96–192 on 2–3 GB phones, 256+ on mid-range and up.
        val lowRam = am == null || am.isLowRamDevice || am.memoryClass <= LOW_RAM_HEAP_MB
        return ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, if (lowRam) 0.12 else 0.20).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(if (lowRam) 40L * MB else 80L * MB)
                    .build()
            }
            .allowRgb565(lowRam)
            .crossfade(false)
            .build()
    }

    private companion object {
        const val MB = 1024L * 1024L
        const val LOW_RAM_HEAP_MB = 192
    }
}
