package studio.inventory.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

class SyncController(
    context: Context,
    private val inventory: InventoryController,
) {
    private val appContext = context.applicationContext
    private val gson: Gson = GsonBuilder().create()
    private val settings = SyncSettingsRepository(appContext)
    private val database = InventoryDatabase(appContext, gson)
    private val backups = InventoryBackupManager(appContext, inventory, settings, database)

    var configuration by mutableStateOf(settings.load())
        private set
    var status by mutableStateOf(if (configuration.isConfigured) SyncStatus.Offline else SyncStatus.Unconfigured)
        private set
    var statusMessage by mutableStateOf(if (configuration.isConfigured) "等待同步" else "未配置 WebDAV")
        private set
    var lastSyncAt by mutableStateOf<String?>(database.getSyncMeta(MetaLastSyncAt))
        private set
    var pendingChanges by mutableStateOf(database.getSyncMeta(MetaPending) == "1")
        private set
    var conflicts by mutableStateOf(database.unresolvedSyncConflicts())
        private set
    var backupRecords by mutableStateOf(backups.allRecords())
        private set
    var backupMessage by mutableStateOf<String?>(null)
        private set
    var restorePending by mutableStateOf(database.getSyncMeta(MetaRestorePending) == "1")
        private set

    init {
        bindInventory()
    }

    fun reload() {
        configuration = settings.load()
        conflicts = database.unresolvedSyncConflicts()
        backupRecords = backups.allRecords()
        restorePending = database.getSyncMeta(MetaRestorePending) == "1"
        bindInventory()
        status = when {
            restorePending -> SyncStatus.Blocked
            configuration.isConfigured -> SyncStatus.Offline
            else -> SyncStatus.Unconfigured
        }
    }

    fun newConfiguration(): SyncConfiguration = settings.newConfiguration()

    fun updateInterval(seconds: Int) {
        require(seconds in SyncSettingsRepository.AllowedIntervals)
        if (!configuration.isConfigured) return
        settings.save(configuration.copy(intervalSeconds = seconds))
        configuration = settings.load()
    }

    suspend fun configure(
        candidate: SyncConfiguration,
        takeOverDeviceName: Boolean = false,
        initialStrategy: InitialSyncStrategy? = null,
        backupPassword: String? = null,
    ) {
        require(candidate.isConfigured) { "同步配置未填写完整。" }
        SyncCrypto.decodeKey(candidate.repositoryKey)
        val client = webDavClient(candidate)
        withContext(Dispatchers.IO) {
            client.testConnection()
            val store = WebDavSyncStore(client, gson)
            store.initialize()
            val nameCollision = store.devices().firstOrNull {
                it.active && it.name == candidate.deviceName && it.deviceId != candidate.deviceId
            }
            if (nameCollision != null && !takeOverDeviceName) throw DeviceNameConflictException(nameCollision)
            val latest = store.latestId()
            val localRepo = localRepository(candidate)
            val localHasContent = inventory.snapshot.hasSyncContent()
            val needsInitialChoice = if (latest != null && localHasContent && localRepo.latestSyncId() == null) {
                val remoteIndex = downloadIndexAndObjects(localRepo, store, latest)
                !inventory.snapshot.sameSyncContent(localRepo.restoreSnapshot(remoteIndex))
            } else {
                false
            }
            if (needsInitialChoice && initialStrategy == null) throw InitialSyncRequiredException()
            if (needsInitialChoice) {
                require(!backupPassword.isNullOrBlank()) { "首次绑定前需要设置至少 8 位保护备份密码。" }
                backups.createLocal(backupPassword, manual = true)
            }
            store.registerDevice(
                SyncDevice(candidate.deviceId, candidate.deviceName),
                takeOverName = takeOverDeviceName,
            )
        }
        settings.save(candidate)
        configuration = settings.load()
        database.putSyncMeta(MetaInitialStrategy, initialStrategy?.name.orEmpty())
        if (initialStrategy == InitialSyncStrategy.LocalRebuild) {
            database.putSyncMeta(MetaForceCloudRebuild, "1")
        }
        database.upsertSyncDevice(SyncDevice(configuration.deviceId, configuration.deviceName))
        pendingChanges = true
        database.putSyncMeta(MetaPending, "1")
        bindInventory()
        syncNow(force = true)
    }

    fun clearConfiguration() {
        WorkManager.getInstance(appContext).cancelUniqueWork(BackgroundWorkName)
        settings.clear()
        configuration = SyncConfiguration()
        status = SyncStatus.Unconfigured
        statusMessage = "未配置 WebDAV"
        pendingChanges = false
        database.putSyncMeta(MetaPending, "0")
        inventory.configureLocalDevice("", "")
    }

    suspend fun refreshBackups() {
        runCatching {
            withContext(Dispatchers.IO) {
                if (configuration.isConfigured) backups.refreshCloudRecords()
            }
        }.onFailure { backupMessage = it.message }
        backupRecords = backups.allRecords()
    }

    suspend fun createLocalBackup(password: String) {
        backupMessage = null
        val record = withContext(Dispatchers.IO) { backups.createLocal(password, manual = true) }
        backupRecords = backups.allRecords()
        backupMessage = "本地备份已创建：${record.createdAt}"
    }

    suspend fun createCloudBackup(password: String) {
        backupMessage = null
        val record = withContext(Dispatchers.IO) { backups.createCloud(password, manual = true) }
        backupRecords = backups.allRecords()
        backupMessage = "云端备份已创建：${record.createdAt}"
    }

    suspend fun createExportBackup(password: String): BackupExport {
        val record = withContext(Dispatchers.IO) { backups.createLocal(password, manual = true) }
        backupRecords = backups.allRecords()
        return BackupExport(
            fileName = "studio-inventory-${LocalDate.now()}.json",
            bytes = withContext(Dispatchers.IO) { backups.readLocal(record) },
        )
    }

    suspend fun importAndRestoreBackup(bytes: ByteArray, password: String) {
        val record = withContext(Dispatchers.IO) { backups.importLocal(bytes) }
        backupRecords = backups.allRecords()
        restoreBackup(record, password)
    }

    suspend fun restoreBackup(record: BackupRecord, password: String) {
        require(!restorePending) { "已有待确认的恢复结果。" }
        val restored = withContext(Dispatchers.IO) {
            val bytes = if (record.scope == "cloud") backups.readCloud(record) else backups.readLocal(record)
            if (record.sha256.isNotBlank()) require(sha256Hex(bytes) == record.sha256) { "备份校验失败，文件可能已损坏。" }
            val parsed = backups.parse(bytes)
            val credentials = parsed.sync?.let {
                backups.decryptCredentials(parsed, password) ?: error("备份缺少加密凭据。")
            }
            val rollback = backups.createLocal(password, manual = true)
            Triple(parsed, credentials, rollback)
        }
        applyRestoredBackup(restored.first, restored.second)
        database.putSyncMeta(MetaRestoreRollback, restored.third.backupId)
        database.putSyncMeta(MetaRestorePending, "1")
        restorePending = true
        backupRecords = backups.allRecords()
        status = SyncStatus.Blocked
        statusMessage = "备份已恢复，请检查后确认新基准或取消恢复"
        backupMessage = "恢复结果尚未写入云端。"
    }

    suspend fun cancelRestore() {
        val rollbackId = database.getSyncMeta(MetaRestoreRollback)
            ?: error("找不到恢复前保护备份。")
        val rollback = database.backupRecords("local").firstOrNull { it.backupId == rollbackId }
            ?: error("恢复前保护备份记录不存在。")
        val password = settings.backupPassword() ?: error("本机没有备份密码。")
        val restored = withContext(Dispatchers.IO) {
            val parsed = backups.parse(backups.readLocal(rollback))
            val credentials = parsed.sync?.let {
                backups.decryptCredentials(parsed, password) ?: error("保护备份缺少加密凭据。")
            }
            parsed to credentials
        }
        applyRestoredBackup(restored.first, restored.second)
        finishRestoreState()
        markInventoryChanged()
        backupMessage = "已取消恢复，回到恢复前数据。"
    }

    suspend fun confirmRestoreAsBaseline() {
        require(restorePending) { "当前没有待确认的恢复结果。" }
        finishRestoreState()
        if (configuration.isConfigured) {
            database.putSyncMeta(MetaForceCloudRebuild, "1")
            pendingChanges = true
            database.putSyncMeta(MetaPending, "1")
            syncNow(force = true)
        } else {
            backupMessage = "恢复结果已设为本地新基准。"
        }
    }

    fun markInventoryChanged() {
        if (!configuration.isConfigured) return
        pendingChanges = true
        database.putSyncMeta(MetaPending, "1")
        enqueueBackgroundSync(appContext, configuration.intervalSeconds.coerceAtLeast(0).toLong())
    }

    suspend fun runForegroundLoop() {
        if (!configuration.isConfigured || restorePending) return
        syncNow(force = true)
        while (configuration.isConfigured) {
            val interval = configuration.intervalSeconds
            if (interval == 0) return
            delay(interval * 1000L)
            syncNow(force = false)
        }
    }

    suspend fun syncNow(force: Boolean = true) {
        if (!configuration.isConfigured || GlobalSyncMutex.isLocked) return
        if (restorePending) {
            status = SyncStatus.Blocked
            statusMessage = "恢复结果待确认，同步已暂停"
            return
        }
        GlobalSyncMutex.withLock {
            val config = configuration
            if (!hasNetwork()) {
                status = SyncStatus.Offline
                statusMessage = "离线，本地变动已保留"
                return
            }
            status = SyncStatus.Syncing
            statusMessage = "正在检查云端"
            try {
                withContext(Dispatchers.IO) {
                    synchronize(config, force)
                }
                conflicts = database.unresolvedSyncConflicts()
                backupRecords = backups.allRecords()
                status = if (conflicts.isEmpty()) SyncStatus.Online else SyncStatus.Blocked
                statusMessage = if (conflicts.isEmpty()) "同步完成" else "有 ${conflicts.size} 个同步异常待处理"
            } catch (error: Exception) {
                val webDav = error as? WebDavException
                status = if (
                    error is CloudBusyException ||
                    (error is IOException && webDav?.statusCode == null)
                ) SyncStatus.Offline else SyncStatus.Blocked
                statusMessage = error.message ?: "同步失败"
            }
        }
    }

    fun isItemBlocked(itemId: String): Boolean = conflicts.any {
        it.entityType == SyncEntityType.Item && it.entityId == itemId
    }

    fun resolveConflict(conflictId: String) {
        database.resolveSyncConflict(conflictId)
        conflicts = database.unresolvedSyncConflicts()
        markInventoryChanged()
    }

    fun beginItemConflictResolution(itemId: String) {
        inventory.beginSyncConflictResolution(itemId)
    }

    fun resolveItemConflictAsCheckedOut(itemId: String) {
        inventory.resolveConflictAsCheckedOut(itemId)
    }

    fun resolveItemConflictAsArchived(itemId: String) {
        inventory.resolveConflictAsArchived(itemId)
    }

    fun resolveNonItemConflict(conflictId: String, useRemote: Boolean) {
        val conflict = conflicts.firstOrNull { it.conflictId == conflictId } ?: return
        if (inventory.applySyncConflictCandidate(conflict, useRemote)) {
            database.resolveSyncConflict(conflictId)
            conflicts = database.unresolvedSyncConflicts()
            markInventoryChanged()
        }
    }

    private suspend fun synchronize(config: SyncConfiguration, force: Boolean) {
        val localRepo = localRepository(config)
        val cloud = WebDavSyncStore(webDavClient(config), gson)
        cloud.initialize()
        cloud.registerDevice(SyncDevice(config.deviceId, config.deviceName))
        database.upsertSyncDevice(SyncDevice(config.deviceId, config.deviceName))

        var cloudLatestId = cloud.latestId()
        val latestSyncId = localRepo.latestSyncId()
        val forceCloudRebuild = database.getSyncMeta(MetaForceCloudRebuild) == "1"
        if (cloudLatestId == null && latestSyncId != null && !forceCloudRebuild) {
            throw WebDavException("云端最新索引指针缺失。为防止误删本地数据，请从备份恢复或明确重建云端基准。")
        }
        val hasPendingAtStart = database.getSyncMeta(MetaPending) == "1"
        if (!hasPendingAtStart && cloudLatestId != null && cloudLatestId == latestSyncId) {
            return
        }

        var lock: CloudSyncLock? = null
        for (attempt in 0 until 3) {
            lock = cloud.acquireLock(config.deviceId, config.deviceName)
            if (lock != null) break
            if (attempt < 2) Thread.sleep(5_000L)
        }
        val acquired = lock ?: throw CloudBusyException()
        coroutineScope {
            val refreshJob = launch(Dispatchers.IO) {
                while (isActive) {
                    delay(30_000L)
                    if (cloud.refreshLock(acquired) == null) {
                        throw WebDavException("云端同步锁已失效，已停止本次同步。")
                    }
                }
            }
            try {
                cloudLatestId = cloud.latestId()
                val hasPendingAfterLock = database.getSyncMeta(MetaPending) == "1"
                if (!hasPendingAfterLock && cloudLatestId != null && cloudLatestId == localRepo.latestSyncId()) {
                    return@coroutineScope
                }
                val localSnapshot = inventory.snapshot
                val initialStrategy = database.getSyncMeta(MetaInitialStrategy)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { InitialSyncStrategy.valueOf(it) }.getOrNull() }

                if (initialStrategy == InitialSyncStrategy.CloudWins && cloudLatestId != null && latestSyncId == null) {
                    val remoteIndex = downloadIndexAndObjects(localRepo, cloud, requireNotNull(cloudLatestId))
                    val remoteSnapshot = localRepo.restoreSnapshot(remoteIndex)
                    withContext(Dispatchers.Main) { inventory.replaceFromSync(remoteSnapshot) }
                    localRepo.setLatest(remoteIndex.id)
                    localRepo.markLatestSync(remoteIndex.id)
                    database.putSyncMeta(MetaInitialStrategy, "")
                    markSuccessfulSync()
                    maybeCreateAutomaticBackups(changed = true)
                    cleanupCloudHistoryIfDue(localRepo, cloud, remoteIndex.id)
                    return@coroutineScope
                }
                val localIndex = localRepo.createSnapshot(
                    snapshot = localSnapshot,
                    deviceId = config.deviceId,
                    conflicts = conflicts,
                )

                if (forceCloudRebuild) {
                    uploadIndexAndObjects(localRepo, cloud, localIndex)
                    cloud.updateLatest(localIndex.id)
                    localRepo.markLatestSync(localIndex.id)
                    database.putSyncMeta(MetaForceCloudRebuild, "0")
                    database.putSyncMeta(MetaInitialStrategy, "")
                    markSuccessfulSync()
                    maybeCreateAutomaticBackups(changed = true)
                    cleanupCloudHistoryIfDue(localRepo, cloud, localIndex.id)
                    return@coroutineScope
                }

                if (cloudLatestId == null) {
                    uploadIndexAndObjects(localRepo, cloud, localIndex)
                    cloud.updateLatest(localIndex.id)
                    localRepo.markLatestSync(localIndex.id)
                    database.putSyncMeta(MetaInitialStrategy, "")
                    markSuccessfulSync()
                    maybeCreateAutomaticBackups(changed = true)
                    cleanupCloudHistoryIfDue(localRepo, cloud, localIndex.id)
                    return@coroutineScope
                }

                val remoteIndex = downloadIndexAndObjects(localRepo, cloud, requireNotNull(cloudLatestId))
                val remoteSnapshot = localRepo.restoreSnapshot(remoteIndex)
                val baseSnapshot = latestSyncId
                    ?.takeIf(localRepo::hasIndex)
                    ?.let(localRepo::readIndex)
                    ?.let(localRepo::restoreSnapshot)
                    ?: InventorySnapshot()
                val merge = SyncMergeEngine(gson).merge(baseSnapshot, localSnapshot, remoteSnapshot)
                val remoteConflicts = localRepo.readConflicts(remoteIndex)
                database.upsertSyncConflicts(merge.conflicts + remoteConflicts)
                val activeConflictIds = (merge.conflicts + remoteConflicts).map { it.conflictId }.toSet()
                database.resolveOpenConflictsNotIn(activeConflictIds)
                val allConflicts = database.unresolvedSyncConflicts()
                withContext(Dispatchers.Main) { inventory.replaceFromSync(merge.snapshot) }

                val mergedIndex = localRepo.createSnapshot(
                    snapshot = merge.snapshot,
                    deviceId = config.deviceId,
                    parentId = remoteIndex.id,
                    conflicts = allConflicts,
                )
                uploadIndexAndObjects(localRepo, cloud, mergedIndex)
                cloud.updateLatest(mergedIndex.id)
                localRepo.markLatestSync(mergedIndex.id)
                database.putSyncMeta(MetaInitialStrategy, "")
                markSuccessfulSync()
                maybeCreateAutomaticBackups(changed = hasPendingAfterLock || remoteIndex.id != latestSyncId)
                cleanupCloudHistoryIfDue(localRepo, cloud, mergedIndex.id)
            } finally {
                refreshJob.cancelAndJoin()
                runCatching { cloud.releaseLock(acquired) }
            }
        }
    }

    private fun uploadIndexAndObjects(local: SyncRepository, cloud: WebDavSyncStore, index: SyncIndex) {
        val hashes = index.items.values + index.locations.values + index.transactions.values + index.conflicts.values
        hashes.distinct().forEach { hash -> cloud.putObject(hash, local.objectBytes(hash)) }
        cloud.putIndex(index.id, local.indexBytes(index.id))
    }

    private fun downloadIndexAndObjects(local: SyncRepository, cloud: WebDavSyncStore, id: String): SyncIndex {
        if (!local.hasIndex(id)) local.importIndex(id, cloud.getIndex(id))
        val index = local.readIndex(id)
        val hashes = index.items.values + index.locations.values + index.transactions.values + index.conflicts.values
        hashes.distinct().forEach { hash ->
            if (!local.hasObject(hash)) local.importObject(hash, cloud.getObject(hash))
        }
        return index
    }

    private suspend fun markSuccessfulSync() {
        val completedAt = nowIso()
        database.putSyncMeta(MetaPending, "0")
        database.putSyncMeta(MetaLastSyncAt, completedAt)
        withContext(Dispatchers.Main) {
            pendingChanges = false
            lastSyncAt = completedAt
        }
    }

    private suspend fun maybeCreateAutomaticBackups(changed: Boolean) {
        if (!changed) return
        val password = settings.backupPassword() ?: return
        val today = LocalDate.now().toString()
        if (database.claimSyncMetaValue(MetaLocalAutoBackupDate, today)) {
            runCatching { backups.createLocal(password, manual = false) }
                .onFailure { database.putSyncMeta(MetaLocalAutoBackupDate, "") }
        }
        if (configuration.isConfigured && database.claimSyncMetaValue(MetaCloudAutoBackupDate, today)) {
            runCatching { backups.createCloud(password, manual = false) }
                .onFailure { database.putSyncMeta(MetaCloudAutoBackupDate, "") }
        }
    }

    private fun cleanupCloudHistoryIfDue(
        local: SyncRepository,
        cloud: WebDavSyncStore,
        latestId: String,
    ) {
        val ids = cloud.indexIds()
        val previous = database.getSyncMeta(MetaLastCleanupAt)?.let {
            runCatching { OffsetDateTime.parse(it) }.getOrNull()
        }
        val dueByAge = previous != null && Duration.between(previous, OffsetDateTime.now()).toDays() >= 7
        if (previous == null && ids.size < 100) {
            database.putSyncMeta(MetaLastCleanupAt, nowIso())
            return
        }
        if (ids.size < 100 && !dueByAge) return

        val indexes = ids.map { id ->
            if (!local.hasIndex(id)) local.importIndex(id, cloud.getIndex(id))
            local.readIndex(id)
        }
        val retained = (indexes.sortedByDescending { it.createdAt }.take(20).map { it.id } + latestId).toSet()
        indexes.asSequence()
            .map { it.id }
            .filterNot(retained::contains)
            .forEach(cloud::deleteIndex)

        val referenced = indexes.asSequence()
            .filter { retained.contains(it.id) }
            .flatMap { index ->
                (index.items.values + index.locations.values + index.transactions.values + index.conflicts.values).asSequence()
            }
            .toSet()
        cloud.objectHashes().asSequence()
            .filterNot(referenced::contains)
            .forEach(cloud::deleteObject)
        database.putSyncMeta(MetaLastCleanupAt, nowIso())
    }

    private fun applyRestoredBackup(
        backup: FullInventoryBackup,
        credentials: BackupCredentialPayload?,
    ) {
        inventory.replaceFromBackup(backup.inventory)
        val sync = backup.sync
        if (sync != null && credentials != null) {
            val currentDeviceId = configuration.deviceId.ifBlank { UUID.randomUUID().toString() }
            settings.save(
                SyncConfiguration(
                    webDavUrl = sync.webDavUrl,
                    username = sync.username,
                    password = credentials.webDavPassword,
                    deviceId = currentDeviceId,
                    deviceName = sync.deviceName.ifBlank { SyncSettingsRepository.defaultDeviceName() },
                    repositoryKey = credentials.repositoryKey,
                    intervalSeconds = sync.intervalSeconds,
                    allowInsecureHttp = sync.allowInsecureHttp,
                ),
            )
        } else {
            settings.clear()
        }
        configuration = settings.load()
        bindInventory()
    }

    private fun finishRestoreState() {
        database.putSyncMeta(MetaRestorePending, "0")
        database.putSyncMeta(MetaRestoreRollback, "")
        restorePending = false
        status = if (configuration.isConfigured) SyncStatus.Offline else SyncStatus.Unconfigured
        statusMessage = if (configuration.isConfigured) "等待同步" else "未配置 WebDAV"
    }

    private fun bindInventory() {
        inventory.onInventoryChanged = ::markInventoryChanged
        inventory.isItemSyncBlocked = ::isItemBlocked
        inventory.onSyncConflictResolved = ::resolveItemConflicts
        if (configuration.isConfigured) {
            inventory.configureLocalDevice(configuration.deviceId, configuration.deviceName)
        } else {
            inventory.configureLocalDevice("", "")
        }
    }

    private fun resolveItemConflicts(itemId: String) {
        database.unresolvedSyncConflicts()
            .filter { it.entityType == SyncEntityType.Item && it.entityId == itemId }
            .forEach { database.resolveSyncConflict(it.conflictId) }
        conflicts = database.unresolvedSyncConflicts()
        markInventoryChanged()
    }

    private fun webDavClient(config: SyncConfiguration) = WebDavClient(
        baseUrl = config.webDavUrl,
        username = config.username,
        password = config.password,
        allowInsecureHttp = config.allowInsecureHttp,
    )

    private fun localRepository(config: SyncConfiguration): SyncRepository {
        val crypto = SyncCrypto(SyncCrypto.decodeKey(config.repositoryKey))
        val identity = sha256Hex("${config.webDavUrl}\n${config.username}\n${config.repositoryKey}".toByteArray()).take(20)
        return SyncRepository(File(appContext.filesDir, "sync_repo_v1/$identity"), gson, crypto)
    }

    private fun InventorySnapshot.hasSyncContent(): Boolean =
        items.isNotEmpty() || locations.isNotEmpty() || transactions.isNotEmpty()

    private fun InventorySnapshot.sameSyncContent(other: InventorySnapshot): Boolean =
        items == other.items && locations == other.locations && transactions == other.transactions

    private fun hasNetwork(): Boolean {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val MetaPending = "pending_changes"
        private const val MetaLastSyncAt = "last_sync_at"
        private const val MetaRestorePending = "restore_pending"
        private const val MetaRestoreRollback = "restore_rollback_backup_id"
        private const val MetaForceCloudRebuild = "force_cloud_rebuild"
        private const val MetaLocalAutoBackupDate = "local_auto_backup_date"
        private const val MetaCloudAutoBackupDate = "cloud_auto_backup_date"
        private const val MetaLastCleanupAt = "last_cloud_cleanup_at"
        private const val MetaInitialStrategy = "initial_sync_strategy"
        private const val BackgroundWorkName = "studio-inventory-webdav-sync"
        private val GlobalSyncMutex = Mutex()

        fun enqueueBackgroundSync(context: Context, delaySeconds: Long = 0) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                BackgroundWorkName,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

private class CloudBusyException : Exception("云端正在由其他设备同步，稍后会自动重试。")

class InitialSyncRequiredException : Exception("本机和云端都有不同数据，需要选择首次绑定方式。")
