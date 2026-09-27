package com.local.parallelvrgallerypro

import kotlin.math.max
import kotlin.math.min

enum class DepthEdgeMode(val cacheVersion: String) {
    LEGACY_V6("depthV6"),
    CENTERED_V7("depthV7");

    companion object {
        fun restore(name: String?, previousCacheVersion: String? = null): DepthEdgeMode =
            entries.firstOrNull { it.name == name }
                ?: if (previousCacheVersion?.contains("_depthV7_") == true) CENTERED_V7 else LEGACY_V6
    }
}

internal object DepthFilters {
    fun process(depth: FloatArray, width: Int, height: Int, diameter: Int, invert: Boolean, mode: DepthEdgeMode): FloatArray =
        when (mode) {
            DepthEdgeMode.LEGACY_V6 -> legacyV6(depth, width, height, diameter, invert)
            DepthEdgeMode.CENTERED_V7 -> boxMean(depth, width, height, diameter, invert)
        }

    // Preserve v1.0.06's asymmetric filter and gain, including its edge normalization.
    // A mathematically centered replacement changes established disparity/contours.
    fun legacyV6(depth: FloatArray, width: Int, height: Int, diameter: Int, invert: Boolean): FloatArray {
        require(width > 0 && height > 0 && depth.size == width * height)
        if (diameter <= 0) return if (invert) FloatArray(depth.size) { 1f - depth[it] } else depth.copyOf()
        val r = diameter.coerceAtLeast(1) / 2
        val horizontal = FloatArray(depth.size)
        val output = FloatArray(depth.size)
        for (y in 0 until height) {
            var sum = 0f
            for (x in 0 until width) {
                sum += depth[y * width + x]
                if (x > r) sum -= depth[y * width + x - r - 1]
                val count = minOf(x + r + 1, width) - maxOf(0, x - r)
                horizontal[y * width + x] = sum / count.toFloat()
            }
        }
        for (x in 0 until width) {
            var sum = 0f
            for (y in 0 until height) {
                sum += horizontal[y * width + x]
                if (y > r) sum -= horizontal[(y - r - 1) * width + x]
                val count = minOf(y + r + 1, height) - maxOf(0, y - r)
                val value = sum / count.toFloat()
                output[y * width + x] = if (invert) 1f - value else value
            }
        }
        return output
    }

    fun boxMean(depth: FloatArray, width: Int, height: Int, diameter: Int, invert: Boolean): FloatArray {
        require(width > 0 && height > 0 && depth.size == width * height)
        val r = diameter.coerceAtLeast(0) / 2
        if (r == 0) return FloatArray(depth.size) { if (invert) 1f - depth[it] else depth[it] }
        val horizontal = FloatArray(depth.size)
        val output = FloatArray(depth.size)
        for (y in 0 until height) {
            var sum = 0.0
            for (x in 0..min(r, width - 1)) sum += depth[y * width + x]
            for (x in 0 until width) {
                horizontal[y * width + x] = (sum / (min(width - 1, x + r) - max(0, x - r) + 1)).toFloat()
                if (x - r >= 0) sum -= depth[y * width + x - r]
                if (x + r + 1 < width) sum += depth[y * width + x + r + 1]
            }
        }
        for (x in 0 until width) {
            var sum = 0.0
            for (y in 0..min(r, height - 1)) sum += horizontal[y * width + x]
            for (y in 0 until height) {
                val mean = (sum / (min(height - 1, y + r) - max(0, y - r) + 1)).toFloat()
                output[y * width + x] = if (invert) 1f - mean else mean
                if (y - r >= 0) sum -= horizontal[(y - r) * width + x]
                if (y + r + 1 < height) sum += horizontal[(y + r + 1) * width + x]
            }
        }
        return output
    }
}
