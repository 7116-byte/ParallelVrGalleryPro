package com.local.parallelvrgallerypro

import org.junit.Assert.*
import org.junit.Test

class ComfyLegacyProjectionTest {
    @Test fun matchesIndependentOriginalPixelLoopIncludingOverwriteAndClippedFill() {
        val random = java.util.Random(8492)
        for (w in listOf(1, 2, 9, 37, 128, 1280)) for (scale in listOf(1f, 40f, 60f, 100f)) {
            val h = 3; val source = IntArray(w * h) { random.nextInt() }
            val depth = FloatArray(w * h) { random.nextFloat() }
            val expected = source.copyOf()
            for (y in 0 until h) for (x in 0 until w) {
                val shift = ((depth[y * w + x] * 255f).toInt() * (scale.toDouble() / w)).toInt()
                val target = (x + shift).coerceIn(0, w - 1)
                for (offset in 0 until shift + 10) {
                    if (target + offset >= w) break
                    expected[y * w + target + offset] = source[y * w + x]
                }
            }
            assertArrayEquals("width=$w scale=$scale", expected, ComfyLegacyProjection.project(source, w, h, depth, scale))
        }
    }
    @Test fun doesNotAlterTheOriginalEyeOrSourceDepth() {
        val source = IntArray(20) { it }; val depth = FloatArray(20) { it / 20f }
        val copy = depth.copyOf(); val result = ComfyLegacyProjection.project(source, 20, 1, depth, 60f)
        assertArrayEquals(IntArray(20) { it }, source); assertArrayEquals(copy, depth, 0f)
        assertFalse(source === result)
    }
    @Test fun usesQuantizedDepthAndOriginalInverseWidthUnits() {
        val width = 1280; val depth = FloatArray(width) { 1f }; val source = IntArray(width) { it }
        val forty = ComfyLegacyProjection.project(source, width, 1, depth, 40f)
        val sixty = ComfyLegacyProjection.project(source, width, 1, depth, 60f)
        assertEquals(0, forty[7]); assertEquals(1, forty[8])
        assertEquals(0, sixty[11]); assertEquals(1, sixty[12])
    }
}
