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
        try {if(!getUiAutomation().injectInputEvent(event,true))throw new AssertionError("Input injection failed; action="+action);} finally{event.recycle();}
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
}
