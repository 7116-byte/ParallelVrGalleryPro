package com.local.parallelvrgallerypro

import org.junit.Assert.*
import org.junit.Test

class ComfySubpixelProjectionTest {
    @Test fun zeroDepthKeepsEverySourcePixelAndRowsIndependent() {
        val source = IntArray(255 * 3) { 0xff000000.toInt() or (it * 13 and 0xffffff) }
        assertArrayEquals(source, ComfySubpixelProjection.project(source, 255, 3, FloatArray(source.size), 60f))
    }
    @Test fun uniformHalfPixelMotionUsesBilinearCoverageInsteadOfRoundingTheShift() {
        val source = IntArray(255) { 0xff000000.toInt() or ((it and 1) * 100 shl 16) }
        val output = ComfySubpixelProjection.project(source, 255, 1, FloatArray(255) { .5f }, 1f)
        for (x in 1 until 255) assertEquals(50, output[x] ushr 16 and 255)
        assertEquals(source[0], output[0])
    }
    @Test fun constantSurfacesDoNotCreateBlackHolesAtHighDisplacementOrTinyWidths() {
        for (w in listOf(1, 2, 10, 255, 1280)) {
            val source = IntArray(w * 2) { 0xffc06030.toInt() }
            assertArrayEquals(source, ComfySubpixelProjection.project(source, w, 2, FloatArray(w * 2) { .7f }, 60f))
        }
    }
    @Test fun doesNotModifySourceOrFloatDepth() {
        val source = IntArray(255) { 0xff8090a0.toInt() }; val copy = source.copyOf()
        val depth = FloatArray(255) { it / 255f }; val d = depth.copyOf()
        ComfySubpixelProjection.project(source, 255, 1, depth, 60f)
        assertArrayEquals(copy, source); assertArrayEquals(d, depth, 0f)
    }
    @Test fun foregroundWinsOcclusionAndFractionalEdgeDoesNotStretchIntoTheBackground() {
        val background = 0xff0000ff.toInt(); val foreground = 0xffff0000.toInt()
        val source = IntArray(255) { background }; source[10] = foreground
        val depth = FloatArray(255); depth[10] = 1f
        assertEquals(foreground, ComfySubpixelProjection.project(source, 255, 1, depth, 1f)[11])
        depth[10] = .5f
        val refined = ComfySubpixelProjection.project(source, 255, 1, depth, 1f)
        assertEquals(128, refined[11] ushr 16 and 255)
        assertEquals(128, refined[11] and 255)
        assertEquals(background, refined[12])
    }
}
