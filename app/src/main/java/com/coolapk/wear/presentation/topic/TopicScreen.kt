package com.coolapk.wear.presentation.topic

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
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

@Composable
fun TopicScreen(
    viewModel: TopicViewModel,
    tag: String,
    onFeedClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onTopicClick: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val primaryColor = LocalAppPrimaryColor.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tag,
                        contentDescription = "话题",
                        tint = primaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "# $tag #",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                item { WearEmptyState("该话题暂无动态") }
            }

            itemsIndexed(uiState.items, key = { index, feed -> "${feed.id}_$index" }) { _, feed ->
                FeedCard(
                    feed = feed,
                    onClick = { onFeedClick(feed.id) },
                    onUserClick = onUserClick,
                    onTopicClick = { clickedTag ->
                        if (clickedTag != tag) onTopicClick(clickedTag)
                    }
                )
            }

            if (uiState.isLoadingMore || (!uiState.hasMore && uiState.items.isNotEmpty())) {
                item {
                    WearLoadMoreFooter(
                        isLoadingMore = uiState.isLoadingMore,
                        hasMore = uiState.hasMore,
                        hasItems = uiState.items.isNotEmpty(),
                        endText = "没有更多话题动态了"
                    )
                }
            }
        }
    }
}
