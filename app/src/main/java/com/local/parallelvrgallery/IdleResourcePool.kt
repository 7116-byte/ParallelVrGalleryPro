package com.local.parallelvrgallerypro

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

internal class IdleResourcePool<K, T : Closeable>(private val maxIdle: Int = 1) : Closeable {
    private class Entry<T>(val value: T, var users: Int)
    class Borrowed<T>(val value: T, private val release: () -> Unit) : Closeable {
        private val closed = AtomicBoolean()
        override fun close() { if (closed.compareAndSet(false, true)) release() }
    }
    private val entries = LinkedHashMap<K, Entry<T>>(16, 0.75f, true)
    private var closed = false

    fun acquire(key: K, create: () -> T): Borrowed<T> {
        val entry = synchronized(this) {
            check(!closed)
            entries.getOrPut(key) { Entry(create(), 0) }.also { it.users++ }
        }
        return Borrowed(entry.value) {
            val dispose = synchronized(this) {
                entry.users--
                val idle = entries.filterValues { it.users == 0 }.keys.toList()
                idle.take((idle.size - if (closed) 0 else maxIdle).coerceAtLeast(0))
                    .mapNotNull { entries.remove(it)?.value }
            }
            dispose.forEach { it.close() }
        }
    }

    override fun close() {
        val dispose = synchronized(this) {
            closed = true
            entries.filterValues { it.users == 0 }.keys.toList().mapNotNull { entries.remove(it)?.value }
        }
        dispose.forEach { it.close() }
    }
}
