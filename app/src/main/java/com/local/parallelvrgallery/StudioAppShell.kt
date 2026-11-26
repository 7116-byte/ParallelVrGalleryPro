package com.local.parallelvrgallerypro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private sealed class AppScreen {
    data object Gallery : AppScreen()
    data object Settings : AppScreen()
    data object Manage : AppScreen()
    data object Viewer : AppScreen()
    data object Debug : AppScreen()
    data object Comparison : AppScreen()
}

private enum class GalleryTopMenu {
    NONE,
    VIEW_OPTIONS,
    MORE,
}

private enum class TuneSubmenu {
    ROOT,
    VIEW,
    FILTER,
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GalleryApp(viewModel: GalleryViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val viewports = rememberSaveable(saver = GalleryViewports.Saver) { GalleryViewports() }
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

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestState by rememberUpdatedState(state)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val images = hasImagePermission(context)
                val videos = hasVideoPermission(context)
                val notifications = hasNotificationPermission(context)
                val current = latestState
                if (images != current.hasPermission || videos != current.hasVideoPermission || notifications != current.hasNotificationPermission) viewModel.onPermissionChanged(images, videos, notifications)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    if (!state.hasPermission) {
        PermissionScreen(state.settings.language) { launcher.launch(mediaPermissions()) }
        return
    }

    state.blockingMessage?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            containerColor = AppleTheme.colors.elevatedSurface,
            shape = RoundedCornerShape(24.dp),
            title = { Text(message, fontWeight = FontWeight.SemiBold) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = AppleTheme.colors.systemBlue)
                    Spacer(Modifier.width(14.dp))
                    Text(state.settings.language.t("请稍等", "Please wait"), color = AppleTheme.colors.labelSecondary)
                }
            },
        )
    }

    val screen = when {
        state.comparison != null -> AppScreen.Comparison
        state.manageOpen -> AppScreen.Manage
        state.settingsOpen -> AppScreen.Settings
        state.debugIndex != null -> AppScreen.Debug
        state.selectedIndex != null -> AppScreen.Viewer
        else -> AppScreen.Gallery
    }
    BackHandler(enabled = screen is AppScreen.Comparison) { viewModel.closeComparison() }
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

    val pageState = rememberSaveableStateHolder()
    if (state.additionalImageSources.isNotEmpty() && !state.settingsOpen) {
        AdditionalImagesDialog(
            state,
            onEdit = viewModel::openSettings,
            onConfirm = viewModel::confirmAdditionalImages,
            onCancel = viewModel::cancelAdditionalImages,
        )
    }
    Box(Modifier.fillMaxSize()) {
        when (screen) {
            AppScreen.Comparison -> state.comparison?.let { comparison ->
                StudioTheme(dark = true) { ModeComparisonScreen(comparison, viewModel::closeComparison) }
            }
            AppScreen.Manage -> ManageScreen(
                state = state,
                viewports = viewports,
                onBack = viewModel::closeManage,
                selectedTab = state.generatedTab,
                onTabChange = viewModel::setGeneratedTab,
                onToggleVersion = viewModel::toggleGeneratedVersion,
                onOpenVersion = viewModel::openGeneratedVersion,
                onCloseVersion = viewModel::closeGeneratedVersion,
                onSetGeneratedColumns = { viewModel.setPageColumns("generated", it) },
                onDeleteVersion = viewModel::deleteGeneratedAlbums,
                onOpenGenerated = viewModel::openGeneratedPhoto,
                onOpenGeneratedVideo = viewModel::openGeneratedVideo,
                onSaveVideo = { viewModel.saveGeneratedVideo(context, it) },
                onDeleteVideo = viewModel::deleteGeneratedVideo,
                onSaveVideos = { viewModel.saveGeneratedVideos(context, it) },
                onDeleteVideos = viewModel::deleteGeneratedVideos,
                onRegenerateVideos = viewModel::regenerateVideos,
                onStartVideos = viewModel::startVideos,
                onPauseVideos = viewModel::pauseVideos,
                onSaveImages = { viewModel.saveManagedGeneratedCopies(context, it) },
                onDeleteImages = viewModel::deleteGeneratedImageEntries,
                onRegenerateImages = viewModel::regenerateImages,
                onGenerateImages = viewModel::requestAdditionalImages,
            )
            AppScreen.Settings -> SettingsScreen(
                fromDebug = state.debugIndex != null,
                initialSection = when {
                    state.additionalImageSources.isNotEmpty() -> "depth"
                    state.debugIndex?.let(state.photos::getOrNull)?.kind == MediaKind.VIDEO -> "video"
                    state.debugIndex != null -> "depth"
                    else -> "general"
                },
                settings = state.settings,
                modelWarmupStatus = state.modelWarmupStatus,
                updateStatus = state.updateStatus,
                updateUrl = state.updateUrl,
                updateAvailable = state.updateAvailable,
                updateDownloading = state.updateDownloading,
                updateDownloadProgress = state.updateDownloadProgress,
                updateApkPath = state.updateApkPath,
                onBack = viewModel::closeSettings,
                onChange = viewModel::updateSettings,
                onSaveDefault = viewModel::saveModeDefault,
                onResetDefault = { viewModel.resetModeDefault() },
                onFactoryDefault = { viewModel.resetModeDefault(factory = true) },
                onCheckUpdate = viewModel::checkForUpdates,
                onDownloadUpdate = viewModel::downloadUpdateApk,
                onInstallUpdate = { viewModel.installDownloadedUpdate(context) },
            )
            AppScreen.Debug -> pageState.SaveableStateProvider("debug-${state.debugIndex}") {
                DebugScreen(
                    state = state,
                    index = state.debugIndex ?: state.allGalleryAnchorIndex(),
                    onBack = viewModel::closeDebug,
                    onSettings = viewModel::openSettings,
                    onCompare = { viewModel.startComparison(state.debugIndex ?: state.allGalleryAnchorIndex()) },
                    onGenerate = { viewModel.requestAdditionalImages(listOf(state.debugIndex ?: state.allGalleryAnchorIndex())) },
                    onRegenerate = { viewModel.regenerateViewedImage(state.debugIndex ?: state.allGalleryAnchorIndex()) },
                    onShare = { viewModel.exportDebug(context, state.debugIndex ?: state.allGalleryAnchorIndex()) },
                )
            }
            AppScreen.Viewer -> StudioTheme(dark = true) {
                ViewerScreen(
                    state = state,
                    startIndex = state.selectedIndex ?: state.allGalleryAnchorIndex(),
                    onClose = viewModel::closeViewer,
                    onIndexChanged = viewModel::onPagerIndexChanged,
                    onVr = viewModel::requestVr,
                    onRetry = viewModel::retry,
                    onOpenDebug = viewModel::openDebug,
                )
            }
            AppScreen.Gallery -> GalleryScreen(
                state = state,
                viewports = viewports,
                onRefresh = viewModel::loadPhotos,
                onOpenScoped = viewModel::openScopedPhoto,
                onSettings = viewModel::openSettings,
                onSetTab = viewModel::setHomeTab,
                onSetGeneratedTab = viewModel::setGeneratedTab,
                onSetPageColumns = viewModel::setPageColumns,
                onSetGallerySort = viewModel::setGallerySortMode,
                onSetGalleryViewMode = viewModel::setGalleryViewMode,
                onSetGalleryFilter = viewModel::setGalleryFilterMode,
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
                onDeleteVersion = viewModel::deleteGeneratedAlbums,
                onOpenGenerated = viewModel::openGeneratedPhoto,
                onOpenGeneratedVideo = viewModel::openGeneratedVideo,
                onSaveVideo = { viewModel.saveGeneratedVideo(context, it) },
                onDeleteVideo = viewModel::deleteGeneratedVideo,
                onSaveVideos = { viewModel.saveGeneratedVideos(context, it) },
                onDeleteVideos = viewModel::deleteGeneratedVideos,
                onRegenerateVideos = viewModel::regenerateVideos,
                onStartVideos = viewModel::startVideos,
                onPauseVideos = viewModel::pauseVideos,
                onSaveImages = { viewModel.saveManagedGeneratedCopies(context, it) },
                onDeleteImages = viewModel::deleteGeneratedImageEntries,
                onRegenerateImages = viewModel::regenerateImages,
                onGenerateImages = viewModel::requestAdditionalImages,
            )
        }
    }
}

@Composable
private fun PermissionScreen(lang: AppLanguage, onGrant: () -> Unit) {
    val context = LocalContext.current
    StudioEmptyState(
        title = lang.t("让素材进入创作空间", "Bring media into your studio"),
        detail = lang.t("允许访问图片和视频后，可在设备本地生成平行眼 VR。原图不会因浏览或生成缓存而被修改。通知仅用于后台任务进度。", "Allow photos and videos to generate parallel-eye VR on this device. Browsing and generating cache do not change originals. Notifications are for background progress."),
        modifier = Modifier.background(AppleTheme.colors.groupedBackground).safeDrawingPadding(),
        icon = Icons.Outlined.PhotoLibrary,
    ) {
        AppleCapsuleButton(text = lang.t("选择媒体访问权限", "Choose media access"), onClick = onGrant)
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))) }) {
            Text(lang.t("前往系统权限设置", "Open system permissions"), color = AppleTheme.colors.systemBlue)
        }
    }
}

internal data class GeneratedSelectionActionsState(
    val count: Int,
    val onClear: () -> Unit,
    val onSelectAll: () -> Unit,
    val onSave: (() -> Unit)?,
    val onRegenerate: (() -> Unit)?,
    val onDelete: () -> Unit,
    val onGenerate: (() -> Unit)? = null,
    val imageReplacement: Boolean = false,
    val videoActions: VideoSelectionActions? = null,
)

internal data class VideoSelectionActions(
    val onStartSelected: () -> Unit,
    val onPauseSelected: () -> Unit,
    val onStartAll: () -> Unit,
    val onPauseAll: () -> Unit,
    val selectedStartCount: Int,
    val selectedPauseCount: Int,
    val allStartCount: Int,
    val allPauseCount: Int,
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun GalleryScreen(
    state: UiState,
    viewports: GalleryViewports,
    onRefresh: () -> Unit,
    onOpenScoped: (Int, List<String>) -> Unit,
    onSettings: () -> Unit,
    onSetTab: (String) -> Unit,
    onSetGeneratedTab: (String) -> Unit,
    onSetPageColumns: (String, Int) -> Unit,
    onSetGallerySort: (GallerySortMode) -> Unit,
    onSetGalleryViewMode: (GalleryViewMode) -> Unit,
    onSetGalleryFilter: (GalleryFilterMode) -> Unit,
    onToggleGeneratedVersion: (String) -> Unit,
    onOpenGeneratedVersion: (String) -> Unit,
    onCloseGeneratedVersion: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onCloseAlbum: () -> Unit,
    onLoadMoreAll: () -> Unit,
    onLoadMoreAlbum: () -> Unit,
    onSaveGenerated: (List<Int>) -> Unit,
    onReplaceOriginal: (List<Int>) -> Unit,
    onDeleteVersion: (List<String>) -> Unit,
    onOpenGenerated: (Int, VrCacheEntry) -> Unit,
    onOpenGeneratedVideo: (Int) -> Unit,
    onSaveVideo: (Int) -> Unit,
    onDeleteVideo: (Int) -> Unit,
    onSaveVideos: (List<Int>) -> Unit,
    onDeleteVideos: (List<Int>) -> Unit,
    onRegenerateVideos: (List<Int>) -> Unit,
    onStartVideos: (List<Int>) -> Unit,
    onPauseVideos: (List<Int>) -> Unit,
    onSaveImages: (List<ManagedCacheItem>) -> Unit,
    onDeleteImages: (List<ManagedCacheItem>) -> Unit,
    onRegenerateImages: (List<ManagedCacheItem>) -> Unit,
    onGenerateImages: (List<Int>) -> Unit,
) {
    val apple = AppleTheme.colors
    val windowMetrics = rememberStudioWindowMetrics()
    val backdrop = rememberStudioBackdrop()
    val lang = state.settings.language
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var openMenu by remember { mutableStateOf(GalleryTopMenu.NONE) }
    var tuneSubmenu by remember { mutableStateOf(TuneSubmenu.ROOT) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var showTasks by rememberSaveable { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }
    var dismissedMessage by remember { mutableStateOf<String?>(null) }
    var dockHeightPx by remember { mutableStateOf(0) }
    val bottomContentPadding = with(density) { dockHeightPx.toDp() } + navBarBottom + 26.dp

    LaunchedEffect(openMenu) {
        if (openMenu != GalleryTopMenu.VIEW_OPTIONS) tuneSubmenu = TuneSubmenu.ROOT
    }

    var contentScrolled by remember(state.homeTab, state.selectedAlbumId, state.selectedGeneratedVersion, state.generatedTab) {
        mutableStateOf(false)
    }
    var canScrollToTop by remember(state.homeTab, state.selectedAlbumId, state.selectedGeneratedVersion, state.generatedTab) {
        mutableStateOf(false)
    }
    var scrollToTopAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val albumColumns = StudioLayout.columns(state.albumListColumns, configuration.screenWidthDp, configuration.fontScale, true)
    var lastPinchAt by remember { mutableStateOf(0L) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var generatedSelectionActions by remember { mutableStateOf<GeneratedSelectionActionsState?>(null) }
    val systemItems = remember(state.photos) { state.photos.filterNot { it.generatedVirtual } }
    val albumItems = remember(systemItems, state.selectedAlbumId) {
        state.selectedAlbumId?.let { bucketId -> systemItems.filter { it.bucketId == bucketId } } ?: emptyList()
    }
    val baseVisibleItems = remember(systemItems, albumItems, state.homeTab, state.selectedAlbumId) {
        if (state.homeTab == "albums" && state.selectedAlbumId != null) albumItems else systemItems
    }
    val visibleItems = remember(
        baseVisibleItems,
        state.gallerySortMode,
        state.galleryFilterMode,
        state.entries,
        state.videoEntries,
    ) {
        val filtered = when (state.galleryFilterMode) {
            GalleryFilterMode.ALL -> baseVisibleItems
            GalleryFilterMode.IMAGES_ONLY -> baseVisibleItems.filter { it.kind == MediaKind.IMAGE }
            GalleryFilterMode.VIDEOS_ONLY -> baseVisibleItems.filter { it.kind == MediaKind.VIDEO }
            GalleryFilterMode.VR_GENERATED -> baseVisibleItems.filter { item ->
                if (item.kind == MediaKind.VIDEO) item.cacheKey in state.videoEntries else item.cacheKey in state.entries
            }
            GalleryFilterMode.VR_NOT_GENERATED -> baseVisibleItems.filter { item ->
                if (item.kind == MediaKind.VIDEO) item.cacheKey !in state.videoEntries else item.cacheKey !in state.entries
            }
        }
        filtered.sortedWith(
            compareByDescending<PhotoItem> { it.sortTimestamp(state.gallerySortMode) }
                .thenByDescending { it.id },
        )
    }
    val photoIndexByKey = remember(state.photos) { state.photos.mapIndexed { index, item -> item.cacheKey to index }.toMap() }
    val visibleScopeKeys = remember(visibleItems, state.homeTab, state.selectedAlbumId) {
        if (state.homeTab == "generated") emptyList() else visibleItems.map { it.cacheKey }
    }
    val selectedIndexes = remember(selectedKeys, photoIndexByKey) {
        if (selectedKeys.isEmpty()) emptyList() else selectedKeys.mapNotNull { photoIndexByKey[it] }
    }
    val timelineScope = if (state.homeTab == "albums") {
        state.selectedAlbumId?.let(GalleryGridScope::album) ?: GalleryGridScope.ALL
    } else GalleryGridScope.ALL
    val showTabletAlbumSplit = windowMetrics.isSplitPane && state.homeTab == "albums" && state.selectedAlbumId != null
    val effectiveGridWidthDp = if (showTabletAlbumSplit) (configuration.screenWidthDp - 276).coerceAtLeast(360) else configuration.screenWidthDp
    val timelineColumns = StudioLayout.columns(
        if (state.homeTab == "albums" && state.selectedAlbumId != null) state.albumDetailColumns else state.allColumns,
        effectiveGridWidthDp,
        configuration.fontScale,
    )
    var generatedTopBarHeightPx by remember { mutableStateOf(0) }
    var generatedTabsBounds by remember { mutableStateOf<Rect?>(null) }
    var generatedTabsOverlap by remember(state.homeTab, state.generatedTab, state.selectedGeneratedVersion) { mutableStateOf(false) }

    val topBarStatusText = if (state.homeTab == "generated" && state.selectedGeneratedVersion != null) {
        cacheVersionTitle(state.selectedGeneratedVersion, lang)
    } else {
        state.message?.let { lang.pickMixed(it) }?.takeUnless { it.startsWith("已加载最近") || it.startsWith("Loaded recent") }
    }
    val activeTaskCount = remember(state.jobs, state.videoJobs) {
        state.jobs.count { it.state == VrState.GENERATING || it.state == VrState.QUEUED } +
            state.videoJobs.count { it.state == VideoVrState.GENERATING || it.state == VideoVrState.QUEUED }
    }
    val isDetail = (state.homeTab == "albums" && state.selectedAlbumId != null) ||
        (state.homeTab == "generated" && state.selectedGeneratedVersion != null)
    val pageLargeTitle = when (state.homeTab) {
        "generated" -> lang.t("生成", "Generated")
        "albums" -> state.selectedAlbumId?.let { id -> state.albums.firstOrNull { it.bucketId == id }?.name } ?: lang.t("相册", "Albums")
        else -> lang.t("全部", "All")
    }

    LaunchedEffect(state.homeTab, state.selectedAlbumId, state.selectedGeneratedVersion) {
        openMenu = GalleryTopMenu.NONE
        if (state.homeTab != "generated") generatedSelectionActions = null
    }

    val topProgressiveBlurAlpha by animateFloatAsState(
        targetValue = if (contentScrolled || generatedTabsOverlap || openMenu != GalleryTopMenu.NONE) 1f else 0f,
        animationSpec = spring(stiffness = 380f),
        label = "topProgressiveBlurAlpha",
    )

    // Full edge-to-edge root Box: photos scroll behind transparent status bar AND bottom glass dock!
    Box(Modifier.fillMaxSize().background(apple.groupedBackground).imePadding()) {
        // 1. Live scrollable content layer recorded into GPU backdrop GraphicsLayer
        Box(
            Modifier
                .fillMaxSize()
                .studioBackdropSource(backdrop),
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0.dp),
            ) { padding ->
                if (state.loading) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = apple.systemBlue, strokeWidth = 2.6.dp)
                    }
                } else if (state.homeTab == "generated") {
                    val genTopPad = with(density) { generatedTopBarHeightPx.toDp() }.coerceAtLeast(statusBarTop + 104.dp)
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        ManageScreen(
                            state = state,
                            viewports = viewports,
                            embedded = true,
                            onBack = {},
                            selectedTab = state.generatedTab,
                            onTabChange = onSetGeneratedTab,
                            onToggleVersion = onToggleGeneratedVersion,
                            onOpenVersion = onOpenGeneratedVersion,
                            onCloseVersion = onCloseGeneratedVersion,
                            contentTopPadding = genTopPad + 8.dp,
                            contentBottomPadding = bottomContentPadding,
                            onSetGeneratedColumns = { onSetPageColumns("generated", it) },
                            onDeleteVersion = onDeleteVersion,
                            onOpenGenerated = onOpenGenerated,
                            onOpenGeneratedVideo = onOpenGeneratedVideo,
                            onSaveVideo = onSaveVideo,
                            onDeleteVideo = onDeleteVideo,
                            onSaveVideos = onSaveVideos,
                            onDeleteVideos = onDeleteVideos,
                            onRegenerateVideos = onRegenerateVideos,
                            onStartVideos = onStartVideos,
                            onPauseVideos = onPauseVideos,
                            onSaveImages = onSaveImages,
                            onDeleteImages = onDeleteImages,
                            onRegenerateImages = onRegenerateImages,
                            onGenerateImages = onGenerateImages,
                            onGeneratedSelectionChange = { generatedSelectionActions = it },
                            tabsBounds = generatedTabsBounds,
                            onTabsOverlapChanged = { generatedTabsOverlap = it },
                            onScrollStateChanged = { scrolled, deep, scrollToTop ->
                                contentScrolled = scrolled
                                canScrollToTop = deep
                                scrollToTopAction = scrollToTop
                            },
                        )
                    }
                } else if (state.homeTab != "generated" && (if (state.homeTab == "albums" && state.selectedAlbumId == null) state.albums.isEmpty() else baseVisibleItems.isEmpty())) {
                    StudioEmptyState(
                        lang.t("还没有可浏览的媒体", "No media to browse"),
                        lang.t("授予媒体访问权限，或在系统图库添加图片与视频后刷新。", "Allow media access, or add photos and videos to your system gallery, then refresh."),
                        icon = Icons.Outlined.PhotoLibrary,
                    ) {
                        AppleCapsuleButton(text = lang.t("刷新", "Refresh"), onClick = onRefresh, primary = false)
                    }
                } else if (state.homeTab == "albums" && state.selectedAlbumId == null) {
                    val albumKeys = remember(state.albums) { listOf("__albums_title__") + state.albums.map { it.bucketId } }
                    val albumListGridState = rememberGalleryGridState(viewports, GalleryGridScope.ALBUMS, albumKeys)
                    val scope = rememberCoroutineScope()
                    val scrolled by remember(albumListGridState) {
                        derivedStateOf { albumListGridState.firstVisibleItemIndex > 0 || albumListGridState.firstVisibleItemScrollOffset > 28 }
                    }
                    val deepScrolled by remember(albumListGridState) {
                        derivedStateOf { albumListGridState.firstVisibleItemIndex > 12 }
                    }
                    LaunchedEffect(scrolled, deepScrolled) {
                        contentScrolled = scrolled
                        canScrollToTop = deepScrolled
                        scrollToTopAction = { scope.launch { albumListGridState.smoothScrollToTop() } }
                    }
                    val sidePad = if (windowMetrics.isTablet) 20.dp else 14.dp
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(albumColumns),
                            state = albumListGridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .discreteColumnPinch(
                                    columns = albumColumns,
                                    onColumns = { onSetPageColumns("albumList", it) },
                                    onPinchActivity = { lastPinchAt = System.currentTimeMillis() },
                                ),
                            contentPadding = PaddingValues(start = sidePad, top = statusBarTop + 56.dp, end = sidePad, bottom = bottomContentPadding),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            item(key = "__albums_title__", span = { GridItemSpan(maxLineSpan) }) {
                                GalleryLargeScrollTitle(pageLargeTitle)
                            }
                            items(state.albums, key = { it.bucketId }) { album ->
                                AlbumTile(album = album, lang = lang, columns = albumColumns, onClick = { onOpenAlbum(album.bucketId) })
                            }
                        }
                        GridScrollbar(albumListGridState, Modifier.align(Alignment.CenterEnd).padding(top = statusBarTop + 58.dp, bottom = bottomContentPadding))
                    }
                } else if (showTabletAlbumSplit) {
                    Row(Modifier.fillMaxSize().padding(padding)) {
                        LazyColumn(
                            modifier = Modifier
                                .width(276.dp)
                                .fillMaxHeight()
                                .background(apple.cardBackground.copy(alpha = if (apple.isDark) 0.55f else 0.72f)),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = statusBarTop + 62.dp, bottom = bottomContentPadding),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(state.albums, key = { it.bucketId }) { album ->
                                val isSelected = album.bucketId == state.selectedAlbumId
                                AlbumSidebarRow(
                                    album = album,
                                    lang = lang,
                                    selected = isSelected,
                                    onClick = {
                                        selectedKeys = emptySet()
                                        onOpenAlbum(album.bucketId)
                                    },
                                )
                            }
                        }
                        Box(Modifier.width(0.5.dp).fillMaxHeight().background(apple.separator))
                        TimelineGrid(
                            pageTitle = pageLargeTitle,
                            items = visibleItems,
                            state = state,
                            lang = lang,
                            viewports = viewports,
                            viewportScope = timelineScope,
                            columns = timelineColumns,
                            selectedKeys = selectedKeys,
                            onColumns = { nextColumns ->
                                lastPinchAt = System.currentTimeMillis()
                                onSetPageColumns("albumDetail", nextColumns)
                            },
                            onItemClick = { photo ->
                                if (selectedKeys.isNotEmpty()) {
                                    selectedKeys = if (photo.cacheKey in selectedKeys) selectedKeys - photo.cacheKey else selectedKeys + photo.cacheKey
                                } else {
                                    val index = state.photos.indexOfFirst { it.cacheKey == photo.cacheKey }
                                    if (index >= 0) onOpenScoped(index, visibleScopeKeys)
                                }
                            },
                            onItemLongClick = { photo ->
                                if (System.currentTimeMillis() - lastPinchAt > 700L) {
                                    selectedKeys = selectedKeys + photo.cacheKey
                                }
                            },
                            onNearEnd = { onLoadMoreAlbum() },
                            footerText = run {
                                val total = state.albums.firstOrNull { it.bucketId == state.selectedAlbumId }?.count ?: visibleItems.size
                                val loaded = visibleItems.size
                                when {
                                    state.albumLoading -> lang.t("正在加载更多...", "Loading more...")
                                    loaded < total -> lang.t("已加载 $loaded/$total，继续下滑加载更多", "Loaded $loaded/$total; scroll for more")
                                    else -> lang.t("已加载全部 $loaded 项", "Loaded all $loaded items")
                                }
                            },
                            contentTopPadding = statusBarTop + 56.dp,
                            contentBottomPadding = bottomContentPadding,
                            horizontalEdgePadding = 2.dp,
                            statusBarTop = statusBarTop,
                            sortMode = state.gallerySortMode,
                            viewMode = state.galleryViewMode,
                            isFiltered = state.galleryFilterMode != GalleryFilterMode.ALL,
                            onClearFilter = { onSetGalleryFilter(GalleryFilterMode.ALL) },
                            onScrollStateChanged = { scrolled, deep, action ->
                                contentScrolled = scrolled
                                canScrollToTop = deep
                                scrollToTopAction = action
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                } else {
                    TimelineGrid(
                        pageTitle = pageLargeTitle,
                        items = visibleItems,
                        state = state,
                        lang = lang,
                        viewports = viewports,
                        viewportScope = timelineScope,
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
                                if (index >= 0) onOpenScoped(index, visibleScopeKeys)
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
                            val loaded = baseVisibleItems.size
                            when {
                                state.albumLoading -> lang.t("正在加载更多...", "Loading more...")
                                loaded < total -> lang.t("已加载 $loaded/$total，继续下滑加载更多", "Loaded $loaded/$total; scroll for more")
                                else -> lang.t("已加载全部 $loaded 项", "Loaded all $loaded items")
                            }
                        } else if (state.homeTab == "all") {
                            when {
                                state.allLoading -> lang.t("正在预取更多...", "Prefetching more...")
                                !state.allExhausted -> lang.t("已加载 ${baseVisibleItems.size} 项，继续下滑加载更多", "Loaded ${baseVisibleItems.size} items; scroll for more")
                                else -> lang.t("已加载全部 ${baseVisibleItems.size} 项", "Loaded all ${baseVisibleItems.size} items")
                            }
                        } else {
                            null
                        },
                        contentTopPadding = statusBarTop + 56.dp,
                        contentBottomPadding = bottomContentPadding,
                        horizontalEdgePadding = if (windowMetrics.isTablet) 2.dp else 0.dp,
                        statusBarTop = statusBarTop,
                        sortMode = state.gallerySortMode,
                        viewMode = state.galleryViewMode,
                        isFiltered = state.galleryFilterMode != GalleryFilterMode.ALL,
                        onClearFilter = { onSetGalleryFilter(GalleryFilterMode.ALL) },
                        onScrollStateChanged = { scrolled, deep, action ->
                            contentScrolled = scrolled
                            canScrollToTop = deep
                            scrollToTopAction = action
                        },
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                }
            }
        }

        // 2. Real-Time GPU Backdrop-Blurred Floating Chrome Layer (siblings above backdropSource!)
        CompositionLocalProvider(LocalStudioBackdrop provides backdrop) {
            // Reference Image 5: Progressive translucent top blur gradient fading smoothly into sharp photos!
            ProgressiveTopBlurOverlay(
                height = statusBarTop + if (state.homeTab == "generated") 122.dp else 88.dp,
                alpha = topProgressiveBlurAlpha,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            // Top Floating Controls: ONLY 2 circular buttons on top-right (+ back circle on detail views)!
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .onSizeChanged { generatedTopBarHeightPx = it.height + with(density) { statusBarTop.roundToPx() } }
                    .padding(horizontal = if (windowMetrics.isTablet) 20.dp else 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isDetail) {
                        AppleGlassCircleButton(
                            icon = Icons.Rounded.ChevronLeft,
                            contentDescription = lang.t("返回", "Back"),
                            onClick = { if (state.homeTab == "albums") onCloseAlbum() else onCloseGeneratedVersion() },
                            glassActive = true,
                            size = 44.dp,
                            iconSize = 24.dp,
                        )
                    } else if (state.homeTab == "generated") {
                        Text(
                            text = lang.t("生成", "Generated"),
                            color = apple.labelPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = (-0.5).sp,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // Reference Image 1 & 5: ONLY two clean circular buttons on top-right!
                    val supportsSortAndFilter = state.homeTab == "all" || (state.homeTab == "albums" && state.selectedAlbumId != null)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppleGlassCircleButton(
                            icon = Icons.Rounded.Tune,
                            contentDescription = lang.t("排序与筛选", "Sort & filter"),
                            onClick = {
                                openMenu = if (openMenu == GalleryTopMenu.VIEW_OPTIONS) GalleryTopMenu.NONE else GalleryTopMenu.VIEW_OPTIONS
                            },
                            glassActive = contentScrolled || generatedTabsOverlap || openMenu == GalleryTopMenu.VIEW_OPTIONS,
                            badgeDot = supportsSortAndFilter && state.galleryFilterMode != GalleryFilterMode.ALL,
                            size = 44.dp,
                            iconSize = 22.dp,
                        )
                        AppleGlassCircleButton(
                            icon = Icons.Rounded.MoreVert,
                            contentDescription = lang.t("更多", "More"),
                            onClick = {
                                openMenu = if (openMenu == GalleryTopMenu.MORE) GalleryTopMenu.NONE else GalleryTopMenu.MORE
                            },
                            glassActive = contentScrolled || generatedTabsOverlap || openMenu == GalleryTopMenu.MORE,
                            badgeDot = activeTaskCount > 0,
                            size = 44.dp,
                            iconSize = 22.dp,
                        )
                    }
                }

                if (state.homeTab == "generated") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        RoundedPillTabs(
                            options = listOf("images" to lang.t("图片", "Images"), "videos" to lang.t("视频", "Videos")),
                            selected = state.generatedTab,
                            onSelect = onSetGeneratedTab,
                            modifier = Modifier
                                .widthIn(max = if (windowMetrics.isTablet) 280.dp else 340.dp)
                                .fillMaxWidth()
                                .onGloballyPositioned { generatedTabsBounds = it.boundsInRoot() },
                            translucent = generatedTabsOverlap || contentScrolled,
                        )
                    }
                }

                if (state.homeTab == "generated" && state.selectedGeneratedVersion != null) {
                    val versionHeader = cacheVersionTitle(state.selectedGeneratedVersion, lang)
                    RealLiquidGlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 20.dp,
                        shadowElevation = 4.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = versionHeader,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                                color = apple.labelPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Two-Layer Liquid Glass Bottom Dock (centered, without Settings button) + Side Floating Back-to-Top button!
            val actions = generatedSelectionActions.takeIf { state.homeTab == "generated" }
            val inSelectionMode = actions != null || selectedKeys.isNotEmpty()
            val transientMessage = topBarStatusText
                ?.takeUnless { state.homeTab == "generated" && state.selectedGeneratedVersion != null }
                ?.takeIf { it != dismissedMessage }
            LaunchedEffect(transientMessage) {
                if (transientMessage != null && (
                        transientMessage.startsWith("已恢复") || transientMessage.startsWith("Restored") ||
                            transientMessage.startsWith("相册已加载") || transientMessage.startsWith("Album loaded") ||
                            transientMessage.startsWith("全部页已加载") || transientMessage.startsWith("All page loaded")
                        )
                ) {
                    delay(3200)
                    dismissedMessage = transientMessage
                }
            }

            // Side-floating Back-to-Top frosted circular button (right edge, separate from Bottom Dock)
            AnimatedVisibility(
                visible = canScrollToTop && scrollToTopAction != null && !inSelectionMode,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = with(density) { dockHeightPx.toDp() } + 18.dp),
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
            ) {
                RealLiquidGlassSurface(
                    modifier = Modifier
                        .size(46.dp)
                        .clickable { scrollToTopAction?.invoke() },
                    shape = CircleShape,
                    blurRadius = 22.dp,
                    tintColor = if (apple.isDark) Color(0x881E1E24) else Color(0x9EF4F5F8),
                    borderWidth = 1.dp,
                    shadowElevation = 10.dp,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = lang.t("回到顶部", "Back to top"),
                            tint = apple.iconPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .onSizeChanged { dockHeightPx = it.height },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (transientMessage != null && !inSelectionMode) {
                    RealLiquidGlassSurface(
                        modifier = Modifier.widthIn(max = 420.dp),
                        shape = CircleShape,
                        blurRadius = 22.dp,
                        shadowElevation = 8.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = lang.pickMixed(transientMessage),
                                style = MaterialTheme.typography.bodySmall,
                                color = apple.labelPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            IconButton(onClick = { dismissedMessage = transientMessage }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Close, lang.t("收起提示", "Dismiss message"), Modifier.size(15.dp), tint = apple.labelSecondary)
                            }
                        }
                    }
                }
                if (inSelectionMode) {
                    RealLiquidGlassSurface(
                        modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        blurRadius = 26.dp,
                        tintColor = if (apple.isDark) Color(0xB81E1E24) else Color(0xC2F7F8FA),
                        shadowElevation = 14.dp,
                    ) {
                        if (actions != null) {
                            ManageSelectionActions(
                                count = actions.count,
                                lang = lang,
                                onClear = actions.onClear,
                                onSelectAll = actions.onSelectAll,
                                onSave = actions.onSave,
                                onRegenerate = actions.onRegenerate,
                                onDelete = actions.onDelete,
                                confirmChanges = !(state.generatedTab == "images" && state.selectedGeneratedVersion == null),
                                onGenerate = actions.onGenerate,
                                imageReplacement = actions.imageReplacement,
                                videoActions = actions.videoActions,
                            )
                        } else {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        lang.t("已选 ${selectedKeys.size} 项 · 仅处理已生成图片", "${selectedKeys.size} selected · generated photos only"),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = apple.labelPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { selectedKeys = emptySet() }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Rounded.Close, lang.t("取消", "Cancel"), tint = apple.labelSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                                AppleDivider()
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { selectedKeys = emptySet() }) {
                                        Text(lang.t("取消", "Cancel"), color = apple.labelSecondary)
                                    }
                                    TextButton(onClick = { onSaveGenerated(selectedIndexes); selectedKeys = emptySet() }) {
                                        Text(lang.t("保存无损 PNG", "Save lossless PNG"), color = apple.systemBlue, fontWeight = FontWeight.SemiBold)
                                    }
                                    TextButton(onClick = { confirmReplace = true }) {
                                        Text(lang.t("替换原图…", "Replace originals…"), color = apple.systemRed, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Outer frosted Liquid Glass Dock capsule ("大圆框") containing the clearer inner lens ("小的更透明")
                    RealLiquidGlassSurface(
                        modifier = Modifier.widthIn(max = 286.dp),
                        shape = CircleShape,
                        blurRadius = 24.dp,
                        tintColor = if (apple.isDark) Color(0x881E1E24) else Color(0x9EF4F5F8),
                        borderWidth = 1.dp,
                        shadowElevation = 14.dp,
                    ) {
                        HomeBottomNav(state.homeTab, lang, { selectedKeys = emptySet(); onSetTab(it) })
                    }
                }
            }

            // Popover 1 (Tune button): Sort + View (Date Grouping / Grid Density) + Filter — Zero duplication with More!
            val supportsSortAndFilter = state.homeTab == "all" || (state.homeTab == "albums" && state.selectedAlbumId != null)
            val activePageKey = when {
                state.homeTab == "generated" -> "generated"
                state.homeTab == "albums" && state.selectedAlbumId == null -> "albumList"
                state.homeTab == "albums" -> "albumDetail"
                else -> "all"
            }
            val currentPageCols = when (activePageKey) {
                "generated" -> state.generatedColumns
                "albumList" -> state.albumListColumns
                "albumDetail" -> state.albumDetailColumns
                else -> state.allColumns
            }
            val densityOptions = if (activePageKey == "albumList" || activePageKey == "generated") {
                if (windowMetrics.isTablet) listOf(3 to lang.t("大图视图（3 列）", "Large grid (3 cols)"), 4 to lang.t("标准视图（4 列）", "Standard grid (4 cols)"), 5 to lang.t("紧凑视图（5 列）", "Compact grid (5 cols)"))
                else listOf(2 to lang.t("大图视图（2 列）", "Large grid (2 cols)"), 3 to lang.t("标准视图（3 列）", "Standard grid (3 cols)"))
            } else {
                if (windowMetrics.isTablet) listOf(3 to lang.t("大图视图（3 列）", "Large grid (3 cols)"), 5 to lang.t("标准视图（5 列）", "Standard grid (5 cols)"), 7 to lang.t("紧凑视图（7 列）", "Compact grid (7 cols)"))
                else listOf(3 to lang.t("大图视图（3 列）", "Large grid (3 cols)"), 4 to lang.t("标准视图（4 列）", "Standard grid (4 cols)"), 5 to lang.t("紧凑视图（5 列）", "Compact grid (5 cols)"))
            }

            AppleGlassPopoverOverlay(
                visible = openMenu == GalleryTopMenu.VIEW_OPTIONS,
                onDismiss = { openMenu = GalleryTopMenu.NONE },
                menuAlignment = Alignment.TopEnd,
                menuModifier = Modifier.padding(top = statusBarTop + 58.dp, end = if (windowMetrics.isTablet) 74.dp else 58.dp),
                minWidth = if (windowMetrics.isTablet) 224.dp else 204.dp,
                maxWidth = if (windowMetrics.isTablet) 268.dp else 232.dp,
                transformOrigin = TransformOrigin(0.9f, 0f),
            ) {
                if (!supportsSortAndFilter) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        densityOptions.forEach { (cols, label) ->
                            GlassPopoverCheckRow(
                                label = label,
                                selected = currentPageCols == cols,
                                onClick = {
                                    onSetPageColumns(activePageKey, cols)
                                    openMenu = GalleryTopMenu.NONE
                                },
                            )
                        }
                    }
                } else {
                    AnimatedContent(
                        targetState = tuneSubmenu,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "tuneSubmenuTransition",
                    ) { submenu ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            when (submenu) {
                                TuneSubmenu.ROOT -> {
                                    GlassPopoverCheckRow(
                                        label = lang.t("按拍摄时间排序", "Sort by capture date"),
                                        selected = state.gallerySortMode == GallerySortMode.TAKEN_TIME,
                                        onClick = {
                                            onSetGallerySort(GallerySortMode.TAKEN_TIME)
                                            openMenu = GalleryTopMenu.NONE
                                        },
                                    )
                                    GlassPopoverCheckRow(
                                        label = lang.t("按添加时间排序", "Sort by date added"),
                                        selected = state.gallerySortMode == GallerySortMode.ADDED_TIME,
                                        onClick = {
                                            onSetGallerySort(GallerySortMode.ADDED_TIME)
                                            openMenu = GalleryTopMenu.NONE
                                        },
                                    )
                                    AppleDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                                    GlassPopoverSubmenuRow(
                                        title = lang.t("视图", "View"),
                                        subtitle = if (state.galleryViewMode == GalleryViewMode.DATE_GROUPED) {
                                            lang.t("按日期分组", "Group by date")
                                        } else {
                                            lang.t("平铺网格", "Flat grid")
                                        },
                                        onClick = { tuneSubmenu = TuneSubmenu.VIEW },
                                    )
                                    GlassPopoverSubmenuRow(
                                        title = lang.t("筛选", "Filter"),
                                        subtitle = galleryFilterLabel(state.galleryFilterMode, lang),
                                        onClick = { tuneSubmenu = TuneSubmenu.FILTER },
                                    )
                                }
                                TuneSubmenu.VIEW -> {
                                    GlassPopoverBackRow(lang.t("视图", "View")) { tuneSubmenu = TuneSubmenu.ROOT }
                                    AppleDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                                    GlassPopoverCheckRow(
                                        label = lang.t("按日期分组", "Group by date"),
                                        selected = state.galleryViewMode == GalleryViewMode.DATE_GROUPED,
                                        onClick = {
                                            onSetGalleryViewMode(GalleryViewMode.DATE_GROUPED)
                                            openMenu = GalleryTopMenu.NONE
                                        },
                                    )
                                    GlassPopoverCheckRow(
                                        label = lang.t("平铺网格", "Flat grid"),
                                        selected = state.galleryViewMode == GalleryViewMode.FLAT_GRID,
                                        onClick = {
                                            onSetGalleryViewMode(GalleryViewMode.FLAT_GRID)
                                            openMenu = GalleryTopMenu.NONE
                                        },
                                    )
                                    AppleDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                                    densityOptions.forEach { (cols, label) ->
                                        GlassPopoverCheckRow(
                                            label = label,
                                            selected = currentPageCols == cols,
                                            onClick = {
                                                onSetPageColumns(activePageKey, cols)
                                                openMenu = GalleryTopMenu.NONE
                                            },
                                        )
                                    }
                                }
                                TuneSubmenu.FILTER -> {
                                    GlassPopoverBackRow(lang.t("筛选", "Filter")) { tuneSubmenu = TuneSubmenu.ROOT }
                                    AppleDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                                    GalleryFilterMode.entries.forEach { mode ->
                                        GlassPopoverCheckRow(
                                            label = galleryFilterLabel(mode, lang),
                                            selected = state.galleryFilterMode == mode,
                                            onClick = {
                                                onSetGalleryFilter(mode)
                                                openMenu = GalleryTopMenu.NONE
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Popover 2 (More button): Tasks + Refresh + User Guide + Settings (sole top-level Settings entry)
            AppleGlassPopoverOverlay(
                visible = openMenu == GalleryTopMenu.MORE,
                onDismiss = { openMenu = GalleryTopMenu.NONE },
                menuAlignment = Alignment.TopEnd,
                menuModifier = Modifier.padding(top = statusBarTop + 58.dp, end = if (windowMetrics.isTablet) 20.dp else 14.dp),
                minWidth = if (windowMetrics.isTablet) 216.dp else 196.dp,
                maxWidth = if (windowMetrics.isTablet) 260.dp else 224.dp,
                transformOrigin = TransformOrigin(0.92f, 0f),
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    val tasksTitle = if (activeTaskCount > 0) {
                        lang.t("任务与进度 · $activeTaskCount", "Tasks & progress · $activeTaskCount")
                    } else {
                        lang.t("任务与进度", "Tasks & progress")
                    }
                    GlassPopoverSimpleRow(tasksTitle) {
                        openMenu = GalleryTopMenu.NONE
                        showTasks = true
                    }
                    GlassPopoverSimpleRow(lang.t("刷新媒体库", "Refresh library")) {
                        openMenu = GalleryTopMenu.NONE
                        onRefresh()
                    }
                    GlassPopoverSimpleRow(lang.t("使用指南", "User guide")) {
                        openMenu = GalleryTopMenu.NONE
                        showGuide = true
                    }
                    GlassPopoverSimpleRow(lang.t("设置", "Settings")) {
                        openMenu = GalleryTopMenu.NONE
                        onSettings()
                    }
                }
            }
        }

        if (showGuide) StudioGuideDialog(lang) { showGuide = false }
        if (showTasks) StudioTasksSheet(state) { showTasks = false }
        if (confirmReplace) {
            AlertDialog(
                onDismissRequest = { confirmReplace = false },
                containerColor = apple.elevatedSurface,
                shape = RoundedCornerShape(24.dp),
                title = { Text(lang.t("替换所选原图？", "Replace selected originals?"), fontWeight = FontWeight.Bold) },
                text = { Text(lang.t("仅替换所选项中已有生成结果的图片。这会改写系统原图，与保存副本不同；系统还会请求写入许可。", "Only selected photos with generated results are replaced. This changes original media, unlike Save copies. Android will also request write access."), color = apple.labelSecondary) },
                confirmButton = {
                    Button(
                        onClick = { confirmReplace = false; onReplaceOriginal(selectedIndexes); selectedKeys = emptySet() },
                        shape = CircleShape,
                    ) { Text(lang.t("继续", "Continue")) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmReplace = false }) { Text(lang.t("取消", "Cancel")) }
                },
            )
        }
    }
}

/** Reference Image 1 large scrolling page title (scrolls cleanly out of view as photos rise). */
@Composable
internal fun GalleryLargeScrollTitle(title: String) {
    Text(
        text = title,
        color = AppleTheme.colors.labelPrimary,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.5).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 8.dp),
    )
}

@Composable
private fun GlassPopoverCheckRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val apple = AppleTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) apple.systemBlue else apple.labelPrimary,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = apple.systemBlue, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun GlassPopoverSubmenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    val apple = AppleTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 16.sp, color = apple.labelPrimary, fontWeight = FontWeight.Normal)
            Text(subtitle, fontSize = 13.sp, color = apple.labelSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = apple.labelTertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun GlassPopoverSimpleRow(title: String, onClick: () -> Unit) {
    val apple = AppleTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, color = apple.labelPrimary, fontWeight = FontWeight.Normal)
    }
}

@Composable
private fun GlassPopoverBackRow(title: String, onClick: () -> Unit) {
    val apple = AppleTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 20.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = apple.systemBlue, modifier = Modifier.size(22.dp))
        Text(title, fontSize = 15.sp, color = apple.labelPrimary, fontWeight = FontWeight.SemiBold)
    }
}

private fun galleryFilterLabel(mode: GalleryFilterMode, lang: AppLanguage): String = when (mode) {
    GalleryFilterMode.ALL -> lang.t("全部媒体", "All media")
    GalleryFilterMode.IMAGES_ONLY -> lang.t("仅照片", "Photos only")
    GalleryFilterMode.VIDEOS_ONLY -> lang.t("仅视频", "Videos only")
    GalleryFilterMode.VR_GENERATED -> lang.t("已生成 VR", "VR generated")
    GalleryFilterMode.VR_NOT_GENERATED -> lang.t("未生成 VR", "Not generated")
}

@Composable
private fun AlbumSidebarRow(
    album: AlbumItem,
    lang: AppLanguage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val apple = AppleTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) apple.systemBlue.copy(alpha = if (apple.isDark) 0.24f else 0.13f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(apple.fillSecondary),
        ) {
            AsyncMediaThumbnail(album.coverKind, album.coverUri, 200, ContentScale.Crop, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = album.name,
                color = if (selected) apple.systemBlue else apple.labelPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = lang.t("${album.count} 项", "${album.count} items"),
                color = if (selected) apple.systemBlue.copy(alpha = 0.8f) else apple.labelSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineGrid(
    pageTitle: String,
    items: List<PhotoItem>,
    state: UiState,
    lang: AppLanguage,
    viewports: GalleryViewports,
    viewportScope: String,
    columns: Int,
    selectedKeys: Set<String>,
    onColumns: (Int) -> Unit,
    onItemClick: (PhotoItem) -> Unit,
    onItemLongClick: (PhotoItem) -> Unit,
    onNearEnd: () -> Unit,
    footerText: String?,
    contentTopPadding: androidx.compose.ui.unit.Dp = 64.dp,
    contentBottomPadding: androidx.compose.ui.unit.Dp = 104.dp,
    horizontalEdgePadding: androidx.compose.ui.unit.Dp = 0.dp,
    statusBarTop: androidx.compose.ui.unit.Dp = 0.dp,
    sortMode: GallerySortMode = GallerySortMode.TAKEN_TIME,
    viewMode: GalleryViewMode = GalleryViewMode.DATE_GROUPED,
    isFiltered: Boolean = false,
    onClearFilter: () -> Unit = {},
    onScrollStateChanged: (scrolled: Boolean, deepScrolled: Boolean, scrollToTop: () -> Unit) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val apple = AppleTheme.colors
    val scope = rememberCoroutineScope()
    val saved = remember(viewports, viewportScope) { viewports[viewportScope] }
    val anchorMediaIndex = remember(saved, items, sortMode) {
        if (saved == null) 0 else {
            val day = saved.key.removePrefix("header_").toLongOrNull()
            items.indexOfFirst { it.cacheKey == saved.key || (day != null && localEpochDay(it.sortTimestamp(sortMode)) == day) }
        }
    }
    val restoreRenderLimit = min(items.size, max(1200, max(saved?.index ?: 0, anchorMediaIndex) + 400))
    var renderLimit by remember(viewports, viewportScope) { mutableStateOf(restoreRenderLimit) }
    LaunchedEffect(items.size, restoreRenderLimit) {
        renderLimit = renderLimit.coerceAtLeast(restoreRenderLimit).coerceAtMost(items.size)
    }
    val renderedItems = remember(items, renderLimit) { items.take(renderLimit) }
    val cells = remember(renderedItems, lang, renderLimit, items.size, sortMode, viewMode) {
        val result = mutableListOf<TimelineCell>()
        var lastDay = Long.MIN_VALUE
        renderedItems.forEach { item ->
            if (viewMode == GalleryViewMode.DATE_GROUPED) {
                val ts = item.sortTimestamp(sortMode)
                val day = localEpochDay(ts)
                if (day != lastDay) {
                    val label = dateGroupLabel(ts, lang)
                    result += TimelineCell.Header(day, label)
                    lastDay = day
                }
            }
            result += TimelineCell.Media(item)
        }
        if (items.isNotEmpty() && (footerText != null || renderLimit < items.size)) {
            result += TimelineCell.Footer(renderLimit, items.size, footerText)
        }
        result
    }
    val keys = remember(cells) { listOf("__page_title__") + cells.map { it.gridKey } }
    val gridState = rememberGalleryGridState(viewports, viewportScope, keys)
    val isScrolled by remember(gridState) {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 36 }
    }
    val isDeepScrolled by remember(gridState) {
        derivedStateOf { gridState.firstVisibleItemIndex > 10 }
    }
    LaunchedEffect(isScrolled, isDeepScrolled, gridState) {
        onScrollStateChanged(isScrolled, isDeepScrolled) {
            scope.launch { gridState.smoothScrollToTop() }
        }
    }
    LaunchedEffect(items.size, gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (lastVisible > renderLimit - 80 && renderLimit < items.size) {
                    renderLimit = min(items.size, renderLimit + 1200)
                }
                if (lastVisible > renderLimit - 80) onNearEnd()
            }
    }
    if (items.isEmpty() && isFiltered) {
        StudioEmptyState(
            title = lang.t("当前筛选条件下没有媒体", "No media matches this filter"),
            detail = lang.t("可以切回全部媒体，或更换筛选条件继续浏览。", "Switch back to All media or choose another filter."),
            modifier = modifier.padding(top = contentTopPadding, bottom = contentBottomPadding),
            icon = Icons.Outlined.PhotoLibrary,
        ) {
            AppleCapsuleButton(text = lang.t("显示全部媒体", "Show all media"), onClick = onClearFilter)
        }
        return
    }
    Box(modifier) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .discreteColumnPinch(columns = columns, onColumns = onColumns),
            contentPadding = PaddingValues(start = horizontalEdgePadding, top = contentTopPadding, end = horizontalEdgePadding, bottom = contentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(1.5.dp),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        ) {
            item(key = "__page_title__", span = { GridItemSpan(maxLineSpan) }) {
                GalleryLargeScrollTitle(pageTitle)
            }
            items(
                items = cells,
                key = { it.gridKey },
                span = { cell -> if (cell is TimelineCell.Media) GridItemSpan(1) else GridItemSpan(maxLineSpan) },
            ) { cell ->
                when (cell) {
                    is TimelineCell.Header -> Text(
                        text = cell.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = apple.labelPrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp),
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
                        color = apple.labelSecondary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                    )
                }
            }
        }
        GridScrollbar(gridState, Modifier.align(Alignment.CenterEnd).padding(top = statusBarTop + 58.dp, bottom = contentBottomPadding))
    }
}

internal suspend fun LazyGridState.smoothScrollToTop() {
    if (firstVisibleItemIndex > 32) {
        scrollToItem(24)
    }
    animateScrollToItem(0)
}

@Composable
internal fun GridToTopButton(
    gridState: LazyGridState,
    lang: AppLanguage,
    modifier: Modifier = Modifier,
) {
    val visible = gridState.firstVisibleItemIndex > 18
    val scope = rememberCoroutineScope()
    val apple = AppleTheme.colors
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
    ) {
        RealLiquidGlassSurface(
            modifier = Modifier
                .size(46.dp)
                .clickable { scope.launch { gridState.smoothScrollToTop() } },
            shape = CircleShape,
            blurRadius = 22.dp,
            shadowElevation = 8.dp,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.KeyboardArrowUp,
                    contentDescription = lang.t("顶部", "Top"),
                    tint = apple.iconPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun AlbumTile(album: AlbumItem, lang: AppLanguage, columns: Int, onClick: () -> Unit) {
    val apple = AppleTheme.colors
    val reduction = columnFontReduction(columns)
    val titleSize = (14.5f - reduction).coerceAtLeast(10f).sp
    val countSize = (12 - reduction).coerceAtLeast(9).sp
    val textGap = if (columns >= 7) 5.dp else 7.dp
    val coverShape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(coverShape)
                .background(apple.fillSecondary),
        ) {
            AsyncMediaThumbnail(album.coverKind, album.coverUri, 420, ContentScale.Crop, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(textGap))
        Text(
            album.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            fontSize = titleSize,
            color = apple.labelPrimary,
        )
        Text(
            lang.t("${album.count} 项", "${album.count} items"),
            color = apple.labelSecondary,
            fontSize = countSize,
            maxLines = 1,
        )
    }
}

/** Reference Image 5 style floating pill scrollbar thumb with up/down indicator arrows. */
@Composable
internal fun GridScrollbar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(gridState.isScrollInProgress, dragging) {
        if (gridState.isScrollInProgress || dragging) shown = true
        else { delay(1100); shown = false }
    }
    val layoutInfo = gridState.layoutInfo
    val total = layoutInfo.totalItemsCount
    val visible = layoutInfo.visibleItemsInfo
    if (!shown || total <= 0 || visible.isEmpty() || total <= visible.size) return
    val first = visible.first().index
    val scrollableCount = (total - visible.size).coerceAtLeast(1)
    val progress = (first.toFloat() / scrollableCount.toFloat()).coerceIn(0f, 1f)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var trackHeight by remember { mutableStateOf(1) }
    val thumbHeightPx = with(density) { 42.dp.toPx() }

    fun scrollToTrackPosition(y: Float) {
        val fraction = (y / trackHeight.toFloat()).coerceIn(0f, 1f)
        val targetIndex = (fraction * (total - 1).toFloat()).roundToInt().coerceIn(0, total - 1)
        scope.launch { gridState.scrollToItem(targetIndex) }
    }

    Box(
        modifier
            .width(34.dp)
            .fillMaxHeight()
            .padding(end = 4.dp)
            .onSizeChanged { trackHeight = it.height.coerceAtLeast(1) }
            .pointerInput(total, trackHeight) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> dragging = true; scrollToTrackPosition(offset.y) },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { change, _ ->
                        scrollToTrackPosition(change.position.y)
                        change.consume()
                    },
                )
            },
        contentAlignment = Alignment.TopEnd,
    ) {
        val travel = (trackHeight - thumbHeightPx).coerceAtLeast(0f)
        Surface(
            shape = CircleShape,
            color = if (AppleTheme.colors.isDark) Color(0xE02C2C2E) else Color(0xEEFFFFFF),
            shadowElevation = 5.dp,
            modifier = Modifier
                .width(if (dragging) 26.dp else 22.dp)
                .height(42.dp)
                .graphicsLayer { translationY = travel * progress },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.UnfoldMore,
                    contentDescription = null,
                    tint = AppleTheme.colors.iconPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeBottomNav(current: String, lang: AppLanguage, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    AppleFloatingNavDock(current = current, lang = lang, onSelect = onSelect, modifier = modifier)
}

@Composable
internal fun RoundedPillTabs(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    translucent: Boolean = false,
) {
    AppleSegmentedControl(
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        translucent = translucent,
    )
}

internal fun Modifier.discreteColumnPinch(
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

@Composable
internal fun Modifier.generatedGridDragSelection(
    gridState: LazyGridState,
    selectionMode: Boolean,
    contentStartPadding: androidx.compose.ui.unit.Dp,
    onSelectIndex: (Int) -> Unit,
): Modifier {
    val currentSelectionMode by rememberUpdatedState(selectionMode)
    val currentOnSelectIndex by rememberUpdatedState(onSelectIndex)
    val contentStartPaddingPx = with(LocalDensity.current) { contentStartPadding.toPx() }
    return pointerInput(gridState, contentStartPaddingPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val selectionModeAtDown = currentSelectionMode
            var lastSelectedIndex: Int? = null

            fun selectAt(pointerPosition: Offset) {
                val layoutInfo = gridState.layoutInfo
                val contentPosition = Offset(
                    x = pointerPosition.x - contentStartPaddingPx,
                    y = pointerPosition.y - layoutInfo.beforeContentPadding,
                )
                val itemIndex = layoutInfo.visibleItemsInfo.firstOrNull { item ->
                    val left = item.offset.x.toFloat()
                    val top = item.offset.y.toFloat()
                    val right = left + item.size.width
                    val bottom = top + item.size.height
                    contentPosition.x >= left &&
                        contentPosition.x < right &&
                        contentPosition.y >= top &&
                        contentPosition.y < bottom
                }?.index ?: return
                if (itemIndex != lastSelectedIndex) {
                    lastSelectedIndex = itemIndex
                    currentOnSelectIndex(itemIndex)
                }
            }

            var latest = down
            val cancelled = withTimeoutOrNull(if (selectionModeAtDown) Long.MAX_VALUE else viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed || event.changes.count { it.pressed } != 1) return@withTimeoutOrNull true
                    latest = change
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        return@withTimeoutOrNull !selectionModeAtDown
                    }
                }
                @Suppress("UNREACHABLE_CODE") false
            }
            if (cancelled == true) return@awaitEachGesture
            selectAt(down.position)
            selectAt(latest.position)
            latest.consume()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (event.changes.count { it.pressed } > 1) break
                change.consume()
                if (!change.pressed) break
                selectAt(change.position)
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
    val apple = AppleTheme.colors
    val compact = columns >= 7
    val labelPaddingH = if (compact) 4.dp else 6.dp
    val labelPaddingV = if (compact) 1.dp else 2.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = photo.displayName; stateDescription = statusText }
            .aspectRatio(1f)
            .background(if (apple.isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        AsyncMediaThumbnail(photo.kind, photo.uri, 420, ContentScale.Crop, Modifier.fillMaxSize())

        // Reference Images 1–5: subtle bottom scrim + crisp outlined white circle play icon for videos
        if (photo.kind == MediaKind.VIDEO && !selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000)))),
            )
            Icon(
                imageVector = Icons.Outlined.PlayCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(if (compact) 3.dp else 5.dp)
                    .size(if (compact) 14.dp else 18.dp),
            )
        }

        if (selected) {
            AppleSelectionOverlay()
        }

        if (!selected && statusText == lang.t("已生成", "Ready")) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(if (compact) 3.dp else 4.dp)
                    .size(if (compact) 15.dp else 18.dp)
                    .clip(CircleShape)
                    .background(Color(0xB3000000))
                    .border(0.75.dp, apple.systemGreen.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = apple.systemGreen,
                    modifier = Modifier.size(if (compact) 9.dp else 11.dp),
                )
            }
        } else if (!selected && statusText != lang.t("原图", "Original") && statusText != lang.t("未生成", "Not generated")) {
            StatusBadge(
                text = statusText,
                fontSize = if (compact) 8.sp else 10.sp,
                modifier = Modifier.align(if (photo.kind == MediaKind.VIDEO) Alignment.TopStart else Alignment.BottomStart),
                horizontalPadding = labelPaddingH,
                verticalPadding = labelPaddingV,
            )
        }
    }
}

@Composable
internal fun StatusBadge(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 7.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 2.5.dp,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(5.dp)
            .clip(CircleShape)
            .background(Color(0xB3141418))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    )
}

internal val ManagedCacheItem.gridKey: String get() = "${entry.photoKey}_${entry.version}"
