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

    @Test fun selectingAnAlbumIncludesCopyOnlySourcesAndDeduplicatesRepeatedSources() {
        val parameters = "depth_anything_v2.tflite_depthV6_s40_b3_f10_inv0_m6000_t4_gpu0_ACCURATE_UNSET_force0"
        val encoder = "_eyesJpegQ100V1"
        val results = listOf(
            "a" to (parameters + encoder),
            "a" to "${parameters}_take0123abcd$encoder",
            "b" to "${parameters}_take4567abcd$encoder",
            "c" to (parameters.replace("_s40_", "_s45_") + encoder),
        )

        assertEquals(listOf(1, 2), GeneratedSelectionScope.regenerationIndexes(photos, results, setOf(parameters + encoder)))
    }

    @Test fun emptyOrStaleSelectionNeverFallsBackToAll() {
        assertTrue(GeneratedSelectionScope.regenerationIndexes(photos, entries, emptySet()).isEmpty())
        assertTrue(GeneratedSelectionScope.regenerationIndexes(photos, entries, setOf("deleted")).isEmpty())
    }
}
