package com.local.parallelvrgallerypro

import kotlin.math.roundToInt

/** YUV coefficients selected from the decoder's color metadata, not fixed to BT.601. */
internal class YuvRgb(standard: Int, fullRange: Boolean) {
    private val kr = when (standard) { 1 -> 0.2126; 6 -> 0.2627; else -> 0.299 }
    private val kb = when (standard) { 1 -> 0.0722; 6 -> 0.0593; else -> 0.114 }
    private val kg = 1 - kr - kb
    private val unit = 65536.0
    private val chromaScale = if (fullRange) 1.0 else 255.0 / 224.0
    private val luma = IntArray(256) { ((if (fullRange) it.toDouble() else (it - 16) * 255.0 / 219.0) * unit).roundToInt() }
    private val redV = IntArray(256) { ((it - 128) * chromaScale * 2 * (1 - kr) * unit).roundToInt() }
    private val blueU = IntArray(256) { ((it - 128) * chromaScale * 2 * (1 - kb) * unit).roundToInt() }
    private val greenU = IntArray(256) { ((it - 128) * chromaScale * 2 * kb * (1 - kb) / kg * unit).roundToInt() }
    private val greenV = IntArray(256) { ((it - 128) * chromaScale * 2 * kr * (1 - kr) / kg * unit).roundToInt() }
    fun pixel(y: Int, u: Int, v: Int): Int {
        val yy = luma[y] + 32768
        val r = ((yy + redV[v]) shr 16).coerceIn(0, 255)
        val b = ((yy + blueU[u]) shr 16).coerceIn(0, 255)
        val g = ((yy - greenU[u] - greenV[v]) shr 16).coerceIn(0, 255)
        return (0xff shl 24) or (r shl 16) or (g shl 8) or b
    }
}
