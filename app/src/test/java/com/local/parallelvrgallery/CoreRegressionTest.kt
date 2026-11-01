package com.local.parallelvrgallerypro

import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class CoreRegressionTest {
    @Test fun videoMissCannotEvictOrAllocateOverThePreparedImageModel() {
        val closed=mutableListOf<String>();val pool=IdleResourcePool<String,Closeable>(1)
        pool.acquire("image770"){Closeable{closed+="image770"}}.close()
        assertNull(pool.acquireIfPresent("video518"))
        assertTrue(closed.isEmpty())
        pool.acquireIfPresent("image770")!!.close()
        assertTrue(closed.isEmpty());pool.close();assertEquals(listOf("image770"),closed)
    }
    @Test fun sharedVideoLeaseRetainsNativeSessionAfterImageLeaseAndPoolClose() {
        val events=mutableListOf<Pair<String,Long>>()
        AffinityWorker("shared-session-owner").use {worker ->
            val pool=IdleResourcePool<String,ThreadBoundResource<Closeable>>(1)
            val key=AppSettings(useGpu=true).toParams().depthSessionKey()
            val image=pool.acquire(key){ThreadBoundResource(worker){events+="create" to Thread.currentThread().id;Closeable{events+="close" to Thread.currentThread().id}}}
            val video=pool.acquireIfPresent(key) ?: error("Same inference configuration should reuse the prepared session")
            image.close();pool.close()
            video.value.use {events+="video" to Thread.currentThread().id}
            assertEquals(listOf("create","video"),events.map{it.first})
            video.close();video.close()
            assertEquals(listOf("create","video","close"),events.map{it.first})
            assertEquals(1,events.map{it.second}.distinct().size)
        }
    }
    @Test fun sharingNeverAliasesDifferentModelOrPrecisionConfigurations() {
        val base=AppSettings(useGpu=true).toParams()
        val variants=listOf(base,base.copy(depthModel="depth_anything_v2_small_770_v1"),base.copy(useGpu=false),base.copy(modelThreads=2),base.copy(gpuTestMode=GpuTestMode.ACCURATE_OPENGL),base.copy(forceGpuNoFallback=false))
        assertEquals(variants.size,variants.map{it.depthSessionKey()}.distinct().size)
    }
    @Test fun generatedViewerAndClosedViewerNeverEnablePrefetch() {
        assertFalse(ViewerScope.canPrefetch(true, false, true, true, true, true))
        assertFalse(ViewerScope.canPrefetch(false, true, true, true, false, true))
        assertTrue(ViewerScope.canPrefetch(true, true, true, true, false, true))
        // A manual regenerate on the home page used to set vrMode and fall back to galleryAnchorIndex.
        assertFalse(ViewerScope.canPrefetch(true, true, false, false, true, false))
        assertFalse(ViewerScope.canPrefetch(true, true, false, true, false, true))
        assertFalse(ViewerScope.canPrefetch(true, true, true, false, false, true))
        assertFalse(ViewerScope.canPrefetch(true, true, true, true, true, true))
        assertFalse(ViewerScope.canPrefetch(true, true, true, true, false, false))
    }

    @Test fun albumOrderNeverFallsBackToGlobalNeighbors() {
        val all = listOf("other1", "albumLast", "other2", "albumFirst", "other3")
        assertEquals(listOf(3, 1), ViewerScope.indices(all, listOf("albumFirst", "albumLast"), emptySet()))
        assertEquals(listOf(1, 3), ViewerScope.indices(all, emptyList(), setOf("albumFirst", "albumLast")))
        assertTrue(ViewerScope.indices(all, listOf("removedAlbumPhoto"), emptySet()).isEmpty())
        assertTrue(ViewerScope.indices(all, emptyList(), emptySet()).isEmpty())
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

    @Test fun generatedViewerKeepsItsCapturedOrderWhenTheLibraryGrows() {
        val captured = listOf("generatedB", "generatedA")
        val before = listOf("original", "generatedA", "generatedB")
        val after = listOf("newOutput", "original", "generatedA", "generatedB", "anotherOutput")
        assertEquals(captured, ViewerScope.indices(before, captured, captured.toSet()).map(before::get))
        assertEquals(captured, ViewerScope.indices(after, captured, captured.toSet()).map(after::get))
        assertTrue(ViewerScope.indices(listOf("original", "newOutput"), captured, captured.toSet()).isEmpty())
        assertTrue(ViewerScope.indices(after, emptyList(), emptySet()).isEmpty())
    }

    @Test fun edgeModeSwitchRoutesToTheExactShippedFilters() {
        val input = FloatArray(35) { (it % 9) / 9f }
        for (invert in listOf(false, true)) {
            for (diameter in listOf(0, 1, 3, 5, 15, 25)) {
                assertArrayEquals(DepthFilters.legacyV6(input, 7, 5, diameter, invert),
                    DepthFilters.process(input, 7, 5, diameter, invert, DepthEdgeMode.LEGACY_V6), 0f)
                assertArrayEquals(DepthFilters.boxMean(input, 7, 5, diameter, invert),
                    DepthFilters.process(input, 7, 5, diameter, invert, DepthEdgeMode.CENTERED_V7), 0f)
            }
        }
    }

    @Test fun modeDefaultsToLegacyAndRestoresExistingVideoQueueVersion() {
        assertEquals(DepthEdgeMode.LEGACY_V6, DepthEdgeMode.restore(null))
        assertEquals(DepthEdgeMode.LEGACY_V6, DepthEdgeMode.restore("invalid"))
        assertEquals(DepthEdgeMode.CENTERED_V7, DepthEdgeMode.restore(null, "model_depthV7_s40_b3_encoderV14"))
        assertEquals(DepthEdgeMode.LEGACY_V6, DepthEdgeMode.restore(null, "model_depthV6_s40_b3_encoderV14"))
        assertEquals(DepthEdgeMode.CENTERED_V7, DepthEdgeMode.restore("CENTERED_V7"))
        assertEquals(DepthEdgeMode.LEGACY_V6, DepthEdgeMode.restore("LEGACY_V6", "model_depthV7_s40"))
        assertNotEquals(DepthEdgeMode.LEGACY_V6.cacheVersion, DepthEdgeMode.CENTERED_V7.cacheVersion)
    }

    @Test fun edgeModeIsPropagatedToImageVideoAndSeparateCacheKeys() {
        val legacy = AppSettings()
        val centered = legacy.copy(edgeMode = DepthEdgeMode.CENTERED_V7)
        assertEquals(DepthEdgeMode.LEGACY_V6, legacy.toParams().edgeMode)
        assertEquals(DepthEdgeMode.CENTERED_V7, centered.toParams().edgeMode)
        assertEquals(DepthEdgeMode.CENTERED_V7, centered.toVideoParams().toVrParams().edgeMode)
        assertEquals(legacy.toParams().cacheVersion().replace("_depthV6_", "_depthV7_"), centered.toParams().cacheVersion())
        assertEquals(legacy.toVideoParams().cacheVersion().replace("_depthV6_", "_depthV7_"), centered.toVideoParams().cacheVersion())
        assertNotEquals(legacy.toParams().cacheVersion(), centered.toParams().cacheVersion())
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
