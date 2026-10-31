package dev.chaik.remember.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.chaik.remember.R
import dev.chaik.remember.core.settings.AppFont

private val Base = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
)

/** Начертания, которые встречаются в приложении. */
private val Weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

/**
 * Шрифт из res/font — вариативный (одна толщина на файл не нужна): для каждой толщины
 * та же гарнитура с нужным значением оси «wght».
 */
private fun variableFamily(resource: Int) = FontFamily(
    Weights.map { weight ->
        Font(resource, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

private val Inter = variableFamily(R.font.inter)
private val Nunito = variableFamily(R.font.nunito)
private val Medeo = variableFamily(R.font.medeo)

/** Типографика с выбранным в Персонализации шрифтом; размеры и толщины — как у Material. */
fun typography(font: AppFont): Typography {
    val family = when (font) {
        AppFont.SYSTEM -> return Base
        AppFont.INTER -> Inter
        AppFont.NUNITO -> Nunito
        AppFont.MEDEO -> Medeo
    }
    return with(Base) {
        Typography(
            displayLarge = displayLarge.copy(fontFamily = family),
            displayMedium = displayMedium.copy(fontFamily = family),
            displaySmall = displaySmall.copy(fontFamily = family),
            headlineLarge = headlineLarge.copy(fontFamily = family),
            headlineMedium = headlineMedium.copy(fontFamily = family),
            headlineSmall = headlineSmall.copy(fontFamily = family),
            titleLarge = titleLarge.copy(fontFamily = family),
            titleMedium = titleMedium.copy(fontFamily = family),
            titleSmall = titleSmall.copy(fontFamily = family),
            bodyLarge = bodyLarge.copy(fontFamily = family),
            bodyMedium = bodyMedium.copy(fontFamily = family),
            bodySmall = bodySmall.copy(fontFamily = family),
            labelLarge = labelLarge.copy(fontFamily = family),
            labelMedium = labelMedium.copy(fontFamily = family),
            labelSmall = labelSmall.copy(fontFamily = family),
        )
    }
}

/** Гарнитура для образца в настройках. */
fun fontFamilyOf(font: AppFont): FontFamily = when (font) {
    AppFont.SYSTEM -> FontFamily.Default
    AppFont.INTER -> Inter
    AppFont.NUNITO -> Nunito
    AppFont.MEDEO -> Medeo
}
