package com.coolapk.wear.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import coil.decode.BitmapFactoryDecoder
import coil.request.ImageRequest
import coil.size.Precision
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.util.TextSanitizer
import com.coolapk.wear.presentation.theme.LocalWatchSettings

/**
 * Material 3 Expressive 动态卡片。
 * 1. 基于 Wear M3 Card + SurfaceTransformation，在 TransformingLazyColumn 中随圆屏边缘流畅形变；
 * 2. 支持引用转发动态预览、投票标识、数码产品/话题标签跳转、星级评分展示与省流无图模式；
 * 3. 支持长按操作（如收藏夹多选）。
 */
@Composable
fun FeedCard(
    feed: FeedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null,
    onLongClick: (() -> Unit)? = null,
    onUserClick: ((String) -> Unit)? = null,
    onTopicClick: ((String) -> Unit)? = null,
    onProductClick: ((String) -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val settings = LocalWatchSettings.current

    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface
        )
    ) {
        // 顶部作者与时间/设备信息
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onUserClick != null && feed.uid.isNotBlank()) {
                        Modifier.clickable { onUserClick(feed.uid) }
                    } else {
                        Modifier
                    }
                )
        ) {
            UserAvatar(
                avatarUrl = feed.userAvatar,
                contentDescription = feed.username,
                size = 24.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feed.username,
                    color = colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildString {
                    if (feed.dateString.isNotBlank()) append(feed.dateString)
                    if (feed.deviceTitle.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(feed.deviceTitle)
                    }
                }
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyExtraSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 动态类型角标（投票 / 评分 / 长文 / 二手）
            val badgeText = remember(feed.feedType, feed.vote, feed.ratingScore, feed.articleBlocks) {
                when {
                    feed.vote != null -> "投票"
                    feed.ratingScore > 0f -> "%.1f★".format(feed.ratingScore)
                    feed.articleBlocks.isNotEmpty() || feed.feedType.equals("feedArticle", ignoreCase = true) -> "长文"
                    feed.feedType.equals("ershou", ignoreCase = true) -> "二手"
                    else -> null
                }
            }
            if (badgeText != null) {
                Spacer(modifier = Modifier.width(4.dp))
                WearPillBadge(
                    text = badgeText,
                    containerColor = colorScheme.tertiaryContainer,
                    contentColor = colorScheme.onTertiaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 真实标题
        val validTitle = remember(feed.title, feed.username) {
            TextSanitizer.filterAutoTitle(feed.title, feed.username)
        }
        if (validTitle.isNotBlank()) {
            Text(
                text = validTitle,
                color = colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
        }

        // 正文摘要
        if (feed.message.isNotBlank()) {
            Text(
                text = feed.message,
                color = colorScheme.onSurface.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 转发引用原动态预览
        feed.forwardFeed?.let { fw ->
            Spacer(modifier = Modifier.height(5.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = "转发",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "@${fw.username}",
                        color = colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val fwPreview = fw.title.ifBlank { fw.message }
                if (fwPreview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = fwPreview,
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyExtraSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 投票概要提示条
        feed.vote?.let { vote ->
            Spacer(modifier = Modifier.height(5.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colorScheme.secondaryContainer.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Poll,
                    contentDescription = "投票",
                    tint = colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "${vote.title} · ${vote.options.size}选项" +
                        if (vote.totalVoteNum > 0) " (${vote.totalVoteNum}人)" else "",
                    color = colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 话题或关联数码产品胶囊
        val firstTopic = remember(feed.message, feed.relationTopics) {
            feed.relationTopics.firstOrNull()
                ?: TextSanitizer.extractTopics(feed.message, maxLength = 16).firstOrNull()
        }
        if (firstTopic != null || (feed.targetProductId.isNotBlank() && feed.targetProductName.isNotBlank())) {
            Spacer(modifier = Modifier.height(5.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (feed.targetProductId.isNotBlank() && feed.targetProductName.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.tertiaryContainer)
                            .then(
                                if (onProductClick != null) {
                                    Modifier.clickable { onProductClick(feed.targetProductId) }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Smartphone,
                            contentDescription = "产品",
                            tint = colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = feed.targetProductName,
                            color = colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (firstTopic != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.primaryContainer.copy(alpha = 0.85f))
                            .then(
                                if (onTopicClick != null) {
                                    Modifier.clickable { onTopicClick(firstTopic) }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tag,
                            contentDescription = "话题",
                            tint = colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = firstTopic,
                            color = colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 缩略图预览（遵循手表端无图省流模式）
        if (feed.picArr.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            if (settings.noImageMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = "图片",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "共 ${feed.picArr.size} 张图片（省流模式）",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                val thumbContext = LocalContext.current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val previewPics = feed.picArr.take(3)
                    previewPics.forEach { picUrl ->
                        val thumbUrl = remember(picUrl) { TextSanitizer.toCoolapkThumbnailUrl(picUrl) }
                        var useOriginalFallback by remember(picUrl) { mutableStateOf(false) }
                        val activeUrl = if (useOriginalFallback) picUrl else thumbUrl
                        val thumbRequest = remember(activeUrl, thumbContext) {
                            ImageRequest.Builder(thumbContext)
                                .data(activeUrl)
                                .size(144, 144)
                                .precision(Precision.INEXACT)
                                .decoderFactory(BitmapFactoryDecoder.Factory())
                                .crossfade(true)
                                .build()
                        }
                        AsyncImage(
                            model = thumbRequest,
                            contentDescription = "图片",
                            contentScale = ContentScale.Crop,
                            onError = {
                                if (!useOriginalFallback && thumbUrl != picUrl) {
                                    useOriginalFallback = true
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colorScheme.surfaceContainerHigh)
                        )
                    }
                    if (feed.picArr.size > 3) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 5.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "+${feed.picArr.size - 3}",
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 底部互动数据栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (feed.ratingScore > 0f) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = "评分",
                    tint = colorScheme.tertiary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "%.1f".format(feed.ratingScore),
                    color = colorScheme.tertiary,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Icon(
                imageVector = if (feed.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "赞",
                tint = if (feed.isLiked) colorScheme.error else colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = if (feed.likeNum > 0) feed.likeNum.toString() else "赞",
                color = if (feed.isLiked) colorScheme.error else colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = Icons.Rounded.ChatBubbleOutline,
                contentDescription = "评",
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = if (feed.replyNum > 0) feed.replyNum.toString() else "评",
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
