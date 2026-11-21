package com.coolapk.wear.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.MessageSession
import com.coolapk.wear.data.model.NotificationCount
import com.coolapk.wear.data.model.NotificationItem
import com.coolapk.wear.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InboxTab(val apiPath: String, val clearKey: String, val label: String) {
    MESSAGES("message", "message", "私信"),
    COMMENT("list", "notification", "评论"),
    AT_ME("atMeList", "atme", "@我"),
    AT_COMMENT("atCommentMeList", "atcommentme", "评论@"),
    LIKES("feedLikeList", "feedlike", "赞我"),
    FOLLOW("contactsFollowList", "contacts_follow", "新粉丝")
}

data class InboxUiState(
    val isLoggedIn: Boolean = false,
    val currentTab: InboxTab = InboxTab.MESSAGES,
    val counts: NotificationCount = NotificationCount(),
    val notifications: List<NotificationItem> = emptyList(),
    val sessions: List<MessageSession> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null,
    val actionMessage: String? = null
)

class InboxViewModel(
    private val api: CoolapkApi,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        InboxUiState(isLoggedIn = authRepository.credentials.value.isLoggedIn)
    )
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.credentials.collect { creds ->
                val wasLoggedIn = _uiState.value.isLoggedIn
                _uiState.update { it.copy(isLoggedIn = creds.isLoggedIn) }
                if (creds.isLoggedIn && !wasLoggedIn) {
                    refreshCounts()
                    loadCurrentTab(refresh = true)
                } else if (!creds.isLoggedIn) {
                    loadJob?.cancel()
                    _uiState.update {
                        it.copy(
                            counts = NotificationCount(),
                            notifications = emptyList(),
                            sessions = emptyList(),
                            isLoading = false,
                            isLoadingMore = false,
                            error = null
                        )
                    }
                }
            }
        }
        if (authRepository.credentials.value.isLoggedIn) {
            refreshCounts()
            loadCurrentTab(refresh = true)
        }
    }

    fun refreshCounts() {
        if (!_uiState.value.isLoggedIn) return
        viewModelScope.launch {
            api.getNotificationCount().onSuccess { cnt ->
                _uiState.update { it.copy(counts = cnt) }
            }
        }
    }

    fun switchTab(tab: InboxTab) {
        if (_uiState.value.currentTab == tab) {
            refresh()
            return
        }
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                currentTab = tab,
                page = 1,
                hasMore = true,
                error = null
            )
        }
        loadCurrentTab(refresh = true)
    }

    fun refresh() {
        refreshCounts()
        loadCurrentTab(refresh = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.isLoggedIn || state.isLoading || state.isLoadingMore || !state.hasMore) return
        loadCurrentTab(refresh = false)
    }

    fun clearCurrentTabUnread() {
        val tab = _uiState.value.currentTab
        if (!_uiState.value.isLoggedIn || tab == InboxTab.MESSAGES) return
        viewModelScope.launch {
            api.clearNotificationCount(tab.clearKey).onSuccess {
                refreshCounts()
                _uiState.update { it.copy(actionMessage = "已清除 ${tab.label} 未读提醒") }
            }.onFailure { err ->
                _uiState.update { it.copy(actionMessage = err.message ?: "清除未读失败") }
            }
        }
    }

    fun deleteNotification(item: NotificationItem) {
        viewModelScope.launch {
            api.deleteNotification(item.id).onSuccess {
                _uiState.update { state ->
                    state.copy(
                        notifications = state.notifications.filterNot { it.id == item.id },
                        actionMessage = "已删除通知"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(actionMessage = err.message ?: "删除失败") }
            }
        }
    }

    fun deleteSession(session: MessageSession) {
        viewModelScope.launch {
            api.deleteMessageChat(session.ukey).onSuccess {
                _uiState.update { state ->
                    state.copy(
                        sessions = state.sessions.filterNot { it.ukey == session.ukey },
                        actionMessage = "已删除私信会话"
                    )
                }
                refreshCounts()
            }.onFailure { err ->
                _uiState.update { it.copy(actionMessage = err.message ?: "删除会话失败") }
            }
        }
    }

    fun resolveNotificationFeed(
        item: NotificationItem,
        onResolved: (String?) -> Unit
    ) {
        if (item.feedId.isNotBlank()) {
            onResolved(item.feedId)
            return
        }
        if (item.replyId.isBlank()) {
            onResolved(null)
            return
        }
        viewModelScope.launch {
            api.resolveFeedIdFromReply(item.replyId).onSuccess { feedId ->
                onResolved(feedId)
            }.onFailure { err ->
                _uiState.update { it.copy(actionMessage = err.message ?: "无法打开原动态") }
                onResolved(null)
            }
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    private fun loadCurrentTab(refresh: Boolean) {
        if (!_uiState.value.isLoggedIn) return
        val tab = _uiState.value.currentTab
        if (refresh) {
            loadJob?.cancel()
        } else if (loadJob?.isActive == true) {
            return
        }
        val targetPage = if (refresh) 1 else _uiState.value.page + 1

        loadJob = viewModelScope.launch {
            _uiState.update { state ->
                if (refresh) state.copy(isLoading = true, isLoadingMore = false, error = null)
                else state.copy(isLoadingMore = true)
            }

            if (tab == InboxTab.MESSAGES) {
                api.getMessageSessions(targetPage).onSuccess { list ->
                    if (_uiState.value.currentTab != tab) return@launch
                    _uiState.update { state ->
                        val merged = if (refresh) list.distinctBy { it.ukey }
                        else (state.sessions + list).distinctBy { it.ukey }
                        val addedNew = refresh || merged.size > state.sessions.size
                        state.copy(
                            sessions = merged,
                            page = targetPage,
                            hasMore = list.isNotEmpty() && addedNew,
                            isLoading = false,
                            isLoadingMore = false,
                            error = null
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = err.message ?: "加载私信列表失败"
                        )
                    }
                }
            } else {
                api.getNotifications(tab.apiPath, targetPage).onSuccess { list ->
                    if (_uiState.value.currentTab != tab) return@launch
                    _uiState.update { state ->
                        val merged = if (refresh) list.distinctBy { it.id }
                        else (state.notifications + list).distinctBy { it.id }
                        val addedNew = refresh || merged.size > state.notifications.size
                        state.copy(
                            notifications = merged,
                            page = targetPage,
                            hasMore = list.isNotEmpty() && addedNew,
                            isLoading = false,
                            isLoadingMore = false,
                            error = null
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = err.message ?: "加载通知失败"
                        )
                    }
                }
            }
        }
    }
}
