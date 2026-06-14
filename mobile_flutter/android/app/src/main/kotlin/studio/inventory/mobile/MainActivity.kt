package studio.inventory.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult

class MainActivity : FlutterActivity() {
    private val scannerChannel = "studio.inventory.mobile/native_scanner"
    private var pendingScanResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, scannerChannel)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "scanQr" -> startQrScan(result)
                    "diagnostics" -> result.success(buildDiagnostics())
                    else -> result.notImplemented()
                }
            }
    }

    private fun startQrScan(result: MethodChannel.Result) {
        if (pendingScanResult != null) {
            result.error("SCAN_ACTIVE", "已有扫码窗口正在运行。", null)
            return
        }

        pendingScanResult = result
        try {
            IntentIntegrator(this).apply {
                setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                setPrompt("对准二维码")
                setBeepEnabled(false)
                setBarcodeImageEnabled(false)
                setOrientationLocked(false)
                initiateScan()
            }
        } catch (error: Exception) {
            pendingScanResult = null
            result.error("SCAN_START_FAILED", error.message ?: "无法打开原生扫码。", null)
        }
    }

    @Deprecated("Deprecated in Android Activity API but still used by ZXing embedded.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        val scanResult: IntentResult? = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (scanResult != null) {
            val result = pendingScanResult
            pendingScanResult = null
            result?.success(scanResult.contents)
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun buildDiagnostics(): Map<String, Any?> {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val cameraPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return mapOf(
            "packageName" to packageName,
            "versionName" to packageInfo.versionName,
            "versionCode" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            },
            "manufacturer" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "model" to Build.MODEL,
            "device" to Build.DEVICE,
            "product" to Build.PRODUCT,
            "hardware" to Build.HARDWARE,
            "board" to Build.BOARD,
            "androidSdk" to Build.VERSION.SDK_INT,
            "androidRelease" to Build.VERSION.RELEASE,
            "androidCodename" to Build.VERSION.CODENAME,
            "androidIncremental" to Build.VERSION.INCREMENTAL,
            "fingerprint" to Build.FINGERPRINT,
            "supportedAbis" to Build.SUPPORTED_ABIS.joinToString(","),
            "cameraPermissionGranted" to cameraPermission,
            "featureCamera" to packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA),
            "featureCameraAny" to packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
            "featureAutofocus" to packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS),
            "featureFlash" to packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        )
    }
}
