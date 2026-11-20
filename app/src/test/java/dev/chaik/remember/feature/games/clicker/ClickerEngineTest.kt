package dev.chaik.remember.feature.games.clicker

import dev.chaik.remember.feature.games.clicker.engine.CardDef
import dev.chaik.remember.feature.games.clicker.engine.CardEffect
import dev.chaik.remember.feature.games.clicker.engine.CategoryDef
import dev.chaik.remember.feature.games.clicker.engine.ClickerCatalog
import dev.chaik.remember.feature.games.clicker.engine.ClickerEngine
import dev.chaik.remember.feature.games.clicker.engine.ClickerState
import dev.chaik.remember.feature.games.clicker.engine.PerkDef
import dev.chaik.remember.feature.games.clicker.engine.PerkEffect
import dev.chaik.remember.feature.games.clicker.engine.Requirement
import dev.chaik.remember.feature.games.clicker.engine.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ClickerEngineTest {
    private val farm = CardDef("farm", "c1", "Ферма", CardEffect.INCOME, baseCost = 100.0, costGrowth = 1.5, baseValue = 3600.0)
    private val mill = CardDef("mill", "c1", "Мельница", CardEffect.INCOME, baseCost = 1000.0, costGrowth = 1.5, baseValue = 36000.0, requires = listOf(Requirement("farm", 2)))
    private val rate = CardDef("rate", "c2", "Вне игры", CardEffect.OFFLINE_RATE, baseCost = 10.0, costGrowth = 2.0, baseValue = 0.1, maxLevel = 1)
    private val catalog = ClickerCatalog(
        version = 1,
        rules = Rules(goal = 1e6),
        categories = listOf(CategoryDef("c1", "Первая"), CategoryDef("c2", "Вторая", unlockAt = 500.0)),
        cards = listOf(farm, mill, rate),
    )
    private val engine = ClickerEngine(catalog)

    @Test
    fun `цена растёт с каждым уровнем`() {
        assertEquals(100.0, farm.cost(0), 1e-9)
        assertEquals(225.0, farm.cost(2), 1e-9)
    }

    @Test
    fun `каждый следующий уровень даёт больше, прибавка растёт линейно`() {
        val card = CardDef("c", "c1", "К", CardEffect.INCOME, 1.0, 1.1, baseValue = 10.0, valueStep = 0.5)
        assertEquals(10.0, card.valueOf(0), 1e-9)
        assertEquals(15.0, card.valueOf(1), 1e-9)
        assertEquals(20.0, card.valueOf(2), 1e-9)
        assertEquals(45.0, card.total(3), 1e-9)
    }

    @Test
    fun `покупка списывает коины и даёт доход`() {
        val state = engine.buy(ClickerState(coins = 150.0), farm)
        assertEquals(50.0, state.coins, 1e-9)
        assertEquals(1, state.level("farm"))
        assertEquals(3600.0, engine.stats(state).incomePerHour, 1e-9)
        // Не хватает — ничего не меняется
        assertEquals(state, engine.buy(state, farm))
    }

    @Test
    fun `карточка открывается по условию и по категории`() {
        assertFalse(engine.isAvailable(ClickerState(), mill))
        assertTrue(engine.isAvailable(ClickerState(levels = mapOf("farm" to 2)), mill))
        assertFalse(engine.isAvailable(ClickerState(earned = 499.0), rate))
        assertTrue(engine.isAvailable(ClickerState(earned = 500.0), rate))
    }

    @Test
    fun `тап тратит энергию и приносит коины`() {
        val state = engine.tap(ClickerState(), 10)
        assertEquals(10.0, state.coins, 1e-9)
        assertEquals(490.0, state.energy, 1e-9)
        // Энергии не хватает на все — сколько есть
        assertEquals(3.0, engine.tap(ClickerState(energy = 3.0), 10).coins, 1e-9)
    }

    @Test
    fun `сила тапа даёт секунды пассивного дохода`() {
        val state = ClickerState(levels = mapOf("farm" to 1)) // 1 коин в секунду
        // 1 коин за тап + 0.2 секунды дохода
        assertEquals(1.2, engine.stats(state).tapValue, 1e-9)
    }

    @Test
    fun `вне игры — доход с коэффициентом и не дольше потолка`() {
        val state = ClickerState(levels = mapOf("farm" to 1)) // 3600 в час
        assertEquals(3600 * 0.4, engine.returnAfter(state, 3600.0).coins, 1e-6)
        // 10 часов, а потолок 3 — считаются только 3
        assertEquals(3 * 3600 * 0.4, engine.returnAfter(state, 10 * 3600.0).coins, 1e-6)
        // Часы перевели назад — ничего
        assertEquals(0.0, engine.returnAfter(state, -3600.0).coins, 1e-9)
    }

    @Test
    fun `перерождение после цели — всё заново, пыль и улучшения остаются`() {
        assertEquals(ClickerState(earned = 1e5), engine.prestige(ClickerState(earned = 1e5)))
        val reborn = engine.prestige(ClickerState(coins = 5.0, earned = 1e6, levels = mapOf("farm" to 3)))
        assertEquals(100, reborn.stardust)
        assertEquals(1, reborn.runs)
        assertEquals(0.0, reborn.coins, 1e-9)
        assertTrue(reborn.levels.isEmpty())
    }

    @Test
    fun `уровень игрока — по заработанному за прохождение`() {
        val e = ClickerEngine(catalog.copy(rules = catalog.rules.copy(levels = listOf(0.0, 100.0, 1000.0))))
        assertEquals(1, e.level(ClickerState()))
        assertEquals(100.0, e.toNextLevel(ClickerState())!!, 1e-9)
        assertEquals(2, e.level(ClickerState(earned = 550.0)))
        assertEquals(0.5f, e.levelProgress(ClickerState(earned = 550.0)), 1e-6f)
        assertEquals(3, e.level(ClickerState(earned = 5000.0)))
        assertEquals(null, e.toNextLevel(ClickerState(earned = 5000.0)))
    }

    @Test
    fun `после третьего прохождения игра замирает со всем накопленным`() {
        var s = ClickerState(coins = 500.0, earned = 1e6, levels = mapOf("farm" to 3), runs = 2, stardust = 40)
        assertTrue(engine.isLastRun(s))
        s = engine.prestige(s)
        assertTrue(engine.isFrozen(s))
        // Ничего не сброшено…
        assertEquals(500.0, s.coins, 1e-9)
        assertEquals(3, s.level("farm"))
        // …и больше ничего не копится
        assertEquals(s, engine.play(s, 3600.0))
        assertEquals(s, engine.returnAfter(s, 3600.0))
        assertEquals(s, engine.tap(s, 10))
        assertEquals(s, engine.buy(s, farm))
        assertEquals(s, engine.prestige(s))
    }

    @Test
    fun `улучшения за пыль — цена растёт с уровнем, эффект сразу`() {
        val perks = listOf(
            PerkDef("stars", "Доход", PerkEffect.INCOME, 0.15, cost = 25, maxLevel = 4),
            PerkDef("discount", "Скидка", PerkEffect.DISCOUNT, 0.05, cost = 20, maxLevel = 4),
        )
        val e = ClickerEngine(catalog.copy(perks = perks))
        var s = ClickerState(stardust = 100, levels = mapOf("farm" to 1))
        s = e.buyPerk(s, perks[0]) // 25
        s = e.buyPerk(s, perks[0]) // 50
        assertEquals(25, s.stardust)
        assertEquals(3600 * 1.3 + 60 * 0.0, e.stats(s).incomePerHour, 1e-6)
        s = e.buyPerk(s, perks[1]) // 20
        assertEquals(5, s.stardust)
        assertEquals(farm.cost(1) * 0.95, e.cost(s, farm), 1e-9)
        // Не хватает пыли — ничего
        assertEquals(s, e.buyPerk(s, perks[0]))
    }

    @Test
    fun `временная карточка продаётся только в свой срок`() {
        val event = CardDef("ev", "c1", "Событие", CardEffect.INCOME, 10.0, 1.1, 1.0, availableFrom = 1_000, availableUntil = 2_000)
        val withEvent = ClickerEngine(catalog.copy(cards = catalog.cards + event))
        val rich = ClickerState(coins = 100.0)
        assertFalse(withEvent.isAvailable(rich, event, nowSeconds = 999))
        assertTrue(withEvent.isAvailable(rich, event, nowSeconds = 1_500))
        assertFalse(withEvent.isAvailable(rich, event, nowSeconds = 2_000))
        // Без часов (симулятор) временных карточек нет, обычные — есть
        assertFalse(withEvent.isAvailable(rich, event))
        assertTrue(withEvent.isAvailable(rich, farm, nowSeconds = 1_500))
    }

    @Test
    fun `набор карточек приложения читается и id не повторяются`() {
        val real = ClickerCatalog.parse(File("src/main/assets/clicker/cards.json").readText())
        assertEquals(real.cards.size, real.cards.map { it.id }.toSet().size)
        real.cards.forEach { card ->
            assertTrue(card.id, real.categories.any { it.id == card.category })
            card.requires.forEach { req -> assertTrue(card.id, real.cardsById.containsKey(req.card)) }
        }
    }
}
