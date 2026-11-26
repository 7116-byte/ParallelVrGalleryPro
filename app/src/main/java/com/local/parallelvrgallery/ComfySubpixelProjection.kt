package com.local.parallelvrgallerypro

/** Main-node eye order/units, with floating disparity and depth-aware coverage. Single-source Rust implementation. */
internal object ComfySubpixelProjection {
    fun project(source: IntArray, width: Int, height: Int, depth: FloatArray, scale: Float): IntArray =
        RustCore.projectComfy(source, depth, width, height, scale, subpixel = true)
}
