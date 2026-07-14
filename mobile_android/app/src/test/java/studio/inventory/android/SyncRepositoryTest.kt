package studio.inventory.android

import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val gson = GsonBuilder().create()

    @Test
    fun createsEncryptedContentAddressedSnapshotsAndRestoresThem() {
        val root = temporaryFolder.newFolder("repo")
        val repository = SyncRepository(root, gson, SyncCrypto(ByteArray(32) { it.toByte() }))
        val snapshot = sampleSnapshot()

        val first = repository.createSnapshot(snapshot, "device-a", parentId = null)
        repository.markLatestSync(first.id)
        val second = repository.createSnapshot(
            snapshot.copy(scanLog = listOf(ScanLogEntry(payload = "local-only"))),
            "device-a",
        )

        assertNotEquals(first.id, second.id)
        assertEquals(first.items, second.items)
        assertEquals(first.locations, second.locations)
        assertEquals(first.transactions, second.transactions)
        assertEquals(first.id, second.parentId)
        assertEquals(first.id, repository.latestSyncId())

        val restored = repository.restoreSnapshot(second, localScanLog = snapshot.scanLog)
        assertEquals(snapshot.items, restored.items)
        assertEquals(snapshot.locations, restored.locations)
        assertEquals(snapshot.transactions, restored.transactions)
        assertEquals(snapshot.scanLog, restored.scanLog)

        val objectFiles = root.resolve("objects").walkTopDown().filter { it.isFile }.toList()
        assertEquals(3, objectFiles.size)
        assertTrue(objectFiles.all { !it.readText().contains("Bambu") })
        assertFalse(root.resolve("refs/latest").readText().isBlank())
    }

    private fun sampleSnapshot(): InventorySnapshot {
        val item = InventoryItem(
            id = "FIL-001",
            type = ItemType.Spool,
            fixed = FixedData(
                type = ItemType.Spool,
                id = "FIL-001",
                brand = "Bambu",
                material = "PLA",
                color = "white",
                tareG = 200.0,
            ),
            state = ItemState(currentG = 650.0, locationId = "LOC-001", locationName = "测试库位"),
        )
        return InventorySnapshot(
            items = mapOf(item.id to item),
            locations = mapOf("LOC-001" to LocationValue("LOC-001", "测试库位")),
            transactions = listOf(
                InventoryTransaction(
                    txId = "tx-001",
                    action = "stock_in",
                    itemId = item.id,
                    itemType = item.type,
                    after = item,
                ),
            ),
            scanLog = listOf(ScanLogEntry(payload = "not-synced")),
        )
    }
}
