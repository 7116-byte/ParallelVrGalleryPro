package com.local.parallelvrgallerypro

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Yuv420ArgbRowsTest {
    @Test fun planarAndInterleavedRowsMatchThePreviousPixelConverter() {
        val rows = Yuv420ArgbRows()
        val luma = TestPlane(buffer(3 + 22 * 24, 3), rowStride = 22, pixelStride = 1)
        val u = TestPlane(buffer(5 + 13 * 12, 5), rowStride = 13, pixelStride = 1)
        val v = TestPlane(buffer(2 + 12 * 12, 2), rowStride = 12, pixelStride = 1)
        val uv = buffer(4 + 24 * 12, 4)
        // Offset V by one byte and cap each plane at its actual last sample.
        val uBuffer = uv.duplicate().apply { limit(4 + 11 * 24 + 19) }
        val vBuffer = uv.duplicate().apply {
            position(5)
            limit(5 + 11 * 24 + 19)
        }
        val uInterleaved = TestPlane(uBuffer, rowStride = 24, pixelStride = 2)
        val vInterleaved = TestPlane(vBuffer, rowStride = 24, pixelStride = 2)
        verify(rows, 20, 21, 0, 0, luma, u, v, YuvRgb(1, false), expectedStripes = 2)
        verify(rows, 19, 21, 1, 3, luma, uInterleaved, vInterleaved, YuvRgb(6, true), expectedStripes = 2)
        verify(rows, 1, 1, 1, 1, luma, uInterleaved, vInterleaved, YuvRgb(4, false), expectedStripes = 1)
    }

    private data class TestPlane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int) {
        fun reader() = Yuv420ArgbRows.Plane(buffer, rowStride, pixelStride)

        fun sample(row: Int, column: Int): Int =
            buffer.get(buffer.position() + row * rowStride + column * pixelStride).toInt() and 255
    }

    private fun buffer(size: Int, offset: Int): ByteBuffer = ByteBuffer.allocateDirect(size).apply {
        for (index in 0 until size) put(index, ((index * 29 xor (index / 9 * 19)) and 255).toByte())
        position(offset)
    }

    private fun verify(
        rows: Yuv420ArgbRows,
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        y: TestPlane,
        u: TestPlane,
        v: TestPlane,
        conversion: YuvRgb,
        expectedStripes: Int,
    ) {
        val expected = IntArray(width * height) { index ->
            val sx = left + index % width
            val sy = top + index / width
            conversion.pixel(y.sample(sy, sx), u.sample(sy / 2, sx / 2), v.sample(sy / 2, sx / 2))
        }
        val actual = IntArray(expected.size)
        var stripes = 0
        rows.convert(width, height, left, top, y.reader(), u.reader(), v.reader(), conversion, {}) { pixels, row, count ->
            stripes++
            pixels.copyInto(actual, row * width, endIndex = count * width)
        }
        assertEquals(expectedStripes, stripes)
        assertArrayEquals(expected, actual)
    }
}
