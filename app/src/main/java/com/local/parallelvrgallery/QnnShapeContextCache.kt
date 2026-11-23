package com.local.parallelvrgallerypro

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** Optional embedded EPContexts. Called by the GPU owner, including eviction and publication. */
internal class QnnShapeContextCache(
    private val directory: File,
    private val compatibility: String,
    private val writeGate: WriteGate,
    private val maxFiles: Int = 6,
    private val maxBytes: Long = 512L * 1024 * 1024,
    private val maxFileBytes: Long = 128L * 1024 * 1024,
) {
    class WriteGate {
        var enabled = true
            private set

        fun disable() { enabled = false }
    }

    enum class Source(val label: String) { FRESH("fresh"), GENERATED("cache-generated"), DISK("disk-cache") }
    data class Opened<T>(val value: T, val source: Source)

    fun pathFor(modelSha256: String, width: Int, height: Int): File {
        val key = "$compatibility\n$modelSha256\n${width}x$height".toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(key).joinToString("") { "%02x".format(it.toInt() and 255) }
        return File(directory, "${hash}_ctx.onnx")
    }

    fun <T> open(
        modelSha256: String,
        width: Int,
        height: Int,
        load: (File) -> T,
        compile: (File?) -> T,
    ): Opened<T> {
        val target = pathFor(modelSha256, width, height)
        if (disk { target.isFile } == true) {
            if (disk { validSize(target) } == true) {
                val loaded = try {
                    Opened(load(target), Source.DISK)
                } catch (failure: Exception) {
                    failure.throwIfCancelled()
                    null
                }
                if (loaded != null) {
                    disk { target.setLastModified(System.currentTimeMillis()); trim(target) }
                    return loaded
                }
            }
            disk { target.delete() }
        }

        if (!writeGate.enabled) return Opened(compile(null), Source.FRESH)
        // A private staging directory keeps the candidate out of the lookup and makes the
        // destination of ORT's possible sidecar files explicit. A genuine embedded result
        // contains a single ONNX file and can be published with one atomic rename.
        val staging = disk {
            if (!directory.isDirectory && !directory.mkdirs()) return@disk null
            removeStaleStages()
            Files.createTempDirectory(directory.toPath(), STAGING_PREFIX).toFile()
        }
        if (staging == null) {
            writeGate.disable()
            return Opened(compile(null), Source.FRESH)
        }
        val candidate = File(staging, target.name)
        try {
            val runtime = try {
                Opened(compile(candidate), Source.GENERATED)
            } catch (failure: Exception) {
                failure.throwIfCancelled()
                // Retry without serialization; even a supported GPU may reject the cache
                // option or its file location. One failure is enough for this process.
                writeGate.disable()
                disk { discardStage(staging) }
                try {
                    return Opened(compile(null), Source.FRESH)
                } catch (fallback: Exception) {
                    fallback.throwIfCancelled()
                    if (fallback !== failure) fallback.addSuppressed(failure)
                    throw fallback
                }
            }
            val published = disk {
                if (!validSize(candidate) || staging.list()?.size != 1) return@disk false
                Files.move(candidate.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                target.setLastModified(System.currentTimeMillis())
                trim(target)
                true
            } == true
            if (published) return runtime
            writeGate.disable()
            return runtime.copy(source = Source.FRESH)
        } finally {
            disk { discardStage(staging) }
        }
    }

    private fun validSize(file: File): Boolean = file.length() in 1..minOf(maxFileBytes, maxBytes)

    private fun trim(recent: File) {
        val files = directory.listFiles { file -> file.isFile && CACHE_NAME.matches(file.name) }.orEmpty()
            .sortedWith(compareByDescending<File> { it == recent }.thenByDescending { it.lastModified() })
        var savedFiles = 0
        var savedBytes = 0L
        for (file in files) {
            if (!validSize(file) || savedFiles >= maxFiles || savedBytes + file.length() > maxBytes) {
                file.delete()
            } else {
                savedFiles++
                savedBytes += file.length()
            }
        }
    }

    private fun removeStaleStages() {
        val cutoff = System.currentTimeMillis() - 60L * 60 * 1000
        directory.listFiles { file -> file.isDirectory && file.name.startsWith(STAGING_PREFIX) && file.lastModified() < cutoff }
            .orEmpty().forEach(::discardStage)
    }

    private fun discardStage(stage: File) {
        stage.listFiles()?.forEach { it.delete() }
        stage.delete()
    }

    private inline fun <T> disk(action: () -> T): T? = try {
        action()
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun Exception.throwIfCancelled() {
        if (this is InterruptedException || this is CancellationException) throw this
    }

    companion object {
        private val CACHE_NAME = Regex("[a-f0-9]{64}_ctx\\.onnx")
        private const val STAGING_PREFIX = "qnn-stage-"

        fun supports(aspect: QnnDepthAspect): Boolean = when (aspect) {
            QnnDepthAspect.ALIGNED, QnnDepthAspect.WIDE_TALL, QnnDepthAspect.PORTRAIT_PAIR -> true
            QnnDepthAspect.SQUARE, QnnDepthAspect.FIT -> false
        }
    }
}
