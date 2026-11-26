package com.local.parallelvrgallerypro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.opengl.EGLConfig
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import android.provider.Settings
import android.util.Size
import android.view.Surface
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.VideoView
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Settings
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.roundToInt

private data class ViewerPage(
    val sourceIndex: Int,
    val photo: PhotoItem,
    val result: VrCacheEntry? = null,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun ViewerScreen(
    state: UiState,
    startIndex: Int,
    onClose: () -> Unit,
    onIndexChanged: (Int, String?) -> Unit,
    onVr: (Int) -> Unit,
    onRetry: (Int) -> Unit,
    onOpenDebug: (Int, String?) -> Unit,
) {
    val lang = state.settings.language
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
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

    val generatedResults = state.viewerGeneratedResults
    val viewerItems = if (generatedResults != null) {
        remember(state.photos, generatedResults, state.managedCacheItems) {
            val photoIndexes = state.photos.withIndex().associate { it.value.cacheKey to it.index }
            val results = state.managedCacheItems.associateBy { it.resultKey }
            generatedResults.mapNotNull { key ->
                val index = photoIndexes[key.first] ?: return@mapNotNull null
                val result = results[key] ?: return@mapNotNull null
                ViewerPage(index, state.photos[index], result.entry)
            }
        }
    } else {
        remember(state.photos, state.viewerScopeKeys, state.viewerScopeOrderedKeys) {
            viewerScopeIndices(state).map { ViewerPage(it, state.photos[it]) }
        }
    }
    if (viewerItems.isEmpty()) {
        StudioEmptyState(lang.t("此浏览范围已没有结果", "No results in this viewer scope"), lang.t("结果可能已被删除。返回列表重新选择，浏览范围不会回落到全部媒体。", "Results may have been deleted. Return to the list; this scope never falls back to all media.")) { Button(onClick = onClose) { Text(lang.t("返回", "Back")) } }
        return
    }
    val initialPage = viewerItems.indexOfFirst { it.sourceIndex == startIndex && it.result?.version == state.viewerResultVersion }
        .coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { viewerItems.size }
    val viewerFlingBehavior = PagerDefaults.flingBehavior(state = pagerState)
    val viewerCacheSignature = remember(viewerItems) { viewerItems.map { it.photo.cacheKey to it.result?.version } }
    val metrics = context.resources.displayMetrics
    val previewSide = (max(metrics.widthPixels, metrics.heightPixels) / 2).coerceIn(960, 1600)
    val viewerBitmapCache = remember(viewerCacheSignature, previewSide) { ViewerImageLoader(context, previewSide) }
    DisposableEffect(viewerBitmapCache) { onDispose { viewerBitmapCache.close() } }
    LaunchedEffect(viewerItems, pagerState.settledPage, state.vrMode, generatedViewer, state.entries) {
        val preloadPages = listOf(
            pagerState.settledPage,
            pagerState.settledPage + 1,
            pagerState.settledPage - 1,
            pagerState.settledPage + 2,
            pagerState.settledPage - 2,
        )
        val nearbyTargets = preloadPages
            .mapNotNull(viewerItems::getOrNull)
            .filter { it.photo.kind == MediaKind.IMAGE }
            .flatMap { item ->
                val entry = item.result ?: state.entries[item.photo.cacheKey]
                if ((state.vrMode || generatedViewer) && entry != null) {
                    if (entry.hasSplitEyes) {
                        listOf(
                            Uri.fromFile(File(entry.leftPath)) to previewSide,
                            Uri.fromFile(File(entry.rightPath)) to previewSide,
                        )
                    } else {
                        listOf(Uri.fromFile(File(entry.outputPath)) to previewSide)
                    }
                } else {
                    listOf(item.photo.uri to previewSide)
                }
            }
            .distinct()
        nearbyTargets.forEach { (uri, maxSide) ->
            viewerBitmapCache.load(uri, maxSide)
        }
    }
    val settledItem = viewerItems.getOrNull(pagerState.settledPage)
    LaunchedEffect(settledItem?.sourceIndex, settledItem?.result?.version) {
        settledItem?.let { onIndexChanged(it.sourceIndex, it.result?.version) }
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsHeld by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible, state.vrMode, pagerState.currentPage, controlsHeld) {
        if (state.vrMode && controlsVisible && !controlsHeld) {
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
            beyondViewportPageCount = 1,
            flingBehavior = viewerFlingBehavior,
            key = { viewerCacheSignature[it] },
        ) { page ->
            val (sourceIndex, photo, result) = viewerItems.getOrNull(page) ?: return@HorizontalPager
            if (photo.kind == MediaKind.VIDEO) {
                val generated = state.videoEntries[photo.cacheKey]
                val job = state.videoJobs.firstOrNull { it.item.cacheKey == photo.cacheKey }
                val videoState = state.videoStates[photo.cacheKey] ?: VideoVrState.NORMAL
                val activePage = resumed && page == pagerState.currentPage
                val playerContent: @Composable () -> Unit = {
                    VideoPlayer(
                        uri = generated?.let { Uri.fromFile(File(it.outputPath)) } ?: photo.uri,
                        modifier = Modifier.fillMaxSize(),
                        active = activePage,
                        sbsMode = generated != null,
                        controlsVisible = controlsVisible,
                        stateLine = listOfNotNull(videoState.label(lang), VideoProgress.frames(videoState, job?.currentFrame, job?.totalFrames)).joinToString("  "),
                        metricsLine = "out ${job?.currentFrameMs ?: 0}ms · avg ${job?.avgFrameMs ?: 0}ms",
                        onSingleTap = { controlsVisible = !controlsVisible },
                    )
                }
                if (generated != null) {
                    key(generated.outputPath) { playerContent() }
                } else {
                    playerContent()
                }
            } else {
                val entry = result ?: state.entries[photo.cacheKey]
                val vrState = state.states[photo.cacheKey] ?: VrState.NORMAL
                if ((state.vrMode || generatedViewer) && entry != null) {
                    SyncStereoEntryImage(entry, viewerBitmapCache, Modifier.fillMaxSize()) {
                        controlsVisible = !controlsVisible
                    }
                } else {
                    AsyncBitmapImage(
                        photo.uri,
                        previewSide,
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
        if (controlsVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                LiquidGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                controlsHeld = true
                                try { while (awaitPointerEvent(PointerEventPass.Final).changes.any { it.pressed }) { /* children handle the action */ } }
                                finally { controlsHeld = false }
                            }
                        },
                    shape = RoundedCornerShape(24.dp),
                    darkHud = true,
                    shadowElevation = 12.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Rounded.Close, lang.t("返回", "Back"), tint = androidx.compose.ui.graphics.Color.White)
                        }
                        Spacer(Modifier.width(6.dp))
                        val currentPage = viewerItems.getOrNull(pagerState.currentPage)
                        val currentIndex = currentPage?.sourceIndex ?: startIndex
                        val currentItem = currentPage?.photo
                        val currentVersion = currentPage?.result?.version
                        Text(
                            text = currentItem?.displayName.orEmpty(),
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).combinedClickable(onClick = {}, onLongClick = { onOpenDebug(currentIndex, currentVersion) }),
                        )
                        Spacer(Modifier.width(8.dp))
                        val currentVideoState = currentItem?.takeIf { it.kind == MediaKind.VIDEO }?.let { state.videoStates[it.cacheKey] ?: VideoVrState.NORMAL }
                        val vrButtonEnabled = if (currentVideoState != null) {
                            !generatedViewer || currentVideoState != VideoVrState.READY
                        } else {
                            !generatedViewer
                        }
                        val vrLabel = if (currentItem?.kind == MediaKind.VIDEO) {
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
                        }
                        AppleCapsuleButton(
                            text = vrLabel,
                            onClick = { onVr(currentIndex) },
                            enabled = vrButtonEnabled,
                            primary = true,
                            darkHud = true,
                        )
                        Spacer(Modifier.width(6.dp))
                        AppleCapsuleButton(
                            text = lang.t("调试", "Debug"),
                            onClick = { onOpenDebug(currentIndex, currentVersion) },
                            primary = false,
                            darkHud = true,
                        )
                    }
                }
            }
            val currentPage = viewerItems.getOrNull(pagerState.currentPage)
            val current = currentPage?.photo
            if (!generatedViewer && state.vrMode && current?.kind != MediaKind.VIDEO) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LiquidGlassSurface(
                        modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        darkHud = true,
                        shadowElevation = 10.dp,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (currentPage != null) {
                                Text(
                                    text = imageLoadedLine(state, currentPage.sourceIndex, lang),
                                    color = androidx.compose.ui.graphics.Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = imageQueueLine(state, currentPage.sourceIndex, lang),
                                    color = androidx.compose.ui.graphics.Color(0xD6FFFFFF),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                val recentLines = imageRecentGenerationLines(state, lang)
                                recentLines.take(2).forEach { line ->
                                    Text(
                                        text = line,
                                        color = androidx.compose.ui.graphics.Color(0xAAFFFFFF),
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
        }
    }
}

@Composable
private fun StatusOverlay(state: VrState, lang: AppLanguage, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x4D000000)),
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(shape = RoundedCornerShape(22.dp), darkHud = true, shadowElevation = 12.dp) {
            Column(
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state == VrState.GENERATING || state == VrState.QUEUED) {
                    CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.8.dp)
                    Text(state.label(lang), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                } else if (state == VrState.FAILED) {
                    Text(lang.t("生成失败", "Generation failed"), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    AppleCapsuleButton(text = lang.t("重试", "Retry"), onClick = onRetry, darkHud = true)
                }
            }
        }
    }
}


@Composable
private fun SyncStereoEntryImage(
    entry: VrCacheEntry,
    bitmapCache: ViewerImageLoader,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    key(entry.photoKey, entry.version, entry.createdAt) {
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
}

@Composable
private fun SyncSbsZoomImage(
    uri: Uri,
    bitmapCache: ViewerImageLoader,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    var detailed by remember(uri) { mutableStateOf(false) }
    val side = if (detailed) bitmapCache.detailSide else bitmapCache.previewSide
    var bitmap by remember(uri) { mutableStateOf(bitmapCache.peek(uri, side)) }
    LaunchedEffect(uri, side, bitmapCache) {
        bitmapCache.load(uri, side)?.let { bitmap = it }
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
    SyncStereoZoomContent(left, right, uri, modifier, onTap, onZoomChanged = { if (it > 1.2f) detailed = true })
}

@Composable
private fun SyncSplitZoomImage(
    leftUri: Uri,
    rightUri: Uri,
    bitmapCache: ViewerImageLoader,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    var detailed by remember(leftUri, rightUri) { mutableStateOf(false) }
    val side = if (detailed) bitmapCache.detailSide else bitmapCache.previewSide
    var leftBitmap by remember(leftUri) { mutableStateOf(bitmapCache.peek(leftUri, side)) }
    var rightBitmap by remember(rightUri) { mutableStateOf(bitmapCache.peek(rightUri, side)) }
    LaunchedEffect(leftUri, rightUri, side, bitmapCache) {
        val leftResult = async { bitmapCache.load(leftUri, side) }
        val rightResult = async { bitmapCache.load(rightUri, side) }
        val loadedLeft = leftResult.await()
        val loadedRight = rightResult.await()
        if (loadedLeft != null && loadedRight != null) {
            leftBitmap = loadedLeft
            rightBitmap = loadedRight
        }
    }
    val left = leftBitmap
    val right = rightBitmap
    if (left == null || right == null) {
        Box(modifier.background(androidx.compose.ui.graphics.Color(0xff202326)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(26.dp))
        }
        return
    }
    SyncStereoZoomContent(left.asImageBitmap(), right.asImageBitmap(), "$leftUri|$rightUri", modifier, onTap, onZoomChanged = { if (it > 1.2f) detailed = true })
}

@Composable
internal fun SyncStereoZoomContent(
    left: ImageBitmap,
    right: ImageBitmap,
    gestureKey: Any,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onZoomChanged: (Float) -> Unit = {},
    onTransformChanged: (Float, Offset) -> Unit = { _, _ -> },
) {
    var scale by remember(gestureKey) { mutableStateOf(1f) }
    LaunchedEffect(scale) { onZoomChanged(scale) }
    var offset by remember(gestureKey) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(scale, offset) { onTransformChanged(scale, offset) }
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
                        val pinchSession = StereoPinchSession()
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            if (firstPosition == null) firstPosition = pressed.firstOrNull()?.position
                            if (pressed.isEmpty()) {
                                pinchSession.reset()
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
                                val next = pinchSession.updatePinch(
                                    state = StereoTransformState(scale, offset),
                                    pointerIds = pressed.mapTo(hashSetOf()) { it.id.value },
                                    positions = pressed.map { it.position },
                                    viewportWidth = viewportSize.width.toFloat(),
                                    viewportHeight = viewportSize.height.toFloat(),
                                    sbsMode = true,
                                )
                                scale = next.scale
                                offset = next.offset
                                event.changes.forEach { it.consume() }
                            } else {
                                pinchSession.reset()
                                val change = pressed.first()
                                if (scale > 1f) {
                                    val eyeWidth = viewportSize.width / 2f
                                    offset = StereoPinchSession.clampStereoOffset(
                                        offset = offset + (change.position - change.previousPosition),
                                        scale = scale,
                                        eyeWidth = eyeWidth,
                                        viewportHeight = viewportSize.height.toFloat(),
                                    )
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

internal fun List<PointerInputChange>.centroid(): Offset {
    if (isEmpty()) return Offset.Zero
    val x = sumOf { it.position.x.toDouble() }.toFloat() / size
    val y = sumOf { it.position.y.toDouble() }.toFloat() / size
    return Offset(x, y)
}

internal fun List<PointerInputChange>.span(center: Offset): Float {
    if (isEmpty()) return 1f
    return map { (it.position - center).getDistance() }.average().toFloat()
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
    @Volatile private var surface: Surface? = null
    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var textureHandle = 0
    private var texMatrixHandle = 0
    private val textureMatrix = FloatArray(16)
    private var frameAvailable = false
    @Volatile private var surfaceCallback: ((Surface?) -> Unit)? = null
    private var requestedActive = false
    private var videoWidth = 0
    private var videoHeight = 0
    private var zoomScale = 1f
    private var zoomOffset = Offset.Zero
    private val verticesBuffer = floatBuffer(FloatArray(8))
    private val textureCoordsBuffer = floatBuffer(FloatArray(8))

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
        queueEvent {
            if (videoWidth != width || videoHeight != height) {
                videoWidth = width
                videoHeight = height
                runCatching { surfaceTexture?.setDefaultBufferSize(width, height) }
                requestRender()
            }
        }
    }

    fun setActive(isActive: Boolean) {
        // SurfaceTexture callbacks supply frames; a paused player needs no continuous GL loop.
        if (requestedActive != isActive) {
            requestedActive = isActive
            if (isActive) requestRender()
        }
    }

    fun setZoom(scale: Float, offset: Offset) {
        queueEvent {
            if (zoomScale != scale || zoomOffset != offset) {
                zoomScale = scale
                zoomOffset = offset
                requestRender()
            }
        }
    }

    fun release() {
        queueEvent { releaseSurfaceTexture() }
    }

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        synchronized(this) { frameAvailable = false }
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
        if (surfaceTexture !== this.surfaceTexture) return
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
        val vertexBuffer = verticesBuffer.apply { clear(); put(vertices); position(0) }
        val texBuffer = textureCoordsBuffer.apply { clear(); put(texCoords); position(0) }
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

internal fun floatBuffer(values: FloatArray): FloatBuffer {
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

internal fun compileShader(type: Int, source: String): Int {
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
internal fun AsyncBitmapImage(
    uri: Uri,
    maxSide: Int,
    contentScale: ContentScale,
    bitmapCache: ViewerImageLoader? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(uri, maxSide) { mutableStateOf(bitmapCache?.peek(uri, maxSide)) }
    LaunchedEffect(uri, maxSide, bitmapCache) {
        bitmap = if (bitmapCache != null) bitmapCache.load(uri, maxSide) else withContext(Dispatchers.IO) {
            runCatching { decodeScaledBitmap(context, uri, maxSide) }.getOrNull()
        }
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
internal fun AsyncMediaThumbnail(kind: MediaKind, uri: Uri, maxSide: Int, contentScale: ContentScale, modifier: Modifier = Modifier) {
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
                            val pinchSession = StereoPinchSession()
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) {
                                    pinchSession.reset()
                                    break
                                }
                                if (pressed.size >= 2) {
                                    multiTouch = true
                                    moved = true
                                    val next = pinchSession.updatePinch(
                                        state = StereoTransformState(scale, offset),
                                        pointerIds = pressed.mapTo(hashSetOf()) { it.id.value },
                                        positions = pressed.map { it.position },
                                        viewportWidth = videoSize.width.toFloat(),
                                        viewportHeight = videoSize.height.toFloat(),
                                        sbsMode = sbsMode,
                                    )
                                    scale = next.scale
                                    offset = next.offset
                                    event.changes.forEach { it.consume() }
                                } else {
                                    pinchSession.reset()
                                    val change = pressed.first()
                                    if (scale > 1f) {
                                        val eyeWidth = if (sbsMode) videoSize.width / 2f else videoSize.width.toFloat()
                                        offset = StereoPinchSession.clampStereoOffset(
                                            offset = offset + (change.position - change.previousPosition),
                                            scale = scale,
                                            eyeWidth = eyeWidth,
                                            viewportHeight = videoSize.height.toFloat(),
                                        )
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
            LiquidGlassSurface(
                modifier = Modifier.align(Alignment.Center),
                shape = CircleShape,
                darkHud = true,
            ) {
                Text(
                    text = it,
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
        if (controlsVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                LiquidGlassSurface(
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    darkHud = true,
                    shadowElevation = 12.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
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
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FastForward,
                                    contentDescription = "Forward 5 seconds",
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Surface(
                                onClick = {
                                    playbackSpeed = if (playbackSpeed >= 2f) 1f else 2f
                                    if (Build.VERSION.SDK_INT >= 23) {
                                        runCatching { mediaPlayer?.let { it.playbackParams = it.playbackParams.setSpeed(playbackSpeed) } }
                                    }
                                },
                                shape = CircleShape,
                                color = androidx.compose.ui.graphics.Color(0x2BFFFFFF),
                            ) {
                                Text(
                                    if (playbackSpeed >= 2f) "2x" else "1x",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stateLine,
                                    color = androidx.compose.ui.graphics.Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = metricsLine,
                                    color = androidx.compose.ui.graphics.Color(0xB8FFFFFF),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                positionText,
                                color = androidx.compose.ui.graphics.Color(0xE6FFFFFF),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                            )
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
                            colors = SliderDefaults.colors(
                                thumbColor = androidx.compose.ui.graphics.Color.White,
                                activeTrackColor = androidx.compose.ui.graphics.Color.White,
                                inactiveTrackColor = androidx.compose.ui.graphics.Color(0x45FFFFFF),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
