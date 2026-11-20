package com.coolapk.wear.presentation.me

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

data class FavoritesUiState(
    val items: List<FeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null,
    val isMultiSelectMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isBatchDeleting: Boolean = false
)

class FavoritesViewModel(
    private val api: CoolapkApi,
    private val feedUpdateBus: FeedUpdateBus? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        if (feedUpdateBus != null) {
            viewModelScope.launch {
                feedUpdateBus.updates.collect { updated ->
                    _uiState.update { state ->
                        if (!updated.isFavorite) {
                            state.copy(
                                items = state.items.filterNot { it.id == updated.id },
                                selectedIds = state.selectedIds - updated.id
                            )
                        } else if (state.items.any { it.id == updated.id }) {
                            state.copy(
                                items = state.items.map { if (it.id == updated.id) updated else it }
                            )
                        } else {
                            state
                        }
                    }
                }
            }
        }
        loadFavorites(refresh = true)
    }

    fun refresh() {
        loadFavorites(refresh = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        loadFavorites(refresh = false)
    }

    fun enterMultiSelectMode(initialFeedId: String? = null) {
        val selected = if (initialFeedId != null) setOf(initialFeedId) else emptySet()
        _uiState.update {
            it.copy(
                isMultiSelectMode = true,
                selectedIds = selected
            )
        }
    }

    fun exitMultiSelectMode() {
        _uiState.update {
            it.copy(
                isMultiSelectMode = false,
                selectedIds = emptySet()
            )
        }
    }

    fun toggleSelection(feedId: String) {
        _uiState.update { state ->
            val updated = if (feedId in state.selectedIds) {
                state.selectedIds - feedId
            } else {
                state.selectedIds + feedId
            }
            state.copy(selectedIds = updated)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedIds = state.items.map { it.id }.toSet())
        }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun batchDeleteSelected(onComplete: (succeeded: Int, failed: Int) -> Unit) {
        val snapshot = _uiState.value
        if (snapshot.isBatchDeleting) return
        val toDelete = snapshot.selectedIds.toList()
        if (toDelete.isEmpty()) return

        _uiState.update { it.copy(isBatchDeleting = true) }

        viewModelScope.launch {
            val succeededIds = mutableSetOf<String>()
            var failedCount = 0
            for (id in toDelete) {
                val res = api.unFavoriteFeed(id)
                if (res.isSuccess) {
                    succeededIds.add(id)
                } else {
                    failedCount++
                }
            }
            // 仅移除服务端确实取消收藏成功的条目，失败项保留供重试
            _uiState.update { state ->
                val remainingItems = state.items.filterNot { it.id in succeededIds }
                val remainingSelected = state.selectedIds - succeededIds
                state.copy(
                    items = remainingItems,
                    isMultiSelectMode = failedCount > 0 && remainingSelected.isNotEmpty(),
                    selectedIds = if (failedCount > 0) remainingSelected else emptySet(),
                    isBatchDeleting = false
                )
            }
            onComplete(succeededIds.size, failedCount)
        }
    }

    private fun loadFavorites(refresh: Boolean) {
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

            val result = api.getMyFavoriteFeeds(targetPage)
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
                        error = err.message ?: "加载收藏失败"
                    )
                }
            }
        }
    }
}
