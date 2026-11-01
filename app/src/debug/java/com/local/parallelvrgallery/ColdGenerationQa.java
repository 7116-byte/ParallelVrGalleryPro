package com.local.parallelvrgallerypro;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.json.*;

/** Fresh generation only; all generators and writes are confined to authorized QA files. */
final class ColdGenerationQa {
    static File root(Context context) {
        File root=new File(context.getExternalFilesDir(null),"PVG_QA_cold1800");
        if(!new File(root,"AUTHORIZED").isFile())throw new AssertionError("Explicit QA authorization marker required");
        return root;
    }
    static void pipeline(Context context,String label)throws Exception {
        pipeline(context,label,true);
    }
    static void videoOnly(Context context,String label)throws Exception {pipeline(context,label,false);}
    private static void pipeline(Context context,String label,boolean imagesFirst)throws Exception {
        if(!label.matches("[AB][1-4]"))throw new AssertionError("Explicit paired measurement label required");
        File qa=root(context),root=new File(qa,(imagesFirst?"":"videoOnly-")+label);if(root.exists())throw new AssertionError("Preserve old measurement");root.mkdirs();
        Context scoped=new ContextWrapper(context){@Override public File getExternalFilesDir(String type){return root;}};
        File models=new File(root,"models");models.mkdirs();
        Files.copy(new File(qa,"model-518.tflite").toPath(),new File(models,"depth_anything_v2.tflite").toPath());
        VrCacheManager cache=new VrCacheManager(scoped);VrGenerator generator=new VrGenerator(scoped,cache,new ModelManager(scoped));
        JSONArray report=new JSONArray(),inference=new JSONArray();
        InferenceProfiling.INSTANCE.setListener((size,resize,pack,invoke,read)->{try{
            inference.put(new JSONObject().put("size",size).put("resizeMs",resize/1e6).put("packMs",pack/1e6).put("invokeMs",invoke/1e6).put("readMs",read/1e6));
        }catch(Exception e){throw new RuntimeException(e);}return kotlin.Unit.INSTANCE;});
        try {
            VrGenerationParams params=ModeProfilesQa.base(DepthEdgeMode.COMFY_LEGACY_V10).toParams();
            for(String name:imagesFirst?new String[]{"PVG_QA_P1.jpg","PVG_QA_P2.jpg","PVG_QA_P3.jpg","PVG_QA_O1.png"}:new String[]{}) {
                File file=new File(qa,name);Bitmap bitmap=BitmapFactory.decodeFile(file.getPath());
                PhotoItem item=new PhotoItem(-1800L,Uri.fromFile(file),name,bitmap.getWidth(),bitmap.getHeight(),file.length(),file.lastModified()/1000,MediaKind.IMAGE,"PVG_QA_cold1800","PVG_QA_cold1800","",null,false);bitmap.recycle();
                long start=System.nanoTime();int first=inference.length();
                VrCacheEntry entry=generator.generate(item,params,p->kotlin.Unit.INSTANCE,0,()->true,e->cache.publish(item,e,java.util.Collections.emptyList()),p->kotlin.Unit.INSTANCE);
                report.put(new JSONObject().put("kind","image").put("sample",name).put("wallMs",(System.nanoTime()-start)/1e6).put("inferenceIndex",first).put("params",new JSONObject(new String(Files.readAllBytes(new File(entry.getParamsPath()).toPath()),java.nio.charset.StandardCharsets.UTF_8))));
            }
            File input=new File(qa,"PVG_QA_720p.mp4");
            PhotoItem item=new PhotoItem(-1801L,Uri.fromFile(input),input.getName(),1280,720,input.length(),input.lastModified()/1000,MediaKind.VIDEO,"PVG_QA_cold1800","PVG_QA_cold1800","",null,false);
            VideoVrGenerator video=new VideoVrGenerator(scoped,new VideoVrCacheManager(scoped),generator);
            JSONArray frames=new JSONArray();StringBuilder runtime=new StringBuilder();int first=inference.length();long started=System.nanoTime();
            VideoCacheEntry entry=video.generate(item,new VideoGenerationParams(params,4,true,1),p->kotlin.Unit.INSTANCE,s->{runtime.append(s).append('\n');return kotlin.Unit.INSTANCE;},()->kotlin.Unit.INSTANCE,
                (p,index,total,fps,metrics,timing,stats)->{try{frames.put(new JSONObject().put("frame",index).put("cacheHit",metrics.getCacheHit()).put("throughputMs",metrics.getThroughputMs()).put("decodeMs",timing.getDecodeMs()).put("depthMs",timing.getDepthMs()).put("postMs",timing.getDepthPostMs()).put("sbsMs",timing.getSbsMs()).put("encodeMs",timing.getEncodeMs()).put("writeMs",stats.getLastCacheWriteMs()));}catch(Exception e){throw new RuntimeException(e);}return kotlin.Unit.INSTANCE;});
            for(int i=0;i<frames.length();i++)if(frames.getJSONObject(i).getBoolean("cacheHit"))throw new AssertionError("Fresh generation cannot use cached frames");
            report.put(new JSONObject().put("kind","video").put("wallMs",(System.nanoTime()-started)/1e6).put("inferenceIndex",first).put("frames",frames).put("runtime",runtime.toString()));
            Files.copy(new File(entry.getOutputPath()).toPath(),new File(root,"fresh-video.mp4").toPath());
            write(new File(root,"report.json"),new JSONObject().put("runs",report).put("inference",inference));
        }finally{InferenceProfiling.INSTANCE.setListener(null);generator.close();}
    }
    static void kernels(Context context)throws Exception {
        File root=root(context);JSONArray report=new JSONArray();
        Bitmap bitmap=BitmapFactory.decodeFile(new File(root,"PVG_QA_P3.jpg").getPath());
        try {
            for(GpuTestMode mode:new GpuTestMode[]{GpuTestMode.ACCURATE_UNSET,GpuTestMode.ACCURATE_OPENGL,GpuTestMode.ACCURATE_OPENCL}) {
                JSONArray times=new JSONArray();StringBuilder runtime=new StringBuilder();
                InferenceProfiling.INSTANCE.setListener((size,resize,pack,invoke,read)->{try{times.put(new JSONObject().put("resize",resize/1e6).put("pack",pack/1e6).put("invoke",invoke/1e6).put("read",read/1e6));}catch(Exception e){throw new RuntimeException(e);}return kotlin.Unit.INSTANCE;});
                try(DepthModelSession session=new DepthModelSession(new File(root,"model-518.tflite"),4,true,mode,true,s->{runtime.append(s).append('\n');return kotlin.Unit.INSTANCE;})) {
                    for(int i=0;i<6;i++) {
                        float[] values=session.run(bitmap,0,()->kotlin.Unit.INSTANCE);
                        if(i==5)try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(root,"kernel-"+mode.name()+".f32"))))){for(float value:values)out.writeFloat(value);}
                    }
                    report.put(new JSONObject().put("mode",mode.name()).put("times",times).put("runtime",runtime.toString()));
                }catch(Throwable error){report.put(new JSONObject().put("mode",mode.name()).put("error",error.toString()));}
            }
        }finally{bitmap.recycle();InferenceProfiling.INSTANCE.setListener(null);}
        write(new File(root,"kernels.json"),report);
    }
    static void preferences(Context context)throws Exception {
        File root=root(context);JSONArray report=new JSONArray();
        for(int preference:new int[]{0,1}) {
            org.tensorflow.lite.gpu.GpuDelegate delegate=null;org.tensorflow.lite.Interpreter interpreter=null;
            try(RandomAccessFile file=new RandomAccessFile(new File(root,"model-518.tflite"),"r")) {
                java.nio.ByteBuffer model=file.getChannel().map(java.nio.channels.FileChannel.MapMode.READ_ONLY,0,file.length());
                delegate=new org.tensorflow.lite.gpu.GpuDelegate(new org.tensorflow.lite.gpu.GpuDelegate.Options().setPrecisionLossAllowed(false).setInferencePreference(preference).setForceBackend(org.tensorflow.lite.gpu.GpuDelegateFactory.Options.GpuBackend.OPENCL));
                interpreter=new org.tensorflow.lite.Interpreter(model,new org.tensorflow.lite.Interpreter.Options().addDelegate(delegate));
                for(String name:new String[]{"PVG_QA_P1.jpg","PVG_QA_P2.jpg","PVG_QA_P3.jpg","PVG_QA_O1.png"}) {
                    Bitmap original=BitmapFactory.decodeFile(new File(root,name).getPath()),scaled=Bitmap.createScaledBitmap(original,518,518,true);
                    int[] pixels=new int[518*518];scaled.getPixels(pixels,0,518,0,0,518,518);if(scaled!=original)scaled.recycle();original.recycle();
                    float[] rgb=new float[pixels.length*3];int offset=0;for(int pixel:pixels){rgb[offset++]=((pixel>>>16)&255)/255f;rgb[offset++]=((pixel>>>8)&255)/255f;rgb[offset++]=(pixel&255)/255f;}
                    java.nio.ByteBuffer input=java.nio.ByteBuffer.allocateDirect(rgb.length*4).order(java.nio.ByteOrder.nativeOrder());input.asFloatBuffer().put(rgb);
                    java.nio.ByteBuffer output=java.nio.ByteBuffer.allocateDirect(pixels.length*4).order(java.nio.ByteOrder.nativeOrder());JSONArray times=new JSONArray();
                    for(int repeat=0;repeat<3;repeat++){input.rewind();output.clear();long start=System.nanoTime();interpreter.run(input,output);times.put((System.nanoTime()-start)/1e6);}
                    float[] values=new float[pixels.length];output.rewind();output.asFloatBuffer().get(values);float min=Float.MAX_VALUE,max=-Float.MAX_VALUE;
                    for(float value:values){if(!Float.isFinite(value))throw new AssertionError("Nonfinite output");min=Math.min(min,value);max=Math.max(max,value);}
                    float range=Math.max(.0001f,max-min);
                    try(DataOutputStream stream=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(root,"preference-"+preference+"-"+name+".f32"))))){for(float value:values)stream.writeFloat((value-min)/range);}
                    report.put(new JSONObject().put("preference",preference).put("sample",name).put("invokeMs",times));
                }
            }finally{if(interpreter!=null)interpreter.close();if(delegate!=null)delegate.close();}
        }
        write(new File(root,"preferences.json"),report);
    }
    static void write(File file,Object value)throws Exception{try(FileOutputStream out=new FileOutputStream(file)){out.write(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
}
