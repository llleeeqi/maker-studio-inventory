package studio.inventory.android

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

private const val DebugScanAction = "studio.inventory.android.DEBUG_SCAN"
private const val DebugSyncAction = "studio.inventory.android.DEBUG_SYNC_CONFIG"

@Composable
fun DebugScanBridge(controller: InventoryController) {
    val context = LocalContext.current
    DisposableEffect(context, controller) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.getStringExtra("payload")?.let(controller::handlePayload)
            }
        }
        val filter = IntentFilter(DebugScanAction)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(
                receiver,
                filter,
                Manifest.permission.DUMP,
                null,
                Context.RECEIVER_EXPORTED,
            )
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter, Manifest.permission.DUMP, null)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
}

@Composable
fun DebugSyncBridge(sync: SyncController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    DisposableEffect(context, sync, scope) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val source = intent ?: return
                val draft = sync.newConfiguration().copy(
                    webDavUrl = source.getStringExtra("url").orEmpty(),
                    username = source.getStringExtra("username").orEmpty(),
                    password = source.getStringExtra("password").orEmpty(),
                    deviceName = source.getStringExtra("device_name").orEmpty().ifBlank { "MuMu 测试设备" },
                    repositoryKey = source.getStringExtra("repository_key").orEmpty(),
                    allowInsecureHttp = source.getBooleanExtra("allow_http", false),
                )
                scope.launch { runCatching { sync.configure(draft, takeOverDeviceName = true) } }
            }
        }
        val filter = IntentFilter(DebugSyncAction)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Manifest.permission.DUMP, null, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter, Manifest.permission.DUMP, null)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
}
