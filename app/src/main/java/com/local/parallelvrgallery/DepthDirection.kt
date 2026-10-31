package com.local.parallelvrgallerypro

/** Stable DA-V2 predicts inverse depth: nearer samples have larger values.
 * Historical snapshots retain two fields; the UI exposes their combined direction.
 */
internal object DepthDirection {
    fun reversed(mode: DepthEdgeMode, invertInput: Boolean, invertProjection: Boolean): Boolean =
        invertInput xor (invertProjection != ModeVisualProfile.defaults(mode).invert)

    fun modelDepth(raw: FloatArray, invertInput: Boolean): FloatArray =
        if (invertInput) FloatArray(raw.size) { 1f - raw[it] } else raw

    fun projectionNearWhite(mode: DepthEdgeMode, invertProjection: Boolean): Boolean =
        if (mode.isComfyMain) invertProjection else !invertProjection
}
