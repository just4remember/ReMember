package dev.chaik.remember.core.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.StrikethroughS
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import dev.chaik.remember.R
import dev.chaik.remember.core.settings.LocalAppSettings

val EditorToolbarHeight = 52.dp

/** Кнопка оформления на панели. Имя константы хранится в настройках — переименовывать нельзя. */
enum class EditorTool(val icon: ImageVector, @param:StringRes val label: Int, val apply: (EditorState) -> Unit) {
    Bold(Icons.Outlined.FormatBold, R.string.editor_bold, { it.format { v -> MarkdownFormatting.toggleWrap(v, "**") } }),
    Italic(Icons.Outlined.FormatItalic, R.string.editor_italic, { it.format { v -> MarkdownFormatting.toggleWrap(v, "_") } }),
    Strike(Icons.Outlined.StrikethroughS, R.string.editor_strike, { it.format { v -> MarkdownFormatting.toggleWrap(v, "~~") } }),
    Spoiler(Icons.Outlined.VisibilityOff, R.string.editor_spoiler, { it.format { v -> MarkdownFormatting.toggleWrap(v, "||") } }),
    Link(Icons.Outlined.Link, R.string.editor_link, { it.linkDialogOpen = true }),
    Heading(Icons.Outlined.Title, R.string.editor_heading, { it.format(MarkdownFormatting::cycleHeading) }),
    Bullets(Icons.AutoMirrored.Outlined.FormatListBulleted, R.string.editor_bullets, { it.format { v -> MarkdownFormatting.toggleLineStyle(v, LineStyle.Bullet) } }),
    Numbers(Icons.Outlined.FormatListNumbered, R.string.editor_numbers, { it.format { v -> MarkdownFormatting.toggleLineStyle(v, LineStyle.Numbered) } }),
    Checklist(Icons.Outlined.Checklist, R.string.editor_checklist, { it.format { v -> MarkdownFormatting.toggleLineStyle(v, LineStyle.Checkbox) } }),
    Rule(Icons.Outlined.HorizontalRule, R.string.editor_rule, { it.format(MarkdownFormatting::insertRule) }),
}

/** Кнопки в сохранённом порядке; новые (появившиеся позже) — в конец. */
fun editorTools(saved: List<String>): List<EditorTool> =
    (saved.mapNotNull { name -> EditorTool.entries.firstOrNull { it.name == name } } + EditorTool.entries).distinct()

/** Панель над клавиатурой: оформление текста, фото и «спрятать клавиатуру». */
@Composable
fun EditorToolbar(
    state: EditorState,
    onAddPhoto: (() -> Unit)?,
    onHideKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Row(Modifier.height(EditorToolbarHeight), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .nestedScroll(KeepScrollInside)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onAddPhoto != null) {
                    ToolButton(Icons.Outlined.AddPhotoAlternate, R.string.editor_add_photo, onAddPhoto)
                    VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
                }
                // Порядок кнопок — из «Персонализации»
                editorTools(LocalAppSettings.current.editorTools).forEach { tool ->
                    ToolButton(tool.icon, tool.label) { tool.apply(state) }
                }
            }
            VerticalDivider(Modifier.height(24.dp))
            ToolButton(Icons.Outlined.KeyboardHide, R.string.editor_hide_keyboard, onHideKeyboard)
        }
    }
}

/**
 * Докрутили панель до края — остаток не уходит наружу. Иначе его подхватывают вкладки приложения,
 * а начало их листания убирает клавиатуру.
 */
private val KeepScrollInside = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
}

@Composable
private fun ToolButton(icon: ImageVector, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = stringResource(label))
    }
}
