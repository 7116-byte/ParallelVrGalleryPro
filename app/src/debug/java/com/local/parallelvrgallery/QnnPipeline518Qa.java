package com.local.parallelvrgallerypro;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.system.Os;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import kotlin.Unit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Compares real first-generation entry points with a fixed, consented synthetic fixture. */
final class QnnPipeline518Qa {
    private static final String SCOPE = "PVG_QA_qnn_pipeline_518";
    private static final String QA_PACKAGE = "com.local.parallelvrgallerypro.benchmark";
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int FRAME_COUNT = 48;
    private static final String PNG_HASH = "3d9d42f58dfb6ea73eba9dcd0e8128d4bb769fe6e63d92978bb15b47380aa08d";
    // Same 48 AVC frames; AAC priming starts at zero so Android's segment muxer can copy it.
    private static final String VIDEO_HASH = "eb76e8c652f521e912963f1c9ba2781f5d47e70e5db92ac0c90fd74f66bf6054";
    private static final String QNN_FILE_NAME = "dav2-gpu-fp16.onnx";
    private static final String QNN_ASSET = "models/" + QNN_FILE_NAME;
    private static final String QNN_SHA256 = "bb60daa7ee9384e1154eebfc32412bc53acc08f74afe28a28e1f15365ea87f1b";

    private enum Backend {
        TFLITE("tflite", 518, "depth_anything_v2_small_tflite", "depth_anything_v2.tflite",
            "b407f34f61750f31441e6f858a4bc48d8572f9ee5399ffd015cee5fa1767083f", false, true, true),
        QNN("qnn", 518, true, true),
        QNN_770("qnn770", 770, false, false),
        QNN_1036("qnn1036", 1036, false, true);

        final String label, modelId, fileName, sha256;
        final int size;
        final boolean bundled, cold, video;

        Backend(String label, int size, boolean cold, boolean video) {
            this(label, size, "depth_anything_v2_small_qnn_fp16_" + size + "_v1", QNN_FILE_NAME,
                QNN_SHA256, true, cold, video);
        }

        Backend(String label, int size, String modelId, String fileName, String sha256,
                boolean bundled, boolean cold, boolean video) {
            this.label = label;
            this.size = size;
            this.modelId = modelId;
            this.fileName = fileName;
            this.sha256 = sha256;
            this.bundled = bundled;
            this.cold = cold;
            this.video = video;
        }

        static Backend fromLabel(String label) {
            for (Backend backend : values()) if (backend.label.equals(label)) return backend;
            throw new AssertionError("Choose tflite, qnn, qnn770 or qnn1036 for QA");
        }
    }

    static void run(Context context, String label) throws Exception {
        require(QA_PACKAGE.equals(context.getPackageName()), "Use the isolated Benchmark app");
        File scope = new File(context.getExternalFilesDir(null), SCOPE);
        require(new File(scope, "AUTHORIZED").isFile(), "Missing explicit QA marker");
        if ("export".equals(label)) {
            for (Backend backend : Backend.values()) {
                File existing = new File(scope, backend.label);
                if (new File(existing, "pipeline.json").isFile()) exposeEvidence(existing);
            }
            exposeDefaults(new File(scope, "qnnDefault"));
            return;
        }
        if ("qnnDefault".equals(label)) {
            measureDefaults(context, scope);
            return;
        }
        Backend backend = Backend.fromLabel(label);
        File png = fixture(scope, "source.png", PNG_HASH);
        File video = fixture(scope, "source.mp4", VIDEO_HASH);
        File sourceModel = backend.bundled ? null : fixture(scope, "model-518.tflite", backend.sha256);
        if (backend.bundled) verifyBundledModel(context, backend.sha256);
        EncodedTrack sourceVideo = readTrack(video, "video/", false);
        EncodedTrack sourceAudio = readTrack(video, "audio/", true);
        require(sourceVideo.width == WIDTH && sourceVideo.height == HEIGHT
                && sourceVideo.ptsUs.size() == FRAME_COUNT && sourceVideo.ptsUs.get(0) == 0,
            "Unexpected authorized video geometry or timeline");
        require(!sourceAudio.ptsUs.isEmpty(), "QA fixture needs its original AAC track");
        BitmapFactory.Options pngBounds = new BitmapFactory.Options();
        pngBounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(png.getPath(), pngBounds);
        require(pngBounds.outWidth == WIDTH && pngBounds.outHeight == HEIGHT,
            "PNG does not match the source frame dimensions");

        File root = new File(scope, backend.label);
        require(root.mkdir(), "Choose a fresh output directory. Preserve prior evidence");
        JSONArray runs = new JSONArray();
        JSONObject report = new JSONObject().put("schema", 1).put("label", backend.label)
            .put("modelId", backend.modelId).put("modelSha256", backend.sha256)
            .put("inferenceSize", backend.size).put("forceGpuNoFallback", true)
            .put("modelSource", backend.bundled ? "apk_asset" : "qa_external_fixture")
            .put("sourcePngSha256", PNG_HASH).put("sourceVideoSha256", VIDEO_HASH)
            .put("sourceVideo", sourceVideo.json()).put("sourceAudio", sourceAudio.json())
            .put("runs", runs);

        for (boolean isVideo : new boolean[]{false, true}) {
            if (isVideo && !backend.video) continue;
            for (boolean warm : new boolean[]{false, true}) {
                if (!warm && !backend.cold) continue;
                JSONObject measurement = measure(context, root, backend, sourceModel,
                    isVideo ? video : png, isVideo, warm, sourceVideo, sourceAudio);
                runs.put(measurement);
                PerformancePipelineQa.writeReport(new File(root, "pipeline.json"), report);
            }
        }
        exposeEvidence(root);
    }

    private static void exposeEvidence(File root) throws Exception {
        // These are generated copies of the pinned synthetic fixture in the isolated package.
        Os.chmod(root.getPath(), 0755);
        Os.chmod(new File(root, "pipeline.json").getPath(), 0644);
        for (String name : new String[]{"image-cold", "image-warm", "video-cold", "video-warm"}) {
            File pass = new File(root, name);
            if (!pass.isDirectory()) continue;
            Os.chmod(pass.getPath(), 0755);
            String[] outputs = name.startsWith("image")
                ? new String[]{"left.jpg", "right.jpg", "depth.png", "model_depth.png"}
                : new String[]{"output.mp4", "job.log", "video_params.txt"};
            for (String output : outputs) {
                File file = new File(pass, output);
                if (file.isFile()) Os.chmod(file.getPath(), 0644);
            }
        }
    }

    private static JSONObject measure(Context context, File root, Backend backend, File model,
            File input, boolean isVideo, boolean warm, EncodedTrack sourceVideo,
            EncodedTrack sourceAudio) throws Exception {
        String kind = isVideo ? "video" : "image";
        String pass = warm ? "warm" : "cold";
        File run = new File(root, kind + "-" + pass);
        require(run.mkdir(), "Preserve prior pass evidence");
        File models = new File(run, "models");
        if (model != null) {
            require(models.mkdir(), "Could not create isolated model directory");
            Files.copy(model.toPath(), new File(models, backend.fileName).toPath());
        }
        Context scoped = new ContextWrapper(context) {
            @Override public File getExternalFilesDir(String type) { return run; }
            @Override public File getFilesDir() { return run; }
        };

        VrGenerationParams params = new VrGenerationParams(backend.modelId, "SBS_PARALLEL",
            50f, 3, 10, true, WIDTH, backend.bundled ? 1 : 4, true,
            backend.bundled ? GpuTestMode.AUTO : GpuTestMode.ACCURATE_OPENCL, true,
            "FOREGROUND_FILL", 94, DepthEdgeMode.COMFY_LEGACY_V10,
            new DepthUpscaleSettings(false, 0, DepthInterpolation.BILINEAR,
                DepthResizeOrder.SMOOTH_THEN_RESIZE));
        PhotoItem item = new PhotoItem(isVideo ? -51802L : -51801L, Uri.fromFile(input),
            input.getName(), WIDTH, HEIGHT, input.length(), input.lastModified() / 1000,
            isVideo ? MediaKind.VIDEO : MediaKind.IMAGE, SCOPE, SCOPE, "", null, false);
        VrCacheManager imageCache = new VrCacheManager(scoped);
        StringBuffer runtime = new StringBuffer();
        JSONArray inference = new JSONArray();
        JSONObject result = new JSONObject().put("kind", kind).put("pass", pass)
            .put("sample", input.getName()).put("modelId", backend.modelId)
            .put("preparedOutsideTiming", warm).put("inference", inference);
        try (VrGenerator generator = new VrGenerator(scoped, imageCache, new ModelManager(scoped))) {
            // A new session pool and new on-disk cache for each pass. Preparation runs no inference.
            InferenceProfiling.INSTANCE.setListener((size, resize, pack, invoke, read) -> {
                require(size == backend.size && invoke > 0, "Unexpected actual model inference dimensions");
                synchronized (inference) {
                    try {
                        inference.put(new JSONObject().put("size", size)
                            .put("resizeMs", resize / 1e6).put("packMs", pack / 1e6)
                            .put("invokeMs", invoke / 1e6).put("readMs", read / 1e6));
                    } catch (Exception error) {
                        throw new IllegalStateException("Could not record inference", error);
                    }
                }
                return Unit.INSTANCE;
            });
            try {
                double prepareMs = 0;
                if (warm) {
                    long start = System.nanoTime();
                    generator.prepareImageSession(params, line -> append(runtime, line));
                    prepareMs = elapsed(start);
                    require(inference.length() == 0, "Session preparation performed inference");
                    require(!runtime.toString().contains("tflite forced gpu mode="),
                        "Session preparation ran TFLite reference inference");
                    if (backend.bundled) assertBackend(backend, runtime.toString());
                    result.put("inferenceCountDuringPrepare", inference.length());
                }
                result.put("prepareMs", prepareMs);
                if (isVideo) {
                    measureVideo(scoped, run, generator, item, params, runtime, result,
                        sourceVideo, sourceAudio);
                } else {
                    measureImage(run, generator, imageCache, item, params, runtime, result, backend);
                }
                int expected = isVideo ? FRAME_COUNT : 1;
                require(inference.length() == expected, "Expected " + expected
                    + " fresh inferences. Observed " + inference.length());
                String logs = runtime.toString();
                require(logs.contains("depth session acquired reused=" + warm),
                    "Session reuse did not match the " + pass + " pass");
                assertBackend(backend, logs);
                if (backend.bundled) {
                    String[] files = models.list();
                    require(files == null || files.length == 0, "Bundled model was copied or downloaded");
                    result.put("externalModelFiles", 0);
                }
                result.put("inferenceCount", inference.length()).put("runtime", logs);
            } finally {
                InferenceProfiling.INSTANCE.setListener(null);
            }
        }
        return result;
    }

    private static void measureImage(File run, VrGenerator generator, VrCacheManager cache,
            PhotoItem item, VrGenerationParams generation, StringBuffer runtime, JSONObject result,
            Backend backend) throws Exception {
        long start = System.nanoTime();
        VrCacheEntry entry = generator.generate(item, generation, progress -> Unit.INSTANCE, 0,
            () -> true, candidate -> cache.publish(item, candidate, Collections.emptyList()),
            progress -> Unit.INSTANCE);
        result.put("wallMs", elapsed(start));
        require(entry.getWidth() == WIDTH * 2 && entry.getHeight() == HEIGHT, "Image output geometry changed");
        JSONObject params = new JSONObject(readText(new File(entry.getParamsPath())));
        require(backend.modelId.equals(params.getString("depthModel"))
                && backend.sha256.equalsIgnoreCase(params.getString("modelSha256"))
                && params.getBoolean("useGpu") && params.getBoolean("forceGpuNoFallback"),
            "Image result did not record the expected forced GPU model");
        copy(new File(entry.getLeftPath()), new File(run, "left.jpg"));
        copy(new File(entry.getRightPath()), new File(run, "right.jpg"));
        copy(new File(entry.getDepthPath()), new File(run, "depth.png"));
        File depth = new File(new File(entry.getParamsPath()).getParentFile(), "model_depth.png");
        copy(depth, new File(run, "model_depth.png"));
        require(nonconstantDepth(depth, backend.size), "Image model-depth diagnostic is flat");
        append(runtime, readText(new File(entry.getLogPath())));
        result.put("params", params).put("cacheHit", false);
    }

    private static void measureVideo(Context scoped, File run, VrGenerator generator, PhotoItem item,
            VrGenerationParams generation, StringBuffer runtime, JSONObject result,
            EncodedTrack sourceVideo, EncodedTrack sourceAudio) throws Exception {
        JSONArray frames = new JSONArray();
        List<Long> noncacheThroughputs = new ArrayList<>();
        VideoVrGenerator video = new VideoVrGenerator(scoped, new VideoVrCacheManager(scoped), generator);
        long start = System.nanoTime();
        VideoCacheEntry entry = video.generate(item, new VideoGenerationParams(generation, generation.getModelThreads(), true, 1),
            progress -> Unit.INSTANCE, line -> append(runtime, line), () -> Unit.INSTANCE,
            (progress, index, total, fps, metrics, timing, stats) -> {
                require(index == frames.length() + 1 && total == FRAME_COUNT, "Invalid frame sequence");
                require(!metrics.getCacheHit() && stats.getCacheHits() == 0, "Fresh pass used a frame cache");
                if (index > 1) noncacheThroughputs.add(metrics.getThroughputMs());
                try {
                    JSONObject frame = new JSONObject().put("frame", index).put("total", total)
                        .put("fps", fps).put("cacheHit", false).put("throughputMs", metrics.getThroughputMs())
                        .put("latencyMs", metrics.getLatencyMs()).put("decodeMs", timing.getDecodeMs())
                        .put("depthMs", timing.getDepthMs()).put("depthPostMs", timing.getDepthPostMs())
                        .put("sbsMs", timing.getSbsMs()).put("encodeMs", timing.getEncodeMs())
                        .put("lastCacheWriteMs", stats.getLastCacheWriteMs())
                        .put("otherLatencyMs", metrics.getQueueWaitMs()).put("cacheLookupMs", timing.getCacheLookupMs());
                    VideoFrameReadTiming read = timing.getFrameRead();
                    if (read != null) {
                        frame.put("decodeBackend", read.getBackend().name()).put("decoderMs", read.getDecoderMs())
                            .put("decodeConversionMs", read.getConversionMs()).put("decodeProbeMs", read.getProbeMs());
                    }
                    VideoDepthGeometry geometry = timing.getDepthGeometry();
                    if (geometry != null) {
                        frame.put("preparedDepthWidth", geometry.getPreparedWidth())
                            .put("preparedDepthHeight", geometry.getPreparedHeight())
                            .put("workingLongEdge", geometry.getWorkingLongEdge());
                    }
                    frames.put(frame);
                } catch (Exception error) {
                    throw new IllegalStateException("Could not record frame evidence", error);
                }
                return Unit.INSTANCE;
            });
        double wall = elapsed(start);
        require(frames.length() == FRAME_COUNT, "Missing fresh video frame progress");
        File mp4 = new File(entry.getOutputPath());
        EncodedTrack generatedVideo = readTrack(mp4, "video/", false);
        EncodedTrack generatedAudio = readTrack(mp4, "audio/", true);
        require(generatedVideo.width == WIDTH * 2 && generatedVideo.height == HEIGHT,
            "Video output is not one full-resolution SBS stream");
        require(sourceVideo.ptsUs.equals(generatedVideo.ptsUs), "Video PTS changed");
        require(sourceAudio.mime.equals(generatedAudio.mime)
                && sourceAudio.sampleSizes.equals(generatedAudio.sampleSizes)
                && sourceAudio.payloadSha256.equals(generatedAudio.payloadSha256), "Audio was not copied exactly");
        File parameters = new File(mp4.getParentFile(), "video_params.txt");
        String recorded = readText(parameters);
        require(recorded.contains("depthModel=" + generation.getDepthModel() + "\n")
                && recorded.contains("useGpu=true\n") && recorded.contains("forceGpuNoFallback=true\n"),
            "Video metadata did not record the selected forced GPU model");
        copy(mp4, new File(run, "output.mp4"));
        copy(new File(entry.getLogPath()), new File(run, "job.log"));
        copy(parameters, new File(run, "video_params.txt"));
        Collections.sort(noncacheThroughputs);
        int middle = noncacheThroughputs.size() / 2;
        double median = noncacheThroughputs.size() % 2 == 1
            ? noncacheThroughputs.get(middle)
            : (noncacheThroughputs.get(middle - 1) + noncacheThroughputs.get(middle)) / 2.0;
        result.put("wallMs", wall).put("frames", frames).put("noncacheFrames", frames.length())
            .put("medianNoncacheThroughputMs", median)
            .put("outputVideo", generatedVideo.json()).put("outputAudio", generatedAudio.json())
            .put("videoPtsIdentical", true).put("audioIdentical", true)
            .put("audioPtsIdentical", sourceAudio.ptsUs.equals(generatedAudio.ptsUs))
            .put("params", recorded).put("output", "output.mp4");
    }

    private static void measureDefaults(Context context, File scope) throws Exception {
        File root = new File(scope, "qnnDefault");
        require(root.mkdir(), "Choose a fresh default model QA output directory");
        verifyBundledModel(context, QNN_SHA256);
        SharedPreferences testPrefs = context.getSharedPreferences(SCOPE + "_" + System.nanoTime(), Context.MODE_PRIVATE);
        require(testPrefs.getAll().isEmpty(), "Default model QA preferences are not fresh");
        Context scoped = new ContextWrapper(context) {
            @Override public File getExternalFilesDir(String type) { return root; }
            @Override public File getFilesDir() { return root; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) { return testPrefs; }
        };
        SettingsStore store = new SettingsStore(scoped);
        AppSettings defaults = store.load();
        VrGenerationParams image = defaults.toParams();
        VideoGenerationParams video = defaults.toVideoParams();
        require(Backend.QNN.modelId.equals(image.getDepthModel())
                && Backend.QNN.modelId.equals(video.getVr().getDepthModel()) && defaults.getDepthResolution() == 518,
            "Fresh supported-device settings did not select bundled QNN 518");
        require(image.getUseGpu() && image.getForceGpuNoFallback() && video.getUseGpu()
                && video.getVr().getForceGpuNoFallback() && video.getDepthWorkers() == 1,
            "Fresh QNN settings did not retain forced GPU and one video depth worker");

        StringBuffer runtime = new StringBuffer();
        AtomicInteger inferenceCount = new AtomicInteger();
        long start = System.nanoTime();
        try (VrGenerator generator = new VrGenerator(scoped, new VrCacheManager(scoped), new ModelManager(scoped))) {
            InferenceProfiling.INSTANCE.setListener((size, resize, pack, invoke, read) -> {
                inferenceCount.incrementAndGet();
                return Unit.INSTANCE;
            });
            try {
                generator.prepareImageSession(image, line -> append(runtime, line));
            } finally {
                InferenceProfiling.INSTANCE.setListener(null);
            }
        }
        double sessionMs = elapsed(start);
        require(inferenceCount.get() == 0, "Default model preparation invoked inference");
        assertBackend(Backend.QNN, runtime.toString());
        String[] files = new File(root, "models").list();
        require(files == null || files.length == 0, "Default model preparation wrote external model files");

        require(testPrefs.edit().putString("modelId", Backend.TFLITE.modelId).commit(), "Could not set QA legacy preference");
        AppSettings legacy = store.load();
        require(Backend.TFLITE.modelId.equals(legacy.getImageModelId()) && Backend.TFLITE.modelId.equals(legacy.getVideoModelId()),
            "Legacy user preference was replaced");
        require(testPrefs.edit().putString("imageModelId", Backend.QNN_770.modelId)
                .putString("videoModelId", Backend.QNN_1036.modelId).commit(), "Could not set explicit QA model preferences");
        AppSettings explicit = store.load();
        require(Backend.QNN_770.modelId.equals(explicit.getImageModelId())
                && Backend.QNN_1036.modelId.equals(explicit.getVideoModelId()), "Explicit user preferences were replaced");
        JSONObject result = new JSONObject().put("schema", 1).put("label", "qnnDefault")
            .put("mode", "production SettingsStore and prepareImageSession in isolated empty preferences")
            .put("imageModelId", image.getDepthModel()).put("videoModelId", video.getVr().getDepthModel())
            .put("modelSource", "apk_asset").put("modelSha256", QNN_SHA256).put("inferenceSize", 518)
            .put("forceGpuNoFallback", true).put("preparedWithoutInference", true).put("inferenceCount", inferenceCount.get())
            .put("externalModelFiles", 0).put("prepareAndCloseMs", sessionMs).put("runtime", runtime.toString())
            .put("legacyPreferencesPreserved", true).put("explicitPreferencesPreserved", true);
        PerformancePipelineQa.writeReport(new File(root, "defaults.json"), result);
        exposeDefaults(root);
    }

    private static void exposeDefaults(File root) throws Exception {
        File report = new File(root, "defaults.json");
        if (report.isFile()) {
            Os.chmod(root.getPath(), 0755);
            Os.chmod(report.getPath(), 0644);
        }
    }

    private static void assertBackend(Backend backend, String runtime) {
        require(!runtime.contains("gpu output flat") && !runtime.contains("gpuMode=CPU"),
            "Invalid GPU output or unexpected CPU runtime");
        if (backend.bundled) {
            require(runtime.contains("qnn gpu runtime input=" + backend.size + "x" + backend.size + " precision=FP16")
                    && runtime.contains("backend=libQnnGpu.so") && runtime.contains("noCpuFallback=true")
                    && !runtime.contains("tflite runtime input="), "Expected strict QNN GPU runtime");
        } else {
            require(runtime.contains("tflite runtime input=518x518")
                    && runtime.contains("requestedGpu=true forceGpu=true delegateActive=true")
                    && runtime.contains("gpuMode=ACCURATE_OPENCL")
                    && !runtime.contains("qnn gpu runtime"), "Expected strict TFLite OpenCL runtime");
        }
    }

    private static boolean nonconstantDepth(File path, int size) {
        Bitmap bitmap = BitmapFactory.decodeFile(path.getPath());
        require(bitmap != null && bitmap.getWidth() == size && bitmap.getHeight() == size,
            "Unexpected model-depth diagnostic size");
        try {
            int first = bitmap.getPixel(0, 0);
            int[] row = new int[size];
            for (int y = 0; y < size; y++) {
                bitmap.getPixels(row, 0, size, 0, y, size, 1);
                for (int pixel : row) if (pixel != first) return true;
            }
            return false;
        } finally {
            bitmap.recycle();
        }
    }

    private static final class EncodedTrack {
        String mime;
        int width, height;
        String payloadSha256 = "";
        final List<Long> ptsUs = new ArrayList<>();
        final List<Integer> sampleSizes = new ArrayList<>();

        JSONObject json() throws Exception {
            return new JSONObject().put("mime", mime).put("width", width).put("height", height)
                .put("sampleCount", ptsUs.size()).put("ptsUs", new JSONArray(ptsUs))
                .put("sampleSizes", new JSONArray(sampleSizes)).put("payloadSha256", payloadSha256);
        }
    }

    private static EncodedTrack readTrack(File file, String prefix, boolean hashPayload) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getPath());
            int index = -1;
            EncodedTrack result = new EncodedTrack();
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith(prefix)) continue;
                require(index < 0, "Expected a single " + prefix + " track");
                index = i;
                result.mime = mime;
                if (prefix.equals("video/")) {
                    result.width = format.getInteger(MediaFormat.KEY_WIDTH);
                    result.height = format.getInteger(MediaFormat.KEY_HEIGHT);
                }
            }
            require(index >= 0, "Missing " + prefix + " track");
            extractor.selectTrack(index);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteBuffer buffer = hashPayload ? ByteBuffer.allocateDirect(512 * 1024) : null;
            while (extractor.getSampleTrackIndex() >= 0) {
                result.ptsUs.add(extractor.getSampleTime());
                if (hashPayload) {
                    buffer.clear();
                    int size = extractor.readSampleData(buffer, 0);
                    require(size > 0 && size <= buffer.capacity(), "Invalid source AAC packet");
                    buffer.position(0).limit(size);
                    digest.update(buffer);
                    result.sampleSizes.add(size);
                }
                if (!extractor.advance()) break;
            }
            if (hashPayload) result.payloadSha256 = hex(digest.digest());
            else Collections.sort(result.ptsUs);
            return result;
        } finally {
            extractor.release();
        }
    }

    private static File fixture(File root, String name, String expectedHash) throws Exception {
        File file = new File(root, name);
        require(file.getCanonicalFile().equals(new File(root.getCanonicalFile(), name)) && file.isFile(),
            "Missing safe authorized fixture " + name);
        try (InputStream stream = Files.newInputStream(file.toPath())) {
            require(expectedHash.equals(sha256(stream)), "Unexpected fixture hash " + name);
        }
        return file;
    }

    private static void verifyBundledModel(Context context, String expectedHash) throws Exception {
        try (AssetFileDescriptor descriptor = context.getAssets().openFd(QNN_ASSET);
                InputStream stream = descriptor.createInputStream()) {
            require(descriptor.getLength() == 50682917L && expectedHash.equals(sha256(stream)),
                "Unexpected bundled ONNX bytes");
        }
    }

    private static String sha256(InputStream stream) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = stream.read(buffer)) != -1) digest.update(buffer, 0, read);
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(Character.forDigit((value >>> 4) & 15, 16));
            hex.append(Character.forDigit(value & 15, 16));
        }
        return hex.toString();
    }

    private static void copy(File from, File to) throws Exception {
        require(from.isFile() && from.length() > 0, "Missing generated output " + from.getName());
        Files.copy(from.toPath(), to.toPath());
    }

    private static String readText(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static Unit append(StringBuffer runtime, String line) {
        runtime.append(line).append('\n');
        return Unit.INSTANCE;
    }

    private static double elapsed(long started) { return (System.nanoTime() - started) / 1e6; }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
