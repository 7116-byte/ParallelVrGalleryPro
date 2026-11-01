package com.local.parallelvrgallerypro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import com.radzivon.bartoshyk.avif.coder.PreciseMode
import java.io.File
import java.nio.ByteBuffer
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/** Explicit QA files only. Never starts the Activity or reads the media library. */
object PerformanceFormats {
    @JvmStatic fun run(context: Context) {
        val root = File(context.getExternalFilesDir(null), "PVG_QA_performance1700")
        check(File(root,"AUTHORIZED").isFile)
        val report = JSONArray()
        for (sample in listOf("PVG_QA_P1.jpg", "PVG_QA_P2.jpg", "PVG_QA_P3.jpg", "PVG_QA_O1.png")) {
            val decoded = requireNotNull(BitmapFactory.decodeFile(File(root,sample).path))
            for (edge in listOf(1280,2560)) {
                val factor = edge.toFloat()/max(decoded.width,decoded.height)
                val scaled = Bitmap.createScaledBitmap(decoded, (decoded.width*factor).roundToInt(), (decoded.height*factor).roundToInt(), true)
                val pixels=IntArray(scaled.width*scaled.height);scaled.getPixels(pixels,0,scaled.width,0,0,scaled.width,scaled.height)
                val bitmap=Bitmap.createBitmap(pixels,scaled.width,scaled.height,Bitmap.Config.ARGB_8888)
                if(scaled!==decoded)scaled.recycle()
                try {
                    val formats = mutableListOf("jpeg100", "jpeg100-buffered", "png", "png-buffered", "webp-lossless0", "webp-lossless40", "raw")
                    if(edge==1280) formats += "webp-lossless100"
                    if(edge==1280 && sample=="PVG_QA_P3.jpg") formats += listOf("avif-lossless","avif-rgb-lossless")
                    for (format in formats) {
                        val file = File(root,"format-$format.bin")
                        val times = JSONArray(); val reads=JSONArray(); var quality=JSONObject()
                        repeat(if(format.startsWith("avif")) 1 else 3) {
                            val start=System.nanoTime()
                            when(format) {
                                "raw" -> file.outputStream().buffered(256*1024).use { out ->
                                    val buffer=ByteBuffer.allocate(bitmap.byteCount);bitmap.copyPixelsToBuffer(buffer);out.write(buffer.array())
                                }
                                "avif-lossless" -> file.writeBytes(HeifCoder().encodeAvif(bitmap, quality=100, preciseMode=PreciseMode.LOSSLESS))
                                "avif-rgb-lossless" -> file.writeBytes(HeifCoder().encodeAvif(bitmap, quality=100, preciseMode=PreciseMode.LOSSLESS, surfaceMode=com.radzivon.bartoshyk.avif.coder.AvifSurfaceMode.RGB, avifChromaSubsampling=com.radzivon.bartoshyk.avif.coder.AvifChromaSubsampling.LOSELESS))
                                else -> {
                                    val codec = when {format.startsWith("jpeg") -> Bitmap.CompressFormat.JPEG;format.startsWith("png") -> Bitmap.CompressFormat.PNG;else -> Bitmap.CompressFormat.WEBP_LOSSLESS}
                                    val effort=when(format) {"webp-lossless0"->0;"webp-lossless40"->40;else->100}
                                    val stream=file.outputStream(); val output=if(format.endsWith("buffered")) stream.buffered(256*1024) else stream
                                    output.use { check(bitmap.compress(codec,effort,it)) }
                                }
                            }
                            times.put((System.nanoTime()-start)/1e6)
                            val readStart=System.nanoTime()
                            val roundtrip=if(format=="raw") Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888).also { it.copyPixelsFromBuffer(ByteBuffer.wrap(file.readBytes())) }
                                else requireNotNull(BitmapFactory.decodeFile(file.path))
                            reads.put((System.nanoTime()-readStart)/1e6)
                            try { if(it==0) quality=compare(bitmap,roundtrip) } finally {roundtrip.recycle()}
                        }
                        report.put(JSONObject().put("sample",sample).put("edge",edge).put("format",format).put("width",bitmap.width).put("height",bitmap.height).put("encodeWriteMs",times).put("decodeMs",reads).put("bytes",file.length()).put("quality",quality))
                        File(root,"formats-srgb.json").writeText(report.toString(2))
                        file.delete()
                    }
                }finally{if(bitmap!==decoded)bitmap.recycle()}
            }
            decoded.recycle()
        }
    }
    internal fun compare(a:Bitmap,b:Bitmap):JSONObject {
        check(a.width==b.width && a.height==b.height)
        val x=IntArray(a.width);val y=IntArray(a.width);var squared=0.0;var absolute=0L;var different=0L;var maximum=0
        for(row in 0 until a.height) {
            a.getPixels(x,0,x.size,0,row,x.size,1);b.getPixels(y,0,y.size,0,row,y.size,1)
            for(i in x.indices) {if(x[i]!=y[i])different++
                for(channel in 0..2) {val shift=channel*8;val delta=abs(((x[i] ushr shift) and 255)-((y[i] ushr shift) and 255));absolute+=delta;squared+=delta.toDouble()*delta;maximum=max(maximum,delta)}
            }
        }
        val count=a.width.toLong()*a.height*3
        return JSONObject().put("differentPixels",different).put("rgbMae",absolute.toDouble()/count).put("maxChannelError",maximum).put("psnrDb",if(squared==0.0) "infinite" else 10*log10(255.0*255*count/squared))
    }
}
