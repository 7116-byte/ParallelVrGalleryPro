package com.local.parallelvrgallerypro

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo

/** Reference mode (V8/V9): zero-copy Rust RGBA->BGR projection + OpenCV Telea inpaint + zero-copy BGR->RGBA. */
internal object ReferenceStereoRenderer {
    private val initialized by lazy {
        check(OpenCVLoader.initLocal()) { "OpenCV initialization failed; reference mode cannot inpaint" }
        true
    }

    // Bound native inpaint allocations across concurrent image workers.
    @Synchronized
    @JvmOverloads
    fun render(source: Bitmap, depth: FloatArray, depthWidth: Int, depthHeight: Int, scale: Float, continuousDepth: Boolean = false, shareOriginalEye: Boolean = false): Pair<Bitmap, Bitmap> {
        check(initialized)
        val width = source.width
        val height = source.height
        val activeDepth: FloatArray
        val activeWidth: Int
        val activeHeight: Int
        if (continuousDepth) {
            activeDepth = restoreContinuousDepth(depth, depthWidth, depthHeight, width, height)
            activeWidth = width
            activeHeight = height
        } else {
            activeDepth = depth
            activeWidth = depthWidth
            activeHeight = depthHeight
        }
        val pixelCount = width * height
        val bgr = ByteArray(pixelCount * 3)
        val holes = ByteArray(pixelCount)
        val zeroCopyProjected = source.config == Bitmap.Config.ARGB_8888 &&
            RustCore.projectReferenceBitmapToBgr(source, activeDepth, activeWidth, activeHeight, scale, bgr, holes)
        if (!zeroCopyProjected) {
            val original = IntArray(pixelCount)
            source.getPixels(original, 0, width, 0, 0, width, height)
            val raster = ReferenceProjection.project(original, width, height, activeDepth, activeWidth, activeHeight, scale)
            raster.holes.copyInto(holes)
            for (i in raster.pixels.indices) {
                val pixel = raster.pixels[i]
                bgr[i * 3] = pixel.toByte()
                bgr[i * 3 + 1] = (pixel ushr 8).toByte()
                bgr[i * 3 + 2] = (pixel ushr 16).toByte()
            }
        }
        val image = Mat()
        val mask = Mat()
        val output = Mat()
        try {
            image.create(height, width, CvType.CV_8UC3)
            mask.create(height, width, CvType.CV_8UC1)
            image.put(0, 0, bgr)
            mask.put(0, 0, holes)
            Photo.inpaint(image, mask, output, 3.0, Photo.INPAINT_TELEA)
            output.get(0, 0, bgr)
            val left = if (shareOriginalEye) source else source.copy(Bitmap.Config.ARGB_8888, false)
            val right = try {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            } catch (error: Throwable) {
                if (left !== source) left.recycle()
                throw error
            }
            try {
                check(RustCore.writeBgrToBitmap(bgr, right)) { "Failed to write inpainted BGR into Bitmap" }
                return left to right
            } catch (error: Throwable) {
                right.recycle()
                if (left !== source) left.recycle()
                throw error
            }
        } finally {
            output.release()
            mask.release()
            image.release()
        }
    }

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
