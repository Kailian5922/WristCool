package com.coolapk.wear.presentation.detail

import android.os.SystemClock
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 表冠/表圈旋转切图步进器（纯逻辑，适配高分辨率无级表冠与机械刻度表圈）：
 * - 累积旋转量达到 [stepThresholdPx] 且满足最小步进间隔 [minStepIntervalMs] 时触发 ±1 张切图；
 * - 方向反转立即清零旧方向残留并跳过同向冷却，确保来回微调跟手；
 * - 停顿超过 [idleResetMs] 自动清零残留累积量。
 */
internal class RotaryPageStepper(
    private val stepThresholdPx: Float = DEFAULT_STEP_THRESHOLD_PX,
    private val minStepIntervalMs: Long = DEFAULT_MIN_STEP_INTERVAL_MS,
    private val idleResetMs: Long = DEFAULT_IDLE_RESET_MS
) {
    private var accumulatedPx: Float = 0f
    private var lastEventTimeMs: Long = 0L
    private var lastStepTimeMs: Long = 0L
    private var lastStepDirection: Int = 0

    fun onRotaryDelta(deltaPx: Float, nowMs: Long): Int {
        if (deltaPx == 0f) return 0

        val direction = if (deltaPx > 0f) 1 else -1
        val reversed = (accumulatedPx > 0f && deltaPx < 0f) || (accumulatedPx < 0f && deltaPx > 0f)
        val reversedFromLastStep = lastStepDirection != 0 && lastStepDirection != direction

        if (reversed || (lastEventTimeMs > 0L && nowMs - lastEventTimeMs > idleResetMs)) {
            accumulatedPx = 0f
        }
        lastEventTimeMs = nowMs
        accumulatedPx += deltaPx

        val cooldownSatisfied = reversedFromLastStep ||
            lastStepTimeMs == 0L ||
            (nowMs - lastStepTimeMs >= minStepIntervalMs)
        if (abs(accumulatedPx) >= stepThresholdPx && cooldownSatisfied) {
            accumulatedPx = 0f
            lastStepTimeMs = nowMs
            lastStepDirection = direction
            return direction
        }
        return 0
    }

    fun reset() {
        accumulatedPx = 0f
        lastEventTimeMs = 0L
        lastStepTimeMs = 0L
        lastStepDirection = 0
    }

    companion object {
        const val DEFAULT_STEP_THRESHOLD_PX = 28f
        const val DEFAULT_MIN_STEP_INTERVAL_MS = 130L
        const val DEFAULT_IDLE_RESET_MS = 450L
    }
}

/**
 * 全屏多图画廊浏览浮层（基于 Wear M3 Expressive HorizontalPagerScaffold + AnimatedPage）。
 * 1. 未缩放时单指左右滑动顺畅切图，不吞噬 HorizontalPager 手势；双指捏合/双击缩放后单指自由拖动；
 * 2. 支持旋转表冠/表圈按刻度逐张切图（含高分表冠与机械表圈双通道），同时锁定底层列表防止误滚；
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
    var pendingTargetPage by remember(safeInitial, imageUrls.size) { mutableIntStateOf(safeInitial) }
    var scrollAnimJob by remember { mutableStateOf<Job?>(null) }
    val rotaryStepper = remember(imageUrls.size) { RotaryPageStepper() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val view = LocalView.current

    BackHandler(onBack = onDismiss)

    // 旋转表冠 / 表圈逐张切图（非阻塞取消旧动画，同时拦截底层列表滚动）
    val rotaryConsumer: suspend (Float) -> Unit = remember(pagerState, imageUrls.size, rotaryStepper) {
        { delta ->
            if (imageUrls.size > 1 && delta != 0f) {
                val step = rotaryStepper.onRotaryDelta(
                    deltaPx = delta,
                    nowMs = SystemClock.uptimeMillis()
                )
                if (step != 0) {
                    val basePage = if (scrollAnimJob?.isActive == true) {
                        pendingTargetPage
                    } else {
                        pagerState.currentPage
                    }
                    val target = (basePage + step).coerceIn(0, imageUrls.lastIndex)
                    if (target != basePage || target != pagerState.currentPage) {
                        pendingTargetPage = target
                        currentPageZoomed = false
                        RotaryDispatcher.triggerTick(context, view)
                        scrollAnimJob?.cancel()
                        scrollAnimJob = scope.launch {
                            try {
                                pagerState.animateScrollToPage(target)
                            } catch (_: CancellationException) {
                                // 被新的表冠切图指令打断时交由新动画接管
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(rotaryConsumer) {
        RotaryDispatcher.push(rotaryConsumer)
        onDispose {
            scrollAnimJob?.cancel()
            rotaryStepper.reset()
            RotaryDispatcher.remove(rotaryConsumer)
        }
    }

    // 切页或首次打开时重新抢占表冠焦点，确保 Compose onRotaryScrollEvent 持续生效
    LaunchedEffect(pagerState.currentPage) {
        currentPageZoomed = false
        if (!pagerState.isScrollInProgress) {
            pendingTargetPage = pagerState.currentPage
        }
        delay(60)
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(pagerState.isScrollInProgress, pagerState.settledPage) {
        if (!pagerState.isScrollInProgress) {
            pendingTargetPage = pagerState.currentPage
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent { event ->
                val delta = if (event.verticalScrollPixels != 0f) {
                    event.verticalScrollPixels
                } else {
                    event.horizontalScrollPixels
                }
                if (delta != 0f && RotaryDispatcher.tryClaimEvent(event.uptimeMillis)) {
                    RotaryDispatcher.dispatchScroll(scope, delta)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
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
                            isCurrentPage = pagerState.currentPage == page,
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
    isCurrentPage: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onTapClose: () -> Unit,
    onRequestSave: () -> Unit
) {
    var scale by remember(imageUrl) { mutableFloatStateOf(1f) }
    var offset by remember(imageUrl) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage && (scale > 1.01f || offset != Offset.Zero)) {
            scale = 1f
            offset = Offset.Zero
        }
    }

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
