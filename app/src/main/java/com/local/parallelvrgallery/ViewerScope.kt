package com.local.parallelvrgallerypro

internal object ViewerScope {
    fun canPrefetch(
        vrMode: Boolean,
        normalOrigin: Boolean,
        viewerOpen: Boolean,
        imageSelected: Boolean,
        generatedSurface: Boolean,
        hasSourceScope: Boolean,
    ): Boolean = vrMode && normalOrigin && viewerOpen && imageSelected && !generatedSurface && hasSourceScope

    fun canResumeExisting(sourcePaused: Boolean, viewerOpen: Boolean, generatedSurface: Boolean): Boolean =
        sourcePaused && (generatedSurface || !viewerOpen)

    fun indices(allKeys: List<String>, orderedKeys: List<String>, allowedKeys: Set<String>): List<Int> {
        if (orderedKeys.isNotEmpty()) {
            val byKey = allKeys.withIndex().associate { it.value to it.index }
            return orderedKeys.mapNotNull { byKey[it] }.distinct()
        }
        if (allowedKeys.isNotEmpty()) return allKeys.indices.filter { allKeys[it] in allowedKeys }
        // An empty/deleted captured scope must never expand into the global library.
        return emptyList()
    }
}
