package com.local.parallelvrgallerypro

import java.nio.ByteBuffer
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

internal data class DepthRunResult(val values:FloatArray,val range:Float)

/** Bulk I/O only; division, channel order, sanitization and normalization retain their FP32 rules. */
internal object TensorTransfer {
    private val channel = FloatArray(256){it/255f}

    fun packRgb(pixels:IntArray,scratch:FloatArray,target:FloatBuffer) {
        require(scratch.size>=3 && scratch.size%3==0 && target.capacity()==pixels.size*3)
        target.clear()
        var from=0
        while(from<pixels.size) {
            val count=min(scratch.size/3,pixels.size-from)
            var offset=0
            for(i in from until from+count) {
                val pixel=pixels[i]
                scratch[offset++]=channel[(pixel ushr 16) and 255]
                scratch[offset++]=channel[(pixel ushr 8) and 255]
                scratch[offset++]=channel[pixel and 255]
            }
            target.put(scratch,0,count*3)
            from+=count
        }
    }

    fun readNormalized(buffer:ByteBuffer,count:Int):DepthRunResult {
        require(count>0 && buffer.capacity()==count*4)
        val values=FloatArray(count)
        buffer.rewind();buffer.asFloatBuffer().get(values)
        var minimum=Float.MAX_VALUE;var maximum=-Float.MAX_VALUE
        for(i in values.indices) {
            val sample=values[i]
            val value=if(sample.isFinite())sample else 0f
            values[i]=value
            minimum=min(minimum,value);maximum=max(maximum,value)
        }
        val rawRange=maximum-minimum
        val range=if(rawRange.isFinite())max(0.0001f,rawRange) else 0.0001f
        for(i in values.indices)values[i]=(values[i]-minimum)/range
        return DepthRunResult(values,rawRange)
    }
}
