package com.local.parallelvrgallerypro

/** Sources that still need a result in the selected parameter album. */
internal fun additionalImageSourceKeys(
    sourceKeys: List<String>,
    targetVersion: String,
    existingResults: List<Pair<String, String>>,
    pendingResults: List<Pair<String, String>> = emptyList(),
): List<String> {
    val targetAlbum = generatedAlbumKey(targetVersion)
    val coveredSources = (existingResults.asSequence() + pendingResults.asSequence())
        .filter { (_, version) -> generatedAlbumKey(version) == targetAlbum }
        .map { (source, _) -> source }
        .toHashSet()
    return sourceKeys.filterNot { it in coveredSources }.distinct()
}
