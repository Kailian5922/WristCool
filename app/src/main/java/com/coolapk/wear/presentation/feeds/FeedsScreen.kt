package com.coolapk.wear.presentation.feeds

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil.compose.AsyncImage
import com.coolapk.wear.presentation.components.ExpressiveLoadingIndicator
import com.coolapk.wear.presentation.components.FeedCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.LoadMoreEffect
import com.coolapk.wear.presentation.components.WearEdgeActionButton
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearLoadMoreFooter
import com.coolapk.wear.presentation.components.WearLoadingPlaceholder
import com.coolapk.wear.presentation.components.rotaryEventHandler
import com.coolapk.wear.presentation.detail.ComposerMode
import com.coolapk.wear.presentation.detail.ReplyOverlay
import com.coolapk.wear.presentation.theme.LocalWatchSettings
import kotlinx.coroutines.launch

@Composable
fun FeedsScreen(
    viewModel: FeedsViewModel,
    unreadCount: Int = 0,
    isActivePage: Boolean = true,
    onFeedClick: (String) -> Unit,
    onMeClick: () -> Unit,
    onInboxClick: () -> Unit = {},
    onSearchClick: () -> Unit,
    onUserClick: (String) -> Unit,
    onTopicClick: (String) -> Unit = {},
    onProductClick: (String) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val settings = LocalWatchSettings.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val tabScrollState = rememberScrollState()
    val followGroupScrollState = rememberScrollState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var showComposeOverlay by remember { mutableStateOf(false) }
    var composeText by remember { mutableStateOf("") }
    var composePrivate by remember { mutableStateOf(false) }

    // 记录每个频道的滚动位置，切回已缓存频道时平滑恢复阅读位置
    val savedScrollPositions = remember { mutableMapOf<Pair<FeedTab, FollowGroup>, Pair<Int, Int>>() }
    var previousChannelKey by remember {
        mutableStateOf(
            uiState.currentTab to if (uiState.currentTab == FeedTab.TIMELINE) uiState.followGroup else FollowGroup.ALL
        )
    }

    // 物理阻尼下拉刷新
    val pullOffsetAnimatable = remember { Animatable(0f) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val refreshTriggerPx = with(density) { 48.dp.toPx() }
    val maxPullPx = with(density) { 82.dp.toPx() }
    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
    var hasVibratedOnThreshold by remember { mutableStateOf(false) }

    val nestedScrollConnection = remember(listState, refreshTriggerPx, maxPullPx, settings.hapticEnabled) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0f && dragOffsetPx > 0f) {
                    val toConsume = available.y.coerceAtLeast(-dragOffsetPx)
                    dragOffsetPx = (dragOffsetPx + toConsume).coerceAtLeast(0f)
                    coroutineScope.launch { pullOffsetAnimatable.snapTo(dragOffsetPx) }
                    return Offset(0f, toConsume)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val firstVisibleIndex = listState.layoutInfo.visibleItems.firstOrNull()?.index ?: 0
                val isAtTop = listState.anchorItemIndex <= 1 && firstVisibleIndex == 0
                if (source == NestedScrollSource.UserInput && available.y > 0f && isAtTop) {
                    val newOffset = (dragOffsetPx + available.y * 0.45f).coerceAtMost(maxPullPx)
                    dragOffsetPx = newOffset
                    if (newOffset >= refreshTriggerPx && !hasVibratedOnThreshold) {
                        hasVibratedOnThreshold = true
                        if (settings.hapticEnabled) {
                            runCatching {
                                vibrator?.vibrate(
                                    VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                                )
                            }
                        }
                    }
                    coroutineScope.launch { pullOffsetAnimatable.snapTo(newOffset) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val currentValue = dragOffsetPx
                if (currentValue <= 0f) return Velocity.Zero
                dragOffsetPx = 0f
                hasVibratedOnThreshold = false
                if (currentValue >= refreshTriggerPx) {
                    viewModel.refresh()
                }
                pullOffsetAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
                return available
            }
        }
    }

    val currentChannelKey = uiState.currentTab to if (uiState.currentTab == FeedTab.TIMELINE) {
        uiState.followGroup
    } else {
        FollowGroup.ALL
    }

    LaunchedEffect(currentChannelKey) {
        if (previousChannelKey != currentChannelKey) {
            savedScrollPositions[previousChannelKey] = listState.anchorItemIndex to listState.anchorItemScrollOffset
            previousChannelKey = currentChannelKey
            val (savedIdx, savedOffset) = savedScrollPositions[currentChannelKey] ?: (0 to 0)
            listState.scrollToItem(savedIdx.coerceAtLeast(0), savedOffset)
        }
    }

    LoadMoreEffect(
        listState = listState,
        itemCount = uiState.items.size,
        hasMore = uiState.hasMore,
        isLoading = uiState.isLoading || uiState.isLoadingMore,
        onLoadMore = { viewModel.loadMore() }
    )

    HorologistScaffold(
        scrollState = listState,
        edgeButton = {
            WearEdgeActionButton(
                scrollState = listState,
                buttonSize = EdgeButtonSize.ExtraSmall,
                onClick = {
                    if (uiState.isLoggedIn) {
                        composeText = ""
                        composePrivate = false
                        showComposeOverlay = true
                    } else {
                        viewModel.refresh()
                    }
                }
            ) {
                Icon(
                    imageVector = if (uiState.isLoggedIn) Icons.Rounded.Create else Icons.Rounded.Refresh,
                    contentDescription = if (uiState.isLoggedIn) "发动态" else "刷新",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (uiState.isLoggedIn) "发动态" else "刷新",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = pullOffsetAnimatable.value * 0.45f
                    }
                    .rotaryEventHandler(listState, enabled = isActivePage && !showComposeOverlay)
            ) {
                // 顶部 Material 3 Expressive 快捷操作组（搜索 / 消息未读 / 我的）
                item(key = "home_header") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    ) {
                        Text(
                            text = "腕酷 · ${uiState.currentTab.title}",
                            color = colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val searchInteraction = remember { MutableInteractionSource() }
                        val inboxInteraction = remember { MutableInteractionSource() }
                        val meInteraction = remember { MutableInteractionSource() }

                        ButtonGroup(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalIconButton(
                                onClick = onSearchClick,
                                interactionSource = searchInteraction,
                                shapes = IconButtonDefaults.animatedShapes(),
                                modifier = Modifier
                                    .weight(1f)
                                    .animateWidth(searchInteraction)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "搜索",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            FilledTonalIconButton(
                                onClick = onInboxClick,
                                interactionSource = inboxInteraction,
                                shapes = IconButtonDefaults.animatedShapes(),
                                modifier = Modifier
                                    .weight(1f)
                                    .animateWidth(inboxInteraction)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Notifications,
                                        contentDescription = "消息",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    if (unreadCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 3.dp, y = (-2).dp)
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(colorScheme.error)
                                        )
                                    }
                                }
                            }

                            FilledTonalIconButton(
                                onClick = onMeClick,
                                interactionSource = meInteraction,
                                shapes = IconButtonDefaults.animatedShapes(),
                                modifier = Modifier
                                    .weight(1f)
                                    .animateWidth(meInteraction)
                            ) {
                                if (uiState.isLoggedIn && uiState.userAvatar.isNotBlank()) {
                                    AsyncImage(
                                        model = uiState.userAvatar,
                                        contentDescription = "我的",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.AccountCircle,
                                        contentDescription = "我的",
                                        tint = if (uiState.isLoggedIn) colorScheme.primary else colorScheme.onSurface,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 7 频道横向切换条（头条 / 推荐 / 热榜 / 关注 / 酷图 / 数码 / 二手）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(tabScrollState),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FeedTab.entries.forEach { tab ->
                                val isSelected = uiState.currentTab == tab
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) colorScheme.primary else colorScheme.surfaceContainerHigh
                                        )
                                        .clickable { viewModel.switchTab(tab) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // 关注流二级分组筛选条
                        if (uiState.currentTab == FeedTab.TIMELINE && uiState.isLoggedIn) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(followGroupScrollState),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FollowGroup.entries.forEach { grp ->
                                    val isSelected = uiState.followGroup == grp
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) colorScheme.secondaryContainer else colorScheme.surfaceContainerLow
                                            )
                                            .clickable { viewModel.switchFollowGroup(grp) }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = grp.label,
                                            style = MaterialTheme.typography.bodyExtraSmall,
                                            color = if (isSelected) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 关注流未登录引导卡片
                val needsTimelineLogin = uiState.currentTab == FeedTab.TIMELINE && !uiState.isLoggedIn
                if (needsTimelineLogin) {
                    item(key = "timeline_login") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.large)
                                .background(colorScheme.surfaceContainer)
                                .padding(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = "未登录",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "关注动态需登录账号",
                                color = colorScheme.onSurface,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "左滑至【我的】通过伴侣端一键同步登录",
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyExtraSmall,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = onMeClick,
                                label = { Text("去导入登录") }
                            )
                        }
                    }
                }

                if (uiState.isLoading && uiState.items.isEmpty()) {
                    item(key = "loading_skeleton") {
                        WearLoadingPlaceholder(label = "正在加载${uiState.currentTab.title}…")
                    }
                }

                if (uiState.error != null && uiState.items.isEmpty() && !needsTimelineLogin) {
                    item(key = "error_retry") {
                        WearErrorRetry(
                            message = uiState.error.orEmpty(),
                            onRetry = { viewModel.refresh() }
                        )
                    }
                }

                if (uiState.items.isEmpty() && !uiState.isLoading && uiState.error == null && !needsTimelineLogin) {
                    item(key = "empty_state") {
                        WearEmptyState(
                            text = if (uiState.currentTab == FeedTab.TIMELINE) {
                                "暂无关注的动态\n快去关注有趣的酷友吧"
                            } else {
                                "暂无动态"
                            }
                        )
                    }
                }

                itemsIndexed(uiState.items, key = { index, feed -> "${feed.id}_$index" }) { _, feed ->
                    FeedCard(
                        feed = feed,
                        onClick = { onFeedClick(feed.id) },
                        onUserClick = onUserClick,
                        onTopicClick = onTopicClick,
                        onProductClick = onProductClick,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                    )
                }

                if (uiState.isLoadingMore || (!uiState.hasMore && uiState.items.isNotEmpty())) {
                    item(key = "load_more_footer") {
                        WearLoadMoreFooter(
                            isLoadingMore = uiState.isLoadingMore,
                            hasMore = uiState.hasMore,
                            hasItems = uiState.items.isNotEmpty(),
                            endText = "没有更多动态了"
                        )
                    }
                }
            }

            // Material 3 Expressive 浮动下拉刷新形态变换指示胶囊
            AnimatedVisibility(
                visible = pullOffsetAnimatable.value > 12f || (uiState.isLoading && uiState.items.isNotEmpty()),
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colorScheme.surfaceContainerHigh.copy(alpha = 0.96f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExpressiveLoadingIndicator(size = 18.dp, contained = false)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "正在刷新…",
                                color = colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else {
                        val progress = (pullOffsetAnimatable.value / refreshTriggerPx).coerceIn(0f, 1f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExpressiveLoadingIndicator(
                                size = 18.dp,
                                contained = false,
                                progress = progress,
                                indicatorColor = if (progress >= 1f) colorScheme.primary else colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (progress >= 1f) "松开刷新" else "下拉刷新",
                                color = if (progress >= 1f) colorScheme.primary else colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // 手表端快捷发布动态浮层
            if (showComposeOverlay) {
                ReplyOverlay(
                    mode = ComposerMode.NEW_FEED,
                    replyText = composeText,
                    onReplyTextChange = { composeText = it },
                    isSubmitting = uiState.isPublishing,
                    isPrivate = composePrivate,
                    onPrivateChange = { composePrivate = it },
                    onSubmit = {
                        viewModel.publishFeed(composeText, composePrivate) { ok, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (ok) {
                                showComposeOverlay = false
                                composeText = ""
                                composePrivate = false
                            }
                        }
                    },
                    onDismiss = { showComposeOverlay = false }
                )
            }
        }
    }
}
