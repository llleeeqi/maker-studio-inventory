package studio.inventory.android

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun ScannerPreview(
    running: Boolean,
    torchOn: Boolean,
    pausedMessage: String = "相机已暂停，点击开始扫描",
    onPayload: (String) -> Unit,
    onError: (String) -> Unit,
    onPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted) {
            onPermissionGranted()
        } else {
            onError("没有相机权限。")
        }
    }

    if (!hasPermission) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                Text("需要相机权限才能扫码。", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("授权并开始扫描")
                }
            }
        }
        return
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CameraPreviewSurface(
            running = running,
            torchOn = torchOn,
            onPayload = onPayload,
            onError = onError,
            modifier = Modifier.fillMaxSize(),
        )
        if (!running) {
            Text(pausedMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CameraPreviewSurface(
    running: Boolean,
    torchOn: Boolean,
    onPayload: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var lastPayload by remember { mutableStateOf("") }
    var lastPayloadAt by remember { mutableLongStateOf(0L) }

    DisposableEffect(scanner, analysisExecutor) {
        onDispose {
            scanner.close()
            analysisExecutor.shutdownNow()
        }
    }

    LaunchedEffect(torchOn, camera) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    DisposableEffect(running) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val listener = Runnable {
            if (disposed) return@Runnable
            runCatching {
                provider = providerFuture.get()
                if (disposed) return@runCatching
                provider?.unbindAll()
                if (!running) {
                    camera = null
                    return@Runnable
                }
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees,
                            )
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    val payload = barcodes.firstOrNull()?.rawValue?.trim()
                                    if (!payload.isNullOrEmpty()) {
                                        val now = System.currentTimeMillis()
                                        if (payload != lastPayload || now - lastPayloadAt > 1400) {
                                            lastPayload = payload
                                            lastPayloadAt = now
                                            onPayload(payload)
                                        }
                                    }
                                }
                                .addOnFailureListener { error ->
                                    onError("扫码解析失败：${error.message ?: error::class.java.simpleName}")
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        }
                    }
                camera = provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
                camera?.cameraControl?.enableTorch(torchOn)
            }.onFailure { error ->
                onError("相机启动失败：${error.message ?: error::class.java.simpleName}")
            }
        }
        providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            provider?.unbindAll()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize(),
    )
}
