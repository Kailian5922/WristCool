package com.coolapk.wear.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import coil.decode.BitmapFactoryDecoder
import coil.request.ImageRequest
import coil.size.Precision
import com.coolapk.wear.data.model.CommentItem
import com.coolapk.wear.data.model.SubReplyItem
import com.coolapk.wear.presentation.theme.LocalWatchSettings

/**
 * 单条评论的楼中楼展开与分页状态，由 ViewModel 托管。
 */
data class SubReplyUiState(
    val isExpanded: Boolean = false,
    val replies: List<SubReplyItem> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Material 3 Expressive 评论卡片。
 * 借鉴 coolapk-desktop 与 c001apk-Revived，支持评论点赞、回复单条评论/楼中楼、删除自己的评论与全量楼中楼分页展开；
 * 评论配图默认以轻量小方块预览图呈现，点击后进入全屏加载完整原图/动图；开启「无图省流模式」后仅展示点击查看入口。
 */
@Composable
fun CommentCard(
    comment: CommentItem,
    subReplyState: SubReplyUiState,
    onToggleExpand: () -> Unit,
    onLoadMoreSubReplies: () -> Unit,
    onRetrySubReplies: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null,
    currentUid: String = "",
    onLikeComment: ((CommentItem) -> Unit)? = null,
    onReplyComment: ((replyId: String, targetUsername: String) -> Unit)? = null,
    onDeleteComment: ((CommentItem) -> Unit)? = null,
    onUserClick: ((String) -> Unit)? = null,
    onViewImage: ((String) -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val noImageMode = LocalWatchSettings.current.noImageMode
    val loadedSubReplies = subReplyState.replies.ifEmpty { comment.subReplies }
    val isOwnComment = currentUid.isNotBlank() && comment.uid == currentUid

    Card(
        onClick = { onReplyComment?.invoke(comment.id, comment.username) },
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface
        )
    ) {
        // 作者行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onUserClick != null && comment.uid.isNotBlank()) {
                        Modifier.clickable { onUserClick(comment.uid) }
                    } else {
                        Modifier
                    }
                )
        ) {
            UserAvatar(
                avatarUrl = comment.userAvatar,
                contentDescription = comment.username,
                size = 20.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = comment.username,
                    color = colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (comment.isAuthor) {
                    Spacer(modifier = Modifier.width(4.dp))
                    WearPillBadge(
                        text = "楼主",
                        containerColor = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer
                    )
                }
                if (comment.isTop) {
                    Spacer(modifier = Modifier.width(4.dp))
                    WearPillBadge(
                        text = "置顶",
                        containerColor = colorScheme.tertiaryContainer,
                        contentColor = colorScheme.onTertiaryContainer
                    )
                }
            }
            if (comment.dateString.isNotBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = comment.dateString,
                    color = colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyExtraSmall,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        // 评论正文
        if (comment.message.isNotBlank()) {
            Text(
                text = comment.message,
                color = colorScheme.onSurface.copy(alpha = 0.94f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // 评论配图 / GIF：默认加载小方块低分辨率预览图（点开才全量加载），无图模式合并为点击查看按钮
        if (comment.pic.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            CommentImagePreview(
                imageUrl = comment.pic,
                previewSize = 56.dp,
                noImageMode = noImageMode,
                isSubReply = false,
                onClick = { onViewImage?.invoke(comment.pic) }
            )
        }

        // 楼中楼嵌套回复区
        val replyTotal = maxOf(comment.subReplies.size, comment.replyNum, loadedSubReplies.size)
        if (replyTotal > 0 || loadedSubReplies.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                val hasMoreThanPreview = replyTotal > 3 || loadedSubReplies.size > 3 || comment.replyNum > loadedSubReplies.size
                val displayReplies = if (subReplyState.isExpanded || !hasMoreThanPreview) {
                    loadedSubReplies
                } else {
                    loadedSubReplies.take(3)
                }

                displayReplies.forEach { sub ->
                    val subText = buildAnnotatedString {
                        withStyle(SpanStyle(color = colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                            append(sub.username)
                        }
                        if (sub.isAuthor) {
                            withStyle(SpanStyle(color = colorScheme.tertiary, fontWeight = FontWeight.Bold)) {
                                append(" [楼主]")
                            }
                        }
                        if (sub.replyUsername.isNotBlank()) {
                            withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                                append(" 回复 ")
                            }
                            withStyle(SpanStyle(color = colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                                append(sub.replyUsername)
                            }
                        }
                        if (sub.message.isNotBlank()) {
                            withStyle(SpanStyle(color = colorScheme.onSurface.copy(alpha = 0.9f))) {
                                append(": ${sub.message}")
                            }
                        }
                    }

                    Text(
                        text = subText,
                        style = MaterialTheme.typography.bodyExtraSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = onReplyComment != null && sub.id.isNotBlank()) {
                                onReplyComment?.invoke(sub.id, sub.username)
                            }
                            .padding(vertical = 2.dp)
                    )

                    if (sub.pic.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        CommentImagePreview(
                            imageUrl = sub.pic,
                            previewSize = 44.dp,
                            noImageMode = noImageMode,
                            isSubReply = true,
                            onClick = { onViewImage?.invoke(sub.pic) }
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }

                if (subReplyState.isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        ExpressiveLoadingIndicator(size = 15.dp, contained = false)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "正在加载楼中楼…",
                            color = colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (subReplyState.error != null && !subReplyState.isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onRetrySubReplies() }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "重试",
                            tint = colorScheme.error,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "加载失败，点击重试",
                            color = colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (!subReplyState.isExpanded && hasMoreThanPreview) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onToggleExpand() }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "展开全部 $replyTotal 条回复",
                            color = colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = "展开",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else if (subReplyState.isExpanded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        if (subReplyState.hasMore && !subReplyState.isLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onLoadMoreSubReplies() }
                                    .padding(vertical = 3.dp)
                            ) {
                                Text(
                                    text = "更多 (${loadedSubReplies.size}/$replyTotal)",
                                    color = colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ExpandMore,
                                    contentDescription = "加载更多",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        } else if (!subReplyState.isLoading) {
                            Text(
                                text = "已全部展开 (${loadedSubReplies.size})",
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onToggleExpand() }
                                .padding(vertical = 3.dp)
                        ) {
                            Text(
                                text = "收起",
                                color = colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Icon(
                                imageVector = Icons.Rounded.ExpandLess,
                                contentDescription = "收起",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        // 评论互动操作条（点赞 / 回复 / 删除自己评论）
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isOwnComment && onDeleteComment != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDeleteComment(comment) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "删除评论",
                        tint = colorScheme.error,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "删除",
                        color = colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = onLikeComment != null) { onLikeComment?.invoke(comment) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (comment.isLiked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt,
                        contentDescription = "点赞评论",
                        tint = if (comment.isLiked) colorScheme.primary else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (comment.likeNum > 0) "${comment.likeNum}" else "赞",
                        color = if (comment.isLiked) colorScheme.primary else colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = onReplyComment != null) {
                            onReplyComment?.invoke(comment.id, comment.username)
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = "回复评论",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (comment.replyNum > 0) "${comment.replyNum}" else "回复",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/**
 * 评论区 / 楼中楼配图预览组件：
 * - 正常模式（noImageMode == false）：加载轻量小方块缩略图（限制解码尺寸且 GIF 仅解首帧，节省手表内存与流量），点击后进入全屏完整加载原图/动图；
 * - 无图省流模式（noImageMode == true）：不预载预览图，仅保留点击查看入口按钮。
 */
@Composable
private fun CommentImagePreview(
    imageUrl: String,
    previewSize: Dp,
    noImageMode: Boolean,
    isSubReply: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isGif = remember(imageUrl) { imageUrl.contains(".gif", ignoreCase = true) }

    if (noImageMode) {
        CompactButton(
            onClick = onClick,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = "图片",
                    modifier = Modifier.size(13.dp)
                )
            },
            label = {
                Text(
                    text = when {
                        isGif && isSubReply -> "查看GIF"
                        isGif -> "查看动图 GIF"
                        isSubReply -> "查看图片"
                        else -> "查看评论配图"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
        return
    }

    val context = LocalContext.current
    val thumbRequest = remember(imageUrl, context) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(160, 160)
            .precision(Precision.INEXACT)
            .decoderFactory(BitmapFactoryDecoder.Factory())
            .crossfade(true)
            .build()
    }

    val shape = RoundedCornerShape(if (isSubReply) 8.dp else 10.dp)
    Box(
        modifier = Modifier
            .size(previewSize)
            .clip(shape)
            .background(colorScheme.surfaceContainerHigh)
            .border(width = 0.75.dp, color = colorScheme.outlineVariant, shape = shape)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = thumbRequest,
            contentDescription = if (isGif) "动图预览，点击查看完整动图" else "评论配图预览，点击查看原图",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (isGif) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "GIF",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyExtraSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
