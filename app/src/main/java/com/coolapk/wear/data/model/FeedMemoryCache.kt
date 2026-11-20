package com.coolapk.wear.data.model

/**
 * 线程安全的有界 LRU 动态内存缓存，避免手表端长时间浏览产生 OOM 或并发崩溃。
 */
object FeedMemoryCache {

    private const val MAX_ENTRIES = 200
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, FeedItem>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FeedItem>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    fun put(item: FeedItem) {
        if (item.id.isBlank()) return
        synchronized(lock) {
            cache[item.id] = item
        }
    }

    fun putAll(items: List<FeedItem>) {
        if (items.isEmpty()) return
        synchronized(lock) {
            for (item in items) {
                if (item.id.isNotBlank()) {
                    cache[item.id] = item
                }
            }
        }
    }

    fun get(id: String): FeedItem? {
        if (id.isBlank()) return null
        return synchronized(lock) {
            cache[id]
        }
    }

    fun update(id: String, transform: (FeedItem) -> FeedItem): FeedItem? {
        if (id.isBlank()) return null
        return synchronized(lock) {
            val current = cache[id] ?: return null
            val updated = transform(current)
            cache[id] = updated
            updated
        }
    }

    fun clear() {
        synchronized(lock) {
            cache.clear()
        }
    }

    fun size(): Int = synchronized(lock) { cache.size }
}
