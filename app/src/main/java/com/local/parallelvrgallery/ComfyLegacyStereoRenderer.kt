package com.local.parallelvrgallerypro

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Isolated SSS main-node path; original phone and external-node renderers stay intact. */
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
            return FloatArray(width * height).also { values ->
                smoothed.get(0, 0, values)
                for (i in values.indices) values[i] = (if (invert) values[i] else 1f - values[i]).coerceIn(0f, 1f)
            }
        } finally { smoothed.release(); normalized.release(); resized.release(); input.release() }
    }

    @Synchronized
    @JvmOverloads
    fun renderPrepared(source: Bitmap, depth: FloatArray, scale: Float, subpixel: Boolean = false, shareOriginalEye: Boolean = false): Pair<Bitmap, Bitmap> {
        val w = source.width; val h = source.height
        val pixels = IntArray(w * h); source.getPixels(pixels, 0, w, 0, 0, w, h)
        val projected = if (subpixel) ComfySubpixelProjection.project(pixels, w, h, depth, scale) else ComfyLegacyProjection.project(pixels, w, h, depth, scale)
        val left = Bitmap.createBitmap(projected, w, h, Bitmap.Config.ARGB_8888)
        try { return left to if (shareOriginalEye) source else Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888) }
        catch (error: Throwable) { left.recycle(); throw error }
    }
}
