package studio.inventory.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
            secondary = Color(0xFF475569),
            tertiary = Color(0xFFB45309),
            surface = Color(0xFFFFFFFF),
            background = Color(0xFFF7F8FA),
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
    val titles = listOf("扫码", "库存", "新增", "流水")

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
            NavigationBar {
                titles.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = page == index,
                        onClick = { page = index },
                        icon = { Text(title.take(1)) },
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
