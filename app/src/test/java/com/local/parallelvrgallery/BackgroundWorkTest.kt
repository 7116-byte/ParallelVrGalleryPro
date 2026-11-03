package com.local.parallelvrgallerypro

import java.io.Closeable
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class BackgroundWorkTest {
    @Test fun batchLeaseDoesNotStopBetweenConsumers() {
        var starts = 0; var stops = 0
        val lease = WorkLease({ starts++ }, { stops++ })
        val first = lease.acquire(); val second = lease.acquire()
        first.close(); assertTrue(lease.active()); assertEquals(0, stops)
        second.close(); second.close()
        assertEquals(1, starts); assertEquals(1, stops); assertFalse(lease.active())
    }
    @Test fun rejectedStartCanRetryWithoutLeakingCount() {
        var reject = true; var starts = 0
        val lease = WorkLease({ if (reject) error("foreground denied"); starts++ }, {})
        try { lease.acquire(); fail("must surface denial") } catch (_: IllegalStateException) { }
        assertFalse(lease.active()); reject = false
        lease.acquire().use { assertTrue(lease.active()) }; assertEquals(1, starts)
    }
    @Test fun oldWorkerReleaseCannotStopNewGeneration() {
        var stops = 0
        val lease = WorkLease({}, { stops++ })
        val old = lease.acquire(); lease.stopAll(); val fresh = lease.acquire()
        old.close(); assertTrue(lease.active()); assertEquals(1, stops)
        fresh.close(); assertEquals(2, stops)
    }
    @Test fun ledgerSurvivesProcessRestartButOnlyAdmitsExplicitKeys() = withLedger { file ->
        BackgroundRunLedger(file).authorize(setOf("VIDEO_101", "VIDEO_102"))
        assertEquals(setOf("VIDEO_101", "VIDEO_102"), BackgroundRunLedger(file).claimSystemRestart(1000))
        assertFalse(BackgroundRunLedger(file).snapshot().contains("VIDEO_999"))
    }
    @Test fun pauseAndDeletionRevokeRestartAdmissionDurably() = withLedger { file ->
        val ledger = BackgroundRunLedger(file)
        ledger.authorize(setOf("VIDEO_101", "VIDEO_102")); ledger.revoke(setOf("VIDEO_101"))
        assertEquals(setOf("VIDEO_102"), BackgroundRunLedger(file).claimSystemRestart(1000))
        ledger.revoke(setOf("VIDEO_102")); assertTrue(BackgroundRunLedger(file).claimSystemRestart(2000).isEmpty())
    }
    @Test fun repeatedNativeCrashesHaveBoundedAutomaticRetries() = withLedger { file ->
        BackgroundRunLedger(file).authorize(setOf("VIDEO_101"))
        repeat(3) { assertEquals(setOf("VIDEO_101"), BackgroundRunLedger(file).claimSystemRestart(1000L + it)) }
        assertTrue(BackgroundRunLedger(file).claimSystemRestart(1010).isEmpty())
        assertEquals(setOf("VIDEO_101"), BackgroundRunLedger(file).claimSystemRestart(1_000_000))
    }
    @Test fun explicitUserStartResetsCrashBudget() = withLedger { file ->
        val ledger = BackgroundRunLedger(file); ledger.authorize(setOf("VIDEO_101"))
        repeat(3) { ledger.claimSystemRestart(1000) }; assertTrue(ledger.claimSystemRestart(1000).isEmpty())
        ledger.authorize(setOf("VIDEO_101")); assertFalse(ledger.claimSystemRestart(1000).isEmpty())
    }
    @Test fun malformedOrPartialLedgerNeverStartsWork() = withLedger { file ->
        file.writeText("PVG_RUN_1\nnot-a-counter\n0\nVIDEO_101")
        assertTrue(BackgroundRunLedger(file).claimSystemRestart(1000).isEmpty())
        file.writeText("unknown\n0\n0\nVIDEO_101")
        assertTrue(BackgroundRunLedger(file).snapshot().isEmpty())
    }
    @Test fun invalidKeysCannotAuthorizePrivateImageWorkOrPaths() = withLedger { file ->
        val ledger = BackgroundRunLedger(file)
        listOf("IMAGE_1", "../VIDEO_1", "VIDEO_1\nVIDEO_2").forEach {
            try { ledger.authorize(setOf(it)); fail("invalid key") } catch (_: IllegalArgumentException) { }
        }
        assertTrue(ledger.snapshot().isEmpty())
    }
    @Test fun completedAndActiveJobsAreSkippedByStartSelected() {
        assertTrue(VideoCommandPolicy.canStart(VideoVrState.PAUSED)); assertTrue(VideoCommandPolicy.canStart(VideoVrState.FAILED))
        assertFalse(VideoCommandPolicy.canStart(VideoVrState.READY)); assertFalse(VideoCommandPolicy.canStart(VideoVrState.QUEUED))
        assertFalse(VideoCommandPolicy.canStart(VideoVrState.GENERATING))
    }
    @Test fun onlyActiveJobsCanBePaused() {
        VideoVrState.entries.forEach { assertEquals(it == VideoVrState.QUEUED || it == VideoVrState.GENERATING, VideoCommandPolicy.canPause(it)) }
    }
    @Test fun automaticRestoreNeverRevivesPausedFailedOrUnrelatedJobs() {
        val admitted = setOf("VIDEO_1")
        VideoVrState.entries.forEach {
            assertEquals(it == VideoVrState.QUEUED || it == VideoVrState.GENERATING, VideoCommandPolicy.canRestoreAutomatically("VIDEO_1", it, true, admitted))
            assertFalse(VideoCommandPolicy.canRestoreAutomatically("VIDEO_2", it, true, admitted))
            assertFalse(VideoCommandPolicy.canRestoreAutomatically("VIDEO_1", it, false, admitted))
        }
    }
    @Test fun lowMemoryTrimClosesOnlyIdleResourcesAndAllowsRecreation() {
        var closed = 0; var created = 0
        val pool = IdleResourcePool<String, Closeable>()
        fun make(): Closeable { created++; return Closeable { closed++ } }
        val active = pool.acquire("active", ::make)
        pool.acquire("idle", ::make).close()
        assertEquals(1, pool.trimIdle()); assertEquals(1, closed)
        assertNotNull(pool.acquireIfPresent("active")?.also { it.close() })
        assertNull(pool.acquireIfPresent("idle"))
        pool.acquire("idle", ::make).close(); assertEquals(3, created)
        active.close(); pool.close(); assertEquals(3, closed)
    }
    private fun withLedger(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("pvg-background-test").toFile()
        try { block(File(dir, "run.txt")) } finally { dir.deleteRecursively() }
    }
}
