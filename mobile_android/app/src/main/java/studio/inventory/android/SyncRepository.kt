package studio.inventory.android

import com.google.gson.Gson
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.TreeMap

class SyncRepository(
    private val root: File,
    private val gson: Gson,
    private val crypto: SyncCrypto,
) {
    private val objectsDir = File(root, "objects")
    private val indexesDir = File(root, "indexes")
    private val refsDir = File(root, "refs")

    init {
        objectsDir.mkdirs()
        indexesDir.mkdirs()
        refsDir.mkdirs()
    }

    fun createSnapshot(
        snapshot: InventorySnapshot,
        deviceId: String,
        parentId: String? = latestId(),
        conflicts: Collection<SyncConflictCandidate> = emptyList(),
    ): SyncIndex {
        val itemRefs = TreeMap<String, String>()
        snapshot.items.toSortedMap().forEach { (id, item) ->
            itemRefs[id] = storeObject(SyncEntityType.Item, id, gson.toJson(item))
        }
        val locationRefs = TreeMap<String, String>()
        snapshot.locations.toSortedMap().forEach { (id, location) ->
            locationRefs[id] = storeObject(SyncEntityType.Location, id, gson.toJson(location))
        }
        val transactionRefs = TreeMap<String, String>()
        snapshot.transactions.associateBy { it.txId }.toSortedMap().forEach { (id, transaction) ->
            transactionRefs[id] = storeObject(SyncEntityType.Transaction, id, gson.toJson(transaction))
        }
        val conflictRefs = TreeMap<String, String>()
        conflicts.sortedBy { it.conflictId }.forEach { conflict ->
            conflictRefs[conflict.conflictId] = storeObject(
                SyncEntityType.Conflict,
                conflict.conflictId,
                gson.toJson(conflict),
            )
        }

        val draft = SyncIndex(
            deviceId = deviceId,
            parentId = parentId,
            items = itemRefs,
            locations = locationRefs,
            transactions = transactionRefs,
            conflicts = conflictRefs,
        )
        val canonical = canonicalIndexJson(draft)
        val index = draft.copy(id = sha256Hex(canonical.toByteArray(StandardCharsets.UTF_8)))
        writeEncrypted(indexFile(index.id), gson.toJson(index).toByteArray(StandardCharsets.UTF_8), "index:${index.id}")
        writeRef("latest", index.id)
        return index
    }

    fun readIndex(id: String): SyncIndex {
        val bytes = readEncrypted(indexFile(id), "index:$id")
        val index = gson.fromJson(String(bytes, StandardCharsets.UTF_8), SyncIndex::class.java)
        require(index.id == id) { "同步索引 ID 不匹配。" }
        return index
    }

    fun restoreSnapshot(index: SyncIndex, localScanLog: List<ScanLogEntry> = emptyList()): InventorySnapshot {
        val items = index.items.toSortedMap().mapValues { (id, hash) ->
            readObject(hash, SyncEntityType.Item, id, InventoryItem::class.java)
        }
        val locations = index.locations.toSortedMap().mapValues { (id, hash) ->
            readObject(hash, SyncEntityType.Location, id, LocationValue::class.java)
        }
        val transactions = index.transactions.toSortedMap().map { (id, hash) ->
            readObject(hash, SyncEntityType.Transaction, id, InventoryTransaction::class.java)
        }.sortedBy { it.createdAt }
        return InventorySnapshot(
            deviceId = index.deviceId,
            items = items,
            locations = locations,
            transactions = transactions,
            scanLog = localScanLog,
        )
    }

    fun readConflicts(index: SyncIndex): List<SyncConflictCandidate> = index.conflicts.toSortedMap().map { (id, hash) ->
        readObject(hash, SyncEntityType.Conflict, id, SyncConflictCandidate::class.java)
    }

    fun latestIndex(): SyncIndex? = latestId()?.let(::readIndex)

    fun hasIndex(id: String): Boolean = indexFile(id).isFile

    fun latestId(): String? = readRef("latest")

    fun latestSyncId(): String? = readRef("latest-sync")

    fun markLatestSync(indexId: String) = writeRef("latest-sync", indexId)

    fun hasObject(hash: String): Boolean = objectFile(hash).isFile

    fun objectBytes(hash: String): ByteArray = objectFile(hash).readBytes()

    fun indexBytes(id: String): ByteArray = indexFile(id).readBytes()

    fun importObject(hash: String, bytes: ByteArray) {
        val file = objectFile(hash)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
    }

    fun importIndex(id: String, bytes: ByteArray) {
        val file = indexFile(id)
        if (!file.exists()) file.writeBytes(bytes)
        readIndex(id)
    }

    fun setLatest(id: String) {
        readIndex(id)
        writeRef("latest", id)
    }

    private fun storeObject(type: SyncEntityType, entityId: String, json: String): String {
        val plain = json.toByteArray(StandardCharsets.UTF_8)
        val identity = "${type.value}\n$entityId\n$json".toByteArray(StandardCharsets.UTF_8)
        val hash = sha256Hex(identity)
        val file = objectFile(hash)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            writeEncrypted(file, plain, "${type.value}:$entityId:$hash")
        }
        return hash
    }

    private fun <T> readObject(hash: String, type: SyncEntityType, entityId: String, clazz: Class<T>): T {
        val bytes = readEncrypted(objectFile(hash), "${type.value}:$entityId:$hash")
        return gson.fromJson(String(bytes, StandardCharsets.UTF_8), clazz)
    }

    private fun canonicalIndexJson(index: SyncIndex): String = gson.toJson(
        index.copy(
            id = "",
            items = TreeMap(index.items),
            locations = TreeMap(index.locations),
            transactions = TreeMap(index.transactions),
            conflicts = TreeMap(index.conflicts),
        ),
    )

    private fun writeEncrypted(file: File, plain: ByteArray, associatedData: String) {
        file.parentFile?.mkdirs()
        val blob = crypto.encrypt(plain, associatedData.toByteArray(StandardCharsets.UTF_8))
        val temp = File(file.parentFile, ".${file.name}.tmp")
        temp.writeText(gson.toJson(blob))
        check(temp.renameTo(file) || run {
            file.writeBytes(temp.readBytes())
            temp.delete()
            true
        })
    }

    private fun readEncrypted(file: File, associatedData: String): ByteArray {
        require(file.isFile) { "同步对象不存在：${file.name}" }
        val blob = gson.fromJson(file.readText(), EncryptedSyncBlob::class.java)
        return crypto.decrypt(blob, associatedData.toByteArray(StandardCharsets.UTF_8))
    }

    private fun objectFile(hash: String): File {
        require(hash.length == 64) { "同步对象哈希格式错误。" }
        return File(File(objectsDir, hash.substring(0, 2)), hash.substring(2))
    }

    private fun indexFile(id: String): File = File(indexesDir, id)

    private fun writeRef(name: String, value: String) {
        refsDir.mkdirs()
        val target = File(refsDir, name)
        val temp = File(refsDir, ".$name.tmp")
        temp.writeText(value)
        check(temp.renameTo(target) || run {
            target.writeText(value)
            temp.delete()
            true
        })
    }

    private fun readRef(name: String): String? = File(refsDir, name)
        .takeIf(File::isFile)
        ?.readText()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}
