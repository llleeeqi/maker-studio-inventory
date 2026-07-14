@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package studio.inventory.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val ScannerIdlePauseMs = 20_000L

@Composable
fun ScanWorkspacePage(controller: InventoryController, modifier: Modifier = Modifier) {
    var scannerRunning by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var lastScannerActivityAt by remember { mutableLongStateOf(0L) }
    var showHistory by remember { mutableStateOf(false) }
    val review = controller.scanReview
    val sessionActive = controller.scanState.hasSession
    val analyzerRunning = scannerRunning && review == null

    fun startScanner() {
        lastScannerActivityAt = System.currentTimeMillis()
        scannerRunning = true
    }

    fun pauseScanner() {
        scannerRunning = false
        torchOn = false
    }

    LaunchedEffect(scannerRunning, lastScannerActivityAt, review) {
        if (!scannerRunning || review != null) return@LaunchedEffect
        val marker = lastScannerActivityAt
        delay(ScannerIdlePauseMs)
        if (scannerRunning && controller.scanReview == null && lastScannerActivityAt == marker) {
            pauseScanner()
            controller.reportError("20 秒未扫码，已暂停相机。")
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(bottom = if (sessionActive) 230.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ScanModeBar(controller) }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    ScannerPreview(
                        running = analyzerRunning,
                        torchOn = torchOn,
                        pausedMessage = if (review != null) "请先确认本次扫码" else "相机已暂停，点击开始扫描",
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
                    Button(onClick = ::startScanner) { Text("开始扫描") }
                    OutlinedButton(onClick = ::pauseScanner) { Text("暂停") }
                    OutlinedButton(onClick = { torchOn = !torchOn }, enabled = analyzerRunning) {
                        Text(if (torchOn) "关灯" else "手电筒")
                    }
                }
            }
            item { ScanSteps(controller) }
            if (!sessionActive) {
                item { IdlePanel(controller) }
            }
            item {
                OutlinedButton(onClick = { showHistory = !showHistory }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showHistory) "收起最近扫码" else "最近扫码")
                }
            }
            if (showHistory) {
                items(controller.snapshot.scanLog.take(8)) { log ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(log.parsedId.ifBlank { log.parsedType.ifBlank { "未知内容" } })
                            Text(
                                listOf(log.result, log.createdAt).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (log.message.isNotBlank()) {
                                Text(log.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        if (sessionActive) {
            ScanSessionPanel(
                controller = controller,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }

    review?.let { ScanReviewSheet(controller, it) }
}

@Composable
private fun ScanModeBar(controller: InventoryController) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScanMode.entries.forEach { mode ->
            FilterChip(
                selected = controller.scanMode == mode,
                onClick = { controller.requestScanMode(mode) },
                enabled = controller.scanReview == null,
                label = { Text(mode.label) },
            )
        }
    }
}

@Composable
private fun ScanSteps(controller: InventoryController) {
    val steps = when (controller.scanMode) {
        ScanMode.StockIn -> listOf(
            "物品" to (controller.pendingItem != null),
            "重量/数量" to (
                controller.pendingWeightG != null ||
                    controller.pendingQty != null ||
                    controller.pendingItem?.type == ItemType.Other
                ),
            "库位" to (controller.pendingLocation != null),
            "确认" to controller.canStockIn(),
        )
        ScanMode.Stocktake -> listOf(
            "物品" to (controller.activeItem != null),
            "重量/数量" to (controller.pendingWeightG != null || controller.pendingQty != null),
            "确认" to controller.canStocktake(),
        )
        ScanMode.BindLocation -> listOf(
            "物品" to (controller.activeItem != null),
            "库位" to (controller.pendingLocation != null),
            "确认" to controller.canMove(),
        )
    }
    val currentIndex = steps.indexOfFirst { !it.second }.let { if (it < 0) steps.lastIndex else it }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEachIndexed { index, (label, done) ->
            val current = index == currentIndex
            val background = when {
                done -> Color(0xFFD1FAE5)
                current -> Color(0xFFDBEAFE)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val foreground = when {
                done -> Color(0xFF065F46)
                current -> Color(0xFF1D4ED8)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .background(background, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(label, color = foreground, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun IdlePanel(controller: InventoryController) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(controller.message)
            if (controller.lastUndo != null) {
                OutlinedButton(onClick = controller::undoLast) { Text("撤销上一笔") }
            }
        }
    }
}

@Composable
private fun ScanSessionPanel(controller: InventoryController, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFFBEB)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("${controller.scanMode.label}流程", style = MaterialTheme.typography.titleMedium)
            Text(controller.message, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            WorkflowInfoRow("物品", controller.pendingItem?.let { "${it.id} · ${it.displayName}" } ?: "未确认")
            if (controller.scanMode != ScanMode.BindLocation) {
                WorkflowInfoRow("重量", controller.pendingWeightG?.let { "${it.gText()}g" } ?: "未确认")
                WorkflowInfoRow("数量", controller.pendingQty?.toString() ?: "未记录")
            }
            WorkflowInfoRow("库位", controller.pendingLocation?.name ?: "未确认")
            controller.sortingLocation?.let { WorkflowInfoRow("整理中", it.name) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (controller.scanMode) {
                    ScanMode.StockIn -> Button(onClick = controller::stockIn, enabled = controller.canStockIn()) { Text("确认入库") }
                    ScanMode.Stocktake -> Button(onClick = controller::stocktake, enabled = controller.canStocktake()) { Text("确认更新") }
                    ScanMode.BindLocation -> {
                        Button(onClick = controller::moveActive, enabled = controller.canMove()) { Text("确认绑定") }
                        OutlinedButton(
                            onClick = controller::startLocationSorting,
                            enabled = controller.pendingLocation != null && controller.sortingLocation == null,
                        ) { Text("整理该库位") }
                        if (controller.sortingLocation != null) {
                            OutlinedButton(onClick = controller::stopLocationSorting) { Text("完成整理") }
                        }
                    }
                }
                OutlinedButton(onClick = controller::clearContext) { Text("取消流程") }
            }
        }
    }
}

@Composable
private fun ScanReviewSheet(controller: InventoryController, review: ScanReview) {
    ModalBottomSheet(onDismissRequest = controller::cancelScanReview) {
        when (review) {
            is ScanReview.Item -> ItemReviewContent(controller, review)
            is ScanReview.Weight -> WeightReviewContent(controller, review)
            is ScanReview.Location -> LocationReviewContent(controller, review)
            is ScanReview.ModeSwitch -> ModeSwitchContent(controller, review)
        }
    }
}

@Composable
private fun ItemReviewContent(controller: InventoryController, review: ScanReview.Item) {
    val scanned = review.scanned
    var name by remember(review) { mutableStateOf(scanned.name) }
    var brand by remember(review) { mutableStateOf(scanned.brand) }
    var material by remember(review) { mutableStateOf(scanned.material) }
    var color by remember(review) { mutableStateOf(scanned.color) }
    var tare by remember(review) { mutableStateOf(scanned.tareG?.gText().orEmpty()) }
    var unitWeight by remember(review) { mutableStateOf(scanned.unitWeightG?.gText().orEmpty()) }
    var note by remember(review) { mutableStateOf(scanned.note) }

    val edited = scanned.copy(
        name = name.trim(),
        brand = brand.trim(),
        material = material.trim(),
        color = color.trim(),
        tareG = tare.toDoubleOrNull(),
        unitWeightG = unitWeight.toDoubleOrNull(),
        note = note.trim(),
    )
    val missing = edited.missingRequiredFields()
    val modeAllowsItem = when (controller.scanMode) {
        ScanMode.StockIn -> review.local?.state?.status != StockStatus.InStock
        ScanMode.Stocktake, ScanMode.BindLocation -> review.local != null
    }

    ReviewColumn {
        Text("确认物品", style = MaterialTheme.typography.titleLarge)
        Text("${edited.id} · ${edited.type.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (review.replacesCurrentItem) {
            Text("确认后会替换当前未完成物品，并清空已扫重量和库位。", color = MaterialTheme.colorScheme.error)
        }
        if (!modeAllowsItem) {
            Text(
                if (controller.scanMode == ScanMode.StockIn) {
                    "该物品已经在库，请切换到更新库存或绑定库位。"
                } else {
                    "该物品还没有入库，不能执行${controller.scanMode.label}。"
                },
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (review.hasFixedConflict) {
            Text("本地固定信息与标签不同。", color = MaterialTheme.colorScheme.error)
            Text("本地：${review.local?.fixed?.displayName}")
            Text("扫码：${scanned.displayName}")
        }
        when (scanned.type) {
            ItemType.Spool -> {
                ReviewTextField("品牌", brand, { brand = it })
                ReviewTextField("材料", material, { material = it })
                ReviewTextField("颜色", color, { color = it })
                ReviewNumberField("空盘重量 g", tare, { tare = it })
            }
            ItemType.Part -> {
                ReviewTextField("名称", name, { name = it })
                ReviewNumberField("单件重量 g", unitWeight, { unitWeight = it })
            }
            ItemType.Other -> ReviewTextField("名称", name, { name = it })
            ItemType.Location, ItemType.Weight -> Unit
        }
        ReviewTextField("备注", note, { note = it })
        if (missing.isNotEmpty()) {
            Text("还缺：${missing.joinToString()}", color = MaterialTheme.colorScheme.error)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { controller.confirmItemReview(edited) },
                enabled = missing.isEmpty() && modeAllowsItem,
            ) { Text("确认扫码信息") }
            if (review.hasFixedConflict) {
                OutlinedButton(onClick = { controller.confirmItemReview(edited, keepLocal = true) }) {
                    Text("使用本地信息")
                }
            }
            OutlinedButton(onClick = controller::cancelScanReview) { Text("取消本次扫码") }
        }
    }
}

@Composable
private fun WeightReviewContent(controller: InventoryController, review: ScanReview.Weight) {
    val fixed = controller.pendingItem
    val valid = when (fixed?.type) {
        ItemType.Spool -> fixed.tareG != null && review.valueG > fixed.tareG
        ItemType.Part -> fixed.unitWeightG != null && fixed.unitWeightG > 0.0
        else -> true
    }
    ReviewColumn {
        Text("确认重量", style = MaterialTheme.typography.titleLarge)
        Text("${review.valueG.gText()}g", style = MaterialTheme.typography.headlineSmall)
        if (review.replacesCurrentWeight) {
            Text("确认后会替换当前流程里的重量。", color = MaterialTheme.colorScheme.error)
        }
        when (fixed?.type) {
            ItemType.Spool -> {
                WorkflowInfoRow("空盘", fixed.tareG?.let { "${it.gText()}g" } ?: "未记录")
                WorkflowInfoRow(
                    "可用",
                    fixed.tareG?.let { "${round1(review.valueG - it).gText()}g" } ?: "无法计算",
                )
            }
            ItemType.Part -> {
                WorkflowInfoRow("单重", fixed.unitWeightG?.let { "${it.gText()}g" } ?: "未记录")
                WorkflowInfoRow("估算", quantityFromWeight(review.valueG, fixed.unitWeightG)?.let { "$it 件" } ?: "无法计算")
            }
            else -> Unit
        }
        if (!valid) {
            Text("当前物品参数不足或重量无效，请取消后先确认物品信息。", color = MaterialTheme.colorScheme.error)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = controller::confirmWeightReview, enabled = valid) { Text("确认重量") }
            OutlinedButton(onClick = controller::cancelScanReview) { Text("取消本次扫码") }
        }
    }
}

@Composable
private fun LocationReviewContent(controller: InventoryController, review: ScanReview.Location) {
    var name by remember(review) { mutableStateOf(review.suggestedName) }
    ReviewColumn {
        Text("确认库位", style = MaterialTheme.typography.titleLarge)
        Text(review.id, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (review.replacesCurrentLocation) {
            Text("确认后会替换当前流程里的库位。", color = MaterialTheme.colorScheme.error)
        }
        ReviewTextField("库位名称", name, { name = it })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { controller.confirmLocationReview(name) }, enabled = name.isNotBlank()) {
                Text("确认库位")
            }
            OutlinedButton(onClick = controller::cancelScanReview) { Text("取消本次扫码") }
        }
    }
}

@Composable
private fun ModeSwitchContent(controller: InventoryController, review: ScanReview.ModeSwitch) {
    ReviewColumn {
        Text("切换到${review.target.label}？", style = MaterialTheme.typography.titleLarge)
        Text("当前未完成流程会被清空。")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = controller::confirmModeSwitch) { Text("清空并切换") }
            OutlinedButton(onClick = controller::cancelScanReview) { Text("继续当前流程") }
        }
    }
}

@Composable
private fun ReviewColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
        Box(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ReviewTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReviewNumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WorkflowInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.width(68.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f))
    }
}
