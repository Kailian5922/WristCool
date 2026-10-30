package com.coolapk.wear.presentation.me

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.AuthCredentials
import com.coolapk.wear.data.model.UserProfile
import com.coolapk.wear.data.repository.AuthRepository
import com.coolapk.wear.data.repository.BrowsingHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MeUiState(
    val credentials: AuthCredentials = AuthCredentials(),
    val userProfile: UserProfile? = null,
    val isLoadingProfile: Boolean = false,
    val isImporting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val historyCount: Int = 0
)

class MeViewModel(
    private val authRepository: AuthRepository,
    private val api: CoolapkApi,
    private val browsingHistoryRepository: BrowsingHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MeUiState(
            credentials = authRepository.credentials.value,
            historyCount = browsingHistoryRepository.history.value.size
        )
    )
    val uiState: StateFlow<MeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.credentials.collect { creds ->
                _uiState.value = _uiState.value.copy(credentials = creds)
                if (creds.isLoggedIn && creds.uid.isNotBlank()) {
                    loadProfile(creds.uid)
                } else {
                    _uiState.value = _uiState.value.copy(userProfile = null)
                }
            }
        }

        viewModelScope.launch {
            browsingHistoryRepository.history.collect { list ->
                _uiState.value = _uiState.value.copy(historyCount = list.size)
            }
        }

        val currentCreds = authRepository.credentials.value
        if (currentCreds.isLoggedIn && currentCreds.uid.isNotBlank()) {
            loadProfile(currentCreds.uid)
        }
    }

    fun loadProfile(uid: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingProfile = true)
            val result = api.getUserProfile(uid)
            result.onSuccess { profile ->
                _uiState.value = _uiState.value.copy(
                    userProfile = profile,
                    isLoadingProfile = false
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoadingProfile = false)
            }
        }
    }

    fun importFromClipboard(clipText: String) {
        val trimmed = clipText.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                message = "手表剪贴板为空，请先在手机伴侣端点击【复制登录信息】",
                isError = true
            )
            return
        }

        _uiState.value = _uiState.value.copy(isImporting = true, message = null)
        viewModelScope.launch {
            val result = authRepository.importCompanionPayload(trimmed)
            result.onSuccess { creds ->
                _uiState.value = _uiState.value.copy(
                    credentials = creds,
                    isImporting = false,
                    message = "凭据导入成功！正在校验账号信息…",
                    isError = false
                )
                if (creds.uid.isNotBlank()) {
                    loadProfile(creds.uid)
                }
                val checkResult = api.checkLoginInfo()
                checkResult.onSuccess { (username, _) ->
                    _uiState.value = _uiState.value.copy(
                        message = "已成功登录：$username",
                        isError = false
                    )
                }.onFailure {
                    _uiState.value = _uiState.value.copy(
                        message = "凭据已生效",
                        isError = false
                    )
                }
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    message = err.message ?: "凭据格式无效，请从伴侣端重新复制",
                    isError = true
                )
            }
        }
    }

    fun logout() {
        authRepository.logout()
        _uiState.value = _uiState.value.copy(
            credentials = AuthCredentials(),
            userProfile = null,
            message = "已退出登录"
        )
    }

    fun clearHistory() {
        browsingHistoryRepository.clearHistory()
        _uiState.value = _uiState.value.copy(
            message = "浏览历史已清空",
            isError = false
        )
    }

    fun clearCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Coil.imageLoader(context).diskCache?.clear()
                Coil.imageLoader(context).memoryCache?.clear()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        message = "图片缓存已清理",
                        isError = false
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        message = "清理缓存失败",
                        isError = true
                    )
                }
            }
        }
    }
}
