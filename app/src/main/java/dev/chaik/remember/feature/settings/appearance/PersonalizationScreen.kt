package dev.chaik.remember.feature.settings.appearance

import dev.chaik.remember.core.editor.editorTools
import dev.chaik.remember.core.editor.EditorTool
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import dev.chaik.remember.core.ui.components.AppSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.editor.CorrectionUnderlinePreview
import dev.chaik.remember.core.editor.MarkdownHighlighter
import dev.chaik.remember.core.editor.correctionLine
import dev.chaik.remember.core.settings.AccentColor
import dev.chaik.remember.core.settings.AppFont
import dev.chaik.remember.core.settings.BackSwipe
import dev.chaik.remember.core.settings.GAP_MAX
import dev.chaik.remember.core.settings.GAP_STEP
import dev.chaik.remember.core.settings.INDENT_MAX
import dev.chaik.remember.core.settings.INDENT_STEP
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.settings.T9_ALPHA_MIN
import dev.chaik.remember.core.settings.T9_ALPHA_STEP
import dev.chaik.remember.core.settings.T9_WIDTH_MAX
import dev.chaik.remember.core.settings.T9_WIDTH_MIN
import dev.chaik.remember.core.settings.T9_WIDTH_STEP
import dev.chaik.remember.core.settings.ThemeMode
import dev.chaik.remember.core.settings.TimePickerStyle
import dev.chaik.remember.core.settings.UnderlineStyle
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.theme.fontFamilyOf
import dev.chaik.remember.core.ui.theme.preview
import dev.chaik.remember.feature.settings.SettingsCard
import dev.chaik.remember.feature.settings.SettingsDropdown
import dev.chaik.remember.feature.settings.SettingsGroupTitle
import dev.chaik.remember.feature.settings.SettingsRadioRow
import dev.chaik.remember.feature.settings.SettingsSwitchRow
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

@Serializable
data object PersonalizationRoute : NavKey

/** Персонализация: тема, акцент, режим левши, свайп «назад», вид выбора времени. Меняется сразу. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalizationScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val store = LocalContext.current.appContainer.settings
    val settings = LocalAppSettings.current
    var pickingColor by remember { mutableStateOf(false) }

    ScreenScaffold(title = stringResource(R.string.settings_personalization), modifier = modifier, onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            SettingsGroupTitle(stringResource(R.string.appearance_theme))
            SettingsDropdown(
                selected = settings.theme,
                options = listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.appearance_theme_system),
                    ThemeMode.LIGHT to stringResource(R.string.appearance_theme_light),
                    ThemeMode.DARK to stringResource(R.string.appearance_theme_dark),
                    ThemeMode.AMOLED to stringResource(R.string.appearance_theme_amoled),
                    ThemeMode.ONE_DARK to stringResource(R.string.appearance_theme_one_dark),
                    ThemeMode.NIGHT_OWL to stringResource(R.string.appearance_theme_night_owl),
                ),
                onSelect = { mode -> store.update { it.copy(theme = mode) } },
            )

            SettingsGroupTitle(stringResource(R.string.appearance_accent))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentColor.entries.filter { it != AccentColor.CUSTOM }.forEach { accent ->
                    AccentSwatch(accent, selected = settings.accent == accent) { store.update { it.copy(accent = accent) } }
                }
                // Свой цвет: кружок этого цвета с пипеткой, тап — палитра
                CustomSwatch(Color(settings.customAccent), selected = settings.accent == AccentColor.CUSTOM) { pickingColor = true }
            }
            if (settings.theme.isPalette) {
                Text(
                    text = stringResource(R.string.appearance_accent_palette_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            } else if (settings.accent == AccentColor.WALLPAPER) {
                Text(
                    text = stringResource(R.string.appearance_accent_wallpaper_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            SettingsGroupTitle(stringResource(R.string.appearance_font))
            // Каждый шрифт показан им самим — и в поле, и в списке
            val fontStyle = MaterialTheme.typography.bodyLarge
            val sample = stringResource(R.string.appearance_font_sample)
            SettingsDropdown(
                selected = settings.font,
                options = listOf(
                    AppFont.SYSTEM to stringResource(R.string.appearance_font_system),
                    AppFont.INTER to stringResource(R.string.appearance_font_inter),
                    AppFont.NUNITO to stringResource(R.string.appearance_font_nunito),
                    AppFont.MEDEO to stringResource(R.string.appearance_font_medeo),
                ),
                onSelect = { font -> store.update { it.copy(font = font) } },
                itemStyle = { font -> fontStyle.copy(fontFamily = fontFamilyOf(font)) },
                itemHint = { sample },
            )

            SettingsGroupTitle(stringResource(R.string.appearance_hands))
            SettingsCard {
                SettingsSwitchRow(
                    title = stringResource(R.string.appearance_left_handed),
                    hint = stringResource(R.string.appearance_left_handed_hint),
                    checked = settings.leftHanded,
                    onChange = { on -> store.update { it.copy(leftHanded = on) } },
                )
            }

            SettingsGroupTitle(stringResource(R.string.appearance_haptics_title))
            SettingsCard {
                SettingsSwitchRow(
                    title = stringResource(R.string.appearance_haptics),
                    hint = stringResource(R.string.appearance_haptics_hint),
                    checked = settings.haptics,
                    onChange = { on -> store.update { it.copy(haptics = on) } },
                )
                if (settings.haptics) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.appearance_game_haptics),
                        checked = settings.gameHaptics,
                        onChange = { on -> store.update { it.copy(gameHaptics = on) } },
                    )
                }
            }

            SettingsGroupTitle(stringResource(R.string.appearance_back_swipe))
            SettingsCard {
                listOf(
                    BackSwipe.RIGHT_TO_LEFT to R.string.appearance_back_rtl,
                    BackSwipe.LEFT_TO_RIGHT to R.string.appearance_back_ltr,
                    BackSwipe.BOTH to R.string.appearance_back_both,
                ).forEach { (direction, label) ->
                    SettingsRadioRow(
                        label = stringResource(label),
                        selected = settings.backSwipe == direction,
                        onClick = { store.update { it.copy(backSwipe = direction) } },
                    )
                }
            }

            SettingsGroupTitle(stringResource(R.string.appearance_time_picker))
            SettingsCard {
                listOf(
                    TimePickerStyle.DIAL to R.string.appearance_time_dial,
                    TimePickerStyle.INPUT to R.string.appearance_time_input,
                ).forEach { (style, label) ->
                    SettingsRadioRow(
                        label = stringResource(label),
                        selected = settings.timePicker == style,
                        onClick = { store.update { it.copy(timePicker = style) } },
                    )
                }
            }

            SettingsGroupTitle(stringResource(R.string.appearance_t9))
            SettingsCard {
                SettingsSwitchRow(
                    title = stringResource(R.string.appearance_t9_undo),
                    hint = stringResource(R.string.appearance_t9_undo_hint),
                    checked = settings.t9Undo,
                    onChange = { on -> store.update { it.copy(t9Undo = on) } },
                )
                if (settings.t9Undo) T9LineSettings()
            }

            SettingsGroupTitle(stringResource(R.string.appearance_editor_tools))
            SettingsCard { EditorToolsOrder() }

            SettingsGroupTitle(stringResource(R.string.appearance_paragraphs))
            SettingsCard {
                SettingsSwitchRow(
                    title = stringResource(R.string.appearance_show_markup),
                    hint = stringResource(R.string.appearance_show_markup_hint),
                    checked = settings.showMarkup,
                    onChange = { on -> store.update { it.copy(showMarkup = on) } },
                )
            }
            SettingsCard { ParagraphSettings() }
        }
    }

    if (pickingColor) {
        ColorPickerDialog(
            initial = settings.customAccent,
            onPick = { argb ->
                store.update { it.copy(accent = AccentColor.CUSTOM, customAccent = argb) }
                pickingColor = false
            },
            onDismiss = { pickingColor = false },
        )
    }

}

/** Линия под заменой Т9: вид, толщина, непрозрачность и образец. Ползунки сохраняются по отпусканию. */
@Composable
private fun T9LineSettings() {
    val store = LocalContext.current.appContainer.settings
    val settings = LocalAppSettings.current
    var width by remember(settings.t9LineWidth) { mutableFloatStateOf(settings.t9LineWidth.toFloat()) }
    var alpha by remember(settings.t9LineAlpha) { mutableFloatStateOf(settings.t9LineAlpha.toFloat()) }
    val styles = listOf(
        UnderlineStyle.NONE to R.string.appearance_t9_line_none,
        UnderlineStyle.SOLID to R.string.appearance_t9_line_solid,
        UnderlineStyle.DASHED to R.string.appearance_t9_line_dashed,
        UnderlineStyle.DOTTED to R.string.appearance_t9_line_dotted,
    )

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.appearance_t9_line), style = MaterialTheme.typography.bodyMedium)
        // Выпадающий список: четыре кнопки в ряд не помещались и обрезали подписи
        Box(Modifier.padding(top = 4.dp)) {
            SettingsDropdown(
                selected = settings.t9Line,
                options = styles.map { (style, label) -> style to stringResource(label) },
                onSelect = { style -> store.update { it.copy(t9Line = style) } },
            )
        }

        if (settings.t9Line != UnderlineStyle.NONE) {
            SliderHeader(stringResource(R.string.appearance_t9_width), stringResource(R.string.appearance_t9_width_value, width.roundToInt() / 10f))
            AppSlider(
                value = width,
                onValueChange = { width = it },
                onValueChangeFinished = { store.update { it.copy(t9LineWidth = width.roundToInt()) } },
                valueRange = T9_WIDTH_MIN.toFloat()..T9_WIDTH_MAX.toFloat(),
                steps = (T9_WIDTH_MAX - T9_WIDTH_MIN) / T9_WIDTH_STEP - 1,
            )
            SliderHeader(stringResource(R.string.appearance_t9_alpha), stringResource(R.string.appearance_t9_alpha_value, alpha.roundToInt()))
            AppSlider(
                value = alpha,
                onValueChange = { alpha = it },
                onValueChangeFinished = { store.update { it.copy(t9LineAlpha = alpha.roundToInt()) } },
                valueRange = T9_ALPHA_MIN.toFloat()..100f,
                steps = (100 - T9_ALPHA_MIN) / T9_ALPHA_STEP - 1,
            )
        }

        // Образец — сразу с тем, что на ползунках, ещё до отпускания
        CorrectionUnderlinePreview(
            text = stringResource(R.string.appearance_t9_sample),
            word = stringResource(R.string.appearance_t9_sample_word),
            line = correctionLine(settings.copy(t9LineWidth = width.roundToInt(), t9LineAlpha = alpha.roundToInt())),
            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
    }
}

/** Красная строка и расстояние между абзацами, с образцом записи. Ползунки сохраняются по отпусканию. */
@Composable
private fun ParagraphSettings() {
    val store = LocalContext.current.appContainer.settings
    val settings = LocalAppSettings.current
    val colors = MaterialTheme.colorScheme
    var indent by remember(settings.paragraphIndent) { mutableFloatStateOf(settings.paragraphIndent.toFloat()) }
    var gap by remember(settings.paragraphGap) { mutableFloatStateOf(settings.paragraphGap.toFloat()) }
    val off = stringResource(R.string.appearance_paragraph_off)

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val indentValue = indent.roundToInt()
        SliderHeader(
            stringResource(R.string.appearance_paragraph_indent),
            if (indentValue == 0) off else stringResource(R.string.appearance_paragraph_indent_value, indentValue / 10f),
        )
        AppSlider(
            value = indent,
            onValueChange = { indent = it },
            onValueChangeFinished = { store.update { it.copy(paragraphIndent = indent.roundToInt()) } },
            valueRange = 0f..INDENT_MAX.toFloat(),
            steps = INDENT_MAX / INDENT_STEP - 1,
        )
        val gapValue = gap.roundToInt()
        SliderHeader(
            stringResource(R.string.appearance_paragraph_gap),
            if (gapValue == 0) off else stringResource(R.string.appearance_paragraph_gap_value, gapValue),
        )
        AppSlider(
            value = gap,
            onValueChange = { gap = it },
            onValueChangeFinished = { store.update { it.copy(paragraphGap = gap.roundToInt()) } },
            valueRange = 0f..GAP_MAX.toFloat(),
            steps = GAP_MAX / GAP_STEP - 1,
        )

        // Образец — так же, как в записи, и сразу с тем, что на ползунках
        val sample = stringResource(R.string.appearance_paragraph_sample)
        val shown = remember(sample, colors, indentValue, gapValue) {
            MarkdownHighlighter(colors.outline, colors.primary, indentValue, gapValue).filter(AnnotatedString(sample)).text
        }
        Text(
            text = shown,
            style = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            modifier = Modifier
                .padding(top = 8.dp, bottom = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceContainerHighest)
                .padding(12.dp),
        )
    }
}

@Composable
private fun SliderHeader(title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CustomSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Icon(Icons.Outlined.Colorize, stringResource(R.string.appearance_custom_color), tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** Кружок цвета; «из обоев» — радужный с картинкой. */
@Composable
private fun AccentSwatch(accent: AccentColor, selected: Boolean, onClick: () -> Unit) {
    val color = accent.preview()
    val background = if (color != null) {
        Modifier.background(color)
    } else {
        Modifier.background(Brush.sweepGradient(listOf(Color(0xFFE5484D), Color(0xFFE0A21B), Color(0xFF3FA34D), Color(0xFF2F80ED), Color(0xFF9A5CE6), Color(0xFFE5484D))))
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(background)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        when {
            selected -> Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
            color == null -> Icon(Icons.Outlined.Wallpaper, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** Порядок кнопок панели над клавиатурой: стрелками вверх и вниз. */
@Composable
private fun EditorToolsOrder() {
    val store = LocalContext.current.appContainer.settings
    val tools = editorTools(LocalAppSettings.current.editorTools)
    fun move(index: Int, by: Int) {
        val list = tools.toMutableList()
        list.add(index + by, list.removeAt(index))
        store.update { it.copy(editorTools = list.map(EditorTool::name)) }
    }
    Column {
        Text(
            stringResource(R.string.appearance_editor_tools_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        tools.forEachIndexed { index, tool ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp)) {
                Icon(tool.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(tool.label), modifier = Modifier.weight(1f).padding(start = 16.dp))
                IconButton(enabled = index > 0, onClick = { move(index, -1) }) {
                    Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.gym_template_up))
                }
                IconButton(enabled = index < tools.lastIndex, onClick = { move(index, 1) }) {
                    Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.gym_template_down))
                }
            }
        }
    }
}
