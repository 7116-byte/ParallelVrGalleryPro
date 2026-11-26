package com.local.parallelvrgallerypro

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

/** Direct JVM contract tests proving all tests execute against the real compiled Rust JNI library. */
class RustCoreContractTest {
    @Test fun hostRustNativeLibraryIsLoaded() {
        val info = RustCore.abiInfo()
        assertTrue("Expected native pvg_core info, got: $info", info.startsWith("pvg_core arch="))
    }

    @Test fun extractQnnDepthDirectMatchesCroppedAndRotatedStrictNormalization() {
        val w = 378
        val h = 672
        val raw = FloatArray(w * h) { i -> ((i * 73) % 997) / 100.0f - 2.5f }
        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        buf.asFloatBuffer().put(raw)

        val directRotated = RustCore.extractQnnDepthDirect(buf, w, h, w, h, 0, 0, rotateCcw = true)
        val expected = rotateQnnDepthCounterClockwise(raw, w, h).also(::normalizeQnnDepth)
        assertArrayEquals(expected, directRotated, 0f)

        val directCropped = RustCore.extractQnnDepthDirect(buf, w, h, 100, 80, 12, 24, rotateCcw = false)
        val expectedCropped = FloatArray(100 * 80) { idx ->
            val y = idx / 100
            val x = idx % 100
            raw[(y + 24) * w + (x + 12)]
        }.also(::normalizeQnnDepth)
        assertArrayEquals(expectedCropped, directCropped, 0f)
    }

    @Test fun phoneStereoMatchesOriginalEndpointFormula() {
        val w = 64
        val h = 16
        val dw = 19
        val dh = 11
        val rng = java.util.Random(42)
        val src = IntArray(w * h) { rng.nextInt() or 0xff000000.toInt() }
        val depth = FloatArray(dw * dh) { rng.nextFloat() }
        val actual = RustCore.projectPhoneStereo(src, depth, w, h, dw, dh, 80f, 10)
        val expected = src.copyOf()
        val depthScaling = 80f / w.toFloat()
        for (y in 0 until h) {
            val depthBase = (y.toLong() * (dh - 1) / (h - 1)).toInt() * dw
            for (x in w - 1 downTo 0) {
                val dx = (x.toLong() * (dw - 1) / (w - 1)).toInt()
                val shift = (depth[depthBase + dx].coerceIn(0f, 1f) * 255f * depthScaling).toInt().coerceIn(0, w - 1)
                for (offset in 0 until 10) {
                    expected[y * w + (x + shift + offset).coerceIn(0, w - 1)] = src[y * w + x]
                }
            }
        }
        assertArrayEquals(expected, actual)
    }

    @Test fun packTfliteRgbDirectWritesExactRgbFloats() {
        val pixels = intArrayOf(0xffff8000.toInt(), 0xff102030.toInt(), 0xff00ff40.toInt())
        val buf = ByteBuffer.allocateDirect(pixels.size * 3 * 4).order(ByteOrder.nativeOrder())
        assertTrue(RustCore.packTfliteRgbDirect(pixels, buf))
        val out = FloatArray(pixels.size * 3)
        buf.asFloatBuffer().get(out)
        assertEquals(255 / 255f, out[0], 0f)
        assertEquals(128 / 255f, out[1], 0f)
        assertEquals(0 / 255f, out[2], 0f)
        assertEquals(0x10 / 255f, out[3], 0f)
        assertEquals(0x20 / 255f, out[4], 0f)
        assertEquals(0x30 / 255f, out[5], 0f)
    }
}
