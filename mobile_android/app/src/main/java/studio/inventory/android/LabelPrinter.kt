package studio.inventory.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dothantech.lpapi.IAtBitmap.DrawParamName
import com.dothantech.lpapi.IAtBitmap.ErrorCorrectionLevel
import com.dothantech.lpapi.LPAPI
import com.dothantech.printer.IDzPrinter
import com.dothantech.printer.IDzPrinter.PrintData
import com.dothantech.printer.IDzPrinter.PrintParamName
import com.dothantech.printer.IDzPrinter.PrintProgress
import com.dothantech.printer.IDzPrinter.PrinterAddress
import com.dothantech.printer.IDzPrinter.PrinterState
import com.dothantech.printer.IDzPrinter.ProgressInfo

data class PrintLabelData(
    val payload: String,
    val line1: String,
    val line2: String,
    val line3: String,
)

class LabelPrinterController(context: Context) {
    private val prefs = context.getSharedPreferences("label_printer", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoConnectFirstPrinter = false
    private val discoveryTimeout = Runnable {
        if (isDiscovering) {
            stopDiscovery("搜索超时，已停止。")
        }
    }
    private val api: LPAPI = LPAPI.Factory.createInstance(object : LPAPI.Callback {
        override fun onProgressInfo(progressInfo: ProgressInfo?, extra: Any?) = Unit

        override fun onStateChange(address: PrinterAddress?, state: PrinterState?) {
            mainHandler.post {
                status = when (state) {
                    PrinterState.Connecting -> "正在连接 ${address?.shownName.orEmpty()}..."
                    PrinterState.Connected, PrinterState.Connected2 -> {
                        connectedPrinter = address
                        if (address != null) saveLastPrinter(address)
                        "已连接 ${address?.shownName.orEmpty()}"
                    }
                    PrinterState.Printing, PrinterState.Working -> "打印机正在工作。"
                    PrinterState.Disconnected -> {
                        connectedPrinter = null
                        "打印机已断开。"
                    }
                    null -> "打印机状态未知。"
                }
            }
        }

        override fun onPrintProgress(
            address: PrinterAddress?,
            data: PrintData?,
            progress: PrintProgress?,
            extra: Any?,
        ) {
            mainHandler.post {
                status = when (progress) {
                    PrintProgress.Connected -> "打印机已准备。"
                    PrintProgress.StartCopy -> "开始打印标签。"
                    PrintProgress.DataEnded -> "标签数据已发送。"
                    PrintProgress.Success -> "打印完成。"
                    PrintProgress.Failed -> "打印失败。"
                    null -> status
                }
            }
        }

        override fun onPrinterDiscovery(address: PrinterAddress?, extra: Any?) {
            if (address == null || !address.isValid) return
            mainHandler.post {
                if (printers.none { it.key() == address.key() }) {
                    printers += address
                    status = "发现打印机 ${address.shownName}。"
                }
                if (autoConnectFirstPrinter && connectedPrinter == null && !api.isPrinterOpened) {
                    connect(address)
                }
            }
        }
    })

    val printers = mutableStateListOf<PrinterAddress>()

    var status by mutableStateOf("未连接打印机。")
        private set

    var isDiscovering by mutableStateOf(false)
        private set

    var autoConnectEnabled by mutableStateOf(prefs.getBoolean(AutoConnectKey, false))
        private set

    var connectedPrinter by mutableStateOf<PrinterAddress?>(null)
        private set

    fun updateAutoConnectEnabled(enabled: Boolean) {
        autoConnectEnabled = enabled
        prefs.edit().putBoolean(AutoConnectKey, enabled).apply()
        if (!enabled) autoConnectFirstPrinter = false
    }

    fun autoConnect() {
        if (!autoConnectEnabled || connectedPrinter != null || api.isPrinterOpened || isDiscovering) return

        val saved = savedPrinterAddress()
        if (saved != null) {
            status = "正在自动连接 ${saved.shownName.ifBlank { saved.macAddress.orEmpty() }}..."
            saveLastPrinter(saved)
            if (api.openPrinterByAddress(saved)) return
        }
        discover(autoConnectFirst = true)
    }

    fun markAutoConnectWaitingForPermission() {
        if (autoConnectEnabled) {
            status = "自动连接已开启，授权蓝牙/定位后会搜索打印机。"
        }
    }

    fun discover(autoConnectFirst: Boolean = false) {
        printers.clear()
        autoConnectFirstPrinter = autoConnectFirst
        mainHandler.removeCallbacks(discoveryTimeout)
        isDiscovering = api.discovery()
        status = if (isDiscovering) {
            mainHandler.postDelayed(discoveryTimeout, DiscoveryTimeoutMs)
            if (autoConnectFirst) "正在搜索并自动连接蓝牙打印机..." else "正在搜索蓝牙打印机..."
        } else {
            autoConnectFirstPrinter = false
            "启动搜索失败，请确认蓝牙和定位已开启。"
        }
    }

    fun stopDiscovery(message: String = "已停止搜索打印机。") {
        mainHandler.removeCallbacks(discoveryTimeout)
        api.stopDiscovery()
        isDiscovering = false
        autoConnectFirstPrinter = false
        status = message
    }

    fun connect(address: PrinterAddress) {
        stopDiscovery("正在连接 ${address.shownName}...")
        saveLastPrinter(address)
        status = if (api.openPrinterByAddress(address)) {
            "正在连接 ${address.shownName}..."
        } else {
            "连接请求失败。"
        }
    }

    fun print(label: PrintLabelData) {
        val connected = connectedPrinter
        if (connected == null && !api.isPrinterOpened) {
            status = "先搜索并连接打印机。"
            return
        }

        runCatching {
            drawLabelJob(label)
            val params = Bundle().apply {
                putInt(PrintParamName.PRINT_COPIES, 1)
            }
            if (!api.commitJobWithParam(params)) {
                status = "提交打印失败。"
            } else {
                status = "已提交打印任务。"
            }
        }.onFailure {
            status = "打印异常：${it.message ?: it::class.java.simpleName}"
        }
    }

    fun renderPreview(label: PrintLabelData): Bitmap? {
        return runCatching {
            drawLabelJob(label)
            val bitmap = api.getJobPages().firstOrNull()?.copy(Bitmap.Config.ARGB_8888, false)
            api.abortJob()
            bitmap
        }.getOrNull()
    }

    fun close() {
        runCatching {
            stopDiscovery("已停止搜索打印机。")
            api.quit()
        }
    }

    private fun drawLabelJob(label: PrintLabelData) {
        api.setDrawParam(DrawParamName.ERROR_CORRECTION, ErrorCorrectionLevel.Q)
        api.setDrawParam(DrawParamName.CHARACTER_SET, "UTF-8")
        api.setDrawParam(DrawParamName.MARGIN, 2)
        api.startJob(40.0, 30.0, 0)
        api.setItemHorizontalAlignment(0)
        api.setItemVerticalAlignment(0)

        val leftX = 2.0
        val leftWidth = 17.0
        api.drawTextRegular(label.line1, leftX, 3.0, leftWidth, 7.0, 2.8, 1)
        api.drawTextRegular(label.line2, leftX, 11.0, leftWidth, 5.5, 2.4, 0)
        api.drawTextRegular(label.line3, leftX, 17.0, leftWidth, 9.0, 2.4, 0)
        api.draw2DQRCode(label.payload, 20.0, 5.0, 18.0)
    }

    private fun saveLastPrinter(address: PrinterAddress) {
        prefs.edit()
            .putString(LastPrinterMacKey, address.macAddress.orEmpty())
            .putString(LastPrinterNameKey, address.shownName.orEmpty())
            .putString(LastPrinterTypeKey, address.addressType?.name.orEmpty())
            .apply()
    }

    private fun savedPrinterAddress(): PrinterAddress? {
        val mac = prefs.getString(LastPrinterMacKey, null)?.takeIf { it.isNotBlank() } ?: return null
        val typeName = prefs.getString(LastPrinterTypeKey, null).orEmpty()
        val type = runCatching { IDzPrinter.AddressType.valueOf(typeName) }
            .getOrNull()
            ?: IDzPrinter.AddressType.DUAL
        val name = prefs.getString(LastPrinterNameKey, null).orEmpty()
        return PrinterAddress(mac, name, type)
    }

    private companion object {
        const val AutoConnectKey = "auto_connect"
        const val LastPrinterMacKey = "last_printer_mac"
        const val LastPrinterNameKey = "last_printer_name"
        const val LastPrinterTypeKey = "last_printer_type"
        const val DiscoveryTimeoutMs = 20_000L
    }
}

fun printerPermissions(): List<String> {
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    if (Build.VERSION.SDK_INT >= 31) {
        permissions += Manifest.permission.BLUETOOTH_SCAN
        permissions += Manifest.permission.BLUETOOTH_CONNECT
    } else {
        @Suppress("DEPRECATION")
        permissions += Manifest.permission.BLUETOOTH
        @Suppress("DEPRECATION")
        permissions += Manifest.permission.BLUETOOTH_ADMIN
    }
    return permissions
}

fun missingPrinterPermissions(context: Context): List<String> =
    printerPermissions().filter {
        context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
    }

fun hasPrinterPermissions(context: Context): Boolean = missingPrinterPermissions(context).isEmpty()
