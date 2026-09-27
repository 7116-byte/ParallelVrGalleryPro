package com.local.parallelvrgallerypro

internal object GeneratedSelectionScope {
    fun regenerationIndexes(photoKeys: List<String>, entries: List<Pair<String, String>>, versions: Set<String>): List<Int> {
        val indexes = photoKeys.withIndex().associate { it.value to it.index }
        return entries.asSequence().filter { it.second in versions }.map { it.first }.distinct()
            .mapNotNull { indexes[it] }.toList()
    }
}
