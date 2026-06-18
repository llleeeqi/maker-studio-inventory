@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package studio.inventory.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ScanPage(controller: InventoryController, modifier: Modifier = Modifier) {
    var scannerRunning by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var manualOpen by remember { mutableStateOf(false) }

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
                    onPayload = controller::handlePayload,
                    onError = controller::reportError,
                    onPermissionGranted = {
                        scannerRunning = true
                        controller.reportError("相机权限已授权，正在启动扫码。")
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (!scannerRunning) {
                    Text("点“开始扫码”打开相机", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { scannerRunning = true }) { Text("开始扫码") }
                OutlinedButton(onClick = {
                    scannerRunning = false
                    torchOn = false
                }) { Text("停止") }
                OutlinedButton(onClick = { torchOn = !torchOn }, enabled = scannerRunning) {
                    Text(if (torchOn) "关灯" else "手电筒")
                }
                OutlinedButton(onClick = { manualOpen = true }) { Text("手动输入") }
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

    if (manualOpen) {
        ManualInputDialog(
            onDismiss = { manualOpen = false },
            onPayload = {
                controller.handlePayload(it)
                manualOpen = false
            },
            onWeight = {
                controller.setManualWeight(it)
                manualOpen = false
            },
            onQty = {
                controller.setManualQty(it)
                manualOpen = false
            },
        )
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
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var type by remember { mutableStateOf(ItemType.Spool) }
    var id by remember { mutableStateOf(nextAutoId(type, controller.snapshot.items.keys)) }
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var material by remember { mutableStateOf("PLA") }
    var color by remember { mutableStateOf("") }
    var tareG by remember { mutableStateOf("") }
    var fullG by remember { mutableStateOf("") }
    var netG by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var spec by remember { mutableStateOf("") }
    var unitWeightG by remember { mutableStateOf("") }
    var weightValue by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var generatedPayload by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(type) {
        id = nextAutoId(type, controller.snapshot.items.keys)
        generatedPayload = ""
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
                NumberTextField("满卷总重 full_g（可选）", fullG, { fullG = it })
                NumberTextField("标称净重 net_g（可选）", netG, { netG = it })
            }
            ItemType.Part -> {
                FormTextField("名称 name", name, { name = it })
                FormTextField("类别 category", category, { category = it })
                FormTextField("规格 spec", spec, { spec = it })
                FormTextField("颜色 color（可选）", color, { color = it })
                NumberTextField("单件重量 unit_weight_g（可选）", unitWeightG, { unitWeightG = it })
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
                generatedPayload = buildLabelPayload(
                    type = type,
                    id = id,
                    name = name,
                    brand = brand,
                    material = material,
                    color = color,
                    tareG = tareG,
                    fullG = fullG,
                    netG = netG,
                    category = category,
                    spec = spec,
                    unitWeightG = unitWeightG,
                    weightValue = weightValue,
                    note = note,
                )
            }) {
                Text("生成 payload")
            }
            OutlinedButton(onClick = {
                if (generatedPayload.isNotBlank()) {
                    clipboard.setText(AnnotatedString(generatedPayload))
                }
            }) { Text("复制") }
            OutlinedButton(onClick = {
                generatedPayload = ""
                name = ""
                brand = ""
                color = ""
                tareG = ""
                fullG = ""
                netG = ""
                category = ""
                spec = ""
                unitWeightG = ""
                weightValue = ""
                note = ""
            }) { Text("清空") }
            OutlinedButton(onClick = {
                scope.launch {
                    snackbarHostState.showSnackbar("打印先占位，后面接打印能力。")
                }
            }) { Text("打印") }
        }
        if (generatedPayload.isNotBlank()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("生成结果", style = MaterialTheme.typography.titleMedium)
                    Text(generatedPayload)
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
private fun ManualInputDialog(
    onDismiss: () -> Unit,
    onPayload: (String) -> Unit,
    onWeight: (Double) -> Unit,
    onQty: (Int) -> Unit,
) {
    var payload by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动兜底") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FormTextField("payload", payload, { payload = it })
                NumberTextField("重量 g", weight, { weight = it })
                NumberTextField("数量", qty, { qty = it })
            }
        },
        confirmButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onPayload(payload) }, enabled = payload.isNotBlank()) { Text("处理 payload") }
                TextButton(onClick = { weight.toDoubleOrNull()?.let(onWeight) }, enabled = weight.toDoubleOrNull() != null) { Text("填重量") }
                TextButton(onClick = { qty.toIntOrNull()?.let(onQty) }, enabled = qty.toIntOrNull() != null) { Text("填数量") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
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
    fullG: String,
    netG: String,
    category: String,
    spec: String,
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
            fields["full_g"] = fullG
            fields["net_g"] = netG
            fields["name"] = listOf(brand, material, color).filter { it.isNotBlank() }.joinToString(" ")
            fields["note"] = note
        }
        ItemType.Part -> {
            fields["id"] = id.trim().uppercase()
            fields["name"] = name
            fields["category"] = category
            fields["spec"] = spec
            fields["color"] = color
            fields["unit_weight_g"] = unitWeightG
            fields["note"] = note
        }
        ItemType.Other, ItemType.Location -> {
            fields["id"] = id.trim().uppercase()
            fields["name"] = name
            fields["note"] = note
        }
        ItemType.Weight -> {
            fields["value_g"] = weightValue
        }
    }
    return buildMsiPayload(fields)
}
