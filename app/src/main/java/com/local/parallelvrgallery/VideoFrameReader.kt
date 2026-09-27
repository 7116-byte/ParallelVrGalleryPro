package com.local.parallelvrgallerypro

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import java.io.Closeable
import kotlin.math.abs

/** The first uncached frame is checked against Android's existing decode path. */
internal class VideoFrameReader(
    private val context: Context,
    private val uri: Uri,
    private val timeline: VideoTimeline,
    private val retriever: MediaMetadataRetriever,
    private val checkActive: () -> Unit,
    private val report: (String) -> Unit,
) : Closeable {
    private var sequential: SequentialVideoDecoder? = null
    private var attempted = false
    private var validated = false

    fun frameAt(index: Int): Bitmap {
        checkActive()
        if (!attempted) {
            attempted = true
            try { sequential = SequentialVideoDecoder(context, uri, timeline, checkActive) }
            catch (error: Exception) {
                checkActive()
                report("decoder=Android retriever; sequential unavailable: ${error.message}")
            }
        }
        val decoder = sequential ?: return legacy(index)
        val decoded = try { decoder.frameAt(index) } catch (error: Exception) {
            checkActive()
            close()
            report("decoder=Android retriever; sequential fallback at frame=$index: ${error.message}")
            return legacy(index)
        }
        if (validated) return decoded
        val referenceStarted = SystemClock.elapsedRealtime()
        val reference = try { legacy(index) } catch (error: Throwable) { decoded.recycle(); throw error }
        val referenceMs = SystemClock.elapsedRealtime() - referenceStarted
        if (!samePixels(decoded, reference)) {
            decoded.recycle()
            close()
            report("decoder=Android retriever; sequential color/rotation validation differed")
            return reference
        }
        if (decoder.lastConversionMs > referenceMs * 1.25 + 8) {
            decoded.recycle()
            close()
            report("decoder=Android retriever; sequential RGB conversion=${decoder.lastConversionMs}ms reference=${referenceMs}ms")
            return reference
        }
        reference.recycle()
        validated = true
        report("decoder=sequential MediaCodec; color/rotation validation passed; source presentation timestamps retained")
        return decoded
    }

    private fun legacy(index: Int): Bitmap {
        // The original timestamp is authoritative, including fractional/VFR/high-FPS sources.
        return retriever.getFrameAtTime(timeline[index], MediaMetadataRetriever.OPTION_CLOSEST)
            ?: error("Unable to decode frame $index at ${timeline[index]}us")
    }

    private fun samePixels(a: Bitmap, b: Bitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        var error = 0L
        var samples = 0
        val rowA = IntArray(a.width)
        val rowB = IntArray(b.width)
        for (y in 0 until a.height step maxOf(1, a.height / 64)) {
            a.getPixels(rowA, 0, a.width, 0, y, a.width, 1)
            b.getPixels(rowB, 0, b.width, 0, y, b.width, 1)
            for (x in 0 until a.width step maxOf(1, a.width / 64)) {
                for (shift in intArrayOf(0, 8, 16)) {
                    error += abs(((rowA[x] shr shift) and 255) - ((rowB[x] shr shift) and 255))
                    samples++
                }
            }
        }
        return error.toDouble() / samples <= 1.5
    }

    override fun close() { sequential?.close(); sequential = null }
}
