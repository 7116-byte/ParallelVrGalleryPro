package com.local.parallelvrgallerypro

import android.content.ContentUris
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.provider.MediaStore
import android.util.Size
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class MediaKind {
    IMAGE,
    VIDEO,
}

enum class GallerySortMode {
    TAKEN_TIME,
    ADDED_TIME,
}

enum class GalleryViewMode {
    DATE_GROUPED,
    FLAT_GRID,
}

enum class GalleryFilterMode {
    ALL,
    IMAGES_ONLY,
    VIDEOS_ONLY,
    VR_GENERATED,
    VR_NOT_GENERATED,
}

data class PhotoItem @JvmOverloads constructor(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val width: Int,
    val height: Int,
    val size: Long,
    val modifiedTime: Long,
    val kind: MediaKind = MediaKind.IMAGE,
    val bucketId: String = "",
    val bucketName: String = "",
    val relativePath: String = "",
    val forcedCacheKey: String? = null,
    val generatedVirtual: Boolean = false,
    val takenTime: Long = modifiedTime,
    val addedTime: Long = modifiedTime,
) {
    val cacheKey: String
        get() = forcedCacheKey ?: if (kind == MediaKind.IMAGE) "${id}_${size}_${modifiedTime}" else "VIDEO_${id}_${size}_${modifiedTime}"
}

internal fun PhotoItem.sortTimestamp(mode: GallerySortMode): Long = when (mode) {
    GallerySortMode.TAKEN_TIME -> if (takenTime > 0L) takenTime else modifiedTime
    GallerySortMode.ADDED_TIME -> if (addedTime > 0L) addedTime else modifiedTime
}

typealias GalleryItem = PhotoItem

data class AlbumItem(
    val bucketId: String,
    val name: String,
    val relativePath: String,
    val coverUri: Uri,
    val coverKind: MediaKind,
    val count: Int,
    val latestModified: Long,
)

enum class VrState {
    NORMAL,
    PAUSED,
    QUEUED,
    GENERATING,
    READY,
    FAILED,
}

enum class VideoVrState {
    NORMAL,
    QUEUED,
    GENERATING,
    PAUSED,
    READY,
    FAILED,
}

data class VrCacheEntry(
    val photoKey: String,
    val version: String,
    val outputPath: String,
    val leftPath: String = "",
    val rightPath: String = "",
    val depthPath: String,
    val paramsPath: String,
    val logPath: String,
    val width: Int,
    val height: Int,
    val createdAt: Long,
) {
    val hasSplitEyes: Boolean
        get() = leftPath.isNotBlank() && rightPath.isNotBlank() && File(leftPath).exists() && File(rightPath).exists()

    val hasLegacySbs: Boolean
        get() = outputPath.isNotBlank() && File(outputPath).exists()
}

internal val IMAGE_CACHE_FILES = listOf(
    "left.jpg",
    "right.jpg",
    "left.webp",
    "right.webp",
    "vr_sbs.jpg",
    "export_sbs.avif",
    "export_sbs.jpg",
    "depth.png",
    "model_depth.png",
    "params.json",
    "job.log",
    "source_preview.jpg",
)

internal fun VrCacheEntry.cacheBytes(): Long {
    return listOf(outputPath, leftPath, rightPath, depthPath, paramsPath, logPath, File(File(paramsPath).parentFile, "model_depth.png").absolutePath)
        .filter { it.isNotBlank() }
        .sumOf { File(it).takeIf { file -> file.exists() }?.length() ?: 0L }
}

data class VrGenerationParams @JvmOverloads constructor(
    val depthModel: String = "depth_anything_v2.tflite",
    val outputMode: String = "SBS_PARALLEL",
    val depthScale: Float = 40f,
    val blurRadius: Int = 3,
    val fillRadius: Int = 10,
    val invertDepth: Boolean = false,
    val maxLongEdge: Int = 6000,
    val modelThreads: Int = 4,
    val useGpu: Boolean = false,
    val gpuTestMode: GpuTestMode = GpuTestMode.ACCURATE_UNSET,
    val forceGpuNoFallback: Boolean = false,
    val inpaintMode: String = "FOREGROUND_FILL",
    val quality: Int = 94,
    val edgeMode: DepthEdgeMode = DepthEdgeMode.LEGACY_V6,
    val depthUpscale: DepthUpscaleSettings = DepthUpscaleSettings(),
    val matchV10Default: Boolean = false,
    val resultVariant: String = "",
    val invertModelDepth: Boolean = false,
)

data class VideoGenerationParams(
    val vr: VrGenerationParams,
    val modelThreads: Int = 4,
    val useGpu: Boolean = false,
    val depthWorkers: Int = 2,
) {
    fun toVrParams(): VrGenerationParams {
        val gpu = useGpu || qnnModelConfig(vr.depthModel) != null
        return vr.copy(modelThreads = modelThreads, useGpu = gpu, forceGpuNoFallback = gpu)
    }
}

enum class AppLanguage {
    ZH,
    EN,
}

enum class GpuTestMode {
    AUTO,
    ACCURATE_UNSET,
    ACCURATE_OPENGL,
    ACCURATE_OPENCL,
    BEST_COMPAT,
}

data class AppSettings(
    val language: AppLanguage = AppLanguage.ZH,
    val imageModelId: String = DEFAULT_TFLITE_MODEL_ID,
    val videoModelId: String = DEFAULT_TFLITE_MODEL_ID,
    val autoPrefetch: Boolean = true,
    val prefetchWindow: Int = 2,
    val depthScale: Float = 40f,
    val blurRadius: Int = 3,
    val fillRadius: Int = 10,
    val invertDepth: Boolean = false,
    val maxLongEdge: Int = 6000,
    val depthResolution: Int = 518,
    val generationWorkers: Int = 1,
    val modelThreads: Int = 4,
    val useGpu: Boolean = false,
    val gpuTestMode: GpuTestMode = GpuTestMode.AUTO,
    val forceGpuNoFallback: Boolean = true,
    val videoModelThreads: Int = 4,
    val videoUseGpu: Boolean = false,
    val videoDepthWorkers: Int = 2,
    val edgeMode: DepthEdgeMode = DepthEdgeMode.LEGACY_V6,
    val depthUpscale: DepthUpscaleSettings = DepthUpscaleSettings(),
    val matchV10Default: Boolean = false,
    val invertModelDepth: Boolean = false,
    val imageExportFormat: String = "PNG",
) {
    private val imageQnn: Boolean get() = qnnModelConfig(imageModelId) != null
    private val imageGpu: Boolean get() = useGpu || imageQnn

    fun toParams(): VrGenerationParams = VrGenerationParams(
        depthModel = imageModelId,
        depthScale = depthScale,
        blurRadius = blurRadius,
        fillRadius = fillRadius,
        invertDepth = invertDepth,
        maxLongEdge = maxLongEdge,
        modelThreads = if (imageQnn) 1 else modelThreads,
        useGpu = imageGpu,
        gpuTestMode = if (imageQnn) GpuTestMode.AUTO else gpuTestMode,
        forceGpuNoFallback = imageGpu,
        edgeMode = edgeMode,
        depthUpscale = depthUpscale,
        matchV10Default = matchV10Default,
        invertModelDepth = invertModelDepth,
    )

    fun toVideoParams(): VideoGenerationParams {
        val qnn = qnnModelConfig(videoModelId) != null
        val gpu = videoUseGpu || qnn
        return VideoGenerationParams(
            vr = toParams().copy(depthModel = videoModelId, useGpu = gpu,
                gpuTestMode = if (qnn) GpuTestMode.AUTO else gpuTestMode, forceGpuNoFallback = gpu),
            modelThreads = if (qnn) 1 else videoModelThreads,
            useGpu = gpu,
            depthWorkers = if (qnn) 1 else videoDepthWorkers,
        )
    }
}

data class ModelSpec(
    val id: String,
    val displayName: String,
    val inputSize: Int,
    val fileName: String,
    val url: String,
    val mirrors: List<String> = emptyList(),
    val sha256: String,
    val downloadable: Boolean = true,
    val videoOnly: Boolean = false,
    val noteZh: String = "",
    val noteEn: String = "",
)

internal const val DEFAULT_TFLITE_MODEL_ID = "depth_anything_v2_small_tflite"
internal const val HIGH_TFLITE_MODEL_ID = "depth_anything_v2_small_770_v1"

// Earlier versions saved the stable TFLite ID even when the user kept the default.
// Resolve the saved choice before applying the active aspect preference migration.
internal fun preferredDepthModelId(savedId: String?, legacyId: String?, qnnAvailable: Boolean): String =
    activeDepthModelId(savedId?.takeIf { it.isNotBlank() } ?: legacyId?.takeIf { it.isNotBlank() }
        ?: if (qnnAvailable) QNN_FP16_MODEL_ID else DEFAULT_TFLITE_MODEL_ID)

internal val ImageModels = listOf(
    ModelSpec(
        id = DEFAULT_TFLITE_MODEL_ID,
        displayName = "Depth Anything V2 Small TFLite 518（稳定）",
        inputSize = 518,
        fileName = "depth_anything_v2.tflite",
        url = "https://github.com/7116-byte/ParallelVrGallery/releases/download/model-assets-v1/depth_anything_v2.tflite",
        mirrors = listOf(
            "https://gh-proxy.com/https://github.com/7116-byte/ParallelVrGallery/releases/download/model-assets-v1/depth_anything_v2.tflite",
            "https://ghproxy.net/https://github.com/7116-byte/ParallelVrGallery/releases/download/model-assets-v1/depth_anything_v2.tflite",
        ),
        sha256 = "B407F34F61750F31441E6F858A4BC48D8572F9EE5399FFD015CEE5FA1767083F",
    ),
    ModelSpec(
        id = HIGH_TFLITE_MODEL_ID,
        displayName = "Depth Anything V2 Small 770（高分辨率）",
        inputSize = 770,
        fileName = "depth_anything_v2_small_770_v1.tflite",
        url = "https://github.com/7116-byte/ParallelVrGalleryPro/releases/download/v1.6.00/depth_anything_v2_small_770_v1.tflite",
        sha256 = "60efba90dbd854f72b9e8140fe349edbdfa464439872510a3ddd154b5e7d0596",
        noteZh = "真实 770×770 输入与深度输出，约 101 MB。平板实测推理约为 518 的 4 倍耗时，首次 GPU 准备更慢；细节可能改善，不保证所有边缘更好。仅图片可选，原 518 保留。",
        noteEn = "Real 770×770 input/output, about 101 MB. Tested tablet inference takes about 4× the 518 time, with slower initial GPU setup. Detail may improve; edges are not universally better. Optional for images; 518 stays available.",
    ),
    ModelSpec(
        id = "qualcomm_depth_anything_v2_tflite",
        displayName = "Qualcomm Depth Anything V2 TFLite 518（实验）",
        inputSize = 518,
        fileName = "qualcomm_depth_anything_v2.tflite",
        url = "https://huggingface.co/qualcomm/Depth-Anything-V2/resolve/main/Depth-Anything-V2.tflite?download=true",
        mirrors = listOf(
            "https://hf-mirror.com/qualcomm/Depth-Anything-V2/resolve/main/Depth-Anything-V2.tflite?download=true",
        ),
        sha256 = "727E025EAB1DB3650C6FED86AA8D7932B994D8746E41A7A5773E663DE740859F",
    ),
) + QnnDepthVariant.entries.flatMap { variant ->
    (QNN_FP16_MIN_TOKENS..QNN_FP16_MAX_TOKENS).flatMap { count ->
        val size = count * QNN_FP16_PATCH_SIZE
        QnnDepthAspect.entries.map { aspect ->
            val dimensions = when (aspect) {
                QnnDepthAspect.SQUARE -> "$size"
                QnnDepthAspect.FIT -> "${size}² · padded"
                QnnDepthAspect.ALIGNED -> "${size}² · 14 px patches"
                QnnDepthAspect.WIDE_TALL, QnnDepthAspect.PORTRAIT_PAIR -> qnnFixedPairSizes(size, aspect)
            }
            ModelSpec(
                id = qnnModelId(QnnModelConfig(variant, size, aspect)),
                displayName = "Depth Anything V2 · QNN GPU ${variant.displayPrecision} · $dimensions",
                inputSize = size,
                fileName = variant.assetName.substringAfterLast('/'),
                url = variant.source,
                sha256 = variant.sha256,
            )
        }
    }
}

internal val VideoModels = ImageModels.filter { it.inputSize == 518 || hasQnnModelPrefix(it.id) } + listOf(
    ModelSpec(
        id = "video_depth_anything_small_pending",
        displayName = "Video Depth Anything Small（视频专用，待导入）",
        inputSize = 518,
        fileName = "video_depth_anything_small.tflite",
        url = "",
        sha256 = "",
        downloadable = false,
        videoOnly = true,
        noteZh = "官方视频时序深度模型；当前需要先导出移动端 TFLite/ONNX/MNN/QNN 资产后才能在手机本地运行。",
        noteEn = "Official temporally consistent video depth model. A mobile TFLite/ONNX/MNN/QNN asset is required before local Android inference.",
    ),
)

internal val AvailableModels = (ImageModels + VideoModels).distinctBy { it.id }

internal const val GENERATED_VR_PREFIX = "PVG_VR_"
internal const val INITIAL_MEDIA_LIMIT = 1800
internal const val ALBUM_PAGE_SIZE = 1200
internal const val ALL_PAGE_SIZE = 1200
internal const val IMAGE_CACHE_ENCODER_VERSION = "eyesJpegQ100V1"
internal const val IMAGE_EYE_JPEG_QUALITY = 100
internal const val VIDEO_ENCODER_VERSION = "encoderV15"
internal const val VIDEO_FRAME_JPEG_QUALITY = 100
internal const val VIDEO_TARGET_BITS_PER_PIXEL_FRAME = 0.10f
internal val READABLE_VIDEO_ENCODER_VERSIONS = setOf("encoderV12", "encoderV13", "encoderV14", VIDEO_ENCODER_VERSION)
internal val CURRENT_VERSION_TAG: String get() = "v${BuildConfig.VERSION_NAME}"
internal val CURRENT_VERSION_CODE: Long get() = BuildConfig.VERSION_CODE.toLong()
internal const val GITHUB_REPO = "7116-byte/ParallelVrGalleryPro"
internal const val MODEL_ASSET_REPO = "7116-byte/ParallelVrGallery"
internal const val UPDATE_APK_NAME = UpdatePolicy.APK_NAME

internal object AppWorkScopes {
    val video = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

internal fun modelSpec(id: String): ModelSpec = AvailableModels.firstOrNull { it.id == id } ?: ImageModels.first()

data class CacheVersionSummary(
    val version: String,
    val kind: String,
    val count: Int,
    val bytes: Long,
)

data class ManagedCacheItem(
    val photoItem: PhotoItem,
    val entry: VrCacheEntry,
) {
    val resultKey: Pair<String, String> get() = entry.photoKey to entry.version
}

data class VideoCacheEntry(
    val videoKey: String,
    val outputPath: String,
    val logPath: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val fps: Int,
    val createdAt: Long,
    val modelId: String = "",
    val cacheVersion: String = "",
    val depthGeometry: VideoDepthGeometry? = null,
)

data class VrJob(
    val photoItem: PhotoItem,
    val priority: Int,
    val state: VrState,
    val progress: Float,
    val source: QueueSource? = null,
    val albumId: String? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val error: String? = null,
)

data class VideoVrJob(
    val item: GalleryItem,
    val state: VideoVrState,
    val progress: Float,
    val currentFrame: Int = 0,
    val totalFrames: Int = 0,
    val fps: Int = 30,
    val currentFrameMs: Long = 0L,
    val avgFrameMs: Long = 0L,
    val frameLatencyMs: Long = 0L,
    val queueWaitMs: Long = 0L,
    val cacheHitReadMs: Long = 0L,
    val modelId: String = "",
    val cacheVersion: String = "",
    val modelThreads: Int = 0,
    val depthWorkers: Int = 0,
    val useGpu: Boolean = false,
    val runtimeInfo: String = "",
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val error: String? = null,
    val frameTimings: VideoFrameTimings = VideoFrameTimings(),
    val pipelineStats: VideoPipelineStats = VideoPipelineStats(),
)

data class VideoPipelineStats(
    val decodeQueue: Int = 0,
    val depthQueue: Int = 0,
    val generatedQueue: Int = 0,
    val waitingFrames: Int = 0,
    val cacheHits: Int = 0,
    val cacheWriteQueue: Int = 0,
    val lastCacheWriteMs: Long = 0L,
    val preparedInputQueue: Int = 0,
    val uprightDepthQueue: Int = 0,
)

data class VideoFrameTimings(
    val frameRead: VideoFrameReadTiming? = null,
    val cacheLookupMs: Long = 0L,
    val depthMs: Long = 0L,
    val temporalMs: Long = 0L,
    val depthPostMs: Long = 0L,
    val sbsMs: Long = 0L,
    val cacheWriteMs: Long = 0L,
    val encodeMs: Long = 0L,
    val depthGeometry: VideoDepthGeometry? = null,
    val inputTransformNanos: Long = 0L,
    val depthRotationNanos: Long = 0L,
) {
    val decodeMs: Long get() = frameRead?.decodeMs ?: 0L
    val totalMs: Long
        get() = decodeMs + (frameRead?.probeMs ?: 0L) + cacheLookupMs + depthMs + temporalMs + depthPostMs + sbsMs + cacheWriteMs + encodeMs
}

data class GenerationTimings(
    val decodeMs: Long = 0L,
    val modelMs: Long = 0L,
    val depthPostMs: Long = 0L,
    val sbsMs: Long = 0L,
    val writeLeftMs: Long = 0L,
    val writeRightMs: Long = 0L,
    val writeParamsMs: Long = 0L,
    val writeLogMs: Long = 0L,
    val writeMs: Long = 0L,
    val totalMs: Long = 0L,
)

data class VideoFrameMetrics(
    val throughputMs: Long = 0L,
    val latencyMs: Long = 0L,
    val queueWaitMs: Long = 0L,
    val cacheHitReadMs: Long = 0L,
    val cacheHit: Boolean = false,
)

data class LastGenerationInfo(
    val relativeIndex: Int,
    val elapsedMs: Long,
)

data class PrefetchSlotState(
    val centerKey: String? = null,
    val tier: Int = 2,
    val prevDone: Int = 0,
    val nextDone: Int = 0,
)

data class UiState(
    val hasPermission: Boolean = false,
    val hasVideoPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val photos: List<PhotoItem> = emptyList(),
    val comparison: ModeComparisonState? = null,
    val selectedIndex: Int? = null,
    val viewerOrigin: ViewerOrigin = ViewerOrigin.NORMAL,
    val manageViewerKeys: Set<String> = emptySet(),
    val viewerScopeKeys: Set<String> = emptySet(),
    val viewerScopeOrderedKeys: List<String> = emptyList(),
    val viewerGeneratedResults: List<Pair<String, String>>? = null,
    val viewerResultVersion: String? = null,
    val viewerQueueSource: String? = null,
    val viewerQueueAlbumId: String? = null,
    val galleryAnchorIndex: Int = 0,
    val galleryAnchorKey: String? = null,
    val settingsOpen: Boolean = false,
    val manageOpen: Boolean = false,
    val homeTab: String = "all",
    val generatedTab: String = "images",
    val selectedGeneratedVersion: String? = null,
    val additionalImageSources: List<String> = emptyList(),
    val allColumns: Int = 5,
    val albumListColumns: Int = 5,
    val albumDetailColumns: Int = 5,
    val generatedColumns: Int = 5,
    val gallerySortMode: GallerySortMode = GallerySortMode.TAKEN_TIME,
    val galleryViewMode: GalleryViewMode = GalleryViewMode.DATE_GROUPED,
    val galleryFilterMode: GalleryFilterMode = GalleryFilterMode.ALL,
    val expandedGeneratedVersions: Set<String> = emptySet(),
    val allOffset: Int = 0,
    val allLoading: Boolean = false,
    val allExhausted: Boolean = false,
    val selectedAlbumId: String? = null,
    val albumOffsets: Map<String, Int> = emptyMap(),
    val albumLoading: Boolean = false,
    val albums: List<AlbumItem> = emptyList(),
    val vrMode: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val activePrefetchWindow: Int = 2,
    val prefetchSlotState: PrefetchSlotState = PrefetchSlotState(),
    val viewerTravelDirection: Int = 0,
    val states: Map<String, VrState> = emptyMap(),
    val entries: Map<String, VrCacheEntry> = emptyMap(),
    val jobs: List<VrJob> = emptyList(),
    val logs: List<String> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
    val debugIndex: Int? = null,
    val modelProgress: Float? = null,
    val modelStatus: String = "模型未下载 / Model not downloaded",
    val modelWarmupStatus: String? = null,
    val blockingMessage: String? = null,
    val cacheVersions: List<CacheVersionSummary> = emptyList(),
    val managedCacheItems: List<ManagedCacheItem> = emptyList(),
    val videoStates: Map<String, VideoVrState> = emptyMap(),
    val videoEntries: Map<String, VideoCacheEntry> = emptyMap(),
    val videoJobs: List<VideoVrJob> = emptyList(),
    val lastGeneration: LastGenerationInfo? = null,
    val recentGenerations: List<LastGenerationInfo> = emptyList(),
    val updateStatus: String? = null,
    val updateUrl: String? = null,
    val updateVersion: String? = null,
    val updateAvailable: Boolean = false,
    val updateDownloading: Boolean = false,
    val updateDownloadProgress: Float = 0f,
    val updateApkPath: String? = null,
)

internal fun UiState.allGalleryAnchorIndex(): Int = galleryAnchorKey
    ?.let { key -> photos.indexOfFirst { it.cacheKey == key }.takeIf { it >= 0 } }
    ?: galleryAnchorIndex

internal fun UiState.viewerEntry(photo: PhotoItem): VrCacheEntry? {
    val version = viewerResultVersion ?: return entries[photo.cacheKey]
    return managedCacheItems.firstOrNull { it.entry.photoKey == photo.cacheKey && it.entry.version == version }?.entry
}

internal fun UiState.canWarmImageModel(visible: Boolean, queueRestored: Boolean): Boolean =
    visible && queueRestored && hasPermission && !loading && !allLoading && !albumLoading &&
        !settingsOpen && !manageOpen && selectedIndex == null && homeTab != "generated" &&
        blockingMessage == null && videoStates.values.none(VideoCommandPolicy::canPause) &&
        states.values.none { it == VrState.QUEUED || it == VrState.GENERATING }

internal data class MediaSnapshot(
    val photos: List<PhotoItem>,
    val imageCount: Int,
    val videoCount: Int,
    val albums: List<AlbumItem>,
    val entries: Map<String, VrCacheEntry>,
    val managedItems: List<ManagedCacheItem>,
    val videoEntries: Map<String, VideoCacheEntry>,
    val cacheVersions: List<CacheVersionSummary>,
)

internal data class UpdateCheckResult(
    val status: String,
    val url: String?,
    val version: String?,
    val available: Boolean,
)

internal data class ReleaseInfo(
    val tag: String,
    val apkUrl: String?,
    val pageUrl: String?,
    val source: String,
)

internal sealed class TimelineCell {
    data class Header(val day: Long, val label: String) : TimelineCell()
    data class Media(val item: PhotoItem) : TimelineCell()
    data class Footer(val loaded: Int, val total: Int, val text: String? = null) : TimelineCell()

    val gridKey: String get() = when (this) {
        is Header -> "header_$day"
        is Media -> item.cacheKey
        is Footer -> "footer_${loaded}_${total}_${text.orEmpty()}"
    }
}

enum class QueueSource {
    ALL,
    ALBUM,
    GENERATED,
}

enum class ViewerOrigin {
    NORMAL,
    GENERATED_TAB,
    MANAGE_MODAL,
}

internal data class QueueContext(val source: QueueSource, val albumId: String?)

internal data class QueueTag(
    val photoKey: String,
    val source: QueueSource,
    val albumId: String?,
    val priority: Int,
    val state: VrState,
    val updatedAt: Long,
    val params: VrGenerationParams? = null,
    val writeIntent: ImageWriteIntent? = null,
    val pauseReason: String? = null,
)

internal data class VideoQueueTag(
    val videoKey: String,
    val state: VideoVrState,
    val params: VideoGenerationParams?,
    val updatedAt: Long,
    val pauseReason: String? = null,
)


internal fun AppLanguage.t(zh: String, en: String): String = if (this == AppLanguage.ZH) zh else en

internal fun AppLanguage.pickMixed(text: String): String {
    val parenMarker = "）/ "
    val parenIdx = text.indexOf(parenMarker)
    if (parenIdx >= 0) {
        return if (this == AppLanguage.ZH) text.substring(0, parenIdx + 1) else text.substring(parenIdx + parenMarker.length)
    }
    val spacedParenMarker = "） / "
    val spacedParenIdx = text.indexOf(spacedParenMarker)
    if (spacedParenIdx >= 0) {
        return if (this == AppLanguage.ZH) text.substring(0, spacedParenIdx + 1) else text.substring(spacedParenIdx + spacedParenMarker.length)
    }
    val marker = " / "
    if (!text.contains(marker)) return text
    val parts = text.split(marker, limit = 2)
    return if (this == AppLanguage.ZH) parts.first() else parts.getOrElse(1) { parts.first() }
}

internal fun queuedVideoItemFromKey(videoKey: String): GalleryItem {
    val rawKey = videoKey.removePrefix("VIDEO_")
    val parts = rawKey.split("_")
    val mediaId = parts.getOrNull(0)?.toLongOrNull()
    val size = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    val modified = parts.getOrNull(2)?.toLongOrNull() ?: 0L
    val id = mediaId ?: abs(videoKey.hashCode()).toLong()
    return GalleryItem(
        id = id,
        uri = mediaId?.let { ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, it) } ?: Uri.EMPTY,
        displayName = mediaId?.let { "restored_video_$it.mp4" } ?: "restored_${videoKey.take(12)}.mp4",
        width = 0,
        height = 0,
        size = size,
        modifiedTime = modified,
        kind = MediaKind.VIDEO,
        forcedCacheKey = videoKey,
        generatedVirtual = true,
    )
}

internal fun VrState.label(lang: AppLanguage): String = when (this) {
    VrState.NORMAL -> lang.t("原图", "Normal")
    VrState.PAUSED -> lang.t("暂停", "Paused")
    VrState.QUEUED -> lang.t("队列中", "Queued")
    VrState.GENERATING -> lang.t("生成中", "Generating")
    VrState.READY -> lang.t("已生成", "Ready")
    VrState.FAILED -> lang.t("失败", "Failed")
}

internal fun VideoVrState.label(lang: AppLanguage): String = when (this) {
    VideoVrState.NORMAL -> lang.t("未生成", "Normal")
    VideoVrState.QUEUED -> lang.t("队列中", "Queued")
    VideoVrState.GENERATING -> lang.t("生成中", "Generating")
    VideoVrState.PAUSED -> lang.t("已暂停", "Paused")
    VideoVrState.READY -> lang.t("已生成", "Ready")
    VideoVrState.FAILED -> lang.t("失败", "Failed")
}

internal fun VideoVrState.shortLabel(lang: AppLanguage): String = when (this) {
    VideoVrState.NORMAL -> lang.t("未生成", "Normal")
    VideoVrState.QUEUED -> lang.t("队列", "Queued")
    VideoVrState.GENERATING -> lang.t("生成", "Gen")
    VideoVrState.PAUSED -> lang.t("暂停", "Paused")
    VideoVrState.READY -> lang.t("完成", "Ready")
    VideoVrState.FAILED -> lang.t("失败", "Failed")
}

private fun statusBadgeTextColor(text: String): androidx.compose.ui.graphics.Color = when {
    text.contains("失败", ignoreCase = true) || text.contains("Failed", ignoreCase = true) ->
        androidx.compose.ui.graphics.Color(0xffd32f2f)
    text.contains("已生成", ignoreCase = true) || text.contains("完成", ignoreCase = true) ||
        text.contains("Ready", ignoreCase = true) ->
        androidx.compose.ui.graphics.Color(0xff2e7d32)
    text.contains("队列", ignoreCase = true) || text.contains("生成", ignoreCase = true) ||
        text.contains("暂停", ignoreCase = true) || text.contains("Queued", ignoreCase = true) ||
        text.contains("Gen", ignoreCase = true) || text.contains("Generating", ignoreCase = true) ||
        text.contains("Paused", ignoreCase = true) ->
        androidx.compose.ui.graphics.Color(0xffffc107)
    else -> androidx.compose.ui.graphics.Color.White
}

internal fun viewerScopeIndices(state: UiState): List<Int> {
    return ViewerScope.indices(state.photos.map { it.cacheKey }, state.viewerScopeOrderedKeys, state.viewerScopeKeys)
}

internal fun relativeIndexInViewerScope(state: UiState, index: Int, selectedIndex: Int): Int {
    val scopeIndices = viewerScopeIndices(state)
    val itemPosition = scopeIndices.indexOf(index)
    val selectedPosition = scopeIndices.indexOf(selectedIndex)
    return if (itemPosition >= 0 && selectedPosition >= 0) itemPosition - selectedPosition else index - selectedIndex
}

private fun imageSideCount(state: UiState, index: Int, offsetStep: Int, states: Set<VrState>): Int {
    val scopeIndices = viewerScopeIndices(state)
    val startPosition = scopeIndices.indexOf(index)
    if (startPosition < 0) return 0
    var count = 0
    var cursor = startPosition + offsetStep
    while (cursor in scopeIndices.indices) {
        val item = state.photos[scopeIndices[cursor]]
        if (item.kind != MediaKind.IMAGE) {
            cursor += offsetStep
            continue
        }
        val itemState = state.states[item.cacheKey] ?: VrState.NORMAL
        if (itemState !in states) break
        count++
        cursor += offsetStep
    }
    return count
}

internal fun imageLoadedLine(state: UiState, index: Int, lang: AppLanguage): String {
    val prev = imageSideCount(state, index, -1, setOf(VrState.READY))
    val next = imageSideCount(state, index, 1, setOf(VrState.READY))
    return lang.t("已加载：前$prev 后$next", "Loaded: prev $prev next $next")
}

internal fun imageQueueLine(state: UiState, index: Int, lang: AppLanguage): String {
    val queueStates = setOf(VrState.QUEUED, VrState.GENERATING, VrState.PAUSED)
    val prev = imageWindowCount(state, index, -1, queueStates)
    val next = imageWindowCount(state, index, 1, queueStates)
    return lang.t("队列中：前$prev 后$next", "Queued: prev $prev next $next")
}

private fun imageWindowCount(state: UiState, index: Int, offsetStep: Int, states: Set<VrState>): Int {
    val scopeIndices = viewerScopeIndices(state)
    val startPosition = scopeIndices.indexOf(index)
    if (startPosition < 0) return 0
    var count = 0
    var seenImages = 0
    var cursor = startPosition + offsetStep
    val window = state.activePrefetchWindow.coerceAtMost(8)
    while (cursor in scopeIndices.indices && seenImages < window) {
        val item = state.photos[scopeIndices[cursor]]
        if (item.kind == MediaKind.IMAGE) {
            seenImages++
            val itemState = state.states[item.cacheKey] ?: VrState.NORMAL
            if (itemState in states) count++
        }
        cursor += offsetStep
    }
    return count
}

internal fun buildAlbums(items: List<GalleryItem>): List<AlbumItem> {
    return items.groupBy { item ->
        item.bucketId.ifBlank { item.relativePath.ifBlank { "bucket_${item.displayName.substringBeforeLast('.', item.displayName)}" } }
    }.mapNotNull { (bucketId, albumItems) ->
        val latest = albumItems.maxByOrNull { it.modifiedTime } ?: return@mapNotNull null
        AlbumItem(
            bucketId = bucketId,
            name = latest.bucketName.ifBlank {
                latest.relativePath.trim('/').substringAfterLast('/').ifBlank { "未命名相册" }
            },
            relativePath = latest.relativePath,
            coverUri = latest.uri,
            coverKind = latest.kind,
            count = albumItems.size,
            latestModified = latest.modifiedTime,
        )
    }.sortedByDescending { it.latestModified }
}

internal fun dateGroupLabel(modifiedTimeSeconds: Long, lang: AppLanguage): String {
    if (modifiedTimeSeconds <= 0L) return lang.t("未知日期", "Unknown date")
    val now = java.util.Calendar.getInstance()
    val target = java.util.Calendar.getInstance().apply { timeInMillis = modifiedTimeSeconds * 1000L }
    val todayYear = now.get(java.util.Calendar.YEAR)
    val todayDay = now.get(java.util.Calendar.DAY_OF_YEAR)
    val targetYear = target.get(java.util.Calendar.YEAR)
    val targetDay = target.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        todayYear == targetYear && todayDay == targetDay -> lang.t("今天", "Today")
        todayYear == targetYear && todayDay - targetDay == 1 -> lang.t("昨天", "Yesterday")
        lang == AppLanguage.ZH -> SimpleDateFormat("M月d日", Locale.CHINA).format(Date(modifiedTimeSeconds * 1000L))
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(modifiedTimeSeconds * 1000L))
    }
}

internal fun localEpochDay(modifiedTimeSeconds: Long): Long {
    if (modifiedTimeSeconds <= 0L) return Long.MIN_VALUE
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = modifiedTimeSeconds * 1000L }
    val year = calendar.get(java.util.Calendar.YEAR)
    val day = calendar.get(java.util.Calendar.DAY_OF_YEAR)
    return year.toLong() * 400L + day.toLong()
}

internal fun jsonStringValue(text: String, key: String): String? {
    return Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]*)\"")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.replace("\\\"", "\"")
}

internal fun jsonIntValue(text: String, key: String): Int? {
    return Regex("\"${Regex.escape(key)}\"\\s*:\\s*(\\d+)")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
}

internal fun jsonLongValue(text: String, key: String): Long? {
    return Regex("\"${Regex.escape(key)}\"\\s*:\\s*(\\d+)")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
}

internal fun imageRecentGenerationLines(state: UiState, lang: AppLanguage): List<String> {
    return state.recentGenerations.take(3).map {
        val side = when {
            it.relativeIndex < 0 -> lang.t("前${abs(it.relativeIndex)}", "prev ${abs(it.relativeIndex)}")
            it.relativeIndex > 0 -> lang.t("后${it.relativeIndex}", "next ${it.relativeIndex}")
            else -> lang.t("当前", "current")
        }
        lang.t("${side}已生成（${it.elapsedMs}ms）", "$side ready (${it.elapsedMs}ms)")
    }
}

private fun imagePrefetchSummary(state: UiState, index: Int, lang: AppLanguage): String {
    val readyPrev = imageSideCount(state, index, -1, setOf(VrState.READY))
    val readyNext = imageSideCount(state, index, 1, setOf(VrState.READY))
    val queuedPrev = imageSideCount(state, index, -1, setOf(VrState.QUEUED, VrState.GENERATING, VrState.PAUSED))
    val queuedNext = imageSideCount(state, index, 1, setOf(VrState.QUEUED, VrState.GENERATING, VrState.PAUSED))
    val last = state.lastGeneration?.let {
        val side = when {
            it.relativeIndex < 0 -> lang.t("前${abs(it.relativeIndex)}", "prev ${abs(it.relativeIndex)}")
            it.relativeIndex > 0 -> lang.t("后${it.relativeIndex}", "next ${it.relativeIndex}")
            else -> lang.t("当前", "current")
        }
        "  ${side}${lang.t("已生成", " ready")}（${it.elapsedMs}ms）"
    }.orEmpty()
    return lang.t(
        "已加载：前$readyPrev 后$readyNext  队列中：前$queuedPrev 后$queuedNext$last",
        "Ready: prev $readyPrev next $readyNext  Queued: prev $queuedPrev next $queuedNext$last",
    )
}
