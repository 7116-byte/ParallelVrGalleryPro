package com.local.parallelvrgallerypro

import org.junit.Assert.*
import org.junit.Test

class DepthDirectionTest {
    @Test fun singleControlAndPreviewCombineHistoricalDirectionFieldsWithoutChangingThem() {
        for (mode in DepthEdgeMode.entries) {
            val base = ModeVisualProfile.defaults(mode).invert
            assertFalse(DepthDirection.reversed(mode, false, base))
            assertTrue(DepthDirection.reversed(mode, true, base))
            assertTrue(DepthDirection.reversed(mode, false, !base))
            assertFalse(DepthDirection.reversed(mode, true, !base))
        }
    }
    @Test fun referenceRasterInversionReversesActualNearFarPixelDisparity() {
        val width = 1000
        val near = 0xff00ff00.toInt(); val far = 0xffff0000.toInt()
        val pixels = IntArray(width) { 0xff303030.toInt() }.apply { this[200] = near; this[700] = far }
        val raw = FloatArray(width) { if (it < 500) 0.9f else 0.1f }
        for (reverse in listOf(false, true)) {
            val canonical = DepthDirection.modelDepth(raw, reverse)
            val processed = FloatArray(width) { 1f - canonical[it] }
            val projected = ReferenceProjection.project(pixels,width,1,processed,width,1,5f).pixels
            val nearDisparity = 200 - projected.indexOf(near)
            val farDisparity = 700 - projected.indexOf(far)
            assertEquals(!reverse, nearDisparity > farDisparity)
        }
    }
    @Test fun inputPolarityIsExplicitAndDoesNotMutateInferenceOutput() {
        val raw = floatArrayOf(0.1f, 0.3f, 0.9f)
        assertSame(raw, DepthDirection.modelDepth(raw, false))
        assertArrayEquals(floatArrayOf(0.9f, 0.7f, 0.1f), DepthDirection.modelDepth(raw, true), 0.00001f)
        assertArrayEquals(floatArrayOf(0.1f, 0.3f, 0.9f), raw, 0f)
    }

    @Test fun defaultRelativeNearFarDirectionAccountsForTheProjectedEye() {
        val near = 0.9f; val far = 0.1f
        for (mode in DepthEdgeMode.entries) {
            val defaults = ModeVisualProfile.defaults(mode)
            val map = DepthFilters.process(floatArrayOf(far, near), 2, 1, 0, defaults.invert, mode)
            val eyeSign = if (mode.isReference) -1f else 1f
            assertTrue("Near should have greater left-minus-right disparity in $mode", eyeSign * (map[1] - map[0]) > 0f)
            assertEquals(!mode.isReference, DepthDirection.projectionNearWhite(mode, defaults.invert))
        }
    }

    @Test fun inputPolarityHasItsOwnCacheAndQueueIdentity() {
        val legacy = VrGenerationParams()
        assertFalse(legacy.cacheVersion().contains("_di1"))
        val reversed = legacy.copy(invertModelDepth = true)
        assertNotEquals(legacy.cacheVersion(), reversed.cacheVersion())
        val queued = VideoGenerationParams(reversed, reversed.modelThreads)
        assertTrue(decodeVideoParams(queued.encodeForQueue())!!.vr.invertModelDepth)
        val old = queued.encodeForQueue().split(',').take(22).joinToString(",")
        assertFalse(decodeVideoParams(old)!!.vr.invertModelDepth)
    }

    @Test fun perModeInputPolaritySurvivesProfileMigrationAndColdDecode() {
        val old = "1|50.0|3|10|true|1280|false|0|BILINEAR|SMOOTH_THEN_RESIZE|true"
        assertFalse(ModeVisualProfile.decode(old)!!.invertModelDepth)
        val custom = ModeVisualProfile.defaults(DepthEdgeMode.COMFY_LEGACY_V10).copy(invertModelDepth = true)
        assertEquals(custom, ModeVisualProfile.decode(custom.encode()))
        assertTrue(custom.applyTo(AppSettings()).toParams().invertModelDepth)
    }
}
