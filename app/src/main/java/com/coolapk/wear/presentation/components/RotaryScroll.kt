package com.coolapk.wear.presentation.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.ScrollState
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
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全局旋转表圈/表冠事件调度器（兼容 Wear OS M3 TransformingLazyColumn 与传统 ScrollState）。
 * 1. 全方法线程安全（synchronized）；
 * 2. 按事件时间戳去重，避免 Activity 与 Compose 双重消费同一次旋转；
 * 3. 支持 [RotaryScrollLock] 与 HorizontalPager 激活页控制，防止穿透模态浮层或误滚非当前页；
 * 4. 合并连续高频旋转量，并可由设置项开关表圈刻度震动反馈。
 */
object RotaryDispatcher {
    private val lock = Any()
    private val consumers = mutableListOf<suspend (Float) -> Unit>()
    private var lastHandledEventTimeMs: Long = -1L

    private var pendingPixels: Float = 0f
    private var isDraining: Boolean = false

    @Volatile
    var hapticEnabled: Boolean = true

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

    fun triggerTick(context: Context, view: View? = null) {
        if (!hapticEnabled) return
        try {
            val tickFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                18 // HapticFeedbackConstants.ROTARY_SCROLL_TICK
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
 * 模态浮层（如全屏大图画廊、确认弹窗、发布/回复页）打开时使用，
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
 * 专为 Wear OS M3 TransformingLazyColumn 设计的表冠与物理表圈旋转滚动扩展。
 * 支持 [enabled] 参数与 Horologist 分页激活语义联动：仅当页面处于前台激活态时抢占表圈焦点。
 */
@Composable
fun Modifier.rotaryEventHandler(
    listState: TransformingLazyColumnState,
    enabled: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() }
): Modifier {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val scrollConsumer: suspend (Float) -> Unit = remember(listState) {
        { pixels -> listState.scrollBy(pixels) }
    }

    DisposableEffect(scrollConsumer, enabled) {
        if (enabled) {
            RotaryDispatcher.push(scrollConsumer)
        }
        onDispose {
            RotaryDispatcher.remove(scrollConsumer)
        }
    }

    LaunchedEffect(focusRequester, enabled) {
        if (enabled) {
            delay(100)
            runCatching { focusRequester.requestFocus() }
            delay(250)
            runCatching { focusRequester.requestFocus() }
        }
    }

    return if (enabled) {
        this
            .onRotaryScrollEvent { event ->
                val delta = if (event.verticalScrollPixels != 0f) {
                    event.verticalScrollPixels
                } else {
                    event.horizontalScrollPixels
                }
                if (delta != 0f && RotaryDispatcher.tryClaimEvent(event.uptimeMillis)) {
                    RotaryDispatcher.triggerTick(context, view)
                    RotaryDispatcher.dispatchScroll(coroutineScope, delta)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
    } else {
        this
    }
}

@Composable
fun Modifier.rotaryEventHandler(
    scrollState: ScrollState,
    enabled: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() }
): Modifier {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val scrollConsumer: suspend (Float) -> Unit = remember(scrollState) {
        { pixels -> scrollState.scrollBy(pixels) }
    }

    DisposableEffect(scrollConsumer, enabled) {
        if (enabled) {
            RotaryDispatcher.push(scrollConsumer)
        }
        onDispose {
            RotaryDispatcher.remove(scrollConsumer)
        }
    }

    LaunchedEffect(focusRequester, enabled) {
        if (enabled) {
            delay(100)
            runCatching { focusRequester.requestFocus() }
        }
    }

    return if (enabled) {
        this
            .onRotaryScrollEvent { event ->
                val delta = if (event.verticalScrollPixels != 0f) {
                    event.verticalScrollPixels
                } else {
                    event.horizontalScrollPixels
                }
                if (delta != 0f && RotaryDispatcher.tryClaimEvent(event.uptimeMillis)) {
                    RotaryDispatcher.triggerTick(context, view)
                    RotaryDispatcher.dispatchScroll(coroutineScope, delta)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
    } else {
        this
    }
}
