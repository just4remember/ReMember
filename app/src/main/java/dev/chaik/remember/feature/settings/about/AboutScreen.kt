package dev.chaik.remember.feature.settings.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.core.ui.components.AppIcon
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.feature.settings.SettingsCard
import dev.chaik.remember.feature.settings.SettingsGroupTitle
import kotlinx.serialization.Serializable

@Serializable
data object AboutRoute : NavKey

/** Что умеет раздел: иконка, название, одна строка. */
private data class Feature(val icon: ImageVector, val title: Int, val text: Int)

private val Features = listOf(
    Feature(Icons.Outlined.WbSunny, R.string.tab_today, R.string.about_today),
    Feature(Icons.Outlined.Description, R.string.tab_notes, R.string.about_notes),
    Feature(Icons.Outlined.TaskAlt, R.string.tab_tasks, R.string.about_tasks),
    Feature(Icons.Outlined.CalendarMonth, R.string.tab_calendar, R.string.about_calendar),
    Feature(Icons.Outlined.Search, R.string.section_search, R.string.about_search),
    Feature(Icons.Outlined.Insights, R.string.section_stats, R.string.about_stats),
    Feature(Icons.Outlined.FitnessCenter, R.string.section_gym, R.string.about_gym),
    Feature(Icons.Outlined.MonitorHeart, R.string.section_health, R.string.about_health),
    Feature(Icons.Outlined.Map, R.string.section_map, R.string.about_map),
    Feature(Icons.Outlined.Alarm, R.string.section_time, R.string.about_time),
    Feature(Icons.Outlined.SportsEsports, R.string.section_games, R.string.about_games),
    Feature(Icons.Outlined.Speed, R.string.section_network, R.string.about_network),
    Feature(Icons.Outlined.Notifications, R.string.settings_reminders, R.string.about_reminders),
    Feature(Icons.Outlined.Widgets, R.string.settings_widgets, R.string.about_widgets),
    Feature(Icons.Outlined.Palette, R.string.about_look_title, R.string.about_look),
    Feature(Icons.Outlined.Backup, R.string.settings_data, R.string.about_data),
    Feature(Icons.Outlined.Lock, R.string.about_privacy_title, R.string.about_privacy),
)

/** О приложении: значок, версия, коротко — что умеет и как хранит данные. */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    }

    ScreenScaffold(title = stringResource(R.string.settings_about), modifier = modifier, onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                AppIcon(size = 96.dp)
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = stringResource(R.string.about_version, version) + " · " + stringResource(R.string.about_author),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp),
                )
            }

            SettingsGroupTitle(stringResource(R.string.about_features))
            SettingsCard {
                Features.forEach { feature ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Icon(feature.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(stringResource(feature.title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = stringResource(feature.text),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
