package com.coolapk.wear.presentation.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全局旋转表圈/表冠事件调度器。
 * 针对三星 Galaxy Watch 4/5/6/7 等 One UI Watch 设备，
 * 在 Compose 焦点丢失或由 Activity 接收到 MotionEvent.AXIS_SCROLL 时，
 * 能够无缝将表圈物理滚动量派发到当前屏幕的活跃列表中，并触发机械刻度物理震动反馈。
 */
object RotaryDispatcher {
    private val consumers = mutableListOf<suspend (Float) -> Unit>()

    val activeScrollBy: (suspend (Float) -> Unit)?
        get() = consumers.lastOrNull()

    fun push(consumer: suspend (Float) -> Unit) {
        synchronized(consumers) {
            consumers.remove(consumer)
            consumers.add(consumer)
        }
    }

    fun remove(consumer: suspend (Float) -> Unit) {
        synchronized(consumers) {
            consumers.remove(consumer)
        }
    }

    /**
     * 触发机械表圈旋转刻度震动（Tick Haptic Feedback）。
     * 支持三星 One UI Watch 与原生 Wear OS 触感引擎。
     */
    fun triggerTick(context: Context, view: View? = null) {
        try {
            // 优先通过 View 派发 Wear OS 旋转表圈触感刻度
            val tickFlag = if (Build.VERSION.SDK_INT >= 34) {
                18 // HapticFeedbackConstants.ROTARY_SCROLL_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
            val performed = view?.performHapticFeedback(
                tickFlag,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            ) ?: false

            if (!performed) {
                val vibrator = context.getSystemService(Vibrator::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(10)
                }
            }
        } catch (_: Exception) {}
    }
}

/**
 * 专为 Wear OS 表冠旋钮与旋转表圈设计的双重处理扩展。
 * 1. 采用后进先出栈（LIFO）自动挂载至 RotaryDispatcher，返回上一页时自动回滚恢复上一页的滚动响应。
 * 2. 内部提供 FocusRequester 自动重试延迟获焦，配合 Compose onRotaryScrollEvent，双重保障。
 * 3. 伴随滚动派发真实表圈机械刻度震动。
 */
@Composable
fun Modifier.rotaryEventHandler(
    listState: ScalingLazyListState,
    focusRequester: FocusRequester = remember { FocusRequester() }
): Modifier {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val scrollConsumer: suspend (Float) -> Unit = remember(listState) {
        { pixels -> listState.scrollBy(pixels) }
    }

    // 绑定至全局调度器栈
    DisposableEffect(listState) {
        RotaryDispatcher.push(scrollConsumer)
        onDispose {
            RotaryDispatcher.remove(scrollConsumer)
        }
    }

    // 延迟请求焦点，确保列表在挂载至 Window 后成功获取表冠事件，退出下级页面返回时依然生效
    LaunchedEffect(listState) {
        RotaryDispatcher.push(scrollConsumer)
        delay(100)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
        delay(300)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    return this
        .focusRequester(focusRequester)
        .focusable()
        .onRotaryScrollEvent { event ->
            coroutineScope.launch {
                RotaryDispatcher.triggerTick(context, view)
                listState.scrollBy(event.verticalScrollPixels)
            }
            true
        }
}
