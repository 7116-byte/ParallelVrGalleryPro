package com.local.parallelvrgallerypro

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@Composable
private fun Modifier.reportContentBehindTabs(
    grid: LazyGridState,
    tabsBounds: Rect?,
    onOverlapChanged: ((Boolean) -> Unit)?,
): Modifier {
    if (onOverlapChanged == null) return this
    var gridTop by remember(grid) { mutableStateOf<Float?>(null) }
    val reportOverlap by rememberUpdatedState(onOverlapChanged)
    LaunchedEffect(grid, tabsBounds) {
        snapshotFlow {
            val top = gridTop
            if (top == null || tabsBounds == null) return@snapshotFlow false
            val layout = grid.layoutInfo
            val contentTop = top - layout.viewportStartOffset
            layout.visibleItemsInfo.any { item ->
                val itemTop = contentTop + item.offset.y
                itemTop < tabsBounds.bottom && itemTop + item.size.height > tabsBounds.top
            }
        }.collect { reportOverlap(it) }
    }
    DisposableEffect(grid) { onDispose { onOverlapChanged(false) } }
    return onGloballyPositioned { gridTop = it.positionInRoot().y }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun ManageScreen(
    state: UiState,
    viewports: GalleryViewports,
    embedded: Boolean = false,
    onBack: () -> Unit,
    selectedTab: String,
    onTabChange: (String) -> Unit,
    onToggleVersion: (String) -> Unit,
    onOpenVersion: (String) -> Unit,
    onCloseVersion: () -> Unit,
    contentTopPadding: androidx.compose.ui.unit.Dp = 0.dp,
    contentBottomPadding: androidx.compose.ui.unit.Dp = 100.dp,
    onSetGeneratedColumns: (Int) -> Unit,
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
    onGeneratedSelectionChange: (GeneratedSelectionActionsState?) -> Unit = {},
    tabsBounds: Rect? = null,
    onTabsOverlapChanged: ((Boolean) -> Unit)? = null,
    onScrollStateChanged: (scrolled: Boolean, deepScrolled: Boolean, scrollToTop: () -> Unit) -> Unit = { _, _, _ -> },
) {
    val apple = AppleTheme.colors
    val windowMetrics = rememberStudioWindowMetrics()
    val lang = state.settings.language
    val config = LocalConfiguration.current
    var selectedImageKeys by remember { mutableStateOf(setOf<String>()) }
    var selectedVideoKeys by remember { mutableStateOf(setOf<String>()) }
    var selectedVersions by remember { mutableStateOf(setOf<String>()) }
    var versionsToDelete by remember { mutableStateOf<Set<String>?>(null) }
    var versionsToRegenerate by remember { mutableStateOf<Set<String>?>(null) }
    BackHandler(selectedVersions.isNotEmpty() || selectedImageKeys.isNotEmpty() || selectedVideoKeys.isNotEmpty()) {
        selectedVersions = emptySet()
        selectedImageKeys = emptySet()
        selectedVideoKeys = emptySet()
    }
    val availablePhotoKeys = remember(state.photos) { state.photos.mapTo(hashSetOf()) { it.cacheKey } }
    val imagesByAlbum = remember(state.managedCacheItems) {
        state.managedCacheItems.groupBy { generatedAlbumKey(it.entry.version) }
    }
    LaunchedEffect(selectedTab, state.selectedGeneratedVersion) {
        selectedImageKeys = emptySet()
        selectedVideoKeys = emptySet()
        selectedVersions = emptySet()
    }
    LaunchedEffect(state.cacheVersions) {
        selectedVersions = selectedVersions.intersect(state.cacheVersions.map { it.version }.toSet())
    }
    LaunchedEffect(state.loading, state.selectedGeneratedVersion, imagesByAlbum) {
        val selected = state.selectedGeneratedVersion
        if (!state.loading && selected != null && selected !in imagesByAlbum) onCloseVersion()
    }
    versionsToDelete?.let { versions ->
        val count = versions.sumOf { imagesByAlbum[it].orEmpty().size }
        AlertDialog(
            onDismissRequest = { versionsToDelete = null },
            containerColor = apple.elevatedSurface,
            shape = RoundedCornerShape(22.dp),
            title = { Text(lang.t("删除 ${versions.size} 个生成相册？", "Delete ${versions.size} generated albums?"), fontWeight = FontWeight.Bold) },
            text = { Text(lang.t("将删除 $count 张生成缓存并取消相关生成任务。系统原图不会删除。", "Delete $count generated images and cancel related jobs. Original photos are kept."), color = apple.labelSecondary) },
            confirmButton = {
                AppleCapsuleButton(
                    text = lang.t("删除", "Delete"),
                    destructive = true,
                    onClick = {
                        onDeleteVersion(versions.toList())
                        selectedVersions = emptySet()
                        versionsToDelete = null
                    },
                )
            },
            dismissButton = { TextButton(onClick = { versionsToDelete = null }) { Text(lang.t("取消", "Cancel")) } },
        )
    }
    DisposableEffect(Unit) {
        onDispose { onGeneratedSelectionChange(null) }
    }
    versionsToRegenerate?.let { versions ->
        val indexes = GeneratedSelectionScope.regenerationIndexes(
            state.photos.map { it.cacheKey },
            state.managedCacheItems.map { it.entry.photoKey to it.entry.version },
            versions,
        )
        AlertDialog(
            onDismissRequest = { versionsToRegenerate = null },
            containerColor = apple.elevatedSurface,
            shape = RoundedCornerShape(22.dp),
            title = { Text(lang.t("重新生成 ${indexes.size} 张图片？", "Regenerate ${indexes.size} images?"), fontWeight = FontWeight.Bold) },
            text = { Text(lang.t("使用当前设置；同一原图只入队一次。完整成功后仅替换选中的旧结果，失败保留。其他历史版本和原图保留，不开启邻图预加载。", "Use current settings once per source. Replace only selected old results after success; keep them on failure. Other versions and originals stay. No neighbor prefetch."), color = apple.labelSecondary) },
            confirmButton = {
                AppleCapsuleButton(
                    text = lang.t("重新生成", "Regenerate"),
                    enabled = indexes.isNotEmpty(),
                    onClick = {
                        onRegenerateImages(versions.flatMap { imagesByAlbum[it].orEmpty() })
                        selectedVersions = emptySet()
                        versionsToRegenerate = null
                    },
                )
            },
            dismissButton = { TextButton(onClick = { versionsToRegenerate = null }) { Text(lang.t("取消", "Cancel")) } },
        )
    }

    val cardSidePad = if (windowMetrics.isTablet) 20.dp else 12.dp
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(apple.groupedBackground)
            .then(if (embedded) Modifier else Modifier.statusBarsPadding())
            .padding(if (embedded) PaddingValues(0.dp) else PaddingValues(16.dp)),
    ) {
        if (!embedded) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppleGlassCircleButton(Icons.Rounded.ChevronLeft, lang.t("返回", "Back"), onBack, size = 36.dp)
                Text(lang.t("生成管理", "Generated Manager"), style = MaterialTheme.typography.titleLarge, color = apple.labelPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            RoundedPillTabs(
                options = listOf("images" to lang.t("图片", "Images"), "videos" to lang.t("视频", "Videos")),
                selected = selectedTab,
                onSelect = onTabChange,
            )
            Spacer(Modifier.height(10.dp))
        }
        LinkedPager(
            selected = selectedTab,
            keys = listOf("images", "videos"),
            onSelected = onTabChange,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            swipeEnabled = selectedImageKeys.isEmpty() && selectedVideoKeys.isEmpty() && selectedVersions.isEmpty(),
        ) { tab ->
            val reportTabsOverlap = onTabsOverlapChanged.takeIf { embedded && tab == selectedTab }
            val gridColumns = StudioLayout.columns(state.generatedColumns, config.screenWidthDp, config.fontScale, tab == "videos" || state.selectedGeneratedVersion == null)
            if (tab == "videos") {
                val jobsByKey = remember(state.videoJobs) { state.videoJobs.associateBy { it.item.cacheKey } }
                val jobKeys = jobsByKey.keys
                val videoIndexes = remember(state.photos, state.videoStates, state.videoEntries, jobKeys) {
                    state.photos.indices.filter { index ->
                        val item = state.photos[index]
                        item.kind == MediaKind.VIDEO &&
                            ((state.videoStates[item.cacheKey] ?: VideoVrState.NORMAL) != VideoVrState.NORMAL ||
                                state.videoEntries.containsKey(item.cacheKey) || item.cacheKey in jobKeys)
                    }
                }
                val videoKeys = remember(state.photos, videoIndexes) { videoIndexes.map { state.photos[it].cacheKey } }
                LaunchedEffect(videoKeys) { selectedVideoKeys = selectedVideoKeys.intersect(videoKeys.toSet()) }
                val selectedIndexes = remember(videoKeys, videoIndexes, selectedVideoKeys) {
                    videoIndexes.filterIndexed { row, _ -> videoKeys[row] in selectedVideoKeys }
                }
                val videoActions = remember(state.photos, state.videoStates, videoIndexes, selectedIndexes, onStartVideos, onPauseVideos) {
                    if (selectedIndexes.isEmpty()) null else {
                        val states = videoIndexes.associateWith { state.videoStates[state.photos[it].cacheKey] ?: VideoVrState.NORMAL }
                        VideoSelectionActions(
                            onStartSelected = { onStartVideos(selectedIndexes) },
                            onPauseSelected = { onPauseVideos(selectedIndexes) },
                            onStartAll = { onStartVideos(videoIndexes) },
                            onPauseAll = { onPauseVideos(videoIndexes) },
                            selectedStartCount = selectedIndexes.count { VideoCommandPolicy.canStart(states.getValue(it)) },
                            selectedPauseCount = selectedIndexes.count { VideoCommandPolicy.canPause(states.getValue(it)) },
                            allStartCount = states.values.count(VideoCommandPolicy::canStart),
                            allPauseCount = states.values.count(VideoCommandPolicy::canPause),
                        )
                    }
                }
                LaunchedEffect(embedded, selectedTab, selectedVideoKeys, selectedIndexes, videoKeys, videoActions) {
                    if (embedded && tab == selectedTab && selectedVideoKeys.isNotEmpty()) {
                        onGeneratedSelectionChange(
                            GeneratedSelectionActionsState(
                                count = selectedVideoKeys.size,
                                onClear = { selectedVideoKeys = emptySet() },
                                onSelectAll = { selectedVideoKeys = videoKeys.toSet() },
                                onSave = { onSaveVideos(selectedIndexes); selectedVideoKeys = emptySet() },
                                onRegenerate = { onRegenerateVideos(selectedIndexes); selectedVideoKeys = emptySet() },
                                onDelete = { onDeleteVideos(selectedIndexes); selectedVideoKeys = emptySet() },
                                videoActions = videoActions,
                            ),
                        )
                    } else if (embedded && tab == selectedTab) {
                        onGeneratedSelectionChange(null)
                    }
                }
                if (selectedVideoKeys.isNotEmpty() && !embedded) {
                    ManageSelectionActions(
                        count = selectedVideoKeys.size,
                        lang = lang,
                        onClear = { selectedVideoKeys = emptySet() },
                        onSelectAll = { selectedVideoKeys = videoKeys.toSet() },
                        onSave = { onSaveVideos(selectedIndexes); selectedVideoKeys = emptySet() },
                        onRegenerate = { onRegenerateVideos(selectedIndexes); selectedVideoKeys = emptySet() },
                        imageReplacement = false,
                        videoActions = videoActions,
                        onDelete = { onDeleteVideos(selectedIndexes); selectedVideoKeys = emptySet() },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (videoIndexes.isEmpty()) {
                    StudioEmptyState(
                        lang.t("还没有视频任务", "No video jobs yet"),
                        lang.t("从全部或相册打开视频，手动加入 VR 队列。视频不会自动加入图片预加载。", "Open a video from All or Albums and add it to the VR queue. Videos are never added by image prefetch."),
                        Modifier.padding(top = contentTopPadding, bottom = contentBottomPadding),
                        icon = Icons.Outlined.VideoLibrary,
                    )
                } else {
                    val videoGridState = rememberGalleryGridState(viewports, GalleryGridScope.GENERATED_VIDEOS, videoKeys)
                    val videoScope = rememberCoroutineScope()
                    val videoScrolled by remember(videoGridState) {
                        derivedStateOf { videoGridState.firstVisibleItemIndex > 0 || videoGridState.firstVisibleItemScrollOffset > 20 }
                    }
                    val videoDeep by remember(videoGridState) {
                        derivedStateOf { videoGridState.firstVisibleItemIndex > 12 }
                    }
                    LaunchedEffect(embedded, selectedTab, videoScrolled, videoDeep) {
                        if (embedded && tab == selectedTab) {
                            onScrollStateChanged(videoScrolled, videoDeep) { videoScope.launch { videoGridState.smoothScrollToTop() } }
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            state = videoGridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .reportContentBehindTabs(videoGridState, tabsBounds, reportTabsOverlap)
                                .background(apple.groupedBackground)
                                .discreteColumnPinch(columns = gridColumns, onColumns = onSetGeneratedColumns)
                                .generatedGridDragSelection(
                                    gridState = videoGridState,
                                    selectionMode = selectedVideoKeys.isNotEmpty(),
                                    contentStartPadding = cardSidePad,
                                ) { gridIndex ->
                                    videoKeys.getOrNull(gridIndex)?.let { key ->
                                        if (key in availablePhotoKeys) selectedVideoKeys = selectedVideoKeys + key
                                    }
                                },
                            contentPadding = PaddingValues(
                                start = cardSidePad,
                                top = if (embedded) contentTopPadding + 4.dp else 8.dp,
                                end = cardSidePad,
                                bottom = contentBottomPadding,
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(videoIndexes, key = { state.photos[it].cacheKey }) { index ->
                                val item = state.photos[index]
                                val videoState = state.videoStates[item.cacheKey] ?: VideoVrState.NORMAL
                                val job = jobsByKey[item.cacheKey]
                                val isSelected = item.cacheKey in selectedVideoKeys
                                val cardShape = RoundedCornerShape(14.dp)
                                Surface(
                                    shape = cardShape,
                                    color = apple.cardBackground,
                                    border = if (isSelected) BorderStroke(2.dp, apple.systemBlue) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(cardShape)
                                        .clickable {
                                            if (selectedVideoKeys.isNotEmpty()) {
                                                selectedVideoKeys = if (isSelected) selectedVideoKeys - item.cacheKey else selectedVideoKeys + item.cacheKey
                                            } else onOpenGeneratedVideo(index)
                                        },
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Box(Modifier.fillMaxWidth().aspectRatio(1.18f)) {
                                            AsyncMediaThumbnail(
                                                MediaKind.VIDEO,
                                                state.videoEntries[item.cacheKey]?.let { Uri.fromFile(File(it.outputPath)) } ?: item.uri,
                                                420,
                                                ContentScale.Crop,
                                                Modifier.fillMaxSize(),
                                            )
                                            if (isSelected) SelectionBadge()
                                            StatusBadge(videoState.shortLabel(lang), fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart))
                                        }
                                        Column(
                                            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = item.displayName,
                                                fontSize = 13.sp,
                                                lineHeight = 17.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = apple.labelPrimary,
                                                minLines = 2,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val statusLine = VideoProgress.frames(videoState, job?.currentFrame, job?.totalFrames)
                                                ?.let { "${VideoProgress.percent(videoState, job?.progress)}% · $it" }
                                                ?: videoState.label(lang)
                                            Text(
                                                text = statusLine,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (videoState == VideoVrState.FAILED) apple.systemRed else apple.labelSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (videoState == VideoVrState.GENERATING || videoState == VideoVrState.QUEUED) {
                                                LinearProgressIndicator(
                                                    progress = { (job?.progress ?: 0f).coerceIn(0f, 1f) },
                                                    modifier = Modifier.fillMaxWidth().height(3.5.dp),
                                                    color = apple.systemBlue,
                                                    trackColor = apple.fillSecondary,
                                                    strokeCap = StrokeCap.Round,
                                                    drawStopIndicator = {},
                                                )
                                            } else if (videoState == VideoVrState.FAILED && !job?.error.isNullOrBlank()) {
                                                Text(
                                                    text = job?.error.orEmpty(),
                                                    color = apple.systemRed,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        GridScrollbar(videoGridState, Modifier.align(Alignment.CenterEnd).padding(top = contentTopPadding, bottom = contentBottomPadding))
                        if (!embedded) {
                            GridToTopButton(
                                videoGridState,
                                lang,
                                Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = contentBottomPadding),
                            )
                        }
                    }
                }
            } else {
                if (state.cacheVersions.isEmpty()) {
                    LaunchedEffect(embedded, selectedTab) {
                        if (embedded && tab == selectedTab) onGeneratedSelectionChange(null)
                    }
                    StudioEmptyState(
                        lang.t("创作结果会出现在这里", "Your creations will appear here"),
                        lang.t("从全部或相册打开图片会自动开启 VR。结果按参数版本保存，长按可多选；不会改写系统原图。", "Opening a photo from All or Albums enables VR; use its Viewer switch to turn it off. Results are kept by parameter version; long press to select. Originals are unchanged."),
                        Modifier.padding(top = contentTopPadding, bottom = contentBottomPadding),
                        icon = Icons.Outlined.AutoAwesome,
                    )
                } else {
                    val selectedVersion = state.selectedGeneratedVersion?.takeIf { it in imagesByAlbum }
                    if (selectedVersion == null) {
                        LaunchedEffect(embedded, selectedTab, selectedVersions, state.cacheVersions) {
                            if (embedded && tab == selectedTab) onGeneratedSelectionChange(
                                selectedVersions.takeIf { it.isNotEmpty() }?.let { versions ->
                                    GeneratedSelectionActionsState(
                                        count = versions.size,
                                        onClear = { selectedVersions = emptySet() },
                                        onSelectAll = { selectedVersions = state.cacheVersions.mapTo(linkedSetOf()) { it.version } },
                                        onSave = null,
                                        onRegenerate = { versionsToRegenerate = versions },
                                        onGenerate = { onGenerateImages(GeneratedSelectionScope.regenerationIndexes(state.photos.map { it.cacheKey }, state.managedCacheItems.map { it.entry.photoKey to it.entry.version }, versions)); selectedVersions = emptySet() },
                                        imageReplacement = true,
                                        onDelete = { versionsToDelete = versions },
                                    )
                                },
                            )
                        }
                        if (!embedded && selectedVersions.isNotEmpty()) {
                            ManageSelectionActions(
                                selectedVersions.size,
                                lang,
                                onClear = { selectedVersions = emptySet() },
                                onSelectAll = { selectedVersions = state.cacheVersions.mapTo(linkedSetOf()) { it.version } },
                                onSave = null,
                                onRegenerate = { versionsToRegenerate = selectedVersions },
                                onDelete = { versionsToDelete = selectedVersions },
                                confirmChanges = false,
                                onGenerate = { onGenerateImages(GeneratedSelectionScope.regenerationIndexes(state.photos.map { it.cacheKey }, state.managedCacheItems.map { it.entry.photoKey to it.entry.version }, selectedVersions)); selectedVersions = emptySet() },
                            )
                        }
                        val versionKeys = remember(state.cacheVersions) { state.cacheVersions.map { it.version } }
                        val versionGridState = rememberGalleryGridState(viewports, GalleryGridScope.GENERATED_VERSIONS, versionKeys)
                        val versionScope = rememberCoroutineScope()
                        val versionScrolled by remember(versionGridState) {
                            derivedStateOf { versionGridState.firstVisibleItemIndex > 0 || versionGridState.firstVisibleItemScrollOffset > 20 }
                        }
                        val versionDeep by remember(versionGridState) {
                            derivedStateOf { versionGridState.firstVisibleItemIndex > 12 }
                        }
                        LaunchedEffect(embedded, selectedTab, versionScrolled, versionDeep) {
                            if (embedded && tab == selectedTab) {
                                onScrollStateChanged(versionScrolled, versionDeep) { versionScope.launch { versionGridState.smoothScrollToTop() } }
                            }
                        }
                        Box(Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(gridColumns),
                                state = versionGridState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .reportContentBehindTabs(versionGridState, tabsBounds, reportTabsOverlap)
                                    .background(apple.groupedBackground)
                                    .discreteColumnPinch(columns = gridColumns, onColumns = onSetGeneratedColumns)
                                    .generatedGridDragSelection(
                                        gridState = versionGridState,
                                        selectionMode = selectedVersions.isNotEmpty(),
                                        contentStartPadding = cardSidePad,
                                    ) { gridIndex ->
                                        state.cacheVersions.getOrNull(gridIndex)?.let { selectedVersions += it.version }
                                    },
                                contentPadding = PaddingValues(
                                    start = cardSidePad,
                                    top = if (embedded) contentTopPadding + 4.dp else 10.dp,
                                    end = cardSidePad,
                                    bottom = contentBottomPadding,
                                ),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.cacheVersions, key = { it.version }) { summary ->
                                    val cover = imagesByAlbum[summary.version]?.maxByOrNull { it.entry.createdAt }
                                    GeneratedVersionTile(
                                        summary = summary,
                                        cover = cover,
                                        lang = lang,
                                        selected = summary.version in selectedVersions,
                                        onOpen = {
                                            if (selectedVersions.isEmpty()) onOpenVersion(summary.version)
                                            else selectedVersions = if (summary.version in selectedVersions) selectedVersions - summary.version else selectedVersions + summary.version
                                        },
                                    )
                                }
                            }
                            GridScrollbar(versionGridState, Modifier.align(Alignment.CenterEnd).padding(top = contentTopPadding, bottom = contentBottomPadding))
                            if (!embedded) {
                                GridToTopButton(
                                    versionGridState,
                                    lang,
                                    Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = contentBottomPadding),
                                )
                            }
                        }
                    } else {
                        val itemsInVersion = imagesByAlbum[selectedVersion].orEmpty()
                        LaunchedEffect(itemsInVersion) {
                            selectedImageKeys = selectedImageKeys.intersect(itemsInVersion.mapTo(hashSetOf()) { "${it.entry.photoKey}|${it.entry.version}" })
                        }
                        val imageKeys = remember(itemsInVersion) { itemsInVersion.map { it.gridKey } }
                        val imageGridState = rememberGalleryGridState(viewports, GalleryGridScope.generatedVersion(selectedVersion), imageKeys)
                        val imageScope = rememberCoroutineScope()
                        val imageScrolled by remember(imageGridState) {
                            derivedStateOf { imageGridState.firstVisibleItemIndex > 0 || imageGridState.firstVisibleItemScrollOffset > 20 }
                        }
                        val imageDeep by remember(imageGridState) {
                            derivedStateOf { imageGridState.firstVisibleItemIndex > 18 }
                        }
                        LaunchedEffect(embedded, selectedTab, imageScrolled, imageDeep) {
                            if (embedded && tab == selectedTab) {
                                onScrollStateChanged(imageScrolled, imageDeep) { imageScope.launch { imageGridState.smoothScrollToTop() } }
                            }
                        }
                        Column(Modifier.fillMaxSize()) {
                            if (!embedded) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AppleGlassCircleButton(Icons.Rounded.ChevronLeft, lang.t("返回", "Back"), onCloseVersion, size = 36.dp)
                                    Text(
                                        text = selectedVersion,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = apple.labelPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    AppleCapsuleButton(
                                        text = lang.t("删除", "Delete"),
                                        onClick = { versionsToDelete = setOf(selectedVersion) },
                                        destructive = true,
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                            val selectedItems = itemsInVersion.filter { "${it.entry.photoKey}|${it.entry.version}" in selectedImageKeys }
                            val selectedImageIndexes = selectedItems.mapNotNull { item -> state.photos.indexOfFirst { it.cacheKey == item.photoItem.cacheKey }.takeIf { it >= 0 } }.distinct()
                            LaunchedEffect(embedded, selectedTab, selectedImageKeys, selectedItems, selectedImageIndexes, itemsInVersion) {
                                if (embedded && tab == selectedTab && selectedImageKeys.isNotEmpty()) {
                                    onGeneratedSelectionChange(
                                        GeneratedSelectionActionsState(
                                            count = selectedImageKeys.size,
                                            onClear = { selectedImageKeys = emptySet() },
                                            onSelectAll = { selectedImageKeys = itemsInVersion.mapTo(linkedSetOf()) { "${it.entry.photoKey}|${it.entry.version}" } },
                                            onSave = { onSaveImages(selectedItems); selectedImageKeys = emptySet() },
                                            onRegenerate = { onRegenerateImages(selectedItems); selectedImageKeys = emptySet() },
                                            onGenerate = { onGenerateImages(selectedImageIndexes); selectedImageKeys = emptySet() },
                                            imageReplacement = true,
                                            onDelete = { onDeleteImages(selectedItems); selectedImageKeys = emptySet() },
                                        ),
                                    )
                                } else if (embedded && tab == selectedTab) {
                                    onGeneratedSelectionChange(null)
                                }
                            }
                            if (selectedImageKeys.isNotEmpty() && !embedded) {
                                ManageSelectionActions(
                                    count = selectedImageKeys.size,
                                    lang = lang,
                                    onClear = { selectedImageKeys = emptySet() },
                                    onSelectAll = { selectedImageKeys = itemsInVersion.mapTo(linkedSetOf()) { "${it.entry.photoKey}|${it.entry.version}" } },
                                    onSave = { onSaveImages(selectedItems); selectedImageKeys = emptySet() },
                                    onRegenerate = { onRegenerateImages(selectedItems); selectedImageKeys = emptySet() },
                                    onGenerate = { onGenerateImages(selectedImageIndexes); selectedImageKeys = emptySet() },
                                    imageReplacement = true,
                                    onDelete = { onDeleteImages(selectedItems); selectedImageKeys = emptySet() },
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Box(Modifier.fillMaxSize()) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(gridColumns),
                                    state = imageGridState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .reportContentBehindTabs(imageGridState, tabsBounds, reportTabsOverlap)
                                        .background(apple.groupedBackground)
                                        .discreteColumnPinch(columns = gridColumns, onColumns = onSetGeneratedColumns)
                                        .generatedGridDragSelection(
                                            gridState = imageGridState,
                                            selectionMode = selectedImageKeys.isNotEmpty(),
                                            contentStartPadding = 8.dp,
                                        ) { gridIndex ->
                                            itemsInVersion.getOrNull(gridIndex)?.let { item ->
                                                if (item.photoItem.cacheKey in availablePhotoKeys) {
                                                    selectedImageKeys = selectedImageKeys + "${item.entry.photoKey}|${item.entry.version}"
                                                }
                                            }
                                        },
                                    contentPadding = PaddingValues(
                                        start = 8.dp,
                                        top = if (embedded) contentTopPadding else 8.dp,
                                        end = 8.dp,
                                        bottom = contentBottomPadding,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    items(itemsInVersion, key = { it.gridKey }) { item ->
                                        val index = state.photos.indexOfFirst { it.cacheKey == item.photoItem.cacheKey }
                                        val selectionKey = "${item.entry.photoKey}|${item.entry.version}"
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (apple.isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA))
                                                .clickable(
                                                    enabled = index >= 0,
                                                    onClick = {
                                                        if (selectedImageKeys.isNotEmpty()) {
                                                            selectedImageKeys = if (selectionKey in selectedImageKeys) selectedImageKeys - selectionKey else selectedImageKeys + selectionKey
                                                        } else {
                                                            onOpenGenerated(index, item.entry)
                                                        }
                                                    },
                                                ),
                                        ) {
                                            GeneratedSbsThumbnail(item.entry, 360, ContentScale.Crop, Modifier.fillMaxSize(), lang)
                                            if (selectionKey in selectedImageKeys) SelectionBadge()
                                        }
                                    }
                                }
                                GridScrollbar(imageGridState, Modifier.align(Alignment.CenterEnd).padding(top = contentTopPadding, bottom = contentBottomPadding))
                                if (!embedded) {
                                    GridToTopButton(
                                        imageGridState,
                                        lang,
                                        Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = contentBottomPadding),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun cacheVersionTitle(version: String, lang: AppLanguage): String {
    val mode = when {
        version.contains("_depthSSSMainV11_") -> lang.t("ComfyUI · 亚像素", "ComfyUI · subpixel")
        version.contains("_depthSSSMainV10_") -> lang.t("ComfyUI · 原样兼容", "ComfyUI · compatible")
        version.contains("_depthSSS22V9_") -> lang.t("参考 · 平滑轮廓", "Reference · smooth contours")
        version.contains("_depthSSS22V8_") -> lang.t("参考 · 1.3.00", "Reference · 1.3.00")
        version.contains("_depthV7_") -> lang.t("居中平滑", "Centered")
        else -> lang.t("旧版兼容", "Legacy")
    }
    val scale = Regex("_s([0-9.]+)_").find(version)?.groupValues?.get(1) ?: "—"
    val edge = Regex("_m([0-9]+)_").find(version)?.groupValues?.get(1) ?: "—"
    val upscale = if (version.contains("_du1_")) lang.t(" · 深度放大", " · Upsampled depth") else ""
    val strength = if (version.contains("_eq1050")) lang.t("默认 · V10 50", "Default · V10 50") else lang.t("强度 $scale", "Scale $scale")
    val take = Regex("_take([A-Za-z0-9-]+)_").find(version)?.groupValues?.get(1)?.take(4)?.let { lang.t(" · 副本 $it", " · Copy $it") }.orEmpty()
    return "$mode · $strength\n${edge}px$upscale$take"
}

@Composable
private fun GeneratedVersionTile(
    summary: CacheVersionSummary,
    cover: ManagedCacheItem?,
    lang: AppLanguage,
    selected: Boolean = false,
    onOpen: () -> Unit,
) {
    val apple = AppleTheme.colors
    val cardShape = RoundedCornerShape(14.dp)
    Surface(
        shape = cardShape,
        color = apple.cardBackground,
        border = if (selected) BorderStroke(2.dp, apple.systemBlue) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(if (apple.isDark) Color(0xFF141416) else Color(0xFFE5E5EA)),
            ) {
                if (cover != null) {
                    GeneratedSbsThumbnail(cover.entry, 420, ContentScale.Crop, Modifier.fillMaxSize(), lang)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(lang.t("暂无预览", "No preview"), color = apple.labelSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    text = lang.t("${summary.count} 张", "${summary.count} images"),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xB3141418))
                        .padding(horizontal = 8.dp, vertical = 2.5.dp),
                )
                if (selected) SelectionBadge()
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = cacheVersionTitle(summary.version, lang),
                    color = apple.labelPrimary,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = remember(summary.version, lang) { generatedAlbumParameterDetails(summary.version, lang) }
                Text(
                    text = details.ifEmpty { "${(summary.bytes / 1024f / 1024f).roundToInt()} MB" },
                    style = MaterialTheme.typography.bodySmall,
                    color = apple.labelSecondary,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GeneratedSbsThumbnail(entry: VrCacheEntry, maxSide: Int, contentScale: ContentScale, modifier: Modifier = Modifier, lang: AppLanguage) {
    val apple = AppleTheme.colors
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
        failed -> Box(modifier.background(apple.fillSecondary), contentAlignment = Alignment.Center) {
            Text(lang.t("暂无预览", "No preview"), color = apple.labelSecondary, style = MaterialTheme.typography.labelSmall)
        }
        else -> Box(modifier.background(apple.fillSecondary), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = apple.systemBlue)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdditionalImagesDialog(
    state: UiState,
    onEdit: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val apple = AppleTheme.colors
    val lang = state.settings.language
    val version = state.settings.toParams().cacheVersion()
    val ready = remember(state.additionalImageSources, state.managedCacheItems, version) {
        additionalImageSourceKeys(state.additionalImageSources, version, state.managedCacheItems.map { it.resultKey })
    }
    val alreadyHave = state.additionalImageSources.size - ready.size
    val targetExists = state.cacheVersions.any { it.version == version }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = apple.elevatedSurface,
        shape = RoundedCornerShape(22.dp),
        title = { Text(lang.t("使用新参数生成", "Generate with new parameters"), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = apple.fillTertiary,
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(cacheVersionTitle(version, lang), style = MaterialTheme.typography.titleSmall, color = apple.labelPrimary)
                        val details = remember(version, lang) { generatedAlbumParameterDetails(version, lang) }
                        if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = apple.labelSecondary)
                    }
                }
                Text(
                    when {
                        ready.isEmpty() -> lang.t("所选原图已有这套参数的结果。调整参数后可生成到另一个相册。", "The selected originals already have results with these parameters. Adjust them to generate into a different album.")
                        targetExists -> lang.t("生成 ${ready.size} 张，归入已有的同参数相册。", "Generate ${ready.size} images into the existing album for these parameters.")
                        else -> lang.t("生成 ${ready.size} 张，首张完成后会显示新相册。", "Generate ${ready.size} images. The new album appears when the first result is ready.")
                    },
                    color = apple.labelSecondary,
                )
                if (ready.isNotEmpty() && alreadyHave > 0) {
                    Text(lang.t("已有结果 $alreadyHave 张，直接跳过。", "Skipping $alreadyHave images with existing results."), color = apple.labelSecondary, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onEdit) { Text(lang.t("调整参数", "Adjust parameters"), color = apple.systemBlue, fontWeight = FontWeight.SemiBold) }
            }
        },
        confirmButton = {
            AppleCapsuleButton(text = lang.t("开始生成", "Start generation"), enabled = ready.isNotEmpty(), onClick = onConfirm)
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(lang.t("取消", "Cancel")) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ManageSelectionActions(
    count: Int,
    lang: AppLanguage,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onSave: (() -> Unit)?,
    onRegenerate: (() -> Unit)?,
    onDelete: () -> Unit,
    confirmChanges: Boolean = true,
    onGenerate: (() -> Unit)? = null,
    imageReplacement: Boolean = true,
    videoActions: VideoSelectionActions? = null,
) {
    val apple = AppleTheme.colors
    var pending by remember { mutableStateOf<String?>(null) }
    val actionArea = if (videoActions != null) {
        Modifier
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.4f).dp)
            .verticalScroll(rememberScrollState())
    } else Modifier
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                lang.t("当前列表 · 已选 $count 项", "Current list · $count selected"),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = apple.labelPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            SelectionActionIcon(Icons.Rounded.Close, lang.t("取消选择", "Cancel selection"), onClear)
        }
        AppleDivider()
        FlowRow(modifier = actionArea, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onSelectAll) { Text(lang.t("全选", "Select all"), color = apple.systemBlue, fontWeight = FontWeight.SemiBold) }
            videoActions?.let { actions ->
                TextButton(onClick = actions.onStartSelected, enabled = actions.selectedStartCount > 0) {
                    Text(lang.t("开始所选 ${actions.selectedStartCount} 项", "Start ${actions.selectedStartCount} selected"))
                }
                TextButton(onClick = actions.onPauseSelected, enabled = actions.selectedPauseCount > 0) {
                    Text(lang.t("暂停所选 ${actions.selectedPauseCount} 项", "Pause ${actions.selectedPauseCount} selected"))
                }
                OutlinedButton(onClick = actions.onStartAll, enabled = actions.allStartCount > 0, shape = CircleShape) {
                    Text(lang.t("全部开始 (${actions.allStartCount})", "Start all (${actions.allStartCount})"))
                }
                OutlinedButton(onClick = actions.onPauseAll, enabled = actions.allPauseCount > 0, shape = CircleShape) {
                    Text(lang.t("全部暂停 (${actions.allPauseCount})", "Pause all (${actions.allPauseCount})"))
                }
            }
            onSave?.let {
                TextButton(onClick = it) {
                    Text(if (imageReplacement) lang.t("保存无损 PNG", "Save lossless PNG") else lang.t("保存副本", "Save copies"), color = apple.systemBlue, fontWeight = FontWeight.SemiBold)
                }
            }
            onGenerate?.let { action ->
                TextButton(onClick = action) {
                    Text(lang.t("使用新参数生成", "Generate with new parameters"), color = apple.systemBlue)
                }
            }
            onRegenerate?.let { action ->
                TextButton(onClick = { if (confirmChanges) pending = "regenerate" else action() }) {
                    Text(lang.t("重新生成", "Regenerate"), color = apple.systemBlue)
                }
            }
            TextButton(onClick = { if (confirmChanges) pending = "delete" else onDelete() }) {
                Text(lang.t("删除结果", "Delete results"), color = apple.systemRed, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    pending?.let { action ->
        val deleting = action == "delete"
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = apple.elevatedSurface,
            shape = RoundedCornerShape(22.dp),
            title = { Text(if (deleting) lang.t("删除所选 $count 项结果？", "Delete $count selected results?") else lang.t("重新生成所选 $count 项？", "Regenerate $count selected items?"), fontWeight = FontWeight.Bold) },
            text = { Text(if (deleting) lang.t("仅删除所选生成结果并取消相关任务，不删除系统原图。", "Only selected generated results and related jobs are removed. Original media is kept.") else if (!imageReplacement) lang.t("按当前设置清理并重新生成所选视频缓存；失败需重试。系统原视频不修改。", "Clear and regenerate selected video cache with current settings; retry on failure. Original videos are unchanged.") else lang.t("使用当前设置生成成功后，仅替换选中的旧结果；失败保留。其他历史版本和原图保留，不启动邻图预加载。", "Replace only the selected old results after success. Keep them on failure. Other historical versions and originals stay; no neighbor prefetch."), color = apple.labelSecondary) },
            confirmButton = {
                AppleCapsuleButton(
                    text = lang.t("确认", "Confirm"),
                    destructive = deleting,
                    onClick = { pending = null; if (deleting) onDelete() else onRegenerate?.invoke() },
                )
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(lang.t("取消", "Cancel")) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val apple = AppleTheme.colors
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(apple.fillSecondary)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = apple.iconPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SelectionBadge() {
    AppleSelectionOverlay()
}
