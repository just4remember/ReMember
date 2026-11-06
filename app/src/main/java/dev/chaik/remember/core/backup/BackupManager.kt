package dev.chaik.remember.core.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import android.app.usage.StorageStatsManager
import android.os.Process
import android.os.storage.StorageManager
import dev.chaik.remember.core.database.DatabaseFactory
import dev.chaik.remember.core.database.ReMemberDatabase
import dev.chaik.remember.core.security.DatabaseKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Сколько места занимают данные приложения. */
data class StorageUsage(
    val databaseBytes: Long,
    val photoBytes: Long,
    val photoCount: Int,
    /** Скачанные города и то, что карта запомнила сама. */
    val mapBytes: Long = 0,
    /** Само приложение: код и ресурсы. */
    val appBytes: Long = 0,
) {
    val totalBytes: Long get() = databaseBytes + photoBytes + mapBytes + appBytes
}

/** Проверенная копия, готовая заменить данные: когда сделана и сколько в ней фото. */
data class RestorePreview(val createdAt: Instant, val photoCount: Int)

/** Копия от более новой версии приложения — её база здесь не откроется. */
class NewerBackupException : IOException("Копия из более новой версии приложения")

/** Описание копии внутри архива. */
@Serializable
private data class BackupManifest(
    val format: Int,
    val createdAt: String,
    val databaseVersion: Int,
    val photos: Int,
)

/**
 * Резервная копия: база, ключ базы, фото и настройки ([BackupSettings]) — одним файлом,
 * зашифрованным паролем копии.
 *
 * Сама база зашифрована ключом из чипа этого телефона, поэтому в копию кладётся и её ключ
 * (под паролем). При восстановлении ключ перешифровывается ключом чипа нового телефона.
 *
 * Восстановление идёт в два шага: [prepareRestore] распаковывает и проверяет копию рядом
 * с данными, а подмена — [applyPendingRestore] — при следующем запуске, до открытия базы.
 * Так подмена не пересекается с работающим приложением, а оборванная — доделается
 * при следующем запуске.
 */
class BackupManager(
    private val context: Context,
    private val database: () -> ReMemberDatabase,
    private val keyManager: DatabaseKeyManager,
    // Ниже — только для тестов, чтобы не трогать настоящие данные
    private val databaseName: String = DatabaseFactory.DATABASE_NAME,
    private val filesRoot: File = context.filesDir,
    private val stagingRoot: File = context.noBackupFilesDir,
    /** Настройки — общие для процесса; тесты на телефоне не должны трогать настоящие. */
    private val withSettings: Boolean = true,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val photosDir get() = File(filesRoot, PHOTOS_DIR)
    private val dbFile get() = context.getDatabasePath(databaseName)
    private val staging get() = File(stagingRoot, STAGING_DIR)

    suspend fun usage(): StorageUsage = withContext(Dispatchers.IO) {
        val dbBytes = listOf("", "-wal", "-shm").sumOf { File(dbFile.path + it).takeIf(File::exists)?.length() ?: 0L }
        val photos = photosDir.walkTopDown().filter { it.isFile && it.parentFile == photosDir }.toList()
        val photoBytes = photosDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        // База карт MapLibre лежит рядом с фото; в копию она не входит
        val mapBytes = listOf("", "-wal", "-shm", "-journal").sumOf { File(filesRoot, MAP_DATABASE + it).takeIf(File::exists)?.length() ?: 0L }
        StorageUsage(dbBytes, photoBytes, photos.size, mapBytes, appBytes())
    }

    /** Размер самого приложения по данным системы; если она не ответила — размер установленного файла. */
    private fun appBytes(): Long = runCatching {
        context.getSystemService(StorageStatsManager::class.java)
            .queryStatsForUid(StorageManager.UUID_DEFAULT, Process.myUid())
            .appBytes
    }.getOrElse {
        val info = context.applicationInfo
        (listOf(info.sourceDir) + info.splitSourceDirs.orEmpty()).sumOf { File(it).length() }
    }

    /** Записать копию в [target] (файл, выбранный пользователем). Возвращает размер файла. */
    suspend fun export(target: Uri, password: CharArray): Long = withContext(Dispatchers.IO) {
        val db = database().openHelper.writableDatabase
        // Всё из журнала WAL — в основной файл, чтобы копировать один файл
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val photos = photosDir.walkTopDown().filter { it.isFile }.toList()
        val manifest = BackupManifest(
            format = FORMAT,
            createdAt = Instant.now().toString(),
            databaseVersion = db.version,
            photos = photos.count { it.parentFile == photosDir },
        )
        val output = context.contentResolver.openOutputStream(target, "wt") ?: throw FileNotFoundException(target.toString())
        output.use { raw ->
            ZipOutputStream(BackupCrypto.encrypt(BufferedOutputStream(raw), password)).use { zip ->
                // База зашифрована, фото — webp: сжимать бесполезно, только тратить время
                zip.setLevel(Deflater.NO_COMPRESSION)
                zip.putBytes(MANIFEST, json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
                zip.putBytes(KEY, keyManager.passphrase())
                // Настройки, будильники, игры, язык — всё, что не в базе
                if (withSettings) zip.putBytes(SETTINGS, BackupSettings.export(context).toByteArray())
                // Пока копируем файл базы, никто не пишет — снимок цельный
                db.beginTransaction()
                try {
                    zip.putFile(ARCHIVE_DB, dbFile)
                    File(dbFile.path + "-wal").takeIf { it.exists() && it.length() > 0 }?.let { zip.putFile(ARCHIVE_WAL, it) }
                } finally {
                    db.endTransaction()
                }
                photos.forEach { zip.putFile("$PHOTOS_DIR/" + it.relativeTo(photosDir).invariantSeparatorsPath, it) }
            }
        }
        context.contentResolver.openFileDescriptor(target, "r")?.use { it.statSize } ?: -1L
    }

    /**
     * Расшифровать и проверить копию [source]; если всё в порядке — она ждёт подмены при перезапуске
     * (см. [markRestoreReady]). Неверный пароль — [WrongPasswordException].
     */
    suspend fun prepareRestore(source: Uri, password: CharArray): RestorePreview = withContext(Dispatchers.IO) {
        val dir = staging.apply { deleteRecursively(); mkdirs() }
        try {
            val input = context.contentResolver.openInputStream(source) ?: throw FileNotFoundException(source.toString())
            input.use { raw -> unpack(BackupCrypto.decrypt(BufferedInputStream(raw), password), dir) }

            val manifest = File(dir, MANIFEST).takeIf { it.exists() }
                ?.let { json.decodeFromString(BackupManifest.serializer(), it.readText()) }
                ?: throw NotBackupException()
            if (manifest.format != FORMAT) throw NotBackupException()
            if (manifest.databaseVersion > database().openHelper.readableDatabase.version) throw NewerBackupException()
            if (File(dir, KEY).length() != KEY_SIZE || !File(dir, ARCHIVE_DB).exists()) throw NotBackupException()
            RestorePreview(Instant.parse(manifest.createdAt), manifest.photos)
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    /** Подтверждено: при следующем запуске данные заменятся копией. */
    fun markRestoreReady() {
        File(staging, READY).createNewFile()
    }

    fun cancelRestore() {
        staging.deleteRecursively()
    }

    /**
     * Подменить данные копией, если она ждёт. Вызывать при запуске, до первого обращения к базе.
     * Каждый шаг можно повторить: оборвался посередине — доделается при следующем запуске.
     */
    fun applyPendingRestore() {
        val dir = staging
        if (!File(dir, READY).exists()) {
            if (dir.exists()) dir.deleteRecursively() // недопроверенная копия — не нужна
            return
        }
        Log.i(TAG, "Восстанавливаю данные из резервной копии")
        // Ключ базы — под чип этого телефона
        keyManager.replace(File(dir, KEY).readBytes())

        // База: новый файл встаёт на место старого одним переименованием
        File(dbFile.parentFile, dbFile.name + "-shm").delete()
        File(dbFile.parentFile, dbFile.name + "-journal").delete()
        val stagedDb = File(dir, ARCHIVE_DB)
        if (stagedDb.exists()) {
            dbFile.parentFile?.mkdirs()
            val wal = File(dbFile.path + "-wal")
            wal.delete()
            File(dir, ARCHIVE_WAL).takeIf { it.exists() }?.renameTo(wal)
            check(stagedDb.renameTo(dbFile)) { "Не удалось заменить базу" }
        }

        // Фото: старую папку — в сторону, новую — на место, старую — удалить
        val stagedPhotos = File(dir, PHOTOS_DIR)
        val old = File(filesRoot, "$PHOTOS_DIR.old")
        if (stagedPhotos.exists()) {
            old.deleteRecursively()
            photosDir.renameTo(old)
            check(stagedPhotos.renameTo(photosDir)) { "Не удалось заменить фото" }
        }
        old.deleteRecursively()

        // Настройки — до того, как их прочитает приложение. В старых копиях их нет: свои остаются
        File(dir, SETTINGS).takeIf { withSettings && it.exists() }?.let { file ->
            runCatching { BackupSettings.restore(context, file.readText()) }
                .onFailure { Log.w(TAG, "Не удалось вернуть настройки из копии", it) }
        }
        dir.deleteRecursively()
    }

    /** Распаковать архив в [dir], не выпуская файлы за её пределы. */
    private fun unpack(input: InputStream, dir: File) {
        val root = dir.canonicalFile
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val target = File(root, entry.name).canonicalFile
                // Имя вроде «../../что-то» вывело бы файл за пределы папки — такой архив не наш
                if (!target.path.startsWith(root.path + File.separator)) throw NotBackupException()
                val allowed = entry.name == MANIFEST || entry.name == KEY || entry.name == SETTINGS ||
                    entry.name.startsWith("$DB_DIR/") || entry.name.startsWith("$PHOTOS_DIR/")
                if (!allowed) continue
                target.parentFile?.mkdirs()
                target.outputStream().use { zip.copyTo(it) }
            }
        }
    }

    private fun ZipOutputStream.putBytes(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.putFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    companion object {
        /** Расширение файла копии. */
        const val EXTENSION = "rmbak"
        private const val TAG = "Backup"
        private const val FORMAT = 1
        private const val MANIFEST = "manifest.json"
        private const val KEY = "key.bin"
        private const val KEY_SIZE = 32L
        private const val SETTINGS = "settings.json"
        private const val DB_DIR = "db"

        /** В архиве база всегда под одним именем — независимо от имени файла на телефоне. */
        private const val ARCHIVE_DB = "$DB_DIR/database.db"
        private const val ARCHIVE_WAL = "$DB_DIR/database.db-wal"
        private const val PHOTOS_DIR = "photos"

        /** Файл базы MapLibre (см. OfflineMaps). */
        private const val MAP_DATABASE = "mbgl-offline.db"
        private const val STAGING_DIR = "restore"
        private const val READY = "READY"
    }
}
