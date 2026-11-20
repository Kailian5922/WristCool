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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.coolapk.wear.data.model.CommentItem
import com.coolapk.wear.data.model.SubReplyItem
import com.coolapk.wear.presentation.theme.LocalAppPrimaryColor
import com.coolapk.wear.presentation.theme.WearSurface
import com.coolapk.wear.presentation.theme.WearSurfaceVariant

/**
 * 单条评论的楼中楼展开与分页状态，由 ViewModel 托管，
 * 避免 ScalingLazyColumn 滚出屏幕回收节点后丢失已加载的楼中楼回复。
 */
data class SubReplyUiState(
    val isExpanded: Boolean = false,
    val replies: List<SubReplyItem> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null
)

@Composable
fun CommentCard(
    comment: CommentItem,
    subReplyState: SubReplyUiState,
    onToggleExpand: () -> Unit,
    onLoadMoreSubReplies: () -> Unit,
    onRetrySubReplies: () -> Unit,
    modifier: Modifier = Modifier,
    onUserClick: ((String) -> Unit)? = null,
    onViewImage: ((String) -> Unit)? = null
) {
    val primaryColor = LocalAppPrimaryColor.current
    val loadedSubReplies = subReplyState.replies.ifEmpty { comment.subReplies }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(WearSurface)
            .padding(9.dp)
    ) {
        // 用户头像、昵称（长昵称自动截断不挤掉角标与时间）与时间栏
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
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (comment.isAuthor) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(primaryColor.copy(alpha = 0.2f))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "楼主",
                            color = primaryColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                if (comment.isTop) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFFF5722).copy(alpha = 0.2f))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "置顶",
                            color = Color(0xFFFF5722),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (comment.dateString.isNotBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = comment.dateString,
                    color = Color(0xFFA0A0A5),
                    fontSize = 9.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 评论正文
        if (comment.message.isNotBlank()) {
            Text(
                text = comment.message,
                color = Color(0xFFEEEEEE),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        // 按需加载评论配图/GIF
        if (comment.pic.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            val isGif = comment.pic.contains(".gif", ignoreCase = true)
            CompactChip(
                onClick = { onViewImage?.invoke(comment.pic) },
                label = { Text(if (isGif) "查看动态GIF" else "查看评论配图", fontSize = 10.sp) },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = "图片",
                        tint = primaryColor,
                        modifier = Modifier.size(13.dp)
                    )
                },
                colors = ChipDefaults.chipColors(backgroundColor = WearSurfaceVariant)
            )
        }

        // 楼中楼嵌套回复区
        val replyTotal = maxOf(comment.subReplies.size, comment.replyNum, loadedSubReplies.size)
        if (replyTotal > 0 || loadedSubReplies.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222225))
                    .padding(6.dp)
            ) {
                val hasMoreThanPreview = replyTotal > 3 || loadedSubReplies.size > 3 || comment.replyNum > loadedSubReplies.size
                val displayReplies = if (subReplyState.isExpanded || !hasMoreThanPreview) {
                    loadedSubReplies
                } else {
                    loadedSubReplies.take(3)
                }

                displayReplies.forEach { sub ->
                    val subText = buildAnnotatedString {
                        withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)) {
                            append(sub.username)
                        }
                        if (sub.isAuthor) {
                            withStyle(SpanStyle(color = primaryColor, fontSize = 9.sp)) {
                                append(" [楼主]")
                            }
                        }
                        if (sub.replyUsername.isNotBlank()) {
                            withStyle(SpanStyle(color = Color(0xFFA0A0A5))) {
                                append(" 回复 ")
                            }
                            withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)) {
                                append(sub.replyUsername)
                            }
                        }
                        if (sub.message.isNotBlank()) {
                            withStyle(SpanStyle(color = Color(0xFFCCCCCC))) {
                                append(": ${sub.message}")
                            }
                        }
                    }

                    Text(
                        text = subText,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )

                    if (sub.pic.isNotBlank()) {
                        val isSubGif = sub.pic.contains(".gif", ignoreCase = true)
                        CompactChip(
                            onClick = { onViewImage?.invoke(sub.pic) },
                            label = { Text(if (isSubGif) "查看GIF" else "查看图片", fontSize = 9.sp) },
                            colors = ChipDefaults.chipColors(backgroundColor = WearSurfaceVariant),
                            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                        )
                    }
                }

                if (subReplyState.isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(
                            indicatorColor = primaryColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "正在加载楼中楼回复...",
                            color = primaryColor,
                            fontSize = 10.sp
                        )
                    }
                }

                if (subReplyState.error != null && !subReplyState.isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onRetrySubReplies() }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "重试",
                            tint = Color(0xFFFF8A65),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "加载失败，点击重试",
                            color = Color(0xFFFF8A65),
                            fontSize = 10.sp
                        )
                    }
                }

                if (!subReplyState.isExpanded && hasMoreThanPreview) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onToggleExpand() }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        Text(
                            text = "共 $replyTotal 条回复 · 点击展开全部",
                            color = primaryColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = "展开",
                            tint = primaryColor,
                            modifier = Modifier.size(13.dp)
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
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onLoadMoreSubReplies() }
                                    .padding(vertical = 3.dp, horizontal = 2.dp)
                            ) {
                                Text(
                                    text = "加载更多 (${loadedSubReplies.size}/$replyTotal)",
                                    color = primaryColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Rounded.ExpandMore,
                                    contentDescription = "加载更多",
                                    tint = primaryColor,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        } else if (!subReplyState.isLoading) {
                            Text(
                                text = "已展示全部 ${loadedSubReplies.size} 条回复",
                                color = Color(0xFFA0A0A5),
                                fontSize = 9.sp
                            )
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onToggleExpand() }
                                .padding(vertical = 3.dp, horizontal = 2.dp)
                        ) {
                            Text(
                                text = "收起",
                                color = primaryColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Rounded.ExpandLess,
                                contentDescription = "收起",
                                tint = primaryColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        if (comment.likeNum > 0 || comment.replyNum > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (comment.likeNum > 0) {
                    Icon(
                        imageVector = if (comment.isLiked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt,
                        contentDescription = "赞",
                        tint = if (comment.isLiked) primaryColor else Color(0xFFA0A0A5),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${comment.likeNum}",
                        color = Color(0xFFA0A0A5),
                        fontSize = 10.sp
                    )
                    if (comment.replyNum > 0) {
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                }

                if (comment.replyNum > 0) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = "回复数",
                        tint = Color(0xFFA0A0A5),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${comment.replyNum}",
                        color = Color(0xFFA0A0A5),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
