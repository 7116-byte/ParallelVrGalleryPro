package com.local.parallelvrgallerypro

import org.junit.Assert.*
import org.junit.Test

class ModeProfilesTest {
    @Test fun allSixProfilesSurvivePersistenceIncludingFractionalDefaults() {
        DepthEdgeMode.entries.forEach { mode ->
            val profile = ModeVisualProfile.defaults(mode)
            assertEquals(profile, ModeVisualProfile.decode(profile.encode()))
        }
        assertNull(ModeVisualProfile.decode("1|NaN|3|10|false|6000|false|0|BILINEAR|SMOOTH_THEN_RESIZE|false"))
        assertNull(ModeVisualProfile.decode("incomplete"))
    }

    @Test fun switchingVisualProfilesKeepsHardwareAndQueuePreferences() {
        val shared = AppSettings(useGpu = true, forceGpuNoFallback = true, videoUseGpu = true,
            modelThreads = 7, prefetchWindow = 8, generationWorkers = 3)
        val v6 = ModeVisualProfile.defaults(DepthEdgeMode.LEGACY_V6).applyTo(shared)
        val savedV6 = ModeVisualProfile.capture(v6.copy(depthScale = 92f, maxLongEdge = 1920))
        val v10 = ModeVisualProfile.defaults(DepthEdgeMode.COMFY_LEGACY_V10).applyTo(v6)
        val restored = ModeVisualProfile.decode(savedV6.encode())!!.applyTo(v10)
        assertEquals(92f, restored.depthScale, 0f)
        assertEquals(1920, restored.maxLongEdge)
        assertTrue(restored.useGpu && restored.forceGpuNoFallback && restored.videoUseGpu)
        assertEquals(7, restored.modelThreads)
        assertEquals(8, restored.prefetchWindow)
        assertEquals(3, restored.generationWorkers)
    }

    @Test fun v6DefaultCompensatesHistoricalThreeByThreeInteriorGain() {
        val d = FloatArray(51 * 51) { 0.7f }
        val processed = DepthFilters.process(d, 51, 51, 3, false, DepthEdgeMode.LEGACY_V6)
        val actual = processed[25 * 51 + 25] * ModeVisualProfile.defaults(DepthEdgeMode.LEGACY_V6).scale
        assertEquals(0.7f * 50f, actual, 0.0001f)
        assertEquals(50f, ModeVisualProfile.defaults(DepthEdgeMode.COMFY_LEGACY_V10).scale, 0f)
        assertTrue(ModeVisualProfile.defaults(DepthEdgeMode.COMFY_LEGACY_V10).invert)
    }

    @Test fun externalDefaultMatchesV10DisparitySlopeAtActualEyeWidths() {
        for (mode in listOf(DepthEdgeMode.REFERENCE_V8, DepthEdgeMode.REFERENCE_V9)) {
            val params = ModeVisualProfile.defaults(mode).applyTo(AppSettings(edgeMode = mode)).toParams()
            for (width in listOf(560, 960, 1280, 1536, 1920, 4096)) {
                val externalSlope = width * params.effectiveScale(width) / 500f
                val comfySlope = 255f * 50f / width
                assertEquals(comfySlope, externalSlope, 0.00001f)
            }
            assertTrue(params.invertDepth) // Opposite static eye requires opposite depth direction.
        }
    }

    @Test fun changingOutputWidthCannotSilentlyUseAFixedExternalDefaultUnit() {
        val settings = ModeVisualProfile.defaults(DepthEdgeMode.REFERENCE_V9).applyTo(AppSettings(edgeMode = DepthEdgeMode.REFERENCE_V9))
        assertEquals(settings.toParams().effectiveScale(1280) / 4f, settings.toParams().effectiveScale(2560), 0.00001f)
        val custom = settings.copy(depthScale = 5f, matchV10Default = false).toParams()
        assertEquals(5f, custom.effectiveScale(1280), 0f)
        assertEquals(5f, custom.effectiveScale(2560), 0f)
    }

    @Test fun fractionalScalesAndCalibratedUnitsCannotCollideWithHistoricalCaches() {
        val original = VrGenerationParams(edgeMode = DepthEdgeMode.LEGACY_V6, depthScale = 112f)
        assertNotEquals(original.cacheVersion(), original.copy(depthScale = 112.5f).cacheVersion())
        assertNotEquals(original.cacheVersion(), original.copy(matchV10Default = true).cacheVersion())
        assertFalse(VrGenerationParams().cacheVersion().contains("_eq1050"))
        assertFalse(VrGenerationParams().cacheVersion().contains("_take"))
        assertEquals(6, DepthEdgeMode.entries.map { ModeVisualProfile.defaults(it).applyTo(AppSettings(edgeMode = it)).toParams().cacheVersion() }.toSet().size)
    }

    @Test fun queuedImageAndVideoSnapshotsKeepCalibrationAndDistinctResultSlot() {
        val params = ModeVisualProfile.defaults(DepthEdgeMode.REFERENCE_V8).applyTo(AppSettings(edgeMode = DepthEdgeMode.REFERENCE_V8, useGpu = true)).toParams().copy(resultVariant = "abcd1234")
        val video = VideoGenerationParams(params, params.modelThreads, params.useGpu)
        assertEquals(params, decodeVideoParams(video.encodeForQueue())!!.vr)
        val old = video.encodeForQueue().split(',').take(20).joinToString(",")
        assertFalse(decodeVideoParams(old)!!.vr.matchV10Default)
        assertEquals("", decodeVideoParams(old)!!.vr.resultVariant)
        assertEquals(DepthEdgeMode.REFERENCE_V8, decodeVideoParams(old)!!.vr.edgeMode)
    }
}
