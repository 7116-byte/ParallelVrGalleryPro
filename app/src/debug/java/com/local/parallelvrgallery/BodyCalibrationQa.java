package com.local.parallelvrgallerypro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.*;
import java.lang.reflect.Method;
import org.json.*;

/** Isolated real renderer sweep, using already captured QA depth; no new inference or gallery tasks. */
final class BodyCalibrationQa {
    static void run(Context context) throws Exception {
        run(context,false);
    }
    static void run(Context context, boolean reverseOnly) throws Exception {
        File root=new File(context.getExternalFilesDir(null),"PVG_QA_body1502");
        if(!root.isDirectory())throw new AssertionError("QA fixtures missing");
        VrGenerator generator=new VrGenerator(context,new VrCacheManager(context),new ModelManager(context));
        Method prepare=VrGenerator.class.getDeclaredMethod("prepareDepth",float[].class,VrGenerationParams.class,int.class,int.class);prepare.setAccessible(true);
        Method render=VrGenerator.class.getDeclaredMethod("makeParallelStereoPair",Bitmap.class,DepthMap.class,float.class,int.class,DepthEdgeMode.class);render.setAccessible(true);
        JSONArray report=new JSONArray();
        try {
            for(String name:new String[]{"PVG_QA_P1.jpg","PVG_QA_P2.jpg","PVG_QA_P3.jpg"}) {
                Bitmap decoded=BitmapFactory.decodeFile(new File(root,name).getPath());
                Bitmap source=Bitmap.createScaledBitmap(decoded,1280,Math.round(1280f*decoded.getHeight()/decoded.getWidth()),true);
                if(source!=decoded)decoded.recycle();
                float[] raw=new float[518*518];
                try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(new File(root,name+".raw-f32-be"))))){for(int i=0;i<raw.length;i++)raw[i]=in.readFloat();}
                try {
                    try(FileOutputStream out=new FileOutputStream(new File(root,name+".source.png"))){source.compress(Bitmap.CompressFormat.PNG,100,out);}
                    for(DepthEdgeMode mode:DepthEdgeMode.values()) {
                        if(reverseOnly && !mode.isReference())continue;
                        float[] scales=mode==DepthEdgeMode.LEGACY_V6?new float[]{30}:mode==DepthEdgeMode.CENTERED_V7?new float[]{25}:
                            mode.isReference()?new float[]{4.5f}:mode==DepthEdgeMode.COMFY_LEGACY_V10?new float[]{35}:new float[]{6,35};
                        for(float scale:scales) {
                            ModeVisualProfile profile=new ModeVisualProfile(scale,3,10,ModeVisualProfile.Companion.defaults(mode).getInvert(),1280,
                                new DepthUpscaleSettings(false,0,DepthInterpolation.BILINEAR,DepthResizeOrder.SMOOTH_THEN_RESIZE),false,reverseOnly);
                            VrGenerationParams params=profile.applyTo(ModeProfilesQa.base(mode)).toParams();
                            DepthMap depth=(DepthMap)prepare.invoke(generator,raw,params,source.getWidth(),source.getHeight());
                            File depthFile=new File(root,name+"."+mode.name()+".prepared-f32-be");
                            if(!depthFile.exists())try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(depthFile)))) {
                                for(float value:depth.getValues())out.writeFloat(value);
                            }
                            StereoBitmapPair eyes=(StereoBitmapPair)render.invoke(generator,source,depth,scale,10,mode);
                            String stem=name+"."+mode.name()+"."+scale+(reverseOnly?".inverted":"");
                            try(FileOutputStream out=new FileOutputStream(new File(root,stem+".png"))) {
                                (mode.isReference()?eyes.getRight():eyes.getLeft()).compress(Bitmap.CompressFormat.PNG,100,out);
                            } finally {eyes.recycle();}
                            report.put(new JSONObject().put("sample",name).put("mode",mode.name()).put("scale",scale).put("width",source.getWidth()).put("height",source.getHeight()).put("image",stem+".png"));
                        }
                    }
                } finally {source.recycle();}
            }
        } finally {generator.close();}
        try(FileOutputStream out=new FileOutputStream(new File(root,reverseOnly?"direction-report.json":"report.json"))){out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }
}
