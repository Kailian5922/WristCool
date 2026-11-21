package com.coolapk.wear.presentation.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.model.MyReplyItem
import com.coolapk.wear.data.model.UserProfile
import com.coolapk.wear.data.repository.BlacklistRepository
import com.coolapk.wear.data.repository.FeedUpdateBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProfileTab(val label: String) {
    FEEDS("动态"),
    REPLIES("回复"),
    LIKES("点赞")
}

data class UserProfileUiState(
    val userProfile: UserProfile? = null,
    val currentTab: ProfileTab = ProfileTab.FEEDS,
    val feeds: List<FeedItem> = emptyList(),
    val replies: List<MyReplyItem> = emptyList(),
    val likedFeeds: List<FeedItem> = emptyList(),
    val isLoadingProfile: Boolean = true,
    val isLoadingFeeds: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isTogglingFollow: Boolean = false,
    val isBlocked: Boolean = false,
    val error: String? = null,
    val actionMessage: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val isSelf: Boolean = false
) {
    val activeItemCount: Int
        get() = when (currentTab) {
            ProfileTab.FEEDS -> feeds.size
            ProfileTab.REPLIES -> replies.size
            ProfileTab.LIKES -> likedFeeds.size
        }
}

class UserProfileViewModel(
    private val api: CoolapkApi,
    private val uid: String,
    private val feedUpdateBus: FeedUpdateBus? = null,
    private val blacklistRepository: BlacklistRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        UserProfileUiState(
            isSelf = uid.isNotBlank() && uid == api.currentUid,
            isBlocked = blacklistRepository?.state?.value?.isUserBlocked(uid) ?: false
        )
    )
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    val currentUid: String
        get() = api.currentUid

    private var profileJob: Job? = null
    private var feedsJob: Job? = null

    init {
        if (blacklistRepository != null) {
            viewModelScope.launch {
                blacklistRepository.state.collect { bl ->
                    _uiState.update { it.copy(isBlocked = bl.isUserBlocked(uid)) }
                }
            }
        }
        if (feedUpdateBus != null) {
            viewModelScope.launch {
                feedUpdateBus.updates.collect { updated ->
                    _uiState.update { state ->
                        state.copy(
                            feeds = state.feeds.map { if (it.id == updated.id) updated else it },
                            likedFeeds = state.likedFeeds.map { if (it.id == updated.id) updated else it }
                        )
                    }
                }
            }
            viewModelScope.launch {
                feedUpdateBus.deletions.collect { deletedId ->
                    _uiState.update { state ->
                        state.copy(
                            feeds = state.feeds.filterNot { it.id == deletedId },
                            likedFeeds = state.likedFeeds.filterNot { it.id == deletedId }
                        )
                    }
                }
            }
        }
        loadProfile()
        loadFeeds(refresh = true)
    }

    fun switchTab(tab: ProfileTab) {
        if (_uiState.value.currentTab == tab) return
        feedsJob?.cancel()
        _uiState.update {
            it.copy(
                currentTab = tab,
                page = 1,
                hasMore = true,
                isLoadingFeeds = false,
                isLoadingMore = false
            )
        }
        loadFeeds(refresh = true)
    }

    fun loadProfile() {
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProfile = true, error = null) }
            val result = api.getUserProfile(uid)
            result.onSuccess { profile ->
                val myUid = api.currentUid
                val isSelf = (uid.isNotBlank() && uid == myUid) ||
                    (profile.uid.isNotBlank() && profile.uid == myUid) ||
                    profile.isSelf
                _uiState.update {
                    it.copy(
                        userProfile = profile,
                        isLoadingProfile = false,
                        isSelf = isSelf,
                        error = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingProfile = false,
                        error = err.message ?: "获取用户信息失败"
                    )
                }
            }
        }
    }

    fun loadFeeds(refresh: Boolean) {
        val current = _uiState.value
        if (!refresh && (current.isLoadingFeeds || current.isLoadingMore || !current.hasMore)) {
            return
        }
        if (refresh) {
            feedsJob?.cancel()
        } else if (feedsJob?.isActive == true) {
            return
        }

        val tab = current.currentTab
        val targetPage = if (refresh) 1 else current.page + 1
        feedsJob = viewModelScope.launch {
            _uiState.update { state ->
                if (refresh) {
                    state.copy(isLoadingFeeds = true, isLoadingMore = false)
                } else {
                    state.copy(isLoadingMore = true)
                }
            }

            when (tab) {
                ProfileTab.FEEDS -> {
                    api.getUserFeeds(uid, targetPage).onSuccess { newItems ->
                        if (_uiState.value.currentTab != tab) return@launch
                        _uiState.update { state ->
                            val combined = if (refresh) newItems.distinctBy { it.id }
                            else (state.feeds + newItems).distinctBy { it.id }
                            val addedNew = refresh || combined.size > state.feeds.size
                            state.copy(
                                feeds = combined,
                                page = targetPage,
                                hasMore = newItems.isNotEmpty() && addedNew,
                                isLoadingFeeds = false,
                                isLoadingMore = false
                            )
                        }
                    }.onFailure {
                        _uiState.update { it.copy(isLoadingFeeds = false, isLoadingMore = false) }
                    }
                }
                ProfileTab.REPLIES -> {
                    api.getUserReplies(uid, targetPage).onSuccess { newItems ->
                        if (_uiState.value.currentTab != tab) return@launch
                        _uiState.update { state ->
                            val combined = if (refresh) newItems.distinctBy { it.id }
                            else (state.replies + newItems).distinctBy { it.id }
                            val addedNew = refresh || combined.size > state.replies.size
                            state.copy(
                                replies = combined,
                                page = targetPage,
                                hasMore = newItems.isNotEmpty() && addedNew,
                                isLoadingFeeds = false,
                                isLoadingMore = false
                            )
                        }
                    }.onFailure {
                        _uiState.update { it.copy(isLoadingFeeds = false, isLoadingMore = false) }
                    }
                }
                ProfileTab.LIKES -> {
                    api.getUserLikes(uid, targetPage).onSuccess { newItems ->
                        if (_uiState.value.currentTab != tab) return@launch
                        _uiState.update { state ->
                            val combined = if (refresh) newItems.distinctBy { it.id }
                            else (state.likedFeeds + newItems).distinctBy { it.id }
                            val addedNew = refresh || combined.size > state.likedFeeds.size
                            state.copy(
                                likedFeeds = combined,
                                page = targetPage,
                                hasMore = newItems.isNotEmpty() && addedNew,
                                isLoadingFeeds = false,
                                isLoadingMore = false
                            )
                        }
                    }.onFailure {
                        _uiState.update { it.copy(isLoadingFeeds = false, isLoadingMore = false) }
                    }
                }
            }
        }
    }

    fun toggleFollow() {
        val snapshot = _uiState.value
        if (snapshot.isTogglingFollow) return
        val currentProfile = snapshot.userProfile ?: return
        val newFollowState = !currentProfile.isFollow
        val newFansNum = if (newFollowState) currentProfile.fansNum + 1 else (currentProfile.fansNum - 1).coerceAtLeast(0)

        _uiState.update {
            it.copy(
                userProfile = currentProfile.copy(isFollow = newFollowState, fansNum = newFansNum),
                isTogglingFollow = true
            )
        }

        viewModelScope.launch {
            val result = api.toggleFollowUser(uid, newFollowState)
            result.onSuccess {
                _uiState.update { it.copy(isTogglingFollow = false) }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        userProfile = currentProfile,
                        isTogglingFollow = false,
                        actionMessage = err.message ?: "关注操作失败，请检查登录状态"
                    )
                }
            }
        }
    }

    fun toggleSpecialFollow() {
        val currentProfile = _uiState.value.userProfile ?: return
        val nextSpecial = !currentProfile.isSpecialFollow
        _uiState.update {
            it.copy(userProfile = currentProfile.copy(isSpecialFollow = nextSpecial))
        }
        viewModelScope.launch {
            api.setSpecialFollowUser(uid, nextSpecial).onSuccess {
                _uiState.update {
                    it.copy(actionMessage = if (nextSpecial) "已设为特别关注" else "已取消特别关注")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        userProfile = currentProfile,
                        actionMessage = err.message ?: "特别关注操作失败"
                    )
                }
            }
        }
    }

    fun toggleBlockUser() {
        val bl = blacklistRepository ?: return
        val profile = _uiState.value.userProfile
        if (_uiState.value.isBlocked) {
            bl.unblockUser(uid)
            _uiState.update { it.copy(actionMessage = "已解除屏蔽") }
        } else {
            bl.blockUser(uid, profile?.username ?: "UID:$uid")
            _uiState.update { it.copy(actionMessage = "已本地屏蔽该用户") }
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }
}
