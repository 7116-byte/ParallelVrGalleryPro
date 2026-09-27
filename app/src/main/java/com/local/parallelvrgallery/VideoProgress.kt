package com.local.parallelvrgallerypro

import kotlin.math.roundToInt

internal object VideoProgress {
    fun percent(state: VideoVrState, progress: Float?): Int {
        if (state == VideoVrState.READY) return 100
        val value = progress?.takeIf { it.isFinite() } ?: 0f
        val maximum = if (state == VideoVrState.GENERATING) 99 else 100
        return (value.coerceIn(0f, 1f) * 100f).roundToInt().coerceAtMost(maximum)
    }

    fun frames(state: VideoVrState, current: Int?, total: Int?): String? {
        if (total == null || total <= 0) return null
        val frame = if (state == VideoVrState.READY) total else (current ?: 0).coerceIn(0, total)
        return "$frame/$total"
    }
}
