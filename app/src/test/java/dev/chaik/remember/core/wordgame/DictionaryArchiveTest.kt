package dev.chaik.remember.core.wordgame

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryArchiveTest {
    @Test
    fun `archive supplies both lists and answers are valid guesses`() {
        val data = zip("answers.txt" to "apple\n", "guesses.txt" to "crane\n")
        val words = DictionaryArchive.parseVerified(data, hash(data))

        assertEquals("apple", words.randomAnswer())
        assertTrue(words.isWord("apple"))
        assertTrue(words.isWord("crane"))
        assertFalse(words.isWord("speed"))
    }

    @Test
    fun `wrong hash unexpected ZIP paths and oversized lists are rejected`() {
        val valid = zip("answers.txt" to "apple\n", "guesses.txt" to "crane\n")
        val unexpectedPath = zip("../answers.txt" to "apple\n", "guesses.txt" to "crane\n")
        val oversized = zip("answers.txt" to "apple\n".repeat(90_000), "guesses.txt" to "crane\n")

        assertThrows(IllegalArgumentException::class.java) {
            DictionaryArchive.parseVerified(valid, "0".repeat(64))
        }
        listOf(unexpectedPath, oversized).forEach { archive ->
            assertThrows(IllegalArgumentException::class.java) {
                DictionaryArchive.parseVerified(archive, hash(archive))
            }
        }
    }

    @Test
    fun `saved round without a dictionary hash remains playable`() {
        val state = Json.decodeFromString<WordGameState>("""{"answer":"apple","input":"apple"}""")
        val fallback = WordLists(listOf("crane"), listOf("speed"))

        assertNull(state.dictionaryHash)
        assertTrue(WordGame(fallback).submit(state).first.won)
    }

    private fun hash(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
