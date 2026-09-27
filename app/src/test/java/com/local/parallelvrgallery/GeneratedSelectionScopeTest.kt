package com.local.parallelvrgallerypro

import org.junit.Assert.*
import org.junit.Test

class GeneratedSelectionScopeTest {
    private val photos = listOf("other", "a", "b", "c")
    private val entries = listOf("a" to "v1", "b" to "v1", "a" to "v2", "c" to "v2", "missing" to "v1")

    @Test fun oneAlbumNeverSelectsOtherAlbumsOrUnloadedKeys() {
        assertEquals(listOf(1, 2), GeneratedSelectionScope.regenerationIndexes(photos, entries, setOf("v1")))
    }

    @Test fun severalAlbumsDeduplicateSharedOriginals() {
        assertEquals(listOf(1, 2, 3), GeneratedSelectionScope.regenerationIndexes(photos, entries, setOf("v1", "v2")))
    }

    @Test fun emptyOrStaleSelectionNeverFallsBackToAll() {
        assertTrue(GeneratedSelectionScope.regenerationIndexes(photos, entries, emptySet()).isEmpty())
        assertTrue(GeneratedSelectionScope.regenerationIndexes(photos, entries, setOf("deleted")).isEmpty())
    }
}
