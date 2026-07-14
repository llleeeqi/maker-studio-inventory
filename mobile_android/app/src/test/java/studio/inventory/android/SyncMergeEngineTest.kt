package studio.inventory.android

import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeEngineTest {
    private val engine = SyncMergeEngine(GsonBuilder().create())

    @Test
    fun mergesIndependentItemsAndKeepsScanLogsLocal() {
        val base = InventorySnapshot()
        val localItem = otherItem("ITEM-LOCAL", "热风枪")
        val remoteItem = otherItem("ITEM-REMOTE", "电烙铁")
        val local = base.copy(
            items = mapOf(localItem.id to localItem),
            scanLog = listOf(ScanLogEntry(payload = "local-log")),
        )
        val remote = base.copy(items = mapOf(remoteItem.id to remoteItem))

        val result = engine.merge(base, local, remote)

        assertEquals(setOf(localItem.id, remoteItem.id), result.snapshot.items.keys)
        assertEquals(local.scanLog, result.snapshot.scanLog)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun treatsConcurrentItemChangesAsWholeItemConflictAndKeepsLocal() {
        val original = spool("FIL-001", 650.0, "LOC-A")
        val local = original.copy(state = original.state.copy(currentG = 620.0))
        val remote = original.copy(state = original.state.copy(locationId = "LOC-B", locationName = "第二库位"))

        val result = engine.merge(
            base = InventorySnapshot(items = mapOf(original.id to original)),
            local = InventorySnapshot(items = mapOf(local.id to local)),
            remote = InventorySnapshot(items = mapOf(remote.id to remote)),
        )

        assertEquals(local, result.snapshot.items[original.id])
        assertEquals(1, result.conflicts.size)
        assertEquals(SyncEntityType.Item, result.conflicts.single().entityType)
        assertEquals(original.id, result.conflicts.single().entityId)
    }

    @Test
    fun appliesRemoteDeletionOnlyWhenLocalDidNotChange() {
        val original = otherItem("ITEM-001", "热风枪")
        val result = engine.merge(
            base = InventorySnapshot(items = mapOf(original.id to original)),
            local = InventorySnapshot(items = mapOf(original.id to original)),
            remote = InventorySnapshot(),
        )

        assertNull(result.snapshot.items[original.id])
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun conflictsWhenRemoteDeletesAnItemChangedLocally() {
        val original = otherItem("ITEM-001", "热风枪")
        val local = original.copy(fixed = original.fixed.copy(note = "本地修改"))
        val result = engine.merge(
            base = InventorySnapshot(items = mapOf(original.id to original)),
            local = InventorySnapshot(items = mapOf(local.id to local)),
            remote = InventorySnapshot(),
        )

        assertEquals(local, result.snapshot.items[local.id])
        assertEquals(1, result.conflicts.size)
    }

    @Test
    fun unionsTransactionsAndDetectsImpossibleIdCollision() {
        val localTx = InventoryTransaction(txId = "tx-local", action = "stock_in", itemId = "A")
        val remoteTx = InventoryTransaction(txId = "tx-remote", action = "checkout", itemId = "B")
        val result = engine.merge(
            base = InventorySnapshot(),
            local = InventorySnapshot(transactions = listOf(localTx)),
            remote = InventorySnapshot(transactions = listOf(remoteTx)),
        )
        assertEquals(setOf("tx-local", "tx-remote"), result.snapshot.transactions.map { it.txId }.toSet())
        assertTrue(result.conflicts.isEmpty())
    }

    private fun otherItem(id: String, name: String) = InventoryItem(
        id = id,
        type = ItemType.Other,
        fixed = FixedData(type = ItemType.Other, id = id, name = name),
    )

    private fun spool(id: String, currentG: Double, locationId: String) = InventoryItem(
        id = id,
        type = ItemType.Spool,
        fixed = FixedData(
            type = ItemType.Spool,
            id = id,
            brand = "Bambu",
            material = "PLA",
            color = "white",
            tareG = 200.0,
        ),
        state = ItemState(currentG = currentG, locationId = locationId, locationName = locationId),
    )
}
