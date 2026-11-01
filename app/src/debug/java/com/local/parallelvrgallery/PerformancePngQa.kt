package com.local.parallelvrgallerypro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

object PerformancePngQa {
    @JvmStatic fun run(context:Context) {
        val root=File(context.getExternalFilesDir(null),"PVG_QA_performance1700")
        check(File(root,"AUTHORIZED").isFile)
        val results=JSONArray()
        for(file in File(root,"baseline/vr_cache").walkTopDown().filter{it.name=="depth.png" || it.name=="model_depth.png"}) {
            val image=requireNotNull(BitmapFactory.decodeFile(file.path));val pixels=IntArray(image.width*image.height)
            image.getPixels(pixels,0,image.width,0,0,image.width,image.height)
            val map=DepthMap(FloatArray(pixels.size){(pixels[it] and 255)/255f},image.width,image.height)
            val old=File(root,"old.png");val fast=File(root,"fast.png")
            val oldTimes=JSONArray();val newTimes=JSONArray()
            try {
                repeat(3){
                    var start=System.nanoTime();old.outputStream().use{check(image.compress(Bitmap.CompressFormat.PNG,100,it))};oldTimes.put((System.nanoTime()-start)/1e6)
                    start=System.nanoTime();DepthPngWriter.write(map,fast);newTimes.put((System.nanoTime()-start)/1e6)
                }
                val decoded=requireNotNull(BitmapFactory.decodeFile(fast.path))
                try {val actual=IntArray(pixels.size);decoded.getPixels(actual,0,image.width,0,0,image.width,image.height);check(pixels.contentEquals(actual)){"Android PNG sample or color mismatch"}}
                finally{decoded.recycle()}
                results.put(JSONObject().put("source",file.relativeTo(root).path).put("width",image.width).put("height",image.height).put("oldMs",oldTimes).put("fastMs",newTimes).put("oldBytes",old.length()).put("fastBytes",fast.length()).put("allAndroidPixelsIdentical",true))
                File(root,"png-comparison.json").writeText(results.toString(2))
            }finally{image.recycle();old.delete();fast.delete()}
        }
        check(results.length()==8)
    }
}
