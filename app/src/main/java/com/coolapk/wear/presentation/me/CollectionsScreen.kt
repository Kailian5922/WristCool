package com.coolapk.wear.presentation.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil.compose.AsyncImage
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.CollectionItem
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.repository.FeedUpdateBus
import com.coolapk.wear.presentation.components.FeedCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.LoadMoreEffect
import com.coolapk.wear.presentation.components.WearCenteredLoadingOverlay
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearLoadMoreFooter
import com.coolapk.wear.presentation.components.WearPillBadge
import com.coolapk.wear.presentation.components.WearScreenHeader
import com.coolapk.wear.presentation.components.rotaryEventHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionsUiState(
    val collections: List<CollectionItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null
)

class CollectionsViewModel(
    private val api: CoolapkApi,
    private val uid: String = ""
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsUiState())
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
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
        val isSelf = uid.isBlank() || uid == api.currentUid
        loadJob = viewModelScope.launch {
            _uiState.update {
                if (refresh) it.copy(isLoading = true, isLoadingMore = false, error = null)
                else it.copy(isLoadingMore = true)
            }
            api.getUserCollections(uid, targetPage).onSuccess { list ->
                _uiState.update { state ->
                    val rawMerged = if (refresh) list.distinctBy { it.id }
                    else (state.collections + list).distinctBy { it.id }
                    val merged = if (refresh && rawMerged.isEmpty() && isSelf) {
                        listOf(
                            CollectionItem(
                                id = "default",
                                uid = api.currentUid,
                                title = "默认收藏夹",
                                isOpen = false,
                                isDefault = true
                            )
                        )
                    } else {
                        rawMerged
                    }
                    val addedNew = refresh || merged.size > state.collections.size
                    state.copy(
                        collections = merged,
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
                        error = err.message ?: "加载收藏夹失败"
                    )
                }
            }
        }
    }
}

data class CollectionDetailUiState(
    val items: List<FeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null
)

class CollectionDetailViewModel(
    private val api: CoolapkApi,
    private val collectionId: String,
    private val isDefault: Boolean = false,
    private val feedUpdateBus: FeedUpdateBus? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionDetailUiState())
    val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        if (feedUpdateBus != null) {
            viewModelScope.launch {
                feedUpdateBus.updates.collect { updated ->
                    _uiState.update { state ->
                        if (state.items.none { it.id == updated.id }) state
                        else state.copy(items = state.items.map { if (it.id == updated.id) updated else it })
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
            api.getCollectionItems(collectionId, targetPage, isDefault = isDefault).onSuccess { list ->
                _uiState.update { state ->
                    val merged = if (refresh) list.distinctBy { it.id }
                    else (state.items + list).distinctBy { it.id }
                    val addedNew = refresh || merged.size > state.items.size
                    state.copy(
                        items = merged,
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
                        error = err.message ?: "加载收藏内容失败"
                    )
                }
            }
        }
    }
}

@Composable
fun CollectionsScreen(
    viewModel: CollectionsViewModel,
    onCollectionClick: (id: String, title: String, isDefault: Boolean) -> Unit,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    LoadMoreEffect(
        listState = listState,
        itemCount = uiState.collections.size,
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
                item(key = "collections_header") {
                    WearScreenHeader(
                        title = "我的收藏",
                        subtitle = if (uiState.collections.isNotEmpty()) "${uiState.collections.size} 个收藏夹" else null,
                        transformationSpec = transformationSpec
                    )
                }

                if (uiState.error != null && uiState.collections.isEmpty()) {
                    item(key = "collections_error") {
                        WearErrorRetry(message = uiState.error.orEmpty(), onRetry = { viewModel.refresh() })
                    }
                }

                if (uiState.collections.isEmpty() && !uiState.isLoading && uiState.error == null) {
                    item(key = "collections_empty") {
                        WearEmptyState("暂无收藏夹", icon = Icons.Rounded.Bookmark)
                    }
                }

                itemsIndexed(uiState.collections, key = { idx, c -> "${c.id}_$idx" }) { _, collection ->
                    Card(
                        onClick = { onCollectionClick(collection.id, collection.title, collection.isDefault) },
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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            if (collection.cover.isNotBlank()) {
                                AsyncImage(
                                    model = collection.cover,
                                    contentDescription = collection.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colorScheme.surfaceContainerHigh)
                                )
                            } else {
                                Icon(
                                    imageVector = if (collection.isDefault) Icons.Rounded.Bookmark else Icons.Rounded.FolderSpecial,
                                    contentDescription = null,
                                    tint = if (collection.isDefault) colorScheme.tertiary else colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = collection.title,
                                        color = colorScheme.onSurface,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (collection.isDefault) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        WearPillBadge(
                                            text = "默认",
                                            containerColor = colorScheme.tertiaryContainer,
                                            contentColor = colorScheme.onTertiaryContainer
                                        )
                                    }
                                    if (!collection.isOpen) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = "私密",
                                            tint = colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                val countText = when {
                                    collection.itemNum > 0 -> "${collection.itemNum} 条内容"
                                    collection.isDefault && collection.description.isBlank() -> "系统默认收藏夹"
                                    else -> "${collection.itemNum} 条内容"
                                }
                                Text(
                                    text = countText +
                                        if (collection.description.isNotBlank()) " · ${collection.description}" else "",
                                    color = colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyExtraSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (uiState.isLoadingMore || (!uiState.hasMore && uiState.collections.isNotEmpty())) {
                    item(key = "collections_footer") {
                        WearLoadMoreFooter(
                            isLoadingMore = uiState.isLoadingMore,
                            hasMore = uiState.hasMore,
                            hasItems = uiState.collections.isNotEmpty()
                        )
                    }
                }
            }
        }

        WearCenteredLoadingOverlay(visible = uiState.isLoading && uiState.collections.isEmpty())
    }
}

@Composable
fun CollectionDetailScreen(
    viewModel: CollectionDetailViewModel,
    title: String,
    onFeedClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onTopicClick: (String) -> Unit,
    onProductClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    LoadMoreEffect(
        listState = listState,
        itemCount = uiState.items.size,
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
                item(key = "col_detail_header") {
                    WearScreenHeader(
                        title = title.ifBlank { "我的收藏" },
                        subtitle = if (uiState.items.isNotEmpty()) "${uiState.items.size} 条动态" else null,
                        transformationSpec = transformationSpec
                    )
                }

                if (uiState.error != null && uiState.items.isEmpty()) {
                    item(key = "col_detail_error") {
                        WearErrorRetry(message = uiState.error.orEmpty(), onRetry = { viewModel.refresh() })
                    }
                }

                if (uiState.items.isEmpty() && !uiState.isLoading && uiState.error == null) {
                    item(key = "col_detail_empty") { WearEmptyState("该收藏夹暂无动态") }
                }

                itemsIndexed(uiState.items, key = { idx, feed -> "${feed.id}_$idx" }) { _, feed ->
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

                if (uiState.isLoadingMore || (!uiState.hasMore && uiState.items.isNotEmpty())) {
                    item(key = "col_detail_footer") {
                        WearLoadMoreFooter(
                            isLoadingMore = uiState.isLoadingMore,
                            hasMore = uiState.hasMore,
                            hasItems = uiState.items.isNotEmpty()
                        )
                    }
                }
            }
        }

        WearCenteredLoadingOverlay(visible = uiState.isLoading && uiState.items.isEmpty())
    }
}
