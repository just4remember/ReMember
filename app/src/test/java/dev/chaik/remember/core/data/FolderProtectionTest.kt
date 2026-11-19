package dev.chaik.remember.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderProtectionTest {
    @Test
    fun `в защищённую папку — своя защита снимается`() {
        assertFalse(protectionAfterMove(wasProtected = true, from = false, to = true))
        assertFalse(protectionAfterMove(wasProtected = false, from = true, to = true))
    }

    @Test
    fun `из защищённой папки в обычную — заметка становится защищённой`() {
        assertTrue(protectionAfterMove(wasProtected = false, from = true, to = false))
    }

    @Test
    fun `между обычными папками — как была`() {
        assertTrue(protectionAfterMove(wasProtected = true, from = false, to = false))
        assertFalse(protectionAfterMove(wasProtected = false, from = false, to = false))
    }
}
