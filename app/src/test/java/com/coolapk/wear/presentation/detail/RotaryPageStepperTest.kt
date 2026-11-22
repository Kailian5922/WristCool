package com.coolapk.wear.presentation.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class RotaryPageStepperTest {

    @Test
    fun `high-res crown accumulates small ticks to step cleanly`() {
        val stepper = RotaryPageStepper(
            stepThresholdPx = 28f,
            minStepIntervalMs = 130L,
            idleResetMs = 450L
        )

        assertEquals(0, stepper.onRotaryDelta(10f, nowMs = 100L))
        assertEquals(0, stepper.onRotaryDelta(10f, nowMs = 120L))
        assertEquals(1, stepper.onRotaryDelta(10f, nowMs = 140L))

        // Within minStepIntervalMs (130ms), further same-direction ticks do not double-jump
        assertEquals(0, stepper.onRotaryDelta(40f, nowMs = 200L))

        // After minStepIntervalMs passes, next tick advances another image
        assertEquals(1, stepper.onRotaryDelta(10f, nowMs = 280L))
    }

    @Test
    fun `notched bezel single click triggers step immediately`() {
        val stepper = RotaryPageStepper(
            stepThresholdPx = 28f,
            minStepIntervalMs = 130L,
            idleResetMs = 450L
        )

        assertEquals(1, stepper.onRotaryDelta(150f, nowMs = 100L))
        assertEquals(1, stepper.onRotaryDelta(150f, nowMs = 300L))
    }

    @Test
    fun `reversing crown direction clears residue and steps backward immediately`() {
        val stepper = RotaryPageStepper(
            stepThresholdPx = 28f,
            minStepIntervalMs = 130L,
            idleResetMs = 450L
        )

        assertEquals(1, stepper.onRotaryDelta(40f, nowMs = 100L))
        // Immediate reversal bypasses same-direction cooldown so back-and-forth feels snappy
        assertEquals(-1, stepper.onRotaryDelta(-32f, nowMs = 150L))
    }

    @Test
    fun `idle gap resets partial accumulated pixels`() {
        val stepper = RotaryPageStepper(
            stepThresholdPx = 28f,
            minStepIntervalMs = 130L,
            idleResetMs = 450L
        )

        assertEquals(0, stepper.onRotaryDelta(20f, nowMs = 100L))
        // After >450ms pause, old 20f residue is discarded so another 20f does not unexpectedly jump
        assertEquals(0, stepper.onRotaryDelta(20f, nowMs = 700L))
        assertEquals(1, stepper.onRotaryDelta(12f, nowMs = 740L))
    }
}
