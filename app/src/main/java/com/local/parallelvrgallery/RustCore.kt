package com.local.parallelvrgallerypro

import android.graphics.Bitmap
import androidx.annotation.Keep
import java.io.File
import java.nio.ByteBuffer

/**
 * Single-source-of-truth native Rust core (`libpvg_core.so` on Android, `pvg_core.dll`/`.so` on Host JVM).
 * All compute kernels, depth processing, geometry, timeline validation, and atomic cache I/O execute in Rust.
 */
@Keep
internal object RustCore {
    init {
        loadNativeLibrary()
    }

    private fun loadNativeLibrary() {
        try {
            System.loadLibrary("pvg_core")
            return
        } catch (primary: UnsatisfiedLinkError) {
            val hostDir = System.getProperty("pvg.rust.host.dir")
            if (!hostDir.isNullOrBlank()) {
                val file = File(hostDir, System.mapLibraryName("pvg_core"))
                if (file.isFile) {
                    System.load(file.absolutePath)
                    return
                }
            }
            throw UnsatisfiedLinkError(
                "Failed to load native Rust core 'pvg_core' (pvg.rust.host.dir=$hostDir): ${primary.message}",
            ).apply { initCause(primary) }
        }
    }

    fun abiInfo(): String = nativeAbiInfo()

    fun projectComfy(source: IntArray, depth: FloatArray, width: Int, height: Int, scale: Float, subpixel: Boolean): IntArray =
        nativeProjectComfy(source, depth, width, height, scale, subpixel)

    fun projectReference(
        source: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
        depthWidth: Int,
        depthHeight: Int,
        scale: Float,
        pixelsOut: IntArray,
        holesOut: ByteArray,
    ) = nativeProjectReference(source, depth, width, height, depthWidth, depthHeight, scale, pixelsOut, holesOut)

    fun projectPhoneStereo(
        source: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
        depthWidth: Int,
        depthHeight: Int,
        scale: Float,
        fillRadius: Int,
    ): IntArray = nativeProjectPhoneStereo(source, depth, width, height, depthWidth, depthHeight, scale, fillRadius)

    fun filterPhoneDepth(depth: FloatArray, width: Int, height: Int, diameter: Int, invert: Boolean, centered: Boolean): FloatArray =
        nativeFilterPhoneDepth(depth, width, height, diameter, invert, centered)

    fun normalizeModelDepth(values: FloatArray, strict: Boolean): Float = nativeNormalizeModelDepth(values, strict)

    fun readNormalizedDirect(buffer: ByteBuffer, count: Int, outValues: FloatArray): Float =
        nativeReadNormalizedDirect(buffer, count, outValues)

    fun packTfliteRgbDirect(pixels: IntArray, outDirectBuffer: ByteBuffer): Boolean =
        nativePackTfliteRgbDirect(pixels, outDirectBuffer)

    fun rotateDepthCcw90(values: FloatArray, width: Int, height: Int): FloatArray =
        nativeRotateDepthCcw90(values, width, height)

    fun extractQnnDepthDirect(
        directBuffer: ByteBuffer,
        tensorWidth: Int,
        tensorHeight: Int,
        contentWidth: Int,
        contentHeight: Int,
        left: Int,
        top: Int,
        rotateCcw: Boolean,
    ): FloatArray = nativeExtractQnnDepthDirect(
        directBuffer, tensorWidth, tensorHeight, contentWidth, contentHeight, left, top, rotateCcw,
    )

    fun disparitySpan(values: FloatArray): Float = nativeDisparitySpan(values)

    fun resizeDepth(source: FloatArray, sourceWidth: Int, sourceHeight: Int, width: Int, height: Int, method: Int): FloatArray =
        nativeResizeDepth(source, sourceWidth, sourceHeight, width, height, method)

    fun depthTargetSize(width: Int, height: Int, longEdge: Int, maxPixels: Long): Long =
        nativeDepthTargetSize(width, height, longEdge, maxPixels)

    fun postProcessDepth(
        source: FloatArray,
        sourceWidth: Int,
        sourceHeight: Int,
        width: Int,
        height: Int,
        mode: Int,
        diameter: Int,
        method: Int,
        invert: Boolean,
        resizeFirst: Boolean,
    ): FloatArray = nativePostProcessDepth(
        source, sourceWidth, sourceHeight, width, height, mode, diameter, method, invert, resizeFirst,
    )

    fun smoothTemporal(current: FloatArray, previous: FloatArray, previousWeight: Float, sceneCut: Float, motion: Float): Boolean =
        nativeSmoothTemporal(current, previous, previousWeight, sceneCut, motion)

    fun mapDepthDirection(source: FloatArray, invert: Boolean, clamp: Boolean = false): FloatArray =
        nativeMapDepthDirection(source, invert, clamp)

    fun qnnInputGeometry(size: Int, sourceWidth: Int, sourceHeight: Int, aspect: Int): IntArray =
        nativeQnnInputGeometry(size, sourceWidth, sourceHeight, aspect)

    fun sortVideoTimeline(samples: LongArray): LongArray = nativeSortVideoTimeline(samples)

    fun checkSegmentCoverage(packed: IntArray, totalFrames: Int) = nativeCheckSegmentCoverage(packed, totalFrames)

    fun validateSegmentTimestamp(
        local: Long,
        previous: Long,
        first: Long,
        expected: Long,
        initial: Boolean,
        sync: Boolean,
    ): Int = nativeValidateSegmentTimestamp(local, previous, first, expected, initial, sync)

    fun segmentRange(total: Int, framesPerSegment: Int, index: Int): Long =
        nativeSegmentRange(total, framesPerSegment, index)

    fun writeDepthCheckpoint(path: String, width: Int, height: Int, values: FloatArray) =
        nativeWriteDepthCheckpoint(path, width, height, values)

    fun readDepthCheckpoint(path: String, shapeOut: IntArray): FloatArray =
        nativeReadDepthCheckpoint(path, shapeOut)

    fun reclaimFrameJpegs(dirPath: String, first: Int, end: Int) =
        nativeReclaimFrameJpegs(dirPath, first, end)

    fun publishImage(fields: Array<String>, recover: Boolean): String? =
        nativePublishImage(fields, recover)

    // --- Android Zero-Copy Bitmap Entrypoints (libjnigraphics.so) ---

    fun projectComfyBitmap(source: Bitmap, dest: Bitmap, depth: FloatArray, scale: Float, subpixel: Boolean): Boolean =
        nativeProjectComfyBitmap(source, dest, depth, scale, subpixel)

    fun projectPhoneBitmap(
        source: Bitmap,
        dest: Bitmap,
        depth: FloatArray,
        depthWidth: Int,
        depthHeight: Int,
        scale: Float,
        fillRadius: Int,
    ): Boolean = nativeProjectPhoneBitmap(source, dest, depth, depthWidth, depthHeight, scale, fillRadius)

    fun projectReferenceBitmapToBgr(
        source: Bitmap,
        depth: FloatArray,
        depthWidth: Int,
        depthHeight: Int,
        scale: Float,
        bgrOut: ByteArray,
        holesOut: ByteArray,
    ): Boolean = nativeProjectReferenceBitmapToBgr(source, depth, depthWidth, depthHeight, scale, bgrOut, holesOut)

    fun writeBgrToBitmap(bgr: ByteArray, dest: Bitmap): Boolean = nativeWriteBgrToBitmap(bgr, dest)

    fun packQnnRgbBitmap(
        source: Bitmap,
        outDirectBuffer: ByteBuffer,
        tensorWidth: Int,
        tensorHeight: Int,
        left: Int,
        top: Int,
    ): Boolean = nativePackQnnRgbBitmap(source, outDirectBuffer, tensorWidth, tensorHeight, left, top)

    @JvmStatic private external fun nativeAbiInfo(): String
    @JvmStatic private external fun nativeProjectComfy(source: IntArray, depth: FloatArray, width: Int, height: Int, scale: Float, subpixel: Boolean): IntArray
    @JvmStatic private external fun nativeProjectReference(source: IntArray, depth: FloatArray, width: Int, height: Int, depthWidth: Int, depthHeight: Int, scale: Float, pixelsOut: IntArray, holesOut: ByteArray)
    @JvmStatic private external fun nativeProjectPhoneStereo(source: IntArray, depth: FloatArray, width: Int, height: Int, depthWidth: Int, depthHeight: Int, scale: Float, fillRadius: Int): IntArray
    @JvmStatic private external fun nativeFilterPhoneDepth(depth: FloatArray, width: Int, height: Int, diameter: Int, invert: Boolean, centered: Boolean): FloatArray
    @JvmStatic private external fun nativeNormalizeModelDepth(values: FloatArray, strict: Boolean): Float
    @JvmStatic private external fun nativeReadNormalizedDirect(buffer: ByteBuffer, count: Int, outValues: FloatArray): Float
    @JvmStatic private external fun nativePackTfliteRgbDirect(pixels: IntArray, outDirectBuffer: ByteBuffer): Boolean
    @JvmStatic private external fun nativeRotateDepthCcw90(values: FloatArray, width: Int, height: Int): FloatArray
    @JvmStatic private external fun nativeExtractQnnDepthDirect(directBuffer: ByteBuffer, tensorWidth: Int, tensorHeight: Int, contentWidth: Int, contentHeight: Int, left: Int, top: Int, rotateCcw: Boolean): FloatArray
    @JvmStatic private external fun nativeDisparitySpan(values: FloatArray): Float
    @JvmStatic private external fun nativeResizeDepth(source: FloatArray, sourceWidth: Int, sourceHeight: Int, width: Int, height: Int, method: Int): FloatArray
    @JvmStatic private external fun nativeDepthTargetSize(width: Int, height: Int, longEdge: Int, maxPixels: Long): Long
    @JvmStatic private external fun nativePostProcessDepth(source: FloatArray, sourceWidth: Int, sourceHeight: Int, width: Int, height: Int, mode: Int, diameter: Int, method: Int, invert: Boolean, resizeFirst: Boolean): FloatArray
    @JvmStatic private external fun nativeSmoothTemporal(current: FloatArray, previous: FloatArray, previousWeight: Float, sceneCut: Float, motion: Float): Boolean
    @JvmStatic private external fun nativeMapDepthDirection(source: FloatArray, invert: Boolean, clamp: Boolean): FloatArray
    @JvmStatic private external fun nativeQnnInputGeometry(size: Int, sourceWidth: Int, sourceHeight: Int, aspect: Int): IntArray
    @JvmStatic private external fun nativeSortVideoTimeline(samples: LongArray): LongArray
    @JvmStatic private external fun nativeCheckSegmentCoverage(packed: IntArray, totalFrames: Int)
    @JvmStatic private external fun nativeValidateSegmentTimestamp(local: Long, previous: Long, first: Long, expected: Long, initial: Boolean, sync: Boolean): Int
    @JvmStatic private external fun nativeSegmentRange(total: Int, framesPerSegment: Int, index: Int): Long
    @JvmStatic private external fun nativeWriteDepthCheckpoint(path: String, width: Int, height: Int, values: FloatArray)
    @JvmStatic private external fun nativeReadDepthCheckpoint(path: String, shapeOut: IntArray): FloatArray
    @JvmStatic private external fun nativeReclaimFrameJpegs(dirPath: String, first: Int, end: Int)
    @JvmStatic private external fun nativePublishImage(fields: Array<String>, recover: Boolean): String?

    @JvmStatic private external fun nativeProjectComfyBitmap(source: Bitmap, dest: Bitmap, depth: FloatArray, scale: Float, subpixel: Boolean): Boolean
    @JvmStatic private external fun nativeProjectPhoneBitmap(source: Bitmap, dest: Bitmap, depth: FloatArray, depthWidth: Int, depthHeight: Int, scale: Float, fillRadius: Int): Boolean
    @JvmStatic private external fun nativeProjectReferenceBitmapToBgr(source: Bitmap, depth: FloatArray, depthWidth: Int, depthHeight: Int, scale: Float, bgrOut: ByteArray, holesOut: ByteArray): Boolean
    @JvmStatic private external fun nativeWriteBgrToBitmap(bgr: ByteArray, dest: Bitmap): Boolean
    @JvmStatic private external fun nativePackQnnRgbBitmap(source: Bitmap, outDirectBuffer: ByteBuffer, tensorWidth: Int, tensorHeight: Int, left: Int, top: Int): Boolean
}
