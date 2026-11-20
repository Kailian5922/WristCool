package com.coolapk.wear.presentation.product

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import com.coolapk.wear.presentation.components.FeedCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.LoadMoreEffect
import com.coolapk.wear.presentation.components.WearEdgeActionButton
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearLoadMoreFooter
import com.coolapk.wear.presentation.components.WearLoadingPlaceholder
import com.coolapk.wear.presentation.components.WearPillBadge
import com.coolapk.wear.presentation.components.WearSectionSubHeader
import com.coolapk.wear.presentation.components.rotaryEventHandler

@Composable
fun ProductScreen(
    viewModel: ProductViewModel,
    onFeedClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onTopicClick: (String) -> Unit,
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
        itemCount = uiState.feeds.size,
        hasMore = uiState.hasMore,
        isLoading = uiState.isLoadingDetail || uiState.isLoadingFeeds || uiState.isLoadingMore,
        onLoadMore = { viewModel.loadFeeds(refresh = false) }
    )

    HorologistScaffold(
        scrollState = listState,
        edgeButton = {
            uiState.product?.let { product ->
                WearEdgeActionButton(
                    scrollState = listState,
                    enabled = !uiState.isTogglingFollow,
                    buttonSize = EdgeButtonSize.ExtraSmall,
                    onClick = { viewModel.toggleFollow() }
                ) {
                    Icon(
                        imageVector = if (product.isFollow) Icons.Rounded.Check else Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (product.isFollow) "已关注" else "关注产品",
                        style = MaterialTheme.typography.labelMedium
                    )
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
            if (uiState.isLoadingDetail && uiState.product == null) {
                item(key = "product_loading") { WearLoadingPlaceholder() }
            }

            if (uiState.error != null && uiState.product == null) {
                item(key = "product_error") {
                    WearErrorRetry(
                        message = uiState.error.orEmpty(),
                        onRetry = {
                            viewModel.loadDetail()
                            viewModel.loadFeeds(refresh = true)
                        }
                    )
                }
            }

            uiState.product?.let { product ->
                item(key = "product_hero") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .clip(MaterialTheme.shapes.large)
                            .background(colorScheme.surfaceContainer)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (product.logo.isNotBlank()) {
                                AsyncImage(
                                    model = product.logo,
                                    contentDescription = product.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colorScheme.surfaceContainerHigh)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Smartphone,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = product.title,
                                    color = colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (product.categoryName.isNotBlank()) {
                                    Text(
                                        text = product.categoryName,
                                        color = colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyExtraSmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (product.score > 0f) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colorScheme.tertiaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Star,
                                        contentDescription = "评分",
                                        tint = colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "%.1f (%d人)".format(product.score, product.ratingCount),
                                        color = colorScheme.onTertiaryContainer,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            if (product.priceText.isNotBlank() && product.priceText != "0") {
                                WearPillBadge(
                                    text = if (product.priceText.all { it.isDigit() || it == '.' }) "¥${product.priceText}起" else product.priceText,
                                    containerColor = colorScheme.primaryContainer,
                                    contentColor = colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        if (product.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = product.description,
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyExtraSmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${product.followNum} 关注 · ${product.feedNum} 讨论",
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // 参数与版本配置摘要（借鉴 c001apk-Revived & coolapk-desktop 产品参数表）
                if (product.specs.isNotEmpty()) {
                    item(key = "product_specs") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .clip(MaterialTheme.shapes.large)
                                .background(colorScheme.surfaceContainerLow)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "核心配置与版本",
                                color = colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            product.specs.take(6).forEach { spec ->
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = spec.name,
                                        color = colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyExtraSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (spec.value.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = spec.value,
                                            color = colorScheme.primary,
                                            style = MaterialTheme.typography.bodyExtraSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "product_feeds_header") {
                    WearSectionSubHeader(
                        title = "相关讨论 (${uiState.feeds.size})",
                        transformationSpec = transformationSpec
                    )
                }

                if (uiState.feeds.isEmpty() && !uiState.isLoadingFeeds) {
                    item(key = "product_feeds_empty") { WearEmptyState("暂无产品相关动态") }
                }

                itemsIndexed(uiState.feeds, key = { idx, f -> "${f.id}_$idx" }) { _, feed ->
                    FeedCard(
                        feed = feed,
                        onClick = { onFeedClick(feed.id) },
                        onUserClick = onUserClick,
                        onTopicClick = onTopicClick,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                    )
                }

                if (uiState.isLoadingMore || (!uiState.hasMore && uiState.feeds.isNotEmpty())) {
                    item(key = "product_footer") {
                        WearLoadMoreFooter(
                            isLoadingMore = uiState.isLoadingMore,
                            hasMore = uiState.hasMore,
                            hasItems = uiState.feeds.isNotEmpty()
                        )
                    }
                }
            }
        }
    }
}
