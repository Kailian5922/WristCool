package com.coolapk.wear.data.repository

import android.content.Context
import com.coolapk.wear.data.api.DeviceProfile
import com.coolapk.wear.data.model.AuthCredentials
import com.coolapk.wear.data.model.FeedMemoryCache
import com.coolapk.wear.data.util.CookieParser
import com.coolapk.wear.data.util.TextSanitizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class AuthRepository(
    context: Context,
    private val deviceProfile: DeviceProfile? = null
) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _credentials = MutableStateFlow(loadCredentials())
    val credentials: StateFlow<AuthCredentials> = _credentials.asStateFlow()

    private fun loadCredentials(): AuthCredentials {
        val cookie = prefs.getString(KEY_COOKIE, "").orEmpty()
        val ddid = prefs.getString(KEY_DDID, "").orEmpty()
        val storedUid = prefs.getString(KEY_UID, "").orEmpty()
        val uid = storedUid.ifBlank { CookieParser.extractValue(cookie, "uid") }
        val username = prefs.getString(KEY_USERNAME, "").orEmpty()
        val avatar = prefs.getString(KEY_AVATAR, "").orEmpty()
        val deviceId = deviceProfile?.customDeviceId?.takeIf { it.isNotBlank() }
            ?: prefs.getString(KEY_DEVICE_ID, "").orEmpty()
        return AuthCredentials(cookie, ddid, uid, username, avatar, deviceId)
    }

    @Synchronized
    fun saveCredentials(credentials: AuthCredentials) {
        val effectiveDeviceId = credentials.deviceId.takeIf { it.isNotBlank() }
            ?: deviceProfile?.customDeviceId.orEmpty()
        if (effectiveDeviceId.isNotBlank()) {
            deviceProfile?.setCustomDeviceId(effectiveDeviceId)
        }
        val persisted = credentials.copy(deviceId = effectiveDeviceId)
        prefs.edit()
            .putString(KEY_COOKIE, persisted.cookie)
            .putString(KEY_DDID, persisted.ddid)
            .putString(KEY_UID, persisted.uid)
            .putString(KEY_USERNAME, persisted.username)
            .putString(KEY_AVATAR, persisted.avatar)
            .putString(KEY_DEVICE_ID, persisted.deviceId)
            .apply()
        _credentials.value = persisted
    }

    /**
     * 导入伴侣端导出的登录信息。
     * 支持手机端复制的 JSON 字符串 `{"cookie":"...", "ddid":"...", "deviceId":"DU..."}`，
     * 同时也兼容直接粘贴纯 Cookie 字符串。
     */
    fun importCompanionPayload(rawText: String): Result<AuthCredentials> {
        return parseCompanionPayload(rawText).onSuccess { credentials ->
            saveCredentials(credentials)
        }
    }

    /**
     * 仅导入/更新数盟设备 ID（不清空已有登录态），支持直接粘贴官方酷安复制的设备日志或 DU... 字符串。
     */
    @Synchronized
    fun importDeviceIdOnly(rawText: String): Result<String> {
        val parsed = DeviceProfile.parseShuzilmDeviceId(rawText)
        if (parsed.isEmpty()) {
            return Result.failure(IllegalArgumentException("未识别到有效的数盟设备 ID（应形如 DU...）"))
        }
        deviceProfile?.setCustomDeviceId(parsed)
        prefs.edit().putString(KEY_DEVICE_ID, parsed).apply()
        _credentials.value = _credentials.value.copy(deviceId = parsed)
        return Result.success(parsed)
    }

    @Synchronized
    fun updateUserInfo(username: String, avatar: String, uid: String = "") {
        val current = _credentials.value
        val normalizedAvatar = TextSanitizer.normalizeImageUrl(avatar).ifBlank { current.avatar }
        val updated = current.copy(
            uid = uid.ifBlank { current.uid },
            username = username.ifBlank { current.username },
            avatar = normalizedAvatar
        )
        if (updated != current) {
            saveCredentials(updated)
        }
    }

    @Synchronized
    fun logout() {
        val preservedDeviceId = deviceProfile?.customDeviceId?.takeIf { it.isNotBlank() }
            ?: _credentials.value.deviceId
        prefs.edit().clear().apply()
        if (preservedDeviceId.isNotBlank()) {
            prefs.edit().putString(KEY_DEVICE_ID, preservedDeviceId).apply()
        }
        FeedMemoryCache.clear()
        _credentials.value = AuthCredentials(deviceId = preservedDeviceId)
    }

    companion object {
        private const val PREFS_NAME = "coolapk_wear_auth"
        private const val KEY_COOKIE = "cookie"
        private const val KEY_DDID = "ddid"
        private const val KEY_UID = "uid"
        private const val KEY_USERNAME = "username"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_DEVICE_ID = "device_id"

        /**
         * 纯解析函数（便于 JVM 单元测试）。
         */
        fun parseCompanionPayload(rawText: String): Result<AuthCredentials> {
            return try {
                val trimmed = rawText.trim()
                if (trimmed.isEmpty()) {
                    return Result.failure(IllegalArgumentException("导入内容为空"))
                }

                var rawCookie = ""
                var ddid = ""
                var deviceId = ""
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    val json = JSONObject(trimmed)
                    rawCookie = json.optString("cookie", "")
                    ddid = json.optString("ddid", "").trim()
                    val rawDeviceId = json.optString("deviceId", "")
                        .ifBlank { json.optString("szlmId", "") }
                        .ifBlank { json.optString("device_id", "") }
                    deviceId = DeviceProfile.parseShuzilmDeviceId(rawDeviceId)
                } else {
                    rawCookie = trimmed
                }

                val cookie = CookieParser.merge(rawCookie)
                if (cookie.isEmpty() || !CookieParser.hasValidAuth(cookie)) {
                    return Result.failure(IllegalArgumentException("未找到有效的 Cookie 登录凭据（缺少 token 或 uid）"))
                }

                val uid = CookieParser.extractValue(cookie, "uid")
                val updated = AuthCredentials(
                    cookie = cookie,
                    ddid = ddid,
                    uid = uid,
                    username = if (uid.isNotEmpty()) "UID: $uid" else "已登录用户",
                    avatar = "",
                    deviceId = deviceId
                )
                Result.success(updated)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
