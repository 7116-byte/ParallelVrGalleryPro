package com.local.parallelvrgallerypro

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal enum class QnnDepthAspect(val id: String) {
    SQUARE("square"),
    FIT("fit"),
    ALIGNED("aligned"),
    WIDE_TALL("widetall"),
    PORTRAIT_PAIR("portraitpair"),
}

internal data class QnnInputGeometry(
    val tensorWidth: Int,
    val tensorHeight: Int,
    val contentWidth: Int,
    val contentHeight: Int,
    val left: Int,
    val top: Int,
    val rotation: Int = 0,
) {
    init {
        val largestAxis = 2 * QNN_FP16_MAX_TOKENS * QNN_FP16_PATCH_SIZE
        require(tensorWidth in QNN_FP16_PATCH_SIZE..largestAxis && tensorWidth % QNN_FP16_PATCH_SIZE == 0)
        require(tensorHeight in QNN_FP16_PATCH_SIZE..largestAxis && tensorHeight % QNN_FP16_PATCH_SIZE == 0)
        require(contentWidth in 1..tensorWidth && contentHeight in 1..tensorHeight)
        require(left in 0..(tensorWidth - contentWidth) && top in 0..(tensorHeight - contentHeight))
        require(rotation == 0 || rotation == 90) { "Unsupported input rotation: $rotation" }
    }

    fun matchesLayout(size: Int, aspect: QnnDepthAspect, allowNativeLandscapeOverride: Boolean = false): Boolean {
        if (!isQnnFp16Size(size) || tensorWidth * tensorHeight > size * size || maxOf(tensorWidth, tensorHeight) > 2 * size) return false
        if (aspect != QnnDepthAspect.PORTRAIT_PAIR && rotation != 0) return false
        return when (aspect) {
            QnnDepthAspect.SQUARE -> tensorWidth == size && tensorHeight == size && contentWidth == size && contentHeight == size
            QnnDepthAspect.FIT -> left == (tensorWidth - contentWidth) / 2 && top == (tensorHeight - contentHeight) / 2
            QnnDepthAspect.ALIGNED -> tensorWidth == contentWidth && tensorHeight == contentHeight
            QnnDepthAspect.WIDE_TALL -> this == wideTall(size, landscape = tensorWidth >= tensorHeight)
            QnnDepthAspect.PORTRAIT_PAIR -> {
                val unrotated = if (rotation == 0) this else copy(rotation = 0)
                val (slim, standard) = portraitPair(size)
                unrotated == slim || unrotated == standard || (allowNativeLandscapeOverride && rotation == 0 &&
                    (this == fullTensor(slim.tensorHeight, slim.tensorWidth) || this == fullTensor(standard.tensorHeight, standard.tensorWidth)))
            }
        }
    }

    /** Use the media's native orientation while preserving its chosen tensor proportions. */
    fun withoutRotation(): QnnInputGeometry = if (rotation == 0) this else QnnInputGeometry(
        tensorHeight, tensorWidth, contentHeight, contentWidth, top, tensorWidth - left - contentWidth,
    )

    companion object {
        /** Fixed input layouts that can be compiled before the source media is known. */
        fun warmupGeometries(size: Int, aspect: QnnDepthAspect): List<QnnInputGeometry> {
            require(isQnnFp16Size(size)) { "Unsupported QNN pixel budget edge: $size" }
            return when (aspect) {
                QnnDepthAspect.SQUARE -> listOf(fullTensor(size, size))
                QnnDepthAspect.WIDE_TALL -> listOf(wideTall(size, landscape = true), wideTall(size, landscape = false))
                QnnDepthAspect.PORTRAIT_PAIR -> portraitPair(size)
                QnnDepthAspect.FIT, QnnDepthAspect.ALIGNED -> emptyList()
            }
        }

        fun forSource(size: Int, sourceWidth: Int, sourceHeight: Int, aspect: QnnDepthAspect): QnnInputGeometry {
            require(isQnnFp16Size(size)) { "Unsupported QNN pixel budget edge: $size" }
            require(sourceWidth > 0 && sourceHeight > 0) { "Invalid source dimensions: ${sourceWidth}x$sourceHeight" }
            val landscape = sourceWidth >= sourceHeight
            if (aspect == QnnDepthAspect.SQUARE) return fullTensor(size, size)
            if (aspect == QnnDepthAspect.WIDE_TALL) return wideTall(size, landscape)
            if (aspect == QnnDepthAspect.PORTRAIT_PAIR) {
                val ratio = minOf(sourceWidth, sourceHeight).toDouble() / maxOf(sourceWidth, sourceHeight)
                val (slim, standard) = portraitPair(size)
                // The geometric mean is the midpoint in log aspect ratio.
                val layout = if (ratio <= sqrt(27.0 / 64.0)) slim else standard
                return if (sourceWidth > sourceHeight) layout.copy(rotation = 90) else layout
            }

            // Plan the long and short axes once so that rotating the source swaps them exactly.
            val ratio = maxOf(sourceWidth, sourceHeight).toDouble() / minOf(sourceWidth, sourceHeight)
            val budgetEdge = size / QNN_FP16_PATCH_SIZE
            val budget = budgetEdge * budgetEdge
            // Beyond 4:1, trade unused area for a bounded long axis. A one-patch short axis
            // still allows exceptionally narrow media without allocating enormous tensors.
            val desiredLong = (budgetEdge * sqrt(ratio)).coerceAtMost(2.0 * budgetEdge)
            val desiredShort = (desiredLong / ratio).coerceAtLeast(1.0)
            val longCandidates = floor(desiredLong).toInt()..ceil(desiredLong).toInt()
            val shortCandidates = floor(desiredShort).toInt()..ceil(desiredShort).toInt()
            var longPatches = longCandidates.first
            var shortPatches = shortCandidates.first
            var smallestError = Double.POSITIVE_INFINITY
            for (long in longCandidates) {
                for (short in shortCandidates) {
                    val area = long * short
                    if (area > budget) continue
                    val longError = (long - desiredLong) / desiredLong
                    val shortError = (short - desiredShort) / desiredShort
                    val error = longError * longError + shortError * shortError
                    if (error < smallestError || (error == smallestError && area > longPatches * shortPatches)) {
                        longPatches = long
                        shortPatches = short
                        smallestError = error
                    }
                }
            }

            val width = QNN_FP16_PATCH_SIZE * if (landscape) longPatches else shortPatches
            val height = QNN_FP16_PATCH_SIZE * if (landscape) shortPatches else longPatches
            if (aspect == QnnDepthAspect.ALIGNED) return fullTensor(width, height)

            val scale = minOf(width.toDouble() / sourceWidth, height.toDouble() / sourceHeight)
            val contentWidth = (sourceWidth * scale).roundToInt().coerceIn(1, width)
            val contentHeight = (sourceHeight * scale).roundToInt().coerceIn(1, height)
            return QnnInputGeometry(width, height, contentWidth, contentHeight, (width - contentWidth) / 2, (height - contentHeight) / 2)
        }

        private fun fullTensor(width: Int, height: Int) = QnnInputGeometry(width, height, width, height, 0, 0)

        private fun wideTall(size: Int, landscape: Boolean): QnnInputGeometry {
            // A 16×9 patch group has the area of a 12×12 group.
            val unit = (size / QNN_FP16_PATCH_SIZE / 12) * QNN_FP16_PATCH_SIZE
            val longSide = 16 * unit
            val shortSide = 9 * unit
            return if (landscape) fullTensor(longSide, shortSide) else fullTensor(shortSide, longSide)
        }

        private fun portraitPair(size: Int): List<QnnInputGeometry> {
            val budgetEdge = size / QNN_FP16_PATCH_SIZE
            val standardUnit = (budgetEdge / sqrt(12.0)).toInt() * QNN_FP16_PATCH_SIZE
            return listOf(wideTall(size, landscape = false), fullTensor(3 * standardUnit, 4 * standardUnit))
        }
    }
}
