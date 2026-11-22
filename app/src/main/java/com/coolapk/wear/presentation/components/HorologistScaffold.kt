package com.coolapk.wear.presentation.components

import android.content.res.Configuration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import kotlin.math.sqrt

/**
 * Horologist 响应式尺寸断点：区分 192dp 小表盘与 >=225dp 大表盘（如 Galaxy Watch 6 / Pixel Watch 3 45mm）。
 */
val Configuration.isLargeWatchScreen: Boolean
    get() = screenHeightDp > 224

/**
 * Horologist FillMaxRectangle 规范：
 * 在圆形表盘内部安全内接最大正方形区域，避免二维码或全屏预览四角被圆屏边缘裁切。
 */
fun Modifier.fillMaxRectangle(): Modifier = composed {
    val config = LocalConfiguration.current
    if (!config.isScreenRound) {
        fillMaxSize()
    } else {
        val inset = ((config.screenHeightDp - sqrt((config.screenWidthDp * config.screenHeightDp) / 2.0)) / 2.0)
            .toFloat()
            .coerceAtLeast(16f)
        fillMaxSize().padding(inset.dp)
    }
}

/**
 * 遵循 Wear OS Material 3 Expressive + Horologist 规范的页面级 Scaffold。
 * - 自动联动全局 AppScaffold 的 TimeText 与右侧弧形 ScrollIndicator；
 * - 支持传入底部弧形 EdgeButton，并与 TransformingLazyColumn 滚动弹性形变联动；
 * - 将 ScreenScaffold 计算的安全 contentPadding 透传给内容区。
 */
@Composable
fun HorologistScaffold(
    scrollState: TransformingLazyColumnState,
    showTimeText: Boolean = true,
    edgeButton: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val bgModifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)

    if (edgeButton != null) {
        ScreenScaffold(
            scrollState = scrollState,
            edgeButton = edgeButton,
            timeText = if (showTimeText) null else ({}),
            modifier = bgModifier
        ) { contentPadding ->
            content(contentPadding)
        }
    } else {
        ScreenScaffold(
            scrollState = scrollState,
            timeText = if (showTimeText) null else ({}),
            modifier = bgModifier
        ) { contentPadding ->
            content(contentPadding)
        }
    }
}

/**
 * 无列表滚动状态页面（如全屏加载、二维码浮层、手动输入页）的 M3 Expressive ScreenScaffold。
 */
@Composable
fun HorologistScaffold(
    scrollState: ScrollState? = null,
    showTimeText: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val bgColor = MaterialTheme.colorScheme.background
    val bgModifier = Modifier
        .fillMaxSize()
        .background(bgColor)

    if (scrollState != null) {
        ScreenScaffold(
            scrollState = scrollState,
            timeText = if (showTimeText) null else ({}),
            modifier = bgModifier
        ) { _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor),
                content = content
            )
        }
    } else {
        ScreenScaffold(
            timeText = if (showTimeText) null else ({}),
            modifier = bgModifier
        ) { _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor),
                content = content
            )
        }
    }
}

/**
 * 遵循 Wear Compose Material 3 Expressive 规范的弧形底部 EdgeButton。
 * 绑定 TransformingLazyColumn 反向滚动与 overscroll 物理阻尼，贴合圆屏底部弧线。
 */
@Composable
fun WearEdgeActionButton(
    scrollState: TransformingLazyColumnState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonSize: EdgeButtonSize = EdgeButtonSize.Small,
    content: @Composable RowScope.() -> Unit
) {
    EdgeButton(
        onClick = onClick,
        enabled = enabled,
        buttonSize = buttonSize,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier.scrollable(
            state = scrollState,
            orientation = Orientation.Vertical,
            reverseDirection = true,
            overscrollEffect = rememberOverscrollEffect()
        ),
        content = content
    )
}
