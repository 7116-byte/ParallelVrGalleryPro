package com.local.parallelvrgallerypro

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.photo.Photo
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Only the new reference mode uses OpenCV. No CPU/GPU inference policy is changed. */
internal object ReferenceStereoRenderer {
    private val initialized by lazy {
        check(OpenCVLoader.initLocal()) { "OpenCV initialization failed; reference mode cannot inpaint" }
        true
    }

    // Bound native inpaint allocations across concurrent image workers.
    @Synchronized
    fun render(source: Bitmap, depth: FloatArray, depthWidth: Int, depthHeight: Int, scale: Float, continuousDepth: Boolean = false): Pair<Bitmap, Bitmap> {
        check(initialized)
        val width = source.width
        val height = source.height
        val original = IntArray(width * height)
        source.getPixels(original, 0, width, 0, 0, width, height)
        val raster = if (continuousDepth) {
            val restored = restoreContinuousDepth(depth, depthWidth, depthHeight, width, height)
            ReferenceProjection.project(original, width, height, restored, width, height, scale)
        } else ReferenceProjection.project(original, width, height, depth, depthWidth, depthHeight, scale)
        val bgr = ByteArray(original.size * 3)
        for (i in raster.pixels.indices) {
            val pixel = raster.pixels[i]
            bgr[i * 3] = pixel.toByte()
            bgr[i * 3 + 1] = (pixel ushr 8).toByte()
            bgr[i * 3 + 2] = (pixel ushr 16).toByte()
        }
        val image = Mat()
        val mask = Mat()
        val output = Mat()
        try {
            image.create(height, width, CvType.CV_8UC3)
            mask.create(height, width, CvType.CV_8UC1)
            image.put(0, 0, bgr)
            mask.put(0, 0, raster.holes)
            Photo.inpaint(image, mask, output, 3.0, Photo.INPAINT_TELEA)
            output.get(0, 0, bgr)
            for (i in raster.pixels.indices) {
                raster.pixels[i] = (0xff shl 24) or ((bgr[i * 3 + 2].toInt() and 255) shl 16) or ((bgr[i * 3 + 1].toInt() and 255) shl 8) or (bgr[i * 3].toInt() and 255)
            }
            val left = Bitmap.createBitmap(original, width, height, Bitmap.Config.ARGB_8888)
            try {
                return left to Bitmap.createBitmap(raster.pixels, width, height, Bitmap.Config.ARGB_8888)
            } catch (error: Throwable) { left.recycle(); throw error }
        } finally {
            output.release()
            mask.release()
            image.release()
        }
    }

    /** The external node expects an already restored depth image. Reconstruct floats
     * before 8-bit quantization so the 518-grid is not enlarged into contour steps.
     * This is a mobile preprocessing revision; V8 and saved V8 jobs retain their path.
     */
    internal fun restoreContinuousDepth(depth: FloatArray, depthWidth: Int, depthHeight: Int, width: Int, height: Int): FloatArray {
        check(initialized)
        val input = Mat(); val resized = Mat(); val smoothed = Mat()
        try {
            input.create(depthHeight, depthWidth, CvType.CV_32FC1)
            input.put(0, 0, depth)
            Imgproc.resize(input, resized, Size(width.toDouble(), height.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
            Imgproc.GaussianBlur(resized, smoothed, Size(3.0, 3.0), 0.0)
            return FloatArray(width * height).also { smoothed.get(0, 0, it) }
        } finally { smoothed.release(); resized.release(); input.release() }
    }
}
