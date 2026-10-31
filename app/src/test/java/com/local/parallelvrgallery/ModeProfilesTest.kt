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

    @Test fun fixedDefaultsStayVisibleAndIndependentOfImageWidth() {
        val expected = listOf(100f,45f,3.5f,3.5f,50f,50f)
        DepthEdgeMode.entries.forEachIndexed { index, mode ->
            val profile = ModeVisualProfile.defaults(mode)
            assertEquals(expected[index], profile.scale, 0f)
            assertFalse(profile.matchV10Default)
            val params = profile.applyTo(AppSettings(edgeMode = mode)).toParams()
            for (width in listOf(560,1280,4096)) assertEquals(profile.scale, params.effectiveScale(width), 0f)
        }
    }

    @Test fun migratingAutoProfilesPreservesOtherVisualControls() {
        val old = ModeVisualProfile(112.5f, blur=9, maxLongEdge=1920, matchV10Default=true)
        val converted = ModeVisualProfile.editable(old, DepthEdgeMode.LEGACY_V6)
        assertEquals(100f, converted.scale, 0f)
        assertEquals(9, converted.blur)
        assertEquals(1920, converted.maxLongEdge)
        assertFalse(converted.matchV10Default)
    }

    @Test fun explicitUserValuesNeverMigrateToTheBuiltInDefault() {
        val custom = ModeVisualProfile(27.25f, invertModelDepth=true)
        assertSame(custom, ModeVisualProfile.editable(custom, DepthEdgeMode.LEGACY_V6))
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
