package com.coolapk.wear.presentation.feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.AuthCredentials
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.repository.AuthRepository
import com.coolapk.wear.data.repository.BlacklistRepository
import com.coolapk.wear.data.repository.FeedUpdateBus
import com.coolapk.wear.data.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FeedTab(val title: String) {
    HEADLINE("头条"),
    RECOMMEND("推荐"),
    HOT("热榜"),
    TIMELINE("关注"),
    PICTURE("酷图"),
    DIGITAL("数码"),
    SECONDHAND("二手");

    companion object {
        fun fromName(name: String?): FeedTab =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: HEADLINE
    }
}

enum class FollowGroup(val apiValue: String, val label: String) {
    ALL("all", "全部"),
    CIRCLE("circle", "好友"),
    TOPIC("topic", "话题"),
    PRODUCT("product", "数码"),
    APK("apk", "应用")
}

data class FeedsUiState(
    val currentTab: FeedTab = FeedTab.HEADLINE,
    val followGroup: FollowGroup = FollowGroup.ALL,
    val rawItems: List<FeedItem> = emptyList(),
    val items: List<FeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isPublishing: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val isLoggedIn: Boolean = false,
    val userAvatar: String = ""
)

class FeedsViewModel(
    private val api: CoolapkApi,
    private val authRepository: AuthRepository? = null,
    private val feedUpdateBus: FeedUpdateBus? = null,
    private val blacklistRepository: BlacklistRepository? = null,
    private val settingsRepository: SettingsRepository? = null,
    credentialsFlow: StateFlow<AuthCredentials>? = null
) : ViewModel() {

    private val effectiveCredentialsFlow: StateFlow<AuthCredentials> =
        credentialsFlow ?: authRepository?.credentials ?: MutableStateFlow(AuthCredentials())

    private data class ChannelKey(
        val tab: FeedTab,
        val followGroup: FollowGroup = FollowGroup.ALL
    )

    private data class ChannelSnapshot(
        val rawItems: List<FeedItem>,
        val page: Int,
        val hasMore: Boolean
    )

    private fun cacheKeyOf(tab: FeedTab, group: FollowGroup): ChannelKey =
        ChannelKey(tab = tab, followGroup = if (tab == FeedTab.TIMELINE) group else FollowGroup.ALL)

    private val initialTab = FeedTab.fromName(settingsRepository?.settings?.value?.defaultFeedTab)
    private val channelCache = mutableMapOf<ChannelKey, ChannelSnapshot>()

    private val _uiState = MutableStateFlow(
        FeedsUiState(
            currentTab = initialTab,
            isLoggedIn = effectiveCredentialsFlow.value.isLoggedIn,
            userAvatar = effectiveCredentialsFlow.value.avatar
        )
    )
    val uiState: StateFlow<FeedsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            effectiveCredentialsFlow.collect { creds ->
                val wasLoggedIn = _uiState.value.isLoggedIn
                _uiState.update { state ->
                    state.copy(
                        isLoggedIn = creds.isLoggedIn,
                        userAvatar = creds.avatar
                    )
                }
                if (wasLoggedIn != creds.isLoggedIn) {
                    channelCache.keys.removeAll { it.tab == FeedTab.TIMELINE }
                }
                if (_uiState.value.currentTab == FeedTab.TIMELINE) {
                    if (!wasLoggedIn && creds.isLoggedIn) {
                        loadFeeds(refresh = true)
                    } else if (wasLoggedIn && !creds.isLoggedIn) {
                        loadJob?.cancel()
                        _uiState.update { state ->
                            state.copy(
                                rawItems = emptyList(),
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

        if (blacklistRepository != null) {
            viewModelScope.launch {
                blacklistRepository.state.collect {
                    _uiState.update { state ->
                        state.copy(items = applyBlacklist(state.rawItems))
                    }
                }
            }
        }

        if (feedUpdateBus != null) {
            viewModelScope.launch {
                feedUpdateBus.updates.collect { updated ->
                    channelCache.entries.forEach { entry ->
                        val cached = entry.value
                        if (cached.rawItems.any { it.id == updated.id }) {
                            entry.setValue(
                                cached.copy(
                                    rawItems = cached.rawItems.map { if (it.id == updated.id) updated else it }
                                )
                            )
                        }
                    }
                    _uiState.update { state ->
                        if (state.rawItems.none { it.id == updated.id }) {
                            state
                        } else {
                            val nextRaw = state.rawItems.map { if (it.id == updated.id) updated else it }
                            state.copy(rawItems = nextRaw, items = applyBlacklist(nextRaw))
                        }
                    }
                }
            }
            viewModelScope.launch {
                feedUpdateBus.deletions.collect { deletedId ->
                    channelCache.entries.forEach { entry ->
                        val cached = entry.value
                        if (cached.rawItems.any { it.id == deletedId }) {
                            entry.setValue(
                                cached.copy(rawItems = cached.rawItems.filterNot { it.id == deletedId })
                            )
                        }
                    }
                    _uiState.update { state ->
                        val nextRaw = state.rawItems.filterNot { it.id == deletedId }
                        state.copy(rawItems = nextRaw, items = applyBlacklist(nextRaw))
                    }
                }
            }
            viewModelScope.launch {
                feedUpdateBus.feedCreated.collect {
                    channelCache.keys.removeAll { it.tab == FeedTab.TIMELINE || it.tab == FeedTab.HEADLINE }
                    if (_uiState.value.currentTab == FeedTab.TIMELINE || _uiState.value.currentTab == FeedTab.HEADLINE) {
                        refresh()
                    }
                }
            }
        }

        // 应用冷启动首次打开时自动刷新默认频道一次，加载后即固定缓存
        loadFeeds(refresh = true)
    }

    private fun applyBlacklist(list: List<FeedItem>): List<FeedItem> {
        return blacklistRepository?.filterFeeds(list) ?: list
    }

    fun isChannelCached(tab: FeedTab, group: FollowGroup = _uiState.value.followGroup): Boolean {
        return channelCache.containsKey(cacheKeyOf(tab, group))
    }

    fun switchTab(tab: FeedTab) {
        if (_uiState.value.currentTab == tab) {
            return
        }
        loadJob?.cancel()
        val targetGroup = _uiState.value.followGroup
        val cached = channelCache[cacheKeyOf(tab, targetGroup)]
        if (cached != null) {
            _uiState.update {
                it.copy(
                    currentTab = tab,
                    rawItems = cached.rawItems,
                    items = applyBlacklist(cached.rawItems),
                    page = cached.page,
                    hasMore = cached.hasMore,
                    isLoading = false,
                    isLoadingMore = false,
                    error = null
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                currentTab = tab,
                rawItems = emptyList(),
                items = emptyList(),
                page = 1,
                hasMore = true,
                isLoading = true,
                isLoadingMore = false,
                error = null
            )
        }
        loadFeeds(refresh = true)
    }

    fun switchFollowGroup(group: FollowGroup) {
        if (_uiState.value.followGroup == group) return
        loadJob?.cancel()
        val tab = _uiState.value.currentTab
        val cached = channelCache[cacheKeyOf(tab, group)]
        if (cached != null) {
            _uiState.update {
                it.copy(
                    followGroup = group,
                    rawItems = cached.rawItems,
                    items = applyBlacklist(cached.rawItems),
                    page = cached.page,
                    hasMore = cached.hasMore,
                    isLoading = false,
                    isLoadingMore = false,
                    error = null
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                followGroup = group,
                rawItems = emptyList(),
                items = emptyList(),
                page = 1,
                hasMore = true,
                isLoading = tab == FeedTab.TIMELINE,
                isLoadingMore = false,
                error = null
            )
        }
        if (tab == FeedTab.TIMELINE) {
            loadFeeds(refresh = true)
        }
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

    fun publishFeed(message: String, isPrivate: Boolean, onComplete: (Boolean, String) -> Unit) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) {
            onComplete(false, "动态内容不能为空")
            return
        }
        if (_uiState.value.isPublishing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPublishing = true) }
            val result = api.createFeed(trimmed, isPrivate = isPrivate)
            _uiState.update { it.copy(isPublishing = false) }
            result.onSuccess { msg ->
                feedUpdateBus?.publishFeedCreated()
                onComplete(true, msg)
            }.onFailure { err ->
                onComplete(false, err.message ?: "发布失败，请检查登录状态")
            }
        }
    }

    private fun loadFeeds(refresh: Boolean) {
        val tab = _uiState.value.currentTab
        val group = _uiState.value.followGroup
        val key = cacheKeyOf(tab, group)
        if (tab == FeedTab.TIMELINE && !_uiState.value.isLoggedIn) {
            loadJob?.cancel()
            _uiState.update {
                it.copy(
                    rawItems = emptyList(),
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
                FeedTab.HEADLINE -> api.getHeadlineFeeds(targetPage)
                FeedTab.RECOMMEND -> api.getRecommendFeeds(targetPage)
                FeedTab.HOT -> api.getHotFeeds(targetPage)
                FeedTab.TIMELINE -> api.getTimelineFeeds(targetPage, group = group.apiValue)
                FeedTab.PICTURE -> api.getPictureFeeds(targetPage)
                FeedTab.DIGITAL -> api.getDigitalFeeds(targetPage)
                FeedTab.SECONDHAND -> api.getSecondHandFeeds(targetPage)
            }

            if (_uiState.value.currentTab != tab || (tab == FeedTab.TIMELINE && _uiState.value.followGroup != group)) {
                return@launch
            }

            result.onSuccess { newItems ->
                _uiState.update { state ->
                    val combinedRaw = if (refresh) {
                        newItems.distinctBy { it.id }
                    } else {
                        (state.rawItems + newItems).distinctBy { it.id }
                    }
                    val addedNew = refresh || combinedRaw.size > state.rawItems.size
                    val nextHasMore = newItems.isNotEmpty() && addedNew
                    channelCache[key] = ChannelSnapshot(
                        rawItems = combinedRaw,
                        page = targetPage,
                        hasMore = nextHasMore
                    )
                    state.copy(
                        rawItems = combinedRaw,
                        items = applyBlacklist(combinedRaw),
                        page = targetPage,
                        hasMore = nextHasMore,
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
