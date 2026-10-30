package com.coolapk.wear.data.model

object FeedMemoryCache {
    private val cache = mutableMapOf<String, FeedItem>()

    fun put(item: FeedItem) {
        if (item.id.isNotBlank()) {
            cache[item.id] = item
        }
    }

    fun putAll(items: List<FeedItem>) {
        items.forEach { put(it) }
    }

    fun get(id: String): FeedItem? = cache[id]
}
