package com.coolapk.wear.presentation.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.PagerScaffoldDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.coolapk.wear.presentation.components.ExpressiveLoadingIndicator
import com.coolapk.wear.presentation.components.RotaryScrollLock

/**
 * 全屏多图画廊浏览浮层（基于 Wear M3 Expressive HorizontalPagerScaffold + AnimatedPage）。
 * 1. 支持在动态九宫格多图间左右滑页浏览，底部显示弧形页面指示器与页码胶囊；
 * 2. 支持双指缩放、双击放大/还原、边界约束拖拽与长按/按钮保存原图；
 * 3. 自动挂载 RotaryScrollLock 与 BackHandler。
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

    BackHandler(onBack = onDismiss)
    RotaryScrollLock()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPagerScaffold(pagerState = pagerState) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !currentPageZoomed && imageUrls.size > 1,
                flingBehavior = PagerScaffoldDefaults.snapWithSpringFlingBehavior(state = pagerState)
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
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                    scale = newScale
                    if (newScale > 1.02f) {
                        val maxX = ((containerSize.width * (newScale - 1f)) / 2f).coerceAtLeast(0f)
                        val maxY = ((containerSize.height * (newScale - 1f)) / 2f).coerceAtLeast(0f)
                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                            y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                        )
                    } else {
                        offset = Offset.Zero
                    }
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
                        ExpressiveLoadingIndicator(size = 40.dp, contained = true)
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
