package com.local.parallelvrgallerypro

import java.io.File
import java.util.UUID

internal object ImageCachePublication {
    fun commit(work: File, target: File) {
        require(work.isDirectory) { "Missing image staging directory" }
        val parent = requireNotNull(target.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Cannot create image cache directory" }
        val backup = File(work.parentFile, "backup-${UUID.randomUUID()}")
        val hadPrevious = target.exists()
        if (hadPrevious) check(target.renameTo(backup)) { "Cannot preserve previous image cache" }
        if (!work.renameTo(target)) {
            if (hadPrevious) check(backup.renameTo(target)) { "Cannot restore image cache: $backup" }
            error("Cannot commit generated image")
        }
        if (hadPrevious) backup.deleteRecursively()
    }
}
