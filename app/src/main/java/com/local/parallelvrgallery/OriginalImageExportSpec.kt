package com.local.parallelvrgallerypro

/** A captured result's parameters and geometry, never the current settings or JPEG pixels. */
internal data class OriginalImageExportSpec(val params: VrGenerationParams, val eyeWidth: Int, val eyeHeight: Int) {
    companion object {
        fun restore(values: Map<String, String>, upscale: Map<String, String>?, photoKey: String,
            version: String, outputWidth: Int, outputHeight: Int): OriginalImageExportSpec {
            fun required(key: String) = requireNotNull(values[key]) { "结果缺少参数 $key，无法按原参数保存 / Missing recorded parameter: $key" }
            fun integer(key: String) = required(key).toInt()
            fun boolean(key: String, default: Boolean? = null): Boolean =
                values[key]?.toBooleanStrict() ?: requireNotNull(default) { "Missing recorded parameter: $key" }
            require(outputWidth > 0 && outputWidth % 2 == 0 && outputHeight > 0)
            values["photoKey"]?.let { require(it == photoKey) { "Result/source identity mismatch" } }
            values["cacheVersion"]?.let { require(it == version) { "Result version mismatch" } }
            val width = outputWidth / 2
            values["sourceWidth"]?.let { require(it.toInt() == width) { "Recorded eye width mismatch" } }
            values["sourceHeight"]?.let { require(it.toInt() == outputHeight) { "Recorded eye height mismatch" } }
            val mode = values["edgeMode"]?.let { DepthEdgeMode.valueOf(it) } ?: run {
                require(version == "default" || DepthEdgeMode.entries.any { version.contains("_${it.cacheVersion}_") }) {
                    "结果缺少可识别的模式 / Missing recorded rendering mode"
                }
                DepthEdgeMode.restore(null, version)
            }
            val scale = required("depthScale").toFloat().also { require(it.isFinite() && it in 0f..10000f) }
            val gpu = boolean("useGpu")
            val resize = if (upscale == null) DepthUpscaleSettings() else DepthUpscaleSettings(
                enabled = requireNotNull(upscale["enabled"]).toBooleanStrict(),
                maxLongEdge = requireNotNull(upscale["maxLongEdge"]).toInt(),
                interpolation = DepthInterpolation.valueOf(requireNotNull(upscale["interpolation"])),
                order = DepthResizeOrder.valueOf(requireNotNull(upscale["order"])),
            )
            val params = VrGenerationParams(
                depthModel = required("depthModel"), depthScale = scale,
                blurRadius = integer("blurRadius").also { require(it in 0..51) },
                fillRadius = integer("fillRadius").also { require(it in 0..32) },
                invertDepth = boolean("invertDepth"), maxLongEdge = integer("maxLongEdge").also { require(it > 0) },
                modelThreads = integer("modelThreads").also { require(it in 1..8) }, useGpu = gpu,
                gpuTestMode = values["gpuTestMode"]?.let { GpuTestMode.valueOf(it) } ?: GpuTestMode.AUTO,
                forceGpuNoFallback = boolean("forceGpuNoFallback", gpu),
                inpaintMode = values["inpaintMode"] ?: "FOREGROUND_FILL", quality = values["quality"]?.toInt() ?: 94,
                edgeMode = mode, depthUpscale = resize, matchV10Default = boolean("matchV10Default", false),
                invertModelDepth = boolean("invertModelDepth", false),
            )
            require(maxOf(width, outputHeight) <= params.maxLongEdge) { "Recorded dimensions exceed output setting" }
            return OriginalImageExportSpec(params, width, outputHeight)
        }
    }
}
