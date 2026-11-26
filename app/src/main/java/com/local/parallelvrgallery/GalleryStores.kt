package com.local.parallelvrgallerypro

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.MediaStore
import android.util.Size
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.Closeable
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val qnnAvailable = QnnFp16DepthRuntime.hasGpuBackend(context)

    fun load(): AppSettings {
        // v1.1.00's global deletion blacklist incorrectly disabled normal source prefetch.
        if (prefs.contains("deletedImagePrefetchKeys")) prefs.edit().remove("deletedImagePrefetchKeys").apply()
        val migratedInvertDefault = prefs.getBoolean("migratedInvertDefaultOffV5", false)
        val invertDepth = if (migratedInvertDefault) {
            prefs.getBoolean("invertDepth", false)
        } else {
            prefs.edit()
                .putBoolean("invertDepth", false)
                .putBoolean("migratedInvertDefaultOffV5", true)
                .apply()
            false
        }
        migrateModelPreferences()
        val legacyModelId = prefs.getString("modelId", null)
        val imageModelId = preferredDepthModelId(prefs.getString("imageModelId", null), legacyModelId, qnnAvailable)
        val videoModelId = preferredDepthModelId(prefs.getString("videoModelId", null), legacyModelId, qnnAvailable)
        val existing = AppSettings(
            language = runCatching { AppLanguage.valueOf(prefs.getString("language", AppLanguage.ZH.name) ?: AppLanguage.ZH.name) }.getOrDefault(AppLanguage.ZH),
            imageModelId = imageModelId,
            videoModelId = videoModelId,
            autoPrefetch = prefs.getBoolean("autoPrefetch", true),
            prefetchWindow = prefs.getInt("prefetchWindow", 2),
            depthScale = prefs.getFloat("depthScale", 40f),
            blurRadius = prefs.getInt("blurRadius", 3),
            fillRadius = prefs.getInt("fillRadius", 10),
            invertDepth = invertDepth,
            maxLongEdge = prefs.getInt("maxLongEdge", 6000),
            depthResolution = modelSpec(imageModelId).inputSize,
            generationWorkers = prefs.getInt("generationWorkers", 1),
            modelThreads = prefs.getInt("modelThreads", 4),
            useGpu = prefs.getBoolean("useGpu", false),
            gpuTestMode = runCatching { GpuTestMode.valueOf(prefs.getString("gpuTestMode", GpuTestMode.ACCURATE_UNSET.name) ?: GpuTestMode.ACCURATE_UNSET.name) }.getOrDefault(GpuTestMode.ACCURATE_UNSET),
            forceGpuNoFallback = true,
            imageExportFormat = prefs.getString("imageExportFormat", "PNG").takeIf {it == "AVIF"} ?: "PNG",
            videoModelThreads = prefs.getInt("videoModelThreads", 4),
            videoUseGpu = prefs.getBoolean("videoUseGpu", false),
            videoDepthWorkers = prefs.getInt("videoDepthWorkers", 2).coerceIn(1, 2),
            edgeMode = if (prefs.getBoolean("modeProfilesV1", false)) DepthEdgeMode.restore(prefs.getString("edgeMode", null))
                else DepthEdgeMode.forCurrentSettings(prefs.getString("edgeMode", null)),
            depthUpscale = DepthUpscaleSettings(
                enabled = prefs.getBoolean("depthUpscaleEnabled", false),
                maxLongEdge = prefs.getInt("depthUpscaleMaxLongEdge", 0),
                interpolation = DepthInterpolation.restore(prefs.getString("depthInterpolation", null)),
                order = DepthResizeOrder.restore(prefs.getString("depthResizeOrder", null)),
            ),
        )
        if (!prefs.contains("edgeMode")) {
            return ModeVisualProfile.defaults(DepthEdgeMode.COMFY_LEGACY_V10).applyTo(existing.copy(edgeMode = DepthEdgeMode.COMFY_LEGACY_V10))
        }
        if (!prefs.getBoolean("modeProfilesV1", false)) {
            save(existing) // Keep the user's active legacy values; initialize other modes lazily.
            return existing
        }
        return profile(existing.edgeMode).applyTo(existing)
    }

    fun profile(mode: DepthEdgeMode): ModeVisualProfile =
        ModeVisualProfile.editable(ModeVisualProfile.decode(prefs.getString("modeProfile.${mode.name}", null)) ?: defaultProfile(mode), mode)

    fun defaultProfile(mode: DepthEdgeMode): ModeVisualProfile =
        ModeVisualProfile.editable(ModeVisualProfile.decode(prefs.getString("modeDefault.${mode.name}", null)) ?: ModeVisualProfile.defaults(mode), mode)

    // Explicit Save waits for disk; frequent setting changes still use apply().
    @android.annotation.SuppressLint("ApplySharedPref")
    fun saveDefault(settings: AppSettings): Boolean =
        prefs.edit().putString("modeDefault.${settings.edgeMode.name}", ModeVisualProfile.capture(settings).encode()).commit()

    fun clearDefault(mode: DepthEdgeMode) {
        prefs.edit().remove("modeDefault.${mode.name}").apply()
    }

    private fun migrateModelPreferences() {
        val replacements = listOf("modelId", "imageModelId", "videoModelId").mapNotNull { key ->
            val savedId = prefs.getString(key, null) ?: return@mapNotNull null
            val activeId = activeDepthModelId(savedId)
            if (activeId == savedId) null else key to activeId
        }
        if (replacements.isEmpty()) return
        val edit = prefs.edit()
        for ((key, id) in replacements) edit.putString(key, id)
        edit.apply()
    }

    fun save(settings: AppSettings) {
        val imageModelId = activeDepthModelId(settings.imageModelId)
        val videoModelId = activeDepthModelId(settings.videoModelId)
        prefs.edit()
            .putBoolean("modeProfilesV1", true)
            .putString("modeProfile.${settings.edgeMode.name}", ModeVisualProfile.capture(settings).encode())
            .putString("language", settings.language.name)
            .putString("modelId", imageModelId)
            .putString("imageModelId", imageModelId)
            .putString("videoModelId", videoModelId)
            .putBoolean("autoPrefetch", settings.autoPrefetch)
            .putInt("prefetchWindow", settings.prefetchWindow)
            .putString("imageExportFormat", settings.imageExportFormat)
            .putFloat("depthScale", settings.depthScale)
            .putInt("blurRadius", settings.blurRadius)
            .putInt("fillRadius", settings.fillRadius)
            .putBoolean("invertDepth", settings.invertDepth)
            .putBoolean("migratedInvertDefaultOffV5", true)
            .putInt("maxLongEdge", settings.maxLongEdge)
            .putInt("generationWorkers", settings.generationWorkers)
            .putInt("modelThreads", settings.modelThreads)
            .putBoolean("useGpu", settings.useGpu)
            .putString("gpuTestMode", settings.gpuTestMode.name)
            .putBoolean("forceGpuNoFallback", true)
            .putInt("videoModelThreads", settings.videoModelThreads)
            .putBoolean("videoUseGpu", settings.videoUseGpu)
            .putInt("videoDepthWorkers", settings.videoDepthWorkers)
            .putString("edgeMode", settings.edgeMode.name)
            .putBoolean("depthUpscaleEnabled", settings.depthUpscale.enabled)
            .putInt("depthUpscaleMaxLongEdge", settings.depthUpscale.boundedLongEdge)
            .putString("depthInterpolation", settings.depthUpscale.interpolation.name)
            .putString("depthResizeOrder", settings.depthUpscale.order.name)
            .apply()
    }

    fun loadGallerySortMode(): GallerySortMode = runCatching {
        GallerySortMode.valueOf(prefs.getString("gallerySortMode", GallerySortMode.TAKEN_TIME.name) ?: GallerySortMode.TAKEN_TIME.name)
    }.getOrDefault(GallerySortMode.TAKEN_TIME)

    fun saveGallerySortMode(mode: GallerySortMode) {
        prefs.edit().putString("gallerySortMode", mode.name).apply()
    }

    fun loadGalleryViewMode(): GalleryViewMode = runCatching {
        GalleryViewMode.valueOf(prefs.getString("galleryViewMode", GalleryViewMode.DATE_GROUPED.name) ?: GalleryViewMode.DATE_GROUPED.name)
    }.getOrDefault(GalleryViewMode.DATE_GROUPED)

    fun saveGalleryViewMode(mode: GalleryViewMode) {
        prefs.edit().putString("galleryViewMode", mode.name).apply()
    }
}

internal class PhotoRepository(private val context: Context) {
    fun loadImages(): List<PhotoItem> = loadMedia(INITIAL_MEDIA_LIMIT).filter { it.kind == MediaKind.IMAGE }

    fun loadAlbums(): List<AlbumItem> = buildAlbums(loadMedia(null))

    fun loadAlbumMedia(bucketId: String, limit: Int, offset: Int): List<GalleryItem> {
        return queryFilesMedia(limit = limit, offset = offset, bucketId = bucketId)
    }

    fun loadMedia(limit: Int? = null, offset: Int = 0): List<GalleryItem> {
        return queryFilesMedia(limit = limit, offset = offset, bucketId = null)
    }

    private fun queryFilesMedia(limit: Int?, offset: Int, bucketId: String?): List<GalleryItem> {
        val result = mutableListOf<PhotoItem>()
        if (!hasImagePermission(context) && !hasVideoPermission(context)) return emptyList()
        val collection = MediaStore.Files.getContentUri("external")
        val idColumn = MediaStore.Files.FileColumns._ID
        val mediaTypeColumn = MediaStore.Files.FileColumns.MEDIA_TYPE
        val nameColumn = MediaStore.MediaColumns.DISPLAY_NAME
        val widthColumn = MediaStore.MediaColumns.WIDTH
        val heightColumn = MediaStore.MediaColumns.HEIGHT
        val sizeColumn = MediaStore.MediaColumns.SIZE
        val modifiedColumn = MediaStore.MediaColumns.DATE_MODIFIED
        val addedColumn = MediaStore.MediaColumns.DATE_ADDED
        val takenColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.DATE_TAKEN else MediaStore.Images.Media.DATE_TAKEN
        val bucketIdColumn = MediaStore.Images.Media.BUCKET_ID
        val bucketNameColumn = MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        val relativePathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH else null
        val projection = listOfNotNull(
            idColumn,
            mediaTypeColumn,
            nameColumn,
            widthColumn,
            heightColumn,
            sizeColumn,
            modifiedColumn,
            addedColumn,
            takenColumn,
            bucketIdColumn,
            bucketNameColumn,
            relativePathColumn,
        ).toTypedArray()
        val fallbackProjection = listOfNotNull(
            idColumn,
            mediaTypeColumn,
            nameColumn,
            widthColumn,
            heightColumn,
            sizeColumn,
            modifiedColumn,
            addedColumn,
            bucketIdColumn,
            bucketNameColumn,
            relativePathColumn,
        ).toTypedArray()
        val mediaTypes = mutableListOf<String>()
        if (hasImagePermission(context)) mediaTypes += MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
        if (hasVideoPermission(context)) mediaTypes += MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        val selectionParts = mutableListOf("${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${mediaTypes.joinToString(",") { "?" }})")
        val args = mutableListOf<String>().apply { addAll(mediaTypes) }
        if (bucketId != null) {
            selectionParts += "$bucketIdColumn=?"
            args += bucketId
        }
        val selection = selectionParts.joinToString(" AND ")
        val limitedSortOrder = if (limit != null) "$modifiedColumn DESC, $idColumn DESC LIMIT $limit OFFSET $offset" else "$modifiedColumn DESC, $idColumn DESC"
        var manualOffset = false
        val cursorResult = runCatching {
            context.contentResolver.query(collection, projection, selection, args.toTypedArray(), limitedSortOrder)
        }.getOrNull() ?: run {
            manualOffset = true
            runCatching {
                context.contentResolver.query(collection, projection, selection, args.toTypedArray(), "$modifiedColumn DESC, $idColumn DESC")
            }.getOrNull() ?: context.contentResolver.query(collection, fallbackProjection, selection, args.toTypedArray(), "$modifiedColumn DESC, $idColumn DESC")
        }
        cursorResult?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(idColumn)
            val mediaTypeCol = cursor.getColumnIndexOrThrow(mediaTypeColumn)
            val nameCol = cursor.getColumnIndexOrThrow(nameColumn)
            val widthCol = cursor.getColumnIndex(widthColumn)
            val heightCol = cursor.getColumnIndex(heightColumn)
            val sizeCol = cursor.getColumnIndex(sizeColumn)
            val modifiedCol = cursor.getColumnIndexOrThrow(modifiedColumn)
            val addedCol = cursor.getColumnIndex(addedColumn)
            val takenCol = cursor.getColumnIndex(takenColumn)
            val bucketIdCol = cursor.getColumnIndex(bucketIdColumn)
            val bucketNameCol = cursor.getColumnIndex(bucketNameColumn)
            val relativePathCol = relativePathColumn?.let { cursor.getColumnIndex(it) } ?: -1
            var skipped = 0
            while (cursor.moveToNext()) {
                if (manualOffset && limit != null && skipped < offset) {
                    skipped++
                    continue
                }
                if (limit != null && result.size >= limit) break
                val mediaType = cursor.getInt(mediaTypeCol)
                val kind = when (mediaType) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaKind.IMAGE
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaKind.VIDEO
                    else -> continue
                }
                val id = cursor.getLong(idCol)
                val displayName = cursor.getString(nameCol) ?: "${kind.name.lowercase(Locale.US)}-$id"
                if (kind == MediaKind.IMAGE && (displayName.startsWith(GENERATED_VR_PREFIX) || displayName.startsWith("VR_"))) continue
                val itemUri = if (kind == MediaKind.IMAGE) {
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                } else {
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                }
                val modifiedSeconds = cursor.getLong(modifiedCol)
                val rawAdded = if (addedCol >= 0) cursor.getLong(addedCol) else 0L
                val addedSeconds = when {
                    rawAdded > 1_000_000_000_000L -> rawAdded / 1000L
                    rawAdded > 0L -> rawAdded
                    else -> modifiedSeconds
                }
                val rawTaken = if (takenCol >= 0) cursor.getLong(takenCol) else 0L
                val takenSeconds = when {
                    rawTaken > 1_000_000_000_000L -> rawTaken / 1000L
                    rawTaken > 0L -> rawTaken
                    else -> modifiedSeconds
                }
                result += GalleryItem(
                    id = id,
                    uri = itemUri,
                    displayName = displayName,
                    width = if (widthCol >= 0) cursor.getInt(widthCol) else 0,
                    height = if (heightCol >= 0) cursor.getInt(heightCol) else 0,
                    size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L,
                    modifiedTime = modifiedSeconds,
                    kind = kind,
                    bucketId = if (bucketIdCol >= 0) cursor.getString(bucketIdCol).orEmpty() else "",
                    bucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol).orEmpty() else "",
                    relativePath = if (relativePathCol >= 0) cursor.getString(relativePathCol).orEmpty() else "",
                    takenTime = takenSeconds,
                    addedTime = addedSeconds,
                )
            }
        }
        return result
    }

    private fun queryMedia(
        collection: Uri,
        idColumn: String,
        nameColumn: String,
        widthColumn: String,
        heightColumn: String,
        sizeColumn: String,
        modifiedColumn: String,
        bucketIdColumn: String,
        bucketNameColumn: String,
        relativePathColumn: String?,
        kind: MediaKind,
        limit: Int?,
        offset: Int = 0,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
    ): List<GalleryItem> {
        val projection = listOfNotNull(idColumn, nameColumn, widthColumn, heightColumn, sizeColumn, modifiedColumn, bucketIdColumn, bucketNameColumn, relativePathColumn).toTypedArray()
        val result = mutableListOf<GalleryItem>()
        val limitedSortOrder = if (limit != null) "$modifiedColumn DESC LIMIT $limit OFFSET $offset" else "$modifiedColumn DESC"
        var manualOffset = false
        val cursorResult = runCatching {
            context.contentResolver.query(collection, projection, selection, selectionArgs, limitedSortOrder)
        }.getOrNull() ?: run {
            manualOffset = true
            context.contentResolver.query(collection, projection, selection, selectionArgs, "$modifiedColumn DESC")
        }
        cursorResult?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(idColumn)
            val nameCol = cursor.getColumnIndexOrThrow(nameColumn)
            val widthCol = cursor.getColumnIndexOrThrow(widthColumn)
            val heightCol = cursor.getColumnIndexOrThrow(heightColumn)
            val sizeCol = cursor.getColumnIndexOrThrow(sizeColumn)
            val modifiedCol = cursor.getColumnIndexOrThrow(modifiedColumn)
            val bucketIdCol = cursor.getColumnIndex(bucketIdColumn)
            val bucketNameCol = cursor.getColumnIndex(bucketNameColumn)
            val relativePathCol = relativePathColumn?.let { cursor.getColumnIndex(it) } ?: -1
            var skipped = 0
            while (cursor.moveToNext()) {
                if (manualOffset && limit != null && skipped < offset) {
                    skipped++
                    continue
                }
                if (limit != null && result.size >= limit) break
                val id = cursor.getLong(idCol)
                val displayName = cursor.getString(nameCol) ?: "${kind.name.lowercase(Locale.US)}-$id"
                if (kind == MediaKind.IMAGE && (displayName.startsWith(GENERATED_VR_PREFIX) || displayName.startsWith("VR_"))) continue
                val bucketId = if (bucketIdCol >= 0) cursor.getString(bucketIdCol).orEmpty() else ""
                val bucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol).orEmpty() else ""
                val relativePath = if (relativePathCol >= 0) cursor.getString(relativePathCol).orEmpty() else ""
                result += GalleryItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = displayName,
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol),
                    size = cursor.getLong(sizeCol),
                    modifiedTime = cursor.getLong(modifiedCol),
                    kind = kind,
                    bucketId = bucketId,
                    bucketName = bucketName,
                    relativePath = relativePath,
                )
            }
        }
        return result
    }
}

internal class QueueTagStore(context: Context) : Closeable {
    private val root = File(context.getExternalFilesDir(null), "queue_index").also { it.mkdirs() }
    private val file = File(root, "image_queue.tsv")
    private val storage = BufferedTextFile(file)
    override fun close() { storage.close() }
    fun flush() = storage.flush()

    @Synchronized
    fun load(): List<QueueTag> {
        return storage.read().lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 6) return@mapNotNull null
            val source = runCatching { QueueSource.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
            val state = runCatching { VrState.valueOf(parts[4]) }.getOrNull() ?: VrState.QUEUED
            QueueTag(
                photoKey = parts[0],
                source = source,
                albumId = parts[2].takeIf { it.isNotBlank() },
                priority = parts[3].toIntOrNull() ?: 99,
                state = state,
                updatedAt = parts[5].toLongOrNull() ?: 0L,
                params = parts.getOrNull(6)?.let { decodeVideoParams(it)?.vr },
                writeIntent = ImageWriteIntent.decode(parts.getOrNull(7)),
                pauseReason = parts.getOrNull(8)?.takeIf { it.isNotBlank() },
            )
        }.distinctBy { it.photoKey }.toList()
    }

    @Synchronized
    fun upsert(job: QueuedJob, state: VrState, pauseReason: String? = null) {
        val next = load()
            .filterNot { it.photoKey == job.photo.cacheKey }
            .toMutableList()
        next += QueueTag(
            photoKey = job.photo.cacheKey,
            source = job.source,
            albumId = job.albumId,
            priority = job.priority,
            state = state,
            updatedAt = System.currentTimeMillis(),
            params = job.params,
            writeIntent = job.writeIntent,
            pauseReason = pauseReason,
        )
        write(next)
    }

    @Synchronized
    fun remove(photoKey: String) {
        write(load().filterNot { it.photoKey == photoKey })
    }

    @Synchronized
    fun clearActive() {
        write(load().filter { it.state == VrState.FAILED })
    }

    @Synchronized
    fun clearAll() {
        write(emptyList())
    }

    private fun write(tags: List<QueueTag>) {
        storage.write(
            tags.joinToString("\n") { tag ->
                listOf(
                    tag.photoKey.safeQueueField(),
                    tag.source.name,
                    tag.albumId.orEmpty().safeQueueField(),
                    tag.priority.toString(),
                    tag.state.name,
                    tag.updatedAt.toString(),
                    tag.params?.let { VideoGenerationParams(it, it.modelThreads, it.useGpu).encodeForQueue() }.orEmpty(),
                    tag.writeIntent?.encode().orEmpty(),
                    tag.pauseReason.orEmpty(),
                ).joinToString("\t")
            },
        )
    }

    private fun String.safeQueueField(): String = replace('\t', '_').replace('\n', '_').replace('\r', '_')
}

internal class VideoQueueTagStore(context: Context) : Closeable {
    private val root = File(context.getExternalFilesDir(null), "queue_index").also { it.mkdirs() }
    private val file = File(root, "video_queue.tsv")
    private val storage = BufferedTextFile(file)
    override fun close() { storage.close() }
    fun flush() = storage.flush()

    @Synchronized
    fun load(): List<VideoQueueTag> {
        return storage.read().lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val state = runCatching { VideoVrState.valueOf(parts[1]) }.getOrNull() ?: VideoVrState.PAUSED
            VideoQueueTag(
                videoKey = parts[0],
                state = state,
                updatedAt = parts[2].toLongOrNull() ?: 0L,
                params = parts.getOrNull(3)?.let { decodeVideoParams(it) },
                pauseReason = parts.getOrNull(4)?.takeIf { it.isNotBlank() },
            )
        }.distinctBy { it.videoKey }.toList()
    }

    @Synchronized
    fun upsert(videoKey: String, state: VideoVrState, params: VideoGenerationParams? = null, pauseReason: String? = null) {
        val next = load()
            .filterNot { it.videoKey == videoKey }
            .toMutableList()
        val previous = load().firstOrNull { it.videoKey == videoKey }
        next += VideoQueueTag(
            videoKey = videoKey,
            state = state,
            params = params ?: previous?.params,
            pauseReason = pauseReason,
            updatedAt = System.currentTimeMillis(),
        )
        write(next)
    }

    @Synchronized
    fun remove(videoKey: String) {
        write(load().filterNot { it.videoKey == videoKey })
    }

    private fun write(tags: List<VideoQueueTag>) {
        storage.write(
            tags.joinToString("\n") { tag ->
                listOf(
                    tag.videoKey.safeQueueField(),
                    tag.state.name,
                    tag.updatedAt.toString(),
                    tag.params?.encodeForQueue().orEmpty(),
                    tag.pauseReason.orEmpty(),
                ).joinToString("\t")
            },
        )
    }


    private fun String.safeQueueField(): String = replace('\t', '_').replace('\n', '_').replace('\r', '_')
}

internal class VrCacheManager(private val context: Context) {
    val root: File = File(context.getExternalFilesDir(null), "vr_cache").also { it.mkdirs() }
    init {
        ImageCachePublication.recover(File(root.parentFile, "image_work"), root).forEach { android.util.Log.w("ImageCacheRecovery", it) }
    }
    private val index = VersionedIndex<VrCacheEntry>({ it.photoKey }, { it.version }, { it.createdAt })
    private val virtualItems = mutableMapOf<String, PhotoItem>()
    private val scanLock = Any()

    fun refresh() = synchronized(scanLock) {
        val since = index.scanRevision()
        val scanned = mutableListOf<Pair<VrCacheEntry, Long>>()
        root.listFiles()?.filter { it.isDirectory }?.forEach { photoDir ->
            readEntry(photoDir.name, DEFAULT_VERSION, photoDir)?.let { scanned += it to it.cacheBytes() }
            photoDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                readEntry(photoDir.name, dir.name, dir)?.let { scanned += it to it.cacheBytes() }
            }
        }
        index.replaceFromScan(scanned, since)
        synchronized(this) { virtualItems.clear() }
    }
    @Synchronized fun register(entry: VrCacheEntry) {
        index.put(entry, entry.cacheBytes())
        virtualItems.remove(entry.photoKey)
    }

    fun entryDir(photo: PhotoItem, version: String): File {
        return if (version == DEFAULT_VERSION) File(root, photo.cacheKey) else File(File(root, photo.cacheKey), version)
    }

    fun stagingDir(): File = File(root.parentFile, "image_work/${java.util.UUID.randomUUID()}").also {
        check(it.mkdirs()) { "Cannot create image staging directory" }
    }

    @Synchronized fun publish(photo: PhotoItem, staged: VrCacheEntry, replacing: List<CacheReplacementTarget> = emptyList()): VrCacheEntry {
        val work = File(staged.paramsPath).parentFile!!
        val target = entryDir(photo, staged.version)
        val retiring = replacing.mapNotNull { old -> index.get(photo.cacheKey, old.version)?.takeIf { it.createdAt == old.createdAt && it.version != staged.version } }
        ImageCachePublication.commit(work, target, retiring.flatMap { old ->
            val dir = File(old.paramsPath).parentFile!!
            if (old.version == DEFAULT_VERSION) IMAGE_CACHE_FILES.map { File(dir, it) }.filter { it.isFile } else listOf(dir)
        })
        retiring.forEach { old -> index.removeWhere { it.photoKey == photo.cacheKey && it.version == old.version && it.createdAt == old.createdAt } }
        fun publishedPath(path: String) = if (path.isBlank()) "" else File(target, File(path).name).absolutePath
        return staged.copy(
            outputPath = publishedPath(staged.outputPath), leftPath = publishedPath(staged.leftPath),
            rightPath = publishedPath(staged.rightPath), depthPath = publishedPath(staged.depthPath),
            paramsPath = publishedPath(staged.paramsPath), logPath = publishedPath(staged.logPath),
        ).also { register(it) }
    }

    fun findEntry(photo: PhotoItem, version: String? = null): VrCacheEntry? {
        return validEntry(index.get(photo.cacheKey, version))
    }

    fun latestEntry(photoKey: String): VrCacheEntry? {
        return validEntry(index.get(photoKey))
    }

    private fun validEntry(entry: VrCacheEntry?): VrCacheEntry? {
        if (entry == null) return null
        if (entry.hasSplitEyes || entry.hasLegacySbs) return entry
        index.removeWhere { it.photoKey == entry.photoKey && it.version == entry.version }
        return null
    }

    fun summaries(): List<CacheVersionSummary> {
        return index.summaries().entries.groupBy { generatedAlbumKey(it.key) }.map { (album, versions) ->
            CacheVersionSummary(
                version = album,
                kind = "图片 / Images",
                count = versions.sumOf { it.value.first },
                bytes = versions.sumOf { it.value.second },
            )
        }.sortedBy { it.version }
    }

    fun allEntries(photos: List<PhotoItem>): List<ManagedCacheItem> {
        return allEntriesByKey(photos.associateBy { it.cacheKey })
    }

    fun allEntriesByKey(photoByKey: Map<String, PhotoItem>): List<ManagedCacheItem> {
        return index.all().map { entry ->
            val photo = photoByKey[entry.photoKey] ?: synchronized(this) {
                virtualItems.getOrPut(entry.photoKey) { virtualPhotoItem(entry.photoKey, entry) }
            }
            ManagedCacheItem(photo, entry)
        }
    }

    fun allEntriesSlow(photos: List<PhotoItem>): List<ManagedCacheItem> {
        return photos.flatMap { photo ->
            val entries = mutableListOf<VrCacheEntry>()
            readEntry(photo.cacheKey, DEFAULT_VERSION, entryDir(photo, DEFAULT_VERSION))?.let { entries += it }
            File(root, photo.cacheKey).listFiles()?.filter { it.isDirectory }?.forEach { versionDir ->
                readEntry(photo.cacheKey, versionDir.name, versionDir)?.let { entries += it }
            }
            entries.map { ManagedCacheItem(photo, it) }
        }.sortedByDescending { it.entry.createdAt }
    }

    @Synchronized fun deleteVersion(version: String) {
        root.listFiles()?.filter { it.isDirectory }?.forEach { photoDir ->
            if (version == DEFAULT_VERSION) {
                IMAGE_CACHE_FILES.forEach {
                    val file = File(photoDir, it)
                    check(!file.exists() || file.delete()) { "Cannot delete image cache: $file" }
                }
            } else {
                val dir = File(photoDir, version)
                check(dir.deleteRecursively()) { "Cannot delete image cache version: $dir" }
            }
        }
        index.removeWhere { it.version == version }
        virtualItems.clear()
    }

    @Synchronized fun deleteEntry(entry: VrCacheEntry) {
        val photoDir = File(root, entry.photoKey)
        if (entry.version == DEFAULT_VERSION) {
            IMAGE_CACHE_FILES.forEach {
                val file = File(photoDir, it)
                check(!file.exists() || file.delete()) { "Cannot delete image cache: $file" }
            }
        } else {
            val dir = File(photoDir, entry.version)
            check(dir.deleteRecursively()) { "Cannot delete image cache: $dir" }
        }
        index.removeWhere { it.photoKey == entry.photoKey && it.version == entry.version }
        virtualItems.remove(entry.photoKey)
    }

    @Synchronized fun deletePhoto(photo: PhotoItem) {
        val dir = File(root, photo.cacheKey)
        check(dir.deleteRecursively()) { "Cannot delete image cache: $dir" }
        index.removeWhere { it.photoKey == photo.cacheKey }
        virtualItems.remove(photo.cacheKey)
    }

    private fun readEntry(photoKey: String, version: String, dir: File): VrCacheEntry? {
        val vr = File(dir, "vr_sbs.jpg")
        val splitEyes = listOf(
            File(dir, "left.jpg") to File(dir, "right.jpg"),
            File(dir, "left.webp") to File(dir, "right.webp"),
        ).firstOrNull { (left, right) -> left.exists() && right.exists() }
        val left = splitEyes?.first
        val right = splitEyes?.second
        val depth = File(dir, "depth.png")
        val params = File(dir, "params.json")
        val log = File(dir, "job.log")
        if ((!vr.exists() && splitEyes == null) || !depth.exists() || !params.exists() || !log.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (left != null && right != null) {
            BitmapFactory.decodeFile(left.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        } else {
            BitmapFactory.decodeFile(vr.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        }
        val leftWidth = if (left != null && right != null) bounds.outWidth else max(1, bounds.outWidth / 2)
        val outputWidth = leftWidth * 2
        val outputHeight = bounds.outHeight
        val modified = listOfNotNull(vr, left, right, params, log)
            .filter { it.exists() }
            .maxOfOrNull { it.lastModified() }
            ?: dir.lastModified()
        return VrCacheEntry(
            photoKey = photoKey,
            version = version,
            outputPath = vr.absolutePath,
            leftPath = left?.absolutePath.orEmpty(),
            rightPath = right?.absolutePath.orEmpty(),
            depthPath = depth.absolutePath,
            paramsPath = params.absolutePath,
            logPath = log.absolutePath,
            width = outputWidth,
            height = outputHeight,
            createdAt = modified,
        )
    }

    private fun virtualPhotoItem(photoKey: String, entry: VrCacheEntry): PhotoItem {
        val paramsText = runCatching { File(entry.paramsPath).readText(Charsets.UTF_8) }.getOrDefault("")
        val displayName = jsonStringValue(paramsText, "displayName") ?: "generated_$photoKey.jpg"
        val sourceWidth = jsonIntValue(paramsText, "sourceWidth") ?: max(1, entry.width / 2)
        val sourceHeight = jsonIntValue(paramsText, "sourceHeight") ?: entry.height
        val parts = photoKey.split("_")
        val id = parts.getOrNull(0)?.toLongOrNull() ?: abs(photoKey.hashCode()).toLong()
        val size = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        val modified = parts.getOrNull(2)?.toLongOrNull() ?: (entry.createdAt / 1000L)
        val uri = parts.getOrNull(0)?.toLongOrNull()?.let { ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it) }
            ?: Uri.fromFile(File(entry.leftPath.takeIf { it.isNotBlank() } ?: entry.outputPath))
        return PhotoItem(
            id = id,
            uri = uri,
            displayName = displayName,
            width = sourceWidth,
            height = sourceHeight,
            size = size,
            modifiedTime = modified,
            kind = MediaKind.IMAGE,
            forcedCacheKey = photoKey,
            generatedVirtual = true,
        )
    }

    companion object {
        const val DEFAULT_VERSION = "default"
    }
}

internal class VideoVrCacheManager(private val context: Context) {
    val root: File = File(context.getExternalFilesDir(null), "video_vr_cache").also { it.mkdirs() }
    private val index = VersionedIndex<VideoCacheEntry>({ it.videoKey }, { File(it.outputPath).parent.orEmpty() }, { it.createdAt })
    private val virtualItems = mutableMapOf<String, GalleryItem>()
    private val scanLock = Any()

    fun refresh() = synchronized(scanLock) {
        val since = index.scanRevision()
        val scanned = mutableListOf<Pair<VideoCacheEntry, Long>>()
        root.listFiles()?.filter { it.isDirectory }?.forEach { videoDir ->
            readEntry(videoDir.name, videoDir)?.let { scanned += it to File(it.outputPath).length() }
            videoDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                readEntry(videoDir.name, dir)?.let { scanned += it to File(it.outputPath).length() }
            }
        }
        index.replaceFromScan(scanned, since)
        synchronized(this) { virtualItems.clear() }
    }
    @Synchronized fun register(item: GalleryItem, dir: File): VideoCacheEntry? {
        return readEntry(item.cacheKey, dir)?.also {
            index.put(it, File(it.outputPath).length())
            virtualItems.remove(it.videoKey)
        }
    }

    fun entryDir(item: GalleryItem): File = File(root, item.cacheKey).also { it.mkdirs() }

    fun entryDir(item: GalleryItem, version: String): File = File(entryDir(item), version).also { it.mkdirs() }

    fun findEntry(item: GalleryItem, version: String? = null): VideoCacheEntry? {
        val entry = index.get(item.cacheKey, version?.let { File(File(root, item.cacheKey), it).absolutePath }) ?: return null
        if (File(entry.outputPath).isFile) return entry
        index.removeWhere { it.outputPath == entry.outputPath }
        return null
    }

    fun allEntries(items: List<GalleryItem>): List<Pair<GalleryItem, VideoCacheEntry>> {
        return allEntriesByKey(items.filter { it.kind == MediaKind.VIDEO }.associateBy { it.cacheKey })
    }

    fun allEntriesByKey(videoByKey: Map<String, GalleryItem>): List<Pair<GalleryItem, VideoCacheEntry>> {
        return index.all().distinctBy { it.videoKey }.map { entry ->
            val item = videoByKey[entry.videoKey] ?: synchronized(this) {
                virtualItems.getOrPut(entry.videoKey) { virtualVideoItem(entry.videoKey, entry) }
            }
            item to entry
        }
    }

    @Synchronized fun delete(item: GalleryItem) {
        File(root, item.cacheKey).deleteRecursively()
        index.removeWhere { it.videoKey == item.cacheKey }
        virtualItems.remove(item.cacheKey)
    }

    private fun readEntry(videoKey: String, dir: File): VideoCacheEntry? {
        val output = File(dir, "vr_sbs.mp4")
        val log = File(dir, "job.log")
        val meta = File(dir, "video_params.txt")
        if (!output.exists() || !log.exists() || !meta.exists()) return null
        val values = meta.readLines().associate {
            val parts = it.split("=", limit = 2)
            parts.first() to parts.getOrElse(1) { "" }
        }
        if (values["encoderVersion"] !in READABLE_VIDEO_ENCODER_VERSIONS) return null
        return VideoCacheEntry(
            videoKey = videoKey,
            outputPath = output.absolutePath,
            logPath = log.absolutePath,
            width = values["width"]?.toIntOrNull() ?: 0,
            height = values["height"]?.toIntOrNull() ?: 0,
            durationMs = values["durationMs"]?.toLongOrNull() ?: 0L,
            fps = values["fps"]?.toIntOrNull() ?: 30,
            createdAt = output.lastModified(),
            modelId = values["depthModel"].orEmpty(),
            cacheVersion = values["cacheVersion"].orEmpty(),
            depthGeometry = VideoDepthGeometry.read(values),
        )
    }

    private fun virtualVideoItem(videoKey: String, entry: VideoCacheEntry): GalleryItem {
        val values = runCatching {
            File(entry.logPath).parentFile?.let { File(it, "video_params.txt") }?.readLines(Charsets.UTF_8)?.associate { line ->
                val parts = line.split("=", limit = 2)
                parts.first() to parts.getOrElse(1) { "" }
            }
        }.getOrNull().orEmpty()
        val displayName = values["source"]?.takeIf { it.isNotBlank() } ?: "generated_$videoKey.mp4"
        val rawKey = videoKey.removePrefix("VIDEO_")
        val parts = rawKey.split("_")
        val id = parts.getOrNull(0)?.toLongOrNull() ?: abs(videoKey.hashCode()).toLong()
        val size = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        val modified = parts.getOrNull(2)?.toLongOrNull() ?: (entry.createdAt / 1000L)
        val uri = parts.getOrNull(0)?.toLongOrNull()?.let { ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, it) }
            ?: Uri.fromFile(File(entry.outputPath))
        return GalleryItem(
            id = id,
            uri = uri,
            displayName = displayName,
            width = entry.width,
            height = entry.height,
            size = size,
            modifiedTime = modified,
            kind = MediaKind.VIDEO,
            forcedCacheKey = videoKey,
            generatedVirtual = true,
        )
    }
}

internal class VideoGenerationNotifier(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "视频 VR 生成", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
    }

    fun show(item: GalleryItem, frame: Int, total: Int, indeterminate: Boolean, status: String) {
        if (!canNotify()) return
        val progress = if (total > 0) ((frame.toFloat() / total.toFloat()) * 100f).roundToInt().coerceIn(0, 100) else 0
        val text = if (total > 0) "$progress%  $frame / $total  $status" else status
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("视频 VR 生成：$text")
            .setContentText(item.displayName)
            .setSubText(if (total > 0) "$progress%" else null)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n${item.displayName}"))
            .setOnlyAlertOnce(true)
            .setOngoing(status != "已完成" && status != "已暂停")
            .setProgress(if (total > 0) total else 100, frame.coerceAtLeast(0), indeterminate)
            .build()
        manager.notify(notificationId(item), notification)
    }

    fun failed(item: GalleryItem, message: String) {
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("视频 VR 生成失败")
            .setContentText("${item.displayName}：$message")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${item.displayName}\n$message"))
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .build()
        manager.notify(notificationId(item), notification)
    }

    private fun canNotify(): Boolean {
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun notificationId(item: GalleryItem): Int = (item.cacheKey.hashCode() and 0x7fffffff).coerceAtLeast(1)

    companion object {
        private const val CHANNEL_ID = "video_vr_generation"
    }
}

internal class ModelManager(private val context: Context) {
    private val modelsDir = File(context.getExternalFilesDir(null), "models").also { it.mkdirs() }

    fun statusText(modelId: String = AvailableModels.first().id): String {
        if (hasQnnModelPrefix(modelId)) {
            return when {
                qnnModelConfig(modelId) == null -> "QNN 模型设置无效 / Invalid QNN model settings"
                !QnnFp16DepthRuntime.hasGpuBackend(context) -> "QNN GPU 不可用 / QNN GPU unavailable"
                hasLocalModel(modelId) -> "模型资产已内置 / Model asset bundled"
                else -> "缺少内置 QNN 模型 / Bundled QNN model missing"
            }
        }
        val spec = modelSpec(modelId)
        if (!spec.downloadable) return "模型待导入 / Model asset required"
        val modelFile = File(modelsDir, spec.fileName)
        return if (modelFile.exists()) {
            "模型已就绪 / Model ready"
        } else {
            "模型未下载 / Model not downloaded"
        }
    }

    fun hasLocalModel(modelId: String): Boolean {
        if (hasQnnModelPrefix(modelId)) {
            val config = qnnModelConfig(modelId) ?: return false
            return QnnFp16DepthRuntime.hasGpuBackend(context) &&
                runCatching { context.assets.openFd(config.variant.assetName).close() }.isSuccess
        }
        return File(modelsDir, modelSpec(modelId).fileName).isFile
    }

    @Synchronized
    fun ensureModel(modelId: String, onProgress: (Float) -> Unit, allowDownload: Boolean = true): File {
        check(!hasQnnModelPrefix(modelId)) { "QNN 模型由内置资源加载 / QNN models load from bundled assets" }
        val spec = modelSpec(modelId)
        if (!spec.downloadable) {
            error("${spec.displayName} 需要先导入移动端模型资产 / Mobile model asset required")
        }
        val modelFile = File(modelsDir, spec.fileName)
        if (modelFile.exists() && modelFile.sha256().equals(spec.sha256, ignoreCase = true)) {
            onProgress(1f)
            return modelFile
        }

        check(allowDownload) { "Local model missing or checksum mismatch; preload does not download" }

        val tmp = File(modelsDir, "${spec.fileName}.download")
        val urls = modelDownloadUrls(spec)
        val errors = mutableListOf<String>()
        for ((urlIndex, modelUrl) in urls.withIndex()) {
            if (tmp.exists()) tmp.delete()
            runCatching {
                val connection = (URL(modelUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 60000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "ParallelVrGalleryPro/$CURRENT_VERSION_TAG")
                    setRequestProperty("Accept", "application/octet-stream,*/*")
                }
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    error("HTTP ${connection.responseCode}")
                }
                val total = connection.contentLengthLong.coerceAtLeast(1L)
                var readTotal = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(tmp).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            readTotal += read
                            val sourceBase = urlIndex.toFloat() / urls.size.toFloat()
                            val sourceSpan = 1f / urls.size.toFloat()
                            onProgress((sourceBase + (readTotal.toFloat() / total.toFloat()) * sourceSpan).coerceIn(0f, 0.99f))
                        }
                    }
                }
                val hash = tmp.sha256()
                if (!hash.equals(spec.sha256, ignoreCase = true)) {
                    tmp.delete()
                    error("SHA-256 mismatch: $hash")
                }
                if (modelFile.exists()) modelFile.delete()
                check(tmp.renameTo(modelFile)) { "Could not publish verified model" }
                onProgress(1f)
                return modelFile
            }.onFailure { error ->
                errors += "${modelUrl.hostLabel()}: ${error.shortMessage()}"
            }
        }
        if (tmp.exists()) tmp.delete()
        error("模型下载失败，所有下载源均不可用 / Model download failed: ${errors.joinToString(" | ")}")
    }

    private fun modelDownloadUrls(spec: ModelSpec): List<String> {
        val direct = listOf(spec.url) + spec.mirrors
        val generatedMirrors = if (spec.url.contains("github.com/$MODEL_ASSET_REPO/releases/download/")) {
            listOf(
                "https://gh-proxy.com/${spec.url}",
                "https://ghproxy.net/${spec.url}",
            )
        } else {
            emptyList()
        }
        return (direct + generatedMirrors)
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun String.hostLabel(): String = runCatching { URL(this).host }
        .getOrDefault(this.substringBefore('/'))
        .removePrefix("www.")
}
