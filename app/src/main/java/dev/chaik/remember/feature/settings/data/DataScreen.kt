package dev.chaik.remember.feature.settings.data

import kotlinx.coroutines.launch
import dev.chaik.remember.core.health.HealthAvailability
import androidx.core.net.toUri
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.OutlinedButton
import android.provider.Settings
import android.os.Process
import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chaik.remember.core.system.PerformanceMonitor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.backup.BackupManager
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.feature.settings.SettingsCard
import dev.chaik.remember.feature.settings.SettingsGroupTitle
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Serializable
data object DataRoute : NavKey

/** Пароль копии — не короче. */
private const val MIN_PASSWORD = 8

/**
 * Данные и резервные копии: сколько места занято, экспорт всех данных в один файл
 * под паролем и восстановление из такого файла (на этом или другом телефоне).
 */
/**
 * Отозвать разрешения. Приложение может снять у себя только обычные разрешения и доступы Health Connect;
 * интернет не отзывается вовсе, а история использования и показ на весь экран — только в настройках Android.
 * Android снимает разрешения, когда процесс завершён, поэтому приложение закрывается.
 */
@Composable
private fun PermissionsCard() {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }

    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.data_permissions_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.data_permissions_revoke))
            }
            TextButton(
                onClick = {
                    // Настройки Android — не повод блокировать приложение
                    container.appLock.allowExternal()
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.data_permissions_system)) }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.data_permissions_confirm_title)) },
            text = { Text(stringResource(R.string.data_permissions_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        runCatching {
                            if (container.health.availability == HealthAvailability.AVAILABLE) container.health.revokeAllPermissions()
                        }
                        context.revokeSelfPermissionsOnKill(listOf(Manifest.permission.POST_NOTIFICATIONS))
                        (context as? Activity)?.finishAffinity()
                        Process.killProcess(Process.myPid())
                    }
                }) { Text(stringResource(R.string.data_permissions_revoke_action), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Четыре окошка с показателями; обновляются, только пока страница на экране. */
@Composable
private fun PerformanceTiles() {
    val context = LocalContext.current
    val flow = remember { PerformanceMonitor(context.applicationContext).observe() }
    val data by flow.collectAsStateWithLifecycle(null)
    val snapshot = data
    fun size(bytes: Long?) = bytes?.let { Formatter.formatShortFileSize(context, it) } ?: "…"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PerformanceTile(
                icon = Icons.Outlined.Memory,
                label = stringResource(R.string.data_perf_ram),
                value = size(snapshot?.ramBytes),
                hint = snapshot?.let { stringResource(R.string.data_perf_ram_hint, size(it.ramTotalBytes)) }.orEmpty(),
                modifier = Modifier.weight(1f),
            )
            PerformanceTile(
                icon = Icons.Outlined.Speed,
                label = stringResource(R.string.data_perf_cpu),
                value = snapshot?.cpuPercent?.let { "%.1f %%".format(it) } ?: "…",
                hint = snapshot?.let { stringResource(R.string.data_perf_cpu_hint, it.cores) }.orEmpty(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PerformanceTile(
                icon = Icons.Outlined.Storage,
                label = stringResource(R.string.data_perf_disk),
                value = size(snapshot?.appDiskBytes),
                hint = snapshot?.let { stringResource(R.string.data_perf_disk_hint, size(it.diskFreeBytes)) }.orEmpty(),
                modifier = Modifier.weight(1f),
            )
            PerformanceTile(
                icon = Icons.Outlined.BatteryStd,
                label = stringResource(R.string.data_perf_battery),
                value = snapshot?.batteryMilliamps?.let { stringResource(R.string.data_perf_ma, it) } ?: "…",
                hint = snapshot?.batteryLevel?.let {
                    stringResource(if (snapshot.charging) R.string.data_perf_battery_charge else R.string.data_perf_battery_draw, it)
                }.orEmpty(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PerformanceTile(icon: ImageVector, label: String, value: String, hint: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerLow)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
        }
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        // Две строки всегда: иначе окошки в ряду разной высоты
        Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, minLines = 2, maxLines = 2)
    }
}

@Composable
fun DataScreen(onBack: () -> Unit, onOpenGallery: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = context.appContainer
    val viewModel = viewModel { DataViewModel(container) }
    var askExportPassword by remember { mutableStateOf(false) }
    var importSource by remember { mutableStateOf<Uri?>(null) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.export(uri) else viewModel.forgetPassword()
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> importSource = uri }

    ScreenScaffold(title = stringResource(R.string.settings_data), modifier = modifier, onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            SettingsGroupTitle(stringResource(R.string.data_storage))
            SettingsCard {
                val usage = viewModel.usage
                UsageRow(stringResource(R.string.data_database), usage?.databaseBytes)
                UsageRow(
                    label = usage?.let { pluralStringResource(R.plurals.data_photos, it.photoCount, it.photoCount) } ?: stringResource(R.string.data_photos_title),
                    bytes = usage?.photoBytes,
                )
                UsageRow(stringResource(R.string.data_maps), usage?.mapBytes)
                UsageRow(stringResource(R.string.data_app), usage?.appBytes)
                UsageRow(stringResource(R.string.data_total), usage?.totalBytes, bold = true)
            }
            SettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenGallery)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(stringResource(R.string.data_gallery), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(R.string.data_gallery_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
                }
            }

            SettingsGroupTitle(stringResource(R.string.data_performance))
            PerformanceTiles()

            SettingsGroupTitle(stringResource(R.string.data_permissions))
            PermissionsCard()

            SettingsGroupTitle(stringResource(R.string.data_backup))
            SettingsCard {
                Text(
                    text = stringResource(R.string.data_backup_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                ) {
                    Button(onClick = { askExportPassword = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.FileUpload, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.data_export), modifier = Modifier.padding(start = 6.dp))
                    }
                    FilledTonalButton(
                        onClick = {
                            container.appLock.allowExternal()
                            openFile.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.data_import), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            Text(
                text = stringResource(R.string.data_backup_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }

    if (askExportPassword) {
        PasswordDialog(
            title = stringResource(R.string.data_export_title),
            confirm = true,
            onDone = { password ->
                askExportPassword = false
                viewModel.rememberPassword(password)
                container.appLock.allowExternal()
                createFile.launch("ReMember-${LocalDate.now()}.${BackupManager.EXTENSION}")
            },
            onDismiss = { askExportPassword = false },
        )
    }

    importSource?.let { source ->
        PasswordDialog(
            title = stringResource(R.string.data_import_title),
            confirm = false,
            onDone = { password ->
                importSource = null
                viewModel.prepareRestore(source, password)
            },
            onDismiss = { importSource = null },
        )
    }

    viewModel.task?.let { task ->
        TaskDialog(
            task = task,
            onRestore = {
                viewModel.confirmRestore()
                restartApp(context)
            },
            onDismiss = {
                viewModel.dismiss()
                viewModel.refreshUsage()
            },
        )
    }
}

@Composable
private fun UsageRow(label: String, bytes: Long?, bold: Boolean = false) {
    val context = LocalContext.current
    val style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, style = style, modifier = Modifier.weight(1f))
        Text(bytes?.let { Formatter.formatShortFileSize(context, it) } ?: "…", style = style)
    }
}

/** Пароль копии: при экспорте — дважды и с напоминанием, что без него копию не открыть. */
@Composable
private fun PasswordDialog(title: String, confirm: Boolean, onDone: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val tooShort = confirm && password.length < MIN_PASSWORD
    val mismatch = confirm && repeat.isNotEmpty() && repeat != password
    val ready = password.isNotEmpty() && !tooShort && (!confirm || repeat == password)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.data_password)) },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, stringResource(R.string.data_show_password))
                        }
                    },
                    supportingText = if (confirm) ({ Text(stringResource(R.string.data_password_length, MIN_PASSWORD)) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (confirm) {
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = { Text(stringResource(R.string.security_repeat_pin)) },
                        singleLine = true,
                        isError = mismatch,
                        visualTransformation = transformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.data_password_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ready, onClick = { onDone(password.toCharArray()) }) {
                Text(stringResource(if (confirm) R.string.data_export else R.string.data_check))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun TaskDialog(task: BackupTask, onRestore: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { if (task !is BackupTask.Working) onDismiss() },
        title = {
            Text(
                stringResource(
                    when (task) {
                        is BackupTask.Working -> R.string.data_wait
                        is BackupTask.Exported -> R.string.data_exported_title
                        is BackupTask.Failed -> R.string.data_failed_title
                        is BackupTask.ReadyToRestore -> R.string.data_restore_title
                    },
                ),
            )
        },
        text = {
            when (task) {
                is BackupTask.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(task.message), modifier = Modifier.padding(start = 16.dp))
                }
                is BackupTask.Exported -> Text(stringResource(R.string.data_exported_text, Formatter.formatShortFileSize(context, task.bytes)))
                is BackupTask.Failed -> Text(stringResource(task.message))
                is BackupTask.ReadyToRestore -> {
                    val date = task.preview.createdAt.atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
                    Text(
                        stringResource(R.string.data_restore_text, date) + "\n\n" +
                            pluralStringResource(R.plurals.data_photos, task.preview.photoCount, task.preview.photoCount),
                    )
                }
            }
        },
        confirmButton = {
            when (task) {
                is BackupTask.Working -> Unit
                is BackupTask.ReadyToRestore -> TextButton(onClick = onRestore) {
                    Text(stringResource(R.string.data_restore_confirm), color = MaterialTheme.colorScheme.error)
                }
                else -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
            }
        },
        dismissButton = {
            if (task is BackupTask.ReadyToRestore) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Перезапуск: копия встанет на место при старте, до открытия базы. */
private fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(Intent.makeRestartActivityTask(launch.component))
    Runtime.getRuntime().exit(0)
}
