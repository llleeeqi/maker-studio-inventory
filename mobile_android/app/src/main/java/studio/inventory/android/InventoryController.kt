package studio.inventory.android

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

data class UndoRecord(
    val action: String,
    val itemId: String,
    val before: InventoryItem?,
    val after: InventoryItem?,
)

class InventoryController(context: Context) {
    private val appContext = context.applicationContext
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val database = InventoryDatabase(appContext, gson)
    private val snapshotFile = File(appContext.filesDir, "inventory_snapshot.json")
    private var localDeviceId: String = ""
    private var localDeviceName: String = ""

    var onInventoryChanged: (() -> Unit)? = null
    var isItemSyncBlocked: (String) -> Boolean = { false }
    var onSyncConflictResolved: ((String) -> Unit)? = null

    var conflictResolutionItemId by mutableStateOf<String?>(null)
        private set

    var snapshot by mutableStateOf(InventorySnapshot())
        private set

    var message by mutableStateOf("扫 v1 物品、重量或库位码。")
        private set

    var scanState by mutableStateOf(ScanWorkflowState())
        private set

    var lastUndo by mutableStateOf<UndoRecord?>(null)
        private set

    val scanMode: ScanMode
        get() = scanState.mode

    val pendingItem: FixedData?
        get() = scanState.item

    val pendingWeightG: Double?
        get() = scanState.weightG

    val pendingQty: Int?
        get() = scanState.quantity

    val pendingLocation: LocationValue?
        get() = scanState.location

    val sortingLocation: LocationValue?
        get() = scanState.sortingLocation

    val scanReview: ScanReview?
        get() = scanState.review

    val activeItem: InventoryItem?
        get() = pendingItem?.id?.let { snapshot.items[it] }

    fun load() {
        snapshot = runCatching {
            if (database.hasData()) {
                return@runCatching database.loadSnapshot().trimmed()
            }
            val migrated = if (snapshotFile.exists()) {
                gson.fromJson(snapshotFile.readText(), InventorySnapshot::class.java) ?: InventorySnapshot()
            } else {
                InventorySnapshot()
            }
            migrated.trimmed().also {
                database.replaceAll(it)
                if (snapshotFile.exists()) {
                    message = "已迁移旧 JSON 测试数据到本地数据库。"
                }
            }
        }.getOrElse {
            message = "读取本地数据库失败，已使用空库存：${it.message}"
            InventorySnapshot()
        }
    }

    fun configureLocalDevice(deviceId: String, deviceName: String) {
        localDeviceId = deviceId
        localDeviceName = deviceName
    }

    fun beginSyncConflictResolution(itemId: String) {
        conflictResolutionItemId = itemId
        scanState = ScanWorkflowState(mode = ScanMode.StockIn)
        message = "同步异常确认：重新扫描该物品、重量或数量，再扫描库位。"
    }

    fun cancelSyncConflictResolution() {
        conflictResolutionItemId = null
        scanState = scanState.clearedForMode(ScanMode.StockIn)
        message = "已取消同步异常确认。"
    }

    fun replaceFromSync(incoming: InventorySnapshot) {
        snapshot = incoming.copy(scanLog = snapshot.scanLog).trimmed()
        runCatching { database.replaceAll(snapshot) }
            .onFailure { message = "写入同步结果失败：${it.message}" }
    }

    fun replaceFromBackup(incoming: InventorySnapshot) {
        snapshot = incoming.trimmed()
        runCatching { database.replaceAll(snapshot) }
            .onFailure { message = "恢复备份失败：${it.message}" }
    }

    fun applySyncConflictCandidate(conflict: SyncConflictCandidate, useRemote: Boolean): Boolean {
        val selectedJson = if (useRemote) conflict.remoteJson else conflict.localJson
        return runCatching {
            when (conflict.entityType) {
                SyncEntityType.Location -> {
                    val locations = snapshot.locations.toMutableMap()
                    val items = snapshot.items.toMutableMap()
                    if (selectedJson == null) {
                        require(items.values.none { it.state.locationId == conflict.entityId }) {
                            "仍有物品使用该库位，不能采用删除结果。"
                        }
                        locations.remove(conflict.entityId)
                    } else {
                        val location = gson.fromJson(selectedJson, LocationValue::class.java)
                        locations[location.id] = location
                        items.replaceAll { _, item ->
                            if (item.state.locationId == location.id) {
                                item.copy(state = item.state.copy(locationName = location.name, updatedAt = nowIso()))
                            } else {
                                item
                            }
                        }
                    }
                    snapshot = snapshot.copy(items = items, locations = locations).trimmed()
                }
                SyncEntityType.Transaction -> {
                    require(selectedJson != null) { "主流水不能通过冲突处理删除。" }
                    val selected = gson.fromJson(selectedJson, InventoryTransaction::class.java)
                    val transactions = snapshot.transactions.associateBy { it.txId }.toMutableMap()
                    transactions[selected.txId] = selected
                    snapshot = snapshot.copy(
                        transactions = transactions.values.sortedWith(
                            compareBy<InventoryTransaction> { it.createdAt }.thenBy { it.txId },
                        ),
                    ).trimmed()
                }
                SyncEntityType.Item -> error("物品冲突必须通过扫码重新确认。")
                SyncEntityType.Conflict -> error("不支持嵌套冲突。")
            }
            database.replaceAll(snapshot)
            onInventoryChanged?.invoke()
            message = "同步异常已按${if (useRemote) "云端" else "本机"}版本处理。"
        }.onFailure {
            message = it.message ?: "处理同步异常失败。"
        }.isSuccess
    }

    fun requestScanMode(mode: ScanMode) {
        if (mode == scanMode) return
        if (scanState.hasSession) {
            scanState = scanState.copy(review = ScanReview.ModeSwitch(mode))
            message = "当前扫码流程未完成，确认后才切换模式。"
            return
        }
        applyScanMode(mode)
    }

    fun confirmModeSwitch() {
        val review = scanReview as? ScanReview.ModeSwitch ?: return
        scanState = scanState.clearedForMode(review.target)
        message = modeMessage(review.target)
    }

    fun handlePayload(payload: String) {
        val clean = payload.trim()
        if (clean.isEmpty() || scanReview != null) return
        val parsed = parseV1Payload(clean)
        if (parsed.type == null) {
            addScanLog(clean, parsed, "rejected", "无法识别，只支持 v1。")
            message = "无法识别，只支持 v1。"
            return
        }

        when (parsed.type) {
            ItemType.Weight -> prepareWeightReview(clean, parsed)
            ItemType.Location -> prepareLocationReview(clean, parsed)
            ItemType.Spool, ItemType.Part, ItemType.Other -> prepareItemReview(clean, parsed)
        }
    }

    fun reportError(text: String) {
        message = text
    }

    private fun applyScanMode(mode: ScanMode) {
        scanState = scanState.clearedForMode(mode)
        message = modeMessage(mode)
    }

    private fun modeMessage(mode: ScanMode): String = when (mode) {
        ScanMode.StockIn -> "入库模式：物品码 -> 重量码/数量 -> 库位码 -> 入库确认。"
        ScanMode.Stocktake -> "更新库存模式：物品码 -> 重量码/数量 -> 更新确认。"
        ScanMode.BindLocation -> "绑定库位模式：物品码 -> 库位码 -> 绑定确认。"
    }

    private fun prepareWeightReview(raw: String, parsed: ParsedPayload) {
        if (scanMode == ScanMode.BindLocation) {
            addScanLog(raw, parsed, "ignored", "绑定库位模式不需要重量")
            message = "绑定库位模式不需要重量码。"
            return
        }
        val value = parsed.weightG
        if (value == null || value <= 0.0) {
            addScanLog(raw, parsed, "rejected", "重量码缺少有效 value_g。")
            message = "重量码缺少有效 value_g。"
            return
        }
        val rounded = round1(value)
        scanState = scanState.copy(
            review = ScanReview.Weight(
                valueG = rounded,
                replacesCurrentWeight = pendingWeightG != null && pendingWeightG != rounded,
            ),
        )
        addScanLog(raw, parsed, "waiting_confirmation", "等待确认重量 ${rounded.gText()}g")
        message = "已识别重量 ${rounded.gText()}g，确认后才进入当前流程。"
    }

    private fun prepareLocationReview(raw: String, parsed: ParsedPayload) {
        if (scanMode == ScanMode.Stocktake) {
            addScanLog(raw, parsed, "ignored", "更新库存模式不需要库位")
            message = "更新库存模式不需要库位码。"
            return
        }
        val fixed = parsed.fixed
        if (fixed == null || fixed.id.isBlank()) {
            addScanLog(raw, parsed, "rejected", "库位码缺少 id。")
            message = "库位码缺少 id。"
            return
        }
        val location = resolveLocation(fixed)
        scanState = scanState.copy(
            review = ScanReview.Location(
                id = location.id,
                suggestedName = location.name,
                replacesCurrentLocation = pendingLocation != null && pendingLocation != location,
            ),
        )
        addScanLog(raw, parsed, "waiting_confirmation", "等待确认库位 ${location.id}")
        message = "已识别库位 ${location.id}，确认名称和用途后继续。"
    }

    private fun prepareItemReview(raw: String, parsed: ParsedPayload) {
        val fixed = parsed.fixed
        if (fixed == null || fixed.id.isBlank()) {
            addScanLog(raw, parsed, "rejected", "物品码缺少 id。")
            message = "物品码缺少 id。"
            return
        }
        if (isItemSyncBlocked(fixed.id) && conflictResolutionItemId != fixed.id) {
            addScanLog(raw, parsed, "rejected", "物品存在同步异常，需要重新确认")
            message = "${fixed.displayName} 存在同步异常，请从同步中心重新确认。"
            return
        }

        val sorting = sortingLocation
        if (sorting != null) {
            moveScannedItemDuringSorting(raw, parsed, fixed, sorting)
            return
        }

        val existing = snapshot.items[fixed.id]
        scanState = scanState.copy(
            review = ScanReview.Item(
                scanned = fixed,
                local = existing,
                replacesCurrentItem = pendingItem != null && pendingItem?.id != fixed.id,
            ),
        )
        addScanLog(raw, parsed, "waiting_confirmation", "等待确认物品 ${fixed.id}")
        message = "已识别 ${fixed.displayName}，确认固定信息后继续。"
    }

    private fun moveScannedItemDuringSorting(
        raw: String,
        parsed: ParsedPayload,
        fixed: FixedData,
        location: LocationValue,
    ) {
        val existing = snapshot.items[fixed.id]
        when (ScanWorkflowRules.sortingDisposition(existing, location)) {
            SortingDisposition.Missing -> {
                addScanLog(raw, parsed, "rejected", "物品未入库，不能整理库位")
                message = "${fixed.displayName} 未入库，不能整理到库位。"
                return
            }
            SortingDisposition.NotInStock -> {
                val known = requireNotNull(existing)
                addScanLog(raw, parsed, "rejected", "物品状态为 ${known.state.status.value}")
                message = "${known.fixed.displayName} 当前${known.state.status.label}，未移动。"
                return
            }
            SortingDisposition.AlreadyThere -> {
                val known = requireNotNull(existing)
                addScanLog(raw, parsed, "ignored", "物品已经在 ${location.name}")
                message = "${known.fixed.displayName} 已经在 ${location.name}，已跳过。"
                return
            }
            SortingDisposition.Move -> Unit
        }
        val known = requireNotNull(existing)
        val updated = known.copy(
            state = known.state.copy(
                locationId = location.id,
                locationName = location.name,
                updatedAt = nowIso(),
            ),
        )
        saveItemOnly(updated)
        addScanLog(raw, parsed, "accepted", "整理到 ${location.name}")
        message = "${updated.fixed.displayName} 已整理到 ${location.name}。"
        signal()
    }

    fun confirmItemReview(edited: FixedData, keepLocal: Boolean = false) {
        val review = scanReview as? ScanReview.Item ?: return
        val localStatus = review.local?.state?.status
        if (localStatus == StockStatus.Archived) {
            message = "该物品已归档，不能继续扫码操作。"
            return
        }
        val resolvingConflict = conflictResolutionItemId == review.scanned.id
        if (!resolvingConflict && !ScanWorkflowRules.allowsItem(scanMode, localStatus) && scanMode != ScanMode.StockIn) {
            message = "只有在库物品可以执行${scanMode.label}。"
            return
        }
        if (!resolvingConflict && !ScanWorkflowRules.allowsItem(scanMode, localStatus)) {
            message = "该物品已经在库，请切换到更新库存或绑定库位。"
            return
        }
        val selected = if (keepLocal && review.local != null) review.local.fixed else edited
        val missing = selected.missingRequiredFields()
        if (missing.isNotEmpty()) {
            message = "还缺必填信息：${missing.joinToString()}。"
            return
        }

        val local = review.local
        if (!keepLocal && local != null && !local.fixed.equivalentTo(selected)) {
            saveItemOnly(local.copy(type = selected.type, fixed = selected))
        }

        scanState = ScanWorkflowRules.confirmItem(scanState, selected)
        message = if (local == null) {
            "已确认 ${selected.displayName}，继续补齐当前流程。"
        } else {
            "已确认 ${selected.displayName}：${snapshot.items[selected.id]?.stockText ?: local.stockText}。"
        }
        signal()
    }

    fun confirmWeightReview() {
        val review = scanReview as? ScanReview.Weight ?: return
        val fixed = pendingItem
        if (fixed?.type == ItemType.Spool) {
            val tare = fixed.tareG
            if (tare == null || review.valueG <= tare) {
                message = "毛重必须大于空盘重量。"
                return
            }
        }
        if (fixed?.type == ItemType.Part && (fixed.unitWeightG == null || fixed.unitWeightG <= 0.0)) {
            message = "零件缺少单重，请先确认物品信息。"
            return
        }
        scanState = scanState.copy(
            weightG = review.valueG,
            quantity = if (fixed?.type == ItemType.Part) quantityFromWeight(review.valueG, fixed.unitWeightG) else pendingQty,
            review = null,
        )
        message = "已确认重量 ${review.valueG.gText()}g。"
        signal()
    }

    fun confirmManualMeasurement(weightG: Double?, quantity: Int?): Boolean {
        val fixed = pendingItem
        if (fixed == null) {
            message = "先扫描并确认物品，再手动录入重量或数量。"
            return false
        }
        if (scanMode == ScanMode.BindLocation) {
            message = "绑定库位模式不需要重量或数量。"
            return false
        }

        when (fixed.type) {
            ItemType.Spool -> {
                val weight = weightG?.takeIf { it > 0.0 }
                val tare = fixed.tareG
                if (weight == null) {
                    message = "请输入有效毛重。"
                    return false
                }
                if (tare == null || weight <= tare) {
                    message = "毛重必须大于空盘重量。"
                    return false
                }
                scanState = scanState.copy(weightG = round1(weight), quantity = null, review = null)
                message = "已手动录入毛重 ${round1(weight).gText()}g。"
            }
            ItemType.Part -> {
                val cleanWeight = weightG?.takeIf { it > 0.0 }?.let(::round1)
                val resolvedQuantity = quantity?.takeIf { it > 0 }
                    ?: quantityFromWeight(cleanWeight, fixed.unitWeightG)
                if (cleanWeight == null && resolvedQuantity == null) {
                    message = "请输入有效总重量或数量。"
                    return false
                }
                scanState = scanState.copy(
                    weightG = cleanWeight,
                    quantity = resolvedQuantity,
                    review = null,
                )
                message = buildString {
                    append("已手动录入")
                    cleanWeight?.let { append("总重 ${it.gText()}g") }
                    if (cleanWeight != null && resolvedQuantity != null) append("，")
                    resolvedQuantity?.let { append("数量 $it") }
                    append("。")
                }
            }
            ItemType.Other -> {
                message = "其他物品不需要重量或数量。"
                return false
            }
            ItemType.Location, ItemType.Weight -> return false
        }
        signal()
        return true
    }

    fun confirmLocationReview(name: String) {
        val review = scanReview as? ScanReview.Location ?: return
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            message = "库位名称不能为空。"
            return
        }
        val location = LocationValue(review.id, cleanName)
        upsertLocation(location)
        scanState = scanState.copy(location = location, review = null)
        message = "已确认库位 ${location.name}。"
        signal()
    }

    fun cancelScanReview() {
        if (scanReview == null) return
        scanState = scanState.copy(review = null)
        message = "已取消本次扫码，当前流程保持不变。"
    }

    fun canStockIn(): Boolean {
        val existingStatus = pendingItem?.id?.let { snapshot.items[it]?.state?.status }
        val statusForRules = if (pendingItem?.id == conflictResolutionItemId) null else existingStatus
        return ScanWorkflowRules.canStockIn(scanState, statusForRules)
    }

    fun stockIn() {
        val fixed = pendingItem ?: return
        val location = pendingLocation ?: return
        if (!canStockIn()) {
            message = "入库条件未满足。"
            return
        }
        val before = snapshot.items[fixed.id]
        upsertLocation(location)
        val state = ItemState(
            status = StockStatus.InStock,
            currentG = if (fixed.type == ItemType.Spool) pendingWeightG else pendingWeightG.takeIf { fixed.type == ItemType.Part },
            currentQty = if (fixed.type == ItemType.Part) resolvedPartQty(fixed) else null,
            locationId = location.id,
            locationName = location.name,
            stockedOn = todayCode(),
            checkedOutOn = null,
            updatedAt = nowIso(),
        )
        val after = InventoryItem(id = fixed.id, type = fixed.type, fixed = fixed, state = state)
        val resolvingConflict = conflictResolutionItemId == fixed.id
        commitItem(if (resolvingConflict) "sync_resolution_in_stock" else "stock_in", before, after)
        scanState = scanState.clearedForMode()
        if (resolvingConflict) {
            conflictResolutionItemId = null
            onSyncConflictResolved?.invoke(after.id)
        }
        message = if (resolvingConflict) {
            "${after.fixed.displayName} 的同步异常已按在库状态重新确认。"
        } else {
            "${after.fixed.displayName} 已入库，库位 ${after.locationText}。"
        }
        signal()
    }

    fun canStocktake(): Boolean {
        return ScanWorkflowRules.canStocktake(scanState, activeItem)
    }

    fun stocktake() {
        val existing = activeItem ?: return
        if (!canStocktake()) {
            message = "盘点条件未满足。"
            return
        }
        val after = existing.copy(
            state = existing.state.copy(
                currentG = when (existing.type) {
                    ItemType.Spool, ItemType.Part -> pendingWeightG
                    else -> existing.state.currentG
                },
                currentQty = if (existing.type == ItemType.Part) resolvedPartQty(existing.fixed) else existing.state.currentQty,
                updatedAt = nowIso(),
            ),
        )
        commitItem("stocktake", existing, after)
        scanState = scanState.clearedForMode()
        message = "${after.fixed.displayName} 已盘点更新。"
        signal()
    }

    fun canMove(): Boolean {
        return ScanWorkflowRules.canMove(scanState, activeItem)
    }

    fun moveActive() {
        val existing = activeItem ?: return
        val location = pendingLocation ?: return
        if (existing.state.status != StockStatus.InStock) {
            message = "只有在库物品可以绑定库位。"
            return
        }
        upsertLocation(location)
        val after = existing.copy(
            state = existing.state.copy(
                locationId = location.id,
                locationName = location.name,
                updatedAt = nowIso(),
            ),
        )
        saveItemOnly(after)
        scanState = scanState.clearedForMode()
        message = "${after.fixed.displayName} 已绑定到 ${after.locationText}。不进入主流水。"
        signal()
    }

    fun canCheckout(): Boolean = activeItem?.state?.status == StockStatus.InStock

    fun checkoutActive() {
        val existing = activeItem ?: return
        checkoutItem(existing.id)
    }

    fun checkoutItem(itemId: String) {
        if (isItemSyncBlocked(itemId)) {
            message = "该物品存在同步异常，不能出库。"
            return
        }
        val existing = snapshot.items[itemId] ?: return
        if (existing.state.status != StockStatus.InStock) {
            message = "当前物品不是在库状态。"
            return
        }
        val after = existing.copy(
            state = existing.state.copy(
                status = StockStatus.CheckedOut,
                locationId = "",
                locationName = "",
                checkedOutOn = todayCode(),
                updatedAt = nowIso(),
            ),
        )
        commitItem("checkout", existing, after)
        scanState = scanState.clearedForMode()
        message = "${after.fixed.displayName} 已出库。"
        signal()
    }

    fun archiveActive() {
        val existing = activeItem ?: return
        archiveItem(existing.id)
    }

    fun archiveItem(itemId: String) {
        if (isItemSyncBlocked(itemId)) {
            message = "该物品存在同步异常，不能归档。"
            return
        }
        val existing = snapshot.items[itemId] ?: return
        val after = existing.copy(
            state = existing.state.copy(
                status = StockStatus.Archived,
                updatedAt = nowIso(),
            ),
        )
        saveItemOnly(after)
        scanState = scanState.clearedForMode()
        message = "${after.fixed.displayName} 已归档。不进入主流水。"
        signal()
    }

    fun resolveConflictAsCheckedOut(itemId: String) {
        val existing = snapshot.items[itemId] ?: run {
            message = "本机没有该物品记录，不能直接确认出库，请重新扫码确认。"
            return
        }
        val after = existing.copy(
            state = existing.state.copy(
                status = StockStatus.CheckedOut,
                locationId = "",
                locationName = "",
                checkedOutOn = todayCode(),
                updatedAt = nowIso(),
            ),
        )
        commitItem("sync_resolution_checkout", existing, after)
        conflictResolutionItemId = null
        onSyncConflictResolved?.invoke(itemId)
        message = "${after.fixed.displayName} 的同步异常已按出库状态确认。"
        signal()
    }

    fun resolveConflictAsArchived(itemId: String) {
        val existing = snapshot.items[itemId] ?: run {
            message = "本机没有该物品记录，不能直接归档，请重新扫码确认。"
            return
        }
        val after = existing.copy(
            state = existing.state.copy(
                status = StockStatus.Archived,
                updatedAt = nowIso(),
            ),
        )
        commitItem("sync_resolution_archive", existing, after)
        conflictResolutionItemId = null
        onSyncConflictResolved?.invoke(itemId)
        message = "${after.fixed.displayName} 的同步异常已归档。"
        signal()
    }

    fun startLocationSorting() {
        val location = pendingLocation
        if (location == null) {
            message = "先扫库位码。"
            return
        }
        scanState = ScanWorkflowState(
            mode = ScanMode.BindLocation,
            location = location,
            sortingLocation = location,
        )
        message = "正在整理 ${location.name}，连续扫已入库物品。"
    }

    fun stopLocationSorting() {
        val name = sortingLocation?.name
        scanState = scanState.clearedForMode(ScanMode.BindLocation)
        message = if (name == null) "未在整理库位。" else "已完成整理 $name。"
    }

    fun clearContext() {
        scanState = scanState.clearedForMode()
        message = "已清空当前上下文。"
    }

    fun undoLast() {
        val undo = lastUndo
        if (undo == null) {
            message = "没有可撤销操作。"
            return
        }
        val items = snapshot.items.toMutableMap()
        if (undo.before == null) {
            items.remove(undo.itemId)
        } else {
            items[undo.itemId] = undo.before
        }
        val transaction = InventoryTransaction(
            txId = newTxId(),
            action = "undo",
            itemId = undo.itemId,
            itemType = undo.before?.type ?: undo.after?.type ?: ItemType.Other,
            sourceDeviceId = localDeviceId,
            sourceDeviceName = localDeviceName,
            before = undo.after,
            after = undo.before,
        )
        snapshot = snapshot.copy(
            items = trimItems(items),
            transactions = (snapshot.transactions + transaction).takeLast(250),
        ).trimmed()
        runCatching {
            database.applyUndo(undo.itemId, undo.before, transaction, snapshot.items.keys)
        }.onFailure {
            message = "保存数据库失败：${it.message}"
        }
        onInventoryChanged?.invoke()
        scanState = scanState.clearedForMode()
        lastUndo = null
        message = "已撤销上一笔：${undo.action}。"
    }

    fun importSnapshot(raw: String): Boolean {
        return runCatching {
            snapshot = gson.fromJson(raw, InventorySnapshot::class.java).trimmed()
            database.replaceAll(snapshot)
        }.onSuccess {
            message = "已导入 JSON。"
            onInventoryChanged?.invoke()
        }.onFailure {
            message = "导入失败：${it.message}"
        }.isSuccess
    }

    fun exportSnapshot(): String = gson.toJson(snapshot)

    private fun resolvedPartQty(fixed: FixedData): Int? {
        return ScanWorkflowRules.resolvedPartQuantity(scanState, fixed)
    }

    private fun addScanLog(payload: String, parsed: ParsedPayload, result: String, logMessage: String) {
        val entry = ScanLogEntry(
            payload = payload,
            parsedType = parsed.type?.payload.orEmpty(),
            parsedId = parsed.id,
            result = result,
            message = logMessage,
        )
        snapshot = snapshot.copy(
            scanLog = (listOf(entry) + snapshot.scanLog).take(50),
        )
        runCatching {
            database.appendScanLog(entry)
        }.onFailure {
            message = "保存数据库失败：${it.message}"
        }
    }

    private fun resolveLocation(fixed: FixedData): LocationValue {
        val existing = snapshot.locations[fixed.id]
        val name = fixed.name.ifBlank { existing?.name.orEmpty() }.ifBlank { fixed.id }
        return LocationValue(id = fixed.id, name = name)
    }

    private fun upsertLocation(location: LocationValue) {
        if (location.id.isBlank()) return
        val normalized = location.copy(name = location.name.ifBlank { location.id })
        snapshot = snapshot.copy(
            locations = snapshot.locations.toMutableMap().also {
                it[normalized.id] = normalized
            },
        ).trimmed()
        runCatching {
            database.upsertLocation(normalized)
        }.onFailure {
            message = "保存数据库失败：${it.message}"
        }
        onInventoryChanged?.invoke()
    }

    private fun saveItemOnly(after: InventoryItem) {
        val items = snapshot.items.toMutableMap()
        items[after.id] = after
        snapshot = snapshot.copy(items = trimItems(items)).trimmed()
        runCatching {
            database.saveItem(after, snapshot.items.keys)
        }.onFailure {
            message = "保存数据库失败：${it.message}"
        }
        onInventoryChanged?.invoke()
    }

    private fun commitItem(action: String, before: InventoryItem?, after: InventoryItem) {
        val items = snapshot.items.toMutableMap()
        items[after.id] = after
        val transaction = InventoryTransaction(
            txId = newTxId(),
            action = action,
            itemId = after.id,
            itemType = after.type,
            sourceDeviceId = localDeviceId,
            sourceDeviceName = localDeviceName,
            before = before,
            after = after,
        )
        snapshot = snapshot.copy(
            items = trimItems(items),
            transactions = (snapshot.transactions + transaction).takeLast(250),
        ).trimmed()
        lastUndo = UndoRecord(action = action, itemId = after.id, before = before, after = after)
        runCatching {
            database.saveItemWithTransaction(after, transaction, snapshot.items.keys)
        }.onFailure {
            message = "保存数据库失败：${it.message}"
        }
        onInventoryChanged?.invoke()
    }

    private fun trimItems(items: MutableMap<String, InventoryItem>): Map<String, InventoryItem> {
        if (items.size <= 600) return items.toMap()
        val over = items.size - 600
        val candidates = items.values
            .filter { it.state.status != StockStatus.InStock }
            .sortedWith(
                compareBy<InventoryItem> {
                    when (it.state.status) {
                        StockStatus.Archived -> 0
                        StockStatus.CheckedOut -> 1
                        StockStatus.InStock -> 2
                    }
                }.thenBy { it.state.updatedAt },
            )
            .take(over)
        candidates.forEach { items.remove(it.id) }
        return items.toMap()
    }

    private fun InventorySnapshot.trimmed(): InventorySnapshot {
        @Suppress("USELESS_CAST")
        val sourceItems = (items as? Map<String, InventoryItem>).orEmpty()
        @Suppress("USELESS_CAST")
        val sourceLocations = (locations as? Map<String, LocationValue>).orEmpty()
        @Suppress("USELESS_CAST")
        val sourceTransactions = (transactions as? List<InventoryTransaction>).orEmpty()
        @Suppress("USELESS_CAST")
        val sourceScanLog = (scanLog as? List<ScanLogEntry>).orEmpty()

        val normalizedLocations = sourceLocations.toMutableMap()
        val normalizedItems = sourceItems.mapValues { (_, item) ->
            val locationId = item.state.locationId
            if (locationId.isBlank()) {
                item.copy(state = item.state.copy(locationName = ""))
            } else {
                val name = item.state.locationName.ifBlank {
                    normalizedLocations[locationId]?.name.orEmpty()
                }.ifBlank { locationId }
                normalizedLocations[locationId] = LocationValue(id = locationId, name = name)
                item.copy(state = item.state.copy(locationName = name))
            }
        }
        return copy(
            items = trimItems(normalizedItems.toMutableMap()),
            locations = normalizedLocations,
            transactions = sourceTransactions
                .filter { it.action in MainTransactionActions }
                .takeLast(250),
            scanLog = sourceScanLog.take(50),
        )
    }

    private fun newTxId(): String {
        val source = localDeviceId.ifBlank { "android" }.take(12)
        return "$source-${System.currentTimeMillis().toString(36)}-${newScanId().takeLast(6)}"
    }

    private val MainTransactionActions = setOf(
        "stock_in",
        "checkout",
        "stocktake",
        "undo",
        "sync_resolution_in_stock",
        "sync_resolution_checkout",
        "sync_resolution_archive",
    )

    private fun signal() {
        runCatching {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
                appContext.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60).startTone(ToneGenerator.TONE_PROP_ACK, 80)
        }
    }
}
