package com.local.parallelvrgallerypro;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Headless acceptance restricted to copied QA inputs and its own newly inserted MediaStore IDs. */
final class OriginalExportQa {
    static File root(Context context) {
        File root=new File(context.getExternalFilesDir(null),"PVG_QA_original1900");
        if(!new File(root,"AUTHORIZED").isFile())throw new AssertionError("Explicit fixture authorization required");
        return root;
    }
    static Context scoped(Context context,File root)throws Exception {
        if(root.exists())throw new AssertionError("Preserve previous QA evidence");
        if(!root.mkdirs())throw new AssertionError("Cannot create QA root");
        File models=new File(root,"models");models.mkdirs();
        Files.copy(new File(root(context),"model-518.tflite").toPath(),new File(models,"depth_anything_v2.tflite").toPath());
        File high=new File(root(context),"model-770.tflite");
        if(high.isFile())Files.copy(high.toPath(),new File(models,"depth_anything_v2_small_770_v1.tflite").toPath());
        return new ContextWrapper(context){@Override public File getExternalFilesDir(String type){return root;}};
    }
    static PhotoItem photo(File file,long id)throws Exception {
        Bitmap b=BitmapFactory.decodeFile(file.getPath());if(b==null)throw new AssertionError("Missing QA source");
        try{return new PhotoItem(id,Uri.fromFile(file),file.getName(),b.getWidth(),b.getHeight(),file.length(),file.lastModified()/1000,
            MediaKind.IMAGE,"PVG_QA_original1900","PVG_QA_original1900","",null,false);}finally{b.recycle();}
    }
    static void images(Context context)throws Exception {
        File qa=root(context),run=new File(qa,"images");Context scoped=scoped(context,run);
        VrCacheManager cache=new VrCacheManager(scoped);VrGenerator generator=new VrGenerator(scoped,cache,new ModelManager(scoped));
        JSONArray rows=new JSONArray();File galleryPng=null;
        try {
            for(DepthEdgeMode mode:DepthEdgeMode.values()) {
                // Real authorized portrait for every mode; parameters are read back from this exact cache entry.
                PhotoItem photo=photo(new File(qa,"PVG_QA_P3.jpg"),-1900-mode.ordinal());
                VrGenerationParams params=ModeVisualProfile.Companion.defaults(mode).applyTo(ModeProfilesQa.base(mode)).toParams();
                VrCacheEntry entry=generator.generate(photo,params,p->kotlin.Unit.INSTANCE,0,()->true,e->cache.publish(photo,e,Collections.emptyList()),p->kotlin.Unit.INSTANCE);
                OriginalImageExportSpec spec=OriginalImageExportKt.readOriginalExportSpec(entry);
                byte[] paramsBefore=Files.readAllBytes(new File(entry.getParamsPath()).toPath());
                // Poison both JPEG previews and any old converted export: original rendering must ignore all three.
                Files.write(new File(entry.getLeftPath()).toPath(),new byte[]{1,2,3});Files.write(new File(entry.getRightPath()).toPath(),new byte[]{4,5,6});
                Files.write(new File(new File(entry.getParamsPath()).getParentFile(),"export_sbs.png").toPath(),new byte[]{7,8,9});
                File output=new File(run,mode.name()+".png");long start=System.nanoTime();
                generator.exportOriginalPng(photo,spec,output,()->true,(p,s)->kotlin.Unit.INSTANCE);
                double ms=(System.nanoTime()-start)/1e6;
                Bitmap actual=BitmapFactory.decodeFile(output.getPath());
                Bitmap source=MainActivityKt.decodeScaledBitmap(scoped,photo.getUri(),Math.max(spec.getEyeWidth(),spec.getEyeHeight()));
                Bitmap expected=null;
                try(IdleResourcePool.Borrowed<DepthModelSession> lease=generator.borrowDepthSession(spec.getParams(),p->kotlin.Unit.INSTANCE,s->kotlin.Unit.INSTANCE)) {
                    expected=generator.generateSbsBitmap(source,spec.getParams(),p->kotlin.Unit.INSTANCE,s->kotlin.Unit.INSTANCE,lease.getValue());
                    if(!LosslessImageExport.INSTANCE.samePixels(actual,expected))throw new AssertionError("PNG differs from fresh pre-JPEG projection: "+mode);
                    int originalX=mode.isReference()?0:source.getWidth();
                    int[] a=new int[source.getWidth()],b=new int[a.length];
                    for(int y=0;y<source.getHeight();y++) {
                        source.getPixels(a,0,a.length,0,y,a.length,1);actual.getPixels(b,0,b.length,originalX,y,b.length,1);
                        if(!Arrays.equals(a,b))throw new AssertionError("Original eye changed: "+mode);
                    }
                }finally{if(expected!=null)expected.recycle();actual.recycle();source.recycle();}
                if(!Arrays.equals(paramsBefore,Files.readAllBytes(new File(entry.getParamsPath()).toPath())))throw new AssertionError("Result parameters changed");
                if(new File(entry.getLeftPath()).length()!=3 || new File(entry.getRightPath()).length()!=3)throw new AssertionError("Preview cache overwritten");
                File cancelled=new File(run,mode.name()+"-cancelled.png");AtomicBoolean active=new AtomicBoolean(true);boolean rejected=false;
                try{generator.exportOriginalPng(photo,spec,cancelled,active::get,(p,s)->{if(p>=.65f)active.set(false);return kotlin.Unit.INSTANCE;});}
                catch(java.util.concurrent.CancellationException expectedError){rejected=true;}
                if(!rejected||cancelled.exists()||new File(cancelled+".part").exists())throw new AssertionError("Cancelled export published output");
                rows.put(new JSONObject().put("mode",mode.name()).put("recordedScale",spec.getParams().getDepthScale()).put("eyeWidth",spec.getEyeWidth()).put("eyeHeight",spec.getEyeHeight())
                    .put("renderAndPngMs",ms).put("pngMatchesFreshPreJpegProjection",true).put("originalEyeExact",true).put("poisonedPreviewIgnored",true).put("cacheParamsUnchanged",true).put("cancellationLeavesNoOutput",true));
                galleryPng=output;
            }
            // Exercise the real publication helper, only under a unique QA name; remove exactly that new URI.
            String name="PVG_QA_original1900_"+System.currentTimeMillis()+".png";
            File saved=galleryPng;Uri uri=MainActivityKt.saveImageToGallery(context,saved,name,"image/png",action->{action.invoke();return kotlin.Unit.INSTANCE;});
            try(InputStream in=context.getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream copy=new ByteArrayOutputStream();byte[] buffer=new byte[32768];int read;
                while((read=in.read(buffer))!=-1)copy.write(buffer,0,read);
                byte[] expected=Files.readAllBytes(saved.toPath()),actual=copy.toByteArray();
                if(!Arrays.equals(expected,actual))throw new AssertionError("Gallery copy differs");
            }finally{if(context.getContentResolver().delete(uri,null,null)!=1)throw new AssertionError("Own QA gallery cleanup failed");}
            boolean rejected=false;
            try{MainActivityKt.saveImageToGallery(context,saved,name+"-cancelled","image/png",action->{throw new IllegalStateException("QA deletion invalidated token before publish");});}
            catch(IllegalStateException expected){rejected=true;}
            if(!rejected)throw new AssertionError("Invalidated gallery publication was allowed");
            JSONObject report=new JSONObject().put("modes",rows).put("galleryPngBytesExact",true).put("ownGalleryUriRemoved",true).put("invalidatedGalleryPublishRejected",true);
            ColdGenerationQa.write(new File(qa,"original-export.json"),report);
        }finally{generator.close();}
    }
    static void moreImages(Context context)throws Exception {
        File qa=root(context),run=new File(qa,"more-images");Context scoped=scoped(context,run);
        VrCacheManager cache=new VrCacheManager(scoped);VrGenerator generator=new VrGenerator(scoped,cache,new ModelManager(scoped));
        JSONArray rows=new JSONArray(),sizes=new JSONArray();
        InferenceProfiling.INSTANCE.setListener((size,a,b,c,d)->{sizes.put(size);return kotlin.Unit.INSTANCE;});
        try {
            String[] names={"PVG_QA_P1.jpg","PVG_QA_P2.jpg","PVG_QA_O1.png","PVG_QA_P3.jpg"};
            DepthEdgeMode[] modes={DepthEdgeMode.COMFY_LEGACY_V10,DepthEdgeMode.LEGACY_V6,DepthEdgeMode.REFERENCE_V9,DepthEdgeMode.COMFY_LEGACY_V10};
            for(int i=0;i<names.length;i++) {
                PhotoItem photo=photo(new File(qa,names[i]),-1950-i);VrGenerationParams p=ModeProfilesQa.base(modes[i]).toParams();
                String model=i==3?"depth_anything_v2_small_770_v1":p.getDepthModel();
                VrGenerationParams params=new VrGenerationParams(model,p.getOutputMode(),p.getDepthScale(),p.getBlurRadius(),p.getFillRadius(),p.getInvertDepth(),1280,p.getModelThreads(),p.getUseGpu(),p.getGpuTestMode(),p.getForceGpuNoFallback(),p.getInpaintMode(),p.getQuality(),p.getEdgeMode(),p.getDepthUpscale(),p.getMatchV10Default(),p.getResultVariant(),p.getInvertModelDepth());
                VrCacheEntry entry=generator.generate(photo,params,v->kotlin.Unit.INSTANCE,0,()->true,e->cache.publish(photo,e,Collections.emptyList()),v->kotlin.Unit.INSTANCE);
                OriginalImageExportSpec spec=OriginalImageExportKt.readOriginalExportSpec(entry);File png=new File(run,"sample-"+i+".png");
                generator.exportOriginalPng(photo,spec,png,()->true,(v,s)->kotlin.Unit.INSTANCE);
                Bitmap actual=BitmapFactory.decodeFile(png.getPath()),source=MainActivityKt.decodeScaledBitmap(scoped,photo.getUri(),Math.max(spec.getEyeWidth(),spec.getEyeHeight()));
                try {
                    int[] a=new int[source.getWidth()],b=new int[a.length];int offset=modes[i].isReference()?0:a.length;
                    for(int y=0;y<source.getHeight();y++){source.getPixels(a,0,a.length,0,y,a.length,1);actual.getPixels(b,0,b.length,offset,y,b.length,1);if(!Arrays.equals(a,b))throw new AssertionError("Original eye differs: "+names[i]);}
                }finally{actual.recycle();source.recycle();}
                byte[] recorded=Files.readAllBytes(new File(entry.getParamsPath()).toPath());
                JSONObject invalid=new JSONObject(new String(recorded,java.nio.charset.StandardCharsets.UTF_8)).put("depthUpscale","corrupt");
                Files.write(new File(entry.getParamsPath()).toPath(),invalid.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                boolean rejected=false;try{OriginalImageExportKt.readOriginalExportSpec(entry);}catch(IllegalArgumentException expected){rejected=true;}
                finally{Files.write(new File(entry.getParamsPath()).toPath(),recorded);}
                if(!rejected)throw new AssertionError("Corrupt recorded options silently defaulted");
                PhotoItem missing=new PhotoItem(photo.getId(),Uri.fromFile(new File(qa,"PVG_QA_missing.jpg")),photo.getDisplayName(),photo.getWidth(),photo.getHeight(),photo.getSize(),photo.getModifiedTime(),MediaKind.IMAGE,"PVG_QA_original1900","PVG_QA_original1900","",null,false);
                File failed=new File(run,"missing-"+i+".png");rejected=false;
                try{generator.exportOriginalPng(missing,spec,failed,()->true,(v,s)->kotlin.Unit.INSTANCE);}catch(IllegalArgumentException expected){rejected=true;}
                if(!rejected||failed.exists())throw new AssertionError("Missing original substituted cache");
                rows.put(new JSONObject().put("source",names[i]).put("model",model).put("width",spec.getEyeWidth()*2).put("height",spec.getEyeHeight()).put("originalEyeExact",true).put("malformedOptionsRejected",true).put("missingOriginalRejected",true));
            }
            boolean high=false;for(int i=0;i<sizes.length();i++)if(sizes.getInt(i)==770)high=true;
            if(!high)throw new AssertionError("Recorded 770 model was not actually used");
            ColdGenerationQa.write(new File(qa,"additional-images.json"),new JSONObject().put("samples",rows).put("actualInferenceSizes",sizes));
        }finally{InferenceProfiling.INSTANCE.setListener(null);generator.close();}
    }
    static void retrieverOwnership(Context context)throws Exception {
        File qa=root(context);Context scoped=scoped(context,new File(qa,"retriever-ownership"));
        VrCacheManager cache=new VrCacheManager(scoped);
        VrGenerator copying=new VrGenerator(scoped,cache,new ModelManager(scoped),false),sharing=new VrGenerator(scoped,cache,new ModelManager(scoped),true);
        android.media.MediaMetadataRetriever retriever=new android.media.MediaMetadataRetriever();Bitmap frame=null;JSONArray rows=new JSONArray();
        try {
            retriever.setDataSource(scoped,Uri.fromFile(new File(qa,"PVG_QA_720p.mp4")));
            frame=retriever.getFrameAtTime(0,android.media.MediaMetadataRetriever.OPTION_CLOSEST);
            if(frame==null)throw new AssertionError("Missing retriever frame");
            int[] scan=new int[frame.getWidth()];for(int y=0;y<frame.getHeight();y++){frame.getPixels(scan,0,scan.length,0,y,scan.length,1);for(int pixel:scan)if((pixel>>>24)!=255)throw new AssertionError("Unexpected video alpha");}
            float[] raw;VrGenerationParams base=ModeProfilesQa.base(DepthEdgeMode.COMFY_LEGACY_V10).toParams();
            try(IdleResourcePool.Borrowed<DepthModelSession> lease=copying.borrowDepthSession(base,p->kotlin.Unit.INSTANCE,s->kotlin.Unit.INSTANCE)){raw=lease.getValue().run(frame,0,()->kotlin.Unit.INSTANCE);}
            for(DepthEdgeMode mode:DepthEdgeMode.values()) {
                VrGenerationParams params=ModeVisualProfile.Companion.defaults(mode).applyTo(ModeProfilesQa.base(mode)).toParams();
                Bitmap a=frame.copy(Bitmap.Config.ARGB_8888,false),b=frame.copy(Bitmap.Config.ARGB_8888,false);
                VideoSbsResult old=copying.finishVideoSbs(new VideoDepthResult(a,raw,new VideoFrameTimings()),params,new DepthTemporalSmoother());
                VideoSbsResult candidate=sharing.finishVideoSbs(new VideoDepthResult(b,raw,new VideoFrameTimings()),params,new DepthTemporalSmoother());
                try {
                    Bitmap original=mode.isReference()?candidate.getEyes().getLeft():candidate.getEyes().getRight();
                    if(original!=b||b.isRecycled())throw new AssertionError("Decoded-eye ownership not transferred: "+mode);
                    if(!LosslessImageExport.INSTANCE.samePixels(old.getEyes().getLeft(),candidate.getEyes().getLeft())||!LosslessImageExport.INSTANCE.samePixels(old.getEyes().getRight(),candidate.getEyes().getRight()))throw new AssertionError("Retriever projection pixels differ: "+mode);
                    rows.put(new JSONObject().put("mode",mode.name()).put("reusedOriginalEye",true).put("bothEyesPixelExact",true).put("sourceAliveUntilConsumersFinish",true));
                }finally{old.getEyes().recycle();candidate.getEyes().recycle();}
                if(!b.isRecycled())throw new AssertionError("Shared source was leaked");
            }
            ColdGenerationQa.write(new File(qa,"retriever-ownership.json"),new JSONObject().put("modes",rows).put("decodedPixelsOpaque",true).put("bitmapHasAlphaMetadata",frame.hasAlpha()));
        }finally{if(frame!=null)frame.recycle();retriever.release();copying.close();sharing.close();}
    }
    static void video(Context context,String label)throws Exception {
        if(!label.matches("[AB][1-4]"))throw new AssertionError("Explicit A/B label required");
        boolean reuse=label.startsWith("B");File qa=root(context),run=new File(qa,"video-"+label);Context scoped=scoped(context,run);
        VrCacheManager cache=new VrCacheManager(scoped);VrGenerator generator=new VrGenerator(scoped,cache,new ModelManager(scoped),reuse);
        JSONArray shared=new JSONArray();
        InferenceProfiling.INSTANCE.setVideoEyeReuseListener(value->{shared.put(value);return kotlin.Unit.INSTANCE;});
        try {
            VrGenerationParams params=ModeProfilesQa.base(DepthEdgeMode.COMFY_LEGACY_V10).toParams();
            generator.prepareImageSession(params,s->kotlin.Unit.INSTANCE);
            File input=new File(qa,"PVG_QA_720p.mp4");PhotoItem item=new PhotoItem(-1909,Uri.fromFile(input),input.getName(),1280,720,input.length(),input.lastModified()/1000,MediaKind.VIDEO,"PVG_QA_original1900","PVG_QA_original1900","",null,false);
            JSONArray frames=new JSONArray();StringBuilder runtime=new StringBuilder();long started=System.nanoTime();
            VideoCacheEntry entry=new VideoVrGenerator(scoped,new VideoVrCacheManager(scoped),generator).generate(item,new VideoGenerationParams(params,4,true,1),p->kotlin.Unit.INSTANCE,s->{runtime.append(s).append('\n');return kotlin.Unit.INSTANCE;},()->kotlin.Unit.INSTANCE,
                (p,index,total,fps,metrics,timing,stats)->{try{frames.put(new JSONObject().put("frame",index).put("cacheHit",metrics.getCacheHit()).put("depthMs",timing.getDepthMs()).put("sbsMs",timing.getSbsMs()).put("throughputMs",metrics.getThroughputMs()).put("encodeMs",timing.getEncodeMs()));}catch(Exception e){throw new RuntimeException(e);}return kotlin.Unit.INSTANCE;});
            double ms=(System.nanoTime()-started)/1e6;
            if(frames.length()!=48)throw new AssertionError("All 48 frames required");
            for(int i=0;i<frames.length();i++)if(frames.getJSONObject(i).getBoolean("cacheHit"))throw new AssertionError("Only fresh generation may be compared");
            Files.copy(new File(entry.getOutputPath()).toPath(),new File(run,"fresh-video.mp4").toPath());
            ColdGenerationQa.write(new File(run,"report.json"),new JSONObject().put("reuseOriginalEye",reuse).put("actualReusedEyePerFrame",shared).put("wallMs",ms).put("frames",frames).put("runtime",runtime.toString()));
        }finally{InferenceProfiling.INSTANCE.setVideoEyeReuseListener(null);generator.close();}
    }
}
