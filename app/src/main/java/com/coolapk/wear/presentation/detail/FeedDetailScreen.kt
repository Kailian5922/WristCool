package com.coolapk.wear.presentation.detail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Smartphone
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.IconToggleButton
import androidx.wear.compose.material3.IconToggleButtonDefaults
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil.compose.AsyncImage
import com.coolapk.wear.data.model.CommentItem
import com.coolapk.wear.data.model.VoteInfo
import com.coolapk.wear.data.util.ImageSaver
import com.coolapk.wear.data.util.TextSanitizer
import com.coolapk.wear.presentation.components.CommentCard
import com.coolapk.wear.presentation.components.ExpressiveLoadingIndicator
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.LoadMoreEffect
import com.coolapk.wear.presentation.components.SubReplyUiState
import com.coolapk.wear.presentation.components.UserAvatar
import com.coolapk.wear.presentation.components.WearCenteredLoadingOverlay
import com.coolapk.wear.presentation.components.WearConfirmDialog
import com.coolapk.wear.presentation.components.WearEdgeActionButton
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearLoadingPlaceholder
import com.coolapk.wear.presentation.components.WearPillBadge
import com.coolapk.wear.presentation.components.rotaryEventHandler
import com.coolapk.wear.presentation.theme.LocalWatchSettings

private data class GalleryViewerState(
    val urls: List<String>,
    val initialIndex: Int
)

private data class ReplyTarget(
    val replyId: String? = null,
    val username: String = ""
)

@Composable
fun FeedDetailScreen(
    viewModel: FeedDetailViewModel,
    imageSaver: ImageSaver,
    onBack: () -> Unit,
    onFeedClick: (String) -> Unit = {},
    onUserClick: (String) -> Unit = {},
    onTopicClick: (String) -> Unit = {},
    onProductClick: (String) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val settings = LocalWatchSettings.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val context = LocalContext.current

    var galleryState by remember { mutableStateOf<GalleryViewerState?>(null) }
    var pendingDownloadImageUrl by remember { mutableStateOf<String?>(null) }
    var activeReplyTarget by remember { mutableStateOf<ReplyTarget?>(null) }
    var showForwardOverlay by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showDeleteFeedConfirm by remember { mutableStateOf(false) }
    var pendingDeleteComment by remember { mutableStateOf<CommentItem?>(null) }
    var showBlockAuthorConfirm by remember { mutableStateOf(false) }
    var composerText by remember { mutableStateOf("") }
    var forceShowImages by remember { mutableStateOf(false) }

    val hasAnyOverlay = galleryState != null ||
        activeReplyTarget != null ||
        showForwardOverlay ||
        showQrDialog ||
        pendingDownloadImageUrl != null ||
        showDeleteFeedConfirm ||
        pendingDeleteComment != null ||
        showBlockAuthorConfirm

    LaunchedEffect(uiState.actionMessage) {
        val msg = uiState.actionMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearActionMessage()
        }
    }

    val topicTags = remember(uiState.feed?.message, uiState.feed?.relationTopics) {
        val extracted = TextSanitizer.extractTopics(uiState.feed?.message.orEmpty(), maxLength = 20)
        (uiState.feed?.relationTopics.orEmpty() + extracted).distinct()
    }

    LoadMoreEffect(
        listState = listState,
        itemCount = uiState.comments.size,
        hasMore = uiState.hasMoreComments,
        isLoading = uiState.isLoading || uiState.isLoadingComments || uiState.isLoadingMoreComments,
        onLoadMore = { viewModel.loadMoreComments() }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        HorologistScaffold(
            scrollState = listState,
            edgeButton = {
                if (uiState.feed != null) {
                    WearEdgeActionButton(
                        scrollState = listState,
                        buttonSize = EdgeButtonSize.ExtraSmall,
                        onClick = {
                            composerText = ""
                            activeReplyTarget = ReplyTarget()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubbleOutline,
                            contentDescription = "写回复",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("写回复", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        ) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryEventHandler(listState, enabled = !hasAnyOverlay)
            ) {
                if (uiState.error != null) {
                    item(key = "detail_error") {
                        val detailChallenge = uiState.detailCaptcha
                        if (detailChallenge != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .background(colorScheme.surfaceContainer)
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = buildString {
                                        append(detailChallenge.message)
                                        if (detailChallenge.messageStatus.isNotBlank()) {
                                            append(" (")
                                            append(detailChallenge.messageStatus)
                                            append(")")
                                        }
                                    },
                                    color = colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "手表无浏览器组件无法滑块，可扫码在手机端查看或稍后重试",
                                    color = colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyExtraSmall,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                FilledTonalButton(
                                    onClick = { showQrDialog = true },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Rounded.QrCode2,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    },
                                    label = { Text("手机扫码查看", maxLines = 1) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                CompactButton(
                                    onClick = { viewModel.loadDetail() },
                                    label = { Text("重试", maxLines = 1) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            WearErrorRetry(
                                message = uiState.error.orEmpty(),
                                onRetry = { viewModel.loadDetail() }
                            )
                        }
                    }
                }

                val feed = uiState.feed
                if (feed != null) {
                    val isOwnFeed = uiState.currentUid.isNotBlank() && feed.uid == uiState.currentUid

                    // 作者头部
                    item(key = "detail_author") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .clip(MaterialTheme.shapes.medium)
                                .then(
                                    if (feed.uid.isNotBlank()) {
                                        Modifier.clickable { onUserClick(feed.uid) }
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            UserAvatar(
                                avatarUrl = feed.userAvatar,
                                contentDescription = feed.username,
                                size = 30.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = feed.username,
                                    color = colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleSmall,
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
                            if (feed.ratingScore > 0f) {
                                WearPillBadge(
                                    text = "%.1f★".format(feed.ratingScore),
                                    containerColor = colorScheme.tertiaryContainer,
                                    contentColor = colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }

                    // 真实标题
                    val validTitle = TextSanitizer.filterAutoTitle(feed.title, feed.username)
                    if (validTitle.isNotBlank()) {
                        item(key = "detail_title") {
                            Text(
                                text = validTitle,
                                color = colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, transformationSpec)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // 正文或长图文分块渲染（借鉴 c001apk-Revived & coolapk-desktop）
                    if (feed.articleBlocks.isNotEmpty()) {
                        itemsIndexed(feed.articleBlocks, key = { idx, _ -> "article_$idx" }) { _, block ->
                            when (block.type) {
                                "text" -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.large)
                                            .background(colorScheme.surfaceContainer)
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = block.text,
                                            color = colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                "image" -> {
                                    if (!settings.noImageMode || forceShowImages) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            val thumbUrl = remember(block.imageUrl) {
                                                TextSanitizer.toCoolapkThumbnailUrl(block.imageUrl)
                                            }
                                            var useOriginalFallback by remember(block.imageUrl) { mutableStateOf(false) }
                                            AsyncImage(
                                                model = if (useOriginalFallback) block.imageUrl else thumbUrl,
                                                contentDescription = block.text.ifBlank { "配图" },
                                                contentScale = ContentScale.Crop,
                                                onError = {
                                                    if (!useOriginalFallback && thumbUrl != block.imageUrl) {
                                                        useOriginalFallback = true
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 150.dp)
                                                    .clip(MaterialTheme.shapes.medium)
                                                    .clickable {
                                                        val allImgs = feed.articleBlocks
                                                            .filter { it.type == "image" && it.imageUrl.isNotBlank() }
                                                            .map { it.imageUrl }
                                                        val startIdx = allImgs.indexOf(block.imageUrl).coerceAtLeast(0)
                                                        galleryState = GalleryViewerState(allImgs, startIdx)
                                                    }
                                            )
                                            if (block.text.isNotBlank()) {
                                                Text(
                                                    text = block.text,
                                                    color = colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodyExtraSmall,
                                                    modifier = Modifier.padding(top = 3.dp, start = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                "shareUrl" -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.medium)
                                            .background(colorScheme.surfaceContainerHigh)
                                            .clickable { showQrDialog = true }
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Link,
                                            contentDescription = "外链",
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = block.linkTitle,
                                            color = colorScheme.primary,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    } else if (feed.message.isNotBlank()) {
                        item(key = "detail_message") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .background(colorScheme.surfaceContainer)
                                    .padding(11.dp)
                            ) {
                                Text(
                                    text = feed.message,
                                    color = colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // 转发引用原动态卡片（可点击直达原动态）
                    feed.forwardFeed?.let { fw ->
                        item(key = "detail_forward") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .background(colorScheme.surfaceContainerHigh)
                                    .then(
                                        if (fw.id.isNotBlank()) {
                                            Modifier.clickable { onFeedClick(fw.id) }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Repeat,
                                        contentDescription = "原动态",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "转发自 @${fw.username}",
                                        color = colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (fw.title.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = fw.title,
                                        color = colorScheme.onSurface,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (fw.message.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = fw.message,
                                        color = colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyExtraSmall,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // 交互式投票卡片（借鉴 coolapk-desktop VoteCard）
                    feed.vote?.let { vote ->
                        item(key = "detail_vote") {
                            VoteCardSection(
                                vote = vote,
                                isSubmitting = uiState.isVoting,
                                onSubmitVote = { selected -> viewModel.submitVote(selected) }
                            )
                        }
                    }

                    // 自适应九宫格图片（省流模式支持一键展开，点击进入 M3 HorizontalPager 多图画廊）
                    if (feed.picArr.isNotEmpty()) {
                        item(key = "detail_images") {
                            if (settings.noImageMode && !forceShowImages) {
                                FilledTonalButton(
                                    onClick = { forceShowImages = true },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Image,
                                            contentDescription = "加载图片",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("省流模式 · 查看 ${feed.picArr.size} 张图") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                FeedImageGrid(
                                    picArr = feed.picArr,
                                    onImageTap = { url ->
                                        val idx = feed.picArr.indexOf(url).coerceAtLeast(0)
                                        galleryState = GalleryViewerState(feed.picArr, idx)
                                    },
                                    onImageLongPress = { url -> pendingDownloadImageUrl = url }
                                )
                            }
                        }
                    }

                    // 关联数码产品 & 话题胶囊
                    if (topicTags.isNotEmpty() || (feed.targetProductId.isNotBlank() && feed.targetProductName.isNotBlank())) {
                        item(key = "detail_tags") {
                            val tagScroll = rememberScrollState()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(tagScroll),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (feed.targetProductId.isNotBlank() && feed.targetProductName.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(colorScheme.tertiaryContainer)
                                            .clickable { onProductClick(feed.targetProductId) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Smartphone,
                                            contentDescription = "产品",
                                            tint = colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = feed.targetProductName,
                                            color = colorScheme.onTertiaryContainer,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                topicTags.forEach { tag ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(colorScheme.primaryContainer)
                                            .clickable { onTopicClick(tag) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Tag,
                                            contentDescription = tag,
                                            tint = colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = tag,
                                            color = colorScheme.onPrimaryContainer,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Material 3 Expressive 形状变形操作栏（点赞 / 收藏 / 转发 / 手机扫码）
                    item(key = "detail_action_group") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        ) {
                            val likeInteraction = remember { MutableInteractionSource() }
                            val favInteraction = remember { MutableInteractionSource() }
                            val forwardInteraction = remember { MutableInteractionSource() }
                            val qrInteraction = remember { MutableInteractionSource() }

                            ButtonGroup(modifier = Modifier.fillMaxWidth()) {
                                IconToggleButton(
                                    checked = uiState.isLiked,
                                    enabled = !uiState.isTogglingLike,
                                    onCheckedChange = { viewModel.toggleLike() },
                                    interactionSource = likeInteraction,
                                    shapes = IconToggleButtonDefaults.variantAnimatedShapes(),
                                    colors = IconToggleButtonDefaults.colors(
                                        checkedContainerColor = colorScheme.errorContainer,
                                        checkedContentColor = colorScheme.onErrorContainer,
                                        uncheckedContainerColor = colorScheme.surfaceContainerHigh,
                                        uncheckedContentColor = colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .animateWidth(likeInteraction)
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "赞",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconToggleButton(
                                    checked = uiState.isFavorite,
                                    enabled = !uiState.isTogglingFavorite,
                                    onCheckedChange = { viewModel.toggleFavorite() },
                                    interactionSource = favInteraction,
                                    shapes = IconToggleButtonDefaults.variantAnimatedShapes(),
                                    colors = IconToggleButtonDefaults.colors(
                                        checkedContainerColor = colorScheme.tertiaryContainer,
                                        checkedContentColor = colorScheme.onTertiaryContainer,
                                        uncheckedContainerColor = colorScheme.surfaceContainerHigh,
                                        uncheckedContentColor = colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .animateWidth(favInteraction)
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                        contentDescription = "收藏",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                FilledTonalIconButton(
                                    onClick = {
                                        composerText = ""
                                        showForwardOverlay = true
                                    },
                                    interactionSource = forwardInteraction,
                                    shapes = IconButtonDefaults.animatedShapes(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .animateWidth(forwardInteraction)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Repeat,
                                        contentDescription = "转发",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                FilledTonalIconButton(
                                    onClick = { showQrDialog = true },
                                    interactionSource = qrInteraction,
                                    shapes = IconButtonDefaults.animatedShapes(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .animateWidth(qrInteraction)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.QrCode2,
                                        contentDescription = "手机扫码查看",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // 互动统计摘要 + 管理操作（删除自己的动态 / 屏蔽作者）
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${uiState.likeCount} 赞 · ${maxOf(feed.replyNum, uiState.comments.size)} 评",
                                    color = colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )

                                if (isOwnFeed) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showDeleteFeedConfirm = true }
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "删除动态",
                                            tint = colorScheme.error,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "删除",
                                            color = colorScheme.error,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                } else if (feed.uid.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showBlockAuthorConfirm = true }
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Block,
                                            contentDescription = "屏蔽作者",
                                            tint = colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "屏蔽TA",
                                            color = colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 评论排序条
                    item(key = "detail_comment_sort") {
                        val sortScrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(sortScrollState)
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CommentSort.entries.forEach { sort ->
                                val isSelected = uiState.commentSort == sort
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) colorScheme.primary else colorScheme.surfaceContainerHigh
                                        )
                                        .clickable { viewModel.setCommentSort(sort) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = sort.label,
                                        color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.isLoadingComments && uiState.comments.isEmpty()) {
                        item(key = "comments_loading") { WearLoadingPlaceholder(useSkeleton = false, height = 56.dp, indicatorSize = 18.dp) }
                    } else if (uiState.commentsError != null && uiState.comments.isEmpty()) {
                        item(key = "comments_error") {
                            val commentChallenge = uiState.commentsCaptcha
                            if (commentChallenge != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.large)
                                        .background(colorScheme.surfaceContainer)
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = buildString {
                                            append("评论区：")
                                            append(commentChallenge.message)
                                            if (commentChallenge.messageStatus.isNotBlank()) {
                                                append(" (")
                                                append(commentChallenge.messageStatus)
                                                append(")")
                                            }
                                        },
                                        color = colorScheme.onSurface,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "手表无浏览器无法滑块，可扫码用手机查看或稍后重试",
                                        color = colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyExtraSmall,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        FilledTonalButton(
                                            onClick = { showQrDialog = true },
                                            label = { Text("手机扫码", maxLines = 1) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        CompactButton(
                                            onClick = { viewModel.retryComments() },
                                            label = { Text("重试", maxLines = 1) }
                                        )
                                    }
                                }
                            } else {
                                WearErrorRetry(
                                    message = uiState.commentsError.orEmpty(),
                                    onRetry = { viewModel.retryComments() }
                                )
                            }
                        }
                    } else if (uiState.comments.isEmpty() && !uiState.isLoading) {
                        item(key = "comments_empty") {
                            WearEmptyState("暂无评论，点击底部“写回复”抢沙发吧")
                        }
                    }

                    // 评论列表（支持单条点赞、楼中楼回复、删除自己评论）
                    itemsIndexed(uiState.comments, key = { index, comment -> "${comment.id}_$index" }) { _, comment ->
                        val subState = uiState.subReplyStates[comment.id] ?: SubReplyUiState(replies = comment.subReplies)
                        CommentCard(
                            comment = comment,
                            subReplyState = subState,
                            currentUid = uiState.currentUid,
                            onToggleExpand = { viewModel.toggleSubReplies(comment) },
                            onLoadMoreSubReplies = { viewModel.loadMoreSubReplies(comment) },
                            onRetrySubReplies = { viewModel.retrySubReplies(comment) },
                            onLikeComment = { viewModel.toggleCommentLike(it) },
                            onReplyComment = { targetReplyId, targetName ->
                                composerText = ""
                                activeReplyTarget = ReplyTarget(replyId = targetReplyId, username = targetName)
                            },
                            onDeleteComment = { pendingDeleteComment = it },
                            onUserClick = onUserClick,
                            onViewImage = { imgUrl ->
                                galleryState = GalleryViewerState(listOf(imgUrl), 0)
                            },
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                        )
                    }

                    // 评论分页底部
                    if (uiState.isLoadingMoreComments) {
                        item(key = "comments_loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ExpressiveLoadingIndicator(size = 20.dp, contained = false)
                            }
                        }
                    } else if (uiState.hasMoreComments && uiState.comments.isNotEmpty()) {
                        item(key = "comments_more_btn") {
                            CompactButton(
                                onClick = { viewModel.loadMoreComments() },
                                label = { Text("查看更多评论") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else if (uiState.comments.isNotEmpty()) {
                        item(key = "comments_end") {
                            Text(
                                text = "已显示全部评论",
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        WearCenteredLoadingOverlay(visible = uiState.isLoading && uiState.feed == null)

        // 全屏多图画廊浮层
        galleryState?.let { gallery ->
            FullscreenImageViewer(
                imageUrls = gallery.urls,
                initialIndex = gallery.initialIndex,
                onDismiss = { galleryState = null },
                onRequestSave = { saveUrl -> pendingDownloadImageUrl = saveUrl }
            )
        }

        // 保存图片确认弹窗
        WearConfirmDialog(
            visible = pendingDownloadImageUrl != null,
            title = "下载图片",
            message = "是否将此图片保存至手表相册？",
            confirmIcon = Icons.Rounded.Download,
            confirmContentDescription = "确认保存",
            onConfirm = {
                val url = pendingDownloadImageUrl
                pendingDownloadImageUrl = null
                if (!url.isNullOrBlank()) imageSaver.saveImageToGalleryAsync(url)
            },
            onDismiss = { pendingDownloadImageUrl = null }
        )

        // 删除动态确认弹窗
        WearConfirmDialog(
            visible = showDeleteFeedConfirm,
            title = "删除动态",
            message = "确认删除这条动态？删除后无法恢复。",
            confirmIcon = Icons.Rounded.Delete,
            confirmContentDescription = "确认删除",
            isDestructive = true,
            onConfirm = {
                showDeleteFeedConfirm = false
                viewModel.deleteFeed(onDeleted = onBack)
            },
            onDismiss = { showDeleteFeedConfirm = false }
        )

        // 删除评论确认弹窗
        WearConfirmDialog(
            visible = pendingDeleteComment != null,
            title = "删除评论",
            message = "确认删除你的这条评论？",
            confirmIcon = Icons.Rounded.Delete,
            confirmContentDescription = "确认删除",
            isDestructive = true,
            onConfirm = {
                val target = pendingDeleteComment
                pendingDeleteComment = null
                if (target != null) viewModel.deleteComment(target)
            },
            onDismiss = { pendingDeleteComment = null }
        )

        // 屏蔽作者确认弹窗
        WearConfirmDialog(
            visible = showBlockAuthorConfirm,
            title = "屏蔽用户",
            message = "本地屏蔽 ${uiState.feed?.username.orEmpty()}？其动态与评论将被自动隐藏。",
            confirmIcon = Icons.Rounded.Block,
            confirmContentDescription = "确认屏蔽",
            isDestructive = true,
            onConfirm = {
                showBlockAuthorConfirm = false
                viewModel.blockFeedAuthor()
            },
            onDismiss = { showBlockAuthorConfirm = false }
        )

        // 回复动态或楼中楼浮层
        activeReplyTarget?.let { target ->
            ReplyOverlay(
                mode = if (target.replyId != null) ComposerMode.REPLY_COMMENT else ComposerMode.REPLY_FEED,
                targetLabel = target.username,
                replyText = composerText,
                onReplyTextChange = { composerText = it },
                isSubmitting = uiState.isSubmittingReply,
                onSubmit = {
                    viewModel.submitReply(composerText, replyId = target.replyId) { ok, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (ok) {
                            activeReplyTarget = null
                            composerText = ""
                        }
                    }
                },
                onDismiss = { activeReplyTarget = null }
            )
        }

        // 转发动态浮层
        if (showForwardOverlay) {
            ReplyOverlay(
                mode = ComposerMode.FORWARD_FEED,
                replyText = composerText,
                onReplyTextChange = { composerText = it },
                isSubmitting = uiState.isForwarding,
                onSubmit = {
                    viewModel.forwardFeed(composerText) { ok, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (ok) {
                            showForwardOverlay = false
                            composerText = ""
                        }
                    }
                },
                onDismiss = { showForwardOverlay = false }
            )
        }

        // 手机扫码查看浮层
        val qrFeedId = uiState.feed?.id?.ifBlank { viewModel.feedId } ?: viewModel.feedId
        if (showQrDialog && qrFeedId.isNotBlank()) {
            QrCodeOverlay(
                feedUrl = "https://www.coolapk.com/feed/$qrFeedId",
                onDismiss = { showQrDialog = false }
            )
        }
    }
}

@Composable
private fun VoteCardSection(
    vote: VoteInfo,
    isSubmitting: Boolean,
    onSubmitVote: (List<String>) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var selectedIds by remember(vote.id, vote.selectedOptionIds) {
        mutableStateOf(vote.selectedOptionIds.toSet())
    }
    val totalVotes = maxOf(vote.totalVoteNum, vote.options.sumOf { it.voteNum }).coerceAtLeast(1)

    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colorScheme.surfaceContainer)
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Rounded.Poll,
                    contentDescription = "投票",
                    tint = colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = vote.title,
                    color = colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (vote.totalVoteNum > 0) {
                Text(
                    text = "${vote.totalVoteNum}人",
                    color = colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyExtraSmall
                )
            }
        }

        vote.options.forEach { opt ->
            val isChosen = opt.id in selectedIds
            val ratio = (opt.voteNum.toFloat() / totalVotes.toFloat()).coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isChosen) colorScheme.primaryContainer.copy(alpha = 0.7f)
                        else colorScheme.surfaceContainerHigh
                    )
                    .clickable(enabled = !vote.isVoted && !isSubmitting) {
                        selectedIds = if (vote.maxSelect <= 1) {
                            setOf(opt.id)
                        } else if (isChosen) {
                            selectedIds - opt.id
                        } else if (selectedIds.size < vote.maxSelect) {
                            selectedIds + opt.id
                        } else {
                            selectedIds
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isChosen) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isChosen) colorScheme.primary else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = opt.title,
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyExtraSmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (vote.isVoted) {
                        Text(
                            text = "${(ratio * 100).toInt()}%",
                            color = colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (vote.isVoted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }
            }
        }

        if (!vote.isVoted) {
            val canSubmit = selectedIds.size >= vote.minSelect && selectedIds.size <= vote.maxSelect
            Button(
                onClick = { onSubmitVote(selectedIds.toList()) },
                enabled = canSubmit && !isSubmitting,
                label = {
                    Text(
                        text = if (isSubmitting) "提交中…" else "提交投票",
                        maxLines = 1
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = "已参与投票",
                color = colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
