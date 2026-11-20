package com.coolapk.wear.presentation.me

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.CardDefaults
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.coolapk.wear.presentation.components.FeedCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.LoadMoreEffect
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearListContentPadding
import com.coolapk.wear.presentation.components.WearLoadMoreFooter
import com.coolapk.wear.presentation.components.WearLoadingPlaceholder
import com.coolapk.wear.presentation.components.rotaryEventHandler
import com.coolapk.wear.presentation.theme.LocalAppPrimaryColor
import com.coolapk.wear.presentation.theme.WearSurface
import com.coolapk.wear.presentation.theme.WearSurfaceVariant

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onFeedClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val primaryColor = LocalAppPrimaryColor.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
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

    HorologistScaffold(listState = listState) {
        ScalingLazyColumn(
            state = listState,
            contentPadding = WearListContentPadding,
            anchorType = ScalingLazyListAnchorType.ItemStart,
            modifier = Modifier
                .fillMaxSize()
                .rotaryEventHandler(listState)
        ) {
            item {
                if (!uiState.isMultiSelectMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = "收藏",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "我的收藏 (${uiState.items.size})",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (uiState.items.isNotEmpty()) {
                            CompactButton(
                                onClick = { viewModel.enterMultiSelectMode() },
                                colors = ButtonDefaults.buttonColors(backgroundColor = WearSurfaceVariant),
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Checklist,
                                    contentDescription = "多选",
                                    tint = primaryColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "已选 ${uiState.selectedIds.size} 项",
                                color = primaryColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                CompactButton(
                                    onClick = {
                                        if (uiState.selectedIds.size == uiState.items.size) {
                                            viewModel.deselectAll()
                                        } else {
                                            viewModel.selectAll()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(backgroundColor = WearSurfaceVariant),
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SelectAll,
                                        contentDescription = "全选",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                CompactButton(
                                    onClick = { viewModel.exitMultiSelectMode() },
                                    colors = ButtonDefaults.buttonColors(backgroundColor = WearSurfaceVariant),
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "退出多选",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        if (uiState.selectedIds.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            CompactChip(
                                enabled = !uiState.isBatchDeleting,
                                onClick = {
                                    viewModel.batchDeleteSelected { succeeded, failed ->
                                        val msg = when {
                                            failed == 0 -> "已取消收藏 $succeeded 条动态"
                                            succeeded == 0 -> "取消收藏失败，请检查网络或登录状态"
                                            else -> "已取消 $succeeded 条，$failed 条失败"
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = {
                                    Text(
                                        text = if (uiState.isBatchDeleting) "正在删除…" else "批量取消收藏 (${uiState.selectedIds.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                },
                                icon = {
                                    if (uiState.isBatchDeleting) {
                                        CircularProgressIndicator(
                                            indicatorColor = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "删除",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                },
                                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFFC62828)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (uiState.isLoading && uiState.items.isEmpty()) {
                item { WearLoadingPlaceholder() }
            }

            if (uiState.error != null && uiState.items.isEmpty()) {
                item {
                    WearErrorRetry(
                        message = uiState.error.orEmpty(),
                        onRetry = { viewModel.refresh() }
                    )
                }
            }

            if (uiState.items.isEmpty() && !uiState.isLoading && uiState.error == null) {
                item { WearEmptyState("暂无收藏动态\n在动态详情中点击“收藏”即可添加") }
            }

            if (!uiState.isMultiSelectMode) {
                itemsIndexed(uiState.items, key = { index, feed -> "${feed.id}_$index" }) { _, feed ->
                    FeedCard(
                        feed = feed,
                        onClick = { onFeedClick(feed.id) },
                        onLongClick = { viewModel.enterMultiSelectMode(feed.id) },
                        onUserClick = onUserClick
                    )
                }
            } else {
                itemsIndexed(uiState.items, key = { index, feed -> "select_${feed.id}_$index" }) { _, feed ->
                    val isSelected = feed.id in uiState.selectedIds
                    Card(
                        onClick = { viewModel.toggleSelection(feed.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        shape = RoundedCornerShape(14.dp),
                        backgroundPainter = CardDefaults.cardBackgroundPainter(
                            startBackgroundColor = if (isSelected) Color(0xFF1E3A2F) else WearSurface,
                            endBackgroundColor = if (isSelected) Color(0xFF1E3A2F) else WearSurface
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = "选择",
                                tint = if (isSelected) primaryColor else Color(0xFF888888),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = feed.username,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = feed.message,
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isLoadingMore || (!uiState.hasMore && uiState.items.isNotEmpty())) {
                item {
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
