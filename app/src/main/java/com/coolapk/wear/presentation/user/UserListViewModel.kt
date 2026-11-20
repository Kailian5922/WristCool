package com.coolapk.wear.presentation.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.UserCardItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    private var loadJob: Job? = null

    init {
        loadUsers(refresh = true)
    }

    fun refresh() {
        loadUsers(refresh = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        loadUsers(refresh = false)
    }

    private fun loadUsers(refresh: Boolean) {
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

            val result = if (type == UserListType.FOLLOW) {
                api.getUserFollowList(uid, targetPage)
            } else {
                api.getUserFansList(uid, targetPage)
            }

            result.onSuccess { newUsers ->
                _uiState.update { state ->
                    val combined = if (refresh) {
                        newUsers.distinctBy { it.uid }
                    } else {
                        (state.users + newUsers).distinctBy { it.uid }
                    }
                    val addedNew = refresh || combined.size > state.users.size
                    state.copy(
                        users = combined,
                        page = targetPage,
                        hasMore = newUsers.isNotEmpty() && addedNew,
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
                        error = err.message ?: "加载用户列表失败"
                    )
                }
            }
        }
    }
}
