package com.local.parallelvrgallerypro

/** Visual controls belong to a projection mode. Hardware and queue controls do not. */
data class ModeVisualProfile(
    val scale: Float,
    val blur: Int = 3,
    val fill: Int = 10,
    val invert: Boolean = false,
    val maxLongEdge: Int = 6000,
    val upscale: DepthUpscaleSettings = DepthUpscaleSettings(),
    val matchV10Default: Boolean = false,
    val invertModelDepth: Boolean = false,
) {
    fun applyTo(settings: AppSettings): AppSettings = settings.copy(
        depthScale = scale, blurRadius = blur, fillRadius = fill, invertDepth = invert,
        maxLongEdge = maxLongEdge, depthUpscale = upscale, matchV10Default = matchV10Default,
        invertModelDepth = invertModelDepth,
    )

    fun encode(): String = listOf("2", scale, blur, fill, invert, maxLongEdge, upscale.enabled,
        upscale.boundedLongEdge, upscale.interpolation.name, upscale.order.name, matchV10Default, invertModelDepth).joinToString("|")

    companion object {
        const val REFERENCE_WIDTH = 1280
        // Interior gain of the historical 3x3 V6 filter is 4/9. Do not change that filter.
        fun defaults(mode: DepthEdgeMode): ModeVisualProfile = when (mode) {
            DepthEdgeMode.LEGACY_V6 -> ModeVisualProfile(50f * 9f / 4f, matchV10Default = true)
            DepthEdgeMode.CENTERED_V7 -> ModeVisualProfile(50f, matchV10Default = true)
            DepthEdgeMode.REFERENCE_V8, DepthEdgeMode.REFERENCE_V9 -> ModeVisualProfile(
                255f * 50f * 500f / (REFERENCE_WIDTH.toFloat() * REFERENCE_WIDTH), invert = true, matchV10Default = true,
            )
            DepthEdgeMode.COMFY_LEGACY_V10, DepthEdgeMode.COMFY_SUBPIXEL_V11 -> ModeVisualProfile(50f, invert = true, matchV10Default = true)
        }

        fun capture(settings: AppSettings) = ModeVisualProfile(settings.depthScale, settings.blurRadius,
            settings.fillRadius, settings.invertDepth, settings.maxLongEdge, settings.depthUpscale, settings.matchV10Default, settings.invertModelDepth)

        fun decode(value: String?): ModeVisualProfile? = runCatching {
            val p = requireNotNull(value).split('|')
            require((p.size == 11 && p[0] == "1") || (p.size == 12 && p[0] == "2"))
            val scale = p[1].toFloat().also { require(it.isFinite() && it in 0f..500f) }
            ModeVisualProfile(scale, p[2].toInt().also { require(it in 0..99) },
                p[3].toInt().also { require(it in 0..99) }, p[4].toBooleanStrict(),
                p[5].toInt().also { require(it in 256..16384) },
                DepthUpscaleSettings(p[6].toBooleanStrict(), p[7].toInt(),
                    DepthInterpolation.valueOf(p[8]), DepthResizeOrder.valueOf(p[9])), p[10].toBooleanStrict(), p.getOrNull(11)?.toBooleanStrict() ?: false)
        }.getOrNull()
    }
}

/** Convert the External node's width-relative unit at the *actual* decoded eye width.
 * Invert=true also aligns relative near/far order with V10 (different static eye).
 * Absolute convergence, normalization and occlusion handling remain mode-specific.
 */
internal fun VrGenerationParams.effectiveScale(eyeWidth: Int, depth: DepthMap? = null): Float =
    depth?.projectionScale ?: if (matchV10Default && edgeMode.isReference) {
        (depthScale * ModeVisualProfile.REFERENCE_WIDTH.toDouble() * ModeVisualProfile.REFERENCE_WIDTH /
            (eyeWidth.toDouble().coerceAtLeast(1.0) * eyeWidth.coerceAtLeast(1))).toFloat().coerceIn(0f, 100f)
    } else depthScale

/** Align the P90-P10 disparity span of each image with V10/50, without modifying
 * a renderer or its historical depth map. V10's cubic resize + normalization can
 * change contrast; a constant 9/4 gain or width-unit conversion alone is insufficient.
 * This is a scalar calibration of disparity amplitude, not a perceptual equivalence claim.
 */
internal object DefaultDisparityCalibration {
    fun span(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        val count = minOf(32768, values.size)
        val sample = FloatArray(count) { i ->
            values[(i.toLong() * values.size / count).toInt()].takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        }
        sample.sort()
        return sample[((count - 1) * 0.9).toInt()] - sample[((count - 1) * 0.1).toInt()]
    }

    fun scale(params: VrGenerationParams, width: Int, prepared: FloatArray, v10Default: FloatArray): Float {
        if (!params.matchV10Default) return params.depthScale
        if (params.edgeMode.isComfyMain) return 50f
        val actualSpan = span(prepared)
        val targetSpan = span(v10Default)
        if (actualSpan < 0.00001f || targetSpan < 0.00001f) return params.effectiveScale(width)
        val unit = if (params.edgeMode.isReference) width / 500f else 255f / width.coerceAtLeast(1)
        return (50f * 255f / width.coerceAtLeast(1) * targetSpan / (unit * actualSpan))
            .coerceIn(0f, if (params.edgeMode.isReference) 100f else 500f)
    }
}

internal fun DepthEdgeMode.shortLabel(): String = when (this) {
    DepthEdgeMode.LEGACY_V6 -> "V6 · 旧版"
    DepthEdgeMode.CENTERED_V7 -> "V7 · 居中"
    DepthEdgeMode.REFERENCE_V8 -> "V8 · External"
    DepthEdgeMode.REFERENCE_V9 -> "V9 · 平滑"
    DepthEdgeMode.COMFY_LEGACY_V10 -> "V10 · ComfyUI"
    DepthEdgeMode.COMFY_SUBPIXEL_V11 -> "V11 · 亚像素"
}
