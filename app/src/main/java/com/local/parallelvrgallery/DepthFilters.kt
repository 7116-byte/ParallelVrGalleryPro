package com.local.parallelvrgallerypro

import kotlin.math.max
import kotlin.math.min

internal object DepthFilters {
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
