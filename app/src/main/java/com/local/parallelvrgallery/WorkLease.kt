package com.local.parallelvrgallerypro

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** A service spans all consumers; stale releases cannot stop a newer generation of work. */
internal class WorkLease(private val start: () -> Unit, private val stop: () -> Unit) {
    private var users = 0
    private var epoch = 0L
    @Synchronized fun acquire(): Closeable {
        if (users == 0) start() // Failure is visible to the caller and never increments the count.
        users++
        val captured = epoch
        val closed = AtomicBoolean()
        return Closeable {
            if (closed.compareAndSet(false, true)) synchronized(this) {
                if (captured == epoch && users > 0 && --users == 0) stop()
            }
        }
    }
    @Synchronized fun stopAll() { epoch++; if (users > 0) { users = 0; stop() } }
    @Synchronized fun active(): Boolean = users > 0
}
