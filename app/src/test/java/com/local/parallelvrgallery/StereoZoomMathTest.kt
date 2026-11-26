package com.local.parallelvrgallerypro

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StereoZoomMathTest {
    private val width = 2000f
    private val height = 1000f
    private val ids = setOf(1L, 2L)

    @Test
    fun straddlingCenterAxisInHalfToThreeQuarterSpreadZoomsInAndPinchZoomsOut() {
        val session = StereoPinchSession()
        var state = StereoTransformState()
        // Finger 1 left of 1/2 (900), Finger 2 in 1/2..3/4 (1350): raw distance = 450, midpoint = 1125 (right eye local 125).
        state = session.updatePinch(state, ids, listOf(Offset(900f, 500f), Offset(1350f, 500f)), width, height, sbsMode = true)
        assertEquals(1f, state.scale, 1e-4f)

        // Spread fingers outward in small steps: must strictly increase scale (never shrink!).
        var prevScale = state.scale
        for (step in 1..6) {
            val delta = step * 25f
            state = session.updatePinch(
                state,
                ids,
                listOf(Offset(900f - delta, 500f), Offset(1350f + delta, 500f)),
                width,
                height,
                sbsMode = true,
            )
            assertTrue("Spread step $step must zoom IN: prev=$prevScale next=${state.scale}", state.scale > prevScale)
            prevScale = state.scale
        }
        val peakScale = state.scale
        assertTrue("Expected noticeable magnification, got $peakScale", peakScale > 1.5f)
        // Focal point was right eye local x=125 (left of eye center 500), so positive offset.x shifts image right to keep x=125 in place.
        assertTrue("Expected positive offset.x for left-side focal anchor, got ${state.offset.x}", state.offset.x > 0f)

        // Pinch fingers back inward: must strictly decrease scale (never enlarge!).
        for (step in 5 downTo 0) {
            val delta = step * 25f
            state = session.updatePinch(
                state,
                ids,
                listOf(Offset(900f - delta, 500f), Offset(1350f + delta, 500f)),
                width,
                height,
                sbsMode = true,
            )
            assertTrue("Pinch step $step must zoom OUT: prev=$prevScale next=${state.scale}", state.scale < prevScale)
            prevScale = state.scale
        }
        assertEquals(1f, state.scale, 0.02f)
        assertEquals(0f, state.offset.x, 1f)
        assertEquals(0f, state.offset.y, 1f)
    }

    @Test
    fun sameEyePinchKeepsExactPixelUnderMidpointStationary() {
        val session = StereoPinchSession()
        var state = StereoTransformState()
        // Both fingers inside right eye around local (300, 400) => screen (1300, 400).
        val eyeWidth = width / 2f
        val eyeCenter = Offset(eyeWidth / 2f, height / 2f)
        val localAnchor = Offset(300f, 400f)
        val anchorFromCenter = localAnchor - eyeCenter // (-200, -100)

        state = session.updatePinch(state, ids, listOf(Offset(1200f, 400f), Offset(1400f, 400f)), width, height, sbsMode = true)
        // Spread to 1.2x (within single-frame clamp) around exact same midpoint (1300, 400).
        state = session.updatePinch(state, ids, listOf(Offset(1180f, 400f), Offset(1420f, 400f)), width, height, sbsMode = true)
        assertEquals(1.2f, state.scale, 1e-3f)
        // Image coordinate under anchorFromCenter before was (-200, -100); after scaling it must still map to anchorFromCenter!
        val mappedAfter = anchorFromCenter * state.scale + state.offset
        assertEquals(anchorFromCenter.x, mappedAfter.x, 0.5f)
        assertEquals(anchorFromCenter.y, mappedAfter.y, 0.5f)
    }

    @Test
    fun midpointDriftingAcrossCenterDividerMidGestureHasNoJump() {
        val session = StereoPinchSession()
        var state = StereoTransformState(scale = 2f, offset = Offset.Zero)
        // Start with midpoint at x=995 (left eye, 5px left of center axis).
        state = session.updatePinch(state, ids, listOf(Offset(895f, 500f), Offset(1095f, 500f)), width, height, sbsMode = true)
        val beforeOffset = state.offset
        // Drift midpoint +10px right to x=1005 (crosses center divider 1000) with constant finger distance.
        state = session.updatePinch(state, ids, listOf(Offset(905f, 500f), Offset(1105f, 500f)), width, height, sbsMode = true)
        assertEquals(2f, state.scale, 1e-4f)
        assertEquals(beforeOffset.x + 10f, state.offset.x, 0.5f)
        assertEquals(beforeOffset.y, state.offset.y, 0.5f)
    }

    @Test
    fun pointerCountChangeReinitializesWithoutJump() {
        val session = StereoPinchSession()
        var state = StereoTransformState(scale = 2f, offset = Offset(40f, -20f))
        state = session.updatePinch(state, setOf(1L, 2L), listOf(Offset(300f, 500f), Offset(500f, 500f)), width, height, sbsMode = true)
        // Third finger touches far away — should re-baseline cleanly without snapping scale or offset!
        val afterThirdFinger = session.updatePinch(
            state,
            setOf(1L, 2L, 3L),
            listOf(Offset(300f, 500f), Offset(500f, 500f), Offset(1600f, 200f)),
            width,
            height,
            sbsMode = true,
        )
        assertEquals(state.scale, afterThirdFinger.scale, 1e-4f)
        assertEquals(state.offset, afterThirdFinger.offset)
    }
}
