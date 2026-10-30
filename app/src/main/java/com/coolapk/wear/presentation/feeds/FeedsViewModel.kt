package com.coolapk.wear.presentation.feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FeedTab(val title: String) {
    HEADLINE("头条"),
    HOT("热榜"),
    TIMELINE("关注"),
    PICTURE("酷图")
}

data class FeedsUiState(
    val currentTab: FeedTab = FeedTab.HEADLINE,
    val items: List<FeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val isLoggedIn: Boolean = false,
    val userAvatar: String = ""
)

class FeedsViewModel(
    private val api: CoolapkApi,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FeedsUiState(
            currentTab = FeedTab.HEADLINE,
            isLoggedIn = authRepository.credentials.value.isLoggedIn,
            userAvatar = authRepository.credentials.value.avatar
        )
    )
    val uiState: StateFlow<FeedsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.credentials.collect { creds ->
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = creds.isLoggedIn,
                    userAvatar = creds.avatar
                )
            }
        }
        loadFeeds(refresh = true)
    }

    fun switchTab(tab: FeedTab) {
        if (_uiState.value.currentTab == tab) {
            // 点击当前激活的 Tab 触发刷新
            refresh()
            return
        }
        _uiState.value = _uiState.value.copy(
            currentTab = tab,
            items = emptyList(),
            page = 1,
            hasMore = true,
            error = null
        )
        loadFeeds(refresh = true)
    }

    fun refresh() {
        loadFeeds(refresh = true)
    }

    fun loadMore() {
        if (_uiState.value.isLoading || _uiState.value.isLoadingMore || !_uiState.value.hasMore) {
            return
        }
        loadFeeds(refresh = false)
    }

    private fun loadFeeds(refresh: Boolean) {
        val tab = _uiState.value.currentTab
        if (tab == FeedTab.TIMELINE && !_uiState.value.isLoggedIn) {
            _uiState.value = _uiState.value.copy(
                items = emptyList(),
                isLoading = false,
                isLoadingMore = false,
                error = "请先在【我的】中导入账号登录，即可查看关注动态"
            )
            return
        }

        val targetPage = if (refresh) 1 else _uiState.value.page + 1
        viewModelScope.launch {
            if (refresh) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            } else {
                _uiState.value = _uiState.value.copy(isLoadingMore = true)
            }

            val result = when (tab) {
                FeedTab.HOT -> api.getHotFeeds(targetPage)
                FeedTab.HEADLINE -> api.getHeadlineFeeds(targetPage)
                FeedTab.TIMELINE -> api.getTimelineFeeds(targetPage)
                FeedTab.PICTURE -> api.getPictureFeeds(targetPage)
            }

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
                    error = err.message ?: "加载失败，请检查网络"
                )
            }
        }
    }
}
