package dev.chaik.remember.feature.games.clicker

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Выгрузка шаров планеты (стадии 1–5) в PNG 512×512 на прозрачном фоне — те, что сейчас
 * рисуются кодом. Файлы — в Android/data/dev.chaik.remember/files/planet/; оттуда их кладут
 * в assets/clicker/planet/, где их можно заменить своими. У стадий 6–8 шар тот же, что у 5-й.
 *
 * Запуск (без удаления приложения):
 *   adb shell am instrument -w -e class dev.chaik.remember.feature.games.clicker.PlanetExport \
 *     dev.chaik.remember.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class PlanetExport {
    @Test
    fun export() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "planet").apply { mkdirs() }
        val px = 512
        for (stage in 1..5) {
            val image = ImageBitmap(px, px)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(px.toFloat(), px.toFloat())) {
                drawPlanetBallForExport(stage)
            }
            File(dir, "planet_$stage.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
