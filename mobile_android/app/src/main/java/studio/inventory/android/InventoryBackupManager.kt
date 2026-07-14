package studio.inventory.android

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class InventoryBackupManager(
    context: Context,
    private val inventory: InventoryController,
    private val settings: SyncSettingsRepository,
    private val database: InventoryDatabase,
) {
    private val appContext = context.applicationContext
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val credentialCrypto = BackupCredentialCrypto(gson)
    private val localDir = File(appContext.filesDir, "inventory_backups").apply { mkdirs() }

    fun createLocal(password: String, manual: Boolean = true): BackupRecord {
        settings.saveBackupPassword(password)
        val bytes = buildBackup(password)
        val record = writeLocal(bytes, manual)
        pruneLocalAutomatic()
        return record
    }

    fun createCloud(password: String, manual: Boolean = true): BackupRecord {
        val config = settings.load()
        require(config.isConfigured) { "尚未配置 WebDAV。" }
        settings.saveBackupPassword(password)
        val bytes = buildBackup(password)
        val prefix = if (manual) "manual" else "auto"
        val fileName = "$prefix-${fileTimestamp()}-${config.deviceName.safeFileName()}-${shortId()}.json"
        val cloud = WebDavSyncStore(
            WebDavClient(
                config.webDavUrl,
                config.username,
                config.password,
                config.allowInsecureHttp,
            ),
            gson,
        )
        cloud.initialize()
        cloud.putBackup(fileName, bytes)
        val record = BackupRecord(
            backupId = "cloud-${UUID.randomUUID()}",
            scope = "cloud",
            path = fileName,
            sha256 = sha256Hex(bytes),
            manual = manual,
            createdAt = nowIso(),
        )
        database.upsertBackupRecord(record)
        if (!manual) pruneCloudAutomatic(cloud)
        return record
    }

    fun localRecords(): List<BackupRecord> = database.backupRecords("local").filter { File(it.path).isFile }

    fun cloudRecords(): List<BackupRecord> = database.backupRecords("cloud")

    fun allRecords(): List<BackupRecord> {
        val records = localRecords() + cloudRecords()
        records.groupBy { "${it.scope}:${it.path}" }.values.forEach { duplicates ->
            duplicates.sortedByDescending { it.createdAt }.drop(1).forEach {
                database.deleteBackupRecord(it.backupId)
            }
        }
        return (localRecords() + cloudRecords()).sortedByDescending { it.createdAt }
    }

    fun refreshCloudRecords(): List<BackupRecord> {
        val config = settings.load()
        if (!config.isConfigured) return cloudRecords()
        val cloud = cloudStore(config)
        cloud.initialize()
        val cloudPaths = cloud.backupFiles().map { it.path.substringAfterLast('/') }.toSet()
        cloudRecords().filterNot { cloudPaths.contains(it.path) }.forEach {
            database.deleteBackupRecord(it.backupId)
        }
        cloudPaths.forEach { fileName ->
            val known = cloudRecords().firstOrNull { it.path == fileName }
            if (known == null) {
                database.upsertBackupRecord(
                    BackupRecord(
                        backupId = "cloud-file-${sha256Hex(fileName.toByteArray()).take(24)}",
                        scope = "cloud",
                        path = fileName,
                        sha256 = "",
                        manual = fileName.startsWith("manual-"),
                        createdAt = timestampFromFileName(fileName),
                    ),
                )
            }
        }
        return cloudRecords()
    }

    fun readLocal(record: BackupRecord): ByteArray = File(record.path).readBytes()

    fun importLocal(bytes: ByteArray): BackupRecord {
        parse(bytes)
        return writeLocal(bytes, manual = true)
    }

    fun readCloud(record: BackupRecord): ByteArray {
        val config = settings.load()
        return cloudStore(config).getBackup(record.path)
    }

    fun parse(bytes: ByteArray): FullInventoryBackup = gson.fromJson(
        String(bytes, Charsets.UTF_8),
        FullInventoryBackup::class.java,
    )

    fun decryptCredentials(backup: FullInventoryBackup, password: String): BackupCredentialPayload? =
        backup.sync?.encryptedCredentials?.let { credentialCrypto.decrypt(it, password.toCharArray()) }

    private fun buildBackup(password: String): ByteArray {
        val config = settings.load()
        val sync = if (config.isConfigured) {
            BackupSyncSettings(
                webDavUrl = config.webDavUrl,
                username = config.username,
                deviceName = config.deviceName,
                intervalSeconds = config.intervalSeconds,
                allowInsecureHttp = config.allowInsecureHttp,
                encryptedCredentials = credentialCrypto.encrypt(
                    BackupCredentialPayload(config.password, config.repositoryKey),
                    password.toCharArray(),
                ),
            )
        } else {
            null
        }
        val backup = FullInventoryBackup(
            backupId = "backup-${UUID.randomUUID()}",
            inventory = inventory.snapshot,
            sync = sync,
        )
        return gson.toJson(backup).toByteArray(Charsets.UTF_8)
    }

    private fun writeLocal(bytes: ByteArray, manual: Boolean): BackupRecord {
        val prefix = if (manual) "manual" else "auto"
        val file = File(localDir, "$prefix-${fileTimestamp()}-${shortId()}.json")
        file.writeBytes(bytes)
        val record = BackupRecord(
            backupId = "local-${UUID.randomUUID()}",
            scope = "local",
            path = file.absolutePath,
            sha256 = sha256Hex(bytes),
            manual = manual,
            createdAt = nowIso(),
        )
        database.upsertBackupRecord(record)
        return record
    }

    private fun pruneLocalAutomatic() {
        database.backupRecords("local")
            .filter { !it.manual }
            .drop(10)
            .forEach {
                File(it.path).delete()
                database.deleteBackupRecord(it.backupId)
            }
    }

    private fun pruneCloudAutomatic(cloud: WebDavSyncStore) {
        cloud.backupFiles()
            .map { it.path.substringAfterLast('/') }
            .filter { it.startsWith("auto-") }
            .sortedDescending()
            .drop(30)
            .forEach(cloud::deleteBackup)
    }

    private fun fileTimestamp(): String = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

    private fun shortId(): String = UUID.randomUUID().toString().take(6)

    private fun timestampFromFileName(fileName: String): String {
        val compact = Regex("(?:auto|manual)-(\\d{8}-\\d{6})").find(fileName)?.groupValues?.getOrNull(1)
            ?: return nowIso()
        return runCatching {
            OffsetDateTime.now()
                .withYear(compact.substring(0, 4).toInt())
                .withMonth(compact.substring(4, 6).toInt())
                .withDayOfMonth(compact.substring(6, 8).toInt())
                .withHour(compact.substring(9, 11).toInt())
                .withMinute(compact.substring(11, 13).toInt())
                .withSecond(compact.substring(13, 15).toInt())
                .withNano(0)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }.getOrDefault(nowIso())
    }

    private fun cloudStore(config: SyncConfiguration) = WebDavSyncStore(
        WebDavClient(config.webDavUrl, config.username, config.password, config.allowInsecureHttp),
        gson,
    )

    private fun String.safeFileName(): String = replace(Regex("[^A-Za-z0-9._-]+"), "_").take(40).ifBlank { "device" }
}
