package com.coolapk.wear.data.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 酷安文本清洗、图片 URL 规范化、话题提取与时间格式化工具（纯 Kotlin，预编译正则）。
 */
object TextSanitizer {

    private val ANCHOR_REGEX = Regex(
        "<a[^>]*>(.*?)</a>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val BR_REGEX = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val BLOCK_CLOSE_REGEX = Regex("</(?:p|div)>", RegexOption.IGNORE_CASE)
    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val NUMERIC_ENTITY_REGEX = Regex("&#(x?[0-9a-fA-F]+);")
    private val MULTI_NEWLINE_REGEX = Regex("\n{3,}")

    private val HTML_IMG_REGEX = Regex(
        """<img[^>]+src=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val BBCODE_IMG_REGEX = Regex(
        """\[(?:img|gif)](https?://[^\s\[\]]+)\[/(?:img|gif)]""",
        RegexOption.IGNORE_CASE
    )
    private val RAW_IMG_URL_REGEX = Regex(
        """(https?://[^\s<>"'\[\]]+\.(?:gif|jpg|jpeg|png|webp)(\?[^\s<>"'\[\]]*)?)""",
        RegexOption.IGNORE_CASE
    )
    private val TOPIC_REGEX = Regex("#([^#\\s]{2,20})#")

    private val SAME_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MM-dd")
    private val OTHER_YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val COOLAPK_THUMBNAIL_HOSTS = setOf("image.coolapk.com", "pic.coolapk.com")
    private val STATIC_IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp")
    private val EXISTING_THUMB_SUFFIXES = listOf(".s.jpg", ".xs.jpg", ".m.jpg", ".t.jpg")

    fun normalizeImageUrl(url: String): String {
        var normalized = url.trim()
        if (normalized.isEmpty()) return ""
        // 过滤包含 HTML 标签、换行、回车或空白的脏数据
        if (normalized.any { it == '<' || it == '>' || it == '\n' || it == '\r' || it == ' ' || it == '\t' }) {
            return ""
        }
        normalized = when {
            normalized.startsWith("http://", ignoreCase = true) -> "https://" + normalized.substring(7)
            normalized.startsWith("//") -> "https:$normalized"
            else -> normalized
        }
        return if (normalized.startsWith("https://", ignoreCase = true)) normalized else ""
    }

    /**
     * 判断图片 URL 是否为动图 GIF（忽略查询参数与锚点）。
     */
    fun isGifImageUrl(url: String): Boolean {
        val cleanPath = url.trim().substringBefore('?').substringBefore('#').lowercase()
        return cleanPath.endsWith(".gif")
    }

    /**
     * 将酷安静态图床原图 URL 转为官方轻量 `.s.jpg` 服务端缩略图（参考 c001apk-Revived）。
     * - 动图 GIF、已带缩略图后缀、带签名查询参数、头像域或非酷安图床地址保持原样返回。
     */
    fun toCoolapkThumbnailUrl(url: String): String {
        val normalized = normalizeImageUrl(url)
        if (normalized.isEmpty()) return ""
        if (isGifImageUrl(normalized)) return normalized
        if (normalized.contains('?') || normalized.contains('#')) return normalized

        val withoutScheme = normalized.substringAfter("://", "")
        val host = withoutScheme.substringBefore('/').substringBefore(':').lowercase()
        if (host !in COOLAPK_THUMBNAIL_HOSTS) return normalized

        val lower = normalized.lowercase()
        if (EXISTING_THUMB_SUFFIXES.any { lower.endsWith(it) }) return normalized
        if (STATIC_IMAGE_EXTENSIONS.none { lower.endsWith(it) }) return normalized

        return "$normalized.s.jpg"
    }

    fun cleanHtmlTags(html: String): String {
        if (html.isBlank()) return ""
        val stripped = html
            .replace(ANCHOR_REGEX, "$1")
            .replace(BR_REGEX, "\n")
            .replace(BLOCK_CLOSE_REGEX, "\n")
            .replace(HTML_TAG_REGEX, "")

        val decodedNumeric = NUMERIC_ENTITY_REGEX.replace(stripped) { match ->
            val raw = match.groupValues[1]
            val codePoint = if (raw.startsWith("x", ignoreCase = true)) {
                raw.substring(1).toIntOrNull(16)
            } else {
                raw.toIntOrNull(10)
            }
            if (codePoint != null && Character.isValidCodePoint(codePoint)) {
                String(Character.toChars(codePoint))
            } else {
                match.value
            }
        }

        // 注意：&amp; 必须放在最后解码，避免把 &amp;lt; 二次解码成 <
        return decodedNumeric
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace(MULTI_NEWLINE_REGEX, "\n\n")
            .trim()
    }

    /**
     * 从评论/回复正文中提取内嵌配图或动图地址（<img> / [img] / 裸链接）。
     */
    fun extractImageFromMessage(rawMessage: String): String {
        if (rawMessage.isBlank()) return ""
        HTML_IMG_REGEX.find(rawMessage)?.let {
            val url = normalizeImageUrl(it.groupValues[1])
            if (url.isNotBlank()) return url
        }
        BBCODE_IMG_REGEX.find(rawMessage)?.let {
            val url = normalizeImageUrl(it.groupValues[1])
            if (url.isNotBlank()) return url
        }
        RAW_IMG_URL_REGEX.find(rawMessage)?.let {
            val url = normalizeImageUrl(it.groupValues[1])
            if (url.isNotBlank()) return url
        }
        return ""
    }

    /**
     * 过滤酷安服务端对普通动态自动附加的占位标题（如 "xxx的动态"、"动态"、"图文" 等）。
     */
    fun filterAutoTitle(rawTitle: String, username: String): String {
        val title = rawTitle.trim()
        val user = username.trim()
        val isAutoTitle = title.isBlank() ||
            title == user ||
            title == "${user}的动态" ||
            title == "${user}的图文" ||
            title == "${user}的回答" ||
            title.endsWith("的动态") ||
            title.endsWith("的新动态") ||
            title == "动态" ||
            title == "图文"
        return if (isAutoTitle) "" else title
    }

    /**
     * 从正文中提取去重后的 #话题# 列表。
     */
    fun extractTopics(message: String, maxLength: Int = 20): List<String> {
        if (message.isBlank()) return emptyList()
        return TOPIC_REGEX.findAll(message)
            .map { it.groupValues[1].trim() }
            .filter { it.length in 2..maxLength }
            .distinct()
            .toList()
    }

    /**
     * 将秒级时间戳格式化为友好的相对时间或日期。
     */
    fun formatRelativeTime(
        seconds: Long,
        nowSeconds: Long = System.currentTimeMillis() / 1000L,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (seconds <= 0L) return ""
        val diff = nowSeconds - seconds
        return when {
            diff < 60L -> "刚刚"
            diff < 3600L -> "${diff / 60L}分钟前"
            diff < 86400L -> "${diff / 3600L}小时前"
            diff < 86400L * 30L -> "${diff / 86400L}天前"
            else -> {
                val targetTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(seconds), zoneId)
                val currentYear = ZonedDateTime.ofInstant(Instant.ofEpochSecond(nowSeconds), zoneId).year
                val formatter = if (targetTime.year == currentYear) SAME_YEAR_FORMATTER else OTHER_YEAR_FORMATTER
                formatter.format(targetTime)
            }
        }
    }
}
