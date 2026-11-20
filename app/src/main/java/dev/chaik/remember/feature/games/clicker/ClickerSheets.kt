package dev.chaik.remember.feature.games.clicker

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.graphics.luminance
import dev.chaik.remember.core.ui.components.SystemBarIcons
import dev.chaik.remember.feature.games.clicker.engine.CategoryDef
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.background
import android.content.res.Resources
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Cyclone
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.chaik.remember.R
import dev.chaik.remember.core.ui.components.swipeToClose
import dev.chaik.remember.feature.games.clicker.engine.CardDef
import dev.chaik.remember.feature.games.clicker.engine.CardEffect
import dev.chaik.remember.feature.games.clicker.engine.ClickerEngine
import dev.chaik.remember.feature.games.clicker.engine.ClickerState
import dev.chaik.remember.feature.games.clicker.engine.ClickerStats
import dev.chaik.remember.feature.games.clicker.engine.PerkDef
import java.text.NumberFormat
import java.util.Locale

private fun CardEffect.label(): Int = when (this) {
    CardEffect.INCOME -> R.string.clicker_effect_income
    CardEffect.TAP -> R.string.clicker_effect_tap
    CardEffect.TAP_SECONDS -> R.string.clicker_effect_tap_seconds
    CardEffect.BOOST -> R.string.clicker_effect_boost
    CardEffect.ENERGY_MAX -> R.string.clicker_effect_energy_max
    CardEffect.ENERGY_REGEN -> R.string.clicker_effect_energy_regen
    CardEffect.OFFLINE_RATE -> R.string.clicker_effect_offline_rate
    CardEffect.OFFLINE_CAP -> R.string.clicker_effect_offline_cap
}

/** Иконка категории карточек. */
fun categoryIcon(id: String): ImageVector = when (id) {
    "planet" -> Icons.Outlined.Public
    "orbit" -> Icons.Outlined.SatelliteAlt
    "star" -> Icons.Outlined.WbSunny
    "galaxy" -> Icons.Outlined.Cyclone
    "universe" -> Icons.Outlined.AutoAwesome
    else -> Icons.Outlined.Category
}

/** Число эффекта для экрана: коины — по-игровому, остальное — с нужной точностью. */
private fun number(value: Double, digits: Int): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = digits }.format(value)

/** Подпись эффекта на карточке — над числом: «Профит в час», «Энергии в секунду»… */
private fun CardEffect.cardLabel(): Int = when (this) {
    CardEffect.INCOME -> R.string.clicker_card_income
    CardEffect.TAP -> R.string.clicker_card_tap
    CardEffect.TAP_SECONDS -> R.string.clicker_card_tap_seconds
    CardEffect.BOOST -> R.string.clicker_card_boost
    CardEffect.ENERGY_MAX -> R.string.clicker_card_energy_max
    CardEffect.ENERGY_REGEN -> R.string.clicker_card_energy_regen
    CardEffect.OFFLINE_RATE -> R.string.clicker_card_offline_rate
    CardEffect.OFFLINE_CAP -> R.string.clicker_card_offline_cap
}

/**
 * Сколько карточка даёт сейчас ([now], всеми купленными уровнями) и сколько добавит
 * следующий уровень ([next]; null — максимум). Доход и тап — с учётом усилителей и
 * улучшений (разница в итоговом доходе); проценты — в процентах.
 */
private class EffectValue(val now: Double, val next: Double?)

private fun effectValue(card: CardDef, engine: ClickerEngine, state: ClickerState, stats: ClickerStats): EffectValue {
    val level = state.level(card.id)
    fun pick(s: ClickerStats): Double = when (card.effect) {
        // Усилитель — сколько коинов в час он добавляет своей категории
        CardEffect.INCOME, CardEffect.BOOST -> s.incomePerHour
        CardEffect.TAP -> s.tapValue
        CardEffect.TAP_SECONDS -> s.tapIncomeSeconds
        CardEffect.ENERGY_MAX -> s.energyMax
        CardEffect.ENERGY_REGEN -> s.energyRegen
        CardEffect.OFFLINE_RATE -> s.offlineRate * 100
        CardEffect.OFFLINE_CAP -> s.offlineCapHours
    }
    val now = if (level == 0) 0.0 else pick(stats) - pick(engine.stats(state.copy(levels = state.levels - card.id)))
    val next = if (card.isMaxed(level)) null else pick(engine.stats(state.copy(levels = state.levels + (card.id to level + 1)))) - pick(stats)
    return EffectValue(now, next)
}

/** Только число: «1 200», «0,06», «10%». */
private fun effectNumber(effect: CardEffect, v: Double, res: Resources): String = when (effect) {
    CardEffect.INCOME, CardEffect.TAP, CardEffect.BOOST -> formatCoins(v, res)
    CardEffect.TAP_SECONDS, CardEffect.ENERGY_REGEN -> number(v, 2)
    CardEffect.ENERGY_MAX -> number(v, 0)
    CardEffect.OFFLINE_CAP -> number(v, 1)
    CardEffect.OFFLINE_RATE -> number(v, 1) + "%"
}

/** Число со словами — для окна карточки: «1 200 в час», «+10% к доходу «Планета»». */
private fun effectPhrase(card: CardDef, engine: ClickerEngine, v: Double, res: Resources): String = when (card.effect) {
    CardEffect.INCOME -> res.getString(R.string.clicker_value_income, formatCoins(v, res))
    CardEffect.TAP -> res.getString(R.string.clicker_value_tap, formatCoins(v, res))
    CardEffect.TAP_SECONDS -> res.getString(R.string.clicker_value_tap_seconds, number(v, 2))
    CardEffect.ENERGY_MAX -> res.getString(R.string.clicker_value_energy_max, number(v, 0))
    CardEffect.ENERGY_REGEN -> res.getString(R.string.clicker_value_energy_regen, number(v, 2))
    CardEffect.OFFLINE_RATE -> res.getString(R.string.clicker_value_offline_rate, number(v, 1))
    CardEffect.OFFLINE_CAP -> res.getString(R.string.clicker_value_offline_cap, number(v, 1))
    CardEffect.BOOST -> res.getString(R.string.clicker_value_boost, number(v, 0), engine.catalog.categories.firstOrNull { it.id == card.target }?.name.orEmpty())
}

/**
 * Шапка страниц карточек и заданий: монеты по центру; сверху тусклее — за тап (слева)
 * и в час (справа).
 */
@Composable
fun CoinsHeader(viewModel: ClickerViewModel, engine: ClickerEngine) {
    val res = LocalContext.current.resources
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            stringResource(R.string.clicker_per_tap, formatCoins(engine.stats(viewModel.state).tapValue, res)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            stringResource(R.string.clicker_per_hour, formatCoins(engine.stats(viewModel.state).incomePerHour, res)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.TopEnd),
        )
        CoinsLine(viewModel.state.coins, large = false, modifier = Modifier.align(Alignment.Center).padding(top = 18.dp))
    }
}

/**
 * Полоса переключения: общая подложка без обводок, крупные кнопки (иконка над подписью),
 * выбранная — подсвечена. Одна и та же у «Улучшений» и «Хаоса».
 */
@Composable
private fun <T> SegmentStrip(items: List<T>, selected: T, label: @Composable (T) -> String, icon: ((T) -> ImageVector)?, onSelect: (T) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        items.forEach { item ->
            val on = item == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(if (icon != null) 60.dp else 44.dp)
                    .raised(on, strip = colors.surfaceContainerHigh)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(item) },
            ) {
                icon?.let { Icon(it(item), null, tint = if (on) colors.onSurface else colors.onSurfaceVariant, modifier = Modifier.size(24.dp)) }
                Text(
                    label(item),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (on) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * «Улучшения»: шапка и полоса категорий стоят, свайпом листаются только карточки.
 * С последней категории свайп уходит на соседнюю вкладку (вложенная прокрутка лент).
 */
@Composable
fun UpgradesTab(viewModel: ClickerViewModel, engine: ClickerEngine, categories: List<CategoryDef>, pager: PagerState) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        CoinsHeader(viewModel, engine)
        SegmentStrip(
            items = categories.indices.toList(),
            selected = pager.currentPage,
            label = { categories[it].name },
            icon = { categoryIcon(categories[it].id) },
            onSelect = { scope.launch { pager.animateScrollToPage(it) } },
        )
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { i ->
            val cards = engine.catalog.cards.filter { it.category == categories[i].id }.sortedBy { it.baseCost }
            CardsGrid(cards, viewModel, engine, emptyText = R.string.clicker_category_empty)
        }
    }
}

/** Карточки «Хаоса»: купленные, продающиеся сейчас и пропавшие (событие кончилось). */
enum class ChaosFilter(val label: Int, val empty: Int, val icon: ImageVector) {
    MINE(R.string.clicker_chaos_mine, R.string.clicker_chaos_mine_empty, Icons.Outlined.Inventory2),
    CURRENT(R.string.clicker_chaos_current, R.string.clicker_chaos_empty, Icons.Outlined.Timer),
    MISSED(R.string.clicker_chaos_missed, R.string.clicker_chaos_missed_empty, Icons.Outlined.HistoryToggleOff),
}

/** «Хаос»: как «Улучшения», но разделы — мои, текущие и пропавшие карточки. */
@Composable
fun ChaosTab(viewModel: ClickerViewModel, engine: ClickerEngine, pager: PagerState) {
    val scope = rememberCoroutineScope()
    val state = viewModel.state
    val now = System.currentTimeMillis() / 1000
    val chaos = engine.catalog.cards.filter { card -> engine.catalog.categories.firstOrNull { it.id == card.category }?.temporary == true }
    Column(Modifier.fillMaxSize()) {
        CoinsHeader(viewModel, engine)
        SegmentStrip(
            items = ChaosFilter.entries.toList(),
            selected = ChaosFilter.entries[pager.currentPage],
            label = { stringResource(it.label) },
            icon = { it.icon },
            onSelect = { scope.launch { pager.animateScrollToPage(it.ordinal) } },
        )
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { i ->
            val filter = ChaosFilter.entries[i]
            val cards = when (filter) {
                ChaosFilter.MINE -> chaos.filter { state.level(it.id) > 0 }
                ChaosFilter.CURRENT -> chaos.filter { it.onSale(now) }
                ChaosFilter.MISSED -> chaos.filter { state.level(it.id) == 0 && (it.availableUntil ?: Long.MAX_VALUE) <= now }
            }.sortedBy { it.baseCost }
            CardsGrid(cards, viewModel, engine, emptyText = filter.empty)
        }
    }
}

/** Карточки по две в ряд; в ряду — одной высоты. Тап — окно с подробностями и покупкой. */
@Composable
fun CardsGrid(cards: List<CardDef>, viewModel: ClickerViewModel, engine: ClickerEngine, emptyText: Int) {
    var opened by remember { mutableStateOf<CardDef?>(null) }
    if (cards.isEmpty()) {
        // Заглушка — сверху по центру
        Text(
            stringResource(emptyText),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
        )
        return
    }
    LazyColumn(
        // Отступ сверху — вчетверо больше прежнего: карточки не липнут к полосе разделов
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Усилители категории — отдельно, внизу списка, во всю ширину
        val (boosts, regular) = cards.partition { it.effect == CardEffect.BOOST }
        items(regular.chunked(2), key = { row -> row.first().id }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                row.forEach { card ->
                    CardTile(card, viewModel, engine, Modifier.weight(1f).fillMaxHeight()) { opened = card }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        items(boosts, key = { it.id }) { card ->
            CardTile(card, viewModel, engine, Modifier.fillMaxWidth().height(IntrinsicSize.Min), wide = true) { opened = card }
        }
    }
    opened?.let { card -> CardDialog(card, viewModel, engine, onDismiss = { opened = null }) }
}

/**
 * Карточка — таблица 2×2: иконка | название, что даёт и сколько всего; тонкая черта;
 * уровень | цена с монетой (между ними — тонкая вертикальная черта). Закрытая — серее,
 * вместо иконки красный замок.
 */
@Composable
private fun CardTile(card: CardDef, viewModel: ClickerViewModel, engine: ClickerEngine, modifier: Modifier, wide: Boolean = false, onClick: () -> Unit) {
    val res = LocalContext.current.resources
    val state = viewModel.state
    val level = state.level(card.id)
    val maxed = card.isMaxed(level)
    val open = viewModel.isAvailable(card) || maxed
    val affordable = viewModel.canBuy(card)
    val missed = !open && level == 0 && !viewModel.isOnSale(card)
    val value = effectValue(card, engine, state, engine.stats(state))
    val colors = MaterialTheme.colorScheme
    val line = colors.outlineVariant.copy(alpha = 0.5f)
    val dim = if (open) 1f else 0.55f

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (open) colors.surfaceContainerHigh else colors.surfaceContainerLow,
        shadowElevation = if (open) 1.5.dp else 0.5.dp,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box {
        // У широкого (усилитель) — по краям парят мини-иконки категории
        if (wide) FloatingCategoryIcons(card.category, Modifier.matchParentSize())
        Column(if (wide) Modifier.padding(horizontal = 40.dp) else Modifier) {
            Row(Modifier.weight(1f).padding(10.dp), verticalAlignment = Alignment.Top) {
                CardIcon(card, open, box = 40.dp, corner = 10.dp, iconSize = 22.dp)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        card.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.onSurface.copy(alpha = dim),
                    )
                    Text(
                        stringResource(card.effect.cardLabel()),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant.copy(alpha = dim),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        effectNumber(card.effect, value.now, res),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface.copy(alpha = dim),
                    )
                    if (card.effect == CardEffect.BOOST) {
                        val category = engine.catalog.categories.firstOrNull { it.id == card.target }?.name.orEmpty()
                        Text(
                            stringResource(R.string.clicker_boost_hint, number(card.baseValue * 100, 0), category),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant.copy(alpha = dim),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = line)
            Row(Modifier.height(IntrinsicSize.Min).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.clicker_level_short, level),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = dim),
                    modifier = Modifier.width(44.dp),
                )
                VerticalDivider(thickness = 0.5.dp, color = line, modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    when {
                        maxed -> Text(stringResource(R.string.clicker_max), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                        missed -> Text(stringResource(R.string.clicker_chaos_missed_label), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        else -> {
                            Icon(Icons.Filled.MonetizationOn, null, tint = CoinColor.copy(alpha = dim), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                formatCoins(engine.cost(state, card), res),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface.copy(alpha = if (affordable) 1f else 0.6f * dim),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

/** Иконка единицы эффекта: монета — для дохода и тапа, молния — для энергии. */
@Composable
private fun EffectIcon(effect: CardEffect, size: androidx.compose.ui.unit.Dp) {
    when (effect) {
        CardEffect.ENERGY_MAX, CardEffect.ENERGY_REGEN -> Icon(Icons.Outlined.Bolt, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(size))
        else -> Icon(Icons.Filled.MonetizationOn, null, tint = CoinColor, modifier = Modifier.size(size))
    }
}

/**
 * Окно покупки: крестик в углу; иконка, название и «Ур. N»; сколько карточка даёт сейчас
 * и сколько добавит следующий уровень (числом с монетой); крупно цена и большая «Купить».
 */
@Composable
private fun CardDialog(card: CardDef, viewModel: ClickerViewModel, engine: ClickerEngine, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    val haptics = LocalHapticFeedback.current
    val state = viewModel.state
    val level = state.level(card.id)
    val maxed = card.isMaxed(level)
    val value = effectValue(card, engine, state, engine.stats(state))
    val price = engine.cost(state, card)
    val canBuy = viewModel.canBuy(card)
    val colors = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh, shadowElevation = 8.dp) {
            Box {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.action_cancel))
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                ) {
                    CardIcon(card, open = true, box = 56.dp, corner = 16.dp, iconSize = 30.dp)
                    Text(card.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                    Text(stringResource(R.string.clicker_level_short, level), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)

                    // Сейчас даёт → сколько добавит следующий уровень
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                        EffectIcon(card.effect, 22.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(effectNumber(card.effect, value.now, res), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        value.next?.let { next ->
                            Spacer(Modifier.width(16.dp))
                            Text("+" + effectNumber(card.effect, next, res), style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            EffectIcon(card.effect, 18.dp)
                        }
                    }

                    if (card.requires.isNotEmpty() && !viewModel.isAvailable(card) && !maxed) {
                        Column(Modifier.padding(top = 16.dp)) {
                            card.requires.forEach { req ->
                                val ok = state.level(req.card) >= req.level
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (ok) Icons.Filled.CheckCircle else Icons.Outlined.Lock,
                                        null,
                                        tint = if (ok) colors.onSurfaceVariant else colors.error,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    val name = engine.catalog.cardsById[req.card]?.name ?: req.card
                                    Text(stringResource(R.string.clicker_need_item, name, req.level) + " (${state.level(req.card)})", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    if (maxed) {
                        Text(stringResource(R.string.clicker_max), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp)) {
                            Icon(Icons.Filled.MonetizationOn, null, tint = CoinColor, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(formatCoinsFull(price), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                        }
                        if (state.coins < price) {
                            Text(stringResource(R.string.clicker_not_enough, formatCoinsFull(price - state.coins)), style = MaterialTheme.typography.bodySmall, color = colors.error)
                        }
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(if (viewModel.buy(card)) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
                                onDismiss()
                            },
                            enabled = canBuy,
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .fillMaxWidth()
                                .height(52.dp),
                        ) { Text(stringResource(R.string.clicker_buy), style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
    }
}

/** Прокачка тапа: сила тапа, объём и восстановление энергии — шторкой с вкладки «Тап». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapUpgradesSheet(viewModel: ClickerViewModel, engine: ClickerEngine, onDismiss: () -> Unit) {
    val hidden = engine.catalog.categories.filter { it.hidden }.map { it.id }.toSet()
    val cards = engine.catalog.cards.filter { it.category in hidden }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.clicker_tap_upgrades), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            CoinsLine(viewModel.state.coins, large = false, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp))
            Column(Modifier.heightIn(max = 480.dp)) { CardsGrid(cards, viewModel, engine, emptyText = R.string.clicker_category_empty) }
        }
    }
}

/**
 * Мета-прогрессия на весь экран: цель прохождения и его завершение (перерождение или,
 * в последний раз, — заморозка), награды и улучшения за звёздную пыль. Закрывается
 * «назад», стрелкой или свайпом вбок, как страницы приложения.
 */
@Composable
fun MetaProgressDialog(viewModel: ClickerViewModel, engine: ClickerEngine, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    val state = viewModel.state
    val rules = engine.catalog.rules
    val frozen = engine.isFrozen(state)
    var confirming by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    // На весь окно, под системными панелями: они остаются прозрачными, без затемнения
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        SystemBarIcons(dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f)
        Surface(Modifier.fillMaxSize().swipeToClose(onDismiss), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.editor_close)) }
                    Text(stringResource(R.string.clicker_meta), style = MaterialTheme.typography.titleLarge)
                }
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                ) {
                    Text(stringResource(if (frozen) R.string.clicker_done else R.string.clicker_goal), style = MaterialTheme.typography.titleMedium)
                    if (frozen) {
                        Text(stringResource(R.string.clicker_done_text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        LinearProgressIndicator(
                            progress = { (state.earned / rules.goal).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .height(8.dp),
                        )
                        Text(
                            stringResource(R.string.clicker_earned, formatCoins(state.earned, res), formatCoins(rules.goal, res)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        if (engine.goalReached(state)) {
                            Button(onClick = { confirming = true }, modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                                Text(stringResource(if (engine.isLastRun(state)) R.string.clicker_finish_last else R.string.clicker_finish))
                            }
                        }
                    }

                    // Доход вне игры — сейчас (растёт улучшениями за звёздную пыль)
                    val stats = engine.stats(state)
                    Text(stringResource(R.string.clicker_offline), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
                    Text(
                        stringResource(R.string.clicker_offline_rate, number(stats.offlineRate * 100, 0)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(stringResource(R.string.clicker_offline_cap, number(stats.offlineCapHours, 1)), style = MaterialTheme.typography.bodyMedium)

                    // Награды за прохождения — в профиль
                    Text(stringResource(R.string.clicker_badges), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
                    Text(
                        stringResource(R.string.clicker_completed, state.runs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                        (1..rules.maxRuns).forEach { n ->
                            val got = state.runs >= n
                            Icon(
                                if (got) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                null,
                                tint = if (got) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.clicker_perks), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(pluralStringResource(R.plurals.clicker_stardust, state.stardust, state.stardust), style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        stringResource(R.string.clicker_perks_hint, rules.stardustPerRun),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    )
                    engine.catalog.perks.forEach { perk ->
                        PerkRow(perk, viewModel, engine) {
                            haptics.performHapticFeedback(if (viewModel.buyPerk(perk)) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
                        }
                    }
                }
            }
        }

        if (confirming) {
            val last = engine.isLastRun(state)
            AlertDialog(
                onDismissRequest = { confirming = false },
                title = { Text(stringResource(if (last) R.string.clicker_finish_last else R.string.clicker_finish_title)) },
                text = { Text(if (last) stringResource(R.string.clicker_finish_last_text) else stringResource(R.string.clicker_finish_text, rules.stardustPerRun)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirming = false
                        viewModel.finishRun()
                    }) { Text(stringResource(if (last) R.string.clicker_finish_last else R.string.clicker_finish)) }
                },
                dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

@Composable
private fun PerkRow(perk: PerkDef, viewModel: ClickerViewModel, engine: ClickerEngine, onBuy: () -> Unit) {
    val state = viewModel.state
    val level = state.perkLevel(perk.id)
    val title = perk.name.substringBefore(':').trim()
    val description = perk.name.substringAfter(':', "").trim()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description.isNotEmpty()) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.clicker_level, level, perk.maxLevel), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (level >= perk.maxLevel) {
            Text(stringResource(R.string.clicker_max), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FilledTonalButton(onClick = onBuy, enabled = engine.canBuyPerk(state, perk)) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(perk.costOf(level).toString())
            }
        }
    }
}

/**
 * Цель прохождения достигнута: поздравление и предложение начать следующее прохождение
 * (что обнулится, что останется) или, если оно было последним, — завершить игру.
 * «Позже» — окно больше не всплывает, завершить можно в мета-прогрессии.
 */
@Composable
fun GoalReachedDialog(viewModel: ClickerViewModel, engine: ClickerEngine) {
    val res = LocalContext.current.resources
    val state = viewModel.state
    val rules = engine.catalog.rules
    val last = engine.isLastRun(state)
    val next = state.runs + 2 // номер следующего прохождения
    AlertDialog(
        onDismissRequest = viewModel::dismissGoalReached,
        icon = { Icon(Icons.Filled.Star, null, tint = CoinColor, modifier = Modifier.size(40.dp)) },
        title = { Text(stringResource(R.string.clicker_goal_title)) },
        text = {
            Text(
                if (last) {
                    stringResource(R.string.clicker_goal_last_text, formatCoins(rules.goal, res))
                } else {
                    stringResource(R.string.clicker_goal_text, formatCoins(rules.goal, res), next, rules.stardustPerRun)
                },
            )
        },
        confirmButton = {
            Button(onClick = viewModel::finishRun) {
                Text(if (last) stringResource(R.string.clicker_finish_last) else stringResource(R.string.clicker_goal_next, next))
            }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissGoalReached) { Text(stringResource(R.string.clicker_later)) } },
    )
}

/**
 * Иконка карточки в рамке [box]. Без картинки — иконка категории на подложке (закрытая —
 * красный замок). С картинкой (assets/clicker/images/) — картинка без подложки; закрытая —
 * та же картинка, тусклее, с красным замком поверх. Оверлей (кольца и т. п.) — поверх всего,
 * может выходить за рамку: его обрезает только край карточки.
 */
@Composable
private fun CardIcon(card: CardDef, open: Boolean, box: androidx.compose.ui.unit.Dp, corner: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp) {
    val colors = MaterialTheme.colorScheme
    val image = card.image?.takeIf { it.isNotBlank() }
    var imageFailed by remember(image) { mutableStateOf(false) }
    val hasImage = image != null && !imageFailed
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(box)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(corner))
                .then(
                    when {
                        hasImage -> Modifier
                        open -> Modifier.background(colors.secondaryContainer)
                        else -> Modifier.background(colors.surfaceContainerHighest)
                    },
                ),
        ) {
            if (hasImage) {
                // Обычный AsyncImage, не Subcompose: карточки меряют высоту заранее, а тот этого не умеет
                coil3.compose.AsyncImage(
                    model = "file:///android_asset/clicker/images/$image",
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    alpha = if (open) 1f else 0.45f,
                    onError = { imageFailed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (open) {
                Icon(categoryIcon(card.category), null, tint = colors.onSecondaryContainer, modifier = Modifier.size(iconSize))
            }
        }
        card.overlay?.takeIf { it.isNotBlank() && hasImage }?.let { overlay ->
            coil3.compose.AsyncImage(
                model = "file:///android_asset/clicker/images/$overlay",
                contentDescription = null,
                alpha = if (open) 1f else 0.45f,
                // requiredSize — больше рамки: выходит за неё, по центру
                modifier = Modifier
                    .offset(box * card.overlayOffsetX.toFloat(), box * card.overlayOffsetY.toFloat())
                    .requiredSize(box * card.overlayScale.toFloat()),
            )
        }
        if (!open) Icon(Icons.Outlined.Lock, null, tint = colors.error, modifier = Modifier.size(iconSize))
    }
}

/**
 * Украшение широкой карточки-усилителя: по левому и правому краю парят мини-иконки
 * категории — без подложки, едва заметно покачиваясь. Рисуются прямо на холсте: карточка
 * меряет свою высоту заранее, а контейнеры вроде BoxWithConstraints такого не умеют.
 */
@Composable
private fun FloatingCategoryIcons(category: String, modifier: Modifier) {
    val painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(categoryIcon(category))
    val tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val time = androidx.compose.animation.core.rememberInfiniteTransition(label = "boostIcons").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(4_000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "boostIconsTime",
    )
    // x от края, доля высоты, размер, сдвиг фазы
    val spots = listOf(
        Triple(8.dp, 0.18f, 14.dp) to 0.0f,
        Triple(22.dp, 0.50f, 11.dp) to 0.33f,
        Triple(10.dp, 0.80f, 16.dp) to 0.66f,
    )
    androidx.compose.foundation.Canvas(modifier) {
        // Векторная иконка кэширует картинку под размер первого рисования — поэтому рисуем
        // всегда одного размера, а мельче делаем масштабом (иначе у маленькой обрезаны края)
        val base = 16.dp.toPx()
        spots.forEachIndexed { i, (spot, phase) ->
            val (x, y, sizeDp) = spot
            val s = sizeDp.toPx()
            listOf(true, false).forEach { left ->
                val shift = if (left) phase else (phase + 0.5f) % 1f
                val bob = kotlin.math.sin(2 * Math.PI * (time.value + shift)).toFloat()
                val cx = if (left) x.toPx() + s / 2 else size.width - x.toPx() - s / 2
                val cy = size.height * y + bob * 4.dp.toPx()
                withTransform({
                    translate(cx - base / 2, cy - base / 2)
                    val pivot = androidx.compose.ui.geometry.Offset(base / 2, base / 2)
                    rotate(bob * 12f * (if (i % 2 == 0) 1 else -1), pivot = pivot)
                    scale(s / base, s / base, pivot = pivot)
                }) {
                    with(painter) { draw(androidx.compose.ui.geometry.Size(base, base), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint)) }
                }
            }
        }
    }
}
