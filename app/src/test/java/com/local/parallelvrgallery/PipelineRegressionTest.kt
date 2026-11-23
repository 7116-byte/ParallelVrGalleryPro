package com.local.parallelvrgallerypro

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class PipelineRegressionTest {
    private class Frame(val index: Int) {
        val disposed = AtomicInteger()
        var prepared = false
        var rawDepth = false
        var restored = false
    }

    @Test(timeout = 10000) fun parallelDepthStillProducesOrderedFramesWithABoundedLiveSet() {
        val live = AtomicInteger()
        val peak = AtomicInteger()
        val frames = ConcurrentHashMap<Int, Frame>()
        val generated = mutableListOf<Int>()
        val encoded = mutableListOf<Int>()
        val persisted = mutableListOf<Int>()
        val result = OrderedFramePipeline<Frame>(4, 2).run(0, 80,
            load = { index -> Frame(index).also { frames[index] = it; peak.accumulateAndGet(live.incrementAndGet(), ::maxOf) } },
            prepareInput = { it.prepared = true },
            depth = { frame, _ ->
                assertTrue(frame.prepared)
                if (frame.index % 2 == 0) Thread.sleep(2)
                frame.rawDepth = true
            },
            restoreDepth = { assertTrue(it.rawDepth); it.restored = true },
            generate = { assertTrue(it.restored); generated += it.index; it.index % 3 != 0 },
            persist = { assertEquals(0, it.disposed.get()); Thread.sleep(1); persisted += it.index },
            encode = { frame, _ -> assertEquals(0, frame.disposed.get()); encoded += frame.index },
            dispose = { it.disposed.incrementAndGet(); live.decrementAndGet() },
        )
        assertEquals((0 until 80).toList(), generated)
        assertEquals(generated, encoded)
        assertEquals((0 until 80).filter { it % 3 != 0 }, persisted)
        assertEquals(persisted.size, result.writes)
        assertTrue(peak.get() <= 4)
        assertEquals(0, live.get())
        assertTrue(frames.values.all { it.disposed.get() == 1 })
    }

    @Test(timeout = 15000) fun everyStageFailureReleasesAllOwnedFramesExactlyOnce() {
        for (failure in listOf("decode", "prepare", "depth", "restore", "generate", "persist", "encode")) {
            val frames = ConcurrentHashMap<Int, Frame>()
            fun fail(stage: String, index: Int) { if (stage == failure && index == 5) error("$stage failure") }
            assertThrows(IllegalStateException::class.java) {
                OrderedFramePipeline<Frame>(3, 2).run(0, 100,
                    load = { index -> fail("decode", index); Frame(index).also { frames[index] = it } },
                    prepareInput = { fail("prepare", it.index) },
                    depth = { frame, _ -> fail("depth", frame.index) },
                    restoreDepth = { fail("restore", it.index) },
                    generate = { fail("generate", it.index); true },
                    persist = { fail("persist", it.index) },
                    encode = { frame, _ -> fail("encode", frame.index) },
                    dispose = { it.disposed.incrementAndGet() },
                )
            }
            assertTrue("Resource leak or double disposal after $failure", frames.values.all { it.disposed.get() == 1 })
        }
    }

    @Test(timeout = 12000) fun bothCpuStagesOverlapWithTheSingleDepthWorker() {
        val secondInputPrepared = CountDownLatch(1)
        val secondDepthInferred = CountDownLatch(1)
        val encoded = mutableListOf<Int>()
        OrderedFramePipeline<Frame>(2, 1).run(0, 2,
            load = ::Frame,
            prepareInput = { if (it.index == 1) secondInputPrepared.countDown() },
            depth = { frame, _ ->
                if (frame.index == 0) {
                    check(secondInputPrepared.await(4, TimeUnit.SECONDS)) { "Depth blocked next input preparation" }
                } else {
                    secondDepthInferred.countDown()
                }
            },
            restoreDepth = {
                if (it.index == 0) {
                    check(secondDepthInferred.await(4, TimeUnit.SECONDS)) { "Depth restore blocked next inference" }
                }
            },
            generate = { false }, persist = {}, encode = { frame, _ -> encoded += frame.index }, dispose = {},
        )
        assertEquals(listOf(0, 1), encoded)
    }

    @Test(timeout = 10000) fun cancelledStageReleasesBufferedFramesAndStopsItsPeers() {
        val frames = ConcurrentHashMap<Int, Frame>()
        val fourthInputPrepared = CountDownLatch(1)
        assertThrows(CancellationException::class.java) {
            OrderedFramePipeline<Frame>(4, 1).run(0, 100,
                load = { index -> Frame(index).also { frames[index] = it } },
                prepareInput = { if (it.index == 3) fourthInputPrepared.countDown() },
                depth = { frame, _ ->
                    check(fourthInputPrepared.await(4, TimeUnit.SECONDS)) { "Preparation did not reach its buffered frames" }
                    if (frame.index == 0) throw CancellationException("cancel pending video")
                },
                restoreDepth = {}, generate = { false }, persist = {}, encode = { _, _ -> },
                dispose = { it.disposed.incrementAndGet() },
            )
        }
        assertTrue(frames.size >= 4)
        assertTrue(frames.values.all { it.disposed.get() == 1 })
    }

    @Test(timeout = 10000) fun encodingDoesNotWaitForTheSameFramesCacheWrite() {
        val encoded = CountDownLatch(1)
        OrderedFramePipeline<Frame>(2, 1).run(0, 1,
            load = { Frame(it) }, depth = { _, _ -> }, generate = { true },
            persist = { check(encoded.await(3, TimeUnit.SECONDS)) { "Writer blocked encoding" } },
            encode = { _, _ -> encoded.countDown() }, dispose = {},
        )
    }

    @Test fun sharedLeaseReleasesAfterBothConsumersOnly() {
        val disposed = AtomicInteger()
        val encoder = SharedLease("pixels") { disposed.incrementAndGet() }
        val writer = encoder.fork()
        encoder.close(); encoder.close()
        assertEquals(0, disposed.get())
        assertEquals("pixels", writer.value)
        writer.close(); writer.close()
        assertEquals(1, disposed.get())
        assertThrows(IllegalStateException::class.java) { writer.fork() }
    }
}
