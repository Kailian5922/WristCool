package com.coolapk.wear.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.model.TopicItem
import com.coolapk.wear.data.model.UserCardItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SearchTab(val label: String) {
    FEEDS("动态"),
    USERS("作者"),
    TOPICS("话题")
}

data class SearchUiState(
    val query: String = "",
    val currentTab: SearchTab = SearchTab.FEEDS,
    val hotKeywords: List<String> = emptyList(),
    val feedResults: List<FeedItem> = emptyList(),
    val userResults: List<UserCardItem> = emptyList(),
    val topicResults: List<TopicItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val hasSearched: Boolean = false
)

class SearchViewModel(private val api: CoolapkApi) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        loadHotKeywords()
    }

    fun loadHotKeywords() {
        viewModelScope.launch {
            val result = api.getHotSearchWords()
            result.onSuccess { words ->
                _uiState.value = _uiState.value.copy(hotKeywords = words)
            }
        }
    }

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun switchTab(tab: SearchTab) {
        if (_uiState.value.currentTab == tab) return
        _uiState.value = _uiState.value.copy(currentTab = tab, page = 1, hasMore = true)
        if (_uiState.value.query.isNotBlank()) {
            executeSearch(_uiState.value.query, tab, 1, refresh = true)
        }
    }

    fun search(newQuery: String? = null) {
        val targetQuery = (newQuery ?: _uiState.value.query).trim()
        if (targetQuery.isBlank()) return

        _uiState.value = _uiState.value.copy(
            query = targetQuery,
            hasSearched = true,
            page = 1,
            hasMore = true
        )
        executeSearch(targetQuery, _uiState.value.currentTab, 1, refresh = true)
    }

    private fun executeSearch(query: String, tab: SearchTab, page: Int, refresh: Boolean) {
        viewModelScope.launch {
            if (refresh) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            } else {
                _uiState.value = _uiState.value.copy(isLoadingMore = true)
            }

            when (tab) {
                SearchTab.FEEDS -> {
                    val result = api.searchFeeds(query, page)
                    result.onSuccess { list ->
                        val combined = if (refresh) list else (_uiState.value.feedResults + list).distinctBy { it.id }
                        _uiState.value = _uiState.value.copy(
                            feedResults = combined,
                            page = page,
                            hasMore = list.isNotEmpty(),
                            isLoading = false,
                            isLoadingMore = false,
                            error = null
                        )
                    }.onFailure { err ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = err.message ?: "搜索动态失败"
                        )
                    }
                }
                SearchTab.USERS -> {
                    val result = api.searchUsers(query, page)
                    result.onSuccess { list ->
                        val combined = if (refresh) list else (_uiState.value.userResults + list).distinctBy { it.uid }
                        _uiState.value = _uiState.value.copy(
                            userResults = combined,
                            page = page,
                            hasMore = list.isNotEmpty(),
                            isLoading = false,
                            isLoadingMore = false,
                            error = null
                        )
                    }.onFailure { err ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = err.message ?: "搜索用户失败"
                        )
                    }
                }
                SearchTab.TOPICS -> {
                    val result = api.searchTopics(query, page)
                    result.onSuccess { list ->
                        val combined = if (refresh) list else (_uiState.value.topicResults + list).distinctBy { it.title }
                        _uiState.value = _uiState.value.copy(
                            topicResults = combined,
                            page = page,
                            hasMore = list.isNotEmpty(),
                            isLoading = false,
                            isLoadingMore = false,
                            error = null
                        )
                    }.onFailure { err ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = err.message ?: "搜索话题失败"
                        )
                    }
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore || state.query.isBlank()) {
            return
        }
        val nextPage = state.page + 1
        executeSearch(state.query, state.currentTab, nextPage, refresh = false)
    }
}
