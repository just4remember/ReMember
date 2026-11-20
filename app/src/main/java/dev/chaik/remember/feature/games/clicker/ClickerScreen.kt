package dev.chaik.remember.feature.games.clicker

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.BuildConfig
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.ui.components.ScreenScaffold
import kotlinx.serialization.Serializable
import kotlin.math.floor

@Serializable
data object ClickerRoute : NavKey

/** Вкладки игры — полоса над островком. */
private enum class GameTab(val label: Int, val icon: ImageVector) {
    TAP(R.string.clicker_tab_tap, Icons.Outlined.TouchApp),
    UPGRADES(R.string.clicker_tab_upgrades, Icons.Outlined.Style),
    CHAOS(R.string.clicker_tab_chaos, Icons.Outlined.Whatshot),
    DAILY(R.string.clicker_tab_daily, Icons.Outlined.EventAvailable),
}

/** Цвет монеты — тёплое золото, на светлой и тёмной теме. */
val CoinColor = Color(0xFFF2B33D)


/**
 * Кликер. Вкладки снизу (над островком): «Тап» — плитки (за тап, до уровня, в час),
 * уровень, монеты, планета и энергия; «Улучшения» — карточки по категориям; «Хаос» —
 * временные карточки; «Ежедневные» — задания. Мета-прогрессия (прохождения, звёздная пыль)
 * — кнопкой в шапке.
 */
@Composable
fun ClickerScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val viewModel = viewModel { ClickerViewModel(container.clickerStore) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onForeground()
                Lifecycle.Event.ON_STOP -> viewModel.onBackground()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.onBackground()
        }
    }
    var metaOpen by remember { mutableStateOf(false) }
    var debugOpen by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(R.string.game_clicker),
        modifier = modifier,
        onBack = onBack,
        actions = {
            if (BuildConfig.DEBUG) {
                Box {
                    IconButton(onClick = { debugOpen = true }) { Icon(Icons.Outlined.BugReport, stringResource(R.string.clicker_debug)) }
                    DebugMenu(viewModel, expanded = debugOpen, onDismiss = { debugOpen = false })
                }
            }
            IconButton(onClick = { metaOpen = true }) { Icon(Icons.Outlined.EmojiEvents, stringResource(R.string.clicker_meta)) }
        },
    ) { padding ->
        val engine = viewModel.engine
        if (engine == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        val categories = remember(engine) { engine.catalog.categories.filterNot { it.temporary || it.hidden } }
        // Между вкладками — только меню внизу; внутри «Улучшений» и «Хаоса» разделы листаются свайпом
        var tab by rememberSaveable { mutableStateOf(GameTab.TAP) }
        val upgrades = rememberPagerState { categories.size }
        val chaos = rememberPagerState { ChaosFilter.entries.size }
        val scope = rememberCoroutineScope()
        Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    GameTab.TAP -> TapTab(viewModel)
                    GameTab.UPGRADES -> UpgradesTab(viewModel, engine, categories, upgrades)
                    GameTab.CHAOS -> ChaosTab(viewModel, engine, chaos)
                    GameTab.DAILY -> DailyTab(viewModel, engine)
                }
            }
            GameTabs(
                selected = tab,
                onSelect = { t ->
                    // «Улучшения» — с «Планеты», «Хаос» — с «Моих»
                    scope.launch {
                        if (t == GameTab.UPGRADES && t != tab) upgrades.scrollToPage(0)
                        if (t == GameTab.CHAOS && t != tab) chaos.scrollToPage(ChaosFilter.MINE.ordinal)
                    }
                    tab = t
                },
            )
        }
        // Сколько накопилось, пока вас не было, — уведомлением сверху по центру
        AwayBanner(
            gain = viewModel.awayGain,
            onDone = viewModel::dismissAway,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = padding.calculateTopPadding() + 8.dp),
        )
        }
    }

    if (viewModel.showGoalReached) GoalReachedDialog(viewModel, viewModel.engine!!)
    if (metaOpen && viewModel.engine != null) MetaProgressDialog(viewModel, viewModel.engine!!, onDismiss = { metaOpen = false })
}

/** Полоса вкладок на своей подложке — чтобы не терялась; выбранная — подсвечена. */
@Composable
private fun GameTabs(selected: GameTab, onSelect: (GameTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(3.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        GameTab.entries.forEach { t ->
            val on = t == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .raised(on, strip = colors.surfaceContainerHigh)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(t) }
                    .padding(vertical = 6.dp),
            ) {
                Icon(t.icon, null, tint = if (on) colors.onSurface else colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Text(
                    stringResource(t.label),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (on) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Монеты с иконкой — целиком, без сокращений. */
@Composable
fun CoinsLine(coins: Double, large: Boolean, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(Icons.Filled.MonetizationOn, null, tint = CoinColor, modifier = Modifier.size(if (large) 32.dp else 26.dp))
        Spacer(Modifier.width(8.dp))
        // Цифры одной ширины (tnum): число растёт справа, а не скачет из стороны в сторону
        Text(
            formatCoinsFull(coins),
            style = (if (large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall).copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun TapTab(viewModel: ClickerViewModel) {
    val engine = viewModel.engine ?: return
    val res = LocalContext.current.resources
    val state = viewModel.state
    val stats = engine.stats(state)
    val level = engine.level(state)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Три плитки: за тап, до следующего уровня, в час
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile(stringResource(R.string.clicker_stat_tap), formatCoins(stats.tapValue, res), Modifier.weight(1f))
            // На последнем уровне — сколько осталось до конца прохождения
            val toNext = engine.toNextLevel(state)
            StatTile(
                stringResource(if (toNext != null) R.string.clicker_stat_next else R.string.clicker_stat_end),
                formatCoins(toNext ?: engine.toGoal(state), res),
                Modifier.weight(1f),
            )
            StatTile(stringResource(R.string.clicker_stat_hour), formatCoins(stats.incomePerHour, res), Modifier.weight(1f))
        }

        // Уровень — от него зависит вид планеты
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text(stringResource(R.string.clicker_player_level_short, level), style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(
                progress = { engine.levelProgress(state) },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
                    .height(6.dp),
            )
            Text(
                "$level/${engine.catalog.rules.levels.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        CoinsLine(state.coins, large = true, modifier = Modifier.padding(top = 16.dp))
        if (engine.isFrozen(state)) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.clicker_frozen), modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            PlanetButton(
                level = level,
                onTap = {
                    val gain = viewModel.tap()
                    if (gain > 0) "+" + formatCoins(gain, res) else null
                },
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
        }

        // Внизу: слева — прокачка тапа, справа — энергия
        var tapUpgrades by remember { mutableStateOf(false) }
        val energy = engine.energyOf(state, stats)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            // Своя круглая кнопка: тень ровно по кругу (у IconButton она шла по невидимой рамке касания)
            Surface(
                onClick = { tapUpgrades = true },
                shape = CircleShape,
                // Как у плиток «за тап» и т. д.
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shadowElevation = 2.dp,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.TouchApp, stringResource(R.string.clicker_tap_upgrades)) }
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.Bolt, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.clicker_energy, floor(energy).toInt(), stats.energyMax.toInt()),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (tapUpgrades) TapUpgradesSheet(viewModel, engine, onDismiss = { tapUpgrades = false })
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 1.5.dp, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(Icons.Filled.MonetizationOn, null, tint = CoinColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Заглушка «Ежедневных»: шапка с монетами, как у карточек; три задания придумаем позже. */
@Composable
private fun DailyTab(viewModel: ClickerViewModel, engine: dev.chaik.remember.feature.games.clicker.engine.ClickerEngine) {
    Column(Modifier.fillMaxSize()) {
        CoinsHeader(viewModel, engine)
        DailyPlaceholder()
    }
}

@Composable
private fun DailyPlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Icon(Icons.Outlined.EventAvailable, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.clicker_daily_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(
            stringResource(R.string.clicker_daily_soon),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Названия уровней — то, что происходит с планетой. */
private fun levelName(level: Int): Int = when (level) {
    1 -> R.string.clicker_level_lava
    2 -> R.string.clicker_level_rock
    3 -> R.string.clicker_level_oceans
    4 -> R.string.clicker_level_grass
    5 -> R.string.clicker_level_cities
    6 -> R.string.clicker_level_satellite
    7 -> R.string.clicker_level_satellites
    else -> R.string.clicker_level_ring
}

@Composable
private fun DebugMenu(viewModel: ClickerViewModel, expanded: Boolean, onDismiss: () -> Unit) {
    // Необратимое — после подтверждения
    var confirm by remember { mutableStateOf<Int?>(null) }
    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(what)) },
            text = {
                Text(stringResource(if (what == R.string.clicker_debug_restore) R.string.clicker_debug_restore_text else R.string.clicker_debug_reset_meta_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    if (what == R.string.clicker_debug_restore) viewModel.debugRestoreSnapshot() else viewModel.debugResetMeta()
                    confirm = null
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        @Composable
        fun item(label: Int, action: () -> Unit) {
            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                action()
                onDismiss()
            })
        }
        item(R.string.clicker_debug_hour) { viewModel.debugAway(1.0) }
        item(R.string.clicker_debug_8h) { viewModel.debugAway(8.0) }
        item(if (viewModel.speed == 1) R.string.clicker_debug_speed else R.string.clicker_debug_speed_off, viewModel::debugToggleSpeed)
        item(R.string.clicker_debug_give_coins, viewModel::debugGiveCoins)
        item(R.string.clicker_debug_give_income, viewModel::debugGiveIncome)
        item(R.string.clicker_debug_give_tap, viewModel::debugGiveTap)
        item(R.string.clicker_debug_coins, viewModel::debugCoins)
        item(R.string.clicker_debug_goal, viewModel::debugReachGoal)
        item(R.string.clicker_debug_snapshot, viewModel::debugSnapshot)
        if (viewModel.hasSnapshot) item(R.string.clicker_debug_restore) { confirm = R.string.clicker_debug_restore }
        item(R.string.clicker_debug_reset_meta) { confirm = R.string.clicker_debug_reset_meta }
        item(R.string.clicker_debug_reset, viewModel::debugReset)
    }
}

/**
 * «+ 12 345» с монетой — сколько принёс доход вне игры. Выезжает сверху, само уходит через
 * 3 с; смахнуть можно в любую сторону (уведомление едет за пальцем).
 */
@Composable
private fun AwayBanner(gain: Double?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    // Последняя сумма остаётся на экране, пока уведомление уезжает
    var shown by remember { mutableStateOf(0.0) }
    if (gain != null) shown = gain
    val done by rememberUpdatedState(onDone)
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(gain) {
        if (gain == null) return@LaunchedEffect
        drag.snapTo(Offset.Zero)
        delay(3_000)
        done()
    }
    AnimatedVisibility(
        visible = gain != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 3.dp,
            modifier = Modifier
                .offset { IntOffset(drag.value.x.roundToInt(), drag.value.y.roundToInt()) }
                .pointerInput(Unit) {
                    val far = 48.dp.toPx()
                    detectDragGestures(
                        onDragEnd = {
                            if (drag.value.getDistance() > far) done() else scope.launch { drag.animateTo(Offset.Zero) }
                        },
                        onDragCancel = { scope.launch { drag.animateTo(Offset.Zero) } },
                        onDrag = { change, amount ->
                            change.consume()
                            scope.launch { drag.snapTo(drag.value + amount) }
                        },
                    )
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.MonetizationOn, null, tint = CoinColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text(formatCoinsFull(shown), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Вид выбранной кнопки полосы: «выпуклая» плашка чуть меньше кнопки (отступ [RAISED_INSET]),
 * на 5% светлее подложки [strip], с тенью снаружи, без акцентной заливки. Рисуется под
 * содержимым и ставится до clip, иначе тень обрежется.
 */
@Composable
fun Modifier.raised(on: Boolean, strip: Color, radius: androidx.compose.ui.unit.Dp = 10.dp): Modifier {
    if (!on) return this
    val fill = lerp(strip, Color.White, 0.05f).toArgb()
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val shadowColor = Color.Black.copy(alpha = if (dark) 0.8f else 0.45f).toArgb()
    return drawBehind {
        val inset = RAISED_INSET.toPx()
        val r = radius.toPx()
        // Тень — у самой плашки (shadowLayer), а не у всей кнопки: так плашку можно сделать меньше
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = fill
            setShadowLayer(5.dp.toPx(), 0f, 1.5.dp.toPx(), shadowColor)
        }
        drawIntoCanvas { it.nativeCanvas.drawRoundRect(inset, inset, size.width - inset, size.height - inset, r, r, paint) }
    }
}

private val RAISED_INSET = 2.dp
