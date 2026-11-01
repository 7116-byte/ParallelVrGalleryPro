package com.local.parallelvrgallerypro

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.*
import org.junit.Test

class TensorTransferTest {
    @Test fun boundedRowsAndPartialLastChunkProduceTheSameBytesAsOneBulkCopy() {
        val pixels=IntArray(518*518+7){java.lang.Integer.rotateLeft(it*72371,it%31)}
        val all=ByteBuffer.allocateDirect(pixels.size*12).order(ByteOrder.nativeOrder())
        val rows=ByteBuffer.allocateDirect(pixels.size*12).order(ByteOrder.nativeOrder())
        TensorTransfer.packRgb(pixels,FloatArray(pixels.size*3),all.asFloatBuffer())
        TensorTransfer.packRgb(pixels,FloatArray(518*3),rows.asFloatBuffer())
        val a=ByteArray(all.capacity());val b=ByteArray(rows.capacity());all.get(a);rows.get(b);assertArrayEquals(a,b)
    }
    @Test fun allChannelValuesAndOrderMatchOriginalFloatDivisionBitForBit() {
        val pixels=IntArray(256){i->(i shl 24) or (i shl 16) or ((255-i) shl 8) or (i xor 147)}
        for(order in listOf(ByteOrder.LITTLE_ENDIAN,ByteOrder.BIG_ENDIAN)) {
            val fast=ByteBuffer.allocateDirect(pixels.size*12).order(order)
            val reference=ByteBuffer.allocate(pixels.size*12).order(order)
            pixels.forEach {p->reference.putFloat(((p ushr 16) and 255)/255f);reference.putFloat(((p ushr 8) and 255)/255f);reference.putFloat((p and 255)/255f)}
            TensorTransfer.packRgb(pixels,FloatArray(pixels.size*3),fast.asFloatBuffer())
            val actual=ByteArray(fast.capacity());fast.get(actual);assertArrayEquals(reference.array(),actual)
        }
    }
    @Test fun repeatedPackingOverwritesEveryValueWithoutDependingOnBufferPosition() {
        val pixels=intArrayOf(0x102030,0x405060);val scratch=FloatArray(6)
        val bytes=ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder());val target=bytes.asFloatBuffer()
        TensorTransfer.packRgb(pixels,scratch,target);pixels.fill(0)
        TensorTransfer.packRgb(pixels,scratch,target);val actual=FloatArray(6);target.rewind();target.get(actual)
        assertArrayEquals(FloatArray(6),actual,0f)
    }
    @Test fun normalizedDepthMatchesOriginalIncludingNonfiniteConstantsAndSignedZero() {
        val random=java.util.Random(1800)
        val cases=listOf(floatArrayOf(-0f,0f,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,1f,-1f),FloatArray(518*518){random.nextFloat()*13-7},FloatArray(770*770){3f},floatArrayOf(-Float.MAX_VALUE,Float.MAX_VALUE))
        for(values in cases)for(order in listOf(ByteOrder.LITTLE_ENDIAN,ByteOrder.BIG_ENDIAN)) {
            val input=ByteBuffer.allocateDirect(values.size*4).order(order);input.asFloatBuffer().put(values)
            val expected=values.map{if(it.isFinite())it else 0f}.toFloatArray()
            var lo=Float.MAX_VALUE;var hi=-Float.MAX_VALUE
            expected.forEach{lo=min(lo,it);hi=max(hi,it)}
            val raw=hi-lo;val range=if(raw.isFinite())max(.0001f,raw) else .0001f
            for(i in expected.indices)expected[i]=(expected[i]-lo)/range
            val actual=TensorTransfer.readNormalized(input,values.size)
            assertEquals(raw.toRawBits(),actual.range.toRawBits())
            for(i in expected.indices)assertEquals(expected[i].toRawBits(),actual.values[i].toRawBits())
        }
    }
    @Test fun invalidBufferContractsFailBeforePartialWrites() {
        assertThrows(IllegalArgumentException::class.java){TensorTransfer.packRgb(intArrayOf(1),FloatArray(2),java.nio.FloatBuffer.allocate(2))}
        assertThrows(IllegalArgumentException::class.java){TensorTransfer.readNormalized(ByteBuffer.allocate(7),2)}
    }
}
