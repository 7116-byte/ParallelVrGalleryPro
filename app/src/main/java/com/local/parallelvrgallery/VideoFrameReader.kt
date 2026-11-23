package com.local.parallelvrgallerypro

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import java.io.Closeable
import kotlin.math.abs

enum class VideoDecodeBackend { MEDIACODEC, ANDROID_RETRIEVER }

data class VideoFrameReadTiming(
    val backend: VideoDecodeBackend,
    val decoderMs: Long,
    val conversionMs: Long = 0,
    val probeMs: Long = 0,
) {
    val decodeMs: Long get() = decoderMs + conversionMs
}

internal data class VideoFrameRead(val bitmap: Bitmap, val timing: VideoFrameReadTiming)

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

    fun frameAt(index: Int): VideoFrameRead {
        checkActive()
        val started = SystemClock.uptimeMillis()
        if (!attempted) {
            attempted = true
            try { sequential = SequentialVideoDecoder(context, uri, timeline, checkActive) }
            catch (error: Exception) {
                checkActive()
                report("decoder=Android retriever; sequential unavailable: ${error.message}")
            }
        }
        val decoder = sequential ?: return compatibilityFrame(index, SystemClock.uptimeMillis() - started)
        val decoderStarted = SystemClock.uptimeMillis()
        val decoded = try { decoder.frameAt(index) } catch (error: Exception) {
            checkActive()
            close()
            report("decoder=Android retriever; sequential fallback at frame=$index: ${error.message}")
            return compatibilityFrame(index, SystemClock.uptimeMillis() - started)
        }
        val sequentialMs = SystemClock.uptimeMillis() - decoderStarted
        val conversionMs = decoder.lastConversionMs.coerceAtMost(sequentialMs)
        fun sequentialFrame() = VideoFrameRead(
            decoded,
            VideoFrameReadTiming(
                VideoDecodeBackend.MEDIACODEC,
                decoderMs = sequentialMs - conversionMs,
                conversionMs = conversionMs,
                probeMs = (SystemClock.uptimeMillis() - started - sequentialMs).coerceAtLeast(0L),
            ),
        )
        if (validated) {
            return sequentialFrame()
        }
        val referenceStarted = SystemClock.uptimeMillis()
        val reference = try { legacy(index) } catch (error: Throwable) { decoded.recycle(); throw error }
        val referenceMs = SystemClock.uptimeMillis() - referenceStarted
        val fallback = try {
            when {
                !samePixels(decoded, reference) -> "sequential color/rotation validation differed"
                conversionMs > referenceMs * 1.25 + 8 -> "sequential RGB conversion=${conversionMs}ms reference=${referenceMs}ms"
                else -> null
            }
        } catch (error: Throwable) {
            decoded.recycle()
            reference.recycle()
            throw error
        }
        if (fallback != null) {
            decoded.recycle()
            try {
                close()
                report("decoder=Android retriever; $fallback")
                return VideoFrameRead(reference, VideoFrameReadTiming(
                    VideoDecodeBackend.ANDROID_RETRIEVER, referenceMs,
                    probeMs = (SystemClock.uptimeMillis() - started - referenceMs).coerceAtLeast(0L),
                ))
            } catch (error: Throwable) {
                reference.recycle()
                throw error
            }
        }
        reference.recycle()
        validated = true
        try {
            report("decoder=sequential MediaCodec; color/rotation validation passed; source presentation timestamps retained")
        } catch (error: Throwable) {
            decoded.recycle()
            throw error
        }
        return sequentialFrame()
    }

    private fun compatibilityFrame(index: Int, probeMs: Long): VideoFrameRead {
        val started = SystemClock.uptimeMillis()
        val bitmap = legacy(index)
        return VideoFrameRead(bitmap, VideoFrameReadTiming(
            VideoDecodeBackend.ANDROID_RETRIEVER, SystemClock.uptimeMillis() - started, probeMs = probeMs,
        ))
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
                for (shift in 0..16 step 8) {
                    error += abs(((rowA[x] shr shift) and 255) - ((rowB[x] shr shift) and 255))
                    samples++
                }
            }
        }
        return error.toDouble() / samples <= 1.5
    }

    override fun close() { sequential?.close(); sequential = null }
}
