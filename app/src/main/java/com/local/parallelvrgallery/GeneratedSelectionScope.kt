package com.local.parallelvrgallerypro

internal object GeneratedSelectionScope {
    fun regenerationIndexes(photoKeys: List<String>, entries: List<Pair<String, String>>, albums: Set<String>): List<Int> {
        val indexes = photoKeys.withIndex().associate { it.value to it.index }
        return entries.asSequence().filter { generatedAlbumKey(it.second) in albums }.map { it.first }.distinct()
            .mapNotNull { indexes[it] }.toList()
    }
}
