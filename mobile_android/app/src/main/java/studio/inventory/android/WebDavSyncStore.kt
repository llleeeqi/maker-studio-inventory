package studio.inventory.android

import com.google.gson.Gson
import java.util.UUID

data class CloudSyncLock(
    val deviceId: String,
    val deviceName: String,
    val token: String,
    val timeMillis: Long,
)

class DeviceNameConflictException(val existing: SyncDevice) : Exception("设备名已存在：${existing.name}")

class WebDavSyncStore(
    private val client: WebDavClient,
    private val gson: Gson,
) {
    fun initialize() {
        client.ensureCollections(
            Root,
            SyncRoot,
            VersionRoot,
            ObjectsRoot,
            IndexesRoot,
            RefsRoot,
            DevicesRoot,
            ConflictsRoot,
            BackupsRoot,
        )
    }

    fun latestId(): String? = client.get("$RefsRoot/latest")
        ?.toString(Charsets.UTF_8)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    fun updateLatest(id: String) {
        client.put("$RefsRoot/latest", id.toByteArray(Charsets.UTF_8), "text/plain; charset=utf-8")
    }

    fun hasObject(hash: String): Boolean = client.exists(objectPath(hash))

    fun getObject(hash: String): ByteArray = client.get(objectPath(hash))
        ?: throw WebDavException("云端同步对象不存在：$hash", 404)

    fun putObject(hash: String, bytes: ByteArray) {
        val prefix = hash.substring(0, 2)
        client.makeCollection("$ObjectsRoot/$prefix")
        if (!hasObject(hash)) client.put(objectPath(hash), bytes)
    }

    fun getIndex(id: String): ByteArray = client.get("$IndexesRoot/$id")
        ?: throw WebDavException("云端同步索引不存在：$id", 404)

    fun putIndex(id: String, bytes: ByteArray) {
        if (!client.exists("$IndexesRoot/$id")) client.put("$IndexesRoot/$id", bytes)
    }

    fun indexIds(): List<String> = client.list(IndexesRoot)
        .asSequence()
        .filter { !it.collection }
        .map { it.path.substringAfterLast('/') }
        .filter { it.length == 64 }
        .toList()

    fun deleteIndex(id: String) {
        client.delete("$IndexesRoot/$id")
    }

    fun objectHashes(): List<String> = client.list(ObjectsRoot)
        .asSequence()
        .filter { it.collection }
        .map { it.path.trimEnd('/').substringAfterLast('/') }
        .filter { it.length == 2 }
        .flatMap { prefix ->
            client.list("$ObjectsRoot/$prefix")
                .asSequence()
                .filter { !it.collection }
                .map { prefix + it.path.substringAfterLast('/') }
        }
        .filter { it.length == 64 }
        .toList()

    fun deleteObject(hash: String) {
        client.delete(objectPath(hash))
    }

    fun registerDevice(device: SyncDevice, takeOverName: Boolean = false) {
        val collision = devices().firstOrNull { it.active && it.name == device.name && it.deviceId != device.deviceId }
        if (collision != null && !takeOverName) throw DeviceNameConflictException(collision)
        if (collision != null) putDevice(collision.copy(active = false, lastSeenAt = nowIso()))
        putDevice(device.copy(active = true, lastSeenAt = nowIso()))
    }

    fun devices(): List<SyncDevice> = client.list(DevicesRoot)
        .asSequence()
        .filter { !it.collection && it.path.substringAfterLast('/').endsWith(".json") }
        .mapNotNull { entry ->
            val name = entry.path.substringAfterLast('/')
            client.get("$DevicesRoot/$name")?.let { bytes ->
                runCatching { gson.fromJson(String(bytes, Charsets.UTF_8), SyncDevice::class.java) }.getOrNull()
            }
        }
        .toList()

    fun acquireLock(deviceId: String, deviceName: String, nowMillis: Long = System.currentTimeMillis()): CloudSyncLock? {
        val current = readLock()
        if (current != null && nowMillis <= current.timeMillis + LockExpiryMillis) {
            return null
        }
        val candidate = CloudSyncLock(
            deviceId = deviceId,
            deviceName = deviceName,
            token = UUID.randomUUID().toString(),
            timeMillis = nowMillis,
        )
        client.put(LockPath, gson.toJson(candidate).toByteArray(Charsets.UTF_8), "application/json")
        return readLock()?.takeIf { it.token == candidate.token }
    }

    fun refreshLock(lock: CloudSyncLock): CloudSyncLock? {
        val current = readLock() ?: return null
        if (current.token != lock.token) return null
        val refreshed = lock.copy(timeMillis = System.currentTimeMillis())
        client.put(LockPath, gson.toJson(refreshed).toByteArray(Charsets.UTF_8), "application/json")
        return readLock()?.takeIf { it.token == lock.token }
    }

    fun releaseLock(lock: CloudSyncLock) {
        if (readLock()?.token == lock.token) client.delete(LockPath)
    }

    fun putBackup(fileName: String, bytes: ByteArray) {
        client.put("$BackupsRoot/$fileName", bytes, "application/json; charset=utf-8")
    }

    fun getBackup(fileName: String): ByteArray = client.get("$BackupsRoot/$fileName")
        ?: throw WebDavException("云端备份不存在：$fileName", 404)

    fun deleteBackup(fileName: String) {
        client.delete("$BackupsRoot/$fileName")
    }

    fun backupFiles(): List<WebDavEntry> = client.list(BackupsRoot)
        .filter { !it.collection && it.path.substringAfterLast('/').endsWith(".json") }

    private fun putDevice(device: SyncDevice) {
        client.put(
            "$DevicesRoot/${device.deviceId}.json",
            gson.toJson(device).toByteArray(Charsets.UTF_8),
            "application/json",
        )
    }

    private fun readLock(): CloudSyncLock? = client.get(LockPath)?.let { bytes ->
        runCatching { gson.fromJson(String(bytes, Charsets.UTF_8), CloudSyncLock::class.java) }.getOrNull()
    }

    private fun objectPath(hash: String): String = "$ObjectsRoot/${hash.substring(0, 2)}/${hash.substring(2)}"

    companion object {
        private const val Root = "studio-inventory"
        private const val SyncRoot = "$Root/sync"
        private const val VersionRoot = "$SyncRoot/v1"
        private const val ObjectsRoot = "$VersionRoot/objects"
        private const val IndexesRoot = "$VersionRoot/indexes"
        private const val RefsRoot = "$VersionRoot/refs"
        private const val DevicesRoot = "$VersionRoot/devices"
        private const val ConflictsRoot = "$VersionRoot/conflicts"
        private const val BackupsRoot = "$Root/backups"
        private const val LockPath = "$VersionRoot/lock-sync.json"
        const val LockExpiryMillis = 65_000L
    }
}
