package com.local.parallelvrgallerypro

import org.junit.Assert.*
import org.junit.Test

class StudioAndReferenceTest {
    @Test fun smallScreensAndLargeFontsNeverLoseAllColumns() {
        assertEquals(1, StudioLayout.columns(5, 240, 2f, true))
        assertEquals(2, StudioLayout.columns(5, 384, 1f, true))
        assertEquals(5, StudioLayout.columns(5, 1024, 1f, true))
        assertTrue(StudioLayout.columns(5, 384, 2f) < StudioLayout.columns(5, 384, 1f))
    }

    @Test fun scrollHeaderNeverMovesIntoContentOrBeyondItsHeight() {
        assertEquals(0f, StudioLayout.headerOffset(-1, 80), 0f)
        assertEquals(-30f, StudioLayout.headerOffset(30, 80), 0f)
        assertEquals(-80f, StudioLayout.headerOffset(Int.MAX_VALUE, 80), 0f)
    }

    @Test fun zeroDepthKeepsEverySourcePixelAndNoHole() {
        val source = IntArray(20) { it + 1 }
        val output = ReferenceProjection.project(source, 10, 2, floatArrayOf(0f), 1, 1, 50f)
        assertArrayEquals(source, output.pixels)
        assertTrue(output.holes.all { it == 0.toByte() })
        assertArrayEquals(IntArray(20) { it + 1 }, source)
    }

    @Test fun referenceScaleUsesImageWidthInsteadOfLegacyInverseWidth() {
        for (width in listOf(1000, 2000)) {
            val source = IntArray(width) { it + 1 }
            val output = ReferenceProjection.project(source, width, 1, floatArrayOf(1f), 1, 1, 5f)
            val shift = width / 100
            assertEquals(source[0], output.pixels[shift])
            assertTrue(output.holes.take(shift).all { it == 255.toByte() })
            assertEquals(source[width - shift - 1], output.pixels.last())
        }
    }

    @Test fun upstreamDescendingSplatKeepsTheLowerSourceXAtCollisions() {
        val depth = FloatArray(10)
        depth[0] = 1f
        val output = ReferenceProjection.project(IntArray(10) { it + 100 }, 10, 1, depth, 10, 1, 50f)
        assertEquals(100, output.pixels[1])
        assertEquals(255.toByte(), output.holes[0])
        assertEquals(0.toByte(), output.holes[1])
    }

    @Test fun nearestDepthAndEightBitQuantizationMatchReferenceRules() {
        val output = ReferenceProjection.project(IntArray(1000) { it }, 1000, 1, floatArrayOf(0f, 0.5f), 2, 1, 5f)
        // 0.5 becomes 127/255, hence a 4px shift, not rounded to 5px.
        assertEquals(500, output.pixels[504])
        assertEquals(255.toByte(), output.holes[500])
        assertEquals(499, output.pixels[499])
    }

    @Test fun referenceBypassesHistoricalBlurButPreservesRequestedInversion() {
        val values = floatArrayOf(0.1f, 0.4f, 0.9f)
        assertArrayEquals(values, DepthFilters.process(values, 3, 1, 25, false, DepthEdgeMode.REFERENCE_V8), 0f)
        assertArrayEquals(floatArrayOf(0.9f, 0.6f, 0.1f), DepthFilters.process(values, 3, 1, 25, true, DepthEdgeMode.REFERENCE_V8), 0.00001f)
        assertEquals(DepthEdgeMode.LEGACY_V6, VrGenerationParams().edgeMode)
        assertEquals(DepthEdgeMode.REFERENCE_V8, DepthEdgeMode.restore(null, "m_depthSSS22V8_s5"))
    }

    @Test fun referenceVideoQueueRoundTripAndCacheIsolation() {
        val legacy = VrGenerationParams(edgeMode = DepthEdgeMode.LEGACY_V6)
        val reference = legacy.copy(edgeMode = DepthEdgeMode.REFERENCE_V8, depthScale = 5f)
        val video = VideoGenerationParams(vr = reference)
        val restored = requireNotNull(decodeVideoParams(video.encodeForQueue()))
        assertEquals(reference.edgeMode, restored.vr.edgeMode)
        assertEquals(video.cacheVersion(), restored.cacheVersion())
        assertNotEquals(legacy.cacheVersion(), reference.cacheVersion())
        assertNotEquals(legacy.copy(edgeMode = DepthEdgeMode.CENTERED_V7).cacheVersion(), reference.cacheVersion())
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidReferenceStrength() {
        ReferenceProjection.project(intArrayOf(0), 1, 1, floatArrayOf(0f), 1, 1, Float.NaN)
    }
}
