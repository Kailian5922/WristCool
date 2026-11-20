package com.coolapk.wear.presentation.me

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CheckboxButton
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.coolapk.wear.presentation.components.FeedCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.LoadMoreEffect
import com.coolapk.wear.presentation.components.WearEdgeActionButton
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearLoadMoreFooter
import com.coolapk.wear.presentation.components.WearLoadingPlaceholder
import com.coolapk.wear.presentation.components.rotaryEventHandler

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onFeedClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val context = LocalContext.current

    BackHandler(enabled = uiState.isMultiSelectMode) {
        viewModel.exitMultiSelectMode()
    }

    LoadMoreEffect(
        listState = listState,
        itemCount = uiState.items.size,
        hasMore = uiState.hasMore,
        isLoading = uiState.isLoading || uiState.isLoadingMore,
        onLoadMore = { viewModel.loadMore() }
    )

    HorologistScaffold(
        scrollState = listState,
        edgeButton = {
            if (uiState.isMultiSelectMode && uiState.selectedIds.isNotEmpty()) {
                WearEdgeActionButton(
                    scrollState = listState,
                    enabled = !uiState.isBatchDeleting,
                    buttonSize = EdgeButtonSize.Small,
                    onClick = {
                        viewModel.batchDeleteSelected { succeeded, failed ->
                            val msg = when {
                                failed == 0 -> "已取消收藏 $succeeded 条动态"
                                succeeded == 0 -> "取消收藏失败，请检查网络或登录状态"
                                else -> "已取消 $succeeded 条，$failed 条失败"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    if (uiState.isBatchDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp))
                    } else {
                        Icon(Icons.Rounded.Delete, contentDescription = "取消收藏", modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("取消收藏 (${uiState.selectedIds.size})", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .rotaryEventHandler(listState)
        ) {
            item(key = "fav_header") {
                if (!uiState.isMultiSelectMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = "收藏",
                            tint = colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "我的收藏 (${uiState.items.size})",
                            color = colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (uiState.items.isNotEmpty()) {
                            FilledTonalIconButton(
                                onClick = { viewModel.enterMultiSelectMode() },
                                shapes = IconButtonDefaults.animatedShapes(),
                                modifier = Modifier.size(IconButtonDefaults.ExtraSmallButtonSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Checklist,
                                    contentDescription = "多选",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    ) {
                        Text(
                            text = "已选 ${uiState.selectedIds.size} 项",
                            color = colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            FilledTonalIconButton(
                                onClick = {
                                    if (uiState.selectedIds.size == uiState.items.size) {
                                        viewModel.deselectAll()
                                    } else {
                                        viewModel.selectAll()
                                    }
                                },
                                shapes = IconButtonDefaults.animatedShapes(),
                                modifier = Modifier.size(IconButtonDefaults.ExtraSmallButtonSize)
                            ) {
                                Icon(Icons.Rounded.SelectAll, contentDescription = "全选", modifier = Modifier.size(15.dp))
                            }
                            FilledTonalIconButton(
                                onClick = { viewModel.exitMultiSelectMode() },
                                shapes = IconButtonDefaults.animatedShapes(),
                                modifier = Modifier.size(IconButtonDefaults.ExtraSmallButtonSize)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "退出多选", modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }

            if (uiState.isLoading && uiState.items.isEmpty()) {
                item(key = "fav_loading") { WearLoadingPlaceholder() }
            }

            if (uiState.error != null && uiState.items.isEmpty()) {
                item(key = "fav_error") {
                    WearErrorRetry(message = uiState.error.orEmpty(), onRetry = { viewModel.refresh() })
                }
            }

            if (uiState.items.isEmpty() && !uiState.isLoading && uiState.error == null) {
                item(key = "fav_empty") {
                    WearEmptyState("暂无收藏动态\n在动态详情点击收藏即可添加", icon = Icons.Rounded.Bookmark)
                }
            }

            if (!uiState.isMultiSelectMode) {
                itemsIndexed(uiState.items, key = { index, feed -> "${feed.id}_$index" }) { _, feed ->
                    FeedCard(
                        feed = feed,
                        onClick = { onFeedClick(feed.id) },
                        onLongClick = { viewModel.enterMultiSelectMode(feed.id) },
                        onUserClick = onUserClick,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                    )
                }
            } else {
                itemsIndexed(uiState.items, key = { index, feed -> "select_${feed.id}_$index" }) { _, feed ->
                    val isSelected = feed.id in uiState.selectedIds
                    CheckboxButton(
                        checked = isSelected,
                        onCheckedChange = { viewModel.toggleSelection(feed.id) },
                        label = {
                            Text(
                                text = feed.username,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = feed.title.ifBlank { feed.message },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    )
                }
            }

            if (uiState.isLoadingMore || (!uiState.hasMore && uiState.items.isNotEmpty())) {
                item(key = "fav_footer") {
                    WearLoadMoreFooter(
                        isLoadingMore = uiState.isLoadingMore,
                        hasMore = uiState.hasMore,
                        hasItems = uiState.items.isNotEmpty(),
                        endText = "已展示全部收藏"
                    )
                }
            }
        }
    }
}
