package com.local.parallelvrgallerypro

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.sqrt

internal class ViewerImageLoader(context: Context, val previewSide: Int) : Closeable {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permits = Semaphore(2)
    private val cache = WeightedLru<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).coerceIn(24L shl 20, 96L shl 20)) { it.allocationByteCount.toLong() }
    val detailSide = sqrt(cache.budget / 8.0).toInt().coerceIn(previewSide, 4096)
    private val inFlight = mutableMapOf<String, Deferred<Bitmap?>>()

    private fun key(uri: Uri, side: Int): String {
        val revision = if (uri.scheme == "file") uri.path?.let { File(it).lastModified() } ?: 0L else 0L
        return "$uri@$side#$revision"
    }
    fun peek(uri: Uri, side: Int): Bitmap? = cache[key(uri, side)]

    suspend fun load(uri: Uri, side: Int): Bitmap? {
        val key = key(uri, side)
        peek(uri, side)?.let { return it }
        val request = synchronized(inFlight) {
            inFlight.getOrPut(key) {
                scope.async(start = CoroutineStart.LAZY) {
                    permits.withPermit {
                        cache[key] ?: try {
                            decodeScaledBitmap(context, uri, side).also { cache.put(key, it) }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { null }
                    }
                }
            }
        }
        request.invokeOnCompletion { synchronized(inFlight) { if (inFlight[key] === request) inFlight.remove(key) } }
        return request.await()
    }

    override fun close() {
        scope.cancel()
        cache.clear()
        // Bitmaps still displayed by Compose retain their references; never recycle on LRU eviction.
    }
}
