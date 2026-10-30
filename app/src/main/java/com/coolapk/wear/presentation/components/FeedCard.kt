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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.CardDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.presentation.theme.LocalAppPrimaryColor
import com.coolapk.wear.presentation.theme.WearSurface

@Composable
fun FeedCard(
    feed: FeedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onUserClick: ((String) -> Unit)? = null,
    onTopicClick: ((String) -> Unit)? = null
) {
    val primaryColor = LocalAppPrimaryColor.current
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
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
            // 顶部用户信息
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onUserClick != null && feed.uid.isNotBlank()) {
                            Modifier.clickable { onUserClick(feed.uid) }
                        } else Modifier
                    )
            ) {
                if (feed.userAvatar.isNotBlank()) {
                    AsyncImage(
                        model = feed.userAvatar,
                        contentDescription = feed.username,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = feed.username,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (feed.dateString.isNotBlank()) {
                        Text(
                            text = feed.dateString + if (feed.deviceTitle.isNotBlank()) " · ${feed.deviceTitle}" else "",
                            color = Color(0xFFA0A0A5),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // 真实标题（彻底过滤重复的作者名占位标题如“xxx的动态”）
            val validTitle = remember(feed.title, feed.username) {
                val t = feed.title.trim()
                val u = feed.username.trim()
                if (t.isBlank() || t == u || t == "${u}的动态" || t == "${u}的图文" || t == "${u}的回答" || t.endsWith("的动态") || t == "动态" || t == "图文") {
                    ""
                } else {
                    t
                }
            }
            if (validTitle.isNotBlank()) {
                Text(
                    text = validTitle,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
            }

            // 正文内容
            if (feed.message.isNotBlank()) {
                Text(
                    text = feed.message,
                    color = Color(0xFFE0E0E0),
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            // 提取话题标签并支持直接点击跳转
            val firstTopic = remember(feed.message) {
                val match = Regex("#([^#\\s]{2,16})#").find(feed.message)
                match?.groupValues?.get(1)
            }
            if (firstTopic != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(primaryColor.copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .then(
                            if (onTopicClick != null) {
                                Modifier.clickable { onTopicClick(firstTopic) }
                            } else Modifier
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tag,
                        contentDescription = "话题",
                        tint = primaryColor,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = firstTopic,
                        color = primaryColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 附带图片小角标或首图
            if (feed.picArr.isNotEmpty()) {
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = feed.picArr.first(),
                        contentDescription = "图片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    if (feed.picArr.size > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+${feed.picArr.size} 图",
                            color = Color(0xFFA0A0A5),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // 底部点赞与评论统计
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (feed.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "赞",
                    tint = if (feed.isLiked) Color.Red else Color(0xFFA0A0A5),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (feed.likeNum > 0) feed.likeNum.toString() else "赞",
                    color = Color(0xFFA0A0A5),
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Icon(
                    imageVector = Icons.Rounded.ChatBubbleOutline,
                    contentDescription = "评",
                    tint = Color(0xFFA0A0A5),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (feed.replyNum > 0) feed.replyNum.toString() else "评",
                    color = Color(0xFFA0A0A5),
                    fontSize = 10.sp
                )
            }
        }
    }
}
