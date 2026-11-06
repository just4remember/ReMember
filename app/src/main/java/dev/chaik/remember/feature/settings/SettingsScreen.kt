package dev.chaik.remember.feature.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.feature.settings.about.AboutRoute
import dev.chaik.remember.feature.settings.appearance.PersonalizationRoute
import dev.chaik.remember.feature.settings.data.DataRoute
import dev.chaik.remember.feature.settings.feedback.FeedbackRoute
import dev.chaik.remember.feature.settings.pages.PagesRoute
import dev.chaik.remember.feature.settings.profile.ProfileRoute
import dev.chaik.remember.feature.settings.reminders.RemindersRoute
import dev.chaik.remember.feature.settings.security.SecurityRoute
import dev.chaik.remember.feature.settings.widgets.WidgetsRoute
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute : NavKey

/** Список разделов настроек; каждый открывается своей страницей. [route] = null — раздел ещё в работе. */
private data class Section(val icon: ImageVector, val title: Int, val hint: Int, val route: NavKey?)

private val Sections = listOf(
    Section(Icons.Outlined.Person, R.string.settings_profile, R.string.settings_profile_hint, ProfileRoute),
    Section(Icons.Outlined.Palette, R.string.settings_personalization, R.string.settings_personalization_hint, PersonalizationRoute),
    Section(Icons.Outlined.Dashboard, R.string.settings_pages, R.string.settings_pages_hint, PagesRoute),
    Section(Icons.Outlined.Widgets, R.string.settings_widgets, R.string.settings_widgets_hint, WidgetsRoute),
    Section(Icons.Outlined.Lock, R.string.settings_security, R.string.settings_security_hint, SecurityRoute),
    Section(Icons.Outlined.Backup, R.string.settings_data, R.string.settings_data_hint, DataRoute),
    Section(Icons.Outlined.Notifications, R.string.settings_reminders, R.string.settings_reminders_hint, RemindersRoute),
    Section(Icons.Outlined.Feedback, R.string.settings_feedback, R.string.settings_feedback_hint, FeedbackRoute),
    Section(Icons.Outlined.Info, R.string.settings_about, R.string.settings_about_hint, AboutRoute),
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpen: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nickname = LocalAppSettings.current.nickname
    ScreenScaffold(title = stringResource(R.string.section_settings), modifier = modifier, onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp)
                .padding(horizontal = 16.dp),
        ) {
            Sections.forEach { section ->
                SettingsItem(
                    icon = section.icon,
                    title = stringResource(section.title),
                    // У профиля вместо подсказки — никнейм, если он задан
                    hint = if (section.route == ProfileRoute && nickname.isNotBlank()) nickname else stringResource(section.hint),
                    onClick = section.route?.let { route -> { onOpen(route) } },
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(icon: ImageVector, title: String, hint: String, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .alpha(if (onClick != null) 1f else 0.45f),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        } else {
            Text(stringResource(R.string.settings_soon), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}
