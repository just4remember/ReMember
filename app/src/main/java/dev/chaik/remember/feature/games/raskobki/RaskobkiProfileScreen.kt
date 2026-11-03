package dev.chaik.remember.feature.games.raskobki

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.raskobki.RaskobkiAccount
import dev.chaik.remember.core.raskobki.RaskobkiApiException
import dev.chaik.remember.core.raskobki.RaskobkiRepository
import dev.chaik.remember.core.raskobki.parseOobCode
import dev.chaik.remember.core.raskobki.raskobkiStats
import dev.chaik.remember.core.raskobki.raskobkiStreak
import dev.chaik.remember.core.raskobki.raskobkiToday
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.components.StreakFlame
import dev.chaik.remember.feature.settings.appearance.ColorPickerDialog
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Важное предупреждение: оранжевый, красный оставлен для ошибок. */
private val WarningColor = Color(0xFFFF9800)

@Serializable
data object RaskobkiProfileRoute : NavKey

/** Профиль Ра[скобок]: вход по ссылке из письма или данные аккаунта. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RaskobkiProfileScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val repository = LocalContext.current.appContainer.raskobki
    val account by repository.account.collectAsState()

    ScreenScaffold(title = stringResource(R.string.raskobki_account), modifier = modifier, onBack = onBack) { padding ->
        val keyboard = WindowInsets.isImeVisible
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .then(if (keyboard) Modifier.imePadding() else Modifier.padding(bottom = padding.calculateBottomPadding()))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            val current = account
            if (current == null) SignIn(repository) else Signed(current, repository)
            HorizontalDivider()
            ColorSetting(R.string.raskobki_bracket_color, raskobkiBracketColor(), repository.bracketColor.collectAsState().value != null, repository::setBracketColor)
            ColorSetting(R.string.raskobki_answer_color, raskobkiAnswerColor(), repository.answerColor.collectAsState().value != null, repository::setAnswerColor)
            RaskobkiColorPreview()
            HorizontalDivider()
            RaskobkiReminderSection()
            HorizontalDivider()
            val metrics = LocalContext.current.appContainer.raskobkiMetrics
            val consent by metrics.consent.collectAsState()
            var terms by rememberSaveable { mutableStateOf(false) }
            RaskobkiConsentSection(consent == true, terms, { terms = it }, metrics)
        }
    }
}

@Composable
private fun SignIn(repository: RaskobkiRepository) {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf(repository.pendingEmail) }
    var email by rememberSaveable { mutableStateOf(repository.pendingEmail.orEmpty()) }
    var link by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }

    fun send() {
        val address = email.trim()
        busy = true
        error = null
        scope.launch {
            try {
                repository.sendSignInMail(address)
                pending = address
            } catch (failure: Exception) {
                error = errorText(failure)
            }
            busy = false
        }
    }

    fun signIn(address: String) {
        val code = parseOobCode(link)
        if (code == null) {
            error = R.string.raskobki_not_a_link
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                repository.signIn(address, code)
            } catch (failure: Exception) {
                error = errorText(failure)
            }
            busy = false
        }
    }

    val sentTo = pending
    if (sentTo == null) {
        Text(stringResource(R.string.raskobki_login_intro))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.raskobki_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = ::send, enabled = !busy && email.contains('@'), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.raskobki_send_mail))
        }
        Text(
            stringResource(R.string.raskobki_login_google),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        Text(stringResource(R.string.raskobki_mail_sent, sentTo), fontWeight = FontWeight.Bold)
        Step(1, R.string.raskobki_step_1)
        Step(2, R.string.raskobki_step_2)
        Step(3, R.string.raskobki_step_3)
        Text(
            stringResource(R.string.raskobki_step_warning),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .background(WarningColor.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                .padding(12.dp),
        )
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            label = { Text(stringResource(R.string.raskobki_link_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { signIn(sentTo) }, enabled = !busy && link.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.raskobki_sign_in))
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = ::send, enabled = !busy) { Text(stringResource(R.string.raskobki_send_again)) }
            TextButton(
                onClick = {
                    repository.pendingEmail = null
                    pending = null
                    error = null
                },
                enabled = !busy,
            ) { Text(stringResource(R.string.raskobki_change_email)) }
        }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
}

/** Цвет отдельно от акцента: кружок открывает выбор, «Сбросить» возвращает цвет темы. */
@Composable
private fun ColorSetting(@StringRes title: Int, color: Color, custom: Boolean, onPick: (Long?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(title), modifier = Modifier.weight(1f))
        if (custom) {
            TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.raskobki_color_reset)) }
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClickLabel = stringResource(title)) { picking = true },
        )
    }
    if (picking) {
        ColorPickerDialog(
            initial = color.toArgb().toLong() and 0xFFFFFFFFL,
            onPick = {
                onPick(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun Step(number: Int, @StringRes text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        ) {
            Text(number.toString(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(stringResource(text), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Signed(account: RaskobkiAccount, repository: RaskobkiRepository) {
    val context = LocalContext.current
    val progress by repository.progress.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    val streak = remember(progress) { raskobkiStreak(progress, raskobkiToday()).size }
    val stats = remember(progress) { raskobkiStats(progress.values) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        RaskobkiAvatar(account, 56)
        Column(Modifier.weight(1f)) {
            Text(
                account.name ?: stringResource(R.string.raskobki_no_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(account.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.raskobki_streak), color = MaterialTheme.colorScheme.onSurfaceVariant)
        StreakFlame(streak)
    }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.raskobki_completed_count), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(progress.values.count { it.c }.toString(), fontWeight = FontWeight.Bold)
    }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.raskobki_average_score), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stats.average.toString(), fontWeight = FontWeight.Bold)
    }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.raskobki_best_score), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stats.best.toString(), fontWeight = FontWeight.Bold)
    }
    Text(
        stringResource(R.string.raskobki_name_on_site),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { openRaskobkiSite(context) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.raskobki_open_site))
        }
        OutlinedButton(onClick = { confirm = true }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.raskobki_sign_out), color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.raskobki_sign_out)) },
            text = { Text(stringResource(R.string.raskobki_sign_out_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    repository.signOut()
                }) { Text(stringResource(R.string.raskobki_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Кружок с первой буквой имени или почты. */
@Composable
internal fun RaskobkiAvatar(account: RaskobkiAccount, size: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
    ) {
        Text(
            (account.name ?: account.email).take(1).uppercase(),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.45f).sp,
        )
    }
}

@StringRes
private fun errorText(failure: Exception): Int {
    val api = failure as? RaskobkiApiException ?: return R.string.raskobki_error_network
    return when {
        api.message.orEmpty().contains("OOB_CODE") -> R.string.raskobki_error_link
        api.message.orEmpty().contains("EMAIL") -> R.string.raskobki_error_email
        api.code == 403 || api.message.orEmpty().contains("OPERATION_NOT_ALLOWED") -> R.string.raskobki_error_blocked
        else -> R.string.raskobki_error_network
    }
}
