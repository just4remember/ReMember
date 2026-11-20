package dev.chaik.remember.feature.games.clicker

import dev.chaik.remember.feature.games.clicker.engine.CardDef
import dev.chaik.remember.feature.games.clicker.engine.CardEffect
import dev.chaik.remember.feature.games.clicker.engine.ClickerEngine
import dev.chaik.remember.feature.games.clicker.engine.ClickerState
import dev.chaik.remember.feature.games.clicker.engine.ClickerStats
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Как играет виртуальный игрок: [activeHoursPerDay] в игре, разбитые на [sessionsPerDay]
 * сессий, между ними — вне игры. В игре тапает [tapsPerSecond] раз в секунду, пока есть
 * энергия, — но только первые [tapSecondsPerSession] секунд сессии (null — всю сессию).
 */
data class Schedule(
    val activeHoursPerDay: Double = 2.0,
    val sessionsPerDay: Int = 4,
    val tapsPerSecond: Double = 5.0,
    val tapSecondsPerSession: Double? = null,
) {
    val sessionSeconds: Double get() = activeHoursPerDay * 3600 / sessionsPerDay
    val gapHours: Double get() = (24 - activeHoursPerDay) / sessionsPerDay

    /** Сколько секунд сессии игрок тапает. */
    val tappingSeconds: Double get() = if (tapsPerSecond <= 0) 0.0 else min(sessionSeconds, tapSecondsPerSession ?: sessionSeconds)
}

/** Как игрок выбирает, что купить. */
enum class Strategy {
    /** Самое быстро окупающееся — игрок с таблицей. */
    BEST_PAYBACK,

    /** Считает примерно: случайное из пяти самых выгодных. */
    ROUGH,
}

data class Purchase(val activeHours: Double, val day: Int, val card: CardDef, val level: Int, val cost: Double, val waitedSeconds: Double)

data class Sample(
    val activeHours: Double,
    val day: Int,
    val incomePerHour: Double,
    val tapValue: Double,
    val energyRegen: Double,
    val offlineRate: Double,
    val offlineCapHours: Double,
    val incomeByCategory: Map<String, Double>,
    val earned: Double = 0.0,
)

data class SimResult(
    val schedule: Schedule,
    val strategy: Strategy,
    val finished: Boolean,
    val activeHours: Double,
    val days: Int,
    val purchases: List<Purchase>,
    val samples: List<Sample>,
    /** Категория → во сколько активных часов куплена её первая карточка. */
    val categoryOpened: Map<String, Double>,
    val finalState: ClickerState,
    /** Откуда коины: тапы, пассивный доход в игре, доход вне игры. */
    val fromTaps: Double = 0.0,
    val fromActive: Double = 0.0,
    val fromOffline: Double = 0.0,
)

/**
 * Симулятор баланса: играет по правилам [engine] в виртуальном времени. Сессия считается
 * посекундно (энергия, тапы, доход), время вне игры — сразу целиком, как в приложении.
 * Сотни часов игры проходят за доли секунды.
 *
 * Игрок умеет «копить на открытие»: если закрытая карточка вместе с уровнями, которых она
 * требует, окупается лучше всего остального, он качает эти уровни.
 */
class ClickerSimulator(
    private val engine: ClickerEngine,
    private val schedule: Schedule = Schedule(),
    private val strategy: Strategy = Strategy.BEST_PAYBACK,
    private val maxActiveHours: Double = 3000.0,
) {
    // Временные карточки («Хаос») — в наборе движка, только если симулируются события
    private val cards = engine.catalog.cards
    private val random = kotlin.random.Random(42)
    private val rules = engine.catalog.rules

    fun run(start: ClickerState = ClickerState()): SimResult {
        var state = start
        var stats = engine.stats(state)
        // Календарное время с начала прохождения, секунды — для событий «Хаоса»
        var now = 0L
        var target = pickTarget(state, stats, now)
        var active = 0.0
        var day = 0
        var sinceBuy = 0.0
        var nextSample = 0.0
        val purchases = ArrayList<Purchase>()
        val samples = ArrayList<Sample>()
        val opened = LinkedHashMap<String, Double>()
        var energy = engine.energyOf(state, stats)
        var tapCarry = 0.0
        var fromTaps = 0.0
        var fromActive = 0.0
        var fromOffline = 0.0

        fun buyWhilePossible() {
            while (true) {
                val card = target ?: return
                val level = state.level(card.id)
                val price = engine.cost(state, card, level)
                if (state.coins < price || !engine.canBuy(state, card, now)) {
                    // Событие кончилось, пока копили, — выбрать другое
                    if (!engine.isAvailable(state, card, now)) target = pickTarget(state, stats, now)
                    return
                }
                state = engine.buy(state, card, now)
                purchases += Purchase(active / 3600, day, card, level + 1, price, sinceBuy)
                if (card.category !in opened) opened[card.category] = active / 3600
                sinceBuy = 0.0
                stats = engine.stats(state)
                target = pickTarget(state, stats, now)
            }
        }

        outer@ while (true) {
            for (session in 0 until schedule.sessionsPerDay) {
                now = day * 86_400L + (session * 86_400L / schedule.sessionsPerDay)
                if (day > 0 || session > 0) {
                    // Вне игры: доход с коэффициентом и потолком — через движок, как в приложении
                    val before = state.earned
                    state = engine.returnAfter(state.copy(energy = energy), schedule.gapHours * 3600)
                    fromOffline += state.earned - before
                    energy = engine.energyOf(state, stats)
                    // Могли начаться или кончиться события — пересмотреть цель
                    target = pickTarget(state, stats, now)
                    buyWhilePossible()
                }
                var t = 0.0
                while (t < schedule.sessionSeconds) {
                    // Секунда игры: тапы (пока игрок тапает и есть энергия) и пассивный доход
                    var taps = 0.0
                    if (t < schedule.tappingSeconds) {
                        tapCarry += schedule.tapsPerSecond
                        taps = min(floor(tapCarry), floor(energy / rules.tapCost))
                        tapCarry -= floor(tapCarry)
                    }
                    val gain = taps * stats.tapValue + stats.incomePerSecond
                    fromTaps += taps * stats.tapValue
                    fromActive += stats.incomePerSecond
                    energy = min(stats.energyMax, energy - taps * rules.tapCost + stats.energyRegen)
                    state = state.copy(coins = state.coins + gain, earned = state.earned + gain)
                    t += 1
                    active += 1
                    sinceBuy += 1
                    if (active >= nextSample) {
                        samples += Sample(
                            active / 3600, day, stats.incomePerHour, stats.tapValue, stats.energyRegen,
                            stats.offlineRate, stats.offlineCapHours, stats.incomeByCategory, state.earned,
                        )
                        nextSample += 3600
                    }
                    if (target != null && state.coins >= engine.cost(state, target!!)) {
                        now = day * 86_400L + (session * 86_400L / schedule.sessionsPerDay) + t.toLong()
                        buyWhilePossible()
                    }
                    if (state.earned >= rules.goal) break@outer
                }
                if (active / 3600 > maxActiveHours) break@outer
            }
            day++
        }
        return SimResult(
            schedule = schedule,
            strategy = strategy,
            finished = engine.goalReached(state),
            activeHours = active / 3600,
            days = day + 1,
            purchases = purchases,
            samples = samples,
            categoryOpened = opened,
            finalState = state.copy(energy = energy),
            fromTaps = fromTaps,
            fromActive = fromActive,
            fromOffline = fromOffline,
        )
    }

    /** Вариант покупки: что купить сейчас, ради какой карточки и как быстро окупится весь путь. */
    private class Option(val next: CardDef, val payback: Double)

    private fun pickTarget(state: ClickerState, stats: ClickerStats, now: Long): CardDef? {
        val options = cards.filter { it.onSale(now) }.mapNotNull { option(state, stats, it) }
        return when (strategy) {
            Strategy.ROUGH -> options.sortedBy { it.payback }.take(5).randomOrNull(random)?.next
            Strategy.BEST_PAYBACK -> options.minByOrNull { it.payback }?.next
        }
    }

    /**
     * Окупаемость карточки вместе с путём к ней: если она закрыта, к её цене и отдаче
     * прибавляются уровни карточек, которых не хватает (и их собственные условия).
     */
    private fun option(state: ClickerState, stats: ClickerStats, card: CardDef): Option? {
        if (card.isMaxed(state.level(card.id))) return null
        var cost = 0.0
        var gain = 0.0
        var next: CardDef? = null
        val planned = HashMap<String, Int>()

        fun plan(c: CardDef, toLevel: Int, depth: Int): Boolean {
            if (depth > 6) return false
            for (req in c.requires) {
                val reqCard = engine.catalog.cardsById[req.card] ?: return false
                val have = planned[reqCard.id] ?: state.level(reqCard.id)
                if (have >= req.level) continue
                if (!plan(reqCard, req.level, depth + 1)) return false
            }
            val from = planned[c.id] ?: state.level(c.id)
            for (level in from until toLevel) {
                if (c.isMaxed(level)) return false
                cost += engine.cost(state, c, level)
                gain += dailyGain(state, stats, c, level)
                if (next == null) next = c
            }
            if (toLevel > from) planned[c.id] = toLevel
            return true
        }

        if (!plan(card, state.level(card.id) + 1, 0)) return null
        val first = next ?: return null
        return if (gain <= 0) null else Option(first, cost / gain)
    }

    /** На сколько коинов в день больше даст уровень [level] карточки (при нынешнем раскладе). */
    private fun dailyGain(state: ClickerState, stats: ClickerStats, card: CardDef, level: Int = state.level(card.id)): Double {
        val s = schedule.sessionsPerDay
        val value = card.valueOf(level)
        val prestige = engine.incomeMultiplier(state)
        val offlineHours = { cap: Double, rate: Double -> s * min(schedule.gapHours, cap) * rate }
        val incomeHours = schedule.activeHoursPerDay + offlineHours(stats.offlineCapHours, stats.offlineRate)
        val tapsPerDay = { maxE: Double, regen: Double ->
            s * min(schedule.tapsPerSecond * schedule.tappingSeconds, maxE + regen * schedule.tappingSeconds)
        }
        val taps = tapsPerDay(stats.energyMax, stats.energyRegen)
        val boostOf = { category: String ->
            cards.filter { it.effect == CardEffect.BOOST && it.target == category }.sumOf { it.total(state.level(it.id)) }
        }
        return when (card.effect) {
            // Доход растёт и сам, и через силу тапа
            CardEffect.INCOME -> value * (1 + boostOf(card.category)) * prestige * (incomeHours + stats.tapIncomeSeconds / 3600 * taps)
            CardEffect.TAP -> value * prestige * taps
            CardEffect.TAP_SECONDS -> value * stats.incomePerSecond * taps
            CardEffect.ENERGY_MAX -> (tapsPerDay(stats.energyMax + value, stats.energyRegen) - taps) * stats.tapValue
            CardEffect.ENERGY_REGEN -> (tapsPerDay(stats.energyMax, stats.energyRegen + value) - taps) * stats.tapValue
            CardEffect.OFFLINE_RATE -> {
                val newRate = min(stats.offlineRate + value, ClickerEngine.MAX_OFFLINE_RATE)
                stats.incomePerHour * (offlineHours(stats.offlineCapHours, newRate) - offlineHours(stats.offlineCapHours, stats.offlineRate))
            }
            CardEffect.OFFLINE_CAP -> stats.incomePerHour * (offlineHours(stats.offlineCapHours + value, stats.offlineRate) - offlineHours(stats.offlineCapHours, stats.offlineRate))
            CardEffect.BOOST -> {
                val category = card.target ?: return 0.0
                val boosted = stats.incomeByCategory[category] ?: return 0.0
                max(0.0, boosted / (1 + boostOf(category)) * value * (incomeHours + stats.tapIncomeSeconds / 3600 * taps))
            }
        }
    }
}
