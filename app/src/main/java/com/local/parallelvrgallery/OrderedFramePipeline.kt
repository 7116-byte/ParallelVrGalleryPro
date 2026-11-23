package com.local.parallelvrgallerypro

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore

internal data class FramePipelineStats(
    val decoded: Int, val depths: Int, val generated: Int, val writes: Int, val lastWriteMs: Long,
    val inputPrepared: Int = 0, val depthRestored: Int = 0,
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
        prepareInput: ((T) -> Unit)? = null,
        restoreDepth: ((T) -> Unit)? = null,
    ): FramePipelineResult {
        data class Frame<T>(val index: Int, val data: T)
        val permits = Semaphore(maxInFlight)
        fun frameChannel(capacity: Int) = Channel<SharedLease<Frame<T>>>(capacity, onUndeliveredElement = { it.close() })
        val decoded = frameChannel(2)
        val prepared = prepareInput?.let { frameChannel(4) }
        val depths = frameChannel(2)
        val restored = restoreDepth?.let { frameChannel(4) }
        val generated = frameChannel(2)
        val writes = frameChannel(2)
        val decodeCount = AtomicInteger()
        val preparedCount = AtomicInteger()
        val depthCount = AtomicInteger()
        val restoredCount = AtomicInteger()
        val generatedCount = AtomicInteger()
        val writeCount = AtomicInteger()
        val completedWrites = AtomicInteger()
        val lastWriteMs = AtomicLong()
        val writeMs = AtomicLong()
        val depthInput = prepared ?: decoded
        val depthInputCount = if (prepared == null) decodeCount else preparedCount
        val orderedInput = restored ?: depths
        val orderedInputCount = if (restored == null) depthCount else restoredCount
        fun stats() = FramePipelineStats(
            decodeCount.get().coerceAtLeast(0), depthCount.get().coerceAtLeast(0),
            generatedCount.get().coerceAtLeast(0), writeCount.get().coerceAtLeast(0), lastWriteMs.get(),
            inputPrepared = preparedCount.get().coerceAtLeast(0), depthRestored = restoredCount.get().coerceAtLeast(0),
        )
        try {
            runBlocking {
                val pipelineScope = this
                fun launchStage(dispatcher: CoroutineDispatcher, work: suspend () -> Unit) = launch(dispatcher) {
                    try {
                        work()
                    } catch (error: CancellationException) {
                        // A worker callback can cancel itself. Cancel its peers so nobody waits on an abandoned queue.
                        pipelineScope.cancel(error)
                        throw error
                    }
                }
                fun transformFrames(
                    input: Channel<SharedLease<Frame<T>>>,
                    output: Channel<SharedLease<Frame<T>>>,
                    inputCount: AtomicInteger,
                    outputCount: AtomicInteger,
                    transform: (T) -> Unit,
                ) = launchStage(Dispatchers.Default) {
                    for (lease in input) {
                        inputCount.decrementAndGet()
                        try {
                            checkActive()
                            transform(lease.value.data)
                            outputCount.incrementAndGet()
                            output.send(lease)
                        } catch (error: Throwable) { lease.close(); throw error }
                    }
                    output.close()
                }
                val writer = launchStage(Dispatchers.IO) {
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
                val producer = launchStage(Dispatchers.IO) {
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
                    decoded.close()
                }
                val inputPreparation = prepared?.let {
                    transformFrames(decoded, it, decodeCount, preparedCount, checkNotNull(prepareInput))
                }
                val depthJobs = (0 until workers).map { worker ->
                    launchStage(Dispatchers.Default) {
                        for (lease in depthInput) {
                            depthInputCount.decrementAndGet()
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
                val depthRestoration = restored?.let {
                    transformFrames(depths, it, depthCount, restoredCount, checkNotNull(restoreDepth))
                }
                val generator = launchStage(Dispatchers.Default) {
                    val ordered = sortedMapOf<Int, SharedLease<Frame<T>>>()
                    var expected = first
                    try {
                        for (received in orderedInput) {
                            orderedInputCount.decrementAndGet()
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
                    }
                    writes.close()
                    generated.close()
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
                producer.join()
                inputPreparation?.join()
                closer.join()
                depthRestoration?.join()
                generator.join()
                writer.join()
            }
        } finally {
            listOfNotNull(decoded, prepared, depths, restored, generated, writes).forEach { it.cancel() }
        }
        return FramePipelineResult(completedWrites.get(), writeMs.get())
    }
}
