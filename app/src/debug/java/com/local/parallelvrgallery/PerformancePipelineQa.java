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

/** Real generators scoped to an explicit QA subtree. No Activity, VM, catalog or queues. */
final class PerformancePipelineQa {
    static void run(Context context, String label) throws Exception {
        File qa=new File(context.getExternalFilesDir(null),"PVG_QA_performance1700");
        if(!new File(qa,"AUTHORIZED").isFile() || !label.matches("baseline|optimized|confirmation"))throw new AssertionError("Explicit QA scope required");
        File root=new File(qa,label);if(root.exists())throw new AssertionError("Preserve previous measurement");root.mkdirs();
        Context scoped=new ContextWrapper(context){@Override public File getExternalFilesDir(String type){return root;}};
        File models=new File(root,"models");models.mkdirs();
        Files.copy(new File(context.getExternalFilesDir(null),"models/depth_anything_v2.tflite").toPath(),new File(models,"depth_anything_v2.tflite").toPath(),StandardCopyOption.REPLACE_EXISTING);
        VrCacheManager imageCache=new VrCacheManager(scoped);
        VrGenerator images=new VrGenerator(scoped,imageCache,new ModelManager(scoped));
        JSONArray report=new JSONArray();
        try {
            VrGenerationParams params=ModeProfilesQa.base(DepthEdgeMode.COMFY_LEGACY_V10).toParams();
            for(String name:new String[]{"PVG_QA_P1.jpg","PVG_QA_P2.jpg","PVG_QA_P3.jpg","PVG_QA_O1.png"}) {
                File input=new File(qa,name);Bitmap bounds=BitmapFactory.decodeFile(input.getPath());
                PhotoItem item=new PhotoItem(-1700L,Uri.fromFile(input),name,bounds.getWidth(),bounds.getHeight(),input.length(),input.lastModified()/1000,MediaKind.IMAGE,"PVG_QA_performance1700","PVG_QA_performance1700","",null,false);bounds.recycle();
                long started=System.nanoTime();
                VrCacheEntry entry=images.generate(item,params,p->kotlin.Unit.INSTANCE,0,()->true,e->imageCache.publish(item,e,java.util.Collections.emptyList()),p->kotlin.Unit.INSTANCE);
                report.put(new JSONObject().put("kind","image").put("sample",name).put("wallMs",(System.nanoTime()-started)/1e6).put("params",new JSONObject(new String(Files.readAllBytes(new File(entry.getParamsPath()).toPath()),java.nio.charset.StandardCharsets.UTF_8))));
                new File(root,"pipeline.json").toPath();
                write(new File(root,"pipeline.json"),report);
            }
            VideoVrGenerator videos=new VideoVrGenerator(scoped,new VideoVrCacheManager(scoped),images);
            File input=new File(qa,"PVG_QA_V1.mp4");
            PhotoItem item=new PhotoItem(-1701L,Uri.fromFile(input),input.getName(),640,360,input.length(),input.lastModified()/1000,MediaKind.VIDEO,"PVG_QA_performance1700","PVG_QA_performance1700","",null,false);
            File missingMarker=null;
            for(String pass:label.equals("confirmation")?new String[]{"cold","cached-resume"}:new String[]{"cold","cached-resume","mixed-resume"}) {
                if(pass.equals("mixed-resume")) {
                    if(missingMarker==null || !missingMarker.delete())throw new AssertionError("Explicit QA frame marker missing");
                }
                JSONArray frames=new JSONArray();long started=System.nanoTime();StringBuilder runtime=new StringBuilder();
                VideoCacheEntry entry=videos.generate(item,new VideoGenerationParams(params,4,true,1),p->kotlin.Unit.INSTANCE,s->{runtime.append(s).append('\n');return kotlin.Unit.INSTANCE;},()->kotlin.Unit.INSTANCE,
                    (p,index,total,fps,metrics,timings,stats)->{try {frames.put(new JSONObject().put("frame",index).put("throughputMs",metrics.getThroughputMs()).put("latencyMs",metrics.getLatencyMs()).put("decode",timings.getDecodeMs()).put("depth",timings.getDepthMs()).put("post",timings.getDepthPostMs()).put("sbs",timings.getSbsMs()).put("encode",timings.getEncodeMs()).put("cacheHit",metrics.getCacheHit()).put("lastWrite",stats.getLastCacheWriteMs()));}catch(Exception e){throw new RuntimeException(e);}return kotlin.Unit.INSTANCE;});
                double wallMs=(System.nanoTime()-started)/1e6;
                missingMarker=new File(new File(entry.getOutputPath()).getParentFile(),"frames/000008.complete");
                File copy=new File(root,"video-"+pass+".mp4");Files.copy(new File(entry.getOutputPath()).toPath(),copy.toPath(),StandardCopyOption.REPLACE_EXISTING);
                report.put(new JSONObject().put("kind","video").put("pass",pass).put("wallMs",wallMs).put("frames",frames).put("runtime",runtime.toString()).put("output",copy.getPath()));
                write(new File(root,"pipeline.json"),report);
            }
        }finally{images.close();}
    }
    private static void write(File path,JSONArray data)throws Exception{try(FileOutputStream out=new FileOutputStream(path)){out.write(data.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
}
