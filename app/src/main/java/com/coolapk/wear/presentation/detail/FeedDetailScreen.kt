package com.coolapk.wear.presentation.detail

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.coolapk.wear.data.util.ImageSaver
import com.coolapk.wear.data.util.QrCodeGenerator
import com.coolapk.wear.presentation.components.CommentCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.WearListContentPadding
import com.coolapk.wear.presentation.components.rotaryEventHandler
import com.coolapk.wear.presentation.theme.LocalAppPrimaryColor
import com.coolapk.wear.presentation.theme.WearSurface
import com.coolapk.wear.presentation.theme.WearSurfaceVariant
import kotlinx.coroutines.launch

@Composable
fun FeedDetailScreen(
    viewModel: FeedDetailViewModel,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit = {},
    onTopicClick: (String) -> Unit = {}
) {
    val primaryColor = LocalAppPrimaryColor.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var previewScale by remember { mutableStateOf(1f) }
    var previewOffset by remember { mutableStateOf(Offset.Zero) }
    var showDownloadDialog by remember { mutableStateOf(false) }

    // 重置图片缩放
    LaunchedEffect(previewImageUrl) {
        previewScale = 1f
        previewOffset = Offset.Zero
    }

    // 评论弹窗状态
    var showReplyDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }

    // 语音输入 Launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                replyText = if (replyText.isBlank()) spoken else "$replyText $spoken"
            }
        }
    }

    val quickPhrases = remember {
        listOf("沙发！", "学到了 👍", "牛哇牛哇", "前排围观", "好图已收", "支持支持", "插眼", "太强了")
    }

    val topicTags = remember(uiState.feed?.message) {
        val msg = uiState.feed?.message ?: ""
        val regex = Regex("#([^#\\s]{2,20})#")
        regex.findAll(msg).map { it.groupValues[1] }.distinct().toList()
    }

    // 触底自动加载更多评论
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                val totalItems = listState.layoutInfo.totalItemsCount
                if (lastVisibleIndex != null && totalItems > 0 && lastVisibleIndex >= totalItems - 2) {
                    if (uiState.hasMoreComments && !uiState.isLoadingMoreComments) {
                        viewModel.loadMoreComments()
                    }
                }
            }
    }

    HorologistScaffold(listState = listState) {
        ScalingLazyColumn(
            state = listState,
            contentPadding = WearListContentPadding,
            anchorType = ScalingLazyListAnchorType.ItemStart,
            modifier = Modifier
                .fillMaxSize()
                .rotaryEventHandler(listState)
        ) {
            // 加载中
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            indicatorColor = primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 错误重试
            if (uiState.error != null) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = uiState.error ?: "",
                            color = Color(0xFFA0A0A5),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.loadDetail() },
                            colors = ButtonDefaults.buttonColors(backgroundColor = primaryColor),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "重试",
                                tint = Color.White
                            )
                        }
                    }
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
                                } else Modifier
                            )
                    ) {
                        if (feed.userAvatar.isNotBlank()) {
                            AsyncImage(
                                model = feed.userAvatar,
                                contentDescription = feed.username,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(primaryColor)
                            )
                        }

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

                // 真实标题（彻底过滤重复的作者名占位标题）
                val validTitle = feed.title.trim().let { t ->
                    val u = feed.username.trim()
                    if (t.isBlank() || t == u || t == "${u}的动态" || t == "${u}的图文" || t == "${u}的回答" || t.endsWith("的动态") || t == "动态" || t == "图文") "" else t
                }
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

                // 附带图片紧凑网格展示（对齐酷安手机端：1图自适应，2/4图双列，3/6/9图三列，消除臃肿长距）
                if (feed.picArr.isNotEmpty()) {
                    item {
                        val picArr = feed.picArr
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (picArr.size == 1) {
                                val picUrl = picArr[0]
                                AsyncImage(
                                    model = picUrl,
                                    contentDescription = "配图",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 160.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .pointerInput(picUrl) {
                                            detectTapGestures(
                                                onTap = { previewImageUrl = picUrl },
                                                onLongPress = {
                                                    previewImageUrl = picUrl
                                                    showDownloadDialog = true
                                                }
                                            )
                                        }
                                )
                            } else if (picArr.size == 2 || picArr.size == 4) {
                                val rowCount = if (picArr.size == 2) 1 else 2
                                for (r in 0 until rowCount) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        for (c in 0 until 2) {
                                            val idx = r * 2 + c
                                            if (idx < picArr.size) {
                                                val picUrl = picArr[idx]
                                                AsyncImage(
                                                    model = picUrl,
                                                    contentDescription = "配图",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .pointerInput(picUrl) {
                                                            detectTapGestures(
                                                                onTap = { previewImageUrl = picUrl },
                                                                onLongPress = {
                                                                    previewImageUrl = picUrl
                                                                    showDownloadDialog = true
                                                                }
                                                            )
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // 3, 5, 6, 7, 8, 9 采用三列九宫格紧密排布
                                val rowCount = (picArr.size + 2) / 3
                                for (r in 0 until rowCount) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        for (c in 0 until 3) {
                                            val idx = r * 3 + c
                                            if (idx < picArr.size) {
                                                val picUrl = picArr[idx]
                                                AsyncImage(
                                                    model = picUrl,
                                                    contentDescription = "配图",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .pointerInput(picUrl) {
                                                            detectTapGestures(
                                                                onTap = { previewImageUrl = picUrl },
                                                                onLongPress = {
                                                                    previewImageUrl = picUrl
                                                                    showDownloadDialog = true
                                                                }
                                                            )
                                                        }
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 紧凑整合区：话题胶囊 + 交互操作栏 + 手机扫码查看（无断层空白）
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 提取正文中的话题标签
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

                        // 交互操作栏：点赞、收藏、评论 (单行不换行，紧凑精致)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 点赞
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (uiState.isLiked) Color(0xFF3A1C1C) else WearSurface)
                                    .clickable { viewModel.toggleLike() }
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

                            // 收藏
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (uiState.isFavorite) Color(0xFF332B14) else WearSurface)
                                    .clickable { viewModel.toggleFavorite() }
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

                            // 回复
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

                        // 在手机上扫码查看二维码入口 (紧凑贴合下方)
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

                // 评论区标题栏与“写回复”
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "评论 (${uiState.comments.size})",
                            color = primaryColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        CompactChip(
                            onClick = {
                                replyText = ""
                                showReplyDialog = true
                            },
                            label = { Text("写回复", fontSize = 10.sp) },
                            colors = ChipDefaults.chipColors(backgroundColor = primaryColor)
                        )
                    }
                }

                // 评论排序栏（对齐酷安手机端：默认、热门、最新、楼主）
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
                        CommentSort.values().forEach { sort ->
                            val isSelected = uiState.commentSort == sort
                            CompactChip(
                                onClick = { viewModel.setCommentSort(sort) },
                                label = {
                                    Text(
                                        text = sort.label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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

                if (uiState.comments.isEmpty() && !uiState.isLoading) {
                    item {
                        Text(
                            text = "暂无评论，点击上方“写回复”抢沙发吧",
                            color = Color(0xFF777777),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }

                // 评论列表项（配图按需加载，点击查看大图；支持原地展开加载完整楼中楼全部回复）
                itemsIndexed(uiState.comments, key = { index, comment -> "${comment.id}_$index" }) { _, comment ->
                    CommentCard(
                        comment = comment,
                        onUserClick = onUserClick,
                        onViewImage = { imgUrl ->
                            previewImageUrl = imgUrl
                        },
                        onLoadSubReplies = { commentId, page ->
                            viewModel.getCommentSubReplies(commentId, page)
                        }
                    )
                }

                // 下拉触底或点击查看更多评论
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

        // 大图全屏预览模式：已移除底部常驻提示，支持双指缩放、双击放大切换、长按弹窗询问保存
        if (previewImageUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(previewImageUrl) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            previewScale = (previewScale * zoom).coerceIn(1f, 4f)
                            if (previewScale > 1f) {
                                previewOffset = Offset(
                                    x = previewOffset.x + pan.x,
                                    y = previewOffset.y + pan.y
                                )
                            } else {
                                previewOffset = Offset.Zero
                            }
                        }
                    }
                    .pointerInput(previewImageUrl) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (previewScale > 1f) {
                                    previewScale = 1f
                                    previewOffset = Offset.Zero
                                } else {
                                    previewScale = 2.5f
                                }
                            },
                            onTap = {
                                if (previewScale > 1f) {
                                    previewScale = 1f
                                    previewOffset = Offset.Zero
                                } else {
                                    previewImageUrl = null
                                }
                            },
                            onLongPress = {
                                showDownloadDialog = true
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = previewImageUrl,
                    contentDescription = "大图预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = previewScale
                            scaleY = previewScale
                            translationX = previewOffset.x
                            translationY = previewOffset.y
                        }
                ) {
                    val state = painter.state
                    when (state) {
                        is coil.compose.AsyncImagePainter.State.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    indicatorColor = primaryColor,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        is coil.compose.AsyncImagePainter.State.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BrokenImage,
                                    contentDescription = "加载失败",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "图片加载失败",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "点击屏幕任意处关闭",
                                    color = Color(0xFFA0A0A5),
                                    fontSize = 9.sp
                                )
                            }
                        }
                        else -> {
                            SubcomposeAsyncImageContent()
                        }
                    }
                }

                // 顶部半透明关闭按钮
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    CompactButton(
                        onClick = { previewImageUrl = null },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Black.copy(alpha = 0.5f)),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // 长按弹窗确认是否下载保存图片
        if (showDownloadDialog) {
            Dialog(
                showDialog = showDownloadDialog,
                onDismissRequest = { showDownloadDialog = false }
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
                                showDownloadDialog = false
                                previewImageUrl?.let { url ->
                                    coroutineScope.launch {
                                        ImageSaver.saveImageToGallery(context, url)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = primaryColor)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = "确认保存", tint = Color.White)
                        }
                    },
                    negativeButton = {
                        Button(
                            onClick = { showDownloadDialog = false },
                            colors = ButtonDefaults.buttonColors(backgroundColor = WearSurfaceVariant)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "取消", tint = Color.White)
                        }
                    }
                )
            }
        }

        // 发表评论/回复弹层
        if (showReplyDialog) {
            val replyListState = rememberScalingLazyListState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f))
            ) {
                ScalingLazyColumn(
                    state = replyListState,
                    contentPadding = WearListContentPadding,
                    anchorType = ScalingLazyListAnchorType.ItemStart,
                    modifier = Modifier
                        .fillMaxSize()
                        .rotaryEventHandler(replyListState)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "回复评论",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            CompactButton(
                                onClick = { showReplyDialog = false },
                                colors = ButtonDefaults.buttonColors(backgroundColor = WearSurfaceVariant),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "关闭",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // 输入框与语音输入
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(WearSurface)
                                    .padding(8.dp)
                            ) {
                                BasicTextField(
                                    value = replyText,
                                    onValueChange = { replyText = it },
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                    cursorBrush = SolidColor(primaryColor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    decorationBox = { innerTextField ->
                                        if (replyText.isEmpty()) {
                                            Text(
                                                text = "点击输入评论...",
                                                color = Color(0xFF888888),
                                                fontSize = 12.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                CompactButton(
                                    onClick = {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                             putExtra(
                                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                            )
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "说出你想回复的内容...")
                                        }
                                        try {
                                            speechLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "未找到语音输入服务", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(backgroundColor = WearSurfaceVariant),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = "语音输入",
                                        tint = primaryColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 常用快捷短语
                    item {
                        Text(
                            text = "快捷短语：",
                            color = Color(0xFFA0A0A5),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }

                    items(quickPhrases) { phrase ->
                        CompactChip(
                            onClick = {
                                replyText = if (replyText.isBlank()) phrase else "$replyText $phrase"
                            },
                            label = { Text(phrase, fontSize = 11.sp) },
                            colors = ChipDefaults.chipColors(backgroundColor = WearSurfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                        )
                    }

                    // 发送回复按钮
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Chip(
                            onClick = {
                                viewModel.submitReply(replyText) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    if (success) {
                                        showReplyDialog = false
                                        replyText = ""
                                    }
                                }
                            },
                            enabled = !uiState.isSubmittingReply && replyText.isNotBlank(),
                            label = {
                                Text(
                                    text = if (uiState.isSubmittingReply) "发送中..." else "发送回复",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            icon = {
                                if (uiState.isSubmittingReply) {
                                    CircularProgressIndicator(
                                        indicatorColor = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "发送",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            },
                            colors = ChipDefaults.chipColors(backgroundColor = primaryColor),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // 手机扫码查看二维码弹窗
        val currentFeed = uiState.feed
        if (showQrDialog && currentFeed != null) {
            val feedUrl = "https://www.coolapk.com/feed/${currentFeed.id}"
            val qrBitmap = remember(feedUrl) {
                QrCodeGenerator.generate(feedUrl, 320)
            }
            val qrListState = rememberScalingLazyListState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f))
            ) {
                ScalingLazyColumn(
                    state = qrListState,
                    contentPadding = WearListContentPadding,
                    anchorType = ScalingLazyListAnchorType.ItemStart,
                    modifier = Modifier
                        .fillMaxSize()
                        .rotaryEventHandler(qrListState)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "手机扫码查看",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            CompactButton(
                                onClick = { showQrDialog = false },
                                colors = ButtonDefaults.buttonColors(backgroundColor = WearSurfaceVariant),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "关闭",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            if (qrBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .size(130.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White)
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "二维码",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "手机微信/浏览器扫一扫\n直接在手机打开该动态",
                                color = Color(0xFFA0A0A5),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = feedUrl,
                                color = Color(0xFF666666),
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
