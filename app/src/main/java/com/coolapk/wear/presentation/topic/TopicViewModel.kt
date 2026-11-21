package com.coolapk.wear.presentation.topic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.model.TopicItem
import com.coolapk.wear.data.repository.BlacklistRepository
import com.coolapk.wear.data.repository.FeedUpdateBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TopicSort(val apiValue: String, val label: String) {
    DEFAULT("", "综合"),
    LATEST("dateline_desc", "最新"),
    HOT("hot", "热门")
}

data class TopicUiState(
    val topicDetail: TopicItem? = null,
    val sort: TopicSort = TopicSort.DEFAULT,
    val items: List<FeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isTogglingFollow: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null,
    val actionMessage: String? = null
)

class TopicViewModel(
    private val api: CoolapkApi,
    private val tag: String,
    private val feedUpdateBus: FeedUpdateBus? = null,
    private val blacklistRepository: BlacklistRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(TopicUiState())
    val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        if (feedUpdateBus != null) {
            viewModelScope.launch {
                feedUpdateBus.updates.collect { updated ->
                    _uiState.update { state ->
                        if (state.items.none { it.id == updated.id }) {
                            state
                        } else {
                            state.copy(
                                items = state.items.map { if (it.id == updated.id) updated else it }
                            )
                        }
                    }
                }
            }
            viewModelScope.launch {
                feedUpdateBus.deletions.collect { deletedId ->
                    _uiState.update { state ->
                        if (state.items.none { it.id == deletedId }) state
                        else state.copy(items = state.items.filterNot { it.id == deletedId })
                    }
                }
            }
        }
        loadTopicDetail()
        loadFeeds(refresh = true)
    }

    private fun loadTopicDetail() {
        viewModelScope.launch {
            api.getTopicDetail(tag).onSuccess { detail ->
                _uiState.update { it.copy(topicDetail = detail) }
            }
        }
    }

    fun setSort(sort: TopicSort) {
        if (_uiState.value.sort == sort) return
        _uiState.update { it.copy(sort = sort, items = emptyList(), page = 1, hasMore = true) }
        loadFeeds(refresh = true)
    }

    fun toggleFollow() {
        val current = _uiState.value.topicDetail ?: TopicItem(id = tag, title = tag)
        if (_uiState.value.isTogglingFollow) return
        val nextFollow = !current.isFollow
        val nextCount = if (nextFollow) current.followNum + 1 else (current.followNum - 1).coerceAtLeast(0)
        _uiState.update {
            it.copy(
                topicDetail = current.copy(isFollow = nextFollow, followNum = nextCount),
                isTogglingFollow = true
            )
        }
        viewModelScope.launch {
            api.toggleFollowTopic(tag, nextFollow).onSuccess {
                _uiState.update {
                    it.copy(
                        isTogglingFollow = false,
                        actionMessage = if (nextFollow) "已关注话题 #$tag#" else "已取消关注话题"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        topicDetail = current,
                        isTogglingFollow = false,
                        actionMessage = err.message ?: "关注操作失败，请检查登录状态"
                    )
                }
            }
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    fun refresh() {
        loadTopicDetail()
        loadFeeds(refresh = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        loadFeeds(refresh = false)
    }

    private fun loadFeeds(refresh: Boolean) {
        if (refresh) {
            loadJob?.cancel()
        } else if (loadJob?.isActive == true) {
            return
        }

        val targetPage = if (refresh) 1 else _uiState.value.page + 1
        val currentSort = _uiState.value.sort
        loadJob = viewModelScope.launch {
            _uiState.update { state ->
                if (refresh) {
                    state.copy(isLoading = true, isLoadingMore = false, error = null)
                } else {
                    state.copy(isLoadingMore = true)
                }
            }

            val result = api.getTopicFeeds(tag, targetPage, listType = currentSort.apiValue)
            if (_uiState.value.sort != currentSort) return@launch
            result.onSuccess { newItems ->
                val filtered = blacklistRepository?.filterFeeds(newItems) ?: newItems
                _uiState.update { state ->
                    val combined = if (refresh) {
                        filtered.distinctBy { it.id }
                    } else {
                        (state.items + filtered).distinctBy { it.id }
                    }
                    val addedNew = refresh || combined.size > state.items.size
                    state.copy(
                        items = combined,
                        page = targetPage,
                        hasMore = newItems.isNotEmpty() && addedNew,
                        isLoading = false,
                        isLoadingMore = false,
                        error = null
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = err.message ?: "加载话题动态失败"
                    )
                }
            }
        }
    }
}
