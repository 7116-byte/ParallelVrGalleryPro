package com.local.parallelvrgallerypro

/** SSS main node, Parallel, HighSodium disabled (30372fec). Single-source Rust implementation. */
internal object ComfyLegacyProjection {
    fun project(source: IntArray, width: Int, height: Int, depth: FloatArray, scale: Float): IntArray =
        RustCore.projectComfy(source, depth, width, height, scale, subpixel = false)
}
