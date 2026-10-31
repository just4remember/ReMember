package dev.chaik.remember.feature.network

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.network.NetworkStatus
import dev.chaik.remember.core.network.NetworkType
import dev.chaik.remember.core.network.SiteResult
import dev.chaik.remember.core.network.SpeedPhase
import dev.chaik.remember.core.network.WhitelistVerdict
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.feature.settings.SettingsCard
import dev.chaik.remember.feature.settings.SettingsGroupTitle
import dev.chaik.remember.feature.settings.SettingsRadioRow
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data object NetworkRoute : NavKey

/** Как часто замер сообщает о ходе — столько же длится шаг полоски прогресса. */
private const val PROGRESS_STEP_MS = 250

/** Зелёный «открылось» / красный «нет» — одинаковые в любой теме, как кольца «Здоровья». */
private val OkColor = Color(0xFF30D158)

/**
 * Сеть: через что сейчас интернет, замер скорости (пинг, скачивание, отдача) и проверка,
 * не включены ли белые списки. Это единственная страница приложения, которая ходит в интернет.
 */
@Composable
fun NetworkScreen(onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val viewModel = viewModel { NetworkViewModel(container) }
    val network by viewModel.network.collectAsStateWithLifecycle()
    val settings by viewModel.store.settings.collectAsStateWithLifecycle()
    var editingServer by remember { mutableStateOf(false) }
    var confirmMetered by remember { mutableStateOf(false) }
    var addingTo by remember { mutableStateOf<Boolean?>(null) }

    ScreenScaffold(
        title = stringResource(R.string.section_network),
        modifier = modifier,
        onBack = onBack,
        actions = {
            IconButton(onClick = { editingServer = true }) {
                Icon(Icons.Outlined.Tune, stringResource(R.string.network_server))
            }
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            NetworkCard(network)

            SettingsGroupTitle(stringResource(R.string.network_speed))
            SettingsCard {
                SpeedPanel(
                    state = viewModel.speed,
                    server = settings.server.host,
                    canStart = network.type != NetworkType.NONE,
                    seconds = settings.testSeconds,
                    onStart = { if (network.metered && settings.askOnMobile) confirmMetered = true else viewModel.startSpeedTest() },
                    onStop = viewModel::stopSpeedTest,
                )
            }

            SettingsGroupTitle(stringResource(R.string.network_whitelist))
            SettingsCard {
                WhitelistPanel(
                    verdict = viewModel.verdict,
                    checking = viewModel.checking,
                    canCheck = network.type != NetworkType.NONE,
                    onCheck = viewModel::checkWhitelist,
                )
                SiteGroup(
                    title = stringResource(R.string.network_sites_allowed),
                    hint = stringResource(R.string.network_sites_allowed_hint),
                    hosts = settings.allowedSites,
                    results = viewModel.sites,
                    onRemove = viewModel::removeSite,
                    onAdd = { addingTo = true },
                )
                SiteGroup(
                    title = stringResource(R.string.network_sites_other),
                    hint = stringResource(R.string.network_sites_other_hint),
                    hosts = settings.otherSites,
                    results = viewModel.sites,
                    onRemove = viewModel::removeSite,
                    onAdd = { addingTo = false },
                )
            }
            Text(
                text = stringResource(R.string.network_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }

    if (confirmMetered) {
        var dontAsk by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmMetered = false },
            title = { Text(stringResource(R.string.network_metered_title)) },
            text = {
                Column {
                    Text(
                        if (settings.mobileLimitMb > 0) {
                            stringResource(R.string.network_metered_text_limit, settings.mobileLimitMb)
                        } else {
                            stringResource(R.string.network_metered_text)
                        },
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { dontAsk = !dontAsk },
                    ) {
                        Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                        Text(stringResource(R.string.network_metered_dont_ask), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmMetered = false
                        if (dontAsk) viewModel.stopAskingOnMobile()
                        viewModel.startSpeedTest()
                    },
                ) { Text(stringResource(R.string.network_start)) }
            },
            dismissButton = { TextButton(onClick = { confirmMetered = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    addingTo?.let { allowed ->
        AddSiteDialog(
            onAdd = { input -> viewModel.addSite(allowed, input).also { if (it) addingTo = null } },
            onDismiss = { addingTo = null },
        )
    }

    if (editingServer) {
        ServerDialog(
            initial = when {
                settings.selectelServer -> ServerChoice.SELECTEL
                settings.customServer -> ServerChoice.CUSTOM
                else -> ServerChoice.CLOUDFLARE
            },
            downloadUrl = settings.downloadUrl,
            uploadUrl = settings.uploadUrl,
            onSave = { choice, download, upload ->
                viewModel.store.update {
                    it.copy(
                        selectelServer = choice == ServerChoice.SELECTEL,
                        customServer = choice == ServerChoice.CUSTOM,
                        downloadUrl = download.trim(),
                        uploadUrl = upload.trim(),
                    )
                }
                editingServer = false
            },
            onDismiss = { editingServer = false },
        )
    }
}

@Composable
private fun NetworkCard(network: NetworkStatus) {
    val (icon, label) = when (network.type) {
        NetworkType.WIFI -> Icons.Outlined.Wifi to stringResource(R.string.network_wifi)
        NetworkType.CELLULAR -> Icons.Outlined.SignalCellularAlt to (
            network.operator?.let { stringResource(R.string.network_cellular_operator, it) } ?: stringResource(R.string.network_cellular)
            )
        NetworkType.ETHERNET -> Icons.Outlined.SettingsEthernet to stringResource(R.string.network_ethernet)
        NetworkType.OTHER -> Icons.Outlined.Wifi to stringResource(R.string.network_other)
        NetworkType.NONE -> Icons.Outlined.WifiOff to stringResource(R.string.network_none)
    }
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (network.vpn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Text(
                            text = stringResource(R.string.network_vpn),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedPanel(state: SpeedState, server: String, canStart: Boolean, seconds: Int, onStart: () -> Unit, onStop: () -> Unit) {
    val units = units()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Крупно — то, что меряется сейчас
        if (state is SpeedState.Running) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(
                        when (state.phase) {
                            SpeedPhase.PING -> R.string.network_ping
                            SpeedPhase.DOWNLOAD -> R.string.network_download
                            SpeedPhase.UPLOAD -> R.string.network_upload
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (state.phase == SpeedPhase.PING) units.ms(state.value) else units.mbps(state.value),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        val done = (state as? SpeedState.Done)?.result
        val running = state as? SpeedState.Running
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Metric(
                label = stringResource(R.string.network_ping),
                value = done?.pingMs?.let(units::ms) ?: running?.ping?.let(units::ms),
                extra = done?.let { stringResource(R.string.network_jitter, units.ms(it.jitterMs)) },
            )
            Metric(
                label = stringResource(R.string.network_download),
                value = done?.downloadMbps?.let(units::mbps) ?: running?.download?.let(units::mbps),
            )
            Metric(
                label = stringResource(R.string.network_upload),
                value = done?.let { it.uploadMbps?.let(units::mbps) ?: "—" },
            )
        }

        when (state) {
            SpeedState.Failed -> Text(
                text = stringResource(R.string.network_speed_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            SpeedState.Idle -> Text(
                text = stringResource(R.string.network_speed_hint, seconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Unit
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state is SpeedState.Running) {
                OutlinedButton(onClick = onStop) { Text(stringResource(R.string.network_stop)) }
            } else {
                Button(onClick = onStart, enabled = canStart) {
                    Text(stringResource(if (state is SpeedState.Done) R.string.network_again else R.string.network_start))
                }
            }
            Text(
                text = stringResource(R.string.network_server_value, server),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
        }

        // Сколько осталось: загрузка и отдача — по времени или по лимиту трафика
        if (state is SpeedState.Running) {
            // Новое значение приходит 4 раза в секунду — едем к нему равномерно ровно за это время,
            // тогда полоска ползёт плавно, без рывков. Новая фаза начинается с нуля, без отката назад
            val progress = remember(state.phase) { Animatable(0f) }
            LaunchedEffect(state.phase, state.fraction) {
                progress.animateTo(state.fraction, tween(durationMillis = PROGRESS_STEP_MS, easing = LinearEasing))
            }
            LinearProgressIndicator(progress = { progress.value }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Metric(label: String, value: String?, extra: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (extra != null) Text(extra, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WhitelistPanel(verdict: WhitelistVerdict?, checking: Boolean, canCheck: Boolean, onCheck: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (verdict != null) {
            val (icon, color, text) = when (verdict) {
                WhitelistVerdict.WHITELIST -> Triple(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error, R.string.network_verdict_whitelist)
                WhitelistVerdict.OPEN -> Triple(Icons.Outlined.CheckCircle, OkColor, R.string.network_verdict_open)
                WhitelistVerdict.NO_INTERNET -> Triple(Icons.Outlined.WifiOff, MaterialTheme.colorScheme.error, R.string.network_verdict_none)
                WhitelistVerdict.PARTIAL -> Triple(Icons.Outlined.HelpOutline, MaterialTheme.colorScheme.tertiary, R.string.network_verdict_partial)
                WhitelistVerdict.UNKNOWN -> Triple(Icons.Outlined.HelpOutline, MaterialTheme.colorScheme.onSurfaceVariant, R.string.network_verdict_unknown)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color)
                Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
            }
        } else {
            Text(
                text = stringResource(R.string.network_whitelist_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onCheck, enabled = canCheck && !checking) {
            if (checking) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.network_checking), modifier = Modifier.padding(start = 8.dp))
            } else {
                Text(stringResource(R.string.network_check))
            }
        }
    }
}

@Composable
private fun SiteGroup(
    title: String,
    hint: String,
    hosts: List<String>,
    results: Map<String, SiteResult?>,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val units = units()
    Column(Modifier.padding(start = 16.dp, end = 4.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        hosts.forEach { host ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(host, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                when {
                    host !in results -> Unit
                    results[host] == null -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    results[host]!!.reachable -> Text(units.ms(results[host]!!.ms!!.toDouble()), color = OkColor, style = MaterialTheme.typography.labelLarge)
                    else -> Text(stringResource(R.string.network_site_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
                IconButton(onClick = { onRemove(host) }) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.network_site_remove), tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                }
            }
        }
        TextButton(onClick = onAdd) {
            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.network_site_add), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun AddSiteDialog(onAdd: (String) -> Boolean, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.network_site_add)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    invalid = false
                },
                placeholder = { Text("example.com") },
                singleLine = true,
                isError = invalid,
                supportingText = if (invalid) ({ Text(stringResource(R.string.network_site_invalid)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = input.isNotBlank(), onClick = { if (!onAdd(input)) invalid = true }) {
                Text(stringResource(R.string.network_site_add_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private enum class ServerChoice { CLOUDFLARE, SELECTEL, CUSTOM }

/** Сервер замера: Cloudflare, Selectel или свой — файл для скачивания и, по желанию, адрес для отдачи. */
@Composable
private fun ServerDialog(
    initial: ServerChoice,
    downloadUrl: String,
    uploadUrl: String,
    onSave: (ServerChoice, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by remember { mutableStateOf(initial) }
    val custom = choice == ServerChoice.CUSTOM
    var download by remember { mutableStateOf(downloadUrl) }
    var upload by remember { mutableStateOf(uploadUrl) }
    val validDownload = download.trim().startsWith("http://") || download.trim().startsWith("https://")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.network_server)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsRadioRow(label = "Cloudflare (speed.cloudflare.com)", selected = choice == ServerChoice.CLOUDFLARE, onClick = { choice = ServerChoice.CLOUDFLARE })
                SettingsRadioRow(label = stringResource(R.string.network_server_selectel), selected = choice == ServerChoice.SELECTEL, onClick = { choice = ServerChoice.SELECTEL })
                if (choice == ServerChoice.SELECTEL) {
                    Text(
                        text = stringResource(R.string.network_server_selectel_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SettingsRadioRow(label = stringResource(R.string.network_server_custom), selected = custom, onClick = { choice = ServerChoice.CUSTOM })
                if (custom) {
                    OutlinedTextField(
                        value = download,
                        onValueChange = { download = it },
                        label = { Text(stringResource(R.string.network_server_download)) },
                        placeholder = { Text("https://nas.local/test.bin") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = upload,
                        onValueChange = { upload = it },
                        label = { Text(stringResource(R.string.network_server_upload)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.network_server_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !custom || validDownload, onClick = { onSave(choice, download, upload) }) {
                Text(stringResource(R.string.gym_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** «24 мс» и «87 Мбит/с» на языке приложения. */
private class Units(private val ms: String, private val mbps: String) {
    fun ms(value: Double) = ms.format(String.format(Locale.getDefault(), "%.0f", value))
    fun mbps(value: Double) = mbps.format(String.format(Locale.getDefault(), if (value < 10) "%.1f" else "%.0f", value))
}

@Composable
private fun units() = Units(stringResource(R.string.network_unit_ms), stringResource(R.string.network_unit_mbps))
