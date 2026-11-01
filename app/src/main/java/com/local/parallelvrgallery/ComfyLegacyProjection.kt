package com.local.parallelvrgallerypro

/** SSS main node, Parallel, HighSodium disabled (30372fec). Not the external node.
 * Depth is quantized before displacement. Later source columns overwrite earlier
 * columns; fill length is displacement + 10. The other eye stays identical.
 */
internal object ComfyLegacyProjection {
    fun project(source: IntArray, width: Int, height: Int, depth: FloatArray, scale: Float): IntArray {
        require(width > 0 && height > 0 && width.toLong() * height == source.size.toLong())
        require(depth.size == source.size && scale.isFinite() && scale in 1f..100f)
        val result = source.copyOf()
        val factor = scale.toDouble() / width
        val unwritten = IntArray(width + 1)
        fun next(start:Int):Int {
            var p=start
            while(unwritten[p]!=p) {unwritten[p]=unwritten[unwritten[p]];p=unwritten[p]}
            return p
        }
        for (y in 0 until height) {
            val row = y * width
            for(i in unwritten.indices)unwritten[i]=i
            // Reverse last-writer order; resolve each destination once without changing any interval.
            for (x in width-1 downTo 0) {
                val d = depth[row + x].takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
                val shift = ((d * 255f).toInt() * factor).toInt()
                val target = (x + shift).coerceIn(0, width - 1)
                val end = minOf(width, target + shift + 10)
                var destination=next(target)
                while(destination<end) {
                    result[row+destination]=source[row+x]
                    unwritten[destination]=next(destination+1)
                    destination=unwritten[destination]
                }
            }
        }
        return result
    }
}
