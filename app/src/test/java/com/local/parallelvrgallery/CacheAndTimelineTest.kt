package com.local.parallelvrgallerypro

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class CacheAndTimelineTest {
    private data class Entry(val key: String, val version: String, val created: Long)
    @Test fun diskScanCannotOverwriteJobsCompletedOrDeletedWhileItWasRunning() {
        val index = VersionedIndex<Entry>({ it.key }, { it.version }, { it.created })
        index.put(Entry("old", "v1", 1), 10)
        val revision = index.scanRevision()
        index.put(Entry("new", "v1", 2), 30)
        index.removeWhere { it.key == "old" }
        index.replaceFromScan(listOf(Entry("old", "v1", 1) to 10L), revision)
        assertNull(index.get("old"))
        assertNotNull(index.get("new"))
        assertEquals(1 to 30L, index.summaries()["v1"])
    }

    @Test fun cacheIndexReplacesInsteadOfDoubleCountingAndDeletesOnlyRequestedVersion() {
        val index = VersionedIndex<Entry>({ it.key }, { it.version }, { it.created })
        index.put(Entry("a", "v1", 1), 20)
        index.put(Entry("a", "v2", 2), 40)
        index.put(Entry("b", "v1", 3), 30)
        index.put(Entry("a", "v1", 4), 50)
        assertEquals("v1", index.get("a")?.version)
        assertEquals(2 to 80L, index.summaries()["v1"])
        index.removeWhere { it.version == "v1" }
        assertNull(index.get("b"))
        assertEquals("v2", index.get("a")?.version)
        assertEquals(1 to 40L, index.summaries()["v2"])
        index.clear()
        assertTrue(index.all().isEmpty())
    }

    @Test fun lruHasAByteBudgetOnEveryInsertion() {
        val cache = WeightedLru<String, String>(10) { it.length.toLong() }
        cache.put("a", "12345"); cache.put("b", "12345")
        assertNotNull(cache["a"])
        cache.put("c", "12345")
        assertNull(cache["b"])
        assertEquals(10, cache.bytes)
        cache.put("a", "1")
        assertEquals(6, cache.bytes)
        cache.put("huge", "12345678901")
        assertNull(cache["huge"])
        assertTrue(cache.bytes <= cache.budget)
    }

    @Test fun queuePersistenceCoalescesAndAtomicallyReplacesTheLastSnapshot() {
        val root = Files.createTempDirectory("queue-regression").toFile()
        try {
            val file = root.resolve("queue.tsv")
            file.writeText("old")
            BufferedTextFile(file, 60_000).use { store ->
                assertEquals("old", store.read())
                repeat(1000) { store.write("version=$it") }
                assertEquals("old", file.readText())
                assertEquals("version=999", store.read())
                store.flush()
                assertEquals("version=999", file.readText())
                assertFalse(root.resolve("queue.tsv.tmp").exists())
                store.write("")
            }
            assertEquals("", file.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun highFrameRateIsNotClampedIntoADifferentDuration() {
        val pts = LongArray(120) { it * 1_000_000L / 120 }
        val timeline = VideoTimeline(pts)
        assertEquals(120, timeline.size)
        assertEquals(991666L, timeline[119])
    }

    @Test fun variableAndFractionalPresentationTimestampsArePreserved() {
        val pts = longArrayOf(1_000_000, 1_100_000, 1_033_367, 1_050_000, 1_300_000)
        val timeline = VideoTimeline(pts)
        assertArrayEquals(pts.sortedArray(), timeline.timestampsUs)
        assertEquals(1, timeline.indexOf(1_033_367))
        assertEquals(1_000_000L, timeline[0]) // Same origin as untouched audio samples.
        assertThrows(IllegalArgumentException::class.java) { VideoTimeline(longArrayOf(1, 1)) }
    }

    @Test fun yuvNeutralRangeAndColorMetadataAreRespected() {
        for (standard in listOf(1, 4, 6)) {
            assertEquals(0xff000000.toInt(), YuvRgb(standard, false).pixel(16, 128, 128))
            assertEquals(0xffffffff.toInt(), YuvRgb(standard, false).pixel(235, 128, 128))
            assertEquals(0xff000000.toInt(), YuvRgb(standard, true).pixel(0, 128, 128))
            assertEquals(0xffffffff.toInt(), YuvRgb(standard, true).pixel(255, 128, 128))
        }
        assertNotEquals(YuvRgb(1, false).pixel(100, 90, 170), YuvRgb(4, false).pixel(100, 90, 170))
    }
}
