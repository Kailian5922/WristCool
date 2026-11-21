package com.coolapk.wear.companion

import org.json.JSONObject

/**
 * 手机伴侣端 Cookie 合并、去重与登录状态校验工具。
 * 保证 WebLoginActivity 与 MainActivity 使用完全一致的登录判定规则。
 */
object CookieHelper {

    fun parseToMap(rawCookie: String): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        if (rawCookie.isBlank()) return result
        rawCookie.split(";").forEach { segment ->
            val trimmed = segment.trim()
            if (trimmed.isEmpty()) return@forEach
            val idx = trimmed.indexOf('=')
            if (idx <= 0) return@forEach
            val key = trimmed.substring(0, idx).trim()
            val value = trimmed.substring(idx + 1).trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                result[key] = value
            }
        }
        return result
    }

    /**
     * 合并多域名 Cookie 并按大小写不敏感键去重，保留靠后的最新值。
     */
    fun mergeCookies(vararg rawCookies: String): String {
        val canonicalKeys = LinkedHashMap<String, String>()
        val values = LinkedHashMap<String, String>()
        for (raw in rawCookies) {
            if (raw.isBlank()) continue
            for ((key, value) in parseToMap(raw)) {
                val lower = key.lowercase()
                canonicalKeys[lower] = key
                values[lower] = value
            }
        }
        return canonicalKeys.entries.joinToString("; ") { (lower, key) ->
            "$key=${values[lower]}"
        }
    }

    fun extractValue(rawCookie: String, targetKey: String): String {
        if (rawCookie.isBlank() || targetKey.isBlank()) return ""
        for ((key, value) in parseToMap(rawCookie)) {
            if (key.equals(targetKey, ignoreCase = true)) {
                return value
            }
        }
        return ""
    }

    /**
     * 酷安有效登录态需同时具备非空 uid 与 token，防止登录中途跳转时只写入单个字段就误判完成。
     */
    fun isLoggedIn(rawCookie: String): Boolean {
        if (rawCookie.isBlank()) return false
        return extractValue(rawCookie, "uid").isNotBlank() &&
            extractValue(rawCookie, "token").isNotBlank()
    }

    private val LABELED_DEVICE_ID_REGEX = Regex(
        """(?i)(?:设备\s*ID|ShuzlmID|szlmId|Device\s*Id|数字联盟\s*ID)\s*[:：=]\s*([A-Za-z0-9_-]{10,128})"""
    )
    private val DU_DEVICE_ID_REGEX = Regex("""\b(DU[A-Za-z0-9_-]{20,128})\b""")
    private val VALID_DEVICE_ID_REGEX = Regex("""^[A-Za-z0-9_-]{10,128}$""")
    private val UUID_REGEX = Regex(
        """^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"""
    )

    fun isValidShuzilmDeviceId(id: String): Boolean {
        val trimmed = id.trim()
        if (!VALID_DEVICE_ID_REGEX.matches(trimmed)) return false
        if (UUID_REGEX.matches(trimmed)) return false
        if (trimmed.startsWith("DU") && trimmed.drop(2).all { it == '0' }) return false
        return true
    }

    /**
     * 从官方 Android 酷安复制的多行设备日志或单行文本中提取有效数盟设备 ID（形如 DU...）。
     */
    fun parseShuzilmDeviceId(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return ""

        LABELED_DEVICE_ID_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.trim()?.let { candidate ->
            if (isValidShuzilmDeviceId(candidate)) return candidate
        }

        DU_DEVICE_ID_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.trim()?.let { candidate ->
            if (isValidShuzilmDeviceId(candidate)) return candidate
        }

        val firstLine = trimmed.lineSequence().firstOrNull()?.trim().orEmpty()
        if (firstLine.isNotEmpty() && !firstLine.contains(':') && !firstLine.contains('：') && !firstLine.contains('=')) {
            if (isValidShuzilmDeviceId(firstLine)) return firstLine
        }
        return ""
    }

    fun buildCompanionPayload(cookie: String, ddid: String, deviceId: String = ""): String {
        val normalized = mergeCookies(cookie)
        val validDeviceId = parseShuzilmDeviceId(deviceId)
        return JSONObject().apply {
            put("cookie", normalized)
            put("ddid", ddid.trim())
            if (validDeviceId.isNotEmpty()) {
                put("deviceId", validDeviceId)
            }
        }.toString()
    }
}
