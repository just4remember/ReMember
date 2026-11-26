package dev.chaik.remember.core.wordgame

import java.security.MessageDigest
import java.util.zip.ZipInputStream

internal object DictionaryArchive {
    const val MAX_ARCHIVE = 2 * 1024 * 1024
    private const val MAX_LIST = 512 * 1024
    private val names = setOf("answers.txt", "guesses.txt")
    private val sha256 = Regex("[0-9a-f]{64}")

    fun isHash(hash: String): Boolean = sha256.matches(hash)

    fun parseVerified(archive: ByteArray, hash: String): WordLists {
        require(archive.size in 1..MAX_ARCHIVE && isHash(hash))
        val expected = ByteArray(32) { index -> hash.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        require(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(archive), expected))

        val lists = mutableMapOf<String, List<String>>()
        ZipInputStream(archive.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(entry.name in names && entry.name !in lists && !entry.isDirectory)
                val bytes = zip.readNBytes(MAX_LIST + 1)
                require(bytes.size <= MAX_LIST)
                val words = bytes.toString(Charsets.UTF_8).lineSequence().filter(String::isNotEmpty).toList()
                require(words.isNotEmpty() && words.all { word -> word.length == WordGameState.WORD_LENGTH && word.all { it in 'a'..'z' } })
                lists[entry.name] = words.distinct()
            }
        }
        require(lists.keys == names)
        return WordLists(lists.getValue("answers.txt"), lists.getValue("guesses.txt"))
    }
}
