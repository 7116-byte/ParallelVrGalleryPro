package com.local.parallelvrgallerypro

import java.io.Closeable

/** Each pipeline consumer owns one idempotent lease, not the underlying bitmap. */
internal class SharedLease<T> private constructor(private val owner: Owner<T>) : Closeable {
    private class Owner<T>(val value: T, val dispose: (T) -> Unit) {
        var references = 1
    }
    constructor(value: T, dispose: (T) -> Unit) : this(Owner(value, dispose))
    private var closed = false
    val value: T get() = synchronized(owner) { check(!closed); owner.value }

    fun fork(): SharedLease<T> = synchronized(owner) {
        check(!closed)
        owner.references++
        SharedLease(owner)
    }

    override fun close() {
        val release = synchronized(owner) {
            if (closed) return
            closed = true
            --owner.references == 0
        }
        if (release) owner.dispose(owner.value)
    }
}
