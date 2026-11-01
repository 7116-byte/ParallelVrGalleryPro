package com.local.parallelvrgallerypro;

import android.content.Context;
import android.os.SystemClock;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import org.json.*;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

/** Private fixed-graph research. No Activity, preference changes, gallery or queue access. */
final class HighResolutionQa {
    static void runPipeline(Context context) throws Exception {
        File root = new File(context.getExternalFilesDir(null), "PVG_QA_resolution1503");
        VrGenerator generator = new VrGenerator(context, new VrCacheManager(context), new ModelManager(context));
        java.lang.reflect.Method prepare = VrGenerator.class.getDeclaredMethod("prepareDepth", float[].class, VrGenerationParams.class, int.class, int.class);
        java.lang.reflect.Method render = VrGenerator.class.getDeclaredMethod("makeParallelStereoPair", android.graphics.Bitmap.class, DepthMap.class, float.class, int.class, DepthEdgeMode.class);
        prepare.setAccessible(true);render.setAccessible(true);
        JSONArray report = new JSONArray();
        try {
            for (int size : new int[]{518,770}) {
                StringBuilder runtime = new StringBuilder();
                DepthModelSession session = new DepthModelSession(new File(root,"model-"+size+".tflite"),4,true,GpuTestMode.ACCURATE_UNSET,true, value->{runtime.append(value).append('\n');return kotlin.Unit.INSTANCE;});
                try {
                    for (String name : new String[]{"PVG_QA_P1.jpg","PVG_QA_P2.jpg","PVG_QA_P3.jpg","PVG_QA_O1.png"}) {
                        android.graphics.Bitmap decoded = android.graphics.BitmapFactory.decodeFile(new File(root,name).getPath());
                        if(decoded==null)throw new AssertionError("QA input missing");
                        android.graphics.Bitmap source = android.graphics.Bitmap.createScaledBitmap(decoded,1280,Math.round(1280f*decoded.getHeight()/decoded.getWidth()),true);
                        if(source!=decoded)decoded.recycle();
                        try {
                            float[] raw=session.run(source,0,()->kotlin.Unit.INSTANCE);
                            if(raw.length!=size*size)throw new AssertionError("Actual runtime depth shape mismatch");
                            try(DataOutputStream stream=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(root,name+"."+size+".runtime-f32-be"))))){for(float value:raw)stream.writeFloat(value);}
                            for(DepthEdgeMode mode:DepthEdgeMode.values()) {
                                VrGenerationParams params=ModeVisualProfile.Companion.defaults(mode).applyTo(ModeProfilesQa.base(mode)).toParams();
                                DepthMap depth=(DepthMap)prepare.invoke(generator,raw,params,source.getWidth(),source.getHeight());
                                StereoBitmapPair eyes=(StereoBitmapPair)render.invoke(generator,source,depth,params.getDepthScale(),params.getFillRadius(),mode);
                                try(FileOutputStream stream=new FileOutputStream(new File(root,name+"."+size+"."+mode.name()+".png"))) {
                                    (mode.isReference()?eyes.getRight():eyes.getLeft()).compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);
                                }finally{eyes.recycle();}
                                report.put(new JSONObject().put("sample",name).put("modelInput",size).put("depthPixels",raw.length).put("mode",mode.name()).put("projectionWidth",depth.getWidth()).put("projectionHeight",depth.getHeight()));
                            }
                        }finally{source.recycle();}
                    }
                }finally{session.close();}
                if(!runtime.toString().contains("delegateActive=true") || !runtime.toString().contains("input="+size+"x"+size))throw new AssertionError("Runtime GPU/input evidence missing");
                try(FileOutputStream stream=new FileOutputStream(new File(root,"runtime-"+size+".log"))){stream.write(runtime.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            }
        }finally{generator.close();}
        try(FileOutputStream stream=new FileOutputStream(new File(root,"pipeline-report.json"))){stream.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }

    static void run(Context context) throws Exception {
        File root = new File(context.getExternalFilesDir(null), "PVG_QA_resolution1503");
        if (!root.isDirectory()) throw new AssertionError("Explicit QA inputs required");
        JSONArray results = new JSONArray();
        for (int size : new int[]{518, 770}) {
            JSONObject result = new JSONObject().put("size", size).put("ownerThread", Thread.currentThread().getId());
            GpuDelegate delegate = null;
            Interpreter interpreter = null;
            long started = SystemClock.elapsedRealtime();
            try (RandomAccessFile file = new RandomAccessFile(new File(root, "model-" + size + ".tflite"), "r")) {
                ByteBuffer model = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, file.length());
                delegate = new GpuDelegate(new GpuDelegate.Options().setPrecisionLossAllowed(false)
                        .setInferencePreference(GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED));
                interpreter = new Interpreter(model, new Interpreter.Options().setNumThreads(4).addDelegate(delegate));
                interpreter.allocateTensors();
                result.put("input", new JSONArray(interpreter.getInputTensor(0).shape()));
                result.put("output", new JSONArray(interpreter.getOutputTensor(0).shape()));
                if (interpreter.getInputTensor(0).numElements() != size * size * 3 ||
                        interpreter.getOutputTensor(0).numElements() != size * size) throw new AssertionError("Unexpected tensor shape");
                result.put("prepareMs", SystemClock.elapsedRealtime() - started);
                JSONArray images = new JSONArray();
                for (String name : new String[]{"PVG_QA_P1", "PVG_QA_P2", "PVG_QA_P3", "PVG_QA_O1"}) {
                    ByteBuffer input = ByteBuffer.allocateDirect(size * size * 3 * 4).order(ByteOrder.LITTLE_ENDIAN);
                    try (FileInputStream stream = new FileInputStream(new File(root, name + "." + size + ".input-f32-le"))) {
                        FileChannel channel = stream.getChannel();
                        while (input.hasRemaining()) if (channel.read(input) < 0) throw new EOFException();
                    }
                    input.rewind();
                    ByteBuffer output = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder());
                    long time = SystemClock.elapsedRealtime();
                    interpreter.run(input, output);output.rewind();
                    long elapsed = SystemClock.elapsedRealtime() - time;
                    float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
                    try (DataOutputStream stream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(root, name + "." + size + ".gpu-f32-be"))))) {
                        while (output.hasRemaining()) {
                            float value = output.getFloat();
                            if (!Float.isFinite(value)) throw new AssertionError("Nonfinite depth");
                            min = Math.min(min, value);max = Math.max(max, value);stream.writeFloat(value);
                        }
                    }
                    if (max - min < .00001f) throw new AssertionError("Flat depth");
                    images.put(new JSONObject().put("sample", name).put("inferenceMs", elapsed).put("min", min).put("max", max));
                }
                result.put("images", images).put("passed", true);
            } catch (Throwable error) { result.put("passed", false).put("error", error.toString()); }
            finally {
                if (interpreter != null) interpreter.close();
                if (delegate != null) delegate.close();
            }
            results.put(result);
            try (FileOutputStream stream = new FileOutputStream(new File(root, "gpu-report.json"))) {
                stream.write(results.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }
}
