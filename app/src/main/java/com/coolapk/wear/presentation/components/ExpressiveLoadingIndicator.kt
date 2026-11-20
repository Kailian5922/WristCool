package com.coolapk.wear.presentation.components

import android.graphics.Matrix
import android.graphics.Path
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.pill
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.wear.compose.material3.MaterialTheme
import kotlinx.coroutines.delay

/**
 * Material 3 Expressive 标志性多边形变形加载序列（基于 androidx.graphics.shapes）。
 * 复刻 M3 Expressive LoadingIndicator 的经典 7 形态序列：
 * SoftBurst → Cookie9 → Pentagon → Pill → Sunny → Clover4 → Squircle。
 */
private object ExpressiveLoadingShapes {
    val polygons: List<RoundedPolygon> by lazy {
        listOf(
            RoundedPolygon.star(
                numVerticesPerRadius = 10,
                radius = 1f,
                innerRadius = 0.72f,
                rounding = CornerRounding(0.24f),
                innerRounding = CornerRounding(0.24f)
            ).normalized(),
            RoundedPolygon.star(
                numVerticesPerRadius = 9,
                radius = 1f,
                innerRadius = 0.80f,
                rounding = CornerRounding(0.32f),
                innerRounding = CornerRounding(0.32f)
            ).normalized(),
            RoundedPolygon(
                numVertices = 5,
                radius = 1f,
                rounding = CornerRounding(0.32f)
            ).normalized(),
            RoundedPolygon.pill(
                width = 1.2f,
                height = 0.76f
            ).normalized(),
            RoundedPolygon.star(
                numVerticesPerRadius = 8,
                radius = 1f,
                innerRadius = 0.76f,
                rounding = CornerRounding(0.26f),
                innerRounding = CornerRounding(0.26f)
            ).normalized(),
            RoundedPolygon.star(
                numVerticesPerRadius = 4,
                radius = 1f,
                innerRadius = 0.62f,
                rounding = CornerRounding(0.38f),
                innerRounding = CornerRounding(0.38f)
            ).normalized(),
            RoundedPolygon(
                numVertices = 4,
                radius = 1f,
                rounding = CornerRounding(0.55f)
            ).normalized()
        )
    }

    val morphs: List<Morph> by lazy {
        val list = polygons
        List(list.size) { index ->
            Morph(list[index], list[(index + 1) % list.size])
        }
    }
}

/**
 * Material 3 Expressive 形态变换加载动画组件。
 * - 不确定进度模式（[progress] == null）：以弹性弹簧节奏持续旋转并在 7 个表现力几何形体间形变；
 * - 确定进度模式（[progress] in 0f..1f）：适合下拉刷新手势，形变与旋转随手指下拉实时响应；
 * - 支持 [contained] 圆形容器样式（类似 M3 ContainedLoadingIndicator）和紧凑无底色样式。
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    contained: Boolean = false,
    progress: Float? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    indicatorColor: Color = if (contained) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
) {
    val morphs = remember { ExpressiveLoadingShapes.morphs }
    val androidPath = remember { Path() }
    val composePath = remember(androidPath) { androidPath.asComposePath() }
    val matrix = remember { Matrix() }

    if (progress != null) {
        val clamped = progress.coerceIn(0f, 1f)
        val morph = morphs[0]
        Canvas(modifier = modifier.size(size)) {
            if (contained) {
                drawCircle(color = containerColor)
            }
            val scaleFactor = (0.72f + 0.28f * clamped)
            val shapePx = minOf(this.size.width, this.size.height) *
                (if (contained) 0.62f else 0.88f) * scaleFactor
            androidPath.rewind()
            morph.toPath(progress = clamped, path = androidPath)
            matrix.reset()
            matrix.setScale(shapePx, shapePx)
            matrix.postTranslate(
                (this.size.width - shapePx) / 2f,
                (this.size.height - shapePx) / 2f
            )
            androidPath.transform(matrix)
            rotate(degrees = clamped * 180f) {
                drawPath(path = composePath, color = indicatorColor)
            }
        }
        return
    }

    var morphIndex by remember { mutableIntStateOf(0) }
    val morphProgress = remember { Animatable(0f) }

    LaunchedEffect(morphs) {
        while (true) {
            morphProgress.snapTo(0f)
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = 200f,
                    visibilityThreshold = 0.002f
                )
            )
            delay(90L)
            morphIndex = (morphIndex + 1) % morphs.size
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "m3_expressive_loading_rotation")
    val continuousRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "m3_expressive_rotation"
    )

    val activeMorph = morphs[morphIndex]
    val p = morphProgress.value.coerceIn(0f, 1f)
    val totalRotation = (continuousRotation + (morphIndex * 90f) + (p * 90f)) % 360f

    Canvas(modifier = modifier.size(size)) {
        if (contained) {
            drawCircle(color = containerColor)
        }
        val shapePx = minOf(this.size.width, this.size.height) * if (contained) 0.62f else 0.88f
        androidPath.rewind()
        activeMorph.toPath(progress = p, path = androidPath)
        matrix.reset()
        matrix.setScale(shapePx, shapePx)
        matrix.postTranslate(
            (this.size.width - shapePx) / 2f,
            (this.size.height - shapePx) / 2f
        )
        androidPath.transform(matrix)
        rotate(degrees = totalRotation) {
            drawPath(path = composePath, color = indicatorColor)
        }
    }
}
