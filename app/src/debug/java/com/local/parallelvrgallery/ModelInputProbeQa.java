package com.local.parallelvrgallerypro;
import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import org.json.*;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

/** Experimental input resizing of the preserved model, strictly isolated from app sessions. */
final class ModelInputProbeQa {
 static void run(Context context)throws Exception {
  File root=new File(context.getExternalFilesDir(null),"PVG_QA_fixed_1501");
  Bitmap original=BitmapFactory.decodeFile(new File(root,"PVG_QA_P3.jpg").getPath());
  if(original==null)throw new AssertionError("Explicit QA portrait required");
  JSONArray report=new JSONArray();
  try(RandomAccessFile file=new RandomAccessFile(new File(context.getExternalFilesDir(null),"models/depth_anything_v2.tflite"),"r")) {
   ByteBuffer model=file.getChannel().map(FileChannel.MapMode.READ_ONLY,0,file.length());
   for(int size:new int[]{518,770,1036}) {
    JSONObject result=new JSONObject().put("requestedInput",size).put("ownerThread",Thread.currentThread().getId());
    GpuDelegate delegate=null;Interpreter interpreter=null;Bitmap input=null;
    try {
     delegate=new GpuDelegate(new GpuDelegate.Options().setPrecisionLossAllowed(false).setInferencePreference(GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED));
     interpreter=new Interpreter(model,new Interpreter.Options().setNumThreads(4).addDelegate(delegate));
     interpreter.resizeInput(0,new int[]{1,size,size,3});interpreter.allocateTensors();
     result.put("allocatedInput",new JSONArray(interpreter.getInputTensor(0).shape()));
     result.put("allocatedOutput",new JSONArray(interpreter.getOutputTensor(0).shape()));
     input=Bitmap.createScaledBitmap(original,size,size,true);
     ByteBuffer in=ByteBuffer.allocateDirect(interpreter.getInputTensor(0).numBytes()).order(ByteOrder.nativeOrder());
     int[] pixels=new int[size*size];input.getPixels(pixels,0,size,0,0,size,size);
     for(int color:pixels){in.putFloat(((color>>16&255)/255f-.485f)/.229f);in.putFloat(((color>>8&255)/255f-.456f)/.224f);in.putFloat(((color&255)/255f-.406f)/.225f);}in.rewind();
     ByteBuffer out=ByteBuffer.allocateDirect(interpreter.getOutputTensor(0).numBytes()).order(ByteOrder.nativeOrder());interpreter.run(in,out);out.rewind();
     float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
     try(DataOutputStream stream=new DataOutputStream(new FileOutputStream(new File(root,"model-input-"+size+".f32-be")))){
      while(out.hasRemaining()){float v=out.getFloat();if(!Float.isFinite(v))throw new AssertionError("Nonfinite depth");min=Math.min(min,v);max=Math.max(max,v);stream.writeFloat(v);}
     }
     if(max-min<.00001f)throw new AssertionError("Flat depth");result.put("inferencePassed",true).put("range",max-min);
    }catch(Throwable error){result.put("inferencePassed",false).put("error",error.toString());}
    finally{if(input!=null&&input!=original)input.recycle();if(interpreter!=null)interpreter.close();if(delegate!=null)delegate.close();}
    report.put(result);
    try(FileOutputStream stream=new FileOutputStream(new File(root,"model-input-report.json"))){stream.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
   }
  }finally{original.recycle();}
 }
}
