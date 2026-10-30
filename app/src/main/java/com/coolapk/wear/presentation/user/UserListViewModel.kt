package com.coolapk.wear.presentation.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.UserCardItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class UserListType(val title: String) {
    FOLLOW("关注列表"),
    FANS("粉丝列表")
}

data class UserListUiState(
    val users: List<UserCardItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null
)

class UserListViewModel(
    private val api: CoolapkApi,
    private val uid: String,
    private val type: UserListType
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserListUiState())
    val uiState: StateFlow<UserListUiState> = _uiState.asStateFlow()

    init {
        loadUsers(refresh = true)
    }

    fun refresh() {
        loadUsers(refresh = true)
    }

    fun loadMore() {
        if (_uiState.value.isLoading || _uiState.value.isLoadingMore || !_uiState.value.hasMore) return
        loadUsers(refresh = false)
    }

    private fun loadUsers(refresh: Boolean) {
        val targetPage = if (refresh) 1 else _uiState.value.page + 1
        viewModelScope.launch {
            if (refresh) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            } else {
                _uiState.value = _uiState.value.copy(isLoadingMore = true)
            }

            val result = if (type == UserListType.FOLLOW) {
                api.getUserFollowList(uid, targetPage)
            } else {
                api.getUserFansList(uid, targetPage)
            }

            result.onSuccess { newUsers ->
                val combined = (if (refresh) newUsers else _uiState.value.users + newUsers).distinctBy { it.uid }
                _uiState.value = _uiState.value.copy(
                    users = combined,
                    page = targetPage,
                    hasMore = newUsers.isNotEmpty(),
                    isLoading = false,
                    isLoadingMore = false,
                    error = null
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = err.message ?: "加载用户列表失败"
                )
            }
        }
    }
}
