package com.local.parallelvrgallerypro

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.provider.MediaStore
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.VideoView
import android.opengl.GLSurfaceView
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import com.radzivon.bartoshyk.avif.coder.PreciseMode
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.gpu.GpuDelegateFactory
import java.io.File
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    GalleryApp()
                }
            }
        }
    }
}

enum class MediaKind {
    IMAGE,
    VIDEO,
}

data class PhotoItem(
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
) {
    val cacheKey: String
        get() = forcedCacheKey ?: if (kind == MediaKind.IMAGE) "${id}_${size}_${modifiedTime}" else "VIDEO_${id}_${size}_${modifiedTime}"
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

private val IMAGE_CACHE_FILES = listOf(
    "left.webp",
    "right.webp",
    "vr_sbs.jpg",
    "export_sbs.avif",
    "export_sbs.jpg",
    "depth.png",
    "params.json",
    "job.log",
    "source_preview.jpg",
)

private fun VrCacheEntry.cacheBytes(): Long {
    return listOf(outputPath, leftPath, rightPath, depthPath, paramsPath, logPath)
        .filter { it.isNotBlank() }
        .sumOf { File(it).takeIf { file -> file.exists() }?.length() ?: 0L }
}

data class VrGenerationParams(
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
)

data class VideoGenerationParams(
    val vr: VrGenerationParams,
    val modelThreads: Int = 4,
    val useGpu: Boolean = false,
    val depthWorkers: Int = 2,
) {
    fun toVrParams(): VrGenerationParams = vr.copy(modelThreads = modelThreads, useGpu = useGpu, forceGpuNoFallback = useGpu)
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
    val imageModelId: String = "depth_anything_v2_small_tflite",
    val videoModelId: String = "depth_anything_v2_small_tflite",
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
) {
    fun toParams(): VrGenerationParams = VrGenerationParams(
        depthModel = imageModelId,
        depthScale = depthScale,
        blurRadius = blurRadius,
        fillRadius = fillRadius,
        invertDepth = invertDepth,
        maxLongEdge = maxLongEdge,
        modelThreads = modelThreads,
        useGpu = useGpu,
        gpuTestMode = gpuTestMode,
        forceGpuNoFallback = useGpu,
    )

    fun toVideoParams(): VideoGenerationParams = VideoGenerationParams(
        vr = toParams().copy(depthModel = videoModelId, useGpu = videoUseGpu, forceGpuNoFallback = videoUseGpu),
        modelThreads = videoModelThreads,
        useGpu = videoUseGpu,
        depthWorkers = videoDepthWorkers,
    )
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

private val ImageModels = listOf(
    ModelSpec(
        id = "depth_anything_v2_small_tflite",
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
)

private val VideoModels = ImageModels + listOf(
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

private val AvailableModels = (ImageModels + VideoModels).distinctBy { it.id }

private const val GENERATED_VR_PREFIX = "PVG_VR_"
private const val INITIAL_MEDIA_LIMIT = 1800
private const val ALBUM_PAGE_SIZE = 1200
private const val ALL_PAGE_SIZE = 1200
private const val IMAGE_GENERATOR_VERSION = "depthV6"
private const val VIDEO_ENCODER_VERSION = "encoderV12"
private val CURRENT_VERSION_TAG: String get() = "v${BuildConfig.VERSION_NAME}"
private val CURRENT_VERSION_CODE: Long get() = BuildConfig.VERSION_CODE.toLong()
private const val GITHUB_REPO = "7116-byte/ParallelVrGalleryPro"
private const val MODEL_ASSET_REPO = "7116-byte/ParallelVrGallery"
private const val UPDATE_APK_NAME = "app-debug.apk"

private object AppWorkScopes {
    val video = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

private fun modelSpec(id: String): ModelSpec = AvailableModels.firstOrNull { it.id == id } ?: ImageModels.first()

data class CacheVersionSummary(
    val version: String,
    val kind: String,
    val count: Int,
    val bytes: Long,
)

data class ManagedCacheItem(
    val photoItem: PhotoItem,
    val entry: VrCacheEntry,
)

data class VideoCacheEntry(
    val videoKey: String,
    val outputPath: String,
    val logPath: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val fps: Int,
    val createdAt: Long,
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
    val generatedQueue: Int = 0,
    val waitingFrames: Int = 0,
    val cacheHits: Int = 0,
)

data class VideoFrameTimings(
    val decodeMs: Long = 0L,
    val depthMs: Long = 0L,
    val temporalMs: Long = 0L,
    val depthPostMs: Long = 0L,
    val sbsMs: Long = 0L,
    val cacheWriteMs: Long = 0L,
    val encodeMs: Long = 0L,
) {
    val totalMs: Long
        get() = decodeMs + depthMs + temporalMs + depthPostMs + sbsMs + cacheWriteMs + encodeMs
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
    val selectedIndex: Int? = null,
    val viewerOrigin: ViewerOrigin = ViewerOrigin.NORMAL,
    val manageViewerKeys: Set<String> = emptySet(),
    val viewerScopeKeys: Set<String> = emptySet(),
    val viewerScopeOrderedKeys: List<String> = emptyList(),
    val viewerQueueSource: String? = null,
    val viewerQueueAlbumId: String? = null,
    val galleryAnchorIndex: Int = 0,
    val galleryScrollIndex: Int = 0,
    val galleryScrollOffset: Int = 0,
    val galleryAnchorSlot: Int = 0,
    val settingsOpen: Boolean = false,
    val manageOpen: Boolean = false,
    val homeTab: String = "all",
    val generatedTab: String = "images",
    val selectedGeneratedVersion: String? = null,
    val allColumns: Int = 5,
    val albumListColumns: Int = 5,
    val albumDetailColumns: Int = 5,
    val generatedColumns: Int = 5,
    val expandedGeneratedVersions: Set<String> = emptySet(),
    val generatedImageScrollIndex: Int = 0,
    val generatedImageScrollOffset: Int = 0,
    val generatedVersionScrollIndexes: Map<String, Int> = emptyMap(),
    val generatedVersionScrollOffsets: Map<String, Int> = emptyMap(),
    val generatedVideoScrollIndex: Int = 0,
    val generatedVideoScrollOffset: Int = 0,
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
    val states: Map<String, VrState> = emptyMap(),
    val entries: Map<String, VrCacheEntry> = emptyMap(),
    val jobs: List<VrJob> = emptyList(),
    val logs: List<String> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
    val debugIndex: Int? = null,
    val modelProgress: Float? = null,
    val modelStatus: String = "模型未下载 / Model not downloaded",
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

private data class MediaSnapshot(
    val photos: List<PhotoItem>,
    val imageCount: Int,
    val videoCount: Int,
    val albums: List<AlbumItem>,
    val entries: Map<String, VrCacheEntry>,
    val managedItems: List<ManagedCacheItem>,
    val videoEntries: Map<String, VideoCacheEntry>,
    val cacheVersions: List<CacheVersionSummary>,
)

private data class UpdateCheckResult(
    val status: String,
    val url: String?,
    val version: String?,
    val available: Boolean,
)

private data class ReleaseInfo(
    val tag: String,
    val apkUrl: String?,
    val pageUrl: String?,
    val source: String,
)

private sealed class TimelineCell {
    data class Header(val day: Long, val label: String) : TimelineCell()
    data class Media(val item: PhotoItem) : TimelineCell()
    data class Footer(val loaded: Int, val total: Int, val text: String? = null) : TimelineCell()
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

private data class QueueContext(val source: QueueSource, val albumId: String?)

private data class QueueTag(
    val photoKey: String,
    val source: QueueSource,
    val albumId: String?,
    val priority: Int,
    val state: VrState,
    val updatedAt: Long,
)

private data class VideoQueueTag(
    val videoKey: String,
    val state: VideoVrState,
    val params: VideoGenerationParams?,
    val updatedAt: Long,
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val repository = PhotoRepository(app)
    private val cache = VrCacheManager(app)
    private val videoCache = VideoVrCacheManager(app)
    private val videoNotifier = VideoGenerationNotifier(app)
    private val videoKeepAlive = VideoGenerationKeepAlive(app)
    private val modelManager = ModelManager(app)
    private val generator = VrGenerator(app, cache, modelManager)
    private val videoGenerator = VideoVrGenerator(app, videoCache, generator)
    private val settingsStore = SettingsStore(app)
    private val queueTags = QueueTagStore(app)
    private val videoQueueTags = VideoQueueTagStore(app)
    private val pending = PriorityQueue<QueuedJob>(compareBy<QueuedJob> { it.priority }.thenBy { it.sequence })
    private val currentPending = PriorityQueue<QueuedJob>(compareBy<QueuedJob> { it.priority }.thenBy { it.sequence })
    private val videoPending = PriorityQueue<VideoQueuedJob>(compareBy<VideoQueuedJob> { it.sequence })
    private val paused = linkedMapOf<String, QueuedJob>()
    private val autoPausedKeys = mutableSetOf<String>()
    private val sourcePausedKeys = mutableSetOf<String>()
    private val pausedVideos = mutableSetOf<String>()
    private val pausedVideoParams = mutableMapOf<String, VideoGenerationParams>()
    private val activeVideoParams = mutableMapOf<String, VideoGenerationParams>()
    private val videoRunTokens = mutableMapOf<String, Long>()
    private var sequence = 0L
    private var videoSequence = 0L
    private var restoredQueueTags = false
    private val workers = mutableListOf<Job>()
    private val videoWorkers = mutableListOf<Job>()
    private var currentWorker: Job? = null
    private var activeKey: String? = null

    private val _uiState = MutableStateFlow(
        UiState(
            hasPermission = hasImagePermission(app),
            hasVideoPermission = hasVideoPermission(app),
            hasNotificationPermission = hasNotificationPermission(app),
            settings = settingsStore.load(),
        ),
    )
    val uiState: StateFlow<UiState> = _uiState

    init {
        if (_uiState.value.hasPermission) {
            loadPhotos()
        }
    }

    override fun onCleared() {
        generator.close()
        super.onCleared()
    }

    fun onPermissionChanged(imageGranted: Boolean, videoGranted: Boolean, notificationGranted: Boolean) {
        _uiState.update { it.copy(hasPermission = imageGranted, hasVideoPermission = videoGranted, hasNotificationPermission = notificationGranted) }
        if (imageGranted) loadPhotos()
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = null) }
            val snapshot = withContext(Dispatchers.IO) {
                val systemPhotos = repository.loadMedia(INITIAL_MEDIA_LIMIT)
                val imageItems = systemPhotos.filter { it.kind == MediaKind.IMAGE }
                val videoItems = systemPhotos.filter { it.kind == MediaKind.VIDEO }
                val photoByKey = imageItems.associateBy { it.cacheKey }
                val videoByKey = videoItems.associateBy { it.cacheKey }
                val managedItems = cache.allEntriesByKey(photoByKey)
                val entries = managedItems
                    .groupBy { it.entry.photoKey }
                    .mapValues { (_, items) -> items.maxBy { it.entry.createdAt }.entry }
                val videoPairs = videoCache.allEntriesByKey(videoByKey)
                val videoEntries = videoPairs.associate { it.first.cacheKey to it.second }
                val virtualItems = (managedItems.map { it.photoItem } + videoPairs.map { it.first })
                    .filter { it.generatedVirtual }
                    .distinctBy { it.cacheKey }
                val photos = systemPhotos + virtualItems
                MediaSnapshot(
                    photos = photos,
                    imageCount = imageItems.size,
                    videoCount = videoItems.size,
                    albums = buildAlbums(systemPhotos),
                    entries = entries,
                    managedItems = managedItems,
                    videoEntries = videoEntries,
                    cacheVersions = cache.summaries(),
                )
            }
            _uiState.update {
                val readyVideoStates = snapshot.videoEntries.keys.associateWith { VideoVrState.READY }
                val activeVideoStates = it.videoStates.filterValues { state -> state != VideoVrState.NORMAL } - readyVideoStates.keys
                val snapshotKeys = snapshot.photos.map { item -> item.cacheKey }.toSet()
                val activeVideoItems = it.videoJobs
                    .filter { job -> job.state != VideoVrState.READY || job.item.cacheKey !in snapshot.videoEntries }
                    .map { job -> if (job.item.cacheKey in snapshotKeys) job.item else job.item.copy(generatedVirtual = true) }
                val mergedPhotos = (snapshot.photos + activeVideoItems)
                    .distinctBy { item -> item.cacheKey }
                it.copy(
                    photos = mergedPhotos,
                    albums = snapshot.albums,
                    entries = snapshot.entries,
                    states = snapshot.entries.keys.associateWith { VrState.READY },
                    videoStates = activeVideoStates + readyVideoStates,
                    videoEntries = snapshot.videoEntries,
                    allOffset = mergedPhotos.count { !it.generatedVirtual },
                    allExhausted = mergedPhotos.count { !it.generatedVirtual } < INITIAL_MEDIA_LIMIT,
                    loading = false,
                    message = "已加载最近 ${mergedPhotos.count { !it.generatedVirtual }} 项媒体，生成库 ${snapshot.managedItems.size + snapshot.videoEntries.size} 项 / Loaded recent media and generated cache",
                    cacheVersions = snapshot.cacheVersions,
                    managedCacheItems = snapshot.managedItems,
                    modelStatus = modelStatusText(it.settings),
                )
            }
            if (!restoredQueueTags) {
                restoredQueueTags = true
                withContext(Dispatchers.IO) {
                    restoreQueueTags()
                    restoreVideoQueueTags()
                }
            }
            viewModelScope.launch {
                val fullAlbums = withContext(Dispatchers.IO) { repository.loadAlbums() }
                _uiState.update { it.copy(albums = fullAlbums) }
            }
        }
    }

    fun loadMoreAllMedia() {
        val state = _uiState.value
        if (state.allLoading || state.allExhausted) return
        viewModelScope.launch {
            val offset = _uiState.value.allOffset
            _uiState.update { it.copy(allLoading = true) }
            val page = withContext(Dispatchers.IO) { repository.loadMedia(ALL_PAGE_SIZE, offset) }
            _uiState.update { current ->
                val virtualItems = current.photos.filter { it.generatedVirtual }
                val systemItems = (current.photos.filterNot { it.generatedVirtual } + page)
                    .distinctBy { it.cacheKey }
                    .sortedByDescending { it.modifiedTime }
                current.copy(
                    photos = systemItems + virtualItems,
                    allOffset = offset + page.size,
                    allLoading = false,
                    allExhausted = page.size < ALL_PAGE_SIZE,
                    message = current.settings.language.t("全部页已加载 ${systemItems.size} 项", "All page loaded ${systemItems.size} items"),
                )
            }
        }
    }

    fun openPhoto(index: Int, firstVisibleIndex: Int = index, firstVisibleOffset: Int = 0) {
        openPhoto(index, firstVisibleIndex, firstVisibleOffset, emptyList())
    }

    fun openScopedPhoto(index: Int, scopeKeys: List<String>, firstVisibleIndex: Int = index, firstVisibleOffset: Int = 0) {
        openPhoto(index, firstVisibleIndex, firstVisibleOffset, scopeKeys)
    }

    private fun openPhoto(index: Int, firstVisibleIndex: Int, firstVisibleOffset: Int, scopeKeys: List<String>) {
        val item = _uiState.value.photos.getOrNull(index)
        val queueContext = activeQueueContext(_uiState.value)
        _uiState.update {
            it.copy(
                selectedIndex = index,
                galleryAnchorIndex = index,
                galleryScrollIndex = firstVisibleIndex,
                galleryScrollOffset = firstVisibleOffset,
                galleryAnchorSlot = (index - firstVisibleIndex).coerceAtLeast(0),
                vrMode = true,
                viewerOrigin = ViewerOrigin.NORMAL,
                manageViewerKeys = emptySet(),
                viewerScopeKeys = scopeKeys.toSet(),
                viewerScopeOrderedKeys = scopeKeys,
                viewerQueueSource = queueContext.source.name,
                viewerQueueAlbumId = queueContext.albumId,
                message = null,
            )
        }
        if (item?.kind == MediaKind.IMAGE) enqueueWindow(index, includeCurrent = true)
    }

    fun openGeneratedPhoto(index: Int, entry: VrCacheEntry? = null) {
        val photo = _uiState.value.photos.getOrNull(index)
        _uiState.update {
            val scopedItems = if (entry != null) {
                it.managedCacheItems.filter { item -> item.entry.version == entry.version }
            } else {
                it.managedCacheItems
            }
            val keys = scopedItems.map { item -> item.photoItem.cacheKey }.toSet()
            val scopedEntries = scopedItems.associate { item -> item.photoItem.cacheKey to item.entry }
            val origin = if (it.manageOpen) ViewerOrigin.MANAGE_MODAL else ViewerOrigin.GENERATED_TAB
            it.copy(
                selectedIndex = index,
                galleryAnchorIndex = index,
                galleryScrollIndex = max(0, index - it.galleryAnchorSlot),
                manageOpen = false,
                homeTab = if (origin == ViewerOrigin.GENERATED_TAB) "generated" else it.homeTab,
                generatedTab = "images",
                selectedGeneratedVersion = entry?.version ?: it.selectedGeneratedVersion,
                viewerOrigin = origin,
                manageViewerKeys = keys + listOfNotNull(photo?.cacheKey),
                viewerScopeKeys = keys + listOfNotNull(photo?.cacheKey),
                viewerScopeOrderedKeys = scopedItems.map { item -> item.photoItem.cacheKey } + listOfNotNull(photo?.cacheKey),
                viewerQueueSource = QueueSource.GENERATED.name,
                viewerQueueAlbumId = null,
                entries = if (photo != null && entry != null) it.entries + scopedEntries + (photo.cacheKey to entry) else it.entries,
                message = null,
            )
        }
    }

    fun openGeneratedVideo(index: Int) {
        _uiState.update {
            val keys = it.photos.filter { item ->
                item.kind == MediaKind.VIDEO &&
                    ((it.videoStates[item.cacheKey] ?: VideoVrState.NORMAL) != VideoVrState.NORMAL ||
                        it.videoEntries.containsKey(item.cacheKey) ||
                        it.videoJobs.any { job -> job.item.cacheKey == item.cacheKey })
            }.map { item -> item.cacheKey }.toSet()
            val origin = if (it.manageOpen) ViewerOrigin.MANAGE_MODAL else ViewerOrigin.GENERATED_TAB
            it.copy(
                selectedIndex = index,
                galleryAnchorIndex = index,
                galleryScrollIndex = max(0, index - it.galleryAnchorSlot),
                manageOpen = false,
                homeTab = if (origin == ViewerOrigin.GENERATED_TAB) "generated" else it.homeTab,
                generatedTab = "videos",
                viewerOrigin = origin,
                manageViewerKeys = keys,
                viewerScopeKeys = keys,
                viewerScopeOrderedKeys = keys.toList(),
                viewerQueueSource = QueueSource.GENERATED.name,
                viewerQueueAlbumId = null,
                message = null,
            )
        }
    }

    fun closeViewer() {
        val wasNormalViewer = _uiState.value.viewerOrigin == ViewerOrigin.NORMAL
        if (wasNormalViewer) {
            clearQueuedImagePrefetch()
        }
        _uiState.update {
            val currentIndex = it.selectedIndex ?: it.galleryAnchorIndex
            val returnToManage = it.viewerOrigin == ViewerOrigin.MANAGE_MODAL
            val returnToGeneratedTab = it.viewerOrigin == ViewerOrigin.GENERATED_TAB
            it.copy(
                selectedIndex = null,
                debugIndex = null,
                manageOpen = returnToManage,
                homeTab = if (returnToGeneratedTab) "generated" else it.homeTab,
                viewerOrigin = ViewerOrigin.NORMAL,
                manageViewerKeys = emptySet(),
                viewerScopeKeys = emptySet(),
                viewerScopeOrderedKeys = emptyList(),
                viewerQueueSource = null,
                viewerQueueAlbumId = null,
                vrMode = if (wasNormalViewer) false else it.vrMode,
                prefetchSlotState = PrefetchSlotState(),
                galleryAnchorIndex = currentIndex,
            )
        }
        if (!wasNormalViewer) {
            rebalanceQueueForActiveSource()
            startWorker()
        }
    }

    fun setHomeTab(tab: String) {
        _uiState.update {
            it.copy(
                homeTab = tab,
                manageOpen = false,
                selectedGeneratedVersion = if (tab == "generated") it.selectedGeneratedVersion else null,
            )
        }
        rebalanceQueueForActiveSource()
        startWorker()
    }

    fun setGeneratedTab(tab: String) {
        _uiState.update { it.copy(generatedTab = tab, selectedGeneratedVersion = if (tab == "images") it.selectedGeneratedVersion else null) }
    }

    fun openGeneratedVersion(version: String) {
        _uiState.update { it.copy(generatedTab = "images", selectedGeneratedVersion = version) }
    }

    fun closeGeneratedVersion() {
        _uiState.update { it.copy(selectedGeneratedVersion = null) }
    }

    fun setPageColumns(page: String, columns: Int) {
        val safeColumns = columns.coerceIn(1, 8)
        _uiState.update {
            when (page) {
                "all" -> it.copy(allColumns = safeColumns)
                "albumList" -> it.copy(albumListColumns = safeColumns)
                "albumDetail" -> it.copy(albumDetailColumns = safeColumns)
                "generated" -> it.copy(generatedColumns = safeColumns)
                else -> it
            }
        }
    }

    fun setGeneratedScroll(tab: String, index: Int, offset: Int) {
        _uiState.update {
            when (tab) {
                "videos" -> it.copy(generatedVideoScrollIndex = index.coerceAtLeast(0), generatedVideoScrollOffset = offset.coerceAtLeast(0))
                else -> it.copy(generatedImageScrollIndex = index.coerceAtLeast(0), generatedImageScrollOffset = offset.coerceAtLeast(0))
            }
        }
    }

    fun setGeneratedVersionScroll(version: String, index: Int, offset: Int) {
        _uiState.update {
            it.copy(
                generatedVersionScrollIndexes = it.generatedVersionScrollIndexes + (version to index.coerceAtLeast(0)),
                generatedVersionScrollOffsets = it.generatedVersionScrollOffsets + (version to offset.coerceAtLeast(0)),
            )
        }
    }

    fun toggleGeneratedVersion(version: String) {
        _uiState.update {
            val expanded = it.expandedGeneratedVersions
            it.copy(expandedGeneratedVersions = if (version in expanded) expanded - version else expanded + version)
        }
    }

    fun openAlbum(bucketId: String) {
        _uiState.update { it.copy(homeTab = "albums", selectedAlbumId = bucketId, manageOpen = false) }
        loadAlbumPage(bucketId, reset = true)
        rebalanceQueueForActiveSource()
        startWorker()
    }

    fun closeAlbum() {
        _uiState.update { it.copy(selectedAlbumId = null) }
        rebalanceQueueForActiveSource()
    }

    fun loadMoreSelectedAlbum() {
        val state = _uiState.value
        val bucketId = state.selectedAlbumId ?: return
        if (state.albumLoading) return
        val album = state.albums.firstOrNull { it.bucketId == bucketId }
        val loaded = state.photos.count { !it.generatedVirtual && it.bucketId == bucketId }
        if (album != null && loaded >= album.count) return
        loadAlbumPage(bucketId, reset = false)
    }

    private fun loadAlbumPage(bucketId: String, reset: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value
            if (current.albumLoading && !reset) return@launch
            val offset = if (reset) 0 else current.albumOffsets[bucketId] ?: current.photos.count { !it.generatedVirtual && it.bucketId == bucketId }
            _uiState.update { it.copy(albumLoading = true) }
            val page = withContext(Dispatchers.IO) { repository.loadAlbumMedia(bucketId, ALBUM_PAGE_SIZE, offset) }
            _uiState.update { state ->
                val virtualItems = state.photos.filter { it.generatedVirtual }
                val otherSystemItems = if (reset) {
                    state.photos.filter { !it.generatedVirtual && it.bucketId != bucketId }
                } else {
                    state.photos.filterNot { it.generatedVirtual }
                }
                val mergedSystem = (otherSystemItems + page).distinctBy { it.cacheKey }.sortedByDescending { it.modifiedTime }
                state.copy(
                    photos = mergedSystem + virtualItems,
                    albumOffsets = state.albumOffsets + (bucketId to (offset + page.size)),
                    albumLoading = false,
                    message = state.settings.language.t("相册已加载 ${offset + page.size} 项", "Album loaded ${offset + page.size} items"),
                )
            }
        }
    }

    fun openSettings() {
        _uiState.update { it.copy(settingsOpen = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(settingsOpen = false) }
    }

    fun checkForUpdates() {
        val lang = _uiState.value.settings.language
        _uiState.update {
            it.copy(
                updateStatus = lang.t("正在检查更新...", "Checking for updates..."),
                updateUrl = null,
                updateVersion = null,
                updateAvailable = false,
                updateDownloading = false,
                updateDownloadProgress = 0f,
                updateApkPath = null,
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { fetchLatestReleaseInfo() }
                    .map { release ->
                        val url = release.apkUrl ?: release.pageUrl
                        if (compareVersionTags(release.tag, CURRENT_VERSION_TAG) <= 0) {
                            UpdateCheckResult(
                                lang.t("已是最新版本：$CURRENT_VERSION_TAG", "Already up to date: $CURRENT_VERSION_TAG"),
                                null,
                                release.tag,
                                false,
                            )
                        } else {
                            UpdateCheckResult(
                                lang.t(
                                    "发现新版本：${release.tag}，当前：$CURRENT_VERSION_TAG（${release.source}）",
                                    "New version: ${release.tag}, current: $CURRENT_VERSION_TAG (${release.source})",
                                ),
                                url,
                                release.tag,
                                url != null,
                            )
                        }
                    }.getOrElse { error ->
                        UpdateCheckResult(
                            lang.t(
                                "检查更新失败：${error.message}。可打开 Release 页面手动下载。",
                                "Update check failed: ${error.message}. Open Releases to download manually.",
                            ),
                            "https://github.com/$GITHUB_REPO/releases/latest",
                            null,
                            true,
                        )
                    }
            }
            _uiState.update {
                it.copy(
                    updateStatus = result.status,
                    updateUrl = result.url,
                    updateVersion = result.version,
                    updateAvailable = result.available,
                )
            }
        }
    }

    private fun fetchLatestReleaseInfo(): ReleaseInfo {
        val errors = mutableListOf<String>()
        for (apiUrl in updateApiUrls()) {
            runCatching {
                val body = httpGetText(apiUrl, accept = "application/vnd.github+json")
                val latest = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.getOrNull(1)
                    ?: error("missing tag_name")
                val downloadUrl = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]*$UPDATE_APK_NAME)\"")
                    .find(body)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.replace("\\/", "/")
                val pageUrl = Regex("\"html_url\"\\s*:\\s*\"([^\"]+)\"")
                    .find(body)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.replace("\\/", "/")
                return ReleaseInfo(latest, downloadUrl ?: releaseApkUrl(latest), pageUrl ?: releasePageUrl(latest), apiUrl.hostLabel())
            }.onFailure { error ->
                errors += "${apiUrl.hostLabel()}: ${error.shortMessage()}"
            }
        }

        for (latestUrl in updateLatestPageUrls()) {
            runCatching {
                val tag = resolveLatestReleaseTag(latestUrl)
                return ReleaseInfo(tag, releaseApkUrl(tag), releasePageUrl(tag), latestUrl.hostLabel())
            }.onFailure { error ->
                errors += "${latestUrl.hostLabel()}: ${error.shortMessage()}"
            }
        }

        error(errors.joinToString(" | ").ifBlank { "no update source available" })
    }

    private fun httpGetText(url: String, accept: String? = null): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ParallelVrGalleryPro/$CURRENT_VERSION_TAG")
            accept?.let { setRequestProperty("Accept", it) }
        }
        connection.connect()
        if (connection.responseCode !in 200..299) {
            val errorText = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(120) }.orEmpty()
            error("HTTP ${connection.responseCode} $errorText")
        }
        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun resolveLatestReleaseTag(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            requestMethod = "GET"
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "ParallelVrGalleryPro/$CURRENT_VERSION_TAG")
        }
        connection.connect()
        val location = connection.getHeaderField("Location").orEmpty()
        Regex("/releases/tag/([^/?#]+)").find(location)?.groupValues?.getOrNull(1)?.let { return it }
        val body = if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            ""
        }
        Regex("/$GITHUB_REPO/releases/tag/([^\"/?#]+)").find(body)?.groupValues?.getOrNull(1)?.let { return it }
        Regex("/releases/tag/([^\"/?#]+)").find(body)?.groupValues?.getOrNull(1)?.let { return it }
        error("missing latest tag")
    }

    private fun updateApiUrls(): List<String> {
        val api = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        return listOf(
            api,
            "https://gh-proxy.com/$api",
            "https://ghproxy.net/$api",
        )
    }

    private fun updateLatestPageUrls(): List<String> {
        val latest = "https://github.com/$GITHUB_REPO/releases/latest"
        return listOf(
            latest,
            "https://gh-proxy.com/$latest",
            "https://ghproxy.net/$latest",
        )
    }

    private fun releasePageUrl(tag: String): String = "https://github.com/$GITHUB_REPO/releases/tag/$tag"

    private fun releaseApkUrl(tag: String): String = "https://github.com/$GITHUB_REPO/releases/download/$tag/$UPDATE_APK_NAME"

    private fun updateDownloadUrls(url: String): List<String> {
        if (!url.contains("github.com/$GITHUB_REPO/releases/download/")) return listOf(url)
        return listOf(
            url,
            "https://gh-proxy.com/$url",
            "https://ghproxy.net/$url",
        ).distinct()
    }

    private fun String.hostLabel(): String = runCatching { URL(this).host }
        .getOrDefault(this.substringBefore('/'))
        .removePrefix("www.")

    private fun downloadFileWithProgress(urls: List<String>, output: File, lang: AppLanguage) {
        val errors = mutableListOf<String>()
        urls.forEachIndexed { sourceIndex, sourceUrl ->
            if (output.exists()) output.delete()
            runCatching {
                val connection = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "ParallelVrGalleryPro/$CURRENT_VERSION_TAG")
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("Pragma", "no-cache")
                }
                connection.connect()
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                val total = connection.contentLengthLong.coerceAtLeast(1L)
                var readTotal = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(output).use { fileOut ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            fileOut.write(buffer, 0, read)
                            readTotal += read
                            val sourceBase = sourceIndex.toFloat() / urls.size.toFloat()
                            val sourceSpan = 1f / urls.size.toFloat()
                            val progress = (sourceBase + (readTotal.toFloat() / total.toFloat()) * sourceSpan).coerceIn(0f, 1f)
                            _uiState.update {
                                it.copy(
                                    updateDownloadProgress = progress,
                                    updateStatus = lang.t(
                                        "正在下载更新 ${(progress * 100f).roundToInt()}%（${sourceUrl.hostLabel()}）",
                                        "Downloading update ${(progress * 100f).roundToInt()}% (${sourceUrl.hostLabel()})",
                                    ),
                                )
                            }
                        }
                    }
                }
                return
            }.onFailure { error ->
                errors += "${sourceUrl.hostLabel()}: ${error.shortMessage()}"
            }
        }
        if (output.exists()) output.delete()
        error(errors.joinToString(" | ").ifBlank { "all update download sources failed" })
    }

    private fun downloadedApkVersion(file: File): Pair<String, Long> {
        val info = app.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: error("无法读取安装包版本 / Cannot read APK version")
        if (info.packageName != app.packageName) {
            error("安装包应用 ID 不匹配：${info.packageName} / APK package mismatch")
        }
        val versionName = info.versionName?.takeIf { it.isNotBlank() }
            ?: error("安装包缺少版本号 / APK versionName missing")
        val versionCode = if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return "v$versionName" to versionCode
    }

    private fun validateDownloadedUpdate(file: File, expectedTag: String?, lang: AppLanguage) {
        val (downloadedTag, downloadedCode) = downloadedApkVersion(file)
        if (compareVersionTags(downloadedTag, CURRENT_VERSION_TAG) <= 0 || downloadedCode <= CURRENT_VERSION_CODE) {
            error(
                lang.t(
                    "下载到旧版安装包：$downloadedTag($downloadedCode)，当前：$CURRENT_VERSION_TAG($CURRENT_VERSION_CODE)，请重新检查更新。",
                    "Downloaded APK is not newer: $downloadedTag($downloadedCode), current: $CURRENT_VERSION_TAG($CURRENT_VERSION_CODE). Check again.",
                ),
            )
        }
        if (expectedTag != null && compareVersionTags(downloadedTag, expectedTag) < 0) {
            error(
                lang.t(
                    "下载版本不对：期望 $expectedTag，实际 $downloadedTag，请重新下载。",
                    "Downloaded wrong version: expected $expectedTag, got $downloadedTag. Download again.",
                ),
            )
        }
    }

    fun downloadUpdateApk() {
        val state = _uiState.value
        val lang = state.settings.language
        val url = state.updateUrl ?: return
        val version = state.updateVersion?.takeIf { it.isNotBlank() } ?: "latest"
        if (!url.endsWith(".apk", ignoreCase = true)) {
            _uiState.update {
                it.copy(
                    updateStatus = lang.t(
                        "当前网络只能打开 Release 页面，请在浏览器中下载 APK。",
                        "Only the Release page is available on this network. Download the APK in your browser.",
                    ),
                )
            }
            app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        _uiState.update {
            it.copy(
                updateDownloading = true,
                updateDownloadProgress = 0f,
                updateStatus = lang.t("正在下载更新 0%", "Downloading update 0%"),
                updateApkPath = null,
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val updatesDir = File(app.getExternalFilesDir(null), "updates").also { it.mkdirs() }
                    val output = File(updatesDir, "ParallelVrGalleryPro-$version.apk")
                    downloadFileWithProgress(updateDownloadUrls(url), output, lang)
                    validateDownloadedUpdate(output, state.updateVersion, lang)
                    output
                }
            }
            result.onSuccess { file ->
                _uiState.update {
                    it.copy(
                        updateDownloading = false,
                        updateDownloadProgress = 1f,
                        updateApkPath = file.absolutePath,
                        updateStatus = lang.t("更新下载完成，点击安装", "Update downloaded. Tap Install."),
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        updateDownloading = false,
                        updateStatus = lang.t("下载更新失败：${error.message}", "Update download failed: ${error.message}"),
                    )
                }
            }
        }
    }

    fun installDownloadedUpdate(context: Context) {
        val lang = _uiState.value.settings.language
        val path = _uiState.value.updateApkPath ?: return
        val file = File(path)
        if (!file.exists()) {
            _uiState.update { it.copy(updateStatus = lang.t("安装包不存在，请重新下载", "APK missing. Download again."), updateApkPath = null) }
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            _uiState.update { it.copy(updateStatus = lang.t("请允许本应用安装未知应用，然后返回点击安装", "Allow this app to install unknown apps, then return and tap Install.")) }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun updateSettings(settings: AppSettings) {
        settingsStore.save(settings)
        _uiState.update {
            it.copy(
                settings = settings,
                activePrefetchWindow = if (settings.autoPrefetch) 2 else settings.prefetchWindow,
                prefetchSlotState = PrefetchSlotState(),
                modelStatus = modelStatusText(settings),
            )
        }
        _uiState.value.selectedIndex?.let { refreshWindow(it) }
        startWorker()
    }

    fun onPagerIndexChanged(index: Int) {
        _uiState.update { it.copy(selectedIndex = index, galleryAnchorIndex = index, activePrefetchWindow = if (it.settings.autoPrefetch) 2 else it.settings.prefetchWindow, prefetchSlotState = PrefetchSlotState()) }
        val state = _uiState.value
        if (state.viewerOrigin != ViewerOrigin.NORMAL) return
        if (state.vrMode && state.photos.getOrNull(index)?.kind == MediaKind.IMAGE) {
            enqueueWindow(index, includeCurrent = true)
        }
    }

    fun requestVr(index: Int) {
        val item = _uiState.value.photos.getOrNull(index) ?: return
        if (item.kind == MediaKind.VIDEO) {
            when (_uiState.value.videoStates[item.cacheKey] ?: VideoVrState.NORMAL) {
                VideoVrState.QUEUED,
                VideoVrState.GENERATING -> pauseVideo(item)
                VideoVrState.PAUSED -> enqueueVideo(item, force = true)
                VideoVrState.FAILED -> enqueueVideo(item, force = true)
                else -> enqueueVideo(item)
            }
            return
        }
        if (_uiState.value.vrMode) {
            stopVr()
        } else {
            _uiState.update { it.copy(vrMode = true, selectedIndex = index, message = null, activePrefetchWindow = if (it.settings.autoPrefetch) 2 else it.settings.prefetchWindow, prefetchSlotState = PrefetchSlotState()) }
            enqueueWindow(index, includeCurrent = true)
        }
    }

    fun stopVr() {
        synchronized(pending) { pending.clear() }
        synchronized(currentPending) { currentPending.clear() }
        synchronized(paused) {
            paused.clear()
            autoPausedKeys.clear()
            sourcePausedKeys.clear()
        }
        queueTags.clearActive()
        _uiState.update { it.copy(vrMode = false, modelProgress = null, prefetchSlotState = PrefetchSlotState()) }
        addLog("vr stopped; generated caches kept")
    }

    private fun clearQueuedImagePrefetch() {
        val active = activeKey
        val queuedKeys = mutableSetOf<String>()
        synchronized(pending) {
            queuedKeys += pending.map { it.photo.cacheKey }
            pending.clear()
        }
        synchronized(currentPending) {
            queuedKeys += currentPending.map { it.photo.cacheKey }
            currentPending.clear()
        }
        synchronized(paused) {
            queuedKeys += paused.keys
            paused.clear()
            autoPausedKeys.clear()
            sourcePausedKeys.clear()
        }
        queuedKeys.remove(active)
        queuedKeys.forEach { queueTags.remove(it) }
        if (queuedKeys.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    states = it.states - queuedKeys,
                    jobs = it.jobs.filterNot { job -> job.photoItem.cacheKey in queuedKeys },
                )
            }
        }
        addLog("viewer closed; cleared queued prefetch=${queuedKeys.size}")
    }

    fun retry(index: Int) {
        val item = _uiState.value.photos.getOrNull(index) ?: return
        if (item.kind == MediaKind.VIDEO) {
            enqueueVideo(item, force = true)
        } else {
            enqueuePhoto(index, priority = 0, force = true, current = true)
            startCurrentWorker()
        }
    }

    fun exportDebug(context: Context, index: Int) {
        val photo = _uiState.value.photos.getOrNull(index) ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val entry = cache.findEntry(photo) ?: return@withContext null
                DebugExporter(context, cache).export(photo, entry)
            }
            if (result == null) {
                _uiState.update { it.copy(message = "当前图片还没有可导出的 VR 缓存 / No READY cache yet") }
            } else {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", result)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "导出调试包 / Export debug package"))
                _uiState.update { it.copy(message = "调试包已创建 / Debug package created: ${result.name}") }
            }
        }
    }

    fun openDebug(index: Int) {
        _uiState.update { it.copy(debugIndex = index) }
    }

    fun closeDebug() {
        _uiState.update { it.copy(debugIndex = null) }
    }

    fun openManage() {
        _uiState.update { it.copy(manageOpen = true, cacheVersions = cache.summaries()) }
    }

    fun closeManage() {
        _uiState.update { it.copy(manageOpen = false) }
    }

    fun deleteCacheVersion(version: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val affectedKeys = _uiState.value.managedCacheItems
                .filter { it.entry.version == version }
                .map { it.entry.photoKey }
                .toSet()
            cache.deleteVersion(version)
            val photos = _uiState.value.photos
            val entries = photos.mapNotNull { cache.findEntry(it) }.associateBy { it.photoKey }
            if (entries.isEmpty()) clearAllImageQueues() else clearImageQueueForKeys(affectedKeys)
            _uiState.update {
                it.copy(
                    entries = entries,
                    states = photos.associate { photo -> photo.cacheKey to if (entries.containsKey(photo.cacheKey)) VrState.READY else VrState.NORMAL },
                    cacheVersions = cache.summaries(),
                    managedCacheItems = cache.allEntries(photos),
                    message = "已删除版本 / Deleted version: $version",
                )
            }
        }
    }

    fun saveGeneratedCopy(context: Context, index: Int) {
        saveGeneratedCopies(context, listOf(index))
    }

    fun saveGeneratedCopies(context: Context, indexes: List<Int>) {
        val lang = _uiState.value.settings.language
        _uiState.update { it.copy(blockingMessage = lang.t("保存中", "Saving")) }
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                var saved = 0
                var failed = 0
                indexes.distinct().forEach { index ->
                    val photo = _uiState.value.photos.getOrNull(index) ?: return@forEach
                    val entry = cache.findEntry(photo) ?: return@forEach
                    runCatching {
                        val avif = materializeSbsAvif(entry)
                        saveImageToGallery(context, avif, "${GENERATED_VR_PREFIX}${photo.cacheKey}_${photo.displayName}", "image/avif")
                        saved++
                    }.onFailure { failed++ }
                }
                when {
                    saved == 0 -> lang.t("没有可保存的已生成 VR 图", "No generated VR images to save")
                    failed == 0 -> lang.t("已保存 $saved 张到系统图库", "Saved $saved images to system gallery")
                    else -> lang.t("已保存 $saved 张，失败 $failed 张", "Saved $saved images, failed $failed")
                }
            }
            _uiState.update { it.copy(blockingMessage = null, message = message) }
        }
    }

    fun saveGeneratedVideo(context: Context, index: Int) {
        saveGeneratedVideos(context, listOf(index))
    }

    fun saveGeneratedVideos(context: Context, indexes: List<Int>) {
        val lang = _uiState.value.settings.language
        _uiState.update { it.copy(blockingMessage = lang.t("保存中", "Saving")) }
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                var saved = 0
                var failed = 0
                indexes.distinct().forEach { index ->
                    val item = _uiState.value.photos.getOrNull(index) ?: return@forEach
                    val entry = _uiState.value.videoEntries[item.cacheKey] ?: videoCache.findEntry(item) ?: return@forEach
                    runCatching {
                        saveVideoToGallery(context, File(entry.outputPath), "${GENERATED_VR_PREFIX}${item.cacheKey}_${item.displayName}")
                        saved++
                    }.onFailure { failed++ }
                }
                when {
                    saved == 0 -> lang.t("没有可保存的已生成 VR 视频", "No generated VR videos to save")
                    failed == 0 -> lang.t("已保存 $saved 个视频到系统相册", "Saved $saved videos")
                    else -> lang.t("已保存 $saved 个视频，失败 $failed 个", "Saved $saved videos, failed $failed")
                }
            }
            _uiState.update { it.copy(blockingMessage = null, message = message) }
        }
    }

    fun deleteGeneratedVideo(index: Int) {
        deleteGeneratedVideos(listOf(index))
    }

    fun deleteGeneratedVideos(indexes: List<Int>) {
        val items = indexes.distinct().mapNotNull { _uiState.value.photos.getOrNull(it) }.filter { it.kind == MediaKind.VIDEO }
        viewModelScope.launch(Dispatchers.IO) {
            items.forEach {
                nextVideoRunToken(it.cacheKey)
                synchronized(videoPending) { videoPending.removeAll { job -> job.item.cacheKey == it.cacheKey } }
                videoCache.delete(it)
                videoQueueTags.remove(it.cacheKey)
            }
            _uiState.update {
                val keys = items.map { item -> item.cacheKey }.toSet()
                it.copy(
                    videoEntries = it.videoEntries - keys,
                    videoStates = it.videoStates + keys.associateWith { VideoVrState.NORMAL },
                    videoJobs = it.videoJobs.filterNot { job -> job.item.cacheKey in keys },
                    message = "已删除 ${items.size} 个视频 VR 缓存",
                )
            }
        }
    }

    fun deleteGeneratedImageEntries(entries: List<ManagedCacheItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            val deletedKeys = entries.map { it.entry.photoKey }.toSet()
            entries.forEach { cache.deleteEntry(it.entry) }
            val photos = _uiState.value.photos
            val currentEntries = photos.mapNotNull { cache.findEntry(it) }.associateBy { it.photoKey }
            if (currentEntries.isEmpty()) clearAllImageQueues() else clearImageQueueForKeys(deletedKeys)
            _uiState.update {
                it.copy(
                    entries = currentEntries,
                    states = photos.associate { photo -> photo.cacheKey to if (currentEntries.containsKey(photo.cacheKey)) VrState.READY else VrState.NORMAL },
                    cacheVersions = cache.summaries(),
                    managedCacheItems = cache.allEntries(photos),
                    message = "已删除 ${entries.size} 张图片 VR 缓存",
                )
            }
        }
    }

    private fun clearImageQueueForKeys(keys: Set<String>) {
        if (keys.isEmpty()) return
        synchronized(pending) { pending.removeAll { it.photo.cacheKey in keys } }
        synchronized(currentPending) { currentPending.removeAll { it.photo.cacheKey in keys } }
        synchronized(paused) {
            keys.forEach { paused.remove(it) }
            autoPausedKeys.removeAll(keys)
            sourcePausedKeys.removeAll(keys)
        }
        keys.forEach { queueTags.remove(it) }
        _uiState.update {
            it.copy(
                states = it.states - keys,
                jobs = it.jobs.filterNot { job -> job.photoItem.cacheKey in keys },
            )
        }
        addLog("cleared image queue keys=${keys.size}")
    }

    private fun clearAllImageQueues() {
        synchronized(pending) { pending.clear() }
        synchronized(currentPending) { currentPending.clear() }
        synchronized(paused) {
            paused.clear()
            autoPausedKeys.clear()
            sourcePausedKeys.clear()
        }
        queueTags.clearAll()
        _uiState.update {
            it.copy(
                states = emptyMap(),
                jobs = emptyList(),
                modelProgress = null,
            )
        }
        addLog("cleared all image queues")
    }

    fun regenerateImages(indexes: List<Int>) {
        val unique = indexes.distinct()
        if (unique.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val photos = unique.mapNotNull { _uiState.value.photos.getOrNull(it) }.filter { it.kind == MediaKind.IMAGE }
            photos.forEach { cache.deletePhoto(it) }
            _uiState.update {
                val keys = photos.map { photo -> photo.cacheKey }.toSet()
                it.copy(
                    vrMode = true,
                    entries = it.entries - keys,
                    states = it.states + keys.associateWith { VrState.NORMAL },
                    cacheVersions = cache.summaries(),
                    managedCacheItems = cache.allEntries(it.photos),
                    message = "已清理缓存并加入 ${photos.size} 张图片重新生成队列",
                )
            }
            unique.forEachIndexed { order, index ->
                enqueuePhoto(index, priority = order + 1, force = true, current = false, context = QueueContext(QueueSource.GENERATED, null))
            }
            startWorker()
        }
    }

    fun regenerateVideos(indexes: List<Int>) {
        val unique = indexes.distinct()
        viewModelScope.launch(Dispatchers.IO) {
            val videos = unique.mapNotNull { _uiState.value.photos.getOrNull(it) }.filter { it.kind == MediaKind.VIDEO }
            videos.forEach {
                nextVideoRunToken(it.cacheKey)
                synchronized(videoPending) { videoPending.removeAll { job -> job.item.cacheKey == it.cacheKey } }
                videoCache.delete(it)
                videoQueueTags.remove(it.cacheKey)
                synchronized(pausedVideoParams) {
                    pausedVideoParams.remove(it.cacheKey)
                    activeVideoParams.remove(it.cacheKey)
                }
                synchronized(pausedVideos) { pausedVideos.remove(it.cacheKey) }
            }
            _uiState.update {
                val keys = videos.map { video -> video.cacheKey }.toSet()
                it.copy(
                    videoEntries = it.videoEntries - keys,
                    videoStates = it.videoStates + keys.associateWith { VideoVrState.NORMAL },
                    videoJobs = it.videoJobs.filterNot { job -> job.item.cacheKey in keys },
                    message = "已清理缓存并加入 ${videos.size} 个视频重新生成队列",
                )
            }
            unique.forEach { index -> _uiState.value.photos.getOrNull(index)?.let { enqueueVideo(it, force = true) } }
        }
    }

    fun replaceOriginalWithGenerated(context: Context, index: Int) {
        replaceOriginalsWithGenerated(context, listOf(index))
    }

    fun replaceOriginalsWithGenerated(context: Context, indexes: List<Int>) {
        val lang = _uiState.value.settings.language
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                var replaced = 0
                var failed = 0
                indexes.distinct().forEach { index ->
                    val photo = _uiState.value.photos.getOrNull(index) ?: return@forEach
                    val entry = cache.findEntry(photo) ?: return@forEach
                    runCatching {
                        replaceOriginalImage(context, photo, materializeSbsJpeg(entry))
                        replaced++
                    }.onFailure { failed++ }
                }
                when {
                    replaced == 0 -> lang.t("替换失败：系统可能不允许写入这些原图", "Replace failed: Android may not allow writing these originals")
                    failed == 0 -> lang.t("已替换 $replaced 张并尝试保留原时间", "Replaced $replaced images and tried to keep timestamps")
                    else -> lang.t("已替换 $replaced 张，失败 $failed 张", "Replaced $replaced images, failed $failed")
                }
            }
            _uiState.update { it.copy(message = message) }
            loadPhotos()
        }
    }

    private fun refreshWindow(index: Int) {
        if (_uiState.value.viewerOrigin != ViewerOrigin.NORMAL) return
        enqueueWindow(index, includeCurrent = false)
    }

    private fun enqueueWindow(index: Int, includeCurrent: Boolean) {
        val state = _uiState.value
        val photos = state.photos
        if (!state.vrMode) return
        if (state.viewerOrigin != ViewerOrigin.NORMAL) return
        if (photos.isEmpty()) return
        val photoIndexByKey = photos.mapIndexed { photoIndex, item -> item.cacheKey to photoIndex }.toMap()
        val scopeIndices = if (state.viewerScopeOrderedKeys.isNotEmpty()) {
            state.viewerScopeOrderedKeys.mapNotNull { photoIndexByKey[it] }.distinct()
        } else if (state.viewerScopeKeys.isNotEmpty()) {
            photos.mapIndexedNotNull { photoIndex, item -> if (item.cacheKey in state.viewerScopeKeys) photoIndex else null }
        } else {
            photos.indices.toList()
        }
        val scopePosition = scopeIndices.indexOf(index)
        if (scopePosition < 0) return
        val currentVersion = state.settings.toParams().cacheVersion()
        val queueContext = activeQueueContext(state)
        val window = state.activePrefetchWindow.coerceAtMost(8)
        val desiredCount = window * 2
        val targets = mutableListOf<Pair<Int, Int>>()
        val targetKeys = mutableSetOf<String>()
        if (includeCurrent) {
            promoteCurrentImageForVr(index, queueContext)
        }
        val occupiedStates = setOf(VrState.QUEUED, VrState.GENERATING)
        fun eligibleAt(position: Int): Int? {
            val photoIndex = scopeIndices.getOrNull(position) ?: return null
            val photo = photos[photoIndex]
            if (photo.kind != MediaKind.IMAGE) return null
            if (photo.cacheKey in targetKeys) return null
            val itemState = state.states[photo.cacheKey] ?: VrState.NORMAL
            if (itemState in occupiedStates) return null
            if (itemState == VrState.PAUSED && synchronized(paused) { photo.cacheKey !in autoPausedKeys }) return null
            if (cache.findEntry(photo, currentVersion) != null) return null
            return photoIndex
        }
        fun stealFrom(offsetStep: Int, fromDistance: Int, toDistance: Int): Int? {
            var distance = fromDistance
            while (distance <= toDistance) {
                val candidate = eligibleAt(scopePosition + offsetStep * distance)
                if (candidate != null) return candidate
                distance++
            }
            return null
        }
        fun addTarget(photoIndex: Int): Boolean {
            if (targets.size >= desiredCount) return false
            val key = photos[photoIndex].cacheKey
            if (!targetKeys.add(key)) return false
            targets += photoIndex to targets.size + 1
            return true
        }
        fun fillAutoSlot(preferredOffsetStep: Int, distance: Int, fromDistance: Int, toDistance: Int) {
            val preferred = eligibleAt(scopePosition + preferredOffsetStep * distance)
            if (preferred != null) {
                addTarget(preferred)
            } else {
                stealFrom(-preferredOffsetStep, fromDistance, toDistance)?.let { addTarget(it) }
            }
        }
        if (state.settings.autoPrefetch) {
            val centerKey = photos[index].cacheKey
            val previousSlotState = state.prefetchSlotState.takeIf { it.centerKey == centerKey } ?: PrefetchSlotState(centerKey = centerKey)
            var slotState = previousSlotState
            val fromDistance = when (slotState.tier) {
                2 -> 1
                4 -> 3
                else -> 5
            }
            val toDistance = slotState.tier
            val tierSlots = toDistance - fromDistance + 1
            while ((slotState.prevDone < tierSlots || slotState.nextDone < tierSlots) && targets.size < desiredCount) {
                if (slotState.prevDone < tierSlots) {
                    val distance = fromDistance + slotState.prevDone
                    fillAutoSlot(preferredOffsetStep = -1, distance = distance, fromDistance = fromDistance, toDistance = toDistance)
                    slotState = slotState.copy(prevDone = slotState.prevDone + 1)
                }
                if (slotState.nextDone < tierSlots && targets.size < desiredCount) {
                    val distance = fromDistance + slotState.nextDone
                    fillAutoSlot(preferredOffsetStep = 1, distance = distance, fromDistance = fromDistance, toDistance = toDistance)
                    slotState = slotState.copy(nextDone = slotState.nextDone + 1)
                }
            }
            if (slotState.prevDone >= tierSlots && slotState.nextDone >= tierSlots) {
                slotState = when (slotState.tier) {
                    2 -> PrefetchSlotState(centerKey = centerKey, tier = 4)
                    4 -> PrefetchSlotState(centerKey = centerKey, tier = 8)
                    else -> slotState
                }
            }
            _uiState.update { it.copy(prefetchSlotState = slotState, activePrefetchWindow = slotState.tier) }
        } else {
            for (distance in 1..window) {
                eligibleAt(scopePosition - distance)?.let { addTarget(it) }
                eligibleAt(scopePosition + distance)?.let { addTarget(it) }
            }
        }

        synchronized(pending) {
            val keep = targets.map { photos[it.first].cacheKey }.toSet()
            val toPause = pending.filter { sameQueueSource(it, queueContext) && it.photo.cacheKey !in keep }
            pending.removeAll(toPause.toSet())
            toPause.forEach { job ->
                paused[job.photo.cacheKey] = job
                autoPausedKeys += job.photo.cacheKey
                queueTags.upsert(job, VrState.PAUSED)
                markState(job.photo.cacheKey, VrState.PAUSED)
                upsertJob(job.photo, job.priority, VrState.PAUSED, 0f, source = job.source, albumId = job.albumId)
            }
        }
        targets.forEach { (targetIndex, priority) -> enqueuePhoto(targetIndex, priority, force = false, current = false, context = queueContext) }
        rebalanceQueueForActiveSource()
        startWorker()
    }

    private fun promoteCurrentImageForVr(index: Int, context: QueueContext? = null) {
        val photo = _uiState.value.photos.getOrNull(index) ?: return
        if (photo.kind != MediaKind.IMAGE) return
        val currentVersion = _uiState.value.settings.toParams().cacheVersion()
        cache.findEntry(photo, currentVersion)?.let { entry ->
            markReady(photo.cacheKey, entry)
            queueTags.remove(photo.cacheKey)
            return
        }
        val queueContext = context ?: activeQueueContext(_uiState.value)
        val job = QueuedJob(photo, priority = 0, sequence = sequence++, source = queueContext.source, albumId = queueContext.albumId)
        synchronized(pending) { pending.removeAll { it.photo.cacheKey == photo.cacheKey } }
        synchronized(paused) {
            paused.remove(photo.cacheKey)
            autoPausedKeys.remove(photo.cacheKey)
            sourcePausedKeys.remove(photo.cacheKey)
        }
        synchronized(currentPending) {
            currentPending.removeAll { it.photo.cacheKey == photo.cacheKey }
            currentPending.add(job)
        }
        queueTags.upsert(job, VrState.QUEUED)
        markState(photo.cacheKey, VrState.QUEUED)
        upsertJob(photo, priority = 0, state = VrState.QUEUED, progress = 0f, source = job.source, albumId = job.albumId)
        addLog("current ${photo.displayName} p=0 source=${queueContext.source}${queueContext.albumId?.let { ":$it" } ?: ""}")
        startCurrentWorker()
    }

    private fun enqueuePhoto(index: Int, priority: Int, force: Boolean, current: Boolean = false, context: QueueContext? = null) {
        val photo = _uiState.value.photos.getOrNull(index) ?: return
        if (photo.kind != MediaKind.IMAGE) return
        val currentVersion = _uiState.value.settings.toParams().cacheVersion()
        if (!force && cache.findEntry(photo, currentVersion) != null) {
            markState(photo.cacheKey, VrState.READY)
            return
        }
        val existingState = _uiState.value.states[photo.cacheKey]
        if (!force && existingState == VrState.GENERATING) return
        if (!force && !current && existingState == VrState.QUEUED) return
        val resumableAutoPaused = !force && !current && existingState == VrState.PAUSED && synchronized(paused) { photo.cacheKey in autoPausedKeys && paused.containsKey(photo.cacheKey) }
        if (!force && !current && existingState == VrState.PAUSED && !resumableAutoPaused) return
        val queueContext = context ?: activeQueueContext(_uiState.value)
        if (current) {
            promoteCurrentImageForVr(index, queueContext)
            return
        }
        val job = if (resumableAutoPaused) {
            synchronized(paused) {
                val old = paused.remove(photo.cacheKey)
                autoPausedKeys.remove(photo.cacheKey)
                sourcePausedKeys.remove(photo.cacheKey)
                old?.copy(priority = priority, sequence = sequence++) ?: QueuedJob(photo, priority, sequence++, queueContext.source, queueContext.albumId)
            }
        } else {
            QueuedJob(photo, priority, sequence++, queueContext.source, queueContext.albumId)
        }
        synchronized(pending) { pending.removeAll { it.photo.cacheKey == photo.cacheKey } }
        if (!resumableAutoPaused) {
            synchronized(paused) {
                paused.remove(photo.cacheKey)
                autoPausedKeys.remove(photo.cacheKey)
                sourcePausedKeys.remove(photo.cacheKey)
            }
        }
        synchronized(pending) { pending.add(job) }
        queueTags.upsert(job, VrState.QUEUED)
        markState(photo.cacheKey, VrState.QUEUED)
        upsertJob(photo, priority, VrState.QUEUED, 0f, source = job.source, albumId = job.albumId)
        addLog("${if (current) "current" else "queued"} ${photo.displayName} p=$priority source=${queueContext.source}${queueContext.albumId?.let { ":$it" } ?: ""}")
    }

    private fun startWorker() {
        workers.removeAll { !it.isActive }
        val desired = _uiState.value.settings.generationWorkers.coerceIn(1, 3)
        repeat((desired - workers.size).coerceAtLeast(0)) {
            workers += viewModelScope.launch(Dispatchers.IO) {
                workerLoop()
            }
        }
    }

    private fun activeQueueContext(state: UiState): QueueContext {
        val viewerSource = state.viewerQueueSource?.let { runCatching { QueueSource.valueOf(it) }.getOrNull() }
        if (state.vrMode && state.viewerOrigin == ViewerOrigin.NORMAL && viewerSource != null) {
            return QueueContext(viewerSource, state.viewerQueueAlbumId)
        }
        return when {
            state.homeTab == "generated" || state.manageOpen -> QueueContext(QueueSource.GENERATED, null)
            state.homeTab == "albums" && state.selectedAlbumId != null -> QueueContext(QueueSource.ALBUM, state.selectedAlbumId)
            else -> QueueContext(QueueSource.ALL, null)
        }
    }

    private fun sameQueueSource(job: QueuedJob, context: QueueContext): Boolean {
        return job.source == context.source && (job.source != QueueSource.ALBUM || job.albumId == context.albumId)
    }

    private fun isGeneratedQueueSurface(state: UiState): Boolean {
        return state.homeTab == "generated" || state.manageOpen || (state.selectedIndex != null && state.viewerOrigin != ViewerOrigin.NORMAL)
    }

    private fun jobAllowedInCurrentContext(job: QueuedJob, state: UiState): Boolean {
        if (isGeneratedQueueSurface(state)) {
            return sameQueueSource(job, QueueContext(QueueSource.GENERATED, null))
        }
        if (state.vrMode && state.viewerOrigin == ViewerOrigin.NORMAL && state.viewerScopeOrderedKeys.isNotEmpty() && job.photo.cacheKey !in state.viewerScopeOrderedKeys) {
            return false
        }
        return sameQueueSource(job, activeQueueContext(state))
    }

    private fun pollAllowedPendingJob(): QueuedJob? {
        val state = _uiState.value
        return synchronized(pending) {
            val job = pending
                .sortedWith(compareBy<QueuedJob> { it.priority }.thenBy { it.sequence })
                .firstOrNull { jobAllowedInCurrentContext(it, state) }
            if (job != null) pending.remove(job)
            job
        }
    }

    private fun pollAllowedCurrentJob(): QueuedJob? {
        val state = _uiState.value
        val deferred = mutableListOf<QueuedJob>()
        val allowed = synchronized(currentPending) {
            var candidate = currentPending.poll()
            while (candidate != null && !jobAllowedInCurrentContext(candidate, state)) {
                deferred += candidate
                candidate = currentPending.poll()
            }
            candidate
        }
        if (deferred.isNotEmpty()) {
            synchronized(paused) {
                deferred.forEach { job ->
                    paused[job.photo.cacheKey] = job
                    autoPausedKeys += job.photo.cacheKey
                    sourcePausedKeys += job.photo.cacheKey
                    queueTags.upsert(job, VrState.PAUSED)
                    markState(job.photo.cacheKey, VrState.PAUSED)
                    upsertJob(job.photo, job.priority, VrState.PAUSED, 0f, source = job.source, albumId = job.albumId)
                }
            }
        }
        return allowed
    }

    private fun rebalanceQueueForActiveSource() {
        val state = _uiState.value
        val disallowed = synchronized(pending) {
            val items = pending.filterNot { jobAllowedInCurrentContext(it, state) }
            pending.removeAll(items.toSet())
            items
        }
        val disallowedCurrent = synchronized(currentPending) {
            val items = currentPending.filterNot { jobAllowedInCurrentContext(it, state) }
            currentPending.removeAll(items.toSet())
            items
        }
        val allDisallowed = disallowed + disallowedCurrent
        if (allDisallowed.isNotEmpty()) {
            synchronized(paused) {
                allDisallowed.forEach { job ->
                    paused[job.photo.cacheKey] = job
                    autoPausedKeys += job.photo.cacheKey
                    sourcePausedKeys += job.photo.cacheKey
                    queueTags.upsert(job, VrState.PAUSED)
                    markState(job.photo.cacheKey, VrState.PAUSED)
                    upsertJob(job.photo, job.priority, VrState.PAUSED, 0f, source = job.source, albumId = job.albumId)
                }
            }
        }
        val restored = synchronized(paused) {
            val requiredPausedKeys = if (isGeneratedQueueSurface(state)) sourcePausedKeys else autoPausedKeys
            val items = paused.values.filter { it.photo.cacheKey in requiredPausedKeys && jobAllowedInCurrentContext(it, state) }
            val keepAutoPaused = isGeneratedQueueSurface(state)
            items.forEach {
                paused.remove(it.photo.cacheKey)
                if (!keepAutoPaused) {
                    autoPausedKeys.remove(it.photo.cacheKey)
                    sourcePausedKeys.remove(it.photo.cacheKey)
                }
            }
            items
        }
        if (restored.isNotEmpty()) {
            synchronized(pending) { restored.forEach { pending.add(it) } }
            restored.forEach { job ->
                queueTags.upsert(job, VrState.QUEUED)
                markState(job.photo.cacheKey, VrState.QUEUED)
                upsertJob(job.photo, job.priority, VrState.QUEUED, 0f, source = job.source, albumId = job.albumId)
            }
        }
    }

    private fun restoreQueueTags() {
        val state = _uiState.value
        val photoByKey = state.photos.filter { it.kind == MediaKind.IMAGE }.associateBy { it.cacheKey }
        val restoredStates = mutableMapOf<String, VrState>()
        val pendingJobs = mutableListOf<QueuedJob>()
        val pausedJobs = mutableListOf<QueuedJob>()
        queueTags.load().forEach { tag ->
            val photo = photoByKey[tag.photoKey] ?: return@forEach
            if (cache.findEntry(photo, state.settings.toParams().cacheVersion()) != null) {
                queueTags.remove(tag.photoKey)
                restoredStates[tag.photoKey] = VrState.READY
                return@forEach
            }
            val restoredState = when (tag.state) {
                VrState.READY -> VrState.READY
                VrState.FAILED -> VrState.FAILED
                VrState.GENERATING -> VrState.QUEUED
                VrState.QUEUED -> VrState.QUEUED
                VrState.PAUSED -> VrState.PAUSED
                VrState.NORMAL -> VrState.QUEUED
            }
            restoredStates[tag.photoKey] = restoredState
            if (restoredState == VrState.FAILED || restoredState == VrState.READY) return@forEach
            val job = QueuedJob(photo, tag.priority, sequence++, tag.source, tag.albumId)
            if (jobAllowedInCurrentContext(job, state) && restoredState != VrState.PAUSED) {
                pendingJobs += job
            } else {
                pausedJobs += job
            }
        }
        synchronized(pending) { pendingJobs.forEach { pending.add(it) } }
        synchronized(paused) { pausedJobs.forEach { paused[it.photo.cacheKey] = it } }
        if (restoredStates.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    states = it.states + restoredStates,
                    message = it.settings.language.t("已恢复 ${restoredStates.size} 条生成标签", "Restored ${restoredStates.size} generation tags"),
                )
            }
            addLog("restored ${restoredStates.size} queue tags")
        }
        if (pendingJobs.isNotEmpty() && _uiState.value.vrMode) startWorker()
    }

    private fun restoreVideoQueueTags() {
        val state = _uiState.value
        val videoByKey = state.photos.filter { it.kind == MediaKind.VIDEO }.associateBy { it.cacheKey }
        val restoredStates = mutableMapOf<String, VideoVrState>()
        val restoredJobs = mutableListOf<VideoVrJob>()
        videoQueueTags.load().forEach { tag ->
            val item = videoByKey[tag.videoKey] ?: queuedVideoItemFromKey(tag.videoKey)
            val entry = videoCache.findEntry(item)
            if (entry != null) {
                videoQueueTags.remove(tag.videoKey)
                restoredStates[tag.videoKey] = VideoVrState.READY
                return@forEach
            }
            val restoredState = when (tag.state) {
                VideoVrState.READY -> VideoVrState.READY
                VideoVrState.FAILED -> VideoVrState.FAILED
                VideoVrState.NORMAL -> VideoVrState.PAUSED
                VideoVrState.QUEUED,
                VideoVrState.GENERATING,
                VideoVrState.PAUSED -> VideoVrState.PAUSED
            }
            restoredStates[tag.videoKey] = restoredState
            if (restoredState == VideoVrState.PAUSED) {
                synchronized(pausedVideos) { pausedVideos.add(tag.videoKey) }
                tag.params?.let { params ->
                    synchronized(pausedVideoParams) { pausedVideoParams[tag.videoKey] = params }
                }
            }
            if (restoredState != VideoVrState.READY) {
                restoredJobs += VideoVrJob(
                    item = item,
                    state = restoredState,
                    progress = if (restoredState == VideoVrState.FAILED) 1f else 0f,
                    modelId = tag.params?.vr?.depthModel.orEmpty(),
                    cacheVersion = tag.params?.cacheVersion().orEmpty(),
                    modelThreads = tag.params?.modelThreads ?: 0,
                    depthWorkers = tag.params?.depthWorkers ?: 0,
                    useGpu = tag.params?.useGpu ?: false,
                    startedAt = null,
                    finishedAt = null,
                    error = if (restoredState == VideoVrState.FAILED) "上次生成失败" else null,
                )
            }
        }
        if (restoredStates.isNotEmpty()) {
            _uiState.update {
                val restoredItems = restoredJobs.map { job -> job.item }
                it.copy(
                    photos = (it.photos + restoredItems).distinctBy { item -> item.cacheKey },
                    videoStates = it.videoStates + restoredStates,
                    videoJobs = (restoredJobs + it.videoJobs).distinctBy { job -> job.item.cacheKey }.take(100),
                    message = it.settings.language.t("已恢复 ${restoredStates.size} 条视频生成标签", "Restored ${restoredStates.size} video generation tags"),
                )
            }
            addLog("restored ${restoredStates.size} video queue tags")
        }
    }

    private fun startCurrentWorker() {
        if (currentWorker?.isActive == true) return
        currentWorker = viewModelScope.launch(Dispatchers.IO) {
            var idleChecks = 0
            while (true) {
                if (!_uiState.value.vrMode) {
                    synchronized(currentPending) { currentPending.clear() }
                    break
                }
                val next = pollAllowedCurrentJob()
                if (next == null) {
                    if (idleChecks++ < 3) {
                        delay(50)
                        continue
                    }
                    break
                }
                idleChecks = 0
                processJob(next)
                delay(25)
            }
        }
    }

    private fun enqueueVideo(item: GalleryItem, force: Boolean = false) {
        if (item.kind != MediaKind.VIDEO) return
        val params = synchronized(pausedVideoParams) {
            if (force) pausedVideoParams.remove(item.cacheKey) else null
        } ?: _uiState.value.settings.toVideoParams()
        synchronized(pausedVideos) { pausedVideos.remove(item.cacheKey) }
        if (!force && videoCache.findEntry(item) != null) {
            videoQueueTags.remove(item.cacheKey)
            _uiState.update { it.copy(videoStates = it.videoStates + (item.cacheKey to VideoVrState.READY)) }
            return
        }
        val existing = _uiState.value.videoStates[item.cacheKey]
        if (!force && (existing == VideoVrState.QUEUED || existing == VideoVrState.GENERATING)) return
        synchronized(videoPending) {
            videoPending.removeAll { it.item.cacheKey == item.cacheKey }
            val runToken = nextVideoRunToken(item.cacheKey)
            videoPending.add(VideoQueuedJob(item, params, videoSequence++, runToken))
        }
        upsertVideoJob(item, VideoVrState.QUEUED, 0f)
        videoQueueTags.upsert(item.cacheKey, VideoVrState.QUEUED, params)
        _uiState.update { it.copy(videoStates = it.videoStates + (item.cacheKey to VideoVrState.QUEUED), message = "视频已加入 VR 队列：${item.displayName}") }
        addLog("video queued ${item.displayName}")
        startVideoWorkers()
    }

    private fun pauseVideo(item: GalleryItem) {
        if (item.kind != MediaKind.VIDEO) return
        synchronized(videoPending) {
            videoPending.removeAll { it.item.cacheKey == item.cacheKey }
        }
        synchronized(pausedVideos) { pausedVideos.add(item.cacheKey) }
        nextVideoRunToken(item.cacheKey)
        val paramsSnapshot = synchronized(pausedVideoParams) {
            activeVideoParams[item.cacheKey]?.let { pausedVideoParams[item.cacheKey] = it }
            pausedVideoParams[item.cacheKey]
        }
        _uiState.update {
            it.copy(
                videoStates = it.videoStates + (item.cacheKey to VideoVrState.PAUSED),
                message = "视频已暂停生成：${item.displayName}",
            )
        }
        videoQueueTags.upsert(item.cacheKey, VideoVrState.PAUSED, paramsSnapshot)
        upsertVideoJob(item, VideoVrState.PAUSED, _uiState.value.videoJobs.firstOrNull { it.item.cacheKey == item.cacheKey }?.progress ?: 0f)
        addLog("video paused ${item.displayName}")
    }

    private fun startVideoWorkers() {
        videoWorkers.removeAll { !it.isActive }
        repeat((1 - videoWorkers.size).coerceAtLeast(0)) {
            videoWorkers += AppWorkScopes.video.launch {
                while (true) {
                    val next = synchronized(videoPending) { videoPending.poll() } ?: break
                    processVideoJob(next)
                    delay(100)
                }
            }
        }
    }

    private fun processVideoJob(next: VideoQueuedJob) {
        if (!isCurrentVideoRun(next.item.cacheKey, next.runToken)) {
            addLog("video stale queued job ignored ${next.item.displayName}")
            return
        }
        synchronized(pausedVideoParams) { activeVideoParams[next.item.cacheKey] = next.params }
        val vrParams = next.params.toVrParams()
        val videoCacheVersion = next.params.cacheVersion()
        var lastRuntimeInfo = "pending"
        _uiState.update { it.copy(videoStates = it.videoStates + (next.item.cacheKey to VideoVrState.GENERATING)) }
        videoQueueTags.upsert(next.item.cacheKey, VideoVrState.GENERATING, next.params)
        upsertVideoJob(next.item, VideoVrState.GENERATING, 0.01f, modelId = vrParams.depthModel, cacheVersion = videoCacheVersion, modelThreads = vrParams.modelThreads, depthWorkers = next.params.depthWorkers, useGpu = vrParams.useGpu, runtimeInfo = lastRuntimeInfo)
        videoNotifier.show(next.item, 0, 0, indeterminate = true, status = "准备生成")
        val started = SystemClock.uptimeMillis()
        var totalFrameTimeMs = 0L
        var measuredFrameCount = 0
        videoKeepAlive.acquire()
        val result = try {
            runCatching {
                videoGenerator.generate(
                    next.item,
                    next.params,
                    onModelProgress = { progress ->
                        _uiState.update {
                            it.copy(
                                modelProgress = progress,
                                modelStatus = if (progress < 1f) "正在下载模型 / Downloading model ${(progress * 100f).roundToInt()}%" else "模型已就绪 / Model ready",
                            )
                        }
                    },
                    onRuntimeInfo = { runtime ->
                        if (runtime != lastRuntimeInfo && runtime.startsWith("tflite")) {
                            addLog("video runtime ${next.item.displayName}: $runtime")
                        }
                        lastRuntimeInfo = runtime
                        upsertVideoJob(next.item, VideoVrState.GENERATING, _uiState.value.videoJobs.firstOrNull { it.item.cacheKey == next.item.cacheKey }?.progress ?: 0f, modelId = vrParams.depthModel, cacheVersion = videoCacheVersion, modelThreads = vrParams.modelThreads, depthWorkers = next.params.depthWorkers, useGpu = vrParams.useGpu, runtimeInfo = runtime)
                    },
                ) { progress, frame, total, fps, currentFrameMs, frameTimings, pipelineStats ->
                    if (!isCurrentVideoRun(next.item.cacheKey, next.runToken)) {
                        throw VideoStaleException()
                    }
                    if (synchronized(pausedVideos) { pausedVideos.contains(next.item.cacheKey) }) {
                        throw VideoPausedException()
                    }
                    if (currentFrameMs > 0L) {
                        totalFrameTimeMs += currentFrameMs
                        measuredFrameCount++
                    }
                    val avgFrameMs = if (measuredFrameCount > 0) totalFrameTimeMs / measuredFrameCount else 0L
                    upsertVideoJob(next.item, VideoVrState.GENERATING, progress, frame, total, fps, currentFrameMs = currentFrameMs, avgFrameMs = avgFrameMs, modelId = vrParams.depthModel, cacheVersion = videoCacheVersion, modelThreads = vrParams.modelThreads, depthWorkers = next.params.depthWorkers, useGpu = vrParams.useGpu, runtimeInfo = lastRuntimeInfo, frameTimings = frameTimings, pipelineStats = pipelineStats)
                    videoNotifier.show(next.item, frame, total, indeterminate = false, status = "生成中")
                }
            }
        } finally {
            videoKeepAlive.release()
        }
        if (!isCurrentVideoRun(next.item.cacheKey, next.runToken)) {
            addLog("video stale result ignored ${next.item.displayName}")
            return
        }
        result.onSuccess { entry ->
            val elapsed = SystemClock.uptimeMillis() - started
            _uiState.update {
                it.copy(
                    videoStates = it.videoStates + (next.item.cacheKey to VideoVrState.READY),
                    videoEntries = it.videoEntries + (next.item.cacheKey to entry),
                    modelProgress = null,
                    modelStatus = modelStatusText(it.settings),
                    message = "视频 VR 已生成：${next.item.displayName}（${elapsed}ms）",
                )
            }
            refreshGeneratedLibrary()
            upsertVideoJob(next.item, VideoVrState.READY, 1f, currentFrameMs = _uiState.value.videoJobs.firstOrNull { it.item.cacheKey == next.item.cacheKey }?.currentFrameMs ?: 0L, avgFrameMs = _uiState.value.videoJobs.firstOrNull { it.item.cacheKey == next.item.cacheKey }?.avgFrameMs ?: 0L, modelId = vrParams.depthModel, cacheVersion = videoCacheVersion, modelThreads = vrParams.modelThreads, depthWorkers = next.params.depthWorkers, useGpu = vrParams.useGpu, runtimeInfo = lastRuntimeInfo, finishedAt = System.currentTimeMillis())
            videoQueueTags.remove(next.item.cacheKey)
            synchronized(pausedVideoParams) {
                pausedVideoParams.remove(next.item.cacheKey)
                activeVideoParams.remove(next.item.cacheKey)
            }
            videoNotifier.show(next.item, 1, 1, indeterminate = false, status = "已完成")
            addLog("video ready ${next.item.displayName} ${entry.width}x${entry.height} ${elapsed}ms")
        }.onFailure { error ->
            if (error is VideoStaleException) {
                addLog("video stale failure ignored ${next.item.displayName}")
            } else if (error is VideoPausedException) {
                _uiState.update { it.copy(videoStates = it.videoStates + (next.item.cacheKey to VideoVrState.PAUSED), modelProgress = null) }
                videoQueueTags.upsert(next.item.cacheKey, VideoVrState.PAUSED, next.params)
                synchronized(pausedVideoParams) {
                    pausedVideoParams[next.item.cacheKey] = next.params
                    activeVideoParams.remove(next.item.cacheKey)
                }
                upsertVideoJob(next.item, VideoVrState.PAUSED, _uiState.value.videoJobs.firstOrNull { it.item.cacheKey == next.item.cacheKey }?.progress ?: 0f, modelId = vrParams.depthModel, cacheVersion = videoCacheVersion, modelThreads = vrParams.modelThreads, depthWorkers = next.params.depthWorkers, useGpu = vrParams.useGpu, runtimeInfo = lastRuntimeInfo)
                addLog("video paused ${next.item.displayName}")
            } else {
                _uiState.update { it.copy(videoStates = it.videoStates + (next.item.cacheKey to VideoVrState.FAILED), modelProgress = null) }
                videoQueueTags.upsert(next.item.cacheKey, VideoVrState.FAILED, next.params)
                synchronized(pausedVideoParams) { activeVideoParams.remove(next.item.cacheKey) }
                upsertVideoJob(next.item, VideoVrState.FAILED, 1f, modelId = vrParams.depthModel, cacheVersion = videoCacheVersion, modelThreads = vrParams.modelThreads, depthWorkers = next.params.depthWorkers, useGpu = vrParams.useGpu, runtimeInfo = lastRuntimeInfo, finishedAt = System.currentTimeMillis(), error = error.message)
                videoNotifier.failed(next.item, error.message ?: "生成失败")
                addLog("video failed ${next.item.displayName}: ${error.message}")
            }
        }
    }

    private suspend fun workerLoop() {
            while (true) {
                if (!_uiState.value.vrMode) {
                    synchronized(pending) { pending.clear() }
                    break
                }
                rebalanceQueueForActiveSource()
                val next = pollAllowedPendingJob()
                if (next == null) {
                    if (expandAutoPrefetchIfNeeded()) {
                        delay(50)
                        continue
                    }
                    break
                }
                processJob(next)
                delay(50)
            }
    }

    private fun processJob(next: QueuedJob) {
        activeKey = next.photo.cacheKey
        queueTags.upsert(next, VrState.GENERATING)
        markState(next.photo.cacheKey, VrState.GENERATING)
        upsertJob(next.photo, next.priority, VrState.GENERATING, 0.1f, source = next.source, albumId = next.albumId)
        val started = System.currentTimeMillis()
        val result = runCatching {
            generator.generate(
                next.photo,
                _uiState.value.settings.toParams(),
                onModelProgress = { progress ->
                    _uiState.update {
                        it.copy(
                            modelProgress = progress,
                            modelStatus = if (progress < 1f) {
                                "正在下载模型 / Downloading model ${(progress * 100f).roundToInt()}%"
                            } else {
                                "模型已就绪 / Model ready"
                            },
                        )
                    }
                },
            ) { progress ->
                upsertJob(next.photo, next.priority, VrState.GENERATING, progress, source = next.source, albumId = next.albumId)
            }
        }
        result.onSuccess { entry ->
            queueTags.remove(next.photo.cacheKey)
            synchronized(paused) { autoPausedKeys.remove(next.photo.cacheKey) }
            markReady(next.photo.cacheKey, entry)
            upsertJob(next.photo, next.priority, VrState.READY, 1f, finishedAt = System.currentTimeMillis(), source = next.source, albumId = next.albumId)
            addLog("ready ${next.photo.displayName} ${entry.width}x${entry.height}")
            val currentState = _uiState.value
            val selected = currentState.selectedIndex ?: currentState.galleryAnchorIndex
            val doneIndex = currentState.photos.indexOfFirst { it.cacheKey == next.photo.cacheKey }
            val generationInfo = LastGenerationInfo(relativeIndexInViewerScope(currentState, doneIndex, selected), System.currentTimeMillis() - started)
            _uiState.update {
                it.copy(
                    modelProgress = null,
                    modelStatus = modelStatusText(it.settings),
                    cacheVersions = cache.summaries(),
                    lastGeneration = generationInfo,
                    recentGenerations = (listOf(generationInfo) + it.recentGenerations).take(3),
                )
            }
        }.onFailure { error ->
            synchronized(paused) { autoPausedKeys.remove(next.photo.cacheKey) }
            queueTags.upsert(next, VrState.FAILED)
            markState(next.photo.cacheKey, VrState.FAILED)
            upsertJob(next.photo, next.priority, VrState.FAILED, 1f, finishedAt = System.currentTimeMillis(), error = error.message, source = next.source, albumId = next.albumId)
            addLog("failed ${next.photo.displayName}: ${error.message}")
        }
        activeKey = null
    }

    private fun expandAutoPrefetchIfNeeded(): Boolean {
        var attempts = 0
        while (attempts++ < 3) {
            val state = _uiState.value
            if (!state.vrMode || !state.settings.autoPrefetch || state.viewerOrigin != ViewerOrigin.NORMAL) return false
            val selected = state.selectedIndex ?: state.galleryAnchorIndex
            val before = state.prefetchSlotState
            if (before.tier >= 8 && before.prevDone >= 4 && before.nextDone >= 4) return false
            enqueueWindow(selected, includeCurrent = false)
            if (synchronized(pending) { pending.any { jobAllowedInCurrentContext(it, _uiState.value) } }) return true
            if (_uiState.value.prefetchSlotState == before) return false
        }
        return false
    }

    private fun markReady(key: String, entry: VrCacheEntry) {
        _uiState.update {
            it.copy(
                states = it.states + (key to VrState.READY),
                entries = it.entries + (key to entry),
            )
        }
        refreshGeneratedLibrary()
    }

    private fun markState(key: String, state: VrState) {
        _uiState.update { it.copy(states = it.states + (key to state)) }
    }

    private fun refreshGeneratedLibrary(message: String? = null) {
        val photos = _uiState.value.photos
        val imageEntries = photos
            .filter { it.kind == MediaKind.IMAGE }
            .mapNotNull { cache.findEntry(it) }
            .associateBy { it.photoKey }
        val managedItems = cache.allEntries(photos)
        val videoPairs = videoCache.allEntries(photos)
        val videoEntries = videoPairs.associate { it.first.cacheKey to it.second }
        val cacheVersions = cache.summaries()
        _uiState.update { current ->
            current.copy(
                entries = current.entries + imageEntries,
                states = current.states + imageEntries.keys.associateWith { VrState.READY },
                videoEntries = current.videoEntries + videoEntries,
                videoStates = current.videoStates + videoEntries.keys.associateWith { VideoVrState.READY },
                managedCacheItems = managedItems,
                cacheVersions = cacheVersions,
                message = message ?: current.message,
            )
        }
    }

    private fun upsertJob(
        photo: PhotoItem,
        priority: Int,
        state: VrState,
        progress: Float,
        finishedAt: Long? = null,
        error: String? = null,
        source: QueueSource? = null,
        albumId: String? = null,
    ) {
        _uiState.update { current ->
            val previous = current.jobs.firstOrNull { it.photoItem.cacheKey == photo.cacheKey }
            val existing = current.jobs.filterNot { it.photoItem.cacheKey == photo.cacheKey }
            val job = VrJob(
                photoItem = photo,
                priority = priority,
                state = state,
                progress = progress,
                source = source ?: previous?.source,
                albumId = albumId ?: previous?.albumId,
                startedAt = System.currentTimeMillis(),
                finishedAt = finishedAt,
                error = error,
            )
            current.copy(jobs = (listOf(job) + existing).take(200))
        }
    }

    private fun upsertVideoJob(
        item: GalleryItem,
        state: VideoVrState,
        progress: Float,
        currentFrame: Int = 0,
        totalFrames: Int = 0,
        fps: Int = 30,
        avgFrameMs: Long = 0L,
        currentFrameMs: Long = 0L,
        modelId: String = "",
        cacheVersion: String = "",
        modelThreads: Int = 0,
        depthWorkers: Int = 0,
        useGpu: Boolean = false,
        runtimeInfo: String = "",
        finishedAt: Long? = null,
        error: String? = null,
        frameTimings: VideoFrameTimings = VideoFrameTimings(),
        pipelineStats: VideoPipelineStats = VideoPipelineStats(),
    ) {
        _uiState.update { current ->
            val previous = current.videoJobs.firstOrNull { it.item.cacheKey == item.cacheKey }
            val existing = current.videoJobs.filterNot { it.item.cacheKey == item.cacheKey }
            val job = VideoVrJob(
                item = item,
                state = state,
                progress = progress,
                currentFrame = currentFrame.takeIf { it > 0 } ?: previous?.currentFrame ?: 0,
                totalFrames = totalFrames.takeIf { it > 0 } ?: previous?.totalFrames ?: 0,
                fps = fps.takeIf { it > 0 } ?: previous?.fps ?: 30,
                currentFrameMs = currentFrameMs.takeIf { it > 0L } ?: previous?.currentFrameMs ?: 0L,
                avgFrameMs = avgFrameMs.takeIf { it > 0L } ?: previous?.avgFrameMs ?: 0L,
                modelId = modelId.ifBlank { previous?.modelId.orEmpty() },
                cacheVersion = cacheVersion.ifBlank { previous?.cacheVersion.orEmpty() },
                modelThreads = modelThreads.takeIf { it > 0 } ?: previous?.modelThreads ?: 0,
                depthWorkers = depthWorkers.takeIf { it > 0 } ?: previous?.depthWorkers ?: 0,
                useGpu = if (modelId.isNotBlank() || cacheVersion.isNotBlank()) useGpu else previous?.useGpu == true,
                runtimeInfo = runtimeInfo.ifBlank { previous?.runtimeInfo.orEmpty() },
                startedAt = previous?.startedAt ?: System.currentTimeMillis(),
                finishedAt = finishedAt,
                error = error,
                frameTimings = if (frameTimings.totalMs > 0L) frameTimings else previous?.frameTimings ?: VideoFrameTimings(),
                pipelineStats = if (pipelineStats != VideoPipelineStats()) pipelineStats else previous?.pipelineStats ?: VideoPipelineStats(),
            )
            current.copy(
                videoStates = current.videoStates + (item.cacheKey to state),
                videoJobs = (listOf(job) + existing).take(100),
            )
        }
    }

    private fun nextVideoRunToken(videoKey: String): Long = synchronized(videoRunTokens) {
        val next = (videoRunTokens[videoKey] ?: 0L) + 1L
        videoRunTokens[videoKey] = next
        next
    }

    private fun isCurrentVideoRun(videoKey: String, token: Long): Boolean = synchronized(videoRunTokens) {
        videoRunTokens[videoKey] == token
    }

    private fun addLog(line: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _uiState.update { it.copy(logs = (listOf("$stamp $line") + it.logs).take(200)) }
    }

    private fun modelStatusText(settings: AppSettings): String {
        val lang = settings.language
        return listOf(
            "${lang.t("图片模型", "Image model")}：${lang.pickMixed(modelManager.statusText(settings.imageModelId))}",
            "${lang.t("视频模型", "Video model")}：${lang.pickMixed(modelManager.statusText(settings.videoModelId))}",
        ).joinToString("\n")
    }
}

private data class QueuedJob(val photo: PhotoItem, val priority: Int, val sequence: Long, val source: QueueSource, val albumId: String?)

private data class VideoQueuedJob(val item: GalleryItem, val params: VideoGenerationParams, val sequence: Long, val runToken: Long)

private class VideoPausedException : RuntimeException("Video generation paused")
private class VideoStaleException : RuntimeException("Video generation superseded")

private fun AppLanguage.t(zh: String, en: String): String = if (this == AppLanguage.ZH) zh else en

private fun AppLanguage.pickMixed(text: String): String {
    val marker = " / "
    if (!text.contains(marker)) return text
    val parts = text.split(marker, limit = 2)
    return if (this == AppLanguage.ZH) parts.first() else parts.getOrElse(1) { parts.first() }
}

private fun queuedVideoItemFromKey(videoKey: String): GalleryItem {
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

private fun VrState.label(lang: AppLanguage): String = when (this) {
    VrState.NORMAL -> lang.t("原图", "Normal")
    VrState.PAUSED -> lang.t("暂停", "Paused")
    VrState.QUEUED -> lang.t("队列中", "Queued")
    VrState.GENERATING -> lang.t("生成中", "Generating")
    VrState.READY -> lang.t("已生成", "Ready")
    VrState.FAILED -> lang.t("失败", "Failed")
}

private fun VideoVrState.label(lang: AppLanguage): String = when (this) {
    VideoVrState.NORMAL -> lang.t("未生成", "Normal")
    VideoVrState.QUEUED -> lang.t("队列中", "Queued")
    VideoVrState.GENERATING -> lang.t("生成中", "Generating")
    VideoVrState.PAUSED -> lang.t("已暂停", "Paused")
    VideoVrState.READY -> lang.t("已生成", "Ready")
    VideoVrState.FAILED -> lang.t("失败", "Failed")
}

private fun VideoVrState.shortLabel(lang: AppLanguage): String = when (this) {
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

private fun viewerScopeIndices(state: UiState): List<Int> {
    if (state.photos.isEmpty()) return emptyList()
    val indexByKey = state.photos.mapIndexed { index, item -> item.cacheKey to index }.toMap()
    return when {
        state.viewerScopeOrderedKeys.isNotEmpty() -> state.viewerScopeOrderedKeys.mapNotNull { indexByKey[it] }.distinct()
        state.viewerScopeKeys.isNotEmpty() -> state.photos.mapIndexedNotNull { index, item -> if (item.cacheKey in state.viewerScopeKeys) index else null }
        else -> state.photos.indices.toList()
    }
}

private fun relativeIndexInViewerScope(state: UiState, index: Int, selectedIndex: Int): Int {
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

private fun imageLoadedLine(state: UiState, index: Int, lang: AppLanguage): String {
    val prev = imageSideCount(state, index, -1, setOf(VrState.READY))
    val next = imageSideCount(state, index, 1, setOf(VrState.READY))
    return lang.t("已加载：前$prev 后$next", "Loaded: prev $prev next $next")
}

private fun imageQueueLine(state: UiState, index: Int, lang: AppLanguage): String {
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

private fun buildAlbums(items: List<GalleryItem>): List<AlbumItem> {
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

private fun dateGroupLabel(modifiedTimeSeconds: Long, lang: AppLanguage): String {
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

private fun localEpochDay(modifiedTimeSeconds: Long): Long {
    if (modifiedTimeSeconds <= 0L) return Long.MIN_VALUE
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = modifiedTimeSeconds * 1000L }
    val year = calendar.get(java.util.Calendar.YEAR)
    val day = calendar.get(java.util.Calendar.DAY_OF_YEAR)
    return year.toLong() * 400L + day.toLong()
}

private fun jsonStringValue(text: String, key: String): String? {
    return Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]*)\"")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.replace("\\\"", "\"")
}

private fun jsonIntValue(text: String, key: String): Int? {
    return Regex("\"${Regex.escape(key)}\"\\s*:\\s*(\\d+)")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
}

private fun jsonLongValue(text: String, key: String): Long? {
    return Regex("\"${Regex.escape(key)}\"\\s*:\\s*(\\d+)")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
}

private fun imageRecentGenerationLines(state: UiState, lang: AppLanguage): List<String> {
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

private class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
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
        val oldModelId = prefs.getString("modelId", "depth_anything_v2_small_tflite") ?: "depth_anything_v2_small_tflite"
        return AppSettings(
            language = runCatching { AppLanguage.valueOf(prefs.getString("language", AppLanguage.ZH.name) ?: AppLanguage.ZH.name) }.getOrDefault(AppLanguage.ZH),
            imageModelId = prefs.getString("imageModelId", oldModelId) ?: oldModelId,
            videoModelId = prefs.getString("videoModelId", oldModelId) ?: oldModelId,
            autoPrefetch = prefs.getBoolean("autoPrefetch", true),
            prefetchWindow = prefs.getInt("prefetchWindow", 2),
            depthScale = prefs.getFloat("depthScale", 40f),
            blurRadius = prefs.getInt("blurRadius", 3),
            fillRadius = prefs.getInt("fillRadius", 10),
            invertDepth = invertDepth,
            maxLongEdge = prefs.getInt("maxLongEdge", 6000),
            depthResolution = 518,
            generationWorkers = prefs.getInt("generationWorkers", 1),
            modelThreads = prefs.getInt("modelThreads", 4),
            useGpu = prefs.getBoolean("useGpu", false),
            gpuTestMode = runCatching { GpuTestMode.valueOf(prefs.getString("gpuTestMode", GpuTestMode.ACCURATE_UNSET.name) ?: GpuTestMode.ACCURATE_UNSET.name) }.getOrDefault(GpuTestMode.ACCURATE_UNSET),
            forceGpuNoFallback = true,
            videoModelThreads = prefs.getInt("videoModelThreads", 4),
            videoUseGpu = prefs.getBoolean("videoUseGpu", false),
            videoDepthWorkers = prefs.getInt("videoDepthWorkers", 2).coerceIn(1, 2),
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString("language", settings.language.name)
            .putString("modelId", settings.imageModelId)
            .putString("imageModelId", settings.imageModelId)
            .putString("videoModelId", settings.videoModelId)
            .putBoolean("autoPrefetch", settings.autoPrefetch)
            .putInt("prefetchWindow", settings.prefetchWindow)
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
            .apply()
    }
}

private class PhotoRepository(private val context: Context) {
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
            context.contentResolver.query(collection, projection, selection, args.toTypedArray(), "$modifiedColumn DESC, $idColumn DESC")
        }
        cursorResult?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(idColumn)
            val mediaTypeCol = cursor.getColumnIndexOrThrow(mediaTypeColumn)
            val nameCol = cursor.getColumnIndexOrThrow(nameColumn)
            val widthCol = cursor.getColumnIndex(widthColumn)
            val heightCol = cursor.getColumnIndex(heightColumn)
            val sizeCol = cursor.getColumnIndex(sizeColumn)
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
                result += GalleryItem(
                    id = id,
                    uri = itemUri,
                    displayName = displayName,
                    width = if (widthCol >= 0) cursor.getInt(widthCol) else 0,
                    height = if (heightCol >= 0) cursor.getInt(heightCol) else 0,
                    size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L,
                    modifiedTime = cursor.getLong(modifiedCol),
                    kind = kind,
                    bucketId = if (bucketIdCol >= 0) cursor.getString(bucketIdCol).orEmpty() else "",
                    bucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol).orEmpty() else "",
                    relativePath = if (relativePathCol >= 0) cursor.getString(relativePathCol).orEmpty() else "",
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

private class QueueTagStore(context: Context) {
    private val root = File(context.getExternalFilesDir(null), "queue_index").also { it.mkdirs() }
    private val file = File(root, "image_queue.tsv")

    @Synchronized
    fun load(): List<QueueTag> {
        if (!file.exists()) return emptyList()
        return file.readLines(Charsets.UTF_8).mapNotNull { line ->
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
            )
        }.distinctBy { it.photoKey }
    }

    @Synchronized
    fun upsert(job: QueuedJob, state: VrState) {
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
        if (tags.isEmpty()) {
            if (file.exists()) file.delete()
            return
        }
        file.writeText(
            tags.joinToString("\n") { tag ->
                listOf(
                    tag.photoKey.safeQueueField(),
                    tag.source.name,
                    tag.albumId.orEmpty().safeQueueField(),
                    tag.priority.toString(),
                    tag.state.name,
                    tag.updatedAt.toString(),
                ).joinToString("\t")
            },
            Charsets.UTF_8,
        )
    }

    private fun String.safeQueueField(): String = replace('\t', '_').replace('\n', '_').replace('\r', '_')
}

private class VideoQueueTagStore(context: Context) {
    private val root = File(context.getExternalFilesDir(null), "queue_index").also { it.mkdirs() }
    private val file = File(root, "video_queue.tsv")

    @Synchronized
    fun load(): List<VideoQueueTag> {
        if (!file.exists()) return emptyList()
        return file.readLines(Charsets.UTF_8).mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val state = runCatching { VideoVrState.valueOf(parts[1]) }.getOrNull() ?: VideoVrState.PAUSED
            VideoQueueTag(
                videoKey = parts[0],
                state = state,
                updatedAt = parts[2].toLongOrNull() ?: 0L,
                params = parts.getOrNull(3)?.let { decodeVideoParams(it) },
            )
        }.distinctBy { it.videoKey }
    }

    @Synchronized
    fun upsert(videoKey: String, state: VideoVrState, params: VideoGenerationParams? = null) {
        val next = load()
            .filterNot { it.videoKey == videoKey }
            .toMutableList()
        val previous = load().firstOrNull { it.videoKey == videoKey }
        next += VideoQueueTag(
            videoKey = videoKey,
            state = state,
            params = params ?: previous?.params,
            updatedAt = System.currentTimeMillis(),
        )
        write(next)
    }

    @Synchronized
    fun remove(videoKey: String) {
        write(load().filterNot { it.videoKey == videoKey })
    }

    private fun write(tags: List<VideoQueueTag>) {
        if (tags.isEmpty()) {
            if (file.exists()) file.delete()
            return
        }
        file.writeText(
            tags.joinToString("\n") { tag ->
                listOf(
                    tag.videoKey.safeQueueField(),
                    tag.state.name,
                    tag.updatedAt.toString(),
                    tag.params?.encodeForQueue().orEmpty(),
                ).joinToString("\t")
            },
            Charsets.UTF_8,
        )
    }

    private fun VideoGenerationParams.encodeForQueue(): String {
        return listOf(
            vr.depthModel,
            vr.outputMode,
            vr.depthScale.toString(),
            vr.blurRadius.toString(),
            vr.fillRadius.toString(),
            vr.invertDepth.toString(),
            vr.maxLongEdge.toString(),
            vr.inpaintMode,
            vr.quality.toString(),
            modelThreads.toString(),
            useGpu.toString(),
            vr.gpuTestMode.name,
            vr.forceGpuNoFallback.toString(),
            cacheVersion(),
            depthWorkers.toString(),
        ).joinToString(",") { it.replace(",", "_").replace('\t', '_').replace('\n', '_').replace('\r', '_') }
    }

    private fun decodeVideoParams(text: String): VideoGenerationParams? {
        val parts = text.split(',')
        if (parts.size < 11) return null
        return runCatching {
            VideoGenerationParams(
                vr = VrGenerationParams(
                    depthModel = parts[0],
                    outputMode = parts[1],
                    depthScale = parts[2].toFloat(),
                    blurRadius = parts[3].toInt(),
                    fillRadius = parts[4].toInt(),
                    invertDepth = parts[5].toBooleanStrictOrNull() ?: false,
                    maxLongEdge = parts[6].toInt(),
                    modelThreads = parts[9].toInt(),
                    useGpu = parts[10].toBooleanStrictOrNull() ?: false,
                    gpuTestMode = parts.getOrNull(11)?.let { runCatching { GpuTestMode.valueOf(it) }.getOrNull() } ?: GpuTestMode.AUTO,
                    forceGpuNoFallback = parts[10].toBooleanStrictOrNull() ?: false,
                    inpaintMode = parts[7],
                    quality = parts[8].toInt(),
                ),
                modelThreads = parts[9].toInt(),
                useGpu = parts[10].toBooleanStrictOrNull() ?: false,
                depthWorkers = parts.getOrNull(14)?.toIntOrNull()?.coerceIn(1, 2) ?: 2,
            )
        }.getOrNull()
    }

    private fun String.safeQueueField(): String = replace('\t', '_').replace('\n', '_').replace('\r', '_')
}

private class VrCacheManager(private val context: Context) {
    val root: File = File(context.getExternalFilesDir(null), "vr_cache").also { it.mkdirs() }

    fun entryDir(photo: PhotoItem, version: String): File {
        return if (version == DEFAULT_VERSION) File(root, photo.cacheKey) else File(File(root, photo.cacheKey), version)
    }

    fun findEntry(photo: PhotoItem, version: String? = null): VrCacheEntry? {
        if (version != null) {
            return readEntry(photo.cacheKey, version, entryDir(photo, version))
        }
        return latestEntry(photo.cacheKey)
    }

    fun latestEntry(photoKey: String): VrCacheEntry? {
        val base = File(root, photoKey)
        val candidates = mutableListOf<Pair<String, File>>()
        candidates += DEFAULT_VERSION to base
        base.listFiles()?.filter { it.isDirectory }?.forEach { candidates += it.name to it }
        return candidates.mapNotNull { (version, dir) -> readEntry(photoKey, version, dir) }.maxByOrNull { it.createdAt }
    }

    fun summaries(): List<CacheVersionSummary> {
        val map = linkedMapOf<String, Pair<Int, Long>>()
        root.listFiles()?.filter { it.isDirectory }?.forEach { photoDir ->
            readEntry(photoDir.name, DEFAULT_VERSION, photoDir)?.let { entry ->
                val current = map[DEFAULT_VERSION] ?: (0 to 0L)
                map[DEFAULT_VERSION] = current.first + 1 to current.second + entry.cacheBytes()
            }
            photoDir.listFiles()?.filter { it.isDirectory }?.forEach { versionDir ->
                readEntry(photoDir.name, versionDir.name, versionDir)?.let { entry ->
                    val current = map[versionDir.name] ?: (0 to 0L)
                    map[versionDir.name] = current.first + 1 to current.second + entry.cacheBytes()
                }
            }
        }
        return map.map { (version, value) ->
            CacheVersionSummary(version = version, kind = "图片 / Images", count = value.first, bytes = value.second)
        }.sortedBy { it.version }
    }

    fun allEntries(photos: List<PhotoItem>): List<ManagedCacheItem> {
        return allEntriesByKey(photos.associateBy { it.cacheKey })
    }

    fun allEntriesByKey(photoByKey: Map<String, PhotoItem>): List<ManagedCacheItem> {
        return root.listFiles()?.asSequence()
            ?.filter { it.isDirectory }
            ?.flatMap { photoDir ->
                val entries = mutableListOf<VrCacheEntry>()
                readEntry(photoDir.name, DEFAULT_VERSION, photoDir)?.let { entries += it }
                photoDir.listFiles()?.filter { it.isDirectory }?.forEach { versionDir ->
                    readEntry(photoDir.name, versionDir.name, versionDir)?.let { entries += it }
                }
                val latest = entries.maxByOrNull { it.createdAt } ?: return@flatMap emptySequence()
                val photo = photoByKey[photoDir.name] ?: virtualPhotoItem(photoDir.name, latest)
                entries.asSequence().map { ManagedCacheItem(photo, it) }
            }
            ?.sortedByDescending { it.entry.createdAt }
            ?.toList()
            ?: emptyList()
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

    fun deleteVersion(version: String) {
        root.listFiles()?.filter { it.isDirectory }?.forEach { photoDir ->
            if (version == DEFAULT_VERSION) {
                IMAGE_CACHE_FILES.forEach {
                    File(photoDir, it).delete()
                }
            } else {
                File(photoDir, version).deleteRecursively()
            }
        }
    }

    fun deleteEntry(entry: VrCacheEntry) {
        val photoDir = File(root, entry.photoKey)
        if (entry.version == DEFAULT_VERSION) {
            IMAGE_CACHE_FILES.forEach {
                File(photoDir, it).delete()
            }
        } else {
            File(photoDir, entry.version).deleteRecursively()
        }
    }

    fun deletePhoto(photo: PhotoItem) {
        File(root, photo.cacheKey).deleteRecursively()
    }

    private fun readEntry(photoKey: String, version: String, dir: File): VrCacheEntry? {
        val vr = File(dir, "vr_sbs.jpg")
        val left = File(dir, "left.webp")
        val right = File(dir, "right.webp")
        val depth = File(dir, "depth.png")
        val params = File(dir, "params.json")
        val log = File(dir, "job.log")
        if ((!vr.exists() && (!left.exists() || !right.exists())) || !depth.exists() || !params.exists() || !log.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (left.exists() && right.exists()) {
            BitmapFactory.decodeFile(left.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        } else {
            BitmapFactory.decodeFile(vr.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        }
        val leftWidth = if (left.exists() && right.exists()) bounds.outWidth else max(1, bounds.outWidth / 2)
        val outputWidth = leftWidth * 2
        val outputHeight = bounds.outHeight
        val modified = listOf(vr, left, right, params, log)
            .filter { it.exists() }
            .maxOfOrNull { it.lastModified() }
            ?: dir.lastModified()
        return VrCacheEntry(
            photoKey = photoKey,
            version = version,
            outputPath = vr.absolutePath,
            leftPath = left.takeIf { it.exists() }?.absolutePath.orEmpty(),
            rightPath = right.takeIf { it.exists() }?.absolutePath.orEmpty(),
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

private class VideoVrCacheManager(private val context: Context) {
    val root: File = File(context.getExternalFilesDir(null), "video_vr_cache").also { it.mkdirs() }

    fun entryDir(item: GalleryItem): File = File(root, item.cacheKey).also { it.mkdirs() }

    fun entryDir(item: GalleryItem, version: String): File = File(entryDir(item), version).also { it.mkdirs() }

    fun findEntry(item: GalleryItem): VideoCacheEntry? {
        val base = entryDir(item)
        val entries = mutableListOf<VideoCacheEntry>()
        readEntry(item.cacheKey, base)?.let { entries += it }
        base.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            readEntry(item.cacheKey, dir)?.let { entries += it }
        }
        return entries.maxByOrNull { it.createdAt }
    }

    fun allEntries(items: List<GalleryItem>): List<Pair<GalleryItem, VideoCacheEntry>> {
        return allEntriesByKey(items.filter { it.kind == MediaKind.VIDEO }.associateBy { it.cacheKey })
    }

    fun allEntriesByKey(videoByKey: Map<String, GalleryItem>): List<Pair<GalleryItem, VideoCacheEntry>> {
        return root.listFiles()?.asSequence()
            ?.filter { it.isDirectory }
            ?.mapNotNull { videoDir ->
                val entries = mutableListOf<VideoCacheEntry>()
                readEntry(videoDir.name, videoDir)?.let { entries += it }
                videoDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                    readEntry(videoDir.name, dir)?.let { entries += it }
                }
                val latest = entries.maxByOrNull { it.createdAt } ?: return@mapNotNull null
                val item = videoByKey[videoDir.name] ?: virtualVideoItem(videoDir.name, latest)
                item to latest
            }
            ?.sortedByDescending { it.second.createdAt }
            ?.toList()
            ?: emptyList()
    }

    fun delete(item: GalleryItem) {
        File(root, item.cacheKey).deleteRecursively()
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
        if (values["encoderVersion"] != VIDEO_ENCODER_VERSION) return null
        return VideoCacheEntry(
            videoKey = videoKey,
            outputPath = output.absolutePath,
            logPath = log.absolutePath,
            width = values["width"]?.toIntOrNull() ?: 0,
            height = values["height"]?.toIntOrNull() ?: 0,
            durationMs = values["durationMs"]?.toLongOrNull() ?: 0L,
            fps = values["fps"]?.toIntOrNull() ?: 30,
            createdAt = output.lastModified(),
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

class VideoGenerationForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            SERVICE_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("视频 VR 生成运行中")
                .setContentText("切到后台或锁屏时继续生成")
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, 0, true)
                .build(),
        )
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "视频 VR 后台生成", NotificationManager.IMPORTANCE_LOW))
        }
    }

    companion object {
        const val ACTION_START = "com.local.parallelvrgallerypro.video.START"
        const val ACTION_STOP = "com.local.parallelvrgallerypro.video.STOP"
        private const val CHANNEL_ID = "video_vr_foreground"
        private const val SERVICE_NOTIFICATION_ID = 9117
    }
}

private class VideoGenerationKeepAlive(context: Context) {
    private val appContext = context.applicationContext
    private val wakeLock = (appContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ParallelVrGalleryPro:VideoGeneration")
        .apply { setReferenceCounted(false) }
    private var activeCount = 0

    @Synchronized
    fun acquire() {
        activeCount++
        if (activeCount == 1) {
            val intent = Intent(appContext, VideoGenerationForegroundService::class.java)
                .setAction(VideoGenerationForegroundService.ACTION_START)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) {
                    ContextCompat.startForegroundService(appContext, intent)
                } else {
                    appContext.startService(intent)
                }
            }
            runCatching {
                if (!wakeLock.isHeld) wakeLock.acquire(12 * 60 * 60 * 1000L)
            }
        }
    }

    @Synchronized
    fun release() {
        if (activeCount > 0) activeCount--
        if (activeCount == 0) {
            runCatching { if (wakeLock.isHeld) wakeLock.release() }
            runCatching { appContext.stopService(Intent(appContext, VideoGenerationForegroundService::class.java)) }
        }
    }
}

private class VideoGenerationNotifier(private val context: Context) {
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
            .setOngoing(status != "已完成")
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

private class ModelManager(private val context: Context) {
    private val modelsDir = File(context.getExternalFilesDir(null), "models").also { it.mkdirs() }

    fun statusText(modelId: String = AvailableModels.first().id): String {
        val spec = modelSpec(modelId)
        if (!spec.downloadable) return "模型待导入 / Model asset required"
        val modelFile = File(modelsDir, spec.fileName)
        return if (modelFile.exists()) {
            "模型已就绪 / Model ready"
        } else {
            "模型未下载 / Model not downloaded"
        }
    }

    fun ensureModel(modelId: String, onProgress: (Float) -> Unit): File {
        val spec = modelSpec(modelId)
        if (!spec.downloadable) {
            error("${spec.displayName} 需要先导入移动端模型资产 / Mobile model asset required")
        }
        val modelFile = File(modelsDir, spec.fileName)
        if (modelFile.exists() && modelFile.sha256().equals(spec.sha256, ignoreCase = true)) {
            onProgress(1f)
            return modelFile
        }

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
                tmp.renameTo(modelFile)
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

private class VrGenerator(
    private val context: Context,
    private val cache: VrCacheManager,
    private val modelManager: ModelManager,
) : Closeable {
    private val imageSessionLock = Any()
    private val imageSessions = linkedMapOf<String, DepthModelSession>()

    fun generate(
        photo: PhotoItem,
        params: VrGenerationParams,
        onModelProgress: (Float) -> Unit,
        onProgress: (Float) -> Unit,
    ): VrCacheEntry {
        val version = params.cacheVersion()
        val dir = cache.entryDir(photo, version).also { it.mkdirs() }
        val log = StringBuilder()
        val start = System.currentTimeMillis()
        fun mark(message: String) {
            val elapsed = System.currentTimeMillis() - start
            log.append(elapsed).append("ms ").append(message).append('\n')
        }

        mark("start name=${photo.displayName} size=${photo.size} modified=${photo.modifiedTime}")
        val depthSession = sharedImageDepthSession(params, onModelProgress) { runtime ->
            mark(runtime)
        }
        mark("model session ready key=${imageSessionKey(params)}")
        onProgress(0.12f)

        val decodeMaxLongEdge = memorySafeMaxLongEdge(photo.width, photo.height, params.maxLongEdge)
        if (decodeMaxLongEdge < params.maxLongEdge) {
            mark("memory capped maxLongEdge ${params.maxLongEdge} -> $decodeMaxLongEdge")
        }
        val decodeStart = System.currentTimeMillis()
        var original = decodeScaledBitmap(context, photo.uri, decodeMaxLongEdge)
        val decodeMs = System.currentTimeMillis() - decodeStart
        mark("decode ${decodeMs}ms size=${original.width}x${original.height}")
        if (max(photo.width, photo.height) > decodeMaxLongEdge) {
            mark("downsampled source because original long edge exceeded $decodeMaxLongEdge")
        }
        onProgress(0.25f)

        val depthStart = System.currentTimeMillis()
        val rawDepth = depthSession.run(original)
        val modelMs = System.currentTimeMillis() - depthStart
        mark("model inference ${modelMs}ms")
        val depthPostStart = System.currentTimeMillis()
        val depthSmall = smoothDepth(rawDepth, params.blurRadius, params.invertDepth)
        onProgress(0.55f)

        val depthBitmap = depthToBitmap(depthSmall)
        val depthPath = File(dir, "depth.png")
        FileOutputStream(depthPath).use { depthBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val depthPostMs = System.currentTimeMillis() - depthPostStart
        mark("depth post ${depthPostMs}ms output=${depthBitmap.width}x${depthBitmap.height}")
        onProgress(0.7f)

        val sbsStart = System.currentTimeMillis()
        val pair = runCatching {
            makeParallelStereoPair(original, depthSmall, params.depthScale, params.fillRadius)
        }.getOrElse { error ->
            if (error !is OutOfMemoryError || max(original.width, original.height) <= 960) throw error
            val retryLongEdge = max(960, (max(original.width, original.height) * 0.72f).roundToInt())
            val scale = retryLongEdge.toFloat() / max(original.width, original.height).toFloat()
            mark("sbs oom retry maxLongEdge=$retryLongEdge error=${error.message}")
            val reduced = Bitmap.createScaledBitmap(
                original,
                max(1, (original.width * scale).roundToInt()),
                max(1, (original.height * scale).roundToInt()),
                true,
            )
            original.recycle()
            original = reduced
            makeParallelStereoPair(original, depthSmall, params.depthScale, params.fillRadius)
        }
        val sbsMs = System.currentTimeMillis() - sbsStart
        mark("stereo ${sbsMs}ms output=${pair.sbsWidth}x${pair.height}")
        val writeStart = System.currentTimeMillis()
        val vrPath = File(dir, "vr_sbs.jpg")
        val leftPath = File(dir, "left.webp")
        val rightPath = File(dir, "right.webp")
        val writeLeftStart = System.currentTimeMillis()
        writeWebpLossless(pair.left, leftPath)
        val writeLeftMs = System.currentTimeMillis() - writeLeftStart
        val writeRightStart = System.currentTimeMillis()
        writeWebpLossless(pair.right, rightPath)
        val writeRightMs = System.currentTimeMillis() - writeRightStart
        onProgress(0.9f)
        val outputWidth = pair.sbsWidth
        val outputHeight = pair.height

        val paramsPath = File(dir, "params.json")
        val logPath = File(dir, "job.log")
        val totalBeforeFinalLog = System.currentTimeMillis() - start
        val timings = GenerationTimings(
            decodeMs = decodeMs,
            modelMs = modelMs,
            depthPostMs = depthPostMs,
            sbsMs = sbsMs,
            writeLeftMs = writeLeftMs,
            writeRightMs = writeRightMs,
            writeMs = 0L,
            totalMs = totalBeforeFinalLog,
        )
        val writeParamsStart = System.currentTimeMillis()
        paramsPath.writeText(params.toJson(photo, original, outputWidth, outputHeight, timings), Charsets.UTF_8)
        val writeParamsFirstMs = System.currentTimeMillis() - writeParamsStart
        val writeMsBeforeLog = System.currentTimeMillis() - writeStart
        val finalTimingsBeforeLog = timings.copy(
            writeParamsMs = writeParamsFirstMs,
            writeMs = writeMsBeforeLog,
            totalMs = System.currentTimeMillis() - start,
        )
        val rewriteParamsStart = System.currentTimeMillis()
        paramsPath.writeText(params.toJson(photo, original, outputWidth, outputHeight, finalTimingsBeforeLog), Charsets.UTF_8)
        val writeParamsMs = writeParamsFirstMs + (System.currentTimeMillis() - rewriteParamsStart)
        val logWriteStart = System.currentTimeMillis()
        mark("write leftWebp=${writeLeftMs}ms rightWebp=${writeRightMs}ms params=${writeParamsMs}ms")
        mark("done total=${System.currentTimeMillis() - start}ms")
        logPath.writeText(log.toString(), Charsets.UTF_8)
        val writeLogMs = System.currentTimeMillis() - logWriteStart
        val writeMs = System.currentTimeMillis() - writeStart
        val finalTimings = timings.copy(
            writeParamsMs = writeParamsMs,
            writeLogMs = writeLogMs,
            writeMs = writeMs,
            totalMs = System.currentTimeMillis() - start,
        )
        paramsPath.writeText(params.toJson(photo, original, outputWidth, outputHeight, finalTimings), Charsets.UTF_8)
        depthBitmap.recycle()
        original.recycle()
        pair.recycle()

        return VrCacheEntry(
            photoKey = photo.cacheKey,
            version = version,
            outputPath = vrPath.absolutePath,
            leftPath = leftPath.absolutePath,
            rightPath = rightPath.absolutePath,
            depthPath = depthPath.absolutePath,
            paramsPath = paramsPath.absolutePath,
            logPath = logPath.absolutePath,
            width = outputWidth,
            height = outputHeight,
            createdAt = System.currentTimeMillis(),
        )
    }

    fun generateSbsBitmap(
        source: Bitmap,
        params: VrGenerationParams,
        onModelProgress: (Float) -> Unit = {},
        onRuntimeInfo: (String) -> Unit = {},
        depthSession: DepthModelSession? = null,
    ): Bitmap {
        val safeMaxLongEdge = memorySafeMaxLongEdge(source.width, source.height, params.maxLongEdge)
        val working = if (max(source.width, source.height) > safeMaxLongEdge) {
            val scale = safeMaxLongEdge.toFloat() / max(source.width, source.height).toFloat()
            Bitmap.createScaledBitmap(source, max(1, (source.width * scale).roundToInt()), max(1, (source.height * scale).roundToInt()), true)
        } else {
            source
        }
        val rawDepth = if (depthSession != null) {
            depthSession.run(working)
        } else {
            openDepthSession(params, onModelProgress, onRuntimeInfo).use { session ->
                session.run(working)
            }
        }
        val depthSmall = smoothDepth(rawDepth, params.blurRadius, params.invertDepth)
        return makeParallelSbs(working, depthSmall, params.depthScale, params.fillRadius)
    }

    fun generateVideoSbsBitmap(
        source: Bitmap,
        params: VrGenerationParams,
        depthSession: DepthModelSession,
        temporalSmoother: DepthTemporalSmoother,
    ): VideoSbsResult {
        val depth = generateVideoDepth(source, params, depthSession)
        return finishVideoSbs(depth, params, temporalSmoother)
    }

    fun generateVideoDepth(
        source: Bitmap,
        params: VrGenerationParams,
        depthSession: DepthModelSession,
    ): VideoDepthResult {
        val safeMaxLongEdge = memorySafeMaxLongEdge(source.width, source.height, params.maxLongEdge)
        val working = if (max(source.width, source.height) > safeMaxLongEdge) {
            val scale = safeMaxLongEdge.toFloat() / max(source.width, source.height).toFloat()
            Bitmap.createScaledBitmap(source, max(1, (source.width * scale).roundToInt()), max(1, (source.height * scale).roundToInt()), true)
        } else {
            source
        }
        val depthStart = SystemClock.uptimeMillis()
        val rawDepth = depthSession.run(working)
        val depthMs = SystemClock.uptimeMillis() - depthStart
        return VideoDepthResult(
            source = working,
            rawDepth = rawDepth,
            timings = VideoFrameTimings(depthMs = depthMs),
        )
    }

    fun finishVideoSbs(
        depth: VideoDepthResult,
        params: VrGenerationParams,
        temporalSmoother: DepthTemporalSmoother,
    ): VideoSbsResult {
        val temporalStart = SystemClock.uptimeMillis()
        val stableDepth = temporalSmoother.smooth(depth.rawDepth)
        val temporalMs = SystemClock.uptimeMillis() - temporalStart
        val depthPostStart = SystemClock.uptimeMillis()
        val depthSmall = smoothDepth(stableDepth, params.blurRadius, params.invertDepth)
        val depthPostMs = SystemClock.uptimeMillis() - depthPostStart
        val sbsStart = SystemClock.uptimeMillis()
        val sbs = try {
            makeParallelSbs(depth.source, depthSmall, params.depthScale, params.fillRadius)
        } finally {
            depth.source.recycle()
        }
        val sbsMs = SystemClock.uptimeMillis() - sbsStart
        return VideoSbsResult(
            bitmap = sbs,
            timings = VideoFrameTimings(
                depthMs = depth.timings.depthMs,
                temporalMs = temporalMs,
                depthPostMs = depthPostMs,
                sbsMs = sbsMs,
            ),
        )
    }

    private fun imageSessionKey(params: VrGenerationParams): String {
        return "${params.depthModel}|threads=${params.modelThreads.coerceIn(1, 8)}|gpu=${params.useGpu}|gpuMode=${params.gpuTestMode}|force=${params.forceGpuNoFallback}"
    }

    private fun sharedImageDepthSession(
        params: VrGenerationParams,
        onModelProgress: (Float) -> Unit,
        onRuntimeInfo: (String) -> Unit,
    ): DepthModelSession {
        val key = imageSessionKey(params)
        synchronized(imageSessionLock) {
            imageSessions[key]?.let { return it }
            val session = openDepthSession(params, onModelProgress, onRuntimeInfo)
            imageSessions[key] = session
            return session
        }
    }

    fun openDepthSession(
        params: VrGenerationParams,
        onModelProgress: (Float) -> Unit = {},
        onRuntimeInfo: (String) -> Unit = {},
    ): DepthModelSession {
        val modelFile = modelManager.ensureModel(params.depthModel, onModelProgress)
        return DepthModelSession(modelFile, params.modelThreads, params.useGpu, params.gpuTestMode, params.forceGpuNoFallback, onRuntimeInfo)
    }

    private fun memorySafeMaxLongEdge(width: Int, height: Int, requested: Int): Int {
        val longEdge = max(width, height).takeIf { it > 0 } ?: requested
        val shortEdge = min(width, height).takeIf { it > 0 } ?: longEdge
        val ratio = shortEdge.toFloat() / longEdge.toFloat()
        val heap = Runtime.getRuntime()
        val heapAvailable = heap.maxMemory() - (heap.totalMemory() - heap.freeMemory())
        val budget = min(160L * 1024L * 1024L, max(48L * 1024L * 1024L, (heapAvailable * 55L) / 100L))
        val bytesPerSourcePixel = 18f
        val requestedPixels = requested.toFloat() * requested.toFloat() * ratio
        val requestedBytes = requestedPixels * bytesPerSourcePixel
        if (requestedBytes <= budget) return requested
        val safe = sqrt(budget.toDouble() / (ratio.toDouble() * bytesPerSourcePixel.toDouble())).roundToInt()
        return safe.coerceIn(768, requested)
    }

    private fun runDepthModel(
        bitmap: Bitmap,
        modelFile: File,
        modelThreads: Int,
        useGpu: Boolean,
        onRuntimeInfo: ((String) -> Unit)? = null,
    ): FloatArray {
        return DepthModelSession(modelFile, modelThreads, useGpu, GpuTestMode.AUTO, false, onRuntimeInfo ?: {}).use { session ->
            session.run(bitmap)
        }
    }

    override fun close() {
        synchronized(imageSessionLock) {
            imageSessions.values.forEach { session -> runCatching { session.close() } }
            imageSessions.clear()
        }
    }

    private fun depthToBitmap(depth: FloatArray): Bitmap {
        val size = 518
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        for (i in depth.indices) {
            val value = (depth[i].coerceIn(0f, 1f) * 255f).roundToInt()
            pixels[i] = Color.rgb(value, value, value)
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    private fun smoothDepth(depth: FloatArray, radius: Int, invert: Boolean): FloatArray {
        val size = 518
        if (radius <= 0) {
            return if (invert) FloatArray(depth.size) { 1f - depth[it] } else depth.copyOf()
        }
        val r = radius.coerceAtLeast(1) / 2
        val horizontal = FloatArray(depth.size)
        val output = FloatArray(depth.size)
        for (y in 0 until size) {
            var sum = 0f
            for (x in 0 until size) {
                sum += depth[y * size + x]
                if (x > r) sum -= depth[y * size + x - r - 1]
                val count = min(x + r + 1, size) - max(0, x - r)
                horizontal[y * size + x] = sum / count.toFloat()
            }
        }
        for (x in 0 until size) {
            var sum = 0f
            for (y in 0 until size) {
                sum += horizontal[y * size + x]
                if (y > r) sum -= horizontal[(y - r - 1) * size + x]
                val count = min(y + r + 1, size) - max(0, y - r)
                val value = sum / count.toFloat()
                output[y * size + x] = if (invert) 1f - value else value
            }
        }
        return output
    }

    private fun makeParallelStereoPair(source: Bitmap, depth: FloatArray, depthScale: Float, fillRadius: Int): StereoBitmapPair {
        val w = source.width
        val h = source.height
        val fill = fillRadius.coerceIn(1, 32)
        val depthScaling = depthScale / w.toFloat()
        val left = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val right = source.copy(Bitmap.Config.ARGB_8888, false)
        val srcRow = IntArray(w)
        val leftRow = IntArray(w)
        val dxForX = IntArray(w) { x -> (x * 517 / max(1, w - 1)).coerceIn(0, 517) }

        for (y in 0 until h) {
            source.getPixels(srcRow, 0, w, 0, y, w, 1)
            srcRow.copyInto(leftRow)
            val depthBase = (y * 517 / max(1, h - 1)).coerceIn(0, 517) * 518
            for (x in w - 1 downTo 0) {
                val d = depth[depthBase + dxForX[x]]
                val shift = (d.coerceIn(0f, 1f) * 255f * depthScaling).toInt().coerceIn(0, w - 1)
                val color = srcRow[x]
                for (offset in 0 until fill) {
                    val leftX = (x + shift + offset).coerceIn(0, w - 1)
                    leftRow[leftX] = color
                }
            }
            left.setPixels(leftRow, 0, w, 0, y, w, 1)
        }
        return StereoBitmapPair(left, right)
    }

    private fun makeParallelSbs(source: Bitmap, depth: FloatArray, depthScale: Float, fillRadius: Int): Bitmap {
        val pair = makeParallelStereoPair(source, depth, depthScale, fillRadius)
        return try {
            composeSbsBitmap(pair.left, pair.right)
        } finally {
            pair.recycle()
        }
    }
}

private data class StereoBitmapPair(
    val left: Bitmap,
    val right: Bitmap,
) {
    val sbsWidth: Int get() = left.width + right.width
    val height: Int get() = max(left.height, right.height)

    fun recycle() {
        left.recycle()
        right.recycle()
    }
}

private data class DepthRunResult(
    val values: FloatArray,
    val range: Float,
)

private data class DepthCompareResult(
    val meanAbs: Float,
    val correlation: Float,
)

private enum class GpuMode {
    ACCURATE_UNSET,
    ACCURATE_OPENGL,
    ACCURATE_OPENCL,
    BEST_COMPAT,
}

class DepthModelSession(
    modelFile: File,
    private val modelThreads: Int,
    private val useGpu: Boolean,
    private val gpuTestMode: GpuTestMode,
    private val forceGpuNoFallback: Boolean,
    private val onRuntimeInfo: (String) -> Unit = {},
) : Closeable {
    private val inputSize = 518
    private val model = loadModelFile(modelFile)
    private var gpuDelegate: GpuDelegate? = null
    private var interpreter: Interpreter
    private var delegateActive = false
    private var gpuValidated = false
    private var activeGpuMode: GpuMode? = null
    private val threadCount = modelThreads.coerceIn(1, 8)
    private val input = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder())
    private val pixels = IntArray(inputSize * inputSize)
    private val output = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 4).order(ByteOrder.nativeOrder())

    init {
        val gpuDiagnostics = mutableListOf<String>()
        gpuDiagnostics += "device=${Build.MANUFACTURER}/${Build.MODEL} sdk=${Build.VERSION.SDK_INT}"
        if (!useGpu) {
            gpuDiagnostics += "gpu=disabled"
            interpreter = Interpreter(model, Interpreter.Options().setNumThreads(threadCount))
        } else {
            val compatibilityResult = runCatching { CompatibilityList() }
            val compatibility = compatibilityResult.getOrNull()
            val compatible = compatibility?.isDelegateSupportedOnThisDevice
            gpuDiagnostics += "compat=${compatible ?: "unknown"}"
            compatibilityResult.exceptionOrNull()?.let { error -> gpuDiagnostics += "compatError=${error.shortMessage()}" }
            interpreter = createFirstGpuInterpreter(compatibility, gpuDiagnostics)
                ?: if (forceGpuNoFallback) {
                    error("Forced GPU failed: ${gpuDiagnostics.joinToString(" ")}")
                } else {
                    Interpreter(model, Interpreter.Options().setNumThreads(threadCount))
                }
            runCatching { compatibility?.close() }
        }
        onRuntimeInfo("tflite runtime threads=$threadCount requestedGpu=$useGpu forceGpu=$forceGpuNoFallback delegateActive=$delegateActive gpuMode=${activeGpuMode ?: "CPU"} reusedSession=true ${gpuDiagnostics.joinToString(" ")}")
    }

    @Synchronized
    fun run(bitmap: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        input.clear()
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        scaled.recycle()
        for (pixel in pixels) {
            input.putFloat(Color.red(pixel) / 255f)
            input.putFloat(Color.green(pixel) / 255f)
            input.putFloat(Color.blue(pixel) / 255f)
        }
        input.rewind()
        var normalized = runInterpreterAndRead()
        if ((!normalized.range.isFinite() || normalized.range <= 0.0001f) && useGpu && delegateActive) {
            onRuntimeInfo("tflite gpu output flat mode=$activeGpuMode range=${normalized.range} forceGpu=$forceGpuNoFallback")
            if (!forceGpuNoFallback) {
                normalized = tryRecoverGpu(null, reason = "flatOutput") ?: fallbackToCpu("flatOutput")
            }
        }
        if (useGpu && delegateActive && !gpuValidated && !forceGpuNoFallback) {
            val cpuReference = runCpuReferenceAndRead()
            val quality = compareDepthMaps(normalized.values, cpuReference.values)
            if (quality.meanAbs > 0.18f || quality.correlation < 0.65f) {
                onRuntimeInfo("tflite gpu validation failed mode=$activeGpuMode meanAbs=${quality.meanAbs.format3()} corr=${quality.correlation.format3()}; trying next gpu mode")
                normalized = tryRecoverGpu(cpuReference, reason = "validation") ?: fallbackToCpu("validation", cpuReference)
            } else {
                onRuntimeInfo("tflite gpu validation ok mode=$activeGpuMode meanAbs=${quality.meanAbs.format3()} corr=${quality.correlation.format3()}")
            }
            gpuValidated = true
        }
        if (useGpu && delegateActive && forceGpuNoFallback && !gpuValidated) {
            onRuntimeInfo("tflite forced gpu mode=$activeGpuMode outputRange=${normalized.range} noCpuValidation=true")
            gpuValidated = true
        }
        return normalized.values
    }

    private fun createFirstGpuInterpreter(compatibility: CompatibilityList?, diagnostics: MutableList<String>): Interpreter? {
        for (mode in gpuModeCandidates()) {
            val created = runCatching { createGpuInterpreter(mode, compatibility) }
            created.onSuccess {
                diagnostics += "gpuMode=$mode"
                diagnostics += "interpreterCreate=ok"
                return it
            }.onFailure { error ->
                diagnostics += "gpuMode=$mode failed:${error.shortMessage()}"
            }
        }
        gpuDelegate?.close()
        gpuDelegate = null
        delegateActive = false
        activeGpuMode = null
        diagnostics += "gpuAllModesFailed"
        return null
    }

    private fun createGpuInterpreter(mode: GpuMode, compatibility: CompatibilityList?): Interpreter {
        runCatching { interpreter.close() }
        gpuDelegate?.close()
        gpuDelegate = null
        val options = Interpreter.Options().setNumThreads(threadCount)
        val delegateOptions = gpuOptions(mode, compatibility)
        val delegate = GpuDelegate(delegateOptions)
        options.addDelegate(delegate)
        val gpuInterpreter = runCatching {
            Interpreter(model, options)
        }.getOrElse { error ->
            delegate.close()
            throw error
        }
        gpuDelegate = delegate
        delegateActive = true
        activeGpuMode = mode
        return gpuInterpreter
    }

    private fun gpuOptions(mode: GpuMode, compatibility: CompatibilityList?): GpuDelegate.Options {
        val options = when (mode) {
            GpuMode.BEST_COMPAT -> compatibility?.bestOptionsForThisDevice ?: GpuDelegate.Options()
            else -> GpuDelegate.Options()
        }
        when (mode) {
            GpuMode.ACCURATE_UNSET -> {
                options.setPrecisionLossAllowed(false)
                options.setInferencePreference(GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED)
            }
            GpuMode.ACCURATE_OPENGL -> {
                options.setPrecisionLossAllowed(false)
                options.setInferencePreference(GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED)
                options.setForceBackend(GpuDelegateFactory.Options.GpuBackend.OPENGL)
            }
            GpuMode.ACCURATE_OPENCL -> {
                options.setPrecisionLossAllowed(false)
                options.setInferencePreference(GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED)
                options.setForceBackend(GpuDelegateFactory.Options.GpuBackend.OPENCL)
            }
            GpuMode.BEST_COMPAT -> Unit
        }
        return options
    }

    private fun gpuModeCandidates(): List<GpuMode> {
        return when (gpuTestMode) {
            GpuTestMode.AUTO -> GpuMode.entries
            GpuTestMode.ACCURATE_UNSET -> listOf(GpuMode.ACCURATE_UNSET)
            GpuTestMode.ACCURATE_OPENGL -> listOf(GpuMode.ACCURATE_OPENGL)
            GpuTestMode.ACCURATE_OPENCL -> listOf(GpuMode.ACCURATE_OPENCL)
            GpuTestMode.BEST_COMPAT -> listOf(GpuMode.BEST_COMPAT)
        }
    }

    private fun tryRecoverGpu(cpuReference: DepthRunResult?, reason: String): DepthRunResult? {
        val start = activeGpuMode?.let { GpuMode.entries.indexOf(it) } ?: -1
        for (mode in gpuModeCandidates().filter { GpuMode.entries.indexOf(it) > start }) {
            val created = runCatching { createGpuInterpreter(mode, null) }
            if (created.isFailure) {
                onRuntimeInfo("tflite gpu mode=$mode create failed after $reason: ${created.exceptionOrNull()?.shortMessage()}")
                continue
            }
            input.rewind()
            val result = runInterpreterAndRead()
            if (!result.range.isFinite() || result.range <= 0.0001f) {
                onRuntimeInfo("tflite gpu mode=$mode flat after $reason range=${result.range}")
                continue
            }
            if (cpuReference != null) {
                val quality = compareDepthMaps(result.values, cpuReference.values)
                if (quality.meanAbs > 0.18f || quality.correlation < 0.65f) {
                    onRuntimeInfo("tflite gpu mode=$mode validation failed meanAbs=${quality.meanAbs.format3()} corr=${quality.correlation.format3()}")
                    continue
                }
                onRuntimeInfo("tflite gpu recovered mode=$mode after $reason meanAbs=${quality.meanAbs.format3()} corr=${quality.correlation.format3()} range=${result.range}")
            } else {
                onRuntimeInfo("tflite gpu recovered mode=$mode after $reason range=${result.range}")
            }
            gpuValidated = cpuReference != null
            return result
        }
        return null
    }

    private fun fallbackToCpu(reason: String, reference: DepthRunResult? = null): DepthRunResult {
        onRuntimeInfo("tflite gpu fallback cpu reason=$reason threads=$threadCount")
        runCatching { interpreter.close() }
        gpuDelegate?.close()
        gpuDelegate = null
        delegateActive = false
        activeGpuMode = null
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(threadCount))
        input.rewind()
        val result = reference ?: runInterpreterAndRead()
        onRuntimeInfo("tflite runtime threads=$threadCount requestedGpu=$useGpu delegateActive=false gpuMode=CPU reusedSession=true gpuFallback=$reason cpuRange=${result.range}")
        return result
    }

    private fun runInterpreterAndRead(): DepthRunResult {
        output.clear()
        runCatching {
            interpreter.run(input, output)
        }.onFailure { error ->
            if (!useGpu || !delegateActive) throw error
            if (forceGpuNoFallback) {
                onRuntimeInfo("tflite forced gpu run failed mode=$activeGpuMode error=${error.shortMessage()}")
                throw error
            }
            onRuntimeInfo("tflite gpu run failed; fallback cpu threads=$threadCount error=${error.shortMessage()}")
            runCatching { interpreter.close() }
            gpuDelegate?.close()
            gpuDelegate = null
            delegateActive = false
            interpreter = Interpreter(model, Interpreter.Options().setNumThreads(threadCount))
            input.rewind()
            output.clear()
            interpreter.run(input, output)
            onRuntimeInfo("tflite runtime threads=$threadCount requestedGpu=$useGpu delegateActive=false reusedSession=true gpuFallback=runFailure")
        }
        return readDepthOutput(output)
    }

    private fun runCpuReferenceAndRead(): DepthRunResult {
        val cpuOutput = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 4).order(ByteOrder.nativeOrder())
        val cpu = Interpreter(model, Interpreter.Options().setNumThreads(threadCount))
        return try {
            input.rewind()
            cpuOutput.clear()
            cpu.run(input, cpuOutput)
            readDepthOutput(cpuOutput)
        } finally {
            runCatching { cpu.close() }
        }
    }

    private fun readDepthOutput(buffer: ByteBuffer): DepthRunResult {
        val flat = FloatArray(inputSize * inputSize)
        var minValue = Float.MAX_VALUE
        var maxValue = -Float.MAX_VALUE
        buffer.rewind()
        for (i in flat.indices) {
            val value = buffer.getFloat().takeIf { it.isFinite() } ?: 0f
            flat[i] = value
            minValue = min(minValue, value)
            maxValue = max(maxValue, value)
        }
        val rawRange = maxValue - minValue
        val range = if (rawRange.isFinite()) max(0.0001f, rawRange) else 0.0001f
        for (i in flat.indices) {
            flat[i] = (flat[i] - minValue) / range
        }
        return DepthRunResult(flat, rawRange)
    }

    private fun compareDepthMaps(a: FloatArray, b: FloatArray): DepthCompareResult {
        var count = 0
        var absSum = 0f
        var sumA = 0f
        var sumB = 0f
        var sumAA = 0f
        var sumBB = 0f
        var sumAB = 0f
        var i = 0
        val step = 8
        while (i < a.size && i < b.size) {
            val av = a[i]
            val bv = b[i]
            absSum += abs(av - bv)
            sumA += av
            sumB += bv
            sumAA += av * av
            sumBB += bv * bv
            sumAB += av * bv
            count++
            i += step
        }
        if (count == 0) return DepthCompareResult(1f, 0f)
        val meanAbs = absSum / count.toFloat()
        val numerator = sumAB - (sumA * sumB / count.toFloat())
        val denomA = sumAA - (sumA * sumA / count.toFloat())
        val denomB = sumBB - (sumB * sumB / count.toFloat())
        val denominator = sqrt(max(0f, denomA) * max(0f, denomB))
        val correlation = if (denominator > 0.000001f) numerator / denominator else 0f
        return DepthCompareResult(meanAbs, correlation)
    }

    override fun close() {
        runCatching { interpreter.close() }
        gpuDelegate?.close()
        gpuDelegate = null
    }
}

data class VideoSbsResult(
    val bitmap: Bitmap,
    val timings: VideoFrameTimings,
)

data class VideoDepthResult(
    val source: Bitmap,
    val rawDepth: FloatArray,
    val timings: VideoFrameTimings,
)

private data class VideoFramePlan(
    val durationMs: Long,
    val fps: Int,
    val totalFrames: Int,
    val useFrameIndex: Boolean,
    val metadataFrameCount: Int?,
    val captureFps: Int?,
)

class DepthTemporalSmoother(
    private val previousWeight: Float = 0.62f,
    private val sceneCutThreshold: Float = 0.20f,
    private val localMotionThreshold: Float = 0.08f,
) {
    private var previous: FloatArray? = null

    fun smooth(current: FloatArray): FloatArray {
        val prev = previous
        if (prev == null || prev.size != current.size) {
            previous = current.copyOf()
            return current
        }
        var diffSum = 0f
        var samples = 0
        var i = 0
        while (i < current.size) {
            diffSum += abs(current[i] - prev[i])
            samples++
            i += 16
        }
        val meanDiff = if (samples > 0) diffSum / samples.toFloat() else 1f
        if (meanDiff > sceneCutThreshold) {
            previous = current.copyOf()
            return current
        }
        for (index in current.indices) {
            val diff = abs(current[index] - prev[index])
            val stableRatio = (1f - diff / localMotionThreshold).coerceIn(0f, 1f)
            val localPreviousWeight = previousWeight * stableRatio * stableRatio
            val localCurrentWeight = 1f - localPreviousWeight
            current[index] = prev[index] * localPreviousWeight + current[index] * localCurrentWeight
        }
        previous = current.copyOf()
        return current
    }
}

private class VideoVrGenerator(
    private val context: Context,
    private val cache: VideoVrCacheManager,
    private val frameGenerator: VrGenerator,
) {
    fun generate(
        item: GalleryItem,
        params: VideoGenerationParams,
        onModelProgress: (Float) -> Unit,
        onRuntimeInfo: (String) -> Unit = {},
        onProgress: (Float, Int, Int, Int, Long, VideoFrameTimings, VideoPipelineStats) -> Unit,
    ): VideoCacheEntry {
        val vrParams = params.toVrParams()
        val version = params.cacheVersion()
        val dir = cache.entryDir(item, version)
        val framesDir = File(dir, "frames").also { it.mkdirs() }
        val output = File(dir, "vr_sbs.mp4")
        val logPath = File(dir, "job.log")
        val metaPath = File(dir, "video_params.txt")
        if (output.exists()) output.delete()
        val log = StringBuilder()
        val started = System.currentTimeMillis()
        fun mark(line: String) {
            log.append(System.currentTimeMillis() - started).append("ms ").append(line).append('\n')
            logPath.writeText(log.toString(), Charsets.UTF_8)
        }

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, item.uri)
        val framePlan = buildFramePlan(retriever)
        val durationMs = framePlan.durationMs
        val fps = framePlan.fps
        val totalFrames = framePlan.totalFrames
        mark("start video=${item.displayName} durationMs=$durationMs fps=$fps frames=$totalFrames frameMode=${if (framePlan.useFrameIndex) "index" else "time"} metadataFrames=${framePlan.metadataFrameCount ?: "-"} captureFps=${framePlan.captureFps ?: "-"} version=$version model=${vrParams.depthModel} threads=${vrParams.modelThreads} depthWorkers=${params.depthWorkers.coerceIn(1, 2)} useGpu=${vrParams.useGpu}")

        var width = 0
        var height = 0
        val temporalSmoother = DepthTemporalSmoother()
        frameGenerator.openDepthSession(vrParams, onModelProgress, onRuntimeInfo).use { depthSession ->
            val first = decodeVideoFrame(retriever, 0, framePlan) ?: error("Unable to decode first video frame")
            val firstSource = first.copy(Bitmap.Config.ARGB_8888, false)
            val firstGenerated = frameGenerator.generateVideoSbsBitmap(firstSource, vrParams, depthSession, temporalSmoother).bitmap
            val cachedFirst = readCachedSbsFrame(framesDir, 0)?.also { onRuntimeInfo("frame cache hit frame=0 version=$version") }
            val firstSbs = cachedFirst ?: firstGenerated.also { bitmap ->
                writeStereoFrameCache(framesDir, 0, bitmap)
            }
            if (cachedFirst != null) firstGenerated.recycle()
            width = even(firstSbs.width)
            height = even(firstSbs.height)
            first.recycle()
            mark("first frame sbs=${firstSbs.width}x${firstSbs.height} encode=${width}x${height}")

            encodeVideo(
                item = item,
                retriever = retriever,
                firstSbs = firstSbs,
                framesDir = framesDir,
                output = output,
                width = width,
                height = height,
                fps = fps,
                totalFrames = totalFrames,
                framePlan = framePlan,
                version = version,
                params = vrParams,
                videoParams = params,
                depthSession = depthSession,
                temporalSmoother = temporalSmoother,
                onModelProgress = onModelProgress,
                onRuntimeInfo = onRuntimeInfo,
                onProgress = onProgress,
                mark = ::mark,
            )
        }
        retriever.release()

        metaPath.writeText(
            listOf(
                "width=$width",
                "height=$height",
                "durationMs=$durationMs",
                "fps=$fps",
                "frameMode=${if (framePlan.useFrameIndex) "index" else "time"}",
                "metadataFrameCount=${framePlan.metadataFrameCount ?: 0}",
                "captureFps=${framePlan.captureFps ?: 0}",
                "source=${item.displayName}",
                "depthModel=${vrParams.depthModel}",
                "cacheVersion=$version",
                "encoderVersion=$VIDEO_ENCODER_VERSION",
                "frameCache=split_webp_lossless",
                "modelThreads=${vrParams.modelThreads}",
                "depthWorkers=${params.depthWorkers.coerceIn(1, 2)}",
                "useGpu=${vrParams.useGpu}",
                "gpuTestMode=${vrParams.gpuTestMode}",
                "forceGpuNoFallback=${vrParams.forceGpuNoFallback}",
                "maxLongEdge=${vrParams.maxLongEdge}",
            ).joinToString("\n"),
            Charsets.UTF_8,
        )
        mark("done output=${output.absolutePath}")
        return cache.findEntry(item) ?: error("Video output cache missing")
    }

    private fun buildFramePlan(retriever: MediaMetadataRetriever): VideoFramePlan {
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.coerceAtLeast(1L)
            ?: 1L
        val captureFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            ?.toFloatOrNull()
            ?.roundToInt()
            ?.coerceIn(1, 60)
        val metadataFrameCount = if (Build.VERSION.SDK_INT >= 28) {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
        } else {
            null
        }
        val derivedFps = metadataFrameCount
            ?.let { ((it * 1000f) / durationMs.toFloat()).roundToInt().coerceIn(1, 60) }
        val fps = derivedFps ?: captureFps ?: 30
        val totalFrames = metadataFrameCount ?: ((durationMs / 1000f) * fps).roundToInt().coerceAtLeast(1)
        return VideoFramePlan(
            durationMs = durationMs,
            fps = fps,
            totalFrames = totalFrames,
            useFrameIndex = metadataFrameCount != null && Build.VERSION.SDK_INT >= 28,
            metadataFrameCount = metadataFrameCount,
            captureFps = captureFps,
        )
    }

    private fun decodeVideoFrame(
        retriever: MediaMetadataRetriever,
        frame: Int,
        plan: VideoFramePlan,
    ): Bitmap? {
        if (plan.useFrameIndex && Build.VERSION.SDK_INT >= 28) {
            runCatching {
                retriever.getFrameAtIndex(frame.coerceIn(0, plan.totalFrames - 1))
            }.getOrNull()?.let { return it }
        }
        val timeUs = frame * 1_000_000L / plan.fps
        return retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }

    private fun encodeVideo(
        item: GalleryItem,
        retriever: MediaMetadataRetriever,
        firstSbs: Bitmap,
        framesDir: File,
        output: File,
        width: Int,
        height: Int,
        fps: Int,
        totalFrames: Int,
        framePlan: VideoFramePlan,
        version: String,
        params: VrGenerationParams,
        videoParams: VideoGenerationParams,
        depthSession: DepthModelSession,
        temporalSmoother: DepthTemporalSmoother,
        onModelProgress: (Float) -> Unit,
        onRuntimeInfo: (String) -> Unit,
        onProgress: (Float, Int, Int, Int, Long, VideoFrameTimings, VideoPipelineStats) -> Unit,
        mark: (String) -> Unit,
    ) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (width * height * fps * 0.08f).roundToInt().coerceAtLeast(2_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val audio = prepareAudioExtractor(item)
        val audioTrack = audio?.format?.let { muxer.addTrack(it) } ?: -1
        var videoTrack = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        fun drain(end: Boolean) {
            if (end) codec.signalEndOfInputStream()
            while (true) {
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!end) return
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        videoTrack = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outputIndex >= 0 -> {
                        val encoded = codec.getOutputBuffer(outputIndex)
                        if (encoded != null && bufferInfo.size > 0 && muxerStarted) {
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrack, encoded, bufferInfo)
                        }
                        val flags = bufferInfo.flags
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                    }
                }
            }
        }

        var renderer: InputSurfaceRenderer? = null
        try {
            val activeRenderer = InputSurfaceRenderer(surface, width, height)
            renderer = activeRenderer
            data class FrameResult(val bitmap: Bitmap, val generatedMs: Long, val timings: VideoFrameTimings)
            data class PipelineInput(val frame: Int, val source: Bitmap?, val cachedSbs: Bitmap?, val decodeMs: Long)
            data class PipelineDepthOutput(val frame: Int, val source: Bitmap?, val cachedSbs: Bitmap?, val depth: VideoDepthResult?, val decodeMs: Long, val depthStartedAt: Long)
            data class PipelineOutput(val frame: Int, val result: FrameResult)

            val decodedChannel = Channel<PipelineInput>(capacity = 2)
            val depthChannel = Channel<PipelineDepthOutput>(capacity = 2)
            val generatedChannel = Channel<PipelineOutput>(capacity = 2)
            val decodeQueueSize = AtomicInteger(0)
            val generatedQueueSize = AtomicInteger(0)
            val cacheHits = AtomicInteger(0)
            val depthWorkerCount = videoParams.depthWorkers.coerceIn(1, 2)
            var timingWindow = VideoFrameTimings()
            var timingWindowCount = 0

            fun stats(waitingFrames: Int): VideoPipelineStats {
                return VideoPipelineStats(
                    decodeQueue = decodeQueueSize.get().coerceAtLeast(0),
                    generatedQueue = generatedQueueSize.get().coerceAtLeast(0),
                    waitingFrames = waitingFrames,
                    cacheHits = cacheHits.get(),
                )
            }

            fun addTiming(a: VideoFrameTimings, b: VideoFrameTimings): VideoFrameTimings {
                return VideoFrameTimings(
                    decodeMs = a.decodeMs + b.decodeMs,
                    depthMs = a.depthMs + b.depthMs,
                    temporalMs = a.temporalMs + b.temporalMs,
                    depthPostMs = a.depthPostMs + b.depthPostMs,
                    sbsMs = a.sbsMs + b.sbsMs,
                    cacheWriteMs = a.cacheWriteMs + b.cacheWriteMs,
                    encodeMs = a.encodeMs + b.encodeMs,
                )
            }

            fun averageTiming(total: VideoFrameTimings, count: Int): VideoFrameTimings {
                if (count <= 0) return VideoFrameTimings()
                return VideoFrameTimings(
                    decodeMs = total.decodeMs / count,
                    depthMs = total.depthMs / count,
                    temporalMs = total.temporalMs / count,
                    depthPostMs = total.depthPostMs / count,
                    sbsMs = total.sbsMs / count,
                    cacheWriteMs = total.cacheWriteMs / count,
                    encodeMs = total.encodeMs / count,
                )
            }

            fun encodeFrame(frame: Int, frameResult: FrameResult, waitingFrames: Int) {
                val sbs = frameResult.bitmap
                val presentationTimeUs = frame * 1_000_000L / fps
                val encodeStart = SystemClock.uptimeMillis()
                activeRenderer.draw(sbs, presentationTimeUs)
                if (frame != 0) sbs.recycle()
                drain(end = false)
                val encodeMs = SystemClock.uptimeMillis() - encodeStart
                val timings = frameResult.timings.copy(encodeMs = encodeMs)
                val effectiveMs = frameResult.generatedMs.takeIf { it > 0L } ?: timings.totalMs
                timingWindow = addTiming(timingWindow, timings)
                timingWindowCount++
                if (timingWindowCount >= 30) {
                    val avg = averageTiming(timingWindow, timingWindowCount)
                    mark("pipeline avg ${timingWindowCount} frames decode=${avg.decodeMs} depth=${avg.depthMs} temporal=${avg.temporalMs} depthPost=${avg.depthPostMs} sbs=${avg.sbsMs} cache=${avg.cacheWriteMs} encode=${avg.encodeMs} cacheHits=${cacheHits.get()}")
                    timingWindow = VideoFrameTimings()
                    timingWindowCount = 0
                }
                onProgress((frame + 1).toFloat() / totalFrames.toFloat(), frame + 1, totalFrames, fps, effectiveMs, timings, stats(waitingFrames))
            }

            runBlocking {
                val decodeJob = launch(Dispatchers.IO) {
                    try {
                        for (frame in 1 until totalFrames) {
                            val decodeStart = SystemClock.uptimeMillis()
                            val cached = readCachedSbsFrame(framesDir, frame)
                            if (cached != null) {
                                val decodeMs = SystemClock.uptimeMillis() - decodeStart
                                onRuntimeInfo("frame cache hit frame=$frame version=$version")
                                decodeQueueSize.incrementAndGet()
                                decodedChannel.send(PipelineInput(frame, source = null, cachedSbs = cached, decodeMs = decodeMs))
                                continue
                            }
                            val bitmap = decodeVideoFrame(retriever, frame, framePlan)
                                ?: error("Unable to decode video frame $frame")
                            val source = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                            bitmap.recycle()
                            val decodeMs = SystemClock.uptimeMillis() - decodeStart
                            decodeQueueSize.incrementAndGet()
                            decodedChannel.send(PipelineInput(frame, source = source, cachedSbs = null, decodeMs = decodeMs))
                        }
                    } finally {
                        decodedChannel.close()
                    }
                }

                val depthJobs = (0 until depthWorkerCount).map { workerIndex ->
                    launch(Dispatchers.IO) {
                        val workerSession = if (workerIndex == 0) {
                            depthSession
                        } else {
                            frameGenerator.openDepthSession(params, onModelProgress, onRuntimeInfo)
                        }
                        try {
                            for (inputFrame in decodedChannel) {
                                decodeQueueSize.decrementAndGet()
                                if (inputFrame.cachedSbs != null) {
                                    cacheHits.incrementAndGet()
                                    depthChannel.send(
                                        PipelineDepthOutput(
                                            frame = inputFrame.frame,
                                            source = null,
                                            cachedSbs = inputFrame.cachedSbs,
                                            depth = null,
                                            decodeMs = inputFrame.decodeMs,
                                            depthStartedAt = 0L,
                                        ),
                                    )
                                } else {
                                    val source = inputFrame.source ?: error("Missing source frame ${inputFrame.frame}")
                                    val started = SystemClock.uptimeMillis()
                                    val depth = frameGenerator.generateVideoDepth(source, params, workerSession)
                                    depthChannel.send(
                                        PipelineDepthOutput(
                                            frame = inputFrame.frame,
                                            source = source,
                                            cachedSbs = null,
                                            depth = depth,
                                            decodeMs = inputFrame.decodeMs,
                                            depthStartedAt = started,
                                        ),
                                    )
                                }
                            }
                        } finally {
                            if (workerIndex != 0) workerSession.close()
                        }
                    }
                }

                val depthCloserJob = launch(Dispatchers.Default) {
                    depthJobs.forEach { it.join() }
                    depthChannel.close()
                }

                val generateJob = launch(Dispatchers.IO) {
                    try {
                        val pendingDepthFrames = mutableMapOf<Int, PipelineDepthOutput>()
                        var expectedDepthFrame = 1
                        while (expectedDepthFrame < totalFrames) {
                            val depthOutput = depthChannel.receiveCatching().getOrNull() ?: break
                            pendingDepthFrames[depthOutput.frame] = depthOutput
                            var lastWaitingFrames = pendingDepthFrames.size
                            while (true) {
                                val inputFrame = pendingDepthFrames.remove(expectedDepthFrame) ?: break
                                lastWaitingFrames = pendingDepthFrames.size
                                val started = SystemClock.uptimeMillis()
                                val output = if (inputFrame.cachedSbs != null) {
                                    PipelineOutput(
                                        inputFrame.frame,
                                        FrameResult(
                                            bitmap = inputFrame.cachedSbs,
                                            generatedMs = 0L,
                                            timings = VideoFrameTimings(decodeMs = inputFrame.decodeMs),
                                        ),
                                    )
                                } else {
                                    val depth = inputFrame.depth ?: error("Missing depth frame ${inputFrame.frame}")
                                    val generatedResult = frameGenerator.finishVideoSbs(depth, params, temporalSmoother)
                                    if (inputFrame.source != depth.source) inputFrame.source?.recycle()
                                    val cacheStart = SystemClock.uptimeMillis()
                                    writeStereoFrameCache(framesDir, inputFrame.frame, generatedResult.bitmap)
                                    val cacheWriteMs = SystemClock.uptimeMillis() - cacheStart
                                    PipelineOutput(
                                        inputFrame.frame,
                                        FrameResult(
                                            bitmap = generatedResult.bitmap,
                                            generatedMs = if (inputFrame.depthStartedAt > 0L) {
                                                SystemClock.uptimeMillis() - inputFrame.depthStartedAt
                                            } else {
                                                SystemClock.uptimeMillis() - started
                                            },
                                            timings = generatedResult.timings.copy(
                                                decodeMs = inputFrame.decodeMs,
                                                cacheWriteMs = cacheWriteMs,
                                            ),
                                        ),
                                    )
                                }
                                generatedQueueSize.incrementAndGet()
                                generatedChannel.send(output)
                                expectedDepthFrame++
                            }
                            if (lastWaitingFrames > 0) {
                                onProgress(
                                    expectedDepthFrame.toFloat() / totalFrames.toFloat(),
                                    expectedDepthFrame,
                                    totalFrames,
                                    fps,
                                    0L,
                                    VideoFrameTimings(),
                                    stats(lastWaitingFrames),
                                )
                            }
                        }
                        if (expectedDepthFrame < totalFrames) {
                            error("Depth pipeline ended before frame $expectedDepthFrame/$totalFrames")
                        }
                    } finally {
                        generatedChannel.close()
                    }
                }

                encodeFrame(0, FrameResult(firstSbs, 0L, VideoFrameTimings()), waitingFrames = 0)
                val pendingFrames = mutableMapOf<Int, FrameResult>()
                var expectedFrame = 1
                while (expectedFrame < totalFrames) {
                    val output = generatedChannel.receiveCatching().getOrNull() ?: break
                    generatedQueueSize.decrementAndGet()
                    pendingFrames[output.frame] = output.result
                    while (true) {
                        val nextResult = pendingFrames.remove(expectedFrame) ?: break
                        encodeFrame(expectedFrame, nextResult, waitingFrames = pendingFrames.size)
                        expectedFrame++
                    }
                }
                if (expectedFrame < totalFrames) {
                    decodeJob.cancel()
                    depthJobs.forEach { it.cancel() }
                    depthCloserJob.cancel()
                    generateJob.cancel()
                    error("Pipeline ended before frame $expectedFrame/$totalFrames")
                }
                decodeJob.join()
                depthCloserJob.join()
                generateJob.join()
                if (timingWindowCount > 0) {
                    val avg = averageTiming(timingWindow, timingWindowCount)
                    mark("pipeline avg ${timingWindowCount} frames decode=${avg.decodeMs} depth=${avg.depthMs} temporal=${avg.temporalMs} depthPost=${avg.depthPostMs} sbs=${avg.sbsMs} cache=${avg.cacheWriteMs} encode=${avg.encodeMs} cacheHits=${cacheHits.get()}")
                }
            }
            drain(end = true)
            if (audio != null && audioTrack >= 0 && muxerStarted) {
                copyAudio(audio.extractor, muxer, audioTrack)
                mark("audio copied")
            }
        } finally {
            firstSbs.recycle()
            renderer?.release()
            runCatching { codec.stop() }
            codec.release()
            surface.release()
            audio?.extractor?.release()
            runCatching { muxer.stop() }
            muxer.release()
        }
    }

    private data class AudioSource(val extractor: MediaExtractor, val format: MediaFormat)

    private fun prepareAudioExtractor(item: GalleryItem): AudioSource? {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, item.uri, null)
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                extractor.selectTrack(i)
                return AudioSource(extractor, format)
            }
        }
        extractor.release()
        return null
    }

    private fun copyAudio(extractor: MediaExtractor, muxer: MediaMuxer, track: Int) {
        val buffer = ByteBuffer.allocateDirect(512 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.set(0, size, extractor.sampleTime.coerceAtLeast(0L), extractor.sampleFlags)
            muxer.writeSampleData(track, buffer, info)
            extractor.advance()
        }
    }

    private fun drawBitmapToSurface(surface: Surface, bitmap: Bitmap, width: Int, height: Int) {
        val canvas = surface.lockCanvas(null)
        try {
            canvas.drawColor(Color.BLACK)
            val scaled = if (bitmap.width == width && bitmap.height == height) bitmap else Bitmap.createScaledBitmap(bitmap, width, height, true)
            canvas.drawBitmap(scaled, 0f, 0f, null)
            if (scaled !== bitmap) scaled.recycle()
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    private fun even(value: Int): Int = if (value % 2 == 0) value else value - 1
}

private class InputSurfaceRenderer(
    private val surface: Surface,
    private val width: Int,
    private val height: Int,
) {
    private val eglDisplay: EGLDisplay
    private val eglContext: EGLContext
    private val eglSurface: EGLSurface
    private val program: Int
    private val textureId: Int
    private val positionBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    init {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "Unable to initialize EGL" }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)) { "Unable to choose EGL config" }
        val eglConfig = configs[0] ?: error("No EGL config")
        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            eglConfig,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0,
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context" }
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL window surface" }
        makeCurrent()

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        textureId = createTexture()
        positionBuffer = floatBufferOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f,
        )
        texCoordBuffer = floatBufferOf(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f,
        )
    }

    fun draw(bitmap: Bitmap, presentationTimeUs: Long) {
        makeCurrent()
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        val textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        GLES20.glUniform1i(textureHandle, 0)

        positionBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, positionBuffer)
        texCoordBuffer.position(0)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)

        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeUs * 1000L)
        check(EGL14.eglSwapBuffers(eglDisplay, eglSurface)) { "Unable to swap EGL buffers" }
    }

    fun release() {
        makeCurrent()
        GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        GLES20.glDeleteProgram(program)
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
    }

    private fun makeCurrent() {
        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) { "Unable to make EGL context current" }
    }

    private fun createTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return textures[0]
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        check(linkStatus[0] == GLES20.GL_TRUE) { "Unable to link GL program: ${GLES20.glGetProgramInfoLog(program)}" }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "Unable to compile GL shader: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }

    private fun floatBufferOf(vararg values: Float): FloatBuffer {
        return ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }
}

private class DebugExporter(
    private val context: Context,
    private val cache: VrCacheManager,
) {
    fun export(photo: PhotoItem, entry: VrCacheEntry): File {
        val exportDir = File(context.getExternalFilesDir(null), "debug_exports").also { it.mkdirs() }
        val safeName = photo.displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val zip = File(exportDir, "${photo.cacheKey}_$safeName.zip")
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            val preview = File(cache.entryDir(photo, entry.version), "source_preview.jpg")
            decodeScaledBitmap(context, photo.uri, 1600).also { bitmap ->
                FileOutputStream(preview).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            }
            out.addFile("source_preview.jpg", preview)
            out.addFile("depth.png", File(entry.depthPath))
            if (entry.hasSplitEyes) {
                out.addFile("left.webp", File(entry.leftPath))
                out.addFile("right.webp", File(entry.rightPath))
            }
            if (entry.hasLegacySbs) {
                out.addFile("vr_sbs.jpg", File(entry.outputPath))
            } else {
                out.addFile("export_sbs.jpg", materializeSbsJpeg(entry))
            }
            out.addFile("params.json", File(entry.paramsPath))
            out.addFile("job.log", File(entry.logPath))
        }
        return zip
    }
}

private sealed class AppScreen {
    data object Gallery : AppScreen()
    data object Settings : AppScreen()
    data object Manage : AppScreen()
    data object Viewer : AppScreen()
    data object Debug : AppScreen()
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GalleryApp(viewModel: GalleryViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingReplaceIndexes by remember { mutableStateOf<List<Int>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        viewModel.onPermissionChanged(
            imageGranted = grants[imagePermission()] == true || hasImagePermission(context),
            videoGranted = grants[videoPermission()] == true || hasVideoPermission(context),
            notificationGranted = grants[notificationPermission()] == true || hasNotificationPermission(context),
        )
    }
    val replaceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingReplaceIndexes.isNotEmpty()) {
            viewModel.replaceOriginalsWithGenerated(context, pendingReplaceIndexes)
        }
        pendingReplaceIndexes = emptyList()
    }

    LaunchedEffect(state.hasPermission, state.hasVideoPermission, state.hasNotificationPermission) {
        if (!state.hasPermission) {
            launcher.launch(mediaPermissions())
        } else if (!state.hasVideoPermission && Build.VERSION.SDK_INT >= 33) {
            launcher.launch(arrayOf(Manifest.permission.READ_MEDIA_VIDEO))
        } else if (!state.hasNotificationPermission && Build.VERSION.SDK_INT >= 33) {
            launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    if (!state.hasPermission) {
        PermissionScreen(state.settings.language) { launcher.launch(mediaPermissions()) }
        return
    }

    state.blockingMessage?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(message) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(state.settings.language.t("请稍等", "Please wait"))
                }
            },
        )
    }

    val screen = when {
        state.manageOpen -> AppScreen.Manage
        state.settingsOpen -> AppScreen.Settings
        state.debugIndex != null -> AppScreen.Debug
        state.selectedIndex != null -> AppScreen.Viewer
        else -> AppScreen.Gallery
    }
    BackHandler(enabled = screen is AppScreen.Debug) { viewModel.closeDebug() }
    BackHandler(enabled = screen is AppScreen.Viewer) { viewModel.closeViewer() }
    BackHandler(enabled = screen is AppScreen.Manage) { viewModel.closeManage() }
    BackHandler(enabled = screen is AppScreen.Settings) { viewModel.closeSettings() }
    BackHandler(enabled = screen is AppScreen.Gallery && state.homeTab == "generated" && state.selectedGeneratedVersion != null) {
        viewModel.closeGeneratedVersion()
    }
    BackHandler(enabled = screen is AppScreen.Gallery && state.homeTab == "albums" && state.selectedAlbumId != null) {
        viewModel.closeAlbum()
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.96f))
        },
        label = "screenTransition",
    ) { target ->
        when (target) {
            AppScreen.Manage -> ManageScreen(
                state = state,
                onBack = viewModel::closeManage,
                selectedTab = state.generatedTab,
                onTabChange = viewModel::setGeneratedTab,
                onToggleVersion = viewModel::toggleGeneratedVersion,
                onOpenVersion = viewModel::openGeneratedVersion,
                onCloseVersion = viewModel::closeGeneratedVersion,
                onSetGeneratedColumns = { viewModel.setPageColumns("generated", it) },
                onGeneratedScroll = viewModel::setGeneratedScroll,
                onGeneratedVersionScroll = viewModel::setGeneratedVersionScroll,
                onDeleteVersion = viewModel::deleteCacheVersion,
                onOpenGenerated = viewModel::openGeneratedPhoto,
                onOpenGeneratedVideo = viewModel::openGeneratedVideo,
                onSaveVideo = { viewModel.saveGeneratedVideo(context, it) },
                onDeleteVideo = viewModel::deleteGeneratedVideo,
                onSaveVideos = { viewModel.saveGeneratedVideos(context, it) },
                onDeleteVideos = viewModel::deleteGeneratedVideos,
                onRegenerateVideos = viewModel::regenerateVideos,
                onSaveImages = { viewModel.saveGeneratedCopies(context, it) },
                onDeleteImages = viewModel::deleteGeneratedImageEntries,
                onRegenerateImages = viewModel::regenerateImages,
            )
            AppScreen.Settings -> SettingsScreen(
                settings = state.settings,
                updateStatus = state.updateStatus,
                updateUrl = state.updateUrl,
                updateAvailable = state.updateAvailable,
                updateDownloading = state.updateDownloading,
                updateDownloadProgress = state.updateDownloadProgress,
                updateApkPath = state.updateApkPath,
                onBack = viewModel::closeSettings,
                onChange = viewModel::updateSettings,
                onCheckUpdate = viewModel::checkForUpdates,
                onDownloadUpdate = viewModel::downloadUpdateApk,
                onInstallUpdate = { viewModel.installDownloadedUpdate(context) },
            )
            AppScreen.Debug -> DebugScreen(
                state = state,
                index = state.debugIndex ?: state.galleryAnchorIndex,
                onBack = viewModel::closeDebug,
                onShare = { viewModel.exportDebug(context, state.debugIndex ?: state.galleryAnchorIndex) },
            )
            AppScreen.Viewer -> ViewerScreen(
                state = state,
                startIndex = state.selectedIndex ?: state.galleryAnchorIndex,
                onClose = viewModel::closeViewer,
                onIndexChanged = viewModel::onPagerIndexChanged,
                onVr = viewModel::requestVr,
                onRetry = viewModel::retry,
                onOpenDebug = viewModel::openDebug,
            )
            AppScreen.Gallery -> GalleryScreen(
                state = state,
                onRefresh = viewModel::loadPhotos,
                onOpen = viewModel::openPhoto,
                onOpenScoped = viewModel::openScopedPhoto,
                onSettings = viewModel::openSettings,
                onSetTab = viewModel::setHomeTab,
                onSetGeneratedTab = viewModel::setGeneratedTab,
                onSetPageColumns = viewModel::setPageColumns,
                onGeneratedScroll = viewModel::setGeneratedScroll,
                onGeneratedVersionScroll = viewModel::setGeneratedVersionScroll,
                onToggleGeneratedVersion = viewModel::toggleGeneratedVersion,
                onOpenGeneratedVersion = viewModel::openGeneratedVersion,
                onCloseGeneratedVersion = viewModel::closeGeneratedVersion,
                onOpenAlbum = viewModel::openAlbum,
                onCloseAlbum = viewModel::closeAlbum,
                onLoadMoreAll = viewModel::loadMoreAllMedia,
                onLoadMoreAlbum = viewModel::loadMoreSelectedAlbum,
                onSaveGenerated = { viewModel.saveGeneratedCopies(context, it) },
                onReplaceOriginal = { indexes ->
                    if (Build.VERSION.SDK_INT >= 30) {
                        val uris = indexes.mapNotNull { state.photos.getOrNull(it)?.uri }
                        if (uris.isNotEmpty()) {
                            pendingReplaceIndexes = indexes
                            val request = MediaStore.createWriteRequest(context.contentResolver, uris)
                            replaceLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                        }
                    } else {
                        viewModel.replaceOriginalsWithGenerated(context, indexes)
                    }
                },
                onDeleteVersion = viewModel::deleteCacheVersion,
                onOpenGenerated = viewModel::openGeneratedPhoto,
                onOpenGeneratedVideo = viewModel::openGeneratedVideo,
                onSaveVideo = { viewModel.saveGeneratedVideo(context, it) },
                onDeleteVideo = viewModel::deleteGeneratedVideo,
                onSaveVideos = { viewModel.saveGeneratedVideos(context, it) },
                onDeleteVideos = viewModel::deleteGeneratedVideos,
                onRegenerateVideos = viewModel::regenerateVideos,
                onSaveImages = { viewModel.saveGeneratedCopies(context, it) },
                onDeleteImages = viewModel::deleteGeneratedImageEntries,
                onRegenerateImages = viewModel::regenerateImages,
            )
        }
    }
}

@Composable
private fun PermissionScreen(lang: AppLanguage, onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(lang.t("平行眼 VR 图库", "Parallel VR Gallery"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(lang.t("授权读取图片和视频后，可以浏览系统相册，并在本地生成平行眼 SBS VR 缓存。", "Allow image and video access to browse your gallery and build local parallel-eye VR cache."))
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGrant) { Text(lang.t("授权图片和视频访问", "Grant access")) }
    }
}

private data class GeneratedSelectionActionsState(
    val count: Int,
    val onClear: () -> Unit,
    val onSave: () -> Unit,
    val onRegenerate: () -> Unit,
    val onDelete: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onOpen: (Int, Int, Int) -> Unit,
    onOpenScoped: (Int, List<String>, Int, Int) -> Unit,
    onSettings: () -> Unit,
    onSetTab: (String) -> Unit,
    onSetGeneratedTab: (String) -> Unit,
    onSetPageColumns: (String, Int) -> Unit,
    onGeneratedScroll: (String, Int, Int) -> Unit,
    onGeneratedVersionScroll: (String, Int, Int) -> Unit,
    onToggleGeneratedVersion: (String) -> Unit,
    onOpenGeneratedVersion: (String) -> Unit,
    onCloseGeneratedVersion: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onCloseAlbum: () -> Unit,
    onLoadMoreAll: () -> Unit,
    onLoadMoreAlbum: () -> Unit,
    onSaveGenerated: (List<Int>) -> Unit,
    onReplaceOriginal: (List<Int>) -> Unit,
    onDeleteVersion: (String) -> Unit,
    onOpenGenerated: (Int, VrCacheEntry) -> Unit,
    onOpenGeneratedVideo: (Int) -> Unit,
    onSaveVideo: (Int) -> Unit,
    onDeleteVideo: (Int) -> Unit,
    onSaveVideos: (List<Int>) -> Unit,
    onDeleteVideos: (List<Int>) -> Unit,
    onRegenerateVideos: (List<Int>) -> Unit,
    onSaveImages: (List<Int>) -> Unit,
    onDeleteImages: (List<ManagedCacheItem>) -> Unit,
    onRegenerateImages: (List<Int>) -> Unit,
) {
    val lang = state.settings.language
    val density = LocalDensity.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val oldStatusBarColor = window?.statusBarColor
        val oldSystemUiVisibility = window?.decorView?.systemUiVisibility
        if (Build.VERSION.SDK_INT >= 21) {
            window?.statusBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= 23) {
            window?.decorView?.systemUiVisibility =
                (oldSystemUiVisibility ?: 0) or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= 21 && oldStatusBarColor != null) {
                window?.statusBarColor = oldStatusBarColor
            }
            if (Build.VERSION.SDK_INT >= 23 && oldSystemUiVisibility != null) {
                window?.decorView?.systemUiVisibility = oldSystemUiVisibility
            }
        }
    }
    var lastPinchAt by remember { mutableStateOf(0L) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var generatedSelectionActions by remember { mutableStateOf<GeneratedSelectionActionsState?>(null) }
    val systemItems = remember(state.photos) { state.photos.filterNot { it.generatedVirtual } }
    val albumItems = remember(systemItems, state.selectedAlbumId) {
        state.selectedAlbumId?.let { bucketId -> systemItems.filter { it.bucketId == bucketId } } ?: emptyList()
    }
    val visibleItems = remember(systemItems, albumItems, state.homeTab, state.selectedAlbumId) {
        if (state.homeTab == "albums" && state.selectedAlbumId != null) albumItems else systemItems
    }
    val photoIndexByKey = remember(state.photos) { state.photos.mapIndexed { index, item -> item.cacheKey to index }.toMap() }
    val visibleScopeKeys = remember(visibleItems, state.homeTab, state.selectedAlbumId) {
        if (state.homeTab == "generated") emptyList() else visibleItems.map { it.cacheKey }
    }
    val selectedIndexes = remember(selectedKeys, photoIndexByKey) {
        if (selectedKeys.isEmpty()) emptyList() else selectedKeys.mapNotNull { photoIndexByKey[it] }
    }
    val allGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = state.galleryScrollIndex.coerceAtLeast(0),
        initialFirstVisibleItemScrollOffset = state.galleryScrollOffset.coerceAtLeast(0),
    )
    val albumListGridState = rememberLazyGridState()
    val albumDetailGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = state.galleryScrollIndex.coerceAtLeast(0),
        initialFirstVisibleItemScrollOffset = state.galleryScrollOffset.coerceAtLeast(0),
    )
    val gridState = if (state.homeTab == "albums" && state.selectedAlbumId != null) albumDetailGridState else allGridState
    val timelineColumns = if (state.homeTab == "albums" && state.selectedAlbumId != null) state.albumDetailColumns else state.allColumns
    var topBarHeightPx by remember { mutableStateOf(0) }
    val topContentPadding = with(density) { topBarHeightPx.toDp() }
    val topBarStatusText = if (state.homeTab == "generated" && state.selectedGeneratedVersion != null) {
        state.selectedGeneratedVersion
    } else {
        state.message?.let { lang.pickMixed(it) }
    }
    val minTopListPadding = if (topBarStatusText != null) 128.dp else 104.dp
    val topListPadding = if (topContentPadding < minTopListPadding) minTopListPadding else topContentPadding
    val topBarScrollOffset = when {
        state.homeTab == "generated" && state.generatedTab == "videos" -> state.generatedVideoScrollIndex * 240 + state.generatedVideoScrollOffset
        state.homeTab == "generated" && state.selectedGeneratedVersion != null -> {
            val version = state.selectedGeneratedVersion
            (state.generatedVersionScrollIndexes[version] ?: 0) * 240 + (state.generatedVersionScrollOffsets[version] ?: 0)
        }
        state.homeTab == "generated" -> state.generatedImageScrollIndex * 240 + state.generatedImageScrollOffset
        state.homeTab == "albums" && state.selectedAlbumId == null -> albumListGridState.firstVisibleItemIndex * 240 + albumListGridState.firstVisibleItemScrollOffset
        else -> gridState.firstVisibleItemIndex * 240 + gridState.firstVisibleItemScrollOffset
    }
    val topBarOverlap = topBarScrollOffset.coerceAtLeast(0).toFloat()
    val topBarAlpha = if (topBarOverlap <= 0f || topBarHeightPx <= 0) {
        1f
    } else {
        (1f - (topBarOverlap / topBarHeightPx.toFloat()).coerceIn(0f, 1f) * 0.58f).coerceIn(0.42f, 1f)
    }
    val topBarControlsAlpha = if (topBarOverlap <= 0f || topBarHeightPx <= 0) {
        1f
    } else {
        (1f - (topBarOverlap / topBarHeightPx.toFloat()).coerceIn(0f, 1f) * 0.42f).coerceIn(0.58f, 1f)
    }
    LaunchedEffect(state.homeTab) {
        if (state.homeTab != "generated") generatedSelectionActions = null
    }

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xfff7f8f9))) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
        ) { padding ->
            if (state.loading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.homeTab == "generated") {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    ManageScreen(
                        state = state,
                        embedded = true,
                        onBack = {},
                        selectedTab = state.generatedTab,
                        onTabChange = onSetGeneratedTab,
                        onToggleVersion = onToggleGeneratedVersion,
                        onOpenVersion = onOpenGeneratedVersion,
                        onCloseVersion = onCloseGeneratedVersion,
                        contentTopPadding = topListPadding + 8.dp,
                        onSetGeneratedColumns = { onSetPageColumns("generated", it) },
                        onGeneratedScroll = onGeneratedScroll,
                        onGeneratedVersionScroll = onGeneratedVersionScroll,
                        onDeleteVersion = onDeleteVersion,
                        onOpenGenerated = onOpenGenerated,
                        onOpenGeneratedVideo = onOpenGeneratedVideo,
                        onSaveVideo = onSaveVideo,
                        onDeleteVideo = onDeleteVideo,
                        onSaveVideos = onSaveVideos,
                        onDeleteVideos = onDeleteVideos,
                        onRegenerateVideos = onRegenerateVideos,
                        onSaveImages = onSaveImages,
                        onDeleteImages = onDeleteImages,
                        onRegenerateImages = onRegenerateImages,
                        onGeneratedSelectionChange = { generatedSelectionActions = it },
                    )
                }
            } else if (state.homeTab == "albums" && state.selectedAlbumId == null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(state.albumListColumns),
                        state = albumListGridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .discreteColumnPinch(
                                columns = state.albumListColumns,
                                onColumns = { onSetPageColumns("albumList", it) },
                                onPinchActivity = { lastPinchAt = System.currentTimeMillis() },
                            ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, top = topListPadding + 14.dp, end = 14.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(state.albums, key = { it.bucketId }) { album ->
                            AlbumTile(album = album, lang = lang, columns = state.albumListColumns, onClick = { onOpenAlbum(album.bucketId) })
                        }
                    }
                    GridScrollbar(albumListGridState, Modifier.align(Alignment.CenterEnd).padding(end = 2.dp))
                    GridToTopButton(albumListGridState, lang, Modifier.align(Alignment.BottomEnd).padding(end = 26.dp, bottom = 18.dp))
                }
            } else {
                TimelineGrid(
                    items = visibleItems,
                    state = state,
                    lang = lang,
                    gridState = gridState,
                    columns = timelineColumns,
                    selectedKeys = selectedKeys,
                    onColumns = { nextColumns ->
                        lastPinchAt = System.currentTimeMillis()
                        onSetPageColumns(if (state.homeTab == "albums") "albumDetail" else "all", nextColumns)
                    },
                    onItemClick = { photo ->
                        if (selectedKeys.isNotEmpty()) {
                            selectedKeys = if (photo.cacheKey in selectedKeys) selectedKeys - photo.cacheKey else selectedKeys + photo.cacheKey
                        } else {
                            val index = state.photos.indexOfFirst { it.cacheKey == photo.cacheKey }
                            if (index >= 0) {
                                if (state.homeTab == "albums" || state.homeTab == "all") {
                                    onOpenScoped(index, visibleScopeKeys, gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
                                } else {
                                    onOpen(index, gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
                                }
                            }
                        }
                    },
                    onItemLongClick = { photo ->
                        if (System.currentTimeMillis() - lastPinchAt > 700L) {
                            selectedKeys = selectedKeys + photo.cacheKey
                        }
                    },
                    onNearEnd = {
                        when {
                            state.homeTab == "albums" && state.selectedAlbumId != null -> onLoadMoreAlbum()
                            state.homeTab == "all" -> onLoadMoreAll()
                        }
                    },
                    footerText = if (state.homeTab == "albums" && state.selectedAlbumId != null) {
                        val total = state.albums.firstOrNull { it.bucketId == state.selectedAlbumId }?.count ?: visibleItems.size
                        val loaded = visibleItems.size
                        when {
                            state.albumLoading -> lang.t("正在加载更多...", "Loading more...")
                            loaded < total -> lang.t("已加载 $loaded/$total，继续下滑加载更多", "Loaded $loaded/$total; scroll for more")
                            else -> lang.t("已加载全部 $loaded 项", "Loaded all $loaded items")
                        }
                    } else if (state.homeTab == "all") {
                        when {
                            state.allLoading -> lang.t("正在预取更多...", "Prefetching more...")
                            !state.allExhausted -> lang.t("已加载 ${visibleItems.size} 项，继续下滑加载更多", "Loaded ${visibleItems.size} items; scroll for more")
                            else -> lang.t("已加载全部 ${visibleItems.size} 项", "Loaded all ${visibleItems.size} items")
                        }
                    } else {
                        null
                    },
                    contentTopPadding = topListPadding + 8.dp,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = topBarAlpha))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .onSizeChanged { topBarHeightPx = it.height },
        ) {
                val generatedActions = generatedSelectionActions.takeIf { state.homeTab == "generated" }
                if (selectedKeys.isEmpty() && generatedActions != null) {
                    ManageSelectionActions(
                        count = generatedActions.count,
                        lang = lang,
                        onClear = generatedActions.onClear,
                        onSave = generatedActions.onSave,
                        onRegenerate = generatedActions.onRegenerate,
                        onDelete = generatedActions.onDelete,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer(alpha = topBarControlsAlpha),
                    ) {
                        if (selectedKeys.isEmpty()) {
                            val title = when {
                                state.homeTab == "albums" && state.selectedAlbumId != null -> state.albums.firstOrNull { it.bucketId == state.selectedAlbumId }?.name ?: lang.t("相册", "Album")
                                state.homeTab == "albums" -> lang.t("相册", "Albums")
                                else -> lang.t("全部", "All")
                            }
                            if (state.homeTab == "albums" && state.selectedAlbumId != null) {
                                OutlinedButton(onClick = onCloseAlbum) { Text(lang.t("返回", "Back")) }
                                Spacer(Modifier.width(8.dp))
                            }
                            if (state.homeTab == "generated") {
                                RoundedPillTabs(
                                    options = listOf("images" to lang.t("图片", "Images"), "videos" to lang.t("视频", "Videos")),
                                    selected = state.generatedTab,
                                    onSelect = onSetGeneratedTab,
                                )
                                Spacer(Modifier.weight(1f))
                            } else {
                                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            }
                            OutlinedButton(onClick = onSettings) { Text(lang.t("设置", "Settings")) }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = onRefresh) { Text(lang.t("刷新", "Refresh")) }
                        } else {
                            Text(lang.t("已选择 ${selectedKeys.size} 项", "${selectedKeys.size} selected"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { selectedKeys = emptySet() }) { Text(lang.t("取消", "Cancel")) }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                onSaveGenerated(selectedIndexes)
                                selectedKeys = emptySet()
                            }) { Text(lang.t("保存", "Save")) }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                onReplaceOriginal(selectedIndexes)
                                selectedKeys = emptySet()
                            }) { Text(lang.t("替换", "Replace")) }
                        }
                    }
                }
                topBarStatusText?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer(alpha = topBarControlsAlpha),
                    )
                }
            }
        HomeBottomNav(
            current = state.homeTab,
            lang = lang,
            onSelect = {
                selectedKeys = emptySet()
                onSetTab(it)
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineGrid(
    items: List<PhotoItem>,
    state: UiState,
    lang: AppLanguage,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    columns: Int,
    selectedKeys: Set<String>,
    onColumns: (Int) -> Unit,
    onItemClick: (PhotoItem) -> Unit,
    onItemLongClick: (PhotoItem) -> Unit,
    onNearEnd: () -> Unit,
    footerText: String?,
    contentTopPadding: androidx.compose.ui.unit.Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    val restoreRenderLimit = min(items.size, max(1200, gridState.firstVisibleItemIndex + 400))
    var renderLimit by remember(items.firstOrNull()?.cacheKey) { mutableStateOf(restoreRenderLimit) }
    LaunchedEffect(items.size) {
        renderLimit = renderLimit.coerceAtLeast(restoreRenderLimit).coerceAtMost(items.size)
    }
    LaunchedEffect(items.size, gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (lastVisible > renderLimit - 80 && renderLimit < items.size) {
                    renderLimit = min(items.size, renderLimit + 1200)
                }
                if (lastVisible > renderLimit - 80) {
                    onNearEnd()
                }
            }
    }
    val renderedItems = remember(items, renderLimit) { items.take(renderLimit) }
    val cells = remember(renderedItems, lang, renderLimit, items.size) {
        val result = mutableListOf<TimelineCell>()
        var lastDay = Long.MIN_VALUE
        renderedItems.forEach { item ->
            val day = localEpochDay(item.modifiedTime)
            if (day != lastDay) {
                val label = dateGroupLabel(item.modifiedTime, lang)
                result += TimelineCell.Header(day, label)
                lastDay = day
            }
            result += TimelineCell.Media(item)
        }
        if (footerText != null || renderLimit < items.size) {
            result += TimelineCell.Footer(renderLimit, items.size, footerText)
        }
        result
    }
    Box(modifier) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .discreteColumnPinch(columns = columns, onColumns = onColumns),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 8.dp, top = contentTopPadding, end = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            items(
                items = cells,
                key = { cell ->
                    when (cell) {
                        is TimelineCell.Header -> "header_${cell.day}"
                        is TimelineCell.Media -> cell.item.cacheKey
                        is TimelineCell.Footer -> "footer_${cell.loaded}_${cell.total}_${cell.text.orEmpty()}"
                    }
                },
                span = { cell -> if (cell is TimelineCell.Media) GridItemSpan(1) else GridItemSpan(maxLineSpan) },
            ) { cell ->
                when (cell) {
                    is TimelineCell.Header -> Text(
                        text = cell.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
                    )
                    is TimelineCell.Media -> {
                        val photo = cell.item
                        val imageState = state.states[photo.cacheKey] ?: VrState.NORMAL
                        val statusText = if (photo.kind == MediaKind.VIDEO) {
                            (state.videoStates[photo.cacheKey] ?: VideoVrState.NORMAL).label(lang)
                        } else {
                            imageState.label(lang)
                        }
                        PhotoTile(
                            photo = photo,
                            statusText = statusText,
                            entry = state.entries[photo.cacheKey],
                            lang = lang,
                            columns = columns,
                            selected = photo.cacheKey in selectedKeys,
                            onClick = { onItemClick(photo) },
                            onLongClick = { onItemLongClick(photo) },
                        )
                    }
                    is TimelineCell.Footer -> Text(
                        text = cell.text ?: lang.t("已显示 ${cell.loaded}/${cell.total}，继续下滑自动加载更多", "Showing ${cell.loaded}/${cell.total}; keep scrolling to load more"),
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color(0xff777777),
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                    )
                }
            }
        }
        GridScrollbar(gridState, Modifier.align(Alignment.CenterEnd).padding(end = 2.dp))
        GridToTopButton(gridState, lang, Modifier.align(Alignment.BottomEnd).padding(end = 26.dp, bottom = 18.dp))
    }
}

@Composable
private fun GridToTopButton(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    lang: AppLanguage,
    modifier: Modifier = Modifier,
) {
    if (gridState.firstVisibleItemIndex <= 18) return
    val scope = rememberCoroutineScope()
    Button(
        onClick = { scope.launch { gridState.animateScrollToItem(0) } },
        modifier = modifier,
    ) {
        Text(lang.t("顶部", "Top"))
    }
}

@Composable
private fun AlbumTile(album: AlbumItem, lang: AppLanguage, columns: Int, onClick: () -> Unit) {
    val reduction = columnFontReduction(columns)
    val titleSize = (16 - reduction).coerceAtLeast(9).sp
    val countSize = (12 - reduction).coerceAtLeast(8).sp
    val textGap = if (columns >= 7) 4.dp else 8.dp
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .background(androidx.compose.ui.graphics.Color(0xffe8eaed), RoundedCornerShape(18.dp)),
        ) {
            AsyncMediaThumbnail(album.coverKind, album.coverUri, 420, ContentScale.Crop, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(textGap))
        Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = titleSize)
        Text(lang.t("${album.count} 项", "${album.count} items"), color = androidx.compose.ui.graphics.Color(0xff777777), fontSize = countSize, maxLines = 1)
    }
}

@Composable
private fun GridScrollbar(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    modifier: Modifier = Modifier,
) {
    val layoutInfo = gridState.layoutInfo
    val total = layoutInfo.totalItemsCount
    val visible = layoutInfo.visibleItemsInfo
    if (total <= 0 || visible.isEmpty()) return
    val first = visible.first().index
    val visibleCount = visible.size.coerceAtLeast(1)
    val thumbFraction = (visibleCount.toFloat() / total.toFloat()).coerceIn(0.06f, 1f)
    val topFraction = (first.toFloat() / total.toFloat()).coerceIn(0f, 1f - thumbFraction)
    val scope = rememberCoroutineScope()
    var trackHeight by remember { mutableStateOf(1) }
    fun scrollToTrackPosition(y: Float) {
        val fraction = (y / trackHeight.toFloat()).coerceIn(0f, 1f)
        val targetIndex = (fraction * (total - 1).toFloat()).roundToInt().coerceIn(0, total - 1)
        scope.launch { gridState.scrollToItem(targetIndex) }
    }
    Box(
        modifier
            .width(22.dp)
            .fillMaxSize()
            .padding(vertical = 12.dp)
            .onSizeChanged { trackHeight = it.height.coerceAtLeast(1) }
            .pointerInput(total, trackHeight) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> scrollToTrackPosition(offset.y) },
                    onVerticalDrag = { change, _ ->
                        scrollToTrackPosition(change.position.y)
                        change.consume()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(androidx.compose.ui.graphics.Color(0x22000000), RoundedCornerShape(3.dp)),
        )
        Box(
            Modifier
                .width(5.dp)
                .fillMaxHeight(thumbFraction)
                .align(Alignment.TopCenter)
                .graphicsLayer { translationY = size.height * topFraction / thumbFraction }
                .background(androidx.compose.ui.graphics.Color(0x99000000), RoundedCornerShape(3.dp)),
        )
    }
}

@Composable
private fun HomeBottomNav(current: String, lang: AppLanguage, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(androidx.compose.ui.graphics.Color(0xccffffff), RoundedCornerShape(34.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            "all" to lang.t("全部", "All"),
            "albums" to lang.t("相册", "Albums"),
            "generated" to lang.t("生成", "Generated"),
        ).forEach { (key, label) ->
            val selected = key == current
            Surface(
                color = if (selected) androidx.compose.ui.graphics.Color(0xcc6650a4) else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .clickable { onSelect(key) },
            ) {
                Text(
                    text = label,
                    color = if (selected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xff333333),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun RoundedPillTabs(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(androidx.compose.ui.graphics.Color(0xccffffff), RoundedCornerShape(28.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (key, label) ->
            val active = key == selected
            Surface(
                color = if (active) androidx.compose.ui.graphics.Color(0x99e9ddff) else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onSelect(key) },
            ) {
                Text(
                    text = label,
                    color = if (active) androidx.compose.ui.graphics.Color(0xff3f2a78) else androidx.compose.ui.graphics.Color(0xff333333),
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
    }
}

private fun Modifier.discreteColumnPinch(
    columns: Int,
    onColumns: (Int) -> Unit,
    onPinchActivity: () -> Unit = {},
): Modifier = pointerInput(columns) {
    val stepThresholdPx = 72f
    var lastStepAt = 0L
    var baselineSpan: Float? = null
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                val centroid = pressed.centroid()
                val currentSpan = pressed.span(centroid)
                val startSpan = baselineSpan ?: currentSpan.also { baselineSpan = it }
                val delta = currentSpan - startSpan
                val now = System.currentTimeMillis()
                val next = when {
                    delta > stepThresholdPx -> columns - 1
                    delta < -stepThresholdPx -> columns + 1
                    else -> columns
                }.coerceIn(1, 8)
                if (next != columns && now - lastStepAt > 140L) {
                    onPinchActivity()
                    onColumns(next)
                    baselineSpan = currentSpan
                    lastStepAt = now
                }
                event.changes.forEach { it.consume() }
            } else {
                baselineSpan = null
            }
        }
    }
}

private fun columnFontReduction(columns: Int): Int = ((columns - 5).coerceAtLeast(0) * 2).coerceAtMost(6)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(
    photo: PhotoItem,
    statusText: String,
    entry: VrCacheEntry?,
    lang: AppLanguage,
    columns: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val compact = columns >= 7
    val labelSize = (14 - columnFontReduction(columns)).coerceAtLeast(8).sp
    val playSize = (20 - columnFontReduction(columns)).coerceAtLeast(10).sp
    val labelPaddingH = if (compact) 3.dp else 5.dp
    val labelPaddingV = if (compact) 1.dp else 2.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(androidx.compose.ui.graphics.Color(0xff16191c))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        AsyncMediaThumbnail(photo.kind, photo.uri, 420, ContentScale.Crop, Modifier.fillMaxSize())
        if (photo.kind == MediaKind.VIDEO) {
            Text(
                text = "▶",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = playSize,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(androidx.compose.ui.graphics.Color(0x99000000), RoundedCornerShape(3.dp))
                    .padding(horizontal = if (compact) 4.dp else 8.dp, vertical = if (compact) 1.dp else 4.dp),
            )
        }
        if (selected) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x6638d8b4)))
            Text(
                text = "✓",
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.TopEnd).background(androidx.compose.ui.graphics.Color(0xcc111315)).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        StatusBadge(
            text = statusText,
            fontSize = labelSize,
            modifier = Modifier.align(Alignment.BottomStart),
            horizontalPadding = labelPaddingH,
            verticalPadding = labelPaddingV,
        )
    }
}

@Composable
private fun StatusBadge(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 5.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 2.dp,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        color = statusBadgeTextColor(text),
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManageScreen(
    state: UiState,
    embedded: Boolean = false,
    onBack: () -> Unit,
    selectedTab: String,
    onTabChange: (String) -> Unit,
    onToggleVersion: (String) -> Unit,
    onOpenVersion: (String) -> Unit,
    onCloseVersion: () -> Unit,
    contentTopPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onSetGeneratedColumns: (Int) -> Unit,
    onGeneratedScroll: (String, Int, Int) -> Unit,
    onGeneratedVersionScroll: (String, Int, Int) -> Unit,
    onDeleteVersion: (String) -> Unit,
    onOpenGenerated: (Int, VrCacheEntry) -> Unit,
    onOpenGeneratedVideo: (Int) -> Unit,
    onSaveVideo: (Int) -> Unit,
    onDeleteVideo: (Int) -> Unit,
    onSaveVideos: (List<Int>) -> Unit,
    onDeleteVideos: (List<Int>) -> Unit,
    onRegenerateVideos: (List<Int>) -> Unit,
    onSaveImages: (List<Int>) -> Unit,
    onDeleteImages: (List<ManagedCacheItem>) -> Unit,
    onRegenerateImages: (List<Int>) -> Unit,
    onGeneratedSelectionChange: (GeneratedSelectionActionsState?) -> Unit = {},
) {
    val lang = state.settings.language
    val tab = selectedTab
    var selectedImageKeys by remember { mutableStateOf(setOf<String>()) }
    var selectedVideoKeys by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(tab, state.selectedGeneratedVersion) {
        selectedImageKeys = emptySet()
        selectedVideoKeys = emptySet()
    }
    DisposableEffect(Unit) {
        onDispose { onGeneratedSelectionChange(null) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xfff5f6f7))
            .then(if (embedded) Modifier else Modifier.statusBarsPadding())
            .padding(
                if (embedded) {
                    androidx.compose.foundation.layout.PaddingValues(0.dp)
                } else {
                    androidx.compose.foundation.layout.PaddingValues(16.dp)
                },
            ),
    ) {
        if (!embedded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text(lang.t("返回", "Back")) }
                Spacer(Modifier.width(12.dp))
                Text(lang.t("生成管理", "Generated Manager"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
        if (!embedded) {
            RoundedPillTabs(
                options = listOf("images" to lang.t("图片", "Images"), "videos" to lang.t("视频", "Videos")),
                selected = tab,
                onSelect = onTabChange,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (tab == "videos") {
            val videoGridState = rememberLazyGridState(
                initialFirstVisibleItemIndex = state.generatedVideoScrollIndex.coerceAtLeast(0),
                initialFirstVisibleItemScrollOffset = state.generatedVideoScrollOffset.coerceAtLeast(0),
            )
            LaunchedEffect(videoGridState) {
                snapshotFlow { videoGridState.firstVisibleItemIndex to videoGridState.firstVisibleItemScrollOffset }
                    .collect { (index, offset) -> onGeneratedScroll("videos", index, offset) }
            }
            val videos = state.photos.filter { item ->
                item.kind == MediaKind.VIDEO &&
                    ((state.videoStates[item.cacheKey] ?: VideoVrState.NORMAL) != VideoVrState.NORMAL ||
                        state.videoEntries.containsKey(item.cacheKey) ||
                        state.videoJobs.any { it.item.cacheKey == item.cacheKey })
            }
            val selectedIndexes = videos.mapNotNull { item -> state.photos.indexOfFirst { it.cacheKey == item.cacheKey }.takeIf { it >= 0 && item.cacheKey in selectedVideoKeys } }
            LaunchedEffect(embedded, selectedVideoKeys, selectedIndexes) {
                if (embedded && selectedVideoKeys.isNotEmpty()) {
                    onGeneratedSelectionChange(
                        GeneratedSelectionActionsState(
                            count = selectedVideoKeys.size,
                            onClear = { selectedVideoKeys = emptySet() },
                            onSave = { onSaveVideos(selectedIndexes); selectedVideoKeys = emptySet() },
                            onRegenerate = { onRegenerateVideos(selectedIndexes); selectedVideoKeys = emptySet() },
                            onDelete = { onDeleteVideos(selectedIndexes); selectedVideoKeys = emptySet() },
                        ),
                    )
                } else if (embedded) {
                    onGeneratedSelectionChange(null)
                }
            }
            if (selectedVideoKeys.isNotEmpty() && !embedded) {
                ManageSelectionActions(
                    count = selectedVideoKeys.size,
                    lang = lang,
                    onClear = { selectedVideoKeys = emptySet() },
                    onSave = { onSaveVideos(selectedIndexes); selectedVideoKeys = emptySet() },
                    onRegenerate = { onRegenerateVideos(selectedIndexes); selectedVideoKeys = emptySet() },
                    onDelete = { onDeleteVideos(selectedIndexes); selectedVideoKeys = emptySet() },
                )
                Spacer(Modifier.height(8.dp))
            }
            if (videos.isEmpty()) {
                Text(
                    text = lang.t("暂无生成中或已生成的视频", "No generating or generated videos"),
                    modifier = Modifier.padding(top = if (embedded) contentTopPadding else 0.dp),
                )
            } else {
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(state.generatedColumns),
                        state = videoGridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.White)
                            .discreteColumnPinch(columns = state.generatedColumns, onColumns = onSetGeneratedColumns),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 8.dp,
                            top = if (embedded) contentTopPadding else 8.dp,
                            end = 8.dp,
                            bottom = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(videos, key = { it.cacheKey }) { item ->
                            val index = state.photos.indexOfFirst { it.cacheKey == item.cacheKey }
                            val videoState = state.videoStates[item.cacheKey] ?: VideoVrState.NORMAL
                            val job = state.videoJobs.firstOrNull { it.item.cacheKey == item.cacheKey }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(androidx.compose.ui.graphics.Color(0xff16191c)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .combinedClickable(
                                            enabled = index >= 0,
                                            onClick = {
                                                if (selectedVideoKeys.isNotEmpty()) {
                                                    selectedVideoKeys = if (item.cacheKey in selectedVideoKeys) selectedVideoKeys - item.cacheKey else selectedVideoKeys + item.cacheKey
                                                } else {
                                                    onOpenGeneratedVideo(index)
                                                }
                                            },
                                            onLongClick = { selectedVideoKeys = selectedVideoKeys + item.cacheKey },
                                        ),
                                ) {
                                    AsyncMediaThumbnail(MediaKind.VIDEO, state.videoEntries[item.cacheKey]?.let { Uri.fromFile(File(it.outputPath)) } ?: item.uri, 420, ContentScale.Crop, Modifier.fillMaxSize())
                                    if (item.cacheKey in selectedVideoKeys) SelectionBadge()
                                    StatusBadge(
                                        text = "${videoState.shortLabel(lang)} ${(job?.progress?.times(100f) ?: 0f).roundToInt()}% 当前${job?.currentFrameMs ?: 0}ms 均${job?.avgFrameMs ?: 0}ms",
                                        fontSize = (10 - columnFontReduction(state.generatedColumns)).coerceAtLeast(7).sp,
                                        modifier = Modifier.align(Alignment.BottomStart),
                                        horizontalPadding = 2.dp,
                                        verticalPadding = 1.dp,
                                    )
                                }
                            }
                        }
                    }
                    GridScrollbar(videoGridState, Modifier.align(Alignment.CenterEnd).padding(end = 2.dp))
                    GridToTopButton(videoGridState, lang, Modifier.align(Alignment.BottomEnd).padding(end = 26.dp, bottom = 18.dp))
                }
            }
        } else {
            if (state.cacheVersions.isEmpty()) {
                LaunchedEffect(embedded) {
                    if (embedded) onGeneratedSelectionChange(null)
                }
                Text(
                    text = lang.t("暂无已生成图片", "No generated images yet"),
                    modifier = Modifier.padding(top = if (embedded) contentTopPadding else 0.dp),
                )
            } else {
                val selectedVersion = state.selectedGeneratedVersion
                if (selectedVersion == null) {
                    LaunchedEffect(embedded, selectedVersion) {
                        if (embedded) onGeneratedSelectionChange(null)
                    }
                    val versionGridState = rememberLazyGridState(
                        initialFirstVisibleItemIndex = state.generatedImageScrollIndex.coerceAtLeast(0),
                        initialFirstVisibleItemScrollOffset = state.generatedImageScrollOffset.coerceAtLeast(0),
                    )
                    LaunchedEffect(versionGridState) {
                        snapshotFlow { versionGridState.firstVisibleItemIndex to versionGridState.firstVisibleItemScrollOffset }
                            .collect { (index, offset) -> onGeneratedScroll("images", index, offset) }
                    }
                    Box(Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(state.generatedColumns.coerceIn(2, 6)),
                            state = versionGridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Color.White)
                                .discreteColumnPinch(columns = state.generatedColumns, onColumns = onSetGeneratedColumns),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 10.dp,
                                top = if (embedded) contentTopPadding else 10.dp,
                                end = 10.dp,
                                bottom = 10.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(state.cacheVersions, key = { it.version }) { summary ->
                                val items = state.managedCacheItems.filter { it.entry.version == summary.version }
                                val cover = items.maxByOrNull { it.entry.createdAt }
                                GeneratedVersionTile(
                                    summary = summary,
                                    cover = cover,
                                    lang = lang,
                                    onOpen = { onOpenVersion(summary.version) },
                                    onDelete = { onDeleteVersion(summary.version) },
                                )
                            }
                        }
                        GridScrollbar(versionGridState, Modifier.align(Alignment.CenterEnd).padding(end = 2.dp))
                        GridToTopButton(versionGridState, lang, Modifier.align(Alignment.BottomEnd).padding(end = 26.dp, bottom = 18.dp))
                    }
                } else {
                    val itemsInVersion = state.managedCacheItems.filter { it.entry.version == selectedVersion }
                    val imageGridState = rememberLazyGridState(
                        initialFirstVisibleItemIndex = (state.generatedVersionScrollIndexes[selectedVersion] ?: 0).coerceAtLeast(0),
                        initialFirstVisibleItemScrollOffset = (state.generatedVersionScrollOffsets[selectedVersion] ?: 0).coerceAtLeast(0),
                    )
                    LaunchedEffect(selectedVersion, imageGridState) {
                        snapshotFlow { imageGridState.firstVisibleItemIndex to imageGridState.firstVisibleItemScrollOffset }
                            .collect { (index, offset) -> onGeneratedVersionScroll(selectedVersion, index, offset) }
                    }
                    Column(Modifier.fillMaxSize()) {
                        if (!embedded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = onCloseVersion) { Text(lang.t("返回", "Back")) }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = selectedVersion,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = { onDeleteVersion(selectedVersion); onCloseVersion() }) { Text(lang.t("删除", "Delete")) }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        val selectedItems = itemsInVersion.filter { "${it.entry.photoKey}|${it.entry.version}" in selectedImageKeys }
                        val selectedImageIndexes = selectedItems.mapNotNull { item -> state.photos.indexOfFirst { it.cacheKey == item.photoItem.cacheKey }.takeIf { it >= 0 } }.distinct()
                        LaunchedEffect(embedded, selectedImageKeys, selectedItems, selectedImageIndexes) {
                            if (embedded && selectedImageKeys.isNotEmpty()) {
                                onGeneratedSelectionChange(
                                    GeneratedSelectionActionsState(
                                        count = selectedImageKeys.size,
                                        onClear = { selectedImageKeys = emptySet() },
                                        onSave = { onSaveImages(selectedImageIndexes); selectedImageKeys = emptySet() },
                                        onRegenerate = { onRegenerateImages(selectedImageIndexes); selectedImageKeys = emptySet() },
                                        onDelete = { onDeleteImages(selectedItems); selectedImageKeys = emptySet() },
                                    ),
                                )
                            } else if (embedded) {
                                onGeneratedSelectionChange(null)
                            }
                        }
                        if (selectedImageKeys.isNotEmpty() && !embedded) {
                            ManageSelectionActions(
                                count = selectedImageKeys.size,
                                lang = lang,
                                onClear = { selectedImageKeys = emptySet() },
                                onSave = { onSaveImages(selectedImageIndexes); selectedImageKeys = emptySet() },
                                onRegenerate = { onRegenerateImages(selectedImageIndexes); selectedImageKeys = emptySet() },
                                onDelete = { onDeleteImages(selectedItems); selectedImageKeys = emptySet() },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Box(Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(state.generatedColumns),
                                state = imageGridState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(androidx.compose.ui.graphics.Color.White)
                                    .discreteColumnPinch(columns = state.generatedColumns, onColumns = onSetGeneratedColumns),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = 8.dp,
                                    top = if (embedded) contentTopPadding else 8.dp,
                                    end = 8.dp,
                                    bottom = 8.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(itemsInVersion, key = { "${it.entry.photoKey}_${it.entry.version}" }) { item ->
                                    val index = state.photos.indexOfFirst { it.cacheKey == item.photoItem.cacheKey }
                                    val selectionKey = "${item.entry.photoKey}|${item.entry.version}"
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .background(androidx.compose.ui.graphics.Color(0xff16191c))
                                            .combinedClickable(
                                                enabled = index >= 0,
                                                onClick = {
                                                    if (selectedImageKeys.isNotEmpty()) {
                                                        selectedImageKeys = if (selectionKey in selectedImageKeys) selectedImageKeys - selectionKey else selectedImageKeys + selectionKey
                                                    } else {
                                                        onOpenGenerated(index, item.entry)
                                                    }
                                                },
                                                onLongClick = { selectedImageKeys = selectedImageKeys + selectionKey },
                                            ),
                                    ) {
                                        GeneratedSbsThumbnail(item.entry, 360, ContentScale.Crop, Modifier.fillMaxSize(), lang)
                                        if (selectionKey in selectedImageKeys) SelectionBadge()
                                    }
                                }
                            }
                            GridScrollbar(imageGridState, Modifier.align(Alignment.CenterEnd).padding(end = 2.dp))
                            GridToTopButton(imageGridState, lang, Modifier.align(Alignment.BottomEnd).padding(end = 26.dp, bottom = 18.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GeneratedVersionTile(
    summary: CacheVersionSummary,
    cover: ManagedCacheItem?,
    lang: AppLanguage,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onDelete),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(androidx.compose.ui.graphics.Color(0xff16191c), RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp)),
        ) {
            if (cover != null) {
                GeneratedSbsThumbnail(cover.entry, 420, ContentScale.Crop, Modifier.fillMaxSize(), lang)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(lang.t("暂无预览", "No preview"), color = androidx.compose.ui.graphics.Color.White)
                }
            }
            Text(
                text = lang.t("${summary.count} 张", "${summary.count} images"),
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(androidx.compose.ui.graphics.Color(0x99000000))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = summary.version,
            color = androidx.compose.ui.graphics.Color(0xff222222),
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = lang.t("${(summary.bytes / 1024f / 1024f).roundToInt()} MB，长按删除", "${(summary.bytes / 1024f / 1024f).roundToInt()} MB, long press delete"),
            style = MaterialTheme.typography.bodySmall,
            color = androidx.compose.ui.graphics.Color(0xff666666),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GeneratedSbsThumbnail(entry: VrCacheEntry, maxSide: Int, contentScale: ContentScale, modifier: Modifier = Modifier, lang: AppLanguage) {
    val signature = "${entry.photoKey}|${entry.version}|${entry.leftPath}|${entry.outputPath}"
    var bitmap by remember(signature, maxSide) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(signature, maxSide) { mutableStateOf(false) }
    LaunchedEffect(signature, maxSide) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { decodeGeneratedLeftPreview(entry, maxSide) }.getOrNull()
        }
        bitmap = loaded
        failed = loaded == null
    }
    when {
        bitmap != null -> Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = contentScale)
        failed -> Box(modifier.background(androidx.compose.ui.graphics.Color(0xff202326)), contentAlignment = Alignment.Center) {
            Text(lang.t("暂无预览", "No preview"), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall)
        }
        else -> Box(modifier.background(androidx.compose.ui.graphics.Color(0xff202326)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ManageSelectionActions(
    count: Int,
    lang: AppLanguage,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(lang.t("已选 $count", "$count selected"), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = onClear) { Text(lang.t("取消", "Cancel")) }
        OutlinedButton(onClick = onSave) { Text(lang.t("保存", "Save")) }
        OutlinedButton(onClick = onRegenerate) { Text(lang.t("重新生成", "Regenerate")) }
        Button(onClick = onDelete) { Text(lang.t("删除", "Delete")) }
    }
}

@Composable
private fun SelectionBadge() {
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x6638d8b4))) {
        Text(
            text = "✓",
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.TopEnd).background(androidx.compose.ui.graphics.Color(0xcc111315)).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    updateStatus: String?,
    updateUrl: String?,
    updateAvailable: Boolean,
    updateDownloading: Boolean,
    updateDownloadProgress: Float,
    updateApkPath: String?,
    onBack: () -> Unit,
    onChange: (AppSettings) -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    val lang = settings.language
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xfff5f6f7))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text(lang.t("返回", "Back")) }
            Spacer(Modifier.width(12.dp))
            Text(lang.t("设置", "Settings"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        SettingsSectionTitle(lang.t("公共设置", "General"))
        Text(lang.t("语言", "Language"), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow {
            listOf(AppLanguage.ZH to "中文", AppLanguage.EN to "English").forEachIndexed { index, option ->
                SegmentedButton(
                    selected = settings.language == option.first,
                    onClick = { onChange(settings.copy(language = option.first)) },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(option.second) }
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedButton(onClick = onCheckUpdate) {
            Text(lang.t("检查更新", "Check for updates"))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = lang.t(
                "当前版本：$CURRENT_VERSION_TAG ($CURRENT_VERSION_CODE)",
                "Current version: $CURRENT_VERSION_TAG ($CURRENT_VERSION_CODE)",
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        updateStatus?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        if (updateDownloading) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { updateDownloadProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        if (updateAvailable && updateApkPath == null && !updateDownloading) updateUrl?.let {
            Spacer(Modifier.height(6.dp))
            Button(onClick = onDownloadUpdate) {
                Text(lang.t("下载更新", "Download update"))
            }
        }
        if (updateApkPath != null && !updateDownloading) {
            Spacer(Modifier.height(6.dp))
            Button(onClick = onInstallUpdate) {
                Text(lang.t("安装更新", "Install update"))
            }
        }
        Spacer(Modifier.height(16.dp))

        SettingFloat(lang.t("深度强度", "Depth scale"), settings.depthScale, listOf(10f, 20f, 30f, 40f, 50f, 60f, 80f)) {
            onChange(settings.copy(depthScale = it))
        }
        SettingInt(lang.t("深度平滑", "Blur radius"), settings.blurRadius, listOf(0, 1, 3, 5, 9, 15, 25)) {
            onChange(settings.copy(blurRadius = it))
        }
        SettingInt(lang.t("边缘填充", "Fill radius"), settings.fillRadius, listOf(0, 3, 5, 10, 15, 20, 30)) {
            onChange(settings.copy(fillRadius = it))
        }
        SettingInt(lang.t("输出最大长边", "Max output long edge"), settings.maxLongEdge, listOf(1280, 1920, 2048, 3072, 4096, 6000)) {
            onChange(settings.copy(maxLongEdge = it))
        }
        Spacer(Modifier.height(12.dp))

        SettingsSectionTitle(lang.t("图片生成设置", "Image generation"))
        ModelPicker(
            title = lang.t("图片模型选择", "Image model"),
            selectedModelId = settings.imageModelId,
            lang = lang,
            models = ImageModels,
            onSelect = { spec -> onChange(settings.copy(imageModelId = spec.id, depthResolution = spec.inputSize)) },
        )
        Text(lang.t("实验模型首次使用会单独下载；如果模型输入输出不兼容，会在日志里显示失败原因。", "Experimental models download separately on first use. Shape incompatibility is reported in logs."), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))

        Text(lang.t("预加载", "Prefetch"), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow {
            val options = listOf("auto", "2", "4", "8")
            options.forEachIndexed { index, option ->
                val selected = if (option == "auto") settings.autoPrefetch else !settings.autoPrefetch && settings.prefetchWindow == option.toInt()
                SegmentedButton(
                    selected = selected,
                    onClick = {
                        if (option == "auto") onChange(settings.copy(autoPrefetch = true, prefetchWindow = 2))
                        else onChange(settings.copy(autoPrefetch = false, prefetchWindow = option.toInt()))
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(if (option == "auto") lang.t("自动", "Auto") else option) }
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingInt(lang.t("后台 worker 数", "Background workers"), settings.generationWorkers, listOf(1, 2, 3)) {
            onChange(settings.copy(generationWorkers = it))
        }
        SettingInt(lang.t("模型 CPU 线程", "Model CPU threads"), settings.modelThreads, listOf(1, 2, 4, 8)) {
            onChange(settings.copy(modelThreads = it))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = settings.useGpu,
                onCheckedChange = { onChange(settings.copy(useGpu = it)) },
            )
            Text(lang.t("尝试 GPU 加速", "Try GPU acceleration"))
        }
        Text(lang.t("GPU 加速模式选择", "GPU acceleration mode"), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        GpuModePicker(settings.gpuTestMode, lang) {
            onChange(settings.copy(gpuTestMode = it))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            lang.t(
                "开启 GPU 后会强制使用所选 GPU 模式；失败或输出异常不会偷偷切 CPU。关闭 GPU 才会恢复 CPU。",
                "When GPU is enabled, the selected GPU mode is forced. Failures or bad output will not silently fall back to CPU. Disable GPU to use CPU.",
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))

        SettingsSectionTitle(lang.t("视频生成设置", "Video generation"))
        ModelPicker(
            title = lang.t("视频模型选择", "Video model"),
            selectedModelId = settings.videoModelId,
            lang = lang,
            models = VideoModels,
            onSelect = { spec -> onChange(settings.copy(videoModelId = spec.id)) },
        )
        SettingInt(lang.t("视频模型 CPU 线程", "Video model CPU threads"), settings.videoModelThreads, listOf(1, 2, 4, 8)) {
            onChange(settings.copy(videoModelThreads = it))
        }
        SettingInt(lang.t("视频深度 worker 数", "Video depth workers"), settings.videoDepthWorkers, listOf(1, 2)) {
            onChange(settings.copy(videoDepthWorkers = it))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = settings.videoUseGpu,
                onCheckedChange = { onChange(settings.copy(videoUseGpu = it)) },
            )
            Text(lang.t("视频生成尝试 GPU 加速", "Try GPU for video generation"))
        }
        Text(lang.t("视频深度 worker 是独立模型会话数量；2 个 worker 可能更快，也会更占显存/内存。视频设置只影响视频 VR 生成。", "Video depth workers are independent model sessions. 2 workers may be faster but use more GPU memory/RAM. Video settings only affect video VR generation."), style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(12.dp))
        Text(lang.t("深度图分辨率", "Depth resolution"), fontWeight = FontWeight.Bold)
        val imageSpec = modelSpec(settings.imageModelId)
        val videoSpec = modelSpec(settings.videoModelId)
        Text(lang.t("图片 ${imageSpec.inputSize} x ${imageSpec.inputSize}；视频 ${videoSpec.inputSize} x ${videoSpec.inputSize}（由所选模型固定）", "Image ${imageSpec.inputSize} x ${imageSpec.inputSize}; video ${videoSpec.inputSize} x ${videoSpec.inputSize}. Fixed by selected models."), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = settings.invertDepth,
                onCheckedChange = { onChange(settings.copy(invertDepth = it)) },
            )
            Text(lang.t("反转深度", "Invert depth"))
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    HorizontalDivider(Modifier.padding(vertical = 10.dp))
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun GpuModePicker(selected: GpuTestMode, lang: AppLanguage, onSelect: (GpuTestMode) -> Unit) {
    val options = listOf(
        GpuTestMode.AUTO to lang.t("自动尝试全部", "Auto try all"),
        GpuTestMode.ACCURATE_UNSET to lang.t("精确模式", "Accurate"),
        GpuTestMode.ACCURATE_OPENGL to lang.t("强制 OpenGL", "Force OpenGL"),
        GpuTestMode.ACCURATE_OPENCL to lang.t("强制 OpenCL", "Force OpenCL"),
        GpuTestMode.BEST_COMPAT to lang.t("系统推荐", "System best"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (mode, label) ->
            val modifier = Modifier.fillMaxWidth()
            if (selected == mode) {
                Button(onClick = { onSelect(mode) }, modifier = modifier) {
                    Text(label)
                }
            } else {
                OutlinedButton(onClick = { onSelect(mode) }, modifier = modifier) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun ModelPicker(
    title: String,
    selectedModelId: String,
    lang: AppLanguage,
    models: List<ModelSpec>,
    onSelect: (ModelSpec) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        models.forEach { spec ->
            val selected = selectedModelId == spec.id
            val buttonModifier = Modifier.fillMaxWidth()
            if (!spec.downloadable) {
                OutlinedButton(onClick = {}, enabled = false, modifier = buttonModifier) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(spec.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(lang.t(spec.noteZh, spec.noteEn), style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else if (selected) {
                Button(onClick = { onSelect(spec) }, modifier = buttonModifier) {
                    Text(spec.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else {
                OutlinedButton(onClick = { onSelect(spec) }, modifier = buttonModifier) {
                    Text(spec.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    Text(
        lang.t("可用模型会在首次生成前自动下载；待导入模型需要先提供移动端资产和 SHA-256。", "Available models download automatically before first generation. Pending models need a mobile asset and SHA-256 first."),
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SettingInt(title: String, value: Int, options: List<Int>, onChange: (Int) -> Unit) {
    Text(title, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = value == option,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(option.toString()) }
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun SettingFloat(title: String, value: Float, options: List<Float>, onChange: (Float) -> Unit) {
    Text(title, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = abs(value - option) < 0.01f,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(option.roundToInt().toString()) }
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun ViewerScreen(
    state: UiState,
    startIndex: Int,
    onClose: () -> Unit,
    onIndexChanged: (Int) -> Unit,
    onVr: (Int) -> Unit,
    onRetry: (Int) -> Unit,
    onOpenDebug: (Int) -> Unit,
) {
    val lang = state.settings.language
    val generatedViewer = state.viewerOrigin != ViewerOrigin.NORMAL
    val view = LocalView.current
    val context = view.context
    DisposableEffect(Unit) {
        val window = (view.context as? ComponentActivity)?.window
        if (Build.VERSION.SDK_INT >= 30) {
            window?.insetsController?.hide(WindowInsets.Type.systemBars())
            window?.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= 30) {
                window?.insetsController?.show(WindowInsets.Type.systemBars())
            }
        }
    }

    val generatedEntryByKey = remember(state.managedCacheItems, state.viewerOrigin, state.selectedGeneratedVersion) {
        if (state.viewerOrigin != ViewerOrigin.NORMAL && state.selectedGeneratedVersion != null) {
            state.managedCacheItems
                .filter { it.entry.version == state.selectedGeneratedVersion }
                .associate { it.photoItem.cacheKey to it.entry }
        } else {
            emptyMap()
        }
    }
    val viewerItems = remember(state.photos, state.managedCacheItems, state.viewerScopeKeys, state.viewerScopeOrderedKeys, state.viewerOrigin, state.selectedGeneratedVersion, startIndex) {
        val indexed = state.photos.mapIndexed { index, item -> index to item }
        val indexedByKey = indexed.associateBy { (_, item) -> item.cacheKey }
        if (state.viewerOrigin != ViewerOrigin.NORMAL && state.selectedGeneratedVersion != null) {
            state.managedCacheItems
                .filter { it.entry.version == state.selectedGeneratedVersion }
                .mapNotNull { generated ->
                    indexedByKey[generated.photoItem.cacheKey]
                }
                .distinctBy { (_, item) -> item.cacheKey }
                .ifEmpty { indexed.filter { it.first == startIndex } }
        } else if (state.viewerScopeOrderedKeys.isNotEmpty()) {
            state.viewerScopeOrderedKeys
                .mapNotNull { indexedByKey[it] }
                .distinctBy { (_, item) -> item.cacheKey }
                .ifEmpty { indexed.filter { it.first == startIndex } }
        } else if (state.viewerScopeKeys.isNotEmpty()) {
            indexed.filter { (_, item) -> item.cacheKey in state.viewerScopeKeys }
                .ifEmpty { indexed.filter { it.first == startIndex } }
        } else {
            indexed
        }
    }
    val initialPage = viewerItems.indexOfFirst { it.first == startIndex }.takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(initialPage = initialPage) { viewerItems.size }
    val viewerFlingBehavior = PagerDefaults.flingBehavior(state = pagerState)
    val viewerBitmapCache = remember { mutableStateMapOf<String, Bitmap>() }
    val viewerCacheSignature = remember(viewerItems) { viewerItems.joinToString("|") { it.second.cacheKey } }
    LaunchedEffect(viewerCacheSignature) {
        viewerBitmapCache.clear()
    }
    LaunchedEffect(viewerCacheSignature, pagerState.settledPage, state.vrMode, generatedViewer, state.entries, generatedEntryByKey) {
        val preloadPages = listOf(
            pagerState.settledPage,
            pagerState.settledPage + 1,
            pagerState.settledPage - 1,
            pagerState.settledPage + 2,
            pagerState.settledPage - 2,
            pagerState.settledPage + 3,
            pagerState.settledPage - 3,
        )
        val nearbyTargets = preloadPages
            .mapNotNull { page -> viewerItems.getOrNull(page)?.second }
            .filter { item -> item.kind == MediaKind.IMAGE }
            .flatMap { item ->
                val entry = generatedEntryByKey[item.cacheKey] ?: state.entries[item.cacheKey]
                if ((state.vrMode || generatedViewer) && entry != null) {
                    if (entry.hasSplitEyes) {
                        listOf(
                            Uri.fromFile(File(entry.leftPath)) to 4096,
                            Uri.fromFile(File(entry.rightPath)) to 4096,
                        )
                    } else {
                        listOf(Uri.fromFile(File(entry.outputPath)) to 4096)
                    }
                } else {
                    listOf(item.uri to 2560)
                }
            }
            .distinct()
        nearbyTargets.forEach { (uri, maxSide) ->
            val key = bitmapCacheKey(uri, maxSide)
            if (viewerBitmapCache[key] == null) {
                val bitmap = withContext(Dispatchers.IO) {
                    runCatching { decodeScaledBitmap(context, uri, maxSide) }.getOrNull()
                }
                if (bitmap != null) {
                    viewerBitmapCache[key] = bitmap
                    while (viewerBitmapCache.size > 12) {
                        viewerBitmapCache.keys.firstOrNull()?.let { viewerBitmapCache.remove(it) } ?: break
                    }
                }
            }
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        viewerItems.getOrNull(pagerState.settledPage)?.first?.let(onIndexChanged)
    }
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, state.vrMode, pagerState.currentPage) {
        if (state.vrMode && controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 2,
            flingBehavior = viewerFlingBehavior,
        ) { page ->
            val (sourceIndex, photo) = viewerItems.getOrNull(page) ?: return@HorizontalPager
            if (photo.kind == MediaKind.VIDEO) {
                val generated = state.videoEntries[photo.cacheKey]
                val job = state.videoJobs.firstOrNull { it.item.cacheKey == photo.cacheKey }
                val videoState = state.videoStates[photo.cacheKey] ?: VideoVrState.NORMAL
                val activePage = page == pagerState.currentPage
                val playerContent: @Composable () -> Unit = {
                    VideoPlayer(
                        uri = generated?.let { Uri.fromFile(File(it.outputPath)) } ?: photo.uri,
                        modifier = Modifier.fillMaxSize(),
                        active = activePage,
                        sbsMode = generated != null,
                        controlsVisible = controlsVisible,
                        stateLine = "${videoState.label(lang)}  ${job?.currentFrame ?: 0}/${job?.totalFrames ?: 0}",
                        metricsLine = "frame ${job?.currentFrameMs ?: 0}ms · avg ${job?.avgFrameMs ?: 0}ms",
                        onSingleTap = { controlsVisible = !controlsVisible },
                    )
                }
                if (generated != null) {
                    key(generated.outputPath) { playerContent() }
                } else {
                    playerContent()
                }
            } else {
                val entry = generatedEntryByKey[photo.cacheKey] ?: state.entries[photo.cacheKey]
                val vrState = state.states[photo.cacheKey] ?: VrState.NORMAL
                if ((state.vrMode || generatedViewer) && entry != null) {
                    SyncStereoEntryImage(entry, viewerBitmapCache, Modifier.fillMaxSize()) {
                        controlsVisible = !controlsVisible
                    }
                } else {
                    AsyncBitmapImage(
                        photo.uri,
                        2560,
                        ContentScale.Fit,
                        viewerBitmapCache,
                        Modifier
                            .fillMaxSize()
                            .tapWithoutConsumingDrag(photo.uri) { controlsVisible = !controlsVisible },
                    )
                    if (state.vrMode && !generatedViewer && vrState != VrState.READY) {
                        StatusOverlay(vrState, lang, onRetry = { onRetry(sourceIndex) })
                    }
                }
            }
        }
        if (controlsVisible || !state.vrMode) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().background(androidx.compose.ui.graphics.Color(0x99000000)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onClose) { Text(lang.t("返回", "Back")) }
                Spacer(Modifier.width(8.dp))
                val currentPair = viewerItems.getOrNull(pagerState.currentPage)
                val currentIndex = currentPair?.first ?: startIndex
                val currentItem = currentPair?.second
                Text(
                    text = currentItem?.displayName.orEmpty(),
                    color = androidx.compose.ui.graphics.Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val currentVideoState = currentItem?.takeIf { it.kind == MediaKind.VIDEO }?.let { state.videoStates[it.cacheKey] ?: VideoVrState.NORMAL }
                val vrButtonEnabled = if (currentVideoState != null) {
                    !generatedViewer || currentVideoState != VideoVrState.READY
                } else {
                    !generatedViewer
                }
                Button(onClick = { onVr(currentIndex) }, enabled = vrButtonEnabled) {
                    Text(
                        if (currentItem?.kind == MediaKind.VIDEO) {
                            when (currentVideoState ?: VideoVrState.NORMAL) {
                                VideoVrState.NORMAL -> lang.t("加入 VR 队列", "Add to VR queue")
                                VideoVrState.QUEUED -> lang.t("暂停生成", "Pause")
                                VideoVrState.GENERATING -> lang.t("暂停生成", "Pause")
                                VideoVrState.PAUSED -> lang.t("继续生成", "Resume")
                                VideoVrState.READY -> lang.t("已生成", "Ready")
                                VideoVrState.FAILED -> lang.t("重试视频 VR", "Retry video VR")
                            }
                        } else if (generatedViewer) {
                            lang.t("已生成", "Ready")
                        } else if (state.vrMode) {
                            lang.t("关闭 VR", "VR Off")
                        } else {
                            lang.t("开启 VR", "VR On")
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onOpenDebug(currentIndex) }) { Text(lang.t("调试", "Debug")) }
            }
            val current = viewerItems.getOrNull(pagerState.currentPage)?.second
            if (!generatedViewer && state.vrMode && current?.kind != MediaKind.VIDEO) Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(androidx.compose.ui.graphics.Color(0x99000000)).padding(10.dp),
            ) {
                if (current != null) {
                    Text(
                        text = imageLoadedLine(state, viewerItems.getOrNull(pagerState.currentPage)?.first ?: startIndex, lang),
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = imageQueueLine(state, viewerItems.getOrNull(pagerState.currentPage)?.first ?: startIndex, lang),
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val recentLines = imageRecentGenerationLines(state, lang)
                    repeat(3) { lineIndex ->
                        Text(
                            text = recentLines.getOrNull(lineIndex).orEmpty(),
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusOverlay(state: VrState, lang: AppLanguage, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x66000000)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state == VrState.GENERATING || state == VrState.QUEUED) {
            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
            Spacer(Modifier.height(12.dp))
            Text(state.label(lang), color = androidx.compose.ui.graphics.Color.White)
        } else if (state == VrState.FAILED) {
            Text(lang.t("生成失败", "Generation failed"), color = androidx.compose.ui.graphics.Color.White)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text(lang.t("重试", "Retry")) }
        }
    }
}

@Composable
private fun DebugScreen(
    state: UiState,
    index: Int,
    onBack: () -> Unit,
    onShare: () -> Unit,
) {
    val lang = state.settings.language
    val photo = state.photos.getOrNull(index)
    val entry = photo?.let { state.entries[it.cacheKey] }
    val vrState = photo?.let { state.states[it.cacheKey] } ?: VrState.NORMAL
    val job = photo?.let { p -> state.jobs.firstOrNull { it.photoItem.cacheKey == p.cacheKey } }
    val isVideo = photo?.kind == MediaKind.VIDEO
    val videoEntry = photo?.let { state.videoEntries[it.cacheKey] }
    val videoJob = photo?.let { p -> state.videoJobs.firstOrNull { it.item.cacheKey == p.cacheKey } }
    val imageLogLines = remember(entry?.logPath, entry?.createdAt) { entry?.logPath?.let { readLastLines(File(it), 16) } ?: emptyList() }
    val imageParamsLines = remember(entry?.paramsPath, entry?.createdAt) { entry?.paramsPath?.let { readLastLines(File(it), 16) } ?: emptyList() }
    val imageTimings = remember(entry?.paramsPath, entry?.createdAt) { entry?.paramsPath?.let { readImageTimings(File(it)) } }
    val videoLogLines = remember(videoEntry?.logPath, videoEntry?.createdAt) { videoEntry?.logPath?.let { readLastLines(File(it), 18) } ?: emptyList() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xff111315))
            .statusBarsPadding()
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text(lang.t("返回", "Back")) }
            Spacer(Modifier.width(8.dp))
            Text(
                text = lang.t("调试", "Debug"),
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onShare, enabled = !isVideo && entry != null) { Text(lang.t("分享调试包", "Share debug package")) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${photo?.displayName.orEmpty()}  ${lang.t("状态", "State")}: ${if (isVideo) photo?.let { state.videoStates[it.cacheKey] } ?: VideoVrState.NORMAL else vrState}",
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (isVideo) {
                DebugSection(lang.t("视频运行状态", "Video runtime")) {
                    DebugLine("State", "${photo?.let { state.videoStates[it.cacheKey] } ?: VideoVrState.NORMAL}")
                    DebugLine("Frame", "${videoJob?.currentFrame ?: 0}/${videoJob?.totalFrames ?: 0}")
                    DebugLine("Progress", "${((videoJob?.progress ?: 0f) * 100f).roundToInt()}%")
                    DebugLine("FPS", "${videoJob?.fps ?: videoEntry?.fps ?: 30}")
                    DebugLine(lang.t("当前有效帧", "Current active frame"), "${videoJob?.currentFrameMs ?: 0}ms")
                    DebugLine(lang.t("平均有效帧", "Avg active frame"), "${videoJob?.avgFrameMs ?: 0}ms")
                    val pipeline = videoJob?.pipelineStats ?: VideoPipelineStats()
                    DebugLine(lang.t("解码队列", "Decode queue"), "${pipeline.decodeQueue}")
                    DebugLine(lang.t("生成队列", "Generated queue"), "${pipeline.generatedQueue}")
                    DebugLine(lang.t("等待编码", "Encode waiting"), "${pipeline.waitingFrames}")
                    DebugLine(lang.t("缓存命中", "Cache hits"), "${pipeline.cacheHits}")
                    val timings = videoJob?.frameTimings ?: VideoFrameTimings()
                    DebugLine(lang.t("取帧", "Decode"), "${timings.decodeMs}ms")
                    DebugLine(lang.t("深度推理", "Depth"), "${timings.depthMs}ms")
                    DebugLine(lang.t("时序平滑", "Temporal"), "${timings.temporalMs}ms")
                    DebugLine(lang.t("深度后处理", "Depth post"), "${timings.depthPostMs}ms")
                    DebugLine("SBS", "${timings.sbsMs}ms")
                    DebugLine(lang.t("缓存写入", "Cache write"), "${timings.cacheWriteMs}ms")
                    DebugLine(lang.t("编码提交", "Encode submit"), "${timings.encodeMs}ms")
                    DebugLine("Model", videoJob?.modelId?.ifBlank { state.settings.videoModelId } ?: state.settings.videoModelId)
                    DebugLine("Cache", videoJob?.cacheVersion?.ifBlank { "-" } ?: "-")
                    DebugLine("Threads", "${videoJob?.modelThreads ?: state.settings.videoModelThreads}")
                    DebugLine("GPU requested", "${videoJob?.useGpu ?: state.settings.videoUseGpu}")
                    DebugLine("GPU mode", state.settings.gpuTestMode.name)
                    DebugLine("Force GPU", "${state.settings.toVideoParams().toVrParams().forceGpuNoFallback}")
                    DebugLine("Runtime", videoJob?.runtimeInfo?.ifBlank { "-" } ?: "-")
                    DebugLine("Output", videoEntry?.outputPath ?: "-")
                    DebugLine(lang.t("深度 worker", "Depth workers"), "${videoJob?.depthWorkers?.takeIf { it > 0 } ?: state.settings.videoDepthWorkers.coerceIn(1, 2)}")
                    DebugLine("Error", videoJob?.error ?: "-")
                }
                DebugSection(lang.t("视频生成日志", "Video log")) {
                    (videoLogLines.ifEmpty { state.logs.filter { it.contains(photo?.displayName.orEmpty()) || it.contains("video runtime") }.take(12) }).forEach {
                        DebugMonoLine(it)
                    }
                }
            } else {
                DebugSection(lang.t("图片运行状态", "Image runtime")) {
                    DebugLine("State", "$vrState")
                    DebugLine("Job", job?.let { "${it.state} ${(it.progress * 100f).roundToInt()}%" } ?: "-")
                    DebugLine("Source", job?.source?.name ?: state.viewerQueueSource ?: "-")
                    DebugLine("Album", job?.albumId ?: state.viewerQueueAlbumId ?: "-")
                    DebugLine("Cache", entry?.version ?: "-")
                    DebugLine("Output", entry?.let { "${it.width}x${it.height}" } ?: "-")
                    DebugLine("Left", entry?.leftPath?.takeIf { it.isNotBlank() } ?: "-")
                    DebugLine("Right", entry?.rightPath?.takeIf { it.isNotBlank() } ?: "-")
                    DebugLine("Model", state.settings.imageModelId)
                    DebugLine("Threads", "${state.settings.modelThreads}")
                    DebugLine("GPU requested", "${state.settings.useGpu}")
                    DebugLine("GPU mode", state.settings.gpuTestMode.name)
                    DebugLine("Force GPU", "${state.settings.toParams().forceGpuNoFallback}")
                    DebugLine("Error", job?.error ?: "-")
                }
                DebugSection(lang.t("性能诊断", "Performance")) {
                    DebugLine(lang.t("解码", "Decode"), imageTimings?.decodeMs?.let { "${it}ms" } ?: "-")
                    DebugLine(lang.t("模型推理", "Model"), imageTimings?.modelMs?.let { "${it}ms" } ?: "-")
                    DebugLine(lang.t("深度后处理", "Depth post"), imageTimings?.depthPostMs?.let { "${it}ms" } ?: "-")
                    DebugLine(lang.t("SBS 合成", "SBS"), imageTimings?.sbsMs?.let { "${it}ms" } ?: "-")
                    DebugLine(lang.t("写文件", "Write"), imageTimings?.writeMs?.let { "${it}ms" } ?: "-")
                    DebugLine("Left WebP", imageTimings?.writeLeftMs?.let { "${it}ms" } ?: "-")
                    DebugLine("Right WebP", imageTimings?.writeRightMs?.let { "${it}ms" } ?: "-")
                    DebugLine("Params", imageTimings?.writeParamsMs?.let { "${it}ms" } ?: "-")
                    DebugLine("Log", imageTimings?.writeLogMs?.let { "${it}ms" } ?: "-")
                    DebugLine(lang.t("总耗时", "Total"), imageTimings?.totalMs?.let { "${it}ms" } ?: "-")
                }
                Row(Modifier.fillMaxWidth().height(260.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebugImagePanel(lang.t("原图", "Source"), photo?.uri, Modifier.weight(1f), lang)
                    DebugImagePanel(lang.t("深度图", "Depth"), entry?.let { Uri.fromFile(File(it.depthPath)) }, Modifier.weight(1f), lang)
                    DebugImagePanel("Left/VR", entry?.let { Uri.fromFile(File(if (it.hasSplitEyes) it.leftPath else it.outputPath)) }, Modifier.weight(1f), lang)
                }
                Spacer(Modifier.height(8.dp))
                DebugSection(lang.t("图片参数", "Image params")) {
                    imageParamsLines.forEach { DebugMonoLine(it) }
                }
                DebugSection(lang.t("图片生成日志", "Image log")) {
                    imageLogLines.forEach { DebugMonoLine(it) }
                }
            }
            DebugSection(lang.t("队列", "Queue")) {
                state.videoJobs.take(4).forEach {
                    DebugMonoLine("${it.state.label(lang)} ${(it.progress * 100f).roundToInt()}% f=${it.currentFrame}/${it.totalFrames} ${it.item.displayName}")
                }
                state.jobs.take(4).forEach {
                    val sourceLabel = it.source?.name?.let { source -> "$source${it.albumId?.let { album -> ":$album" } ?: ""}" } ?: "-"
                    DebugMonoLine("${it.state.label(lang)} ${(it.progress * 100f).roundToInt()}% [$sourceLabel] ${it.photoItem.displayName}")
                }
            }
            DebugSection(lang.t("最近日志", "Recent app logs")) {
                state.logs.take(10).forEach { DebugMonoLine(it) }
            }
        }
    }
}

@Composable
private fun DebugSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(androidx.compose.ui.graphics.Color(0xff202326))
            .padding(10.dp),
    ) {
        Text(title, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun DebugLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = androidx.compose.ui.graphics.Color(0xffc8cdd2), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(96.dp))
        Text(value, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DebugMonoLine(value: String) {
    Text(
        value,
        color = androidx.compose.ui.graphics.Color.White,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun readLastLines(file: File, count: Int): List<String> {
    return runCatching {
        if (!file.exists()) emptyList() else file.readLines(Charsets.UTF_8).takeLast(count)
    }.getOrDefault(emptyList())
}

private fun readImageTimings(file: File): GenerationTimings? {
    return runCatching {
        if (!file.exists()) return@runCatching null
        val text = file.readText(Charsets.UTF_8)
        val decode = jsonLongValue(text, "decodeMs") ?: return@runCatching null
        GenerationTimings(
            decodeMs = decode,
            modelMs = jsonLongValue(text, "modelMs") ?: 0L,
            depthPostMs = jsonLongValue(text, "depthPostMs") ?: 0L,
            sbsMs = jsonLongValue(text, "sbsMs") ?: 0L,
            writeLeftMs = jsonLongValue(text, "writeLeftMs") ?: 0L,
            writeRightMs = jsonLongValue(text, "writeRightMs") ?: 0L,
            writeParamsMs = jsonLongValue(text, "writeParamsMs") ?: 0L,
            writeLogMs = jsonLongValue(text, "writeLogMs") ?: 0L,
            writeMs = jsonLongValue(text, "writeMs") ?: 0L,
            totalMs = jsonLongValue(text, "totalMs") ?: 0L,
        )
    }.getOrNull()
}

@Composable
private fun DebugImagePanel(title: String, uri: Uri?, modifier: Modifier = Modifier, lang: AppLanguage = AppLanguage.ZH) {
    Column(modifier) {
        Text(title, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        if (uri == null) {
            Box(
                modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xff202326)),
                contentAlignment = Alignment.Center,
            ) {
                Text(lang.t("暂无", "None"), color = androidx.compose.ui.graphics.Color.White)
            }
        } else {
            AsyncBitmapImage(
                uri,
                2048,
                ContentScale.Fit,
                modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
            )
        }
    }
}

@Composable
private fun SyncStereoEntryImage(
    entry: VrCacheEntry,
    bitmapCache: MutableMap<String, Bitmap>,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    if (entry.hasSplitEyes) {
        SyncSplitZoomImage(
            leftUri = Uri.fromFile(File(entry.leftPath)),
            rightUri = Uri.fromFile(File(entry.rightPath)),
            bitmapCache = bitmapCache,
            modifier = modifier,
            onTap = onTap,
        )
    } else {
        SyncSbsZoomImage(Uri.fromFile(File(entry.outputPath)), bitmapCache, modifier, onTap)
    }
}

@Composable
private fun SyncSbsZoomImage(
    uri: Uri,
    bitmapCache: MutableMap<String, Bitmap>,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val cacheKey = bitmapCacheKey(uri, 4096)
    var bitmap by remember(uri) { mutableStateOf(bitmapCache[cacheKey]) }
    var scale by remember(uri) { mutableStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember(uri) { mutableStateOf(IntSize.Zero) }
    var lastTapAt by remember(uri) { mutableStateOf(0L) }
    LaunchedEffect(uri) {
        bitmap = bitmapCache[cacheKey] ?: withContext(Dispatchers.IO) {
            runCatching { decodeScaledBitmap(context, uri, 4096) }.getOrNull()
        }?.also { bitmapCache[cacheKey] = it }
        scale = 1f
        offset = Offset.Zero
    }
    if (bitmap == null) {
        Box(modifier.background(androidx.compose.ui.graphics.Color(0xff202326)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(26.dp))
        }
        return
    }

    val source = bitmap!!
    val halfWidth = (source.width / 2).coerceAtLeast(1)
    val left = remember(source) { Bitmap.createBitmap(source, 0, 0, halfWidth, source.height).asImageBitmap() }
    val right = remember(source) { Bitmap.createBitmap(source, halfWidth, 0, source.width - halfWidth, source.height).asImageBitmap() }
    SyncStereoZoomContent(left, right, uri, modifier, onTap)
}

@Composable
private fun SyncSplitZoomImage(
    leftUri: Uri,
    rightUri: Uri,
    bitmapCache: MutableMap<String, Bitmap>,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val leftKey = bitmapCacheKey(leftUri, 4096)
    val rightKey = bitmapCacheKey(rightUri, 4096)
    var leftBitmap by remember(leftUri) { mutableStateOf(bitmapCache[leftKey]) }
    var rightBitmap by remember(rightUri) { mutableStateOf(bitmapCache[rightKey]) }
    LaunchedEffect(leftUri, rightUri) {
        leftBitmap = bitmapCache[leftKey] ?: withContext(Dispatchers.IO) {
            runCatching { decodeScaledBitmap(context, leftUri, 4096) }.getOrNull()
        }?.also { bitmapCache[leftKey] = it }
        rightBitmap = bitmapCache[rightKey] ?: withContext(Dispatchers.IO) {
            runCatching { decodeScaledBitmap(context, rightUri, 4096) }.getOrNull()
        }?.also { bitmapCache[rightKey] = it }
    }
    val left = leftBitmap
    val right = rightBitmap
    if (left == null || right == null) {
        Box(modifier.background(androidx.compose.ui.graphics.Color(0xff202326)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(26.dp))
        }
        return
    }
    SyncStereoZoomContent(left.asImageBitmap(), right.asImageBitmap(), "$leftUri|$rightUri", modifier, onTap)
}

@Composable
private fun SyncStereoZoomContent(
    left: ImageBitmap,
    right: ImageBitmap,
    gestureKey: Any,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    var scale by remember(gestureKey) { mutableStateOf(1f) }
    var offset by remember(gestureKey) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember(gestureKey) { mutableStateOf(IntSize.Zero) }
    var lastTapAt by remember(gestureKey) { mutableStateOf(0L) }
    val imageModifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationX = offset.x
            translationY = offset.y
        }
    Box(
        modifier = modifier
            .background(androidx.compose.ui.graphics.Color.Black)
            .onSizeChanged { viewportSize = it }
            .pointerInput(gestureKey) {
                awaitPointerEventScope {
                    while (true) {
                        val firstEvent = awaitPointerEvent(PointerEventPass.Initial)
                        if (firstEvent.changes.none { it.pressed }) continue
                        var multiTouch = false
                        var moved = false
                        var firstPosition: Offset? = null
                        var zoomAnchor: Offset? = null
                        val sbsPointerSides = mutableMapOf<Long, Boolean>()
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            if (firstPosition == null) firstPosition = pressed.firstOrNull()?.position
                            if (pressed.isEmpty()) {
                                if (!multiTouch && !moved) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastTapAt < 280L) {
                                        scale = 1f
                                        offset = Offset.Zero
                                        lastTapAt = 0L
                                    } else {
                                        lastTapAt = now
                                        onTap()
                                    }
                                }
                                break
                            }
                            if (pressed.size >= 2) {
                                multiTouch = true
                                val halfViewportWidth = viewportSize.width / 2f
                                val useSbsLocalGesture = halfViewportWidth > 0f
                                val centroid = if (useSbsLocalGesture) pressed.stableSbsLocalCentroid(halfViewportWidth, sbsPointerSides) else pressed.centroid()
                                val previousCentroid = if (useSbsLocalGesture) {
                                    pressed.previousStableSbsLocalCentroid(halfViewportWidth, sbsPointerSides)
                                } else {
                                    pressed.previousCentroid()
                                }
                                val anchor = zoomAnchor ?: centroid.also { zoomAnchor = it }
                                val previousSpan = if (useSbsLocalGesture) {
                                    pressed.previousStableSbsLocalSpan(previousCentroid, halfViewportWidth, sbsPointerSides)
                                } else {
                                    pressed.previousSpan(previousCentroid)
                                }.coerceAtLeast(1f)
                                val currentSpan = if (useSbsLocalGesture) {
                                    pressed.stableSbsLocalSpan(centroid, halfViewportWidth, sbsPointerSides)
                                } else {
                                    pressed.span(centroid)
                                }
                                val zoom = (currentSpan / previousSpan).coerceIn(0.85f, 1.18f)
                                val nextScale = (scale * zoom).coerceIn(1f, 8f)
                                val effectiveZoom = if (scale <= 0f) 1f else nextScale / scale
                                val localWidth = if (useSbsLocalGesture) halfViewportWidth else viewportSize.width.toFloat()
                                val containerCenter = Offset(localWidth / 2f, viewportSize.height / 2f)
                                scale = nextScale
                                offset = if (nextScale == 1f || viewportSize == IntSize.Zero) {
                                    Offset.Zero
                                } else {
                                    val anchorFromCenter = anchor - containerCenter
                                    anchorFromCenter - (anchorFromCenter - offset) * effectiveZoom
                                }
                                event.changes.forEach { it.consume() }
                            } else {
                                zoomAnchor = null
                                val change = pressed.first()
                                if (scale > 1f) {
                                    offset += change.position - change.previousPosition
                                    moved = true
                                    change.consume()
                                } else {
                                    val start = firstPosition
                                    val current = change.position
                                    if (start != null && (current - start).getDistance() > 18f) moved = true
                                }
                            }
                        }
                    }
                }
            },
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxSize().clipToBounds().background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.CenterEnd) {
                Image(left, contentDescription = null, modifier = imageModifier, contentScale = ContentScale.Fit)
            }
            Box(Modifier.weight(1f).fillMaxSize().clipToBounds().background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.CenterStart) {
                Image(right, contentDescription = null, modifier = imageModifier, contentScale = ContentScale.Fit)
            }
        }
        Box(Modifier.align(Alignment.Center).width(1.dp).fillMaxSize().background(androidx.compose.ui.graphics.Color(0x99ffffff)))
    }
}

private fun List<PointerInputChange>.centroid(): Offset {
    if (isEmpty()) return Offset.Zero
    val x = sumOf { it.position.x.toDouble() }.toFloat() / size
    val y = sumOf { it.position.y.toDouble() }.toFloat() / size
    return Offset(x, y)
}

private fun List<PointerInputChange>.previousCentroid(): Offset {
    if (isEmpty()) return Offset.Zero
    val x = sumOf { it.previousPosition.x.toDouble() }.toFloat() / size
    val y = sumOf { it.previousPosition.y.toDouble() }.toFloat() / size
    return Offset(x, y)
}

private fun List<PointerInputChange>.span(center: Offset): Float {
    if (isEmpty()) return 1f
    return map { (it.position - center).getDistance() }.average().toFloat()
}

private fun List<PointerInputChange>.previousSpan(center: Offset): Float {
    if (isEmpty()) return 1f
    return map { (it.previousPosition - center).getDistance() }.average().toFloat()
}

private fun PointerInputChange.stableSbsLocalPosition(halfWidth: Float, sideByPointer: MutableMap<Long, Boolean>): Offset {
    val rightEye = sideByPointer.getOrPut(id.value) { position.x >= halfWidth }
    return Offset(if (rightEye) position.x - halfWidth else position.x, position.y)
}

private fun PointerInputChange.previousStableSbsLocalPosition(halfWidth: Float, sideByPointer: MutableMap<Long, Boolean>): Offset {
    val rightEye = sideByPointer.getOrPut(id.value) { previousPosition.x >= halfWidth }
    return Offset(if (rightEye) previousPosition.x - halfWidth else previousPosition.x, previousPosition.y)
}

private fun List<PointerInputChange>.stableSbsLocalCentroid(halfWidth: Float, sideByPointer: MutableMap<Long, Boolean>): Offset {
    if (isEmpty()) return Offset.Zero
    val x = sumOf { it.stableSbsLocalPosition(halfWidth, sideByPointer).x.toDouble() }.toFloat() / size
    val y = sumOf { it.stableSbsLocalPosition(halfWidth, sideByPointer).y.toDouble() }.toFloat() / size
    return Offset(x, y)
}

private fun List<PointerInputChange>.previousStableSbsLocalCentroid(halfWidth: Float, sideByPointer: MutableMap<Long, Boolean>): Offset {
    if (isEmpty()) return Offset.Zero
    val x = sumOf { it.previousStableSbsLocalPosition(halfWidth, sideByPointer).x.toDouble() }.toFloat() / size
    val y = sumOf { it.previousStableSbsLocalPosition(halfWidth, sideByPointer).y.toDouble() }.toFloat() / size
    return Offset(x, y)
}

private fun List<PointerInputChange>.stableSbsLocalSpan(center: Offset, halfWidth: Float, sideByPointer: MutableMap<Long, Boolean>): Float {
    if (isEmpty()) return 1f
    return map { (it.stableSbsLocalPosition(halfWidth, sideByPointer) - center).getDistance() }.average().toFloat()
}

private fun List<PointerInputChange>.previousStableSbsLocalSpan(center: Offset, halfWidth: Float, sideByPointer: MutableMap<Long, Boolean>): Float {
    if (isEmpty()) return 1f
    return map { (it.previousStableSbsLocalPosition(halfWidth, sideByPointer) - center).getDistance() }.average().toFloat()
}

private fun Modifier.tapWithoutConsumingDrag(key: Any?, onTap: () -> Unit): Modifier = pointerInput(key) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed } ?: continue
            val start = down.position
            var moved = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) {
                    if (!moved) onTap()
                    break
                }
                if (pressed.any { (it.position - start).getDistance() > 18f }) moved = true
            }
        }
    }

}

private class SbsVideoGlPlayerView(context: Context) :
    GLSurfaceView(context),
    GLSurfaceView.Renderer,
    SurfaceTexture.OnFrameAvailableListener {

    private var textureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var textureHandle = 0
    private var texMatrixHandle = 0
    private val textureMatrix = FloatArray(16)
    private var frameAvailable = false
    private var surfaceCallback: ((Surface?) -> Unit)? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private var zoomScale = 1f
    private var zoomOffset = Offset.Zero

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setSurfaceCallback(callback: (Surface?) -> Unit) {
        surfaceCallback = callback
        surface?.let { currentSurface ->
            post { surfaceCallback?.invoke(currentSurface) }
        }
    }

    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        queueEvent {
            runCatching { surfaceTexture?.setDefaultBufferSize(width, height) }
            requestRender()
        }
    }

    fun setActive(isActive: Boolean) {
        renderMode = if (isActive) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY
        requestRender()
    }

    fun setZoom(scale: Float, offset: Offset) {
        zoomScale = scale
        zoomOffset = offset
        requestRender()
    }

    fun release() {
        queueEvent { releaseSurfaceTexture() }
    }

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        runCatching { surface?.release() }
        runCatching { surfaceTexture?.release() }
        surface = null
        surfaceTexture = null
        program = createExternalTextureProgram()
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        texMatrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix")
        textureId = createOesTexture()
        surfaceTexture = SurfaceTexture(textureId).also {
            it.setOnFrameAvailableListener(this)
            surface = Surface(it)
        }
        surface?.let { currentSurface ->
            post { surfaceCallback?.invoke(currentSurface) }
        }
    }

    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
        requestRender()
    }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        val texture = surfaceTexture ?: return
        synchronized(this) {
            if (frameAvailable) {
                texture.updateTexImage()
                frameAvailable = false
            }
        }
        texture.getTransformMatrix(textureMatrix)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (width <= 0 || height <= 0) return
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(textureHandle, 0)
        GLES20.glUniformMatrix4fv(texMatrixHandle, 1, false, textureMatrix, 0)
        drawEye(left = true)
        drawEye(left = false)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(this) { frameAvailable = true }
        requestRender()
    }

    private fun releaseSurfaceTexture() {
        post { surfaceCallback?.invoke(null) }
        runCatching { surface?.release() }
        runCatching { surfaceTexture?.release() }
        surface = null
        surfaceTexture = null
    }

    private fun drawEye(left: Boolean) {
        val halfWidth = (width / 2).coerceAtLeast(1)
        val viewportX = if (left) 0 else halfWidth
        GLES20.glViewport(viewportX, 0, halfWidth, height)

        val eyeAspect = if (videoWidth > 0 && videoHeight > 0) (videoWidth.toFloat() / 2f) / videoHeight.toFloat() else halfWidth.toFloat() / height.toFloat()
        val viewportAspect = halfWidth.toFloat() / height.toFloat()
        val fitX: Float
        val fitY: Float
        if (viewportAspect > eyeAspect) {
            fitX = (eyeAspect / viewportAspect).coerceIn(0f, 1f)
            fitY = 1f
        } else {
            fitX = 1f
            fitY = (viewportAspect / eyeAspect).coerceIn(0f, 1f)
        }
        val dx = (zoomOffset.x / halfWidth.toFloat()) * 2f
        val dy = -(zoomOffset.y / height.toFloat()) * 2f
        val l = -fitX * zoomScale + dx
        val r = fitX * zoomScale + dx
        val b = -fitY * zoomScale + dy
        val t = fitY * zoomScale + dy
        val s0 = if (left) 0f else 0.5f
        val s1 = if (left) 0.5f else 1f
        drawTexturedQuad(
            floatArrayOf(l, b, r, b, l, t, r, t),
            floatArrayOf(s0, 0f, s1, 0f, s0, 1f, s1, 1f),
        )
    }

    private fun drawTexturedQuad(vertices: FloatArray, texCoords: FloatArray) {
        val vertexBuffer = floatBuffer(vertices)
        val texBuffer = floatBuffer(texCoords)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }
}

private fun floatBuffer(values: FloatArray): FloatBuffer {
    return ByteBuffer.allocateDirect(values.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(values)
            position(0)
        }
}

private fun createOesTexture(): Int {
    val ids = IntArray(1)
    GLES20.glGenTextures(1, ids, 0)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    return ids[0]
}

private fun createExternalTextureProgram(): Int {
    val vertexShader = compileShader(
        GLES20.GL_VERTEX_SHADER,
        """
        attribute vec4 aPosition;
        attribute vec4 aTexCoord;
        uniform mat4 uTexMatrix;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = (uTexMatrix * aTexCoord).xy;
        }
        """.trimIndent(),
    )
    val fragmentShader = compileShader(
        GLES20.GL_FRAGMENT_SHADER,
        """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        uniform samplerExternalOES uTexture;
        varying vec2 vTexCoord;
        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoord);
        }
        """.trimIndent(),
    )
    return GLES20.glCreateProgram().also { program ->
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
    }
}

private fun compileShader(type: Int, source: String): Int {
    return GLES20.glCreateShader(type).also { shader ->
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
    }
}

@Composable
private fun WindowSelector(value: Int, onChange: (Int) -> Unit) {
    val options = listOf(2, 4, 8)
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = value == option,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text("$option")
            }
        }
    }
}

private fun bitmapCacheKey(uri: Uri, maxSide: Int): String = "${uri}@$maxSide"

@Composable
private fun AsyncBitmapImage(
    uri: Uri,
    maxSide: Int,
    contentScale: ContentScale,
    bitmapCache: MutableMap<String, Bitmap>? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cacheKey = bitmapCacheKey(uri, maxSide)
    var bitmap by remember(uri, maxSide) { mutableStateOf(bitmapCache?.get(cacheKey)) }
    LaunchedEffect(uri, maxSide) {
        bitmap = bitmapCache?.get(cacheKey) ?: withContext(Dispatchers.IO) {
            runCatching { decodeScaledBitmap(context, uri, maxSide) }.getOrNull()
        }?.also { decoded -> bitmapCache?.put(cacheKey, decoded) }
    }
    if (bitmap == null) {
        Box(modifier.background(androidx.compose.ui.graphics.Color(0xff202326)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(26.dp))
        }
    } else {
        Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = contentScale)
    }
}

@Composable
private fun AsyncMediaThumbnail(kind: MediaKind, uri: Uri, maxSide: Int, contentScale: ContentScale, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val safeSide = maxSide.coerceIn(96, 320)
    var bitmap by remember(uri, maxSide) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, maxSide) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                if (uri.scheme == "file" && kind == MediaKind.VIDEO) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(requireNotNull(uri.path) { "Missing video file path" })
                        retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {
                        retriever.release()
                    }
                } else if (Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.loadThumbnail(uri, Size(safeSide, safeSide), null)
                } else if (kind == MediaKind.IMAGE) {
                    decodeScaledBitmap(context, uri, safeSide)
                } else {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    retriever.release()
                    frame
                }
            }.getOrNull()
        }
    }
    if (bitmap == null) {
        Box(modifier.background(androidx.compose.ui.graphics.Color(0xff202326)))
    } else {
        Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = contentScale)
    }
}

@Composable
private fun VideoPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    active: Boolean,
    sbsMode: Boolean = false,
    controlsVisible: Boolean,
    stateLine: String,
    metricsLine: String,
    onSingleTap: () -> Unit,
) {
    val activeState = rememberUpdatedState(active)
    val context = LocalContext.current
    var normalVideoView by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var sbsSurface by remember(uri) { mutableStateOf<Surface?>(null) }
    var sbsGlView by remember(uri) { mutableStateOf<SbsVideoGlPlayerView?>(null) }
    var videoSize by remember { mutableStateOf(IntSize.Zero) }
    var lastTapAt by remember { mutableStateOf(0L) }
    var progress by remember(uri) { mutableStateOf(0f) }
    var positionText by remember(uri) { mutableStateOf("00:00 / 00:00") }
    var hint by remember(uri) { mutableStateOf<String?>(null) }
    var isPlaying by remember(uri) { mutableStateOf(false) }
    var playbackSpeed by remember(uri) { mutableStateOf(1f) }
    var sbsPrepared by remember(uri) { mutableStateOf(false) }
    var sbsRetryToken by remember(uri) { mutableStateOf(0) }
    var sbsActiveRetries by remember(uri) { mutableStateOf(0) }
    var scale by remember(uri) { mutableStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(uri, mediaPlayer, sbsMode, active) {
        while (true) {
            val player = mediaPlayer
            val duration = runCatching { player?.duration?.takeIf { it > 0 } ?: 0 }.getOrDefault(0)
            val position = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
            isPlaying = runCatching { player?.isPlaying == true }.getOrDefault(false)
            progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
            positionText = "${formatDuration(position)} / ${formatDuration(duration)}"
            delay(350)
        }
    }

    LaunchedEffect(uri, sbsMode) {
        scale = 1f
        offset = Offset.Zero
        playbackSpeed = 1f
    }

    DisposableEffect(uri, sbsMode, sbsSurface, sbsRetryToken) {
        if (!sbsMode || sbsSurface == null) {
            onDispose { }
        } else {
            val player = MediaPlayer()
            mediaPlayer = player
            sbsPrepared = false
            var released = false
            runCatching {
                if (uri.scheme == "file") {
                    player.setDataSource(requireNotNull(uri.path) { "Missing video file path" })
                } else {
                    player.setDataSource(context, uri)
                }
                player.setSurface(sbsSurface)
                player.isLooping = true
                player.setOnVideoSizeChangedListener { _, width, height ->
                    sbsGlView?.setVideoSize(width, height)
                }
                player.setOnPreparedListener {
                    sbsPrepared = true
                    sbsActiveRetries = 0
                    sbsGlView?.setVideoSize(it.videoWidth, it.videoHeight)
                    if (Build.VERSION.SDK_INT >= 23) runCatching { it.playbackParams = it.playbackParams.setSpeed(playbackSpeed) }
                    if (activeState.value) runCatching { it.start() } else runCatching { it.pause() }
                }
                player.setOnErrorListener { _, _, _ ->
                    if (!released) {
                        released = true
                        runCatching { player.release() }
                        if (mediaPlayer === player) mediaPlayer = null
                        sbsPrepared = false
                        if (activeState.value && sbsActiveRetries < 3) {
                            sbsActiveRetries += 1
                            sbsRetryToken += 1
                        }
                    }
                    true
                }
                player.prepareAsync()
            }.onFailure {
                released = true
                runCatching { player.release() }
                if (mediaPlayer === player) mediaPlayer = null
                sbsPrepared = false
                if (activeState.value && sbsActiveRetries < 3) {
                    sbsActiveRetries += 1
                    sbsRetryToken += 1
                }
            }
            onDispose {
                released = true
                runCatching { player.stop() }
                runCatching { player.release() }
                if (mediaPlayer === player) mediaPlayer = null
                sbsPrepared = false
            }
        }
    }

    LaunchedEffect(active, sbsMode, sbsPrepared, mediaPlayer) {
        if (sbsMode && active && !sbsPrepared && sbsActiveRetries < 3) {
            delay(900)
            if (activeState.value && !sbsPrepared) {
                sbsActiveRetries += 1
                sbsRetryToken += 1
            }
        }
    }

    LaunchedEffect(active, mediaPlayer, sbsMode, sbsPrepared) {
        if (sbsMode) {
            val player = mediaPlayer ?: return@LaunchedEffect
            sbsGlView?.setActive(active)
            if (active && sbsPrepared) {
                runCatching {
                    val position = player.currentPosition
                    player.seekTo(position)
                    if (!player.isPlaying) player.start()
                }
            } else {
                runCatching { if (player.isPlaying) player.pause() }
            }
        }
    }

    LaunchedEffect(hint) {
        if (hint != null) {
            delay(900)
            hint = null
        }
    }

    Box(
        modifier
            .background(androidx.compose.ui.graphics.Color.Black)
            .onSizeChanged { videoSize = it },
    ) {
        if (sbsMode) {
            AndroidView(
                factory = { context ->
                    SbsVideoGlPlayerView(context).apply {
                        setSurfaceCallback { surface -> sbsSurface = surface }
                        setZoom(scale, offset)
                        sbsGlView = this
                    }
                },
                update = { view ->
                    view.setSurfaceCallback { surface -> sbsSurface = surface }
                    view.setActive(active)
                    view.setZoom(scale, offset)
                    sbsGlView = view
                },
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.align(Alignment.Center).width(1.dp).fillMaxSize().background(androidx.compose.ui.graphics.Color(0x66ffffff)))
        } else {
            AndroidView(
                factory = {
                    VideoView(it).apply {
                        tag = uri
                        setVideoURI(uri)
                        setOnPreparedListener { player ->
                            mediaPlayer = player
                            player.isLooping = true
                            if (Build.VERSION.SDK_INT >= 23) runCatching { player.playbackParams = player.playbackParams.setSpeed(playbackSpeed) }
                            if (activeState.value) start() else pause()
                        }
                        normalVideoView = this
                    }
                },
                update = { view ->
                    if (view.tag != uri) {
                        view.tag = uri
                        view.setVideoURI(uri)
                    }
                    if (active) {
                        if (!view.isPlaying) view.start()
                    } else if (view.isPlaying) {
                        view.pause()
                    }
                    normalVideoView = view
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        DisposableEffect(uri, sbsMode) {
            onDispose {
                if (!sbsMode) {
                    runCatching { normalVideoView?.stopPlayback() }
                    normalVideoView = null
                    mediaPlayer = null
                } else {
                    sbsGlView?.release()
                    sbsGlView = null
                    sbsSurface = null
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(uri) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed } ?: continue
                            var moved = false
                            var multiTouch = false
                            val start = down.position
                            var zoomAnchor: Offset? = null
                            val sbsPointerSides = mutableMapOf<Long, Boolean>()
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    multiTouch = true
                                    moved = true
                                    val halfWidth = videoSize.width / 2f
                                    val useSbsLocalGesture = sbsMode && halfWidth > 0f
                                    val centroid = if (useSbsLocalGesture) pressed.stableSbsLocalCentroid(halfWidth, sbsPointerSides) else pressed.centroid()
                                    val previousCentroid = if (useSbsLocalGesture) {
                                        pressed.previousStableSbsLocalCentroid(halfWidth, sbsPointerSides)
                                    } else {
                                        pressed.previousCentroid()
                                    }
                                    val anchor = zoomAnchor ?: centroid.also { zoomAnchor = it }
                                    val previousSpan = if (useSbsLocalGesture) {
                                        pressed.previousStableSbsLocalSpan(previousCentroid, halfWidth, sbsPointerSides)
                                    } else {
                                        pressed.previousSpan(previousCentroid)
                                    }.coerceAtLeast(1f)
                                    val currentSpan = if (useSbsLocalGesture) {
                                        pressed.stableSbsLocalSpan(centroid, halfWidth, sbsPointerSides)
                                    } else {
                                        pressed.span(centroid)
                                    }
                                    val zoom = (currentSpan / previousSpan).coerceIn(0.85f, 1.18f)
                                    val nextScale = (scale * zoom).coerceIn(1f, 8f)
                                    val effectiveZoom = if (scale <= 0f) 1f else nextScale / scale
                                    scale = nextScale
                                    val localWidth = if (useSbsLocalGesture) halfWidth else videoSize.width.toFloat()
                                    val containerCenter = Offset(localWidth / 2f, videoSize.height / 2f)
                                    offset = if (nextScale == 1f || videoSize == IntSize.Zero) {
                                        Offset.Zero
                                    } else {
                                        val anchorFromCenter = anchor - containerCenter
                                        anchorFromCenter - (anchorFromCenter - offset) * effectiveZoom
                                    }
                                    event.changes.forEach { it.consume() }
                                } else {
                                    zoomAnchor = null
                                    val change = pressed.first()
                                    if (scale > 1f) {
                                        offset += change.position - change.previousPosition
                                        moved = true
                                        change.consume()
                                    } else if ((change.position - start).getDistance() > 18f) {
                                        moved = true
                                    }
                                }
                            }
                            if (multiTouch) continue
                            if (!moved) {
                                val now = System.currentTimeMillis()
                                if (now - lastTapAt < 280L) {
                                    scale = 1f
                                    offset = Offset.Zero
                                    lastTapAt = 0L
                                } else {
                                    lastTapAt = now
                                    onSingleTap()
                                }
                            }
                        }
                    }
                },
        )
        hint?.let {
            Text(
                text = it,
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(androidx.compose.ui.graphics.Color(0xaa000000))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color(0x99000000))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    IconButton(
                        onClick = {
                            val player = mediaPlayer
                            val playing = runCatching { player?.isPlaying == true }.getOrDefault(false)
                            if (playing) {
                                runCatching { player?.pause() }
                                isPlaying = false
                            } else {
                                runCatching { player?.start() }
                                isPlaying = true
                            }
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            val player = mediaPlayer
                            val duration = runCatching { player?.duration ?: 0 }.getOrDefault(0).coerceAtLeast(0)
                            val current = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0).coerceAtLeast(0)
                            val target = (current + 5_000).let { if (duration > 0) it.coerceAtMost(duration) else it }
                            runCatching {
                                if (Build.VERSION.SDK_INT >= 26 && player != null) {
                                    player.seekTo(target.toLong(), MediaPlayer.SEEK_CLOSEST)
                                } else {
                                    player?.seekTo(target)
                                }
                            }
                            progress = if (duration > 0) (target.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else progress
                            positionText = "${formatDuration(target)} / ${formatDuration(duration)}"
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FastForward,
                            contentDescription = "Forward 5 seconds",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            playbackSpeed = if (playbackSpeed >= 2f) 1f else 2f
                            if (Build.VERSION.SDK_INT >= 23) {
                                runCatching { mediaPlayer?.let { it.playbackParams = it.playbackParams.setSpeed(playbackSpeed) } }
                            }
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Text(if (playbackSpeed >= 2f) "2x" else "1x", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stateLine,
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = metricsLine,
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(positionText, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall)
                }
                Slider(
                    value = progress,
                    onValueChange = { value ->
                        val player = mediaPlayer
                        val duration = runCatching { player?.duration ?: 0 }.getOrDefault(0).coerceAtLeast(0)
                        progress = value.coerceIn(0f, 1f)
                        if (duration > 0) {
                            val target = (duration * progress).roundToInt().coerceIn(0, duration)
                            runCatching {
                                if (Build.VERSION.SDK_INT >= 26 && player != null) {
                                    player.seekTo(target.toLong(), MediaPlayer.SEEK_CLOSEST)
                                } else {
                                    player?.seekTo(target)
                                }
                            }
                            positionText = "${formatDuration(target)} / ${formatDuration(duration)}"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun formatDuration(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(Locale.US, minutes, seconds)
}

private fun hasImagePermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, imagePermission()) == PackageManager.PERMISSION_GRANTED
}

private fun hasVideoPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, videoPermission()) == PackageManager.PERMISSION_GRANTED
}

private fun hasNotificationPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

private fun imagePermission(): String {
    return if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
}

private fun videoPermission(): String {
    return if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
}

private fun notificationPermission(): String {
    return if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else Manifest.permission.READ_EXTERNAL_STORAGE
}

private fun mediaPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun decodeScaledBitmap(context: Context, uri: Uri, maxLongEdge: Int): Bitmap {
    if (Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = min(1f, maxLongEdge.toFloat() / max(w, h).toFloat())
            decoder.setTargetSize(max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }.copy(Bitmap.Config.ARGB_8888, false)
    }

    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
    var sample = 1
    while (max(options.outWidth / sample, options.outHeight / sample) > maxLongEdge) sample *= 2
    val decode = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri).use { input ->
        BitmapFactory.decodeStream(input, null, decode) ?: error("Unable to decode image")
    }.copy(Bitmap.Config.ARGB_8888, false)
}

private fun decodeGeneratedLeftPreview(entry: VrCacheEntry, maxLongEdge: Int): Bitmap {
    if (entry.hasSplitEyes) {
        return decodeScaledBitmap(File(entry.leftPath), maxLongEdge)
    }
    return decodeSbsLeftPreview(File(entry.outputPath), maxLongEdge)
}

private fun decodeScaledBitmap(file: File, maxLongEdge: Int): Bitmap {
    require(file.exists()) { "Image file missing: ${file.absolutePath}" }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxLongEdge.coerceAtLeast(96)) sample *= 2
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        },
    ) ?: error("Unable to decode image file: ${file.absolutePath}")
}

private fun decodeSbsLeftPreview(file: File, maxLongEdge: Int): Bitmap {
    require(file.exists()) { "Generated file missing: ${file.absolutePath}" }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val width = bounds.outWidth
    val height = bounds.outHeight
    require(width > 0 && height > 0) { "Invalid generated image bounds: ${file.absolutePath}" }
    val leftWidth = (width / 2).coerceAtLeast(1)
    var sample = 1
    while (max(leftWidth / sample, height / sample) > maxLongEdge.coerceAtLeast(96)) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    FileInputStream(file).use { input ->
        val decoder = BitmapRegionDecoder.newInstance(input, false)
            ?: error("Unable to open generated preview decoder: ${file.absolutePath}")
        return try {
            decoder.decodeRegion(Rect(0, 0, leftWidth, height), options)
                ?: error("Unable to decode generated preview: ${file.absolutePath}")
        } finally {
            decoder.recycle()
        }
    }
}

private fun saveImageToGallery(context: Context, source: File, displayName: String, mimeType: String = "image/jpeg"): Uri {
    val extension = when (mimeType) {
        "image/avif" -> "avif"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
    val safeName = displayName.withImageExtension(extension).ifBlank { "parallel_vr_${System.currentTimeMillis()}.$extension" }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, safeName)
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000L)
        put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ParallelVrGalleryPro")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore insert failed")
    runCatching {
        resolver.openOutputStream(uri)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Unable to open gallery output")
        if (Build.VERSION.SDK_INT >= 29) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
    }.onFailure { error ->
        resolver.delete(uri, null, null)
        throw error
    }
    return uri
}

private fun saveVideoToGallery(context: Context, source: File, displayName: String): Uri {
    val safeName = displayName.ifBlank { "parallel_vr_${System.currentTimeMillis()}.mp4" }
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, safeName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000L)
        put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ParallelVrGalleryPro")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore insert failed")
    runCatching {
        resolver.openOutputStream(uri)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Unable to open video output")
        if (Build.VERSION.SDK_INT >= 29) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        }
    }.onFailure { error ->
        resolver.delete(uri, null, null)
        throw error
    }
    return uri
}

private fun replaceOriginalImage(context: Context, photo: PhotoItem, source: File) {
    val resolver = context.contentResolver
    resolver.openOutputStream(photo.uri, "w")?.use { output ->
        source.inputStream().use { input -> input.copyTo(output) }
    } ?: error("Unable to open original image for writing")
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, photo.displayName)
        put(MediaStore.Images.Media.DATE_MODIFIED, photo.modifiedTime)
    }
    resolver.update(photo.uri, values, null, null)
}

private fun writeWebpLossless(bitmap: Bitmap, file: File) {
    file.parentFile?.mkdirs()
    FileOutputStream(file).use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output)) {
            "WEBP lossless encode failed: ${file.absolutePath}"
        }
    }
}

private fun readCachedSbsFrame(framesDir: File, frame: Int): Bitmap? {
    val left = File(framesDir, "left_${frame.toString().padStart(6, '0')}.webp")
    val right = File(framesDir, "right_${frame.toString().padStart(6, '0')}.webp")
    if (left.exists() && right.exists()) {
        val leftBitmap = BitmapFactory.decodeFile(left.absolutePath) ?: return null
        val rightBitmap = BitmapFactory.decodeFile(right.absolutePath)
        if (rightBitmap == null) {
            leftBitmap.recycle()
            return null
        }
        return try {
            composeSbsBitmap(leftBitmap, rightBitmap)
        } finally {
            leftBitmap.recycle()
            rightBitmap.recycle()
        }
    }
    val legacy = File(framesDir, "frame_${frame.toString().padStart(6, '0')}.jpg")
    return if (legacy.exists()) BitmapFactory.decodeFile(legacy.absolutePath) else null
}

private fun writeStereoFrameCache(framesDir: File, frame: Int, sbs: Bitmap) {
    framesDir.mkdirs()
    val half = (sbs.width / 2).coerceAtLeast(1)
    val left = Bitmap.createBitmap(sbs, 0, 0, half, sbs.height)
    val right = Bitmap.createBitmap(sbs, half, 0, sbs.width - half, sbs.height)
    try {
        writeWebpLossless(left, File(framesDir, "left_${frame.toString().padStart(6, '0')}.webp"))
        writeWebpLossless(right, File(framesDir, "right_${frame.toString().padStart(6, '0')}.webp"))
    } finally {
        left.recycle()
        right.recycle()
    }
}

private fun composeSbsBitmap(left: Bitmap, right: Bitmap): Bitmap {
    val width = left.width + right.width
    val height = max(left.height, right.height)
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawBitmap(left, 0f, 0f, null)
    canvas.drawBitmap(right, left.width.toFloat(), 0f, null)
    return output
}

private fun composeSbsBitmap(entry: VrCacheEntry): Bitmap {
    if (!entry.hasSplitEyes) {
        return BitmapFactory.decodeFile(entry.outputPath)
            ?: error("Missing legacy SBS cache: ${entry.outputPath}")
    }
    val left = BitmapFactory.decodeFile(entry.leftPath) ?: error("Missing left eye cache: ${entry.leftPath}")
    val right = BitmapFactory.decodeFile(entry.rightPath) ?: error("Missing right eye cache: ${entry.rightPath}")
    return try {
        composeSbsBitmap(left, right)
    } finally {
        left.recycle()
        right.recycle()
    }
}

private fun materializeSbsJpeg(entry: VrCacheEntry, quality: Int = 96): File {
    val legacy = File(entry.outputPath)
    if (legacy.exists()) return legacy
    val dir = File(entry.paramsPath).parentFile ?: legacy.parentFile ?: error("Missing cache directory for ${entry.photoKey}")
    val output = File(dir, "export_sbs.jpg")
    if (output.exists()) return output
    val bitmap = composeSbsBitmap(entry)
    try {
        FileOutputStream(output).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), stream)) {
                "JPEG SBS export failed: ${output.absolutePath}"
            }
        }
    } finally {
        bitmap.recycle()
    }
    return output
}

private fun materializeSbsAvif(entry: VrCacheEntry): File {
    val dir = File(entry.paramsPath).parentFile ?: error("Missing cache directory for ${entry.photoKey}")
    val output = File(dir, "export_sbs.avif")
    if (output.exists()) return output
    val bitmap = composeSbsBitmap(entry)
    val evenBitmap = bitmap.ensureEvenDimensions()
    try {
        val bytes = HeifCoder().encodeAvif(evenBitmap, quality = 100, preciseMode = PreciseMode.LOSSLESS)
        output.writeBytes(bytes)
    } finally {
        if (evenBitmap !== bitmap) evenBitmap.recycle()
        bitmap.recycle()
    }
    return output
}

private fun Bitmap.ensureEvenDimensions(): Bitmap {
    val evenWidth = if (width % 2 == 0) width else width + 1
    val evenHeight = if (height % 2 == 0) height else height + 1
    if (evenWidth == width && evenHeight == height) return this
    val padded = Bitmap.createBitmap(evenWidth, evenHeight, Bitmap.Config.ARGB_8888)
    Canvas(padded).drawBitmap(this, 0f, 0f, null)
    return padded
}

private fun String.withImageExtension(extension: String): String {
    val clean = replace(Regex("\\.(jpg|jpeg|png|webp|avif)$", RegexOption.IGNORE_CASE), "")
    return "$clean.$extension"
}

private fun loadModelFile(file: File): ByteBuffer {
    FileInputStream(file).use { input ->
        val channel = input.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
    }
}

private fun VrGenerationParams.toJson(photo: PhotoItem, source: Bitmap, outputWidth: Int, outputHeight: Int, timings: GenerationTimings): String {
    return """
        {
          "photoKey": "${photo.cacheKey}",
          "cacheVersion": "${cacheVersion()}",
          "displayName": "${photo.displayName.replace("\"", "\\\"")}",
          "sourceWidth": ${source.width},
          "sourceHeight": ${source.height},
          "outputWidth": $outputWidth,
          "outputHeight": $outputHeight,
          "depthModel": "$depthModel",
          "outputMode": "SPLIT_EYES_WEBP_LOSSLESS",
          "depthScale": $depthScale,
          "blurRadius": $blurRadius,
          "fillRadius": $fillRadius,
          "invertDepth": $invertDepth,
          "maxLongEdge": $maxLongEdge,
          "modelThreads": $modelThreads,
          "useGpu": $useGpu,
          "gpuTestMode": "$gpuTestMode",
          "forceGpuNoFallback": $forceGpuNoFallback,
          "inpaintMode": "$inpaintMode",
          "quality": $quality,
          "timings": {
            "decodeMs": ${timings.decodeMs},
            "modelMs": ${timings.modelMs},
            "depthPostMs": ${timings.depthPostMs},
            "sbsMs": ${timings.sbsMs},
            "writeLeftMs": ${timings.writeLeftMs},
            "writeRightMs": ${timings.writeRightMs},
            "writeParamsMs": ${timings.writeParamsMs},
            "writeLogMs": ${timings.writeLogMs},
            "writeMs": ${timings.writeMs},
            "totalMs": ${timings.totalMs}
          },
          "modelSha256": "B407F34F61750F31441E6F858A4BC48D8572F9EE5399FFD015CEE5FA1767083F",
          "modelSource": "https://github.com/7116-byte/ParallelVrGallery/releases/download/model-assets-v1/depth_anything_v2.tflite"
        }
    """.trimIndent()
}

private fun VrGenerationParams.cacheVersion(): String {
    val scale = depthScale.roundToInt()
    val invert = if (invertDepth) "inv1" else "inv0"
    val gpu = if (useGpu) "gpu1" else "gpu0"
    val force = if (forceGpuNoFallback) "force1" else "force0"
    return "${depthModel}_${IMAGE_GENERATOR_VERSION}_s${scale}_b${blurRadius}_f${fillRadius}_${invert}_m${maxLongEdge}_t${modelThreads}_${gpu}_${gpuTestMode}_$force"
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
}

private fun VideoGenerationParams.cacheVersion(): String {
    return "${toVrParams().cacheVersion()}_dw${depthWorkers.coerceIn(1, 2)}_$VIDEO_ENCODER_VERSION"
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
}

private fun Throwable.shortMessage(): String {
    val type = this::class.java.simpleName.ifBlank { "Error" }
    val message = message?.replace('\n', ' ')?.replace('\r', ' ')?.take(180)
    return if (message.isNullOrBlank()) type else "$type:$message"
}

private fun Float.format3(): String = "%.3f".format(Locale.US, this)

private fun compareVersionTags(left: String, right: String): Int {
    val a = left.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
    val b = right.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
    val size = max(a.size, b.size)
    for (index in 0 until size) {
        val av = a.getOrElse(index) { 0 }
        val bv = b.getOrElse(index) { 0 }
        if (av != bv) return av.compareTo(bv)
    }
    return 0
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02X".format(it) }
}

private fun ZipOutputStream.addFile(name: String, file: File) {
    putNextEntry(ZipEntry(name))
    file.inputStream().use { it.copyTo(this) }
    closeEntry()
}

