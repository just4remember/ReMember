package dev.chaik.remember.core.raskobki

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaskobkiModelTest {
    private val day = LocalDate.of(2026, 10, 1)

    private fun str(value: String) = """{"stringValue":"$value"}"""
    private fun node(id: String, answer: String, vararg parts: String) =
        """{"mapValue":{"fields":{"nodeId":${str(id)},"firstLetter":${str(answer.take(1))},
        "acceptedHashes":{"arrayValue":{"values":[${str(sha256(answer))}]}},
        "parts":{"arrayValue":{"values":[${parts.joinToString(",")}]}}}}}"""

    private fun document(schema: Int = 1, isReal: Boolean = true): String {
        val root = listOf(str("за"), node("outer", "работал", str("тогда "), node("inner", "работа", str("труд"))))
        return """{"fields":{"schemaVersion":{"integerValue":"$schema"},"isReal":{"booleanValue":$isReal},
        "date":${str(day.toString())},"puzzleId":${str("$day-q1")},"era":${str("э")},"region":${str("р")},
        "template":{"mapValue":{"fields":{"fact":${str("факт")},"sourceUrl":${str("https://a.b")},
        "root":{"arrayValue":{"values":[${root.joinToString(",")}]}}}}}}}"""
    }

    private fun parse(text: String): RaskobkiPuzzle? {
        val fields = Json.parseToJsonElement(text).jsonObject.getValue("fields").jsonObject
        return parseRaskobkiPuzzle(firestoreFields(fields))
    }

    @Test
    fun `вложенные скобки открываются изнутри`() {
        val puzzle = assertNotNull(parse(document())).let { parse(document())!! }
        assertEquals(2, puzzle.total)
        assertEquals(listOf("inner"), puzzle.ready(emptyList()).map { it.id })
        assertEquals(listOf("outer"), puzzle.ready(listOf("inner")).map { it.id })
    }

    @Test
    fun `ответ принимается только для доступной скобки`() {
        val puzzle = parse(document())!!
        assertEquals("inner", puzzle.match(" РАБОТА ", emptyList())?.id)
        assertNull(puzzle.match("работал", emptyList()))
        assertEquals("outer", puzzle.match("работал", listOf("inner"))?.id)
    }

    @Test
    fun `незнакомый выпуск уходит на сайт`() {
        assertNull(parse(document(schema = 2)))
        assertNull(parse(document(isReal = false)))
    }

    @Test
    fun `нормализация и проверка ответа`() {
        assertEquals("еж и уж", normalizeAnswer("  Ёж   и  Уж "))
        assertTrue(isValidAnswer("Санкт-Петербург"))
        assertFalse(isValidAnswer("abc"))
        assertFalse(isValidAnswer("слово1"))
    }

    @Test
    fun `очки и сохранность считаются как на сайте`() {
        assertEquals(83, RaskobkiState(w = 1, h = listOf("a", "b", "c")).score)
        assertEquals(0, RaskobkiState(w = 100).score)
        assertEquals(RaskobkiGrade.V, RaskobkiGrade.of(95))
        assertEquals(RaskobkiGrade.II, RaskobkiGrade.of(50))
        assertEquals(RaskobkiGrade.I, RaskobkiGrade.of(49))
    }

    @Test
    fun `ключ попытки совпадает с алгоритмом сайта`() {
        // FNV-1a от пустой строки
        assertEquals(2166136261L.toString(36), attemptKey(""))
    }

    private fun doneOn(date: LocalDate) = RaskobkiState(
        c = true,
        completedAt = date.atTime(12, 0).atZone(RaskobkiZone).toInstant().toEpochMilli(),
    )

    @Test
    fun `серия живёт до конца следующего дня и считает только день выхода`() {
        val today = LocalDate.of(2026, 11, 1)
        val yesterday = today.minusDays(1)
        val before = today.minusDays(2)
        val progress = mapOf(yesterday to doneOn(yesterday), before to doneOn(before))
        assertEquals(2, raskobkiStreak(progress, today).size)
        assertEquals(0, raskobkiStreak(progress, today.plusDays(2)).size)
        // Архивный выпуск, закрытый позже своей даты, серию не продлевает
        assertEquals(0, raskobkiStreak(mapOf(yesterday to doneOn(today)), today).size)
    }

    @Test
    fun `код входа находится и в завёрнутой ссылке`() {
        assertEquals("abc", parseOobCode("https://x.app/__/auth/action?mode=signIn&oobCode=abc&apiKey=k"))
        assertEquals("abc", parseOobCode(" https://x.app/?link=https%3A%2F%2Fy.app%2F%3FoobCode%3Dabc%26mode%3DsignIn "))
        assertNull(parseOobCode("просто текст"))
    }

    @Test
    fun `полностью открытая скобка стоит 15 очков`() {
        assertEquals(85, RaskobkiState(rv = listOf("a")).score)
    }

    @Test
    fun `объединение прогресса ничего не теряет`() {
        val merged = mergeStates(
            RaskobkiState(s = listOf("a"), w = 2, e = 10, completedAt = 5, c = true),
            RaskobkiState(s = listOf("b"), w = 1, e = 20, completedAt = 3),
        )
        assertEquals(listOf("a", "b"), merged.s)
        assertEquals(2, merged.w)
        assertEquals(20L, merged.e)
        assertEquals(3L, merged.completedAt)
        assertTrue(merged.c)
    }

    @Test
    fun `числа и строки уходят в Firestore своими типами`() {
        val value = Json.parseToJsonElement("""{"n":3,"t":"3","b":true,"l":["a"]}""")
        val back = firestoreToJson(jsonToFirestore(value).jsonObject)
        assertEquals(value, back)
    }

    @Test
    fun `номер скобки идёт по дереву и не содержит ответа`() {
        val puzzle = parse(document())!!
        assertEquals("b0", puzzle.analyticsId("outer"))
        assertEquals("b0.0", puzzle.analyticsId("inner"))
        assertNull(puzzle.analyticsId("нет"))
    }

    @Test
    fun `метрика скобки и завершения без текста ответов`() {
        val puzzle = parse(document())!!
        val progress = progressMetric(puzzle, "inner", 1)
        assertEquals(setOf("puzzle_id", "bracket_id"), progress.params.keys)
        val state = RaskobkiState(s = listOf("inner"), x = mapOf("inner" to "работа"), w = 1, e = 30)
        val completed = completedMetric(puzzle, state, 1)
        assertFalse(completed.params.values.any { "работа" in it })
        assertEquals("98", completed.params["score"])
    }

    @Test
    fun `очередь метрик теряет самые старые события`() {
        val first = RaskobkiMetric("a", 1, emptyMap())
        val second = RaskobkiMetric("b", 2, emptyMap())
        assertEquals(listOf(second), appendMetric(listOf(first), second, cap = 1))
    }

    @Test
    fun `средний счёт и рекорд только по решённым выпускам`() {
        val stats = raskobkiStats(
            listOf(RaskobkiState(c = true), RaskobkiState(c = true, w = 5), RaskobkiState(w = 40)),
        )
        assertEquals(95, stats.average)
        assertEquals(100, stats.best)
        assertEquals(RaskobkiStats(0, 0), raskobkiStats(emptyList()))
    }
}
