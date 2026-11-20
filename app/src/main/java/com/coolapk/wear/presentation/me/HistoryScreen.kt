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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
import com.coolapk.wear.data.repository.BrowsingHistoryRepository
import com.coolapk.wear.presentation.components.FeedCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.WearConfirmDialog
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearLoadingPlaceholder
import com.coolapk.wear.presentation.components.rotaryEventHandler

private enum class HistorySource(val label: String) {
    LOCAL("本地离线"),
    CLOUD("云端足迹")
}

@Composable
fun HistoryScreen(
    browsingHistoryRepository: BrowsingHistoryRepository,
    api: CoolapkApi,
    onFeedClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val localHistory by browsingHistoryRepository.history.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    var source by remember { mutableStateOf(HistorySource.LOCAL) }
    var cloudHistory by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var isLoadingCloud by remember { mutableStateOf(false) }
    var cloudError by remember { mutableStateOf<String?>(null) }
    var cloudReloadTick by remember { mutableStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(source, cloudReloadTick) {
        if (source == HistorySource.CLOUD) {
            isLoadingCloud = true
            cloudError = null
            api.getCloudHitHistory(1).onSuccess { list ->
                cloudHistory = list.distinctBy { it.id }
                isLoadingCloud = false
            }.onFailure { err ->
                cloudError = err.message ?: "请先登录查看云端足迹"
                isLoadingCloud = false
            }
        }
    }

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

                if (source == HistorySource.LOCAL) {
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
                } else {
                    if (isLoadingCloud) {
                        item(key = "cloud_loading") { WearLoadingPlaceholder() }
                    } else if (cloudError != null) {
                        item(key = "cloud_error") {
                            WearErrorRetry(
                                message = cloudError.orEmpty(),
                                onRetry = { cloudReloadTick++ }
                            )
                        }
                    } else if (cloudHistory.isEmpty()) {
                        item(key = "cloud_empty") {
                            WearEmptyState("暂无云端足迹记录", icon = Icons.Rounded.History)
                        }
                    } else {
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
                    }
                }
            }
        }

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
