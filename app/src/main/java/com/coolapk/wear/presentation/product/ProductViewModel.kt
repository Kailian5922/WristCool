package com.coolapk.wear.presentation.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.model.ProductItem
import com.coolapk.wear.data.repository.BlacklistRepository
import com.coolapk.wear.data.repository.FeedUpdateBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductUiState(
    val product: ProductItem? = null,
    val feeds: List<FeedItem> = emptyList(),
    val isLoadingDetail: Boolean = true,
    val isLoadingFeeds: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isTogglingFollow: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null,
    val actionMessage: String? = null
)

class ProductViewModel(
    private val api: CoolapkApi,
    private val productId: String,
    private val feedUpdateBus: FeedUpdateBus? = null,
    private val blacklistRepository: BlacklistRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductUiState())
    val uiState: StateFlow<ProductUiState> = _uiState.asStateFlow()

    private var feedsJob: Job? = null

    init {
        if (feedUpdateBus != null) {
            viewModelScope.launch {
                feedUpdateBus.updates.collect { updated ->
                    _uiState.update { state ->
                        if (state.feeds.none { it.id == updated.id }) state
                        else state.copy(feeds = state.feeds.map { if (it.id == updated.id) updated else it })
                    }
                }
            }
            viewModelScope.launch {
                feedUpdateBus.deletions.collect { deletedId ->
                    _uiState.update { state ->
                        if (state.feeds.none { it.id == deletedId }) state
                        else state.copy(feeds = state.feeds.filterNot { it.id == deletedId })
                    }
                }
            }
        }
        loadDetail()
        loadFeeds(refresh = true)
    }

    fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetail = true, error = null) }
            api.getProductDetail(productId).onSuccess { product ->
                _uiState.update { it.copy(product = product, isLoadingDetail = false) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingDetail = false,
                        error = err.message ?: "加载产品详情失败"
                    )
                }
            }
        }
    }

    fun loadFeeds(refresh: Boolean) {
        val current = _uiState.value
        if (!refresh && (current.isLoadingFeeds || current.isLoadingMore || !current.hasMore)) return
        if (refresh) feedsJob?.cancel() else if (feedsJob?.isActive == true) return
        val targetPage = if (refresh) 1 else current.page + 1

        feedsJob = viewModelScope.launch {
            _uiState.update {
                if (refresh) it.copy(isLoadingFeeds = true, isLoadingMore = false)
                else it.copy(isLoadingMore = true)
            }
            api.getProductFeeds(productId, targetPage).onSuccess { list ->
                val filtered = blacklistRepository?.filterFeeds(list) ?: list
                _uiState.update { state ->
                    val merged = if (refresh) filtered.distinctBy { it.id }
                    else (state.feeds + filtered).distinctBy { it.id }
                    val addedNew = refresh || merged.size > state.feeds.size
                    state.copy(
                        feeds = merged,
                        page = targetPage,
                        hasMore = list.isNotEmpty() && addedNew,
                        isLoadingFeeds = false,
                        isLoadingMore = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingFeeds = false, isLoadingMore = false) }
            }
        }
    }

    fun toggleFollow() {
        val product = _uiState.value.product ?: return
        if (_uiState.value.isTogglingFollow) return
        val nextFollow = !product.isFollow
        val nextCount = if (nextFollow) product.followNum + 1 else (product.followNum - 1).coerceAtLeast(0)
        _uiState.update {
            it.copy(
                product = product.copy(isFollow = nextFollow, followNum = nextCount),
                isTogglingFollow = true
            )
        }
        viewModelScope.launch {
            api.toggleFollowProduct(productId, nextFollow).onSuccess {
                _uiState.update {
                    it.copy(
                        isTogglingFollow = false,
                        actionMessage = if (nextFollow) "已关注该产品" else "已取消关注"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        product = product,
                        isTogglingFollow = false,
                        actionMessage = err.message ?: "操作失败，请检查登录状态"
                    )
                }
            }
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }
}
