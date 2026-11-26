package com.local.parallelvrgallerypro

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.roundToInt

@Composable
internal fun DebugScreen(
    state: UiState,
    index: Int,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onCompare: () -> Unit,
    onGenerate: () -> Unit,
    onRegenerate: () -> Unit,
    onShare: () -> Unit,
) {
    val apple = AppleTheme.colors
    val lang = state.settings.language
    val photo = state.photos.getOrNull(index)
    val entry = photo?.let(state::viewerEntry)
    val vrState = photo?.let { state.states[it.cacheKey] } ?: VrState.NORMAL
    val job = photo?.let { p -> state.jobs.firstOrNull { it.photoItem.cacheKey == p.cacheKey } }
    val isVideo = photo?.kind == MediaKind.VIDEO
    val videoEntry = photo?.let { state.videoEntries[it.cacheKey] }
    val videoJob = photo?.let { p -> state.videoJobs.firstOrNull { it.item.cacheKey == p.cacheKey } }
    val imageLogLines = remember(entry?.logPath, entry?.createdAt) { entry?.logPath?.let { readLastLines(File(it), 16) } ?: emptyList() }
    val imageParamsLines = remember(entry?.paramsPath, entry?.createdAt) { entry?.paramsPath?.let { readLastLines(File(it), 16) } ?: emptyList() }
    val imageTimings = remember(entry?.paramsPath, entry?.createdAt) { entry?.paramsPath?.let { readImageTimings(File(it)) } }
    val recordedParams = remember(entry?.paramsPath, entry?.createdAt) { entry?.paramsPath?.let { runCatching { org.json.JSONObject(File(it).readText(Charsets.UTF_8)) }.getOrNull() } }
    val videoLogLines = remember(videoEntry?.logPath, videoEntry?.createdAt) { videoEntry?.logPath?.let { readLastLines(File(it), 18) } ?: emptyList() }

    StudioPage(header = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppleGlassCircleButton(Icons.Rounded.ChevronLeft, lang.t("返回", "Back"), onBack, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    text = lang.t("调试", "Debug"),
                    color = apple.labelPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${photo?.displayName.orEmpty()} · ${if (isVideo) photo?.let { state.videoStates[it.cacheKey] } ?: VideoVrState.NORMAL else vrState}",
                    color = apple.labelSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AppleGlassCircleButton(Icons.Outlined.Settings, lang.t("调试参数设置", "Diagnostic settings"), onSettings, size = 36.dp)
            if (!isVideo && entry != null) {
                AppleGlassCircleButton(Icons.Outlined.Share, lang.t("分享调试包（包含媒体）", "Share diagnostics (includes media)"), onShare, size = 36.dp)
            }
        }
    }) {
        if (!isVideo && recordedParams != null) {
            DebugSection(lang.t("当前结果的实际参数", "Actual parameters of this result")) {
                DebugLine(lang.t("模式", "Mode"), recordedParams.optString("edgeMode", "历史版本"))
                DebugLine(lang.t("实际原生强度", "Actual native strength"), recordedParams.optDouble("effectiveDepthScale", recordedParams.optDouble("depthScale", 0.0)).toString())
                DebugLine(lang.t("自动默认匹配", "Automatic defaults"), if (recordedParams.optBoolean("matchV10Default", false)) "V10 · 50 / P90−P10" else lang.t("自定或历史参数", "Custom or historical parameters"))
            }
        }
        if (!isVideo && photo != null && !photo.generatedVirtual) {
            StudioCard(lang.t("对比与重算", "Compare & regenerate")) {
                AppleCapsuleButton(text = lang.t("六模式临时对比", "Compare six modes temporarily"), onClick = onCompare, modifier = Modifier.fillMaxWidth())
                Text(
                    lang.t("仅这张图，不写成品库、不改参数；快速切换保留双眼缩放与位置。", "Only this image. No library/settings changes; switching preserves both-eye zoom and pan."),
                    style = MaterialTheme.typography.bodySmall,
                    color = apple.labelSecondary,
                )
                var confirmReplace by remember { mutableStateOf(false) }
                AppleCapsuleButton(text = lang.t("使用新参数生成（保留旧结果）", "Generate with new parameters (keep old results)"), primary = false, onClick = onGenerate, modifier = Modifier.fillMaxWidth())
                if (entry != null) {
                    AppleCapsuleButton(text = lang.t("重新生成（替换当前旧结果）", "Regenerate (replace this result)"), primary = false, onClick = { confirmReplace = true }, modifier = Modifier.fillMaxWidth())
                }
                if (confirmReplace) {
                    AlertDialog(
                        onDismissRequest = { confirmReplace = false },
                        containerColor = apple.elevatedSurface,
                        shape = RoundedCornerShape(22.dp),
                        title = { Text("重新生成当前结果？", fontWeight = FontWeight.Bold) },
                        text = { Text("按当前设置生成成功后，仅替换当前查看的这一个旧结果。失败保留旧结果；其他历史版本和原图保留。", color = apple.labelSecondary) },
                        confirmButton = { AppleCapsuleButton(text = "重新生成", onClick = { confirmReplace = false; onRegenerate() }) },
                        dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("取消") } },
                    )
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (isVideo) {
                DebugSection(lang.t("视频运行状态", "Video runtime")) {
                    DebugLine("State", "${photo?.let { state.videoStates[it.cacheKey] } ?: VideoVrState.NORMAL}")
                    val videoState = photo?.let { state.videoStates[it.cacheKey] } ?: VideoVrState.NORMAL
                    DebugLine("Frame", VideoProgress.frames(videoState, videoJob?.currentFrame, videoJob?.totalFrames) ?: "-")
                    DebugLine("Progress", "${VideoProgress.percent(videoState, videoJob?.progress)}%")
                    DebugLine("FPS", "${videoJob?.fps ?: videoEntry?.fps ?: 30}")
                    DebugLine(lang.t("吞吐间隔", "Throughput interval"), "${videoJob?.currentFrameMs ?: 0}ms")
                    DebugLine(lang.t("平均吞吐", "Avg throughput"), "${videoJob?.avgFrameMs ?: 0}ms")
                    DebugLine(lang.t("端到端延迟", "Frame latency"), "${videoJob?.frameLatencyMs ?: 0}ms")
                    DebugLine(lang.t("其余耗时（估算）", "Other latency (estimated)"), "${videoJob?.queueWaitMs ?: 0}ms")
                    DebugLine(lang.t("缓存命中读取", "Cache-hit read"), "${videoJob?.cacheHitReadMs ?: 0}ms")
                    val pipeline = videoJob?.pipelineStats ?: VideoPipelineStats()
                    val timings = videoJob?.frameTimings ?: VideoFrameTimings()
                    DebugLine(lang.t("解码队列", "Decode queue"), "${pipeline.decodeQueue}")
                    if (timings.inputTransformNanos > 0L) {
                        DebugLine(lang.t("输入旋转缩放", "Input rotation and resize"), "${timings.inputTransformNanos / 1000} µs · ${pipeline.preparedInputQueue}/4")
                        DebugLine(lang.t("深度转回", "Depth rotation back"), "${timings.depthRotationNanos / 1000} µs · ${pipeline.uprightDepthQueue}/4")
                    }
                    DebugLine(lang.t("深度结果队列", "Depth queue"), "${pipeline.depthQueue}")
                    DebugLine(lang.t("生成队列", "Generated queue"), "${pipeline.generatedQueue}")
                    DebugLine(lang.t("等待编码", "Encode waiting"), "${pipeline.waitingFrames}")
                    DebugLine(lang.t("缓存命中", "Cache hits"), "${pipeline.cacheHits}")
                    DebugLine(lang.t("后台写入队列", "Cache write queue"), "${pipeline.cacheWriteQueue}")
                    val frameRead = timings.frameRead
                    val decodeLabel = when (frameRead?.backend) {
                        VideoDecodeBackend.MEDIACODEC -> "MediaCodec"
                        VideoDecodeBackend.ANDROID_RETRIEVER -> lang.t("Android 兼容", "Android compatibility")
                        null -> null
                    }
                    DebugLine(lang.t("取帧", "Decode"), decodeLabel?.let { "${timings.decodeMs}ms · $it" } ?: "-")
                    if (frameRead?.backend == VideoDecodeBackend.MEDIACODEC) {
                        DebugLine(lang.t("解码器 / YUV 转 RGB", "Codec / YUV to RGB"), "${frameRead.decoderMs} / ${frameRead.conversionMs}ms")
                    }
                    if (frameRead != null && frameRead.probeMs > 0L) {
                        DebugLine(lang.t("解码探测", "Decoder probe"), "${frameRead.probeMs}ms")
                    }
                    if (frameRead != null && timings.cacheLookupMs > 0L) {
                        DebugLine(lang.t("逐帧缓存检查", "Frame cache lookup"), "${timings.cacheLookupMs}ms")
                    }
                    DebugLine(lang.t("深度推理", "Depth"), "${timings.depthMs}ms")
                    DebugLine(lang.t("时序平滑", "Temporal"), "${timings.temporalMs}ms")
                    DebugLine(lang.t("深度后处理", "Depth post"), "${timings.depthPostMs}ms")
                    val recordedEntry = videoEntry?.takeIf { videoJob?.cacheVersion.isNullOrBlank() || it.cacheVersion == videoJob?.cacheVersion }
                    val geometry = timings.depthGeometry ?: recordedEntry?.takeUnless { videoState == VideoVrState.GENERATING }?.depthGeometry
                    DebugLine(lang.t("模型原生深度", "Native model depth"), geometry?.let { "${it.modelWidth}×${it.modelHeight}" } ?: "-")
                    if (geometry != null && (geometry.inputRotation != 0 || geometry.contentWidth != geometry.modelWidth || geometry.contentHeight != geometry.modelHeight)) {
                        val label = if (geometry.inputRotation == 90) lang.t("转回后深度", "Upright depth") else lang.t("裁回后深度", "Cropped depth")
                        DebugLine(label, "${geometry.contentWidth}×${geometry.contentHeight}")
                    }
                    DebugLine(lang.t("后处理深度（最近实测）", "Prepared depth (last measured)"), geometry?.let { "${it.preparedWidth}×${it.preparedHeight}" } ?: "-")
                    geometry?.workingLongEdge?.let { DebugLine(lang.t("该任务单眼长边上限", "Job working eye limit"), "$it px") }
                    DebugLine(lang.t("SBS 成品尺寸", "SBS output size"), recordedEntry?.let { "${it.width}×${it.height}" } ?: "-")
                    DebugLine("SBS", "${timings.sbsMs}ms")
                    DebugLine(lang.t("最近后台写入", "Last async cache write"), "${pipeline.lastCacheWriteMs}ms")
                    DebugLine(lang.t("编码提交", "Encode submit"), "${timings.encodeMs}ms")
                    val jobModelId = videoJob?.modelId?.takeIf { it.isNotBlank() } ?: recordedEntry?.modelId?.takeIf { it.isNotBlank() }
                    DebugLine(lang.t("该任务模型", "Job model"), AvailableModels.firstOrNull { it.id == jobModelId }?.displayName ?: jobModelId ?: "-")
                    DebugLine(lang.t("新任务模型", "New-job model"), modelSpec(state.settings.videoModelId).displayName)
                    DebugLine("Cache", videoJob?.cacheVersion?.takeIf { it.isNotBlank() } ?: recordedEntry?.cacheVersion?.takeIf { it.isNotBlank() } ?: "-")
                    DebugLine("Threads", "${videoJob?.modelThreads ?: state.settings.videoModelThreads}")
                    DebugLine("GPU requested", "${videoJob?.useGpu ?: state.settings.toVideoParams().useGpu}")
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
                val modelDepthFile = entry?.let { File(File(it.paramsPath).parentFile, "model_depth.png") }?.takeIf { it.isFile }
                SettingHelp(lang.t("深度图随“反转深度”开关黑白互换，生成结果也随之交换前后景。历史结果没有这份深度图时显示暂无，不自动重算。", "Invert depth swaps black/white in this map and reverses foreground/background in the generated result. Historical maps may be unavailable without regeneration."))
                Row(Modifier.fillMaxWidth().height(250.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DebugImagePanel(lang.t("原图", "Source"), photo?.uri, Modifier.weight(1f), lang)
                    DebugImagePanel(lang.t("深度图", "Depth map"), modelDepthFile?.let { Uri.fromFile(it) }, Modifier.weight(1f), lang)
                }
                Row(Modifier.fillMaxWidth().height(250.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DebugImagePanel("Left/VR", entry?.let { Uri.fromFile(File(if (it.hasSplitEyes) it.leftPath else it.outputPath)) }, Modifier.weight(1f), lang)
                }
                DebugSection(lang.t("图片参数", "Image params")) {
                    imageParamsLines.forEach { DebugMonoLine(it) }
                }
                DebugSection(lang.t("图片生成日志", "Image log")) {
                    imageLogLines.forEach { DebugMonoLine(it) }
                }
            }
            DebugSection(lang.t("队列", "Queue")) {
                state.videoJobs.take(4).forEach {
                    DebugMonoLine("${it.state.label(lang)} ${VideoProgress.percent(it.state, it.progress)}% f=${VideoProgress.frames(it.state, it.currentFrame, it.totalFrames) ?: "-"} ${it.item.displayName}")
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
    StudioCard(title = title, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), content = content)
}

@Composable
private fun DebugLine(label: String, value: String) {
    val apple = AppleTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = apple.labelSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(108.dp))
        Text(value, color = apple.labelPrimary, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DebugMonoLine(value: String) {
    Text(
        value,
        color = AppleTheme.colors.labelSecondary,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
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
    val apple = AppleTheme.colors
    val previewShape = RoundedCornerShape(12.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = apple.labelSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        if (uri == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(previewShape)
                    .background(Color(0xFF1C1C1E)),
                contentAlignment = Alignment.Center,
            ) {
                Text(lang.t("暂无", "None"), color = Color(0xFF98989F), style = MaterialTheme.typography.bodySmall)
            }
        } else {
            AsyncBitmapImage(
                uri,
                2048,
                ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(previewShape)
                    .background(Color.Black),
            )
        }
    }
}

@Composable
internal fun StudioGuideDialog(lang: AppLanguage, onClose: () -> Unit) {
    val apple = AppleTheme.colors
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = apple.elevatedSurface,
        shape = RoundedCornerShape(22.dp),
        title = { Text(lang.t("开始创作", "Start creating"), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(lang.t("打开素材 → VR 生成 → 查看结果", "Open media → Generate VR → View results"), fontWeight = FontWeight.SemiBold, color = apple.systemBlue)
                Text(lang.t("图片：当前图先生成，随后只在本次全部/相册范围内预加载。生成页不扩展预加载，视频需手动加入队列。", "Images start with the current photo, then prefetch within the captured All/Album source. Generated never expands prefetch; videos are queued manually."), color = apple.labelPrimary)
                Text(lang.t("长按结果可多选。全选限当前列表；重新生成使用当前设置；删除只针对所选生成结果，不删除系统原图。", "Long press results to select. Select all stays in the current list. Regenerate uses current settings; Delete removes selected generated results, not originals."), color = apple.labelPrimary)
                Text(lang.t("双指缩放、单指拖动、双击复位；双眼同步。点击画面收起控件。设置分为通用、深度与输出、图片、视频。", "Pinch to zoom, drag to pan and double tap to reset; both eyes stay synchronized. Tap the image to hide controls. Settings are grouped by General, Depth, Images and Video."), color = apple.labelPrimary)
                Text(lang.t("保存副本与替换原图不同。替换会改写原始媒体，始终先阅读确认提示。", "Save copies and Replace originals are different. Replacing changes original media; always read its confirmation."), color = apple.systemRed)
            }
        },
        confirmButton = { AppleCapsuleButton(text = lang.t("知道了", "Got it"), onClick = onClose) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudioTasksSheet(state: UiState, onClose: () -> Unit) {
    val apple = AppleTheme.colors
    val lang = state.settings.language
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = apple.groupedBackground,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text(lang.t("任务与进度", "Tasks & progress"), style = MaterialTheme.typography.titleLarge, color = apple.labelPrimary, fontWeight = FontWeight.Bold)
            Text(
                lang.t("仅查看现有队列；不会新增任务或恢复所有暂停项。视频的暂停/续跑在 Viewer 中操作。", "View existing jobs only; opening this sheet does not enqueue or resume jobs. Pause/resume video in its Viewer."),
                style = MaterialTheme.typography.bodySmall,
                color = apple.labelSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (state.jobs.isEmpty() && state.videoJobs.isEmpty()) {
                    item {
                        Text(
                            lang.t("当前没有任务。从全部或相册打开素材开始。", "No jobs yet. Open media from All or Albums to begin."),
                            color = apple.labelSecondary,
                            modifier = Modifier.padding(vertical = 28.dp),
                        )
                    }
                }
                items(state.videoJobs.size, key = { "video_" + state.videoJobs[it].item.cacheKey }) { index ->
                    val job = state.videoJobs[index]
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = apple.cardBackground,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    job.item.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = apple.labelPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    job.state.label(lang),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when (job.state) {
                                        VideoVrState.READY -> apple.systemGreen
                                        VideoVrState.FAILED -> apple.systemRed
                                        VideoVrState.GENERATING -> apple.systemBlue
                                        else -> apple.labelSecondary
                                    },
                                )
                            }
                            val percent = VideoProgress.percent(job.state, job.progress)
                            LinearProgressIndicator(
                                progress = { percent / 100f },
                                modifier = Modifier.fillMaxWidth().height(3.5.dp),
                                color = if (job.state == VideoVrState.READY) apple.systemGreen else apple.systemBlue,
                                trackColor = apple.fillSecondary,
                                strokeCap = StrokeCap.Round,
                                drawStopIndicator = {},
                            )
                            Text(
                                "$percent% · " + (VideoProgress.frames(job.state, job.currentFrame, job.totalFrames) ?: lang.t("等待帧信息", "Waiting for frame information")),
                                style = MaterialTheme.typography.bodySmall,
                                color = apple.labelSecondary,
                            )
                            if (job.state == VideoVrState.FAILED && !job.error.isNullOrBlank()) {
                                Text(job.error, color = apple.systemRed, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                items(state.jobs.size, key = { "image_" + state.jobs[it].photoItem.cacheKey }) { index ->
                    val job = state.jobs[index]
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = apple.cardBackground,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    job.photoItem.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = apple.labelPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    job.state.label(lang),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when (job.state) {
                                        VrState.READY -> apple.systemGreen
                                        VrState.FAILED -> apple.systemRed
                                        VrState.GENERATING -> apple.systemBlue
                                        else -> apple.labelSecondary
                                    },
                                )
                            }
                            val percent = if (job.state == VrState.READY) 100 else (job.progress * 100).roundToInt().coerceIn(0, 99)
                            LinearProgressIndicator(
                                progress = { percent / 100f },
                                modifier = Modifier.fillMaxWidth().height(3.5.dp),
                                color = if (job.state == VrState.READY) apple.systemGreen else apple.systemBlue,
                                trackColor = apple.fillSecondary,
                                strokeCap = StrokeCap.Round,
                                drawStopIndicator = {},
                            )
                            Text("$percent% · " + (job.source?.name ?: "—"), style = MaterialTheme.typography.bodySmall, color = apple.labelSecondary)
                            if (job.state == VrState.FAILED && !job.error.isNullOrBlank()) {
                                Text(job.error, color = apple.systemRed, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
