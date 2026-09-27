package com.local.parallelvrgallerypro

import java.io.Closeable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Native delegates are created, used and destroyed on the same owner thread. */
internal class AffinityWorker(name: String) : Closeable {
    private val sequence = AtomicLong()
    private val executor = ThreadPoolExecutor(
        1, 1, 0, TimeUnit.MILLISECONDS, PriorityBlockingQueue(),
        { work -> Thread(work, name).apply { isDaemon = true } },
    )

    private class Task<T>(val priority: Int, val order: Long, block: () -> T) :
        FutureTask<T>(java.util.concurrent.Callable(block)), Comparable<Task<*>> {
        override fun compareTo(other: Task<*>): Int =
            compareValuesBy(this, other, Task<*>::priority, Task<*>::order)
    }

    fun <T> submit(priority: Int = 1, block: () -> T): FutureTask<T> =
        Task(priority, sequence.getAndIncrement(), block).also(executor::execute)

    override fun close() { executor.shutdown() }
}

// Do not recycle a caller's bitmap while a non-interruptible native invocation still owns it.
internal fun <T> FutureTask<T>.awaitNative(): T {
    var interrupted = false
    try {
        while (true) {
            try { return get() }
            catch (_: InterruptedException) { interrupted = true }
            catch (error: ExecutionException) { throw error.cause ?: error }
        }
    } finally {
        if (interrupted) Thread.currentThread().interrupt()
    }
}

internal class ThreadBoundResource<T : Closeable>(
    private val worker: AffinityWorker,
    create: () -> T,
) : Closeable {
    private val resource = worker.submit(block = create).awaitNative()
    private val gate = Any()
    private var closed = false

    fun <R> use(priority: Int = 1, block: (T) -> R): R {
        val task = synchronized(gate) {
            check(!closed) { "Native resource has been closed" }
            worker.submit(priority) { block(resource) }
        }
        return task.awaitNative()
    }

    override fun close() {
        val task = synchronized(gate) {
            if (closed) return
            closed = true
            worker.submit(Int.MAX_VALUE) { resource.close() }
        }
        task.awaitNative()
    }
}
