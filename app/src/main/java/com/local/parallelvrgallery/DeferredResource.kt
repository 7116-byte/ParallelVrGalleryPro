package com.local.parallelvrgallerypro

import java.io.Closeable

/** Cache-only work never creates an inference session or downloads its model. */
internal class DeferredResource<T:Closeable>(private val create:()->T):Closeable {
    private var value:T?=null
    private var closed=false
    @Synchronized fun get():T {
        check(!closed)
        return value ?: create().also {value=it}
    }
    @Synchronized override fun close() {
        if(closed)return
        closed=true
        val owned=value;value=null;owned?.close()
    }
}
