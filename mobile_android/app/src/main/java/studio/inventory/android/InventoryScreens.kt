@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package studio.inventory.android

import android.graphics.Bitmap
import android.util.DisplayMetrics
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryPage(controller: InventoryController, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<ItemType?>(null) }
    var statusFilter by remember { mutableStateOf(StockStatus.InStock) }
    var selectedItem by remember { mutableStateOf<InventoryItem?>(null) }

    val entries = filterInventoryItems(
        items = controller.snapshot.items.values,
        query = query,
        typeFilter = typeFilter,
        statusFilter = statusFilter,
    )

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
                footer = item.fixed.note,
                onClick = { selectedItem = item },
            )
        }
    }
    selectedItem?.let { item ->
        InventoryDetailSheet(
            controller = controller,
            item = item,
            onDismiss = { selectedItem = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryDetailSheet(controller: InventoryController, item: InventoryItem, onDismiss: () -> Unit) {
    var pendingAction by remember(item.id) { mutableStateOf<InventoryDetailAction?>(null) }
    val recentTransactions = controller.snapshot.transactions
        .filter { it.itemId == item.id }
        .takeLast(3)
        .asReversed()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(item.fixed.displayName, style = MaterialTheme.typography.titleLarge)
            Text(item.id, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            InfoRow("类型", item.type.label)
            InfoRow("状态", item.state.status.label)
            InfoRow("库位", item.locationText)
            when (item.type) {
                ItemType.Spool -> {
                    InfoRow("品牌", item.fixed.brand.ifBlank { "未记录" })
                    InfoRow("材料", item.fixed.material.ifBlank { "未记录" })
                    InfoRow("颜色", item.fixed.color.ifBlank { "未记录" })
                    InfoRow("毛重", item.state.currentG?.let { "${it.gText()}g" } ?: "未称重")
                    InfoRow("空盘", item.fixed.tareG?.let { "${it.parameterText()}g" } ?: "未记录")
                    InfoRow("可用", item.usableG?.let { "${it.gText()}g" } ?: "未计算")
                }
                ItemType.Part -> {
                    InfoRow("单重", item.fixed.unitWeightG?.let { "${it.parameterText()}g" } ?: "未记录")
                    InfoRow("总重", item.state.currentG?.let { "${it.gText()}g" } ?: "未称重")
                    InfoRow("数量", item.state.currentQty?.toString() ?: "未记录")
                }
                ItemType.Other -> Unit
                ItemType.Location, ItemType.Weight -> Unit
            }
            if (item.fixed.note.isNotBlank()) {
                InfoRow("备注", item.fixed.note)
            }
            HorizontalDivider()
            InfoRow("入库", displayDate(item.state.stockedOn))
            InfoRow("出库", displayDate(item.state.checkedOutOn))
            InfoRow("更新", displayTimestamp(item.state.updatedAt))
            if (recentTransactions.isNotEmpty()) {
                HorizontalDivider()
                Text("最近流水", style = MaterialTheme.typography.titleMedium)
                recentTransactions.forEach { tx ->
                    InfoRow(transactionActionLabel(tx.action), displayTimestamp(tx.createdAt))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.state.status == StockStatus.InStock) {
                    Button(onClick = { pendingAction = InventoryDetailAction.Checkout }) { Text("出库") }
                }
                if (item.state.status != StockStatus.Archived) {
                    OutlinedButton(onClick = { pendingAction = InventoryDetailAction.Archive }) { Text("归档") }
                }
                OutlinedButton(onClick = onDismiss) { Text("关闭") }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(if (action == InventoryDetailAction.Checkout) "确认出库" else "确认归档") },
            text = {
                Text(
                    if (action == InventoryDetailAction.Checkout) {
                        "${item.fixed.displayName} 出库后会清空当前库位，保留最后重量或数量。"
                    } else {
                        "${item.fixed.displayName} 归档后默认不显示在在库列表。"
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    when (action) {
                        InventoryDetailAction.Checkout -> controller.checkoutItem(item.id)
                        InventoryDetailAction.Archive -> controller.archiveItem(item.id)
                    }
                    pendingAction = null
                    onDismiss()
                }) { Text("确认") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingAction = null }) { Text("取消") }
            },
        )
    }
}

private enum class InventoryDetailAction {
    Checkout,
    Archive,
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

    fun existingIdsFor(option: ItemType): Set<String> {
        return if (option == ItemType.Location) {
            controller.snapshot.locations.keys
        } else {
            controller.snapshot.items.keys
        }
    }

    LaunchedEffect(type) {
        id = nextAutoId(type, existingIdsFor(type))
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
            OutlinedButton(onClick = { id = nextAutoId(type, existingIdsFor(type)) }, enabled = type != ItemType.Weight) {
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
            LabelPreviewCard(previewBitmap)
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
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("最近流水 ${transactions.size}", style = MaterialTheme.typography.titleLarge)
                Text("版本 ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(
                    onClick = { uriHandler.openUri(RepositoryUrl) },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("github.com/llleeeqi/maker-studio-inventory")
                }
            }
        }
        items(transactions) { tx ->
            val source = tx.sourceDeviceName.ifBlank { tx.sourceDeviceId }.ifBlank { "本机旧记录" }
            ListCard(
                title = "${transactionActionLabel(tx.action)} · ${tx.itemId}",
                subtitle = "${tx.itemType.label} · ${displayTimestamp(tx.createdAt)} · $source",
                footer = "tx=${tx.txId}",
            )
        }
    }
}

private const val RepositoryUrl = "https://github.com/llleeeqi/maker-studio-inventory"

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(value)
    }
}

@Composable
private fun ListCard(title: String, subtitle: String, footer: String = "", onClick: (() -> Unit)? = null) {
    val cardModifier = if (onClick == null) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    }
    Card(modifier = cardModifier) {
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
private fun LabelPreviewCard(bitmap: Bitmap?) {
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val metrics = remember(view, configuration.orientation) {
        DisplayMetrics().also { displayMetrics ->
            @Suppress("DEPRECATION")
            view.display?.getRealMetrics(displayMetrics)
            if (displayMetrics.widthPixels <= 0) {
                displayMetrics.setTo(view.resources.displayMetrics)
            }
        }
    }
    val xDpi = metrics.xdpi.takeIf { it in 100f..1000f } ?: metrics.densityDpi.toFloat()
    val yDpi = metrics.ydpi.takeIf { it in 100f..1000f } ?: metrics.densityDpi.toFloat()
    val physicalWidthDp = with(density) { (LabelPrintSpec.WidthMm * xDpi / 25.4f).toDp() }
    val physicalHeightDp = with(density) { (LabelPrintSpec.HeightMm * yDpi / 25.4f).toDp() }
    val screenWidthMm = metrics.widthPixels * 25.4f / xDpi
    val screenHeightMm = metrics.heightPixels * 25.4f / yDpi

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("标签预览", style = MaterialTheme.typography.titleMedium)
            Text(
                "40 × 30 mm · 屏幕报告约 ${screenWidthMm.toInt()} × ${screenHeightMm.toInt()} mm",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val scale = min(1f, maxWidth.value / physicalWidthDp.value)
                val shownWidth = physicalWidthDp * scale
                val shownHeight = physicalHeightDp * scale
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "40 × 30 mm 标签打印预览",
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .size(shownWidth, shownHeight)
                                    .border(1.dp, Color.Black)
                                    .background(Color.White),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(shownWidth, shownHeight)
                                    .border(1.dp, Color.Black)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("正在生成预览", color = Color.DarkGray)
                            }
                        }
                    }
                    Text(
                        if (scale == 1f) "按当前屏幕报告尺寸 1:1 显示" else "屏幕空间不足，已等比缩小",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
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
