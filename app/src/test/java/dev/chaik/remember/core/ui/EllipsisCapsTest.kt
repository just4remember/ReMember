package dev.chaik.remember.core.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EllipsisCapsTest {
    @Test
    fun `после многоточия и пробела — новое предложение`() {
        assertTrue(EllipsisCaps.startsSentence("Думал... "))
        assertTrue(EllipsisCaps.startsSentence("Думал… "))
        assertTrue(EllipsisCaps.startsSentence("«Ну...» "))
        assertTrue(EllipsisCaps.startsSentence("(так...)  "))
    }

    @Test
    fun `без пробела и без многоточия — нет`() {
        assertFalse(EllipsisCaps.startsSentence("Думал..."))
        assertFalse(EllipsisCaps.startsSentence("т.е. "))
        assertFalse(EllipsisCaps.startsSentence("Думал.. "))
        assertFalse(EllipsisCaps.startsSentence("слово "))
    }
}
