package com.lottodost.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.lottodost.app.R
import com.lottodost.app.domain.TicketParser
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live camera OCR (on-device ML Kit, works offline once the model is on the phone).
 * Stops at the first frame that contains a valid ticket number and hands the raw text back;
 * the Check screen then asks the user to confirm the number before checking it.
 */
@Composable
fun ScanScreen(onClose: () -> Unit, onText: (String) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    DisposableEffect(Unit) { onDispose { recognizer.close() } }
    val done = remember { AtomicBoolean(false) }
    var photoError by remember { mutableStateOf(false) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { InputImage.fromFilePath(ctx, uri) }.onSuccess { img ->
            recognizer.process(img)
                .addOnSuccessListener { if (TicketParser.fromOcr(it.text).tickets.isNotEmpty()) onText(it.text) else photoError = true }
                .addOnFailureListener { photoError = true }
        }.onFailure { photoError = true }
    }

    LaunchedEffect(Unit) { if (!granted) askCamera.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraPreview(onFrameText = { text ->
                if (!done.get() && TicketParser.fromOcr(text).tickets.isNotEmpty() && done.compareAndSet(false, true)) onText(text)
            }, recognizerProcess = recognizer)
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth(0.88f).aspectRatio(2.2f)
                    .border(3.dp, MaterialTheme.colorScheme.secondary, MaterialTheme.shapes.medium)
            )
        }
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp)) {
            Text(stringResource(if (granted) R.string.scan_aim else R.string.scan_need_camera), color = Color.White,
                style = MaterialTheme.typography.titleMedium)
            if (photoError) Text(stringResource(R.string.scan_none), color = MaterialTheme.colorScheme.secondary)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel), color = Color.White) }
            Button(onClick = {
                photoError = false
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.pick_photo)) }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(onFrameText: (String) -> Unit, recognizerProcess: com.google.mlkit.vision.text.TextRecognizer) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val busy = remember { AtomicBoolean(false) }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            val view = PreviewView(c)
            val providerFuture = ProcessCameraProvider.getInstance(c)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null || !busy.compareAndSet(false, true)) { proxy.close(); return@setAnalyzer }
                    val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    recognizerProcess.process(img)
                        .addOnSuccessListener { onFrameText(it.text) }
                        .addOnCompleteListener { busy.set(false); proxy.close() }
                }
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
        onRelease = { ProcessCameraProvider.getInstance(ctx).get().unbindAll() },
    )
}
