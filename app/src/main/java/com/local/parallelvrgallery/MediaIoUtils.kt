package com.local.parallelvrgallerypro

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Rect as BitmapRect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Check
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal fun formatDuration(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(Locale.US, minutes, seconds)
}

internal fun hasImagePermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, imagePermission()) == PackageManager.PERMISSION_GRANTED
}

internal fun hasVideoPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, videoPermission()) == PackageManager.PERMISSION_GRANTED
}

internal fun hasNotificationPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

internal fun imagePermission(): String {
    return if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
}

internal fun videoPermission(): String {
    return if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
}

internal fun notificationPermission(): String {
    return if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else Manifest.permission.READ_EXTERNAL_STORAGE
}

internal fun mediaPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

internal fun decodeScaledBitmap(context: Context, uri: Uri, maxLongEdge: Int): Bitmap {
    if (Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = min(1f, maxLongEdge.toFloat() / max(w, h).toFloat())
            decoder.setTargetSize(max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }.let { decoded ->
            if (decoded.config == Bitmap.Config.ARGB_8888) decoded
            else decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
        }
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

internal fun decodeGeneratedLeftPreview(entry: VrCacheEntry, maxLongEdge: Int): Bitmap {
    if (entry.hasSplitEyes) {
        return decodeScaledBitmap(File(entry.leftPath), maxLongEdge)
    }
    return decodeSbsLeftPreview(File(entry.outputPath), maxLongEdge)
}

internal fun decodeScaledBitmap(file: File, maxLongEdge: Int): Bitmap {
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
            decoder.decodeRegion(BitmapRect(0, 0, leftWidth, height), options)
                ?: error("Unable to decode generated preview: ${file.absolutePath}")
        } finally {
            decoder.recycle()
        }
    }
}

internal fun saveImageToGallery(context: Context, source: File, displayName: String, mimeType: String = "image/jpeg",
    publish: (() -> Unit) -> Unit = { it() }): Uri {
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
        publish {
            if (Build.VERSION.SDK_INT >= 29) {
                check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1) { "Cannot publish saved image" }
            }
        }
    }.onFailure { error ->
        resolver.delete(uri, null, null)
        throw error
    }
    return uri
}

internal fun saveVideoToGallery(context: Context, source: File, displayName: String): Uri {
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

internal fun replaceOriginalImage(context: Context, photo: PhotoItem, source: File) {
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

private fun writeJpegCache(bitmap: Bitmap, file: File, quality: Int) {
    file.parentFile?.mkdirs()
    FileOutputStream(file).buffered(256 * 1024).use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), output)) {
            "JPEG cache encode failed: ${file.absolutePath}"
        }
    }
}

internal data class StereoWriteTimings(
    val leftMs: Long,
    val rightMs: Long,
)

internal fun writeStereoJpegParallel(
    left: Bitmap,
    leftFile: File,
    right: Bitmap,
    rightFile: File,
    quality: Int,
): StereoWriteTimings {
    return runBlocking {
        val leftJob = async(Dispatchers.Default) {
            val start = System.currentTimeMillis()
            writeJpegCache(left, leftFile, quality)
            System.currentTimeMillis() - start
        }
        val rightJob = async(Dispatchers.Default) {
            val start = System.currentTimeMillis()
            writeJpegCache(right, rightFile, quality)
            System.currentTimeMillis() - start
        }
        StereoWriteTimings(leftJob.await(), rightJob.await())
    }
}

internal fun readCachedStereoFrame(framesDir: File, frame: Int, fallbackFramesDirs: List<File> = emptyList()): StereoBitmapPair? {
    return readCachedStereoFrameInDir(framesDir, frame)
        ?: fallbackFramesDirs.asSequence().mapNotNull { readCachedStereoFrameInDir(it, frame) }.firstOrNull()
}

private fun readCachedStereoFrameInDir(framesDir: File, frame: Int): StereoBitmapPair? {
    val frameNumber = frame.toString().padStart(6, '0')
    if (framesDir.parentFile?.name?.endsWith("_$VIDEO_ENCODER_VERSION") == true &&
        !File(framesDir, "$frameNumber.complete").isFile) return null
    val splitEyes = listOf(
        File(framesDir, "left_$frameNumber.jpg") to File(framesDir, "right_$frameNumber.jpg"),
        File(framesDir, "left_$frameNumber.webp") to File(framesDir, "right_$frameNumber.webp"),
    ).firstOrNull { (left, right) -> left.exists() && right.exists() }
    if (splitEyes != null) {
        val (left, right) = splitEyes
        val leftBitmap = BitmapFactory.decodeFile(left.absolutePath) ?: return null
        val rightBitmap = BitmapFactory.decodeFile(right.absolutePath)
        if (rightBitmap == null) {
            leftBitmap.recycle()
            return null
        }
        if (leftBitmap.width != rightBitmap.width || leftBitmap.height != rightBitmap.height) {
            leftBitmap.recycle()
            rightBitmap.recycle()
            return null
        }
        return StereoBitmapPair(leftBitmap, rightBitmap)
    }
    val legacy = File(framesDir, "frame_$frameNumber.jpg")
    if (!legacy.exists()) return null
    val sbs = BitmapFactory.decodeFile(legacy.absolutePath) ?: return null
    return try {
        val half = (sbs.width / 2).coerceAtLeast(1)
        StereoBitmapPair(
            left = Bitmap.createBitmap(sbs, 0, 0, half, sbs.height),
            right = Bitmap.createBitmap(sbs, half, 0, sbs.width - half, sbs.height),
        )
    } finally {
        sbs.recycle()
    }
}

internal fun writeStereoFrameCache(framesDir: File, frame: Int, eyes: StereoBitmapPair) {
    framesDir.mkdirs()
    val frameNumber = frame.toString().padStart(6, '0')
    val complete = File(framesDir, "$frameNumber.complete")
    val left = File(framesDir, "left_$frameNumber.jpg.part")
    val right = File(framesDir, "right_$frameNumber.jpg.part")
    complete.delete()
    try {
        writeStereoJpegParallel(eyes.left, left, eyes.right, right, VIDEO_FRAME_JPEG_QUALITY)
        java.nio.file.Files.move(left.toPath(), File(framesDir, "left_$frameNumber.jpg").toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        java.nio.file.Files.move(right.toPath(), File(framesDir, "right_$frameNumber.jpg").toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        complete.writeText("split-jpeg-complete")
    } finally {
        left.delete()
        right.delete()
    }
}

internal fun composeSbsBitmap(left: Bitmap, right: Bitmap): Bitmap {
    val width = left.width + right.width
    val height = max(left.height, right.height)
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawBitmap(left, 0f, 0f, null)
    canvas.drawBitmap(right, left.width.toFloat(), 0f, null)
    return output
}

internal fun composeSbsBitmap(entry: VrCacheEntry): Bitmap {
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

internal fun materializeSbsJpeg(entry: VrCacheEntry, quality: Int = 96): File {
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
    val output = File(dir, "export_sbs_verified.avif")
    if (output.exists()) return output
    val bitmap = composeSbsBitmap(entry)
    val evenBitmap = bitmap.ensureEvenDimensions()
    try {
        LosslessImageExport.avif(evenBitmap,output)
    } finally {
        if (evenBitmap !== bitmap) evenBitmap.recycle()
        bitmap.recycle()
    }
    return output
}

private fun materializeSbsPng(entry:VrCacheEntry):File {
    val dir=File(entry.paramsPath).parentFile ?: error("Missing cache directory")
    val output=File(dir,"export_sbs.png")
    if(output.isFile)return output
    val bitmap=composeSbsBitmap(entry)
    try {LosslessImageExport.png(bitmap,output)}finally{bitmap.recycle()}
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

internal fun loadModelFile(file: File): ByteBuffer {
    FileInputStream(file).use { input ->
        val channel = input.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
    }
}

internal fun VrGenerationParams.toJson(photo: PhotoItem, source: Bitmap, outputWidth: Int, outputHeight: Int,
    timings: GenerationTimings, depth: DepthMap, inference: DepthInferenceOutput): String {
    val existing = org.json.JSONObject()
        .put("photoKey", photo.cacheKey).put("cacheVersion", cacheVersion()).put("displayName", photo.displayName)
        .put("projection", when { edgeMode == DepthEdgeMode.COMFY_SUBPIXEL_V11 -> "SSS_MAIN_SUBPIXEL_COVERAGE_V11"; edgeMode.isComfyMain -> "SSS_MAIN_LEGACY_30372fec"; edgeMode.isReference -> "SSS_EXTERNAL_V2_2_30372fec"; else -> "LEGACY_PHONE" })
        .put("projectionDepthPreparation", when { edgeMode.isComfyMain -> "FLOAT_CUBIC_EYE_SIZE_RENORMALIZE_GAUSSIAN"; edgeMode == DepthEdgeMode.REFERENCE_V9 -> "FLOAT_LINEAR_EYE_SIZE_GAUSSIAN_3_BEFORE_QUANTIZE"; else -> "HISTORICAL" })
        .put("inpaint", when { edgeMode == DepthEdgeMode.COMFY_SUBPIXEL_V11 -> "DEPTH_AWARE_SUBPIXEL_COVERAGE"; edgeMode.isComfyMain -> "FORWARD_OVERWRITE_SHIFT_PLUS_10"; edgeMode.isReference -> "OpenCV_4_12_TELEA_radius_3"; else -> "HORIZONTAL_FILL" })
        .put("legacyFilterAndFillApplied", !edgeMode.isReference && !edgeMode.isComfyMain)
        .put("sourceWidth", source.width).put("sourceHeight", source.height)
        .put("outputWidth", outputWidth).put("outputHeight", outputHeight)
        .put("depthModel", depthModel).put("outputMode", "SPLIT_EYES_JPEG_Q$IMAGE_EYE_JPEG_QUALITY")
        .put("invertModelDepth", invertModelDepth).put("expectedModelDepthConvention", "near-white / far-black; explicit input polarity")
        .put("projectionNearWhiteForCanonicalInput", DepthDirection.projectionNearWhite(edgeMode, invertDepth))
        .put("depthScale", depthScale.toDouble()).put("effectiveDepthScale", effectiveScale(source.width, depth).toDouble()).put("matchV10Default", matchV10Default).put("blurRadius", blurRadius).put("edgeMode", edgeMode.name)
        .put("fillRadius", fillRadius).put("invertDepth", invertDepth).put("maxLongEdge", maxLongEdge)
        .put("modelThreads", modelThreads).put("useGpu", useGpu).put("gpuTestMode", gpuTestMode.name)
        .put("forceGpuNoFallback", forceGpuNoFallback).put("inpaintMode", inpaintMode).put("quality", quality)
        .put("timings", org.json.JSONObject()
            .put("decodeMs", timings.decodeMs).put("modelMs", timings.modelMs).put("depthPostMs", timings.depthPostMs)
            .put("sbsMs", timings.sbsMs).put("writeLeftMs", timings.writeLeftMs).put("writeRightMs", timings.writeRightMs)
            .put("writeParamsMs", timings.writeParamsMs).put("writeLogMs", timings.writeLogMs)
            .put("writeMs", timings.writeMs).put("totalMs", timings.totalMs))
        .put("modelSha256", modelSpec(depthModel).sha256).put("modelSource", modelSpec(depthModel).url)
    existing.put("modelInputWidth", inference.inputWidth).put("modelInputHeight", inference.inputHeight)
        .put("modelDepthContentWidth", inference.geometry?.contentWidth ?: inference.map.width)
        .put("modelDepthContentHeight", inference.geometry?.contentHeight ?: inference.map.height)
        .put("modelDepthPadLeft", inference.geometry?.left ?: 0).put("modelDepthPadTop", inference.geometry?.top ?: 0)
        .put("modelInputRotation", inference.geometry?.rotation ?: 0)
        .put("modelDepthUprightWidth", inference.map.width).put("modelDepthUprightHeight", inference.map.height)
        .put("depthWidth", depth.width).put("depthHeight", depth.height)
        .put("depthUpscale", org.json.JSONObject()
            .put("enabled", depthUpscale.enabled)
            .put("maxLongEdge", depthUpscale.boundedLongEdge)
            .put("interpolation", depthUpscale.interpolation.name)
            .put("order", depthUpscale.order.name))
    inference.rotationTiming?.let {
        existing.put("modelInputTransformNanos", it.inputTransformNanos)
            .put("modelOutputRotationNanos", it.outputRotationNanos)
    }
    return existing.toString(2)
}

internal fun VrGenerationParams.depthSessionKey(): String =
    "$depthModel|threads=${modelThreads.coerceIn(1, 8)}|gpu=$useGpu|gpuMode=$gpuTestMode|force=$forceGpuNoFallback"

internal fun VrGenerationParams.visualGenerationVersion(): String {
    val scale = if (depthScale == depthScale.roundToInt().toFloat()) depthScale.roundToInt().toString() else depthScale.toString()
    val invert = if (invertDepth) "inv1" else "inv0"
    val gpu = if (useGpu) "gpu1" else "gpu0"
    val force = if (forceGpuNoFallback) "force1" else "force0"
    return "${depthModel}_${edgeMode.cacheVersion}_s${scale}_b${blurRadius}_f${fillRadius}_${invert}_m${maxLongEdge}_t${modelThreads}_${gpu}_${gpuTestMode}_$force${depthUpscale.cacheSuffix()}${if (matchV10Default) "_eq1050" else ""}${if (invertModelDepth) "_di1" else ""}${if (resultVariant.isNotEmpty()) "_take${resultVariant}" else ""}"
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
}

internal fun VrGenerationParams.cacheVersion(): String {
    return "${visualGenerationVersion()}_$IMAGE_CACHE_ENCODER_VERSION"
}

internal fun VideoGenerationParams.cacheVersion(): String {
    return "${toVrParams().visualGenerationVersion()}_dw${depthWorkers.coerceIn(1, 2)}_$VIDEO_ENCODER_VERSION"
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
}

internal fun Throwable.shortMessage(): String {
    val type = this::class.java.simpleName.ifBlank { "Error" }
    val message = message?.replace('\n', ' ')?.replace('\r', ' ')?.take(180)
    return if (message.isNullOrBlank()) type else "$type:$message"
}

internal fun Float.format3(): String = "%.3f".format(Locale.US, this)

internal fun compareVersionTags(left: String, right: String): Int = UpdatePolicy.compare(left, right)

internal fun File.sha256(): String {
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

internal fun ZipOutputStream.addFile(name: String, file: File) {
    putNextEntry(ZipEntry(name))
    file.inputStream().use { it.copyTo(this) }
    closeEntry()
}
