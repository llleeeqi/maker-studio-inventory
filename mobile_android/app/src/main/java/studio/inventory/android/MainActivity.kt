package studio.inventory.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val controller = remember { InventoryController(applicationContext) }
            val printer = remember { LabelPrinterController(applicationContext) }
            LaunchedEffect(Unit) {
                controller.load()
                if (printer.autoConnectEnabled) {
                    if (hasPrinterPermissions(applicationContext)) {
                        printer.autoConnect()
                    } else {
                        printer.markAutoConnectWaitingForPermission()
                    }
                }
            }
            DisposableEffect(Unit) {
                onDispose { printer.close() }
            }
            StudioInventoryTheme {
                StudioInventoryApp(controller, printer)
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
fun StudioInventoryApp(controller: InventoryController, printer: LabelPrinterController) {
    var page by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val destinations = listOf(
        "扫码" to Icons.Default.Search,
        "库存" to Icons.Default.Home,
        "新增" to Icons.Default.AddCircle,
        "流水" to Icons.AutoMirrored.Filled.List,
    )

    DebugScanBridge(controller)

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("工作室物品管理", style = MaterialTheme.typography.titleSmall)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(64.dp),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                destinations.forEachIndexed { index, (title, icon) ->
                    NavigationBarItem(
                        selected = page == index,
                        onClick = { page = index },
                        icon = { Icon(icon, contentDescription = title) },
                        label = { Text(title) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
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
