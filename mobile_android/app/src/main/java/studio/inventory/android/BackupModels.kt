package studio.inventory.android

data class BackupSyncSettings(
    val webDavUrl: String = "",
    val username: String = "",
    val deviceName: String = "",
    val intervalSeconds: Int = 5,
    val allowInsecureHttp: Boolean = false,
    val encryptedCredentials: EncryptedCredentialBlock? = null,
)

data class BackupCredentialPayload(
    val webDavPassword: String = "",
    val repositoryKey: String = "",
)

data class EncryptedCredentialBlock(
    val schema: Int = 1,
    val algorithm: String = "AES-256-GCM",
    val kdf: String = "PBKDF2-HMAC-SHA256",
    val iterations: Int = 210_000,
    val salt: String = "",
    val nonce: String = "",
    val ciphertext: String = "",
)

data class FullInventoryBackup(
    val schema: Int = 1,
    val backupId: String = "",
    val createdAt: String = nowIso(),
    val appVersion: String = BuildConfig.VERSION_NAME,
    val inventory: InventorySnapshot = InventorySnapshot(),
    val sync: BackupSyncSettings? = null,
)

data class BackupExport(
    val fileName: String,
    val bytes: ByteArray,
)
