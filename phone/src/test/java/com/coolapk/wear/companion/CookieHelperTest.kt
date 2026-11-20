package com.coolapk.wear.companion

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieHelperTest {

    @Test
    fun `mergeCookies deduplicates across domains and keeps values with equals`() {
        val merged = CookieHelper.mergeCookies(
            "uid=123; SESSID=old; token=abc==",
            "UID=123; SESSID=new; token=finalToken=="
        )
        assertEquals("123", CookieHelper.extractValue(merged, "uid"))
        assertEquals("new", CookieHelper.extractValue(merged, "SESSID"))
        assertEquals("finalToken==", CookieHelper.extractValue(merged, "token"))
        // Ensure no duplicate keys
        assertEquals(3, merged.split(";").size)
    }

    @Test
    fun `isLoggedIn requires both non-empty uid and token`() {
        assertFalse(CookieHelper.isLoggedIn(""))
        assertFalse(CookieHelper.isLoggedIn("uid=123"))
        assertFalse(CookieHelper.isLoggedIn("token=abc"))
        assertFalse(CookieHelper.isLoggedIn("uid=; token=abc"))
        assertTrue(CookieHelper.isLoggedIn("uid=123; token=abc=="))
    }

    @Test
    fun `buildCompanionPayload produces clean json with normalized cookie`() {
        val payload = CookieHelper.buildCompanionPayload("uid=99; uid=99; token=tok==", "  my-ddid  ")
        val json = JSONObject(payload)
        assertEquals("uid=99; token=tok==", json.getString("cookie"))
        assertEquals("my-ddid", json.getString("ddid"))
    }
}
