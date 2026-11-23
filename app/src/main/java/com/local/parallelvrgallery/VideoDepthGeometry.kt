package com.local.parallelvrgallerypro

import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Dimensions observed on an actually generated frame. Cached eye images alone do not provide them. */
data class VideoDepthGeometry @JvmOverloads constructor(
    val modelWidth: Int,
    val modelHeight: Int,
    val preparedWidth: Int,
    val preparedHeight: Int,
    val contentWidth: Int = modelWidth,
    val contentHeight: Int = modelHeight,
    val inputRotation: Int = 0,
    val workingLongEdge: Int? = null,
) {
    internal fun metadataLines(): List<String> = listOf(
        "modelDepthWidth=$modelWidth",
        "modelDepthHeight=$modelHeight",
        "preparedDepthWidth=$preparedWidth",
        "preparedDepthHeight=$preparedHeight",
        "depthContentWidth=$contentWidth",
        "depthContentHeight=$contentHeight",
        "modelInputRotation=$inputRotation",
    ) + listOfNotNull(workingLongEdge?.let { "videoWorkingLongEdge=$it" })

    internal companion object {
        fun measured(model: DepthMap, prepared: DepthMap) =
            VideoDepthGeometry(model.width, model.height, prepared.width, prepared.height)

        fun read(values: Map<String, String>): VideoDepthGeometry? {
            val modelWidth = values.positiveDimension("modelDepthWidth") ?: return null
            val modelHeight = values.positiveDimension("modelDepthHeight") ?: return null
            val preparedWidth = values.positiveDimension("preparedDepthWidth") ?: return null
            val preparedHeight = values.positiveDimension("preparedDepthHeight") ?: return null
            val rotation = values["modelInputRotation"]?.let { it.toIntOrNull() ?: return null } ?: 0
            if (rotation != 0 && rotation != 90) return null
            val uprightWidth = if (rotation == 90) modelHeight else modelWidth
            val uprightHeight = if (rotation == 90) modelWidth else modelHeight
            val contentWidth = if ("depthContentWidth" in values) values.positiveDimension("depthContentWidth") ?: return null else uprightWidth
            val contentHeight = if ("depthContentHeight" in values) values.positiveDimension("depthContentHeight") ?: return null else uprightHeight
            if (contentWidth > uprightWidth || contentHeight > uprightHeight) return null
            val workingLongEdge = if ("videoWorkingLongEdge" in values) values.positiveDimension("videoWorkingLongEdge") ?: return null else null
            return VideoDepthGeometry(modelWidth, modelHeight, preparedWidth, preparedHeight, contentWidth, contentHeight, rotation, workingLongEdge)
        }

        private fun Map<String, String>.positiveDimension(key: String) = get(key)?.toIntOrNull()?.takeIf { it > 0 }
    }
}

internal fun fitVideoWorkingFrame(width: Int, height: Int, workingLongEdge: Int): Pair<Int, Int> {
    require(width > 0 && height > 0 && workingLongEdge > 0)
    val sourceLongEdge = maxOf(width, height)
    if (sourceLongEdge <= workingLongEdge) return width to height
    val scale = workingLongEdge.toFloat() / sourceLongEdge
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}

internal fun memorySafeStereoLongEdge(width: Int, height: Int, requested: Int, mode: DepthEdgeMode, heapAvailable: Long): Int {
    val longEdge = maxOf(width, height).takeIf { it > 0 } ?: requested
    val shortEdge = minOf(width, height).takeIf { it > 0 } ?: longEdge
    val ratio = shortEdge.toFloat() / longEdge.toFloat()
    val budget = ((heapAvailable * 55L) / 100L).coerceIn(48L * 1024L * 1024L, 160L * 1024L * 1024L)
    val bytesPerPixel = when {
        mode.isComfyMain || mode == DepthEdgeMode.REFERENCE_V9 -> 64f
        mode == DepthEdgeMode.REFERENCE_V8 -> 48f
        else -> 18f
    }
    val requestedBytes = requested.toFloat() * requested.toFloat() * ratio * bytesPerPixel
    if (requestedBytes <= budget) return requested
    val safe = sqrt(budget.toDouble() / (ratio.toDouble() * bytesPerPixel.toDouble())).roundToInt()
    return safe.coerceIn(768, requested)
}

internal fun recoverVideoWorkingLongEdge(
    encodedWidth: Int, encodedHeight: Int, geometry: VideoDepthGeometry?, postMatchesEye: Boolean,
): Int {
    geometry?.workingLongEdge?.let { return it }
    val eyeWidth = encodedWidth / 2
    // The old encoder rounded the track height down to even. A matching Comfy depth sample can restore an odd height.
    val eyeHeight = geometry?.preparedHeight?.takeIf {
        postMatchesEye && geometry.preparedWidth == eyeWidth && it / 2 * 2 == encodedHeight
    } ?: encodedHeight
    return maxOf(eyeWidth, eyeHeight)
}

internal fun VideoFrameTimings.withPreviousFrame(previous: VideoFrameTimings?): VideoFrameTimings =
    if (depthGeometry == null && previous?.depthGeometry != null) copy(depthGeometry = previous.depthGeometry) else this
