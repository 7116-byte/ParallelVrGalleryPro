package com.local.parallelvrgallerypro

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.READ
import java.security.MessageDigest
import java.util.Locale

/** A single video job owns this store. Only atomically published, verified segments survive a restart. */
internal class VideoSegmentStore(
    private val directory: File,
    private val framesDir: File,
    private val totalFrames: Int,
    private val framesPerSegment: Int,
    private val syncDirectory: (File) -> Boolean = ::syncVideoSegmentDirectory,
) {
    data class Range(val index: Int, val startFrame: Int, val endFrameExclusive: Int)

    data class Checkpoint(val width: Int, val height: Int, val values: FloatArray) {
        init {
            require(checkedDepthCount(width, height) == values.size && values.all(Float::isFinite))
        }
    }

    data class Completed(
        val range: Range,
        val mediaFile: File,
        val width: Int,
        val height: Int,
        val geometry: VideoDepthGeometry?,
        internal val checkpointHash: String?,
    )

    class Stage internal constructor(internal val owner: VideoSegmentStore, val range: Range, internal val directory: File) : Closeable {
        val mediaFile: File get() = File(directory, MEDIA_NAME)
        override fun close() { directory.deleteRecursively() }
    }

    init { require(totalFrames > 0 && framesPerSegment > 0) }

    val ranges: List<Range> = object : AbstractList<Range>() {
        override val size: Int = (totalFrames - 1) / framesPerSegment + 1

        override fun get(index: Int): Range {
            if (index !in indices) throw IndexOutOfBoundsException("Video segment $index of $size")
            val packed = RustCore.segmentRange(totalFrames, framesPerSegment, index)
            check(packed >= 0L) { "Video segment $index out of range" }
            return Range(index, (packed ushr 32).toInt(), packed.toInt())
        }
    }

    private var completedCount = 0
    private var trackSize: Pair<Int, Int>? = null

    /** Call before beginning work so a cancelled or crashed job resumes at the first unfinished range. */
    fun recover(): List<Completed> {
        check(!directory.exists() || directory.isDirectory) { "Invalid video segment storage" }
        val children = if (directory.isDirectory) requireNotNull(directory.listFiles()) else emptyArray()
        val candidates = children.mapNotNull { child ->
            SEGMENT_NAME.matchEntire(child.name)?.groupValues?.get(1)?.toIntOrNull()?.let { it to child }
        }.sortedBy { it.first }
        val recovered = candidates.mapIndexed { index, (recorded, dir) ->
            check(index == recorded && index in ranges.indices && dir == segmentDirectory(index)) {
                "Noncontiguous video segment storage at segment $index"
            }
            readCompleted(ranges[index], dir)
        }
        val newest = recovered.lastOrNull()
        if (newest != null) validateCheckpoint(newest)
        val expectedSize = newest?.let { it.width to it.height }
        check(recovered.all { (it.width to it.height) == expectedSize }) { "Video segment track size changed" }

        // No work in this store is active during recovery. A crash may have left a private stage.
        children.filter { it.isDirectory && STAGING_NAME.matches(it.name) }.forEach { it.deleteRecursively() }
        completedCount = recovered.size
        trackSize = expectedSize
        if (newest != null && recovered.all { syncDirectory(requireNotNull(it.mediaFile.parentFile)) } && syncDirectory(directory)) {
            recovered.forEach { reclaimJpegs(it.range) }
            recovered.dropLast(1).forEach { checkpointFile(requireNotNull(it.mediaFile.parentFile)).delete() }
        }
        return recovered
    }

    fun begin(range: Range): Stage {
        require(range.index in ranges.indices && ranges[range.index] == range) { "Unplanned video segment $range" }
        check(range.index == completedCount && !segmentDirectory(range.index).exists()) { "Video segment already exists or is out of order" }
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create video segment storage" }
        return Stage(this, range, Files.createTempDirectory(directory.toPath(), STAGING_PREFIX).toFile())
    }

    fun publish(
        stage: Stage,
        width: Int,
        height: Int,
        temporal: Checkpoint?,
        geometry: VideoDepthGeometry? = null,
        checkActive: () -> Unit = {},
    ): Completed {
        require(stage.owner === this && width > 0 && height > 0)
        check(stage.range.index == completedCount && stage.directory.isDirectory) { "Inactive video segment stage" }
        val size = width to height
        check(trackSize == null || trackSize == size) { "Video segment track size changed" }
        val media = stage.mediaFile
        check(media.isFile && media.length() > 0) { "Missing encoded video segment" }
        RandomAccessFile(media, "rw").use { it.fd.sync() }
        val hash = sha256(media)
        val checkpointHash = temporal?.let {
            val file = checkpointFile(stage.directory)
            writeCheckpoint(file, it)
            sha256(file)
        }
        val manifest = listOf(
            "format=1", "totalFrames=$totalFrames", "framesPerSegment=$framesPerSegment",
            "index=${stage.range.index}", "start=${stage.range.startFrame}", "end=${stage.range.endFrameExclusive}",
            "width=$width", "height=$height", "mediaBytes=${media.length()}", "mediaSha256=$hash",
            "checkpointSha256=${checkpointHash.orEmpty()}", "depthGeometry=${if (geometry != null) 1 else 0}",
        ) + geometry?.metadataLines().orEmpty()
        FileOutputStream(File(stage.directory, MANIFEST_NAME)).use {
            it.write(manifest.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }

        val stagedDurably = syncDirectory(stage.directory)
        val target = segmentDirectory(stage.range.index)
        check(!target.exists()) { "Video segment already published" }
        checkActive()
        Files.move(stage.directory.toPath(), target.toPath(), ATOMIC_MOVE)
        val publishedDurably = syncDirectory(directory)
        val done = Completed(stage.range, File(target, MEDIA_NAME), width, height, geometry, checkpointHash)
        completedCount++
        trackSize = size
        checkActive()
        if (stagedDurably && publishedDurably) {
            reclaimJpegs(done.range)
            if (done.range.index > 0) checkpointFile(segmentDirectory(done.range.index - 1)).delete()
        }
        return done
    }

    /** Only the newest committed segment is guaranteed to retain its temporal state. */
    fun readCheckpoint(completed: Completed): Checkpoint? {
        require(completed.range.index == completedCount - 1 && completed.mediaFile == File(segmentDirectory(completed.range.index), MEDIA_NAME)) {
            "Only the latest video checkpoint can restore temporal history"
        }
        val file = validateCheckpoint(completed) ?: return null
        val shape = IntArray(2)
        val values = RustCore.readDepthCheckpoint(file.absolutePath, shape)
        return Checkpoint(shape[0], shape[1], values)
    }

    private fun readCompleted(range: Range, dir: File): Completed {
        val manifest = File(dir, MANIFEST_NAME)
        check(dir.isDirectory && manifest.isFile && manifest.length() in 1L..8192L) { "Missing video segment manifest ${range.index}" }
        val rows = manifest.readLines(Charsets.UTF_8)
        val fields = rows.associate { line -> line.substringBefore('=') to line.substringAfter('=', "") }
        check(rows.size == fields.size && fields["format"] == "1" && fields["totalFrames"]?.toIntOrNull() == totalFrames &&
            fields["framesPerSegment"]?.toIntOrNull() == framesPerSegment && fields["index"]?.toIntOrNull() == range.index &&
            fields["start"]?.toIntOrNull() == range.startFrame && fields["end"]?.toIntOrNull() == range.endFrameExclusive) {
            "Video segment frame plan mismatch at ${range.index}"
        }
        val media = File(dir, MEDIA_NAME)
        check(media.isFile && media.length() > 0L && media.length() == fields["mediaBytes"]?.toLongOrNull() && sha256(media) == fields["mediaSha256"]) {
            "Invalid encoded video segment ${range.index}"
        }
        val width = requireNotNull(fields["width"]?.toIntOrNull()?.takeIf { it > 0 })
        val height = requireNotNull(fields["height"]?.toIntOrNull()?.takeIf { it > 0 })
        val geometry = when (fields["depthGeometry"]) {
            "0" -> null
            "1" -> requireNotNull(VideoDepthGeometry.read(fields)) { "Invalid video segment depth geometry" }
            else -> error("Missing video segment depth geometry flag")
        }
        val checkpoint = requireNotNull(fields["checkpointSha256"]).takeIf(String::isNotEmpty)
        check(checkpoint == null || SHA256_NAME.matches(checkpoint)) { "Invalid video segment checkpoint identity" }
        return Completed(range, media, width, height, geometry, checkpoint)
    }

    private fun validateCheckpoint(completed: Completed): File? {
        val hash = completed.checkpointHash ?: return null
        val file = checkpointFile(requireNotNull(completed.mediaFile.parentFile))
        check(file.isFile && file.length() in (DEPTH_HEADER_BYTES + Float.SIZE_BYTES)..MAX_DEPTH_FILE_BYTES && sha256(file) == hash) {
            "Missing or damaged video depth checkpoint for segment ${completed.range.index}"
        }
        return file
    }

    private fun writeCheckpoint(file: File, checkpoint: Checkpoint) {
        RustCore.writeDepthCheckpoint(file.absolutePath, checkpoint.width, checkpoint.height, checkpoint.values)
    }

    private fun reclaimJpegs(range: Range) {
        if (framesDir.list()?.isEmpty() != false) return
        RustCore.reclaimFrameJpegs(framesDir.absolutePath, range.startFrame, range.endFrameExclusive)
    }

    private fun segmentDirectory(index: Int) = File(directory, String.format(Locale.US, "segment_%06d", index))

    private companion object {
        const val MEDIA_NAME = "segment.mp4"
        const val MANIFEST_NAME = "segment.txt"
        const val STAGING_PREFIX = ".stage-"
        const val DEPTH_MAGIC = 0x50564744
        const val DEPTH_HEADER_BYTES = 12L
        const val MAX_DEPTH_PIXELS = 16_777_216
        const val MAX_DEPTH_FILE_BYTES = DEPTH_HEADER_BYTES + MAX_DEPTH_PIXELS.toLong() * Float.SIZE_BYTES
        val SEGMENT_NAME = Regex("segment_([0-9]{6,10})")
        val STAGING_NAME = Regex("[.]stage-[0-9]+")
        val SHA256_NAME = Regex("[0-9a-f]{64}")

        fun checkedDepthCount(width: Int, height: Int): Int {
            val size = width.toLong() * height
            require(width > 0 && height > 0 && size in 1L..MAX_DEPTH_PIXELS.toLong()) { "Invalid video depth checkpoint dimensions" }
            return size.toInt()
        }

        fun checkpointFile(directory: File) = File(directory, "temporal-depth.bin")

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            file.inputStream().use { input ->
                var count = input.read(buffer)
                while (count >= 0) {
                    digest.update(buffer, 0, count)
                    count = input.read(buffer)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }
}

/** Keep reclaim optional on a filesystem without a directory persistence barrier. */
private fun syncVideoSegmentDirectory(directory: File): Boolean = try {
    FileChannel.open(directory.toPath(), READ).use { it.force(true) }
    true
} catch (_: IOException) {
    false
} catch (_: SecurityException) {
    false
} catch (_: UnsupportedOperationException) {
    false
}
