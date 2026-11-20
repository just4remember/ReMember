package dev.chaik.remember.feature.games.clicker

import dev.chaik.remember.feature.games.clicker.engine.ClickerCatalog
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** HTML-отчёт по прогонам симулятора: сводка, графики (SVG) и таблицы. */
object BalanceReport {

    fun html(catalog: ClickerCatalog, main: SimResult, others: List<Pair<String, SimResult>>, extra: String = ""): String = buildString {
        append(
            """
            <!doctype html><html lang="ru"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Баланс кликера</title>
            <style>
            :root{--bg:#fafafa;--fg:#1d1d1f;--muted:#6e6e73;--line:#d2d2d7;--accent:#3a6ff7;--accent2:#e8590c;--card:#fff}
            @media (prefers-color-scheme:dark){:root{--bg:#161618;--fg:#f2f2f2;--muted:#9a9aa0;--line:#38383c;--accent:#7aa2ff;--accent2:#ff8a4c;--card:#1f1f22}}
            body{background:var(--bg);color:var(--fg);font:15px/1.45 system-ui,sans-serif;margin:0;padding:24px 16px;max-width:980px;margin:auto}
            h1{font-size:24px;margin:0 0 4px} h2{font-size:18px;margin:32px 0 8px}
            p.note{color:var(--muted);margin:4px 0 12px}
            table{border-collapse:collapse;width:100%;background:var(--card);font-size:14px}
            th,td{border-bottom:1px solid var(--line);padding:6px 8px;text-align:right} th:first-child,td:first-child{text-align:left}
            th{color:var(--muted);font-weight:600}
            .wrap{overflow-x:auto} svg{width:100%;height:auto;background:var(--card);border:1px solid var(--line);border-radius:8px}
            .axis{stroke:var(--line)} .tick{fill:var(--muted);font-size:11px} .l1{fill:none;stroke:var(--accent);stroke-width:2} .l2{fill:none;stroke:var(--accent2);stroke-width:1.5}
            .ok{color:#2b8a3e} .bad{color:#c92a2a}
            </style></head><body>
            """.trimIndent(),
        )
        append("<h1>Баланс кликера</h1>")
        append("<p class=\"note\">Набор карточек v${catalog.version}: ${catalog.cards.size} карточек, ${catalog.categories.size} категорий, цель — заработать ${fmt(catalog.rules.goal)}. ")
        append("Основной игрок: ${describe(main)}.</p>")

        append("<h2>Сводка</h2><div class=\"wrap\"><table><tr><th>Игрок</th><th>Прошёл</th><th>Активных часов</th><th>Дней</th><th>Покупок</th><th>Самое долгое ожидание</th></tr>")
        (listOf("Основной" to main) + others).forEach { (name, r) ->
            val longest = r.purchases.maxOfOrNull { it.waitedSeconds } ?: 0.0
            append("<tr><td>$name<br><span style=\"color:var(--muted)\">${describe(r)}</span></td>")
            append("<td class=\"${if (r.finished) "ok" else "bad"}\">${if (r.finished) "да" else "нет"}</td>")
            append("<td>${"%.0f".format(Locale.US, r.activeHours)}</td><td>${r.days}</td><td>${r.purchases.size}</td><td>${duration(longest)}</td></tr>")
        }
        append("</table></div>")
        append(extra)

        val total = main.fromTaps + main.fromActive + main.fromOffline
        if (total > 0) {
            append("<h2>Откуда коины</h2><p>Тапы — ${pct(main.fromTaps / total)}, пассивный доход в игре — ${pct(main.fromActive / total)}, ")
            append("доход вне игры — ${pct(main.fromOffline / total)}.</p>")
        }
        append("<h2>Пассивный доход, коинов в час</h2><p class=\"note\">Логарифмическая шкала: ровная линия — равномерный рост.</p>")
        append(chart(main.samples.map { it.activeHours to it.incomePerHour }, log = true))

        append("<h2>Коинов за тап</h2>")
        append(chart(main.samples.map { it.activeHours to it.tapValue }, log = true))

        append("<h2>Ожидание следующей покупки</h2><p class=\"note\">Минут активной игры между покупками: синяя — самое долгое за каждые 5 часов, оранжевая — среднее. Всплески — места, где игра «застревает».</p>")
        val buckets = main.purchases.groupBy { floor(it.activeHours / 5) * 5 }.toSortedMap()
        append(
            chart(
                buckets.map { (h, list) -> h to list.maxOf { it.waitedSeconds } / 60 },
                second = buckets.map { (h, list) -> h to list.map { it.waitedSeconds }.average() / 60 },
            ),
        )

        append("<h2>Покупок за час игры</h2>")
        append(chart(buckets.map { (h, list) -> h to list.size / 5.0 }))

        val cats = catalog.categories.filterNot { it.temporary }
        append("<h2>Категории по ходу игры</h2><p class=\"note\">Первая покупка в категории и какую долю покупок (п.) и пассивного дохода (д.) она даёт в каждые 50 часов. Если старые категории продолжают получать покупки — они не брошены.</p>")
        append("<div class=\"wrap\"><table><tr><th>Часы</th>")
        cats.forEach { append("<th>${it.name}<br><span style=\"color:var(--muted)\">с ${main.categoryOpened[it.id]?.let { h -> "%.0f ч".format(Locale.US, h) } ?: "—"}</span></th>") }
        append("</tr>")
        categoryBands(main).forEach { (band, row) ->
            append("<tr><td>$band–${band + 50}</td>")
            cats.forEach { c -> val (p, d) = row[c.id] ?: (0.0 to 0.0); append("<td>п. ${pct0(p)} · д. ${pct0(d)}</td>") }
            append("</tr>")
        }
        append("</table></div>")

        append("<h2>Уровни карточек в конце</h2><div class=\"wrap\"><table><tr><th>Карточка</th><th>Эффект</th><th>Уровень</th><th>Цена след. уровня</th></tr>")
        catalog.cards.forEach { card ->
            val level = main.finalState.level(card.id)
            append("<tr><td>${card.name}</td><td>${card.effect.name.lowercase()}</td><td>$level${card.maxLevel?.let { " / $it" } ?: ""}</td><td>${if (card.isMaxed(level)) "—" else fmt(card.cost(level))}</td></tr>")
        }
        append("</table></div>")

        append("<h2>Прочее в конце</h2><p>Восстановление энергии: ${"%.2f".format(Locale.US, main.samples.lastOrNull()?.energyRegen ?: 0.0)}/с, ")
        append("доход вне игры: ${"%.0f".format(Locale.US, (main.samples.lastOrNull()?.offlineRate ?: 0.0) * 100)}%, ")
        append("запас вне игры: ${"%.1f".format(Locale.US, main.samples.lastOrNull()?.offlineCapHours ?: 0.0)} ч.</p>")
        append("</body></html>")
    }

    fun describe(r: SimResult): String =
        "${fmtHours(r.schedule.activeHoursPerDay)} в день, ${r.schedule.sessionsPerDay} сессии, " +
            "${"%.0f".format(Locale.US, r.schedule.tapsPerSecond)} тапов/с, " +
            if (r.strategy == Strategy.BEST_PAYBACK) "берёт самое выгодное" else "берёт одно из пяти выгодных"

    private fun pct(x: Double) = "%.1f%%".format(Locale.US, x * 100)

    private fun pct0(x: Double) = "%.0f%%".format(Locale.US, x * 100)

    /** По 50 часов: категория → (доля покупок, доля пассивного дохода в конце отрезка). */
    fun categoryBands(r: SimResult): List<Pair<Int, Map<String, Pair<Double, Double>>>> {
        val bands = r.purchases.groupBy { (it.activeHours / 50).toInt() * 50 }.toSortedMap()
        return bands.map { (band, list) ->
            val sample = r.samples.lastOrNull { it.activeHours <= band + 50 } ?: r.samples.last()
            val income = sample.incomeByCategory.values.sum().takeIf { it > 0 } ?: 1.0
            val byCat = list.groupingBy { it.card.category }.eachCount()
            val ids = byCat.keys + sample.incomeByCategory.keys
            band to ids.associateWith { id -> (byCat[id] ?: 0).toDouble() / list.size to (sample.incomeByCategory[id] ?: 0.0) / income }
        }
    }

    private fun fmtHours(h: Double) = if (h == floor(h)) "${h.toInt()} ч" else "%.1f ч".format(Locale.US, h)

    /** 1.23K, 4.5M, … — как будет в игре. */
    fun fmt(v: Double): String {
        if (abs(v) < 1000) return "%.0f".format(Locale.US, v)
        val units = listOf("K", "M", "B", "T", "aa", "ab", "ac", "ad", "ae", "af", "ag", "ah", "ai", "aj")
        var i = -1
        var x = v
        while (abs(x) >= 1000 && i < units.lastIndex) {
            x /= 1000
            i++
        }
        return "%.3g".format(Locale.US, x) + units[i]
    }

    private fun duration(seconds: Double): String = when {
        seconds < 120 -> "%.0f с".format(Locale.US, seconds)
        seconds < 7200 -> "%.0f мин".format(Locale.US, seconds / 60)
        else -> "%.1f ч".format(Locale.US, seconds / 3600)
    }

    /** Линейный график по точкам (x — активные часы). [log] — логарифмическая ось Y. */
    private fun chart(points: List<Pair<Double, Double>>, log: Boolean = false, second: List<Pair<Double, Double>> = emptyList()): String {
        if (points.isEmpty()) return "<p>нет данных</p>"
        val w = 900.0
        val h = 260.0
        val left = 64.0
        val bottom = 28.0
        val top = 10.0
        val right = 12.0
        val tf = { y: Double -> if (log) log10(max(y, 1e-9)) else y }
        val all = points + second
        val xMax = all.maxOf { it.first }.coerceAtLeast(1.0)
        var yMin = all.minOf { tf(it.second) }
        var yMax = all.maxOf { tf(it.second) }
        if (!log) yMin = 0.0
        if (log) {
            yMin = floor(yMin)
            yMax = ceil(yMax)
        }
        if (yMax <= yMin) yMax = yMin + 1
        val px = { x: Double -> left + x / xMax * (w - left - right) }
        val py = { y: Double -> h - bottom - (tf(y) - yMin) / (yMax - yMin) * (h - top - bottom) }
        val path = { list: List<Pair<Double, Double>> ->
            list.joinToString(" ") { (x, y) -> "%.1f,%.1f".format(Locale.US, px(x), py(y)) }
        }
        return buildString {
            append("<svg viewBox=\"0 0 ${w.toInt()} ${h.toInt()}\">")
            append("<line class=\"axis\" x1=\"$left\" y1=\"${h - bottom}\" x2=\"${w - right}\" y2=\"${h - bottom}\"/>")
            append("<line class=\"axis\" x1=\"$left\" y1=\"$top\" x2=\"$left\" y2=\"${h - bottom}\"/>")
            // Ось X — часы
            val xStep = listOf(10.0, 25.0, 50.0, 100.0, 200.0).first { xMax / it <= 10 }
            var x = 0.0
            while (x <= xMax) {
                append("<text class=\"tick\" x=\"${px(x)}\" y=\"${h - 8}\" text-anchor=\"middle\">${x.toInt()} ч</text>")
                x += xStep
            }
            // Ось Y
            val ticks = if (log) {
                val step = max(1, ceil((yMax - yMin) / 8).toInt())
                (yMin.toInt()..yMax.toInt() step step).map { 10.0.pow(it) }
            } else {
                (0..4).map { yMin + (yMax - yMin) * it / 4 }
            }
            ticks.forEach { t ->
                val y = py(t)
                append("<line class=\"axis\" x1=\"$left\" y1=\"$y\" x2=\"${w - right}\" y2=\"$y\" stroke-dasharray=\"2 4\"/>")
                append("<text class=\"tick\" x=\"${left - 6}\" y=\"${y + 4}\" text-anchor=\"end\">${fmt(t).takeIf { log } ?: "%.1f".format(Locale.US, t)}</text>")
            }
            if (second.isNotEmpty()) append("<polyline class=\"l2\" points=\"${path(second)}\"/>")
            append("<polyline class=\"l1\" points=\"${path(points)}\"/>")
            append("</svg>")
        }
    }
}
