package com.lottodost.app.ui

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lottodost.app.BuildConfig
import com.lottodost.app.R
import com.lottodost.app.data.SheetSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The publisher's own result sheet, shown exactly as published (downloaded by the phone on demand,
 * rendered with the platform PDF renderer). Pinch to zoom; drag to move while zoomed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetViewerScreen(title: String, url: String, publisher: String, mayChange: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(url) { mutableStateOf<SheetState>(SheetState.Loading) }

    LaunchedEffect(url, attempt) {
        state = SheetState.Loading
        state = withContext(Dispatchers.IO) {
            when (val r = SheetSource.load(ctx.cacheDir, url, mayChange, "LottoDost/${BuildConfig.VERSION_NAME} (Android)")) {
                is SheetSource.Result.Ok -> render(r.file, ctx.resources.displayMetrics.widthPixels)
                SheetSource.Result.NotPublished -> SheetState.NotPublished
                SheetSource.Result.Failed -> SheetState.Failed
            }
        }
    }
    DisposableEffect(url) { onDispose { (state as? SheetState.Pages)?.pages?.forEach { it.recycle() } } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title, maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) } },
            actions = { TextButton(onClick = { openUrl(ctx, url) }) { Text(stringResource(R.string.open_in_browser)) } },
        )
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val s = state) {
                SheetState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.sheet_loading))
                }
                SheetState.NotPublished, SheetState.Failed -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(if (s == SheetState.NotPublished) R.string.sheet_not_ready else R.string.sheet_failed))
                    TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.retry)) }
                }
                is SheetState.Pages -> ZoomablePages(s.pages, stringResource(R.string.sheet_source, publisher))
            }
        }
    }
}

private sealed interface SheetState {
    data object Loading : SheetState
    data object NotPublished : SheetState
    data object Failed : SheetState
    data class Pages(val pages: List<Bitmap>) : SheetState
}

/**
 * Renders every page at about 1.5x screen width: sharp when zoomed, and an A4 page at 1620 px
 * in RGB_565 is ~4.5 MB, which is fine on 2 GB phones.
 */
private fun render(file: File, screenWidth: Int): SheetState {
    // Explicit close() rather than use{}: PdfRenderer is not AutoCloseable on every supported API level.
    var fd: ParcelFileDescriptor? = null
    var pdf: PdfRenderer? = null
    return try {
        fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pdf = PdfRenderer(fd)
        val pages = (0 until minOf(pdf.pageCount, 4)).map { i ->
            val page = pdf.openPage(i)
            try {
                val w = (screenWidth * 1.5f).toInt().coerceIn(800, 1800)
                val h = (w.toLong() * page.height / page.width).toInt()
                // PdfRenderer draws onto ARGB_8888 only; convert once, then let the large copy go.
                val argb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(AColor.WHITE) }
                page.render(argb, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                argb.copy(Bitmap.Config.RGB_565, false).also { argb.recycle() }
            } finally {
                page.close()
            }
        }
        if (pages.isEmpty()) SheetState.NotPublished else SheetState.Pages(pages)
    } catch (_: Exception) {
        // A damaged or unexpected file: drop it so the next try downloads it again.
        file.delete()
        SheetState.Failed
    } finally {
        pdf?.close()
        fd?.close()
    }
}

@Composable
private fun ZoomablePages(pages: List<Bitmap>, caption: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Column(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .verticalScroll(rememberScrollState(), enabled = scale == 1f),
    ) {
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        pages.forEach { bmp ->
            Image(
                bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                },
            )
        }
    }
}
