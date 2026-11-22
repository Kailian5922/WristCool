package com.coolapk.wear.data.repository

import android.content.Context
import com.coolapk.wear.data.model.FeedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

class BrowsingHistoryRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistMutex = Mutex()

    private val _history = MutableStateFlow<List<FeedItem>>(loadHistory())
    val history: StateFlow<List<FeedItem>> = _history.asStateFlow()

    fun recordView(feed: FeedItem) {
        if (feed.id.isBlank()) return
        var snapshot: List<FeedItem> = emptyList()
        _history.update { current ->
            val updated = buildList(minOf(current.size + 1, MAX_HISTORY_ITEMS)) {
                add(feed)
                for (item in current) {
                    if (item.id != feed.id && size < MAX_HISTORY_ITEMS) {
                        add(item)
                    }
                }
            }
            snapshot = updated
            updated
        }
        persistAsync(snapshot)
    }

    fun updateFeedIfPresent(updatedFeed: FeedItem) {
        if (updatedFeed.id.isBlank()) return
        var changed = false
        var snapshot: List<FeedItem> = emptyList()
        _history.update { current ->
            val mapped = current.map { existing ->
                if (existing.id == updatedFeed.id) {
                    changed = true
                    updatedFeed
                } else {
                    existing
                }
            }
            snapshot = mapped
            mapped
        }
        if (changed) {
            persistAsync(snapshot)
        }
    }

    fun removeItem(feedId: String) {
        if (feedId.isBlank()) return
        var snapshot: List<FeedItem> = emptyList()
        var changed = false
        _history.update { current ->
            val filtered = current.filterNot { it.id == feedId }
            changed = filtered.size != current.size
            snapshot = filtered
            filtered
        }
        if (changed) {
            persistAsync(snapshot)
        }
    }

    fun clearHistory() {
        _history.value = emptyList()
        ioScope.launch {
            persistMutex.withLock {
                prefs.edit().remove(KEY_HISTORY_JSON).apply()
            }
        }
    }

    private fun persistAsync(list: List<FeedItem>) {
        ioScope.launch {
            persistMutex.withLock {
                saveHistory(list)
            }
        }
    }

    private fun loadHistory(): List<FeedItem> {
        val jsonStr = prefs.getString(KEY_HISTORY_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = ArrayList<FeedItem>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                if (id.isBlank()) continue
                val pics = mutableListOf<String>()
                val picArr = obj.optJSONArray("picArr")
                if (picArr != null) {
                    for (p in 0 until picArr.length()) {
                        val url = picArr.optString(p, "")
                        if (url.isNotBlank()) pics.add(url)
                    }
                }
                list.add(
                    FeedItem(
                        id = id,
                        uid = obj.optString("uid", ""),
                        username = obj.optString("username", ""),
                        userAvatar = obj.optString("userAvatar", ""),
                        dateline = obj.optLong("dateline", 0L),
                        dateString = obj.optString("dateString", ""),
                        deviceTitle = obj.optString("deviceTitle", ""),
                        title = obj.optString("title", ""),
                        message = obj.optString("message", ""),
                        picArr = pics,
                        likeNum = obj.optInt("likeNum", 0),
                        replyNum = obj.optInt("replyNum", 0),
                        isLiked = obj.optBoolean("isLiked", false),
                        isFavorite = obj.optBoolean("isFavorite", false)
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
            prefs.edit().putString(KEY_HISTORY_JSON, arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val PREFS_NAME = "coolapk_wear_history"
        private const val KEY_HISTORY_JSON = "history_json"
        private const val MAX_HISTORY_ITEMS = 50
    }
}
