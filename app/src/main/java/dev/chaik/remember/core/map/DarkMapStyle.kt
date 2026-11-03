package dev.chaik.remember.core.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Тёмная карта из цветной: те же цвета, но светлота перевёрнута — светлый фон становится тёмным,
 * тёмные подписи светлыми, а парки, вода и дороги сохраняют свой оттенок. Источники и шрифты
 * те же, поэтому скачанный город работает в обеих темах без второго скачивания.
 */
object DarkMapStyle {
    /** Тёмный вариант стиля [json]: перекрашиваются только цвета в `paint` слоёв. */
    fun darken(json: String): String {
        val style = Json.parseToJsonElement(json).jsonObject
        val layers = style["layers"]?.jsonArray ?: return json
        val dark = layers.map { layer ->
            val obj = layer.jsonObject
            val paint = obj["paint"]?.jsonObject ?: return@map layer
            var newPaint = JsonObject(paint.mapValues { (_, value) -> recolor(value) })
            // Узор — готовая светлая картинка, перекрасить её нельзя: на тёмном она светилась бы
            // полосками (пешеходные площади вроде Красной). Вместо узора — ровная заливка
            (paint["fill-pattern"] as? JsonPrimitive)?.content?.let { pattern ->
                newPaint = JsonObject(newPaint - "fill-pattern" + ("fill-color" to JsonPrimitive(PatternColors[pattern] ?: PATTERN_COLOR)))
            }
            // Рельеф мелкого масштаба — светлая картинка; приглушаем, иначе он светился бы на тёмном
            if (obj["type"]?.jsonPrimitive?.content == "raster") {
                newPaint = JsonObject(newPaint + ("raster-brightness-max" to JsonPrimitive(RASTER_BRIGHTNESS)))
            }
            JsonObject(obj + ("paint" to newPaint))
        }
        return JsonObject(style + ("layers" to JsonArray(dark))).toString()
    }

    private fun recolor(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map(::recolor))
        is JsonObject -> JsonObject(element.mapValues { (_, value) -> recolor(value) })
        is JsonPrimitive -> if (element.isString) darkColor(element.content)?.let(::JsonPrimitive) ?: element else element
        else -> element
    }

    /** Цвет в тёмной теме или null, если строка — не цвет. */
    fun darkColor(color: String): String? {
        val (h, s, l, a) = parse(color.trim()) ?: return null
        val light = DARKEST + (1 - l) * (BRIGHTEST - DARKEST)
        // Почти бесцветное (фон, дома, белые дороги) — в холодный серый под тёмную тему приложения:
        // тёплый бежевый фон карты в тёмном превращался в бурый
        val chroma = (1 - abs(2 * l - 1)) * s
        val neutral = chroma < NEUTRAL_CHROMA
        return "hsla(%d, %d%%, %d%%, %s)".format(
            if (neutral) NEUTRAL_HUE else h.roundToInt(),
            if (neutral) NEUTRAL_SATURATION else (s * SATURATION * 100).roundToInt(),
            (light * 100).roundToInt(),
            alpha(a),
        )
    }

    private data class Hsla(val h: Float, val s: Float, val l: Float, val a: Float)

    private val Hex = Regex("""#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})""")
    private val Rgb = Regex("""rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)""")
    private val Hsl = Regex("""hsla?\(\s*([\d.]+)\s*,\s*([\d.]+)%\s*,\s*([\d.]+)%\s*(?:,\s*([\d.]+)\s*)?\)""")

    private fun parse(color: String): Hsla? {
        Hex.matchEntire(color)?.let { match ->
            val hex = match.groupValues[1].let { if (it.length == 3) it.map { c -> "$c$c" }.joinToString("") else it }
            val channels = hex.chunked(2).map { it.toInt(16) / 255f }
            return fromRgb(channels[0], channels[1], channels[2], channels.getOrElse(3) { 1f })
        }
        Rgb.matchEntire(color)?.let { match ->
            val (r, g, b) = match.groupValues.drop(1).take(3).map { it.toFloat() / 255f }
            return fromRgb(r, g, b, match.groupValues[4].toFloatOrNull() ?: 1f)
        }
        Hsl.matchEntire(color)?.let { match ->
            val v = match.groupValues
            return Hsla(v[1].toFloat(), v[2].toFloat() / 100, v[3].toFloat() / 100, v[4].toFloatOrNull() ?: 1f)
        }
        return null
    }

    private fun fromRgb(r: Float, g: Float, b: Float, a: Float): Hsla {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2
        val d = max - min
        if (d == 0f) return Hsla(0f, 0f, l, a)
        val s = d / (1 - abs(2 * l - 1))
        val h = when (max) {
            r -> 60 * (((g - b) / d) % 6)
            g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }
        return Hsla((h + 360) % 360, s, l, a)
    }

    private fun alpha(a: Float): String = if (a >= 1f) "1" else "%.2f".format(java.util.Locale.ROOT, a)

    /** Самый светлый цвет становится таким тёмным… */
    private const val DARKEST = 0.11f

    /** …а самый тёмный — таким светлым. */
    private const val BRIGHTEST = 0.86f

    /** На тёмном насыщенные цвета режут глаз — чуть приглушаем. */
    private const val SATURATION = 0.8f

    private const val RASTER_BRIGHTNESS = 0.25f

    /** Пешеходная площадь — чуть светлее фона, как площадь среди кварталов. */
    private const val PATTERN_COLOR = "hsla(220, 12%, 19%, 1)"

    /** Узоры, которым нужен свой цвет: болото — тёмно-зелёное, как лес. */
    private val PatternColors = mapOf("wetland_bg_11" to "hsla(150, 25%, 20%, 1)")

    /** Насколько цвет должен отличаться от серого, чтобы сохранить свой оттенок. */
    private const val NEUTRAL_CHROMA = 0.08f
    private const val NEUTRAL_HUE = 220
    private const val NEUTRAL_SATURATION = 15
}
