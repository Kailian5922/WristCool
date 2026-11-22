package com.coolapk.wear.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray

/**
 * 本地搜索历史记录仓储（最多保留 20 条，去重前置）。
 */
class SearchHistoryRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _history = MutableStateFlow(load())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    fun add(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        _history.update { current ->
            val updated = buildList(minOf(current.size + 1, MAX_ITEMS)) {
                add(clean)
                for (item in current) {
                    if (!item.equals(clean, ignoreCase = true) && size < MAX_ITEMS) {
                        add(item)
                    }
                }
            }
            persist(updated)
            updated
        }
    }

    fun remove(query: String) {
        _history.update { current ->
            val updated = current.filterNot { it == query }
            persist(updated)
            updated
        }
    }

    fun clear() {
        _history.value = emptyList()
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun load(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i, "").trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(items: List<String>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "coolapk_wear_search_history"
        private const val KEY_HISTORY = "keywords_json"
        private const val MAX_ITEMS = 20
    }
}
