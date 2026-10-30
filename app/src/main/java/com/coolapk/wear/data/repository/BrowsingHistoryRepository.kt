package com.coolapk.wear.data.repository

import android.content.Context
import com.coolapk.wear.data.model.FeedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class BrowsingHistoryRepository(context: Context) {

    private val prefs = context.getSharedPreferences("coolapk_wear_history", Context.MODE_PRIVATE)

    private val _history = MutableStateFlow<List<FeedItem>>(loadHistory())
    val history: StateFlow<List<FeedItem>> = _history.asStateFlow()

    fun recordView(feed: FeedItem) {
        if (feed.id.isBlank()) return
        val current = _history.value.toMutableList()
        current.removeAll { it.id == feed.id }
        current.add(0, feed)
        val trimmed = if (current.size > 50) current.subList(0, 50) else current
        _history.value = trimmed
        saveHistory(trimmed)
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove("history_json").apply()
    }

    private fun loadHistory(): List<FeedItem> {
        val jsonStr = prefs.getString("history_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<FeedItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val pics = mutableListOf<String>()
                val picArr = obj.optJSONArray("picArr")
                if (picArr != null) {
                    for (p in 0 until picArr.length()) pics.add(picArr.optString(p))
                }
                list.add(
                    FeedItem(
                        id = obj.optString("id"),
                        uid = obj.optString("uid"),
                        username = obj.optString("username"),
                        userAvatar = obj.optString("userAvatar"),
                        dateline = obj.optLong("dateline"),
                        dateString = obj.optString("dateString"),
                        deviceTitle = obj.optString("deviceTitle"),
                        title = obj.optString("title"),
                        message = obj.optString("message"),
                        picArr = pics,
                        likeNum = obj.optInt("likeNum"),
                        replyNum = obj.optInt("replyNum"),
                        isLiked = obj.optBoolean("isLiked"),
                        isFavorite = obj.optBoolean("isFavorite")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveHistory(list: List<FeedItem>) {
        try {
            val arr = JSONArray()
            for (item in list) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("uid", item.uid)
                obj.put("username", item.username)
                obj.put("userAvatar", item.userAvatar)
                obj.put("dateline", item.dateline)
                obj.put("dateString", item.dateString)
                obj.put("deviceTitle", item.deviceTitle)
                obj.put("title", item.title)
                obj.put("message", item.message)
                obj.put("likeNum", item.likeNum)
                obj.put("replyNum", item.replyNum)
                obj.put("isLiked", item.isLiked)
                obj.put("isFavorite", item.isFavorite)
                val pics = JSONArray()
                item.picArr.forEach { pics.put(it) }
                obj.put("picArr", pics)
                arr.put(obj)
            }
            prefs.edit().putString("history_json", arr.toString()).apply()
        } catch (_: Exception) {}
    }
}
