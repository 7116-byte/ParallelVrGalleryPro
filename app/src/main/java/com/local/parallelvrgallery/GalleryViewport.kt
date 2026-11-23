package com.local.parallelvrgallerypro

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull

internal object GalleryGridScope {
    const val ALL = "all"
    const val ALBUMS = "albums"
    const val GENERATED_VERSIONS = "generated/versions"
    const val GENERATED_VIDEOS = "generated/videos"

    fun album(id: String): String = "album/$id"
    fun generatedVersion(id: String): String = "generated/version/$id"
}

internal data class GalleryViewport(val key: String, val index: Int, val offset: Int) {
    fun restore(itemKeys: List<String>): Pair<Int, Int> {
        if (itemKeys.isEmpty()) return 0 to 0
        val matchingIndex = itemKeys.indexOf(key)
        return (if (matchingIndex >= 0) matchingIndex else index.coerceIn(0, itemKeys.lastIndex)) to offset
    }
}

/** Only opaque grid keys and scroll coordinates are saved in the Activity's Compose state. */
internal class GalleryViewports {
    private val byScope = mutableMapOf<String, GalleryViewport>()

    operator fun get(scope: String): GalleryViewport? = byScope[scope]

    fun record(scope: String, viewport: GalleryViewport) {
        byScope[scope] = viewport
    }

    companion object {
        val Saver = listSaver<GalleryViewports, Any>(
            save = { saved ->
                saved.byScope.flatMap { (scope, viewport) -> listOf(scope, viewport.key, viewport.index, viewport.offset) }
            },
            restore = { values ->
                GalleryViewports().apply {
                    for ((scope, key, index, offset) in values.chunked(4)) {
                        record(scope as String, GalleryViewport(key as String, index as Int, offset as Int))
                    }
                }
            },
        )
    }
}

@Composable
internal fun rememberGalleryGridState(
    viewports: GalleryViewports,
    scope: String,
    itemKeys: List<String>,
): LazyGridState {
    // Keep the lazy layout's existing key tracking while it remains composed.
    // A fresh grid restores against current keys, which may have moved since it was left.
    val grid = remember(viewports, scope, itemKeys.isEmpty()) {
        val (index, offset) = viewports[scope]?.restore(itemKeys) ?: (0 to 0)
        LazyGridState(index, offset)
    }
    LaunchedEffect(viewports, scope, grid) {
        snapshotFlow { grid.viewport() }.filterNotNull().collect { viewports.record(scope, it) }
    }
    DisposableEffect(viewports, scope, grid) {
        onDispose { grid.viewport()?.let { viewports.record(scope, it) } }
    }
    return grid
}

private fun LazyGridState.viewport(): GalleryViewport? {
    val index = firstVisibleItemIndex
    val key = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.key as? String ?: return null
    return GalleryViewport(key, index, firstVisibleItemScrollOffset)
}

internal fun isAllGallerySource(source: String?): Boolean = source == QueueSource.ALL.name
