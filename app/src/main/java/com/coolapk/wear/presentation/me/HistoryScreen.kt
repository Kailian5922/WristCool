package com.coolapk.wear.presentation.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.rememberRevealState
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.repository.AuthRepository
import com.coolapk.wear.data.repository.BrowsingHistoryRepository
import com.coolapk.wear.presentation.components.FeedCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.LoadMoreEffect
import com.coolapk.wear.presentation.components.WearCenteredLoadingOverlay
import com.coolapk.wear.presentation.components.WearConfirmDialog
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearLoadMoreFooter
import com.coolapk.wear.presentation.components.rotaryEventHandler
import kotlinx.coroutines.launch

private enum class HistorySource(val label: String) {
    CLOUD("云端足迹"),
    LOCAL("本地回退")
}

@Composable
fun HistoryScreen(
    browsingHistoryRepository: BrowsingHistoryRepository,
    authRepository: AuthRepository,
    api: CoolapkApi,
    onFeedClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val localHistory by browsingHistoryRepository.history.collectAsState()
    val credentials by authRepository.credentials.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val scope = rememberCoroutineScope()

    var source by remember { mutableStateOf(HistorySource.CLOUD) }
    var cloudHistory by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var cloudPage by remember { mutableStateOf(1) }
    var cloudHasMore by remember { mutableStateOf(true) }
    var isLoadingCloud by remember { mutableStateOf(credentials.isLoggedIn) }
    var isLoadingMoreCloud by remember { mutableStateOf(false) }
    var cloudError by remember { mutableStateOf<String?>(null) }
    var cloudReloadTick by remember { mutableStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(credentials.isLoggedIn, cloudReloadTick) {
        if (!credentials.isLoggedIn) {
            cloudHistory = emptyList()
            cloudPage = 1
            cloudHasMore = false
            isLoadingCloud = false
            isLoadingMoreCloud = false
            cloudError = null
            return@LaunchedEffect
        }
        isLoadingCloud = true
        isLoadingMoreCloud = false
        cloudError = null
        api.getCloudHitHistory(1).onSuccess { list ->
            cloudHistory = list.distinctBy { it.id }
            cloudPage = 1
            cloudHasMore = list.isNotEmpty()
            isLoadingCloud = false
        }.onFailure { err ->
            cloudError = err.message ?: "云端足迹加载失败"
            isLoadingCloud = false
        }
    }

    fun loadMoreCloud() {
        if (!credentials.isLoggedIn || isLoadingCloud || isLoadingMoreCloud || !cloudHasMore) return
        val nextPage = cloudPage + 1
        isLoadingMoreCloud = true
        scope.launch {
            api.getCloudHitHistory(nextPage).onSuccess { list ->
                val merged = (cloudHistory + list).distinctBy { it.id }
                val addedNew = merged.size > cloudHistory.size
                cloudHistory = merged
                cloudPage = nextPage
                cloudHasMore = list.isNotEmpty() && addedNew
                isLoadingMoreCloud = false
            }.onFailure {
                isLoadingMoreCloud = false
            }
        }
    }

    val isCloudInitialLoading = source == HistorySource.CLOUD && isLoadingCloud && cloudHistory.isEmpty()
    val isCloudFallingBackToLocal =
        source == HistorySource.CLOUD && !isLoadingCloud && cloudHistory.isEmpty() && localHistory.isNotEmpty()

    LoadMoreEffect(
        listState = listState,
        itemCount = if (source == HistorySource.CLOUD) cloudHistory.size else 0,
        hasMore = source == HistorySource.CLOUD && credentials.isLoggedIn && cloudHistory.isNotEmpty() && cloudHasMore,
        isLoading = isLoadingCloud || isLoadingMoreCloud,
        onLoadMore = { loadMoreCloud() }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        HorologistScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryEventHandler(listState, enabled = !showClearDialog)
            ) {
                item(key = "history_header") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = "历史",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "浏览历史",
                                    color = colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (source == HistorySource.LOCAL && localHistory.isNotEmpty()) {
                                FilledTonalIconButton(
                                    onClick = { showClearDialog = true },
                                    shapes = IconButtonDefaults.animatedShapes(),
                                    modifier = Modifier.size(IconButtonDefaults.ExtraSmallButtonSize)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "清空",
                                        tint = colorScheme.error,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            } else if (source == HistorySource.CLOUD && credentials.isLoggedIn && !isLoadingCloud) {
                                FilledTonalIconButton(
                                    onClick = { cloudReloadTick++ },
                                    shapes = IconButtonDefaults.animatedShapes(),
                                    modifier = Modifier.size(IconButtonDefaults.ExtraSmallButtonSize)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "刷新云端足迹",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            HistorySource.entries.forEach { tab ->
                                val selected = source == tab
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) colorScheme.primary else colorScheme.surfaceContainerHigh
                                        )
                                        .clickable { source = tab }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = tab.label,
                                        color = if (selected) colorScheme.onPrimary else colorScheme.onSurface,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                if (source == HistorySource.CLOUD) {
                    if (isCloudInitialLoading) {
                        // 由屏幕居中旋转指示器展示加载态
                    } else if (cloudHistory.isNotEmpty()) {
                        itemsIndexed(cloudHistory, key = { idx, feed -> "cld_${feed.id}_$idx" }) { _, feed ->
                            FeedCard(
                                feed = feed,
                                onClick = { onFeedClick(feed.id) },
                                onUserClick = onUserClick,
                                transformation = SurfaceTransformation(transformationSpec),
                                modifier = Modifier
                                    .transformedHeight(this, transformationSpec)
                                    .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                            )
                        }
                        if (isLoadingMoreCloud || !cloudHasMore) {
                            item(key = "cloud_footer") {
                                WearLoadMoreFooter(
                                    isLoadingMore = isLoadingMoreCloud,
                                    hasMore = cloudHasMore,
                                    hasItems = cloudHistory.isNotEmpty()
                                )
                            }
                        }
                    } else if (isCloudFallingBackToLocal) {
                        item(key = "cloud_fallback_notice") {
                            val fallbackReason = when {
                                !credentials.isLoggedIn -> "未登录 · 已回退本地记录"
                                cloudError != null -> "云端暂不可用 · 已回退本地记录"
                                else -> "云端暂无记录 · 已回退本地记录"
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .background(colorScheme.surfaceContainerHigh)
                                    .clickable(enabled = credentials.isLoggedIn) { cloudReloadTick++ }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = fallbackReason,
                                    color = colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyExtraSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        itemsIndexed(localHistory, key = { idx, feed -> "fb_${feed.id}_$idx" }) { _, feed ->
                            FeedCard(
                                feed = feed,
                                onClick = { onFeedClick(feed.id) },
                                onUserClick = onUserClick,
                                transformation = SurfaceTransformation(transformationSpec),
                                modifier = Modifier
                                    .transformedHeight(this, transformationSpec)
                                    .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                            )
                        }
                    } else if (!credentials.isLoggedIn) {
                        item(key = "cloud_unlogged") {
                            WearEmptyState("登录后可查看云端足迹\n暂无本地浏览记录", icon = Icons.Rounded.History)
                        }
                    } else if (cloudError != null) {
                        item(key = "cloud_error") {
                            WearErrorRetry(
                                message = cloudError.orEmpty(),
                                onRetry = { cloudReloadTick++ }
                            )
                        }
                    } else {
                        item(key = "cloud_empty") {
                            WearEmptyState("暂无云端足迹与本地记录", icon = Icons.Rounded.History)
                        }
                    }
                } else {
                    if (localHistory.isEmpty()) {
                        item(key = "local_empty") {
                            WearEmptyState("暂无本地浏览记录\n左滑单条记录可单独移除", icon = Icons.Rounded.History)
                        }
                    } else {
                        itemsIndexed(localHistory, key = { idx, feed -> "loc_${feed.id}_$idx" }) { _, feed ->
                            val revealState = rememberRevealState()
                            SwipeToReveal(
                                revealState = revealState,
                                onSwipePrimaryAction = { browsingHistoryRepository.removeItem(feed.id) },
                                primaryAction = {
                                    PrimaryActionButton(
                                        onClick = { browsingHistoryRepository.removeItem(feed.id) },
                                        icon = { Icon(Icons.Rounded.Delete, contentDescription = "删除") },
                                        text = { Text("删除") }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, transformationSpec)
                                    .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                            ) {
                                FeedCard(
                                    feed = feed,
                                    onClick = { onFeedClick(feed.id) },
                                    onUserClick = onUserClick,
                                    transformation = SurfaceTransformation(transformationSpec)
                                )
                            }
                        }
                    }
                }
            }
        }

        WearCenteredLoadingOverlay(visible = isCloudInitialLoading)

        WearConfirmDialog(
            visible = showClearDialog,
            title = "清空浏览历史",
            message = "确认清空全部 ${localHistory.size} 条本地记录？",
            confirmIcon = Icons.Rounded.Delete,
            confirmContentDescription = "确认清空",
            isDestructive = true,
            onConfirm = {
                showClearDialog = false
                browsingHistoryRepository.clearHistory()
            },
            onDismiss = { showClearDialog = false }
        )
    }
}
