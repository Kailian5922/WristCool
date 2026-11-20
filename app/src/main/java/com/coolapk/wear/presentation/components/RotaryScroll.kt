package com.coolapk.wear.presentation.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全局旋转表圈/表冠事件调度器。
 * 1. 全方法线程安全（synchronized）；
 * 2. 支持按事件时间戳去重，避免 Activity 与 Compose 双重消费同一次旋转；
 * 3. 支持通过 [RotaryScrollLock] 在全屏大图/弹窗打开时锁定底层列表滚动；
 * 4. 合并连续高频旋转量，避免快速转动表圈时堆积大量并发协程。
 */
object RotaryDispatcher {
    private val lock = Any()
    private val consumers = mutableListOf<suspend (Float) -> Unit>()
    private var lastHandledEventTimeMs: Long = -1L

    private var pendingPixels: Float = 0f
    private var isDraining: Boolean = false

    val activeScrollBy: (suspend (Float) -> Unit)?
        get() = synchronized(lock) { consumers.lastOrNull() }

    fun push(consumer: suspend (Float) -> Unit) {
        synchronized(lock) {
            consumers.remove(consumer)
            consumers.add(consumer)
        }
    }

    fun remove(consumer: suspend (Float) -> Unit) {
        synchronized(lock) {
            consumers.remove(consumer)
        }
    }

    /**
     * 标记某次旋转事件已处理；若同一时间戳已被处理则返回 false，防止双重触发。
     */
    fun tryClaimEvent(eventTimeMs: Long): Boolean {
        if (eventTimeMs <= 0L) return true
        return synchronized(lock) {
            if (eventTimeMs == lastHandledEventTimeMs) {
                false
            } else {
                lastHandledEventTimeMs = eventTimeMs
                true
            }
        }
    }

    /**
     * 将旋转滚动像素累加并按序派发给当前栈顶消费者，避免高频旋转并发抢占。
     */
    fun dispatchScroll(scope: CoroutineScope, pixels: Float) {
        if (pixels == 0f) return
        val shouldStartDrain = synchronized(lock) {
            pendingPixels += pixels
            if (!isDraining) {
                isDraining = true
                true
            } else {
                false
            }
        }
        if (!shouldStartDrain) return

        scope.launch {
            try {
                while (true) {
                    val (delta, consumer) = synchronized(lock) {
                        val d = pendingPixels
                        pendingPixels = 0f
                        if (d == 0f) {
                            isDraining = false
                            return@launch
                        }
                        d to consumers.lastOrNull()
                    }
                    try {
                        consumer?.invoke(delta)
                    } catch (_: Exception) {
                        // 忽略页面销毁期间的滚动取消异常
                    }
                }
            } finally {
                synchronized(lock) {
                    isDraining = false
                }
            }
        }
    }

    /**
     * 触发机械表圈旋转刻度震动（Tick Haptic Feedback）。
     */
    fun triggerTick(context: Context, view: View? = null) {
        try {
            val tickFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                18 // HapticFeedbackConstants.ROTARY_SCROLL_TICK (public in API 35, available from API 34)
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
            val performed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                view?.performHapticFeedback(tickFlag) ?: false
            } else {
                @Suppress("DEPRECATION")
                view?.performHapticFeedback(
                    tickFlag,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                ) ?: false
            }

            if (!performed) {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Vibrator::class.java)
                }
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            }
        } catch (_: Exception) {
        }
    }
}

/**
 * 模态浮层（如全屏大图预览、确认弹窗、手动输入页）打开时使用，
 * 拦截表圈旋转事件，防止旋转表圈误滚底层列表。
 */
@Composable
fun RotaryScrollLock(enabled: Boolean = true) {
    if (!enabled) return
    val blocker: suspend (Float) -> Unit = remember { { _ -> } }
    DisposableEffect(blocker) {
        RotaryDispatcher.push(blocker)
        onDispose {
            RotaryDispatcher.remove(blocker)
        }
    }
}

/**
 * 专为 Wear OS 表冠旋钮与旋转表圈设计的双重处理扩展。
 * 1. 采用 LIFO 栈自动挂载至 [RotaryDispatcher]，返回上一页时自动恢复上一页滚动；
 * 2. 延迟请求 Compose 焦点并配合时间戳去重，兼容三星 Galaxy Watch 与原生 Wear OS；
 * 3. 伴随滚动派发表圈机械刻度震动反馈。
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

    DisposableEffect(scrollConsumer) {
        RotaryDispatcher.push(scrollConsumer)
        onDispose {
            RotaryDispatcher.remove(scrollConsumer)
        }
    }

    LaunchedEffect(focusRequester) {
        delay(100)
        runCatching { focusRequester.requestFocus() }
        delay(250)
        runCatching { focusRequester.requestFocus() }
    }

    return this
        .focusRequester(focusRequester)
        .focusable()
        .onRotaryScrollEvent { event ->
            if (RotaryDispatcher.tryClaimEvent(event.uptimeMillis)) {
                RotaryDispatcher.triggerTick(context, view)
                RotaryDispatcher.dispatchScroll(coroutineScope, event.verticalScrollPixels)
            }
            true
        }
}
