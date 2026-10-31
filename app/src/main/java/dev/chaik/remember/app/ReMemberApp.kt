package dev.chaik.remember.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import dev.chaik.remember.appContainer
import dev.chaik.remember.feature.welcome.WelcomeScreen
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.runtime.key
import dev.chaik.remember.app.navigation.isDisabled
import dev.chaik.remember.app.navigation.islandTabs
import dev.chaik.remember.app.navigation.Destination
import androidx.navigationevent.NavigationEvent
import dev.chaik.remember.core.settings.BackSwipe
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.reminders.OpenTarget
import dev.chaik.remember.feature.tasks.editor.TaskEditorRoute
import dev.chaik.remember.feature.gym.workout.WorkoutRoute
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.chaik.remember.app.navigation.appEntryProvider
import dev.chaik.remember.app.navigation.popTransition
import dev.chaik.remember.app.navigation.pushTransition
import dev.chaik.remember.app.ui.FloatingIsland
import dev.chaik.remember.app.ui.IslandHeight
import dev.chaik.remember.core.security.AppLock
import dev.chaik.remember.core.ui.LocalIslandPadding
import dev.chaik.remember.core.ui.LocalTabVisible
import dev.chaik.remember.core.ui.components.SwipeBackHost
import dev.chaik.remember.core.ui.theme.ReMemberTheme
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private val IslandBottomMargin = 12.dp

/** Как быстро растворяется приветствие. */
private const val WELCOME_FADE_MILLIS = 250

@Composable
fun ReMemberApp(lock: AppLock) {
    val locked by lock.locked.collectAsStateWithLifecycle()
    // Интерфейс строится сразу, под замком (окно блокировки — поверх), чтобы данные успели
    // загрузиться, пока вводится PIN. До первой разблокировки он невидим — на случай, если
    // окно замка появится на долю секунды позже.
    var unlockedOnce by remember { mutableStateOf(!locked) }
    LaunchedEffect(locked) { if (!locked) unlockedOnce = true }

    ReMemberTheme {
        val container = LocalContext.current.appContainer
        val settings = LocalAppSettings.current
        // Приветствие — один раз за запуск: сразу после PIN (или старта, если PIN нет),
        // пока главная страница достраивается под ним, — столько, сколько выбрано в Страницах
        var welcome by remember { mutableStateOf(settings.welcome && !container.welcomeShown) }
        LaunchedEffect(unlockedOnce) {
            if (!welcome || !unlockedOnce) return@LaunchedEffect
            container.welcomeShown = true
            delay(settings.welcomeMillis.toLong())
            withFrameNanos { } // главная страница успела отрисоваться под приветствием
            welcome = false
        }

        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.graphicsLayer { alpha = if (unlockedOnce) 1f else 0f }) {
                MainScaffold()
            }
            AnimatedVisibility(visible = welcome, enter = EnterTransition.None, exit = fadeOut(tween(WELCOME_FADE_MILLIS))) {
                WelcomeScreen(nickname = settings.nickname)
            }
        }
    }
}

/**
 * Вкладки островка, листаются свайпом влево/вправо. Состав и порядок — из Настроек → Страницы.
 *
 * У каждой вкладки свой стек экранов: например, «Ещё → Настройки» остаётся открытым,
 * пока вы на других вкладках. Пока во вкладке открыта вложенная страница,
 * свайп вправо работает как «назад», а не как переход на соседнюю вкладку.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainScaffold() {
    val settings = LocalAppSettings.current
    val tabs = remember(settings.islandTabs) { settings.islandTabs() }
    val pagerState = rememberPagerState { tabs.size }
    // Стек у каждой вкладки свой; key — чтобы при смене состава стеки не перепутались
    val backStacks = tabs.associateWith { tab -> key(tab) { rememberNavBackStack(tab.root) } }
    val currentTab = tabs[pagerState.currentPage.coerceIn(tabs.indices)]
    val currentStack = backStacks.getValue(currentTab)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    // Поменяли состав островка (обычно из «Ещё → Настройки») — остаёмся на той же вкладке
    var shownTab by remember { mutableStateOf(currentTab) }
    LaunchedEffect(pagerState, tabs) {
        tabs.indexOf(shownTab).takeIf { it >= 0 && it != pagerState.currentPage }?.let { pagerState.scrollToPage(it) }
        snapshotFlow { pagerState.settledPage }.collect { page -> tabs.getOrNull(page)?.let { shownTab = it } }
    }

    /**
     * Переход на вкладку тапом по островку — сразу, без пролистывания: анимация через
     * промежуточные вкладки заставляла их строиться зря и подтормаживала. Свайп листает как раньше.
     */
    fun goTo(tab: Destination) {
        val page = tabs.indexOf(tab).takeIf { it >= 0 } ?: return
        scope.launch { pagerState.scrollToPage(page) }
    }

    // Лёгкая вибрация при смене вкладки — и по тапу, и по свайпу
    val appContainer = LocalContext.current.appContainer
    val session = appContainer.todaySession
    val latestTabs by rememberUpdatedState(tabs)
    val latestStacks by rememberUpdatedState(backStacks)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.drop(1).collect { page ->
            session.onTabChanged()
            // Защищённые заметки закрываются: по возвращении на вкладку будет список
            val shown = latestTabs.getOrNull(page)
            latestStacks.forEach { (tab, stack) ->
                if (tab != shown) stack.removeAll { it in appContainer.protectedScreens }
            }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            focusManager.clearFocus()
        }
    }
    // Начали листать вкладки — курсор и клавиатура остаются на старой странице, убираем их сразу:
    // иначе ввод вернул бы на страницу с курсором
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.filter { it }.collect { focusManager.clearFocus() }
    }

    // Тап по уведомлению: нужная вкладка с её корня. День и сон «Сегодня» и дату календаря
    // выставят сами экраны (и обнулят запрос); задачу открываем здесь
    val container = LocalContext.current.appContainer
    val openRequest by container.openRequests.collectAsStateWithLifecycle()
    LaunchedEffect(openRequest) {
        val target = openRequest ?: return@LaunchedEffect
        val section = when (target) {
            OpenTarget.Day, OpenTarget.Sleep, is OpenTarget.DayAt -> Destination.Today
            is OpenTarget.Calendar -> Destination.Calendar
            is OpenTarget.Task -> Destination.Tasks
            is OpenTarget.Workout -> Destination.Gym
            OpenTarget.Health -> Destination.Health
            OpenTarget.Time -> Destination.Time
        }
        // Раздел выключен в «Страницах» — туда не попасть и по уведомлению
        if (settings.isDisabled(section)) {
            container.openRequests.value = null
            return@LaunchedEffect
        }
        // Раздел во вкладке — открываем вкладку; иначе — «Ещё» и раздел поверх, чтобы «назад» вёл в «Ещё»
        val tab = section.takeIf { it in tabs } ?: Destination.More
        val stack = backStacks.getValue(tab)
        while (stack.size > 1) stack.removeLastOrNull()
        if (tab == Destination.More) stack.add(section.root)
        pagerState.scrollToPage(tabs.indexOf(tab))
        when (target) {
            is OpenTarget.Task -> stack.add(TaskEditorRoute(target.taskId))
            is OpenTarget.Workout -> stack.add(WorkoutRoute(target.workoutId, target.finish))
            // Достаточно открыть сам раздел
            OpenTarget.Health, OpenTarget.Time -> Unit
            // День, сон и дату календаря выставит сам экран и обнулит запрос
            else -> return@LaunchedEffect
        }
        container.openRequests.value = null
    }

    // «Назад» на корне любой вкладки, кроме первой, возвращает на первую
    BackHandler(enabled = pagerState.currentPage != 0 && currentStack.size == 1) {
        goTo(tabs.first())
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val islandPadding = navBarPadding + IslandHeight + IslandBottomMargin * 2

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalIslandPadding provides islandPadding) {
            HorizontalPager(
                state = pagerState,
                // Во вложенной странице горизонтальный свайп отдан жесту «назад»
                userScrollEnabled = currentStack.size == 1,
                key = { tabs.getOrNull(it)?.name ?: it },
                // Соседние вкладки держим готовыми: иначе после паузы страница строилась заново
                // прямо во время свайпа, и первые кадры анимации подтормаживали
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                CompositionLocalProvider(LocalTabVisible provides (page == pagerState.currentPage)) {
                    tabs.getOrNull(page)?.let { TabContent(backStacks.getValue(it)) }
                }
            }
        }

        // Пока открыта клавиатура, островок прячется, чтобы не мешать вводу
        AnimatedVisibility(
            visible = !WindowInsets.isImeVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = IslandBottomMargin),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            FloatingIsland(
                tabs = tabs,
                selected = currentTab,
                indicatorPosition = { pagerState.position },
                onSelect = { tab ->
                    if (tab == currentTab) {
                        // Повторный тап по вкладке — вернуться к её корню
                        while (currentStack.size > 1) currentStack.removeLastOrNull()
                    } else {
                        goTo(tab)
                    }
                },
            )
        }
    }
}

/** Стек экранов одной вкладки. */
@Composable
private fun TabContent(backStack: NavBackStack<NavKey>) {
    val leftToRight = LocalAppSettings.current.backSwipe == BackSwipe.LEFT_TO_RIGHT
    SwipeBackHost(enabled = backStack.size > 1) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            // Свои ViewModel и сохранённое состояние у каждой страницы стека
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            transitionSpec = { pushTransition() },
            popTransitionSpec = { popTransition(leftToRight) },
            // Свайпом — страница уезжает туда, куда ведёт палец (важно, когда разрешены оба направления)
            predictivePopTransitionSpec = { edge -> popTransition(leftToRight = edge == NavigationEvent.EDGE_LEFT) },
            entryProvider = appEntryProvider(backStack),
        )
    }
}

/** Дробная позиция пейджера: 1.5 = на полпути между второй и третьей вкладкой. */
private val PagerState.position: Float
    get() = currentPage + currentPageOffsetFraction
