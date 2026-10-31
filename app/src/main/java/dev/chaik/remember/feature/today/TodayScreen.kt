package dev.chaik.remember.feature.today

import dev.chaik.remember.core.settings.LocalAppSettings
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.editor.EditorToolbar
import dev.chaik.remember.core.editor.EditorToolbarHeight
import dev.chaik.remember.core.editor.PhotoViewer
import dev.chaik.remember.core.editor.RichEditor
import dev.chaik.remember.core.reminders.OpenTarget
import dev.chaik.remember.core.ui.components.MasterButton
import dev.chaik.remember.core.ui.components.daySwipeModifier
import dev.chaik.remember.core.ui.components.MasterButtonItem
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.secondaryCorner
import dev.chaik.remember.feature.today.calendar.DayPickerPage
import dev.chaik.remember.feature.today.day.DayController
import dev.chaik.remember.feature.today.day.DayRating
import dev.chaik.remember.feature.today.day.DayTags
import dev.chaik.remember.feature.today.money.MoneyContent
import dev.chaik.remember.feature.today.sleep.SleepContent
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute : NavKey

/** Сколько фото можно выбрать за раз. */
private const val MAX_PHOTOS_PER_PICK = 10

/** Место под кнопками внизу, чтобы последняя строка не пряталась за ними. */
private val FabSpace = 96.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val container = context.appContainer
    val viewModel = viewModel { TodayViewModel(container) }
    val day = viewModel.day
    val haptics = LocalHapticFeedback.current

    // Открыли из уведомления «Как прошёл день?» / «Как спалось?» — сегодняшний день в нужном режиме;
    // из галереи фото — запись нужного дня
    val openRequest by container.openRequests.collectAsStateWithLifecycle()
    LaunchedEffect(openRequest) {
        val request = openRequest
        val mode = when (request) {
            OpenTarget.Day, is OpenTarget.DayAt -> TodayMode.Day
            OpenTarget.Sleep -> TodayMode.Sleep
            else -> return@LaunchedEffect
        }
        viewModel.selectDate((request as? OpenTarget.DayAt)?.date ?: viewModel.currentDay)
        viewModel.selectMode(mode)
        container.openRequests.value = null
    }

    // Уходим в фон — сохраняем сразу, не дожидаясь паузы в наборе
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }

    val pickPhotos = rememberLauncherForActivityResult(PickMultipleVisualMedia(MAX_PHOTOS_PER_PICK)) { uris ->
        day.addPhotos(uris)
    }
    val launchPicker = {
        // Выбор фото — системное окно: блокировка по возвращении не нужна
        container.appLock.allowExternal()
        pickPhotos.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }

    val failedImport = stringResource(R.string.editor_photo_import_failed)
    LaunchedEffect(day.failedImports) {
        if (day.failedImports > 0) {
            Toast.makeText(context, failedImport, Toast.LENGTH_SHORT).show()
            day.consumeImportError()
        }
    }

    // Новый день мог наступить, пока приложение открыто; смену времени в Профиле тоже подхватываем сразу
    val dayStart = LocalAppSettings.current.dayStartMinutes
    LaunchedEffect(dayStart) {
        while (true) {
            viewModel.refreshToday()
            delay(DAY_CHECK_MS)
        }
    }

    var openedPhotos by remember { mutableStateOf<OpenedPhotos?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    val keyboardVisible = WindowInsets.isImeVisible
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val mode = viewModel.mode

    // «Пятница, 23 октября» — на языке телефона
    val title = remember(viewModel.date) {
        viewModel.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).replaceFirstChar { it.titlecase() }
    }

    ScreenScaffold(
        title = title,
        modifier = modifier,
        onBack = onBack,
        onTitleClick = { pickingDate = true },
        titleModifier = daySwipeModifier(canGoForward = !viewModel.isToday) { days ->
            focusManager.clearFocus()
            viewModel.selectDate(viewModel.date.plusDays(days))
        },
        actions = {
            val streak by viewModel.streak.collectAsStateWithLifecycle()
            StreakFlame(streak, Modifier.padding(end = 12.dp))
            if (!viewModel.isToday) {
                IconButton(onClick = { viewModel.selectDate(viewModel.currentDay) }) {
                    Icon(Icons.Outlined.Today, stringResource(R.string.today_go_today))
                }
            }
        },
    ) { padding ->
        val bottomSpace = if (keyboardVisible) EditorToolbarHeight + 24.dp else padding.calculateBottomPadding() + FabSpace

        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "todayMode",
            ) { shown ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .imePadding()
                        // Отступ под шапку — снаружи прокрутки: строка под шапкой считается невидимой,
                        // и курсор, оказавшийся там, редактор подкрутит обратно
                        .padding(top = padding.calculateTopPadding())
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    when (shown) {
                        TodayMode.Day -> DayModeContent(
                            day = day,
                            photoFile = container.photoStorage::file,
                            onOpenPhotos = { ids, index -> openedPhotos = OpenedPhotos(ids, index) },
                            bottomSpace = bottomSpace,
                        )
                        TodayMode.Sleep -> {
                            if (viewModel.sleep.isLoaded) SleepContent(viewModel.sleep, Modifier.padding(top = 8.dp))
                            // Пустое место под текстом: тап ставит курсор в конец записи сна
                            Spacer(
                                Modifier
                                    .fillMaxWidth()
                                    .height(bottomSpace)
                                    .clickable(interactionSource = null, indication = null) { viewModel.sleep.dream.focusEnd() },
                            )
                        }
                        TodayMode.Money -> {
                            MoneyContent(viewModel.money, Modifier.padding(top = 8.dp))
                            Spacer(Modifier.height(bottomSpace))
                        }
                    }
                }
            }

            val buttonsPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = padding.calculateBottomPadding())
            PhotoButton(
                visible = !keyboardVisible && mode == TodayMode.Day,
                isImporting = day.isImporting,
                onClick = { if (!day.isImporting) launchPicker() },
                padding = buttonsPadding,
            )
            MasterButton(
                icon = mode.icon,
                contentDescription = stringResource(R.string.today_switch_mode, stringResource(mode.label)),
                // По дуге слева направо-вверх: Траты (180°) → Сон → День (90°)
                items = listOf(TodayMode.Money, TodayMode.Sleep, TodayMode.Day).map { item ->
                    // Текущий раздел — тёмный и неактивный
                    MasterButtonItem(item, item.icon, stringResource(item.label), enabled = item != mode)
                },
                onSelect = {
                    focusManager.clearFocus()
                    viewModel.selectMode(it)
                },
                visible = !keyboardVisible,
                padding = buttonsPadding,
            )

            // Панель форматирования — когда печатаем в записи дня или сна; фото — только в записи дня
            val typingDream = mode == TodayMode.Sleep && viewModel.sleep.dream.hasFocus
            AnimatedVisibility(
                visible = keyboardVisible && ((mode == TodayMode.Day && day.editor.hasFocus) || typingDream),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding(),
            ) {
                EditorToolbar(
                    state = if (mode == TodayMode.Sleep) viewModel.sleep.dream else day.editor,
                    onAddPhoto = if (mode == TodayMode.Sleep) null else launchPicker,
                    onHideKeyboard = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    },
                )
            }
        }
    }

    if (pickingDate) {
        val marks by viewModel.calendarMarks.collectAsStateWithLifecycle()
        val tags by viewModel.calendarTags.collectAsStateWithLifecycle()
        DayPickerPage(
            selected = viewModel.date,
            marks = marks,
            tags = tags,
            firstMonth = viewModel.calendarStart,
            onPick = {
                viewModel.selectDate(it)
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }

    openedPhotos?.let { opened ->
        PhotoViewer(
            files = opened.ids.map(container.photoStorage::file),
            initialIndex = opened.index,
            onDismiss = { openedPhotos = null },
        )
    }
}

private const val DAY_CHECK_MS = 30_000L

/** Цвет огонька с такого числа дней подряд. Следующая строка — следующий цвет. */
private class StreakLevel(val fromDays: Int, val color: Color)

private val StreakLevels = listOf(
    StreakLevel(1, Color(0xFFFFC83D)), // жёлтый
    StreakLevel(7, Color(0xFFFF8C2E)), // оранжевый
    StreakLevel(14, Color(0xFFF0504D)), // красный
    StreakLevel(30, Color(0xFF5CC8F5)), // голубой
    StreakLevel(60, Color(0xFF4A86F0)), // синий
    StreakLevel(100, Color(0xFFA25CE6)), // фиолетовый
    StreakLevel(200, Color(0xFF3FB868)), // зелёный
)

private fun streakLevel(days: Int): StreakLevel? = StreakLevels.lastOrNull { days >= it.fromDays }

/** Огонёк серии: значок и число одного цвета, цвет растёт вместе с серией. Тап — какие цвета бывают. */
@Composable
private fun StreakFlame(days: Int, modifier: Modifier = Modifier) {
    var showLevels by remember { mutableStateOf(false) }
    val description = pluralStringResource(R.plurals.today_streak, days, days)
    val color = streakLevel(days)?.color ?: MaterialTheme.colorScheme.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = stringResource(R.string.today_streak_levels)) { showLevels = true }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Icon(Icons.Filled.LocalFireDepartment, null, tint = color, modifier = Modifier.size(22.dp))
        Text(days.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
    if (showLevels) StreakLevelsDialog(days, description, onDismiss = { showLevels = false })
}

/** Все цвета огонька и сколько дней подряд нужно для каждого; текущий выделен. */
@Composable
private fun StreakLevelsDialog(days: Int, current: String, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val active = streakLevel(days)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.today_streak_levels)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = current,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                StreakLevels.forEachIndexed { index, level ->
                    val next = StreakLevels.getOrNull(index + 1)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .then(if (level === active) Modifier.background(colors.surfaceContainerHighest) else Modifier)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Filled.LocalFireDepartment, null, tint = level.color, modifier = Modifier.size(26.dp))
                        Text(
                            text = if (next != null) {
                                stringResource(R.string.today_streak_range, level.fromDays, next.fromDays - 1)
                            } else {
                                stringResource(R.string.today_streak_from, level.fromDays)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (level === active) FontWeight.Bold else null,
                            color = level.color,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

/** Режим «День»: оценка, теги и запись с фото. */
@Composable
private fun DayModeContent(
    day: DayController,
    photoFile: (String) -> File,
    onOpenPhotos: (List<String>, Int) -> Unit,
    bottomSpace: Dp,
) {
    if (!day.isLoaded) return
    DayRating(day, Modifier.padding(top = 4.dp))
    DayTags(day, Modifier.padding(top = 8.dp))
    // Черта отделяет «шапку» дня (оценка, теги) от самой записи
    HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 16.dp))
    RichEditor(
        state = day.editor,
        photoFile = photoFile,
        onOpenPhotos = onOpenPhotos,
        placeholder = stringResource(R.string.today_placeholder),
        modifier = Modifier.fillMaxWidth(),
    )
    // Пустое место под текстом: тап ставит курсор в конец записи
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(bottomSpace)
            .clickable(interactionSource = null, indication = null) { day.editor.focusEnd() },
    )
}

/** Кнопка «добавить фото» слева снизу — только в режиме «День». */
@Composable
private fun BoxScope.PhotoButton(
    visible: Boolean,
    isImporting: Boolean,
    onClick: () -> Unit,
    padding: PaddingValues,
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        // Напротив мастеркнопки: у левши — справа
        modifier = Modifier
            .align(secondaryCorner())
            .padding(padding),
    ) {
        FloatingActionButton(onClick = onClick) {
            if (isImporting) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.editor_add_photo))
            }
        }
    }
}

/** Какие фото открыты на весь экран и с какого начать. */
private data class OpenedPhotos(val ids: List<String>, val index: Int)
