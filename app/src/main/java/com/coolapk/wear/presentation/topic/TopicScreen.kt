package com.coolapk.wear.presentation.topic

import android.widget.Toast
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.coolapk.wear.presentation.components.FeedCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.LoadMoreEffect
import com.coolapk.wear.presentation.components.WearCenteredLoadingOverlay
import com.coolapk.wear.presentation.components.WearEdgeActionButton
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearLoadMoreFooter
import com.coolapk.wear.presentation.components.rotaryEventHandler

@Composable
fun TopicScreen(
    viewModel: TopicViewModel,
    tag: String,
    onFeedClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onTopicClick: (String) -> Unit = {},
    onProductClick: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val context = LocalContext.current

    LaunchedEffect(uiState.actionMessage) {
        val msg = uiState.actionMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearActionMessage()
        }
    }

    LoadMoreEffect(
        listState = listState,
        itemCount = uiState.items.size,
        hasMore = uiState.hasMore,
        isLoading = uiState.isLoading || uiState.isLoadingMore,
        onLoadMore = { viewModel.loadMore() }
    )

    val isFollowed = uiState.topicDetail?.isFollow == true

    Box(modifier = Modifier.fillMaxSize()) {
    HorologistScaffold(
        scrollState = listState,
        edgeButton = {
            WearEdgeActionButton(
                scrollState = listState,
                enabled = !uiState.isTogglingFollow,
                buttonSize = EdgeButtonSize.ExtraSmall,
                onClick = { viewModel.toggleFollow() }
            ) {
                Icon(
                    imageVector = if (isFollowed) Icons.Rounded.Check else Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isFollowed) "已关注" else "关注话题",
                    style = MaterialTheme.typography.labelMedium
                )
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
            item(key = "topic_hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .clip(MaterialTheme.shapes.large)
                        .background(colorScheme.surfaceContainer)
                        .padding(11.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Tag,
                            contentDescription = "话题",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tag,
                            color = colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    uiState.topicDetail?.let { detail ->
                        if (detail.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = detail.description,
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyExtraSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (detail.followNum > 0 || detail.feedNum > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${detail.followNum} 关注 · ${detail.feedNum} 讨论",
                                color = colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TopicSort.entries.forEach { sort ->
                            val selected = uiState.sort == sort
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) colorScheme.primary else colorScheme.surfaceContainerHigh
                                    )
                                    .clickable { viewModel.setSort(sort) }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = sort.label,
                                    color = if (selected) colorScheme.onPrimary else colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.error != null && uiState.items.isEmpty()) {
                item(key = "topic_error") {
                    WearErrorRetry(message = uiState.error.orEmpty(), onRetry = { viewModel.refresh() })
                }
            }

            if (uiState.items.isEmpty() && !uiState.isLoading && uiState.error == null) {
                item(key = "topic_empty") { WearEmptyState("该话题暂无动态") }
            }

            itemsIndexed(uiState.items, key = { index, feed -> "${feed.id}_$index" }) { _, feed ->
                FeedCard(
                    feed = feed,
                    onClick = { onFeedClick(feed.id) },
                    onUserClick = onUserClick,
                    onTopicClick = { clickedTag ->
                        if (clickedTag != tag) onTopicClick(clickedTag)
                    },
                    onProductClick = onProductClick,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                )
            }

            if (uiState.isLoadingMore || (!uiState.hasMore && uiState.items.isNotEmpty())) {
                item(key = "topic_footer") {
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

        WearCenteredLoadingOverlay(visible = uiState.isLoading && uiState.items.isEmpty())
    }
}
