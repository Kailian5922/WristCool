package com.coolapk.wear.presentation.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.model.MyReplyItem
import com.coolapk.wear.data.repository.FeedUpdateBus
import com.coolapk.wear.presentation.components.FeedCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.LoadMoreEffect
import com.coolapk.wear.presentation.components.WearCenteredLoadingOverlay
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearLoadMoreFooter
import com.coolapk.wear.presentation.components.WearScreenHeader
import com.coolapk.wear.presentation.components.rotaryEventHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MyActivityType(val routeKey: String, val title: String) {
    LIKES("likes", "我的点赞"),
    REPLIES("replies", "我的回复")
}

data class MyActivityUiState(
    val feeds: List<FeedItem> = emptyList(),
    val replies: List<MyReplyItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null
)

class MyActivityViewModel(
    private val api: CoolapkApi,
    private val uid: String,
    private val type: MyActivityType,
    private val feedUpdateBus: FeedUpdateBus? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyActivityUiState())
    val uiState: StateFlow<MyActivityUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        if (type == MyActivityType.LIKES && feedUpdateBus != null) {
            viewModelScope.launch {
                feedUpdateBus.updates.collect { updated ->
                    _uiState.update { state ->
                        if (state.feeds.none { it.id == updated.id }) state
                        else state.copy(feeds = state.feeds.map { if (it.id == updated.id) updated else it })
                    }
                }
            }
        }
        load(refresh = true)
    }

    fun refresh() = load(refresh = true)

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        load(refresh = false)
    }

    private fun load(refresh: Boolean) {
        if (refresh) loadJob?.cancel() else if (loadJob?.isActive == true) return
        val targetPage = if (refresh) 1 else _uiState.value.page + 1
        loadJob = viewModelScope.launch {
            _uiState.update {
                if (refresh) it.copy(isLoading = true, isLoadingMore = false, error = null)
                else it.copy(isLoadingMore = true)
            }
            if (type == MyActivityType.LIKES) {
                api.getUserLikes(uid, targetPage).onSuccess { list ->
                    _uiState.update { state ->
                        val merged = if (refresh) list.distinctBy { it.id }
                        else (state.feeds + list).distinctBy { it.id }
                        val addedNew = refresh || merged.size > state.feeds.size
                        state.copy(
                            feeds = merged,
                            page = targetPage,
                            hasMore = list.isNotEmpty() && addedNew,
                            isLoading = false,
                            isLoadingMore = false,
                            error = null
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, isLoadingMore = false, error = err.message ?: "加载点赞失败")
                    }
                }
            } else {
                api.getUserReplies(uid, targetPage).onSuccess { list ->
                    _uiState.update { state ->
                        val merged = if (refresh) list.distinctBy { it.id }
                        else (state.replies + list).distinctBy { it.id }
                        val addedNew = refresh || merged.size > state.replies.size
                        state.copy(
                            replies = merged,
                            page = targetPage,
                            hasMore = list.isNotEmpty() && addedNew,
                            isLoading = false,
                            isLoadingMore = false,
                            error = null
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, isLoadingMore = false, error = err.message ?: "加载回复失败")
                    }
                }
            }
        }
    }
}

@Composable
fun MyActivityScreen(
    viewModel: MyActivityViewModel,
    type: MyActivityType,
    onFeedClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onTopicClick: (String) -> Unit,
    onProductClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val itemCount = if (type == MyActivityType.LIKES) uiState.feeds.size else uiState.replies.size

    LoadMoreEffect(
        listState = listState,
        itemCount = itemCount,
        hasMore = uiState.hasMore,
        isLoading = uiState.isLoading || uiState.isLoadingMore,
        onLoadMore = { viewModel.loadMore() }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        HorologistScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryEventHandler(listState)
            ) {
                item(key = "act_header") {
                    WearScreenHeader(
                        title = type.title,
                        subtitle = if (itemCount > 0) "$itemCount 条" else null,
                        transformationSpec = transformationSpec
                    )
                }

                if (uiState.error != null && itemCount == 0) {
                    item(key = "act_error") {
                        WearErrorRetry(message = uiState.error.orEmpty(), onRetry = { viewModel.refresh() })
                    }
                }

                if (itemCount == 0 && !uiState.isLoading && uiState.error == null) {
                    item(key = "act_empty") {
                        WearEmptyState(
                            text = if (type == MyActivityType.LIKES) "暂无点赞记录" else "暂无发表过的回复",
                            icon = if (type == MyActivityType.LIKES) Icons.Rounded.FavoriteBorder else Icons.Rounded.ChatBubbleOutline
                        )
                    }
                }

                if (type == MyActivityType.LIKES) {
                    itemsIndexed(uiState.feeds, key = { idx, f -> "${f.id}_$idx" }) { _, feed ->
                        FeedCard(
                            feed = feed,
                            onClick = { onFeedClick(feed.id) },
                            onUserClick = onUserClick,
                            onTopicClick = onTopicClick,
                            onProductClick = onProductClick,
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                        )
                    }
                } else {
                    itemsIndexed(uiState.replies, key = { idx, r -> "${r.id}_$idx" }) { _, reply ->
                        Card(
                            onClick = { if (reply.feedId.isNotBlank()) onFeedClick(reply.feedId) },
                            transformation = SurfaceTransformation(transformationSpec),
                            colors = CardDefaults.cardColors(
                                containerColor = colorScheme.surfaceContainer,
                                contentColor = colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (reply.targetUsername.isNotBlank()) "回复 @${reply.targetUsername}" else "我的回复",
                                    color = colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (reply.dateString.isNotBlank()) {
                                    Text(
                                        text = reply.dateString,
                                        color = colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyExtraSmall
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = reply.message,
                                color = colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (reply.feedTitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "原动态：${reply.feedTitle}",
                                    color = colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyExtraSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (uiState.isLoadingMore || (!uiState.hasMore && itemCount > 0)) {
                    item(key = "act_footer") {
                        WearLoadMoreFooter(
                            isLoadingMore = uiState.isLoadingMore,
                            hasMore = uiState.hasMore,
                            hasItems = itemCount > 0
                        )
                    }
                }
            }
        }

        WearCenteredLoadingOverlay(visible = uiState.isLoading && itemCount == 0)
    }
}
