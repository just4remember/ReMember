package dev.chaik.remember.app.navigation

import dev.chaik.remember.core.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationTest {
    @Test
    fun `по умолчанию — четыре вкладки и «Ещё»`() {
        val s = AppSettings()
        assertEquals(Destination.DefaultTabs + Destination.More, s.islandTabs())
        assertEquals(
            listOf(Destination.Feed, Destination.Search, Destination.Stats, Destination.Finance, Destination.Health, Destination.Gym, Destination.Network, Destination.Time, Destination.Games, Destination.Places, Destination.Settings),
            s.moreTiles(),
        )
    }

    @Test
    fun `убранная вкладка уходит в «Ещё», добавленная — пропадает оттуда`() {
        val s = AppSettings(islandTabs = listOf("Today", "Gym", "Calendar"))
        assertEquals(listOf(Destination.Today, Destination.Gym, Destination.Calendar, Destination.More), s.islandTabs())
        val tiles = s.moreTiles()
        assertTrue(Destination.Notes in tiles && Destination.Tasks in tiles)
        assertTrue(Destination.Gym !in tiles)
        assertEquals(Destination.Settings, tiles.last())
    }

    @Test
    fun `выключенный раздел пропадает из «Ещё» и вкладок, но остаётся в списке «Страниц»`() {
        val s = AppSettings(disabledPages = listOf("Gym", "Settings"), islandTabs = listOf("Today", "Notes", "Gym"))
        assertTrue(Destination.Gym !in s.moreTiles())
        assertTrue(Destination.Gym !in s.islandTabs())
        assertTrue(Destination.Gym in s.moreOrderAll())
        // «Настройки» выключить нельзя
        assertEquals(Destination.Settings, s.moreTiles().last())
    }

    @Test
    fun `неверные настройки — по умолчанию`() {
        // Одна вкладка — мало; «Настройки» и «Ещё» вкладками не ставятся; незнакомые имена пропускаются
        assertEquals(Destination.DefaultTabs + Destination.More, AppSettings(islandTabs = listOf("Today")).islandTabs())
        assertEquals(
            listOf(Destination.Today, Destination.Notes, Destination.More),
            AppSettings(islandTabs = listOf("Today", "Settings", "More", "Нечто", "Notes")).islandTabs(),
        )
    }

    @Test
    fun `свой порядок плиток, новые разделы — в конец`() {
        val s = AppSettings(moreOrder = listOf("Gym", "Feed"))
        assertEquals(listOf(Destination.Gym, Destination.Feed, Destination.Search), s.moreTiles().take(3))
    }
}
