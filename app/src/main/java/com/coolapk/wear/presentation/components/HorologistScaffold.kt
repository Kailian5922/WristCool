package com.coolapk.wear.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.scrollAway

/**
 * 遵循 Horologist 规范的 Scaffold 容器。
 * 集成了 Wear OS 圆屏边界适配、TimeText 随滚动渐隐、边缘 PositionIndicator。
 */
@Composable
fun HorologistScaffold(
    listState: ScalingLazyListState? = null,
    showTimeText: Boolean = true,
    content: @Composable () -> Unit
) {
    Scaffold(
        timeText = {
            if (showTimeText) {
                if (listState != null) {
                    TimeText(modifier = Modifier.scrollAway(listState))
                } else {
                    TimeText()
                }
            }
        },
        positionIndicator = {
            if (listState != null) {
                PositionIndicator(scalingLazyListState = listState)
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            content()
        }
    }
}

val WearListContentPadding = PaddingValues(
    top = 36.dp,
    bottom = 44.dp,
    start = 12.dp,
    end = 12.dp
)
