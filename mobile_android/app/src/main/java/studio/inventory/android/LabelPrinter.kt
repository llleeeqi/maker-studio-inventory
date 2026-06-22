package studio.inventory.android

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
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

class LabelPrinterController {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val api: LPAPI = LPAPI.Factory.createInstance(object : LPAPI.Callback {
        override fun onProgressInfo(progressInfo: ProgressInfo?, extra: Any?) = Unit

        override fun onStateChange(address: PrinterAddress?, state: PrinterState?) {
            mainHandler.post {
                status = when (state) {
                    PrinterState.Connecting -> "正在连接 ${address?.shownName.orEmpty()}..."
                    PrinterState.Connected, PrinterState.Connected2 -> {
                        connectedPrinter = address
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
            }
        }
    })

    val printers = mutableStateListOf<PrinterAddress>()

    var status by mutableStateOf("未连接打印机。")
        private set

    var connectedPrinter by mutableStateOf<PrinterAddress?>(null)
        private set

    fun discover() {
        printers.clear()
        status = if (api.discovery()) "正在搜索蓝牙打印机..." else "启动搜索失败，请确认蓝牙和定位已开启。"
    }

    fun connect(address: PrinterAddress) {
        api.stopDiscovery()
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

    fun close() {
        runCatching {
            api.stopDiscovery()
            api.quit()
        }
    }
}
