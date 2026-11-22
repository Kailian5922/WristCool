package com.coolapk.wear.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.AppItem
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.model.ProductItem
import com.coolapk.wear.data.model.TopicItem
import com.coolapk.wear.data.model.UserCardItem
import com.coolapk.wear.data.repository.BlacklistRepository
import com.coolapk.wear.data.repository.FeedUpdateBus
import com.coolapk.wear.data.repository.SearchHistoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchTab(val label: String) {
    FEEDS("动态"),
    USERS("作者"),
    TOPICS("话题"),
    PRODUCTS("数码"),
    APPS("应用")
}

enum class SearchFeedSort(val apiValue: String, val label: String) {
    DEFAULT("default", "综合"),
    REALTIME("dateline", "实时"),
    HOT("hot", "热度"),
    REPLY("reply", "回复")
}

data class SearchUiState(
    val query: String = "",
    val currentTab: SearchTab = SearchTab.FEEDS,
    val feedSort: SearchFeedSort = SearchFeedSort.DEFAULT,
    val hotKeywords: List<String> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val feedResults: List<FeedItem> = emptyList(),
    val userResults: List<UserCardItem> = emptyList(),
    val topicResults: List<TopicItem> = emptyList(),
    val productResults: List<ProductItem> = emptyList(),
    val appResults: List<AppItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val hasSearched: Boolean = false
) {
    val activeResultCount: Int
        get() = when (currentTab) {
            SearchTab.FEEDS -> feedResults.size
            SearchTab.USERS -> userResults.size
            SearchTab.TOPICS -> topicResults.size
            SearchTab.PRODUCTS -> productResults.size
            SearchTab.APPS -> appResults.size
        }
}

class SearchViewModel(
    private val api: CoolapkApi,
    private val feedUpdateBus: FeedUpdateBus? = null,
    private val searchHistoryRepository: SearchHistoryRepository? = null,
    private val blacklistRepository: BlacklistRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SearchUiState(searchHistory = searchHistoryRepository?.history?.value.orEmpty())
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var suggestJob: Job? = null

    init {
        loadHotKeywords()
        if (searchHistoryRepository != null) {
            viewModelScope.launch {
                searchHistoryRepository.history.collect { list ->
                    _uiState.update { it.copy(searchHistory = list) }
                }
            }
        }
        if (feedUpdateBus != null) {
            viewModelScope.launch {
                feedUpdateBus.updates.collect { updated ->
                    _uiState.update { state ->
                        if (state.feedResults.none { it.id == updated.id }) {
                            state
                        } else {
                            state.copy(
                                feedResults = state.feedResults.map { if (it.id == updated.id) updated else it }
                            )
                        }
                    }
                }
            }
            viewModelScope.launch {
                feedUpdateBus.deletions.collect { deletedId ->
                    _uiState.update { state ->
                        if (state.feedResults.none { it.id == deletedId }) state
                        else state.copy(feedResults = state.feedResults.filterNot { it.id == deletedId })
                    }
                }
            }
        }
    }

    fun loadHotKeywords() {
        viewModelScope.launch {
            api.getHotSearchWords().onSuccess { words ->
                _uiState.update { it.copy(hotKeywords = words) }
            }
        }
    }

    fun updateQuery(query: String) {
        if (query.isEmpty()) {
            searchJob?.cancel()
            suggestJob?.cancel()
            _uiState.update {
                it.copy(
                    query = "",
                    suggestions = emptyList(),
                    hasSearched = false,
                    isLoading = false,
                    isLoadingMore = false,
                    error = null
                )
            }
        } else {
            _uiState.update { it.copy(query = query) }
            if (!_uiState.value.hasSearched) {
                fetchSuggestionsDebounced(query)
            }
        }
    }

    private fun fetchSuggestionsDebounced(query: String) {
        suggestJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(280)
            api.getSearchSuggestions(trimmed).onSuccess { list ->
                if (_uiState.value.query.trim() == trimmed && !_uiState.value.hasSearched) {
                    _uiState.update { it.copy(suggestions = list.take(6)) }
                }
            }
        }
    }

    fun removeHistoryItem(keyword: String) {
        searchHistoryRepository?.remove(keyword)
    }

    fun clearHistory() {
        searchHistoryRepository?.clear()
    }

    fun switchTab(tab: SearchTab) {
        if (_uiState.value.currentTab == tab) return
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                currentTab = tab,
                page = 1,
                hasMore = true,
                isLoading = false,
                isLoadingMore = false,
                error = null
            )
        }
        val currentQuery = _uiState.value.query.trim()
        if (currentQuery.isNotBlank() && _uiState.value.hasSearched) {
            executeSearch(currentQuery, tab, _uiState.value.feedSort, 1, refresh = true)
        }
    }

    fun setFeedSort(sort: SearchFeedSort) {
        if (_uiState.value.feedSort == sort) return
        _uiState.update { it.copy(feedSort = sort, page = 1, hasMore = true) }
        val currentQuery = _uiState.value.query.trim()
        if (currentQuery.isNotBlank() && _uiState.value.hasSearched && _uiState.value.currentTab == SearchTab.FEEDS) {
            executeSearch(currentQuery, SearchTab.FEEDS, sort, 1, refresh = true)
        }
    }

    fun search(newQuery: String? = null) {
        val targetQuery = (newQuery ?: _uiState.value.query).trim()
        if (targetQuery.isBlank()) return

        searchHistoryRepository?.add(targetQuery)
        suggestJob?.cancel()
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                query = targetQuery,
                suggestions = emptyList(),
                hasSearched = true,
                page = 1,
                hasMore = true,
                error = null
            )
        }
        executeSearch(targetQuery, _uiState.value.currentTab, _uiState.value.feedSort, 1, refresh = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasSearched || state.isLoading || state.isLoadingMore || !state.hasMore || state.query.isBlank()) {
            return
        }
        executeSearch(state.query.trim(), state.currentTab, state.feedSort, state.page + 1, refresh = false)
    }

    private fun executeSearch(
        query: String,
        tab: SearchTab,
        sort: SearchFeedSort,
        page: Int,
        refresh: Boolean
    ) {
        if (refresh) {
            searchJob?.cancel()
        } else if (searchJob?.isActive == true) {
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.update { state ->
                if (refresh) {
                    state.copy(isLoading = true, isLoadingMore = false, error = null)
                } else {
                    state.copy(isLoadingMore = true)
                }
            }

            when (tab) {
                SearchTab.FEEDS -> {
                    val result = api.searchFeeds(query, page, sort = sort.apiValue)
                    if (_uiState.value.currentTab != tab || _uiState.value.query.trim() != query) return@launch
                    result.onSuccess { list ->
                        val filtered = blacklistRepository?.filterFeeds(list) ?: list
                        _uiState.update { state ->
                            val combined = if (refresh) filtered.distinctBy { it.id }
                            else (state.feedResults + filtered).distinctBy { it.id }
                            val addedNew = refresh || combined.size > state.feedResults.size
                            state.copy(
                                feedResults = combined,
                                page = page,
                                hasMore = list.isNotEmpty() && addedNew,
                                isLoading = false,
                                isLoadingMore = false,
                                error = null
                            )
                        }
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(isLoading = false, isLoadingMore = false, error = err.message ?: "搜索动态失败")
                        }
                    }
                }
                SearchTab.USERS -> {
                    val result = api.searchUsers(query, page)
                    if (_uiState.value.currentTab != tab || _uiState.value.query.trim() != query) return@launch
                    result.onSuccess { list ->
                        _uiState.update { state ->
                            val combined = if (refresh) list.distinctBy { it.uid }
                            else (state.userResults + list).distinctBy { it.uid }
                            val addedNew = refresh || combined.size > state.userResults.size
                            state.copy(
                                userResults = combined,
                                page = page,
                                hasMore = list.isNotEmpty() && addedNew,
                                isLoading = false,
                                isLoadingMore = false,
                                error = null
                            )
                        }
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(isLoading = false, isLoadingMore = false, error = err.message ?: "搜索用户失败")
                        }
                    }
                }
                SearchTab.TOPICS -> {
                    val result = api.searchTopics(query, page)
                    if (_uiState.value.currentTab != tab || _uiState.value.query.trim() != query) return@launch
                    result.onSuccess { list ->
                        _uiState.update { state ->
                            val combined = if (refresh) list.distinctBy { it.title }
                            else (state.topicResults + list).distinctBy { it.title }
                            val addedNew = refresh || combined.size > state.topicResults.size
                            state.copy(
                                topicResults = combined,
                                page = page,
                                hasMore = list.isNotEmpty() && addedNew,
                                isLoading = false,
                                isLoadingMore = false,
                                error = null
                            )
                        }
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(isLoading = false, isLoadingMore = false, error = err.message ?: "搜索话题失败")
                        }
                    }
                }
                SearchTab.PRODUCTS -> {
                    val result = api.searchProducts(query, page)
                    if (_uiState.value.currentTab != tab || _uiState.value.query.trim() != query) return@launch
                    result.onSuccess { list ->
                        _uiState.update { state ->
                            val combined = if (refresh) list.distinctBy { it.id }
                            else (state.productResults + list).distinctBy { it.id }
                            val addedNew = refresh || combined.size > state.productResults.size
                            state.copy(
                                productResults = combined,
                                page = page,
                                hasMore = list.isNotEmpty() && addedNew,
                                isLoading = false,
                                isLoadingMore = false,
                                error = null
                            )
                        }
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(isLoading = false, isLoadingMore = false, error = err.message ?: "搜索数码产品失败")
                        }
                    }
                }
                SearchTab.APPS -> {
                    val result = api.searchApps(query, page)
                    if (_uiState.value.currentTab != tab || _uiState.value.query.trim() != query) return@launch
                    result.onSuccess { list ->
                        _uiState.update { state ->
                            val combined = if (refresh) list.distinctBy { it.id }
                            else (state.appResults + list).distinctBy { it.id }
                            val addedNew = refresh || combined.size > state.appResults.size
                            state.copy(
                                appResults = combined,
                                page = page,
                                hasMore = list.isNotEmpty() && addedNew,
                                isLoading = false,
                                isLoadingMore = false,
                                error = null
                            )
                        }
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(isLoading = false, isLoadingMore = false, error = err.message ?: "搜索应用失败")
                        }
                    }
                }
            }
        }
    }
}
