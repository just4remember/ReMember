package dev.chaik.remember.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupSettingsTest {
    @Test
    fun `значения возвращаются со своими типами`() {
        val prefs = mapOf(
            "settings" to mapOf(
                "theme" to "LIGHT",
                "day_start_minutes" to 120,
                "welcome" to true,
                "custom_accent" to 0xFF5B6CF0L,
                "t9_line_alpha" to 0.5f,
                "disabled_pages" to setOf("map", "gym"),
            ),
            "time" to mapOf("alarms" to "[]"),
        )
        val (language, files) = BackupSettings.decode(BackupSettings.encode("ru", prefs))
        assertEquals("ru", language)
        assertEquals(prefs, files)
        // Int и Long различаются: SharedPreferences падают, если прочитать не тем типом
        assertEquals(Int::class, files.getValue("settings").getValue("day_start_minutes")::class)
        assertEquals(Long::class, files.getValue("settings").getValue("custom_accent")::class)
    }

    @Test
    fun `пустой язык — как в системе, а без поля — не трогать`() {
        assertEquals("", BackupSettings.decode(BackupSettings.encode("", emptyMap())).first)
        assertNull(BackupSettings.decode("""{"prefs":{}}""").first)
    }

    @Test
    fun `непонятные значения пропускаются`() {
        val text = """{"prefs":{"games":{"a":{"t":"x","v":1},"b":{"t":"i"},"c":{"t":"i","v":7}}}}"""
        assertEquals(mapOf("games" to mapOf("c" to 7)), BackupSettings.decode(text).second)
    }
}
