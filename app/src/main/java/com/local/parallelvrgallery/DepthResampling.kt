package com.local.parallelvrgallerypro

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class DepthInterpolation(val code: String) {
    NEAREST("n"), BILINEAR("l"), BICUBIC("c");

    companion object {
        fun restore(value: String?) = entries.firstOrNull { it.name == value } ?: BILINEAR
    }
}

enum class DepthResizeOrder(val code: String) {
    SMOOTH_THEN_RESIZE("sr"), RESIZE_THEN_SMOOTH("rs");

    companion object {
        fun restore(value: String?) = entries.firstOrNull { it.name == value } ?: SMOOTH_THEN_RESIZE
    }
}

data class DepthUpscaleSettings(
    val enabled: Boolean = false,
    val maxLongEdge: Int = 0,
    val interpolation: DepthInterpolation = DepthInterpolation.BILINEAR,
    val order: DepthResizeOrder = DepthResizeOrder.SMOOTH_THEN_RESIZE,
) {
    val boundedLongEdge: Int get() = if (maxLongEdge <= 0) 0 else maxLongEdge.coerceIn(256, 8192)
    fun cacheSuffix(): String = if (!enabled) "" else "_du1_m${boundedLongEdge}_i${interpolation.code}_o${order.code}"
}

internal data class DepthMap(val values: FloatArray, val width: Int, val height: Int) {
    init { require(width > 0 && height > 0 && width.toLong() * height == values.size.toLong()) }
}

internal object DepthResampling {
    // Endpoint alignment matches the existing projection's full-image depth coordinates.
    private data class Axis(val indices: IntArray, val weights: FloatArray, val taps: Int)

    private fun axis(input: Int, output: Int, method: DepthInterpolation): Axis {
        val taps = when (method) { DepthInterpolation.NEAREST -> 1; DepthInterpolation.BILINEAR -> 2; DepthInterpolation.BICUBIC -> 4 }
        val indices = IntArray(output * taps)
        val weights = FloatArray(output * taps)
        for (i in 0 until output) {
            val position = if (output == 1) (input - 1) / 2.0 else i.toDouble() * (input - 1) / (output - 1)
            val base = floor(position).toInt()
            val t = (position - base).toFloat()
            for (k in 0 until taps) {
                val source = when (method) {
                    DepthInterpolation.NEAREST -> floor(position + 0.5).toInt()
                    DepthInterpolation.BILINEAR -> base + k
                    DepthInterpolation.BICUBIC -> base + k - 1
                }
                indices[i * taps + k] = source.coerceIn(0, input - 1)
                weights[i * taps + k] = when (method) {
                    DepthInterpolation.NEAREST -> 1f
                    DepthInterpolation.BILINEAR -> if (k == 0) 1f - t else t
                    DepthInterpolation.BICUBIC -> when (k) {
                        0 -> -0.5f * t + t * t - 0.5f * t * t * t
                        1 -> 1f - 2.5f * t * t + 1.5f * t * t * t
                        2 -> 0.5f * t + 2f * t * t - 1.5f * t * t * t
                        else -> -0.5f * t * t + 0.5f * t * t * t
                    }
                }
            }
        }
        return Axis(indices, weights, taps)
    }

    fun resize(source: DepthMap, width: Int, height: Int, method: DepthInterpolation): DepthMap {
        require(width > 0 && height > 0 && width.toLong() * height <= Int.MAX_VALUE)
        require(width.toLong() * source.height <= Int.MAX_VALUE)
        if (source.width == width && source.height == height) return source
        val xs = axis(source.width, width, method)
        val ys = axis(source.height, height, method)
        val horizontal = FloatArray(width * source.height)
        for (y in 0 until source.height) {
            for (x in 0 until width) {
                var value = 0f
                for (k in 0 until xs.taps) value += source.values[y * source.width + xs.indices[x * xs.taps + k]] * xs.weights[x * xs.taps + k]
                horizontal[y * width + x] = value
            }
        }
        val result = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var value = 0f
                for (k in 0 until ys.taps) value += horizontal[ys.indices[y * ys.taps + k] * width + x] * ys.weights[y * ys.taps + k]
                // Cubic interpolation can overshoot a near/far discontinuity.
                result[y * width + x] = value.coerceIn(0f, 1f)
            }
        }
        return DepthMap(result, width, height)
    }
}

internal object DepthPostProcessing {
    fun targetSize(sourceWidth: Int, sourceHeight: Int, settings: DepthUpscaleSettings, maxPixels: Long): Pair<Int, Int> {
        require(sourceWidth > 0 && sourceHeight > 0 && maxPixels > 0)
        val edge = max(sourceWidth, sourceHeight)
        val requestedScale = if (settings.boundedLongEdge == 0) 1.0 else min(1.0, settings.boundedLongEdge.toDouble() / edge)
        val memoryScale = min(1.0, sqrt(maxPixels.toDouble() / (sourceWidth.toLong() * sourceHeight)))
        val scale = min(requestedScale, memoryScale)
        val width = max(1, floor(sourceWidth * scale).toInt())
        val height = max(1, floor(sourceHeight * scale).toInt())
        return min(width.toLong(), maxPixels / height).coerceAtLeast(1).toInt() to height.coerceAtMost(maxPixels.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    fun process(raw: DepthMap, sourceWidth: Int, sourceHeight: Int, params: VrGenerationParams, maxPixels: Long): DepthMap {
        fun smooth(map: DepthMap) = DepthMap(
            DepthFilters.process(map.values, map.width, map.height, params.blurRadius, params.invertDepth, params.edgeMode),
            map.width, map.height,
        )
        if (!params.depthUpscale.enabled) return smooth(raw)
        val (width, height) = targetSize(sourceWidth, sourceHeight, params.depthUpscale, maxPixels)
        fun resize(map: DepthMap) = DepthResampling.resize(map, width, height, params.depthUpscale.interpolation)
        return when (params.depthUpscale.order) {
            DepthResizeOrder.SMOOTH_THEN_RESIZE -> resize(smooth(raw))
            DepthResizeOrder.RESIZE_THEN_SMOOTH -> smooth(resize(raw))
        }
    }
}
