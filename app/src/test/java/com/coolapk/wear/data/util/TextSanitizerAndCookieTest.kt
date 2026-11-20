package com.coolapk.wear.data.util

import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.model.FeedMemoryCache
import com.coolapk.wear.data.repository.AuthRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class TextSanitizerAndCookieTest {

    @Test
    fun `cleanHtmlTags handles multiline links br numeric entities and avoids double decode`() {
        val raw = """<a href="https://x.com">Link<br/>Text</a> &amp;lt;tag&amp;gt; &quot;hi&quot; &#39;ok&#39;"""
        val cleaned = TextSanitizer.cleanHtmlTags(raw)
        assertEquals("Link\nText &lt;tag&gt; \"hi\" 'ok'", cleaned)
    }

    @Test
    fun `normalizeImageUrl upgrades http and protocol-relative and rejects dirty strings`() {
        assertEquals("https://img.coolapk.com/a.jpg", TextSanitizer.normalizeImageUrl("http://img.coolapk.com/a.jpg"))
        assertEquals("https://img.coolapk.com/b.png", TextSanitizer.normalizeImageUrl("//img.coolapk.com/b.png"))
        assertEquals("", TextSanitizer.normalizeImageUrl("<img src=x>"))
        assertEquals("", TextSanitizer.normalizeImageUrl("https://img.coolapk.com/a b.jpg"))
    }

    @Test
    fun `filterAutoTitle removes placeholder titles and preserves real titles`() {
        assertEquals("", TextSanitizer.filterAutoTitle("张三的动态", "张三"))
        assertEquals("", TextSanitizer.filterAutoTitle("动态", "张三"))
        assertEquals("手表深度评测", TextSanitizer.filterAutoTitle("手表深度评测", "张三"))
    }

    @Test
    fun `extractTopics extracts unique hashtags`() {
        val topics = TextSanitizer.extractTopics("今天体验 #WearOS# 和 #酷安# 还有 #WearOS#")
        assertEquals(listOf("WearOS", "酷安"), topics)
    }

    @Test
    fun `formatRelativeTime handles recent and cross-year dates`() {
        val now = 1767225600L // 2026-01-01 00:00:00 UTC
        assertEquals("刚刚", TextSanitizer.formatRelativeTime(now - 10, now, ZoneOffset.UTC))
        assertEquals("5分钟前", TextSanitizer.formatRelativeTime(now - 300, now, ZoneOffset.UTC))
        assertEquals("2小时前", TextSanitizer.formatRelativeTime(now - 7200, now, ZoneOffset.UTC))
        assertEquals("2025-11-01", TextSanitizer.formatRelativeTime(1761955200L, now, ZoneOffset.UTC))
    }

    @Test
    fun `CookieParser preserves base64 equals and deduplicates keys`() {
        val merged = CookieParser.merge(
            "uid=100; token=old==; SESSID=s1",
            "UID=100; token=newToken==; extra=val=1"
        )
        assertEquals("100", CookieParser.extractValue(merged, "uid"))
        assertEquals("newToken==", CookieParser.extractValue(merged, "token"))
        assertEquals("val=1", CookieParser.extractValue(merged, "extra"))
        assertTrue(CookieParser.hasValidAuth(merged))
    }

    @Test
    fun `AuthRepository parseCompanionPayload parses JSON and rejects invalid payload`() {
        val ok = AuthRepository.parseCompanionPayload("""{"cookie":"uid=88; token=xyz==","ddid":"d1"}""")
        assertTrue(ok.isSuccess)
        assertEquals("88", ok.getOrThrow().uid)
        assertEquals("d1", ok.getOrThrow().ddid)
        assertTrue(ok.getOrThrow().isLoggedIn)

        val bad = AuthRepository.parseCompanionPayload("""{"cookie":"foo=bar"}""")
        assertTrue(bad.isFailure)
    }

    @Test
    fun `ImageSaver detectImageFormat identifies gif png webp and fallback`() {
        val gifBytes = "GIF89a......".toByteArray(Charsets.US_ASCII)
        assertEquals(
            ImageSaver.ImageFormat("gif", "image/gif"),
            ImageSaver.detectImageFormat("https://img.coolapk.com/file.jpg", "image/jpeg", gifBytes)
        )
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertEquals(
            ImageSaver.ImageFormat("png", "image/png"),
            ImageSaver.detectImageFormat("https://img.coolapk.com/unknown", "", pngBytes)
        )
        val webpBytes = "RIFF1234WEBP".toByteArray(Charsets.US_ASCII)
        assertEquals(
            ImageSaver.ImageFormat("webp", "image/webp"),
            ImageSaver.detectImageFormat("https://img.coolapk.com/x", "", webpBytes)
        )
    }

    @Test
    fun `FeedMemoryCache enforces LRU bound and supports update`() {
        FeedMemoryCache.clear()
        for (i in 1..250) {
            FeedMemoryCache.put(sampleFeed(id = "feed_$i"))
        }
        assertEquals(200, FeedMemoryCache.size())
        assertNull(FeedMemoryCache.get("feed_1"))
        assertNotNull(FeedMemoryCache.get("feed_250"))

        val updated = FeedMemoryCache.update("feed_250") { it.copy(isLiked = true, likeNum = 99) }
        assertNotNull(updated)
        assertTrue(FeedMemoryCache.get("feed_250")!!.isLiked)
        assertEquals(99, FeedMemoryCache.get("feed_250")!!.likeNum)

        FeedMemoryCache.clear()
        assertEquals(0, FeedMemoryCache.size())
        assertFalse(CookieParser.hasValidAuth(""))
    }

    private fun sampleFeed(id: String) = FeedItem(
        id = id,
        uid = "1",
        username = "User",
        userAvatar = "",
        dateline = 0L,
        dateString = "",
        deviceTitle = "",
        title = "",
        message = "hello"
    )
}
