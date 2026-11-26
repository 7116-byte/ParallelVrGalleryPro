package com.local.parallelvrgallerypro

enum class DepthEdgeMode(val cacheVersion: String) {
    LEGACY_V6("depthV6"),
    CENTERED_V7("depthV7"),
    REFERENCE_V8("depthSSS22V8"),
    REFERENCE_V9("depthSSS22V9"),
    COMFY_LEGACY_V10("depthSSSMainV10"),
    COMFY_SUBPIXEL_V11("depthSSSMainV11");

    val isReference: Boolean get() = this == REFERENCE_V8 || this == REFERENCE_V9
    val isComfyMain: Boolean get() = this == COMFY_LEGACY_V10 || this == COMFY_SUBPIXEL_V11

    companion object {
        // Preferences move to the corrected renderer; saved jobs and old results
        // use restore() directly and retain the exact algorithm they recorded.
        fun forCurrentSettings(name: String?): DepthEdgeMode = restore(name).let { if (it == REFERENCE_V8) REFERENCE_V9 else it }
        fun restore(name: String?, previousCacheVersion: String? = null): DepthEdgeMode =
            entries.firstOrNull { it.name == name }
                ?: when {
                    previousCacheVersion?.contains("_depthSSSMainV11_") == true -> COMFY_SUBPIXEL_V11
                    previousCacheVersion?.contains("_depthSSSMainV10_") == true -> COMFY_LEGACY_V10
                    previousCacheVersion?.contains("_depthSSS22V9_") == true -> REFERENCE_V9
                    previousCacheVersion?.contains("_depthSSS22V8_") == true -> REFERENCE_V8
                    previousCacheVersion?.contains("_depthV7_") == true -> CENTERED_V7
                    else -> LEGACY_V6
                }
    }
}

/** Single-source Rust depth filtering for V6–V11. */
internal object DepthFilters {
    fun process(depth: FloatArray, width: Int, height: Int, diameter: Int, invert: Boolean, mode: DepthEdgeMode): FloatArray =
        when (mode) {
            DepthEdgeMode.LEGACY_V6 -> legacyV6(depth, width, height, diameter, invert)
            DepthEdgeMode.CENTERED_V7 -> boxMean(depth, width, height, diameter, invert)
            DepthEdgeMode.COMFY_LEGACY_V10, DepthEdgeMode.COMFY_SUBPIXEL_V11 -> {
                require(width > 0 && height > 0 && depth.size == width * height)
                RustCore.mapDepthDirection(depth, invert = !invert)
            }
            DepthEdgeMode.REFERENCE_V8, DepthEdgeMode.REFERENCE_V9 -> {
                require(width > 0 && height > 0 && depth.size == width * height)
                RustCore.mapDepthDirection(depth, invert = invert)
            }
        }

    fun legacyV6(depth: FloatArray, width: Int, height: Int, diameter: Int, invert: Boolean): FloatArray =
        RustCore.filterPhoneDepth(depth, width, height, diameter, invert, centered = false)

    fun boxMean(depth: FloatArray, width: Int, height: Int, diameter: Int, invert: Boolean): FloatArray =
        RustCore.filterPhoneDepth(depth, width, height, diameter, invert, centered = true)
}
