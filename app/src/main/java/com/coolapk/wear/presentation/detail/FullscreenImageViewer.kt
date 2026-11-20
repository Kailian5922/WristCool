package com.coolapk.wear.presentation.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.coolapk.wear.presentation.components.RotaryScrollLock
import com.coolapk.wear.presentation.theme.LocalAppPrimaryColor

/**
 * 全屏大图预览浮层。
 * 1. 拦截物理返回键（BackHandler）优先关闭预览，避免误退整个详情页；
 * 2. 挂载 [RotaryScrollLock]，避免表圈转动误滚底层详情页；
 * 3. 双指缩放与拖拽平移增加边界约束，防止图片拖出可视范围。
 */
@Composable
fun FullscreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit,
    onRequestSave: (String) -> Unit
) {
    val primaryColor = LocalAppPrimaryColor.current
    var scale by remember(imageUrl) { mutableFloatStateOf(1f) }
    var offset by remember(imageUrl) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    BackHandler(onBack = onDismiss)
    RotaryScrollLock()

    LaunchedEffect(imageUrl) {
        scale = 1f
        offset = Offset.Zero
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
                    if (newScale > 1f) {
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
                            onDismiss()
                        }
                    },
                    onLongPress = {
                        onRequestSave(imageUrl)
                    }
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
                else -> SubcomposeAsyncImageContent()
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
                onClick = onDismiss,
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
