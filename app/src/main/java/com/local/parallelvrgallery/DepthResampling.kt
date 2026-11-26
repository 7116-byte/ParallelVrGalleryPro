package com.local.parallelvrgallerypro

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

internal data class DepthMap @JvmOverloads constructor(val values: FloatArray, val width: Int, val height: Int, val projectionScale: Float? = null) {
    init { require(width > 0 && height > 0 && width.toLong() * height == values.size.toLong()) }
}

/** Single-source Rust endpoint-aligned depth resampling. */
internal object DepthResampling {
    fun resize(source: DepthMap, width: Int, height: Int, method: DepthInterpolation): DepthMap {
        require(width > 0 && height > 0 && width.toLong() * height <= Int.MAX_VALUE)
        require(width.toLong() * source.height <= Int.MAX_VALUE)
        if (source.width == width && source.height == height) return source
        return DepthMap(
            RustCore.resizeDepth(source.values, source.width, source.height, width, height, method.ordinal),
            width,
            height,
        )
    }
}

/** Single-source Rust combined depth post-processing (filter + optional upscale in one JNI call). */
internal object DepthPostProcessing {
    fun targetSize(sourceWidth: Int, sourceHeight: Int, settings: DepthUpscaleSettings, maxPixels: Long): Pair<Int, Int> {
        val packed = RustCore.depthTargetSize(sourceWidth, sourceHeight, settings.boundedLongEdge, maxPixels)
        return (packed ushr 32).toInt() to packed.toInt()
    }

    fun process(raw: DepthMap, sourceWidth: Int, sourceHeight: Int, params: VrGenerationParams, maxPixels: Long): DepthMap {
        val (width, height) = if (params.depthUpscale.enabled) {
            targetSize(sourceWidth, sourceHeight, params.depthUpscale, maxPixels)
        } else {
            raw.width to raw.height
        }
        val values = RustCore.postProcessDepth(
            raw.values,
            raw.width,
            raw.height,
            width,
            height,
            params.edgeMode.ordinal,
            params.blurRadius,
            params.depthUpscale.interpolation.ordinal,
            params.invertDepth,
            params.depthUpscale.order == DepthResizeOrder.RESIZE_THEN_SMOOTH,
        )
        return DepthMap(values, width, height)
    }
}
