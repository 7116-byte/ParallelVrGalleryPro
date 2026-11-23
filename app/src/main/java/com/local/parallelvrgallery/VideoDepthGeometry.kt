package com.local.parallelvrgallerypro

/** Dimensions observed on an actually generated frame. Cached eye images alone do not provide them. */
data class VideoDepthGeometry @JvmOverloads constructor(
    val modelWidth: Int,
    val modelHeight: Int,
    val preparedWidth: Int,
    val preparedHeight: Int,
    val contentWidth: Int = modelWidth,
    val contentHeight: Int = modelHeight,
    val inputRotation: Int = 0,
) {
    internal fun metadataLines(): List<String> = listOf(
        "modelDepthWidth=$modelWidth",
        "modelDepthHeight=$modelHeight",
        "preparedDepthWidth=$preparedWidth",
        "preparedDepthHeight=$preparedHeight",
        "depthContentWidth=$contentWidth",
        "depthContentHeight=$contentHeight",
        "modelInputRotation=$inputRotation",
    )

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
            return VideoDepthGeometry(modelWidth, modelHeight, preparedWidth, preparedHeight, contentWidth, contentHeight, rotation)
        }

        private fun Map<String, String>.positiveDimension(key: String) = get(key)?.toIntOrNull()?.takeIf { it > 0 }
    }
}

internal fun VideoFrameTimings.withPreviousFrame(previous: VideoFrameTimings?): VideoFrameTimings =
    if (depthGeometry == null && previous?.depthGeometry != null) copy(depthGeometry = previous.depthGeometry) else this
