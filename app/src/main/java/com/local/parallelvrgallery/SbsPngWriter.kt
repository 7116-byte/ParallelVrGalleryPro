package com.local.parallelvrgallerypro

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/** Exact sRGB RGBA rows, joined directly; no full SBS bitmap or JPEG intermediate. */
internal object SbsPngWriter {
    fun write(eyeWidth: Int, height: Int, stream: OutputStream,
        leftRow: (Int, IntArray) -> Unit, rightRow: (Int, IntArray) -> Unit) {
        require(eyeWidth > 0 && height > 0 && eyeWidth <= (Int.MAX_VALUE - 1) / 8)
        val output = DataOutputStream(stream)
        output.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        val header = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).apply {
            writeInt(eyeWidth * 2); writeInt(height); write(byteArrayOf(8, 6, 0, 0, 0))
        } }.toByteArray()
        chunk(output, "IHDR", header, 0, header.size)
        chunk(output, "sRGB", byteArrayOf(0), 0, 1)
        val idat = object : OutputStream() {
            override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
            override fun write(bytes: ByteArray, offset: Int, length: Int) = chunk(output, "IDAT", bytes, offset, length)
        }
        val deflater = Deflater(Deflater.BEST_SPEED)
        try {
            val compressed = DeflaterOutputStream(idat, deflater, 64 * 1024)
            val pixels = IntArray(eyeWidth)
            val row = ByteArray(eyeWidth * 8 + 1)
            val readers = arrayOf(leftRow, rightRow)
            row[0] = 1 // PNG Sub filter; lossless, low compression effort.
            for (y in 0 until height) {
                var offset = 1
                for (read in readers) {
                    read(y, pixels)
                    for (pixel in pixels) {
                        row[offset++] = (pixel ushr 16).toByte(); row[offset++] = (pixel ushr 8).toByte()
                        row[offset++] = pixel.toByte(); row[offset++] = (pixel ushr 24).toByte()
                    }
                }
                for (i in row.lastIndex downTo 5) row[i] = (row[i] - row[i - 4]).toByte()
                compressed.write(row)
            }
            compressed.finish()
        } finally { deflater.end() }
        chunk(output, "IEND", byteArrayOf(), 0, 0)
        output.flush()
    }

    private fun chunk(output: DataOutputStream, type: String, bytes: ByteArray, offset: Int, length: Int) {
        val name = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(name); update(bytes, offset, length) }
        output.writeInt(length); output.write(name); output.write(bytes, offset, length); output.writeInt(crc.value.toInt())
    }
}
