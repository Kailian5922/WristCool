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
}
