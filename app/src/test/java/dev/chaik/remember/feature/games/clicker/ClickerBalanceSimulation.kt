package dev.chaik.remember.feature.games.clicker

import dev.chaik.remember.feature.games.clicker.engine.CardDef
import dev.chaik.remember.feature.games.clicker.engine.CardEffect
import dev.chaik.remember.feature.games.clicker.engine.ClickerCatalog
import dev.chaik.remember.feature.games.clicker.engine.ClickerEngine
import dev.chaik.remember.feature.games.clicker.engine.ClickerState
import dev.chaik.remember.feature.games.clicker.engine.PerkEffect
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Прогон баланса: виртуальные игроки проходят игру по набору карточек из
 * assets/clicker/cards.json, отчёт — app/build/reports/clicker/balance.html.
 * Отдельно: ./gradlew testDebugUnitTest --tests "*ClickerBalanceSimulation*" --rerun
 */
class ClickerBalanceSimulation {
    // Игрок в игре начинает с нуля; в симуляции — 60 в час с начала, чтобы мог начать и тот, кто не тапает
    private val catalog = ClickerCatalog.parse(File("src/main/assets/clicker/cards.json").readText())
        .let { it.copy(rules = it.rules.copy(baseIncomePerHour = 60.0)) }
    private val engine = ClickerEngine(catalog)

    /**
     * События «Хаоса», как их мог бы присылать сервер: раз в [everyDays] дней на [lastDays] дня
     * три карточки — для середины, второй половины и конца игры (цены со ступеней лестницы),
     * окупаются вдвое быстрее обычных карточек той же цены. Купленное остаётся до перерождения.
     */
    private fun chaosEvents(everyDays: Int = 7, lastDays: Int = 3, firstDay: Int = 7, days: Int = 400): List<CardDef> {
        val ladder = catalog.cards.filter { it.effect == CardEffect.INCOME }.sortedBy { it.baseCost }
        return (firstDay until days step everyDays).flatMapIndexed { e, day ->
            listOf(0.40, 0.60, 0.80).mapIndexed { i, at ->
                val base = ladder[(ladder.size * at).toInt()]
                CardDef(
                    id = "chaos_${e}_$i", category = "chaos", name = "Хаос ${e + 1}.${i + 1}", effect = CardEffect.INCOME,
                    baseCost = base.baseCost, costGrowth = base.costGrowth, baseValue = base.baseValue * 2, valueGrowth = base.valueGrowth, maxLevel = 15,
                    availableFrom = day * 86_400L, availableUntil = (day + lastDays) * 86_400L,
                )
            }
        }
    }

    private val withChaos = ClickerEngine(catalog.copy(cards = catalog.cards + chaosEvents()))

    /** Звёздная пыль — по приоритету (как выбрал бы игрок, который мало тапает). */
    private fun spendStardust(state: ClickerState): ClickerState {
        val order = listOf(PerkEffect.INCOME, PerkEffect.OFFLINE_RATE, PerkEffect.DISCOUNT, PerkEffect.OFFLINE_CAP, PerkEffect.START_COINS)
        var s = state
        while (true) {
            val perk = order.asSequence().flatMap { e -> catalog.perks.filter { it.effect == e } }.firstOrNull { engine.canBuyPerk(s, it) } ?: return s
            s = engine.buyPerk(s, perk)
        }
    }

    /** Три прохождения подряд: между ними — перерождение и покупка улучшений. */
    private fun threeRuns(eng: ClickerEngine, schedule: Schedule): List<SimResult> {
        val runs = ArrayList<SimResult>()
        var start = ClickerState()
        repeat(3) {
            val r = ClickerSimulator(eng, schedule).run(start)
            runs += r
            start = spendStardust(eng.prestige(r.finalState))
        }
        return runs
    }

    @Test
    fun report() {
        // Основной игрок — как большинство: тапает только первую минуту сессии, растит пассивный доход
        val typical = Schedule(tapSecondsPerSession = 60.0)
        val main = ClickerSimulator(engine, typical).run()
        val others = listOf(
            "Тапает всю сессию" to ClickerSimulator(engine, Schedule()).run(),
            "Не тапает" to ClickerSimulator(engine, Schedule(tapsPerSecond = 0.0)).run(),
            "Мало играет" to ClickerSimulator(engine, typical.copy(activeHoursPerDay = 1.0, sessionsPerDay = 3)).run(),
            "Много играет" to ClickerSimulator(engine, typical.copy(activeHoursPerDay = 4.0, sessionsPerDay = 6)).run(),
            "Считает примерно" to ClickerSimulator(engine, typical, Strategy.ROUGH).run(),
            "С «Хаосом» (раз в неделю)" to ClickerSimulator(withChaos, typical).run(),
        )
        val plain = threeRuns(engine, typical)
        val chaos = threeRuns(withChaos, typical)
        // Какие улучшения будут у игрока к началу 2-го, 3-го и 4-го прохождения
        val perksBefore = (1..3).map { n ->
            var s = ClickerState()
            repeat(n) { s = spendStardust(engine.prestige(s.copy(earned = catalog.rules.goal))) }
            s.perks.entries.joinToString { (id, l) -> "${catalog.perks.first { it.id == id }.name.substringBefore(':')} ×$l" }
        }
        val runsHtml = buildString {
            append("<h2>Три прохождения</h2><p class=\"note\">За каждое — 100 звёздной пыли; основной игрок тратит её по приоритету: доход, доход вне игры, скидка, запас, стартовый капитал.</p>")
            append("<div class=\"wrap\"><table><tr><th>Прохождение</th><th>Без «Хаоса», ч / дней</th><th>С «Хаосом», ч / дней</th><th>Улучшения после него</th></tr>")
            plain.indices.forEach { i ->
                append("<tr><td>${i + 1}</td><td>${"%.0f".format(Locale.US, plain[i].activeHours)} / ${plain[i].days}</td>")
                append("<td>${"%.0f".format(Locale.US, chaos[i].activeHours)} / ${chaos[i].days}</td><td>${perksBefore[i]}</td></tr>")
            }
            append("</table></div>")
        }
        val out = File("build/reports/clicker/balance.html")
        out.parentFile.mkdirs()
        out.writeText(BalanceReport.html(catalog, main, others, runsHtml))

        println("Кликер: ${"%.0f".format(Locale.US, main.activeHours)} активных часов, ${main.days} дней, ${main.purchases.size} покупок → ${out.absolutePath}")
        println("  первая покупка в категории: " + main.categoryOpened.entries.joinToString { "${it.key}=${"%.0f".format(Locale.US, it.value)}" })
        BalanceReport.categoryBands(main).forEach { (band, row) ->
            println("  $band ч: " + row.entries.sortedBy { it.key }.joinToString { (id, v) -> "$id п${"%.0f".format(Locale.US, v.first * 100)}/д${"%.0f".format(Locale.US, v.second * 100)}" })
        }
        println("  дольше всего ждал: " + main.purchases.sortedByDescending { it.waitedSeconds }.take(4).joinToString { "${"%.0f".format(Locale.US, it.waitedSeconds / 60)} мин на ${"%.0f".format(Locale.US, it.activeHours)} ч (${it.card.id} ур.${it.level})" })
        main.purchases.groupBy { (it.activeHours / 100).toInt() * 100 }.toSortedMap().forEach { (band, list) ->
            val waits = list.map { it.waitedSeconds / 60 }.sorted()
            fun q(p: Double) = waits[((waits.size - 1) * p).toInt()]
            println("  $band–${band + 100} ч: покупок ${list.size}, ожидание мин: 90% ${"%.0f".format(Locale.US, q(0.9))}, макс ${"%.0f".format(Locale.US, waits.last())}")
        }
        val marks = listOf(1.0, 10.0, 50.0, 100.0, 200.0, 300.0).mapNotNull { h -> main.samples.firstOrNull { it.activeHours >= h }?.let { h to it } }
        println("  доход в час: " + marks.joinToString { (h, s) -> "${h.toInt()} ч → ${BalanceReport.fmt(s.incomePerHour)}" } + ", в конце → ${BalanceReport.fmt(main.samples.last().incomePerHour)}")
        println("  уровни планеты: " + catalog.rules.levels.mapIndexed { i, need -> "${i + 1}=${main.samples.firstOrNull { it.earned >= need }?.activeHours?.let { "%.0f".format(Locale.US, it) } ?: "—"} ч" }.joinToString())
        others.forEach { (name, r) -> println("  $name: ${"%.0f".format(Locale.US, r.activeHours)} ч, ${r.days} дн., прошёл: ${r.finished}") }
        println("  три прохождения без «Хаоса»: " + plain.joinToString { "%.0f".format(Locale.US, it.activeHours) } + " ч; с «Хаосом»: " + chaos.joinToString { "%.0f".format(Locale.US, it.activeHours) } + " ч")

        // Сторож баланса: правка карточек, которая сильно сдвинула прохождение, видна сразу
        assertTrue("Игра не проходится", main.finished)
        assertTrue("Прохождение ${main.activeHours} ч — далеко от 300–400", main.activeHours in 280.0..420.0)
    }
}
