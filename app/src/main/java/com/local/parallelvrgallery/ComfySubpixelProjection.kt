package com.local.parallelvrgallerypro

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Main-node eye order/units, with floating disparity and depth-aware coverage.
 * Only a one-pixel footprint is blended. Revealed background is filled from the
 * farther neighboring surface; whole images/foregrounds are never blurred.
 */
internal object ComfySubpixelProjection {
    fun project(source: IntArray, width: Int, height: Int, depth: FloatArray, scale: Float): IntArray {
        require(width > 0 && height > 0 && width.toLong() * height == source.size.toLong())
        require(depth.size == source.size && scale.isFinite() && scale in 1f..100f)
        val result = IntArray(source.size)
        val factor = 255.0 * scale / width
        val maxSearch = minOf(width, ceil(factor).toInt() + 12)
        val near = FloatArray(width); val far = FloatArray(width)
        val weight = FloatArray(width); val red = FloatArray(width); val green = FloatArray(width); val blue = FloatArray(width)
        val background = IntArray(width); val left = IntArray(width); val right = IntArray(width)
        val tolerance = .02f
        for (y in 0 until height) {
            near.fill(-1f); far.fill(2f); weight.fill(0f); red.fill(0f); green.fill(0f); blue.fill(0f)
            val row = y * width
            for (x in 0 until width) {
                val d = depth[row + x].takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
                val position = x + d * factor
                val first = floor(position).toInt(); val fraction = (position - first).toFloat()
                val color = source[row + x]
                for (tap in 0..1) {
                    val target = first + tap; val coverage = if (tap == 0) 1f - fraction else fraction
                    if (target !in 0 until width || coverage <= 0f) continue
                    if (d < far[target]) { far[target] = d; background[target] = color }
                    if (d > near[target] + tolerance) {
                        weight[target] = 0f; red[target] = 0f; green[target] = 0f; blue[target] = 0f
                    } else if (d < near[target] - tolerance) continue
                    near[target] = maxOf(near[target], d)
                    weight[target] += coverage
                    red[target] += ((color ushr 16) and 255) * coverage
                    green[target] += ((color ushr 8) and 255) * coverage
                    blue[target] += (color and 255) * coverage
                }
            }
            var previous = -1
            for (x in 0 until width) { left[x] = previous; if (weight[x] > 0f) previous = x }
            previous = -1
            for (x in width - 1 downTo 0) { right[x] = previous; if (weight[x] > 0f) previous = x }
            for (x in 0 until width) {
                val a = left[x].takeIf { it >= 0 && x - it <= maxSearch }
                val b = right[x].takeIf { it >= 0 && it - x <= maxSearch }
                val neighbor = when {
                    a == null -> b
                    b == null -> a
                    abs(far[a] - far[b]) > tolerance -> if (far[a] < far[b]) a else b
                    else -> if (x - a <= b - x) a else b
                }
                if (weight[x] <= 0f) {
                    result[row + x] = if (neighbor != null) background[neighbor] else source[row + x]
                    continue
                }
                var back = background[x]; var backDepth = far[x]
                if (neighbor != null && far[neighbor] < backDepth) { back = background[neighbor]; backDepth = far[neighbor] }
                val alpha = if (backDepth < near[x] - tolerance) weight[x].coerceAtMost(1f) else 1f
                fun channel(sum: Float, shift: Int): Int = (sum / weight[x] * alpha + ((back ushr shift) and 255) * (1f - alpha) + .5f).toInt().coerceIn(0, 255)
                result[row + x] = (0xff shl 24) or (channel(red[x], 16) shl 16) or (channel(green[x], 8) shl 8) or channel(blue[x], 0)
            }
        }
        return result
    }
}
