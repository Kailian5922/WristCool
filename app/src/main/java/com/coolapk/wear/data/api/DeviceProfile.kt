package com.coolapk.wear.data.api

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.util.UUID

/**
 * 酷安设备画像与请求头提供器
 * 100% 对应酷安官方及 coolapk-desktop 标准规范。
 */
class DeviceProfile(context: Context) {

    private val prefs = context.getSharedPreferences("coolapk_wear_device", Context.MODE_PRIVATE)

    val deviceCode: String
        get() {
            var code = prefs.getString("device_code", null)
            if (code.isNullOrBlank()) {
                code = generateOfficialDeviceCode()
                prefs.edit().putString("device_code", code).apply()
            }
            return code
        }

    val userAgent: String =
        "Dalvik/2.1.0 (Linux; U; Android 14; 23113RKC6C Build/UKQ1.230804.001) (#Build; Xiaomi; 23113RKC6C; UKQ1.230804.001; 14) +CoolMarket/16.2.0-2604201"

    private fun generateOfficialDeviceCode(): String {
        val randomSeed = UUID.randomUUID().toString() + System.currentTimeMillis()
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(randomSeed.toByteArray(Charsets.UTF_8))
        val hexId = digest.take(8).joinToString("") { "%02x".format(it) }

        val raw = "$hexId; ; ; ; Xiaomi; Xiaomi; 23113RKC6C; UKQ1.230804.001; "
        val b64 = Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return b64.reversed().replace("=", "").trim()
    }
}
