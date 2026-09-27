package com.local.parallelvrgallerypro

import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class CoreRegressionTest {
    @Test fun generatedViewerAndClosedViewerNeverEnablePrefetch() {
        assertFalse(ViewerScope.canPrefetch(true, false))
        assertFalse(ViewerScope.canPrefetch(false, true))
        assertTrue(ViewerScope.canPrefetch(true, true))
    }

    @Test fun albumOrderNeverFallsBackToGlobalNeighbors() {
        val all = listOf("other1", "albumLast", "other2", "albumFirst", "other3")
        assertEquals(listOf(3, 1), ViewerScope.indices(all, listOf("albumFirst", "albumLast"), emptySet()))
        assertEquals(listOf(1, 3), ViewerScope.indices(all, emptyList(), setOf("albumFirst", "albumLast")))
        assertTrue(ViewerScope.indices(all, listOf("removedAlbumPhoto"), emptySet()).isEmpty())
        assertEquals(all.indices.toList(), ViewerScope.indices(all, emptyList(), emptySet()))
    }

    @Test fun modelPoolKeepsOnlyOneIdleSessionAndNeverClosesAnActiveSession() {
        val closed = mutableListOf<String>()
        val pool = IdleResourcePool<String, Closeable>(1)
        val a = pool.acquire("a") { Closeable { closed += "a" } }
        val b = pool.acquire("b") { Closeable { closed += "b" } }
        a.close()
        assertTrue(closed.isEmpty())
        b.close()
        assertEquals(listOf("a"), closed)
        val again = pool.acquire("b") { error("Should reuse idle session") }
        pool.close()
        assertEquals(listOf("a"), closed)
        again.close(); again.close()
        assertEquals(listOf("a", "b"), closed)
    }

    @Test fun depthBlurPreservesConstantsAndEdges() {
        for (diameter in listOf(0, 1, 3, 5, 50)) {
            assertArrayEquals(FloatArray(35) { 1f }, DepthFilters.boxMean(FloatArray(35) { 1f }, 7, 5, diameter, false), 0.00001f)
            assertArrayEquals(FloatArray(35), DepthFilters.boxMean(FloatArray(35) { 1f }, 7, 5, diameter, true), 0.00001f)
        }
    }

    @Test fun depthBlurMatchesCenteredReference() {
        val width = 7
        val height = 5
        val input = FloatArray(width * height) { (it % 11) / 11f }
        for (diameter in 0..12) {
            val radius = diameter / 2
            val expected = FloatArray(input.size) { index ->
                val x = index % width
                val y = index / width
                val values = (maxOf(0, y - radius)..minOf(height - 1, y + radius)).flatMap { yy ->
                    (maxOf(0, x - radius)..minOf(width - 1, x + radius)).map { xx -> input[yy * width + xx] }
                }
                values.average().toFloat()
            }
            assertArrayEquals(expected, DepthFilters.boxMean(input, width, height, diameter, false), 0.00001f)
        }
    }

    @Test fun nativeResourceHasOneThreadForItsWholeLifetime() {
        AffinityWorker("test-affinity").use { worker ->
            val threads = mutableListOf<Long>()
            val resource = ThreadBoundResource(worker) {
                threads += Thread.currentThread().id
                Closeable { threads += Thread.currentThread().id }
            }
            resource.use { threads += Thread.currentThread().id }
            resource.close()
            resource.close()
            assertEquals(3, threads.size)
            assertEquals(1, threads.distinct().size)
            assertNotEquals(Thread.currentThread().id, threads.first())
            assertThrows(IllegalStateException::class.java) { resource.use {} }
        }
    }

    @Test fun currentInferenceOvertakesWaitingBackgroundButNotRunningInference() {
        AffinityWorker("test-priority").use { worker ->
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val order = mutableListOf<String>()
            val first = worker.submit { started.countDown(); check(release.await(5, TimeUnit.SECONDS)); order += "running" }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val background = worker.submit(10) { order += "background" }
            val current = worker.submit(0) { order += "current" }
            release.countDown()
            first.awaitNative(); background.awaitNative(); current.awaitNative()
            assertEquals(listOf("running", "current", "background"), order)
        }
    }
}
