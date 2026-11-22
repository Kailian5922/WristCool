package com.coolapk.wear.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CoolapkAuthTest {

    @Test
    fun `getAppToken generates valid v3 bcrypt token`() {
        val deviceCode = DeviceProfile.generateOfficialDeviceCode()
        assertTrue("Device code should be non-blank", deviceCode.isNotBlank())
        assertTrue("Device code should not contain padding '='", !deviceCode.contains('='))

        val auth = CoolapkAuth(deviceCode)
        val token = auth.getAppToken(nowSec = 1730000000L)

        assertTrue("Token must start with v3", token.startsWith("v3"))
        val decoded = String(Base64.getMimeDecoder().decode(token.removePrefix("v3")), Charsets.UTF_8)
        assertTrue("Decoded hash must start with \$2y\$04\$", decoded.startsWith("\$2y\$04\$"))
        assertEquals("BCrypt hash should be 60 chars", 60, decoded.length)
    }

    @Test
    fun `different timestamps or device codes produce different tokens`() {
        val auth1 = CoolapkAuth("device-code-alpha")
        val auth2 = CoolapkAuth("device-code-beta")

        val t1 = auth1.getAppToken(nowSec = 1730000100L)
        val t2 = auth1.getAppToken(nowSec = 1730000200L)
        val t3 = auth2.getAppToken(nowSec = 1730000100L)

        assertNotEquals(t1, t2)
        assertNotEquals(t1, t3)
    }

    @Test
    fun `getAppTokenV2 generates valid v2 bcrypt token matching c001apk-Revived`() {
        val deviceCode = DeviceProfile.generateOfficialDeviceCode()
        val auth = CoolapkAuth(deviceCode)
        val token = auth.getAppTokenV2(nowSec = 1730000000L)

        assertTrue("V2 token must start with v2", token.startsWith("v2"))
        assertTrue("V2 token should not contain padding '='", !token.contains('='))
        val decoded = String(Base64.getMimeDecoder().decode(token.removePrefix("v2")), Charsets.UTF_8)
        assertTrue("Decoded V2 hash must start with \$2y\$10\$", decoded.startsWith("\$2y\$10\$"))
        assertEquals("BCrypt hash should be 60 chars", 60, decoded.length)
        // 盐前缀必须完整保留 Base64(timestamp) + '/'
        val expectedTsB64 = Base64.getEncoder().encodeToString("1730000000".toByteArray()).replace("=", "")
        assertTrue(decoded.startsWith("\$2y\$10\$$expectedTsB64/"))

        val nextSecondToken = auth.getAppTokenV2(nowSec = 1730000001L)
        assertNotEquals(token, nextSecondToken)
    }
}
