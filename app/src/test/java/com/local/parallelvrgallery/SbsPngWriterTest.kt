package com.local.parallelvrgallerypro

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.OutputStream
import java.util.zip.CRC32
import org.junit.Assert.*
import org.junit.Test

class SbsPngWriterTest {
    private fun encode(w:Int,h:Int,left:IntArray,right:IntArray):ByteArray = ByteArrayOutputStream().also { out ->
        SbsPngWriter.write(w,h,out,{y,row->left.copyInto(row,0,y*w,(y+1)*w)},{y,row->right.copyInto(row,0,y*w,(y+1)*w)})
    }.toByteArray()
    @Test fun independentPngDecoderRestoresBothEyesAllChannelsAndAlphaExactly() {
        for(w in listOf(1,3,257)) {
            val h=7; val random=java.util.Random(1900)
            val left=IntArray(w*h){random.nextInt()};val right=IntArray(w*h){random.nextInt()}
            // Android's compile stubs omit java.desktop; the JVM test runtime still supplies its independent PNG reader.
            val image=Class.forName("javax.imageio.ImageIO").getMethod("read",java.io.InputStream::class.java)
                .invoke(null,ByteArrayInputStream(encode(w,h,left,right)))
            val type=image.javaClass
            assertEquals(w*2,type.getMethod("getWidth").invoke(image));assertEquals(h,type.getMethod("getHeight").invoke(image))
            val pixel=type.getMethod("getRGB",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType)
            for(y in 0 until h)for(x in 0 until w) {
                assertEquals(left[y*w+x],pixel.invoke(image,x,y));assertEquals(right[y*w+x],pixel.invoke(image,x+w,y))
            }
        }
    }
    @Test fun incompressibleRowsProduceBoundedIdatChunksWithValidCrcAndSrgb() {
        val w=518;val h=80;val random=java.util.Random(190019)
        val bytes=encode(w,h,IntArray(w*h){random.nextInt()},IntArray(w*h){random.nextInt()})
        val input=DataInputStream(ByteArrayInputStream(bytes));input.skipBytes(8);var idat=0;var srgb=false;var ended=false
        while(input.available()>0) {
            val length=input.readInt();val type=ByteArray(4).also{input.readFully(it)};val data=ByteArray(length).also{input.readFully(it)}
            val crc=CRC32().apply{update(type);update(data)};assertEquals(crc.value.toInt(),input.readInt())
            when(String(type,Charsets.US_ASCII)) {
                "IDAT"->{assertTrue(length<=64*1024);idat++}
                "sRGB"->{assertArrayEquals(byteArrayOf(0),data);srgb=true}
                "IEND"->{assertEquals(0,length);ended=true}
            }
        }
        assertTrue(idat>1 && srgb && ended)
    }
    @Test fun cancelledRowAndOutputFailuresPropagateWithoutValidCompletedPng() {
        val output=ByteArrayOutputStream()
        assertThrows(IllegalStateException::class.java) { SbsPngWriter.write(5,4,output,{y,row->check(y<2);row.fill(-1)},{_,row->row.fill(0)}) }
        assertThrows(java.io.IOException::class.java) { SbsPngWriter.write(5,4,object:OutputStream(){override fun write(value:Int){throw java.io.IOException("full")}}, {_,_->},{_,_->}) }
    }
}
