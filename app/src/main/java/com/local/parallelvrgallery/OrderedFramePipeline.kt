package com.local.parallelvrgallerypro

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore

internal data class FramePipelineStats(
    val decoded: Int, val depths: Int, val generated: Int, val writes: Int, val lastWriteMs: Long,
)
internal data class FramePipelineResult(val writes: Int, val writeTimeMs: Long)

/** Bounded ownership from decode to both consumers; temporal generation and encoding stay ordered. */
internal class OrderedFramePipeline<T>(private val maxInFlight: Int, private val workers: Int) {
    init { require(maxInFlight >= 1 && workers >= 1) }

    fun run(
        first: Int,
        endExclusive: Int,
        load: (Int) -> T,
        depth: (T, Int) -> Unit,
        generate: (T) -> Boolean,
        persist: (T) -> Unit,
        encode: (T, FramePipelineStats) -> Unit,
        dispose: (T) -> Unit,
        checkActive: () -> Unit = {},
        beforeConsume: () -> Unit = {},
    ): FramePipelineResult {
        data class Frame<T>(val index: Int, val data: T)
        val permits = Semaphore(maxInFlight)
        val decoded = Channel<SharedLease<Frame<T>>>(2, onUndeliveredElement = { it.close() })
        val depths = Channel<SharedLease<Frame<T>>>(2, onUndeliveredElement = { it.close() })
        val generated = Channel<SharedLease<Frame<T>>>(2, onUndeliveredElement = { it.close() })
        val writes = Channel<SharedLease<Frame<T>>>(2, onUndeliveredElement = { it.close() })
        val decodeCount = AtomicInteger()
        val depthCount = AtomicInteger()
        val generatedCount = AtomicInteger()
        val writeCount = AtomicInteger()
        val completedWrites = AtomicInteger()
        val lastWriteMs = AtomicLong()
        val writeMs = AtomicLong()
        fun stats() = FramePipelineStats(
            decodeCount.get().coerceAtLeast(0), depthCount.get().coerceAtLeast(0),
            generatedCount.get().coerceAtLeast(0), writeCount.get().coerceAtLeast(0), lastWriteMs.get(),
        )
        try {
            runBlocking {
                val writer = launch(Dispatchers.IO) {
                    for (lease in writes) lease.use {
                        checkActive()
                        val started = System.nanoTime()
                        persist(it.value.data)
                        val elapsed = (System.nanoTime() - started) / 1_000_000L
                        lastWriteMs.set(elapsed)
                        writeMs.addAndGet(elapsed)
                        completedWrites.incrementAndGet()
                        writeCount.decrementAndGet()
                    }
                }
                val producer = launch(Dispatchers.IO) {
                    try {
                        for (index in first until endExclusive) {
                            checkActive()
                            permits.acquire()
                            val value = try { load(index) } catch (error: Throwable) { permits.release(); throw error }
                            val lease = SharedLease(Frame(index, value)) {
                                try { dispose(it.data) } finally { permits.release() }
                            }
                            try {
                                decodeCount.incrementAndGet()
                                decoded.send(lease)
                            } catch (error: Throwable) { lease.close(); throw error }
                        }
                    } finally { decoded.close() }
                }
                val depthJobs = (0 until workers).map { worker ->
                    launch(Dispatchers.Default) {
                        for (lease in decoded) {
                            decodeCount.decrementAndGet()
                            try {
                                checkActive()
                                depth(lease.value.data, worker)
                                depthCount.incrementAndGet()
                                depths.send(lease)
                            } catch (error: Throwable) { lease.close(); throw error }
                        }
                    }
                }
                val closer = launch { depthJobs.forEach { it.join() }; depths.close() }
                val generator = launch(Dispatchers.Default) {
                    val ordered = sortedMapOf<Int, SharedLease<Frame<T>>>()
                    var expected = first
                    try {
                        for (received in depths) {
                            depthCount.decrementAndGet()
                            ordered[received.value.index] = received
                            while (true) {
                                val lease = ordered.remove(expected) ?: break
                                try {
                                    checkActive()
                                    if (generate(lease.value.data)) {
                                        val writerLease = lease.fork()
                                        writeCount.incrementAndGet()
                                        try { writes.send(writerLease) }
                                        catch (error: Throwable) { writerLease.close(); throw error }
                                    }
                                    generatedCount.incrementAndGet()
                                    generated.send(lease)
                                    expected++
                                } catch (error: Throwable) { lease.close(); throw error }
                            }
                        }
                        check(expected == endExclusive) { "Depth pipeline ended at $expected/$endExclusive" }
                    } finally {
                        ordered.values.forEach { it.close() }
                        writes.close()
                        generated.close()
                    }
                }
                beforeConsume()
                var expected = first
                for (lease in generated) {
                    generatedCount.decrementAndGet()
                    lease.use {
                        checkActive()
                        check(it.value.index == expected) { "Frame order changed" }
                        encode(it.value.data, stats())
                        expected++
                    }
                }
                check(expected == endExclusive) { "Encoding ended at $expected/$endExclusive" }
                producer.join(); closer.join(); generator.join(); writer.join()
            }
        } finally {
            decoded.cancel(); depths.cancel(); generated.cancel(); writes.cancel()
        }
        return FramePipelineResult(completedWrites.get(), writeMs.get())
    }
}
