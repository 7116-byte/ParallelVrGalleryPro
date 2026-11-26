package com.local.parallelvrgallerypro

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Isolated SSS main-node path; projection executes zero-copy in Rust when Bitmap is RGBA_8888. */
internal object ComfyLegacyStereoRenderer {
    private val initialized by lazy { check(OpenCVLoader.initLocal()); true }

    @Synchronized
    fun prepareDepth(raw: FloatArray, depthWidth: Int, depthHeight: Int, width: Int, height: Int, blur: Int, invert: Boolean): FloatArray {
        check(initialized)
        require(raw.size == depthWidth * depthHeight)
        val input = Mat(); val resized = Mat(); val normalized = Mat(); val smoothed = Mat()
        try {
            input.create(depthHeight, depthWidth, CvType.CV_32FC1); input.put(0, 0, raw)
            // Same half-pixel cubic geometry as the desktop estimator, before quantization.
            Imgproc.resize(input, resized, Size(width.toDouble(), height.toDouble()), 0.0, 0.0, Imgproc.INTER_CUBIC)
            Core.normalize(resized, normalized, 0.0, 1.0, Core.NORM_MINMAX)
            // Desktop estimator inverts once; the node's invert checkbox inverts again.
            val kernel = blur.coerceIn(1, 51).let { if (it % 2 == 0) it + 1 else it }
            Imgproc.GaussianBlur(normalized, smoothed, Size(kernel.toDouble(), kernel.toDouble()), 0.0)
            val values = FloatArray(width * height)
            smoothed.get(0, 0, values)
            return RustCore.mapDepthDirection(values, invert = !invert, clamp = true)
        } finally { smoothed.release(); normalized.release(); resized.release(); input.release() }
    }

    @Synchronized
    @JvmOverloads
    fun renderPrepared(source: Bitmap, depth: FloatArray, scale: Float, subpixel: Boolean = false, shareOriginalEye: Boolean = false): Pair<Bitmap, Bitmap> {
        val w = source.width; val h = source.height
        val left = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val zeroCopyEligible = source.config == Bitmap.Config.ARGB_8888 && (!subpixel || !source.hasAlpha())
            val projectedZeroCopy = zeroCopyEligible && RustCore.projectComfyBitmap(source, left, depth, scale, subpixel)
            if (!projectedZeroCopy) {
                val pixels = IntArray(w * h)
                source.getPixels(pixels, 0, w, 0, 0, w, h)
                val projected = RustCore.projectComfy(pixels, depth, w, h, scale, subpixel)
                left.setPixels(projected, 0, w, 0, 0, w, h)
            }
            val right = if (shareOriginalEye) source else source.copy(Bitmap.Config.ARGB_8888, false)
            return left to right
        } catch (error: Throwable) {
            left.recycle()
            throw error
        }
    }
}
