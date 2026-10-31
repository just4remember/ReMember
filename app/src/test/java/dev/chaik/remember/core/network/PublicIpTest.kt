package dev.chaik.remember.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicIpTest {
    @Test
    fun `адрес из ответа Cloudflare`() {
        assertEquals("203.0.113.7", PublicIp.fromTrace("fl=1\nip=203.0.113.7\nts=1\n"))
        assertNull(PublicIp.fromTrace("fl=1\n"))
    }

    @Test
    fun `адрес из ответа Яндекса`() {
        assertEquals("203.0.113.7", PublicIp.fromYandex("\"203.0.113.7\"\n"))
        assertNull(PublicIp.fromYandex(""))
    }
}
