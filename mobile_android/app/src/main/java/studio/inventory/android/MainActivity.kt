package studio.inventory.android

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setContent {
            val controller = remember { InventoryController(applicationContext) }
            val sync = remember { SyncController(applicationContext, controller) }
            val printer = remember { LabelPrinterController(applicationContext) }
            var loaded by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                controller.load()
                sync.reload()
                loaded = true
                if (printer.autoConnectEnabled) {
                    if (hasPrinterPermissions(applicationContext)) {
                        printer.autoConnect()
                    } else {
                        printer.markAutoConnectWaitingForPermission()
                    }
                }
            }
            DisposableEffect(Unit) {
                onDispose {
                    if (sync.pendingChanges) SyncController.enqueueBackgroundSync(applicationContext)
                    printer.close()
                }
            }
            StudioInventoryTheme {
                StudioInventoryApp(controller, printer, sync, loaded)
            }
        }
    }
}

@Composable
private fun StudioInventoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF0F766E),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFCCFBF1),
            onPrimaryContainer = Color(0xFF134E4A),
            secondary = Color(0xFF475569),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE2E8F0),
            onSecondaryContainer = Color(0xFF1E293B),
            tertiary = Color(0xFFB45309),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFEF3C7),
            onTertiaryContainer = Color(0xFF78350F),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF111827),
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF475569),
            surfaceBright = Color.White,
            surfaceDim = Color(0xFFE2E8F0),
            surfaceContainer = Color(0xFFF8FAFC),
            surfaceContainerHigh = Color(0xFFF1F5F9),
            surfaceContainerHighest = Color(0xFFE2E8F0),
            surfaceContainerLow = Color.White,
            surfaceContainerLowest = Color.White,
            background = Color(0xFFF8FAFC),
            onBackground = Color(0xFF111827),
            outline = Color(0xFF94A3B8),
            outlineVariant = Color(0xFFCBD5E1),
        ),
        content = {
            Surface(color = MaterialTheme.colorScheme.background) {
                content()
            }
        },
    )
}

@Composable
fun StudioInventoryApp(
    controller: InventoryController,
    printer: LabelPrinterController,
    sync: SyncController,
    loaded: Boolean = true,
) {
    var page by remember { mutableIntStateOf(0) }
    var showSyncCenter by remember { mutableStateOf(false) }
    var lastExitRequestAt by remember { mutableLongStateOf(0L) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val destinations = listOf(
        "扫码" to Icons.Default.Search,
        "库存" to Icons.Default.Home,
        "新增" to Icons.Default.AddCircle,
        "流水" to Icons.AutoMirrored.Filled.List,
    )

    DebugScanBridge(controller)
    DebugSyncBridge(sync)

    LaunchedEffect(loaded, sync.configuration.isConfigured, sync.configuration.intervalSeconds) {
        if (loaded && sync.configuration.isConfigured) sync.runForegroundLoop()
    }

    BackHandler {
        when {
            showSyncCenter -> {
                showSyncCenter = false
                page = 0
                scope.launch { sync.syncNow(force = true) }
            }
            page != 0 -> {
                page = 0
                scope.launch { sync.syncNow(force = true) }
            }
            !sync.pendingChanges -> activity?.finish()
            System.currentTimeMillis() - lastExitRequestAt <= 2_000L -> {
                SyncController.enqueueBackgroundSync(context)
                activity?.finish()
            }
            else -> {
                lastExitRequestAt = System.currentTimeMillis()
                SyncController.enqueueBackgroundSync(context)
                scope.launch {
                    snackbarHostState.showSnackbar("再次返回将退出，同步会在后台继续")
                }
                scope.launch { sync.syncNow(force = true) }
            }
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .height(38.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "工作室物品管理",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                SyncStatusIndicator(sync = sync, onClick = { showSyncCenter = true })
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(64.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                destinations.forEachIndexed { index, (title, icon) ->
                    NavigationBarItem(
                        selected = !showSyncCenter && page == index,
                        onClick = {
                            showSyncCenter = false
                            page = index
                        },
                        icon = { Icon(icon, contentDescription = title) },
                        label = { Text(title) },
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val modifier = Modifier.padding(padding)
        if (showSyncCenter) {
            SyncCenterPage(
                sync = sync,
                modifier = modifier,
                onOpenItem = {
                    showSyncCenter = false
                    page = 0
                },
            )
        } else {
            when (page) {
                0 -> ScanWorkspacePage(controller = controller, modifier = modifier)
                1 -> InventoryPage(controller = controller, modifier = modifier)
                2 -> AddLabelPage(
                    controller = controller,
                    printer = printer,
                    snackbarHostState = snackbarHostState,
                    modifier = modifier,
                )
                3 -> TransactionsPage(controller = controller, modifier = modifier)
            }
        }
    }
}
