package com.local.parallelvrgallerypro

import java.io.Closeable
import org.junit.Assert.*
import org.junit.Test

class StartupAndProgressTest {
    @Test fun deletionDoesNotBlacklistFutureSourcePrefetch() {
        val album = (0..19).map { "album-$it" }
        val all = album + listOf("other-album")
        val guard = GenerationGuard()
        val oldTokens = all.associateWith(guard::token)
        guard.invalidate(all.toSet())
        all.forEach { assertFalse(guard.isCurrent(it, oldTokens.getValue(it))) }
        album.forEach { assertTrue(guard.isCurrent(it, guard.token(it))) }
        val scope = ViewerScope.indices(all, album, album.toSet()).map(all::get)
        assertEquals(album, scope)
        assertFalse("other-album" in scope)
    }

    @Test fun generatedPageCanConsumeExistingWorkButNeverExpandPrefetch() {
        for (vr in listOf(false, true)) {
            for (normal in listOf(false, true)) {
                for (viewer in listOf(false, true)) {
                    assertFalse(ViewerScope.canPrefetch(vr, normal, viewer, true, true, true))
                    assertTrue(ViewerScope.canResumeExisting(true, viewer, true))
                    assertFalse(ViewerScope.canResumeExisting(false, viewer, true))
                }
            }
        }
        assertFalse(ViewerScope.canPrefetch(true, true, false, true, false, true))
    }

    @Test fun normalSourceStillPrefetchesAfterCurrentStarts() {
        val guard = GenerationGuard()
        val first = CurrentImageFirst()
        guard.invalidate((0..24).map(Int::toString).toSet())
        first.select("12")
        assertFalse(first.permits("12", cached = false))
        first.started("12")
        assertTrue(first.permits("12", cached = false))
        assertTrue(ViewerScope.canPrefetch(true, true, true, true, false, true))
        assertTrue(guard.isCurrent("13", guard.token("13")))
        val old = guard.token("13")
        guard.invalidate(setOf("13"))
        assertFalse(guard.isCurrent("13", old))
    }

    @Test fun cachedReadyVideoIsCompleteWithoutAnInMemoryJob() {
        assertEquals(100, VideoProgress.percent(VideoVrState.READY, null))
        assertEquals(100, VideoProgress.percent(VideoVrState.READY, 0f))
        assertEquals(100, VideoProgress.percent(VideoVrState.READY, 0.35f))
        assertNull(VideoProgress.frames(VideoVrState.READY, null, null))
        assertNull(VideoProgress.frames(VideoVrState.READY, 0, 0))
        assertEquals("120/120", VideoProgress.frames(VideoVrState.READY, 117, 120))
    }

    @Test fun activeVideoIsNotReportedAsCompleteUntilFinalizationSucceeds() {
        assertEquals(0, VideoProgress.percent(VideoVrState.QUEUED, null))
        assertEquals(45, VideoProgress.percent(VideoVrState.GENERATING, 0.45f))
        assertEquals(99, VideoProgress.percent(VideoVrState.GENERATING, 1f))
        assertEquals(0, VideoProgress.percent(VideoVrState.FAILED, Float.NaN))
        assertEquals(0, VideoProgress.percent(VideoVrState.GENERATING, -0.1f))
        assertEquals(37, VideoProgress.percent(VideoVrState.PAUSED, 0.37f))
        assertEquals("37/100", VideoProgress.frames(VideoVrState.PAUSED, 37, 100))
    }

    @Test fun warmedSessionIsReusedAndVisualSettingsDoNotRecreateIt() {
        val settings = AppSettings()
        val key = settings.toParams().depthSessionKey()
        assertEquals(key, settings.copy(edgeMode = DepthEdgeMode.CENTERED_V7, depthScale = 60f, blurRadius = 9).toParams().depthSessionKey())
        assertNotEquals(key, settings.copy(useGpu = true).toParams().depthSessionKey())
        assertNotEquals(key, settings.copy(modelThreads = 8).toParams().depthSessionKey())
        assertNotEquals(key, settings.copy(imageModelId = "another-model").toParams().depthSessionKey())
        var created = 0
        var closed = 0
        val pool = IdleResourcePool<String, Closeable>(1)
        pool.acquire(key) { created++; Closeable { closed++ } }.close()
        pool.acquire(key) { error("First photo must reuse the preloaded session") }.close()
        assertEquals(1, created)
        assertEquals(0, closed)
        pool.close()
        assertEquals(1, closed)
    }

    @Test fun failedPreloadDoesNotPoisonThePoolForExplicitGeneration() {
        val pool = IdleResourcePool<String, Closeable>(1)
        assertThrows(IllegalStateException::class.java) { pool.acquire("model") { error("invalid local model") } }
        var created = 0
        pool.acquire("model") { created++; Closeable {} }.close()
        assertEquals(1, created)
        pool.close()
    }

    @Test fun preparationAndInferenceKeepTheSameNativeOwnerThread() {
        AffinityWorker("prepare-test").use { worker ->
            val calls = mutableListOf<Pair<String, Thread>>()
            val resource = ThreadBoundResource(worker) {
                calls += "create" to Thread.currentThread()
                Closeable { calls += "close" to Thread.currentThread() }
            }
            resource.use(100) { calls += "prepare" to Thread.currentThread() }
            resource.use(0) { calls += "infer" to Thread.currentThread() }
            resource.close()
            assertEquals(listOf("create", "prepare", "infer", "close"), calls.map { it.first })
            assertEquals(1, calls.map { it.second }.distinct().size)
            assertNotEquals(Thread.currentThread(), calls.first().second)
        }
    }
}
