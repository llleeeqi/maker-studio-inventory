@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package studio.inventory.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SyncStatusIndicator(sync: SyncController, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = when (sync.status) {
        SyncStatus.Unconfigured, SyncStatus.Offline -> Color(0xFF94A3B8)
        SyncStatus.Online, SyncStatus.Syncing -> Color(0xFF16A34A)
        SyncStatus.Blocked -> Color(0xFFDC2626)
    }
    Box(
        modifier = modifier
            .size(34.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (sync.status == SyncStatus.Syncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = color,
                strokeWidth = 2.dp,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_cloud_status),
            contentDescription = "云同步：${sync.statusMessage}",
            tint = color,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun SyncCenterPage(sync: SyncController, modifier: Modifier = Modifier, onOpenItem: (String) -> Unit = {}) {
    if (!sync.configuration.isConfigured) {
        SyncSetupPage(sync, modifier)
        return
    }
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var directResolution by remember { mutableStateOf<Pair<SyncConflictCandidate, String>?>(null) }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            Text("同步与备份", style = MaterialTheme.typography.titleLarge)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(sync.statusMessage, style = MaterialTheme.typography.titleMedium)
                    Text("设备：${sync.configuration.deviceName}")
                    Text("最后同步：${displayTimestamp(sync.lastSyncAt)}")
                    Text(if (sync.pendingChanges) "存在待同步变动" else "本地已同步")
                    Button(onClick = { scope.launch { sync.syncNow(force = true) } }) { Text("立即同步") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("前台同步间隔", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 10, 30, 60, 0).forEach { seconds ->
                            FilterChip(
                                selected = sync.configuration.intervalSeconds == seconds,
                                onClick = { sync.updateInterval(seconds) },
                                label = { Text(if (seconds == 0) "仅手动" else "${seconds}秒") },
                            )
                        }
                    }
                }
            }
        }
        item { BackupPanel(sync) }
        if (sync.conflicts.isNotEmpty()) {
            item { Text("同步异常 ${sync.conflicts.size}", style = MaterialTheme.typography.titleMedium, color = Color(0xFFDC2626)) }
            items(sync.conflicts, key = { it.conflictId }) { conflict ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${conflict.entityType.value} · ${conflict.entityId}", style = MaterialTheme.typography.titleMedium)
                        Text("本机和云端都修改过，未自动覆盖。")
                        if (conflict.entityType == SyncEntityType.Item) {
                            Button(onClick = {
                                sync.beginItemConflictResolution(conflict.entityId)
                                onOpenItem(conflict.entityId)
                            }) { Text("重新确认在库") }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { directResolution = conflict to "checkout" }) { Text("确认已出库") }
                                TextButton(onClick = { directResolution = conflict to "archive" }) { Text("确认归档") }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { directResolution = conflict to "local" }) { Text("保留本机") }
                                TextButton(onClick = { directResolution = conflict to "remote" }) { Text("采用云端") }
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("WebDAV", style = MaterialTheme.typography.titleMedium)
                    Text(sync.configuration.webDavUrl)
                    Text(sync.configuration.username, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { confirmClear = true }) { Text("清除同步配置") }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除同步配置？") },
            text = { Text("不会删除本地库存，只会停止同步并清除本机 WebDAV 凭据。") },
            confirmButton = {
                Button(onClick = {
                    sync.clearConfiguration()
                    confirmClear = false
                }) { Text("确认清除") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
    directResolution?.let { (conflict, action) ->
        AlertDialog(
            onDismissRequest = { directResolution = null },
            title = {
                Text(
                    when (action) {
                        "checkout" -> "确认物品已出库？"
                        "archive" -> "确认归档物品？"
                        "remote" -> "采用云端版本？"
                        else -> "保留本机版本？"
                    },
                )
            },
            text = {
                Text(
                    if (conflict.entityType == SyncEntityType.Item) {
                        "这会解决该物品的同步异常，并写入一条校正流水。"
                    } else {
                        "这会把选中的完整记录作为当前结果，不会按字段拼接。"
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    when (action) {
                        "checkout" -> sync.resolveItemConflictAsCheckedOut(conflict.entityId)
                        "archive" -> sync.resolveItemConflictAsArchived(conflict.entityId)
                        "remote" -> sync.resolveNonItemConflict(conflict.conflictId, useRemote = true)
                        else -> sync.resolveNonItemConflict(conflict.conflictId, useRemote = false)
                    }
                    directResolution = null
                }) { Text("确认") }
            },
            dismissButton = { OutlinedButton(onClick = { directResolution = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SyncSetupPage(sync: SyncController, modifier: Modifier) {
    var draft by remember { mutableStateOf(sync.newConfiguration()) }
    var error by remember { mutableStateOf<String?>(null) }
    var collision by remember { mutableStateOf<DeviceNameConflictException?>(null) }
    var initialChoice by remember { mutableStateOf(false) }
    var initialBackupPassword by remember { mutableStateOf("") }
    var chosenInitialStrategy by remember { mutableStateOf<InitialSyncStrategy?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun connect(
        takeOver: Boolean = false,
        strategy: InitialSyncStrategy? = null,
        backupPassword: String? = null,
    ) {
        working = true
        error = null
        scope.launch {
            runCatching {
                sync.configure(
                    draft,
                    takeOverDeviceName = takeOver,
                    initialStrategy = strategy,
                    backupPassword = backupPassword,
                )
            }
                .onFailure {
                    when (it) {
                        is DeviceNameConflictException -> collision = it
                        is InitialSyncRequiredException -> initialChoice = true
                        else -> error = it.message
                    }
                }
            working = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item { Text("启用 WebDAV 同步", style = MaterialTheme.typography.titleLarge) }
        item {
            OutlinedTextField(
                value = draft.webDavUrl,
                onValueChange = { draft = draft.copy(webDavUrl = it) },
                label = { Text("WebDAV 地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = draft.username,
                onValueChange = { draft = draft.copy(username = it) },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = draft.password,
                onValueChange = { draft = draft.copy(password = it) },
                label = { Text("密码") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = draft.deviceName,
                onValueChange = { draft = draft.copy(deviceName = it) },
                label = { Text("设备名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = draft.repositoryKey,
                onValueChange = { draft = draft.copy(repositoryKey = it) },
                label = { Text("同步仓库密钥") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("允许 HTTP", modifier = Modifier.weight(1f))
                Switch(
                    checked = draft.allowInsecureHttp,
                    onCheckedChange = { draft = draft.copy(allowInsecureHttp = it) },
                )
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            Button(
                onClick = { connect() },
                enabled = !working && draft.webDavUrl.isNotBlank() && draft.username.isNotBlank() &&
                    draft.password.isNotBlank() && draft.deviceName.isNotBlank() && draft.repositoryKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (working) "正在连接" else "连接并启用") }
        }
        item { BackupPanel(sync) }
    }

    collision?.let { conflict ->
        AlertDialog(
            onDismissRequest = { collision = null },
            title = { Text("设备名已存在") },
            text = { Text("云端已有设备“${conflict.existing.name}”。可以修改设备名，或接管该名称。") },
            confirmButton = {
                Button(onClick = {
                    collision = null
                    connect(
                        takeOver = true,
                        strategy = chosenInitialStrategy,
                        backupPassword = initialBackupPassword.takeIf { it.isNotBlank() },
                    )
                }) { Text("接管设备名") }
            },
            dismissButton = { TextButton(onClick = { collision = null }) { Text("修改名称") } },
        )
    }
    if (initialChoice) {
        AlertDialog(
            onDismissRequest = { initialChoice = false },
            title = { Text("首次绑定数据不同") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("本机和云端都有数据且没有共同基准。选择前会先生成本机保护备份。")
                    OutlinedTextField(
                        value = initialBackupPassword,
                        onValueChange = { initialBackupPassword = it },
                        label = { Text("保护备份密码（至少 8 位）") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            initialChoice = false
                            chosenInitialStrategy = InitialSyncStrategy.SafeMerge
                            connect(strategy = InitialSyncStrategy.SafeMerge, backupPassword = initialBackupPassword)
                        },
                        enabled = initialBackupPassword.length >= 8,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("安全合并（推荐）") }
                    OutlinedButton(
                        onClick = {
                            initialChoice = false
                            chosenInitialStrategy = InitialSyncStrategy.CloudWins
                            connect(strategy = InitialSyncStrategy.CloudWins, backupPassword = initialBackupPassword)
                        },
                        enabled = initialBackupPassword.length >= 8,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("采用云端") }
                    OutlinedButton(
                        onClick = {
                            initialChoice = false
                            chosenInitialStrategy = InitialSyncStrategy.LocalRebuild
                            connect(strategy = InitialSyncStrategy.LocalRebuild, backupPassword = initialBackupPassword)
                        },
                        enabled = initialBackupPassword.length >= 8,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("本机重建云端") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { initialChoice = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun BackupPanel(sync: SyncController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var restoreRecord by remember { mutableStateOf<BackupRecord?>(null) }
    var pendingExport by remember { mutableStateOf<BackupExport?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val export = pendingExport
        pendingExport = null
        if (uri != null && export != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "w")!!.use { it.write(export.bytes) }
                    }
                }.onSuccess { notice = "备份文件已导出。" }
                    .onFailure { error = it.message }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            working = true
            error = null
            scope.launch {
                runCatching {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    }
                    sync.importAndRestoreBackup(bytes, password)
                }.onFailure { error = it.message }
                working = false
            }
        }
    }

    LaunchedEffect(sync.configuration.isConfigured) {
        sync.refreshBackups()
    }

    fun runBackup(block: suspend () -> Unit) {
        working = true
        error = null
        scope.launch {
            runCatching { block() }.onFailure { error = it.message }
            working = false
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("全量备份", style = MaterialTheme.typography.titleMedium)
            Text(
                "库存内容保持可读，WebDAV 密码和仓库密钥使用此密码加密。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("备份密码（至少 8 位）") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { runBackup { sync.createLocalBackup(password) } },
                    enabled = !working && password.length >= 8,
                ) { Text("备份到本机") }
                OutlinedButton(
                    onClick = { runBackup { sync.createCloudBackup(password) } },
                    enabled = !working && password.length >= 8 && sync.configuration.isConfigured,
                ) { Text("备份到云端") }
                OutlinedButton(
                    onClick = {
                        runBackup {
                            val export = sync.createExportBackup(password)
                            pendingExport = export
                            exportLauncher.launch(export.fileName)
                        }
                    },
                    enabled = !working && password.length >= 8,
                ) { Text("导出文件") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    enabled = !working && !sync.restorePending && password.length >= 8,
                ) { Text("导入文件") }
            }
            sync.backupMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (sync.restorePending) {
                Text("恢复结果待确认，自动同步已暂停。", color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { runBackup { sync.confirmRestoreAsBaseline() } },
                        enabled = !working,
                    ) { Text("设为新基准") }
                    OutlinedButton(
                        onClick = { runBackup { sync.cancelRestore() } },
                        enabled = !working,
                    ) { Text("取消恢复") }
                }
            }

            if (sync.backupRecords.isNotEmpty()) {
                Text("最近备份", style = MaterialTheme.typography.titleSmall)
                sync.backupRecords.take(12).forEach { record ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(if (record.scope == "cloud") "云端" else "本机")
                            Text(
                                "${if (record.manual) "手动" else "自动"} · ${displayTimestamp(record.createdAt)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = { restoreRecord = record },
                            enabled = !working && !sync.restorePending && password.length >= 8,
                        ) { Text("恢复") }
                    }
                }
            }
        }
    }

    restoreRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { restoreRecord = null },
            title = { Text("恢复这份备份？") },
            text = { Text("当前数据会先生成保护备份。恢复后同步暂停，直到你确认新基准或取消恢复。") },
            confirmButton = {
                Button(onClick = {
                    restoreRecord = null
                    runBackup { sync.restoreBackup(record, password) }
                }) { Text("开始恢复") }
            },
            dismissButton = { OutlinedButton(onClick = { restoreRecord = null }) { Text("取消") } },
        )
    }
}
