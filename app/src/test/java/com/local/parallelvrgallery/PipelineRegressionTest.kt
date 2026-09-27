package com.local.parallelvrgallerypro

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class PipelineRegressionTest {
    private class Frame(val index: Int) { val disposed = AtomicInteger() }

    @Test(timeout = 10000) fun parallelDepthStillProducesOrderedFramesWithABoundedLiveSet() {
        val live = AtomicInteger()
        val peak = AtomicInteger()
        val frames = ConcurrentHashMap<Int, Frame>()
        val generated = mutableListOf<Int>()
        val encoded = mutableListOf<Int>()
        val persisted = mutableListOf<Int>()
        val result = OrderedFramePipeline<Frame>(4, 2).run(0, 80,
            load = { index -> Frame(index).also { frames[index] = it; peak.accumulateAndGet(live.incrementAndGet(), ::maxOf) } },
            depth = { frame, _ -> if (frame.index % 2 == 0) Thread.sleep(2) },
            generate = { generated += it.index; it.index % 3 != 0 },
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
        for (failure in listOf("decode", "depth", "generate", "persist", "encode", "pause")) {
            val frames = ConcurrentHashMap<Int, Frame>()
            fun fail(stage: String, index: Int) { if (stage == failure && index == 5) error("$stage failure") }
            assertThrows(IllegalStateException::class.java) {
                OrderedFramePipeline<Frame>(3, 2).run(0, 100,
                    load = { index -> fail("decode", index); Frame(index).also { frames[index] = it } },
                    depth = { frame, _ -> fail("depth", frame.index) },
                    generate = { fail("generate", it.index); true },
                    persist = { fail("persist", it.index) },
                    encode = { frame, _ -> fail("encode", frame.index); fail("pause", frame.index) },
                    dispose = { it.disposed.incrementAndGet() },
                )
            }
            assertTrue("Resource leak or double disposal after $failure", frames.values.all { it.disposed.get() == 1 })
        }
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
