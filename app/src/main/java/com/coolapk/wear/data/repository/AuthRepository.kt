package com.coolapk.wear.data.repository

import android.content.Context
import com.coolapk.wear.data.model.AuthCredentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class AuthRepository(context: Context) {

    private val prefs = context.getSharedPreferences("coolapk_wear_auth", Context.MODE_PRIVATE)

    private val _credentials = MutableStateFlow(loadCredentials())
    val credentials: StateFlow<AuthCredentials> = _credentials.asStateFlow()

    private fun loadCredentials(): AuthCredentials {
        val cookie = prefs.getString("cookie", "") ?: ""
        val ddid = prefs.getString("ddid", "") ?: ""
        val uid = prefs.getString("uid", "") ?: extractUidFromCookie(cookie)
        val username = prefs.getString("username", "") ?: ""
        val avatar = prefs.getString("avatar", "") ?: ""
        return AuthCredentials(cookie, ddid, uid, username, avatar)
    }

    fun saveCredentials(credentials: AuthCredentials) {
        prefs.edit()
            .putString("cookie", credentials.cookie)
            .putString("ddid", credentials.ddid)
            .putString("uid", credentials.uid)
            .putString("username", credentials.username)
            .putString("avatar", credentials.avatar)
            .apply()
        _credentials.value = credentials
    }

    /**
     * 导入伴侣端导出的登录信息。
     * 支持手机端复制的 JSON 字符串 {"cookie":"...", "ddid":"..."}，
     * 同时也兼容直接粘贴纯 Cookie 字符串。
     */
    fun importCompanionPayload(rawText: String): Result<AuthCredentials> {
        return try {
            val trimmed = rawText.trim()
            if (trimmed.isEmpty()) {
                return Result.failure(IllegalArgumentException("导入内容为空"))
            }

            var cookie = ""
            var ddid = ""
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val json = JSONObject(trimmed)
                cookie = json.optString("cookie", "")
                ddid = json.optString("ddid", "")
            } else {
                cookie = trimmed
            }

            if (cookie.isEmpty()) {
                return Result.failure(IllegalArgumentException("未找到有效的 Cookie 登录凭据"))
            }

            val uid = extractUidFromCookie(cookie)
            val updated = AuthCredentials(
                cookie = cookie,
                ddid = ddid,
                uid = uid,
                username = if (uid.isNotEmpty()) "UID: $uid" else "已登录用户",
                avatar = ""
            )
            saveCredentials(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateUserInfo(username: String, avatar: String) {
        val current = _credentials.value
        val updated = current.copy(username = username, avatar = avatar)
        saveCredentials(updated)
    }

    fun logout() {
        prefs.edit().clear().apply()
        _credentials.value = AuthCredentials()
    }

    private fun extractUidFromCookie(cookie: String): String {
        val parts = cookie.split(";")
        for (part in parts) {
            val kv = part.trim().split("=")
            if (kv.size == 2 && kv[0].trim().equals("uid", ignoreCase = true)) {
                return kv[1].trim()
            }
        }
        return ""
    }
}
