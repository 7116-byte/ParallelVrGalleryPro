package com.local.parallelvrgallerypro

internal object VideoCommandPolicy {
    fun canStart(state: VideoVrState): Boolean = state == VideoVrState.NORMAL || state == VideoVrState.PAUSED || state == VideoVrState.FAILED
    fun canPause(state: VideoVrState): Boolean = state == VideoVrState.QUEUED || state == VideoVrState.GENERATING
    fun canRestoreAutomatically(key: String, state: VideoVrState, hasParams: Boolean, admitted: Set<String>): Boolean =
        key in admitted && hasParams && (state == VideoVrState.QUEUED || state == VideoVrState.GENERATING)
}
