package dev.chaik.remember.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMapsTest {
    @Test
    fun `область побольше весит больше, крошечная — почти одна общая часть`() {
        val tiny = OfflineMaps.estimateBytes(55.7510, 37.6200, 55.7500, 37.6190)
        val city = OfflineMaps.estimateBytes(55.95, 37.85, 55.55, 37.35)
        assertTrue(tiny < 11_000_000)
        assertTrue(city > tiny + 20_000_000)
    }

    @Test
    fun `поиск города разбирает границы как юг, север, запад, восток`() {
        val body = """[{"name":"Москва","display_name":"Москва, Центральный федеральный округ, Россия",
            "boundingbox":["55.1421","56.0212","36.8031","37.9674"]}]"""
        val result = CitySearch.parse(body).single()
        assertEquals("Москва", result.name)
        assertEquals(56.0212, result.bounds.latitudeNorth, 1e-6)
        assertEquals(55.1421, result.bounds.latitudeSouth, 1e-6)
        assertEquals(36.8031, result.bounds.longitudeWest, 1e-6)
        assertEquals(37.9674, result.bounds.longitudeEast, 1e-6)
    }
}
