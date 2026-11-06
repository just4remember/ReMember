package dev.chaik.remember.feature.settings.widgets

import dev.chaik.remember.core.widgets.WidgetLanguage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.widgets.ClockWeight
import dev.chaik.remember.core.widgets.DialTheme
import dev.chaik.remember.core.widgets.WIDGET_CARD_COLOR
import dev.chaik.remember.core.widgets.Widgets
import dev.chaik.remember.feature.settings.SettingsCard
import dev.chaik.remember.feature.settings.SettingsDropdown
import dev.chaik.remember.feature.settings.SettingsGroupTitle
import dev.chaik.remember.feature.settings.SettingsRadioRow
import dev.chaik.remember.feature.settings.SettingsSwitchRow
import dev.chaik.remember.feature.settings.appearance.ColorPickerDialog
import kotlinx.serialization.Serializable

@Serializable
data object WidgetsRoute : NavKey

/** Какой цвет сейчас выбирают. */
private enum class Picking { ANALOG, DIGITAL_CARD, HEALTH_CARD }

/**
 * Виджеты на рабочем столе: часы со стрелками, цифровые часы и кольца здоровья.
 * Настройки общие для всех копий виджета и применяются сразу.
 */
@Composable
fun WidgetsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = context.appContainer.widgetSettings
    val settings by store.settings.collectAsStateWithLifecycle()
    // Акцент приложения — для образца «как в приложении»; пересчитывается со сменой настроек приложения
    val appSettings = LocalAppSettings.current
    val accent = remember(appSettings.accent, appSettings.customAccent) { Color(Widgets.accent(context)) }
    var picking by remember { mutableStateOf<Picking?>(null) }

    ScreenScaffold(title = stringResource(R.string.settings_widgets), modifier = modifier, onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                stringResource(R.string.widgets_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            SettingsGroupTitle(stringResource(R.string.widgets_common))
            SettingsCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.widgets_language), style = MaterialTheme.typography.bodyLarge)
                    SettingsDropdown(
                        selected = settings.language,
                        options = WidgetLanguage.entries.map { it to stringResource(it.label) },
                        onSelect = { language -> store.update { it.copy(language = language) } },
                    )
                    Text(
                        stringResource(R.string.widgets_language_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsGroupTitle(stringResource(R.string.widgets_analog))
            SettingsCard {
                Text(
                    stringResource(R.string.widgets_dial_theme),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
                DialTheme.entries.forEach { theme ->
                    SettingsRadioRow(
                        label = stringResource(theme.label),
                        selected = settings.analogTheme == theme,
                        onClick = { store.update { it.copy(analogTheme = theme) } },
                    )
                }
                SettingsSwitchRow(stringResource(R.string.widgets_seconds), settings.analogSeconds, { on -> store.update { it.copy(analogSeconds = on) } })
                ColorRow(
                    title = stringResource(R.string.widgets_color),
                    hint = if (settings.analogColor == null) stringResource(R.string.widgets_color_accent) else stringResource(R.string.widgets_color_hint),
                    color = settings.analogColor?.let { Color(it.toInt()) } ?: accent,
                    onClick = { picking = Picking.ANALOG },
                    onReset = if (settings.analogColor != null) ({ store.update { it.copy(analogColor = null) } }) else null,
                    resetLabel = stringResource(R.string.widgets_color_reset),
                )
                SettingsSwitchRow(stringResource(R.string.widgets_date), settings.analogDate, { on -> store.update { it.copy(analogDate = on) } })
                SettingsSwitchRow(
                    stringResource(R.string.widgets_streak),
                    settings.analogStreak,
                    { on -> store.update { it.copy(analogStreak = on) } },
                    hint = stringResource(R.string.widgets_streak_hint),
                )
            }

            SettingsGroupTitle(stringResource(R.string.widgets_digital))
            SettingsCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.widgets_weight), style = MaterialTheme.typography.bodyLarge)
                    SettingsDropdown(
                        selected = settings.digitalWeight,
                        options = ClockWeight.entries.map { it to stringResource(it.label) },
                        onSelect = { weight -> store.update { it.copy(digitalWeight = weight) } },
                        // Каждая толщина в списке показывает сама себя
                        itemStyle = { TextStyle(fontWeight = FontWeight(it.weight)) },
                    )
                }
                SettingsSwitchRow(stringResource(R.string.widgets_card), settings.digitalCard, { on -> store.update { it.copy(digitalCard = on) } })
                ColorRow(
                    title = stringResource(R.string.widgets_card_color),
                    hint = null,
                    color = Color(settings.digitalCardColor.toInt()),
                    enabled = settings.digitalCard,
                    onClick = { picking = Picking.DIGITAL_CARD },
                    onReset = if (settings.digitalCardColor != WIDGET_CARD_COLOR) ({ store.update { it.copy(digitalCardColor = WIDGET_CARD_COLOR) } }) else null,
                    resetLabel = stringResource(R.string.widgets_card_reset),
                )
                SettingsSwitchRow(stringResource(R.string.widgets_date), settings.digitalDate, { on -> store.update { it.copy(digitalDate = on) } })
                SettingsSwitchRow(
                    stringResource(R.string.widgets_streak),
                    settings.digitalStreak,
                    { on -> store.update { it.copy(digitalStreak = on) } },
                    hint = stringResource(R.string.widgets_streak_hint),
                )
            }

            SettingsGroupTitle(stringResource(R.string.widgets_health))
            SettingsCard {
                Text(
                    stringResource(R.string.widgets_text),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
                SettingsRadioRow(
                    label = stringResource(R.string.widgets_text_outside),
                    selected = !settings.healthTextInside,
                    hint = stringResource(R.string.widgets_text_outside_hint),
                    onClick = { store.update { it.copy(healthTextInside = false) } },
                )
                SettingsRadioRow(
                    label = stringResource(R.string.widgets_text_inside),
                    selected = settings.healthTextInside,
                    onClick = { store.update { it.copy(healthTextInside = true) } },
                )
                // Одна настройка на все цели; внутри колец целей нет — переключатель не действует
                Box(Modifier.alpha(if (settings.healthTextInside) 0.4f else 1f)) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.widgets_goals_show),
                        checked = settings.healthGoals && !settings.healthTextInside,
                        onChange = { on -> if (!settings.healthTextInside) store.update { it.copy(healthGoals = on) } },
                    )
                }
                ColorRow(
                    title = stringResource(R.string.widgets_card_color),
                    hint = null,
                    color = Color(settings.healthCardColor.toInt()),
                    onClick = { picking = Picking.HEALTH_CARD },
                    onReset = if (settings.healthCardColor != WIDGET_CARD_COLOR) ({ store.update { it.copy(healthCardColor = WIDGET_CARD_COLOR) } }) else null,
                    resetLabel = stringResource(R.string.widgets_card_reset),
                )
            }
        }
    }

    when (picking) {
        Picking.ANALOG -> ColorPickerDialog(
            initial = settings.analogColor ?: (accent.toArgb().toLong() and 0xFFFFFFFFL),
            title = stringResource(R.string.widgets_color),
            onPick = { argb ->
                store.update { it.copy(analogColor = argb) }
                picking = null
            },
            onDismiss = { picking = null },
            maxLightness = 0.9f,
        )
        Picking.DIGITAL_CARD, Picking.HEALTH_CARD -> {
            val digital = picking == Picking.DIGITAL_CARD
            ColorPickerDialog(
                initial = if (digital) settings.digitalCardColor else settings.healthCardColor,
                title = stringResource(R.string.widgets_card_color),
                onPick = { argb ->
                    store.update { if (digital) it.copy(digitalCardColor = argb) else it.copy(healthCardColor = argb) }
                    picking = null
                },
                onDismiss = { picking = null },
                // Подложка — обычно тёмная: светлота от чёрного
                minLightness = 0f,
            )
        }
        null -> Unit
    }
}

/** Строка выбора цвета: название, подсказка, кружок цвета; [onReset] — вернуть цвет по умолчанию. */
@Composable
private fun ColorRow(
    title: String,
    hint: String?,
    color: Color,
    onClick: () -> Unit,
    onReset: (() -> Unit)?,
    resetLabel: String,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onReset != null && enabled) {
            IconButton(onClick = onReset) { Icon(Icons.Outlined.Refresh, resetLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Box(
            Modifier
                .padding(end = 8.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}
