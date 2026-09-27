package com.local.parallelvrgallerypro

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class DeletionAndStartupTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun publicationReplacesWholeCacheOnlyAfterCompleteStaging() {
        val work = folder.newFolder("work")
        File(work, "left.jpg").writeText("new left")
        File(work, "right.jpg").writeText("new right")
        val target = folder.newFolder("photo", "version")
        File(target, "left.jpg").writeText("old left")
        ImageCachePublication.commit(work, target)
        assertEquals("new left", File(target, "left.jpg").readText())
        assertEquals("new right", File(target, "right.jpg").readText())
        assertFalse(work.exists())
    }

    @Test fun cancelledStagingNeverRecreatesDeletedCacheDirectory() {
        val guard = GenerationGuard()
        val token = guard.token("photo")
        val work = folder.newFolder("work")
        File(work, "left.jpg").writeText("stale")
        val target = File(folder.root, "photo/version")
        guard.invalidate(setOf("photo"))
        assertFalse(guard.ifCurrent("photo", token) { ImageCachePublication.commit(work, target) })
        assertFalse(target.exists())
        assertTrue(work.deleteRecursively())
    }

    @Test fun invalidStagingDoesNotRemovePreviousValidCache() {
        val target = folder.newFolder("version")
        File(target, "left.jpg").writeText("existing")
        assertThrows(IllegalArgumentException::class.java) { ImageCachePublication.commit(File(folder.root, "missing"), target) }
        assertEquals("existing", File(target, "left.jpg").readText())
    }

    @Test fun rebalanceCannotReviveOldWindowManualOrHistoricalPauses() {
        for (viewerOpen in listOf(false, true)) {
            assertFalse(ViewerScope.canResumeExisting(false, viewerOpen, generatedSurface = true))
            assertFalse(ViewerScope.canResumeExisting(false, viewerOpen, generatedSurface = false))
        }
        assertTrue(ViewerScope.canResumeExisting(true, viewerOpen = false, generatedSurface = false))
        assertTrue(ViewerScope.canResumeExisting(true, viewerOpen = true, generatedSurface = true))
        assertFalse(ViewerScope.canResumeExisting(true, viewerOpen = true, generatedSurface = false))
    }

    @Test fun deletedInFlightJobCannotPublishOrReportReady() {
        val guard = GenerationGuard()
        val token = guard.token("photo")
        guard.invalidate(setOf("photo"))
        assertFalse(guard.ifCurrent("photo", token) { fail("Deleted output must not be committed") })
        assertFalse(guard.permitsAutomatic("photo"))
    }

    @Test fun explicitRegenerationDoesNotRevalidateOlderWork() {
        val guard = GenerationGuard()
        val old = guard.token("photo")
        guard.invalidate(setOf("photo"))
        guard.explicitlyRequest("photo")
        assertFalse(guard.isCurrent("photo", old))
        assertTrue(guard.isCurrent("photo", guard.token("photo")))
        assertTrue(guard.permitsAutomatic("unrelated"))
    }

    @Test fun deletionSuppressionSurvivesRestartButExplicitOpenRemovesIt() {
        var persisted = emptySet<String>()
        GenerationGuard(persistDeleted = { persisted = it }).invalidate(setOf("photo"))
        val restored = GenerationGuard(persisted) { persisted = it }
        assertFalse(restored.permitsAutomatic("photo"))
        restored.explicitlyRequest("photo")
        assertTrue(persisted.isEmpty())
    }

    @Test fun queuedCompletionAfterDeleteCannotResurrectCache() {
        val guard = GenerationGuard()
        val rendered = CountDownLatch(1)
        val deleted = CountDownLatch(1)
        val old = guard.token("photo")
        var committed = false
        val worker = thread {
            rendered.countDown()
            check(deleted.await(5, TimeUnit.SECONDS))
            committed = guard.ifCurrent("photo", old) { }
        }
        assertTrue(rendered.await(5, TimeUnit.SECONDS))
        guard.invalidate(setOf("photo"))
        deleted.countDown()
        worker.join(5000)
        assertFalse(worker.isAlive)
        assertFalse(committed)
    }

    @Test fun currentImageStartsBeforeNeighborsButCachedImageCanSkipStartup() {
        val gate = CurrentImageFirst()
        gate.select("current")
        assertFalse(gate.permits("current", cached = false))
        gate.started("previous")
        assertFalse(gate.permits("current", cached = false))
        gate.started("current")
        assertTrue(gate.permits("current", cached = false))
        gate.select("next")
        assertFalse(gate.permits("next", cached = false))
        assertTrue(gate.permits("next", cached = true))
        gate.select(null)
        assertFalse(gate.permits("next", cached = true))
    }

    @Test fun restoredDepthV6PreservesThePreviouslyShippedDisparityGain() {
        val depth = FloatArray(518 * 518) { 1f }
        assertEquals(4f / 9f, DepthFilters.legacyV6(depth, 518, 518, 3, false)[259 * 518 + 259], 0.000001f)
        assertEquals(9f / 25f, DepthFilters.legacyV6(depth, 518, 518, 5, false)[259 * 518 + 259], 0.000001f)
        assertEquals(1f, DepthFilters.boxMean(depth, 518, 518, 3, false)[259 * 518 + 259], 0.000001f)
        val input = floatArrayOf(0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f, 0f)
        assertArrayEquals(input, DepthFilters.legacyV6(input, 3, 3, 0, false), 0f)
        assertArrayEquals(FloatArray(input.size) { 1 - input[it] }, DepthFilters.legacyV6(input, 3, 3, 1, true), 0f)
    }

    @Test fun depthV6MatchesIndependentCausalReferenceIncludingBordersAndInversion() {
        val w = 19
        val h = 17
        val depth = FloatArray(w * h) { i -> if (i % w < w / 2) 0.1f else 0.85f + (i % 7) * 0.01f }
        for (diameter in listOf(0, 1, 3, 5, 9, 15, 25)) {
            val r = diameter / 2
            val horizontal = FloatArray(depth.size) { i ->
                val x = i % w
                val sum = (maxOf(0, x - r)..x).sumOf { depth[i / w * w + it].toDouble() }
                (sum / (minOf(x + r + 1, w) - maxOf(0, x - r))).toFloat()
            }
            val expected = FloatArray(depth.size) { i ->
                val y = i / w
                val sum = (maxOf(0, y - r)..y).sumOf { horizontal[it * w + i % w].toDouble() }
                (sum / (minOf(y + r + 1, h) - maxOf(0, y - r))).toFloat()
            }
            assertArrayEquals(expected, DepthFilters.legacyV6(depth, w, h, diameter, false), 0.00001f)
            assertArrayEquals(FloatArray(depth.size) { 1f - expected[it] }, DepthFilters.legacyV6(depth, w, h, diameter, true), 0.00001f)
        }
    }
}
