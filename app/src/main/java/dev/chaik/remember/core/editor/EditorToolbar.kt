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
import dev.chaik.remember.R

val EditorToolbarHeight = 52.dp

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
                ToolButton(Icons.Outlined.FormatBold, R.string.editor_bold) {
                    state.format { MarkdownFormatting.toggleWrap(it, "**") }
                }
                ToolButton(Icons.Outlined.FormatItalic, R.string.editor_italic) {
                    state.format { MarkdownFormatting.toggleWrap(it, "_") }
                }
                ToolButton(Icons.Outlined.StrikethroughS, R.string.editor_strike) {
                    state.format { MarkdownFormatting.toggleWrap(it, "~~") }
                }
                ToolButton(Icons.Outlined.VisibilityOff, R.string.editor_spoiler) {
                    state.format { MarkdownFormatting.toggleWrap(it, "||") }
                }
                ToolButton(Icons.Outlined.Link, R.string.editor_link) { state.linkDialogOpen = true }
                ToolButton(Icons.Outlined.Title, R.string.editor_heading) {
                    state.format(MarkdownFormatting::cycleHeading)
                }
                ToolButton(Icons.AutoMirrored.Outlined.FormatListBulleted, R.string.editor_bullets) {
                    state.format { MarkdownFormatting.toggleLineStyle(it, LineStyle.Bullet) }
                }
                ToolButton(Icons.Outlined.FormatListNumbered, R.string.editor_numbers) {
                    state.format { MarkdownFormatting.toggleLineStyle(it, LineStyle.Numbered) }
                }
                ToolButton(Icons.Outlined.Checklist, R.string.editor_checklist) {
                    state.format { MarkdownFormatting.toggleLineStyle(it, LineStyle.Checkbox) }
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
