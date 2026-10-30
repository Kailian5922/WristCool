package com.coolapk.wear.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.CommentItem
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.repository.BrowsingHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CommentSort(val key: String, val label: String) {
    DEFAULT("", "默认"),
    POPULAR("popular", "热门"),
    LATEST("dateline_desc", "最新"),
    AUTHOR("author", "楼主")
}

data class FeedDetailUiState(
    val feed: FeedItem? = null,
    val comments: List<CommentItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLiked: Boolean = false,
    val likeCount: Int = 0,
    val isFavorite: Boolean = false,
    val isSubmittingReply: Boolean = false,
    val commentSort: CommentSort = CommentSort.DEFAULT,
    val commentPage: Int = 1,
    val isLoadingMoreComments: Boolean = false,
    val hasMoreComments: Boolean = true
)

class FeedDetailViewModel(
    private val api: CoolapkApi,
    private val feedId: String,
    initialFeed: FeedItem? = null,
    private val browsingHistoryRepository: BrowsingHistoryRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FeedDetailUiState(
            feed = initialFeed,
            isLiked = initialFeed?.isLiked ?: false,
            likeCount = initialFeed?.likeNum ?: 0,
            isFavorite = initialFeed?.isFavorite ?: false,
            isLoading = initialFeed == null
        )
    )
    val uiState: StateFlow<FeedDetailUiState> = _uiState.asStateFlow()

    init {
        if (initialFeed != null) {
            browsingHistoryRepository?.recordView(initialFeed)
        }
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            if (_uiState.value.feed == null) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }

            val detailResult = api.getFeedDetail(feedId)
            val commentsResult = api.getFeedReplies(feedId, 1, _uiState.value.commentSort.key)
            val comments = commentsResult.getOrDefault(emptyList())

            detailResult.onSuccess { item ->
                browsingHistoryRepository?.recordView(item)
                _uiState.value = _uiState.value.copy(
                    feed = item,
                    comments = comments,
                    commentPage = 1,
                    hasMoreComments = comments.size >= 10,
                    isLiked = item.isLiked,
                    likeCount = item.likeNum,
                    isFavorite = item.isFavorite,
                    isLoading = false,
                    error = null
                )
            }.onFailure { err ->
                if (_uiState.value.feed != null) {
                    _uiState.value = _uiState.value.copy(
                        comments = comments,
                        commentPage = 1,
                        hasMoreComments = comments.size >= 10,
                        isLoading = false,
                        error = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = err.message ?: "加载详情失败"
                    )
                }
            }
        }
    }

    fun setCommentSort(sort: CommentSort) {
        if (_uiState.value.commentSort == sort) return
        _uiState.value = _uiState.value.copy(
            commentSort = sort,
            commentPage = 1,
            hasMoreComments = true
        )
        fetchComments(page = 1, sort = sort, replace = true)
    }

    fun loadMoreComments() {
        val current = _uiState.value
        if (current.isLoadingMoreComments || !current.hasMoreComments) return
        val nextPage = current.commentPage + 1
        fetchComments(page = nextPage, sort = current.commentSort, replace = false)
    }

    private fun fetchComments(page: Int, sort: CommentSort, replace: Boolean) {
        viewModelScope.launch {
            if (!replace) {
                _uiState.value = _uiState.value.copy(isLoadingMoreComments = true)
            }
            val result = api.getFeedReplies(feedId, page, sort.key)
            result.onSuccess { newComments ->
                val combined = if (replace) newComments else _uiState.value.comments + newComments
                _uiState.value = _uiState.value.copy(
                    comments = combined,
                    commentPage = page,
                    isLoadingMoreComments = false,
                    hasMoreComments = newComments.isNotEmpty() && newComments.size >= 10
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoadingMoreComments = false)
            }
        }
    }

    fun toggleLike() {
        val current = _uiState.value
        val newLiked = !current.isLiked
        val newCount = if (newLiked) current.likeCount + 1 else (current.likeCount - 1).coerceAtLeast(0)

        _uiState.value = current.copy(isLiked = newLiked, likeCount = newCount)

        viewModelScope.launch {
            val result = api.toggleLike(feedId)
            if (result.isFailure) {
                _uiState.value = current
            }
        }
    }

    fun toggleFavorite() {
        val current = _uiState.value
        val newFav = !current.isFavorite
        _uiState.value = current.copy(isFavorite = newFav)

        viewModelScope.launch {
            val result = if (newFav) {
                api.toggleFavorite(feedId)
            } else {
                api.unFavoriteFeed(feedId)
            }
            if (result.isFailure) {
                _uiState.value = current
            }
        }
    }

    fun submitReply(message: String, onComplete: (Boolean, String) -> Unit) {
        if (message.isBlank()) {
            onComplete(false, "回复内容不能为空")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmittingReply = true)
            val result = api.replyFeed(feedId, message.trim())
            _uiState.value = _uiState.value.copy(isSubmittingReply = false)

            result.onSuccess { msg ->
                onComplete(true, msg)
                // 刷新评论列表第一页
                fetchComments(page = 1, sort = _uiState.value.commentSort, replace = true)
            }.onFailure { err ->
                onComplete(false, err.message ?: "回复失败，请检查网络或登录状态")
            }
        }
    }

    suspend fun getCommentSubReplies(commentId: String, page: Int = 1): List<com.coolapk.wear.data.model.SubReplyItem> {
        val result = api.getCommentSubReplies(commentId, page)
        return result.getOrDefault(emptyList())
    }
}
