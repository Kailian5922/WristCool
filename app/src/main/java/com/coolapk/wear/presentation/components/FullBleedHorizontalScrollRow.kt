package com.coolapk.wear.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 突破父级 `TransformingLazyColumn` 的左右 `contentPadding` 裁剪边界，
 * 让横向滑动条（主页频道条、消息分类条、帖子话题/排序条、搜索标签条、评论表情栏等）
 * 的滚动视口铺满整个表盘宽度（Edge-to-Edge）：
 * - 外层通过 [Modifier.layout] 将滚动视口左右扩展 `startBleed + endBleed`，
 *   消除表盘左右两侧原本存在的黑色截断区；
 * - 内层在 [Modifier.horizontalScroll] 之后保留同等首尾内边距，
 *   静止时首尾胶囊仍与上下文卡片对齐，滑动时则一路平滑延伸到屏幕物理边缘。
 */
@Composable
fun FullBleedHorizontalScrollRow(
    scrollState: ScrollState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    val startBleed = contentPadding.calculateStartPadding(layoutDirection)
    val endBleed = contentPadding.calculateEndPadding(layoutDirection)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val startPx = startBleed.roundToPx()
                val endPx = endBleed.roundToPx()
                val expandedWidth = if (constraints.hasBoundedWidth) {
                    (constraints.maxWidth + startPx + endPx).coerceAtLeast(0)
                } else {
                    constraints.maxWidth
                }
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = if (constraints.hasBoundedWidth) expandedWidth else constraints.minWidth,
                        maxWidth = expandedWidth
                    )
                )
                val reportedWidth = if (constraints.hasBoundedWidth) {
                    constraints.maxWidth
                } else {
                    placeable.width
                }
                layout(reportedWidth, placeable.height) {
                    val offsetX = if (layoutDirection == LayoutDirection.Ltr) -startPx else -endPx
                    placeable.placeRelative(offsetX, 0)
                }
            }
            .horizontalScroll(scrollState)
            .padding(start = startBleed, end = endBleed),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content
    )
}
