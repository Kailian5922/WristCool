package com.coolapk.wear.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapk.wear.data.api.CaptchaChallenge
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.api.CoolapkCaptchaRequiredException
import com.coolapk.wear.data.model.CommentItem
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.repository.BlacklistRepository
import com.coolapk.wear.data.repository.BrowsingHistoryRepository
import com.coolapk.wear.data.repository.FeedUpdateBus
import com.coolapk.wear.data.repository.SettingsRepository
import com.coolapk.wear.presentation.components.SubReplyUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CommentSort(val key: String, val label: String) {
    DEFAULT("", "默认"),
    POPULAR("popular", "热门"),
    LATEST("dateline_desc", "最新"),
    AUTHOR("author", "楼主");

    companion object {
        fun fromKey(key: String?): CommentSort =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

data class FeedDetailUiState(
    val feed: FeedItem? = null,
    val rawComments: List<CommentItem> = emptyList(),
    val comments: List<CommentItem> = emptyList(),
    val subReplyStates: Map<String, SubReplyUiState> = emptyMap(),
    val isLoading: Boolean = false,
    val isLoadingComments: Boolean = false,
    val error: String? = null,
    val commentsError: String? = null,
    val detailCaptcha: CaptchaChallenge? = null,
    val commentsCaptcha: CaptchaChallenge? = null,
    val isLiked: Boolean = false,
    val likeCount: Int = 0,
    val isFavorite: Boolean = false,
    val isTogglingLike: Boolean = false,
    val isTogglingFavorite: Boolean = false,
    val isSubmittingReply: Boolean = false,
    val isForwarding: Boolean = false,
    val isDeletingFeed: Boolean = false,
    val isVoting: Boolean = false,
    val commentSort: CommentSort = CommentSort.DEFAULT,
    val commentPage: Int = 1,
    val isLoadingMoreComments: Boolean = false,
    val hasMoreComments: Boolean = true,
    val actionMessage: String? = null,
    val currentUid: String = ""
)

class FeedDetailViewModel(
    private val api: CoolapkApi,
    val feedId: String,
    initialFeed: FeedItem? = null,
    private val browsingHistoryRepository: BrowsingHistoryRepository? = null,
    private val feedUpdateBus: FeedUpdateBus? = null,
    private val blacklistRepository: BlacklistRepository? = null,
    private val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    private val defaultSort = CommentSort.fromKey(settingsRepository?.settings?.value?.defaultCommentSort)

    private val _uiState = MutableStateFlow(
        FeedDetailUiState(
            feed = initialFeed,
            isLiked = initialFeed?.isLiked ?: false,
            likeCount = initialFeed?.likeNum ?: 0,
            isFavorite = initialFeed?.isFavorite ?: false,
            isLoading = initialFeed == null,
            commentSort = defaultSort,
            currentUid = api.currentUid
        )
    )
    val uiState: StateFlow<FeedDetailUiState> = _uiState.asStateFlow()

    private var detailJob: Job? = null
    private var commentsJob: Job? = null
    private val subReplyJobs = mutableMapOf<String, Job>()

    init {
        if (initialFeed != null && shouldRecordHistory()) {
            browsingHistoryRepository?.recordView(initialFeed)
        }
        if (blacklistRepository != null) {
            viewModelScope.launch {
                blacklistRepository.state.collect {
                    _uiState.update { state ->
                        state.copy(comments = applyCommentBlacklist(state.rawComments))
                    }
                }
            }
        }
        loadDetail()
    }

    private fun shouldRecordHistory(): Boolean =
        settingsRepository?.settings?.value?.recordHistory ?: true

    private fun applyCommentBlacklist(list: List<CommentItem>): List<CommentItem> =
        blacklistRepository?.filterComments(list) ?: list

    fun loadDetail() {
        detailJob?.cancel()
        commentsJob?.cancel()
        detailJob = viewModelScope.launch {
            val hasCachedFeed = _uiState.value.feed != null
            _uiState.update { state ->
                state.copy(
                    isLoading = !hasCachedFeed,
                    isLoadingComments = state.comments.isEmpty(),
                    error = null,
                    commentsError = null,
                    detailCaptcha = null,
                    commentsCaptcha = null,
                    currentUid = api.currentUid
                )
            }

            val sortKey = _uiState.value.commentSort.key

            if (hasCachedFeed) {
                // 已有列表缓存正文时，优先顺序拉取评论区，避免与 /v6/feed/detail 同毫秒并发抢占连接或加重风控
                val commentsResult = api.getFeedReplies(
                    feedId = feedId,
                    page = 1,
                    sortType = sortKey
                )
                val commentFailure = commentsResult.exceptionOrNull()
                val commentsCaptcha = (commentFailure as? CoolapkCaptchaRequiredException)?.challenge
                val commentsError = commentFailure?.let { it.message?.ifBlank { "加载评论失败" } ?: "加载评论失败" }
                val rawComments = commentsResult.getOrDefault(emptyList()).distinctBy { it.id }

                _uiState.update { state ->
                    state.copy(
                        rawComments = if (commentFailure == null) rawComments else state.rawComments,
                        comments = if (commentFailure == null) applyCommentBlacklist(rawComments) else state.comments,
                        commentPage = 1,
                        hasMoreComments = commentFailure == null && rawComments.size >= COMMENT_PAGE_MIN_FOR_MORE,
                        isLoading = false,
                        isLoadingComments = false,
                        commentsError = commentsError,
                        commentsCaptcha = commentsCaptcha
                    )
                }

                // 若评论区已被酷安要求人机验证，不再紧接着补发 /v6/feed/detail 制造多余拦截请求
                if (commentsCaptcha == null) {
                    api.getFeedDetail(feedId).onSuccess { item ->
                        if (shouldRecordHistory()) {
                            browsingHistoryRepository?.recordView(item)
                        }
                        feedUpdateBus?.publish(item)
                        _uiState.update { state ->
                            state.copy(
                                feed = item,
                                isLiked = item.isLiked,
                                likeCount = item.likeNum,
                                isFavorite = item.isFavorite,
                                error = null,
                                detailCaptcha = null
                            )
                        }
                    }.onFailure { err ->
                        val detailCaptcha = (err as? CoolapkCaptchaRequiredException)?.challenge
                        _uiState.update { state ->
                            state.copy(detailCaptcha = detailCaptcha)
                        }
                    }
                }
            } else {
                // 无缓存正文时（如消息通知直达），先拉取详情；详情失败时直接停手，不再空耗评论接口请求
                val detailResult = api.getFeedDetail(feedId)
                detailResult.onFailure { err ->
                    val detailCaptcha = (err as? CoolapkCaptchaRequiredException)?.challenge
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isLoadingComments = false,
                            error = err.message ?: "加载详情失败",
                            detailCaptcha = detailCaptcha
                        )
                    }
                }.onSuccess { item ->
                    if (shouldRecordHistory()) {
                        browsingHistoryRepository?.recordView(item)
                    }
                    feedUpdateBus?.publish(item)
                    _uiState.update { state ->
                        state.copy(
                            feed = item,
                            isLiked = item.isLiked,
                            likeCount = item.likeNum,
                            isFavorite = item.isFavorite,
                            isLoading = false,
                            isLoadingComments = true,
                            error = null,
                            detailCaptcha = null
                        )
                    }

                    val commentsResult = api.getFeedReplies(
                        feedId = feedId,
                        page = 1,
                        sortType = sortKey
                    )
                    val commentFailure = commentsResult.exceptionOrNull()
                    val commentsCaptcha = (commentFailure as? CoolapkCaptchaRequiredException)?.challenge
                    val commentsError = commentFailure?.let { it.message?.ifBlank { "加载评论失败" } ?: "加载评论失败" }
                    val rawComments = commentsResult.getOrDefault(emptyList()).distinctBy { it.id }

                    _uiState.update { state ->
                        state.copy(
                            rawComments = if (commentFailure == null) rawComments else state.rawComments,
                            comments = if (commentFailure == null) applyCommentBlacklist(rawComments) else state.comments,
                            commentPage = 1,
                            hasMoreComments = commentFailure == null && rawComments.size >= COMMENT_PAGE_MIN_FOR_MORE,
                            isLoadingComments = false,
                            commentsError = commentsError,
                            commentsCaptcha = commentsCaptcha
                        )
                    }
                }
            }
        }
    }

    fun retryComments() {
        fetchComments(
            page = 1,
            sort = _uiState.value.commentSort,
            replace = true
        )
    }

    fun setCommentSort(sort: CommentSort) {
        if (_uiState.value.commentSort == sort) return
        commentsJob?.cancel()
        _uiState.update {
            it.copy(
                commentSort = sort,
                rawComments = emptyList(),
                comments = emptyList(),
                subReplyStates = emptyMap(),
                commentPage = 1,
                hasMoreComments = true,
                isLoadingComments = true,
                isLoadingMoreComments = false,
                commentsError = null,
                commentsCaptcha = null
            )
        }
        fetchComments(page = 1, sort = sort, replace = true)
    }

    fun loadMoreComments() {
        val current = _uiState.value
        if (current.isLoading || current.isLoadingComments || current.isLoadingMoreComments || !current.hasMoreComments) {
            return
        }
        fetchComments(page = current.commentPage + 1, sort = current.commentSort, replace = false)
    }

    private fun fetchComments(
        page: Int,
        sort: CommentSort,
        replace: Boolean
    ) {
        if (replace) {
            commentsJob?.cancel()
        }
        val existingRaw = _uiState.value.rawComments
        val firstItem = if (!replace && page > 1) existingRaw.firstOrNull()?.id.orEmpty() else ""
        val lastItem = if (!replace && page > 1) existingRaw.lastOrNull()?.id.orEmpty() else ""
        commentsJob = viewModelScope.launch {
            if (replace) {
                _uiState.update {
                    it.copy(
                        isLoadingComments = true,
                        isLoadingMoreComments = false,
                        commentsError = null,
                        commentsCaptcha = null
                    )
                }
            } else {
                _uiState.update { it.copy(isLoadingMoreComments = true) }
            }

            val result = api.getFeedReplies(
                feedId = feedId,
                page = page,
                sortType = sort.key,
                firstItem = firstItem,
                lastItem = lastItem
            )
            if (_uiState.value.commentSort != sort) return@launch

            result.onSuccess { newComments ->
                _uiState.update { state ->
                    val combinedRaw = if (replace) {
                        newComments.distinctBy { it.id }
                    } else {
                        (state.rawComments + newComments).distinctBy { it.id }
                    }
                    val addedNew = replace || combinedRaw.size > state.rawComments.size
                    state.copy(
                        rawComments = combinedRaw,
                        comments = applyCommentBlacklist(combinedRaw),
                        commentPage = page,
                        isLoadingComments = false,
                        isLoadingMoreComments = false,
                        commentsError = null,
                        commentsCaptcha = null,
                        hasMoreComments = addedNew && newComments.size >= COMMENT_PAGE_MIN_FOR_MORE
                    )
                }
            }.onFailure { err ->
                val captcha = (err as? CoolapkCaptchaRequiredException)?.challenge
                val errMsg = err.message?.ifBlank { "加载评论失败" } ?: "加载评论失败"
                _uiState.update { state ->
                    state.copy(
                        isLoadingComments = false,
                        isLoadingMoreComments = false,
                        commentsError = if (replace || state.comments.isEmpty()) errMsg else state.commentsError,
                        commentsCaptcha = captcha ?: state.commentsCaptcha,
                        actionMessage = if (!replace && state.comments.isNotEmpty()) errMsg else state.actionMessage
                    )
                }
            }
        }
    }

    fun toggleSubReplies(comment: CommentItem) {
        val current = _uiState.value.subReplyStates[comment.id] ?: SubReplyUiState(replies = comment.subReplies)
        if (current.isExpanded) {
            updateSubReplyState(comment.id) { it.copy(isExpanded = false) }
            return
        }

        val initialReplies = current.replies.ifEmpty { comment.subReplies }
        val needsFirstFetch = current.page == 0 && (comment.replyNum > initialReplies.size || initialReplies.size <= 4)
        updateSubReplyState(comment.id) {
            it.copy(
                isExpanded = true,
                replies = initialReplies,
                error = null
            )
        }
        if (needsFirstFetch) {
            fetchSubRepliesPage(comment, page = 1)
        }
    }

    fun loadMoreSubReplies(comment: CommentItem) {
        val current = _uiState.value.subReplyStates[comment.id] ?: SubReplyUiState(replies = comment.subReplies)
        if (current.isLoading || !current.hasMore) return
        val nextPage = (current.page + 1).coerceAtLeast(1)
        fetchSubRepliesPage(comment, page = nextPage)
    }

    fun retrySubReplies(comment: CommentItem) {
        val current = _uiState.value.subReplyStates[comment.id] ?: SubReplyUiState(replies = comment.subReplies)
        if (current.isLoading) return
        val targetPage = if (current.page <= 0) 1 else current.page + 1
        fetchSubRepliesPage(comment, page = targetPage)
    }

    private fun fetchSubRepliesPage(comment: CommentItem, page: Int) {
        if (subReplyJobs[comment.id]?.isActive == true) return
        val currentReplies = _uiState.value.subReplyStates[comment.id]?.replies.orEmpty()
        val lastItem = if (page > 1) currentReplies.lastOrNull()?.id.orEmpty() else ""
        subReplyJobs[comment.id] = viewModelScope.launch {
            updateSubReplyState(comment.id) {
                it.copy(
                    isExpanded = true,
                    replies = it.replies.ifEmpty { comment.subReplies },
                    isLoading = true,
                    error = null
                )
            }

            val result = api.getCommentSubReplies(comment.id, page, lastItem = lastItem)
            result.onSuccess { fetched ->
                updateSubReplyState(comment.id) { state ->
                    val base = state.replies.ifEmpty { comment.subReplies }
                    val merged = (base + fetched).distinctBy { it.id }
                    val addedNew = merged.size > base.size || (page == 1 && fetched.isNotEmpty())
                    val totalKnown = maxOf(comment.replyNum, merged.size)
                    val moreLikely = fetched.size >= SUB_REPLY_PAGE_MIN_FOR_MORE ||
                        (addedNew && fetched.isNotEmpty() && merged.size < totalKnown)
                    state.copy(
                        replies = merged,
                        page = page,
                        hasMore = moreLikely,
                        isLoading = false,
                        error = null
                    )
                }
            }.onFailure { err ->
                updateSubReplyState(comment.id) { state ->
                    state.copy(
                        isLoading = false,
                        error = err.message ?: "加载楼中楼失败"
                    )
                }
            }
        }
    }

    private fun updateSubReplyState(
        commentId: String,
        transform: (SubReplyUiState) -> SubReplyUiState
    ) {
        _uiState.update { state ->
            val current = state.subReplyStates[commentId] ?: SubReplyUiState()
            state.copy(
                subReplyStates = state.subReplyStates + (commentId to transform(current))
            )
        }
    }

    fun toggleLike() {
        val snapshot = _uiState.value
        if (snapshot.isTogglingLike) return

        val prevLiked = snapshot.isLiked
        val prevCount = snapshot.likeCount
        val newLiked = !prevLiked
        val newCount = if (newLiked) prevCount + 1 else (prevCount - 1).coerceAtLeast(0)

        _uiState.update {
            it.copy(
                isLiked = newLiked,
                likeCount = newCount,
                isTogglingLike = true
            )
        }

        viewModelScope.launch {
            val result = api.toggleLike(feedId, like = newLiked)
            result.onSuccess {
                _uiState.update { state ->
                    val updatedFeed = state.feed?.copy(isLiked = newLiked, likeNum = newCount)
                    updatedFeed?.let { publishFeedUpdate(it) }
                    state.copy(feed = updatedFeed, isTogglingLike = false)
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        isLiked = prevLiked,
                        likeCount = prevCount,
                        isTogglingLike = false,
                        actionMessage = err.message ?: "点赞操作失败，请检查登录状态"
                    )
                }
            }
        }
    }

    /**
     * 点赞或取消点赞单条评论（乐观更新 + 失败回滚）。
     */
    fun toggleCommentLike(comment: CommentItem) {
        val newLiked = !comment.isLiked
        val newCount = if (newLiked) comment.likeNum + 1 else (comment.likeNum - 1).coerceAtLeast(0)

        fun updateCommentInList(targetId: String, liked: Boolean, count: Int) {
            _uiState.update { state ->
                val nextRaw = state.rawComments.map { c ->
                    if (c.id == targetId) c.copy(isLiked = liked, likeNum = count) else c
                }
                state.copy(rawComments = nextRaw, comments = applyCommentBlacklist(nextRaw))
            }
        }

        updateCommentInList(comment.id, newLiked, newCount)
        viewModelScope.launch {
            api.toggleReplyLike(comment.id, like = newLiked).onFailure { err ->
                updateCommentInList(comment.id, comment.isLiked, comment.likeNum)
                _uiState.update { it.copy(actionMessage = err.message ?: "评论点赞失败，请先登录") }
            }
        }
    }

    fun toggleFavorite() {
        val snapshot = _uiState.value
        if (snapshot.isTogglingFavorite) return

        val prevFav = snapshot.isFavorite
        val newFav = !prevFav

        _uiState.update {
            it.copy(
                isFavorite = newFav,
                isTogglingFavorite = true
            )
        }

        viewModelScope.launch {
            val result = api.toggleFavorite(feedId, favorite = newFav)
            result.onSuccess {
                _uiState.update { state ->
                    val updatedFeed = state.feed?.copy(isFavorite = newFav)
                    updatedFeed?.let { publishFeedUpdate(it) }
                    state.copy(feed = updatedFeed, isTogglingFavorite = false)
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        isFavorite = prevFav,
                        isTogglingFavorite = false,
                        actionMessage = err.message ?: "收藏操作失败，请检查登录状态"
                    )
                }
            }
        }
    }

    fun submitReply(
        message: String,
        replyId: String? = null,
        onComplete: (Boolean, String) -> Unit
    ) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) {
            onComplete(false, "回复内容不能为空")
            return
        }
        if (_uiState.value.isSubmittingReply) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingReply = true) }
            val result = api.replyFeed(feedId, trimmed, replyId = replyId)
            _uiState.update { it.copy(isSubmittingReply = false) }

            result.onSuccess { msg ->
                _uiState.update { state ->
                    val updatedFeed = state.feed?.copy(replyNum = state.feed.replyNum + 1)
                    updatedFeed?.let { publishFeedUpdate(it) }
                    state.copy(feed = updatedFeed)
                }
                onComplete(true, msg)
                fetchComments(page = 1, sort = _uiState.value.commentSort, replace = true)
            }.onFailure { err ->
                onComplete(false, err.message ?: "回复失败，请检查网络或登录状态")
            }
        }
    }

    fun forwardFeed(message: String, onComplete: (Boolean, String) -> Unit) {
        if (_uiState.value.isForwarding) return
        viewModelScope.launch {
            _uiState.update { it.copy(isForwarding = true) }
            val result = api.forwardFeed(feedId, message.trim().ifBlank { "转发动态" })
            _uiState.update { it.copy(isForwarding = false) }
            result.onSuccess { msg ->
                _uiState.update { state ->
                    val updated = state.feed?.copy(forwardNum = state.feed.forwardNum + 1)
                    updated?.let { publishFeedUpdate(it) }
                    state.copy(feed = updated)
                }
                feedUpdateBus?.publishFeedCreated()
                onComplete(true, msg)
            }.onFailure { err ->
                onComplete(false, err.message ?: "转发失败，请检查登录状态")
            }
        }
    }

    fun deleteFeed(onDeleted: () -> Unit) {
        if (_uiState.value.isDeletingFeed) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingFeed = true) }
            api.deleteFeed(feedId).onSuccess {
                _uiState.update { it.copy(isDeletingFeed = false, actionMessage = "动态已删除") }
                feedUpdateBus?.publishDeleted(feedId)
                browsingHistoryRepository?.removeItem(feedId)
                onDeleted()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isDeletingFeed = false,
                        actionMessage = err.message ?: "删除动态失败"
                    )
                }
            }
        }
    }

    fun deleteComment(comment: CommentItem) {
        viewModelScope.launch {
            api.deleteReply(comment.id).onSuccess {
                _uiState.update { state ->
                    val nextRaw = state.rawComments.filterNot { it.id == comment.id }
                    val updatedFeed = state.feed?.copy(replyNum = (state.feed.replyNum - 1).coerceAtLeast(0))
                    updatedFeed?.let { publishFeedUpdate(it) }
                    state.copy(
                        feed = updatedFeed,
                        rawComments = nextRaw,
                        comments = applyCommentBlacklist(nextRaw),
                        actionMessage = "评论已删除"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(actionMessage = err.message ?: "删除评论失败") }
            }
        }
    }

    fun submitVote(optionIds: List<String>) {
        if (_uiState.value.isVoting || optionIds.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isVoting = true) }
            api.submitVote(feedId, optionIds).onSuccess {
                _uiState.update { state ->
                    val currentVote = state.feed?.vote
                    val updatedVote = currentVote?.copy(
                        isVoted = true,
                        selectedOptionIds = optionIds,
                        totalVoteNum = currentVote.totalVoteNum + 1,
                        options = currentVote.options.map { opt ->
                            if (opt.id in optionIds) opt.copy(voteNum = opt.voteNum + 1) else opt
                        }
                    )
                    val updatedFeed = state.feed?.copy(vote = updatedVote)
                    updatedFeed?.let { publishFeedUpdate(it) }
                    state.copy(
                        feed = updatedFeed,
                        isVoting = false,
                        actionMessage = "投票成功"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isVoting = false,
                        actionMessage = err.message ?: "投票失败，请检查登录状态"
                    )
                }
            }
        }
    }

    fun blockFeedAuthor() {
        val feed = _uiState.value.feed ?: return
        if (feed.uid.isBlank()) return
        blacklistRepository?.blockUser(feed.uid, feed.username)
        _uiState.update { it.copy(actionMessage = "已屏蔽 ${feed.username}") }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    private fun publishFeedUpdate(feed: FeedItem) {
        feedUpdateBus?.publish(feed)
        browsingHistoryRepository?.updateFeedIfPresent(feed)
    }

    companion object {
        private const val COMMENT_PAGE_MIN_FOR_MORE = 10
        private const val SUB_REPLY_PAGE_MIN_FOR_MORE = 10
    }
}
