package com.local.parallelvrgallerypro

internal const val QNN_FP16_PATCH_SIZE = 14
internal const val QNN_FP16_MIN_TOKENS = 37
internal const val QNN_FP16_MAX_TOKENS = 74
internal const val QNN_FP16_MODEL_SIZE = QNN_FP16_MIN_TOKENS * QNN_FP16_PATCH_SIZE
private const val QNN_MODEL_PREFIX = "depth_anything_v2_small_qnn_"
private const val QNN_MODEL_SUFFIX = "_v1"
internal const val QNN_FP16_MODEL_ID = "${QNN_MODEL_PREFIX}fp16_518$QNN_MODEL_SUFFIX"
internal const val QNN_FP16_770_MODEL_ID = "${QNN_MODEL_PREFIX}fp16_770$QNN_MODEL_SUFFIX"
internal const val QNN_FP16_1036_MODEL_ID = "${QNN_MODEL_PREFIX}fp16_1036$QNN_MODEL_SUFFIX"
internal const val QNN_FP16_MODEL_ASSET = "models/dav2-gpu-fp16.onnx"
internal const val QNN_FP16_MODEL_SHA256 = "bb60daa7ee9384e1154eebfc32412bc53acc08f74afe28a28e1f15365ea87f1b"
internal const val QNN_FP32_HEAD_MODEL_ASSET = "models/dav2-gpu-mx-fp16-head.onnx"
internal const val QNN_FP32_HEAD_MODEL_SHA256 = "592d92e9a9b28ee62ed981741d862e52fa37c82698dff8f50be2eb121d217a6f"

internal enum class QnnDepthVariant(
    val id: String,
    val assetName: String,
    val sha256: String,
    val source: String,
    val displayPrecision: String,
) {
    FP16(
        "fp16", QNN_FP16_MODEL_ASSET, QNN_FP16_MODEL_SHA256,
        "https://raw.githubusercontent.com/7116-byte/ParallelEyeConverter/7059fb36b76662acd5b643c129259939e3dd4f1b/app/src/main/assets/dav2-gpu-fp16.onnx",
        "FP16",
    ),
    FP32_HEAD(
        "fp32head", QNN_FP32_HEAD_MODEL_ASSET, QNN_FP32_HEAD_MODEL_SHA256,
        "https://huggingface.co/onnx-community/depth-anything-v2-small/tree/4472b7362082ad9968fee890ca0f1e5aca36b93d",
        "FP16 + FP32 head",
    ),
}

internal data class QnnModelConfig(val variant: QnnDepthVariant, val size: Int, val aspect: QnnDepthAspect)

internal fun isQnnFp16Size(size: Int): Boolean =
    size % QNN_FP16_PATCH_SIZE == 0 && size / QNN_FP16_PATCH_SIZE in QNN_FP16_MIN_TOKENS..QNN_FP16_MAX_TOKENS

internal fun qnnModelId(config: QnnModelConfig): String {
    require(isQnnFp16Size(config.size)) { "Unsupported QNN pixel budget: ${config.size}" }
    val aspect = if (config.aspect == QnnDepthAspect.SQUARE) "" else "_${config.aspect.id}"
    return "$QNN_MODEL_PREFIX${config.variant.id}_${config.size}$aspect$QNN_MODEL_SUFFIX"
}

internal fun hasQnnModelPrefix(modelId: String): Boolean = modelId.startsWith(QNN_MODEL_PREFIX)

internal fun qnnModelConfig(modelId: String): QnnModelConfig? {
    if (!hasQnnModelPrefix(modelId) || !modelId.endsWith(QNN_MODEL_SUFFIX)) return null
    val fields = modelId.removePrefix(QNN_MODEL_PREFIX).removeSuffix(QNN_MODEL_SUFFIX).split('_')
    if (fields.size !in 2..3) return null
    val variant = QnnDepthVariant.entries.firstOrNull { it.id == fields[0] } ?: return null
    val size = fields[1].toIntOrNull()?.takeIf(::isQnnFp16Size) ?: return null
    val aspect = if (fields.size == 2) QnnDepthAspect.SQUARE
        else QnnDepthAspect.entries.firstOrNull { it.id == fields[2] } ?: return null
    val config = QnnModelConfig(variant, size, aspect)
    return config.takeIf { qnnModelId(it) == modelId }
}

internal fun activeDepthModelId(modelId: String): String {
    val config = qnnModelConfig(modelId) ?: return modelId
    val activeAspect = when (config.aspect) {
        QnnDepthAspect.FIT -> QnnDepthAspect.ALIGNED
        QnnDepthAspect.WIDE_TALL -> QnnDepthAspect.PORTRAIT_PAIR
        else -> return modelId
    }
    return qnnModelId(config.copy(aspect = activeAspect))
}

internal fun qnnFp16ModelId(size: Int): String = qnnModelId(QnnModelConfig(QnnDepthVariant.FP16, size, QnnDepthAspect.SQUARE))

internal fun qnnFp16Size(modelId: String): Int? = qnnModelConfig(modelId)
    ?.takeIf { it.variant == QnnDepthVariant.FP16 && it.aspect == QnnDepthAspect.SQUARE }
    ?.size
