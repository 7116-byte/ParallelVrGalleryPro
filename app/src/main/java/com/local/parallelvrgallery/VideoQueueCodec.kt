package com.local.parallelvrgallerypro

internal fun VideoGenerationParams.encodeForQueue(): String {
    return listOf(
        vr.depthModel,
        vr.outputMode,
        vr.depthScale.toString(),
        vr.blurRadius.toString(),
        vr.fillRadius.toString(),
        vr.invertDepth.toString(),
        vr.maxLongEdge.toString(),
        vr.inpaintMode,
        vr.quality.toString(),
        modelThreads.toString(),
        useGpu.toString(),
        vr.gpuTestMode.name,
        vr.forceGpuNoFallback.toString(),
        cacheVersion(),
        depthWorkers.toString(),
        vr.edgeMode.name,
        vr.depthUpscale.enabled.toString(),
        vr.depthUpscale.boundedLongEdge.toString(),
        vr.depthUpscale.interpolation.name,
        vr.depthUpscale.order.name,
    ).joinToString(",") { it.replace(",", "_").replace('\t', '_').replace('\n', '_').replace('\r', '_') }
}

internal fun decodeVideoParams(text: String): VideoGenerationParams? {
    val parts = text.split(',')
    if (parts.size < 11) return null
    return runCatching {
        VideoGenerationParams(
            vr = VrGenerationParams(
                depthModel = parts[0],
                outputMode = parts[1],
                depthScale = parts[2].toFloat(),
                blurRadius = parts[3].toInt(),
                fillRadius = parts[4].toInt(),
                invertDepth = parts[5].toBooleanStrictOrNull() ?: false,
                maxLongEdge = parts[6].toInt(),
                modelThreads = parts[9].toInt(),
                useGpu = parts[10].toBooleanStrictOrNull() ?: false,
                gpuTestMode = parts.getOrNull(11)?.let { runCatching { GpuTestMode.valueOf(it) }.getOrNull() } ?: GpuTestMode.AUTO,
                forceGpuNoFallback = parts[10].toBooleanStrictOrNull() ?: false,
                inpaintMode = parts[7],
                quality = parts[8].toInt(),
                edgeMode = DepthEdgeMode.restore(parts.getOrNull(15), parts.getOrNull(13)),
                depthUpscale = DepthUpscaleSettings(
                    enabled = parts.getOrNull(16)?.toBooleanStrictOrNull() ?: false,
                    maxLongEdge = parts.getOrNull(17)?.toIntOrNull() ?: 0,
                    interpolation = DepthInterpolation.restore(parts.getOrNull(18)),
                    order = DepthResizeOrder.restore(parts.getOrNull(19)),
                ),
            ),
            modelThreads = parts[9].toInt(),
            useGpu = parts[10].toBooleanStrictOrNull() ?: false,
            depthWorkers = parts.getOrNull(14)?.toIntOrNull()?.coerceIn(1, 2) ?: 2,
        )
    }.getOrNull()
}
