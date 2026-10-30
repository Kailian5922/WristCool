package com.coolapk.wear.presentation.topic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.FeedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TopicUiState(
    val items: List<FeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null
)

class TopicViewModel(
    private val api: CoolapkApi,
    private val tag: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(TopicUiState())
    val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

    init {
        loadFeeds(refresh = true)
    }

    fun refresh() {
        loadFeeds(refresh = true)
    }

    fun loadMore() {
        if (_uiState.value.isLoading || _uiState.value.isLoadingMore || !_uiState.value.hasMore) return
        loadFeeds(refresh = false)
    }

    private fun loadFeeds(refresh: Boolean) {
        val targetPage = if (refresh) 1 else _uiState.value.page + 1
        viewModelScope.launch {
            if (refresh) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            } else {
                _uiState.value = _uiState.value.copy(isLoadingMore = true)
            }

            val result = api.getTopicFeeds(tag, targetPage)
            result.onSuccess { newItems ->
                val combined = (if (refresh) newItems else _uiState.value.items + newItems).distinctBy { it.id }
                _uiState.value = _uiState.value.copy(
                    items = combined,
                    page = targetPage,
                    hasMore = newItems.isNotEmpty(),
                    isLoading = false,
                    isLoadingMore = false,
                    error = null
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = err.message ?: "加载话题动态失败"
                )
            }
        }
    }
}
