package dev.chaik.remember.feature.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.core.ui.components.AppIcon
import dev.chaik.remember.core.security.AppLock
import dev.chaik.remember.core.security.PinResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val KeySize = 76.dp

/**
 * Экран блокировки: точки PIN, цифровая клавиатура и кнопка отпечатка.
 * PIN проверяется сам, как только набраны все цифры.
 *
 * @param onBiometric показать окно отпечатка; null — отпечаток недоступен
 */
@Composable
fun LockScreen(
    lock: AppLock,
    onUnlocked: () -> Unit,
    onBiometric: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** Подпись вместо названия приложения. */
    title: String? = null,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var entered by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var attemptsLeft by remember { mutableStateOf<Int?>(null) }
    var lockoutUntil by remember { mutableLongStateOf(lock.lockoutUntil() ?: 0L) }
    var secondsLeft by remember { mutableLongStateOf(0L) }
    val shake = remember { Animatable(0f) }

    // Обратный отсчёт паузы после череды ошибок
    LaunchedEffect(lockoutUntil) {
        while (true) {
            val left = lockoutUntil - System.currentTimeMillis()
            secondsLeft = if (left > 0) (left + 999) / 1000 else 0
            if (left <= 0) break
            delay(250)
        }
    }
    val lockedOut = secondsLeft > 0

    fun submit(pin: String) {
        checking = true
        scope.launch {
            val result = lock.unlockWithPin(pin)
            when (result) {
                PinResult.Unlocked -> onUnlocked()
                is PinResult.Wrong -> attemptsLeft = result.attemptsLeft
                is PinResult.LockedOut -> {
                    attemptsLeft = null
                    lockoutUntil = result.untilMillis
                }
            }
            // Неверный PIN. Смотрим на результат, а не на замок приложения: заметку проверяют и в открытом приложении
            if (result != PinResult.Unlocked) {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                shake.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 400
                        -24f at 50
                        24f at 110
                        -16f at 170
                        16f at 230
                        -8f at 290
                        8f at 350
                    },
                )
                entered = ""
            }
            checking = false
        }
    }

    fun press(digit: Char) {
        if (checking || lockedOut || entered.length >= lock.pinLength) return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        entered += digit
        if (entered.length == lock.pinLength) submit(entered)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        // Внутри экрана места меньше (шапка и островок), значок там не нужен: клавиатура не должна сжиматься
        if (title == null) {
            AppIcon(size = 72.dp)
            Spacer(Modifier.height(16.dp))
        }
        Text(title ?: stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                lockedOut -> stringResource(R.string.lock_wait, secondsLeft)
                attemptsLeft != null -> pluralStringResource(R.plurals.lock_wrong, attemptsLeft!!, attemptsLeft!!)
                else -> stringResource(R.string.lock_enter_pin)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (lockedOut || attemptsLeft != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        // Точки набранных цифр; при ошибке трясутся
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.offset { IntOffset(shake.value.roundToInt(), 0) },
        ) {
            repeat(lock.pinLength) { index ->
                val dot = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                Box(
                    if (index < entered.length) {
                        dot.background(MaterialTheme.colorScheme.primary)
                    } else {
                        dot.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    },
                )
            }
        }
        Spacer(Modifier.height(40.dp))

        Keypad(
            enabled = !checking && !lockedOut,
            onDigit = ::press,
            onBackspace = {
                if (entered.isNotEmpty() && !checking) entered = entered.dropLast(1)
            },
            onBiometric = onBiometric,
        )
    }
}

@Composable
private fun Keypad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit -> DigitKey(digit, enabled) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (onBiometric != null) {
                IconKey(Icons.Outlined.Fingerprint, stringResource(R.string.lock_fingerprint), onBiometric)
            } else {
                Spacer(Modifier.size(KeySize))
            }
            DigitKey('0', enabled) { onDigit('0') }
            IconKey(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.lock_backspace), onBackspace)
        }
    }
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(KeySize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(digit.toString(), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun IconKey(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(KeySize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, modifier = Modifier.size(30.dp))
        }
    }
}
