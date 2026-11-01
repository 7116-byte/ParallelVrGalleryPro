package com.local.parallelvrgallerypro

import java.io.Closeable
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class DeferredResourceTest {
    @Test fun cacheOnlyPathCreatesNoModelOrNativeResource() {
        DeferredResource<Closeable>{error("Cache hit must not open model")}.use{}
    }
    @Test fun concurrentMissesCreateExactlyOneOwnedResourceAndCloseOnce() {
        var creates=0;var closes=0
        val resource=DeferredResource {creates++;Closeable{closes++}}
        val executor=Executors.newFixedThreadPool(3)
        try {val tasks=(0..8).map{executor.submit<Closeable>{resource.get()}}
            val first=tasks.first().get();tasks.forEach{assertSame(first,it.get())}
            assertEquals(1,creates)
        }finally{executor.shutdownNow();resource.close();resource.close()}
        assertEquals(1,closes);assertThrows(IllegalStateException::class.java){resource.get()}
    }
    @Test fun failedCreationPropagatesWithoutClosingAnUnownedResource() {
        val resource=DeferredResource<Closeable>{throw java.io.IOException("GPU initialization failed")}
        assertThrows(java.io.IOException::class.java){resource.get()};resource.close()
    }
}
