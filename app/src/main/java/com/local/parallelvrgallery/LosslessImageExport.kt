package com.local.parallelvrgallerypro

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import com.radzivon.bartoshyk.avif.coder.PreciseMode
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object LosslessImageExport {
    fun samePixels(a:Bitmap,b:Bitmap):Boolean {
        if(a.width!=b.width || a.height!=b.height)return false
        val rowA=IntArray(a.width);val rowB=IntArray(a.width)
        for(y in 0 until a.height) {
            a.getPixels(rowA,0,a.width,0,y,a.width,1);b.getPixels(rowB,0,b.width,0,y,b.width,1)
            if(!rowA.contentEquals(rowB))return false
        }
        return true
    }
    fun png(bitmap:Bitmap,file:File) = publish(file) { target ->
        target.outputStream().buffered(256*1024).use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)){"PNG encoding failed"}}
    }
    fun avif(bitmap:Bitmap,file:File) {
        val bytes=HeifCoder().encodeAvif(bitmap,quality=100,preciseMode=PreciseMode.LOSSLESS)
        val decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: error("AVIF decode validation failed")
        try {check(samePixels(bitmap,decoded)){"AVIF 像素/色彩校验失败；请在设置中选择 PNG / AVIF pixel/color validation failed; select PNG in settings"}}
        finally{decoded.recycle()}
        publish(file){it.writeBytes(bytes)}
    }
    private fun publish(file:File,write:(File)->Unit) {
        val pending=File(file.parentFile,file.name+".part")
        try {
            write(pending)
            Files.move(pending.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        }finally{pending.delete()}
    }
}
