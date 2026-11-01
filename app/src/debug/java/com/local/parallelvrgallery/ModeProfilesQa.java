package com.local.parallelvrgallerypro;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Opt-in, private QA fixtures only. Does not create an Activity, VM, queue or gallery entry. */
final class ModeProfilesQa {
    static AppSettings base(DepthEdgeMode mode) {
        return new AppSettings(AppLanguage.ZH, "depth_anything_v2_small_tflite", "depth_anything_v2_small_tflite",
            false, 2, 50f, 3, 10, true, 6000, 518, 1, 4, true, GpuTestMode.ACCURATE_UNSET, true,
            4, true, 2, mode, new DepthUpscaleSettings(false, 0, DepthInterpolation.BILINEAR, DepthResizeOrder.SMOOTH_THEN_RESIZE), false, false,"PNG");
    }

    static void run(Context context) throws Exception {
        File root = new File(context.getExternalFilesDir(null), "PVG_QA_profiles_1500");
        if (!root.isDirectory()) throw new AssertionError("Explicit QA fixture directory required");
        JSONObject report = new JSONObject();
        Context scoped = new ContextWrapper(context) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("PVG_QA_profiles_1500_" + name, mode);
            }
        };
        SharedPreferences prefs = scoped.getSharedPreferences("settings", 0);
        prefs.edit().clear().putString("edgeMode", "LEGACY_V6").putFloat("depthScale", 33f)
            .putBoolean("migratedInvertDefaultOffV5", true).putBoolean("invertDepth", true).commit();
        SettingsStore store = new SettingsStore(scoped);
        AppSettings migrated = store.load();
        if (migrated.getDepthScale()!=33f || !migrated.getInvertDepth()) throw new AssertionError("Migration changed existing visual parameters");
        store.saveDefault(migrated);
        if(new SettingsStore(scoped).defaultProfile(DepthEdgeMode.LEGACY_V6).getScale()!=33f)throw new AssertionError("Saved default lost after cold restore");
        if(store.defaultProfile(DepthEdgeMode.COMFY_LEGACY_V10).getScale()!=50f)throw new AssertionError("Saving one default changed another mode");
        store.clearDefault(DepthEdgeMode.LEGACY_V6);
        if(!store.defaultProfile(DepthEdgeMode.LEGACY_V6).equals(ModeVisualProfile.Companion.defaults(DepthEdgeMode.LEGACY_V6)))throw new AssertionError("Built-in default did not restore");
        report.put("saveDefaultScopeColdRestoreAndFactoryReset",true);

        for (DepthEdgeMode mode : DepthEdgeMode.values()) store.save(ModeVisualProfile.Companion.defaults(mode).applyTo(base(mode)));
        for (DepthEdgeMode mode : DepthEdgeMode.values()) {
            AppSettings restored = store.profile(mode).applyTo(base(mode));
            store.save(restored);
            AppSettings cold = new SettingsStore(scoped).load();
            if (cold.getEdgeMode()!=mode || !ModeVisualProfile.Companion.capture(cold).equals(ModeVisualProfile.Companion.defaults(mode)))
                throw new AssertionError("Cold profile mismatch: " + mode);
            if (!cold.getUseGpu() || !cold.getForceGpuNoFallback()) throw new AssertionError("GPU preference lost");
        }
        report.put("profileMigrationAndSixColdRestores", true);
        prefs.edit().clear().commit();

        Map<DepthEdgeMode,ModeVisualProfile> profiles = new LinkedHashMap<>();
        for (DepthEdgeMode mode : DepthEdgeMode.values()) profiles.put(mode, ModeVisualProfile.Companion.defaults(mode));
        VrGenerator generator = new VrGenerator(context, new VrCacheManager(context), new ModelManager(context));
        JSONArray cases = new JSONArray();
        try {
            for (String filename : new String[]{"PVG_QA_P1.jpg", "PVG_QA_P2.jpg", "PVG_QA_P3.jpg", "PVG_QA_O1.png"}) {
                File input = new File(root, filename);
                if (!input.isFile()) throw new AssertionError("Missing authorized input " + filename);
                Bitmap bounds = android.graphics.BitmapFactory.decodeFile(input.getPath());
                int w=bounds.getWidth(), h=bounds.getHeight(); bounds.recycle();
                PhotoItem photo = new PhotoItem(1L, Uri.fromFile(input), filename, w, h, input.length(), input.lastModified()/1000,
                    MediaKind.IMAGE, "PVG_QA_profiles_1500", "PVG_QA_profiles_1500", "", null, false);
                for (int edge : (filename.equals("PVG_QA_P3.jpg") || filename.equals("PVG_QA_P1.jpg")) ? new int[]{960,1280,1920} : new int[]{1280}) {
                    String prefix = filename + "." + edge;
                    List<ModeComparisonImage> images = generator.generateComparison(photo, base(DepthEdgeMode.COMFY_LEGACY_V10), profiles,
                        edge, ()->true, (progress,message)->kotlin.Unit.INSTANCE);
                    if (images.size()!=6) throw new AssertionError("Incomplete comparison");
                    Bitmap original = images.get(0).getRight();
                    int actualW=original.getWidth(), actualH=original.getHeight();
                    Set<Bitmap> owned = Collections.newSetFromMap(new IdentityHashMap<>());
                    try {
                        StringBuilder runtime = new StringBuilder();
                        DepthModelSession session = new DepthModelSession(new File(context.getExternalFilesDir(null),"models/depth_anything_v2.tflite"),
                            4,true,GpuTestMode.ACCURATE_UNSET,true, value->{runtime.append(value).append('\n');return kotlin.Unit.INSTANCE;});
                        float[] raw;
                        try { raw=session.run(original,0,()->kotlin.Unit.INSTANCE); }
                        finally {session.close();}
                        if (!runtime.toString().contains("delegateActive")) throw new AssertionError("GPU ownership/activation evidence missing");
                        JSONArray modes = new JSONArray();
                        java.lang.reflect.Method prepare = VrGenerator.class.getDeclaredMethod("prepareDepth", float[].class, VrGenerationParams.class, int.class,int.class);
                        prepare.setAccessible(true);
                        for (ModeComparisonImage image : images) {
                            owned.add(image.getLeft()); owned.add(image.getRight());owned.add(image.getModelDepth());owned.add(image.getProjectionDepth());
                            if(image.getLeft().getWidth()!=actualW || image.getRight().getHeight()!=actualH) throw new AssertionError("Mode changed geometry");
                            try(FileOutputStream out=new FileOutputStream(new File(root,prefix+"."+image.getMode().name()+".left.png"))) {
                                if(!image.getLeft().compress(Bitmap.CompressFormat.PNG,100,out))throw new AssertionError("PNG write failed");
                            }
                            try(FileOutputStream out=new FileOutputStream(new File(root,prefix+"."+image.getMode().name()+".model-depth.png"))) {image.getModelDepth().compress(Bitmap.CompressFormat.PNG,100,out);}
                            try(FileOutputStream out=new FileOutputStream(new File(root,prefix+"."+image.getMode().name()+".projection-depth.png"))) {image.getProjectionDepth().compress(Bitmap.CompressFormat.PNG,100,out);}
                            Object map=prepare.invoke(generator,raw,image.getParams(),actualW,actualH);
                            float[] values=(float[])map.getClass().getMethod("getValues").invoke(map);
                            float[] sorted=values.clone();Arrays.sort(sorted);
                            float span=sorted[(int)((sorted.length-1)*.9)]-sorted[(int)((sorted.length-1)*.1)];
                            float unit=image.getMode().isReference()?actualW*image.getActualScale()/500f:255f*image.getActualScale()/actualW;
                            modes.put(new JSONObject().put("mode",image.getMode().name()).put("scale",image.getParams().getDepthScale())
                                .put("effectiveScale",image.getActualScale()).put("invert",image.getParams().getInvertDepth())
                                .put("renderMs",image.getElapsedMs()).put("floatDepthP90P10DisparitySpanPx",span*unit));
                            if(image.getMode().isReference()) try(FileOutputStream out=new FileOutputStream(new File(root,prefix+"."+image.getMode().name()+".right.png"))) {
                                image.getRight().compress(Bitmap.CompressFormat.PNG,100,out);
                            }
                        }
                        try(FileOutputStream out=new FileOutputStream(new File(root,prefix+".original.png"))) {original.compress(Bitmap.CompressFormat.PNG,100,out);}
                        cases.put(new JSONObject().put("input",filename).put("requestedLongEdge",edge).put("width",actualW).put("height",actualH)
                            .put("forcedGpu",true).put("runtime",runtime.toString()).put("modes",modes));
                    } finally {for(Bitmap bitmap:owned)bitmap.recycle();}
                }
            }
        } finally {generator.close();}
        report.put("cases",cases);
        report.put("catalogWrites",0).put("queueWrites",0).put("mediaStoreWrites",0);
        try(FileOutputStream out=new FileOutputStream(new File(root,"report.json"))) {out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}
    }
}
