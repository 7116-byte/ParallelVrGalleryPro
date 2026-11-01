package com.local.parallelvrgallerypro

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.util.zip.CRC32
import java.util.zip.InflaterInputStream
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Test

class DepthPngWriterTest {
    private fun decode(bytes:ByteArray):ByteArray {
        val input=DataInputStream(bytes.inputStream());assertEquals(-8552249625308161526L,input.readLong())
        val idat=ByteArrayOutputStream()
        while(input.available()>0) {
            val length=input.readInt();val type=ByteArray(4);input.readFully(type);val payload=ByteArray(length);input.readFully(payload);input.readInt()
            when(String(type,Charsets.US_ASCII)) {
                "IHDR"->{assertEquals(8,payload[8].toInt());assertEquals(0,payload[9].toInt())}
                "IDAT"->idat.write(payload)
            }
        }
        return InflaterInputStream(idat.toByteArray().inputStream()).use{it.readBytes()}
    }
    @Test fun standardDecoderReadsExactDiagnosticSamplesIncludingClampAndRounding() {
        val values=floatArrayOf(-1f,0f,.001f,.1f,.5f,.9f,1f,2f)
        val out=ByteArrayOutputStream();DepthPngWriter.write(DepthMap(values,4,2),out)
        val raw=decode(out.toByteArray());assertEquals(10,raw.size)
        for(i in values.indices) assertEquals((values[i].coerceIn(0f,1f)*255f).roundToInt(),raw[(i/4)*5+1+i%4].toInt() and 255)
    }
    @Test fun largeNoisyMapHasValidStreamingChunksAndIndependentCrc() {
        val random=java.util.Random(1700);val values=FloatArray(770*770){random.nextFloat()}
        val out=ByteArrayOutputStream();DepthPngWriter.write(DepthMap(values,770,770),out)
        val stream=DataInputStream(out.toByteArray().inputStream());stream.skipBytes(8);var chunks=0
        while(stream.available()>0) {
            val length=stream.readInt();val type=ByteArray(4);stream.readFully(type);val bytes=ByteArray(length);stream.readFully(bytes)
            val crc=CRC32();crc.update(type);crc.update(bytes);assertEquals(crc.value.toInt(),stream.readInt())
            if(String(type,Charsets.US_ASCII)=="IDAT") {assertTrue(length<=64*1024);chunks++}
        }
        assertTrue(chunks>1)
        val raw=decode(out.toByteArray());assertEquals(771*770,raw.size)
        for(i in values.indices step 71)assertEquals((values[i]*255f).roundToInt(),raw[(i/770)*771+1+i%770].toInt() and 255)
    }
    @Test fun writeFailurePropagatesAndInvalidGeometryIsRejected() {
        assertThrows(java.io.IOException::class.java) {DepthPngWriter.write(DepthMap(floatArrayOf(0f),1,1),object:java.io.OutputStream(){override fun write(b:Int){throw java.io.IOException("full")}})}
        assertThrows(IllegalArgumentException::class.java){DepthPngWriter.write(DepthMap(floatArrayOf(0f),2,1),ByteArrayOutputStream())}
    }
}
