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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.CardDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.coolapk.wear.data.model.CommentItem
import com.coolapk.wear.data.model.SubReplyItem
import com.coolapk.wear.presentation.theme.LocalAppPrimaryColor
import com.coolapk.wear.presentation.theme.WearSurface
import com.coolapk.wear.presentation.theme.WearSurfaceVariant
import kotlinx.coroutines.launch

@Composable
fun CommentCard(
    comment: CommentItem,
    modifier: Modifier = Modifier,
    onUserClick: ((String) -> Unit)? = null,
    onViewImage: ((String) -> Unit)? = null,
    onLoadSubReplies: (suspend (String, Int) -> List<SubReplyItem>)? = null
) {
    val primaryColor = LocalAppPrimaryColor.current
    val coroutineScope = rememberCoroutineScope()
    var isExpanded by remember(comment.id) { mutableStateOf(false) }
    var loadedSubReplies by remember(comment.id) { mutableStateOf<List<SubReplyItem>>(comment.subReplies) }
    var isLoadingSubReplies by remember(comment.id) { mutableStateOf(false) }
    var subReplyPage by remember(comment.id) { mutableStateOf(1) }
    var hasMoreSubReplies by remember(comment.id) { mutableStateOf(true) }

    LaunchedEffect(comment.subReplies) {
        if (loadedSubReplies.isEmpty()) {
            loadedSubReplies = comment.subReplies
        }
    }

    fun triggerExpand() {
        isExpanded = true
        // 若总回复数大于当前已展示数，且支持网络拉取，则自动加载第 1 页楼中楼全量回复
        if (onLoadSubReplies != null && (comment.replyNum > loadedSubReplies.size || loadedSubReplies.size <= 4) && !isLoadingSubReplies) {
            coroutineScope.launch {
                isLoadingSubReplies = true
                val list = onLoadSubReplies.invoke(comment.id, 1)
                if (list.isNotEmpty()) {
                    loadedSubReplies = (loadedSubReplies + list).distinctBy { it.id }
                    subReplyPage = 1
                    hasMoreSubReplies = list.size >= 10
                } else {
                    hasMoreSubReplies = false
                }
                isLoadingSubReplies = false
            }
        }
    }

    fun triggerLoadMore() {
        if (onLoadSubReplies != null && !isLoadingSubReplies && hasMoreSubReplies) {
            coroutineScope.launch {
                isLoadingSubReplies = true
                val nextP = subReplyPage + 1
                val moreList = onLoadSubReplies.invoke(comment.id, nextP)
                if (moreList.isNotEmpty()) {
                    loadedSubReplies = (loadedSubReplies + moreList).distinctBy { it.id }
                    subReplyPage = nextP
                    hasMoreSubReplies = moreList.size >= 10
                } else {
                    hasMoreSubReplies = false
                }
                isLoadingSubReplies = false
            }
        }
    }
    Card(
        onClick = {},
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = WearSurface,
            endBackgroundColor = WearSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // 用户头像与昵称栏
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onUserClick != null && comment.uid.isNotBlank()) {
                            Modifier.clickable { onUserClick(comment.uid) }
                        } else Modifier
                    )
            ) {
                if (comment.userAvatar.isNotBlank()) {
                    AsyncImage(
                        model = comment.userAvatar,
                        contentDescription = comment.username,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = comment.username,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 楼主角标
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
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 置顶角标
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
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (comment.dateString.isNotBlank()) {
                    Text(
                        text = comment.dateString,
                        color = Color(0xFFA0A0A5),
                        fontSize = 9.sp
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

            // 按需加载图片按钮：用户点击时才加载显示，保障手表流畅度与省流量
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

            // 楼中楼嵌套回复列表（支持网络拉取并看完全部几十条甚至上百条回复）
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
                    val hasMoreThan3 = replyTotal > 3 || loadedSubReplies.size > 3
                    val displayReplies = if (isExpanded || !hasMoreThan3) {
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
                            withStyle(SpanStyle(color = Color(0xFFCCCCCC))) {
                                append(": ${sub.message}")
                            }
                        }

                        Text(
                            text = subText,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )

                        // 楼中楼附带图片按需查看
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

                    // 正在网络加载更多/全量楼中楼
                    if (isLoadingSubReplies) {
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

                    if (!isExpanded && hasMoreThan3) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { triggerExpand() }
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
                    } else if (isExpanded) {
                        // 展开状态下操作条（支持看完全部数十/数百条）
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            if (hasMoreSubReplies && loadedSubReplies.size < replyTotal) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { triggerLoadMore() }
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
                            } else {
                                Text(
                                    text = "已展示全部 ${loadedSubReplies.size} 条回复",
                                    color = Color(0xFFA0A0A5),
                                    fontSize = 9.sp
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { isExpanded = false }
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

            Spacer(modifier = Modifier.height(4.dp))

            // 底部点赞与回复数
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
                    Spacer(modifier = Modifier.width(10.dp))
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
