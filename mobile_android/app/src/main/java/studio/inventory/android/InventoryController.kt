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

data class ReplaceCandidate(
    val kind: CandidateKind,
    val fixed: FixedData? = null,
    val weightG: Double? = null,
    val qty: Int? = null,
    val location: LocationValue? = null,
)

enum class CandidateKind {
    Item,
    Weight,
    Quantity,
    Location,
}

data class FixedConflict(
    val scanned: FixedData,
    val local: InventoryItem,
)

data class UndoRecord(
    val action: String,
    val itemId: String,
    val before: InventoryItem?,
    val after: InventoryItem?,
)

class InventoryController(context: Context) {
    private val appContext = context.applicationContext
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val snapshotFile = File(appContext.filesDir, "inventory_snapshot.json")

    var snapshot by mutableStateOf(InventorySnapshot())
        private set

    var message by mutableStateOf("扫 v1 物品、重量或库位码。")
        private set

    var pendingItem by mutableStateOf<FixedData?>(null)
        private set

    var pendingWeightG by mutableStateOf<Double?>(null)
        private set

    var pendingQty by mutableStateOf<Int?>(null)
        private set

    var pendingLocation by mutableStateOf<LocationValue?>(null)
        private set

    var sortingLocation by mutableStateOf<LocationValue?>(null)
        private set

    var replaceCandidate by mutableStateOf<ReplaceCandidate?>(null)
        private set

    var fixedConflict by mutableStateOf<FixedConflict?>(null)
        private set

    var lastUndo by mutableStateOf<UndoRecord?>(null)
        private set

    val activeItem: InventoryItem?
        get() = pendingItem?.id?.let { snapshot.items[it] }

    fun load() {
        snapshot = runCatching {
            if (!snapshotFile.exists()) return@runCatching InventorySnapshot()
            gson.fromJson(snapshotFile.readText(), InventorySnapshot::class.java) ?: InventorySnapshot()
        }.getOrElse {
            message = "读取本地 JSON 失败，已使用空库存：${it.message}"
            InventorySnapshot()
        }.trimmed()
    }

    fun handlePayload(payload: String) {
        val clean = payload.trim()
        if (clean.isEmpty()) return
        addScanLog(clean)
        val parsed = parseV1Payload(clean)
        if (parsed.type == null) {
            message = "无法识别，只支持 v1。"
            return
        }

        when (parsed.type) {
            ItemType.Weight -> handleWeight(parsed.weightG)
            ItemType.Location -> handleLocation(parsed.fixed)
            ItemType.Spool, ItemType.Part, ItemType.Other -> handleItem(parsed.fixed)
        }
    }

    fun reportError(text: String) {
        message = text
    }

    private fun handleWeight(value: Double?) {
        if (value == null || value <= 0.0) {
            message = "重量码缺少有效 value_g。"
            return
        }
        val rounded = round1(value)
        if (pendingWeightG != null && pendingWeightG != rounded) {
            replaceCandidate = ReplaceCandidate(CandidateKind.Weight, weightG = rounded)
            message = "已有待处理重量，确认后才替换。"
            return
        }
        pendingWeightG = rounded
        pendingItem?.let { fixed ->
            if (fixed.type == ItemType.Part) {
                pendingQty = quantityFromWeight(rounded, fixed.unitWeightG) ?: pendingQty
            }
        }
        message = "已收到重量 ${rounded.gText()}g。"
        signal()
    }

    private fun handleLocation(fixed: FixedData?) {
        if (fixed == null) {
            message = "库位码格式错误。"
            return
        }
        val missing = fixed.missingRequiredFields()
        if (missing.isNotEmpty()) {
            message = "库位标签缺字段：${missing.joinToString()}。"
            return
        }
        val location = LocationValue(fixed.id, fixed.displayName)
        if (pendingLocation != null && pendingLocation != location) {
            replaceCandidate = ReplaceCandidate(CandidateKind.Location, location = location)
            message = "已有待处理库位，确认后才替换。"
            return
        }
        pendingLocation = location
        message = "已收到库位 ${location.name}。可用于入库，或开始整理该库位。"
        signal()
    }

    private fun handleItem(fixed: FixedData?) {
        if (fixed == null) {
            message = "物品码格式错误。"
            return
        }
        val missing = fixed.missingRequiredFields()
        if (missing.isNotEmpty()) {
            message = "标签缺必填字段：${missing.joinToString()}。请重新生成标签。"
            return
        }

        val sorting = sortingLocation
        if (sorting != null) {
            moveScannedItemDuringSorting(fixed, sorting)
            return
        }

        val existing = snapshot.items[fixed.id]
        if (existing != null && !existing.fixed.equivalentTo(fixed)) {
            fixedConflict = FixedConflict(scanned = fixed, local = existing)
            pendingItem = existing.fixed
            message = "同 ID 标签和本地记录不一致，请选择保留或更新本地固定信息。"
            return
        }

        if (pendingItem != null && pendingItem?.id != fixed.id && !canStockIn()) {
            replaceCandidate = ReplaceCandidate(CandidateKind.Item, fixed = fixed)
            message = "已有未完成物品，确认后才替换。"
            return
        }

        pendingItem = existing?.fixed ?: fixed
        pendingItem?.let { item ->
            if (item.type == ItemType.Part && pendingWeightG != null) {
                pendingQty = quantityFromWeight(pendingWeightG, item.unitWeightG) ?: pendingQty
            }
        }
        message = if (existing == null) {
            "${fixed.displayName} 尚未入库，补齐重量/数量和库位后点入库。"
        } else {
            "${existing.fixed.displayName}：${existing.stockText}，库位 ${existing.locationText}。"
        }
        signal()
    }

    private fun moveScannedItemDuringSorting(fixed: FixedData, location: LocationValue) {
        val existing = snapshot.items[fixed.id]
        if (existing == null) {
            message = "${fixed.displayName} 未入库，不能整理到库位。"
            return
        }
        if (existing.state.status != StockStatus.InStock) {
            message = "${existing.fixed.displayName} 当前${existing.state.status.label}，未移动。"
            return
        }
        val updated = existing.copy(
            state = existing.state.copy(
                locationId = location.id,
                locationName = location.name,
                updatedAt = nowIso(),
            ),
        )
        commitItem("move", existing, updated)
        pendingItem = updated.fixed
        message = "${updated.fixed.displayName} 已整理到 ${location.name}。"
        signal()
    }

    fun confirmReplace() {
        val candidate = replaceCandidate ?: return
        when (candidate.kind) {
            CandidateKind.Item -> {
                pendingItem = candidate.fixed
                pendingWeightG = null
                pendingQty = null
                pendingLocation = null
                message = "已替换为 ${candidate.fixed?.displayName ?: "新物品"}，未完成上下文已清空。"
            }
            CandidateKind.Weight -> {
                pendingWeightG = candidate.weightG
                pendingItem?.let { fixed ->
                    if (fixed.type == ItemType.Part) {
                        pendingQty = quantityFromWeight(candidate.weightG, fixed.unitWeightG)
                    }
                }
                message = "已替换重量为 ${candidate.weightG?.gText()}g。"
            }
            CandidateKind.Quantity -> {
                pendingQty = candidate.qty
                message = "已替换数量为 ${candidate.qty}。"
            }
            CandidateKind.Location -> {
                pendingLocation = candidate.location
                message = "已替换库位为 ${candidate.location?.name}。"
            }
        }
        replaceCandidate = null
    }

    fun cancelReplace() {
        replaceCandidate = null
        message = "已保留当前未完成上下文。"
    }

    fun keepLocalFixed() {
        fixedConflict = null
        message = "已保留本地固定信息。"
    }

    fun updateLocalFixed() {
        val conflict = fixedConflict ?: return
        val before = conflict.local
        val after = before.copy(fixed = conflict.scanned, type = conflict.scanned.type)
        commitItem("edit_fixed", before, after)
        pendingItem = after.fixed
        fixedConflict = null
        message = "已更新本地固定信息：${after.fixed.displayName}。"
    }

    fun canStockIn(): Boolean {
        val fixed = pendingItem ?: return false
        val location = pendingLocation ?: return false
        if (location.id.isBlank()) return false
        return when (fixed.type) {
            ItemType.Spool -> {
                val current = pendingWeightG
                val tare = fixed.tareG
                current != null && tare != null && current > tare
            }
            ItemType.Part -> resolvedPartQty(fixed) != null
            ItemType.Other -> true
            ItemType.Location, ItemType.Weight -> false
        }
    }

    fun stockIn() {
        val fixed = pendingItem ?: return
        val location = pendingLocation ?: return
        if (!canStockIn()) {
            message = "入库条件未满足。"
            return
        }
        val before = snapshot.items[fixed.id]
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
        commitItem("stock_in", before, after)
        pendingItem = after.fixed
        pendingWeightG = null
        pendingQty = null
        message = "${after.fixed.displayName} 已入库，库位 ${after.locationText}。"
        signal()
    }

    fun canStocktake(): Boolean {
        val existing = activeItem ?: return false
        if (existing.state.status != StockStatus.InStock) return false
        return when (existing.type) {
            ItemType.Spool -> pendingWeightG != null && pendingWeightG!! > (existing.fixed.tareG ?: 0.0)
            ItemType.Part -> resolvedPartQty(existing.fixed) != null
            else -> false
        }
    }

    fun stocktake() {
        val existing = activeItem ?: return
        if (!canStocktake()) {
            message = "盘点条件未满足。"
            return
        }
        val after = existing.copy(
            state = existing.state.copy(
                currentG = if (existing.type == ItemType.Spool) pendingWeightG else existing.state.currentG,
                currentQty = if (existing.type == ItemType.Part) resolvedPartQty(existing.fixed) else existing.state.currentQty,
                updatedAt = nowIso(),
            ),
        )
        commitItem("stocktake", existing, after)
        pendingWeightG = null
        pendingQty = null
        message = "${after.fixed.displayName} 已盘点更新。"
        signal()
    }

    fun canMove(): Boolean {
        val existing = activeItem ?: return false
        return existing.state.status == StockStatus.InStock && pendingLocation != null
    }

    fun moveActive() {
        val existing = activeItem ?: return
        val location = pendingLocation ?: return
        if (existing.state.status != StockStatus.InStock) {
            message = "只有在库物品可以移库。"
            return
        }
        val after = existing.copy(
            state = existing.state.copy(
                locationId = location.id,
                locationName = location.name,
                updatedAt = nowIso(),
            ),
        )
        commitItem("move", existing, after)
        pendingLocation = null
        message = "${after.fixed.displayName} 已移动到 ${after.locationText}。"
        signal()
    }

    fun canCheckout(): Boolean = activeItem?.state?.status == StockStatus.InStock

    fun checkoutActive() {
        val existing = activeItem ?: return
        if (existing.state.status != StockStatus.InStock) {
            message = "当前物品不是在库状态。"
            return
        }
        val after = existing.copy(
            state = existing.state.copy(
                status = StockStatus.CheckedOut,
                checkedOutOn = todayCode(),
                updatedAt = nowIso(),
            ),
        )
        commitItem("checkout", existing, after)
        message = "${after.fixed.displayName} 已出库。"
        signal()
    }

    fun archiveActive() {
        val existing = activeItem ?: return
        val after = existing.copy(
            state = existing.state.copy(
                status = StockStatus.Archived,
                updatedAt = nowIso(),
            ),
        )
        commitItem("archive", existing, after)
        message = "${after.fixed.displayName} 已归档。"
        signal()
    }

    fun startLocationSorting() {
        val location = pendingLocation
        if (location == null) {
            message = "先扫库位码。"
            return
        }
        sortingLocation = location
        pendingItem = null
        pendingWeightG = null
        pendingQty = null
        message = "正在整理 ${location.name}，连续扫已入库物品。"
    }

    fun stopLocationSorting() {
        val name = sortingLocation?.name
        sortingLocation = null
        message = if (name == null) "未在整理库位。" else "已完成整理 $name。"
    }

    fun clearContext() {
        pendingItem = null
        pendingWeightG = null
        pendingQty = null
        pendingLocation = null
        sortingLocation = null
        replaceCandidate = null
        fixedConflict = null
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
            before = undo.after,
            after = undo.before,
        )
        snapshot = snapshot.copy(
            items = trimItems(items),
            transactions = (snapshot.transactions + transaction).takeLast(350),
        )
        save()
        pendingItem = undo.before?.fixed ?: undo.after?.fixed
        lastUndo = null
        message = "已撤销上一笔：${undo.action}。"
    }

    fun importSnapshot(raw: String): Boolean {
        return runCatching {
            snapshot = gson.fromJson(raw, InventorySnapshot::class.java).trimmed()
            save()
        }.onSuccess {
            message = "已导入 JSON。"
        }.onFailure {
            message = "导入失败：${it.message}"
        }.isSuccess
    }

    fun exportSnapshot(): String = gson.toJson(snapshot)

    private fun resolvedPartQty(fixed: FixedData): Int? {
        return pendingQty ?: quantityFromWeight(pendingWeightG, fixed.unitWeightG)
    }

    private fun addScanLog(payload: String) {
        snapshot = snapshot.copy(
            scanLog = (listOf(ScanLogEntry(payload = payload)) + snapshot.scanLog).take(50),
        )
        save()
    }

    private fun commitItem(action: String, before: InventoryItem?, after: InventoryItem) {
        val items = snapshot.items.toMutableMap()
        items[after.id] = after
        val transaction = InventoryTransaction(
            txId = newTxId(),
            action = action,
            itemId = after.id,
            itemType = after.type,
            before = before,
            after = after,
        )
        snapshot = snapshot.copy(
            items = trimItems(items),
            transactions = (snapshot.transactions + transaction).takeLast(350),
        )
        lastUndo = UndoRecord(action = action, itemId = after.id, before = before, after = after)
        save()
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
        return copy(
            items = trimItems(items.toMutableMap()),
            transactions = transactions.takeLast(350),
            scanLog = scanLog.take(50),
        )
    }

    private fun save() {
        runCatching {
            snapshotFile.writeText(gson.toJson(snapshot))
        }.onFailure {
            message = "保存 JSON 失败：${it.message}"
        }
    }

    private fun newTxId(): String = "android-${System.currentTimeMillis().toString(36)}"

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
