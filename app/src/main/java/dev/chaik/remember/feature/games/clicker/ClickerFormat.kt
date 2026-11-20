package dev.chaik.remember.feature.games.clicker

import android.content.res.Resources
import dev.chaik.remember.R
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/**
 * Коины для экрана: до миллиона — целиком с разрядами («123 456»), дальше — «1,23 млн»,
 * «45,6 млрд», «2 трлн». Понятнее, чем «1.2aa».
 */
fun formatCoins(value: Double, res: Resources): String {
    val v = floor(value)
    if (abs(v) < 1_000_000) return NumberFormat.getIntegerInstance(Locale.getDefault()).format(v)
    val units = listOf(R.string.clicker_millions, R.string.clicker_billions, R.string.clicker_trillions, R.string.clicker_quadrillions)
    var scaled = v / 1_000_000
    var unit = 0
    while (abs(scaled) >= 1000 && unit < units.lastIndex) {
        scaled /= 1000
        unit++
    }
    val number = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = when {
            abs(scaled) < 10 -> 2
            abs(scaled) < 100 -> 1
            else -> 0
        }
    }.format(scaled)
    return res.getString(units[unit], number)
}

/** Коины целиком, с разрядами: «4 000 000 000 000» — баланс показываем без сокращений. */
fun formatCoinsFull(value: Double): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(floor(value))
