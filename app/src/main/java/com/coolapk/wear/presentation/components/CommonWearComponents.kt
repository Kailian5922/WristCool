package com.coolapk.wear.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.CardDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.coolapk.wear.data.model.UserCardItem
import com.coolapk.wear.presentation.theme.LocalAppOnPrimaryColor
import com.coolapk.wear.presentation.theme.LocalAppPrimaryColor
import com.coolapk.wear.presentation.theme.WearSurface
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 统一的触底自动加载更多副作用。
 * 基于 `layoutInfo.totalItemsCount` 计算剩余条目数（自动包含 Header/Footer），
 * 且在数据列表为空、正在加载或已无更多数据时不会误触发。
 */
@Composable
fun LoadMoreEffect(
    listState: ScalingLazyListState,
    itemCount: Int,
    hasMore: Boolean,
    isLoading: Boolean,
    buffer: Int = 2,
    onLoadMore: () -> Unit
) {
    val currentItemCount by rememberUpdatedState(itemCount)
    val currentHasMore by rememberUpdatedState(hasMore)
    val currentIsLoading by rememberUpdatedState(isLoading)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index
            val total = layoutInfo.totalItemsCount
            lastVisible to total
        }
            .distinctUntilChanged()
            .collect { (lastVisibleIndex, totalItems) ->
                if (lastVisibleIndex != null &&
                    totalItems > 0 &&
                    currentItemCount > 0 &&
                    currentHasMore &&
                    !currentIsLoading &&
                    lastVisibleIndex >= totalItems - 1 - buffer
                ) {
                    currentOnLoadMore()
                }
            }
    }
}

@Composable
fun WearLoadingPlaceholder(
    height: Dp = 100.dp,
    indicatorSize: Dp = 24.dp
) {
    val primaryColor = LocalAppPrimaryColor.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            indicatorColor = primaryColor,
            modifier = Modifier.size(indicatorSize)
        )
    }
}

@Composable
fun WearErrorRetry(
    message: String,
    onRetry: () -> Unit,
    buttonSize: Dp = 36.dp
) {
    val primaryColor = LocalAppPrimaryColor.current
    val onPrimaryColor = LocalAppOnPrimaryColor.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = message,
            color = Color(0xFFA0A0A5),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(backgroundColor = primaryColor),
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "重试",
                tint = onPrimaryColor
            )
        }
    }
}

@Composable
fun WearEmptyState(text: String) {
    Text(
        text = text,
        color = Color(0xFF777777),
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}

@Composable
fun WearLoadMoreFooter(
    isLoadingMore: Boolean,
    hasMore: Boolean,
    hasItems: Boolean,
    endText: String = "没有更多了"
) {
    val primaryColor = LocalAppPrimaryColor.current
    if (isLoadingMore) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                indicatorColor = primaryColor,
                modifier = Modifier.size(16.dp)
            )
        }
    } else if (!hasMore && hasItems) {
        Text(
            text = endText,
            color = Color(0xFF666666),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        )
    }
}

@Composable
fun UserAvatar(
    avatarUrl: String,
    contentDescription: String,
    size: Dp
) {
    val primaryColor = LocalAppPrimaryColor.current
    if (avatarUrl.isNotBlank()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(primaryColor)
        )
    }
}

@Composable
fun UserRowCard(
    user: UserCardItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = WearSurface,
            endBackgroundColor = WearSurface
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            UserAvatar(
                avatarUrl = user.userAvatar,
                contentDescription = user.username,
                size = 28.dp
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.username,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (user.bio.isNotBlank()) {
                    Text(
                        text = user.bio,
                        color = Color(0xFFA0A0A5),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
