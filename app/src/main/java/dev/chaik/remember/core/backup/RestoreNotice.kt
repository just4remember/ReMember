package dev.chaik.remember.core.backup

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.chaik.remember.R
import dev.chaik.remember.appContainer

/**
 * Окно после восстановления из копии: что в копию не входит и что нужно ввести заново.
 * Отметку ставит [BackupManager.applyPendingRestore], окно показывается один раз.
 */
object RestoreNotice {
    private const val PREFS = "restore_notice"
    private const val KEY_SHOW = "show"

    fun mark(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) { putBoolean(KEY_SHOW, true) }

    fun pending(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHOW, false)

    fun clear(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(KEY_SHOW) }
}

/** Показать окно, когда приложение готово (после блокировки и приветствия). */
@Composable
fun RestoreNoticeDialog(ready: Boolean) {
    val context = LocalContext.current
    var show by remember { mutableStateOf(RestoreNotice.pending(context)) }
    if (!show || !ready) return
    // На том же телефоне настройки копий на сервер остались — о них говорить незачем
    val serverMissing = !context.appContainer.serverBackup.settings.value.configured
    val close = {
        RestoreNotice.clear(context)
        show = false
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.restore_notice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (serverMissing) Text(stringResource(R.string.restore_notice_server))
                Text(
                    stringResource(if (serverMissing) R.string.restore_notice_other else R.string.restore_notice_other_alone),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.tutorial_got_it)) } },
    )
}
