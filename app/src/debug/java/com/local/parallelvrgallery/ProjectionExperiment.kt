package com.local.parallelvrgallerypro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.DataInputStream
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Investigates exact last-writer coverage without changing projection math. */
object ProjectionExperiment {
    fun candidate(source:IntArray,w:Int,h:Int,depth:FloatArray,scale:Float):IntArray {
        val result=source.copyOf();val parent=IntArray(w+1);val factor=scale.toDouble()/w
        fun find(start:Int):Int {
            var p=start
            while(parent[p]!=p){parent[p]=parent[parent[p]];p=parent[p]}
            return p
        }
        for(y in 0 until h) {
            for(i in parent.indices)parent[i]=i
            val row=y*w
            for(x in w-1 downTo 0) {
                val value=depth[row+x];val d=if(value.isFinite())value.coerceIn(0f,1f) else 0f
                val shift=((d*255f).toInt()*factor).toInt()
                val target=(x+shift).coerceIn(0,w-1);val end=minOf(w,target+shift+10)
                var p=find(target)
                while(p<end){result[row+p]=source[row+x];parent[p]=find(p+1);p=parent[p]}
            }
        }
        return result
    }
    @JvmStatic fun run(context:Context) {
        val root=ColdGenerationQa.root(context)
        val raw=DataInputStream(File(root,"kernel-ACCURATE_UNSET.f32").inputStream().buffered()).use{stream->FloatArray(518*518){stream.readFloat()}}
        val decoded=requireNotNull(BitmapFactory.decodeFile(File(root,"PVG_QA_P3.jpg").path));val report=JSONArray()
        try {
            for(w in listOf(1280,2560)) {
                val bitmap=Bitmap.createScaledBitmap(decoded,w,w,true);val pixels=IntArray(w*w)
                bitmap.getPixels(pixels,0,w,0,0,w,w);if(bitmap!==decoded)bitmap.recycle()
                val depth=DepthResampling.resize(DepthMap(raw,518,518),w,w,DepthInterpolation.BILINEAR).values
                for(scale in listOf(50f,100f)) {
                    check(ComfyLegacyProjection.project(pixels,w,w,depth,scale).contentEquals(candidate(pixels,w,w,depth,scale)))
                    val before=JSONArray();val after=JSONArray()
                    repeat(4){round->
                        fun old(){val s=System.nanoTime();val r=ComfyLegacyProjection.project(pixels,w,w,depth,scale);check(r.size==pixels.size);before.put((System.nanoTime()-s)/1e6)}
                        fun fast(){val s=System.nanoTime();val r=candidate(pixels,w,w,depth,scale);check(r.size==pixels.size);after.put((System.nanoTime()-s)/1e6)}
                        if(round%2==0){old();fast()}else{fast();old()}
                    }
                    report.put(JSONObject().put("width",w).put("scale",scale).put("beforeMs",before).put("candidateMs",after).put("allPixelsIdentical",true))
                }
            }
        }finally{decoded.recycle()}
        File(root,"projection.json").writeText(report.toString(2))
    }
}
