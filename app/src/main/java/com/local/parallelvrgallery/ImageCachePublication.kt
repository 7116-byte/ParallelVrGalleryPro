package com.local.parallelvrgallerypro

import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import java.util.UUID

internal object ImageCachePublication {
    private const val MARKER = ".pvg-publication"
    data class Prepared(val id: String, val work: File, val target: File,
        val oldToBackup: List<Pair<File, File>>, val journal: File) {
        val done: File get() = File(journal.parentFile, "transaction-$id.done")
    }

    internal fun prepare(work: File, target: File, retire: List<File>): Prepared {
        require(work.isDirectory) { "Missing image staging directory" }
        val parent = requireNotNull(target.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Cannot create image cache directory" }
        val id = UUID.randomUUID().toString()
        val moves = (listOf(target) + retire).distinctBy { it.canonicalPath }.filter { it.exists() }.mapIndexed { i, old ->
            require(old.canonicalFile == target.canonicalFile || !target.canonicalFile.toPath().startsWith(old.canonicalFile.toPath()))
            old to File(work.parentFile, "backup-$id-$i")
        }
        FileOutputStream(File(work, MARKER)).use { it.write(id.toByteArray(Charsets.UTF_8)); it.fd.sync() }
        val props = Properties().apply {
            setProperty("id", id); setProperty("work", work.canonicalPath); setProperty("target", target.canonicalPath)
            setProperty("count", moves.size.toString())
            moves.forEachIndexed { i, (old, backup) -> setProperty("old.$i", old.canonicalPath); setProperty("backup.$i", backup.canonicalPath) }
        }
        val journal = File(work.parentFile, "transaction-$id.properties")
        val temp = File(work.parentFile, "transaction-$id.tmp")
        FileOutputStream(temp).use { props.store(it, "Image replacement recovery"); it.fd.sync() }
        check(temp.renameTo(journal)) { "Cannot persist image replacement journal" }
        return Prepared(id, work, target, moves, journal)
    }

    @Synchronized
    fun commit(work: File, target: File, retire: List<File> = emptyList(),
        move: (File, File) -> Boolean = { from, to -> from.renameTo(to) }) {
        val transaction = prepare(work, target, retire)
        val preserved = mutableListOf<Pair<File, File>>()
        try {
            transaction.oldToBackup.forEach { (old, backup) ->
                check(move(old, backup)) { "Cannot preserve previous image cache" }
                preserved += old to backup
            }
            check(move(work, target)) { "Cannot commit generated image" }
        } catch (error: Throwable) {
            var restored = true
            preserved.asReversed().forEach { (old, backup) ->
                if (!move(backup, old)) {
                    restored = false
                    error.addSuppressed(IllegalStateException("Recovery journal retained: ${transaction.journal}"))
                }
            }
            if (restored) transaction.journal.delete()
            throw error
        }
        // The marker in the new directory covers a kill before this completion sidecar.
        runCatching { FileOutputStream(transaction.done).use { it.write(transaction.id.toByteArray(Charsets.UTF_8)); it.fd.sync() } }
        cleanCommitted(transaction)
    }

    private fun cleanCommitted(transaction: Prepared) {
        transaction.oldToBackup.forEach { (_, backup) -> if (backup.exists()) backup.deleteRecursively() }
        if (transaction.oldToBackup.none { it.second.exists() } && transaction.journal.delete()) transaction.done.delete()
    }

    /** Before cache scan/workers; journal paths are restricted to application-owned roots. */
    @Synchronized
    fun recover(workRoot: File, cacheRoot: File): List<String> {
        val warnings = mutableListOf<String>()
        fun inside(file: File, root: File): Boolean = file.canonicalFile != root.canonicalFile &&
            file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())
        workRoot.listFiles()?.filter { it.name.startsWith("transaction-") && it.extension == "properties" }?.forEach { journal ->
            runCatching {
                require(journal.length() < 2L * 1024 * 1024)
                val p = Properties().apply { journal.inputStream().use { load(it) } }
                val id = p.getProperty("id").also { require(it.matches(Regex("[a-f0-9-]{36}"))) }
                val work = File(p.getProperty("work")); val target = File(p.getProperty("target"))
                require(inside(work, workRoot) && inside(target, cacheRoot))
                val count = p.getProperty("count").toInt().also { require(it in 0..10000) }
                val moves = (0 until count).map { i ->
                    val old = File(p.getProperty("old.$i")); val backup = File(p.getProperty("backup.$i"))
                    require(inside(old, cacheRoot) && inside(backup, workRoot))
                    old to backup
                }
                val transaction = Prepared(id, work, target, moves, journal)
                val published = File(target, MARKER).takeIf { it.isFile }?.readText(Charsets.UTF_8) == id
                if (transaction.done.isFile || published) {
                    cleanCommitted(transaction)
                } else {
                    moves.asReversed().forEach { (old, backup) ->
                        if (backup.exists()) {
                            check(!old.exists()) { "Recovery conflict; both copies retained" }
                            check(old.parentFile!!.isDirectory || old.parentFile!!.mkdirs())
                            check(backup.renameTo(old)) { "Cannot restore old result; backup retained" }
                        }
                    }
                    journal.delete()
                }
            }.onFailure { warnings += "Image cache recovery pending (${journal.name}): ${it.message}" }
        }
        return warnings
    }
}
