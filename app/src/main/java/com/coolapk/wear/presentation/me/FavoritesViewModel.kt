package com.coolapk.wear.presentation.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.FeedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val api: CoolapkApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        loadFavorites(refresh = true)
    }

    fun refresh() {
        loadFavorites(refresh = true)
    }

    fun loadMore() {
        if (_uiState.value.isLoading || _uiState.value.isLoadingMore || !_uiState.value.hasMore) return
        loadFavorites(refresh = false)
    }

    fun enterMultiSelectMode(initialFeedId: String? = null) {
        val selected = if (initialFeedId != null) setOf(initialFeedId) else emptySet()
        _uiState.value = _uiState.value.copy(
            isMultiSelectMode = true,
            selectedIds = selected
        )
    }

    fun exitMultiSelectMode() {
        _uiState.value = _uiState.value.copy(
            isMultiSelectMode = false,
            selectedIds = emptySet()
        )
    }

    fun toggleSelection(feedId: String) {
        val current = _uiState.value.selectedIds.toMutableSet()
        if (current.contains(feedId)) {
            current.remove(feedId)
        } else {
            current.add(feedId)
        }
        _uiState.value = _uiState.value.copy(selectedIds = current)
    }

    fun selectAll() {
        val allIds = _uiState.value.items.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(selectedIds = allIds)
    }

    fun deselectAll() {
        _uiState.value = _uiState.value.copy(selectedIds = emptySet())
    }

    fun batchDeleteSelected(onComplete: (Int) -> Unit) {
        val toDelete = _uiState.value.selectedIds.toList()
        if (toDelete.isEmpty()) return

        _uiState.value = _uiState.value.copy(isBatchDeleting = true)

        viewModelScope.launch {
            var successCount = 0
            for (id in toDelete) {
                val res = api.unFavoriteFeed(id)
                if (res.isSuccess) {
                    successCount++
                }
            }
            // 从本地列表中移除已删除项
            val remainingItems = _uiState.value.items.filterNot { toDelete.contains(it.id) }
            _uiState.value = _uiState.value.copy(
                items = remainingItems,
                isMultiSelectMode = false,
                selectedIds = emptySet(),
                isBatchDeleting = false
            )
            onComplete(successCount)
        }
    }

    private fun loadFavorites(refresh: Boolean) {
        val targetPage = if (refresh) 1 else _uiState.value.page + 1
        viewModelScope.launch {
            if (refresh) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            } else {
                _uiState.value = _uiState.value.copy(isLoadingMore = true)
            }

            val result = api.getMyFavoriteFeeds(targetPage)
            result.onSuccess { newItems ->
                val combined = if (refresh) newItems else _uiState.value.items + newItems
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
                    error = err.message ?: "加载收藏失败"
                )
            }
        }
    }
}
