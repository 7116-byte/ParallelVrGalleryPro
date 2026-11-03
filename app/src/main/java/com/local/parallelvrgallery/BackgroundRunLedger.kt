package com.local.parallelvrgallerypro

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Explicitly admitted video keys only. No scanning or revival of unrelated historical jobs. */
internal class BackgroundRunLedger(private val file: File) {
    private val keys = linkedSetOf<String>()
    private var restarts = 0
    private var windowStart = 0L
    init {
        if (file.isFile) runCatching {
            val lines = file.readLines(Charsets.UTF_8)
            require(lines.firstOrNull() == "PVG_RUN_1")
            restarts = lines[1].toInt().coerceAtLeast(0); windowStart = lines[2].toLong()
            keys.addAll(lines.drop(3).filter(::validKey))
        }.onFailure { keys.clear(); restarts = 0; windowStart = 0 }
    }
    @Synchronized fun authorize(selected: Set<String>) {
        require(selected.all(::validKey))
        keys.addAll(selected); restarts = 0; windowStart = 0; persist()
    }
    @Synchronized fun revoke(selected: Set<String>) { if (keys.removeAll(selected)) persist() }
    @Synchronized fun clear() { keys.clear(); restarts = 0; windowStart = 0; persist() }
    @Synchronized fun claimSystemRestart(now: Long): Set<String> {
        if (keys.isEmpty()) return emptySet()
        if (windowStart <= 0 || now - windowStart > 15 * 60 * 1000 || now < windowStart) { restarts = 0; windowStart = now }
        if (restarts >= 3) return emptySet() // Avoid a repeated native/OOM crash loop.
        restarts++; persist(); return keys.toSet()
    }
    @Synchronized fun snapshot(): Set<String> = keys.toSet()
    private fun persist() {
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val temp = File(file.parentFile, file.name + ".tmp")
        val text = (listOf("PVG_RUN_1", restarts.toString(), windowStart.toString()) + keys).joinToString("\n")
        FileOutputStream(temp).use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
    private fun validKey(value: String) = value.startsWith("VIDEO_") && value.matches(Regex("[A-Za-z0-9_.-]{1,180}"))
}
