package com.local.parallelvrgallerypro

/** Opt-in diagnostics; no media, trace or timing records are retained by default. */
internal object InferenceProfiling {
    @Volatile var listener: ((Int, Long, Long, Long, Long) -> Unit)? = null
}
