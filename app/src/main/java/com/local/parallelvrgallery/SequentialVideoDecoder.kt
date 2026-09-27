package com.local.parallelvrgallerypro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import java.io.Closeable

internal data class VideoTrackInfo(val timeline: VideoTimeline, val format: MediaFormat) {
    companion object {
        fun read(context: Context, uri: Uri): VideoTrackInfo {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                val track = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/")
                } ?: error("No video track")
                val format = extractor.getTrackFormat(track)
                extractor.selectTrack(track)
                val samples = ArrayList<Long>()
                while (extractor.sampleTime >= 0) {
                    samples += extractor.sampleTime
                    if (!extractor.advance()) break
                }
                return VideoTrackInfo(VideoTimeline(samples.toLongArray()), format)
            } finally { extractor.release() }
        }
    }
}

/** Sequential decode; cached frames are drained without allocating RGB bitmaps. */
internal class SequentialVideoDecoder(
    context: Context,
    uri: Uri,
    private val timeline: VideoTimeline,
    private val checkActive: () -> Unit,
) : Closeable {
    private val extractor = MediaExtractor()
    private var codec: MediaCodec? = null
    private var inputEnded = false
    private var outputEnded = false
    private var lastRequested = -1
    private var rotation = 0
    var lastConversionMs: Long = 0L
        private set
    private var inputFormat: MediaFormat

    init {
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/") }
            extractor.selectTrack(track)
            inputFormat = extractor.getTrackFormat(track)
            val transfer = inputFormat.intOr(MediaFormat.KEY_COLOR_TRANSFER, 3)
            require(transfer != MediaFormat.COLOR_TRANSFER_ST2084 && transfer != MediaFormat.COLOR_TRANSFER_HLG) {
                "HDR requires a color-managed decoder; refusing an implicit SDR conversion"
            }
            rotation = inputFormat.intOr(MediaFormat.KEY_ROTATION, 0)
            inputFormat.setInteger(MediaFormat.KEY_ROTATION, 0)
            inputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            codec = MediaCodec.createDecoderByType(requireNotNull(inputFormat.getString(MediaFormat.KEY_MIME))).also {
                it.configure(inputFormat, null, null, 0)
                it.start()
            }
        } catch (error: Throwable) { close(); throw error }
    }

    fun frameAt(index: Int): Bitmap {
        require(index > lastRequested) { "Sequential decoder cannot seek backwards" }
        lastRequested = index
        val target = timeline[index]
        val decoder = checkNotNull(codec)
        val info = MediaCodec.BufferInfo()
        var lastProgress = SystemClock.elapsedRealtime()
        while (!outputEnded) {
            checkActive()
            check(SystemClock.elapsedRealtime() - lastProgress < 15_000) { "Video decoder stalled at frame $index" }
            if (!inputEnded) {
                val inputIndex = decoder.dequeueInputBuffer(0)
                if (inputIndex >= 0) {
                    val buffer = checkNotNull(decoder.getInputBuffer(inputIndex)).apply { clear() }
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputEnded = true
                    } else {
                        val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) MediaCodec.BUFFER_FLAG_PARTIAL_FRAME else 0
                        decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, flags)
                        extractor.advance()
                    }
                    lastProgress = SystemClock.elapsedRealtime()
                }
            }
            val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)
            if (outputIndex >= 0) {
                try {
                    outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        if (info.presentationTimeUs > target) error("Decoder skipped requested timestamp $target (got ${info.presentationTimeUs})")
                        if (info.presentationTimeUs == target) return bitmapFromOutput(decoder, outputIndex)
                    }
                } finally { decoder.releaseOutputBuffer(outputIndex, false) }
                lastProgress = SystemClock.elapsedRealtime()
            }
        }
        error("Decoder ended before frame $index")
    }

    private fun bitmapFromOutput(decoder: MediaCodec, index: Int): Bitmap {
        val started = SystemClock.elapsedRealtime()
        val image = decoder.getOutputImage(index) ?: error("Decoder does not expose YUV output images")
        val bitmap = image.use {
            require(image.format == ImageFormat.YUV_420_888) { "Unsupported decoder image format ${image.format}" }
            val crop = image.cropRect
            val planes = image.planes
            val format = decoder.outputFormat
            val standard = format.intOr(MediaFormat.KEY_COLOR_STANDARD, inputFormat.intOr(MediaFormat.KEY_COLOR_STANDARD, if (crop.height() >= 720) 1 else 4))
            val fullRange = format.intOr(MediaFormat.KEY_COLOR_RANGE, inputFormat.intOr(MediaFormat.KEY_COLOR_RANGE, 2)) == MediaFormat.COLOR_RANGE_FULL
            val conversion = YuvRgb(standard, fullRange)
            val pixels = IntArray(crop.width() * crop.height())
            val buffers = planes.map { it.buffer.duplicate() }
            val starts = buffers.map { it.position() }
            for (y in 0 until crop.height()) {
                if (y % 64 == 0) checkActive()
                val sy = y + crop.top
                for (x in 0 until crop.width()) {
                    val sx = x + crop.left
                    val yy = buffers[0].get(starts[0] + sy * planes[0].rowStride + sx * planes[0].pixelStride).toInt() and 255
                    val u = buffers[1].get(starts[1] + (sy / 2) * planes[1].rowStride + (sx / 2) * planes[1].pixelStride).toInt() and 255
                    val v = buffers[2].get(starts[2] + (sy / 2) * planes[2].rowStride + (sx / 2) * planes[2].pixelStride).toInt() and 255
                    pixels[y * crop.width() + x] = conversion.pixel(yy, u, v)
                }
            }
            Bitmap.createBitmap(pixels, crop.width(), crop.height(), Bitmap.Config.ARGB_8888)
        }
        if (rotation == 0) return bitmap.also { lastConversionMs = SystemClock.elapsedRealtime() - started }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        } finally { bitmap.recycle(); lastConversionMs = SystemClock.elapsedRealtime() - started }
    }

    override fun close() {
        codec?.let { runCatching { it.stop() }; it.release() }
        codec = null
        extractor.release()
    }
}

private fun MediaFormat.intOr(key: String, fallback: Int): Int = if (containsKey(key)) getInteger(key) else fallback
