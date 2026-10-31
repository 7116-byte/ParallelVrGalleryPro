package com.local.parallelvrgallerypro

import kotlin.math.floor

/** Independent Kotlin implementation of the SSS external-depth V2.2 fast projection rules.
 * Reference: SamSeenX/ComfyUI_SSStereoscope, commit 30372fec69a1929ce6eb5fded8d23ba2604fd68f.
 * The left eye is static. The right eye uses 8-bit nearest-sampled depth, relative
 * disparity and right-to-left splatting. Missing pixels are explicitly masked.
 */
internal object ReferenceProjection {
    data class Raster(val pixels: IntArray, val holes: ByteArray)

    fun project(source: IntArray, width: Int, height: Int, depth: FloatArray, depthWidth: Int, depthHeight: Int, scale: Float): Raster {
        require(width > 0 && height > 0 && width.toLong() * height == source.size.toLong())
        require(depthWidth > 0 && depthHeight > 0 && depthWidth.toLong() * depthHeight == depth.size.toLong())
        require(scale.isFinite() && scale in 0f..100f)
        val pixels = IntArray(source.size) { 0xff000000.toInt() }
        val holes = ByteArray(source.size) { 255.toByte() }
        val factor = width.toDouble() * scale / 500.0 / 255.0
        for (y in 0 until height) {
            val dy = floor((y + 0.5) * depthHeight / height).toInt().coerceAtMost(depthHeight - 1)
            for (x in width - 1 downTo 0) {
                val dx = floor((x + 0.5) * depthWidth / width).toInt().coerceAtMost(depthWidth - 1)
                val d = depth[dy * depthWidth + dx].takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
                val quantized = (d * 255f).toInt()
                val shift = (quantized * factor).toInt().coerceIn(0, width - 1)
                val target = x + shift
                if (target < width) {
                    pixels[y * width + target] = source[y * width + x]
                    holes[y * width + target] = 0
                }
            }
        }
        return Raster(pixels, holes)
    }
}
