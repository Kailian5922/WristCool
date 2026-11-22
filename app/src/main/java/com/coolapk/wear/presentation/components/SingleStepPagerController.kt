package com.coolapk.wear.presentation.components

import android.os.SystemClock
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.pager.PagerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 根级横向分页器的「单步防跳页控制器」。
 *
 * 解决手势快速右向左横滑、弧面表盘边缘触控断续（一次滑动产生两段紧凑的 DOWN/UP）
 * 或未完全停稳时连续横滑导致从主页（Page 0）直接跳过消息页（Page 1）落到我的/设置页（Page 2）的问题。
 */
class SingleStepPagerController(
    initialPage: Int = 0,
    private val settleCooldownMs: Long = DEFAULT_SETTLE_COOLDOWN_MS
) {
    var anchorPage: Int = initialPage
        private set

    var lastSettledPage: Int = initialPage
        private set

    var lastSettledAtMs: Long = -settleCooldownMs
        private set

    /**
     * 当分页器完全静止（`!isScrollInProgress`）时同步当前停靠页。
     * 若刚刚切换到一个新页面，需等待 [settleCooldownMs] 冷却窗口后才推进 [anchorPage]，
     * 防止单次物理滑动中的触控抖动在刚抵达 Page 1 的瞬间把锚点立刻改写为 Page 1 并继续滑向 Page 2。
     */
    fun onPageSettled(settledPage: Int, nowMs: Long) {
        if (settledPage != lastSettledPage) {
            lastSettledPage = settledPage
            lastSettledAtMs = nowMs
        }
        if (nowMs - lastSettledAtMs >= settleCooldownMs) {
            anchorPage = settledPage
        }
    }

    /**
     * 计算距离当前停靠页冷却结束还需等待的毫秒数。
     */
    fun remainingCooldownMs(nowMs: Long): Long {
        if (anchorPage == lastSettledPage) return 0L
        return (settleCooldownMs - (nowMs - lastSettledAtMs)).coerceAtLeast(0L)
    }

    /**
     * 新手指按下时刷新手势起始锚点页。
     */
    fun onTouchDown(isScrollInProgress: Boolean, currentSettledPage: Int, nowMs: Long) {
        if (!isScrollInProgress) {
            if (currentSettledPage != lastSettledPage) {
                lastSettledPage = currentSettledPage
                lastSettledAtMs = nowMs
            }
            if (nowMs - lastSettledAtMs >= settleCooldownMs) {
                anchorPage = currentSettledPage
            }
        }
    }

    /**
     * 显式按钮点击跳转（如点击顶栏「消息」或「我的」）时直接更新锚点页，不受单步滑动限制。
     */
    fun onProgrammaticJump(targetPage: Int, nowMs: Long) {
        anchorPage = targetPage
        lastSettledPage = targetPage
        lastSettledAtMs = nowMs - settleCooldownMs
    }

    /**
     * 当前手势允许到达的页码区间：`[anchorPage - 1, anchorPage + 1]`。
     */
    fun allowedPageRange(pageCount: Int): IntRange {
        val maxIndex = (pageCount - 1).coerceAtLeast(0)
        val minAllowed = (anchorPage - 1).coerceAtLeast(0)
        val maxAllowed = (anchorPage + 1).coerceAtMost(maxIndex)
        return minAllowed..maxAllowed
    }

    /**
     * 将单步滚动增量限制在允许的页码区间内，禁止跨越超过 1 页。
     */
    fun clampScrollDelta(
        deltaPx: Float,
        currentContinuousPage: Float,
        pageSizePx: Int,
        pageCount: Int
    ): Float {
        if (pageSizePx <= 0 || pageCount <= 1 || deltaPx == 0f) return deltaPx
        val range = allowedPageRange(pageCount)
        val minPage = range.first.toFloat()
        val maxPage = range.last.toFloat()

        return if (deltaPx > 0f) {
            if (currentContinuousPage >= maxPage) {
                0f
            } else {
                val maxAllowedDeltaPx = (maxPage - currentContinuousPage) * pageSizePx
                deltaPx.coerceAtMost(maxAllowedDeltaPx)
            }
        } else {
            if (currentContinuousPage <= minPage) {
                0f
            } else {
                val minAllowedDeltaPx = (minPage - currentContinuousPage) * pageSizePx
                deltaPx.coerceAtLeast(minAllowedDeltaPx)
            }
        }
    }

    /**
     * 若拖拽阶段已越过当前手势允许的最大/最小页边界，计算拉回边界所需的修正像素偏移。
     */
    fun correctionScrollDeltaPx(
        currentContinuousPage: Float,
        pageSizePx: Int,
        pageCount: Int
    ): Float {
        if (pageSizePx <= 0 || pageCount <= 1) return 0f
        val range = allowedPageRange(pageCount)
        val minPage = range.first.toFloat()
        val maxPage = range.last.toFloat()

        return when {
            currentContinuousPage > maxPage -> (maxPage - currentContinuousPage) * pageSizePx
            currentContinuousPage < minPage -> (minPage - currentContinuousPage) * pageSizePx
            else -> 0f
        }
    }

    /**
     * 若当前位置已抵达或越过本次手势允许的目标边界，则压制继续向越界方向的惯性速度。
     */
    fun clampFlingVelocity(
        initialVelocity: Float,
        currentContinuousPage: Float,
        pageCount: Int
    ): Float {
        if (pageCount <= 1) return 0f
        val range = allowedPageRange(pageCount)
        val minPage = range.first.toFloat()
        val maxPage = range.last.toFloat()

        return when {
            initialVelocity > 0f && currentContinuousPage >= maxPage - BOUNDARY_EPSILON -> 0f
            initialVelocity < 0f && currentContinuousPage <= minPage + BOUNDARY_EPSILON -> 0f
            else -> initialVelocity
        }
    }

    companion object {
        const val DEFAULT_SETTLE_COOLDOWN_MS = 220L
        private const val BOUNDARY_EPSILON = 0.01f
    }
}

/**
 * 追踪 [PagerState] 的连续浮点页位置（例如 `0.0f` 表示第 0 页，`1.0f` 表示第 1 页）。
 */
fun PagerState.continuousPagePosition(): Float = currentPage + currentPageOffsetFraction

/**
 * 记住并绑定到 [PagerState] 的 [SingleStepPagerController]。
 */
@Composable
fun rememberSingleStepPagerController(pagerState: PagerState): SingleStepPagerController {
    val controller = remember(pagerState) {
        SingleStepPagerController(initialPage = pagerState.currentPage)
    }

    LaunchedEffect(pagerState, controller) {
        snapshotFlow {
            Triple(
                pagerState.isScrollInProgress,
                pagerState.settledPage,
                pagerState.currentPageOffsetFraction
            )
        }.collectLatest { (isScrolling, settledPage, offsetFraction) ->
            if (!isScrolling && abs(offsetFraction) < 0.005f) {
                val now = SystemClock.uptimeMillis()
                controller.onPageSettled(settledPage = settledPage, nowMs = now)
                val remaining = controller.remainingCooldownMs(now)
                if (remaining > 0L) {
                    delay(remaining)
                    if (!pagerState.isScrollInProgress) {
                        controller.onPageSettled(
                            settledPage = pagerState.settledPage,
                            nowMs = SystemClock.uptimeMillis()
                        )
                    }
                }
            }
        }
    }

    return controller
}

/**
 * 在 [PointerEventPass.Initial] 阶段记录手势按下时刻的起始锚点页。
 */
fun Modifier.singleStepPagerTouchTracker(
    pagerState: PagerState,
    controller: SingleStepPagerController
): Modifier = pointerInput(pagerState, controller) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        controller.onTouchDown(
            isScrollInProgress = pagerState.isScrollInProgress,
            currentSettledPage = pagerState.settledPage,
            nowMs = SystemClock.uptimeMillis()
        )
    }
}

/**
 * 拦截顶栏按钮区上的短距横向拨动：
 * Wear Compose `HorizontalPager` 将 `touchSlop` 放大了 1.1 倍（约 20dp），
 * 当用户在主页右侧「我的」按钮区域从右向左短距快拨（位移在 6dp..20dp 之间）时，
 * `HorizontalPager` 未触发拖拽，而宽度约 65dp 的「我的」按钮会将其误判为点击并调用 `jumpToPage(2)` 直接跳到设置/我的页。
 *
 * 该修饰符在 `DOWN`/`MOVE` 阶段完全不消费事件（不影响长距离分页拖拽与纵向列表滚动），
 * 仅在手指抬起（`UP`）且未被消费但总位移已超过点击容差（6dp）时拦截误触点击，
 * 若为明显的从右向左短拨（`dx < -8dp`）则顺滑推进 1 页到消息页。
 */
fun Modifier.guardHeaderButtonsAgainstSwipeMisclick(
    onShortSwipeLeftToNextPage: () -> Unit
): Modifier = pointerInput(onShortSwipeLeftToNextPage) {
    val tapCancelSlopPx = 6.dp.toPx()
    val shortSwipeTriggerPx = 8.dp.toPx()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var lastPosition = down.position
        var maxDisplacementPx = 0f

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
                ?: event.changes.firstOrNull()
                ?: break

            lastPosition = change.position
            val dx = lastPosition.x - down.position.x
            val dy = lastPosition.y - down.position.y
            maxDisplacementPx = maxOf(maxDisplacementPx, hypot(dx, dy))

            if (change.changedToUp()) {
                if (!change.isConsumed && maxDisplacementPx > tapCancelSlopPx) {
                    event.changes.forEach { it.consume() }
                    if (dx < -shortSwipeTriggerPx && abs(dx) > abs(dy) * 1.2f) {
                        onShortSwipeLeftToNextPage()
                    }
                }
                break
            }

            if (!change.pressed) {
                break
            }
        }
    }
}

/**
 * 包装底层 [TargetedFlingBehavior]，强制每次手势最多相对于 [SingleStepPagerController.anchorPage] 移动 1 页。
 */
@Composable
fun rememberSingleStepTargetedFlingBehavior(
    pagerState: PagerState,
    controller: SingleStepPagerController,
    delegate: TargetedFlingBehavior
): TargetedFlingBehavior = remember(pagerState, controller, delegate) {
    object : TargetedFlingBehavior {
        override suspend fun ScrollScope.performFling(
            initialVelocity: Float,
            onRemainingDistanceUpdated: (Float) -> Unit
        ): Float {
            val outerScope = this
            val pageCount = pagerState.pageCount
            val pageSizePx = pagerState.layoutInfo.pageSize
            val preContinuousPage = pagerState.continuousPagePosition()

            // 若拖拽阶段已经越过允许的 ±1 页边界，先拉回边界位置
            val correctionPx = controller.correctionScrollDeltaPx(
                currentContinuousPage = preContinuousPage,
                pageSizePx = pageSizePx,
                pageCount = pageCount
            )
            if (abs(correctionPx) > 0.5f) {
                outerScope.scrollBy(correctionPx)
            }

            val postCorrectionPage = pagerState.continuousPagePosition()
            val effectiveVelocity = controller.clampFlingVelocity(
                initialVelocity = initialVelocity,
                currentContinuousPage = postCorrectionPage,
                pageCount = pageCount
            )

            val clampedScope = object : ScrollScope {
                override fun scrollBy(pixels: Float): Float {
                    val currentPagePos = pagerState.continuousPagePosition()
                    val currentPageSize = pagerState.layoutInfo.pageSize
                    val allowedPixels = controller.clampScrollDelta(
                        deltaPx = pixels,
                        currentContinuousPage = currentPagePos,
                        pageSizePx = currentPageSize,
                        pageCount = pagerState.pageCount
                    )
                    if (abs(allowedPixels) < 0.001f) return 0f
                    return outerScope.scrollBy(allowedPixels)
                }
            }

            val remainingVelocity = with(delegate) {
                clampedScope.performFling(
                    initialVelocity = effectiveVelocity,
                    onRemainingDistanceUpdated = onRemainingDistanceUpdated
                )
            }

            // 收尾对齐：若因边界截断残留亚像素/单像素偏移，将其精确对齐至允许的边界页
            val finalPageSizePx = pagerState.layoutInfo.pageSize
            if (finalPageSizePx > 0) {
                val postFlingCorrectionPx = controller.correctionScrollDeltaPx(
                    currentContinuousPage = pagerState.continuousPagePosition(),
                    pageSizePx = finalPageSizePx,
                    pageCount = pagerState.pageCount
                )
                if (abs(postFlingCorrectionPx) > 0.1f) {
                    outerScope.scrollBy(postFlingCorrectionPx)
                }

                val allowedRange = controller.allowedPageRange(pagerState.pageCount)
                val currentPage = pagerState.currentPage
                val residualFraction = pagerState.currentPageOffsetFraction
                if ((currentPage == allowedRange.first || currentPage == allowedRange.last) &&
                    abs(residualFraction) in 0.0005f..0.03f
                ) {
                    outerScope.scrollBy(-residualFraction * finalPageSizePx)
                }
            }

            return remainingVelocity
        }
    }
}
