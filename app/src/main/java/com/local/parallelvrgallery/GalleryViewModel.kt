package com.local.parallelvrgallerypro

import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Size
import android.view.Surface
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.Closeable
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class GalleryViewModel @JvmOverloads constructor(application: Application, private val serviceResumeKeys: Set<String> = emptySet()) : AndroidViewModel(application) {
    private val app = application
    private val repository = PhotoRepository(app)
    private val cache = VrCacheManager(app)
    private val videoCache = VideoVrCacheManager(app)
    private val videoNotifier = VideoGenerationNotifier(app)
    private val videoKeepAlive = VideoGenerationKeepAlive(app)
    private val backgroundRuns = BackgroundRunLedger(File(app.getExternalFilesDir(null), "queue_index/background_run.txt"))
    private val uiVisible = MutableStateFlow(false)
    private val modelManager = ModelManager(app)
    private val generator = VrGenerator(app, cache, modelManager)
    private val videoGenerator = VideoVrGenerator(app, videoCache, generator)
    private val settingsStore = SettingsStore(app)
    private val imageRuns = GenerationGuard()
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
    private val videoControlLock = Any()
    private var videoQueueLease: Closeable? = null
    private var restoredQueueTags = false
    @Volatile private var queueRestorationComplete = false
    private val workers = mutableListOf<Job>()
    private val videoWorkers = mutableListOf<Job>()
    private var currentWorker: Job? = null
    private val activeImageKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val runningImageJobs = java.util.concurrent.ConcurrentHashMap<String, QueuedJob>()
    private val claimedImageJobs = mutableSetOf<QueuedJob>()
    private val currentImageFirst = CurrentImageFirst()
    private var modelWarmupJob: Job? = null
    private var comparisonJob: Job? = null
    private var imageExportJob: Job? = null
    private var backgroundTrimJob: Job? = null
    private val comparisonRevision = java.util.concurrent.atomic.AtomicLong()

    private val _uiState = MutableStateFlow(
        UiState(
            hasPermission = hasImagePermission(app),
            hasVideoPermission = hasVideoPermission(app),
            hasNotificationPermission = hasNotificationPermission(app),
            settings = settingsStore.load(),
            gallerySortMode = settingsStore.loadGallerySortMode(),
            galleryViewMode = settingsStore.loadGalleryViewMode(),
        ),
    )
    val uiState: StateFlow<UiState> = _uiState

    init {
        modelWarmupJob = viewModelScope.launch(Dispatchers.IO) {
            combine(_uiState, uiVisible) { state, visible ->
                state.settings.toParams() to state.canWarmImageModel(visible, queueRestorationComplete)
            }.distinctUntilChanged { old, new -> old.first.depthSessionKey() == new.first.depthSessionKey() && old.second == new.second }
                .collectLatest { (params, allowed) ->
                    if (!allowed) return@collectLatest
                    delay(1200) // Keep cold-start layout and immediate navigation clear of model compilation.
                    val key = params.depthSessionKey()
                    fun stillIdle(): Boolean = _uiState.value.let {
                        it.canWarmImageModel(uiVisible.value, queueRestorationComplete) &&
                            it.settings.toParams().depthSessionKey() == key
                    }
                    if (!stillIdle()) return@collectLatest
                    val warmupContext = currentCoroutineContext()
                    fun checkCurrent() {
                        warmupContext.ensureActive()
                        if (!stillIdle()) throw java.util.concurrent.CancellationException("Image warmup no longer idle")
                    }
                    var lastWarmupStatus: String? = null
                    fun report(status: String) {
                        lastWarmupStatus = status
                        _uiState.update {
                            if (it.settings.toParams().depthSessionKey() == key) it.copy(modelWarmupStatus = status) else it
                        }
                    }
                    if (!modelManager.hasLocalModel(params.depthModel)) {
                        val status = if (hasQnnModelPrefix(params.depthModel)) modelManager.statusText(params.depthModel)
                            else "模型未下载，首次生成时下载 / Model downloads on first generation"
                        report(status)
                        return@collectLatest
                    }
                    val qnn = qnnModelConfig(params.depthModel)
                    val qnnShapes = qnn?.let { QnnInputGeometry.warmupGeometries(it.size, it.aspect) }
                    if (qnnShapes?.isEmpty() == true) {
                        report("按素材比例准备推理尺寸 / Model shape is prepared for each source aspect")
                        return@collectLatest
                    }
                    val started = SystemClock.uptimeMillis()
                    report("正在后台初始化图片模型 / Preparing image model in background")
                    addLog("model warmup start $key")
                    android.util.Log.i("PvgDepthWarmup", "Gallery loaded, preparing model=${params.depthModel}")
                    try {
                        generator.prepareImageSession(params, qnnShapes.orEmpty(), ::checkCurrent, onShapeReady = { count, total ->
                            if (count == total) {
                                val elapsed = SystemClock.uptimeMillis() - started
                                val ratios = if (qnn?.aspect == QnnDepthAspect.PORTRAIT_PAIR) "9:16 / 3:4" else "16:9 / 9:16"
                                report("$ratios 已在内存就绪（${elapsed}ms）/ $ratios ready in memory")
                            } else {
                                report("正在后台预热图片模型（$count/$total）/ Preparing image model in background ($count/$total)")
                            }
                            android.util.Log.i("PvgDepthWarmup", "Prepared shape $count/$total model=${params.depthModel}")
                        }) { addLog("model warmup $it") }
                        checkCurrent()
                        val elapsed = SystemClock.uptimeMillis() - started
                        if (qnnShapes?.size != 2) report("图片模型已在内存就绪（${elapsed}ms）/ Image model ready in memory")
                        addLog("model warmup ready $key ${elapsed}ms")
                        android.util.Log.i("PvgDepthWarmup", "Model ready=${params.depthModel} elapsedMs=$elapsed")
                    } catch (error: java.util.concurrent.CancellationException) {
                        _uiState.update {
                            if (it.settings.toParams().depthSessionKey() == key && it.modelWarmupStatus == lastWarmupStatus) {
                                it.copy(modelWarmupStatus = null)
                            } else it
                        }
                        throw error
                    } catch (error: Throwable) {
                        report("模型预加载失败，生成时可重试 / Model preload failed: ${error.shortMessage()}")
                        addLog("model warmup failed $key: ${error.shortMessage()}")
                        android.util.Log.w("PvgDepthWarmup", "Model preparation failed=${params.depthModel}: ${error.shortMessage()}")
                    }
                }
        }
        if (_uiState.value.hasPermission) {
            loadPhotos()
        }
    }

    override fun onCleared() {
        comparisonRevision.incrementAndGet()
        comparisonJob?.cancel()
        val imageJobs = workers.toList() + listOfNotNull(currentWorker, modelWarmupJob, imageExportJob)
        imageJobs.forEach { it.cancel() }
        AppWorkScopes.video.launch {
            imageJobs.forEach { it.join() }
            queueTags.close()
            videoWorkers.toList().forEach { it.join() }
            generator.close()
            videoQueueTags.close()
        }
        super.onCleared()
    }

    fun setUiVisible(visible: Boolean) {
        uiVisible.value = visible
        backgroundTrimJob?.cancel()
        if (!visible) backgroundTrimJob = viewModelScope.launch {
            delay(30_000)
            if (!uiVisible.value) trimIdleGenerationMemory()
        }
    }

    fun onUiOwnerRemoved() {
        closeComparison()
        if (_uiState.value.selectedIndex != null) closeViewer()
        _uiState.update { it.copy(settingsOpen = false, manageOpen = false, debugIndex = null) }
    }

    fun flushWorkForBackground() {
        AppWorkScopes.video.launch { queueTags.flush(); videoQueueTags.flush() }
    }

    fun stopServiceIfIdleAfterRestore() {
        AppWorkScopes.video.launch {
            _uiState.first { !it.loading }
            delay(500)
            if (!videoKeepAlive.active()) app.stopService(Intent(app, VideoGenerationForegroundService::class.java))
        }
    }

    fun onForegroundServiceDestroyed() {
        if (videoKeepAlive.active()) onForegroundUnavailable("后台服务被停止 / Background service stopped")
    }

    private fun pauseImagesForService(reason: String): Unit = synchronized(imageRuns) {
        val jobs = (synchronized(pending) { pending.toList() } + synchronized(currentPending) { currentPending.toList() } + claimedImageJobs + runningImageJobs.values)
            .distinctBy { it.photo.cacheKey }.filter { _uiState.value.states[it.photo.cacheKey] in setOf(VrState.QUEUED, VrState.GENERATING) }
        val keys = jobs.mapTo(hashSetOf()) { it.photo.cacheKey }
        imageRuns.invalidate(keys)
        synchronized(pending) { pending.removeAll { it.photo.cacheKey in keys } }
        synchronized(currentPending) { currentPending.removeAll { it.photo.cacheKey in keys } }
        synchronized(paused) {
            autoPausedKeys.removeAll(keys); sourcePausedKeys.removeAll(keys)
            jobs.forEach { job ->
                val saved = job.copy(runToken = imageRuns.token(job.photo.cacheKey))
                paused[job.photo.cacheKey] = saved
                queueTags.upsert(saved, VrState.PAUSED, reason)
                markState(job.photo.cacheKey, VrState.PAUSED)
                upsertJob(job.photo, job.priority, VrState.PAUSED, 0f, error = reason, source = job.source, albumId = job.albumId)
            }
        }
        imageExportJob?.cancel()
    }

    fun onForegroundUnavailable(detail: String) {
        pauseImagesForService("SERVICE_UNAVAILABLE")
        pauseVideosByKey(_uiState.value.videoStates.filterValues(VideoCommandPolicy::canPause).keys, "SERVICE_UNAVAILABLE")
        _uiState.update { it.copy(message = "后台服务不可用，任务已暂停；重新打开后可继续 / Background service unavailable: $detail") }
        synchronized(videoControlLock) { videoQueueLease = null; videoKeepAlive.stopAll() }
        flushWorkForBackground()
    }

    fun onBackgroundTimeLimit() {
        pauseImagesForService("SYSTEM_LIMIT")
        pauseVideosByKey(_uiState.value.videoStates.filterValues(VideoCommandPolicy::canPause).keys, "SYSTEM_LIMIT")
        _uiState.update { it.copy(message = "已达系统后台时限，任务已保存并暂停；回到应用后可继续 / System background time limit reached") }
        synchronized(videoControlLock) { videoQueueLease = null; videoKeepAlive.stopAll() }
        flushWorkForBackground()
    }

    fun trimIdleGenerationMemory() {
        AppWorkScopes.video.launch {
            if (generator.trimIdleSessions() > 0) _uiState.update { it.copy(modelWarmupStatus = "内存紧张，已释放空闲模型；需要时重新准备 / Idle model released under memory pressure") }
        }
    }

    fun onPermissionChanged(imageGranted: Boolean, videoGranted: Boolean, notificationGranted: Boolean) {
        _uiState.update { it.copy(hasPermission = imageGranted, hasVideoPermission = videoGranted, hasNotificationPermission = notificationGranted) }
        if (imageGranted) loadPhotos()
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = null) }
            val snapshot = withContext(Dispatchers.IO) {
                cache.refresh()
                videoCache.refresh()
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
            synchronized(imageRuns) {
                val latestManaged = cache.allEntries(snapshot.photos)
                val latestEntries = latestManaged.groupBy { it.entry.photoKey }.mapValues { (_, items) -> items.maxBy { it.entry.createdAt }.entry }
                _uiState.update {
                    val readyVideoStates = snapshot.videoEntries.keys.associateWith { VideoVrState.READY }
                    val activeVideoStates = it.videoStates.filterValues { state -> state in setOf(VideoVrState.QUEUED, VideoVrState.GENERATING, VideoVrState.PAUSED, VideoVrState.FAILED) }
                    val snapshotKeys = snapshot.photos.map { item -> item.cacheKey }.toSet()
                    val activeVideoItems = it.videoJobs
                        .filter { job -> job.state != VideoVrState.READY || job.item.cacheKey !in snapshot.videoEntries }
                        .map { job -> if (job.item.cacheKey in snapshotKeys) job.item else job.item.copy(generatedVirtual = true) }
                    val mergedPhotos = (snapshot.photos + activeVideoItems)
                        .distinctBy { item -> item.cacheKey }
                    it.copy(
                        photos = mergedPhotos,
                        albums = snapshot.albums,
                        entries = latestEntries,
                        states = latestEntries.keys.associateWith { VrState.READY } + it.states.filterValues { value -> value in setOf(VrState.QUEUED, VrState.GENERATING, VrState.PAUSED, VrState.FAILED) },
                        videoStates = readyVideoStates + activeVideoStates,
                        videoEntries = snapshot.videoEntries,
                        allOffset = mergedPhotos.count { !it.generatedVirtual },
                        allExhausted = mergedPhotos.count { !it.generatedVirtual } < INITIAL_MEDIA_LIMIT,
                        albumOffsets = emptyMap(),
                        loading = !queueRestorationComplete,
                        message = "已加载最近 ${mergedPhotos.count { !it.generatedVirtual }} 项媒体，生成库 ${latestManaged.size + snapshot.videoEntries.size} 项 / Loaded recent media and generated cache",
                        cacheVersions = cache.summaries(),
                        managedCacheItems = latestManaged,
                        modelStatus = modelStatusText(it.settings),
                    )
                }
            }
            if (!restoredQueueTags) {
                restoredQueueTags = true
                withContext(Dispatchers.IO) {
                    restoreQueueTags()
                    restoreVideoQueueTags()
                }
                queueRestorationComplete = true
                _uiState.update { it.copy(loading = false) }
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

    fun openScopedPhoto(index: Int, scopeKeys: List<String>) = synchronized(imageRuns) {
        val item = _uiState.value.photos.getOrNull(index)
        currentImageFirst.select(item?.cacheKey)
        pauseWaitingImageJobs()
        val queueContext = activeQueueContext(_uiState.value)
        val allSource = isAllGallerySource(queueContext.source.name)
        val orderedKeys = scopeKeys.ifEmpty {
            _uiState.value.photos.filter {
                !it.generatedVirtual && (queueContext.source != QueueSource.ALBUM || it.bucketId == queueContext.albumId)
            }.map { it.cacheKey }
        }
        _uiState.update {
            it.copy(
                selectedIndex = index,
                galleryAnchorIndex = if (allSource) index else it.galleryAnchorIndex,
                galleryAnchorKey = if (allSource) item?.cacheKey else it.galleryAnchorKey,
                vrMode = true,
                viewerOrigin = ViewerOrigin.NORMAL,
                viewerTravelDirection = 0,
                manageViewerKeys = emptySet(),
                viewerScopeKeys = orderedKeys.toSet(),
                viewerScopeOrderedKeys = orderedKeys,
                viewerQueueSource = queueContext.source.name,
                viewerQueueAlbumId = queueContext.albumId,
                activePrefetchWindow = if (it.settings.autoPrefetch) 2 else it.settings.prefetchWindow,
                prefetchSlotState = PrefetchSlotState(),
                message = null,
            )
        }
        if (item?.kind == MediaKind.IMAGE) enqueueWindow(index, includeCurrent = true)
    }

    fun openGeneratedPhoto(index: Int, entry: VrCacheEntry) = synchronized(imageRuns) {
        currentImageFirst.select(null)
        _uiState.update {
            val album = generatedAlbumKey(entry.version)
            val results = it.managedCacheItems.filter { item -> generatedAlbumKey(item.entry.version) == album }
            val keys = results.map { item -> item.entry.photoKey }.distinct()
            val origin = if (it.manageOpen) ViewerOrigin.MANAGE_MODAL else ViewerOrigin.GENERATED_TAB
            it.copy(
                selectedIndex = index,
                manageOpen = false,
                homeTab = if (origin == ViewerOrigin.GENERATED_TAB) "generated" else it.homeTab,
                generatedTab = "images",
                selectedGeneratedVersion = album,
                viewerOrigin = origin,
                vrMode = false,
                manageViewerKeys = keys.toSet(),
                viewerScopeKeys = keys.toSet(),
                viewerScopeOrderedKeys = keys,
                viewerGeneratedResults = results.map { item -> item.resultKey },
                viewerResultVersion = entry.version,
                viewerQueueSource = QueueSource.GENERATED.name,
                viewerQueueAlbumId = null,
                message = null,
            )
        }
    }

    fun openGeneratedVideo(index: Int) = synchronized(imageRuns) {
        currentImageFirst.select(null)
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
                manageOpen = false,
                homeTab = if (origin == ViewerOrigin.GENERATED_TAB) "generated" else it.homeTab,
                generatedTab = "videos",
                selectedGeneratedVersion = null,
                viewerOrigin = origin,
                vrMode = false,
                manageViewerKeys = keys,
                viewerScopeKeys = keys,
                viewerScopeOrderedKeys = keys.toList(),
                viewerQueueSource = QueueSource.GENERATED.name,
                viewerQueueAlbumId = null,
                message = null,
            )
        }
    }

    fun closeViewer() = synchronized(imageRuns) {
        val wasNormalViewer = _uiState.value.viewerOrigin == ViewerOrigin.NORMAL
        currentImageFirst.select(null)
        _uiState.update {
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
                viewerGeneratedResults = null,
                viewerResultVersion = null,
                viewerQueueSource = null,
                viewerQueueAlbumId = null,
                vrMode = if (wasNormalViewer) false else it.vrMode,
                prefetchSlotState = PrefetchSlotState(),
            )
        }
        if (!wasNormalViewer) {
            rebalanceQueueForActiveSource()
            startWorker()
        }
    }

    fun setHomeTab(tab: String) = synchronized(imageRuns) {
        _uiState.update {
            it.copy(
                homeTab = tab,
                manageOpen = false,
                vrMode = if (it.selectedIndex == null) false else it.vrMode,
                selectedGeneratedVersion = null,
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

    fun setGallerySortMode(mode: GallerySortMode) {
        settingsStore.saveGallerySortMode(mode)
        _uiState.update { it.copy(gallerySortMode = mode) }
    }

    fun setGalleryViewMode(mode: GalleryViewMode) {
        settingsStore.saveGalleryViewMode(mode)
        _uiState.update { it.copy(galleryViewMode = mode) }
    }

    fun setGalleryFilterMode(mode: GalleryFilterMode) {
        _uiState.update { it.copy(galleryFilterMode = mode) }
    }

    fun toggleGeneratedVersion(version: String) {
        _uiState.update {
            val expanded = it.expandedGeneratedVersions
            it.copy(expandedGeneratedVersions = if (version in expanded) expanded - version else expanded + version)
        }
    }

    fun openAlbum(bucketId: String) {
        _uiState.update { it.copy(homeTab = "albums", selectedAlbumId = bucketId, manageOpen = false) }
        if (bucketId !in _uiState.value.albumOffsets) loadAlbumPage(bucketId, reset = true)
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
        urls.forEach { sourceUrl ->
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
                val total = connection.contentLengthLong
                var readTotal = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(output).use { fileOut ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            fileOut.write(buffer, 0, read)
                            readTotal += read
                            val progress = UpdatePolicy.downloadFraction(readTotal, total)
                            _uiState.update {
                                it.copy(
                                    updateDownloadProgress = progress,
                                    updateStatus = if (progress < 0) lang.t("已下载 ${readTotal / 1024 / 1024} MB（${sourceUrl.hostLabel()}）", "Downloaded ${readTotal / 1024 / 1024} MB (${sourceUrl.hostLabel()})") else lang.t(
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
        val archive = app.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error("Cannot inspect update signing certificate")
        val digests = archive.signingInfo?.apkContentsSigners?.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        }.orEmpty()
        UpdatePolicy.validate(archive.packageName, app.packageName, downloadedTag, downloadedCode, CURRENT_VERSION_TAG, CURRENT_VERSION_CODE, expectedTag, digests)
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

    fun updateSettings(requested: AppSettings) {
        val before = _uiState.value.settings
        val settings = if (requested.edgeMode != before.edgeMode) {
            settingsStore.save(before)
            settingsStore.profile(requested.edgeMode).applyTo(requested)
        } else requested
        settingsStore.save(settings)
        val modelChanged = before.toParams().depthSessionKey() != settings.toParams().depthSessionKey()
        _uiState.update {
            it.copy(
                settings = settings,
                activePrefetchWindow = if (settings.autoPrefetch) 2 else settings.prefetchWindow,
                prefetchSlotState = PrefetchSlotState(),
                modelStatus = modelStatusText(settings),
                modelWarmupStatus = if (modelChanged) null else it.modelWarmupStatus,
            )
        }
        _uiState.value.selectedIndex?.let { refreshWindow(it) }
        startWorker()
    }

    fun saveModeDefault(): Boolean {
        val settings = _uiState.value.settings
        val saved = settingsStore.saveDefault(settings)
        _uiState.update { it.copy(message = if (saved) "已保存 ${settings.edgeMode.shortLabel()} 的默认视觉参数" else "默认值保存失败，请检查存储空间") }
        return saved
    }

    fun resetModeDefault(factory: Boolean = false) {
        val settings = _uiState.value.settings
        if (factory) settingsStore.clearDefault(settings.edgeMode)
        updateSettings(settingsStore.defaultProfile(settings.edgeMode).applyTo(settings))
    }

    fun onPagerIndexChanged(index: Int, version: String?) = synchronized(imageRuns) {
        val before = _uiState.value
        if (before.viewerGeneratedResults != null) {
            if (before.selectedIndex != null && (before.selectedIndex != index || before.viewerResultVersion != version)) {
                _uiState.update { it.copy(selectedIndex = index, viewerResultVersion = version) }
            }
            return
        }
        if (before.selectedIndex == null || before.selectedIndex == index || index !in viewerScopeIndices(before)) return
        currentImageFirst.select(before.photos.getOrNull(index)?.cacheKey)
        if (before.viewerOrigin == ViewerOrigin.NORMAL && before.vrMode) pauseWaitingImageJobs()
        val scope = viewerScopeIndices(before)
        val direction = NeighborPriority.direction(scope.indexOf(before.selectedIndex), scope.indexOf(index))
        val allSource = isAllGallerySource(before.viewerQueueSource)
        _uiState.update {
            it.copy(
                selectedIndex = index,
                galleryAnchorIndex = if (allSource) index else it.galleryAnchorIndex,
                galleryAnchorKey = if (allSource) before.photos.getOrNull(index)?.cacheKey else it.galleryAnchorKey,
                viewerTravelDirection = direction,
                activePrefetchWindow = if (it.settings.autoPrefetch) 2 else it.settings.prefetchWindow,
                prefetchSlotState = PrefetchSlotState(),
            )
        }
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
                VideoVrState.PAUSED -> enqueueVideo(item)
                VideoVrState.FAILED -> enqueueVideo(item)
                else -> enqueueVideo(item)
            }
            return
        }
        if (_uiState.value.viewerOrigin != ViewerOrigin.NORMAL || isGeneratedQueueSurface(_uiState.value)) return
        if (_uiState.value.vrMode) {
            stopVr()
        } else {
            synchronized(imageRuns) { currentImageFirst.select(item.cacheKey) }
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
        val active = activeImageKeys.toSet()
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
        queuedKeys.removeAll(active)
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
            enqueueVideo(item)
        } else {
            enqueuePhoto(index, priority = 0, force = true, current = true)
            startCurrentWorker()
        }
    }

    fun exportDebug(context: Context, index: Int) {
        val state = _uiState.value
        val photo = state.photos.getOrNull(index) ?: return
        val version = state.viewerResultVersion
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val entry = cache.findEntry(photo, version) ?: return@withContext null
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

    fun openDebug(index: Int, version: String?) {
        _uiState.update {
            if (version == null) it.copy(debugIndex = index)
            else it.copy(debugIndex = index, selectedIndex = index, viewerResultVersion = version)
        }
    }

    fun startComparison(index: Int) {
        val photo = _uiState.value.photos.getOrNull(index)?.takeIf { it.kind == MediaKind.IMAGE && !it.generatedVirtual } ?: return
        val revision = comparisonRevision.incrementAndGet()
        comparisonJob?.cancel()
        val base = _uiState.value.settings
        val profiles = DepthEdgeMode.entries.associateWith { mode ->
            if (mode == base.edgeMode) ModeVisualProfile.capture(base) else settingsStore.profile(mode)
        }
        val longEdge = profiles.values.maxOf { it.maxLongEdge }
        val id = java.util.UUID.randomUUID().toString()
        _uiState.update { it.copy(comparison = ModeComparisonState(id, photo.cacheKey, photo.displayName, longEdge)) }
        comparisonJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val images = generator.generateComparison(photo, base, profiles, longEdge,
                    shouldContinue = { comparisonRevision.get() == revision },
                    onProgress = { progress, message ->
                        _uiState.update { state -> if (state.comparison?.id == id) state.copy(comparison = state.comparison.copy(progress = progress, message = message)) else state }
                    })
                _uiState.update { state -> if (state.comparison?.id == id) state.copy(comparison = state.comparison.copy(images = images, progress = 1f)) else state }
            } catch (cancelled: java.util.concurrent.CancellationException) {
                // Native inference finishes on its owner; obsolete comparison cannot publish anything.
            } catch (error: Throwable) {
                _uiState.update { state -> if (state.comparison?.id == id) state.copy(comparison = state.comparison.copy(error = error.shortMessage())) else state }
            }
        }
    }

    fun closeComparison() {
        comparisonRevision.incrementAndGet()
        comparisonJob?.cancel()
        comparisonJob = null
        _uiState.update { it.copy(comparison = null) }
        // UI bitmaps are released by GC after Compose drops the last frame, never recycled under a draw.
    }

    fun closeDebug() {
        closeComparison()
        _uiState.update { it.copy(debugIndex = null) }
        _uiState.value.selectedIndex?.let(::refreshWindow)
    }

    fun openManage() {
        _uiState.update { it.copy(manageOpen = true, cacheVersions = cache.summaries()) }
    }

    fun closeManage() {
        _uiState.update { it.copy(manageOpen = false) }
    }

    fun deleteGeneratedAlbums(albums: List<String>) = synchronized(imageRuns) {
        if (albums.isEmpty()) return
        val selected = albums.toSet()
        val entries = _uiState.value.managedCacheItems.filter { generatedAlbumKey(it.entry.version) in selected }
        if (entries.isEmpty()) return
        deleteImageWork(entries, selected)
    }

    fun saveGeneratedCopy(context: Context, index: Int) {
        saveGeneratedCopies(context, listOf(index))
    }

    fun saveGeneratedCopies(context: Context, indexes: List<Int>) {
        val state = _uiState.value
        val version = state.viewerResultVersion
        val selected = indexes.distinct().mapNotNull { index ->
            state.photos.getOrNull(index)?.let { photo -> cache.findEntry(photo, version)?.let { ManagedCacheItem(photo, it) } }
        }
        saveManagedGeneratedCopies(context, selected)
    }

    fun saveManagedGeneratedCopies(context: Context, selected: List<ManagedCacheItem>) {
        if (imageExportJob?.isActive == true) return
        val lang = _uiState.value.settings.language
        // Capture exact entries and parameters before asynchronous work or navigation changes the selection.
        val requests = selected.distinctBy { it.entry.photoKey to it.entry.version }.map { item ->
            Triple(item, imageRuns.token(item.entry.photoKey), runCatching { readOriginalExportSpec(item.entry) })
        }
        if (requests.isEmpty()) { _uiState.update { it.copy(message = lang.t("没有可保存的已生成 VR 图", "No generated VR images to save")) }; return }
        _uiState.update { it.copy(blockingMessage = lang.t("正在从原图生成无损 PNG", "Rendering lossless PNG from original")) }
        imageExportJob = viewModelScope.launch {
            var protection: Closeable? = null
            try {
                protection = videoKeepAlive.acquire()
                val message = withContext(Dispatchers.IO) {
                val coroutineJob = kotlin.coroutines.coroutineContext[Job]
                var saved = 0
                var failed = 0
                var failure: String? = null
                requests.forEachIndexed { position, (item, token, captured) ->
                    val photo = item.photoItem
                    val entry = item.entry
                    fun current() = coroutineJob?.isActive != false && imageRuns.isCurrent(entry.photoKey, token)
                    val work = File(app.getExternalFilesDir(null), "export_work/${java.util.UUID.randomUUID()}")
                    try {
                        val spec = captured.getOrThrow()
                        check(current() && cache.findEntry(photo, entry.version)?.createdAt == entry.createdAt) { "Result deleted or replaced" }
                        check(work.mkdirs()) { "Cannot create export staging directory" }
                        val output = generator.exportOriginalPng(photo, spec, File(work, "original_sbs.png"), ::current) { _, stage ->
                            _uiState.update { it.copy(blockingMessage = lang.t("无损 PNG ${position + 1}/${requests.size} · $stage", "Lossless PNG ${position + 1}/${requests.size} · $stage")) }
                        }
                        saveImageToGallery(context, output, "${GENERATED_VR_PREFIX}${photo.cacheKey}_${photo.displayName}", "image/png") { publish ->
                            check(imageRuns.ifCurrent(entry.photoKey, token) {
                                check(current() && cache.findEntry(photo, entry.version)?.createdAt == entry.createdAt) { "结果已删除或替换，取消保存 / Result deleted or replaced" }
                                publish()
                            }) { "结果已删除或替换，取消保存 / Result deleted or replaced" }
                        }
                        saved++
                    } catch (error: Throwable) {
                        if (coroutineJob?.isActive == false) throw error
                        failed++; failure = error.message
                    } finally { work.deleteRecursively() }
                }
                when {
                    failed > 0 -> lang.t("已保存 $saved 张无损 PNG，失败 $failed 张：${failure.orEmpty()}", "Saved $saved lossless PNG images, failed $failed: ${failure.orEmpty()}")
                    else -> lang.t("已从原图生成并保存 $saved 张无损 PNG", "Rendered and saved $saved lossless PNG images from originals")
                }
                }
                _uiState.update { it.copy(message = message) }
            } catch (error: Exception) {
                if (error is java.util.concurrent.CancellationException) throw error
                _uiState.update { it.copy(message = "保存失败 / Save failed: ${error.message}") }
            } finally {
                protection?.close()
                _uiState.update { it.copy(blockingMessage = null) }
            }
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
                backgroundRuns.revoke(setOf(it.cacheKey))
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
        if (entries.isNotEmpty()) deleteImageWork(entries)
    }

    private fun deleteImageWork(entries: List<ManagedCacheItem>, albums: Set<String> = emptySet()) {
        // Invalidate synchronously: a worker finishing during the IO dispatch cannot resurrect a deletion.
        val selectedIdentities = entries.mapTo(hashSetOf()) { it.resultKey }
        val versions = if (albums.isEmpty()) emptySet() else entries.mapTo(hashSetOf()) { it.entry.version }
        val (keys, deletingAll) = synchronized(imageRuns) {
            val currentParams = _uiState.value.settings.toParams()
            val jobs = synchronized(pending) { pending.toList() } +
                synchronized(currentPending) { currentPending.toList() } +
                synchronized(paused) { paused.values.toList() } + claimedImageJobs + runningImageJobs.values.toList()
            val deletingAll = albums.isEmpty() && _uiState.value.managedCacheItems.all { item ->
                item.resultKey in selectedIdentities
            }
            val affected = entries.mapTo(mutableSetOf()) { it.entry.photoKey }
            if (deletingAll) {
                affected += _uiState.value.jobs.map { it.photoItem.cacheKey }
                affected += queueTags.load().map { it.photoKey }
            }
            if (albums.isNotEmpty()) {
                jobs.filter { generatedAlbumKey((it.params ?: currentParams).cacheVersion()) in albums }
                    .mapTo(affected) { it.photo.cacheKey }
                queueTags.load().filter { generatedAlbumKey((it.params ?: currentParams).cacheVersion()) in albums }
                    .mapTo(affected) { it.photoKey }
            }
            imageRuns.invalidate(affected)
            clearImageQueueForKeys(affected)
            _uiState.update { it.copy(blockingMessage = it.settings.language.t("删除中", "Deleting")) }
            affected to deletingAll
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                synchronized(imageRuns) {
                    versions.forEach { cache.deleteVersion(it) }
                    entries.filter { it.entry.version !in versions }.forEach { cache.deleteEntry(it.entry) }
                    val photos = _uiState.value.photos
                    if (deletingAll) cache.allEntries(photos).filter { it.entry.photoKey in keys }.forEach { cache.deleteEntry(it.entry) }
                    val remaining = cache.allEntries(photos)
                    val currentEntries = remaining.groupBy { it.entry.photoKey }.mapValues { (_, items) -> items.maxBy { it.entry.createdAt }.entry }
                    _uiState.update {
                        it.copy(
                            entries = currentEntries,
                            states = (it.states - keys) + currentEntries.keys.associateWith { VrState.READY },
                            cacheVersions = cache.summaries(),
                            managedCacheItems = remaining,
                            selectedGeneratedVersion = it.selectedGeneratedVersion?.takeIf { album ->
                                remaining.any { item -> generatedAlbumKey(item.entry.version) == album }
                            },
                            blockingMessage = null,
                            message = it.settings.language.t("已删除 ${entries.size} 张图片缓存，相关任务已取消", "Deleted ${entries.size} image caches and cancelled their jobs"),
                        )
                    }
                }
            } catch (error: Exception) {
                _uiState.update { it.copy(blockingMessage = null, message = "删除失败 / Delete failed: ${error.message}") }
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

    fun regenerateViewedImage(index: Int) {
        val state = _uiState.value
        val photo = state.photos.getOrNull(index) ?: return
        val entry = state.viewerEntry(photo) ?: return
        regenerateImages(listOf(ManagedCacheItem(photo, entry)))
    }

    fun regenerateImages(selected: List<ManagedCacheItem>) {
        val state = _uiState.value
        val selectedByKey = selected.filter { it.photoItem.kind == MediaKind.IMAGE && !it.photoItem.generatedVirtual }
            .groupBy { it.entry.photoKey }
        submitManualImages(selectedByKey.mapNotNull { (key, _) -> state.photos.firstOrNull { it.cacheKey == key } }, selectedByKey)
    }

    fun requestAdditionalImages(indexes: List<Int>) {
        _uiState.update { state ->
            val sources = indexes.mapNotNull { state.photos.getOrNull(it) }
                .filter { it.kind == MediaKind.IMAGE && !it.generatedVirtual }
                .map { it.cacheKey }.distinct()
            if (sources.isEmpty()) state.copy(message = state.settings.language.t("没有可用原图", "No original photos available"))
            else state.copy(additionalImageSources = sources)
        }
    }

    fun cancelAdditionalImages() {
        _uiState.update { it.copy(additionalImageSources = emptyList()) }
    }

    fun confirmAdditionalImages(): Unit = synchronized(imageRuns) {
        val state = _uiState.value
        val requested = state.settings.toParams()
        val pendingJobs = synchronized(pending) { pending.toList() } +
            synchronized(currentPending) { currentPending.toList() } +
            synchronized(paused) { paused.values.toList() } + claimedImageJobs + runningImageJobs.values
        val pendingResults = pendingJobs.filter { imageRuns.isCurrent(it.photo.cacheKey, it.runToken) }
            .map { it.photo.cacheKey to (it.params ?: requested).cacheVersion() }
        val version = requested.cacheVersion()
        val photos = additionalImageSourceKeys(
            state.additionalImageSources,
            version,
            state.managedCacheItems.map { it.resultKey },
            pendingResults,
        ).mapNotNull { key -> state.photos.firstOrNull { it.cacheKey == key && !it.generatedVirtual } }
            .filter { cache.findEntry(it, version) == null }
        val showAlbums = state.homeTab == "generated" || state.manageOpen || state.viewerOrigin != ViewerOrigin.NORMAL
        if (showAlbums && state.selectedIndex != null) closeViewer()
        _uiState.update {
            it.copy(
                additionalImageSources = emptyList(),
                homeTab = if (showAlbums) "generated" else it.homeTab,
                generatedTab = if (showAlbums) "images" else it.generatedTab,
                selectedGeneratedVersion = if (showAlbums) null else it.selectedGeneratedVersion,
                manageOpen = if (showAlbums) false else it.manageOpen,
                message = if (photos.isEmpty()) it.settings.language.t(
                    "这套参数的图片已存在或正在排队",
                    "These photos already have results for these parameters or are queued",
                ) else it.message,
            )
        }
        if (photos.isNotEmpty()) submitManualImages(photos, emptyMap(), requested)
    }

    private fun submitManualImages(
        photos: List<PhotoItem>,
        selected: Map<String, List<ManagedCacheItem>>,
        requested: VrGenerationParams = _uiState.value.settings.toParams(),
    ) {
        if (photos.isEmpty()) { _uiState.update { it.copy(message = "没有可用原图，未修改旧结果") }; return }
        synchronized(imageRuns) {
            val keys = photos.mapTo(mutableSetOf()) { it.cacheKey }
            imageRuns.invalidate(keys)
            clearImageQueueForKeys(keys)
            photos.distinctBy { it.cacheKey }.forEachIndexed { order, photo ->
                val targets = selected[photo.cacheKey].orEmpty().map { CacheReplacementTarget(it.entry.version, it.entry.createdAt) }.distinct()
                val existingTarget = if (targets.isNotEmpty()) cache.findEntry(photo, requested.cacheVersion()) else null
                // A pre-existing unselected target version is historical too: use a distinct slot.
                val params = if (existingTarget != null && targets.none { it.version == existingTarget.version })
                    requested.copy(resultVariant = java.util.UUID.randomUUID().toString().take(8)) else requested
                val index = _uiState.value.photos.indexOfFirst { it.cacheKey == photo.cacheKey }
                if (index >= 0) enqueuePhoto(index, order + 1, force = true, context = QueueContext(QueueSource.GENERATED, null),
                    paramsOverride = params, writeIntent = ImageWriteIntent(targets, forceRender = true))
            }
            _uiState.update { it.copy(message = if (selected.isEmpty()) "已加入 ${photos.size} 张图片：使用新参数生成，旧结果全部保留"
                else "已加入 ${photos.size} 张图片：成功后仅替换所选旧结果，失败保留；其他版本不动") }
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
                backgroundRuns.revoke(setOf(it.cacheKey))
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

    private fun canPrefetch(state: UiState): Boolean = ViewerScope.canPrefetch(
        vrMode = state.vrMode,
        normalOrigin = state.viewerOrigin == ViewerOrigin.NORMAL,
        viewerOpen = state.selectedIndex != null && state.debugIndex == null && state.comparison == null,
        imageSelected = state.selectedIndex?.let { state.photos.getOrNull(it)?.kind == MediaKind.IMAGE } == true,
        generatedSurface = isGeneratedQueueSurface(state),
        hasSourceScope = state.viewerScopeOrderedKeys.isNotEmpty() && state.viewerQueueSource in setOf(QueueSource.ALL.name, QueueSource.ALBUM.name),
    )

    private fun enqueueWindow(index: Int, includeCurrent: Boolean) = synchronized(imageRuns) {
        val state = _uiState.value
        val photos = state.photos
        if (!canPrefetch(state) || index != state.selectedIndex) return
        if (photos.isEmpty()) return
        val scopeIndices = ViewerScope.indices(photos.map { it.cacheKey }, state.viewerScopeOrderedKeys, state.viewerScopeKeys)
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
        if (!currentImageFirst.permits(photos[index].cacheKey, cache.findEntry(photos[index], currentVersion) != null)) return
        fun eligibleAt(position: Int): Int? {
            val photoIndex = scopeIndices.getOrNull(position) ?: return null
            val photo = photos[photoIndex]
            if (photo.kind != MediaKind.IMAGE) return null
            if (photo.cacheKey in targetKeys) return null
            val itemState = state.states[photo.cacheKey] ?: VrState.NORMAL
            if (!NeighborPriority.canAdmit(itemState, synchronized(paused) {photo.cacheKey in autoPausedKeys})) return null
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
            val direction = if(state.settings.autoPrefetch) state.viewerTravelDirection else 0
            targets += photoIndex to NeighborPriority.rank(scopeIndices.indexOf(photoIndex)-scopePosition,direction)
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
            val limit = if (state.settings.autoPrefetch) 8 else window
            val keep = targets.map { photos[it.first].cacheKey }.toSet() +
                (max(0, scopePosition - limit)..min(scopeIndices.lastIndex, scopePosition + limit))
                    .map { photos[scopeIndices[it]].cacheKey }
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
        addLog("image prefetch-window center=${photos[index].displayName} source=${queueContext.source} album=${queueContext.albumId} tier=$window targets=${targets.size} scope=${scopeIndices.size} direction=${state.viewerTravelDirection}")
        targets.forEach { (targetIndex, priority) -> enqueuePhoto(targetIndex, priority, force = false, current = false, context = queueContext) }
        rebalanceQueueForActiveSource()
        startWorker()
    }

    private fun promoteCurrentImageForVr(index: Int, context: QueueContext? = null) = synchronized(imageRuns) {
        val photo = _uiState.value.photos.getOrNull(index) ?: return
        if (photo.kind != MediaKind.IMAGE) return
        if (runningImageJobs[photo.cacheKey]?.let { imageRuns.isCurrent(photo.cacheKey, it.runToken) } == true) return
        val currentVersion = _uiState.value.settings.toParams().cacheVersion()
        cache.findEntry(photo, currentVersion)?.let { entry ->
            markReady(photo.cacheKey, entry)
            queueTags.remove(photo.cacheKey)
            return
        }
        val queueContext = context ?: activeQueueContext(_uiState.value)
        val job = QueuedJob(photo, priority = 0, sequence = sequence++, source = queueContext.source, albumId = queueContext.albumId,
            runToken = imageRuns.token(photo.cacheKey), params = _uiState.value.settings.toParams())
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

    private fun enqueuePhoto(index: Int, priority: Int, force: Boolean, current: Boolean = false, context: QueueContext? = null, paramsOverride: VrGenerationParams? = null, writeIntent: ImageWriteIntent? = null) = synchronized(imageRuns) {
        val state = _uiState.value
        val photo = state.photos.getOrNull(index) ?: return
        if (photo.kind != MediaKind.IMAGE) return
        if (!force && !current && (!canPrefetch(state) || photo.cacheKey !in state.viewerScopeKeys)) return
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
        val job = (if (resumableAutoPaused) {
            synchronized(paused) {
                val old = paused.remove(photo.cacheKey)
                autoPausedKeys.remove(photo.cacheKey)
                sourcePausedKeys.remove(photo.cacheKey)
                old?.copy(priority = priority, sequence = sequence++, source = queueContext.source, albumId = queueContext.albumId) ?: QueuedJob(photo, priority, sequence++, queueContext.source, queueContext.albumId)
            }
        } else {
            QueuedJob(photo, priority, sequence++, queueContext.source, queueContext.albumId)
        }).copy(runToken = imageRuns.token(photo.cacheKey), params = paramsOverride ?: _uiState.value.settings.toParams(), writeIntent = writeIntent)
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
        val admission = if (force) "manual" else if (resumableAutoPaused) "resume-window" else "new-prefetch"
        addLog("image $admission ${photo.displayName} p=$priority source=${queueContext.source}${queueContext.albumId?.let { ":$it" } ?: ""} scope=${state.viewerScopeOrderedKeys.size}")
    }

    private fun startWorker(): Unit = synchronized(imageRuns) {
        workers.removeAll { !it.isActive }
        val desired = _uiState.value.settings.generationWorkers.coerceIn(1, 3)
        repeat((desired - workers.size).coerceAtLeast(0)) {
            workers += viewModelScope.launch(Dispatchers.IO) {
                try { workerLoop() } finally {
                    val self = kotlin.coroutines.coroutineContext[Job]
                    synchronized(imageRuns) {
                        workers.remove(self)
                        if (self?.isActive == true && synchronized(pending) { pending.any { jobAllowedInCurrentContext(it, _uiState.value) } }) startWorker()
                    }
                }
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

    private fun pauseWaitingImageJobs() = synchronized(imageRuns) {
        val waiting = synchronized(pending) { pending.toList().also { pending.clear() } } +
            synchronized(currentPending) { currentPending.toList().also { currentPending.clear() } }
        synchronized(paused) {
            waiting.filter { imageRuns.isCurrent(it.photo.cacheKey, it.runToken) }.forEach { job ->
                paused[job.photo.cacheKey] = job
                autoPausedKeys += job.photo.cacheKey
                queueTags.upsert(job, VrState.PAUSED)
                markState(job.photo.cacheKey, VrState.PAUSED)
                upsertJob(job.photo, job.priority, VrState.PAUSED, 0f, source = job.source, albumId = job.albumId)
            }
        }
    }

    private fun isGeneratedQueueSurface(state: UiState): Boolean {
        return state.homeTab == "generated" || state.manageOpen || (state.selectedIndex != null && state.viewerOrigin != ViewerOrigin.NORMAL)
    }

    private fun jobAllowedInCurrentContext(job: QueuedJob, state: UiState): Boolean {
        if (!imageRuns.isCurrent(job.photo.cacheKey, job.runToken)) return false
        if (job.writeIntent != null) return true // Explicit request only; never admits neighbors.
        if (isGeneratedQueueSurface(state)) {
            return sameQueueSource(job, QueueContext(QueueSource.GENERATED, null)) || synchronized(paused) { job.photo.cacheKey in sourcePausedKeys }
        }
        if (state.vrMode && state.viewerOrigin == ViewerOrigin.NORMAL && state.viewerScopeOrderedKeys.isNotEmpty() && job.photo.cacheKey !in state.viewerScopeOrderedKeys) {
            return false
        }
        return sameQueueSource(job, activeQueueContext(state))
    }

    private fun pollAllowedPendingJob(): QueuedJob? = synchronized(imageRuns) {
        val state = _uiState.value
        val scope = if(state.settings.autoPrefetch && canPrefetch(state)) state.viewerScopeOrderedKeys.withIndex().associate { it.value to it.index } else emptyMap()
        val center = state.selectedIndex?.let { state.photos.getOrNull(it)?.cacheKey }?.let { scope[it] }
        fun rank(job:QueuedJob):Int = if(job.writeIntent == null && center != null && job.photo.cacheKey in scope)
            NeighborPriority.rank(scope.getValue(job.photo.cacheKey)-center,state.viewerTravelDirection) else job.priority
        return synchronized(pending) {
            val job = pending
                .sortedWith(compareBy<QueuedJob> { rank(it) }.thenBy { it.sequence })
                .firstOrNull { jobAllowedInCurrentContext(it, state) }
            if (job != null) {
                pending.remove(job)
                claimedImageJobs += job
            }
            job
        }
    }

    private fun pollAllowedCurrentJob(): QueuedJob? = synchronized(imageRuns) {
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
        if (allowed != null) claimedImageJobs += allowed
        return allowed
    }

    private fun rebalanceQueueForActiveSource() = synchronized(imageRuns) {
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
            // Window-paused jobs are admitted only by enqueueWindow, never by a global source rebalance.
            val items = paused.values.filter {
                ViewerScope.canResumeExisting(it.photo.cacheKey in sourcePausedKeys, state.selectedIndex != null, isGeneratedQueueSurface(state)) &&
                    jobAllowedInCurrentContext(it, state)
            }
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
                addLog("image resume-source ${job.photo.displayName} source=${job.source}${job.albumId?.let { ":$it" } ?: ""} surface=${state.homeTab}; existing job only")
            }
        }
    }

    private fun restoreQueueTags(): Unit = synchronized(imageRuns) {
        val state = _uiState.value
        val photoByKey = state.photos.filter { it.kind == MediaKind.IMAGE }.associateBy { it.cacheKey }
        val restoredStates = mutableMapOf<String, VrState>()
        val pendingJobs = mutableListOf<QueuedJob>()
        val pausedJobs = mutableListOf<QueuedJob>()
        queueTags.load().forEach { tag ->
            val photo = photoByKey[tag.photoKey] ?: return@forEach
            if (tag.writeIntent != null && tag.params == null) {
                restoredStates[tag.photoKey] = VrState.FAILED
                return@forEach // Missing snapshot cannot retire an old result using different parameters.
            }
            if (tag.writeIntent?.forceRender != true && cache.findEntry(photo, (tag.params ?: state.settings.toParams()).cacheVersion()) != null) {
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
            val job = QueuedJob(photo, tag.priority, sequence++, tag.source, tag.albumId,
                runToken = imageRuns.token(photo.cacheKey), params = tag.params ?: state.settings.toParams(), writeIntent = tag.writeIntent)
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
            val entry = tag.params?.let { videoCache.findEntry(item, it.cacheVersion()) } ?: if (tag.params == null) videoCache.findEntry(item) else null
            if (entry != null) {
                videoQueueTags.remove(tag.videoKey)
                backgroundRuns.revoke(setOf(tag.videoKey))
                restoredStates[tag.videoKey] = VideoVrState.READY
                return@forEach
            }
            val resume = VideoCommandPolicy.canRestoreAutomatically(tag.videoKey, tag.state, tag.params != null, serviceResumeKeys)
            val restoredState = if (resume) VideoVrState.QUEUED else when (tag.state) {
                VideoVrState.READY -> VideoVrState.READY
                VideoVrState.FAILED -> VideoVrState.FAILED
                VideoVrState.NORMAL -> VideoVrState.PAUSED
                VideoVrState.QUEUED,
                VideoVrState.GENERATING,
                VideoVrState.PAUSED -> VideoVrState.PAUSED
            }
            restoredStates[tag.videoKey] = restoredState
            if (resume) synchronized(videoPending) {
                videoPending.add(VideoQueuedJob(item, requireNotNull(tag.params), videoSequence++, nextVideoRunToken(tag.videoKey)))
            }
            if (restoredState == VideoVrState.PAUSED) {
                backgroundRuns.revoke(setOf(tag.videoKey))
                videoQueueTags.upsert(tag.videoKey, VideoVrState.PAUSED, tag.params,
                    tag.pauseReason ?: "PROCESS_RESTART")
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
                    error = when {
                        restoredState == VideoVrState.FAILED -> "上次生成失败"
                        restoredState != VideoVrState.PAUSED -> null
                        tag.pauseReason == "USER" -> "用户暂停"
                        tag.pauseReason == "SYSTEM_LIMIT" -> "系统后台时限暂停"
                        tag.pauseReason == "SERVICE_UNAVAILABLE" -> "后台服务不可用，已暂停"
                        else -> "进程重启后暂停，可继续"
                    },
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
        if (synchronized(videoPending) { videoPending.isNotEmpty() }) startVideoWorkers()
    }

    private fun startCurrentWorker(): Unit = synchronized(imageRuns) {
        if (currentWorker?.isActive == true) return
        currentWorker = viewModelScope.launch(Dispatchers.IO) {
            var protection: Closeable? = null
            try {
                var idleChecks = 0
                while (true) {
                    val next = pollAllowedCurrentJob()
                    if (next == null) {
                        if (idleChecks++ < 3) {
                            delay(50)
                            continue
                        }
                        break
                    }
                    idleChecks = 0
                    if (protection == null) {
                        try { protection = videoKeepAlive.acquire() }
                        catch (error: RuntimeException) { onForegroundUnavailable(error.message.orEmpty()); break }
                    }
                    processJob(next)
                    delay(25)
                }
            } finally {
                protection?.close()
                val self = kotlin.coroutines.coroutineContext[Job]
                synchronized(imageRuns) {
                    if (currentWorker === self) currentWorker = null
                    if (self?.isActive == true && synchronized(currentPending) { currentPending.any { jobAllowedInCurrentContext(it, _uiState.value) } }) startCurrentWorker()
                }
            }
        }
    }

    fun startVideos(indexes: List<Int>) {
        if (!queueRestorationComplete) return
        val state = _uiState.value
        val selected = indexes.distinct().mapNotNull { state.photos.getOrNull(it) }
            .filter { it.kind == MediaKind.VIDEO && VideoCommandPolicy.canStart(state.videoStates[it.cacheKey] ?: VideoVrState.NORMAL) }
        if (selected.isEmpty()) return
        try { backgroundRuns.authorize(selected.mapTo(linkedSetOf()) { it.cacheKey }) }
        catch (error: Exception) { _uiState.update { it.copy(message = "无法保存任务状态，未开始生成 / Cannot persist work: ${error.message}") }; return }
        selected.forEach { enqueueVideo(it, authorize = false) }
        _uiState.update { it.copy(message = "已请求开始 ${selected.size} 项视频，已完成项跳过 / Start requested for ${selected.size} videos. Completed videos skipped.") }
    }

    fun pauseVideos(indexes: List<Int>) {
        val state = _uiState.value
        val keys = indexes.distinct().mapNotNull { state.photos.getOrNull(it) }.filter {
            it.kind == MediaKind.VIDEO && VideoCommandPolicy.canPause(state.videoStates[it.cacheKey] ?: VideoVrState.NORMAL)
        }.mapTo(linkedSetOf()) { it.cacheKey }
        pauseVideosByKey(keys, "USER")
        _uiState.update { it.copy(message = "已暂停 ${keys.size} 项视频，保留参数和已完成帧 / Paused ${keys.size} videos and kept their parameters and completed frames.") }
    }

    private fun enqueueVideo(item: GalleryItem, force: Boolean = false, authorize: Boolean = true) = synchronized(videoControlLock) {
        if (!queueRestorationComplete && serviceResumeKeys.isEmpty()) return@synchronized
        if (item.kind != MediaKind.VIDEO) return@synchronized
        val existing = _uiState.value.videoStates[item.cacheKey] ?: VideoVrState.NORMAL
        if (!force && (existing == VideoVrState.QUEUED || existing == VideoVrState.GENERATING)) return@synchronized
        val params = if (force) _uiState.value.settings.toVideoParams() else synchronized(pausedVideoParams) { pausedVideoParams[item.cacheKey] }
            ?: videoQueueTags.load().firstOrNull { it.videoKey == item.cacheKey }?.params ?: _uiState.value.settings.toVideoParams()
        if (!force && videoCache.findEntry(item, params.cacheVersion()) != null) {
            videoQueueTags.remove(item.cacheKey); backgroundRuns.revoke(setOf(item.cacheKey))
            _uiState.update { it.copy(videoStates = it.videoStates + (item.cacheKey to VideoVrState.READY)) }
            return@synchronized
        }
        if (authorize) {
            try { backgroundRuns.authorize(setOf(item.cacheKey)) }
            catch (error: Exception) { _uiState.update { it.copy(message = "无法保存任务状态，未开始生成 / Cannot persist work: ${error.message}") }; return@synchronized }
        }
        synchronized(pausedVideos) { pausedVideos.remove(item.cacheKey) }
        synchronized(pausedVideoParams) { pausedVideoParams.remove(item.cacheKey) }
        synchronized(videoPending) {
            videoPending.removeAll { it.item.cacheKey == item.cacheKey }
            videoPending.add(VideoQueuedJob(item, params, videoSequence++, nextVideoRunToken(item.cacheKey)))
        }
        upsertVideoJob(item, VideoVrState.QUEUED, 0f, modelId = params.vr.depthModel, cacheVersion = params.cacheVersion(), modelThreads = params.modelThreads, depthWorkers = params.depthWorkers, useGpu = params.useGpu)
        videoQueueTags.upsert(item.cacheKey, VideoVrState.QUEUED, params)
        _uiState.update { it.copy(videoStates = it.videoStates + (item.cacheKey to VideoVrState.QUEUED), message = "视频已加入 VR 队列：${item.displayName}") }
        startVideoWorkers()
    }

    private fun pauseVideo(item: GalleryItem) = pauseVideosByKey(setOf(item.cacheKey), "USER")

    private fun pauseVideosByKey(keys: Set<String>, reason: String) = synchronized(videoControlLock) {
        if (keys.isEmpty()) return@synchronized
        backgroundRuns.revoke(keys)
        val state = _uiState.value
        val saved = videoQueueTags.load().associateBy { it.videoKey }
        keys.forEach { key ->
            val item = state.photos.firstOrNull { it.cacheKey == key } ?: queuedVideoItemFromKey(key)
            val queued = synchronized(videoPending) { videoPending.firstOrNull { it.item.cacheKey == key }.also { videoPending.removeAll { job -> job.item.cacheKey == key } } }
            val params = synchronized(pausedVideoParams) {
                (queued?.params ?: activeVideoParams[key] ?: pausedVideoParams[key] ?: saved[key]?.params)?.also { pausedVideoParams[key] = it }
            }
            synchronized(pausedVideos) { pausedVideos.add(key) }
            nextVideoRunToken(key)
            videoQueueTags.upsert(key, VideoVrState.PAUSED, params, reason)
            _uiState.update { it.copy(videoStates = it.videoStates + (key to VideoVrState.PAUSED)) }
            val text = when (reason) { "SYSTEM_LIMIT" -> "系统后台时限暂停"; "SERVICE_UNAVAILABLE" -> "后台服务不可用，已暂停"; else -> "用户暂停" }
            upsertVideoJob(item, VideoVrState.PAUSED, state.videoJobs.firstOrNull { it.item.cacheKey == key }?.progress ?: 0f, error = text)
            val job = state.videoJobs.firstOrNull { it.item.cacheKey == key }
            videoNotifier.show(item, job?.currentFrame ?: 0, job?.totalFrames ?: 0, indeterminate = false, status = "已暂停")
        }
    }

    private fun startVideoWorkers(): Unit = synchronized(videoControlLock) {
        videoWorkers.removeAll { !it.isActive }
        if (videoWorkers.isNotEmpty() || synchronized(videoPending) { videoPending.isEmpty() }) return@synchronized
        try { if (videoQueueLease == null) videoQueueLease = videoKeepAlive.acquire() }
        catch (error: Exception) { onForegroundUnavailable(error.message.orEmpty()); return@synchronized }
        videoWorkers += AppWorkScopes.video.launch {
            try {
                videoQueueTags.flush()
                while (true) {
                    val next = synchronized(videoPending) { videoPending.poll() } ?: break
                    processVideoJob(next)
                    delay(100)
                }
            } catch (error: Exception) {
                onForegroundUnavailable(error.message.orEmpty())
            } finally {
                val self = kotlin.coroutines.coroutineContext[Job]
                synchronized(videoControlLock) {
                    videoWorkers.remove(self)
                    if (synchronized(videoPending) { videoPending.isNotEmpty() }) startVideoWorkers()
                    else { videoQueueLease?.close(); videoQueueLease = null }
                }
            }
        }
    }

    private fun mutateVideoRun(next: VideoQueuedJob, action: () -> Unit): Boolean = synchronized(videoControlLock) {
        if (!isCurrentVideoRun(next.item.cacheKey, next.runToken)) return@synchronized false
        action(); true
    }

    private fun processVideoJob(next: VideoQueuedJob) {
        if (!isCurrentVideoRun(next.item.cacheKey, next.runToken)) {
            addLog("video stale queued job ignored ${next.item.displayName}")
            return
        }
        val vrParams = next.params.toVrParams()
        val effectiveDepthWorkers = if (vrParams.useGpu) 1 else next.params.depthWorkers.coerceIn(1, 2)
        val videoCacheVersion = next.params.cacheVersion()
        var lastRuntimeInfo = "pending"
        if (!mutateVideoRun(next) {
            synchronized(pausedVideoParams) { activeVideoParams[next.item.cacheKey] = next.params }
            _uiState.update { it.copy(videoStates = it.videoStates + (next.item.cacheKey to VideoVrState.GENERATING)) }
            videoQueueTags.upsert(next.item.cacheKey, VideoVrState.GENERATING, next.params)
            upsertVideoJob(
                next.item,
                VideoVrState.GENERATING,
                0.01f,
                currentFrameMs = 0L,
                avgFrameMs = 0L,
                frameLatencyMs = 0L,
                queueWaitMs = 0L,
                cacheHitReadMs = 0L,
                modelId = vrParams.depthModel,
                cacheVersion = videoCacheVersion,
                modelThreads = vrParams.modelThreads,
                depthWorkers = effectiveDepthWorkers,
                useGpu = vrParams.useGpu,
                runtimeInfo = lastRuntimeInfo, runToken = next.runToken, resetFrameDiagnostics = true)
            videoNotifier.show(next.item, 0, 0, indeterminate = true, status = "准备生成")
        }) return
        val started = SystemClock.uptimeMillis()
        var totalThroughputMs = 0L
        var measuredGeneratedFrames = 0
        val result = runCatching {
                videoGenerator.generate(
                    next.item,
                    next.params,
                    checkActive = {
                        if (!isCurrentVideoRun(next.item.cacheKey, next.runToken)) throw VideoStaleException()
                        if (synchronized(pausedVideos) { pausedVideos.contains(next.item.cacheKey) }) throw VideoPausedException()
                    },
                    onModelProgress = { progress ->
                        mutateVideoRun(next) {
                            _uiState.update {
                                it.copy(
                                    modelProgress = progress,
                                    modelStatus = if (progress < 1f) "正在下载模型 / Downloading model ${(progress * 100f).roundToInt()}%" else "模型已就绪 / Model ready",
                                )
                            }
                        }
                    },
                    onRuntimeInfo = { runtime ->
                        mutateVideoRun(next) {
                            if (runtime != lastRuntimeInfo && runtime.startsWith("tflite")) {
                                addLog("video runtime ${next.item.displayName}: $runtime")
                            }
                            lastRuntimeInfo = runtime
                            upsertVideoJob(next.item, VideoVrState.GENERATING, _uiState.value.videoJobs.firstOrNull { it.item.cacheKey == next.item.cacheKey }?.progress ?: 0f, modelId = vrParams.depthModel, cacheVersion = videoCacheVersion, modelThreads = vrParams.modelThreads, depthWorkers = effectiveDepthWorkers, useGpu = vrParams.useGpu, runtimeInfo = runtime, runToken = next.runToken)
                        }
                    },
                ) { progress, frame, total, fps, frameMetrics, frameTimings, pipelineStats ->
                    if (!isCurrentVideoRun(next.item.cacheKey, next.runToken)) {
                        throw VideoStaleException()
                    }
                    if (synchronized(pausedVideos) { pausedVideos.contains(next.item.cacheKey) }) {
                        throw VideoPausedException()
                    }
                    if (!frameMetrics.cacheHit && frameMetrics.throughputMs > 0L) {
                        totalThroughputMs += frameMetrics.throughputMs
                        measuredGeneratedFrames++
                    }
                    val avgFrameMs = if (measuredGeneratedFrames > 0) totalThroughputMs / measuredGeneratedFrames else 0L
                    mutateVideoRun(next) {
                        upsertVideoJob(
                            next.item,
                            VideoVrState.GENERATING,
                            progress,
                            frame,
                            total,
                            fps,
                            currentFrameMs = frameMetrics.throughputMs,
                            avgFrameMs = avgFrameMs,
                            frameLatencyMs = frameMetrics.latencyMs,
                            queueWaitMs = frameMetrics.queueWaitMs,
                            cacheHitReadMs = frameMetrics.cacheHitReadMs,
                            modelId = vrParams.depthModel,
                            cacheVersion = videoCacheVersion,
                            modelThreads = vrParams.modelThreads,
                            depthWorkers = effectiveDepthWorkers,
                            useGpu = vrParams.useGpu,
                            runtimeInfo = lastRuntimeInfo,
                            frameTimings = frameTimings,
                            pipelineStats = pipelineStats, runToken = next.runToken)
                        videoNotifier.show(next.item, frame, total, indeterminate = false, status = "生成中")
                    }
                }
        }
        if (!isCurrentVideoRun(next.item.cacheKey, next.runToken)) {
            addLog("video stale result ignored ${next.item.displayName}")
            return
        }
        var refresh = false
        mutateVideoRun(next) {
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
                refresh = true
                upsertVideoJob(next.item, VideoVrState.READY, 1f, currentFrameMs = _uiState.value.videoJobs.firstOrNull { it.item.cacheKey == next.item.cacheKey }?.currentFrameMs ?: 0L, avgFrameMs = _uiState.value.videoJobs.firstOrNull { it.item.cacheKey == next.item.cacheKey }?.avgFrameMs ?: 0L, modelId = vrParams.depthModel, cacheVersion = videoCacheVersion, modelThreads = vrParams.modelThreads, depthWorkers = effectiveDepthWorkers, useGpu = vrParams.useGpu, runtimeInfo = lastRuntimeInfo, finishedAt = System.currentTimeMillis(), runToken = next.runToken)
                videoQueueTags.remove(next.item.cacheKey)
                backgroundRuns.revoke(setOf(next.item.cacheKey))
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
                    upsertVideoJob(next.item, VideoVrState.PAUSED, _uiState.value.videoJobs.firstOrNull { it.item.cacheKey == next.item.cacheKey }?.progress ?: 0f, modelId = vrParams.depthModel, cacheVersion = videoCacheVersion, modelThreads = vrParams.modelThreads, depthWorkers = effectiveDepthWorkers, useGpu = vrParams.useGpu, runtimeInfo = lastRuntimeInfo, runToken = next.runToken)
                    addLog("video paused ${next.item.displayName}")
                } else {
                    val failure = if (error is OutOfMemoryError) {
                        "视频生成内存不足。已完成片段保留，释放内存后可重试 / Out of memory. Completed segments were kept. Free memory and retry."
                    } else error.message
                    _uiState.update { it.copy(videoStates = it.videoStates + (next.item.cacheKey to VideoVrState.FAILED), modelProgress = null) }
                    backgroundRuns.revoke(setOf(next.item.cacheKey))
                    videoQueueTags.upsert(next.item.cacheKey, VideoVrState.FAILED, next.params)
                    synchronized(pausedVideoParams) { activeVideoParams.remove(next.item.cacheKey) }
                    upsertVideoJob(next.item, VideoVrState.FAILED, 1f, modelId = vrParams.depthModel, cacheVersion = videoCacheVersion, modelThreads = vrParams.modelThreads, depthWorkers = effectiveDepthWorkers, useGpu = vrParams.useGpu, runtimeInfo = lastRuntimeInfo, finishedAt = System.currentTimeMillis(), error = failure, runToken = next.runToken)
                    videoNotifier.failed(next.item, failure ?: "生成失败")
                    addLog("video failed ${next.item.displayName}: $failure")
                }
            }
        }
        if (refresh) refreshGeneratedLibrary()
    }

    private suspend fun workerLoop() {
        var protection: Closeable? = null
        try {
            while (true) {
                rebalanceQueueForActiveSource()
                val next = pollAllowedPendingJob()
                if (next == null) {
                    if (expandAutoPrefetchIfNeeded()) {
                        delay(50)
                        continue
                    }
                    break
                }
                if (protection == null) {
                    try { protection = videoKeepAlive.acquire() }
                    catch (error: RuntimeException) { onForegroundUnavailable(error.message.orEmpty()); break }
                }
                processJob(next)
                delay(50)
            }
        } finally { protection?.close() }
    }

    private fun releaseCurrentPrefetch(key: String) = synchronized(imageRuns) {
        val state = _uiState.value
        val selected = state.selectedIndex ?: return
        if (canPrefetch(state) && state.photos.getOrNull(selected)?.cacheKey == key &&
            !currentImageFirst.permits(key, cached = false)) {
            currentImageFirst.started(key)
            enqueueWindow(selected, includeCurrent = false)
        }
    }

    private suspend fun processJob(next: QueuedJob) {
        val coroutineJob = kotlin.coroutines.coroutineContext[Job]
        var ownsKey = false
        try {
            while (!activeImageKeys.add(next.photo.cacheKey)) {
                if (!imageRuns.isCurrent(next.photo.cacheKey, next.runToken)) return
                delay(25)
            }
            ownsKey = true
            val params = next.params ?: _uiState.value.settings.toParams()
            var alreadyReady = false
            var sourceChanged = false
            if (!imageRuns.ifCurrent(next.photo.cacheKey, next.runToken) {
                if (!jobAllowedInCurrentContext(next, _uiState.value)) {
                    synchronized(paused) {
                        paused[next.photo.cacheKey] = next
                        autoPausedKeys += next.photo.cacheKey
                        sourcePausedKeys += next.photo.cacheKey
                    }
                    queueTags.upsert(next, VrState.PAUSED)
                    markState(next.photo.cacheKey, VrState.PAUSED)
                    upsertJob(next.photo, next.priority, VrState.PAUSED, 0f, source = next.source, albumId = next.albumId)
                    sourceChanged = true
                    return@ifCurrent
                }
                runningImageJobs[next.photo.cacheKey] = next
                val cached = cache.findEntry(next.photo, params.cacheVersion())
                if (cached != null && next.writeIntent?.forceRender != true) {
                    queueTags.remove(next.photo.cacheKey)
                    synchronized(paused) { autoPausedKeys.remove(next.photo.cacheKey); sourcePausedKeys.remove(next.photo.cacheKey) }
                    markReady(next.photo.cacheKey, cached)
                    upsertJob(next.photo, next.priority, VrState.READY, 1f, source = next.source, albumId = next.albumId)
                    alreadyReady = true
                    if (next.writeIntent == null) releaseCurrentPrefetch(next.photo.cacheKey)
                } else {
                    queueTags.upsert(next, VrState.GENERATING)
                    markState(next.photo.cacheKey, VrState.GENERATING)
                    upsertJob(next.photo, next.priority, VrState.GENERATING, 0.1f, source = next.source, albumId = next.albumId)
                }
            } || alreadyReady || sourceChanged) return
            val started = System.currentTimeMillis()
            val model = qnnModelConfig(params.depthModel)
            val modelReadyProgress = if (model != null && model.aspect != QnnDepthAspect.SQUARE) 0.55f else 0.12f
            val result = runCatching {
                generator.generate(
                    next.photo,
                    params,
                    priority = next.priority,
                    shouldContinue = { coroutineJob?.isActive != false && imageRuns.isCurrent(next.photo.cacheKey, next.runToken) },
                    commit = { entry ->
                        var published: VrCacheEntry? = null
                        imageRuns.ifCurrent(next.photo.cacheKey, next.runToken) { published = cache.publish(next.photo, entry, next.writeIntent?.replacements.orEmpty()) }
                        published ?: throw ImageJobObsoleteException()
                    },
                    onModelProgress = { progress ->
                        imageRuns.ifCurrent(next.photo.cacheKey, next.runToken) {
                            _uiState.update {
                                it.copy(
                                    modelProgress = progress,
                                    modelStatus = if (progress < 1f) {
                                        "正在下载模型 / Downloading model ${(progress * 100f).roundToInt()}%"
                                    } else {
                                        "模型文件已就绪 / Model file ready"
                                    },
                                )
                            }
                        }
                    },
                ) { progress ->
                    imageRuns.ifCurrent(next.photo.cacheKey, next.runToken) {
                        if (progress == modelReadyProgress &&
                            model?.aspect != QnnDepthAspect.WIDE_TALL && model?.aspect != QnnDepthAspect.PORTRAIT_PAIR) {
                            _uiState.update {
                                if (it.settings.toParams().depthSessionKey() == params.depthSessionKey())
                                    it.copy(modelWarmupStatus = "图片模型已在内存就绪 / Image model ready in memory") else it
                            }
                        }
                        upsertJob(next.photo, next.priority, VrState.GENERATING, progress, source = next.source, albumId = next.albumId)
                        if (progress >= 0.30f) if (next.writeIntent == null) releaseCurrentPrefetch(next.photo.cacheKey)
                    }
                }
            }
            result.onSuccess { entry ->
                imageRuns.ifCurrent(next.photo.cacheKey, next.runToken) {
                    queueTags.remove(next.photo.cacheKey)
                    synchronized(paused) { autoPausedKeys.remove(next.photo.cacheKey); sourcePausedKeys.remove(next.photo.cacheKey) }
                    markReady(next.photo.cacheKey, entry)
                    if (next.writeIntent != null) refreshGeneratedLibrary()
                    if (next.writeIntent == null) releaseCurrentPrefetch(next.photo.cacheKey)
                    upsertJob(next.photo, next.priority, VrState.READY, 1f, finishedAt = System.currentTimeMillis(), source = next.source, albumId = next.albumId)
                    addLog("ready ${next.photo.displayName} ${entry.width}x${entry.height}")
                    val currentState = _uiState.value
                    val selected = currentState.selectedIndex ?: currentState.allGalleryAnchorIndex()
                    val doneIndex = currentState.photos.indexOfFirst { it.cacheKey == next.photo.cacheKey }
                    val generationInfo = LastGenerationInfo(relativeIndexInViewerScope(currentState, doneIndex, selected), System.currentTimeMillis() - started)
                    _uiState.update {
                        it.copy(
                            modelProgress = null,
                            modelStatus = modelStatusText(it.settings),
                            lastGeneration = generationInfo,
                            recentGenerations = (listOf(generationInfo) + it.recentGenerations).take(3),
                        )
                    }
                }
            }.onFailure { error ->
                if (error is java.util.concurrent.CancellationException) {
                    if (coroutineJob?.isActive == false) throw error
                    addLog("image cancelled ${next.photo.displayName}; stale output discarded")
                    return@onFailure
                }
                imageRuns.ifCurrent(next.photo.cacheKey, next.runToken) {
                    synchronized(paused) { autoPausedKeys.remove(next.photo.cacheKey); sourcePausedKeys.remove(next.photo.cacheKey) }
                    queueTags.upsert(next, VrState.FAILED)
                    markState(next.photo.cacheKey, VrState.FAILED)
                    upsertJob(next.photo, next.priority, VrState.FAILED, 1f, finishedAt = System.currentTimeMillis(), error = error.message, source = next.source, albumId = next.albumId)
                    addLog("failed ${next.photo.displayName}: ${error.message}")
                }
            }
        } finally {
            synchronized(imageRuns) {
                claimedImageJobs.remove(next)
                if (ownsKey) {
                    runningImageJobs.remove(next.photo.cacheKey, next)
                    activeImageKeys.remove(next.photo.cacheKey)
                }
            }
        }
    }

    private fun expandAutoPrefetchIfNeeded(): Boolean = synchronized(imageRuns) {
        var attempts = 0
        while (attempts++ < 3) {
            val state = _uiState.value
            if (!canPrefetch(state) || !state.settings.autoPrefetch) return false
            val selected = state.selectedIndex ?: return false
            val before = state.prefetchSlotState
            if (before.tier >= 8 && before.prevDone >= 4 && before.nextDone >= 4) return false
            enqueueWindow(selected, includeCurrent = false)
            if (synchronized(pending) { pending.any { jobAllowedInCurrentContext(it, _uiState.value) } }) return true
            if (_uiState.value.prefetchSlotState == before) return false
        }
        return false
    }

    private fun markReady(key: String, entry: VrCacheEntry) {
        cache.register(entry)
        val summaries = cache.summaries()
        _uiState.update {
            val photo = it.photos.firstOrNull { photo -> photo.cacheKey == key }
            val managed = if (photo == null) it.managedCacheItems else (
                it.managedCacheItems.filterNot { item -> item.entry.photoKey == key && item.entry.version == entry.version } + ManagedCacheItem(photo, entry)
            ).sortedByDescending { item -> item.entry.createdAt }
            it.copy(
                states = it.states + (key to VrState.READY),
                entries = it.entries + (key to entry),
                managedCacheItems = managed,
                cacheVersions = summaries,
            )
        }
    }

    private fun markState(key: String, state: VrState) {
        _uiState.update { it.copy(states = it.states + (key to state)) }
    }

    private fun refreshGeneratedLibrary(message: String? = null): Unit = synchronized(imageRuns) {
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
                states = imageEntries.keys.associateWith { VrState.READY } + current.states.filterValues { it != VrState.NORMAL },
                videoEntries = current.videoEntries + videoEntries,
                videoStates = videoEntries.keys.associateWith { VideoVrState.READY } + current.videoStates.filterValues { it != VideoVrState.NORMAL },
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
        avgFrameMs: Long? = null,
        currentFrameMs: Long? = null,
        frameLatencyMs: Long? = null,
        queueWaitMs: Long? = null,
        cacheHitReadMs: Long? = null,
        modelId: String = "",
        cacheVersion: String = "",
        modelThreads: Int = 0,
        depthWorkers: Int = 0,
        useGpu: Boolean = false,
        runtimeInfo: String = "",
        finishedAt: Long? = null,
        error: String? = null,
        frameTimings: VideoFrameTimings? = null,
        pipelineStats: VideoPipelineStats = VideoPipelineStats(),
        runToken: Long? = null,
        resetFrameDiagnostics: Boolean = false,
    ): Unit = synchronized(videoControlLock) {
        if (runToken != null && !isCurrentVideoRun(item.cacheKey, runToken)) return@synchronized
        _uiState.update { current ->
            val previous = current.videoJobs.firstOrNull { it.item.cacheKey == item.cacheKey }
            val existing = current.videoJobs.filterNot { it.item.cacheKey == item.cacheKey }
            val previousTimings = previous?.takeIf {
                !resetFrameDiagnostics && (cacheVersion.isBlank() || it.cacheVersion == cacheVersion)
            }?.frameTimings
            val job = VideoVrJob(
                item = item,
                state = state,
                progress = progress,
                currentFrame = currentFrame.takeIf { it > 0 } ?: previous?.currentFrame ?: 0,
                totalFrames = totalFrames.takeIf { it > 0 } ?: previous?.totalFrames ?: 0,
                fps = fps.takeIf { it > 0 } ?: previous?.fps ?: 30,
                currentFrameMs = currentFrameMs ?: previous?.currentFrameMs ?: 0L,
                avgFrameMs = avgFrameMs ?: previous?.avgFrameMs ?: 0L,
                frameLatencyMs = frameLatencyMs ?: previous?.frameLatencyMs ?: 0L,
                queueWaitMs = queueWaitMs ?: previous?.queueWaitMs ?: 0L,
                cacheHitReadMs = cacheHitReadMs ?: previous?.cacheHitReadMs ?: 0L,
                modelId = modelId.ifBlank { previous?.modelId.orEmpty() },
                cacheVersion = cacheVersion.ifBlank { previous?.cacheVersion.orEmpty() },
                modelThreads = modelThreads.takeIf { it > 0 } ?: previous?.modelThreads ?: 0,
                depthWorkers = depthWorkers.takeIf { it > 0 } ?: previous?.depthWorkers ?: 0,
                useGpu = if (modelId.isNotBlank() || cacheVersion.isNotBlank()) useGpu else previous?.useGpu == true,
                runtimeInfo = runtimeInfo.ifBlank { previous?.runtimeInfo.orEmpty() },
                startedAt = previous?.startedAt ?: System.currentTimeMillis(),
                finishedAt = finishedAt,
                error = error,
                frameTimings = frameTimings?.withPreviousFrame(previousTimings) ?: previousTimings ?: VideoFrameTimings(),
                pipelineStats = when {
                    resetFrameDiagnostics || pipelineStats != VideoPipelineStats() -> pipelineStats
                    else -> previous?.pipelineStats ?: VideoPipelineStats()
                },
            )
            current.copy(
                videoStates = current.videoStates + (item.cacheKey to state),
                videoJobs = (listOf(job) + existing).take(100),
            )
        }
    }

    private fun nextVideoRunToken(videoKey: String): Long = synchronized(videoControlLock) {
        val next = (videoRunTokens[videoKey] ?: 0L) + 1L
        videoRunTokens[videoKey] = next
        next
    }

    private fun isCurrentVideoRun(videoKey: String, token: Long): Boolean = synchronized(videoControlLock) {
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

internal data class QueuedJob(val photo: PhotoItem, val priority: Int, val sequence: Long, val source: QueueSource, val albumId: String?, val runToken: Long = 0L, val params: VrGenerationParams? = null, val writeIntent: ImageWriteIntent? = null)

internal class ImageJobObsoleteException : java.util.concurrent.CancellationException("Image generation cancelled by deletion or replacement")

private data class VideoQueuedJob(val item: GalleryItem, val params: VideoGenerationParams, val sequence: Long, val runToken: Long)

private class VideoPausedException : RuntimeException("Video generation paused")
private class VideoStaleException : RuntimeException("Video generation superseded")
