package com.coolapk.wear.presentation.feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.repository.AuthRepository
import com.coolapk.wear.data.repository.FeedUpdateBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val authRepository: AuthRepository,
    private val feedUpdateBus: FeedUpdateBus? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FeedsUiState(
            currentTab = FeedTab.HEADLINE,
            isLoggedIn = authRepository.credentials.value.isLoggedIn,
            userAvatar = authRepository.credentials.value.avatar
        )
    )
    val uiState: StateFlow<FeedsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.credentials.collect { creds ->
                val wasLoggedIn = _uiState.value.isLoggedIn
                _uiState.update { state ->
                    state.copy(
                        isLoggedIn = creds.isLoggedIn,
                        userAvatar = creds.avatar
                    )
                }
                if (_uiState.value.currentTab == FeedTab.TIMELINE) {
                    if (!wasLoggedIn && creds.isLoggedIn) {
                        loadFeeds(refresh = true)
                    } else if (wasLoggedIn && !creds.isLoggedIn) {
                        loadJob?.cancel()
                        _uiState.update { state ->
                            state.copy(
                                items = emptyList(),
                                isLoading = false,
                                isLoadingMore = false,
                                hasMore = false,
                                error = null
                            )
                        }
                    }
                }
            }
        }

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

    fun switchTab(tab: FeedTab) {
        if (_uiState.value.currentTab == tab) {
            refresh()
            return
        }
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                currentTab = tab,
                items = emptyList(),
                page = 1,
                hasMore = true,
                isLoading = false,
                isLoadingMore = false,
                error = null
            )
        }
        loadFeeds(refresh = true)
    }

    fun refresh() {
        loadFeeds(refresh = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        if (state.currentTab == FeedTab.TIMELINE && !state.isLoggedIn) return
        loadFeeds(refresh = false)
    }

    private fun loadFeeds(refresh: Boolean) {
        val tab = _uiState.value.currentTab
        if (tab == FeedTab.TIMELINE && !_uiState.value.isLoggedIn) {
            loadJob?.cancel()
            _uiState.update {
                it.copy(
                    items = emptyList(),
                    isLoading = false,
                    isLoadingMore = false,
                    hasMore = false,
                    error = "请先在【我的】中导入账号登录，即可查看关注动态"
                )
            }
            return
        }

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

            val result = when (tab) {
                FeedTab.HOT -> api.getHotFeeds(targetPage)
                FeedTab.HEADLINE -> api.getHeadlineFeeds(targetPage)
                FeedTab.TIMELINE -> api.getTimelineFeeds(targetPage)
                FeedTab.PICTURE -> api.getPictureFeeds(targetPage)
            }

            // 防止切换 Tab 后旧响应覆盖新 Tab 数据
            if (_uiState.value.currentTab != tab) return@launch

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
                        error = err.message ?: "加载失败，请检查网络"
                    )
                }
            }
        }
    }
}
