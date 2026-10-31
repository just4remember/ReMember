package dev.chaik.remember.feature.settings.security

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.security.AppLock
import dev.chaik.remember.core.security.BiometricUnlock
import dev.chaik.remember.core.security.PinResult
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.feature.settings.SettingsCard
import dev.chaik.remember.feature.settings.SettingsGroupTitle
import dev.chaik.remember.feature.settings.SettingsRadioRow
import dev.chaik.remember.feature.settings.SettingsSwitchRow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object SecurityRoute : NavKey

/** Что спрашивает окно PIN: задать новый, сменить (старый + новый) или подтвердить старым. */
private enum class PinDialogMode { SET, CHANGE, CONFIRM_DISABLE, CONFIRM_APP_OFF }

/** Безопасность: PIN-код (поставить, сменить, выключить), отпечаток, через сколько блокировать. */
@Composable
fun SecurityScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lock = context.appContainer.appLock
    val settings by lock.settings.collectAsStateWithLifecycle()
    val biometricAvailable = remember { BiometricUnlock.isAvailable(context) }
    var dialog by remember { mutableStateOf<PinDialogMode?>(null) }

    // Подтверждение отпечатком вместо старого PIN — если PIN забыт. Отпечаток телефона
    // подходит, даже если вход по отпечатку в приложении выключен
    val activity = LocalActivity.current as? FragmentActivity
    val fingerprintTitle = stringResource(R.string.security_fingerprint_title)
    val fingerprintCancel = stringResource(R.string.action_cancel)
    val confirmWithFingerprint: ((onSuccess: () -> Unit) -> Unit)? =
        if (biometricAvailable && activity != null) {
            { onSuccess ->
                // Системное окно — не повод блокировать приложение
                lock.allowExternal()
                BiometricUnlock.prompt(activity, fingerprintTitle, fingerprintCancel, onSuccess)
            }
        } else {
            null
        }

    ScreenScaffold(title = stringResource(R.string.settings_security), modifier = modifier, onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            if (!settings.enabled) {
                SettingsCard {
                    Text(
                        text = stringResource(R.string.security_off_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                    Button(onClick = { dialog = PinDialogMode.SET }, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)) {
                        Text(stringResource(R.string.security_set_pin))
                    }
                }
                return@ScreenScaffold
            }

            SettingsCard {
                SettingsSwitchRow(
                    title = stringResource(R.string.security_lock_app),
                    hint = stringResource(R.string.security_lock_app_hint),
                    checked = settings.lockApp,
                    // Включить можно сразу, выключить — только зная PIN
                    onChange = { on -> if (on) lock.setLockApp(true) else dialog = PinDialogMode.CONFIRM_APP_OFF },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.security_lock),
                    hint = stringResource(R.string.security_lock_hint),
                    checked = true,
                    // Выключить можно, только зная PIN
                    onChange = { dialog = PinDialogMode.CONFIRM_DISABLE },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dialog = PinDialogMode.CHANGE }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(stringResource(R.string.security_change_pin), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
                }
                if (biometricAvailable) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.security_biometric),
                        hint = stringResource(R.string.security_biometric_hint),
                        checked = settings.biometric,
                        onChange = lock::setBiometric,
                    )
                }
            }

            if (settings.lockApp) {
                SettingsGroupTitle(stringResource(R.string.security_timeout))
                SettingsCard {
                    AppLock.TimeoutOptions.forEach { millis ->
                        SettingsRadioRow(
                            label = timeoutLabel(millis),
                            selected = settings.timeoutMillis == millis,
                            onClick = { lock.setTimeout(millis) },
                        )
                    }
                }
            }
        }
    }

    dialog?.let { mode -> PinDialog(mode, lock, confirmWithFingerprint, onDone = { dialog = null }) }
}

@Composable
private fun timeoutLabel(millis: Long): String {
    val seconds = (millis / 1000).toInt()
    return if (seconds == 0) {
        stringResource(R.string.security_now)
    } else if (seconds < 60) {
        pluralStringResource(R.plurals.security_after_seconds, seconds, seconds)
    } else {
        pluralStringResource(R.plurals.security_after_minutes, seconds / 60, seconds / 60)
    }
}

/**
 * Окно PIN-кода. Цифры скрыты. Старый PIN проверяется так же, как на экране блокировки:
 * пять ошибок подряд — пауза. Вместо старого PIN можно приложить палец ([confirmWithFingerprint]).
 */
@Composable
private fun PinDialog(
    mode: PinDialogMode,
    lock: AppLock,
    confirmWithFingerprint: ((onSuccess: () -> Unit) -> Unit)?,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    /** Старый PIN подтверждён отпечатком — вводить его не нужно. */
    var fingerprintOk by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val needCurrent = mode != PinDialogMode.SET && !fingerprintOk
    val needNew = mode == PinDialogMode.SET || mode == PinDialogMode.CHANGE
    /** Что сделать, когда старый PIN подтверждён в окне без нового PIN. */
    fun confirmed() {
        if (mode == PinDialogMode.CONFIRM_APP_OFF) lock.setLockApp(false) else lock.disable()
    }
    val lengthError = stringResource(R.string.security_pin_length, AppLock.MIN_PIN, AppLock.MAX_PIN)
    val mismatchError = stringResource(R.string.security_pin_mismatch)
    val wrongTemplate = pluralWrong()
    val waitTemplate = stringResource(R.string.lock_wait)

    fun submit() {
        if (busy) return
        if (needNew && new.length !in AppLock.MIN_PIN..AppLock.MAX_PIN) {
            error = lengthError
            return
        }
        if (needNew && new != repeat) {
            error = mismatchError
            return
        }
        busy = true
        scope.launch {
            if (needCurrent) {
                when (val result = lock.unlockWithPin(current)) {
                    is PinResult.Wrong -> {
                        error = wrongTemplate(result.attemptsLeft)
                        busy = false
                        return@launch
                    }
                    is PinResult.LockedOut -> {
                        error = waitTemplate.format(((result.untilMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(1))
                        busy = false
                        return@launch
                    }
                    PinResult.Unlocked -> Unit
                }
            }
            if (needNew) lock.setPin(new) else confirmed()
            onDone()
        }
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = {
            Text(
                stringResource(
                    when (mode) {
                        PinDialogMode.SET -> R.string.security_set_pin
                        PinDialogMode.CHANGE -> R.string.security_change_pin
                        PinDialogMode.CONFIRM_DISABLE -> R.string.security_disable_title
                        PinDialogMode.CONFIRM_APP_OFF -> R.string.security_lock_app_off_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (needCurrent) {
                    PinField(stringResource(R.string.security_current_pin), current, Modifier.focusRequester(focus)) { current = it; error = null }
                    if (confirmWithFingerprint != null) {
                        TextButton(
                            onClick = {
                                confirmWithFingerprint {
                                    error = null
                                    if (!needNew) {
                                        confirmed()
                                        onDone()
                                    } else {
                                        fingerprintOk = true
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.Fingerprint, null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.security_forgot_pin))
                        }
                    }
                } else if (fingerprintOk) {
                    Text(
                        text = stringResource(R.string.security_fingerprint_ok),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (needNew) {
                    PinField(
                        label = stringResource(R.string.security_new_pin),
                        value = new,
                        modifier = if (mode != PinDialogMode.SET) Modifier else Modifier.focusRequester(focus),
                    ) { new = it; error = null }
                    PinField(stringResource(R.string.security_repeat_pin), repeat) { repeat = it; error = null }
                    Text(
                        text = stringResource(R.string.security_pin_hint, AppLock.MIN_PIN, AppLock.MAX_PIN),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = ::submit) {
                Text(stringResource(if (needNew) R.string.gym_save else R.string.security_disable))
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PinField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.length <= AppLock.MAX_PIN && v.all(Char::isDigit)) onChange(v) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier.fillMaxWidth(),
    )
}

/** «Неверный PIN. Осталось N попыток» — шаблон, чтобы вызвать вне композиции. */
@Composable
private fun pluralWrong(): (Int) -> String {
    val resources = LocalContext.current.resources
    return { left -> resources.getQuantityString(R.plurals.lock_wrong, left, left) }
}
