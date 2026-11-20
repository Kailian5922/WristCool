package com.coolapk.wear.presentation.detail

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * 自适应紧凑多图网格：1 图自适应，2/4 图双列对称，3/5/6/7/8/9 图三列九宫格。
 */
@Composable
fun FeedImageGrid(
    picArr: List<String>,
    onImageTap: (String) -> Unit,
    onImageLongPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (picArr.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        when (picArr.size) {
            1 -> {
                val picUrl = picArr[0]
                AsyncImage(
                    model = picUrl,
                    contentDescription = "配图",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .pointerInput(picUrl) {
                            detectTapGestures(
                                onTap = { onImageTap(picUrl) },
                                onLongPress = { onImageLongPress(picUrl) }
                            )
                        }
                )
            }
            2, 4 -> {
                GridImageRows(
                    picArr = picArr,
                    columns = 2,
                    cornerRadius = 8.dp,
                    onImageTap = onImageTap,
                    onImageLongPress = onImageLongPress
                )
            }
            else -> {
                GridImageRows(
                    picArr = picArr,
                    columns = 3,
                    cornerRadius = 6.dp,
                    onImageTap = onImageTap,
                    onImageLongPress = onImageLongPress
                )
            }
        }
    }
}

@Composable
private fun GridImageRows(
    picArr: List<String>,
    columns: Int,
    cornerRadius: Dp,
    onImageTap: (String) -> Unit,
    onImageLongPress: (String) -> Unit
) {
    val rowCount = (picArr.size + columns - 1) / columns
    for (r in 0 until rowCount) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (c in 0 until columns) {
                val idx = r * columns + c
                if (idx < picArr.size) {
                    val picUrl = picArr[idx]
                    AsyncImage(
                        model = picUrl,
                        contentDescription = "配图",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(cornerRadius))
                            .pointerInput(picUrl) {
                                detectTapGestures(
                                    onTap = { onImageTap(picUrl) },
                                    onLongPress = { onImageLongPress(picUrl) }
                                )
                            }
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
