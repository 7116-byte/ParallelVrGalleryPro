package com.local.parallelvrgallerypro

/** Deletion invalidates in-flight results before disk or UI changes can be committed. */
internal class GenerationGuard {
    private val revisions = mutableMapOf<String, Long>()

    @Synchronized fun token(key: String): Long = revisions[key] ?: 0L
    @Synchronized fun isCurrent(key: String, token: Long): Boolean = token(key) == token

    @Synchronized fun invalidate(keys: Set<String>) {
        keys.forEach { revisions[it] = token(it) + 1L }
    }

    @Synchronized fun ifCurrent(key: String, token: Long, action: () -> Unit): Boolean {
        if (!isCurrent(key, token)) return false
        action()
        return true
    }
}
