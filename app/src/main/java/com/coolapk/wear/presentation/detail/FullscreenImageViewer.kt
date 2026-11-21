package com.coolapk.wear.presentation.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.PagerDefaults
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.coolapk.wear.presentation.components.ExpressiveLoadingIndicator
import com.coolapk.wear.presentation.components.RotaryDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 全屏多图画廊浏览浮层（基于 Wear M3 Expressive HorizontalPagerScaffold + AnimatedPage）。
 * 1. 未缩放时单指左右滑动顺畅切图，不吞噬 HorizontalPager 手势；双指捏合/双击缩放后单指自由拖动；
 * 2. 支持旋转表冠/表圈按刻度逐张切图，同时锁定底层列表防止误滚；
 * 3. 长按或点顶部下载按钮可保存原图，单击空白处关闭。
 */
@Composable
fun FullscreenImageViewer(
    imageUrls: List<String>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit,
    onRequestSave: (String) -> Unit
) {
    if (imageUrls.isEmpty()) return
    val safeInitial = initialIndex.coerceIn(0, imageUrls.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeInitial) { imageUrls.size }
    var currentPageZoomed by remember { mutableStateOf(false) }
    val currentZoomedState by rememberUpdatedState(currentPageZoomed)
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val view = LocalView.current

    BackHandler(onBack = onDismiss)

    // 旋转表冠 / 表圈逐张切图（同时拦截底层列表滚动）
    val rotaryAccumulator = remember { floatArrayOf(0f) }
    val rotaryLastEventTime = remember { longArrayOf(0L) }
    val rotaryLastStepTime = remember { longArrayOf(0L) }
    val rotaryConsumer: suspend (Float) -> Unit = remember(pagerState, imageUrls.size) {
        { delta ->
            if (!currentZoomedState && imageUrls.size > 1 && delta != 0f) {
                val now = System.currentTimeMillis()
                // 若方向反转或连续转动中断超过 350ms，清零残留累积量
                if ((delta > 0f && rotaryAccumulator[0] < 0f) ||
                    (delta < 0f && rotaryAccumulator[0] > 0f) ||
                    now - rotaryLastEventTime[0] > 350L
                ) {
                    rotaryAccumulator[0] = 0f
                }
                rotaryLastEventTime[0] = now
                rotaryAccumulator[0] += delta
                if (abs(rotaryAccumulator[0]) >= 72f && now - rotaryLastStepTime[0] >= 140L) {
                    val step = if (rotaryAccumulator[0] > 0f) 1 else -1
                    rotaryAccumulator[0] = 0f
                    rotaryLastStepTime[0] = now
                    val target = (pagerState.currentPage + step).coerceIn(0, imageUrls.lastIndex)
                    if (target != pagerState.currentPage) {
                        try {
                            pagerState.animateScrollToPage(target)
                        } catch (_: CancellationException) {
                            pagerState.scrollToPage(target)
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(rotaryConsumer) {
        RotaryDispatcher.push(rotaryConsumer)
        onDispose {
            RotaryDispatcher.remove(rotaryConsumer)
        }
    }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focusRequester.requestFocus() }
    }

    // 切页后重置缩放锁状态
    LaunchedEffect(pagerState.currentPage) {
        currentPageZoomed = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                val delta = event.verticalScrollPixels
                if (delta != 0f && RotaryDispatcher.tryClaimEvent(event.uptimeMillis)) {
                    RotaryDispatcher.triggerTick(context, view)
                    RotaryDispatcher.dispatchScroll(scope, delta)
                }
                true
            }
    ) {
        HorizontalPagerScaffold(pagerState = pagerState) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = !currentPageZoomed && imageUrls.size > 1,
                rotaryScrollableBehavior = null,
                flingBehavior = PagerDefaults.snapFlingBehavior(
                    state = pagerState,
                    maxFlingPages = 0,
                    snapAnimationSpec = PagerDefaults.SnapAnimationSpec,
                    snapPositionalThreshold = 0.35f
                ),
                key = { idx -> "${idx}_${imageUrls[idx]}" }
            ) { page ->
                val imageUrl = imageUrls[page]
                AnimatedPage(pageIndex = page, pagerState = pagerState) {
                    ScreenScaffold(timeText = {}) {
                        ZoomableImagePage(
                            imageUrl = imageUrl,
                            onZoomChanged = { zoomed ->
                                if (pagerState.currentPage == page) {
                                    currentPageZoomed = zoomed
                                }
                            },
                            onTapClose = onDismiss,
                            onRequestSave = { onRequestSave(imageUrl) }
                        )
                    }
                }
            }
        }

        // 顶部页码提示与快捷操作
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        ) {
            if (imageUrls.size > 1) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${imageUrls.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            FilledTonalIconButton(
                onClick = { onRequestSave(imageUrls[pagerState.currentPage]) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White
                ),
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "保存图片",
                    modifier = Modifier.size(14.dp)
                )
            }

            FilledTonalIconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White
                ),
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭",
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun ZoomableImagePage(
    imageUrl: String,
    onZoomChanged: (Boolean) -> Unit,
    onTapClose: () -> Unit,
    onRequestSave: () -> Unit
) {
    var scale by remember(imageUrl) { mutableFloatStateOf(1f) }
    var offset by remember(imageUrl) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1.05f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
            .pointerInput(imageUrl) {
                // 仅在双指捏合缩放或已放大拖动时消费触摸事件；未放大时单指滑动直接透传给 HorizontalPager 切图
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val isPinching = pressedCount >= 2 || abs(zoomChange - 1f) > 0.01f
                        if (isPinching) {
                            val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                            scale = newScale
                            if (newScale > 1.02f) {
                                val maxX = ((containerSize.width * (newScale - 1f)) / 2f).coerceAtLeast(0f)
                                val maxY = ((containerSize.height * (newScale - 1f)) / 2f).coerceAtLeast(0f)
                                offset = Offset(
                                    x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + panChange.y).coerceIn(-maxY, maxY)
                                )
                            } else {
                                offset = Offset.Zero
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        } else if (scale > 1.05f && panChange != Offset.Zero) {
                            val maxX = ((containerSize.width * (scale - 1f)) / 2f).coerceAtLeast(0f)
                            val maxY = ((containerSize.height * (scale - 1f)) / 2f).coerceAtLeast(0f)
                            offset = Offset(
                                x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                y = (offset.y + panChange.y).coerceIn(-maxY, maxY)
                            )
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(imageUrl) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                    onTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            onTapClose()
                        }
                    },
                    onLongPress = { onRequestSave() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = "大图预览",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ExpressiveLoadingIndicator(size = 40.dp, contained = false)
                    }
                }
                is AsyncImagePainter.State.Error -> {
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "图片加载失败",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "点击任意处关闭",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                else -> SubcomposeAsyncImageContent()
            }
        }
    }
}
