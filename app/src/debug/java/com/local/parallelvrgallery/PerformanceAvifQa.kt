package com.local.parallelvrgallerypro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.radzivon.bartoshyk.avif.coder.*
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

object PerformanceAvifQa {
    @JvmStatic fun run(context:Context) {
        val root=File(context.getExternalFilesDir(null),"PVG_QA_performance1700");check(File(root,"AUTHORIZED").isFile)
        val source=requireNotNull(BitmapFactory.decodeFile(File(root,"PVG_QA_P3.jpg").path))
        val pixels=IntArray(source.width*source.height);source.getPixels(pixels,0,source.width,0,0,source.width,source.height)
        val bitmap=Bitmap.createBitmap(pixels,source.width,source.height,Bitmap.Config.ARGB_8888);source.recycle()
        val report=JSONArray()
        try {
            File(root,"avif-source.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            for(chroma in listOf(AvifChromaSubsampling.AUTO,AvifChromaSubsampling.LOSELESS)) {
                val bytes=HeifCoder().encodeAvif(bitmap,quality=100,preciseMode=PreciseMode.LOSSLESS,surfaceMode=AvifSurfaceMode.RGB,avifChromaSubsampling=chroma)
                File(root,"test-${chroma.name}.avif").writeBytes(bytes)
                for(decoder in listOf("Android","library")) {
                    val decoded=if(decoder=="Android") requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size)) else HeifCoder().decode(bytes)
                    try {
                        report.put(JSONObject().put("chroma",chroma.name).put("decoder",decoder).put("sourceColorSpace",bitmap.colorSpace?.name).put("decodedColorSpace",decoded.colorSpace?.name).put("quality",PerformanceFormats.compare(bitmap,decoded)))
                        File(root,"avif-${chroma.name}-$decoder.png").outputStream().use{decoded.compress(Bitmap.CompressFormat.PNG,100,it)}
                    }finally{decoded.recycle()}
                }
            }
            File(root,"avif-diagnostics.json").writeText(report.toString(2))
            val png=File(root,"verified-export.png");LosslessImageExport.png(bitmap,png)
            val roundtrip=requireNotNull(BitmapFactory.decodeFile(png.path))
            try {check(LosslessImageExport.samePixels(bitmap,roundtrip))}finally{roundtrip.recycle()}
            val rejected=File(root,"rejected-export.avif")
            var failed=false
            try {LosslessImageExport.avif(bitmap,rejected)}catch(error:IllegalStateException){check(error.message.orEmpty().contains("validation failed"));failed=true}
            check(failed && !rejected.exists() && !File(root,"rejected-export.avif.part").exists())
            File(root,"export-validation.json").writeText(JSONObject().put("pngPixelExact",true).put("corruptAvifRejectedBeforePublish",true).put("noFormatFallback",true).toString(2))
        }finally{bitmap.recycle()}
    }
}
