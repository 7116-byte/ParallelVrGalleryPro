package com.local.parallelvrgallerypro

import org.junit.Assert.*
import org.junit.Test

class ReferenceRevisionTest {
    @Test fun onlyTheBrokenReferencePreferenceMovesToTheCorrectedMode() {
        assertEquals(DepthEdgeMode.REFERENCE_V9, DepthEdgeMode.forCurrentSettings("REFERENCE_V8"))
        for (mode in listOf(DepthEdgeMode.LEGACY_V6, DepthEdgeMode.CENTERED_V7)) {
            assertEquals(mode, DepthEdgeMode.forCurrentSettings(mode.name))
        }
    }
    @Test fun oldAndCorrectedResultsNeverShareAKey() {
        val old = VrGenerationParams(edgeMode = DepthEdgeMode.REFERENCE_V8)
        val new = old.copy(edgeMode = DepthEdgeMode.REFERENCE_V9)
        assertNotEquals(old.cacheVersion(), new.cacheVersion())
        assertEquals(DepthEdgeMode.REFERENCE_V8, DepthEdgeMode.restore(null, "m_depthSSS22V8_s5_"))
        assertEquals(DepthEdgeMode.REFERENCE_V9, DepthEdgeMode.restore(null, "m_depthSSS22V9_s5_"))
    }
    @Test fun persistedVideoJobsKeepTheirOwnRendererRevision() {
        for (mode in listOf(DepthEdgeMode.REFERENCE_V8, DepthEdgeMode.REFERENCE_V9)) {
            val job = VideoGenerationParams(vr = VrGenerationParams(edgeMode = mode))
            val restored = requireNotNull(decodeVideoParams(job.encodeForQueue()))
            assertEquals(mode, restored.vr.edgeMode)
            assertEquals(job.cacheVersion(), restored.cacheVersion())
        }
    }
    @Test fun correctedReferencePreservesFloatDepthUntilTheFinalRestorationStage() {
        val raw = floatArrayOf(.20001f, .20002f, .8f)
        assertArrayEquals(raw, DepthFilters.process(raw, 3, 1, 25, false, DepthEdgeMode.REFERENCE_V9), 0f)
        assertEquals(.79998f, DepthFilters.process(raw, 3, 1, 25, true, DepthEdgeMode.REFERENCE_V9)[1], .000001f)
    }
}
