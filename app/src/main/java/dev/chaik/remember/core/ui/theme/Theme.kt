package dev.chaik.remember.core.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.UserManager
import androidx.compose.runtime.remember
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.settings.AccentColor
import dev.chaik.remember.core.settings.AppSettings
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.settings.ThemeMode

/**
 * Тема приложения по настройкам «Персонализации»: светлая / тёмная / AMOLED / как в системе,
 * акцент — из обоев (Material You) или свой. Заодно раздаёт настройки экранам через [LocalAppSettings].
 */
@Composable
fun ReMemberTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // До первой разблокировки настройки закрыты: звонок будильника идёт в оформлении по умолчанию
    val unlocked = remember { context.getSystemService(UserManager::class.java).isUserUnlocked }
    val settings = if (unlocked) {
        context.appContainer.settings.settings.collectAsStateWithLifecycle().value
    } else {
        remember { AppSettings() }
    }
    val dark = isDarkTheme(settings.theme)

    val preset = settings.accent.argb
    val base = when {
        settings.accent == AccentColor.CUSTOM -> accentScheme(Color(settings.customAccent), dark)
        preset == null -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> accentScheme(Color(preset), dark)
    }
    val colorScheme = when (settings.theme) {
        ThemeMode.AMOLED -> base.amoled()
        ThemeMode.ONE_DARK -> oneDarkScheme().withAccent(chosenAccent(settings))
        ThemeMode.NIGHT_OWL -> nightOwlScheme().withAccent(chosenAccent(settings))
        else -> base
    }

    // Значки строки состояния и навигации — под тему приложения, а не системы
    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(activity, dark) {
        val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        // Без полупрозрачной подложки под кнопками навигации — контент виден до самого низа
        activity?.window?.isNavigationBarContrastEnforced = false
    }

    // Вибрацию выключили — весь отклик приложения идёт в «пустую» вибрацию
    val haptics = if (settings.haptics) LocalHapticFeedback.current else SilentHaptics
    CompositionLocalProvider(LocalAppSettings provides settings, LocalHapticFeedback provides haptics) {
        MaterialTheme(colorScheme = colorScheme, typography = typography(settings.font), content = content)
    }
}

private object SilentHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

/** Тёмная ли тема сейчас — для цвета значков в строке состояния. */
@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.AMOLED, ThemeMode.ONE_DARK, ThemeMode.NIGHT_OWL -> true
}

/** Образцы для выбора акцента в настройках. */
fun AccentColor.preview(): Color? = argb?.let(::Color)

/**
 * Палитра из одного цвета: оттенки по светлоте, как тона Material (40/80 — основной,
 * 90/30 — контейнеры). Нейтральные поверхности — стандартные, чтобы фон оставался спокойным.
 */
private fun accentScheme(seed: Color, dark: Boolean): ColorScheme {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(seed.toArgb(), it) }
    fun tone(lightness: Float, saturation: Float = hsl[1], hueShift: Float = 0f) =
        Color(ColorUtils.HSLToColor(floatArrayOf((hsl[0] + hueShift + 360f) % 360f, saturation.coerceIn(0f, 1f), lightness)))
    val muted = hsl[1] * 0.35f
    return if (dark) {
        darkColorScheme(
            primary = tone(0.78f),
            onPrimary = tone(0.18f),
            primaryContainer = tone(0.30f),
            onPrimaryContainer = tone(0.90f),
            secondary = tone(0.78f, muted),
            onSecondary = tone(0.18f, muted),
            secondaryContainer = tone(0.28f, muted),
            onSecondaryContainer = tone(0.90f, muted),
            tertiary = tone(0.78f, hueShift = 60f),
            onTertiary = tone(0.18f, hueShift = 60f),
            tertiaryContainer = tone(0.30f, hueShift = 60f),
            onTertiaryContainer = tone(0.90f, hueShift = 60f),
        )
    } else {
        lightColorScheme(
            primary = tone(0.42f),
            onPrimary = Color.White,
            primaryContainer = tone(0.90f),
            onPrimaryContainer = tone(0.15f),
            secondary = tone(0.40f, muted),
            onSecondary = Color.White,
            secondaryContainer = tone(0.88f, muted),
            onSecondaryContainer = tone(0.15f, muted),
            tertiary = tone(0.40f, hueShift = 60f),
            onTertiary = Color.White,
            tertiaryContainer = tone(0.90f, hueShift = 60f),
            onTertiaryContainer = tone(0.15f, hueShift = 60f),
        )
    }
}

/** AMOLED: фон чисто чёрный, карточки — чуть светлее, чтобы оставаться видимыми. */
/** Акцент, выбранный вручную (свой или из готовых); «из обоев» — null. */
private fun chosenAccent(settings: AppSettings): Color? = when {
    settings.accent == AccentColor.CUSTOM -> Color(settings.customAccent)
    else -> settings.accent.argb?.let(::Color)
}

/**
 * Тема-палитра с выбранным акцентом: меняется только основной цвет (кнопки, выделение),
 * фон, карточки и вторичные цвета остаются от темы. Без акцента — родной цвет темы.
 */
private fun ColorScheme.withAccent(accent: Color?): ColorScheme {
    if (accent == null) return this
    val tones = accentScheme(accent, dark = true)
    return copy(
        primary = tones.primary,
        onPrimary = tones.onPrimary,
        primaryContainer = tones.primaryContainer,
        onPrimaryContainer = tones.onPrimaryContainer,
        inversePrimary = tones.inversePrimary,
        surfaceTint = tones.primary,
    )
}

private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0E0E10),
    surfaceContainer = Color(0xFF141416),
    surfaceContainerHigh = Color(0xFF1B1B1E),
    surfaceContainerHighest = Color(0xFF232327),
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF232327),
)
