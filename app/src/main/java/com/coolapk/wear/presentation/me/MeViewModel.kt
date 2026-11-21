package com.coolapk.wear.presentation.me

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.AuthCredentials
import com.coolapk.wear.data.model.UserProfile
import com.coolapk.wear.data.repository.AuthRepository
import com.coolapk.wear.data.repository.BrowsingHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
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

    private var profileJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.credentials.collect { creds ->
                _uiState.update { it.copy(credentials = creds) }
            }
        }

        // 仅当登录态 / UID / Cookie 真正变化时拉取个人资料，消除重复请求与循环触发
        viewModelScope.launch {
            authRepository.credentials
                .distinctUntilChangedBy { Triple(it.isLoggedIn, it.uid, it.cookie) }
                .collect { creds ->
                    if (creds.isLoggedIn && creds.uid.isNotBlank()) {
                        loadProfile(creds.uid)
                    } else {
                        profileJob?.cancel()
                        _uiState.update { it.copy(userProfile = null, isLoadingProfile = false) }
                    }
                }
        }

        viewModelScope.launch {
            browsingHistoryRepository.history.collect { list ->
                _uiState.update { it.copy(historyCount = list.size) }
            }
        }
    }

    fun loadProfile(uid: String) {
        if (uid.isBlank()) return
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProfile = true) }
            val result = api.getUserProfile(uid)
            result.onSuccess { profile ->
                authRepository.updateUserInfo(
                    username = profile.username,
                    avatar = profile.userAvatar,
                    uid = profile.uid
                )
                _uiState.update {
                    it.copy(
                        userProfile = profile,
                        isLoadingProfile = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingProfile = false) }
            }
        }
    }

    fun importFromClipboard(clipText: String) {
        val trimmed = clipText.trim()
        if (trimmed.isEmpty()) {
            _uiState.update {
                it.copy(
                    message = "手表剪贴板为空，请先在手机伴侣端点击【复制登录信息】",
                    isError = true
                )
            }
            return
        }

        if (_uiState.value.isImporting) return
        _uiState.update { it.copy(isImporting = true, message = null, isError = false) }
        viewModelScope.launch {
            val result = authRepository.importCompanionPayload(trimmed)
            result.onSuccess { creds ->
                val hasDeviceId = creds.deviceId.isNotBlank()
                _uiState.update {
                    it.copy(
                        credentials = creds,
                        isImporting = false,
                        message = if (hasDeviceId) "凭据与数盟设备 ID 导入成功，正在校验…" else "凭据导入成功，正在校验账号…",
                        isError = false
                    )
                }
                val checkResult = api.checkLoginInfo()
                checkResult.onSuccess { info ->
                    if (info.uid.isNotBlank() && _uiState.value.userProfile?.uid != info.uid) {
                        loadProfile(info.uid)
                    }
                    _uiState.update {
                        it.copy(
                            message = if (hasDeviceId) "已登录：${info.username}（含数盟设备 ID）" else "已成功登录：${info.username}",
                            isError = false
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            message = "凭据已保存（校验提示：${err.message ?: "请检查网络"}）",
                            isError = false
                        )
                    }
                }
            }.onFailure { err ->
                // 若剪贴板里是纯官方数盟设备日志或 DU... ID，直接更新设备 ID 而不影响已有登录态
                val deviceResult = authRepository.importDeviceIdOnly(trimmed)
                deviceResult.onSuccess { savedId ->
                    val shortId = if (savedId.length > 10) "${savedId.take(6)}…${savedId.takeLast(4)}" else savedId
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            message = "数盟设备 ID 已生效：$shortId",
                            isError = false
                        )
                    }
                }.onFailure {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            message = err.message ?: "凭据格式无效，请从伴侣端重新复制",
                            isError = true
                        )
                    }
                }
            }
        }
    }

    fun logout() {
        profileJob?.cancel()
        authRepository.logout()
        _uiState.update {
            it.copy(
                credentials = AuthCredentials(),
                userProfile = null,
                isLoadingProfile = false,
                message = "已退出登录",
                isError = false
            )
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCache(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val cleared = runCatching {
                val loader = Coil.imageLoader(appContext)
                loader.diskCache?.clear()
                loader.memoryCache?.clear()
            }.isSuccess
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        message = if (cleared) "图片缓存已清理" else "清理缓存失败",
                        isError = !cleared
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
