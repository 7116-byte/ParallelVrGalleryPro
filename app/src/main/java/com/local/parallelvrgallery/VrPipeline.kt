package com.local.parallelvrgallerypro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.gpu.GpuDelegateFactory
import java.io.File
import java.io.Closeable
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class VrGenerator @JvmOverloads constructor(
    private val context: Context,
    private val cache: VrCacheManager,
    private val modelManager: ModelManager,
    private val reuseVideoOriginalEye: Boolean = true,
) : Closeable {
    private val depthSessions = IdleResourcePool<String, DepthModelSession>(maxIdle = 1)
    fun trimIdleSessions(): Int = depthSessions.trimIdle()

    fun generate(
        photo: PhotoItem,
        params: VrGenerationParams,
        onModelProgress: (Float) -> Unit,
        priority: Int = 1,
        shouldContinue: () -> Boolean = { true },
        commit: (VrCacheEntry) -> VrCacheEntry = { cache.publish(photo, it) },
        onProgress: (Float) -> Unit,
    ): VrCacheEntry {
        val version = params.cacheVersion()
        fun checkCurrent() { if (!shouldContinue()) throw ImageJobObsoleteException() }
        checkCurrent()
        val dir = cache.stagingDir()
        val log = StringBuilder()
        val start = System.currentTimeMillis()
        fun mark(message: String) {
            val elapsed = System.currentTimeMillis() - start
            log.append(elapsed).append("ms ").append(message).append('\n')
        }

        mark("start name=${photo.displayName} size=${photo.size} modified=${photo.modifiedTime}")
        var sessionLease: IdleResourcePool.Borrowed<DepthModelSession>? = null
        val ownedBitmaps = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Bitmap, Boolean>())
        try {
            sessionLease = borrowDepthSession(params, onModelProgress) { mark(it) }
            checkCurrent()
            val depthSession = sessionLease.value
            mark("model session acquired key=${params.depthSessionKey()}")
            onProgress(0.12f)

            val decodeMaxLongEdge = memorySafeMaxLongEdge(photo.width, photo.height, params.maxLongEdge, if (params.matchV10Default) DepthEdgeMode.COMFY_LEGACY_V10 else params.edgeMode)
            if (decodeMaxLongEdge < params.maxLongEdge) {
                mark("memory capped maxLongEdge ${params.maxLongEdge} -> $decodeMaxLongEdge")
            }
            val decodeStart = System.currentTimeMillis()
            var original = decodeScaledBitmap(context, photo.uri, decodeMaxLongEdge).also { ownedBitmaps.add(it) }
            val decodeMs = System.currentTimeMillis() - decodeStart
            mark("decode ${decodeMs}ms size=${original.width}x${original.height}")
            if (max(photo.width, photo.height) > decodeMaxLongEdge) {
                mark("downsampled source because original long edge exceeded $decodeMaxLongEdge")
            }
            onProgress(0.25f)
            checkCurrent()

            val depthStart = System.currentTimeMillis()
            val inference = depthSession.infer(original, priority) {
                checkCurrent()
                // The owner thread has accepted the current inference before neighbors are admitted.
                onProgress(0.30f)
            }
            val rawDepth = inference.map
            checkCurrent()
            val modelMs = System.currentTimeMillis() - depthStart
            mark("model inference ${modelMs}ms tensor=${inference.inputWidth}x${inference.inputHeight} content=${rawDepth.width}x${rawDepth.height}")
            val depthPostStart = System.currentTimeMillis()
            DepthPngWriter.write(rawDepth.copy(values = DepthDirection.modelDepth(rawDepth.values,
                DepthDirection.reversed(params.edgeMode, params.invertModelDepth, params.invertDepth))), File(dir,"model_depth.png"))
            var depthMap = prepareDepth(rawDepth, params, original.width, original.height)
            onProgress(0.55f)

            val depthPath = File(dir, "depth.png")
            DepthPngWriter.write(depthMap,depthPath)
            val depthPostMs = System.currentTimeMillis() - depthPostStart
            mark("depth post ${depthPostMs}ms output=${depthMap.width}x${depthMap.height} source=${original.width}x${original.height} upscale=${params.depthUpscale}; memory/output limits apply")
            onProgress(0.7f)
            checkCurrent()

            val sbsStart = System.currentTimeMillis()
            val pair = runCatching {
                makeParallelStereoPair(original, depthMap, params.effectiveScale(original.width, depthMap), params.fillRadius, params.edgeMode)
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
                original = reduced.also { ownedBitmaps.add(it) }
                depthMap = prepareDepth(rawDepth, params, original.width, original.height)
                // Diagnostic depth must describe the geometry actually used by the successful retry.
                DepthPngWriter.write(depthMap,depthPath)
                makeParallelStereoPair(original, depthMap, params.effectiveScale(original.width, depthMap), params.fillRadius, params.edgeMode)
            }
            ownedBitmaps.add(pair.left)
            ownedBitmaps.add(pair.right)
            val sbsMs = System.currentTimeMillis() - sbsStart
            mark("stereo ${sbsMs}ms output=${pair.sbsWidth}x${pair.height}")
            val writeStart = System.currentTimeMillis()
            checkCurrent()
            val vrPath = File(dir, "vr_sbs.jpg")
            val leftPath = File(dir, "left.jpg")
            val rightPath = File(dir, "right.jpg")
            val stereoWrite = writeStereoJpegParallel(
                pair.left,
                leftPath,
                pair.right,
                rightPath,
                IMAGE_EYE_JPEG_QUALITY,
            )
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
                writeLeftMs = stereoWrite.leftMs,
                writeRightMs = stereoWrite.rightMs,
                writeMs = 0L,
                totalMs = totalBeforeFinalLog,
            )
            val writeParamsStart = System.currentTimeMillis()
            paramsPath.writeText(params.toJson(photo, original, outputWidth, outputHeight, timings, depthMap, inference), Charsets.UTF_8)
            val writeParamsFirstMs = System.currentTimeMillis() - writeParamsStart
            val writeMsBeforeLog = System.currentTimeMillis() - writeStart
            val finalTimingsBeforeLog = timings.copy(
                writeParamsMs = writeParamsFirstMs,
                writeMs = writeMsBeforeLog,
                totalMs = System.currentTimeMillis() - start,
            )
            val rewriteParamsStart = System.currentTimeMillis()
            paramsPath.writeText(params.toJson(photo, original, outputWidth, outputHeight, finalTimingsBeforeLog, depthMap, inference), Charsets.UTF_8)
            val writeParamsMs = writeParamsFirstMs + (System.currentTimeMillis() - rewriteParamsStart)
            val logWriteStart = System.currentTimeMillis()
            mark("write leftJpegQ$IMAGE_EYE_JPEG_QUALITY=${stereoWrite.leftMs}ms rightJpegQ$IMAGE_EYE_JPEG_QUALITY=${stereoWrite.rightMs}ms params=${writeParamsMs}ms")
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
            paramsPath.writeText(params.toJson(photo, original, outputWidth, outputHeight, finalTimings, depthMap, inference), Charsets.UTF_8)
            original.recycle()
            pair.recycle()

            checkCurrent()
            return commit(VrCacheEntry(
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
            ))
        } finally {
            ownedBitmaps.forEach { if (!it.isRecycled) it.recycle() }
            sessionLease?.close()
            dir.deleteRecursively()
        }
    }

    fun exportOriginalPng(photo: PhotoItem, spec: OriginalImageExportSpec, output: File,
        shouldContinue: () -> Boolean, onProgress: (Float, String) -> Unit): File {
        fun checkCurrent() { if (!shouldContinue()) throw ImageJobObsoleteException() }
        checkCurrent()
        require(!output.exists() && output.extension == "png") { "Use a new PNG export staging path" }
        validateOriginalExportSource(context, photo)
        val params = spec.params
        var original: Bitmap? = null
        var eyes: StereoBitmapPair? = null
        borrowDepthSession(params, { onProgress(it * 0.1f, "准备记录的模型 / Preparing recorded model") }, {}).use { lease ->
            try {
                checkCurrent()
                val edge = max(spec.eyeWidth, spec.eyeHeight)
                check(memorySafeMaxLongEdge(spec.eyeWidth, spec.eyeHeight, edge,
                    if (params.matchV10Default) DepthEdgeMode.COMFY_LEGACY_V10 else params.edgeMode) >= edge) {
                    "内存不足，未降低保存尺寸；请结束其他任务后重试 / Insufficient memory; export dimensions were not reduced"
                }
                onProgress(0.15f, "读取原图 / Reading original")
                val source = decodeScaledBitmap(context, photo.uri, edge).also { original = it }
                check(source.width == spec.eyeWidth && source.height == spec.eyeHeight) {
                    "原图尺寸与该结果不一致，未使用缓存替代 / Original dimensions differ from recorded result"
                }
                checkCurrent()
                onProgress(0.3f, "按该结果参数推理 / Inferring with recorded parameters")
                val raw = lease.value.infer(source, 0, spec.inputGeometry, ::checkCurrent).map
                checkCurrent()
                val depth = prepareDepth(raw, params, source.width, source.height)
                onProgress(0.65f, "投影单眼 / Projecting one eye")
                val rendered = makeParallelStereoPair(source, depth, params.effectiveScale(source.width, depth),
                    params.fillRadius, params.edgeMode, shareOriginalEye = true).also { eyes = it }
                checkCurrent()
                onProgress(0.8f, "写入无损 PNG / Writing lossless PNG")
                LosslessImageExport.originalPng(rendered.left, rendered.right, output, ::checkCurrent)
                onProgress(1f, "完成 / Complete")
                return output
            } finally {
                listOfNotNull(eyes?.left, eyes?.right, original).distinct().forEach { if (!it.isRecycled) it.recycle() }
            }
        }
    }

    fun generateComparison(photo: PhotoItem, base: AppSettings,
        profiles: Map<DepthEdgeMode, ModeVisualProfile>, requestedLongEdge: Int,
        shouldContinue: () -> Boolean, onProgress: (Float, String) -> Unit): List<ModeComparisonImage> {
        fun checkCurrent() { if (!shouldContinue()) throw ImageJobObsoleteException() }
        checkCurrent()
        val runtime = Runtime.getRuntime()
        val available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        // Seven retained RGB eyes (one shared original), plus worst-case native projection buffers.
        val budget = minOf(224L * 1024 * 1024, (available * 0.60).toLong()).coerceAtLeast(16L * 1024 * 1024)
        val ratio = max(photo.width, photo.height).toDouble().coerceAtLeast(1.0) / min(photo.width, photo.height).coerceAtLeast(1)
        val cap = kotlin.math.sqrt(budget / 120.0 * ratio).toInt().coerceAtLeast(256)
        val source = decodeScaledBitmap(context, photo.uri, minOf(requestedLongEdge, cap))
        val result = mutableListOf<ModeComparisonImage>()
        val owned = mutableListOf(source)
        var success = false
        try {
            onProgress(0.05f, "推理当前图片；不创建队列")
            val raw = borrowDepthSession(base.toParams(), {}, { android.util.Log.i("ModeComparison", it) }).use { lease ->
                lease.value.infer(source, 0) { checkCurrent() }.map
            }
            checkCurrent()
            DepthEdgeMode.entries.forEachIndexed { index, mode ->
                onProgress(0.15f + index / 6f * 0.8f, "${mode.shortLabel()} · ${source.width}×${source.height}")
                val params = profiles.getValue(mode).applyTo(base.copy(edgeMode = mode)).toParams()
                val limit = params.maxLongEdge.coerceAtMost(max(source.width, source.height))
                val factor = limit.toFloat() / max(source.width, source.height)
                val modeSource = if (factor < 1f) Bitmap.createScaledBitmap(source,
                    max(1, (source.width * factor).roundToInt()), max(1, (source.height * factor).roundToInt()), true) else source
                if (modeSource !== source) owned += modeSource
                android.util.Log.i("ModeComparison", "${mode.name}: scale=${params.depthScale}, eye=${modeSource.width}x${modeSource.height}, blur=${params.blurRadius}, invert=${params.invertDepth}, inputInvert=${params.invertModelDepth}, upscale=${params.depthUpscale}")
                val started = SystemClock.uptimeMillis()
                val depth = prepareDepth(raw, params, modeSource.width, modeSource.height)
                checkCurrent()
                val pair = makeParallelStereoPair(modeSource, depth, params.effectiveScale(modeSource.width, depth), params.fillRadius, mode)
                // All renderers keep exactly one original eye; share it across the six variants.
                val left: Bitmap
                val right: Bitmap
                if (mode.isReference) { pair.left.recycle(); left = modeSource; right = pair.right }
                else { pair.right.recycle(); left = pair.left; right = modeSource }
                owned += if (mode.isReference) right else left
                fun preview(map: DepthMap): Bitmap {
                    val factor = minOf(1.0, 512.0 / max(source.width, source.height))
                    val w = max(1, (source.width * factor).roundToInt())
                    val h = max(1, (source.height * factor).roundToInt())
                    return depthToBitmap(DepthResampling.resize(map, w, h, DepthInterpolation.BILINEAR))
                }
                val modelPreview = preview(raw.copy(values = DepthDirection.modelDepth(raw.values,
                    DepthDirection.reversed(mode, params.invertModelDepth, params.invertDepth)))).also { owned += it }
                val projectionPreview = preview(depth).also { owned += it }
                result += ModeComparisonImage(mode, left, right, params, params.effectiveScale(modeSource.width, depth), SystemClock.uptimeMillis() - started, modelPreview, projectionPreview)
                checkCurrent()
            }
            success = true
            return result
        } finally {
            if (!success) {
                owned.distinct().forEach { if (!it.isRecycled) it.recycle() }
            }
        }
    }

    fun generateSbsBitmap(
        source: Bitmap,
        params: VrGenerationParams,
        onModelProgress: (Float) -> Unit = {},
        onRuntimeInfo: (String) -> Unit = {},
        depthSession: DepthModelSession? = null,
    ): Bitmap {
        val safeMaxLongEdge = memorySafeMaxLongEdge(source.width, source.height, params.maxLongEdge, if (params.matchV10Default) DepthEdgeMode.COMFY_LEGACY_V10 else params.edgeMode)
        val working = if (max(source.width, source.height) > safeMaxLongEdge) {
            val scale = safeMaxLongEdge.toFloat() / max(source.width, source.height).toFloat()
            Bitmap.createScaledBitmap(source, max(1, (source.width * scale).roundToInt()), max(1, (source.height * scale).roundToInt()), true)
        } else {
            source
        }
        try {
            val rawDepth = if (depthSession != null) {
                depthSession.infer(working).map
            } else {
                openDepthSession(params, onModelProgress, onRuntimeInfo).use { session ->
                    session.infer(working).map
                }
            }
            val depthMap = prepareDepth(rawDepth, params, working.width, working.height)
            return makeParallelSbs(working, depthMap, params.effectiveScale(working.width, depthMap), params.fillRadius, params.edgeMode)
        } finally {
            if (working !== source) working.recycle()
        }
    }

    @JvmOverloads fun generateVideoSbsBitmap(
        source: Bitmap,
        params: VrGenerationParams,
        depthSession: DepthModelSession,
        temporalSmoother: DepthTemporalSmoother,
        orientation: QnnVideoOrientation? = null,
        workingLongEdge: Int = videoWorkingLongEdge(source, params),
    ): VideoSbsResult {
        val depth = generateVideoDepth(source, depthSession, orientation, workingLongEdge)
        return finishVideoSbs(depth, params, temporalSmoother)
    }

    fun generateVideoDepth(
        source: Bitmap,
        depthSession: DepthModelSession,
        orientation: QnnVideoOrientation?,
        workingLongEdge: Int,
    ): VideoDepthResult {
        val working = resizeVideoSource(source, workingLongEdge)
        val depthStart = SystemClock.uptimeMillis()
        val inference = try {
            if (orientation == null) depthSession.infer(working)
            else depthSession.inferVideo(working, orientation)
        } catch (error: Throwable) {
            if (working !== source) working.recycle()
            throw error
        }
        val depthMs = SystemClock.uptimeMillis() - depthStart
        return videoDepthResult(working, inference, depthMs)
    }

    fun videoWorkingLongEdge(source: Bitmap, params: VrGenerationParams): Int {
        val edgeMode = if (params.matchV10Default) DepthEdgeMode.COMFY_LEGACY_V10 else params.edgeMode
        return minOf(maxOf(source.width, source.height), memorySafeMaxLongEdge(source.width, source.height, params.maxLongEdge, edgeMode))
    }

    private fun resizeVideoSource(source: Bitmap, workingLongEdge: Int): Bitmap {
        val (width, height) = fitVideoWorkingFrame(source.width, source.height, workingLongEdge)
        if (source.width == width && source.height == height) return source
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    fun prepareRotatedVideoInput(source: Bitmap, geometry: QnnInputGeometry, workingLongEdge: Int): Pair<Bitmap, Bitmap> {
        val working = resizeVideoSource(source, workingLongEdge)
        return try {
            working to transformQnnInput(working, geometry)
        } catch (error: Throwable) {
            if (working !== source) working.recycle()
            throw error
        }
    }

    fun inferPreparedVideoDepth(
        source: Bitmap,
        tensor: Bitmap,
        geometry: QnnInputGeometry,
        session: DepthModelSession,
        inputTransformNanos: Long,
    ): VideoDepthResult {
        val started = SystemClock.uptimeMillis()
        val inference = session.inferPreparedVideo(tensor, geometry)
        return videoDepthResult(source, inference, SystemClock.uptimeMillis() - started, inputTransformNanos)
    }

    private fun videoDepthResult(
        source: Bitmap,
        inference: DepthInferenceOutput,
        depthMs: Long,
        inputTransformNanos: Long = inference.rotationTiming?.inputTransformNanos ?: 0L,
    ): VideoDepthResult = VideoDepthResult(
            source = source,
            rawDepth = inference.map.values,
            timings = VideoFrameTimings(
                depthMs = depthMs,
                inputTransformNanos = inputTransformNanos,
                depthRotationNanos = inference.rotationTiming?.outputRotationNanos ?: 0L,
            ),
            depthWidth = inference.map.width,
            depthHeight = inference.map.height,
            modelInputWidth = inference.inputWidth,
            modelInputHeight = inference.inputHeight,
            modelInputRotation = inference.geometry?.rotation ?: 0,
        )

    fun finishVideoSbs(
        depth: VideoDepthResult,
        params: VrGenerationParams,
        temporalSmoother: DepthTemporalSmoother,
    ): VideoSbsResult {
        val modelDepth = if (depth.depthWidth > 0 && depth.depthHeight > 0) {
            DepthMap(depth.rawDepth, depth.depthWidth, depth.depthHeight)
        } else DepthTensorShape.map(depth.rawDepth)
        val temporalStart = SystemClock.uptimeMillis()
        val stableDepth = temporalSmoother.smooth(modelDepth)
        val temporalMs = SystemClock.uptimeMillis() - temporalStart
        val depthPostStart = SystemClock.uptimeMillis()
        val depthMap = try {
            prepareDepth(stableDepth, params, depth.source.width, depth.source.height)
        } catch (error: Throwable) {
            depth.source.recycle()
            throw error
        }
        val depthPostMs = SystemClock.uptimeMillis() - depthPostStart
        val sbsStart = SystemClock.uptimeMillis()
        val eyes = try {
            // Decoded video pixels are opaque even when Bitmap's hasAlpha metadata is true.
            // Only reuse canonical sRGB ARGB8 storage for the encoder's raw texture upload.
            val share = reuseVideoOriginalEye && depth.source.config == Bitmap.Config.ARGB_8888 && depth.source.colorSpace?.isSrgb == true
            makeParallelStereoPair(depth.source, depthMap, params.effectiveScale(depth.source.width, depthMap), params.fillRadius, params.edgeMode, shareOriginalEye = share)
        } catch (error: Throwable) {
            depth.source.recycle()
            throw error
        }
        // Ownership of a shared decoded eye passes to the pair until both encode and cache write finish.
        InferenceProfiling.videoEyeReuseListener?.invoke(depth.source === eyes.left || depth.source === eyes.right)
        if (depth.source !== eyes.left && depth.source !== eyes.right) depth.source.recycle()
        val sbsMs = SystemClock.uptimeMillis() - sbsStart
        return VideoSbsResult(
            eyes = eyes,
            timings = VideoFrameTimings(
                depthMs = depth.timings.depthMs,
                temporalMs = temporalMs,
                depthPostMs = depthPostMs,
                sbsMs = sbsMs,
                    inputTransformNanos = depth.timings.inputTransformNanos,
                    depthRotationNanos = depth.timings.depthRotationNanos,
                depthGeometry = VideoDepthGeometry(
                    depth.modelInputWidth.takeIf { it > 0 } ?: modelDepth.width,
                    depth.modelInputHeight.takeIf { it > 0 } ?: modelDepth.height,
                    depthMap.width, depthMap.height, modelDepth.width, modelDepth.height, depth.modelInputRotation,
                ),
            ),
        )
    }

    fun prepareImageSession(params: VrGenerationParams, onRuntimeInfo: (String) -> Unit) {
        val shapes = qnnModelConfig(params.depthModel)
            ?.let { QnnInputGeometry.warmupGeometries(it.size, it.aspect) }.orEmpty()
        prepareImageSession(params, shapes, {}, { _, _ -> }, onRuntimeInfo)
    }

    fun prepareImageSession(
        params: VrGenerationParams,
        qnnShapes: List<QnnInputGeometry>,
        checkCurrent: () -> Unit,
        onShapeReady: (Int, Int) -> Unit,
        onRuntimeInfo: (String) -> Unit,
    ) {
        if (qnnShapes.size == 2) {
            val key = params.depthSessionKey()
            val lease = depthSessions.acquireIfPresent(key) ?: run {
                // Release an idle old model before keeping two new GPU graphs resident.
                depthSessions.trimIdle()
                checkCurrent()
                depthSessions.acquire(key) { openDepthSession(params, onRuntimeInfo = onRuntimeInfo, allowDownload = false) }
            }
            lease.use {
                qnnShapes.forEachIndexed { index, geometry ->
                    checkCurrent()
                    lease.value.prepareQnnShape(geometry, checkCurrent)
                    checkCurrent()
                    onShapeReady(index + 1, qnnShapes.size)
                }
            }
            return
        }
        depthSessions.acquire(params.depthSessionKey()) {
            checkCurrent()
            val session = openDepthSession(params, onRuntimeInfo = onRuntimeInfo, allowDownload = false)
            try {
                session.prepare()
                session
            } catch (error: Throwable) {
                runCatching { session.close() }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
        }.close()
    }

    fun borrowDepthSession(
        params: VrGenerationParams,
        onModelProgress: (Float) -> Unit,
        onRuntimeInfo: (String) -> Unit,
    ): IdleResourcePool.Borrowed<DepthModelSession> {
        val key = params.depthSessionKey()
        var created = false
        val lease = depthSessions.acquire(key) { created = true; openDepthSession(params, onModelProgress, onRuntimeInfo) }
        onRuntimeInfo("depth session acquired reused=${!created} key=$key")
        return lease
    }

    fun openDepthSession(
        params: VrGenerationParams,
        onModelProgress: (Float) -> Unit = {},
        onRuntimeInfo: (String) -> Unit = {},
        allowDownload: Boolean = true,
    ): DepthModelSession {
        val qnn = qnnModelConfig(params.depthModel)
        if (qnn != null) {
            check(QnnFp16DepthRuntime.hasGpuBackend(context)) { "QNN GPU 不可用 / QNN GPU unavailable" }
            onModelProgress(1f)
            return DepthModelSession.openQnn(context, qnn, onRuntimeInfo)
        }
        val modelFile = modelManager.ensureModel(params.depthModel, onModelProgress, allowDownload)
        return DepthModelSession(modelFile, params.modelThreads, params.useGpu, params.gpuTestMode, params.forceGpuNoFallback, onRuntimeInfo)
    }

    fun borrowVideoDepthSession(
        params: VrGenerationParams,
        onModelProgress: (Float) -> Unit,
        onRuntimeInfo: (String) -> Unit,
        orientation: QnnVideoOrientation?,
        checkActive: () -> Unit,
    ): IdleResourcePool.Borrowed<DepthModelSession> {
        val key = params.depthSessionKey()
        if (orientation?.useNativeLandscape != true) {
            depthSessions.acquireIfPresent(key)?.let {
                onRuntimeInfo("depth session acquired reused=true key=$key")
                return it
            }
        }
        // A private video session cannot evict either of the prepared image shapes.
        val owned = openDepthSession(params, onModelProgress, onRuntimeInfo)
        try {
            if (orientation?.useNativeLandscape == true) {
                owned.prepareQnnShape(orientation.inferenceInput, checkActive, allowNativeLandscapeOverride = true)
            }
            onRuntimeInfo("depth session acquired reused=false ownedVideo=true key=$key")
            return IdleResourcePool.Borrowed(owned) { owned.close() }
        } catch (error: Throwable) {
            runCatching { owned.close() }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    private fun memorySafeMaxLongEdge(width: Int, height: Int, requested: Int, mode: DepthEdgeMode): Int {
        val heap = Runtime.getRuntime()
        val heapAvailable = heap.maxMemory() - (heap.totalMemory() - heap.freeMemory())
        return memorySafeStereoLongEdge(width, height, requested, mode, heapAvailable)
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
        depthSessions.close()
    }

    private fun depthToBitmap(depth: DepthMap): Bitmap {
        val bitmap = Bitmap.createBitmap(depth.width, depth.height, Bitmap.Config.ARGB_8888)
        try {
            val row = IntArray(depth.width)
            for (y in 0 until depth.height) {
                for (x in row.indices) {
                    val value = (depth.values[y * depth.width + x].coerceIn(0f, 1f) * 255f).roundToInt()
                    row[x] = Color.rgb(value, value, value)
                }
                bitmap.setPixels(row, 0, depth.width, 0, y, depth.width, 1)
            }
            return bitmap
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    private fun prepareDepth(depth: DepthMap, params: VrGenerationParams, width: Int, height: Int): DepthMap {
        // Reserve space for resize/filter intermediates and the diagnostic bitmap per active job.
        val budget = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(96L * 1024 * 1024)
        val canonical = DepthDirection.modelDepth(depth.values, params.invertModelDepth)
        val modelDepth = depth.copy(values = canonical)
        val prepared = DepthPostProcessing.process(modelDepth, width, height, params, max(1L, budget / 16))
        if (params.edgeMode.isComfyMain) {
            val restored = ComfyLegacyStereoRenderer.prepareDepth(prepared.values, prepared.width, prepared.height, width, height, params.blurRadius, true)
            return DepthMap(restored, width, height, if (params.matchV10Default) 50f else null)
        }
        if (!params.matchV10Default) return prepared
        val baseline = ComfyLegacyStereoRenderer.prepareDepth(canonical, modelDepth.width, modelDepth.height, width, height, 3, true)
        return prepared.copy(projectionScale = DefaultDisparityCalibration.scale(params, width, prepared.values, baseline))
    }

    private fun makeParallelStereoPair(source: Bitmap, depth: DepthMap, depthScale: Float, fillRadius: Int, mode: DepthEdgeMode = DepthEdgeMode.LEGACY_V6, shareOriginalEye: Boolean = false): StereoBitmapPair {
        if (mode.isComfyMain) {
            val (left, right) = ComfyLegacyStereoRenderer.renderPrepared(source, depth.values, depthScale, mode == DepthEdgeMode.COMFY_SUBPIXEL_V11, shareOriginalEye)
            return StereoBitmapPair(left, right)
        }
        if (mode.isReference) {
            val (left, right) = ReferenceStereoRenderer.render(source, depth.values, depth.width, depth.height, depthScale, mode == DepthEdgeMode.REFERENCE_V9, shareOriginalEye)
            return StereoBitmapPair(left, right)
        }
        val w = source.width
        val h = source.height
        val left = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val right = try { if (shareOriginalEye) source else source.copy(Bitmap.Config.ARGB_8888, false) } catch (error: Throwable) {
            left.recycle()
            throw error
        }
        try {
            val zeroCopyProjected = source.config == Bitmap.Config.ARGB_8888 &&
                RustCore.projectPhoneBitmap(source, left, depth.values, depth.width, depth.height, depthScale, fillRadius)
            if (!zeroCopyProjected) {
                val srcPixels = IntArray(w * h)
                source.getPixels(srcPixels, 0, w, 0, 0, w, h)
                val projected = RustCore.projectPhoneStereo(srcPixels, depth.values, w, h, depth.width, depth.height, depthScale, fillRadius)
                left.setPixels(projected, 0, w, 0, 0, w, h)
            }
            return StereoBitmapPair(left, right)
        } catch (error: Throwable) {
            left.recycle()
            if (right !== source) right.recycle()
            throw error
        }
    }

    private fun makeParallelSbs(source: Bitmap, depth: DepthMap, depthScale: Float, fillRadius: Int, mode: DepthEdgeMode = DepthEdgeMode.LEGACY_V6): Bitmap {
        val pair = makeParallelStereoPair(source, depth, depthScale, fillRadius, mode)
        return try {
            composeSbsBitmap(pair.left, pair.right)
        } finally {
            pair.recycle()
        }
    }
}

internal data class StereoBitmapPair(
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


private data class DepthCompareResult(
    val meanAbs: Float,
    val correlation: Float,
)

internal enum class GpuMode {
    ACCURATE_UNSET,
    ACCURATE_OPENGL,
    ACCURATE_OPENCL,
    BEST_COMPAT,
}

class DepthModelSession private constructor(
    useGpu: Boolean,
    createRuntime: () -> DepthInferenceRuntime,
) : Closeable {
    constructor(
        modelFile: File,
        modelThreads: Int,
        useGpu: Boolean,
        gpuTestMode: GpuTestMode,
        forceGpuNoFallback: Boolean,
        onRuntimeInfo: (String) -> Unit = {},
    ) : this(useGpu, {
        DepthModelRuntime(modelFile, modelThreads, useGpu, gpuTestMode, forceGpuNoFallback, onRuntimeInfo)
    })

    private val ownsWorker = !useGpu
    private val worker = if (useGpu) gpuWorker else AffinityWorker("depth-cpu")
    private val resource = try {
        ThreadBoundResource(worker, createRuntime)
    } catch (error: Throwable) {
        if (ownsWorker) worker.close()
        throw error
    }

    fun run(bitmap: Bitmap, priority: Int = 1, onStarted: () -> Unit = {}): FloatArray = resource.use(priority) {
        onStarted()
        it.run(bitmap)
    }

    internal fun infer(bitmap: Bitmap, priority: Int = 1, geometryOverride: QnnInputGeometry? = null,
        onStarted: () -> Unit = {}): DepthInferenceOutput = resource.use(priority) {
        onStarted()
        it.infer(bitmap, geometryOverride)
    }

    fun prepare() = resource.use(priority = 100) { it.prepare() }

    internal fun prepareQnnShape(
        geometry: QnnInputGeometry,
        checkCurrent: () -> Unit,
        allowNativeLandscapeOverride: Boolean = false,
    ) = resource.use(priority = 100) {
        checkCurrent()
        it.prepareQnnShape(geometry, allowNativeLandscapeOverride)
    }

    internal fun inferVideo(bitmap: Bitmap, orientation: QnnVideoOrientation): DepthInferenceOutput = resource.use {
        it.infer(bitmap, orientation.inferenceInput, orientation.useNativeLandscape)
    }

    internal fun inferPreparedVideo(bitmap: Bitmap, geometry: QnnInputGeometry): DepthInferenceOutput = resource.use {
        it.inferPreparedVideo(bitmap, geometry)
    }

    override fun close() {
        try { resource.close() } finally { if (ownsWorker) worker.close() }
    }

    companion object {
        private val gpuWorker = AffinityWorker("depth-gpu-owner")

        internal fun openQnn(context: Context, config: QnnModelConfig, onRuntimeInfo: (String) -> Unit = {}): DepthModelSession =
            DepthModelSession(useGpu = true) { QnnFp16DepthRuntime.open(context, config, onRuntimeInfo) }
    }
}

private class DepthModelRuntime(
    modelFile: File,
    private val modelThreads: Int,
    private val useGpu: Boolean,
    private val gpuTestMode: GpuTestMode,
    private val forceGpuNoFallback: Boolean,
    private val onRuntimeInfo: (String) -> Unit = {},
) : DepthInferenceRuntime {
    private val inputSize: Int
    private val model = loadModelFile(modelFile)
    private var gpuDelegate: GpuDelegate? = null
    private var interpreter: Interpreter
    private var delegateActive = false
    private var gpuValidated = false
    private var activeGpuMode: GpuMode? = null
    private val threadCount = modelThreads.coerceIn(1, 8)
    private val input: ByteBuffer
    private val pixels: IntArray
    private val output: ByteBuffer
    private val packedRgb: FloatArray
    private val floatInput: FloatBuffer
    private var lastInvokeNs = 0L
    private var lastReadNs = 0L

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
        try {
            inputSize = DepthTensorShape.inputSize(interpreter.getInputTensor(0).shape(), interpreter.getOutputTensor(0).shape())
            input = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder())
            pixels = IntArray(inputSize * inputSize)
            output = ByteBuffer.allocateDirect(inputSize * inputSize * 4).order(ByteOrder.nativeOrder())
            packedRgb = FloatArray(inputSize * 3)
            floatInput = input.asFloatBuffer()
        } catch (error: Throwable) {
            interpreter.close();gpuDelegate?.close();throw error
        }
        onRuntimeInfo("tflite runtime input=${inputSize}x$inputSize threads=$threadCount requestedGpu=$useGpu forceGpu=$forceGpuNoFallback delegateActive=$delegateActive gpuMode=${activeGpuMode ?: "CPU"} reusedSession=true ${gpuDiagnostics.joinToString(" ")}")
    }

    @Synchronized
    override fun prepare() { interpreter.allocateTensors() }

    @Synchronized
    override fun run(bitmap: Bitmap): FloatArray {
        val prepareStarted = System.nanoTime()
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        input.clear()
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        if (scaled !== bitmap) scaled.recycle()
        val packStarted = System.nanoTime()
        TensorTransfer.packRgb(pixels,packedRgb,floatInput)
        input.rewind()
        val packedAt = System.nanoTime()
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
        InferenceProfiling.listener?.invoke(inputSize, packStarted-prepareStarted, packedAt-packStarted, lastInvokeNs, lastReadNs)
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
        val invokedAt = System.nanoTime()
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
        val readAt = System.nanoTime()
        lastInvokeNs = readAt-invokedAt
        return readDepthOutput(output).also {lastReadNs=System.nanoTime()-readAt}
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
        return TensorTransfer.readNormalized(buffer,inputSize*inputSize)
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

internal data class VideoSbsResult(
    val eyes: StereoBitmapPair,
    val timings: VideoFrameTimings,
)

data class VideoDepthResult @JvmOverloads constructor(
    val source: Bitmap,
    val rawDepth: FloatArray,
    val timings: VideoFrameTimings,
    val depthWidth: Int = 0,
    val depthHeight: Int = 0,
    val modelInputWidth: Int = 0,
    val modelInputHeight: Int = 0,
    val modelInputRotation: Int = 0,
)

private fun VideoDepthResult.restoreRotation(): VideoDepthResult {
    val started = System.nanoTime()
    val upright = rotateQnnDepthCounterClockwise(rawDepth, depthWidth, depthHeight)
    return copy(
        rawDepth = upright,
        depthWidth = depthHeight,
        depthHeight = depthWidth,
        timings = timings.copy(depthRotationNanos = System.nanoTime() - started),
    )
}

private data class VideoFramePlan(
    val timeline: VideoTimeline,
    val durationMs: Long,
    val fps: Int,
    val totalFrames: Int,
    val useFrameIndex: Boolean,
    val metadataFrameCount: Int?,
    val captureFps: Int?,
    val sourceBitrate: Int?,
    val decodedBitmapBytes: Long,
)

class DepthTemporalSmoother(
    private val previousWeight: Float = 0.62f,
    private val sceneCutThreshold: Float = 0.20f,
    private val localMotionThreshold: Float = 0.08f,
) {
    private var previous: FloatArray? = null
    private var previousWidth = 0
    private var previousHeight = 0

    internal fun checkpoint(): VideoSegmentStore.Checkpoint? = previous?.let {
        VideoSegmentStore.Checkpoint(previousWidth, previousHeight, it)
    }

    internal fun restore(checkpoint: VideoSegmentStore.Checkpoint) {
        previousWidth = checkpoint.width
        previousHeight = checkpoint.height
        previous = checkpoint.values
    }

    internal fun smooth(current: DepthMap): DepthMap {
        if (previousWidth != current.width || previousHeight != current.height) {
            previous = null
            previousWidth = current.width
            previousHeight = current.height
        }
        return current.copy(values = smooth(current.values))
    }

    fun smooth(current: FloatArray): FloatArray {
        val prev = previous
        if (prev == null || prev.size != current.size) {
            previous = current.copyOf()
            return current
        }
        RustCore.smoothTemporal(current, prev, previousWeight, sceneCutThreshold, localMotionThreshold)
        previous = current.copyOf()
        return current
    }
}

internal class VideoVrGenerator(
    private val context: Context,
    private val cache: VideoVrCacheManager,
    private val frameGenerator: VrGenerator,
) {
    fun generate(
        item: GalleryItem,
        params: VideoGenerationParams,
        onModelProgress: (Float) -> Unit,
        onRuntimeInfo: (String) -> Unit = {},
        checkActive: () -> Unit = {},
        onProgress: (Float, Int, Int, Int, VideoFrameMetrics, VideoFrameTimings, VideoPipelineStats) -> Unit,
    ): VideoCacheEntry {
        val vrParams = params.toVrParams()
        val version = params.cacheVersion()
        val dir = cache.entryDir(item, version)
        val framesDir = File(dir, "frames").also { it.mkdirs() }
        val portraitPair = qnnModelConfig(vrParams.depthModel)?.takeIf { it.aspect == QnnDepthAspect.PORTRAIT_PAIR }
        val orientationFile = portraitPair?.let { File(dir, "qnn_orientation.tsv") }
        var orientation = orientationFile?.takeIf { it.isFile }?.let {
            QnnVideoOrientation.restore(it.readText(Charsets.UTF_8), portraitPair.size)
        }
        check(orientationFile == null || orientation != null || framesDir.list().isNullOrEmpty()) {
            "视频续跑缺少推理方向记录 / Missing QNN orientation for cached video frames"
        }
        val previousFramesDirs = listOf("encoderV13", "encoderV12")
            .asSequence()
            .map { encoderVersion -> version.replace("_$VIDEO_ENCODER_VERSION", "_$encoderVersion") }
            .filter { previousVersion -> previousVersion != version }
            .map { previousVersion -> File(File(File(cache.root, item.cacheKey), previousVersion), "frames") }
            .filter { candidate -> candidate.isDirectory }
            .toList()
        val output = File(dir, "vr_sbs.mp4")
        val pendingOutput = File(dir, "vr_sbs.mp4.pending")
        val logPath = File(dir, "job.log")
        val metaPath = File(dir, "video_params.txt")
        if (pendingOutput.exists()) pendingOutput.delete()
        logPath.writeText("", Charsets.UTF_8)
        val started = System.currentTimeMillis()
        fun mark(line: String) {
            logPath.appendText("${System.currentTimeMillis() - started}ms $line\n", Charsets.UTF_8)
        }

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, item.uri)
        var frameReader: VideoFrameReader? = null
        try {
            checkActive()
            val framePlan = buildFramePlan(retriever, VideoTrackInfo.read(context, item.uri))
            val reader = VideoFrameReader(context, item.uri, framePlan.timeline, retriever, checkActive, onRuntimeInfo)
            frameReader = reader
            val durationMs = framePlan.durationMs
            val fps = framePlan.fps
            val totalFrames = framePlan.totalFrames
            mark("start video=${item.displayName} durationMs=$durationMs fps=$fps frames=$totalFrames frameMode=${if (framePlan.useFrameIndex) "index" else "time"} metadataFrames=${framePlan.metadataFrameCount ?: "-"} captureFps=${framePlan.captureFps ?: "-"} sourceBitrate=${framePlan.sourceBitrate ?: 0} version=$version model=${vrParams.depthModel} threads=${vrParams.modelThreads} depthWorkers=${params.depthWorkers.coerceIn(1, 2)} useGpu=${vrParams.useGpu}")
            if (previousFramesDirs.isNotEmpty()) {
                mark("frame cache fallbacks=${previousFramesDirs.joinToString("|") { it.absolutePath }}")
            }

            val segmentsDirectory = File(dir, "segments")
            val store = if (segmentsDirectory.isDirectory ||
                framesDir.list().isNullOrEmpty() && previousFramesDirs.isEmpty()
            ) VideoSegmentStore(segmentsDirectory, framesDir, totalFrames, (fps * 3 / 2).coerceIn(6, 72)) else null
            val completed = store?.recover()?.toMutableList() ?: mutableListOf()
            var width = 0
            var height = 0
            var workingLongEdge = completed.lastOrNull()?.geometry?.workingLongEdge ?: completed.firstOrNull()?.let { first ->
                recoverVideoWorkingLongEdge(first.width, first.height, first.geometry, vrParams.edgeMode.isComfyMain)
            }
            var decodedBitmapBytes = framePlan.decodedBitmapBytes
            val temporalSmoother = DepthTemporalSmoother()
            completed.lastOrNull()?.let { last ->
                width = last.width
                height = last.height
                requireNotNull(store?.readCheckpoint(last)) { "Missing video segment depth checkpoint" }.let(temporalSmoother::restore)
                mark("resume segments=${completed.size} nextFrame=${last.range.endFrameExclusive}")
            }
            val depthGeometry = DeferredResource {
                frameGenerator.borrowVideoDepthSession(vrParams, onModelProgress, onRuntimeInfo, orientation, checkActive)
            }.use { depthSession ->
                fun encodeRange(target: File, segment: VideoSegmentStore.Range? = null): VideoDepthGeometry? {
                    val firstIndex = segment?.startFrame ?: 0
                    val firstLoadStarted = SystemClock.uptimeMillis()
                    val cachedFirst = if (segment != null) null else readCachedStereoFrame(framesDir, firstIndex, previousFramesDirs)?.also {
                        onRuntimeInfo("frame cache hit frame=0 version=$version")
                    }
                    val firstCacheLookupMs = if (segment == null) SystemClock.uptimeMillis() - firstLoadStarted else 0L
                    var firstTimings = VideoFrameTimings(cacheLookupMs = firstCacheLookupMs)
                    val firstEyes = cachedFirst ?: run {
                        val read = reader.frameAt(firstIndex)
                        val first = read.bitmap
                        var ownedEyes: StereoBitmapPair? = null
                        var firstSource: Bitmap? = null
                        try {
                            val source = if (first.config == Bitmap.Config.ARGB_8888) {
                                first
                            } else {
                                first.copy(Bitmap.Config.ARGB_8888, false)
                            }.also { firstSource = it }
                            decodedBitmapBytes = maxOf(decodedBitmapBytes, source.allocationByteCount.toLong())
                            if (portraitPair != null && orientationFile != null && orientation == null) {
                                val input = QnnInputGeometry.forSource(portraitPair.size, source.width, source.height, portraitPair.aspect)
                                checkActive()
                                val planned = QnnVideoOrientation.forNewVideo(input)
                                val temporary = File.createTempFile(".qnn_orientation-", ".tmp", dir)
                                try {
                                    temporary.writeText(planned.encode(), Charsets.UTF_8)
                                    java.nio.file.Files.move(temporary.toPath(), orientationFile.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                                } finally {
                                    temporary.delete()
                                }
                                orientation = planned
                            }
                            orientation?.let {
                                mark("qnn video rotation=${it.inferenceInput.rotation} nativeLandscape=${it.useNativeLandscape} input=${it.inferenceInput.tensorWidth}x${it.inferenceInput.tensorHeight}")
                            }
                            val session = depthSession.get().value
                            val limit = workingLongEdge ?: frameGenerator.videoWorkingLongEdge(source, vrParams).also { workingLongEdge = it }
                            val generated = frameGenerator.generateVideoSbsBitmap(source, vrParams, session, temporalSmoother, orientation, limit)
                            firstTimings = generated.timings.copy(frameRead = read.timing, cacheLookupMs = firstCacheLookupMs)
                            try {
                                if (segment == null) writeStereoFrameCache(framesDir, firstIndex, generated.eyes)
                            } catch (error: Throwable) {
                                generated.eyes.recycle()
                                throw error
                            }
                            generated.eyes.also { ownedEyes = it }
                        } finally {
                            listOfNotNull(first, firstSource).distinct().forEach {
                                if (it !== ownedEyes?.left && it !== ownedEyes?.right && !it.isRecycled) it.recycle()
                            }
                        }
                    }
                    if (width == 0) {
                        width = even(firstEyes.sbsWidth)
                        height = even(firstEyes.height)
                    }
                    val limit = workingLongEdge ?: maxOf(firstEyes.left.width, firstEyes.left.height).also { workingLongEdge = it }
                    try {
                        mark("first frame index=$firstIndex eyes=${firstEyes.left.width}x${firstEyes.left.height} encode=${width}x${height} cached=${cachedFirst != null} workingLongEdge=$limit")
                    } catch (error: Throwable) {
                        firstEyes.recycle()
                        throw error
                    }

                    return encodeVideo(
                        item = item,
                        reader = reader,
                        checkActive = checkActive,
                        firstEyes = firstEyes,
                        firstCacheHit = cachedFirst != null,
                        firstTimings = firstTimings,
                        firstLoadStarted = firstLoadStarted,
                        framesDir = framesDir,
                        fallbackFramesDirs = previousFramesDirs,
                        output = target,
                        width = width,
                        height = height,
                        fps = fps,
                        totalFrames = totalFrames,
                        framePlan = framePlan,
                        version = version,
                        params = vrParams,
                        videoParams = params,
                        workingLongEdge = limit,
                        decodedBitmapBytes = decodedBitmapBytes,
                        depthSession = depthSession,
                        orientation = orientation,
                        temporalSmoother = temporalSmoother,
                        onModelProgress = onModelProgress,
                        onRuntimeInfo = onRuntimeInfo,
                        onProgress = onProgress,
                        mark = ::mark,
                        segment = segment,
                    )
                }
                if (store == null) {
                    encodeRange(pendingOutput)
                } else {
                    var geometry = completed.lastOrNull()?.geometry
                    for (range in store.ranges.drop(completed.size)) {
                        checkActive()
                        store.begin(range).use { stage ->
                            geometry = encodeRange(stage.mediaFile, range)
                            val checkpoint = requireNotNull(temporalSmoother.checkpoint())
                            completed += store.publish(stage, width, height, checkpoint, geometry, checkActive)
                            mark("segment committed index=${range.index} start=${range.startFrame} end=${range.endFrameExclusive}")
                        }
                    }
                    geometry
                }
            }

            checkActive()
            if (store != null) {
                reader.close()
                VideoSegmentMuxer.mux(
                    context, item.uri,
                    completed.map { VideoSegment(it.mediaFile, it.range.startFrame, it.range.endFrameExclusive - it.range.startFrame) },
                    framePlan.timeline, pendingOutput, checkActive,
                )
                mark("segments stitched count=${completed.size}")
            }
            metaPath.writeText(
                (listOf(
                    "width=$width",
                    "height=$height",
                    "durationMs=$durationMs",
                    "fps=$fps",
                    "frameMode=${if (framePlan.useFrameIndex) "index" else "time"}",
                    "metadataFrameCount=${framePlan.metadataFrameCount ?: 0}",
                    "captureFps=${framePlan.captureFps ?: 0}",
                    "sourceBitrate=${framePlan.sourceBitrate ?: 0}",
                    "source=${item.displayName}",
                    "depthModel=${vrParams.depthModel}",
                    "cacheVersion=$version",
                    "encoderVersion=$VIDEO_ENCODER_VERSION",
                    if (store == null) "frameCache=split_jpeg_q$VIDEO_FRAME_JPEG_QUALITY" else "frameCache=segments",
                    "segments=${completed.size}",
                    "frameCacheFallbacks=${previousFramesDirs.joinToString("|") { it.absolutePath }}",
                    "modelThreads=${vrParams.modelThreads}",
                    "depthWorkers=${params.depthWorkers.coerceIn(1, 2)}",
                    "useGpu=${vrParams.useGpu}",
                    "gpuTestMode=${vrParams.gpuTestMode}",
                    "forceGpuNoFallback=${vrParams.forceGpuNoFallback}",
                    "maxLongEdge=${vrParams.maxLongEdge}",
                    "depthUpscaleEnabled=${vrParams.depthUpscale.enabled}",
                    "depthUpscaleMaxLongEdge=${vrParams.depthUpscale.boundedLongEdge}",
                    "depthInterpolation=${vrParams.depthUpscale.interpolation}",
                    "depthResizeOrder=${vrParams.depthUpscale.order}",
                ) + depthGeometry?.metadataLines().orEmpty() + orientation?.let {
                    listOf("qnnNativeLandscape=${it.useNativeLandscape}") +
                        listOfNotNull(it.rotationNanos?.let { nanos -> "qnnRotationProbeNanos=$nanos" })
                }.orEmpty()).joinToString("\n"),
                Charsets.UTF_8,
            )
            java.io.FileOutputStream(pendingOutput, true).use { it.fd.sync() }
            checkActive()
            java.nio.file.Files.move(pendingOutput.toPath(), output.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            val entry = cache.register(item, dir) ?: error("Video output cache missing")
            if (store != null) segmentsDirectory.deleteRecursively()
            framesDir.deleteRecursively()
            mark("done output=${output.absolutePath}")
            return entry
        } finally {
            frameReader?.close()
            retriever.release()
            pendingOutput.delete()
        }
    }

    private fun buildFramePlan(retriever: MediaMetadataRetriever, track: VideoTrackInfo): VideoFramePlan {
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.coerceAtLeast(1L)
            ?: 1L
        val captureFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            ?.toFloatOrNull()
            ?.roundToInt()
            ?.coerceIn(1, 240)
        val sourceBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            ?.toLongOrNull()
            ?.coerceIn(1L, Int.MAX_VALUE.toLong())
            ?.toInt()
        val metadataFrameCount = if (Build.VERSION.SDK_INT >= 28) {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
        } else {
            null
        }
        val totalFrames = track.timeline.size
        val fps = ((totalFrames * 1000.0) / durationMs).roundToInt().coerceIn(1, 240)
        return VideoFramePlan(
            timeline = track.timeline,
            durationMs = durationMs,
            fps = fps,
            totalFrames = totalFrames,
            useFrameIndex = metadataFrameCount != null && Build.VERSION.SDK_INT >= 28,
            metadataFrameCount = metadataFrameCount,
            captureFps = captureFps,
            sourceBitrate = sourceBitrate,
            decodedBitmapBytes = 4L * track.format.getInteger(MediaFormat.KEY_WIDTH, 0).coerceAtLeast(0) *
                track.format.getInteger(MediaFormat.KEY_HEIGHT, 0).coerceAtLeast(0),
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
        reader: VideoFrameReader,
        checkActive: () -> Unit,
        firstEyes: StereoBitmapPair,
        firstCacheHit: Boolean,
        firstTimings: VideoFrameTimings,
        firstLoadStarted: Long,
        framesDir: File,
        fallbackFramesDirs: List<File>,
        output: File,
        width: Int,
        height: Int,
        fps: Int,
        totalFrames: Int,
        framePlan: VideoFramePlan,
        version: String,
        params: VrGenerationParams,
        videoParams: VideoGenerationParams,
        workingLongEdge: Int,
        decodedBitmapBytes: Long,
        depthSession: DeferredResource<IdleResourcePool.Borrowed<DepthModelSession>>,
        orientation: QnnVideoOrientation?,
        temporalSmoother: DepthTemporalSmoother,
        onModelProgress: (Float) -> Unit,
        onRuntimeInfo: (String) -> Unit,
        onProgress: (Float, Int, Int, Int, VideoFrameMetrics, VideoFrameTimings, VideoPipelineStats) -> Unit,
        mark: (String) -> Unit,
        segment: VideoSegmentStore.Range? = null,
    ): VideoDepthGeometry? {
        var ownedCodec: MediaCodec? = null
        var ownedSurface: Surface? = null
        var ownedMuxer: MediaMuxer? = null
        var ownedAudio: AudioSource? = null
        var renderer: InputSurfaceRenderer? = null
        var muxerStopped = false
        try {
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also { ownedCodec = it }
            val codecCapabilities = codec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val videoCapabilities = requireNotNull(codecCapabilities.videoCapabilities) { "AVC encoder has no video capabilities" }
            val encoderCapabilities = requireNotNull(codecCapabilities.encoderCapabilities) { "AVC encoder has no encoder capabilities" }
            val pixelRateTarget = (width.toDouble() * height.toDouble() * fps.toDouble() * VIDEO_TARGET_BITS_PER_PIXEL_FRAME.toDouble())
                .roundToInt()
                .coerceAtLeast(2_000_000)
            val sourceRateTarget = framePlan.sourceBitrate?.let { (it.toLong() * 2L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() } ?: 0
            val targetBitrate = videoCapabilities.bitrateRange.clamp(max(pixelRateTarget, sourceRateTarget))
            val useVbr = encoderCapabilities.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                if (segment != null) setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                if (useVbr) {
                    setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                }
            }
            val supportsHighProfile = codecCapabilities.profileLevels.any {
                it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
            }
            if (supportsHighProfile) {
                format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                if (!codecCapabilities.isFormatSupported(format)) {
                    format.removeKey(MediaFormat.KEY_PROFILE)
                }
            }
            mark("encoder codec=${codec.codecInfo.name} bitrate=$targetBitrate sourceBitrate=${framePlan.sourceBitrate ?: 0} mode=${if (useVbr) "VBR" else "default"} highProfile=${format.containsKey(MediaFormat.KEY_PROFILE)} frameCache=jpegQ$VIDEO_FRAME_JPEG_QUALITY")
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface().also { ownedSurface = it }
            codec.start()

            val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { ownedMuxer = it }
            val audio = if (segment == null) prepareAudioExtractor(item).also { ownedAudio = it } else null
            val audioTrack = audio?.format?.let { muxer.addTrack(it) } ?: -1
            var videoTrack = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()

            fun drain(end: Boolean) {
                if (end) codec.signalEndOfInputStream()
                var lastProgress = SystemClock.elapsedRealtime()
                while (true) {
                    checkActive()
                    check(SystemClock.elapsedRealtime() - lastProgress < 15_000) { "Video encoder timed out while draining" }
                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!end) return
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            videoTrack = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outputIndex >= 0 -> {
                            lastProgress = SystemClock.elapsedRealtime()
                            val encoded = codec.getOutputBuffer(outputIndex)
                            if (encoded != null && bufferInfo.size > 0 && muxerStarted && bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
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

            val activeRenderer = InputSurfaceRenderer(surface, width, height)
            renderer = activeRenderer
            data class PipelineFrame(
                val index: Int,
                var source: Bitmap? = null,
                var tensor: Bitmap? = null,
                var inputTransformNanos: Long = 0L,
                var depth: VideoDepthResult? = null,
                var eyes: StereoBitmapPair? = null,
                var timings: VideoFrameTimings = VideoFrameTimings(),
                var cacheHit: Boolean = false,
                var loadStartedAt: Long = 0L,
            )
            val available = Runtime.getRuntime().let { it.maxMemory() - (it.totalMemory() - it.freeMemory()) }
            val systemMemory = android.app.ActivityManager.MemoryInfo().also {
                (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(it)
            }
            val memoryBudget = minOf((available * 0.4).toLong(), (systemMemory.availMem - systemMemory.threshold).coerceAtLeast(0L) / 8)
            val pairBytes = firstEyes.left.allocationByteCount.toLong() + firstEyes.right.allocationByteCount
            val rotatedInput = orientation?.inferenceInput?.takeIf { it.rotation == 90 }
            val modelBytes = rotatedInput?.let { 4L * it.tensorWidth * it.tensorHeight } ?: 0L
            val unscaledDecodeBytes = (decodedBitmapBytes - firstEyes.left.allocationByteCount).coerceAtLeast(0L)
            val inFlightLimit = (memoryBudget / (pairBytes * 2 + modelBytes * 2 + unscaledDecodeBytes).coerceAtLeast(1))
                .coerceIn(1L, if (rotatedInput == null) 4L else 12L).toInt()
            val cacheHits = AtomicInteger(if (firstCacheHit) 1 else 0)
            val depthWorkerCount = if (params.useGpu) 1 else videoParams.depthWorkers.coerceIn(1, 2)
            val extraSessions = mutableMapOf<Int, DepthModelSession>()
            var lastGeneratedEncodedAt = 0L
            var lastDepthGeometry: VideoDepthGeometry? = null
            var rotationFrames = 0
            var inputTransformNanos = 0L
            var depthRotationNanos = 0L
            var peakPrepared = 0
            var peakUpright = 0
            var throughputSamples = 0
            var totalThroughputMs = 0L
            mark("pipeline inFlightLimit=$inFlightLimit depthWorkers=$depthWorkerCount cacheWriter=${if (segment == null) "independent" else "off"} rotationQueues=${if (rotatedInput != null) "4/4" else "off"}")

            fun releaseFrame(data: PipelineFrame) {
                data.tensor?.let { if (it !== data.source && !it.isRecycled) it.recycle() }
                data.source?.let { if (!it.isRecycled) it.recycle() }
                data.depth?.source?.let { if (!it.isRecycled) it.recycle() }
                data.eyes?.recycle()
            }
            fun encodeFrame(data: PipelineFrame, pipeline: FramePipelineStats) {
                checkActive()
                val eyes = requireNotNull(data.eyes)
                val encodeStart = SystemClock.uptimeMillis()
                val firstPts = segment?.let { framePlan.timeline[it.startFrame] } ?: 0L
                activeRenderer.drawStereo(eyes.left, eyes.right, framePlan.timeline[data.index] - firstPts)
                drain(end = false)
                val timings = data.timings.copy(
                    encodeMs = SystemClock.uptimeMillis() - encodeStart,
                    depthGeometry = data.timings.depthGeometry?.copy(workingLongEdge = workingLongEdge),
                )
                if (data.index > 0 && !data.cacheHit && rotatedInput != null) {
                    rotationFrames++
                    inputTransformNanos += timings.inputTransformNanos
                    depthRotationNanos += timings.depthRotationNanos
                    peakPrepared = max(peakPrepared, pipeline.inputPrepared.coerceAtMost(4))
                    peakUpright = max(peakUpright, pipeline.depthRestored.coerceAtMost(4))
                }
                timings.depthGeometry?.let { geometry ->
                    if (geometry != lastDepthGeometry) {
                        mark("depth post output=${geometry.preparedWidth}x${geometry.preparedHeight} model=${geometry.modelWidth}x${geometry.modelHeight} upscale=${params.depthUpscale}; memory/output limits apply")
                        lastDepthGeometry = geometry
                    }
                }
                val now = SystemClock.uptimeMillis()
                val throughput = if (!data.cacheHit && lastGeneratedEncodedAt > 0L) now - lastGeneratedEncodedAt else 0L
                if (throughput > 0L) {
                    throughputSamples++
                    totalThroughputMs += throughput
                }
                if (!data.cacheHit) lastGeneratedEncodedAt = now
                val rotationWorkMs = if (data.index > 0 && rotatedInput != null)
                    (timings.inputTransformNanos + timings.depthRotationNanos) / 1_000_000L else 0L
                val activeMs = timings.depthMs + timings.temporalMs + timings.depthPostMs + timings.sbsMs + rotationWorkMs
                val latency = now - data.loadStartedAt
                val metrics = VideoFrameMetrics(
                    throughputMs = throughput, latencyMs = latency,
                    queueWaitMs = (latency - activeMs - timings.decodeMs - timings.cacheLookupMs -
                        (timings.frameRead?.probeMs ?: 0L) - timings.encodeMs).coerceAtLeast(0L),
                    cacheHitReadMs = if (data.cacheHit) timings.cacheLookupMs else 0L, cacheHit = data.cacheHit,
                )
                val stats = VideoPipelineStats(
                    decodeQueue = pipeline.decoded, depthQueue = pipeline.depths, generatedQueue = pipeline.generated,
                    waitingFrames = pipeline.generated, cacheHits = cacheHits.get(),
                    cacheWriteQueue = pipeline.writes, lastCacheWriteMs = pipeline.lastWriteMs,
                    preparedInputQueue = pipeline.inputPrepared.coerceAtMost(4),
                    uprightDepthQueue = pipeline.depthRestored.coerceAtMost(4),
                )
                onProgress((data.index + 1).toFloat() / totalFrames, data.index + 1, totalFrames, fps, metrics, timings, stats)
            }
            try {
                val pipelineResult = OrderedFramePipeline<PipelineFrame>(inFlightLimit, depthWorkerCount).run(
                    first = (segment?.startFrame ?: 0) + 1,
                    endExclusive = segment?.endFrameExclusive ?: totalFrames,
                    checkActive = checkActive,
                    load = { index ->
                        val data = PipelineFrame(index)
                        try {
                            data.loadStartedAt = SystemClock.uptimeMillis()
                            if (segment == null) data.eyes = readCachedStereoFrame(framesDir, index, fallbackFramesDirs)
                            val cacheLookupMs = if (segment == null) SystemClock.uptimeMillis() - data.loadStartedAt else 0L
                            data.cacheHit = data.eyes != null
                            val read = if (data.cacheHit) {
                                cacheHits.incrementAndGet()
                                null
                            } else reader.frameAt(index)
                            data.source = read?.bitmap
                            data.timings = VideoFrameTimings(frameRead = read?.timing, cacheLookupMs = cacheLookupMs)
                            data
                        } catch (error: Throwable) { releaseFrame(data); throw error }
                    },
                    depth = { data, worker ->
                        if (!data.cacheHit) {
                            val session = if (worker == 0 || orientation != null) depthSession.get().value else synchronized(extraSessions) {
                                extraSessions.getOrPut(worker) { frameGenerator.openDepthSession(params, onModelProgress, onRuntimeInfo) }
                            }
                            val source = requireNotNull(data.source)
                            if (rotatedInput == null) {
                                data.depth = frameGenerator.generateVideoDepth(source, session, orientation, workingLongEdge)
                            } else {
                                val tensor = requireNotNull(data.tensor)
                                try {
                                    data.depth = frameGenerator.inferPreparedVideoDepth(source, tensor, rotatedInput, session, data.inputTransformNanos)
                                } finally {
                                    if (tensor !== source) tensor.recycle()
                                    data.tensor = null
                                }
                            }
                        }
                    },
                    generate = { data ->
                        if (!data.cacheHit) {
                            val result = frameGenerator.finishVideoSbs(requireNotNull(data.depth), params, temporalSmoother)
                            data.eyes = result.eyes
                            data.source?.let { if (it !== result.eyes.left && it !== result.eyes.right && !it.isRecycled) it.recycle() }
                            data.source = null
                            data.depth = null
                            data.timings = result.timings.copy(frameRead = data.timings.frameRead, cacheLookupMs = data.timings.cacheLookupMs)
                        }
                        !data.cacheHit && segment == null
                    },
                    persist = { data -> writeStereoFrameCache(framesDir, data.index, requireNotNull(data.eyes)) },
                    encode = ::encodeFrame,
                    dispose = ::releaseFrame,
                    beforeConsume = {
                        encodeFrame(
                            PipelineFrame(segment?.startFrame ?: 0, eyes = firstEyes, cacheHit = firstCacheHit,
                                timings = firstTimings, loadStartedAt = firstLoadStarted),
                            FramePipelineStats(0, 0, 0, 0, 0),
                        )
                    },
                    prepareInput = rotatedInput?.let { geometry ->
                        { data ->
                            if (!data.cacheHit) {
                                val source = requireNotNull(data.source)
                                val started = System.nanoTime()
                                val (working, tensor) = frameGenerator.prepareRotatedVideoInput(source, geometry, workingLongEdge)
                                data.inputTransformNanos = System.nanoTime() - started
                                data.source = working
                                data.tensor = tensor
                                if (working !== source) source.recycle()
                            }
                        }
                    },
                    restoreDepth = rotatedInput?.let {
                        { data -> if (!data.cacheHit) data.depth = requireNotNull(data.depth).restoreRotation() }
                    },
                )
                mark("cache writer completed=" + pipelineResult.writes + " averageMs=" + pipelineResult.writeTimeMs / pipelineResult.writes.coerceAtLeast(1))
                if (rotationFrames > 0) {
                    mark("rotation frames=$rotationFrames meanInputNs=${inputTransformNanos / rotationFrames} meanRestoreNs=${depthRotationNanos / rotationFrames} sampledQueuePeaks=$peakPrepared/$peakUpright")
                }
                if (throughputSamples > 0) {
                    mark("new frame throughput samples=$throughputSamples meanMs=${totalThroughputMs.toDouble() / throughputSamples}")
                }
            } finally {
                extraSessions.values.forEach { it.close() }
            }
            drain(end = true)
            check(muxerStarted) { "Encoder finished without producing a video track" }
            if (audio != null && audioTrack >= 0 && muxerStarted) {
                copyAudio(audio.extractor, muxer, audioTrack)
                mark("audio copied")
            }
            muxer.stop()
            muxerStopped = true
            return lastDepthGeometry
        } finally {
            firstEyes.recycle()
            runCatching { renderer?.release() }
            runCatching { ownedCodec?.stop() }
            runCatching { ownedCodec?.release() }
            runCatching { ownedSurface?.release() }
            runCatching { ownedAudio?.extractor?.release() }
            if (!muxerStopped) runCatching { ownedMuxer?.stop() }
            runCatching { ownedMuxer?.release() }
        }
    }

    private data class AudioSource(val extractor: MediaExtractor, val format: MediaFormat)

    private fun prepareAudioExtractor(item: GalleryItem): AudioSource? {
        val extractor = MediaExtractor()
        try {
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
        } catch (error: Throwable) { extractor.release(); throw error }
    }

    private fun copyAudio(extractor: MediaExtractor, muxer: MediaMuxer, track: Int) {
        val buffer = ByteBuffer.allocateDirect(512 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            val sampleFlags = extractor.sampleFlags
            var codecFlags = 0
            if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
            }
            if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
            }
            info.set(0, size, extractor.sampleTime.coerceAtLeast(0L), codecFlags)
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
    private val positionHandle: Int
    private val texCoordHandle: Int
    private val textureHandle: Int
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
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
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

    fun drawStereo(left: Bitmap, right: Bitmap, presentationTimeUs: Long) {
        makeCurrent()
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        positionBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, positionBuffer)
        texCoordBuffer.position(0)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        val leftWidth = width / 2
        GLES20.glViewport(0, 0, leftWidth, height)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, left, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glViewport(leftWidth, 0, width - leftWidth, height)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, right, 0)
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

internal class DebugExporter(
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
            File(File(entry.paramsPath).parentFile, "model_depth.png").takeIf { it.isFile }?.let { out.addFile("model_depth.png", it) }
            if (entry.hasSplitEyes) {
                val left = File(entry.leftPath)
                val right = File(entry.rightPath)
                out.addFile(left.name, left)
                out.addFile(right.name, right)
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
