package dev.chaik.remember.core.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chaik.remember.core.data.DayRepository
import dev.chaik.remember.core.database.DatabaseFactory
import dev.chaik.remember.core.model.DayEntry
import dev.chaik.remember.core.security.DatabaseKeyManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.time.LocalDate

/**
 * Полный круг на телефоне: экспорт с одного «телефона» (свой ключ чипа), восстановление
 * на другом (другой ключ). Всё на отдельных тестовых файлах и ключах — дневник не трогается.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.cacheDir, "backup-test")
    private val day = LocalDate.of(2026, 10, 24)

    @Before
    @After
    fun cleanUp() {
        listOf(SRC_DB, DST_DB).forEach(context::deleteDatabase)
        root.deleteRecursively()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        listOf(SRC, DST).forEach { name ->
            File(context.noBackupFilesDir, "$name.key").delete()
            keyStore.deleteEntry(name)
        }
    }

    private fun keys(name: String) = DatabaseKeyManager(context, name, "$name.key")

    @Test
    fun exportOnOnePhoneRestoreOnAnother() = runTest {
        // «Старый телефон»: запись дня и фото
        val srcKeys = keys(SRC)
        val srcDb = DatabaseFactory.create(context, srcKeys, SRC_DB)
        DayRepository(srcDb).save(DayEntry(date = day, text = "Запись из копии", mood = 20))
        val srcFiles = File(root, "src").apply { mkdirs() }
        val photo = byteArrayOf(1, 2, 3, 4, 5)
        File(srcFiles, "photos/thumbs").mkdirs()
        File(srcFiles, "photos/abc.webp").writeBytes(photo)
        File(srcFiles, "photos/thumbs/abc.webp").writeBytes(byteArrayOf(9))

        val file = File(root, "copy.rmbak")
        val src = BackupManager(context, { srcDb }, srcKeys, SRC_DB, srcFiles, File(root, "src-staging"), withSettings = false)
        assertTrue(src.export(Uri.fromFile(file), "пароль-копии".toCharArray()) > 0)
        srcDb.close()

        // «Новый телефон»: своя пустая база со своим ключом
        val dstKeys = keys(DST)
        val dstDb = DatabaseFactory.create(context, dstKeys, DST_DB)
        dstDb.openHelper.writableDatabase
        val dstFiles = File(root, "dst").apply { mkdirs() }
        val dst = BackupManager(context, { dstDb }, dstKeys, DST_DB, dstFiles, File(root, "dst-staging"), withSettings = false)

        assertThrows(WrongPasswordException::class.java) {
            kotlinx.coroutines.runBlocking { dst.prepareRestore(Uri.fromFile(file), "не тот".toCharArray()) }
        }
        val preview = dst.prepareRestore(Uri.fromFile(file), "пароль-копии".toCharArray())
        assertEquals(1, preview.photoCount)

        dst.markRestoreReady()
        dstDb.close()
        dst.applyPendingRestore() // так делает приложение при следующем запуске

        // Ключ базы теперь под чип «нового телефона», данные и фото на месте
        val restored = DatabaseFactory.create(context, keys(DST), DST_DB)
        assertEquals("Запись из копии", DayRepository(restored).observe(day).first()?.text)
        restored.close()
        assertArrayEquals(photo, File(dstFiles, "photos/abc.webp").readBytes())
        assertTrue(File(dstFiles, "photos/thumbs/abc.webp").exists())
        assertFalse(File(root, "dst-staging/restore").exists())
    }

    private companion object {
        const val SRC = "backup_test_src"
        const val DST = "backup_test_dst"
        const val SRC_DB = "backup-src.db"
        const val DST_DB = "backup-dst.db"
    }
}
