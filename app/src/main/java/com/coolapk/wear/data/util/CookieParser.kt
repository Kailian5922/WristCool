package com.coolapk.wear.data.util

/**
 * 统一的 Cookie 解析、合并与去重工具。
 */
object CookieParser {

    /**
     * 解析单个 Cookie 头为有序键值对（按 `=` 最多分割 2 段，避免 Base64 值中的 `=` 被截断）。
     */
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
     * 合并多个 Cookie 字符串并按大小写不敏感键去重（保留靠后的非空值）。
     */
    fun merge(vararg rawCookies: String): String {
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
        return canonicalKeys.entries.joinToString("; ") { (lower, originalKey) ->
            "$originalKey=${values[lower]}"
        }
    }

    /**
     * 从 Cookie 字符串中提取指定键的值（键名大小写不敏感）。
     */
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
     * 判断 Cookie 是否包含酷安有效登录凭据。
     * 优先校验 token，也兼容携带 uid + SESSID 的会话。
     */
    fun hasValidAuth(rawCookie: String): Boolean {
        if (rawCookie.isBlank()) return false
        val uid = extractValue(rawCookie, "uid")
        val token = extractValue(rawCookie, "token")
        val sessId = extractValue(rawCookie, "SESSID")
        return token.isNotBlank() || (uid.isNotBlank() && sessId.isNotBlank())
    }
}
