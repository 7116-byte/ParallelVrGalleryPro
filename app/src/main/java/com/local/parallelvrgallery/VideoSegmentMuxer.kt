package com.local.parallelvrgallerypro

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

internal data class VideoSegment(val file: File, val firstFrame: Int, val frameCount: Int)

/** Joins compatible AVC access units and the source audio without decoding or re-encoding. */
internal object VideoSegmentMuxer {
    fun mux(
        context: Context,
        sourceUri: Uri,
        segments: List<VideoSegment>,
        timeline: VideoTimeline,
        output: File,
        checkActive: () -> Unit,
    ) {
        checkVideoSegmentCoverage(segments, timeline.size)
        checkActive()
        val videoFormat = readVideoTrack(segments.first().file) { _, format -> format }
        val audio = MediaExtractor()
        try {
            audio.setDataSource(context, sourceUri, null)
            val audioIndex = (0 until audio.trackCount).firstOrNull { index ->
                audio.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            }
            check(output.createNewFile()) { "Video remux output already exists: ${output.name}" }
            var finished = false
            try {
                val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                try {
                    val videoTrack = muxer.addTrack(videoFormat)
                    val audioTrack = audioIndex?.let { index ->
                        audio.selectTrack(index)
                        muxer.addTrack(audio.getTrackFormat(index))
                    }
                    muxer.start()
                    val writer = SampleWriter(muxer)
                    for (segment in segments) {
                        checkActive()
                        readVideoTrack(segment.file) { reader, format ->
                            checkSameAvcFormat(videoFormat, format, segment.file.name)
                            copyVideoSegment(reader, writer, videoTrack, segment, timeline, checkActive)
                        }
                    }
                    if (audioTrack != null) {
                        while (audio.sampleTrackIndex >= 0) {
                            checkActive()
                            // AAC preroll packets legitimately carry negative source PTS.
                            writer.write(audio, audioTrack, audio.sampleTime, allowNegativePts = true)
                            audio.advance()
                        }
                    }
                    checkActive()
                    muxer.stop()
                } finally {
                    muxer.release()
                }
                finished = true
            } finally {
                if (!finished) output.delete()
            }
        } finally {
            audio.release()
        }
    }

    private fun <T> readVideoTrack(file: File, read: (MediaExtractor, MediaFormat) -> T): T {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            check(extractor.trackCount == 1) { "Expected one video-only track in ${file.name}" }
            val format = extractor.getTrackFormat(0)
            check(format.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_VIDEO_AVC) {
                "Expected AVC video in ${file.name}"
            }
            check(format.getInteger(MediaFormat.KEY_WIDTH) > 0 && format.getInteger(MediaFormat.KEY_HEIGHT) > 0) {
                "Invalid video dimensions in ${file.name}"
            }
            check(listOf("csd-0", "csd-1").all { format.getByteBuffer(it)?.hasRemaining() == true }) {
                "Missing AVC parameter sets in ${file.name}"
            }
            extractor.selectTrack(0)
            return read(extractor, format)
        } finally {
            extractor.release()
        }
    }

    private fun checkSameAvcFormat(reference: MediaFormat, candidate: MediaFormat, fileName: String) {
        val integerKeys = arrayOf(
            MediaFormat.KEY_WIDTH, MediaFormat.KEY_HEIGHT, MediaFormat.KEY_PROFILE, MediaFormat.KEY_LEVEL,
            MediaFormat.KEY_COLOR_STANDARD, MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.KEY_COLOR_RANGE,
            MediaFormat.KEY_ROTATION, "crop-left", "crop-top", "crop-right", "crop-bottom", "sar-width", "sar-height",
        )
        for (key in integerKeys) {
            check(reference.getInteger(key, -1) == candidate.getInteger(key, -1)) {
                "AVC track metadata differs for $key in $fileName"
            }
        }
        val csdKeys = (reference.keys + candidate.keys).filter { it.startsWith("csd-") }
        for (key in csdKeys) {
            check(reference.getByteBuffer(key)?.duplicate() == candidate.getByteBuffer(key)?.duplicate()) {
                "AVC codec configuration differs for $key in $fileName"
            }
        }
    }

    private fun copyVideoSegment(
        reader: MediaExtractor,
        writer: SampleWriter,
        track: Int,
        segment: VideoSegment,
        timeline: VideoTimeline,
        checkActive: () -> Unit,
    ) {
        val timestamp = VideoSegmentTimestamps(segment, timeline)
        while (reader.sampleTrackIndex >= 0) {
            checkActive()
            val flags = reader.sampleFlags
            check(flags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME == 0) {
                "Fragmented AVC access unit in ${segment.file.name}"
            }
            val pts = timestamp.next(reader.sampleTime, flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
            writer.write(reader, track, pts)
            reader.advance()
        }
        timestamp.checkComplete()
    }

    private class SampleWriter(private val muxer: MediaMuxer) {
        private var buffer = ByteBuffer.allocateDirect(512 * 1024)
        private val info = MediaCodec.BufferInfo()

        fun write(reader: MediaExtractor, track: Int, pts: Long, allowNegativePts: Boolean = false) {
            require(allowNegativePts || pts >= 0L) { "Invalid sample timestamp" }
            val flags = reader.sampleFlags
            check(flags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "Encrypted media cannot be remuxed" }
            val size = reader.sampleSize
            check(size in 1..MAX_SAMPLE_BYTES.toLong()) { "Invalid or oversized media sample: $size bytes" }
            if (size > buffer.capacity()) {
                val capacity = (buffer.capacity() * 2L).coerceIn(size, MAX_SAMPLE_BYTES.toLong()).toInt()
                buffer = ByteBuffer.allocateDirect(capacity)
            }
            buffer.clear()
            val read = reader.readSampleData(buffer, 0)
            check(read == size.toInt()) { "Incomplete media sample: read $read of $size bytes" }
            buffer.position(0)
            buffer.limit(read)
            var codecFlags = 0
            if (flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) codecFlags = MediaCodec.BUFFER_FLAG_KEY_FRAME
            if (flags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
                codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
            }
            info.set(0, read, pts, codecFlags)
            muxer.writeSampleData(track, buffer, info)
        }
    }

    private const val MAX_SAMPLE_BYTES = 32 * 1024 * 1024
}

internal fun checkVideoSegmentCoverage(segments: List<VideoSegment>, frameCount: Int) {
    val packed = IntArray(segments.size * 2)
    segments.forEachIndexed { index, segment ->
        packed[index * 2] = segment.firstFrame
        packed[index * 2 + 1] = segment.frameCount
    }
    RustCore.checkSegmentCoverage(packed, frameCount)
}

internal class VideoSegmentTimestamps(private val segment: VideoSegment, private val timeline: VideoTimeline) {
    private var count = 0
    private var firstLocalPts = -1L
    private var lastLocalPts = -1L

    fun next(localPts: Long, sync: Boolean): Long {
        check(count < segment.frameCount) { "Too many frames in ${segment.file.name}" }
        val globalPts = timeline[segment.firstFrame + count]
        val originalOffset = globalPts - timeline[segment.firstFrame]
        val first = if (count == 0) localPts else firstLocalPts
        when (RustCore.validateSegmentTimestamp(localPts, lastLocalPts, first, originalOffset, count == 0, sync)) {
            0 -> Unit
            1 -> error("Nonmonotonic AVC timestamps in ${segment.file.name}")
            2 -> error("Video segment ${segment.file.name} does not start with a sync sample")
            else -> error("Unexpected AVC sample timestamp in ${segment.file.name} at frame ${segment.firstFrame + count}")
        }
        firstLocalPts = first
        lastLocalPts = localPts
        count++
        return globalPts
    }

    fun checkComplete() {
        check(count == segment.frameCount) {
            "Video segment ${segment.file.name} has $count of ${segment.frameCount} expected frames"
        }
    }
}
