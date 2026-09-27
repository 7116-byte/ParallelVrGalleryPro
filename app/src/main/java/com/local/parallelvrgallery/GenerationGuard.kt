package com.local.parallelvrgallerypro

/** Deletion invalidates in-flight results before disk or UI changes can be committed. */
internal class GenerationGuard(
    initiallyDeleted: Set<String> = emptySet(),
    private val persistDeleted: (Set<String>) -> Unit = {},
) {
    private val revisions = mutableMapOf<String, Long>()
    private val deleted = initiallyDeleted.toMutableSet()

    @Synchronized fun token(key: String): Long = revisions[key] ?: 0L
    @Synchronized fun isCurrent(key: String, token: Long): Boolean = token(key) == token && key !in deleted
    @Synchronized fun permitsAutomatic(key: String): Boolean = key !in deleted

    @Synchronized fun explicitlyRequest(key: String) {
        if (deleted.remove(key)) persistDeleted(deleted.toSet())
    }

    @Synchronized fun invalidate(keys: Set<String>) {
        keys.forEach { revisions[it] = token(it) + 1L }
        deleted += keys
        persistDeleted(deleted.toSet())
    }

    @Synchronized fun ifCurrent(key: String, token: Long, action: () -> Unit): Boolean {
        if (!isCurrent(key, token)) return false
        action()
        return true
    }
}
