package com.coolapk.wear.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.ChatMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val partnerName: String = "",
    val partnerUid: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSending: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null,
    val actionMessage: String? = null
)

class ChatViewModel(
    private val api: CoolapkApi,
    private val ukey: String,
    private val partnerUid: String,
    partnerName: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(partnerName = partnerName, partnerUid = partnerUid)
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        markRead()
        loadMessages(refresh = true)
    }

    private fun markRead() {
        if (ukey.isBlank()) return
        viewModelScope.launch {
            api.markMessageRead(ukey)
        }
    }

    fun refresh() {
        loadMessages(refresh = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        loadMessages(refresh = false)
    }

    fun sendMessage(text: String, onComplete: (Boolean, String) -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            onComplete(false, "消息内容不能为空")
            return
        }
        val targetUid = partnerUid.ifBlank {
            val myUid = api.currentUid
            ukey.split("_").firstOrNull { it.isNotBlank() && it != myUid }.orEmpty()
        }
        if (targetUid.isBlank()) {
            onComplete(false, "无法确定私信对象 UID")
            return
        }
        if (_uiState.value.isSending) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val result = api.sendPrivateMessage(targetUid, trimmed)
            _uiState.update { it.copy(isSending = false) }
            result.onSuccess {
                onComplete(true, "已发送")
                loadMessages(refresh = true)
            }.onFailure { err ->
                onComplete(false, err.message ?: "发送私信失败")
            }
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    private fun loadMessages(refresh: Boolean) {
        if (ukey.isBlank()) {
            _uiState.update { it.copy(isLoading = false, hasMore = false) }
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
                if (refresh) state.copy(isLoading = true, isLoadingMore = false, error = null)
                else state.copy(isLoadingMore = true)
            }
            api.getChatMessages(ukey, targetPage).onSuccess { list ->
                _uiState.update { state ->
                    val merged = if (refresh) list.distinctBy { it.id }
                    else (state.messages + list).distinctBy { it.id }
                    val addedNew = refresh || merged.size > state.messages.size
                    state.copy(
                        messages = merged,
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
                        error = err.message ?: "加载聊天记录失败"
                    )
                }
            }
        }
    }

    companion object {
        fun buildUkey(uidA: String, uidB: String): String {
            val a = uidA.trim().toLongOrNull()
            val b = uidB.trim().toLongOrNull()
            return if (a != null && b != null) {
                "${minOf(a, b)}_${maxOf(a, b)}"
            } else {
                listOf(uidA.trim(), uidB.trim()).sorted().joinToString("_")
            }
        }
    }
}
