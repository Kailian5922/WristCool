package com.coolapk.wear.presentation.feeds

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.coolapk.wear.presentation.components.FeedCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.WearListContentPadding
import com.coolapk.wear.presentation.components.rotaryEventHandler
import com.coolapk.wear.presentation.theme.LocalAppPrimaryColor
import com.coolapk.wear.presentation.theme.WearSurface
import com.coolapk.wear.presentation.theme.WearSurfaceVariant
import kotlinx.coroutines.launch

@Composable
fun FeedsScreen(
    viewModel: FeedsViewModel,
    onFeedClick: (String) -> Unit,
    onMeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onUserClick: (String) -> Unit,
    onTopicClick: (String) -> Unit = {}
) {
    val primaryColor = LocalAppPrimaryColor.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    val tabScrollState = rememberScrollState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 原生下拉刷新手势与物理阻尼
    val pullOffsetAnimatable = remember { Animatable(0f) }
    val refreshTriggerPx = with(density) { 50.dp.toPx() }
    val maxPullPx = with(density) { 85.dp.toPx() }
    val vibrator = remember(context) { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    var hasVibratedOnThreshold by remember { mutableStateOf(false) }

    val nestedScrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 仅响应用户手指真实拖动（排除惯性与系统滚动）
                if (source == NestedScrollSource.Drag && available.y < 0 && pullOffsetAnimatable.value > 0f) {
                    val toConsume = available.y.coerceAtLeast(-pullOffsetAnimatable.value)
                    coroutineScope.launch {
                        pullOffsetAnimatable.snapTo(pullOffsetAnimatable.value + toConsume)
                    }
                    return Offset(0f, toConsume)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val isAtTop = listState.centerItemIndex <= 1 && (listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0) == 0
                if (source == NestedScrollSource.Drag && available.y > 0 && isAtTop) {
                    val newOffset = (pullOffsetAnimatable.value + available.y * 0.45f).coerceAtMost(maxPullPx)
                    if (newOffset >= refreshTriggerPx && !hasVibratedOnThreshold) {
                        hasVibratedOnThreshold = true
                        try {
                            vibrator?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
                        } catch (_: Exception) {}
                    }
                    coroutineScope.launch {
                        pullOffsetAnimatable.snapTo(newOffset)
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val currentValue = pullOffsetAnimatable.value
                hasVibratedOnThreshold = false
                if (currentValue >= refreshTriggerPx) {
                    viewModel.refresh()
                }
                pullOffsetAnimatable.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                return Velocity.Zero
            }
        }
    }

    // 切换 Tab 时自动重置列表滚动位置至顶部
    LaunchedEffect(uiState.currentTab) {
        listState.scrollToItem(0)
    }

    // 触底自动加载下一页
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= uiState.items.size - 2) {
                    viewModel.loadMore()
                }
            }
    }

    HorologistScaffold(listState = listState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            ScalingLazyColumn(
                state = listState,
                contentPadding = WearListContentPadding,
                anchorType = ScalingLazyListAnchorType.ItemStart,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = pullOffsetAnimatable.value * 0.5f
                    }
                    .rotaryEventHandler(listState)
            ) {
                // 顶部导航栏与 Tab 切换（已移除刷新按钮，支持下拉刷新与点击 Tab 刷新）
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 顶部标题与功能按钮（搜索、我的）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "酷安",
                                color = primaryColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 搜索快捷按钮
                                CompactButton(
                                    onClick = onSearchClick,
                                    colors = ButtonDefaults.buttonColors(backgroundColor = WearSurfaceVariant),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = "搜索",
                                        modifier = Modifier.size(15.dp),
                                        tint = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // “我的”按钮（显示当前用户头像或个人图标）
                                CompactButton(
                                    onClick = onMeClick,
                                    colors = ButtonDefaults.buttonColors(backgroundColor = WearSurfaceVariant),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    if (uiState.isLoggedIn && uiState.userAvatar.isNotBlank()) {
                                        AsyncImage(
                                            model = uiState.userAvatar,
                                            contentDescription = "我的",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.AccountCircle,
                                            contentDescription = "我的",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (uiState.isLoggedIn) primaryColor else Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 频道 Tab 滚轮栏：热榜（默认）、推荐、关注、酷图（支持点击已激活 Tab 快速刷新）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(tabScrollState),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FeedTab.values().forEach { tab ->
                                val isSelected = uiState.currentTab == tab
                                CompactChip(
                                    onClick = { viewModel.switchTab(tab) },
                                    label = {
                                        Text(
                                            text = tab.title,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = ChipDefaults.chipColors(
                                        backgroundColor = if (isSelected) primaryColor else WearSurfaceVariant
                                    ),
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // 关注流未登录提示卡片
                if (uiState.currentTab == FeedTab.TIMELINE && !uiState.isLoggedIn) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(WearSurface)
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = "未登录",
                                tint = Color(0xFFA0A0A5),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "关注动态需登录账号",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "请前往【我的】导入手机端登录信息",
                                color = Color(0xFFA0A0A5),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            CompactChip(
                                onClick = onMeClick,
                                label = { Text("去导入登录", fontSize = 11.sp) },
                                colors = ChipDefaults.chipColors(backgroundColor = primaryColor)
                            )
                        }
                    }
                }

                // 首次全屏加载指示
                if (uiState.isLoading && uiState.items.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                indicatorColor = primaryColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // 错误提示与重试按钮
                if (uiState.error != null && uiState.items.isEmpty() && !(uiState.currentTab == FeedTab.TIMELINE && !uiState.isLoggedIn)) {
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
                                onClick = { viewModel.refresh() },
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

                // 空列表提示
                if (uiState.items.isEmpty() && !uiState.isLoading && uiState.error == null && !(uiState.currentTab == FeedTab.TIMELINE && !uiState.isLoggedIn)) {
                    item {
                        Text(
                            text = if (uiState.currentTab == FeedTab.TIMELINE) "暂无关注的动态\n快去关注有趣的酷友吧" else "暂无动态",
                            color = Color(0xFF777777),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                }

                // 动态流列表
                itemsIndexed(uiState.items, key = { index, feed -> "${feed.id}_$index" }) { _, feed ->
                    FeedCard(
                        feed = feed,
                        onClick = { onFeedClick(feed.id) },
                        onUserClick = onUserClick,
                        onTopicClick = onTopicClick
                    )
                }

                // 底部加载更多动画或结束提示
                if (uiState.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                indicatorColor = primaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else if (!uiState.hasMore && uiState.items.isNotEmpty()) {
                    item {
                        Text(
                            text = "没有更多动态了",
                            color = Color(0xFF666666),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
            }

            // 浮动下拉刷新指示器（位于顶部中央，不占用列表条目）
            AnimatedVisibility(
                visible = pullOffsetAnimatable.value > 12f || (uiState.isLoading && uiState.items.isNotEmpty()),
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(WearSurface.copy(alpha = 0.95f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                indicatorColor = primaryColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "正在刷新…",
                                color = primaryColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        val progress = (pullOffsetAnimatable.value / refreshTriggerPx).coerceIn(0f, 1f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowDownward,
                                contentDescription = "下拉刷新",
                                tint = if (progress >= 1f) primaryColor else Color(0xFFA0A0A5),
                                modifier = Modifier
                                    .size(13.dp)
                                    .graphicsLayer {
                                        rotationZ = progress * 180f
                                    }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (progress >= 1f) "松开立即刷新" else "下拉刷新",
                                color = if (progress >= 1f) primaryColor else Color(0xFFA0A0A5),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
