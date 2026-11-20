package com.coolapk.wear.data.repository

import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.model.FeedMemoryCache
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 动态状态变更事件总线。
 * 当用户在详情页点赞、收藏或发表回复时，通知所有订阅该动态的列表页面实时同步状态，避免返回列表后数据陈旧。
 */
class FeedUpdateBus {
    private val _updates = MutableSharedFlow<FeedItem>(extraBufferCapacity = 32)
    val updates: SharedFlow<FeedItem> = _updates.asSharedFlow()

    fun publish(updated: FeedItem) {
        if (updated.id.isBlank()) return
        FeedMemoryCache.put(updated)
        _updates.tryEmit(updated)
    }
}
