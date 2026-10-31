package com.local.parallelvrgallerypro

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SelectedReplacementTest {
    @get:Rule val folder = TemporaryFolder()
    private fun dir(name: String, content: String): File = folder.newFolder(name).also { File(it, "left.jpg").writeText(content) }

    @Test fun replacingSelectedVersionsKeepsUnselectedHistory() {
        val selected = dir("selected", "old selected")
        val history = dir("history", "old history")
        val work = dir("work", "new")
        val target = File(folder.root, "new-version")
        ImageCachePublication.commit(work, target, listOf(selected))
        assertEquals("new", File(target, "left.jpg").readText())
        assertEquals("old history", File(history, "left.jpg").readText())
        assertFalse(selected.exists())
    }

    @Test fun eachMoveFailureRestoresAllOldResultsBeforeReportingFailure() {
        for (failAt in 1..3) {
            val parent = folder.newFolder("attempt-$failAt")
            val target = File(parent, "target").also { it.mkdir(); File(it, "old").writeText("target") }
            val selected = File(parent, "selected").also { it.mkdir(); File(it, "old").writeText("selected") }
            val work = File(parent, "work").also { it.mkdir(); File(it, "new").writeText("complete") }
            var moves = 0
            assertThrows(IllegalStateException::class.java) {
                ImageCachePublication.commit(work, target, listOf(selected)) { from, to ->
                    moves++; if (moves == failAt) false else from.renameTo(to)
                }
            }
            assertEquals("target", File(target, "old").readText())
            assertEquals("selected", File(selected, "old").readText())
            assertEquals("complete", File(work, "new").readText())
        }
    }

    @Test fun replacingRootLevelLegacyFilesKeepsVersionSubdirectories() {
        val photo = folder.newFolder("photo")
        val legacy = File(photo, "left.webp").also { it.writeText("legacy") }
        val history = File(photo, "history").also { it.mkdir(); File(it, "left.jpg").writeText("history") }
        val work = dir("work", "new")
        ImageCachePublication.commit(work, File(photo, "new-version"), listOf(legacy))
        assertFalse(legacy.exists())
        assertEquals("history", File(history, "left.jpg").readText())
        assertEquals("new", File(photo, "new-version/left.jpg").readText())
    }

    @Test fun deletionInvalidatesTheEntireReplacementTransaction() {
        val guard = GenerationGuard()
        val selected = dir("selected", "old")
        val work = dir("work", "obsolete")
        val target = File(folder.root, "new-version")
        val token = guard.token("photo")
        guard.invalidate(setOf("photo"))
        assertFalse(guard.ifCurrent("photo", token) { ImageCachePublication.commit(work, target, listOf(selected)) })
        assertFalse(target.exists())
        assertEquals("old", File(selected, "left.jpg").readText())
    }

    @Test fun replacementSelectionRoundTripsWithRecordedTimestamps() {
        val intent = ImageWriteIntent(listOf(CacheReplacementTarget("depthV6_s112.5", 1234), CacheReplacementTarget("depthV7", 5678)), true)
        assertEquals(intent, ImageWriteIntent.decode(intent.encode()))
        assertEquals(ImageWriteIntent(emptyList(), true), ImageWriteIntent.decode(ImageWriteIntent(emptyList(), true).encode()))
        assertNull(ImageWriteIntent.decode("1|true|../../private~1"))
        assertNull(ImageWriteIntent.decode("1|true|valid~oops"))
    }
}
