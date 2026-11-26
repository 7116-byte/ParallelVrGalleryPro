package com.local.parallelvrgallerypro

/** SSS external-depth V2.2 fast projection rules. Single-source Rust implementation. */
internal object ReferenceProjection {
    data class Raster(val pixels: IntArray, val holes: ByteArray)

    fun project(source: IntArray, width: Int, height: Int, depth: FloatArray, depthWidth: Int, depthHeight: Int, scale: Float): Raster {
        require(width > 0 && height > 0 && width.toLong() * height == source.size.toLong())
        val pixels = IntArray(source.size)
        val holes = ByteArray(source.size)
        RustCore.projectReference(source, depth, width, height, depthWidth, depthHeight, scale, pixels, holes)
        return Raster(pixels, holes)
    }
}
