package com.coolapk.wear.presentation.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserProfileUiState(
    val userProfile: UserProfile? = null,
    val feeds: List<FeedItem> = emptyList(),
    val isLoadingProfile: Boolean = true,
    val isLoadingFeeds: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val isSelf: Boolean = false
)

class UserProfileViewModel(
    private val api: CoolapkApi,
    private val uid: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        UserProfileUiState(isSelf = uid.isNotBlank() && uid == api.currentUid)
    )
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
        loadFeeds(refresh = true)
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingProfile = true, error = null)
            val result = api.getUserProfile(uid)
            result.onSuccess { profile ->
                val myUid = api.currentUid
                val isSelf = (uid.isNotBlank() && uid == myUid) || (profile.uid.isNotBlank() && profile.uid == myUid) || profile.isSelf
                _uiState.value = _uiState.value.copy(
                    userProfile = profile,
                    isLoadingProfile = false,
                    isSelf = isSelf
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isLoadingProfile = false,
                    error = err.message ?: "获取用户信息失败"
                )
            }
        }
    }

    fun loadFeeds(refresh: Boolean) {
        val targetPage = if (refresh) 1 else _uiState.value.page + 1
        viewModelScope.launch {
            if (refresh) {
                _uiState.value = _uiState.value.copy(isLoadingFeeds = true)
            } else {
                _uiState.value = _uiState.value.copy(isLoadingMore = true)
            }

            val result = api.getUserFeeds(uid, targetPage)
            result.onSuccess { newItems ->
                val combined = (if (refresh) newItems else _uiState.value.feeds + newItems).distinctBy { it.id }
                _uiState.value = _uiState.value.copy(
                    feeds = combined,
                    page = targetPage,
                    hasMore = newItems.isNotEmpty(),
                    isLoadingFeeds = false,
                    isLoadingMore = false
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoadingFeeds = false,
                    isLoadingMore = false
                )
            }
        }
    }

    fun toggleFollow() {
        val currentProfile = _uiState.value.userProfile ?: return
        val newFollowState = !currentProfile.isFollow
        val newFansNum = if (newFollowState) currentProfile.fansNum + 1 else (currentProfile.fansNum - 1).coerceAtLeast(0)

        _uiState.value = _uiState.value.copy(
            userProfile = currentProfile.copy(isFollow = newFollowState, fansNum = newFansNum)
        )

        viewModelScope.launch {
            val result = api.toggleFollowUser(uid, newFollowState)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(userProfile = currentProfile)
            }
        }
    }
}
