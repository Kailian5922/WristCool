package com.coolapk.wear.data.repository

import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.model.FeedMemoryCache
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 动态状态变更事件总线。
 * 1. 点赞、收藏、评论、投票等操作实时同步至所有订阅该动态的列表；
 * 2. 删除动态或发表新动态时广播事件，让列表立即同步刷新。
 */
class FeedUpdateBus {
    private val _updates = MutableSharedFlow<FeedItem>(extraBufferCapacity = 32)
    val updates: SharedFlow<FeedItem> = _updates.asSharedFlow()

    private val _deletions = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val deletions: SharedFlow<String> = _deletions.asSharedFlow()

    private val _feedCreated = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val feedCreated: SharedFlow<Unit> = _feedCreated.asSharedFlow()

    fun publish(updated: FeedItem) {
        if (updated.id.isBlank()) return
        FeedMemoryCache.put(updated)
        _updates.tryEmit(updated)
    }

    fun publishDeleted(feedId: String) {
        if (feedId.isBlank()) return
        _deletions.tryEmit(feedId)
    }

    fun publishFeedCreated() {
        _feedCreated.tryEmit(Unit)
    }
}
