package studio.inventory.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson

class InventoryDatabase(
    context: Context,
    private val gson: Gson,
) : SQLiteOpenHelper(context, DatabaseName, null, DatabaseVersion) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE items (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                status TEXT NOT NULL,
                location_id TEXT,
                fixed_json TEXT NOT NULL,
                state_json TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE locations (
                location_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE transactions (
                tx_id TEXT PRIMARY KEY,
                action TEXT NOT NULL,
                item_id TEXT NOT NULL,
                item_type TEXT NOT NULL,
                before_json TEXT,
                after_json TEXT,
                created_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE scan_logs (
                scan_id TEXT PRIMARY KEY,
                raw_payload TEXT NOT NULL,
                parsed_type TEXT,
                parsed_id TEXT,
                result TEXT NOT NULL,
                message TEXT,
                created_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS scan_logs")
        db.execSQL("DROP TABLE IF EXISTS transactions")
        db.execSQL("DROP TABLE IF EXISTS locations")
        db.execSQL("DROP TABLE IF EXISTS items")
        onCreate(db)
    }

    fun hasData(): Boolean = readableDatabase.rawQuery(
        "SELECT (SELECT COUNT(*) FROM items) + (SELECT COUNT(*) FROM transactions) + (SELECT COUNT(*) FROM scan_logs)",
        null,
    ).use { cursor ->
        cursor.moveToFirst() && cursor.getInt(0) > 0
    }

    fun loadSnapshot(): InventorySnapshot {
        val db = readableDatabase
        val locations = linkedMapOf<String, LocationValue>()
        db.query(
            "locations",
            arrayOf("location_id", "name"),
            null,
            null,
            null,
            null,
            "location_id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0).orEmpty()
                if (id.isNotBlank()) {
                    locations[id] = LocationValue(id = id, name = cursor.getString(1).orEmpty().ifBlank { id })
                }
            }
        }

        val items = linkedMapOf<String, InventoryItem>()
        db.query(
            "items",
            arrayOf("id", "type", "fixed_json", "state_json"),
            null,
            null,
            null,
            null,
            "id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0).orEmpty()
                val type = ItemType.fromPayload(cursor.getString(1).orEmpty()) ?: ItemType.Other
                val fixed = gson.fromJson(cursor.getString(2), FixedData::class.java) ?: FixedData(type = type, id = id)
                val state = gson.fromJson(cursor.getString(3), ItemState::class.java) ?: ItemState()
                val locationName = state.locationId.takeIf { it.isNotBlank() }
                    ?.let { locations[it]?.name }
                    ?: state.locationName
                items[id] = InventoryItem(
                    id = id,
                    type = type,
                    fixed = fixed,
                    state = state.copy(locationName = locationName),
                )
            }
        }

        val transactions = mutableListOf<InventoryTransaction>()
        db.query(
            "transactions",
            arrayOf("tx_id", "action", "item_id", "item_type", "before_json", "after_json", "created_at"),
            null,
            null,
            null,
            null,
            "created_at ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                transactions += InventoryTransaction(
                    txId = cursor.getString(0).orEmpty(),
                    action = cursor.getString(1).orEmpty(),
                    itemId = cursor.getString(2).orEmpty(),
                    itemType = ItemType.fromPayload(cursor.getString(3).orEmpty()) ?: ItemType.Other,
                    before = cursor.getString(4)?.takeIf { it.isNotBlank() }?.let {
                        gson.fromJson(it, InventoryItem::class.java)
                    },
                    after = cursor.getString(5)?.takeIf { it.isNotBlank() }?.let {
                        gson.fromJson(it, InventoryItem::class.java)
                    },
                    createdAt = cursor.getString(6).orEmpty(),
                )
            }
        }

        val scanLogs = mutableListOf<ScanLogEntry>()
        db.query(
            "scan_logs",
            arrayOf("scan_id", "raw_payload", "parsed_type", "parsed_id", "result", "message", "created_at"),
            null,
            null,
            null,
            null,
            "created_at DESC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                scanLogs += ScanLogEntry(
                    scanId = cursor.getString(0).orEmpty(),
                    payload = cursor.getString(1).orEmpty(),
                    parsedType = cursor.getString(2).orEmpty(),
                    parsedId = cursor.getString(3).orEmpty(),
                    result = cursor.getString(4).orEmpty(),
                    message = cursor.getString(5).orEmpty(),
                    createdAt = cursor.getString(6).orEmpty(),
                )
            }
        }

        return InventorySnapshot(
            items = items,
            locations = locations,
            transactions = transactions,
            scanLog = scanLogs,
        )
    }

    fun replaceAll(snapshot: InventorySnapshot) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("items", null, null)
            db.delete("locations", null, null)
            db.delete("transactions", null, null)
            db.delete("scan_logs", null, null)

            snapshot.locations.values.forEach { location ->
                db.insertWithOnConflict("locations", null, locationValues(location), SQLiteDatabase.CONFLICT_REPLACE)
            }
            snapshot.items.values.forEach { item ->
                db.insertWithOnConflict("items", null, itemValues(item), SQLiteDatabase.CONFLICT_REPLACE)
            }
            snapshot.transactions.forEach { tx ->
                db.insertWithOnConflict("transactions", null, transactionValues(tx), SQLiteDatabase.CONFLICT_REPLACE)
            }
            snapshot.scanLog.forEach { log ->
                db.insertWithOnConflict("scan_logs", null, scanLogValues(log), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun upsertLocation(location: LocationValue) {
        writableDatabase.insertWithOnConflict(
            "locations",
            null,
            locationValues(location),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun saveItem(item: InventoryItem, retainedItemIds: Set<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict("items", null, itemValues(item), SQLiteDatabase.CONFLICT_REPLACE)
            deleteItemsNotRetained(db, retainedItemIds)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun saveItemWithTransaction(
        item: InventoryItem,
        transaction: InventoryTransaction,
        retainedItemIds: Set<String>,
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict("items", null, itemValues(item), SQLiteDatabase.CONFLICT_REPLACE)
            db.insertWithOnConflict(
                "transactions",
                null,
                transactionValues(transaction),
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            deleteItemsNotRetained(db, retainedItemIds)
            pruneTransactions(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun applyUndo(
        itemId: String,
        restoredItem: InventoryItem?,
        transaction: InventoryTransaction,
        retainedItemIds: Set<String>,
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (restoredItem == null) {
                db.delete("items", "id = ?", arrayOf(itemId))
            } else {
                db.insertWithOnConflict("items", null, itemValues(restoredItem), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.insertWithOnConflict(
                "transactions",
                null,
                transactionValues(transaction),
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            deleteItemsNotRetained(db, retainedItemIds)
            pruneTransactions(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun appendScanLog(log: ScanLogEntry) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict("scan_logs", null, scanLogValues(log), SQLiteDatabase.CONFLICT_REPLACE)
            db.execSQL(
                "DELETE FROM scan_logs WHERE scan_id NOT IN " +
                    "(SELECT scan_id FROM scan_logs ORDER BY created_at DESC LIMIT 50)",
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun deleteItemsNotRetained(db: SQLiteDatabase, retainedItemIds: Set<String>) {
        if (retainedItemIds.isEmpty()) {
            db.delete("items", null, null)
            return
        }
        val placeholders = retainedItemIds.joinToString(",") { "?" }
        db.delete("items", "id NOT IN ($placeholders)", retainedItemIds.toTypedArray())
    }

    private fun pruneTransactions(db: SQLiteDatabase) {
        db.execSQL(
            "DELETE FROM transactions WHERE tx_id NOT IN " +
                "(SELECT tx_id FROM transactions ORDER BY created_at DESC LIMIT 250)",
        )
    }

    private fun itemValues(item: InventoryItem): ContentValues = ContentValues().apply {
        put("id", item.id)
        put("type", item.type.payload)
        put("status", item.state.status.value)
        put("location_id", item.state.locationId)
        put("fixed_json", gson.toJson(item.fixed))
        put("state_json", gson.toJson(item.state))
        put("updated_at", item.state.updatedAt)
    }

    private fun locationValues(location: LocationValue): ContentValues = ContentValues().apply {
        put("location_id", location.id)
        put("name", location.name.ifBlank { location.id })
        put("updated_at", nowIso())
    }

    private fun transactionValues(tx: InventoryTransaction): ContentValues = ContentValues().apply {
        put("tx_id", tx.txId)
        put("action", tx.action)
        put("item_id", tx.itemId)
        put("item_type", tx.itemType.payload)
        put("before_json", tx.before?.let { gson.toJson(it) })
        put("after_json", tx.after?.let { gson.toJson(it) })
        put("created_at", tx.createdAt)
    }

    private fun scanLogValues(log: ScanLogEntry): ContentValues = ContentValues().apply {
        val scanId = log.scanId.safe()
        val payload = log.payload.safe()
        val result = log.result.safe()
        put("scan_id", scanId.ifBlank { newScanId() })
        put("raw_payload", payload)
        put("parsed_type", log.parsedType.safe())
        put("parsed_id", log.parsedId.safe())
        put("result", result.ifBlank { "accepted" })
        put("message", log.message.safe())
        put("created_at", log.createdAt.safe().ifBlank { nowIso() })
    }

    private fun String?.safe(): String = this.orEmpty()

    companion object {
        private const val DatabaseName = "studio_inventory.db"
        private const val DatabaseVersion = 1
    }
}
