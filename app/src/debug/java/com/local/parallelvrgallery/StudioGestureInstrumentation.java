package com.local.parallelvrgallerypro;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.provider.MediaStore;
import android.database.Cursor;
import java.io.File;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Real multi-pointer input on an explicitly named QA album; debug builds only. */
public final class StudioGestureInstrumentation extends Instrumentation {
    private String album, sample, kind;
    private boolean compatibilityDialogInput;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        album = args.getString("album", ""); sample = args.getString("sample", "");
        kind = args.getString("kind", "gestures");
        if (!album.startsWith("PVG_QA_") || !sample.startsWith("PVG_QA_")) {
            Bundle result = new Bundle(); result.putString("error", "Explicit QA album/sample required"); finish(1, result); return;
        }
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if(kind.equals("performanceConfirmation")) {
                PerformancePipelineQa.run(getTargetContext(),"confirmation");
                result.putString("qa","PASS: final generation confirmation without altering a cached frame");finish(0,result);return;
            }
            if(kind.equals("performanceAvif")) {
                PerformanceAvifQa.run(getTargetContext());
                result.putString("qa","PASS: AVIF Android/library roundtrip diagnostics recorded");finish(0,result);return;
            }
            if(kind.equals("performancePng")) {
                PerformancePngQa.run(getTargetContext());
                result.putString("qa","PASS: fast PNG matches every decoded Android pixel of eight actual model/projection maps");finish(0,result);return;
            }
            if(kind.equals("performanceBaseline") || kind.equals("performanceOptimized")) {
                PerformancePipelineQa.run(getTargetContext(),kind.equals("performanceBaseline")?"baseline":"optimized");
                result.putString("qa","PASS: scoped actual image/video generators benchmarked");finish(0,result);return;
            }
            if(kind.equals("performanceFormats")) {
                PerformanceFormats.run(getTargetContext());
                result.putString("qa","PASS: QA format timing and decoded pixel error recorded; no gallery or queue access");finish(0,result);return;
            }
            if(kind.equals("highResolutionPipeline")) {
                HighResolutionQa.runPipeline(getTargetContext());
                result.putString("qa","PASS: actual session and six-mode pipeline at 518/770; isolated authorized inputs; no catalog writes");finish(0,result);return;
            }
            if(kind.equals("highResolution")) {
                HighResolutionQa.run(getTargetContext());
                result.putString("qa","DONE: isolated fixed 518/770 GPU research; inspect per-size results; no CPU fallback or gallery writes");finish(0,result);return;
            }
            if(kind.equals("bodyCalibration")) {
                BodyCalibrationQa.run(getTargetContext());
                result.putString("qa","PASS: user-supplied scales and V11/35 control rendered; only isolated QA inputs, no inference or queues");finish(0,result);return;
            }
            if(kind.equals("referenceDirection")) {
                FixedCalibrationQa.run(getTargetContext(),true);
                result.putString("qa","PASS: V8/V9 reversed-depth candidate eye images rendered from fixed QA inputs; no new inference");finish(0,result);return;
            }
            if(kind.equals("modelInputProbe")) {
                ModelInputProbeQa.run(getTargetContext());
                result.putString("qa","DONE: isolated GPU model input allocation/inference probe; inspect per-size report, failures are retained");finish(0,result);return;
            }
            if(kind.equals("fixedCalibration")) {
                FixedCalibrationQa.run(getTargetContext());
                result.putString("qa","PASS: fixed-strength real renderer candidates; previously captured QA depth; no new inference/gallery/queue writes");finish(0,result);return;
            }
            if(kind.equals("modeProfiles")) {
                ModeProfilesQa.run(getTargetContext());
                result.putString("qa", "PASS: isolated settings migration/cold restores; six real GPU modes and resolution cases; no gallery/queue writes");
                finish(0,result);return;
            }
            if(kind.equals("comfyPipeline")) {
                File root=new File(getTargetContext().getExternalFilesDir(null),"PVG_QA_comfy_1400");
                if(!root.isDirectory())throw new AssertionError("Explicit private QA directory missing");
                StringBuilder runtime=new StringBuilder();
                org.json.JSONArray checks=new org.json.JSONArray();
                VrGenerator generator=new VrGenerator(getTargetContext(),new VrCacheManager(getTargetContext()),new ModelManager(getTargetContext()));
                DepthModelSession session=new DepthModelSession(new File(getTargetContext().getExternalFilesDir(null),"models/depth_anything_v2.tflite"),4,true,GpuTestMode.ACCURATE_UNSET,true,value->{runtime.append(value).append('\n');return kotlin.Unit.INSTANCE;});
                try {
                    for(DepthEdgeMode mode:new DepthEdgeMode[]{DepthEdgeMode.COMFY_LEGACY_V10,DepthEdgeMode.COMFY_SUBPIXEL_V11}) {
                        VrGenerationParams params=new VrGenerationParams("depth_anything_v2.tflite","SBS_PARALLEL",60f,3,10,true,1280,4,true,GpuTestMode.ACCURATE_UNSET,true,"FOREGROUND_FILL",94,mode,new DepthUpscaleSettings(false,0,DepthInterpolation.BILINEAR,DepthResizeOrder.SMOOTH_THEN_RESIZE));
                        Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(new File(root,"PVG_QA_P3.jpg").getPath());
                        Bitmap sbs;
                        try {sbs=generator.generateSbsBitmap(bitmap,params,value->kotlin.Unit.INSTANCE,value->kotlin.Unit.INSTANCE,session);}
                        finally {bitmap.recycle();}
                        Bitmap expected=android.graphics.BitmapFactory.decodeFile(new File(root,mode==DepthEdgeMode.COMFY_LEGACY_V10?"PVG_QA_P3.jpg.main-s60.png":"PVG_QA_P3.jpg.smooth-s60.png").getPath());
                        try {
                            if(sbs.getWidth()!=expected.getWidth()*2||sbs.getHeight()!=expected.getHeight())throw new AssertionError("Actual pipeline changed QA output size");
                            int[] row=new int[expected.getWidth()],reference=new int[expected.getWidth()];long differing=0;
                            for(int y=0;y<expected.getHeight();y++) {sbs.getPixels(row,0,row.length,0,y,row.length,1);expected.getPixels(reference,0,reference.length,0,y,reference.length,1);for(int x=0;x<row.length;x++)if(row[x]!=reference[x])differing++;}
                            if(differing!=0)throw new AssertionError("Actual image pipeline differs from direct tested renderer: "+differing);
                            checks.put(new org.json.JSONObject().put("mode",mode.name()).put("imagePipelineMatchesDirect",true).put("differingPixels",differing));
                        } finally {sbs.recycle();expected.recycle();}
                        DepthTemporalSmoother smoother=new DepthTemporalSmoother();
                        for(int frame=0;frame<3;frame++) {
                            Bitmap synthetic=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);
                            android.graphics.Canvas canvas=new android.graphics.Canvas(synthetic);canvas.drawColor(android.graphics.Color.rgb(30,70,100));
                            android.graphics.Paint paint=new android.graphics.Paint();paint.setColor(android.graphics.Color.rgb(200,140,60));canvas.drawCircle(120+frame*10,90,45,paint);
                            VideoSbsResult rendered=generator.generateVideoSbsBitmap(synthetic,params,session,smoother);
                            StereoBitmapPair eyes=rendered.getEyes();
                            try {if(eyes.getLeft().getWidth()!=320||eyes.getRight().getHeight()!=180)throw new AssertionError("Video eye geometry");}
                            finally {eyes.getLeft().recycle();eyes.getRight().recycle();}
                        }
                        checks.put(new org.json.JSONObject().put("mode",mode.name()).put("threeSyntheticVideoFrames",true));
                    }
                } finally {session.close();generator.close();}
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(root,"pipeline-report.json"))){out.write(checks.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                result.putString("qa","PASS: actual image pipeline equals tested raster; both modes process temporal synthetic video frames; no gallery writes");finish(0,result);return;
            }
            if(kind.equals("comfyRaster")) {
                // Runs without Activity/ViewModel/MediaStore; never admits gallery jobs.
                File root=new File(getTargetContext().getExternalFilesDir(null),"PVG_QA_comfy_1400");
                if(!root.isDirectory())throw new AssertionError("Explicit private QA directory missing");
                StringBuilder runtime=new StringBuilder();
                org.json.JSONArray report=new org.json.JSONArray();
                DepthModelSession session=new DepthModelSession(new File(getTargetContext().getExternalFilesDir(null),"models/depth_anything_v2.tflite"),4,true,GpuTestMode.ACCURATE_UNSET,true,value->{runtime.append(value).append('\n');return kotlin.Unit.INSTANCE;});
                try {
                    for(String name:new String[]{"PVG_QA_O1.png","PVG_QA_P1.jpg","PVG_QA_P2.jpg","PVG_QA_P3.jpg"}) {
                        Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(new File(root,name).getPath());
                        if(bitmap==null)throw new AssertionError("Missing explicit QA input: "+name);
                        try {
                            try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(root,name+".decoded.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
                            long start=SystemClock.elapsedRealtime();
                            float[] raw=session.run(bitmap,0,()->kotlin.Unit.INSTANCE);
                            org.json.JSONObject item=new org.json.JSONObject().put("sample",name).put("width",bitmap.getWidth()).put("height",bitmap.getHeight()).put("gpuMs",SystemClock.elapsedRealtime()-start);
                            try(java.io.DataOutputStream out=new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(new File(root,name+".raw-f32-be")),262144))){for(float value:raw)out.writeFloat(value);}
                            start=SystemClock.elapsedRealtime();
                            float[] prepared=ComfyLegacyStereoRenderer.INSTANCE.prepareDepth(raw,518,518,bitmap.getWidth(),bitmap.getHeight(),3,true);
                            item.put("prepareMs",SystemClock.elapsedRealtime()-start);
                            try(java.io.DataOutputStream out=new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(new File(root,name+".main-depth-f32-be")),262144))){for(float value:prepared)out.writeFloat(value);}
                            for(float scale:new float[]{40f,60f}) {
                                start=SystemClock.elapsedRealtime();
                                kotlin.Pair<Bitmap,Bitmap> eyes=ComfyLegacyStereoRenderer.INSTANCE.renderPrepared(bitmap,prepared,scale,false);
                                item.put("render"+(int)scale+"Ms",SystemClock.elapsedRealtime()-start);
                                if(scale==40f)try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(root,name+".decoded.png"))){eyes.getSecond().compress(Bitmap.CompressFormat.PNG,100,out);}
                                try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(root,name+".main-s"+(int)scale+".png"))){eyes.getFirst().compress(Bitmap.CompressFormat.PNG,100,out);}
                                finally {eyes.getFirst().recycle();eyes.getSecond().recycle();}
                                start=SystemClock.elapsedRealtime();
                                eyes=ComfyLegacyStereoRenderer.INSTANCE.renderPrepared(bitmap,prepared,scale,true);
                                item.put("subpixel"+(int)scale+"Ms",SystemClock.elapsedRealtime()-start);
                                try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(root,name+".smooth-s"+(int)scale+".png"))){eyes.getFirst().compress(Bitmap.CompressFormat.PNG,100,out);}
                                finally {eyes.getFirst().recycle();eyes.getSecond().recycle();}
                            }
                            for(boolean matched:new boolean[]{false,true}) {
                                float scale=matched?(float)(255.0*60*500/bitmap.getWidth()/bitmap.getWidth()):5f;
                                kotlin.Pair<Bitmap,Bitmap> eyes=ReferenceStereoRenderer.INSTANCE.render(bitmap,raw,518,518,scale,true);
                                try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(root,name+(matched?".v9-matched60.png":".v9-s5.png")))){eyes.getSecond().compress(Bitmap.CompressFormat.PNG,100,out);}
                                finally {eyes.getFirst().recycle();eyes.getSecond().recycle();}
                            }
                            report.put(item);
                        } finally {bitmap.recycle();}
                    }
                } finally {session.close();}
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(root,"runtime.txt"))){out.write(runtime.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(root,"report.json"))){out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                result.putString("qa","PASS: four explicit inputs, forced GPU, main Legacy 40/60 and matched-disparity reference; private output only");finish(0,result);return;
            }
            if(kind.equals("referenceRaster")) {
                File root=new File(getTargetContext().getExternalFilesDir(null),"PVG_QA_reference_1301");
                if(!root.isDirectory())throw new AssertionError("Explicit private QA directory missing");
                StringBuilder runtime=new StringBuilder();
                DepthModelSession session=new DepthModelSession(new File(getTargetContext().getExternalFilesDir(null),"models/depth_anything_v2.tflite"),4,true,GpuTestMode.ACCURATE_UNSET,true,value->{runtime.append(value).append('\n');return kotlin.Unit.INSTANCE;});
                try {
                    for(String name:new String[]{"PVG_QA_O1.png","PVG_QA_P1.jpg","PVG_QA_P2.jpg","PVG_QA_P3.jpg"}){
                        android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(new File(root,name).getPath());
                        if(bitmap==null)throw new AssertionError("Missing explicit QA input: "+name);
                        try {
                            try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new File(root,name+".decoded.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}
                            float[] depth=session.run(bitmap,0,()->kotlin.Unit.INSTANCE);
                            try(java.io.DataOutputStream stream=new java.io.DataOutputStream(new java.io.FileOutputStream(new File(root,name+".depth-f32-be")))){for(float value:depth)stream.writeFloat(value);}
                            for(boolean smooth:new boolean[]{false,true}){
                                kotlin.Pair<android.graphics.Bitmap,android.graphics.Bitmap> eyes=ReferenceStereoRenderer.INSTANCE.render(bitmap,depth,518,518,5f,smooth);
                                if(!smooth)try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new File(root,name+".decoded.png"))){eyes.getFirst().compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}
                                try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new File(root,name+(smooth?".v9.png":".v8.png")))){eyes.getSecond().compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}
                                finally{eyes.getFirst().recycle();eyes.getSecond().recycle();}
                            }
                            android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("settings",0);
                            DepthUpscaleSettings upscale=new DepthUpscaleSettings(prefs.getBoolean("depthUpscaleEnabled",false),prefs.getInt("depthUpscaleMaxLongEdge",0),DepthInterpolation.Companion.restore(prefs.getString("depthInterpolation",null)),DepthResizeOrder.Companion.restore(prefs.getString("depthResizeOrder",null)));
                            for(boolean smooth:new boolean[]{false,true}){
                                VrGenerationParams params=new VrGenerationParams("depth_anything_v2.tflite","SBS_PARALLEL",prefs.getFloat("depthScale",5f),prefs.getInt("blurRadius",3),prefs.getInt("fillRadius",10),prefs.getBoolean("invertDepth",false),1536,4,true,GpuTestMode.ACCURATE_UNSET,true,"FOREGROUND_FILL",94,smooth?DepthEdgeMode.REFERENCE_V9:DepthEdgeMode.REFERENCE_V8,upscale);
                                long budget=Math.min(Runtime.getRuntime().maxMemory()/8,96L*1024*1024);
                                DepthMap prepared=DepthPostProcessing.INSTANCE.process(new DepthMap(depth,518,518),bitmap.getWidth(),bitmap.getHeight(),params,Math.max(1,budget/16));
                                if(!smooth){
                                    try(java.io.DataOutputStream stream=new java.io.DataOutputStream(new java.io.FileOutputStream(new File(root,name+".current-depth-f32-be")))){for(float value:prepared.getValues())stream.writeFloat(value);}
                                    org.json.JSONObject meta=new org.json.JSONObject().put("width",prepared.getWidth()).put("height",prepared.getHeight()).put("scale",params.getDepthScale()).put("depthUpscale",upscale.getEnabled()).put("interpolation",upscale.getInterpolation().name()).put("order",upscale.getOrder().name());
                                    try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new File(root,name+".current.json"))){stream.write(meta.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                                }
                                kotlin.Pair<android.graphics.Bitmap,android.graphics.Bitmap> eyes=ReferenceStereoRenderer.INSTANCE.render(bitmap,prepared.getValues(),prepared.getWidth(),prepared.getHeight(),params.getDepthScale(),smooth);
                                try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new File(root,name+(smooth?".current-v9.png":".current-v8.png")))){eyes.getSecond().compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}
                                finally{eyes.getFirst().recycle();eyes.getSecond().recycle();}
                            }
                        } finally{bitmap.recycle();}
                    }
                } finally{session.close();}
                try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new File(root,"runtime.txt"))){stream.write(runtime.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                result.putString("result","PASS: four explicit private QA inputs; forced GPU; same float depth rendered by V8 and V9; no MediaStore/queue operations");finish(-1,result);return;
            }
            android.accessibilityservice.AccessibilityServiceInfo info=getUiAutomation().getServiceInfo();
            info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                    | android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            getUiAutomation().setServiceInfo(info);
            // Shell launch also works under Android 17's background activity restrictions.
            shell("am start -W -f 0x10008000 -n com.local.parallelvrgallerypro/.MainActivity");
            SystemClock.sleep(1200);
            if (find("Android 应用兼容性") != null) click("确定");
            if(kind.equals("selection")) {
                verifySelectionScope();
                click("生成");click("图片");
                String version="旧版兼容 · 强度 60\n1536px";
                click(version);
                Rect label=new Rect();find(version).getBoundsInScreen(label);
                Rect screen=new Rect();requireRoot().getBoundsInScreen(screen);
                float x=screen.width()*.1f,y=label.bottom+100;
                shell("input swipe "+(int)x+" "+(int)y+" "+(int)x+" "+(int)y+" 900");
                if(!waitFor("当前列表 · 已选 1 项"))throw new AssertionError("Long press was toggled off on release");
                click("取消选择");
                long down=SystemClock.uptimeMillis();
                inject(down,down,MotionEvent.ACTION_DOWN,new float[]{x},new float[]{y});SystemClock.sleep(700);
                for(int i=1;i<=30;i++){
                    inject(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,new float[]{x+screen.width()*.4f*i/30},new float[]{y});SystemClock.sleep(20);
                }
                inject(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,new float[]{x+screen.width()*.4f},new float[]{y});SystemClock.sleep(300);
                if(!waitFor("当前列表 · 已选 3 项"))throw new AssertionError("Held drag did not select the first three tiles");
                click("全选");if(!waitFor("当前列表 · 已选 6 项"))throw new AssertionError("Select-all escaped the explicit QA version");
                click("取消选择");
                result.putString("result","PASS: long-press persists after release; initial held drag selects three; select-all exactly six QA results; cancel preserves files");
                finish(-1,result);return;
            }
            if (kind.equals("videoResume")) { click("生成"); click("视频"); click(sample); }
            else { click("相册"); click(album); click(sample); }
            if(kind.equals("imageWrites")) {
                if(!album.equals("PVG_QA_profiles_1500")||!sample.equals("PVG_QA_S1.jpg"))throw new AssertionError("Synthetic S1 fixture only");
                click("调试");
                long id=sampleId();long deadline=SystemClock.uptimeMillis()+40000;
                java.util.Map<String,String> before;
                do {before=qaImageHashes(id);if(!before.isEmpty())break;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<deadline);
                if(before.isEmpty())throw new AssertionError("Synthetic base result missing");
                click("使用新参数生成（保留旧结果）");
                java.util.Map<String,String> added=waitQaImageChange(id,before,null,true);
                assertQaHistory(before,added);
                java.util.Set<String> newKeys=new java.util.HashSet<>(added.keySet());newKeys.removeAll(before.keySet());
                if(newKeys.size()!=1)throw new AssertionError("Additive action did not create exactly one slot");
                click("重新生成（替换当前旧结果）");click("重新生成");
                java.util.Map<String,String> replaced=waitQaImageChange(id,added,newKeys.iterator().next(),false);
                assertQaHistory(before,replaced);
                String source="/sdcard/Pictures/PVG_QA_profiles_1500/PVG_QA_S1.jpg",held=source+".PVG_QA_hold";
                shell("mv "+source+" "+held);
                try {
                    click("重新生成（替换当前旧结果）");click("重新生成");
                    deadline=SystemClock.uptimeMillis()+40000;boolean failed=false;
                    File queue=new File(getTargetContext().getExternalFilesDir(null),"queue_index/image_queue.tsv");
                    while(SystemClock.uptimeMillis()<deadline) {
                        String text=new String(java.nio.file.Files.readAllBytes(queue.toPath()),java.nio.charset.StandardCharsets.UTF_8);
                        for(String line:text.split("\\n"))if(line.startsWith(id+"_")&&line.contains("\tFAILED\t"))failed=true;
                        if(failed)break;SystemClock.sleep(100);
                    }
                    if(!failed)throw new AssertionError("Explicit missing-fixture decode did not fail");
                    if(!replaced.equals(qaImageHashes(id)))throw new AssertionError("Failure modified old results");
                    saveComparisonScreenshot("image-regeneration-failed-preserved");
                } finally {shell("mv "+held+" "+source);}
                org.json.JSONObject evidence=new org.json.JSONObject().put("syntheticId",id).put("before",new org.json.JSONObject(before))
                    .put("additive",new org.json.JSONObject(added)).put("replacement",new org.json.JSONObject(replaced))
                    .put("allOldHashesKeptByAdditive",true).put("onlyCurrentRetiredByReplacement",true).put("allOldHashesKeptOnActualFailure",true);
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"PVG_QA_profiles_1500/image-write-report.json"))) {
                    out.write(evidence.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                result.putString("result","PASS: real synthetic UI additive keeps all history hashes; replacement retires only current result; actual missing-input failure preserves all old bytes");
                finish(-1,result);return;
            }
            if (kind.equals("comparison")) {
                Rect title = new Rect(), debug = new Rect();
                find(sample).getBoundsInScreen(title);find("调试").getBoundsInScreen(debug);
                if(title.right>debug.left || Math.abs(title.centerY()-debug.centerY())>debug.height())
                    throw new AssertionError("Long filename displaced viewer actions");
                saveComparisonScreenshot("viewer-long-title");
                // Screenshot compression may outlast the viewer's idle timeout; refresh live bounds.
                if(find(sample)==null){Rect screen=new Rect();requireRoot().getBoundsInScreen(screen);tap(screen.width()*.25f,screen.height()*.5f);}
                find(sample).getBoundsInScreen(title);
                shell("input swipe "+title.centerX()+" "+title.centerY()+" "+title.centerX()+" "+title.centerY()+" 800");
                if(!waitFor("六模式临时对比"))throw new AssertionError("Long title did not open debug");
                click("六模式临时对比");
                long deadline=SystemClock.uptimeMillis()+120000;
                while(SystemClock.uptimeMillis()<deadline) {
                    AccessibilityNodeInfo ready=find("切换对比模式");
                    // The disabled chip's text node can report enabled. Wait for the actual image viewport.
                    if(ready!=null && comparisonTransform(requireRoot())!=null)break;
                    SystemClock.sleep(200);
                }
                if(find("切换对比模式")==null || comparisonTransform(requireRoot())==null) {
                    saveComparisonScreenshot("comparison-not-ready");
                    throw new AssertionError("Comparison never became ready: "+visibleText(requireRoot()));
                }
                getUiAutomation().waitForIdle(100,3000);SystemClock.sleep(200);
                Rect bounds=new Rect();requireRoot().getBoundsInScreen(bounds);
                long plain=imageHash();pinch(bounds,false);drag(bounds,true);SystemClock.sleep(300);
                long zoomed=imageHash();if(plain==zoomed)throw new AssertionError("Comparison pinch/pan did not change view");
                String transform=comparisonTransform(requireRoot());
                if(transform==null || Float.parseFloat(transform.split(" ")[1])<=1.2f)throw new AssertionError("Comparison did not actually zoom: "+transform+" bounds="+bounds);
                saveComparisonScreenshot("comparison-zoomed-v10");
                java.util.Set<Long> frames=new java.util.HashSet<>();frames.add(zoomed);
                for(int step=1;step<=6;step++) {
                    click("切换对比模式");SystemClock.sleep(100);
                    frames.add(imageHash());
                    if(!transform.equals(comparisonTransform(requireRoot())))throw new AssertionError("Cycle changed exact transform: "+step);
                }
                if(imageHash()!=zoomed)throw new AssertionError("Six clicks did not restore exact V10 frame");
                if(frames.size()<2)throw new AssertionError("Mode button did not change image");
                if(find("按默认参数生成")!=null || find("1280px")!=null)throw new AssertionError("Obsolete comparison controls remain");
                pinch(bounds,true);drag(bounds,false);
                getUiAutomation().setRotation(UiAutomation.ROTATION_FREEZE_90);SystemClock.sleep(700);
                if(find("切换对比模式")==null)throw new AssertionError("Landscape hid mode controls");
                saveComparisonScreenshot("comparison-landscape");
                getUiAutomation().setRotation(UiAutomation.ROTATION_FREEZE_0);SystemClock.sleep(700);
                long beforeHome=imageHash();shell("input keyevent KEYCODE_HOME");SystemClock.sleep(700);
                shell("am start -W -n com.local.parallelvrgallerypro/.MainActivity");SystemClock.sleep(700);
                if(imageHash()!=beforeHome)throw new AssertionError("Background return reset comparison view");
                saveComparisonScreenshot("comparison-returned");
                click("返回调试");
                result.putString("result","PASS: long title single-row + long-press debug; real six-mode UI; single-button six-mode cycle with exact zoom/pan and return-frame hash; cross-center/held gestures; rotation; Home return; no comparison catalog writes");
                finish(-1,result);return;
            }
            if (kind.startsWith("video")) {
                if (!kind.equals("videoResume")) {
                    if(find("继续生成") != null) click("继续生成");
                    else click("加入 VR 队列");
                    click("暂停生成"); SystemClock.sleep(500); click("继续生成");
                } else if (find("继续生成") != null) click("继续生成");
                if (kind.equals("videoStart")) {
                    result.putString("result", "PASS: synthetic video queued, paused, resumed; ready for external force-stop");
                    finish(-1, result); return;
                }
                if (kind.equals("videoFull")) {
                    shell("input keyevent KEYCODE_HOME");
                    SystemClock.sleep(600);
                    shell("am start -W -n com.local.parallelvrgallerypro/.MainActivity");
                    SystemClock.sleep(400);
                }
                long id = sampleId();
                long deadline = SystemClock.uptimeMillis() + 90000;
                while (SystemClock.uptimeMillis() < deadline && !videoComplete(id)) SystemClock.sleep(200);
                if (!videoComplete(id)) throw new AssertionError("Video did not finish after resume");
                result.putString("result", "PASS: " + kind + "; completed SBS MP4 and final video metadata found");
                finish(-1, result); return;
            }
            SystemClock.sleep(1000);
            Rect bounds = new Rect(); requireRoot().getBoundsInScreen(bounds);
            long before = imageHash();
            pinch(bounds, false);
            SystemClock.sleep(350);
            long after = imageHash();
            if (before == after) throw new AssertionError("Pinch did not visibly change central content");
            pinch(bounds, true); // cross the eye center line with stable pointer IDs
            drag(bounds, true);
            doubleTap(bounds);
            getUiAutomation().setRotation(UiAutomation.ROTATION_FREEZE_90);
            SystemClock.sleep(600);
            requireRoot().getBoundsInScreen(bounds);
            pinch(bounds, false); doubleTap(bounds);
            for (int i = 0; i < 8; i++) drag(bounds, i % 2 == 0);
            getUiAutomation().setRotation(UiAutomation.ROTATION_FREEZE_0);
            SystemClock.sleep(600);
            if (!"com.local.parallelvrgallerypro".contentEquals(requireRoot().getPackageName())) {
                throw new AssertionError("Viewer lost foreground");
            }
            result.putString("result", "PASS: pinch visibly changes image; cross-center two-pointer drag; held drag; reset; rotation; 8 rapid swipes");
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("error", error.toString()); finish(1, result);
        }
    }
    private long sampleId() {
        String[] columns={MediaStore.Files.FileColumns._ID};
        try(Cursor cursor=getTargetContext().getContentResolver().query(MediaStore.Files.getContentUri("external"),columns,
                MediaStore.Files.FileColumns.DISPLAY_NAME+"=? AND "+MediaStore.Files.FileColumns.RELATIVE_PATH+"=?",
                new String[]{sample,"Pictures/"+album+"/"},null)) {
            if(cursor!=null&&cursor.moveToFirst())return cursor.getLong(0);
        }
        throw new AssertionError("QA sample is not in the explicit QA album");
    }
    private void verifySelectionScope() {
        java.util.Set<String> allowed=new java.util.HashSet<>();
        try(Cursor cursor=getTargetContext().getContentResolver().query(MediaStore.Files.getContentUri("external"),new String[]{"_id"},"relative_path=?",new String[]{"Pictures/"+album+"/"},null)){
            if(cursor!=null)while(cursor.moveToNext())allowed.add(cursor.getString(0));
        }
        File[] sources=new File(getTargetContext().getExternalFilesDir(null),"vr_cache").listFiles();int count=0;
        if(sources!=null)for(File source:sources){File[] versions=source.listFiles();if(versions==null)continue;for(File version:versions){
            if(version.getName().contains("_depthV6_s60_")&&version.getName().contains("_m1536_")&&!version.getName().contains("_du1_")){
                if(!allowed.contains(source.getName().split("_")[0]))throw new AssertionError("Selection version contains non-QA media");count++;
            }
        }}
        if(count!=6)throw new AssertionError("Expected exactly six QA results before selection test");
    }
    private boolean videoComplete(long id) {
        File root=new File(getTargetContext().getExternalFilesDir(null),"video_vr_cache");
        File[] sources=root.listFiles();if(sources==null)return false;
        for(File source:sources)if(source.getName().startsWith("VIDEO_"+id+"_")){
            File[] versions=source.listFiles();if(versions==null)continue;
            for(File version:versions) {
                if(new File(version,"video_params.txt").isFile()&&new File(version,"vr_sbs.mp4").length()>0) {
                    return true;
                }
            }
        }
        return false;
    }
    private AccessibilityNodeInfo requireRoot() {
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        if (root == null) { throw new AssertionError("No active window"); }
        return root;
    }
    private AccessibilityNodeInfo find(String value) {
        if(android.os.Build.VERSION.SDK_INT>=36)getUiAutomation().clearCache();
        return findNode(getUiAutomation().getRootInActiveWindow(), value);
    }
    private boolean waitFor(String value) {
        for(int i=0;i<30;i++){if(find(value)!=null)return true;SystemClock.sleep(100);}return false;
    }
    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo node, String value) {
        if (node == null) return null;
        if (value.contentEquals(node.getText() == null ? "" : node.getText()) || value.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())) return node;
        for (int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo found=findNode(node.getChild(i),value); if(found!=null)return found; }
        return null;
    }
    private void click(String value) {
        AccessibilityNodeInfo node=null;
        for(int i=0;i<40&&node==null;i++){
            if(i==4 && (value.equals("暂停生成") || value.equals("继续生成"))) {
                Rect screen=new Rect();requireRoot().getBoundsInScreen(screen);
                try{shell("input tap "+screen.centerX()+" "+screen.centerY());}catch(java.io.IOException error){throw new RuntimeException(error);}
            }
            // Android 17 can present this developer-only dialog asynchronously after launch.
            if (!value.equals("确定") && find("Android 应用兼容性") != null) {
                AccessibilityNodeInfo confirm=find("确定");
                if(confirm!=null){dismissCompatibility(confirm);SystemClock.sleep(400);}
            }
            AccessibilityNodeInfo active=getUiAutomation().getRootInActiveWindow();
            if(active!=null && "com.local.parallelvrgallerypro".contentEquals(active.getPackageName()==null?"":active.getPackageName())) node=find(value);
            else if(value.equals("确定") && find("Android 应用兼容性")!=null)node=find(value);
            if(node==null)SystemClock.sleep(150);
        }
        if(node==null)throw new AssertionError("Missing target: "+value);
        Rect r=new Rect();node.getBoundsInScreen(r);
        if(value.equals("确定")&&find("Android 应用兼容性")!=null){dismissCompatibility(node);SystemClock.sleep(400);return;}
        // Shell tap is used for navigation; the actual gesture checks below use multi-pointer events.
        if(!"com.local.parallelvrgallerypro".contentEquals(requireRoot().getPackageName()))throw new AssertionError("Navigation left target app");
        try{shell("input tap "+r.centerX()+" "+r.centerY());}catch(java.io.IOException error){throw new RuntimeException(error);}
        SystemClock.sleep(500);
    }
    private void dismissCompatibility(AccessibilityNodeInfo node) {
        Rect bounds=new Rect();node.getBoundsInScreen(bounds);
        try{shell("input tap "+bounds.centerX()+" "+bounds.centerY());}catch(java.io.IOException error){throw new RuntimeException(error);}
    }
    private void tap(float x,float y) {
        long down=SystemClock.uptimeMillis(); inject(down,down,MotionEvent.ACTION_DOWN,new float[]{x},new float[]{y});
        SystemClock.sleep(60);
        inject(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,new float[]{x},new float[]{y});SystemClock.sleep(80);
    }
    private void doubleTap(Rect r) {tap(r.width()*.25f,r.height()*.5f);SystemClock.sleep(100);tap(r.width()*.25f,r.height()*.5f);SystemClock.sleep(200);}
    private void pinch(Rect r, boolean crossCenter) {
        float w=r.width(),h=r.height();long down=SystemClock.uptimeMillis();
        inject(down,down,MotionEvent.ACTION_DOWN,new float[]{.18f*w},new float[]{.5f*h});
        SystemClock.sleep(20);
        inject(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[]{.18f*w,.32f*w},new float[]{.5f*h,.5f*h});
        for(int i=1;i<=24;i++){
            float t=i/24f, move=crossCenter ? .4f*w*t : 0;
            inject(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,new float[]{(.18f-.10f*t)*w+move,(.32f+.10f*t)*w+move},new float[]{.5f*h,.5f*h});SystemClock.sleep(16);
        }
        float move=crossCenter ? .4f*w:0;
        inject(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[]{.08f*w+move,.42f*w+move},new float[]{.5f*h,.5f*h});
        inject(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,new float[]{.08f*w+move},new float[]{.5f*h});
    }
    private void drag(Rect r,boolean forward) {
        float from=r.width()*(forward?.8f:.2f), to=r.width()*(forward?.2f:.8f),y=r.height()*.5f;long down=SystemClock.uptimeMillis();
        inject(down,down,MotionEvent.ACTION_DOWN,new float[]{from},new float[]{y});
        for(int i=1;i<=30;i++){inject(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,new float[]{from+(to-from)*i/30},new float[]{y});SystemClock.sleep(16);}
        SystemClock.sleep(250);
        inject(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,new float[]{to},new float[]{y});SystemClock.sleep(120);
    }
    private void inject(long down,long now,int action,float[] xs,float[] ys) {
        CharSequence foreground=requireRoot().getPackageName();
        if(!"com.local.parallelvrgallerypro".contentEquals(foreground==null?"":foreground) && !compatibilityDialogInput)throw new AssertionError("Left target app; input stopped; foreground="+foreground);
        MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[xs.length];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[xs.length];
        for(int i=0;i<xs.length;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=xs[i];coords[i].y=ys[i];coords[i].pressure=1;coords[i].size=1;}
        MotionEvent event=MotionEvent.obtain(down,now,action,xs.length,props,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        try {sendPointerSync(event);} finally{event.recycle();}
    }
    private void shell(String command) throws java.io.IOException {
        try(java.io.InputStream stream=new android.os.ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(command))) {
            byte[] buffer=new byte[1024];while(stream.read(buffer)!=-1) { /* no private output retained */ }
        }
    }
    private long imageHash() {
        Bitmap image=getUiAutomation().takeScreenshot();if(image==null)throw new AssertionError("Screenshot unavailable");
        long hash=0;for(int y=image.getHeight()/3;y<image.getHeight()*2/3;y+=17)for(int x=0;x<image.getWidth();x+=17)hash=hash*31+image.getPixel(x,y);image.recycle();return hash;
    }
    private void saveComparisonScreenshot(String label) throws java.io.IOException {
        File root=new File(getTargetContext().getExternalFilesDir(null),"PVG_QA_profiles_1500");
        Bitmap image=getUiAutomation().takeScreenshot();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(root,label+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
        finally{image.recycle();}
    }
    private String comparisonTransform(AccessibilityNodeInfo node) {
        node.refresh();
        CharSequence d=node.getContentDescription();if(d!=null&&d.toString().startsWith("对比视口 "))return d.toString();
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null){String found=comparisonTransform(child);if(found!=null)return found;}}
        return null;
    }
    private String visibleText(AccessibilityNodeInfo node) {
        StringBuilder out=new StringBuilder();if(node.getText()!=null)out.append(node.getText()).append(" | ");
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)out.append(visibleText(child));}
        return out.toString();
    }
    private java.util.Map<String,String> qaImageHashes(long id) throws Exception {
        java.util.Map<String,String> result=new java.util.TreeMap<>();
        File cache=new File(getTargetContext().getExternalFilesDir(null),"vr_cache");File[] photos=cache.listFiles();
        if(photos==null)return result;
        for(File photo:photos)if(photo.getName().startsWith(id+"_")) {
            File[] versions=photo.listFiles();if(versions==null)continue;
            for(File version:versions)if(new File(version,"params.json").isFile()&&new File(version,"left.jpg").isFile()&&new File(version,"right.jpg").isFile()) {
                java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
                for(String name:new String[]{"params.json","left.jpg","right.jpg","model_depth.png"}) {
                    File file=new File(version,name);if(!file.isFile())continue;digest.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    try(java.io.FileInputStream in=new java.io.FileInputStream(file)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}
                }
                StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.US,"%02x",b));
                result.put(photo.getName()+"/"+version.getName(),hash.toString());
            }
        }
        return result;
    }
    private void assertQaHistory(java.util.Map<String,String> old,java.util.Map<String,String> current) {
        for(java.util.Map.Entry<String,String> entry:old.entrySet())if(!entry.getValue().equals(current.get(entry.getKey())))throw new AssertionError("Unselected history changed");
    }
    private java.util.Map<String,String> waitQaImageChange(long id,java.util.Map<String,String> before,String retired,boolean additive) throws Exception {
        long deadline=SystemClock.uptimeMillis()+40000;
        while(SystemClock.uptimeMillis()<deadline) {
            java.util.Map<String,String> now=qaImageHashes(id);
            if(additive ? now.size()==before.size()+1 : now.size()==before.size()&&!now.containsKey(retired))return now;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Synthetic explicit request did not complete");
    }
}
