package com.local.parallelvrgallerypro

/** Presentation only: never changes persisted column preferences or generation parameters. */
object StudioLayout {
    fun columns(preferred: Int, widthDp: Int, fontScale: Float, detailed: Boolean = false): Int {
        val scale = fontScale.coerceIn(1f, 2f)
        val minimum = if (detailed) 144f * scale else 64f * scale
        val available = (widthDp - 32).coerceAtLeast(1)
        return preferred.coerceAtLeast(1).coerceAtMost((available / minimum).toInt().coerceAtLeast(1))
    }

    fun headerOffset(scrollPx: Int, heightPx: Int): Float =
        -scrollPx.coerceIn(0, heightPx.coerceAtLeast(0)).toFloat()
}
