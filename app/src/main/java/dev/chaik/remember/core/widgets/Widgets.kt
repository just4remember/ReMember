package dev.chaik.remember.core.widgets

import java.util.Locale
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import dev.chaik.remember.core.settings.AppLanguage
import android.content.res.Resources
import android.content.res.Configuration
import android.content.BroadcastReceiver
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue.COMPLEX_UNIT_DIP
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import dev.chaik.remember.MainActivity
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.reminders.OpenTarget
import dev.chaik.remember.core.settings.AccentColor
import dev.chaik.remember.core.settings.logicalToday
import dev.chaik.remember.core.ui.components.streakColor
import dev.chaik.remember.core.data.DayStreak
import dev.chaik.remember.feature.time.world.WorldCities
import dev.chaik.remember.feature.time.world.WorldCity
import java.time.LocalDate

/**
 * Виджеты на рабочий стол. Всё, что тикает (цифры, стрелки), считает сам Android —
 * приложение их только раскладывает и раскрашивает: обновлять нужно, лишь когда сменились
 * настройки, данные или размер виджета. Каждый виджет рисуется под свой точный размер.
 */
object Widgets {
    /** Язык подписей виджетов по настройке; пусто у «как в приложении» — значит язык телефона. */
    fun locale(context: Context): Locale {
        val setting = context.appContainer.widgetSettings.settings.value.language
        val tag = when (setting) {
            WidgetLanguage.APP -> AppLanguage.current(context)
            WidgetLanguage.SYSTEM -> ""
            else -> setting.tag
        }
        return if (tag.isEmpty()) Resources.getSystem().configuration.locales[0] else Locale.forLanguageTag(tag)
    }

    /** Контекст на языке виджетов: строки ресурсов («из 6 000», «мин») берутся из него. */
    fun localized(context: Context): Context {
        val config = Configuration(context.resources.configuration).apply { setLocale(locale(context)) }
        return context.createConfigurationContext(config)
    }

    /** Перерисовать все виджеты: сменились настройки, акцент или шрифт приложения. */
    fun updateAll(context: Context) {
        DigitalClockWidget.render(context)
        AnalogClockWidget.render(context)
        HealthWidget.render(context)
    }

    /** Только часы — у них огонёк серии. */
    fun updateClocks(context: Context) {
        DigitalClockWidget.render(context)
        AnalogClockWidget.render(context)
    }

    /**
     * Акцент приложения в светлом тоне — как основной цвет тёмной темы: на тёмной подложке
     * и на обоях он читается. Акцент «из обоев» — системный тон Material You.
     */
    fun accent(context: Context): Int {
        val settings = context.appContainer.settings.settings.value
        val seed = when (settings.accent) {
            AccentColor.CUSTOM -> settings.customAccent
            else -> settings.accent.argb
        } ?: return context.getColor(android.R.color.system_accent1_200)
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(seed.toInt(), it) }
        hsl[2] = 0.78f
        return ColorUtils.HSLToColor(hsl)
    }

    /** Цвет подложки: выбранный, чуть прозрачный — как у системных виджетов. */
    fun cardColor(argb: Long): ColorStateList = ColorStateList.valueOf(((argb and 0xFFFFFF) or 0xF2000000).toInt())

    internal fun ids(context: Context, provider: Class<out AppWidgetProvider>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider))

    /** Размер виджета в dp, как его показывает рабочий стол; только что поставленный — [fallback]. */
    internal fun size(manager: AppWidgetManager, id: Int, fallback: SizeF): SizeF {
        val options: Bundle = manager.getAppWidgetOptions(id)
        options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            ?.firstOrNull()?.takeIf { it.width > 0 && it.height > 0 }?.let { return it }
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return if (width > 0 && height > 0) SizeF(width.toFloat(), height.toFloat()) else fallback
    }

    /** Тап по виджету — открыть нужный раздел приложения. */
    internal fun open(context: Context, target: OpenTarget, request: Int): PendingIntent = PendingIntent.getActivity(
        context,
        request,
        OpenTarget.put(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            target,
        ),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Что писать под часами: первая строка — дата («четверг, 5 ноября»), вторая, мельче — город
     * часов (если не местное время) и огонёк серии. Размер текста подобран под [maxWidth] (dp),
     * чтобы строки не обрезались; дата всегда крупнее второй строки.
     */
    internal class DateRowSpec(
        val date: String?,
        val place: String?,
        val streak: Boolean,
        val dateSp: Float,
        val smallSp: Float,
    ) {
        val secondLine: Boolean get() = place != null || streak
        val visible: Boolean get() = date != null || secondLine

        /** Высота обеих строк, dp — столько места часы оставляют под ними. */
        val height: Float get() = (if (date != null) dateSp * LINE else 0f) + (if (secondLine) smallSp * LINE else 0f)

        private companion object {
            const val LINE = 1.35f
        }
    }

    internal fun dateRowSpec(
        context: Context,
        date: Boolean,
        streakOn: Boolean,
        sp: Float,
        maxWidth: Float,
        place: String? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DateRowSpec {
        // Дата на выбранном языке по поясу часов. В полночь виджеты перерисуются сами — см. WidgetMidnight
        val dateText = if (date) LocalDate.now(zone).format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale(context))) else null
        if (date) WidgetMidnight.schedule(context)
        val dateSp = dateText?.let { fitSp(it, MEDIUM, maxWidth, sp) } ?: sp
        // Вторая строка: город и «🔥 000» (значок шириной в символ с небольшим запасом)
        val second = listOfNotNull(place, "MM000".takeIf { streakOn }).joinToString("  ")
        val small = minOf(sp * SMALL, dateSp * SMALL)
        val smallSp = if (second.isEmpty()) small else fitSp(second, REGULAR, maxWidth, small)
        return DateRowSpec(dateText, place, streakOn, dateSp, smallSp)
    }

    /** Две строки под часами по [spec]. [plain] — на подложке: без тени (тень на лету не убрать, поэтому два макета). */
    internal fun dateRow(context: Context, spec: DateRowSpec, plain: Boolean): RemoteViews {
        val layout = if (plain) R.layout.widget_date_plain else R.layout.widget_date
        return RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.clock_date, spec.date.orEmpty())
            setTextViewTextSize(R.id.clock_date, COMPLEX_UNIT_DIP, spec.dateSp)
            setViewVisibility(R.id.clock_date, if (spec.date != null) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.clock_line2, if (spec.secondLine) View.VISIBLE else View.GONE)
            setTextViewText(R.id.clock_place, spec.place.orEmpty())
            setTextViewTextSize(R.id.clock_place, COMPLEX_UNIT_DIP, spec.smallSp)
            setViewVisibility(R.id.clock_place, if (spec.place != null) View.VISIBLE else View.GONE)
            val streakVisibility = if (spec.streak) View.VISIBLE else View.GONE
            setViewVisibility(R.id.streak_icon, streakVisibility)
            setViewVisibility(R.id.streak_count, streakVisibility)
            if (spec.streak) {
                val days = WidgetStreak.current(context)
                // Цвет огонька растёт вместе с серией, как в приложении; нет серии — бледный
                val color = streakColor(days)?.toArgb() ?: 0x99FFFFFF.toInt()
                setColorStateList(R.id.streak_icon, "setImageTintList", ColorStateList.valueOf(color))
                setTextViewText(R.id.streak_count, days.toString())
                setTextColor(R.id.streak_count, color)
                setTextViewTextSize(R.id.streak_count, COMPLEX_UNIT_DIP, spec.smallSp)
                setViewLayoutWidth(R.id.streak_icon, spec.smallSp * 1.15f, COMPLEX_UNIT_DIP)
                setViewLayoutHeight(R.id.streak_icon, spec.smallSp * 1.15f, COMPLEX_UNIT_DIP)
                if (spec.place == null) setViewLayoutMargin(R.id.streak_icon, RemoteViews.MARGIN_START, 0f, COMPLEX_UNIT_DIP)
            }
        }
    }

    private val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    /** Вторая строка мельче даты. */
    private const val SMALL = 0.8f

    /** Наибольший размер не крупнее [sp], при котором [text] помещается в [maxWidth] dp; не меньше 8. */
    private fun fitSp(text: String, typeface: Typeface, maxWidth: Float, sp: Float): Float {
        val width = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 100f
            this.typeface = typeface
        }.measureText(text)
        if (width <= 0f) return sp
        return minOf(sp, maxWidth * 0.96f * 100f / width).coerceAtLeast(8f)
    }

    /** Отступ подложки цифровых часов от содержимого, dp: по бокам и сверху-снизу. */
    internal const val CARD_PADDING_H = 14f
    internal const val CARD_PADDING_V = 10f

    /** Подложка цифровых часов на весь виджет: фон блока с часами, отступ от краёв — [CARD_PADDING_H] и [CARD_PADDING_V]. */
    internal fun card(context: Context, views: RemoteViews, on: Boolean, color: Long) {
        val px = context.resources.displayMetrics.density
        if (on) {
            views.setInt(R.id.widget_card, "setBackgroundResource", R.drawable.widget_card_bg)
            views.setColorStateList(R.id.widget_card, "setBackgroundTintList", cardColor(color))
            views.setViewPadding(
                R.id.widget_card,
                (CARD_PADDING_H * px).toInt(), (CARD_PADDING_V * px).toInt(),
                (CARD_PADDING_H * px).toInt(), (CARD_PADDING_V * px).toInt(),
            )
        } else {
            views.setInt(R.id.widget_card, "setBackgroundResource", 0)
            views.setViewPadding(R.id.widget_card, 0, 0, 0, 0)
        }
    }

    /** Отступ между часами и строками даты, dp: заметный, но меньше на низких виджетах. */
    internal fun dateGap(height: Float): Float = (height * 0.08f).coerceIn(6f, 16f)

    /** Название города на языке виджетов; null — местное время. */
    internal fun cityName(context: Context, city: WorldCity?): String? =
        city?.name(locale(context).language == "ru")

    /** Ширина «00:00» и высота строки цифр при размере 100 dp — чтобы подобрать размер под виджет. */
    internal fun measureDigits(weight: ClockWeight): DigitsSize {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 100f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            fontVariationSettings = "'wght' ${weight.weight}"
            fontFeatureSettings = "tnum"
        }
        val metrics = paint.fontMetrics
        val glyphs = android.graphics.Rect().also { paint.getTextBounds("0123456789:", 0, 11, it) }
        // Строка шрифта выше самих цифр: сверху место под высокие буквы, снизу — под хвосты.
        // Эти поля срезаются отрицательными отступами (с небольшим запасом), иначе часы
        // сидят посередине с пустотой сверху и снизу
        val safety = (glyphs.bottom - glyphs.top) * 0.06f
        val trimTop = (glyphs.top - metrics.ascent - safety).coerceAtLeast(0f)
        val trimBottom = (metrics.descent - glyphs.bottom - safety).coerceAtLeast(0f)
        return DigitsSize(
            width = paint.measureText("00:00"),
            height = metrics.descent - metrics.ascent - trimTop - trimBottom,
            trimTop = trimTop,
            trimBottom = trimBottom,
        )
    }

    /** Цифры «00:00» при размере 100: ширина, видимая высота и срезаемые поля строки сверху и снизу. */
    internal class DigitsSize(val width: Float, val height: Float, val trimTop: Float, val trimBottom: Float) {
        /** Срезать пустые поля строки у цифр размера [sp]. */
        fun trim(views: RemoteViews, sp: Float) {
            views.setViewLayoutMargin(R.id.clock_digits, RemoteViews.MARGIN_TOP, -trimTop * sp / 100f, COMPLEX_UNIT_DIP)
            views.setViewLayoutMargin(R.id.clock_digits, RemoteViews.MARGIN_BOTTOM, -trimBottom * sp / 100f, COMPLEX_UNIT_DIP)
        }
    }
}

/**
 * Серия дней с записью для огонька. Базу из виджета не открываем (она зашифрована и
 * открывается долго): приложение запоминает длину серии и до какого дня она доживёт без записей.
 */
object WidgetStreak {
    /** Даты с записью или правила серии изменились — пересчитать; изменилась серия — перерисовать часы. */
    fun update(context: Context, written: Set<LocalDate>) {
        val settings = context.appContainer.settings.settings.value
        val today = settings.logicalToday()
        val rules = settings.streakRules
        val length = DayStreak.length(written, today, rules)
        val alive = DayStreak.aliveThrough(written, today, rules)?.toEpochDay() ?: Long.MIN_VALUE
        val prefs = prefs(context)
        if (prefs.getLong(KEY_ALIVE, Long.MIN_VALUE) == alive && prefs.getInt(KEY_LENGTH, 0) == length) return
        prefs.edit {
            remove("end") // раньше хранили последний день серии
            putLong(KEY_ALIVE, alive)
            putInt(KEY_LENGTH, length)
        }
        Widgets.updateClocks(context)
    }

    /** Серия на сегодня: держится, пока не прошёл последний день, до которого она доживает. */
    fun current(context: Context): Int {
        val prefs = prefs(context)
        val alive = prefs.getLong(KEY_ALIVE, Long.MIN_VALUE)
        val today = context.appContainer.settings.settings.value.logicalToday().toEpochDay()
        return if (alive >= today) prefs.getInt(KEY_LENGTH, 0) else 0
    }

    private fun prefs(context: Context) = context.getSharedPreferences("widget_streak", Context.MODE_PRIVATE)
    private const val KEY_ALIVE = "alive"
    private const val KEY_LENGTH = "length"
}

/** Цифровые часы: крупные цифры шрифтом из настроек, под ними дата и огонёк; подложка — по настройке. */
class DigitalClockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = render(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) =
        render(context)

    companion object {
        fun render(context: Context) {
            val ids = Widgets.ids(context, DigitalClockWidget::class.java)
            if (ids.isEmpty()) return
            val manager = AppWidgetManager.getInstance(context)
            val settings = context.appContainer.widgetSettings.settings.value
            val measured = Widgets.measureDigits(settings.digitalWeight)
            val digitsWidth = measured.width
            val digitsHeight = measured.height
            val click = Widgets.open(context, OpenTarget.WorldClock, REQUEST_DIGITAL)
            val digitalStreak = settings.digitalStreak && context.appContainer.settings.settings.value.streak
            val mainCity = settings.digitalCity?.let(WorldCities::find)
            val extras = settings.digitalCities.mapNotNull(WorldCities::find).filter { it.id != mainCity?.id }.take(MAX_EXTRA_CITIES)
            ids.forEach { id ->
                val size = Widgets.size(manager, id, SizeF(250f, 110f))
                // Есть другие города и виджет от 3×2 — таблица; маленькому — только основное время
                if (extras.isNotEmpty() && size.width >= GRID_MIN_WIDTH && size.height >= GRID_MIN_HEIGHT) {
                    manager.updateAppWidget(id, grid(context, settings, size, listOf(mainCity) + extras, digitalStreak, click))
                    return@forEach
                }
                val padding = if (settings.digitalCard) Widgets.CARD_PADDING_H else 4f
                val paddingV = if (settings.digitalCard) Widgets.CARD_PADDING_V else 4f
                val row = Widgets.dateRowSpec(
                    context, settings.digitalDate, digitalStreak,
                    sp = (size.height * 0.12f).coerceIn(10f, 16f),
                    maxWidth = size.width - padding * 2,
                    place = Widgets.cityName(context, mainCity),
                    zone = mainCity?.zoneId ?: ZoneId.systemDefault(),
                )
                // Цифры уменьшаются, чтобы под ними поместились отступ и строки даты и города
                val gap = Widgets.dateGap(size.height)
                val rowHeight = if (row.visible) row.height + gap else 0f
                // Цифры — во всю ширину или высоту, что раньше упрётся, и уменьшены по настройке
                val digits = (
                    minOf(
                        (size.width - padding * 2) * 0.94f * 100f / digitsWidth,
                        (size.height - paddingV * 2 - rowHeight) * 100f / digitsHeight,
                    ) * settings.digitalSize.coerceIn(DIGITS_SIZE_MIN, 100) / 100f
                    ).coerceAtLeast(12f)
                val views = RemoteViews(context.packageName, R.layout.widget_clock_digital).apply {
                    Widgets.card(context, this, settings.digitalCard, settings.digitalCardColor)
                    removeAllViews(R.id.digits_slot)
                    addView(
                        R.id.digits_slot,
                        RemoteViews(
                            context.packageName,
                            if (settings.digitalCard) settings.digitalWeight.plainLayout else settings.digitalWeight.layout,
                        ).apply {
                            setTextViewTextSize(R.id.clock_digits, COMPLEX_UNIT_DIP, digits)
                            measured.trim(this, digits)
                            mainCity?.let { setString(R.id.clock_digits, "setTimeZone", it.zone) }
                        },
                    )
                    removeAllViews(R.id.date_slot)
                    if (row.visible) {
                        addView(R.id.date_slot, Widgets.dateRow(context, row, plain = settings.digitalCard))
                        setViewLayoutMargin(R.id.date_slot, RemoteViews.MARGIN_TOP, gap, COMPLEX_UNIT_DIP)
                    }
                    setOnClickPendingIntent(R.id.widget_root, click)
                }
                manager.updateAppWidget(id, views)
            }
        }

        private const val REQUEST_DIGITAL = 7201

        /** С какого размера виджета (dp) города встают таблицей: примерно 3×2 ячейки рабочего стола. */
        private const val GRID_MIN_WIDTH = 200f
        private const val GRID_MIN_HEIGHT = 140f

        /** Таблица 1×2 или 2×2: в ячейке цифры по поясу города и его название. Первая — основное время. */
        private fun grid(
            context: Context,
            settings: WidgetSettings,
            size: SizeF,
            cities: List<WorldCity?>,
            streak: Boolean,
            click: PendingIntent,
        ): RemoteViews {
            val measured = Widgets.measureDigits(settings.digitalWeight)
            val digitsWidth = measured.width
            val digitsHeight = measured.height
            val padding = 4f + if (settings.digitalCard) Widgets.CARD_PADDING_H else 0f
            val rows = if (cities.size > 2) 2 else 1
            val main = cities.first()
            // Города подписаны в ячейках — под таблицей только дата и огонёк
            val row = Widgets.dateRowSpec(
                context, settings.digitalDate, streak,
                sp = (size.height * 0.08f).coerceIn(10f, 14f),
                maxWidth = size.width - padding * 2,
                zone = main?.zoneId ?: ZoneId.systemDefault(),
            )
            val gap = Widgets.dateGap(size.height)
            val rowHeight = if (row.visible) row.height + gap else 0f
            val labelSp = (size.height / rows * 0.11f).coerceIn(10f, 14f)
            val cellWidth = (size.width - padding * 2) / 2
            val cellHeight = (size.height - padding * 2 - rowHeight) / rows - labelSp * 1.4f
            val digits = (
                minOf(cellWidth * 0.9f * 100f / digitsWidth, cellHeight * 100f / digitsHeight) *
                    settings.digitalSize.coerceIn(DIGITS_SIZE_MIN, 100) / 100f
                ).coerceAtLeast(10f)
            val local = Widgets.localized(context).getString(R.string.widget_local_time)
            val cells = listOf(
                Triple(R.id.world_cell1, R.id.world_digits1, R.id.world_label1),
                Triple(R.id.world_cell2, R.id.world_digits2, R.id.world_label2),
                Triple(R.id.world_cell3, R.id.world_digits3, R.id.world_label3),
                Triple(R.id.world_cell4, R.id.world_digits4, R.id.world_label4),
            )
            return RemoteViews(context.packageName, R.layout.widget_clock_world).apply {
                Widgets.card(context, this, settings.digitalCard, settings.digitalCardColor)
                setViewVisibility(R.id.world_row2, if (rows == 2) View.VISIBLE else View.GONE)
                cells.forEachIndexed { i, (cell, slot, label) ->
                    if (i >= cities.size) {
                        setViewVisibility(cell, View.GONE)
                        return@forEachIndexed
                    }
                    val city = cities[i]
                    setViewVisibility(cell, View.VISIBLE)
                    removeAllViews(slot)
                    addView(
                        slot,
                        RemoteViews(
                            context.packageName,
                            if (settings.digitalCard) settings.digitalWeight.plainLayout else settings.digitalWeight.layout,
                        ).apply {
                            setTextViewTextSize(R.id.clock_digits, COMPLEX_UNIT_DIP, digits)
                            measured.trim(this, digits)
                            city?.let { setString(R.id.clock_digits, "setTimeZone", it.zone) }
                        },
                    )
                    setTextViewText(label, Widgets.cityName(context, city) ?: local)
                    setTextViewTextSize(label, COMPLEX_UNIT_DIP, labelSp)
                }
                removeAllViews(R.id.date_slot)
                if (row.visible) {
                    addView(R.id.date_slot, Widgets.dateRow(context, row, plain = settings.digitalCard))
                    setViewLayoutMargin(R.id.date_slot, RemoteViews.MARGIN_TOP, gap, COMPLEX_UNIT_DIP)
                }
                setOnClickPendingIntent(R.id.widget_root, click)
            }
        }
    }
}

/** Часы со стрелками: свой циферблат; главные риски и секундная стрелка — в акценте или своём цвете. */
class AnalogClockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = render(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) =
        render(context)

    companion object {
        fun render(context: Context) {
            val ids = Widgets.ids(context, AnalogClockWidget::class.java)
            if (ids.isEmpty()) return
            val manager = AppWidgetManager.getInstance(context)
            val settings = context.appContainer.widgetSettings.settings.value
            val color = ColorStateList.valueOf(settings.analogColor?.toInt() ?: Widgets.accent(context))
            val click = Widgets.open(context, OpenTarget.WorldClock, REQUEST_ANALOG)
            val analogStreak = settings.analogStreak && context.appContainer.settings.settings.value.streak
            val city = settings.analogCity?.let(WorldCities::find)
            ids.forEach { id ->
                val size = Widgets.size(manager, id, SizeF(110f, 110f))
                // Часы не местные — во второй строке мелко подписано, чьё это время. Циферблат
                // занимает остаток высоты и сам уменьшается под строки
                val row = Widgets.dateRowSpec(
                    context, settings.analogDate, analogStreak,
                    sp = (minOf(size.width, size.height) * 0.08f).coerceIn(10f, 15f),
                    maxWidth = size.width - 8f,
                    place = Widgets.cityName(context, city),
                    zone = city?.zoneId ?: ZoneId.systemDefault(),
                )
                // Без секундной стрелки — свой циферблат: у виджета стрелку не убрать на лету
                val dial = RemoteViews(
                    context.packageName,
                    if (settings.analogSeconds) R.layout.widget_analog_dial_seconds else R.layout.widget_analog_dial,
                ).apply {
                    setColorStateList(R.id.clock_major, "setImageTintList", color)
                    // Тема: круг, риски и часовая с минутной стрелкой
                    val ink = listOf(
                        R.id.clock_ticks to "setImageTintList",
                        R.id.clock_analog to "setHourHandTintList",
                        R.id.clock_analog to "setMinuteHandTintList",
                    )
                    when (settings.analogTheme) {
                        DialTheme.SYSTEM -> {
                            setColorStateList(R.id.clock_face, "setImageTintList", R.color.widget_dial_face)
                            ink.forEach { (view, method) -> setColorStateList(view, method, R.color.widget_dial_ink) }
                        }
                        else -> {
                            val dark = settings.analogTheme == DialTheme.DARK
                            setColorStateList(R.id.clock_face, "setImageTintList", ColorStateList.valueOf(if (dark) DARK_FACE else LIGHT_FACE))
                            val inkColor = ColorStateList.valueOf(if (dark) DARK_INK else LIGHT_INK)
                            ink.forEach { (view, method) -> setColorStateList(view, method, inkColor) }
                        }
                    }
                    if (settings.analogSeconds) setColorStateList(R.id.clock_analog, "setSecondHandTintList", color)
                    city?.let { setString(R.id.clock_analog, "setTimeZone", it.zone) }
                }
                val views = RemoteViews(context.packageName, R.layout.widget_clock_analog).apply {
                    removeAllViews(R.id.dial_slot)
                    addView(R.id.dial_slot, dial)
                    removeAllViews(R.id.date_slot)
                    setViewVisibility(R.id.date_slot, if (row.visible) View.VISIBLE else View.GONE)
                    if (row.visible) addView(R.id.date_slot, Widgets.dateRow(context, row, plain = false))
                    setOnClickPendingIntent(R.id.widget_root, click)
                }
                manager.updateAppWidget(id, views)
            }
        }

        private const val REQUEST_ANALOG = 7202
        private const val DARK_FACE = 0xF21C1F26.toInt()
        private const val DARK_INK = 0xFFFFFFFF.toInt()
        private const val LIGHT_FACE = 0xF2F4F4F7.toInt()
        private const val LIGHT_INK = 0xFF1C1F26.toInt()
    }
}

/**
 * Перерисовка виджетов в полночь: дата под часами — обычный текст на выбранном языке,
 * сама она не сменится.
 */
object WidgetMidnight {
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        // Ближайшая полночь среди поясов часов: у города дата сменится в свою полночь
        val settings = context.appContainer.widgetSettings.settings.value
        val zones = listOfNotNull(ZoneId.systemDefault()) +
            listOfNotNull(settings.digitalCity, settings.analogCity).mapNotNull { WorldCities.find(it)?.zoneId }
        val next = zones.minOf { LocalDate.now(it).plusDays(1).atStartOfDay(it).toInstant().toEpochMilli() } + 5_000
        // Точный, но без пробуждения: спящий телефон обновит дату, когда включится экран
        if (alarms.canScheduleExactAlarms()) alarms.setExact(AlarmManager.RTC, next, intent(context))
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC, next, intent(context))
    }

    private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, WidgetRefreshReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val REQUEST = 7205
}

/** Полночь — перерисовать виджеты (новая дата, новые кольца, серия). */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Widgets.updateAll(context)
}
