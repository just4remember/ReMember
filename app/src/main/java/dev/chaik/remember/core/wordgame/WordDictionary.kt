package dev.chaik.remember.core.wordgame

import android.content.Context
import android.os.UserManager
import android.util.LruCache
import androidx.core.content.edit
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.random.Random

/** Слова для игры: что загадывается и какие попытки принимаются. */
interface WordDictionary {
    fun isWord(word: String): Boolean
    fun randomAnswer(random: Random = Random.Default): String
    fun newRound(): WordGameState = WordGameState(answer = randomAnswer())
}

internal class WordLists(private val answers: List<String>, guesses: List<String>) : WordDictionary {
    private val guesses = guesses.toHashSet() + answers
    override fun isWord(word: String): Boolean = word.lowercase() in guesses
    override fun randomAnswer(random: Random): String = answers.random(random)
}

/** Встроенный ENABLE и скачанные версии. Партия обращается к своему снимку по SHA-256. */
internal class LocalWordDictionary(private val context: Context) : WordDictionary {
    private val directory by lazy { File(context.filesDir, "word-dictionaries") }
    private val settings by lazy { context.getSharedPreferences("word_dictionary", Context.MODE_PRIVATE) }
    private val installed = LruCache<String, WordLists>(3)
    private val bundled by lazy { WordLists(readAsset("answers.txt"), readAsset("guesses.txt")) }

    fun forRound(hash: String?): WordDictionary = cached(hash) ?: bundled

    override fun isWord(word: String): Boolean = forRound(currentHash()).isWord(word)
    override fun randomAnswer(random: Random): String = forRound(currentHash()).randomAnswer(random)

    override fun newRound(): WordGameState {
        val hash = currentHash()
        val dictionary = cached(hash)
        return WordGameState(
            answer = (dictionary ?: bundled).randomAnswer(),
            dictionaryHash = hash.takeIf { dictionary != null },
        )
    }

    fun hasVersion(version: Long, hash: String): Boolean =
        settings.getLong("version", 0L) == version && currentHash() == hash && cached(hash) != null

    fun install(version: Long, hash: String, archive: ByteArray, words: WordLists) {
        check(directory.isDirectory || directory.mkdirs())
        val target = File(directory, "$hash.zip")
        val temporary = File.createTempFile("dictionary-", ".zip", directory)
        try {
            temporary.outputStream().use { stream ->
                stream.write(archive)
                stream.fd.sync()
            }
            Files.move(temporary.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
        installed.put(hash, words)
        settings.edit(commit = true) {
            putString("sha256", hash)
            putLong("version", version)
        }
    }

    private fun currentHash(): String? =
        if (context.getSystemService(UserManager::class.java).isUserUnlocked) settings.getString("sha256", null) else null

    private fun cached(hash: String?): WordLists? {
        if (hash == null || !DictionaryArchive.isHash(hash) || !context.getSystemService(UserManager::class.java).isUserUnlocked) return null
        installed.get(hash)?.let { return it }
        return runCatching {
            val archive = File(directory, "$hash.zip").inputStream().use { it.readNBytes(DictionaryArchive.MAX_ARCHIVE + 1) }
            DictionaryArchive.parseVerified(archive, hash).also { installed.put(hash, it) }
        }.getOrNull()
    }

    private fun readAsset(name: String): List<String> =
        context.assets.open("words/$name").bufferedReader().useLines { lines ->
            lines.map(String::trim).filter(String::isNotEmpty).toList()
        }
}
