package com.local.parallelvrgallerypro

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.FloatBuffer

class QnnDepthContentTest {
    @Test fun onlyVisibleDepthParticipatesInNormalization() {
        val geometry = QnnInputGeometry(14, 28, 3, 2, 5, 13)
        val tensor = FloatArray(14 * 28) { Float.NaN }
        val content = floatArrayOf(20f, 30f, 40f, 50f, 60f, 70f)
        content.copyInto(tensor, 13 * 14 + 5, 0, 3)
        content.copyInto(tensor, 14 * 14 + 5, 3, 6)
        val buffer = FloatBuffer.wrap(tensor)

        val cropped = readQnnDepthContent(buffer, geometry)
        assertArrayEquals(content, cropped, 0f)
        normalizeQnnDepth(cropped)
        assertArrayEquals(floatArrayOf(0f, .2f, .4f, .6f, .8f, 1f), cropped, 1e-6f)
        assertEquals(0, buffer.position())
    }

    @Test fun unpaddedRectangleKeepsAllRowsInTensorOrder() {
        val geometry = QnnInputGeometry(28, 14, 28, 14, 0, 0)
        val values = FloatArray(28 * 14) { it.toFloat() }
        assertArrayEquals(values, readQnnDepthContent(FloatBuffer.wrap(values), geometry), 0f)
    }

    @Test fun clockwiseInputRestoresLandscapeDepthCornersAndRows() {
        val portraitModel = floatArrayOf(4f, 1f, 5f, 2f, 6f, 3f)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f),
            rotateQnnDepthCounterClockwise(portraitModel, width = 2, height = 3), 0f)
    }

    @Test fun counterClockwiseTurnKeepsEveryFloatAcrossPartialTiles() {
        val originalWidth = 39
        val originalHeight = 35
        val original = FloatArray(originalWidth * originalHeight) { (it - 1000) / 16f }
        val portrait = FloatArray(original.size)
        for (y in 0 until originalHeight) for (x in 0 until originalWidth) {
            portrait[x * originalHeight + originalHeight - 1 - y] = original[y * originalWidth + x]
        }
        assertArrayEquals(original,
            rotateQnnDepthCounterClockwise(portrait, width = originalHeight, height = originalWidth), 0f)
    }
}
