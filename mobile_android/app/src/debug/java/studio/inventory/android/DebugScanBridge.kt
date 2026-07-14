package studio.inventory.android

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

private const val DebugScanAction = "studio.inventory.android.DEBUG_SCAN"

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
