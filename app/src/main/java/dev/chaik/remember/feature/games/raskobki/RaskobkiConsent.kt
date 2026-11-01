package dev.chaik.remember.feature.games.raskobki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.core.raskobki.RaskobkiMetrics
import dev.chaik.remember.core.ui.components.ScreenScaffold

/** Экран согласия перед первой игрой. На саму игру выбор не влияет. */
@Composable
internal fun RaskobkiConsentScreen(metrics: RaskobkiMetrics, onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(title = stringResource(R.string.raskobki_consent_title), modifier = modifier, onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.raskobki_consent_text), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = metrics::accept, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.raskobki_consent_accept))
            }
            OutlinedButton(onClick = metrics::decline, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.raskobki_consent_decline))
            }
        }
    }
}

/** Блок профиля: состояние согласия и условия, которые можно перечитать. */
@Composable
internal fun RaskobkiConsentSection(accepted: Boolean, showTerms: Boolean, onShowTerms: (Boolean) -> Unit, metrics: RaskobkiMetrics) {
    Text(stringResource(R.string.raskobki_consent_title), fontWeight = FontWeight.Bold)
    Text(
        stringResource(if (accepted) R.string.raskobki_consent_on else R.string.raskobki_consent_off),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedButton(onClick = { onShowTerms(true) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.raskobki_consent_read))
    }
    if (showTerms) {
        AlertDialog(
            onDismissRequest = { onShowTerms(false) },
            title = { Text(stringResource(R.string.raskobki_consent_title)) },
            text = {
                Text(
                    stringResource(R.string.raskobki_consent_text),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (accepted) metrics.decline() else metrics.accept()
                    onShowTerms(false)
                }) {
                    Text(stringResource(if (accepted) R.string.raskobki_consent_withdraw else R.string.raskobki_consent_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowTerms(false) }) { Text(stringResource(R.string.editor_close)) }
            },
        )
    }
}
