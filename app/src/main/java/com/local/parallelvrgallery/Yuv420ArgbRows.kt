package com.local.parallelvrgallerypro

import java.nio.ByteBuffer

/** Copies bounded stripes while honoring each plane's crop, base offset and strides. */
internal class Yuv420ArgbRows {
    class Plane(source: ByteBuffer, private val rowStride: Int, val pixelStride: Int) {
        private val buffer = source.duplicate()
        private val base = buffer.position()

        fun span(width: Int): Int = (width - 1) * pixelStride + 1

        fun read(row: Int, column: Int, width: Int, destination: ByteArray) {
            buffer.position(base + row * rowStride + column * pixelStride)
            buffer.get(destination, 0, span(width))
        }
    }

    private var luma = ByteArray(0)
    private var chromaU = ByteArray(0)
    private var chromaV = ByteArray(0)
    private var pixels = IntArray(0)

    fun convert(
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        yPlane: Plane,
        uPlane: Plane,
        vPlane: Plane,
        rgb: YuvRgb,
        checkActive: () -> Unit,
        write: (pixels: IntArray, top: Int, rows: Int) -> Unit,
    ) {
        val chromaLeft = left / 2
        val chromaWidth = (left + width - 1) / 2 - chromaLeft + 1
        val chromaOffset = left % 2
        val yStride = yPlane.pixelStride
        val uStride = uPlane.pixelStride
        val vStride = vPlane.pixelStride
        val stripeRows = minOf(height, 16)
        if (luma.size < yPlane.span(width)) luma = ByteArray(yPlane.span(width))
        if (chromaU.size < uPlane.span(chromaWidth)) chromaU = ByteArray(uPlane.span(chromaWidth))
        if (chromaV.size < vPlane.span(chromaWidth)) chromaV = ByteArray(vPlane.span(chromaWidth))
        if (pixels.size < width * stripeRows) pixels = IntArray(width * stripeRows)

        var lastChromaRow = -1
        for (stripeTop in 0 until height step stripeRows) {
            checkActive()
            val rows = minOf(stripeRows, height - stripeTop)
            var output = 0
            for (row in stripeTop until stripeTop + rows) {
                val sourceRow = top + row
                val chromaRow = sourceRow / 2
                yPlane.read(sourceRow, left, width, luma)
                if (chromaRow != lastChromaRow) {
                    uPlane.read(chromaRow, chromaLeft, chromaWidth, chromaU)
                    vPlane.read(chromaRow, chromaLeft, chromaWidth, chromaV)
                    lastChromaRow = chromaRow
                }
                if (chromaOffset == 1) {
                    pixels[output++] = rgb.pixel(luma[0].toInt() and 255, chromaU[0].toInt() and 255, chromaV[0].toInt() and 255)
                }
                var x = chromaOffset
                var yIndex = chromaOffset * yStride
                var uIndex = chromaOffset * uStride
                var vIndex = chromaOffset * vStride
                while (x + 1 < width) {
                    rgb.pair(
                        luma[yIndex].toInt() and 255, luma[yIndex + yStride].toInt() and 255,
                        chromaU[uIndex].toInt() and 255, chromaV[vIndex].toInt() and 255,
                        pixels, output,
                    )
                    x += 2
                    output += 2
                    yIndex += 2 * yStride
                    uIndex += uStride
                    vIndex += vStride
                }
                if (x < width) {
                    pixels[output++] = rgb.pixel(
                        luma[yIndex].toInt() and 255,
                        chromaU[uIndex].toInt() and 255,
                        chromaV[vIndex].toInt() and 255,
                    )
                }
            }
            write(pixels, stripeTop, rows)
        }
    }
}
