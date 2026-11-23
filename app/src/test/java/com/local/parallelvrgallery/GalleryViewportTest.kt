package com.local.parallelvrgallerypro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryViewportTest {
    @Test fun eachGridKeepsItsOwnAnchorAcrossTabAndDetailChanges() {
        val saved = GalleryViewports()
        val scopes = listOf(
            GalleryGridScope.ALL, GalleryGridScope.ALBUMS,
            GalleryGridScope.album("1"), GalleryGridScope.album("2"),
            GalleryGridScope.GENERATED_VERSIONS, GalleryGridScope.GENERATED_VIDEOS,
            GalleryGridScope.generatedVersion("1"), GalleryGridScope.generatedVersion("2"),
        )
        scopes.forEachIndexed { index, scope -> saved.record(scope, GalleryViewport("key$index", index, index * 7)) }
        scopes.forEachIndexed { index, scope ->
            assertEquals(GalleryViewport("key$index", index, index * 7), saved[scope])
        }
    }

    @Test fun restoreTracksTheSameItemWhenHeadersOrMediaMove() {
        val photo = GalleryViewport("second", 1, 13)
        val keys = listOf("header_4", "new", "header_3", "first", "second")
        assertEquals(4 to 13, photo.restore(keys))
        assertEquals(2 to 12, GalleryViewport("header_3", 0, 12).restore(keys))
        assertEquals(1 to 13, photo.restore(listOf("header_4", "remaining")))
        assertEquals(0 to 0, photo.restore(emptyList()))
    }

    @Test fun allAnchorOnlyAcceptsTheCapturedAllViewerSource() {
        assertTrue(isAllGallerySource(QueueSource.ALL.name))
        assertFalse(isAllGallerySource(QueueSource.ALBUM.name))
        assertFalse(isAllGallerySource(QueueSource.GENERATED.name))
        assertFalse(isAllGallerySource(null))
    }
}
