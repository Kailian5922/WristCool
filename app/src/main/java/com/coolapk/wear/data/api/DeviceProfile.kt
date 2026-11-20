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

    val deviceCode: String
        get() {
            cachedDeviceCode?.let { return it }
            return synchronized(this) {
                cachedDeviceCode?.let { return it }
                var code = prefs.getString(KEY_DEVICE_CODE, null)
                if (code.isNullOrBlank()) {
                    code = generateOfficialDeviceCode()
                    prefs.edit().putString(KEY_DEVICE_CODE, code).apply()
                }
                cachedDeviceCode = code
                code
            }
        }

    val userAgent: String =
        "Dalvik/2.1.0 (Linux; U; Android 14; 23113RKC6C Build/UKQ1.230804.001) (#Build; Xiaomi; 23113RKC6C; UKQ1.230804.001; 14) +CoolMarket/16.2.0-2604201"

    companion object {
        private const val PREFS_NAME = "coolapk_wear_device"
        private const val KEY_DEVICE_CODE = "device_code"

        fun generateOfficialDeviceCode(): String {
            val randomSeed = UUID.randomUUID().toString() + System.currentTimeMillis()
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(randomSeed.toByteArray(Charsets.UTF_8))
            val hexId = digest.take(8).joinToString("") { "%02x".format(it) }

            val raw = "$hexId; ; ; ; Xiaomi; Xiaomi; 23113RKC6C; UKQ1.230804.001; "
            val b64 = Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
            return b64.reversed().replace("=", "").trim()
        }
    }
}
