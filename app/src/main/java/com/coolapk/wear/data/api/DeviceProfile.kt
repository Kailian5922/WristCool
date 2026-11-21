package com.coolapk.wear.data.api

import android.content.Context
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/**
 * 酷安设备画像与请求头提供器。
 * 内存缓存 deviceCode，避免高频网络请求反复读取 SharedPreferences 或竞态生成不同设备码。
 */
class DeviceProfile(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedDeviceCode: String? = null

    @Volatile
    private var cachedCustomDeviceId: String? = null

    val customDeviceId: String
        get() {
            cachedCustomDeviceId?.let { return it }
            return synchronized(this) {
                cachedCustomDeviceId?.let { return it }
                val stored = prefs.getString(KEY_CUSTOM_DEVICE_ID, "").orEmpty().trim()
                val valid = if (isValidShuzilmDeviceId(stored)) stored else ""
                cachedCustomDeviceId = valid
                valid
            }
        }

    val hasCustomDeviceId: Boolean
        get() = customDeviceId.isNotBlank()

    val deviceCode: String
        get() {
            cachedDeviceCode?.let { return it }
            return synchronized(this) {
                cachedDeviceCode?.let { return it }
                val customId = customDeviceId
                val code = if (customId.isNotBlank()) {
                    generateOfficialDeviceCode(customId)
                } else {
                    var stored = prefs.getString(KEY_DEVICE_CODE, null)
                    if (stored.isNullOrBlank()) {
                        stored = generateOfficialDeviceCode()
                        prefs.edit().putString(KEY_DEVICE_CODE, stored).apply()
                    }
                    stored
                }
                cachedDeviceCode = code
                code
            }
        }

    @Synchronized
    fun setCustomDeviceId(rawInput: String): String {
        val parsed = parseShuzilmDeviceId(rawInput)
        if (parsed.isEmpty()) {
            return ""
        }
        prefs.edit().putString(KEY_CUSTOM_DEVICE_ID, parsed).apply()
        cachedCustomDeviceId = parsed
        cachedDeviceCode = generateOfficialDeviceCode(parsed)
        return parsed
    }

    @Synchronized
    fun clearCustomDeviceId() {
        prefs.edit().remove(KEY_CUSTOM_DEVICE_ID).apply()
        cachedCustomDeviceId = ""
        cachedDeviceCode = null
    }

    val userAgent: String =
        "Dalvik/2.1.0 (Linux; U; Android 14; 23113RKC6C Build/UKQ1.230804.001) (#Build; Xiaomi; 23113RKC6C; UKQ1.230804.001; 14) +CoolMarket/16.2.0-2604201-universal"

    companion object {
        private const val PREFS_NAME = "coolapk_wear_device"
        private const val KEY_DEVICE_CODE = "device_code"
        private const val KEY_CUSTOM_DEVICE_ID = "custom_device_id"

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

        fun generateOfficialDeviceCode(customDeviceId: String? = null): String {
            val normalizedId = customDeviceId?.trim()?.takeIf { isValidShuzilmDeviceId(it) }
            val identifier = normalizedId ?: run {
                val randomSeed = UUID.randomUUID().toString() + System.currentTimeMillis()
                val md = MessageDigest.getInstance("MD5")
                val digest = md.digest(randomSeed.toByteArray(Charsets.UTF_8))
                digest.take(8).joinToString("") { "%02x".format(it) }
            }

            val raw = "$identifier; ; ; ; Xiaomi; Xiaomi; 23113RKC6C; UKQ1.230804.001; "
            val b64 = Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
            return b64.reversed().replace("=", "").trim()
        }
    }
}
