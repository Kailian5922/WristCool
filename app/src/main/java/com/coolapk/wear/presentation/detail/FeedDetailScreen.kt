package com.coolapk.wear.presentation.detail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import com.coolapk.wear.data.util.ImageSaver
import com.coolapk.wear.data.util.TextSanitizer
import com.coolapk.wear.presentation.components.CommentCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.LoadMoreEffect
import com.coolapk.wear.presentation.components.RotaryScrollLock
import com.coolapk.wear.presentation.components.SubReplyUiState
import com.coolapk.wear.presentation.components.UserAvatar
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearListContentPadding
import com.coolapk.wear.presentation.components.WearLoadingPlaceholder
import com.coolapk.wear.presentation.components.rotaryEventHandler
import com.coolapk.wear.presentation.theme.LocalAppOnPrimaryColor
import com.coolapk.wear.presentation.theme.LocalAppPrimaryColor
import com.coolapk.wear.presentation.theme.WearSurface
import com.coolapk.wear.presentation.theme.WearSurfaceVariant

@Composable
fun FeedDetailScreen(
    viewModel: FeedDetailViewModel,
    imageSaver: ImageSaver,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit = {},
    onTopicClick: (String) -> Unit = {}
) {
    val primaryColor = LocalAppPrimaryColor.current
    val onPrimaryColor = LocalAppOnPrimaryColor.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    val context = LocalContext.current

    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var pendingDownloadImageUrl by remember { mutableStateOf<String?>(null) }
    var showReplyDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }

    // 统一展示点赞/收藏/评论操作反馈
    LaunchedEffect(uiState.actionMessage) {
        val msg = uiState.actionMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearActionMessage()
        }
    }

    val topicTags = remember(uiState.feed?.message) {
        TextSanitizer.extractTopics(uiState.feed?.message.orEmpty(), maxLength = 20)
    }

    LoadMoreEffect(
        listState = listState,
        itemCount = uiState.comments.size,
        hasMore = uiState.hasMoreComments,
        isLoading = uiState.isLoading || uiState.isLoadingComments || uiState.isLoadingMoreComments,
        onLoadMore = { viewModel.loadMoreComments() }
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
            if (uiState.isLoading) {
                item { WearLoadingPlaceholder(height = 140.dp) }
            }

            if (uiState.error != null) {
                item {
                    WearErrorRetry(
                        message = uiState.error.orEmpty(),
                        onRetry = { viewModel.loadDetail() }
                    )
                }
            }

            val feed = uiState.feed
            if (feed != null) {
                // 作者信息
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .then(
                                if (feed.uid.isNotBlank()) {
                                    Modifier.clickable { onUserClick(feed.uid) }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        UserAvatar(
                            avatarUrl = feed.userAvatar,
                            contentDescription = feed.username,
                            size = 28.dp
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Text(
                                text = feed.username,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (feed.dateString.isNotBlank()) {
                                Text(
                                    text = feed.dateString + if (feed.deviceTitle.isNotBlank()) " · ${feed.deviceTitle}" else "",
                                    color = Color(0xFFA0A0A5),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                // 真实标题
                val validTitle = TextSanitizer.filterAutoTitle(feed.title, feed.username)
                if (validTitle.isNotBlank()) {
                    item {
                        Text(
                            text = validTitle,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                }

                // 完整正文
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(WearSurface)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = feed.message,
                            color = Color(0xFFEEEEEE),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                // 自适应多图九宫格
                if (feed.picArr.isNotEmpty()) {
                    item {
                        FeedImageGrid(
                            picArr = feed.picArr,
                            onImageTap = { url -> previewImageUrl = url },
                            onImageLongPress = { url -> pendingDownloadImageUrl = url }
                        )
                    }
                }

                // 话题胶囊 + 操作栏 + 手机扫码查看
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (topicTags.isNotEmpty()) {
                            val topicScrollState = rememberScrollState()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(topicScrollState),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                topicTags.forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(primaryColor.copy(alpha = 0.15f))
                                            .clickable { onTopicClick(tag) }
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.Tag,
                                                contentDescription = tag,
                                                tint = primaryColor,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = tag,
                                                color = primaryColor,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 交互操作栏：点赞、收藏、回复
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (uiState.isLiked) Color(0xFF3A1C1C) else WearSurface)
                                    .clickable(enabled = !uiState.isTogglingLike) { viewModel.toggleLike() }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (uiState.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "赞",
                                        tint = if (uiState.isLiked) Color.Red else Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${uiState.likeCount}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (uiState.isFavorite) Color(0xFF332B14) else WearSurface)
                                    .clickable(enabled = !uiState.isTogglingFavorite) { viewModel.toggleFavorite() }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (uiState.isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                        contentDescription = "收藏",
                                        tint = if (uiState.isFavorite) Color(0xFFFFB300) else Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (uiState.isFavorite) "已收藏" else "收藏",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(WearSurface)
                                    .clickable {
                                        replyText = ""
                                        showReplyDialog = true
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChatBubbleOutline,
                                        contentDescription = "回复",
                                        tint = primaryColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "回复",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // 手机扫码查看
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(WearSurface)
                                .clickable { showQrDialog = true }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCode2,
                                    contentDescription = "手机查看",
                                    tint = primaryColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "在手机上查看 (扫码)",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // 评论区标题栏与写回复
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val displayReplyCount = maxOf(feed.replyNum, uiState.comments.size)
                        Text(
                            text = "评论 ($displayReplyCount)",
                            color = primaryColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        CompactChip(
                            onClick = {
                                replyText = ""
                                showReplyDialog = true
                            },
                            label = {
                                Text(
                                    text = "写回复",
                                    fontSize = 10.sp,
                                    color = onPrimaryColor
                                )
                            },
                            colors = ChipDefaults.chipColors(backgroundColor = primaryColor)
                        )
                    }
                }

                // 评论排序栏
                item {
                    val sortScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(sortScrollState)
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CommentSort.entries.forEach { sort ->
                            val isSelected = uiState.commentSort == sort
                            CompactChip(
                                onClick = { viewModel.setCommentSort(sort) },
                                label = {
                                    Text(
                                        text = sort.label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) onPrimaryColor else Color.White
                                    )
                                },
                                colors = ChipDefaults.chipColors(
                                    backgroundColor = if (isSelected) primaryColor else WearSurfaceVariant
                                ),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }

                if (uiState.isLoadingComments && uiState.comments.isEmpty()) {
                    item { WearLoadingPlaceholder(height = 60.dp, indicatorSize = 18.dp) }
                } else if (uiState.comments.isEmpty() && !uiState.isLoading) {
                    item { WearEmptyState("暂无评论，点击上方“写回复”抢沙发吧") }
                }

                // 评论列表（楼中楼状态托管于 ViewModel，滚出屏幕不丢失）
                itemsIndexed(uiState.comments, key = { index, comment -> "${comment.id}_$index" }) { _, comment ->
                    val subState = uiState.subReplyStates[comment.id] ?: SubReplyUiState(replies = comment.subReplies)
                    CommentCard(
                        comment = comment,
                        subReplyState = subState,
                        onToggleExpand = { viewModel.toggleSubReplies(comment) },
                        onLoadMoreSubReplies = { viewModel.loadMoreSubReplies(comment) },
                        onRetrySubReplies = { viewModel.retrySubReplies(comment) },
                        onUserClick = onUserClick,
                        onViewImage = { imgUrl -> previewImageUrl = imgUrl }
                    )
                }

                // 评论分页加载底部
                if (uiState.isLoadingMoreComments) {
                    item {
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
                    }
                } else if (uiState.hasMoreComments && uiState.comments.isNotEmpty()) {
                    item {
                        CompactChip(
                            onClick = { viewModel.loadMoreComments() },
                            label = { Text("查看更多评论", fontSize = 11.sp) },
                            colors = ChipDefaults.chipColors(backgroundColor = WearSurfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                } else if (uiState.comments.isNotEmpty()) {
                    item {
                        Text(
                            text = "已显示全部评论",
                            color = Color(0xFF666666),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        )
                    }
                }
            }
        }

        // 全屏大图预览浮层
        previewImageUrl?.let { url ->
            FullscreenImageViewer(
                imageUrl = url,
                onDismiss = { previewImageUrl = null },
                onRequestSave = { saveUrl -> pendingDownloadImageUrl = saveUrl }
            )
        }

        // 长按保存图片确认弹窗（独立于预览状态，取消不会意外停留在全屏大图）
        pendingDownloadImageUrl?.let { downloadUrl ->
            RotaryScrollLock()
            Dialog(
                showDialog = true,
                onDismissRequest = { pendingDownloadImageUrl = null }
            ) {
                Alert(
                    title = {
                        Text(
                            text = "下载图片",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    },
                    content = {
                        Text(
                            text = "是否将此图片保存至手表相册？",
                            color = Color(0xFFA0A0A5),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    },
                    positiveButton = {
                        Button(
                            onClick = {
                                pendingDownloadImageUrl = null
                                imageSaver.saveImageToGalleryAsync(downloadUrl)
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = primaryColor)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = "确认保存", tint = onPrimaryColor)
                        }
                    },
                    negativeButton = {
                        Button(
                            onClick = { pendingDownloadImageUrl = null },
                            colors = ButtonDefaults.buttonColors(backgroundColor = WearSurfaceVariant)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "取消", tint = Color.White)
                        }
                    }
                )
            }
        }

        // 写评论浮层
        if (showReplyDialog) {
            ReplyOverlay(
                replyText = replyText,
                onReplyTextChange = { replyText = it },
                isSubmitting = uiState.isSubmittingReply,
                onSubmit = {
                    viewModel.submitReply(replyText) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) {
                            showReplyDialog = false
                            replyText = ""
                        }
                    }
                },
                onDismiss = { showReplyDialog = false }
            )
        }

        // 手机扫码查看浮层
        val currentFeed = uiState.feed
        if (showQrDialog && currentFeed != null) {
            QrCodeOverlay(
                feedUrl = "https://www.coolapk.com/feed/${currentFeed.id}",
                onDismiss = { showQrDialog = false }
            )
        }
    }
}
