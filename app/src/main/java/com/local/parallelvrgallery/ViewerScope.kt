package com.local.parallelvrgallerypro

internal object ViewerScope {
    fun canPrefetch(vrMode: Boolean, normalOrigin: Boolean): Boolean = vrMode && normalOrigin

    fun indices(allKeys: List<String>, orderedKeys: List<String>, allowedKeys: Set<String>): List<Int> {
        if (orderedKeys.isNotEmpty()) {
            val byKey = allKeys.withIndex().associate { it.value to it.index }
            return orderedKeys.mapNotNull { byKey[it] }.distinct()
        }
        if (allowedKeys.isNotEmpty()) return allKeys.indices.filter { allKeys[it] in allowedKeys }
        return allKeys.indices.toList()
    }
}
