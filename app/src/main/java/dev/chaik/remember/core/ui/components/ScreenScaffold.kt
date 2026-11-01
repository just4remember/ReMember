package dev.chaik.remember.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.core.ui.LocalIslandPadding

/**
 * Общий каркас экрана: компактная шапка (обычная высота, без большого заголовка —
 * он съедал много места) и кнопка «назад» для вложенных страниц.
 * При прокрутке шапка слегка подкрашивается, чтобы отделяться от контента.
 *
 * [content] получает отступы сверху (под заголовок) и снизу (под островок).
 * [navigationIcon] заменяет «назад» — например, на «Выделить все» в режиме выделения.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    /** Заголовок с оформлением; если задан, показывается вместо [title]. */
    styledTitle: AnnotatedString? = null,
    onBack: (() -> Unit)? = null,
    /** Если задано — заголовок нажимается (например, чтобы выбрать дату). */
    onTitleClick: (() -> Unit)? = null,
    /** Модификатор всей полосы заголовка — например, жесты по ней. */
    titleModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    navigationIcon: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val islandPadding = LocalIslandPadding.current

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Samsung отдаёт в Material You разные background и surface — берём surface, как у заголовка
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Box(titleModifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (styledTitle != null) {
                            Text(styledTitle)
                        } else if (onTitleClick == null) {
                            Text(title)
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = onTitleClick),
                            ) {
                                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (navigationIcon != null) {
                        navigationIcon()
                    } else if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
            )
        },
        // Низ экрана обрабатываем сами через LocalIslandPadding
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
    ) { innerPadding ->
        content(
            PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = islandPadding,
            ),
        )
    }
}
