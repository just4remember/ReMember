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
        ThemeMode.SOLARIZED_LIGHT -> solarizedLightScheme().withAccent(chosenAccent(settings), dark = false)
        ThemeMode.TOKYO_NIGHT_LIGHT -> tokyoNightLightScheme().withAccent(chosenAccent(settings), dark = false)
        else -> base
    }.accentEverywhere()

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
    ThemeMode.LIGHT, ThemeMode.SOLARIZED_LIGHT, ThemeMode.TOKYO_NIGHT_LIGHT -> false
    ThemeMode.DARK, ThemeMode.AMOLED, ThemeMode.ONE_DARK, ThemeMode.NIGHT_OWL -> true
}

/** Образцы для выбора акцента в настройках. */
fun AccentColor.preview(): Color? = argb?.let(::Color)

/**
 * Палитра из одного цвета: оттенки по светлоте, как тона Material (40/80 — основной,
 * 90/30 — контейнеры). Нейтральные поверхности (фон, карточки, подложки, обводки) — почти
 * серые с едва заметным оттенком акцента, как в Material You: со стандартными нейтральными
 * Material, чуть сиреневыми, тёплые акценты (коричневый, оранжевый) смотрелись чужеродно.
 */
private fun accentScheme(seed: Color, dark: Boolean): ColorScheme {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(seed.toArgb(), it) }
    fun tone(lightness: Float, saturation: Float = hsl[1], hueShift: Float = 0f) =
        Color(ColorUtils.HSLToColor(floatArrayOf((hsl[0] + hueShift + 360f) % 360f, saturation.coerceIn(0f, 1f), lightness)))
    val muted = hsl[1] * 0.35f
    // Насыщенность нейтральных: едва заметно, у «вариантов» (подложки, обводки) — чуть сильнее
    val neutral = (hsl[1] * 0.12f).coerceAtMost(0.08f)
    val neutralVariant = (hsl[1] * 0.2f).coerceAtMost(0.14f)
    fun n(lightness: Float) = tone(lightness, neutral)
    fun nv(lightness: Float) = tone(lightness, neutralVariant)
    return if (dark) {
        darkColorScheme(
            background = n(0.07f),
            onBackground = n(0.90f),
            surface = n(0.07f),
            onSurface = n(0.90f),
            surfaceVariant = nv(0.27f),
            onSurfaceVariant = nv(0.79f),
            surfaceContainerLowest = n(0.04f),
            surfaceContainerLow = n(0.09f),
            surfaceContainer = n(0.11f),
            surfaceContainerHigh = n(0.14f),
            surfaceContainerHighest = n(0.17f),
            surfaceDim = n(0.07f),
            surfaceBright = n(0.21f),
            inverseSurface = n(0.90f),
            inverseOnSurface = n(0.19f),
            outline = nv(0.57f),
            outlineVariant = nv(0.30f),
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
            background = n(0.985f),
            onBackground = n(0.11f),
            surface = n(0.985f),
            onSurface = n(0.11f),
            surfaceVariant = nv(0.90f),
            onSurfaceVariant = nv(0.30f),
            surfaceContainerLowest = n(1.0f),
            surfaceContainerLow = n(0.965f),
            surfaceContainer = n(0.948f),
            surfaceContainerHigh = n(0.93f),
            surfaceContainerHighest = n(0.91f),
            surfaceDim = n(0.86f),
            surfaceBright = n(0.985f),
            inverseSurface = n(0.19f),
            inverseOnSurface = n(0.95f),
            outline = nv(0.48f),
            outlineVariant = nv(0.80f),
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
private fun ColorScheme.withAccent(accent: Color?, dark: Boolean = true): ColorScheme {
    if (accent == null) return this
    val tones = accentScheme(accent, dark)
    return copy(
        primary = tones.primary,
        onPrimary = tones.onPrimary,
        primaryContainer = tones.primaryContainer,
        onPrimaryContainer = tones.onPrimaryContainer,
        inversePrimary = tones.inversePrimary,
        surfaceTint = tones.primary,
    )
}

/**
 * Один акцент на всё приложение. Компоненты Material красят выделение по-разному: чипсы,
 * островок, тональные кнопки, полосы громкости — «вторичным» цветом, переключатели AM/PM —
 * «третичным». У палитр и обоев эти цвета свои, и выбранный акцент до них не доходил.
 * Поэтому вторичный и третичный цвета — те же, что основной.
 */
private fun ColorScheme.accentEverywhere(): ColorScheme = copy(
    secondary = primary,
    onSecondary = onPrimary,
    secondaryContainer = primaryContainer,
    onSecondaryContainer = onPrimaryContainer,
    tertiary = primary,
    onTertiary = onPrimary,
    tertiaryContainer = primaryContainer,
    onTertiaryContainer = onPrimaryContainer,
)

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
