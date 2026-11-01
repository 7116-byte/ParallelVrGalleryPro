package com.local.parallelvrgallerypro

import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.math.roundToInt

/** The same 8-bit diagnostic samples as depthToBitmap, with bounded grayscale PNG storage. */
internal object DepthPngWriter {
    fun write(depth: DepthMap, file: File) = file.outputStream().buffered(64 * 1024).use { write(depth, it) }

    fun write(depth: DepthMap, stream: OutputStream) {
        require(depth.width > 0 && depth.height > 0 && depth.width.toLong() * depth.height == depth.values.size.toLong())
        val output = DataOutputStream(stream)
        output.write(byteArrayOf(137.toByte(),80,78,71,13,10,26,10))
        val header = java.io.ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).apply {
            writeInt(depth.width);writeInt(depth.height);write(byteArrayOf(8,0,0,0,0))
        } }.toByteArray()
        chunk(output,"IHDR",header,header.size)
        chunk(output,"sRGB",byteArrayOf(0),1)
        val idat = object: OutputStream() {
            override fun write(value:Int) { val one=byteArrayOf(value.toByte());write(one,0,1) }
            override fun write(bytes:ByteArray,offset:Int,length:Int) { chunk(output,"IDAT",bytes,length,offset) }
        }
        val deflater = Deflater(Deflater.BEST_SPEED)
        try {
            val compressed = DeflaterOutputStream(idat,deflater,64*1024)
            val row=ByteArray(depth.width+1) // PNG filter None; no full-size bitmap or pixel copy.
            for (y in 0 until depth.height) {
                for(x in 0 until depth.width) row[x+1]=(depth.values[y*depth.width+x].coerceIn(0f,1f)*255f).roundToInt().toByte()
                compressed.write(row)
            }
            compressed.finish()
        } finally {deflater.end()}
        chunk(output,"IEND",byteArrayOf(),0)
        output.flush()
    }

    private fun chunk(out:DataOutputStream,type:String,bytes:ByteArray,length:Int,offset:Int=0) {
        val name=type.toByteArray(Charsets.US_ASCII)
        val crc=CRC32();crc.update(name);crc.update(bytes,offset,length)
        out.writeInt(length);out.write(name);out.write(bytes,offset,length);out.writeInt(crc.value.toInt())
    }
}
