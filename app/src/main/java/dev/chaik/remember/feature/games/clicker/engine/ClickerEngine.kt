package dev.chaik.remember.feature.games.clicker.engine

import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.min

/**
 * Состояние игры. Время — в секундах (с какого момента считать — дело вызывающего:
 * в приложении это реальные часы, в симуляторе — виртуальные).
 */
@Serializable
data class ClickerState(
    val coins: Double = 0.0,
    /** Заработано за текущее прохождение — от него открываются категории и считается цель. */
    val earned: Double = 0.0,
    /** Уровни карточек, в том числе купленных во время событий «Хаоса» — до перерождения. */
    val levels: Map<String, Int> = emptyMap(),
    val energy: Double = Double.NaN,
    /** Сколько раз игра пройдена; дошли до [Rules.maxRuns] — игра заморожена. */
    val runs: Int = 0,
    /** Звёздная пыль и постоянные улучшения за неё — переживают перерождение. */
    val stardust: Int = 0,
    val perks: Map<String, Int> = emptyMap(),
    /** Отладка: прибавка к доходу в час и к тапу (кнопки в debug-сборке). */
    val debugIncome: Double = 0.0,
    val debugTap: Double = 0.0,
    /** Поздравление с достигнутой целью уже показано (в этом прохождении). */
    val goalSeen: Boolean = false,
) {
    fun level(id: String): Int = levels[id] ?: 0

    fun perkLevel(id: String): Int = perks[id] ?: 0
}

/** Всё, что сейчас даёт игроку его набор карточек. */
data class ClickerStats(
    /** Коинов за тап: постоянная часть плюс [tapIncomeSeconds] секунд пассивного дохода. */
    val tapValue: Double,
    val tapIncomeSeconds: Double,
    val incomePerHour: Double,
    val energyMax: Double,
    val energyRegen: Double,
    val offlineRate: Double,
    val offlineCapHours: Double,
    /** Доход в час по категориям — нужен усилителям категорий. */
    val incomeByCategory: Map<String, Double>,
) {
    val incomePerSecond: Double get() = incomePerHour / 3600
}

/**
 * Правила игры поверх набора карточек: цены, открытие карточек, тапы, доход в игре и вне её,
 * перерождение. Чистые функции без Android — их же гоняет симулятор баланса.
 */
class ClickerEngine(val catalog: ClickerCatalog) {
    private val rules get() = catalog.rules

    /** Сумма эффекта постоянных улучшений одного вида. */
    fun perkTotal(state: ClickerState, effect: PerkEffect): Double =
        catalog.perks.filter { it.effect == effect }.sumOf { it.value * state.perkLevel(it.id) }

    /** Множитель ко всему доходу и тапу от постоянных улучшений. */
    fun incomeMultiplier(state: ClickerState): Double = 1 + perkTotal(state, PerkEffect.INCOME)

    /** Цена уровня [level] карточки с учётом скидки от улучшений. */
    fun cost(state: ClickerState, card: CardDef, level: Int = state.level(card.id)): Double =
        card.cost(level) * (1 - min(perkTotal(state, PerkEffect.DISCOUNT), MAX_DISCOUNT))

    fun stats(state: ClickerState): ClickerStats {
        var tap = rules.tapValue
        var tapSeconds = rules.tapIncomeSeconds + perkTotal(state, PerkEffect.TAP_SECONDS)
        var energyMax = rules.energyMax + perkTotal(state, PerkEffect.ENERGY_MAX)
        var regen = rules.energyRegen + perkTotal(state, PerkEffect.ENERGY_REGEN)
        var offlineRate = rules.offlineRate + perkTotal(state, PerkEffect.OFFLINE_RATE)
        var offlineCap = rules.offlineCapHours + perkTotal(state, PerkEffect.OFFLINE_CAP)
        val rawIncome = HashMap<String, Double>()
        val boosts = HashMap<String, Double>()
        for (card in catalog.cards) {
            val levels = state.level(card.id)
            if (levels == 0) continue
            val total = card.total(levels)
            when (card.effect) {
                CardEffect.INCOME -> rawIncome.merge(card.category, total, Double::plus)
                CardEffect.TAP -> tap += total
                CardEffect.TAP_SECONDS -> tapSeconds += total
                CardEffect.ENERGY_MAX -> energyMax += total
                CardEffect.ENERGY_REGEN -> regen += total
                CardEffect.OFFLINE_RATE -> offlineRate += total
                CardEffect.OFFLINE_CAP -> offlineCap += total
                CardEffect.BOOST -> card.target?.let { boosts.merge(it, total, Double::plus) }
            }
        }
        val prestige = incomeMultiplier(state)
        val byCategory = rawIncome.mapValues { (category, income) -> income * (1 + (boosts[category] ?: 0.0)) * prestige }
        val incomePerHour = byCategory.values.sum() + rules.baseIncomePerHour * prestige + state.debugIncome
        return ClickerStats(
            tapValue = tap * prestige + incomePerHour / 3600 * tapSeconds + state.debugTap,
            tapIncomeSeconds = tapSeconds,
            incomePerHour = incomePerHour,
            energyMax = energyMax,
            energyRegen = regen,
            offlineRate = min(offlineRate, MAX_OFFLINE_RATE),
            offlineCapHours = offlineCap,
            incomeByCategory = byCategory,
        )
    }

    fun isCategoryOpen(state: ClickerState, categoryId: String): Boolean {
        val category = catalog.categories.firstOrNull { it.id == categoryId } ?: return false
        return state.earned >= category.unlockAt
    }

    /**
     * Карточку видно и можно покупать: категория открыта, выполнено условие и, если карточка
     * временная, сейчас её срок. [nowSeconds] — секунды Unix; null — без часов (симулятор),
     * тогда временных карточек нет.
     */
    fun isAvailable(state: ClickerState, card: CardDef, nowSeconds: Long? = null): Boolean {
        if (!isCategoryOpen(state, card.category)) return false
        if (!card.onSale(nowSeconds)) return false
        return card.requires.all { state.level(it.card) >= it.level }
    }

    /**
     * Игра пройдена последний раз: всё накопленное остаётся, но больше ничего не копится —
     * доход, тапы и покупки ничего не дают.
     */
    fun isFrozen(state: ClickerState): Boolean = state.runs >= rules.maxRuns

    fun canBuy(state: ClickerState, card: CardDef, nowSeconds: Long? = null): Boolean {
        if (isFrozen(state)) return false
        val level = state.level(card.id)
        return isAvailable(state, card, nowSeconds) && !card.isMaxed(level) && state.coins >= cost(state, card, level)
    }

    /** Купить следующий уровень; не хватает коинов или нельзя — состояние то же. */
    fun buy(state: ClickerState, card: CardDef, nowSeconds: Long? = null): ClickerState {
        if (!canBuy(state, card, nowSeconds)) return state
        val level = state.level(card.id)
        return state.copy(coins = state.coins - cost(state, card, level), levels = state.levels + (card.id to level + 1))
    }

    /** Энергия у нового игрока — полная шкала. */
    fun energyOf(state: ClickerState, stats: ClickerStats = stats(state)): Double =
        if (state.energy.isNaN()) stats.energyMax else min(state.energy, stats.energyMax)

    /** Сколько тапов можно сделать сейчас. */
    fun tapsAvailable(state: ClickerState): Int = floor(energyOf(state) / rules.tapCost).toInt()

    /** [count] тапов подряд (сколько хватит энергии). */
    fun tap(state: ClickerState, count: Int = 1): ClickerState {
        if (isFrozen(state)) return state
        val stats = stats(state)
        val energy = energyOf(state, stats)
        val done = min(count, floor(energy / rules.tapCost).toInt())
        if (done <= 0) return state
        val gain = done * stats.tapValue
        return state.copy(coins = state.coins + gain, earned = state.earned + gain, energy = energy - done * rules.tapCost)
    }

    /** [seconds] в игре: пассивный доход целиком и восстановление энергии. */
    fun play(state: ClickerState, seconds: Double): ClickerState {
        if (isFrozen(state)) return state
        val stats = stats(state)
        val gain = stats.incomePerSecond * seconds
        return state.copy(
            coins = state.coins + gain,
            earned = state.earned + gain,
            energy = min(stats.energyMax, energyOf(state, stats) + stats.energyRegen * seconds),
        )
    }

    /**
     * Игрок вернулся через [seconds] вне игры: доход с коэффициентом и не больше, чем за
     * потолок часов; энергия восстанавливается как обычно. Отрицательное время (часы
     * перевели назад) не даёт ничего.
     */
    fun returnAfter(state: ClickerState, seconds: Double): ClickerState {
        if (seconds <= 0 || isFrozen(state)) return state
        val stats = stats(state)
        val counted = min(seconds, stats.offlineCapHours * 3600)
        val gain = stats.incomePerSecond * counted * stats.offlineRate
        return state.copy(
            coins = state.coins + gain,
            earned = state.earned + gain,
            energy = min(stats.energyMax, energyOf(state, stats) + stats.energyRegen * seconds),
        )
    }

    fun goalReached(state: ClickerState): Boolean = state.earned >= rules.goal

    /** Уровень игрока с 1: сколько порогов [Rules.levels] пройдено за это прохождение. */
    fun level(state: ClickerState): Int = rules.levels.count { state.earned >= it }.coerceAtLeast(1)

    /** Сколько ещё заработать до следующего уровня; null — уровень последний. */
    fun toNextLevel(state: ClickerState): Double? = rules.levels.getOrNull(level(state))?.let { it - state.earned }

    /** Сколько ещё заработать до цели прохождения (0 — достигнута). */
    fun toGoal(state: ClickerState): Double = (rules.goal - state.earned).coerceAtLeast(0.0)

    /** Доля пути от текущего уровня к следующему (0..1); на последнем — к цели прохождения. */
    fun levelProgress(state: ClickerState): Float {
        val l = level(state)
        val from = rules.levels.getOrElse(l - 1) { 0.0 }
        val to = rules.levels.getOrNull(l) ?: rules.goal.takeIf { it > from } ?: return 1f
        return ((state.earned - from) / (to - from)).toFloat().coerceIn(0f, 1f)
    }

    /** Это прохождение — последнее: после него игра замрёт, а не начнётся заново. */
    fun isLastRun(state: ClickerState): Boolean = state.runs + 1 >= rules.maxRuns

    /**
     * Завершить прохождение (только после цели). Не последнее — перерождение: прогресс
     * заново (коины, карточки, в том числе купленные в «Хаосе»), остаются звёздная пыль
     * (+[Rules.stardustPerRun]) и улучшения, стартовые коины от улучшений — сразу.
     * Последнее — игра замирает со всем накопленным ([isFrozen]).
     */
    fun prestige(state: ClickerState): ClickerState {
        if (!goalReached(state) || isFrozen(state)) return state
        if (isLastRun(state)) return state.copy(runs = state.runs + 1)
        val reborn = ClickerState(runs = state.runs + 1, stardust = state.stardust + rules.stardustPerRun, perks = state.perks)
        return reborn.copy(coins = perkTotal(reborn, PerkEffect.START_COINS))
    }

    fun canBuyPerk(state: ClickerState, perk: PerkDef): Boolean {
        if (isFrozen(state)) return false
        val level = state.perkLevel(perk.id)
        return level < perk.maxLevel && state.stardust >= perk.costOf(level)
    }

    /** Купить уровень улучшения за звёздную пыль. */
    fun buyPerk(state: ClickerState, perk: PerkDef): ClickerState {
        if (!canBuyPerk(state, perk)) return state
        val level = state.perkLevel(perk.id)
        val next = state.copy(stardust = state.stardust - perk.costOf(level), perks = state.perks + (perk.id to level + 1))
        // Стартовые коины, купленные в начале прохождения, — сразу
        return if (perk.effect == PerkEffect.START_COINS && next.earned == 0.0) next.copy(coins = next.coins + perk.value) else next
    }

    companion object {
        /** Вне игры доход не может быть больше, чем в игре. */
        const val MAX_OFFLINE_RATE = 0.9

        /** Скидка от улучшений не больше половины цены. */
        const val MAX_DISCOUNT = 0.5
    }
}

