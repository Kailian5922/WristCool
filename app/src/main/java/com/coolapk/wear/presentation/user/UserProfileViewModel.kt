package com.coolapk.wear.presentation.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.model.UserProfile
import com.coolapk.wear.data.repository.FeedUpdateBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserProfileUiState(
    val userProfile: UserProfile? = null,
    val feeds: List<FeedItem> = emptyList(),
    val isLoadingProfile: Boolean = true,
    val isLoadingFeeds: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isTogglingFollow: Boolean = false,
    val error: String? = null,
    val actionMessage: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val isSelf: Boolean = false
)

class UserProfileViewModel(
    private val api: CoolapkApi,
    private val uid: String,
    private val feedUpdateBus: FeedUpdateBus? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        UserProfileUiState(isSelf = uid.isNotBlank() && uid == api.currentUid)
    )
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    private var profileJob: Job? = null
    private var feedsJob: Job? = null

    init {
        if (feedUpdateBus != null) {
            viewModelScope.launch {
                feedUpdateBus.updates.collect { updated ->
                    _uiState.update { state ->
                        if (state.feeds.none { it.id == updated.id }) {
                            state
                        } else {
                            state.copy(
                                feeds = state.feeds.map { if (it.id == updated.id) updated else it }
                            )
                        }
                    }
                }
            }
        }
        loadProfile()
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

        val targetPage = if (refresh) 1 else current.page + 1
        feedsJob = viewModelScope.launch {
            _uiState.update { state ->
                if (refresh) {
                    state.copy(isLoadingFeeds = true, isLoadingMore = false)
                } else {
                    state.copy(isLoadingMore = true)
                }
            }

            val result = api.getUserFeeds(uid, targetPage)
            result.onSuccess { newItems ->
                _uiState.update { state ->
                    val combined = if (refresh) {
                        newItems.distinctBy { it.id }
                    } else {
                        (state.feeds + newItems).distinctBy { it.id }
                    }
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
                _uiState.update { state ->
                    state.copy(
                        isLoadingFeeds = false,
                        isLoadingMore = false
                    )
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

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }
}
