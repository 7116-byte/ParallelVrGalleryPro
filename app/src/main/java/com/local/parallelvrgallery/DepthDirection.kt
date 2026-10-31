package com.local.parallelvrgallerypro

/** Stable DA-V2 predicts inverse depth: nearer samples have larger values.
 * Model polarity and a projection's eye-order inversion are separate controls.
 */
internal object DepthDirection {
    fun modelDepth(raw: FloatArray, invertInput: Boolean): FloatArray =
        if (invertInput) FloatArray(raw.size) { 1f - raw[it] } else raw

    fun projectionNearWhite(mode: DepthEdgeMode, invertProjection: Boolean): Boolean =
        if (mode.isComfyMain) invertProjection else !invertProjection
}
