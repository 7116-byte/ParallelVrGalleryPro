package com.local.parallelvrgallerypro

/** Keep one orientation for the whole video, including frames resumed from disk. */
internal data class QnnVideoOrientation(val portraitInput: QnnInputGeometry, val rotationNanos: Long?) {
    init {
        require(rotationNanos == null || (rotationNanos >= 0 && (portraitInput.rotation == 90 || rotationNanos == 0L)))
    }

    val useNativeLandscape: Boolean get() = portraitInput.rotation == 90 && rotationNanos != null && rotationNanos > 20_000_000L
    val inferenceInput: QnnInputGeometry get() = if (useNativeLandscape) portraitInput.withoutRotation() else portraitInput

    fun encode(): String = with(portraitInput) {
        listOf(tensorWidth, tensorHeight, rotation, rotationNanos?.toString() ?: FIXED_PORTRAIT).joinToString("\t")
    }

    companion object {
        private const val FIXED_PORTRAIT = "portrait-v2"

        fun forNewVideo(portraitInput: QnnInputGeometry): QnnVideoOrientation = QnnVideoOrientation(portraitInput, null)

        fun restore(encoded: String, budget: Int): QnnVideoOrientation {
            val fields = encoded.trim().split('\t')
            require(fields.size == 4) { "Invalid video QNN orientation record" }
            val width = fields[0].toInt()
            val height = fields[1].toInt()
            val geometry = QnnInputGeometry(width, height, width, height, 0, 0, rotation = fields[2].toInt())
            require(geometry.matchesLayout(budget, QnnDepthAspect.PORTRAIT_PAIR)) { "Invalid recorded video QNN shape" }
            return if (fields[3] == FIXED_PORTRAIT) forNewVideo(geometry)
                else QnnVideoOrientation(geometry, fields[3].toLong())
        }
    }
}
