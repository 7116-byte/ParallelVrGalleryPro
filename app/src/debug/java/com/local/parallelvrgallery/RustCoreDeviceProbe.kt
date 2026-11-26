package com.local.parallelvrgallerypro

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.os.Bundle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random

/**
 * Debug-only synthetic on-device verification & latency probe for RustCore zero-copy AndroidBitmap JNI.
 * Touches zero user files/media. Invoke via:
 *   adb shell am instrument -w com.local.parallelvrgallerypro/.RustCoreDeviceProbe
 */
class RustCoreDeviceProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            val report = StringBuilder()
            val abi = RustCore.abiInfo()
            check(abi.contains("os=android") && abi.contains("zero_copy_bitmap=true")) {
                "Unexpected RustCore ABI info: $abi"
            }
            report.appendLine("ABI: $abi")

            val w = 1920
            val h = 1080
            val rng = Random(20261125)
            val srcPixels = IntArray(w * h) { (0xff shl 24) or (rng.nextInt() and 0x00ff_ffff) }
            val depthFull = FloatArray(w * h) { idx ->
                val x = (idx % w).toFloat() / w
                val y = (idx / w).toFloat() / h
                // Smooth gradient + foreground blob (realistic photo depth distribution)
                val dx = x - 0.5f; val dy = y - 0.45f
                val dist2 = dx * dx + dy * dy
                (0.25f + 0.55f * kotlin.math.exp(-dist2 * 14f) + 0.1f * y).coerceIn(0f, 1f)
            }
            val depthNoise = FloatArray(w * h) { rng.nextFloat() }
            val dw = 518
            val dh = 518
            val depthModel = FloatArray(dw * dh) { rng.nextFloat() }

            val srcBitmap = Bitmap.createBitmap(srcPixels, w, h, Bitmap.Config.ARGB_8888)
            srcBitmap.setHasAlpha(false)
            val dstZero = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val dstArray = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val checkZero = IntArray(w * h)
            val checkArray = IntArray(w * h)

            try {
                // 1. Bit-exact parity: V10 zero-copy Bitmap vs IntArray path (both smooth & random noise depth)
                check(RustCore.projectComfyBitmap(srcBitmap, dstZero, depthNoise, 50f, false))
                val v10Arr = RustCore.projectComfy(srcPixels, depthNoise, w, h, 50f, false)
                dstZero.getPixels(checkZero, 0, w, 0, 0, w, h)
                check(checkZero.contentEquals(v10Arr)) { "V10 zero-copy Bitmap mismatch!" }

                // 2. Bit-exact parity: V11 subpixel zero-copy Bitmap vs IntArray path (random noise depth)
                check(RustCore.projectComfyBitmap(srcBitmap, dstZero, depthNoise, 50f, true))
                val v11Arr = RustCore.projectComfy(srcPixels, depthNoise, w, h, 50f, true)
                dstZero.getPixels(checkZero, 0, w, 0, 0, w, h)
                check(checkZero.contentEquals(v11Arr)) { "V11 zero-copy Bitmap mismatch!" }

                // 3. Bit-exact parity: V6/V7 phone stereo zero-copy Bitmap vs IntArray path
                check(RustCore.projectPhoneBitmap(srcBitmap, dstZero, depthModel, dw, dh, 100f, 10))
                val phoneArr = RustCore.projectPhoneStereo(srcPixels, depthModel, w, h, dw, dh, 100f, 10)
                dstZero.getPixels(checkZero, 0, w, 0, 0, w, h)
                check(checkZero.contentEquals(phoneArr)) { "Phone stereo zero-copy Bitmap mismatch!" }

                // 4. Bit-exact parity: V8/V9 Reference RGBA->BGR->RGBA zero-copy vs ReferenceProjection
                val bgr = ByteArray(w * h * 3)
                val holes = ByteArray(w * h)
                check(RustCore.projectReferenceBitmapToBgr(srcBitmap, depthModel, dw, dh, 3.5f, bgr, holes))
                check(RustCore.writeBgrToBitmap(bgr, dstZero))
                val refRaster = ReferenceProjection.project(srcPixels, w, h, depthModel, dw, dh, 3.5f)
                dstZero.getPixels(checkZero, 0, w, 0, 0, w, h)
                check(holes.contentEquals(refRaster.holes)) { "Reference holes mismatch!" }
                check(checkZero.contentEquals(refRaster.pixels)) { "Reference zero-copy Bitmap mismatch!" }

                // 5. Bit-exact parity: QNN 1036x1036 NCHW RGB pack zero-copy vs Kotlin loop
                val qSize = 1036
                val qCount = qSize * qSize
                val qSrc = IntArray(qCount) { (0xff shl 24) or (rng.nextInt() and 0x00ff_ffff) }
                val qBitmap = Bitmap.createBitmap(qSrc, qSize, qSize, Bitmap.Config.ARGB_8888)
                val qRgbBytes = ByteBuffer.allocateDirect(3 * qCount * 4).order(ByteOrder.nativeOrder())
                val qRgbRef = ByteBuffer.allocateDirect(3 * qCount * 4).order(ByteOrder.nativeOrder())
                val redLut = FloatArray(256) { (it / 255f - 0.485f) / 0.229f }
                val greenLut = FloatArray(256) { (it / 255f - 0.456f) / 0.224f }
                val blueLut = FloatArray(256) { (it / 255f - 0.406f) / 0.225f }
                try {
                    check(RustCore.packQnnRgbBitmap(qBitmap, qRgbBytes, qSize, qSize, 0, 0))
                    val refFloat = qRgbRef.asFloatBuffer()
                    val qPx = IntArray(qCount)
                    qBitmap.getPixels(qPx, 0, qSize, 0, 0, qSize, qSize)
                    for (i in qPx.indices) {
                        val c = qPx[i]
                        refFloat.put(i, redLut[(c ushr 16) and 255])
                        refFloat.put(i + qCount, greenLut[(c ushr 8) and 255])
                        refFloat.put(i + qCount * 2, blueLut[c and 255])
                    }
                    val actualFloats = FloatArray(3 * qCount).also { qRgbBytes.asFloatBuffer().get(it) }
                    val expectedFloats = FloatArray(3 * qCount).also { qRgbRef.asFloatBuffer().get(it) }
                    for (i in actualFloats.indices) {
                        check(actualFloats[i] == expectedFloats[i]) { "QNN pack mismatch at $i" }
                    }

                    // 6. Latency benchmark (median of 9 runs after 2 warmup runs)
                    fun medianMs(runs: Int = 9, block: () -> Unit): Double {
                        repeat(2) { block() }
                        val times = LongArray(runs) {
                            val t0 = System.nanoTime()
                            block()
                            System.nanoTime() - t0
                        }.sortedArray()
                        return times[runs / 2] / 1_000_000.0
                    }

                    val v10ZeroMs = medianMs { RustCore.projectComfyBitmap(srcBitmap, dstZero, depthFull, 50f, false) }
                    val v10CopyMs = medianMs {
                        srcBitmap.getPixels(checkArray, 0, w, 0, 0, w, h)
                        val out = RustCore.projectComfy(checkArray, depthFull, w, h, 50f, false)
                        dstArray.setPixels(out, 0, w, 0, 0, w, h)
                    }
                    val v11ZeroMs = medianMs { RustCore.projectComfyBitmap(srcBitmap, dstZero, depthFull, 50f, true) }
                    val v11CopyMs = medianMs {
                        srcBitmap.getPixels(checkArray, 0, w, 0, 0, w, h)
                        val out = RustCore.projectComfy(checkArray, depthFull, w, h, 50f, true)
                        dstArray.setPixels(out, 0, w, 0, 0, w, h)
                    }
                    val phoneZeroMs = medianMs { RustCore.projectPhoneBitmap(srcBitmap, dstZero, depthModel, dw, dh, 100f, 10) }
                    val refZeroMs = medianMs {
                        RustCore.projectReferenceBitmapToBgr(srcBitmap, depthModel, dw, dh, 3.5f, bgr, holes)
                        RustCore.writeBgrToBitmap(bgr, dstZero)
                    }
                    val qnnPackZeroMs = medianMs { RustCore.packQnnRgbBitmap(qBitmap, qRgbBytes, qSize, qSize, 0, 0) }
                    val qnnPackKotlinMs = medianMs {
                        qBitmap.getPixels(qPx, 0, qSize, 0, 0, qSize, qSize)
                        for (i in qPx.indices) {
                            val c = qPx[i]
                            refFloat.put(i, redLut[(c ushr 16) and 255])
                            refFloat.put(i + qCount, greenLut[(c ushr 8) and 255])
                            refFloat.put(i + qCount * 2, blueLut[c and 255])
                        }
                    }

                    report.appendLine("PARITY: V10=PASS V11=PASS PhoneV6V7=PASS RefV8V9=PASS QnnPack1036=PASS")
                    report.appendLine("BENCH 1920x1080 V10:       zeroCopy=${"%.2f".format(v10ZeroMs)}ms vs arrayCopy=${"%.2f".format(v10CopyMs)}ms")
                    report.appendLine("BENCH 1920x1080 V11:       zeroCopy=${"%.2f".format(v11ZeroMs)}ms vs arrayCopy=${"%.2f".format(v11CopyMs)}ms")
                    report.appendLine("BENCH 1920x1080 Phone:     zeroCopy=${"%.2f".format(phoneZeroMs)}ms")
                    report.appendLine("BENCH 1920x1080 RefV8V9:   zeroCopy=${"%.2f".format(refZeroMs)}ms")
                    report.appendLine("BENCH 1036x1036 QnnPack:   zeroCopy=${"%.2f".format(qnnPackZeroMs)}ms vs kotlinGetPixels=${"%.2f".format(qnnPackKotlinMs)}ms")
                } finally {
                    qBitmap.recycle()
                }
            } finally {
                srcBitmap.recycle()
                dstZero.recycle()
                dstArray.recycle()
            }

            result.putString("report", report.toString())
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("error", error.stackTraceToString())
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
