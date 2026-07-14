package studio.inventory.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val inventory = InventoryController(applicationContext).apply { load() }
        val sync = SyncController(applicationContext, inventory)
        if (!sync.configuration.isConfigured) return Result.success()
        sync.syncNow(force = true)
        return when (sync.status) {
            SyncStatus.Online, SyncStatus.Unconfigured -> Result.success()
            SyncStatus.Offline, SyncStatus.Syncing -> Result.retry()
            SyncStatus.Blocked -> Result.failure()
        }
    }
}
