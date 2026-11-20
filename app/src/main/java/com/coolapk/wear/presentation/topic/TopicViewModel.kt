package com.coolapk.wear.presentation.topic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.repository.FeedUpdateBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val tag: String,
    private val feedUpdateBus: FeedUpdateBus? = null
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
        }
        loadFeeds(refresh = true)
    }

    fun refresh() {
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
        loadJob = viewModelScope.launch {
            _uiState.update { state ->
                if (refresh) {
                    state.copy(isLoading = true, isLoadingMore = false, error = null)
                } else {
                    state.copy(isLoadingMore = true)
                }
            }

            val result = api.getTopicFeeds(tag, targetPage)
            result.onSuccess { newItems ->
                _uiState.update { state ->
                    val combined = if (refresh) {
                        newItems.distinctBy { it.id }
                    } else {
                        (state.items + newItems).distinctBy { it.id }
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
