@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package studio.inventory.android

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ScannerIdlePauseMs = 20_000L

@Composable
fun ScanPage(controller: InventoryController, modifier: Modifier = Modifier) {
    var scannerRunning by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var lastScannerActivityAt by remember { mutableLongStateOf(0L) }

    fun startScanner() {
        lastScannerActivityAt = System.currentTimeMillis()
        scannerRunning = true
    }

    fun pauseScanner() {
        scannerRunning = false
        torchOn = false
    }

    LaunchedEffect(scannerRunning, lastScannerActivityAt) {
        if (!scannerRunning) return@LaunchedEffect
        val marker = lastScannerActivityAt
        delay(ScannerIdlePauseMs)
        if (scannerRunning && lastScannerActivityAt == marker) {
            pauseScanner()
            controller.reportError("20 秒未扫码，已暂停相机。")
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ScannerPreview(
                    running = scannerRunning,
                    torchOn = torchOn,
                    onPayload = { payload ->
                        lastScannerActivityAt = System.currentTimeMillis()
                        controller.handlePayload(payload)
                    },
                    onError = controller::reportError,
                    onPermissionGranted = {
                        startScanner()
                        controller.reportError("相机权限已授权，正在启动扫码。")
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { startScanner() }) { Text("开始扫描") }
                OutlinedButton(onClick = { pauseScanner() }) { Text("暂停") }
                OutlinedButton(onClick = { torchOn = !torchOn }, enabled = scannerRunning) {
                    Text(if (torchOn) "关灯" else "手电筒")
                }
                OutlinedButton(onClick = controller::clearContext) { Text("清空") }
            }
        }
        item { ContextCard(controller) }
        controller.replaceCandidate?.let {
            item { ReplaceCandidateCard(controller) }
        }
        controller.fixedConflict?.let {
            item { FixedConflictCard(controller, it) }
        }
        item { ActionButtons(controller) }
        item {
            Text("最近扫码", style = MaterialTheme.typography.titleMedium)
        }
        items(controller.snapshot.scanLog.take(8)) { log ->
            ListCard(
                title = log.payload,
                subtitle = log.createdAt,
            )
        }
    }

}

@Composable
private fun ContextCard(controller: InventoryController) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("当前上下文", style = MaterialTheme.typography.titleMedium)
            Text(controller.message)
            HorizontalDivider()
            InfoRow("物品", controller.pendingItem?.let { "${it.id} · ${it.displayName}" } ?: "无")
            InfoRow("重量", controller.pendingWeightG?.let { "${it.gText()}g" } ?: "无")
            InfoRow("数量", controller.pendingQty?.toString() ?: "无")
            InfoRow("库位", controller.pendingLocation?.let { "${it.id} · ${it.name}" } ?: "无")
            InfoRow("整理模式", controller.sortingLocation?.name ?: "未开启")
            controller.activeItem?.let { item ->
                HorizontalDivider()
                Text("本地库存：${item.stockText} / 库位 ${item.locationText}")
            }
        }
    }
}

@Composable
private fun ReplaceCandidateCard(controller: InventoryController) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("检测到未完成上下文冲突", style = MaterialTheme.typography.titleMedium)
            Text("默认不替换，确认后才会覆盖当前待处理内容。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = controller::confirmReplace) { Text("确认替换") }
                OutlinedButton(onClick = controller::cancelReplace) { Text("保留当前") }
            }
        }
    }
}

@Composable
private fun FixedConflictCard(controller: InventoryController, conflict: FixedConflict) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("同 ID 固定信息冲突", style = MaterialTheme.typography.titleMedium)
            Text("本地：${conflict.local.fixed.displayName}")
            Text("扫码：${conflict.scanned.displayName}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = controller::updateLocalFixed) { Text("更新本地") }
                OutlinedButton(onClick = controller::keepLocalFixed) { Text("保留本地") }
            }
        }
    }
}

@Composable
private fun ActionButtons(controller: InventoryController) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Button(onClick = controller::stockIn, enabled = controller.canStockIn()) { Text("入库") }
        OutlinedButton(onClick = controller::stocktake, enabled = controller.canStocktake()) { Text("更新重量/数量") }
        OutlinedButton(onClick = controller::moveActive, enabled = controller.canMove()) { Text("绑定库位") }
        OutlinedButton(onClick = controller::checkoutActive, enabled = controller.canCheckout()) { Text("出库") }
        OutlinedButton(onClick = controller::archiveActive, enabled = controller.activeItem != null) { Text("归档") }
        OutlinedButton(
            onClick = controller::startLocationSorting,
            enabled = controller.pendingLocation != null && controller.sortingLocation == null,
        ) { Text("整理该库位") }
        OutlinedButton(
            onClick = controller::stopLocationSorting,
            enabled = controller.sortingLocation != null,
        ) { Text("完成整理") }
        OutlinedButton(onClick = controller::undoLast, enabled = controller.lastUndo != null) { Text("撤销上一笔") }
    }
}

@Composable
fun InventoryPage(controller: InventoryController, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<ItemType?>(null) }
    var statusFilter by remember { mutableStateOf(StockStatus.InStock) }

    val entries = controller.snapshot.items.values
        .filter { item ->
            val text = query.trim().lowercase()
            (text.isBlank() || item.searchText.contains(text)) &&
                (typeFilter == null || item.type == typeFilter) &&
                item.state.status == statusFilter
        }
        .sortedWith(compareBy<InventoryItem> { it.state.locationId }.thenBy { it.id })

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索 ID、名称、品牌、材料、颜色、库位") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text("全部") })
                listOf(ItemType.Spool, ItemType.Part, ItemType.Other).forEach { type ->
                    FilterChip(selected = typeFilter == type, onClick = { typeFilter = type }, label = { Text(type.label) })
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StockStatus.entries.forEach { status ->
                    FilterChip(
                        selected = statusFilter == status,
                        onClick = { statusFilter = status },
                        label = { Text(status.label) },
                    )
                }
            }
        }
        item {
            Text("显示 ${entries.size} / ${controller.snapshot.items.size}", style = MaterialTheme.typography.bodyMedium)
        }
        items(entries) { item ->
            ListCard(
                title = "${item.id} · ${item.fixed.displayName}",
                subtitle = "${item.type.label} · ${item.stockText} · 库位 ${item.locationText}",
                footer = item.fixed.searchText,
            )
        }
    }
}

@Composable
fun AddLabelPage(
    controller: InventoryController,
    printer: LabelPrinterController,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(ItemType.Spool) }
    var id by remember { mutableStateOf(nextAutoId(type, controller.snapshot.items.keys)) }
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var material by remember { mutableStateOf("PLA") }
    var color by remember { mutableStateOf("") }
    var tareG by remember { mutableStateOf("") }
    var unitWeightG by remember { mutableStateOf("") }
    var weightValue by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var generatedLabel by remember { mutableStateOf<PrintLabelData?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingPrinterAction by remember { mutableStateOf<PrinterPermissionAction?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(type) {
        id = nextAutoId(type, controller.snapshot.items.keys)
        generatedLabel = null
    }

    fun currentLabel(): PrintLabelData {
        val payload = buildLabelPayload(
            type = type,
            id = id,
            name = name,
            brand = brand,
            material = material,
            color = color,
            tareG = tareG,
            unitWeightG = unitWeightG,
            weightValue = weightValue,
            note = note,
        )
        return PrintLabelData(
            payload = payload,
            line1 = labelTitleLine(type, name, brand, material, color, weightValue),
            line2 = todayDisplayDate(),
            line3 = note.ifBlank { " " },
        )
    }

    fun runPrinterAction(action: PrinterPermissionAction?) {
        when (action) {
            PrinterPermissionAction.Search -> printer.discover(autoConnectFirst = printer.autoConnectEnabled)
            PrinterPermissionAction.AutoConnect -> printer.autoConnect()
            PrinterPermissionAction.Print -> {
                val label = currentLabel()
                generatedLabel = label
                printer.print(label)
            }
            null -> Unit
        }
    }

    val printerPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val denied = printerPermissions().filter { grants[it] != true }
        val action = pendingPrinterAction
        pendingPrinterAction = null
        if (denied.isEmpty()) {
            runPrinterAction(action)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("缺少蓝牙或定位权限，不能搜索打印机。")
            }
        }
    }

    fun ensurePrinterPermissions(action: PrinterPermissionAction) {
        val missing = missingPrinterPermissions(context)
        if (missing.isEmpty()) {
            runPrinterAction(action)
        } else {
            pendingPrinterAction = action
            printerPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(printer.autoConnectEnabled) {
        if (printer.autoConnectEnabled) {
            if (hasPrinterPermissions(context)) {
                printer.autoConnect()
            } else {
                printer.markAutoConnectWaitingForPermission()
            }
        }
    }

    LaunchedEffect(generatedLabel) {
        previewBitmap = generatedLabel?.let { printer.renderPreview(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("标签生成器", style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ItemType.Spool, ItemType.Part, ItemType.Other, ItemType.Location, ItemType.Weight).forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = { type = option },
                    label = { Text(option.label) },
                )
            }
        }
        if (type != ItemType.Weight) {
            FormTextField("ID", id, { id = it.uppercase() })
        }
        when (type) {
            ItemType.Spool -> {
                FormTextField("品牌 brand", brand, { brand = it })
                FormTextField("材料 material", material, { material = it })
                FormTextField("颜色 color", color, { color = it })
                NumberTextField("空盘重量 tare_g", tareG, { tareG = it })
                FormTextField("备注 note（可选）", note, { note = it })
            }
            ItemType.Part -> {
                FormTextField("名称 name", name, { name = it })
                NumberTextField("单件重量 unit_weight_g", unitWeightG, { unitWeightG = it })
                FormTextField("备注 note（可选）", note, { note = it })
            }
            ItemType.Other -> {
                FormTextField("名称 name", name, { name = it })
                FormTextField("备注 note（可选）", note, { note = it })
            }
            ItemType.Location -> {
                FormTextField("库位名称 name", name, { name = it })
                FormTextField("备注 note（可选）", note, { note = it })
            }
            ItemType.Weight -> {
                NumberTextField("重量 value_g", weightValue, { weightValue = it })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { id = nextAutoId(type, controller.snapshot.items.keys) }, enabled = type != ItemType.Weight) {
                Text("生成 ID")
            }
            Button(onClick = {
                generatedLabel = currentLabel()
            }) {
                Text("生成标签")
            }
            OutlinedButton(onClick = {
                generatedLabel = null
                name = ""
                brand = ""
                color = ""
                tareG = ""
                unitWeightG = ""
                weightValue = ""
                note = ""
            }) { Text("清空") }
            OutlinedButton(onClick = {
                if (printer.isDiscovering) {
                    printer.stopDiscovery()
                } else {
                    ensurePrinterPermissions(PrinterPermissionAction.Search)
                }
            }) { Text(if (printer.isDiscovering) "停止搜索" else "搜索打印机") }
            OutlinedButton(onClick = {
                ensurePrinterPermissions(PrinterPermissionAction.Print)
            }) { Text("打印") }
        }
        PrinterPanel(
            printer = printer,
            onAutoConnectChange = { enabled ->
                printer.updateAutoConnectEnabled(enabled)
                if (enabled) {
                    ensurePrinterPermissions(PrinterPermissionAction.AutoConnect)
                } else if (printer.isDiscovering) {
                    printer.stopDiscovery("已关闭自动连接。")
                }
            },
        )
        generatedLabel?.let { label ->
            LabelPreviewCard(label, previewBitmap)
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("二维码内容", style = MaterialTheme.typography.titleMedium)
                    Text(label.payload)
                }
            }
        }
    }
}

@Composable
fun TransactionsPage(controller: InventoryController, modifier: Modifier = Modifier) {
    val transactions = controller.snapshot.transactions.asReversed()
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("最近流水 ${transactions.size}", style = MaterialTheme.typography.titleLarge)
        }
        items(transactions) { tx ->
            ListCard(
                title = "${tx.action} · ${tx.itemId}",
                subtitle = "${tx.itemType.label} · ${tx.createdAt}",
                footer = "tx=${tx.txId}",
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(value)
    }
}

@Composable
private fun ListCard(title: String, subtitle: String, footer: String = "") {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            if (footer.isNotBlank()) {
                Text(
                    footer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class PrinterPermissionAction {
    Search,
    AutoConnect,
    Print,
}

@Composable
private fun LabelPreviewCard(label: PrintLabelData, bitmap: Bitmap?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("标签预览", style = MaterialTheme.typography.titleMedium)
            Text("40 x 30 mm", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .border(1.dp, Color.Black)
                            .background(Color.White),
                    )
                } else {
                    FallbackLabelPreview(label)
                }
            }
        }
    }
}

@Composable
private fun FallbackLabelPreview(label: PrintLabelData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .border(1.dp, Color.Black)
            .background(Color.White)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Text(label.line1, color = Color.Black, style = MaterialTheme.typography.titleMedium)
            Text(label.line2, color = Color.Black, style = MaterialTheme.typography.bodyMedium)
            Text(label.line3, color = Color.Black, style = MaterialTheme.typography.bodyMedium)
        }
        QrPreviewPattern(
            payload = label.payload,
            modifier = Modifier
                .width(132.dp)
                .aspectRatio(1f)
                .border(1.dp, Color.Black),
        )
    }
}

@Composable
private fun QrPreviewPattern(payload: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color.White)) {
        val cells = 25
        val cell = size.minDimension / cells
        val seed = payload.fold(0) { acc, char -> (acc * 31 + char.code) and 0x7fffffff }

        fun drawCell(x: Int, y: Int) {
            drawRect(
                color = Color.Black,
                topLeft = Offset(x * cell, y * cell),
                size = Size(cell, cell),
            )
        }

        fun drawFinder(left: Int, top: Int) {
            for (y in 0 until 7) {
                for (x in 0 until 7) {
                    val border = x == 0 || y == 0 || x == 6 || y == 6
                    val center = x in 2..4 && y in 2..4
                    if (border || center) drawCell(left + x, top + y)
                }
            }
        }

        drawFinder(1, 1)
        drawFinder(cells - 8, 1)
        drawFinder(1, cells - 8)
        for (y in 0 until cells) {
            for (x in 0 until cells) {
                val inFinder =
                    (x in 1..7 && y in 1..7) ||
                        (x in cells - 8 until cells - 1 && y in 1..7) ||
                        (x in 1..7 && y in cells - 8 until cells - 1)
                if (!inFinder && ((x * 17 + y * 23 + seed) % 7 < 3)) {
                    drawCell(x, y)
                }
            }
        }
    }
}

@Composable
private fun PrinterPanel(
    printer: LabelPrinterController,
    onAutoConnectChange: (Boolean) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("标签打印机", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启动自动连接")
                    Text(
                        if (printer.autoConnectEnabled) "打开 App 后会自动连接上次打印机；没有记录时连接第一个发现的打印机。"
                        else "关闭时只手动搜索和连接。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = printer.autoConnectEnabled,
                    onCheckedChange = onAutoConnectChange,
                )
            }
            HorizontalDivider()
            Text(printer.status)
            if (printer.printers.isNotEmpty()) {
                HorizontalDivider()
                printer.printers.forEach { address ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(address.shownName.ifBlank { "未知打印机" })
                            Text(address.macAddress.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { printer.connect(address) }) {
                            Text(if (printer.connectedPrinter?.key() == address.key()) "已连接" else "连接")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun buildLabelPayload(
    type: ItemType,
    id: String,
    name: String,
    brand: String,
    material: String,
    color: String,
    tareG: String,
    unitWeightG: String,
    weightValue: String,
    note: String,
): String {
    val fields = linkedMapOf("type" to type.payload)
    when (type) {
        ItemType.Spool -> {
            fields["id"] = id.trim().uppercase()
            fields["brand"] = brand
            fields["material"] = material
            fields["color"] = color
            fields["tare_g"] = tareG
            fields["created_on"] = todayCode()
            fields["note"] = note
        }
        ItemType.Part -> {
            fields["id"] = id.trim().uppercase()
            fields["name"] = name
            fields["unit_weight_g"] = unitWeightG
            fields["created_on"] = todayCode()
            fields["note"] = note
        }
        ItemType.Other, ItemType.Location -> {
            fields["id"] = id.trim().uppercase()
            fields["name"] = name
            fields["created_on"] = todayCode()
            fields["note"] = note
        }
        ItemType.Weight -> {
            fields["value_g"] = weightValue
        }
    }
    return buildV1Payload(fields)
}

private fun labelTitleLine(
    type: ItemType,
    name: String,
    brand: String,
    material: String,
    color: String,
    weightValue: String,
): String {
    return when (type) {
        ItemType.Spool -> listOf(material, color, brand).filter { it.isNotBlank() }.joinToString(" ")
        ItemType.Weight -> weightValue.trim().ifBlank { "0" } + "g"
        else -> name.trim()
    }.ifBlank { type.label }
}

private fun todayDisplayDate(): String {
    val code = todayCode()
    return "20${code.substring(0, 2)}-${code.substring(2, 4)}-${code.substring(4, 6)}"
}
