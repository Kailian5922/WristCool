package com.coolapk.wear.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.AuthCredentials
import com.coolapk.wear.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val credentials: AuthCredentials = AuthCredentials(),
    val isImporting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val api: CoolapkApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(credentials = authRepository.credentials.value))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.credentials.collect { creds ->
                _uiState.value = _uiState.value.copy(credentials = creds)
            }
        }
    }

    fun importFromClipboard(rawText: String) {
        val trimmed = rawText.trim()
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
                // 校验并同步网络个人信息
                val checkResult = api.checkLoginInfo()
                checkResult.onSuccess { (username, _) ->
                    _uiState.value = _uiState.value.copy(
                        message = "已成功登录：$username",
                        isError = false
                    )
                }.onFailure {
                    _uiState.value = _uiState.value.copy(
                        message = "凭据已保存，但验证遇到问题：${it.message}",
                        isError = false
                    )
                }
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    message = "导入失败：${err.message}",
                    isError = true
                )
            }
        }
    }

    fun logout() {
        authRepository.logout()
        _uiState.value = _uiState.value.copy(
            message = "已退出登录，当前处于游客模式",
            isError = false
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
