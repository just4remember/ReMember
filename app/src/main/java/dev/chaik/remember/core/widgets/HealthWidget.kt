package dev.chaik.remember.core.widgets

import kotlin.math.roundToInt
import android.util.TypedValue.COMPLEX_UNIT_DIP
import android.view.View
import android.os.Bundle
import android.graphics.Typeface
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.health.DEFAULT_WEIGHT_KG
import dev.chaik.remember.core.health.DayActivity
import dev.chaik.remember.core.health.HealthAvailability
import dev.chaik.remember.core.health.HealthGoals
import dev.chaik.remember.core.reminders.OpenTarget
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Виджет «Здоровье»: кольца шагов, активности и калорий, как на странице «Здоровье».
 * Раскладка — под точный размер: узкий — только кольца, широкий — ещё и цифры рядом (или внутри колец).
 *
 * Health Connect отдаёт данные в фоне только с отдельным разрешением, поэтому последние
 * прочитанные за сегодня цифры запоминаем: их обновляют и страница «Здоровье», и проверка шагов.
 */
class HealthWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        context.appContainer.appScope.launch {
            try {
                refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) =
        render(context)

    companion object {
        /** Свежие цифры за сегодня прочитаны где-то в приложении — запомнить и показать. */
        fun push(context: Context, activity: DayActivity) {
            prefs(context).edit {
                putLong(KEY_DAY, LocalDate.now().toEpochDay())
                putLong(KEY_STEPS, activity.steps)
                putInt(KEY_MINUTES, activity.activeMinutes)
                putFloat(KEY_CALORIES, activity.calories.toFloat())
            }
            render(context)
        }

        /** Попробовать прочитать сегодняшний день самим; не вышло — показать запомненное. */
        suspend fun refresh(context: Context) {
            val container = context.appContainer
            val health = container.health
            try {
                if (health.availability == HealthAvailability.AVAILABLE && health.hasPermissions()) {
                    val weight = runCatching { container.workoutRepository.lastBodyWeight() }.getOrNull() ?: DEFAULT_WEIGHT_KG
                    push(context, health.day(LocalDate.now(), weight))
                    return
                }
            } catch (e: Exception) {
                // Без разрешения на чтение в фоне Health Connect откажет — это нормально
                Log.i(TAG, "Нет свежих данных для виджета: ${e.message}")
            }
            render(context)
        }

        fun render(context: Context) {
            val ids = Widgets.ids(context, HealthWidget::class.java)
            if (ids.isEmpty()) return
            val manager = AppWidgetManager.getInstance(context)
            val settings = context.appContainer.widgetSettings.settings.value
            val activity = cached(context)
            val goals = context.appContainer.healthGoals.goals.value
            val progress = progress(activity, goals)
            val number = NumberFormat.getIntegerInstance()
            val values = listOf(
                number.format(activity.steps),
                activity.activeMinutes.toString(),
                number.format(activity.calories.roundToLong()),
            )
            // Цели — из раздела «Здоровье»; внутри колец их нет: места мало
            val showGoals = settings.healthGoals && !settings.healthTextInside
            val outsideGoals = listOf(
                context.getString(R.string.health_of_steps, number.format(goals.steps)),
                context.getString(R.string.health_of_minutes, goals.activeMinutes.toString()),
                context.getString(R.string.health_of_calories, number.format(goals.calories)),
            )
            val density = context.resources.displayMetrics.density
            val click = Widgets.open(context, OpenTarget.Health, REQUEST_HEALTH)

            ids.forEach { id ->
                val size = Widgets.size(manager, id, SizeF(250f, 110f))
                val padding = if (minOf(size.width, size.height) < 100f) 8f else 14f
                val innerWidth = size.width - padding * 2
                val innerHeight = size.height - padding * 2
                val rows = (0..2).map { i ->
                    Row(values[i], outsideGoals[i].takeIf { showGoals }, RING_COLORS[i])
                }
                // Цифры рядом: кольца не уже 42% виджета, текст — по оставшемуся месту.
                // Если так цифры выходят совсем мелкими (виджет почти квадратный) — одни кольца
                val widest = rows.maxOf { it.width(1f) }
                // Кольца побольше, пока цифрам остаётся место на размер ~16 dp
                val sideRing = minOf(maxOf(innerWidth - TEXT_GAP - widest * 16f, innerWidth * 0.42f), innerHeight)
                val valueSize = minOf(20f, (innerWidth - sideRing - TEXT_GAP) / widest, innerHeight / (3 * 1.35f))
                val outside = !settings.healthTextInside && innerWidth >= innerHeight * 1.2f && valueSize >= MIN_TEXT
                val ring = if (outside) sideRing else minOf(innerWidth, innerHeight)
                val ringPx = (ring * density).toInt().coerceIn(MIN_RING_PX, MAX_RING_PX)
                val inside = if (settings.healthTextInside) {
                    (0..2).map { i -> Row(values[i], null, RING_COLORS[i]) }
                } else {
                    null
                }
                val views = RemoteViews(context.packageName, R.layout.widget_health).apply {
                    setColorStateList(R.id.widget_bg, "setImageTintList", Widgets.cardColor(settings.healthCardColor))
                    setImageViewBitmap(R.id.health_rings, ringsBitmap(progress, ringPx, inside))
                    setViewLayoutWidth(R.id.health_rings, ring, COMPLEX_UNIT_DIP)
                    setViewLayoutHeight(R.id.health_rings, ring, COMPLEX_UNIT_DIP)
                    setViewVisibility(R.id.health_text, if (outside) View.VISIBLE else View.GONE)
                    setViewLayoutMargin(R.id.health_text, RemoteViews.MARGIN_START, TEXT_GAP, COMPLEX_UNIT_DIP)
                    if (outside) {
                        listOf(R.id.health_row1, R.id.health_row2, R.id.health_row3).forEachIndexed { i, view ->
                            setTextViewText(view, rows[i].text(valueSize))
                        }
                    }
                    setOnClickPendingIntent(R.id.widget_root, click)
                }
                manager.updateAppWidget(id, views)
            }
        }

        /** Запомненное за сегодня; со вчера — нули: новый день начинается с пустых колец. */
        private fun cached(context: Context): DayActivity {
            val prefs = prefs(context)
            if (prefs.getLong(KEY_DAY, -1) != LocalDate.now().toEpochDay()) return DayActivity()
            return DayActivity(
                steps = prefs.getLong(KEY_STEPS, 0),
                activeMinutes = prefs.getInt(KEY_MINUTES, 0),
                calories = prefs.getFloat(KEY_CALORIES, 0f).toDouble(),
            )
        }

        private fun progress(activity: DayActivity, goals: HealthGoals) = listOf(
            activity.steps.toFloat() / goals.steps.coerceAtLeast(1),
            activity.activeMinutes.toFloat() / goals.activeMinutes.coerceAtLeast(1),
            (activity.calories / goals.calories.coerceAtLeast(1)).toFloat(),
        )

        /**
         * Строка «6 240 из 6 000»: значение жирно и цветом кольца, цель — мельче и бледнее.
         * Цели может не быть — её выключили в настройках.
         */
        private class Row(val value: String, val goal: String?, val color: Int) {
            private val goalText get() = goal?.let { "  $it" }.orEmpty()

            /** Ширина строки при значении размером [size] (в тех же единицах). */
            fun width(size: Float): Float {
                val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 100f; typeface = Typeface.DEFAULT_BOLD }
                val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 100f * GOAL_SCALE }
                return (bold.measureText(value) + regular.measureText(goalText)) * size / 100f
            }

            fun text(sizeDp: Float): CharSequence = SpannableStringBuilder().apply {
                append(value)
                setSpan(ForegroundColorSpan(color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(AbsoluteSizeSpan(sizeDp.roundToInt(), true), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val start = length
                append(goalText)
                setSpan(AbsoluteSizeSpan((sizeDp * GOAL_SCALE).roundToInt(), true), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            /** Нарисовать строку по центру [cx] на базовой линии [baseline]; [size] — размер значения в px. */
            fun draw(canvas: Canvas, cx: Float, baseline: Float, size: Float) {
                val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = Typeface.DEFAULT_BOLD; this.color = this@Row.color }
                val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size * GOAL_SCALE; this.color = 0x99FFFFFF.toInt() }
                val valueWidth = bold.measureText(value)
                val goalWidth = goal?.let { regular.measureText(" $it") } ?: 0f
                var x = cx - (valueWidth + goalWidth) / 2
                canvas.drawText(value, x, baseline, bold)
                x += valueWidth
                goal?.let { canvas.drawText(" $it", x, baseline, regular) }
            }

            fun drawWidth(size: Float): Float {
                val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = Typeface.DEFAULT_BOLD }
                val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size * GOAL_SCALE }
                return bold.measureText(value) + (goal?.let { regular.measureText(" $it") } ?: 0f)
            }
        }

        /**
         * Кольца картинкой — у виджетов нет своего рисования. Как на странице «Здоровье»:
         * дуга от тёмного хвоста к яркой голове, сверх цели — второй круг поверх, под головой тень.
         * [inside] — цифры в середине колец.
         */
        private fun ringsBitmap(progress: List<Float>, px: Int, inside: List<Row>?): Bitmap {
            val bitmap = createBitmap(px, px)
            val canvas = Canvas(bitmap)
            val width = px * 0.088f
            val step = width + px * 0.024f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = width }
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            RING_COLORS.forEachIndexed { i, color ->
                val inset = width / 2 + step * i
                val rect = RectF(inset, inset, px - inset, px - inset)
                val value = progress.getOrElse(i) { 0f }.coerceIn(0f, 20f)
                val cx = rect.centerX()
                val cy = rect.centerY()
                val radius = rect.width() / 2
                fun point(degrees: Float): Pair<Float, Float> {
                    val r = Math.toRadians(degrees.toDouble())
                    return (cx + radius * cos(r)).toFloat() to (cy + radius * sin(r)).toFloat()
                }
                if (value < 1f) {
                    paint.shader = null
                    paint.color = ColorUtils.setAlphaComponent(color, 0x33)
                    canvas.drawOval(rect, paint)
                }
                if (value <= 0f) return@forEachIndexed
                val overflow = value > 1f
                val head = -90f + 360f * (if (overflow) value % 1f else value)
                val tail = ColorUtils.blendARGB(color, Color.BLACK, 0.25f * value.coerceAtMost(1f))
                val start = if (overflow) head else -90f
                // Прозрачность кисти умножается на градиент: после бледной дорожки — снова непрозрачная
                paint.color = Color.BLACK
                val sweep = if (overflow) 360f else 360f * value
                paint.shader = SweepGradient(cx, cy, intArrayOf(tail, color), floatArrayOf(0f, sweep / 360f)).apply {
                    setLocalMatrix(Matrix().apply { setRotate(start, cx, cy) })
                }
                canvas.drawArc(rect, start, sweep, false, paint)
                if (!overflow) {
                    fill.color = tail
                    val (x, y) = point(-90f)
                    canvas.drawCircle(x, y, width / 2, fill)
                }
                val shift = Math.toDegrees((width * 0.18f / radius).toDouble()).toFloat()
                fill.color = Color.argb(89, 0, 0, 0)
                point(head + shift).let { (x, y) -> canvas.drawCircle(x, y, width / 2, fill) }
                fill.color = Color.argb(51, 0, 0, 0)
                point(head + shift * 2).let { (x, y) -> canvas.drawCircle(x, y, width / 2, fill) }
                fill.color = color
                point(head).let { (x, y) -> canvas.drawCircle(x, y, width / 2, fill) }
            }
            if (inside != null) {
                // Свободный круг внутри последнего кольца; три строки — по размеру, чтобы влезли
                val hole = px / 2f - (width / 2 + step * 2) - width / 2 - px * 0.02f
                val widest = inside.maxOf { it.drawWidth(100f) } / 100f
                val size = minOf(hole * 1.6f / widest, hole * 1.5f / (3 * LINE)).coerceAtLeast(1f)
                val top = px / 2f - LINE * size * 1.5f
                inside.forEachIndexed { i, row ->
                    row.draw(canvas, px / 2f, top + LINE * size * i + size * 0.95f, size)
                }
            }
            return bitmap
        }

        private fun prefs(context: Context) = context.getSharedPreferences("health_widget", Context.MODE_PRIVATE)

        /** Цвета колец — те же, что на странице «Здоровье»: шаги, активность, калории. */
        private val RING_COLORS = listOf(0xFF30D158.toInt(), 0xFF0A84FF.toInt(), 0xFFBF5AF2.toInt())

        /** Цель мельче значения во столько раз. */
        private const val GOAL_SCALE = 0.62f

        /** Высота строки текста внутри колец — в размерах шрифта. */
        private const val LINE = 1.2f

        /** Отступ цифр от колец, dp. */
        private const val TEXT_GAP = 14f

        /** Мельче этого цифры рядом с кольцами не пишем, dp. */
        private const val MIN_TEXT = 10f
        private const val MIN_RING_PX = 120
        private const val MAX_RING_PX = 720
        private const val REQUEST_HEALTH = 7203
        private const val KEY_DAY = "day"
        private const val KEY_STEPS = "steps"
        private const val KEY_MINUTES = "minutes"
        private const val KEY_CALORIES = "calories"
        private const val TAG = "HealthWidget"
    }
}
