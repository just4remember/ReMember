package dev.chaik.remember.feature.time.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class WorldCitiesTest {
    @Test
    fun `все пояса существуют, названия уникальны`() {
        WorldCities.all.forEach { ZoneId.of(it.zone) }
        assertEquals(WorldCities.all.size, WorldCities.all.map { it.id }.toSet().size)
        assertTrue(WorldCities.all.size > 150)
    }

    @Test
    fun `города по странам, внутри по алфавиту`() {
        val groups = citiesByCountry(WorldCities.all, russian = true)
        val countries = groups.map { it.first }
        assertEquals(countries.sortedWith(java.text.Collator.getInstance(java.util.Locale("ru"))::compare), countries)
        assertEquals("Австралия", countries.first())
        val kz = groups.first { it.first == "Казахстан" }.second.map { it.nameRu }
        assertEquals(listOf("Актау", "Алматы", "Астана"), kz)
        val russia = groups.first { it.first == "Россия" }.second.map { it.nameRu }
        assertEquals(russia.sortedWith(java.text.Collator.getInstance(java.util.Locale("ru"))::compare), russia)
    }

    @Test
    fun `поиск по названию и стране на обоих языках`() {
        fun ids(q: String) = WorldCities.all.filter { it.matches(q) }.map { it.id }
        assertTrue("Moscow" in ids("моск"))
        assertTrue("Moscow" in ids("MOSCOW"))
        assertTrue("Tokyo" in ids("япония"))
        assertTrue("Kyiv" in ids("киев"))
        assertTrue("Rostov-on-Don" in ids("ростов на дону"))
        assertTrue("Kirov" in ids("Киров"))
        assertTrue("Chisinau" in ids("кишинев"))
    }
}
