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
     * Строка «дата · огонёк» под часами. [plain] — на подложке: без тени (тень у виджета
     * на лету не убрать, поэтому два макета). Число серии без тени всегда — с ней цветные цифры «светились».
     */
    internal fun dateRow(context: Context, date: Boolean, streakOn: Boolean, sp: Float, plain: Boolean): RemoteViews {
        val layout = if (plain) R.layout.widget_date_plain else R.layout.widget_date
        val locale = locale(context)
        // Дата на выбранном языке: «четверг, 5 ноября» / «Thursday, 5 November». В полночь
        // виджеты перерисуются сами — см. WidgetMidnight
        val dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
        if (date) WidgetMidnight.schedule(context)
        return RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.clock_date, dateText)
            setTextViewTextSize(R.id.clock_date, COMPLEX_UNIT_DIP, sp)
            setViewVisibility(R.id.clock_date, if (date) View.VISIBLE else View.GONE)
            val streakVisibility = if (streakOn) View.VISIBLE else View.GONE
            setViewVisibility(R.id.streak_icon, streakVisibility)
            setViewVisibility(R.id.streak_count, streakVisibility)
            if (streakOn) {
                val days = WidgetStreak.current(context)
                // Цвет огонька растёт вместе с серией, как в приложении; нет серии — бледный
                val color = streakColor(days)?.toArgb() ?: 0x99FFFFFF.toInt()
                setColorStateList(R.id.streak_icon, "setImageTintList", ColorStateList.valueOf(color))
                setTextViewText(R.id.streak_count, days.toString())
                setTextColor(R.id.streak_count, color)
                setTextViewTextSize(R.id.streak_count, COMPLEX_UNIT_DIP, sp)
                setViewLayoutWidth(R.id.streak_icon, sp * 1.15f, COMPLEX_UNIT_DIP)
                setViewLayoutHeight(R.id.streak_icon, sp * 1.15f, COMPLEX_UNIT_DIP)
                if (!date) setViewLayoutMargin(R.id.streak_icon, RemoteViews.MARGIN_START, 0f, COMPLEX_UNIT_DIP)
            }
        }
    }

    /** Ширина «00:00» и высота строки цифр при размере 100 dp — чтобы подобрать размер под виджет. */
    internal fun measureDigits(weight: ClockWeight): Pair<Float, Float> {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 100f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            fontVariationSettings = "'wght' ${weight.weight}"
            fontFeatureSettings = "tnum"
        }
        val metrics = paint.fontMetrics
        return paint.measureText("00:00") to (metrics.descent - metrics.ascent)
    }
}

/**
 * Серия дней с записью для огонька. Базу из виджета не открываем (она зашифрована и
 * открывается долго): приложение запоминает, каким днём закончилась серия и какой длины.
 */
object WidgetStreak {
    /** Даты с записью изменились — пересчитать; изменилась серия — перерисовать часы. */
    fun update(context: Context, written: Set<LocalDate>) {
        val today = context.appContainer.settings.settings.value.logicalToday()
        val end = written.filter { !it.isAfter(today) }.maxOrNull()
        var length = 0
        if (end != null) {
            var day: LocalDate = end
            while (day in written) {
                length++
                day = day.minusDays(1)
            }
        }
        val prefs = prefs(context)
        val endDay = end?.toEpochDay() ?: Long.MIN_VALUE
        if (prefs.getLong(KEY_END, Long.MIN_VALUE) == endDay && prefs.getInt(KEY_LENGTH, 0) == length) return
        prefs.edit {
            putLong(KEY_END, endDay)
            putInt(KEY_LENGTH, length)
        }
        Widgets.updateClocks(context)
    }

    /** Серия на сегодня: обрывается, только если пропущен вчерашний день (сегодня ещё можно написать). */
    fun current(context: Context): Int {
        val prefs = prefs(context)
        val end = prefs.getLong(KEY_END, Long.MIN_VALUE)
        val today = context.appContainer.settings.settings.value.logicalToday().toEpochDay()
        return if (end >= today - 1) prefs.getInt(KEY_LENGTH, 0) else 0
    }

    private fun prefs(context: Context) = context.getSharedPreferences("widget_streak", Context.MODE_PRIVATE)
    private const val KEY_END = "end"
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
            val (digitsWidth, digitsHeight) = Widgets.measureDigits(settings.digitalWeight)
            val click = Widgets.open(context, OpenTarget.Time, REQUEST_DIGITAL)
            val showRow = settings.digitalDate || settings.digitalStreak
            ids.forEach { id ->
                val size = Widgets.size(manager, id, SizeF(250f, 110f))
                val padding = if (settings.digitalCard) 12f else 4f
                val rowSp = (size.height * 0.12f).coerceIn(10f, 16f)
                val rowHeight = if (showRow) rowSp * 1.45f else 0f
                // Цифры — во всю ширину или высоту, что раньше упрётся
                val digits = minOf(
                    (size.width - padding * 2) * 0.94f * 100f / digitsWidth,
                    (size.height - padding * 2 - rowHeight) * 100f / digitsHeight,
                ).coerceAtLeast(12f)
                val views = RemoteViews(context.packageName, R.layout.widget_clock_digital).apply {
                    setViewVisibility(R.id.widget_bg, if (settings.digitalCard) View.VISIBLE else View.GONE)
                    if (settings.digitalCard) setColorStateList(R.id.widget_bg, "setImageTintList", Widgets.cardColor(settings.digitalCardColor))
                    removeAllViews(R.id.digits_slot)
                    addView(
                        R.id.digits_slot,
                        RemoteViews(
                            context.packageName,
                            if (settings.digitalCard) settings.digitalWeight.plainLayout else settings.digitalWeight.layout,
                        ).apply {
                            setTextViewTextSize(R.id.clock_digits, COMPLEX_UNIT_DIP, digits)
                        },
                    )
                    removeAllViews(R.id.date_slot)
                    if (showRow) addView(R.id.date_slot, Widgets.dateRow(context, settings.digitalDate, settings.digitalStreak, rowSp, plain = settings.digitalCard))
                    setOnClickPendingIntent(R.id.widget_root, click)
                }
                manager.updateAppWidget(id, views)
            }
        }

        private const val REQUEST_DIGITAL = 7201
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
            val click = Widgets.open(context, OpenTarget.Time, REQUEST_ANALOG)
            val showRow = settings.analogDate || settings.analogStreak
            ids.forEach { id ->
                val size = Widgets.size(manager, id, SizeF(110f, 110f))
                val rowSp = (minOf(size.width, size.height) * 0.08f).coerceIn(10f, 15f)
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
                }
                val views = RemoteViews(context.packageName, R.layout.widget_clock_analog).apply {
                    removeAllViews(R.id.dial_slot)
                    addView(R.id.dial_slot, dial)
                    removeAllViews(R.id.date_slot)
                    setViewVisibility(R.id.date_slot, if (showRow) View.VISIBLE else View.GONE)
                    if (showRow) addView(R.id.date_slot, Widgets.dateRow(context, settings.analogDate, settings.analogStreak, rowSp, plain = false))
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
        val next = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 5_000
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
