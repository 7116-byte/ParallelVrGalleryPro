package com.local.parallelvrgallerypro

private val imageParameters = Regex(
    """(?<model>[A-Za-z0-9._-]+)_(?<renderer>${DepthEdgeMode.entries.joinToString("|") { it.cacheVersion }})_s[-0-9.Ee]+_b(?<smooth>[0-9]+)_f(?<fill>[0-9]+)_inv(?<invert>[01])_m[0-9]+_t[0-9]+_gpu(?<gpu>[01])_(?:${GpuTestMode.entries.joinToString("|") { it.name }})_force[01](?:_du1_m[0-9]+_i[nlc]_o(?:sr|rs))?(?:_eq1050)?(?<invertModel>_di1)?(?:_take[0-9a-f]{8})?_eyesJpegQ100V1""",
)

/** Supplement the album's short title using the parameters recorded in a known image cache key. */
internal fun generatedAlbumParameterDetails(version: String, lang: AppLanguage): String {
    val match = imageParameters.matchEntire(version) ?: return ""
    fun field(name: String) = match.groups[name]?.value.orEmpty()
    fun label(zh: String, en: String) = if (lang == AppLanguage.ZH) zh else en

    val modelId = field("model")
    val qnn = qnnModelConfig(modelId)
    val modelAndInput = if (qnn != null) {
        val model = when (qnn.variant) {
            QnnDepthVariant.FP16 -> "QNN GPU FP16"
            QnnDepthVariant.FP32_HEAD -> label("QNN GPU 末端 FP32", "QNN GPU FP32 head")
        }
        val size = qnn.size
        val input = when (qnn.aspect) {
            QnnDepthAspect.SQUARE -> label("${size}×$size 固定方形", "${size}×$size square")
            QnnDepthAspect.FIT -> label("${size}² 预算 · 等比例补齐", "${size}² budget · Aspect padding")
            QnnDepthAspect.ALIGNED -> label("${size}² 预算 · 14 像素分块", "${size}² budget · 14-pixel patches")
            QnnDepthAspect.WIDE_TALL -> {
                val sizes = QnnInputGeometry.warmupGeometries(size, qnn.aspect)
                    .joinToString(" / ") { "${it.tensorWidth}×${it.tensorHeight}" }
                label("固定横竖屏 $sizes", "Fixed landscape / portrait $sizes")
            }
            QnnDepthAspect.PORTRAIT_PAIR -> {
                val sizes = QnnInputGeometry.warmupGeometries(size, qnn.aspect)
                    .joinToString(" / ") { "${it.tensorWidth}×${it.tensorHeight}" }
                label("双竖版 $sizes", "Two portrait shapes $sizes")
            }
        }
        "$model · $input"
    } else {
        val spec = AvailableModels.firstOrNull {
            !it.videoOnly && it.fileName.endsWith(".tflite") && (it.id == modelId || it.fileName == modelId)
        } ?: return ""
        val backend = if (field("gpu") == "1") "GPU" else "CPU"
        val model = if (spec.id.startsWith("qualcomm_")) label("高通 TFLite", "Qualcomm TFLite") else "TFLite"
        "$model $backend · ${spec.inputSize}×${spec.inputSize}"
    }

    val mode = DepthEdgeMode.entries.first { it.cacheVersion == field("renderer") }
    val reversed = DepthDirection.reversed(mode, field("invertModel").isNotEmpty(), field("invert") == "1")
    val direction = if (reversed) label("反转开", "Inverted") else label("反转关", "Inversion off")
    return "$modelAndInput · ${label("平滑", "Smooth")} ${field("smooth")} · ${label("填充", "Fill")} ${field("fill")} · $direction"
}
