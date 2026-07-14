package studio.inventory.android

data class SyncIndex(
    val schema: Int = 1,
    val id: String = "",
    val createdAt: String = nowIso(),
    val deviceId: String = "",
    val parentId: String? = null,
    val items: Map<String, String> = emptyMap(),
    val locations: Map<String, String> = emptyMap(),
    val transactions: Map<String, String> = emptyMap(),
    val conflicts: Map<String, String> = emptyMap(),
)

enum class SyncEntityType(val value: String) {
    Item("item"),
    Location("location"),
    Transaction("transaction"),
    Conflict("conflict"),
}

data class SyncConflictCandidate(
    val conflictId: String,
    val entityType: SyncEntityType,
    val entityId: String,
    val baseJson: String?,
    val localJson: String?,
    val remoteJson: String?,
    val createdAt: String = nowIso(),
)

data class SyncMergeResult(
    val snapshot: InventorySnapshot,
    val conflicts: List<SyncConflictCandidate>,
)

data class EncryptedSyncBlob(
    val schema: Int = 1,
    val algorithm: String = "AES-256-GCM",
    val nonce: String = "",
    val ciphertext: String = "",
)

data class SyncDevice(
    val deviceId: String,
    val name: String,
    val active: Boolean = true,
    val lastSeenAt: String = nowIso(),
)

enum class SyncStatus {
    Unconfigured,
    Offline,
    Online,
    Syncing,
    Blocked,
}

enum class InitialSyncStrategy {
    SafeMerge,
    CloudWins,
    LocalRebuild,
}

data class BackupRecord(
    val backupId: String,
    val scope: String,
    val path: String,
    val sha256: String,
    val manual: Boolean,
    val createdAt: String,
)
