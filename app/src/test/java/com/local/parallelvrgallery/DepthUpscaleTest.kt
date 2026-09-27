package com.local.parallelvrgallerypro

import kotlin.math.abs
import kotlin.math.floor
import org.junit.Assert.*
import org.junit.Test

class DepthUpscaleTest {
    private val ramp = DepthMap(floatArrayOf(0f, 0.5f, 0.5f, 1f), 2, 2)

    @Test fun bilinearProducesKnownRampWithoutEightBitQuantization() {
        val map = DepthResampling.resize(ramp, 3, 3, DepthInterpolation.BILINEAR)
        assertArrayEquals(floatArrayOf(0f, .25f, .5f, .25f, .5f, .75f, .5f, .75f, 1f), map.values, 0.000001f)
        val fine = DepthResampling.resize(DepthMap(floatArrayOf(.50001f, .50003f), 2, 1), 3, 1, DepthInterpolation.BILINEAR)
        assertEquals(.50002f, fine.values[1], .000001f)
    }

    @Test fun nearestOnlyUsesExistingDepthValues() {
        val map = DepthResampling.resize(ramp, 9, 7, DepthInterpolation.NEAREST)
        assertTrue(map.values.all { value -> ramp.values.any { it == value } })
        assertEquals(0f, map.values.first(), 0f)
        assertEquals(1f, map.values.last(), 0f)
    }

    @Test fun allInterpolatorsPreserveConstantMapsAndSinglePixels() {
        for (method in DepthInterpolation.entries) {
            for (map in listOf(DepthMap(floatArrayOf(.37f), 1, 1), DepthMap(FloatArray(12) { .37f }, 3, 4))) {
                assertTrue(DepthResampling.resize(map, 23, 17, method).values.all { abs(it - .37f) < .000001f })
            }
        }
    }

    @Test fun identityResizeDoesNotAlterOrCopyTheMap() {
        for (method in DepthInterpolation.entries) assertSame(ramp, DepthResampling.resize(ramp, 2, 2, method))
    }

    @Test fun bicubicMatchesIndependentTwoDimensionalReferenceAndClampsOvershoot() {
        val src = DepthMap(FloatArray(20) { ((it * 13) % 19) / 18f }, 5, 4)
        val actual = DepthResampling.resize(src, 17, 13, DepthInterpolation.BICUBIC)
        fun kernel(distance: Double): Double {
            val x = abs(distance)
            return when { x <= 1 -> 1.5 * x * x * x - 2.5 * x * x + 1
                x < 2 -> -.5 * x * x * x + 2.5 * x * x - 4 * x + 2
                else -> 0.0 }
        }
        for (y in 0 until 13) for (x in 0 until 17) {
            val sx = x * 4.0 / 16
            val sy = y * 3.0 / 12
            var expected = 0.0
            for (j in -1..2) for (i in -1..2) {
                val xi = floor(sx).toInt() + i
                val yi = floor(sy).toInt() + j
                expected += src.values[yi.coerceIn(0, 3) * 5 + xi.coerceIn(0, 4)] * kernel(sx - xi) * kernel(sy - yi)
            }
            assertEquals(expected.coerceIn(0.0, 1.0), actual.values[y * 17 + x].toDouble(), .000002)
        }
        assertTrue(actual.values.all { it.isFinite() && it in 0f..1f })
    }

    @Test fun narrowAndSingleOutputAxesAreWellDefined() {
        for (method in DepthInterpolation.entries) {
            val horizontal = DepthResampling.resize(ramp, 1, 9, method)
            val vertical = DepthResampling.resize(ramp, 9, 1, method)
            assertEquals(9, horizontal.values.size)
            assertArrayEquals(horizontal.values, vertical.values, .000001f)
        }
    }

    @Test fun processingOffIsBitIdenticalToExistingFilters() {
        val raw = DepthMap(FloatArray(35) { it / 35f }, 7, 5)
        for (mode in DepthEdgeMode.entries) for (blur in listOf(0, 1, 3, 5, 25)) for (invert in listOf(false, true)) {
            val params = VrGenerationParams(edgeMode = mode, blurRadius = blur, invertDepth = invert)
            val expected = DepthFilters.process(raw.values, 7, 5, blur, invert, mode)
            val actual = DepthPostProcessing.process(raw, 2000, 1000, params, 1)
            assertEquals(7, actual.width)
            assertEquals(5, actual.height)
            assertArrayEquals(expected, actual.values, 0f)
        }
    }

    @Test fun orderControlsWhichResolutionReceivesTheFilter() {
        val raw = DepthMap(floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f, 1f, 1f), 4, 2)
        val settings = DepthUpscaleSettings(enabled = true)
        val params = VrGenerationParams(edgeMode = DepthEdgeMode.CENTERED_V7, blurRadius = 3, invertDepth = true, depthUpscale = settings)
        val smoothFirst = DepthPostProcessing.process(raw, 12, 6, params, 1000)
        val resizeFirst = DepthPostProcessing.process(raw, 12, 6, params.copy(depthUpscale = settings.copy(order = DepthResizeOrder.RESIZE_THEN_SMOOTH)), 1000)
        val smooth = DepthMap(DepthFilters.process(raw.values, 4, 2, 3, true, params.edgeMode), 4, 2)
        assertArrayEquals(DepthResampling.resize(smooth, 12, 6, settings.interpolation).values, smoothFirst.values, 0f)
        val resized = DepthResampling.resize(raw, 12, 6, settings.interpolation)
        assertArrayEquals(DepthFilters.process(resized.values, 12, 6, 3, true, params.edgeMode), resizeFirst.values, 0f)
        assertFalse(smoothFirst.values.contentEquals(resizeFirst.values))
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f, 1f, 1f), raw.values, 0f)
    }

    @Test fun sizesRespectSourceAspectCustomLimitAndMemoryBudget() {
        val settings = DepthUpscaleSettings(enabled = true)
        assertEquals(4000 to 3000, DepthPostProcessing.targetSize(4000, 3000, settings, 20_000_000))
        assertEquals(2000 to 1500, DepthPostProcessing.targetSize(4000, 3000, settings.copy(maxLongEdge = 2000), 20_000_000))
        assertEquals(1000 to 750, DepthPostProcessing.targetSize(1000, 750, settings.copy(maxLongEdge = 8192), 20_000_000))
        for ((w, h) in listOf(4000 to 3000, 3000 to 4000, Int.MAX_VALUE to 1, 1 to Int.MAX_VALUE)) {
            for (limit in listOf(1L, 900L, 1_000_000L)) {
                val (rw, rh) = DepthPostProcessing.targetSize(w, h, settings, limit)
                assertTrue(rw > 0 && rh > 0 && rw <= w && rh <= h && rw.toLong() * rh <= limit)
            }
        }
    }

    @Test fun dimensionsAreValidatedBeforeAllocation() {
        assertThrows(IllegalArgumentException::class.java) { DepthMap(FloatArray(3), 2, 2) }
        assertThrows(IllegalArgumentException::class.java) { DepthResampling.resize(ramp, 0, 3, DepthInterpolation.BILINEAR) }
        assertThrows(IllegalArgumentException::class.java) { DepthPostProcessing.targetSize(0, 2, DepthUpscaleSettings(), 1) }
    }

    @Test fun offKeepsOldCacheKeysWhileEnabledOptionsStaySeparated() {
        val original = VrGenerationParams()
        assertEquals("depth_anything_v2.tflite_depthV6_s40_b3_f10_inv0_m6000_t4_gpu0_ACCURATE_UNSET_force0", original.visualGenerationVersion())
        assertEquals(original.cacheVersion(), original.copy(depthUpscale = DepthUpscaleSettings(maxLongEdge = 4096, interpolation = DepthInterpolation.BICUBIC)).cacheVersion())
        val versions = mutableSetOf<String>()
        for (mode in DepthInterpolation.entries) for (order in DepthResizeOrder.entries) for (edge in listOf(0, 2048)) {
            val updated = original.copy(depthUpscale = DepthUpscaleSettings(true, edge, mode, order))
            assertTrue(versions.add(updated.cacheVersion()))
            assertNotEquals(original.cacheVersion(), updated.cacheVersion())
            assertNotEquals(VideoGenerationParams(original).cacheVersion(), VideoGenerationParams(updated).cacheVersion())
            assertEquals(original.depthSessionKey(), updated.depthSessionKey())
        }
    }

    @Test fun videoQueueRoundTripRetainsUpscaleAndOrder() {
        for (mode in DepthInterpolation.entries) for (order in DepthResizeOrder.entries) {
            val settings = AppSettings(videoUseGpu = true, depthUpscale = DepthUpscaleSettings(true, 3072, mode, order))
            val params = settings.toVideoParams()
            val restored = requireNotNull(decodeVideoParams(params.encodeForQueue()))
            assertEquals(params, restored)
            assertEquals(params.cacheVersion(), restored.cacheVersion())
        }
    }

    @Test fun oldVideoQueuesRestoreWithoutEnablingNewProcessing() {
        val params = AppSettings(edgeMode = DepthEdgeMode.CENTERED_V7).toVideoParams()
        val previous = params.encodeForQueue().split(',').take(16).joinToString(",")
        val older = previous.split(',').take(15).joinToString(",")
        for (text in listOf(previous, older)) {
            val restored = requireNotNull(decodeVideoParams(text))
            assertFalse(restored.vr.depthUpscale.enabled)
            assertEquals(params.cacheVersion(), restored.cacheVersion())
        }
        assertNull(decodeVideoParams("broken"))
        assertEquals(DepthInterpolation.BILINEAR, DepthInterpolation.restore("unknown"))
        assertEquals(DepthResizeOrder.SMOOTH_THEN_RESIZE, DepthResizeOrder.restore(null))
    }
}
