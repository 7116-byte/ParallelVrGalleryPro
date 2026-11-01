package com.local.parallelvrgallerypro

import kotlin.math.abs

/** Ranking only: admission, source scope, tier size and pause ownership stay with the queue. */
internal object NeighborPriority {
    fun canAdmit(state:VrState, windowPaused:Boolean):Boolean = when(state) {
        VrState.QUEUED,VrState.GENERATING,VrState.FAILED -> false
        VrState.PAUSED -> windowPaused
        else -> true
    }
    fun direction(previous: Int, current: Int): Int =
        if (previous < 0 || current < 0) 0 else current.compareTo(previous)

    fun rank(offset: Int, direction: Int): Int {
        if (offset == 0) return 0
        val distance = abs(offset)
        return if (direction == 0) distance * 2 + if (offset > 0) 1 else 0
        else distance * (if (offset.compareTo(0) == direction) 2 else 4) + if (offset.compareTo(0) == direction) 0 else 1
    }
}
