package com.local.parallelvrgallerypro

import org.junit.Assert.*
import org.junit.Test

class ComfyModeTest {
    @Test fun modesAndOldResultsHaveDistinctCachesAndPreferenceMigrationLeavesChoicesAlone() {
        val keys = DepthEdgeMode.entries.map { VrGenerationParams(edgeMode = it).cacheVersion() }
        assertEquals(keys.size, keys.toSet().size)
        for (mode in listOf(DepthEdgeMode.COMFY_LEGACY_V10, DepthEdgeMode.COMFY_SUBPIXEL_V11)) {
            assertEquals(mode, DepthEdgeMode.forCurrentSettings(mode.name))
            assertEquals(mode, DepthEdgeMode.restore(null, "model_${mode.cacheVersion}_s60_"))
        }
    }
    @Test fun nodeCheckboxAccountsForTheDesktopEstimatorsOwnInversionWithoutPhoneBoxFilter() {
        val raw = floatArrayOf(.1f, .4f, .9f)
        for (mode in listOf(DepthEdgeMode.COMFY_LEGACY_V10, DepthEdgeMode.COMFY_SUBPIXEL_V11)) {
            assertArrayEquals(raw, DepthFilters.process(raw, 3, 1, 25, true, mode), 0f)
            assertArrayEquals(floatArrayOf(.9f, .6f, .1f), DepthFilters.process(raw, 3, 1, 25, false, mode), .00001f)
        }
    }
    @Test fun savedVideoJobsKeepModeStrengthInversionAndOptionalUpsampling() {
        for (mode in listOf(DepthEdgeMode.COMFY_LEGACY_V10, DepthEdgeMode.COMFY_SUBPIXEL_V11)) {
            val params = VrGenerationParams(edgeMode = mode, depthScale = 60f, blurRadius = 3, invertDepth = true,
                depthUpscale = DepthUpscaleSettings(true, 1024, DepthInterpolation.BICUBIC, DepthResizeOrder.RESIZE_THEN_SMOOTH))
            val job = VideoGenerationParams(vr = params)
            assertEquals(job, requireNotNull(decodeVideoParams(job.encodeForQueue())))
        }
    }
    @Test fun optionalUpsamplingContinuesToSeparateParameterVersions() {
        val params = VrGenerationParams(edgeMode = DepthEdgeMode.COMFY_SUBPIXEL_V11)
        val enabled = params.copy(depthUpscale = DepthUpscaleSettings(true, 1024))
        assertFalse(params.cacheVersion().contains("_du1_"))
        assertTrue(enabled.cacheVersion().contains("_du1_"))
        assertNotEquals(params.cacheVersion(), enabled.cacheVersion())
    }
}
