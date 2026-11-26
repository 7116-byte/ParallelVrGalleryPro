package com.local.parallelvrgallerypro

/** Stable DA-V2 predicts inverse depth: nearer samples have larger values. */
internal object DepthDirection {
    fun reversed(mode: DepthEdgeMode, invertInput: Boolean, invertProjection: Boolean): Boolean =
        invertInput xor (invertProjection != ModeVisualProfile.defaults(mode).invert)

    fun modelDepth(raw: FloatArray, invertInput: Boolean): FloatArray =
        if (invertInput) RustCore.mapDepthDirection(raw, invert = true) else raw

    fun projectionNearWhite(mode: DepthEdgeMode, invertProjection: Boolean): Boolean =
        if (mode.isComfyMain) invertProjection else !invertProjection
}
