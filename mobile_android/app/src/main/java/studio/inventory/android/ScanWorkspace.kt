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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
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
    var sessionPanelHeightPx by remember { mutableIntStateOf(0) }
    val review = controller.scanReview
    val sessionActive = controller.scanState.hasSession
    val analyzerRunning = scannerRunning && review == null
    val density = LocalDensity.current
    val sessionBottomPadding = if (sessionActive) {
        with(density) { sessionPanelHeightPx.toDp() + 16.dp }
    } else {
        12.dp
    }

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
            contentPadding = PaddingValues(bottom = sessionBottomPadding),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { if (scannerRunning) pauseScanner() else startScanner() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        if (!scannerRunning) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                        }
                        Text(if (scannerRunning) "暂停相机" else "开始扫描")
                    }
                    OutlinedButton(
                        onClick = { torchOn = !torchOn },
                        enabled = analyzerRunning,
                        modifier = Modifier
                            .width(112.dp)
                            .height(48.dp),
                    ) {
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
                    .onSizeChanged { sessionPanelHeightPx = it.height }
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
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
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
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val sorting = controller.sortingLocation
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (sorting == null) controller.scanMode.label else "整理库位",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    " · ${controller.message}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (sorting != null) {
                Text(
                    "目标 · ${sorting.name}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactButton(onClick = controller::stopLocationSorting, label = "完成")
                    CompactOutlinedButton(onClick = controller::clearContext, label = "取消")
                }
            } else {
                val item = controller.pendingItem
                Text(
                    item?.let { "${it.id} · ${it.displayName}" } ?: "物品未确认",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    compactSessionStatus(controller),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (controller.scanMode) {
                        ScanMode.StockIn -> CompactButton(
                            onClick = controller::stockIn,
                            label = "入库",
                            enabled = controller.canStockIn(),
                        )
                        ScanMode.Stocktake -> CompactButton(
                            onClick = controller::stocktake,
                            label = "更新",
                            enabled = controller.canStocktake(),
                        )
                        ScanMode.BindLocation -> {
                            if (controller.pendingItem != null) {
                                CompactButton(
                                    onClick = controller::moveActive,
                                    label = "绑定",
                                    enabled = controller.canMove(),
                                )
                            }
                            if (controller.pendingItem == null && controller.pendingLocation != null) {
                                CompactOutlinedButton(
                                onClick = controller::startLocationSorting,
                                    label = "整理库位",
                                )
                            }
                        }
                    }
                    CompactOutlinedButton(onClick = controller::clearContext, label = "取消")
                }
            }
        }
    }
}

private fun compactSessionStatus(controller: InventoryController): String {
    val values = mutableListOf<String>()
    val item = controller.pendingItem
    if (controller.scanMode != ScanMode.BindLocation) {
        when (item?.type) {
            ItemType.Spool -> values += "重量 ${controller.pendingWeightG?.let { "${it.gText()}g" } ?: "未确认"}"
            ItemType.Part -> {
                values += "重量 ${controller.pendingWeightG?.let { "${it.gText()}g" } ?: "未确认"}"
                values += "数量 ${controller.pendingQty ?: "未记录"}"
            }
            ItemType.Other -> Unit
            ItemType.Location, ItemType.Weight -> Unit
            null -> controller.pendingWeightG?.let { values += "重量 ${it.gText()}g" }
        }
    }
    if (controller.scanMode != ScanMode.Stocktake) {
        values += "库位 ${controller.pendingLocation?.name ?: "未确认"}"
    }
    return values.ifEmpty { listOf("继续扫码补齐当前流程") }.joinToString(" · ")
}

@Composable
private fun CompactButton(onClick: () -> Unit, label: String, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { Text(label) }
}

@Composable
private fun CompactOutlinedButton(onClick: () -> Unit, label: String, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { Text(label) }
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
    var tare by remember(review) { mutableStateOf(scanned.tareG?.parameterText().orEmpty()) }
    var unitWeight by remember(review) { mutableStateOf(scanned.unitWeightG?.parameterText().orEmpty()) }
    var note by remember(review) { mutableStateOf(scanned.note) }
    var editing by remember(review) { mutableStateOf(scanned.missingRequiredFields().isNotEmpty()) }

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
    val localStatus = review.local?.state?.status
    val modeAllowsItem = ScanWorkflowRules.allowsItem(controller.scanMode, localStatus)

    ReviewColumn {
        Text("确认物品", style = MaterialTheme.typography.titleLarge)
        Text("${edited.id} · ${edited.type.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (review.replacesCurrentItem) {
            Text("确认后会替换当前未完成物品，并清空已扫重量和库位。", color = MaterialTheme.colorScheme.error)
        }
        if (!modeAllowsItem) {
            Text(
                if (localStatus == StockStatus.Archived) {
                    "该物品已归档，不能继续扫码操作。"
                } else if (controller.scanMode == ScanMode.StockIn) {
                    "该物品已经在库，请切换到更新库存或绑定库位。"
                } else {
                    "只有在库物品可以执行${controller.scanMode.label}。"
                },
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (review.hasFixedConflict) {
            Text("本地固定信息与标签不同。", color = MaterialTheme.colorScheme.error)
            Text("本地：${review.local?.fixed?.compactDescription()}")
            Text("扫码：${scanned.compactDescription()}")
        }
        if (editing) {
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
        } else {
            FixedReviewSummary(edited)
            TextButton(onClick = { editing = true }) { Text("编辑信息") }
        }
        if (missing.isNotEmpty()) {
            Text("还缺：${missing.joinToString()}", color = MaterialTheme.colorScheme.error)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { controller.confirmItemReview(edited) },
                enabled = missing.isEmpty() && modeAllowsItem,
            ) { Text("确认") }
            if (review.hasFixedConflict) {
                OutlinedButton(
                    onClick = { controller.confirmItemReview(edited, keepLocal = true) },
                    enabled = modeAllowsItem,
                ) {
                    Text("使用本地信息")
                }
            }
            OutlinedButton(onClick = controller::cancelScanReview) { Text("取消") }
        }
    }
}

@Composable
private fun FixedReviewSummary(fixed: FixedData) {
    when (fixed.type) {
        ItemType.Spool -> {
            Text(fixed.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                "空盘 ${fixed.tareG?.let { "${it.parameterText()}g" } ?: "未记录"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ItemType.Part -> {
            Text(fixed.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                "单重 ${fixed.unitWeightG?.let { "${it.parameterText()}g" } ?: "未记录"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ItemType.Other -> Text(fixed.displayName, style = MaterialTheme.typography.titleMedium)
        ItemType.Location, ItemType.Weight -> Unit
    }
    if (fixed.note.isNotBlank()) {
        Text(
            fixed.note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun FixedData.compactDescription(): String = when (type) {
    ItemType.Spool -> "$displayName · 空盘 ${tareG?.let { "${it.parameterText()}g" } ?: "未记录"}"
    ItemType.Part -> "$displayName · 单重 ${unitWeightG?.let { "${it.parameterText()}g" } ?: "未记录"}"
    ItemType.Other, ItemType.Location, ItemType.Weight -> displayName
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
                WorkflowInfoRow("空盘", fixed.tareG?.let { "${it.parameterText()}g" } ?: "未记录")
                WorkflowInfoRow(
                    "可用",
                    fixed.tareG?.let { "${round1(review.valueG - it).gText()}g" } ?: "无法计算",
                )
            }
            ItemType.Part -> {
                WorkflowInfoRow("单重", fixed.unitWeightG?.let { "${it.parameterText()}g" } ?: "未记录")
                WorkflowInfoRow("估算", quantityFromWeight(review.valueG, fixed.unitWeightG)?.let { "$it 件" } ?: "无法计算")
            }
            else -> Unit
        }
        if (!valid) {
            Text("当前物品参数不足或重量无效，请取消后先确认物品信息。", color = MaterialTheme.colorScheme.error)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = controller::confirmWeightReview, enabled = valid) { Text("确认") }
            OutlinedButton(onClick = controller::cancelScanReview) { Text("取消") }
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
                Text("确认")
            }
            OutlinedButton(onClick = controller::cancelScanReview) { Text("取消") }
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
        Text(value, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
