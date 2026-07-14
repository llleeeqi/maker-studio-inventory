package studio.inventory.android

import com.google.gson.Gson
class SyncMergeEngine(private val gson: Gson) {
    fun merge(
        base: InventorySnapshot,
        local: InventorySnapshot,
        remote: InventorySnapshot,
    ): SyncMergeResult {
        val conflicts = mutableListOf<SyncConflictCandidate>()
        val items = mergeMap(
            type = SyncEntityType.Item,
            base = base.items,
            local = local.items,
            remote = remote.items,
            conflicts = conflicts,
        )
        val locations = mergeMap(
            type = SyncEntityType.Location,
            base = base.locations,
            local = local.locations,
            remote = remote.locations,
            conflicts = conflicts,
        )
        val transactions = mergeTransactions(base, local, remote, conflicts)
        return SyncMergeResult(
            snapshot = InventorySnapshot(
                schema = maxOf(base.schema, local.schema, remote.schema),
                deviceId = local.deviceId,
                items = items,
                locations = locations,
                transactions = transactions,
                scanLog = local.scanLog,
            ),
            conflicts = conflicts,
        )
    }

    private fun <T> mergeMap(
        type: SyncEntityType,
        base: Map<String, T>,
        local: Map<String, T>,
        remote: Map<String, T>,
        conflicts: MutableList<SyncConflictCandidate>,
    ): Map<String, T> {
        val result = linkedMapOf<String, T>()
        (base.keys + local.keys + remote.keys).toSortedSet().forEach { id ->
            val baseValue = base[id]
            val localValue = local[id]
            val remoteValue = remote[id]
            when {
                localValue == remoteValue -> localValue?.let { result[id] = it }
                localValue == baseValue -> remoteValue?.let { result[id] = it }
                remoteValue == baseValue -> localValue?.let { result[id] = it }
                else -> {
                    localValue?.let { result[id] = it }
                    conflicts += conflict(type, id, baseValue, localValue, remoteValue)
                }
            }
        }
        return result
    }

    private fun mergeTransactions(
        base: InventorySnapshot,
        local: InventorySnapshot,
        remote: InventorySnapshot,
        conflicts: MutableList<SyncConflictCandidate>,
    ): List<InventoryTransaction> {
        val baseMap = base.transactions.associateBy { it.txId }
        val localMap = local.transactions.associateBy { it.txId }
        val remoteMap = remote.transactions.associateBy { it.txId }
        return mergeMap(
            type = SyncEntityType.Transaction,
            base = baseMap,
            local = localMap,
            remote = remoteMap,
            conflicts = conflicts,
        ).values.sortedWith(compareBy<InventoryTransaction> { it.createdAt }.thenBy { it.txId })
    }

    private fun conflict(type: SyncEntityType, id: String, base: Any?, local: Any?, remote: Any?) =
        SyncConflictCandidate(
            conflictId = "conflict-" + sha256Hex(
                listOf(type.value, id, gson.toJson(base), gson.toJson(local), gson.toJson(remote))
                    .joinToString("\n")
                    .toByteArray(),
            ),
            entityType = type,
            entityId = id,
            baseJson = base?.let(gson::toJson),
            localJson = local?.let(gson::toJson),
            remoteJson = remote?.let(gson::toJson),
        )
}
