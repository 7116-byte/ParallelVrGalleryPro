package com.local.parallelvrgallerypro

import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** One in-memory snapshot, coalesced writes, and an atomic replacement on disk. */
internal class BufferedTextFile(private val file: File, private val delayMs: Long = 150) : Closeable {
    private val writer = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "queue-index-writer").apply { isDaemon = true }
    }
    private var value: String? = null
    private var scheduled: ScheduledFuture<*>? = null
    private var revision = 0L
    private var savedRevision = 0L
    @Volatile var lastError: Throwable? = null
        private set

    @Synchronized fun read(): String = value ?: (if (file.exists()) file.readText() else "").also { value = it }
    @Synchronized fun write(text: String) {
        value = text
        revision++
        // Keep the first deadline: continuous queue changes must not postpone persistence forever.
        if (scheduled == null) scheduled = writer.schedule(::persist, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun persist() {
        val snapshot = synchronized(this) { scheduled = null; (value ?: "") to revision }
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.outputStream().use { output -> output.write(snapshot.first.toByteArray(Charsets.UTF_8)); output.fd.sync() }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            synchronized(this) { savedRevision = snapshot.second; lastError = null }
        } catch (error: Exception) {
            lastError = error
        } finally {
            synchronized(this) {
                if (savedRevision != revision && scheduled == null && !writer.isShutdown) {
                    scheduled = writer.schedule(::persist, maxOf(1000, delayMs), TimeUnit.MILLISECONDS)
                }
            }
        }
    }

    fun flush() {
        synchronized(this) {
            if (value == null || savedRevision == revision) return
            scheduled?.cancel(false)
            scheduled = null
        }
        writer.submit { persist() }.get()
        lastError?.let { throw it }
    }
    override fun close() { try { flush() } finally { writer.shutdown() } }
}
