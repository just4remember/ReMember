package dev.chaik.remember.feature.time.world

import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chaik.remember.R
import dev.chaik.remember.core.ui.primaryCornerModifier
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private val CardSpacing = 10.dp
private val CardShape = RoundedCornerShape(24.dp)

/**
 * Вкладка «Мир». Первая карточка — основное время, от него считается разница у остальных;
 * она стоит на месте. Нажатие на карточку — кнопки «выше / ниже / удалить» прямо на ней,
 * удержание города — перетаскивание среди городов. Отпустили город над основным временем
 * (оно показывает пунктирный контур) — город стал основным, а прежнее встало на его место.
 */
@Composable
fun WorldClockTab(padding: PaddingValues) {
    val store = WorldClockStore.get(LocalContext.current)
    val places by store.places.collectAsStateWithLifecycle()
    val russian = Locale.getDefault().language == "ru"
    var adding by remember { mutableStateOf(false) }
    // Карточка с открытыми кнопками
    var selected by remember { mutableStateOf<String?>(null) }

    // Порядок на экране: во время перетаскивания меняется здесь, в хранилище — когда отпустили
    var order by remember { mutableStateOf(places) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // Перетаскиваемый город над карточкой основного времени: отпустить — заменить основное
    var overMain by remember { mutableStateOf(false) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    val spacing = with(LocalDensity.current) { CardSpacing.toPx() }
    val haptic = LocalHapticFeedback.current

    // Хранилище изменилось (добавили, удалили, отпустили карточку) — показать его порядок
    LaunchedEffect(places) { if (dragging == null) order = places }

    // Тикает ровно на смене секунды
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }

    fun dragBy(id: String, dy: Float) {
        dragOffset += dy
        // Повели вниз — уже не над основным временем
        if (dragOffset >= 0) overMain = false
        val list = order.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        // Край карточки заехал за середину соседа — меняемся местами, смещение пересчитываем
        // от нового места. Высоты во время переноса не меняются (см. main ниже), поэтому
        // после обмена карточка не перескакивает обратно
        if (dragOffset > 0 && index < list.lastIndex) {
            val height = heights[list[index + 1].id] ?: return
            val next = height + spacing
            if (dragOffset > height / 2 + spacing) {
                list.add(index + 1, list.removeAt(index))
                order = list
                dragOffset -= next
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        } else if (dragOffset < 0 && index == 1) {
            // Выше — основное время: оно не сдвигается, а ждёт замену. Над ним — когда
            // половина карточки города заехала на него
            val dragged = heights[id] ?: return
            val over = -dragOffset > dragged / 2 + spacing
            if (over != overMain) {
                overMain = over
                if (over) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        } else if (dragOffset < 0 && index > 1) {
            val height = heights[list[index - 1].id] ?: return
            val prev = height + spacing
            if (-dragOffset > height / 2 + spacing) {
                list.add(index - 1, list.removeAt(index))
                order = list
                dragOffset += prev
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        }
    }

    fun dropDragged() {
        val id = dragging
        if (id != null) {
            val list = order.toMutableList()
            if (overMain) {
                // Город и основное время меняются местами
                val index = list.indexOfFirst { it.id == id }
                if (index > 0) list[index] = list[0].also { list[0] = list[index] }
                order = list
            }
            store.setOrder(list.map { it.id })
        }
        dragging = null
        overMain = false
        dragOffset = 0f
    }

    // Основное — первое в списке; во время переноса оно не двигается
    val main = order.firstOrNull() ?: WorldPlace.Local
    val mainTime = now.withZoneSameInstant(main.zoneId)

    Box(Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CardSpacing),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = padding.calculateBottomPadding() + 88.dp),
        ) {
            order.forEachIndexed { index, place ->
                key(place.id) {
                    val isDragged = dragging == place.id
                    val isMain = place.id == main.id
                    // Удержание сразу берёт город в руку; основное время не переносится
                    val gestures = if (isMain) Modifier else Modifier.pointerInput(place.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragging = place.id
                                selected = null
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragBy(place.id, amount.y)
                            },
                            onDragEnd = ::dropDragged,
                            onDragCancel = ::dropDragged,
                        )
                    }
                    PlaceCard(
                        place = place,
                        main = isMain,
                        dropTarget = isMain && overMain,
                        time = now.withZoneSameInstant(place.zoneId),
                        mainTime = mainTime,
                        russian = russian,
                        editing = selected == place.id && !isDragged,
                        canUp = index > 0,
                        canDown = index < order.lastIndex,
                        onClick = { selected = if (selected == place.id) null else place.id },
                        onUp = { store.move(place.id, up = true) },
                        onDown = { store.move(place.id, up = false) },
                        onRemove = {
                            store.remove(place.id)
                            selected = null
                        },
                        modifier = Modifier
                            .onSizeChanged { heights[place.id] = it.height }
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragged) dragOffset else 0f
                                val scale = if (isDragged) 1.02f else 1f
                                scaleX = scale
                                scaleY = scale
                                shadowElevation = if (isDragged) 12f else 0f
                                // Тень повторяет скругление карточки
                                shape = CardShape
                            }
                            .then(gestures),
                    )
                }
            }
            if (order.size == 1) {
                Text(
                    stringResource(R.string.world_clock_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
        FloatingActionButton(
            onClick = { adding = true },
            modifier = primaryCornerModifier(bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            Icon(Icons.Outlined.Add, stringResource(R.string.world_clock_add))
        }
    }

    if (adding) {
        CityPickerPage(
            title = stringResource(R.string.world_clock_add),
            russian = russian,
            disabled = places.map { it.id }.toSet(),
            onPick = { city ->
                city?.let(store::add)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun PlaceCard(
    place: WorldPlace,
    main: Boolean,
    time: ZonedDateTime,
    /** Над карточкой несут город: без заливки, пунктирный контур акцентного цвета. */
    dropTarget: Boolean = false,
    mainTime: ZonedDateTime,
    russian: Boolean,
    editing: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onClick: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (main) colors.onPrimaryContainer else colors.onSurface
    val name = place.city?.name(russian) ?: stringResource(R.string.world_clock_local)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (dropTarget) Modifier.dashedOutline(colors.primary) else Modifier)
            .clip(CardShape)
            .background(
                when {
                    dropTarget -> Color.Transparent
                    main -> colors.primaryContainer
                    else -> colors.surfaceContainer
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = if (main) 16.dp else 14.dp),
    ) {
        if (main) {
            Text(
                listOfNotNull(name, place.city?.country(russian)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                clockText(time),
                fontSize = 48.sp,
                fontWeight = FontWeight.Light,
                color = content,
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
            )
            Text(dateText(time), style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.8f))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(place.city?.country(russian), offsetText(mainTime, time)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Text(
                    clockText(time),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Light,
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        AnimatedVisibility(editing) {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(40.dp),
            ) {
                IconButton(onClick = onUp, enabled = canUp) {
                    Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.world_clock_up), tint = if (canUp) content else content.copy(alpha = 0.3f))
                }
                IconButton(onClick = onDown, enabled = canDown) {
                    Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.world_clock_down), tint = if (canDown) content else content.copy(alpha = 0.3f))
                }
                if (place.city != null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.world_clock_remove), tint = colors.error)
                    }
                }
            }
        }
    }
}

/** Пунктирный контур по скруглению карточки. */
private fun Modifier.dashedOutline(color: Color): Modifier = drawBehind {
    val stroke = 2.dp.toPx()
    val radius = 24.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius - stroke / 2),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
    )
}

/** Время с секундами; цифры одинаковой ширины (tnum в стиле), чтобы секунды не дёргали строку. */
private fun clockText(time: ZonedDateTime) = "%02d:%02d:%02d".format(time.hour, time.minute, time.second)

/** «Ср, 19 ноября». */
private fun dateText(time: ZonedDateTime): String {
    val locale = Locale.getDefault()
    val day = time.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.titlecase(locale) }
    return "$day, ${DateTimeFormatter.ofPattern("d MMMM", locale).format(time)}"
}

/** «+5 ч, завтра», «−1 ч 30 мин», «как основное». Разница — от основного времени. */
@Composable
private fun offsetText(main: ZonedDateTime, there: ZonedDateTime): String {
    val minutes = Duration.ofSeconds((there.offset.totalSeconds - main.offset.totalSeconds).toLong()).toMinutes()
    val sign = if (minutes < 0) "−" else "+"
    val hours = abs(minutes) / 60
    val rest = abs(minutes) % 60
    val shift = when {
        minutes == 0L -> stringResource(R.string.world_clock_same)
        rest == 0L -> stringResource(R.string.world_clock_hours, sign, hours)
        else -> stringResource(R.string.world_clock_hours_minutes, sign, hours, rest)
    }
    val days = ChronoUnit.DAYS.between(main.toLocalDate(), there.toLocalDate())
    val day = when {
        days > 0 -> stringResource(R.string.world_clock_tomorrow)
        days < 0 -> stringResource(R.string.world_clock_yesterday)
        else -> null
    }
    return listOfNotNull(shift, day).joinToString(", ")
}
