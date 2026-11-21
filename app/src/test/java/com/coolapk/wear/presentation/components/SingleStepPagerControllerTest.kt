package com.coolapk.wear.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SingleStepPagerControllerTest {

    @Test
    fun `swiping from page 0 is strictly bounded to page 1 and cannot skip to page 2`() {
        val controller = SingleStepPagerController(initialPage = 0, settleCooldownMs = 220L)

        assertEquals(0..1, controller.allowedPageRange(pageCount = 3))

        // At continuous page 0.75 (300px into 400px page), a 250px forward scroll is clamped to 100px (reaching Page 1)
        val clamped = controller.clampScrollDelta(
            deltaPx = 250f,
            currentContinuousPage = 0.75f,
            pageSizePx = 400,
            pageCount = 3
        )
        assertEquals(100f, clamped, 0.01f)

        // Once at Page 1 (1.0f), any further forward scroll in the same gesture is blocked
        val blockedBeyondPage1 = controller.clampScrollDelta(
            deltaPx = 120f,
            currentContinuousPage = 1.0f,
            pageSizePx = 400,
            pageCount = 3
        )
        assertEquals(0f, blockedBeyondPage1, 0.001f)
    }

    @Test
    fun `touch bounce within settle cooldown keeps anchor at page 0 and prevents skipping to page 2`() {
        val controller = SingleStepPagerController(initialPage = 0, settleCooldownMs = 220L)

        // First segment of a right-to-left swipe reaches Page 1 at t = 60ms
        controller.onPageSettled(settledPage = 1, nowMs = 60L)
        assertEquals(0, controller.anchorPage)
        assertEquals(220L, controller.remainingCooldownMs(nowMs = 60L))

        // Capacitive touch bounce / second touch segment occurs 25ms later (t = 85ms)
        controller.onTouchDown(isScrollInProgress = false, currentSettledPage = 1, nowMs = 85L)
        assertEquals(0, controller.anchorPage)
        assertEquals(0..1, controller.allowedPageRange(pageCount = 3))

        // If raw drag pushed slightly past Page 1 (e.g. 1.08f on a 400px screen), correction pulls it back -32px to Page 1
        val correctionPx = controller.correctionScrollDeltaPx(
            currentContinuousPage = 1.08f,
            pageSizePx = 400,
            pageCount = 3
        )
        assertEquals(-32f, correctionPx, 0.05f)

        // Forward fling velocity is suppressed so SnapFlingBehavior cannot snap to Page 2
        val effectiveVelocity = controller.clampFlingVelocity(
            initialVelocity = 2800f,
            currentContinuousPage = 1.0f,
            pageCount = 3
        )
        assertEquals(0f, effectiveVelocity, 0.001f)
    }

    @Test
    fun `after settle cooldown elapses on page 1 next swipe can advance to page 2`() {
        val controller = SingleStepPagerController(initialPage = 0, settleCooldownMs = 220L)

        controller.onPageSettled(settledPage = 1, nowMs = 100L)
        assertEquals(0, controller.anchorPage)

        // After 220ms cooldown (t = 350ms), anchor advances to Page 1
        controller.onTouchDown(isScrollInProgress = false, currentSettledPage = 1, nowMs = 350L)
        assertEquals(1, controller.anchorPage)
        assertEquals(0..2, controller.allowedPageRange(pageCount = 3))

        // Now forward velocity from Page 1 is allowed to advance toward Page 2
        val effectiveVelocity = controller.clampFlingVelocity(
            initialVelocity = 2400f,
            currentContinuousPage = 1.2f,
            pageCount = 3
        )
        assertEquals(2400f, effectiveVelocity, 0.001f)
    }

    @Test
    fun `programmatic jump immediately updates anchor page without cooldown restriction`() {
        val controller = SingleStepPagerController(initialPage = 0, settleCooldownMs = 220L)

        controller.onProgrammaticJump(targetPage = 2, nowMs = 500L)
        assertEquals(2, controller.anchorPage)
        assertEquals(0L, controller.remainingCooldownMs(nowMs = 500L))
        assertEquals(1..2, controller.allowedPageRange(pageCount = 3))
    }
}
