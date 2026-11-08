package dev.chaik.remember.feature.settings.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.BuildConfig
import dev.chaik.remember.R
import dev.chaik.remember.core.feedback.openExternalLink
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.feature.settings.SettingsCard
import dev.chaik.remember.feature.settings.SettingsGroupTitle
import kotlinx.serialization.Serializable

@Serializable
data object SupportRoute : NavKey

/**
 * Ссылка на сбор средств в банке — из secrets.properties (support.url). Пусто — раздела нет.
 * Перевод добровольный и безвозмездный: за него ничего не открывается, иначе это была бы оплата.
 */
val SupportUrl: String get() = BuildConfig.SUPPORT_URL

/** Поддержать автора: что это, кнопка перевода и другие способы помочь. */
@Composable
fun SupportScreen(onBack: () -> Unit, onOpenFeedback: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    ScreenScaffold(title = stringResource(R.string.settings_support), modifier = modifier, onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Icon(
                    Icons.Outlined.VolunteerActivism,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Text(
                    text = stringResource(R.string.support_text),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
                )
            }
            Button(
                onClick = { openExternalLink(context, SupportUrl) },
                enabled = SupportUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.support_button)) }
            Text(
                text = stringResource(R.string.support_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsGroupTitle(stringResource(R.string.support_other_title))
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.support_other_text),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onOpenFeedback) { Text(stringResource(R.string.support_feedback_button)) }
                }
            }
        }
    }
}
