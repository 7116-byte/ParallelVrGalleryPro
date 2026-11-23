package com.local.parallelvrgallerypro

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel

internal interface DepthInferenceRuntime : Closeable {
    fun prepare()
    fun run(bitmap: Bitmap): FloatArray

    fun prepareQnnShape(geometry: QnnInputGeometry, allowNativeLandscapeOverride: Boolean = false) {
        error("This depth model cannot prepare a separate QNN shape: $geometry")
    }

    /** Read a video station's tensor-size bitmap. The caller retains ownership. */
    fun inferPreparedVideo(bitmap: Bitmap, geometry: QnnInputGeometry): DepthInferenceOutput {
        error("This depth model does not accept a prepared video tensor: $geometry")
    }

    fun infer(
        bitmap: Bitmap,
        geometryOverride: QnnInputGeometry? = null,
        allowNativeLandscapeOverride: Boolean = false,
    ): DepthInferenceOutput {
        require(geometryOverride == null) { "Recorded QNN geometry needs an adaptive QNN model" }
        return DepthInferenceOutput(DepthTensorShape.map(run(bitmap)))
    }
}

internal data class DepthInferenceOutput(
    val map: DepthMap,
    val inputWidth: Int = map.width,
    val inputHeight: Int = map.height,
    val geometry: QnnInputGeometry? = null,
    val rotationTiming: QnnDepthRotationTiming? = null,
)

internal data class QnnDepthRotationTiming(
    val inputTransformNanos: Long,
    val inputPackingNanos: Long,
    val outputRotationNanos: Long,
) {
    // The fused image transform includes resizing as well as rotation.
    val totalNanos: Long get() = inputTransformNanos + outputRotationNanos
}

/** Created, used and closed by the shared GPU owner in [DepthModelSession]. */
internal class QnnFp16DepthRuntime private constructor(
    private val session: OrtSession,
    environment: OrtEnvironment,
    private val width: Int,
    private val height: Int,
    private val budgetSize: Int,
) : DepthInferenceRuntime {
    private val count = width * height
    private val inputShape = longArrayOf(1, 3, height.toLong(), width.toLong())
    private val outputShape = longArrayOf(1, height.toLong(), width.toLong())

    init {
        requireTensor(session.inputInfo[INPUT]?.info as? TensorInfo, inputShape, INPUT)
        requireTensor(session.outputInfo[OUTPUT]?.info as? TensorInfo, outputShape, OUTPUT)
    }

    private val pixels = IntArray(count)
    private val rgb = floatBuffer(3 * count)
    private val depth = floatBuffer(count)
    private val input = OnnxTensor.createTensor(environment, rgb, inputShape)
    private val output = try {
        OnnxTensor.createTensor(environment, depth, outputShape)
    } catch (error: Throwable) {
        runCatching { input.close() }.exceptionOrNull()?.let(error::addSuppressed)
        throw error
    }
    private val inputs = mapOf(INPUT to input)
    private val outputs = mapOf(OUTPUT to output)
    private var rotatedDepthScratch: FloatArray? = null

    // ORT has already prepared its graph when the session was created.
    override fun prepare() = Unit

    override fun run(bitmap: Bitmap): FloatArray = infer(bitmap).map.values

    override fun infer(
        bitmap: Bitmap,
        geometryOverride: QnnInputGeometry?,
        allowNativeLandscapeOverride: Boolean,
    ): DepthInferenceOutput {
        require(geometryOverride == null) { "Recorded QNN geometry needs an adaptive QNN model" }
        return predict(bitmap, QnnInputGeometry(width, height, width, height, 0, 0), reportGeometry = false)
    }

    private fun predict(
        bitmap: Bitmap,
        geometry: QnnInputGeometry,
        reportGeometry: Boolean,
        preparedVideo: Boolean = false,
    ): DepthInferenceOutput {
        require(geometry.tensorWidth == width && geometry.tensorHeight == height)
        val rotated = !preparedVideo && geometry.rotation == 90
        require(!rotated || geometry.coversTensor())
        val prepareStarted = System.nanoTime()
        val scaled = if (preparedVideo) bitmap else transformQnnInput(bitmap, geometry)
        val transformFinished = if (rotated) System.nanoTime() else prepareStarted
        try {
            scaled.getPixels(pixels, 0, geometry.contentWidth, 0, 0, geometry.contentWidth, geometry.contentHeight)
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
        val packStarted = System.nanoTime()
        if (geometry.coversTensor()) {
            for (index in pixels.indices) packPixel(index, pixels[index])
        } else {
            // Bilinear scaling preserves the content aspect ratio. Repeating the nearest
            // content pixel at its borders avoids inventing black edges before inference.
            for (y in 0 until height) {
                val contentRow = (y - geometry.top).coerceIn(0, geometry.contentHeight - 1) * geometry.contentWidth
                val tensorRow = y * width
                for (x in 0 until width) {
                    val contentColumn = (x - geometry.left).coerceIn(0, geometry.contentWidth - 1)
                    packPixel(tensorRow + x, pixels[contentRow + contentColumn])
                }
            }
        }
        val invokeStarted = System.nanoTime()
        session.run(inputs, outputs).use { }
        val readStarted = System.nanoTime()
        val values: FloatArray
        val rotationTiming = if (rotated) {
            val scratch = rotatedDepthScratch ?: FloatArray(count).also { rotatedDepthScratch = it }
            depth.duplicate().get(scratch)
            val turnStarted = System.nanoTime()
            values = rotateQnnDepthCounterClockwise(scratch, width, height)
            QnnDepthRotationTiming(transformFinished - prepareStarted, invokeStarted - transformFinished, System.nanoTime() - turnStarted)
        } else {
            values = readQnnDepthContent(depth, geometry)
            null
        }
        normalizeQnnDepth(values)
        InferenceProfiling.listener?.invoke(
            budgetSize, packStarted - prepareStarted, invokeStarted - packStarted,
            readStarted - invokeStarted, System.nanoTime() - readStarted,
        )
        val map = if (rotated) DepthMap(values, geometry.contentHeight, geometry.contentWidth)
        else DepthMap(values, geometry.contentWidth, geometry.contentHeight)
        return DepthInferenceOutput(map, width, height, geometry.takeIf { reportGeometry }, rotationTiming)
    }

    private fun packPixel(index: Int, color: Int) {
        rgb.put(index, red[(color ushr 16) and 255])
        rgb.put(index + count, green[(color ushr 8) and 255])
        rgb.put(index + count * 2, blue[color and 255])
    }

    override fun close() {
        session.use { input.use { output.close() } }
    }

    private class AdaptiveRuntime(
        private val model: ByteBuffer,
        private val backend: File,
        private val config: QnnModelConfig,
        private val contextCache: QnnShapeContextCache?,
    ) : DepthInferenceRuntime {
        private data class Shape(val width: Int, val height: Int)

        // Wide/tall has a known pair of orientations. At higher budgets the arbitrary
        // aspect modes continue to keep a single resident graph.
        private val capacity = if (config.aspect.hasFixedPair() || config.size <= 770) 2 else 1
        private val shapes = LinkedHashMap<Shape, QnnFp16DepthRuntime>(capacity, 0.75f, true)
        private var previousShape: Shape? = null
        private var sourceWidth = 0
        private var sourceHeight = 0
        private var sourceGeometry: QnnInputGeometry? = null

        // Preparing an adaptive graph requires media dimensions. Keep the selected model
        // mapped, then compile the first shape when its source is accepted by the owner.
        override fun prepare() = Unit

        override fun prepareQnnShape(geometry: QnnInputGeometry, allowNativeLandscapeOverride: Boolean) {
            require(config.aspect.hasFixedPair() && geometry.rotation == 0 &&
                geometry.matchesLayout(config.size, config.aspect, allowNativeLandscapeOverride)) {
                "Unexpected QNN warmup shape: $geometry"
            }
            val shape = Shape(geometry.tensorWidth, geometry.tensorHeight)
            if (shapes[shape] == null) openShape(shape) else logReuse(shape)
        }

        override fun run(bitmap: Bitmap): FloatArray = infer(bitmap).map.values

        override fun infer(
            bitmap: Bitmap,
            geometryOverride: QnnInputGeometry?,
            allowNativeLandscapeOverride: Boolean,
        ): DepthInferenceOutput {
            val geometry = geometryOverride?.also {
                require(it.matchesLayout(config.size, config.aspect, allowNativeLandscapeOverride)) { "Unexpected recorded QNN geometry: $it" }
            } ?: geometryFor(bitmap)
            return inferWithGeometry(bitmap, geometry, preparedVideo = false)
        }

        override fun inferPreparedVideo(bitmap: Bitmap, geometry: QnnInputGeometry): DepthInferenceOutput {
            require(geometry.matchesLayout(config.size, config.aspect, allowNativeLandscapeOverride = true) &&
                geometry.coversTensor() && bitmap.width == geometry.tensorWidth && bitmap.height == geometry.tensorHeight) {
                "Unexpected prepared video tensor: ${bitmap.width}x${bitmap.height}, $geometry"
            }
            return inferWithGeometry(bitmap, geometry, preparedVideo = true)
        }

        private fun inferWithGeometry(bitmap: Bitmap, geometry: QnnInputGeometry, preparedVideo: Boolean): DepthInferenceOutput {
            val previous = previousShape
            val shape = previous?.takeIf { it.width == geometry.tensorWidth && it.height == geometry.tensorHeight }
                ?: Shape(geometry.tensorWidth, geometry.tensorHeight)
            val existing = shapes[shape]
            val runtime = existing ?: openShape(shape)
            if (existing != null && previous != shape) logReuse(shape)
            previousShape = shape
            return runtime.predict(bitmap, geometry, reportGeometry = true, preparedVideo = preparedVideo)
        }

        private fun openShape(shape: Shape): QnnFp16DepthRuntime {
            if (shapes.size >= capacity) shapes.remove(shapes.keys.first())?.close()
            return openForShape(model, backend, config, shape.width, shape.height, contextCache) { Log.i(LOG_TAG, it) }
                .also { shapes[shape] = it }
        }

        private fun logReuse(shape: Shape) {
            Log.i(LOG_TAG, "qnn gpu reuses input=${shape.width}x${shape.height} aspect=${config.aspect.id} source=resident setupMs=0")
        }

        private fun geometryFor(bitmap: Bitmap): QnnInputGeometry {
            val width = bitmap.width
            val height = bitmap.height
            val cached = sourceGeometry
            if (cached != null && sourceWidth == width && sourceHeight == height) return cached
            val geometry = QnnInputGeometry.forSource(config.size, width, height, config.aspect)
            sourceWidth = width
            sourceHeight = height
            sourceGeometry = geometry
            return geometry
        }

        override fun close() {
            var failure: Throwable? = null
            for (runtime in shapes.values) {
                try {
                    runtime.close()
                } catch (error: Throwable) {
                    val previous = failure
                    if (previous == null) failure = error else previous.addSuppressed(error)
                }
            }
            shapes.clear()
            failure?.let { throw it }
        }
    }

    companion object {
        private const val INPUT = "pixel_values"
        private const val OUTPUT = "predicted_depth"
        private const val BACKEND = "libQnnGpu.so"
        private const val LOG_TAG = "PvgQnnDepth"
        private val contextCacheWrites = QnnShapeContextCache.WriteGate()
        private val red = rgbNorm(0.485f, 0.229f)
        private val green = rgbNorm(0.456f, 0.224f)
        private val blue = rgbNorm(0.406f, 0.225f)

        fun hasGpuBackend(context: Context): Boolean {
            if (!Process.is64Bit() || Build.SUPPORTED_64_BIT_ABIS.firstOrNull() != "arm64-v8a") return false
            val qualcomm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manufacturer = Build.SOC_MANUFACTURER
                manufacturer.equals("Qualcomm", ignoreCase = true) ||
                    manufacturer.startsWith("Qualcomm ", ignoreCase = true) ||
                    manufacturer.equals("QTI", ignoreCase = true)
            } else {
                Build.HARDWARE.equals("qcom", ignoreCase = true)
            }
            return qualcomm && File(context.applicationInfo.nativeLibraryDir, BACKEND).isFile
        }

        fun open(context: Context, config: QnnModelConfig, onRuntimeInfo: (String) -> Unit): DepthInferenceRuntime {
            require(isQnnFp16Size(config.size)) { "Unknown QNN input size: ${config.size}" }
            check(hasGpuBackend(context)) { "QNN GPU 不可用 / QNN GPU unavailable" }
            val model = context.assets.openFd(config.variant.assetName).use { asset ->
                FileInputStream(asset.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, asset.startOffset, asset.length)
                }
            }
            val backend = File(context.applicationInfo.nativeLibraryDir, BACKEND)
            return if (config.aspect == QnnDepthAspect.SQUARE) {
                openForShape(model, backend, config, config.size, config.size, onRuntimeInfo = onRuntimeInfo)
            } else {
                onRuntimeInfo("qnn gpu creates ${config.aspect.id} shapes as requested, budget=${config.size}²")
                val cache = if (QnnShapeContextCache.supports(config.aspect)) createContextCache(context, backend) else null
                AdaptiveRuntime(model, backend, config, cache)
            }
        }

        private fun createContextCache(context: Context, backend: File): QnnShapeContextCache? {
            val directory = context.cacheDir ?: return null
            // The OS/vendor build and driver file stamps change when the on-device
            // runtime environment changes. A rejected context always goes through JIT.
            val nativeDirectory = backend.parentFile ?: return null
            val binaries = listOf(backend, File(nativeDirectory, "libQnnSystem.so"), File(nativeDirectory, "libonnxruntime.so"),
                File("/vendor/lib64/egl/libGLESv2_adreno.so"), File("/vendor/lib64/libOpenCL.so"))
            val compatibility = buildString {
                appendLine("embedded-gpu-aligned-v1")
                appendLine("${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_CODE}")
                appendLine(Build.FINGERPRINT)
                appendLine(Build.VERSION.INCREMENTAL)
                appendLine("${Build.HARDWARE}/${Build.BOARD}/qairt-2.42.0")
                appendLine(OrtEnvironment.getEnvironment().version)
                for (file in binaries) appendLine("${file.name}:${file.length()}:${file.lastModified()}")
            }
            return QnnShapeContextCache(File(directory, "qnn-shape-context"), compatibility, contextCacheWrites)
        }

        private fun openForShape(
            model: ByteBuffer,
            backend: File,
            config: QnnModelConfig,
            width: Int,
            height: Int,
            cache: QnnShapeContextCache? = null,
            onRuntimeInfo: (String) -> Unit,
        ): QnnFp16DepthRuntime {
            val environment = OrtEnvironment.getEnvironment()
            environment.setTelemetry(false)
            val started = System.nanoTime()
            fun create(cachedFile: File? = null, candidate: File? = null): QnnFp16DepthRuntime {
                val session = OrtSession.SessionOptions().use { options ->
                    options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                    options.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_WARNING)
                    options.setIntraOpNumThreads(1)
                    options.setSymbolicDimensionValue("batch_size", 1)
                    options.setSymbolicDimensionValue("height", height.toLong())
                    options.setSymbolicDimensionValue("width", width.toLong())
                    options.addConfigEntry("session.disable_cpu_ep_fallback", "1")
                    options.addQnn(mapOf("backend_path" to backend.absolutePath))
                    if (candidate != null) {
                        options.addConfigEntry("ep.context_enable", "1")
                        options.addConfigEntry("ep.context_embed_mode", "1")
                        options.addConfigEntry("ep.context_file_path", candidate.absolutePath)
                    }
                    if (cachedFile == null) environment.createSession(model.duplicate(), options)
                    else environment.createSession(cachedFile.absolutePath, options)
                }
                return try {
                    QnnFp16DepthRuntime(session, environment, width, height, config.size)
                } catch (error: Throwable) {
                    runCatching { session.close() }.exceptionOrNull()?.let(error::addSuppressed)
                    throw error
                }
            }
            val result = cache?.open(config.variant.sha256, width, height, { create(cachedFile = it) }, { create(candidate = it) })
            val runtime = result?.value ?: create()
            val source = result?.source ?: QnnShapeContextCache.Source.FRESH
            try {
                val setupMs = (System.nanoTime() - started) / 1_000_000L
                onRuntimeInfo("qnn gpu runtime input=${width}x$height precision=${config.variant.displayPrecision} aspect=${config.aspect.id} backend=$BACKEND noCpuFallback=true source=${source.label} setupMs=$setupMs")
                return runtime
            } catch (error: Throwable) {
                runCatching { runtime.close() }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
        }

        private fun requireTensor(info: TensorInfo?, shape: LongArray, name: String) {
            require(info?.type == OnnxJavaType.FLOAT && info.shape.contentEquals(shape)) {
                "Unexpected QNN depth tensor '$name': $info"
            }
        }

        private fun floatBuffer(size: Int): FloatBuffer =
            ByteBuffer.allocateDirect(size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()

        private fun rgbNorm(mean: Float, std: Float) = FloatArray(256) { (it / 255f - mean) / std }
    }
}

private fun QnnInputGeometry.coversTensor(): Boolean = tensorWidth == contentWidth && tensorHeight == contentHeight

private fun QnnDepthAspect.hasFixedPair(): Boolean = this == QnnDepthAspect.WIDE_TALL || this == QnnDepthAspect.PORTRAIT_PAIR

/** Resample and orient the visible model content in one operation. Recycle only a new bitmap. */
internal fun transformQnnInput(bitmap: Bitmap, geometry: QnnInputGeometry): Bitmap {
    if (geometry.rotation == 0) return Bitmap.createScaledBitmap(bitmap, geometry.contentWidth, geometry.contentHeight, true)
    // Scale and rotate in one rasterization. createBitmap preserves the bitmap color space,
    // and getPixels converts to the same sRGB integers used by the unrotated path.
    val transform = Matrix()
    transform.setScale(geometry.contentHeight.toFloat() / bitmap.width, geometry.contentWidth.toFloat() / bitmap.height)
    transform.postRotate(90f)
    val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
    if (transformed.width != geometry.contentWidth || transformed.height != geometry.contentHeight) {
        if (transformed !== bitmap) transformed.recycle()
        error("Unexpected raster dimensions for $geometry")
    }
    return transformed
}

/** Matrix input is clockwise. Restore a row-major float map to the source orientation. */
internal fun rotateQnnDepthCounterClockwise(values: FloatArray, width: Int, height: Int): FloatArray {
    require(width > 0 && height > 0 && width.toLong() * height == values.size.toLong())
    val turned = FloatArray(values.size)
    // A tile keeps the strided source lines hot while writing contiguous output rows.
    val tile = 32
    for (top in 0 until width step tile) {
        for (left in 0 until height step tile) {
            for (y in top until minOf(top + tile, width)) {
                var from = left * width + width - 1 - y
                var to = y * height + left
                val length = minOf(tile, height - left)
                repeat(length) {
                    turned[to++] = values[from]
                    from += width
                }
            }
        }
    }
    return turned
}

/** Extract the visible content before min/max normalization so padding cannot affect its depth range. */
internal fun readQnnDepthContent(depth: FloatBuffer, geometry: QnnInputGeometry): FloatArray {
    val values = FloatArray(geometry.contentWidth * geometry.contentHeight)
    val source = depth.duplicate()
    if (geometry.coversTensor()) {
        source.get(values)
    } else {
        for (y in 0 until geometry.contentHeight) {
            source.position((y + geometry.top) * geometry.tensorWidth + geometry.left)
            source.get(values, y * geometry.contentWidth, geometry.contentWidth)
        }
    }
    return values
}

internal fun normalizeQnnDepth(values: FloatArray) {
    var minimum = Float.POSITIVE_INFINITY
    var maximum = Float.NEGATIVE_INFINITY
    for (value in values) {
        check(value.isFinite()) { "QNN GPU 深度含无效数值 / Non-finite QNN GPU depth" }
        minimum = minOf(minimum, value)
        maximum = maxOf(maximum, value)
    }
    val range = maximum - minimum
    check(range.isFinite() && range > 0.0001f) { "QNN GPU 深度变化不足 / Flat QNN GPU depth" }
    for (index in values.indices) values[index] = (values[index] - minimum) / range
}
