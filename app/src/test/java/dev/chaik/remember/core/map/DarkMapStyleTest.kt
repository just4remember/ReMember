package dev.chaik.remember.core.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DarkMapStyleTest {
    @Test
    fun `светлота переворачивается, цветное сохраняет оттенок`() {
        assertEquals("hsla(220, 15%, 11%, 1)", DarkMapStyle.darkColor("#fff"))
        assertEquals("hsla(220, 15%, 86%, 1)", DarkMapStyle.darkColor("#000000"))
        // Тёплый бежевый фон карты — тоже в холодный серый
        assertEquals("hsla(220, 15%, 14%, 1)", DarkMapStyle.darkColor("#f8f4f0"))
        assertEquals("hsla(120, 80%, 49%, 0.50)", DarkMapStyle.darkColor("rgba(0, 255, 0, 0.5)"))
        assertEquals("hsla(35, 46%, 26%, 0.49)", DarkMapStyle.darkColor("hsla(35,57%,80%,0.49)"))
    }

    @Test
    fun `не цвета не трогаются`() {
        assertNull(DarkMapStyle.darkColor("linear"))
        assertNull(DarkMapStyle.darkColor("Noto Sans Regular"))
    }

    @Test
    fun `перекрашиваются только цвета слоёв, вместе с выражениями`() {
        val style = """{"version":8,"layers":[
            {"id":"bg","type":"background","paint":{"background-color":"#f8f4f0"}},
            {"id":"road","type":"line","layout":{"line-cap":"round"},
             "paint":{"line-color":["interpolate",["linear"],["zoom"],5,"#fff",10,"#000"],"line-width":2}},
            {"id":"relief","type":"raster","paint":{"raster-opacity":0.5}},
            {"id":"square","type":"fill","paint":{"fill-pattern":"pedestrian_polygon"}}
        ]}"""
        val layers = Json.parseToJsonElement(DarkMapStyle.darken(style)).jsonObject.getValue("layers").jsonArray
        val road = layers[1].jsonObject
        val color = road.getValue("paint").jsonObject.getValue("line-color").jsonArray
        assertEquals("linear", color[1].jsonArray[0].jsonPrimitive.content)
        assertEquals("hsla(220, 15%, 11%, 1)", color[4].jsonPrimitive.content)
        assertEquals("round", road.getValue("layout").jsonObject.getValue("line-cap").jsonPrimitive.content)
        assertEquals("0.25", layers[2].jsonObject.getValue("paint").jsonObject.getValue("raster-brightness-max").jsonPrimitive.content)
        // Светлый узор пешеходной площади — ровной тёмной заливкой
        val square = layers[3].jsonObject.getValue("paint").jsonObject
        assertEquals(null, square["fill-pattern"])
        assertEquals("hsla(220, 12%, 19%, 1)", square.getValue("fill-color").jsonPrimitive.content)
    }
}
