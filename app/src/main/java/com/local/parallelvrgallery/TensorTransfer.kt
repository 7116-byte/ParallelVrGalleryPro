package com.local.parallelvrgallerypro

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.min

internal data class DepthRunResult(val values: FloatArray, val range: Float)

/** Bulk I/O; normalization executes in Rust (zero-copy DirectByteBuffer when native-ordered). */
internal object TensorTransfer {
    private val channel = FloatArray(256) { it / 255f }

    fun packRgb(pixels: IntArray, scratch: FloatArray, target: FloatBuffer) {
        require(scratch.size >= 3 && scratch.size % 3 == 0 && target.capacity() == pixels.size * 3)
        target.clear()
        var from = 0
        while (from < pixels.size) {
            val count = min(scratch.size / 3, pixels.size - from)
            var offset = 0
            for (i in from until from + count) {
                val pixel = pixels[i]
                scratch[offset++] = channel[(pixel ushr 16) and 255]
                scratch[offset++] = channel[(pixel ushr 8) and 255]
                scratch[offset++] = channel[pixel and 255]
            }
            target.put(scratch, 0, count * 3)
            from += count
        }
    }

    fun readNormalized(buffer: ByteBuffer, count: Int): DepthRunResult {
        require(count > 0 && buffer.capacity() == count * 4)
        val values = FloatArray(count)
        if (buffer.isDirect && buffer.order() == ByteOrder.nativeOrder()) {
            val rawRange = RustCore.readNormalizedDirect(buffer, count, values)
            return DepthRunResult(values, rawRange)
        }
        buffer.rewind()
        buffer.asFloatBuffer().get(values)
        val rawRange = RustCore.normalizeModelDepth(values, strict = false)
        return DepthRunResult(values, rawRange)
    }
}
