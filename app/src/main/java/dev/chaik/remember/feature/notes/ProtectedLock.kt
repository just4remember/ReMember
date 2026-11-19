package dev.chaik.remember.feature.notes

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.security.BiometricUnlock
import dev.chaik.remember.core.ui.LocalTabVisible
import dev.chaik.remember.feature.lock.LockScreen
import kotlinx.coroutines.flow.first

/**
 * Вход в защищённую заметку или папку: тот же PIN и отпечаток, что у приложения;
 * окно отпечатка — сразу. [title] — над полем PIN и в окне отпечатка.
 */
@Composable
internal fun ProtectedLock(title: String, onUnlocked: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val lock = container.appLock
    val activity = LocalActivity.current as? FragmentActivity
    val usePin = stringResource(R.string.lock_biometric_use_pin)
    val showBiometric: (() -> Unit)? = activity
        ?.takeIf { lock.biometricEnabled && BiometricUnlock.isAvailable(it) }
        ?.let { host ->
            {
                lock.allowExternal()
                BiometricUnlock.prompt(host, title, usePin, onSuccess = onUnlocked)
            }
        }
    // Окно отпечатка не показываем поверх другой вкладки, в фоне и пока закрыто само приложение
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tabVisible = LocalTabVisible.current
    LaunchedEffect(tabVisible) {
        if (!tabVisible) return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        lock.locked.first { !it }
        showBiometric?.invoke()
    }
    LockScreen(lock = lock, onUnlocked = onUnlocked, onBiometric = showBiometric, modifier = modifier, title = title)
}
