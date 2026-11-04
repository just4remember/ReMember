package dev.chaik.remember.feature.settings.pages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.text.style.TextDecoration
import dev.chaik.remember.feature.settings.SettingsRadioRow
import dev.chaik.remember.core.settings.NotesOrder
import dev.chaik.remember.app.navigation.isDisabled
import dev.chaik.remember.app.navigation.moreOrderAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import dev.chaik.remember.core.ui.components.AppSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.app.navigation.Destination
import dev.chaik.remember.app.navigation.islandTabs
import dev.chaik.remember.app.navigation.moreTiles
import dev.chaik.remember.app.ui.FloatingIsland
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.network.MobileLimitOptions
import dev.chaik.remember.core.network.TestSecondsOptions
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.settings.StepsCheckOptions
import dev.chaik.remember.core.settings.WELCOME_MAX
import dev.chaik.remember.core.settings.WELCOME_MIN
import dev.chaik.remember.core.settings.WELCOME_STEP
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.feature.settings.SettingsCard
import dev.chaik.remember.feature.settings.SettingsDropdown
import dev.chaik.remember.feature.settings.SettingsGroupTitle
import dev.chaik.remember.feature.settings.SettingsSwitchRow
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object PagesRoute : NavKey

/**
 * Страницы: какие разделы стоят вкладками островка (от 2 до 4, «Ещё» — всегда последней)
 * и в каком порядке плитки на странице «Ещё». Меняется сразу — островок над страницей тоже.
 */
@Composable
fun PagesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val store = LocalContext.current.appContainer.settings
    val settings = LocalAppSettings.current
    val island = settings.islandTabs()
    val tabs = island - Destination.More
    // Все разделы «Ещё», и выключенные тоже; вкладкой можно поставить только включённый
    val tiles = settings.moreOrderAll()
    val addable = tiles.filterNot(settings::isDisabled)
    var adding by remember { mutableStateOf(false) }

    fun saveTabs(list: List<Destination>) = store.update { it.copy(islandTabs = list.map(Destination::name)) }

    // Полный порядок «Ещё» хранится для всех разделов: так убранная вкладка встаёт на своё место
    fun saveTiles(visible: List<Destination>) = store.update { s ->
        val full = (s.moreOrder.mapNotNull { n -> Destination.entries.firstOrNull { it.name == n } } + Destination.DefaultMoreOrder)
            .distinct()
            .filter { it.movable }
        s.copy(moreOrder = (visible + (full - visible.toSet())).map(Destination::name))
    }

    ScreenScaffold(title = stringResource(R.string.settings_pages), modifier = modifier, onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            // Как будет выглядеть островок
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FloatingIsland(tabs = island, selected = island.first(), indicatorPosition = { 0f }, onSelect = {})
            }

            SettingsGroupTitle(stringResource(R.string.pages_island))
            SettingsCard {
                tabs.forEachIndexed { i, tab ->
                    OrderRow(
                        destination = tab,
                        onUp = if (i > 0) ({ saveTabs(tabs.swap(i, i - 1)) }) else null,
                        onDown = if (i < tabs.lastIndex) ({ saveTabs(tabs.swap(i, i + 1)) }) else null,
                        // Меньше двух вкладок (трёх с «Ещё») — нельзя
                        onRemove = if (tabs.size > Destination.MIN_TABS) ({ saveTabs(tabs - tab) }) else null,
                    )
                }
                FixedRow(Destination.More)
                if (tabs.size < Destination.MAX_TABS) {
                    TextButton(onClick = { adding = true }, modifier = Modifier.padding(start = 4.dp)) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.pages_add_tab), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            Hint(stringResource(R.string.pages_island_hint))

            SettingsGroupTitle(stringResource(R.string.pages_more_tiles))
            SettingsCard {
                tiles.forEachIndexed { i, tile ->
                    val disabled = settings.isDisabled(tile)
                    OrderRow(
                        destination = tile,
                        onUp = if (i > 0) ({ saveTiles(tiles.swap(i, i - 1)) }) else null,
                        onDown = if (i < tiles.lastIndex) ({ saveTiles(tiles.swap(i, i + 1)) }) else null,
                        onRemove = null,
                        disabled = disabled,
                        onToggle = {
                            store.update { s ->
                                s.copy(disabledPages = if (disabled) s.disabledPages - tile.name else s.disabledPages + tile.name)
                            }
                        },
                    )
                }
                FixedRow(Destination.Settings)
            }
            Hint(stringResource(R.string.pages_disable_hint))

            SettingsGroupTitle(stringResource(R.string.pages_start))
            SettingsCard {
                SettingsSwitchRow(
                    title = stringResource(R.string.pages_welcome),
                    hint = stringResource(R.string.pages_welcome_hint),
                    checked = settings.welcome,
                    onChange = { on -> store.update { it.copy(welcome = on) } },
                )
                if (settings.welcome) {
                    // Ползунок двигается плавно, а сохраняется значение по отпусканию
                    var millis by remember(settings.welcomeMillis) { mutableFloatStateOf(settings.welcomeMillis.toFloat()) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    ) {
                        Text(stringResource(R.string.pages_welcome_duration), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.pages_welcome_seconds, millis.roundToInt() / 1000f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    AppSlider(
                        value = millis,
                        onValueChange = { millis = it },
                        onValueChangeFinished = { store.update { it.copy(welcomeMillis = millis.roundToInt()) } },
                        valueRange = WELCOME_MIN.toFloat()..WELCOME_MAX.toFloat(),
                        steps = (WELCOME_MAX - WELCOME_MIN) / WELCOME_STEP - 1,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
            }

            // Настройки самой страницы «Сегодня» — отдельным разделом с её названием
            SettingsGroupTitle(stringResource(R.string.tab_today))
            SettingsCard {
                SettingsSwitchRow(
                    title = stringResource(R.string.pages_today_auto),
                    hint = stringResource(R.string.pages_today_auto_hint),
                    checked = settings.todayAutoMode,
                    onChange = { on -> store.update { it.copy(todayAutoMode = on) } },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.pages_sleep_guess),
                    hint = stringResource(R.string.pages_sleep_guess_hint),
                    checked = settings.sleepGuess,
                    onChange = { on -> store.update { it.copy(sleepGuess = on) } },
                )
            }

            SettingsGroupTitle(stringResource(R.string.tab_notes))
            SettingsCard {
                // Порядок заметок; «Вручную» включается и сам, когда заметки перетаскивают
                listOf(
                    NotesOrder.EDITED to R.string.pages_notes_order_edited,
                    NotesOrder.CREATED to R.string.pages_notes_order_created,
                    NotesOrder.MANUAL to R.string.pages_notes_order_manual,
                ).forEach { (order, label) ->
                    SettingsRadioRow(stringResource(label), selected = settings.notesOrder == order) {
                        store.update { it.copy(notesOrder = order) }
                    }
                }
                SettingsSwitchRow(
                    title = stringResource(R.string.pages_notes_photos),
                    hint = stringResource(R.string.pages_notes_photos_hint),
                    checked = settings.notesShowPhotos,
                    onChange = { on -> store.update { it.copy(notesShowPhotos = on) } },
                )
            }

            SettingsGroupTitle(stringResource(R.string.tab_tasks))
            SettingsCard {
                SettingsSwitchRow(
                    title = stringResource(R.string.pages_tasks_clear_draft),
                    hint = stringResource(R.string.pages_tasks_clear_draft_hint),
                    checked = settings.tasksClearDraft,
                    onChange = { on -> store.update { it.copy(tasksClearDraft = on) } },
                )
            }

            SettingsGroupTitle(stringResource(R.string.section_health))
            SettingsCard { StepsGoalSettings() }

            SettingsGroupTitle(stringResource(R.string.section_network))
            SettingsCard { NetworkTestSettings() }

            TextButton(
                onClick = { store.update { it.copy(islandTabs = emptyList(), moreOrder = emptyList()) } },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.pages_reset))
            }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.pages_add_title)) },
            text = {
                Column {
                    addable.forEach { candidate ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    saveTabs(tabs + candidate)
                                    adding = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                        ) {
                            Icon(candidate.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(candidate.label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/**
 * Раздел со стрелками «выше / ниже» и, если можно, крестиком «убрать из островка».
 * [onToggle] — глаз «включить / выключить раздел» ([disabled] — сейчас выключен, строка бледная).
 */
@Composable
private fun OrderRow(
    destination: Destination,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    disabled: Boolean = false,
    onToggle: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
        Icon(destination.icon, null, tint = if (disabled) colors.outline else colors.onSurfaceVariant)
        Text(
            text = stringResource(destination.label),
            style = MaterialTheme.typography.bodyLarge,
            color = if (disabled) colors.outline else colors.onSurface,
            textDecoration = if (disabled) TextDecoration.LineThrough else null,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
        IconButton(onClick = { onUp?.invoke() }, enabled = onUp != null) {
            Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.pages_move_up))
        }
        IconButton(onClick = { onDown?.invoke() }, enabled = onDown != null) {
            Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.pages_move_down))
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, stringResource(R.string.pages_remove)) }
        }
        if (onToggle != null) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (disabled) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(if (disabled) R.string.pages_enable else R.string.pages_disable),
                    tint = if (disabled) colors.outline else colors.primary,
                )
            }
        }
    }
}

/** Раздел, место которого не меняется: «Ещё» в островке, «Настройки» в плитках. */
@Composable
private fun FixedRow(destination: Destination) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Icon(destination.icon, null, tint = MaterialTheme.colorScheme.outline)
        Text(
            text = stringResource(destination.label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
        Icon(Icons.Outlined.Lock, stringResource(R.string.pages_fixed), tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> = toMutableList().also { it[a] = this[b]; it[b] = this[a] }

/** Состояние фонового чтения шагов в Health Connect. */
private enum class BackgroundAccess { CHECKING, GRANTED, MISSING, UNSUPPORTED }

/**
 * Уведомление о цели по шагам: как часто проверять (или не проверять вовсе). Без разрешения
 * на чтение в фоне проверка ничего не увидит — тогда здесь же кнопка, чтобы его дать.
 */
@Composable
private fun StepsGoalSettings() {
    val container = LocalContext.current.appContainer
    val settings = LocalAppSettings.current
    val health = container.health
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf(BackgroundAccess.CHECKING) }

    suspend fun refresh() {
        access = when {
            !runCatching { health.backgroundReadSupported() }.getOrDefault(false) -> BackgroundAccess.UNSUPPORTED
            runCatching { health.hasBackgroundPermission() }.getOrDefault(false) -> BackgroundAccess.GRANTED
            else -> BackgroundAccess.MISSING
        }
    }
    LaunchedEffect(Unit) { refresh() }
    val askPermission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        scope.launch { refresh() }
    }

    val options = StepsCheckOptions.map { minutes ->
        minutes to when (minutes) {
            0 -> stringResource(R.string.pages_steps_off)
            in 1..59 -> pluralStringResource(R.plurals.pages_steps_every_minutes, minutes, minutes)
            else -> pluralStringResource(R.plurals.pages_steps_every_hours, minutes / 60, minutes / 60)
        }
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.pages_steps_title), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.pages_steps_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsDropdown(
            selected = settings.stepsGoalCheckMinutes,
            options = options,
            onSelect = { minutes -> container.settings.update { it.copy(stepsGoalCheckMinutes = minutes) } },
        )
        if (settings.stepsGoalCheckMinutes > 0) {
            when (access) {
                BackgroundAccess.MISSING -> {
                    Text(
                        text = stringResource(R.string.pages_steps_permission),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    FilledTonalButton(
                        onClick = {
                            // Окно Health Connect — не повод блокировать приложение
                            container.appLock.allowExternal()
                            askPermission.launch(health.backgroundPermissions)
                        },
                    ) { Text(stringResource(R.string.pages_steps_allow)) }
                }
                BackgroundAccess.UNSUPPORTED -> Text(
                    text = stringResource(R.string.pages_steps_unsupported),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Unit
            }
        }
    }
}

/** Замер скорости на странице «Сеть»: сколько длится и сколько мобильного трафика можно потратить. */
@Composable
private fun NetworkTestSettings() {
    val store = LocalContext.current.appContainer.networkSettings
    val settings by store.settings.collectAsStateWithLifecycle()

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.pages_network_seconds), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.pages_network_seconds_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsDropdown(
            selected = settings.testSeconds,
            options = TestSecondsOptions.map { it to stringResource(R.string.pages_network_seconds_value, it) },
            onSelect = { seconds -> store.update { it.copy(testSeconds = seconds) } },
        )
        Text(
            text = stringResource(R.string.pages_network_limit),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.pages_network_limit_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsDropdown(
            selected = settings.mobileLimitMb,
            options = MobileLimitOptions.map { mb ->
                mb to if (mb == 0) stringResource(R.string.pages_network_limit_off) else stringResource(R.string.pages_network_limit_value, mb)
            },
            onSelect = { mb -> store.update { it.copy(mobileLimitMb = mb) } },
        )
    }
    SettingsSwitchRow(
        title = stringResource(R.string.pages_network_ask),
        checked = settings.askOnMobile,
        onChange = { on -> store.update { it.copy(askOnMobile = on) } },
    )
}

