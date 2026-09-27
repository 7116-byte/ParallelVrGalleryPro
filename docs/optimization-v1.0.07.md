# v1.0.07 Optimization and Regression Notes

## Scope and compatibility

This patch is based on Pro v1.0.06. It does not replace gallery navigation,
drag selection, source-scoped prefetch tiers, GPU force mode, export formats,
or the single-player SBS video display. No existing generated library is deleted.

The centered depth filter changes generated pixels, so new image/video jobs use
`depthV7`. Video pipeline/cache commits use `encoderV15`. Finished V12/V13/V14
videos remain readable. Older depth-version frame caches are not mixed into a
new generation. JPEG quality 100 remains lossy; this patch does not claim 99%
similarity or a measured device speedup without representative source/device tests.

## Components

- `AffinityWorker` / `ThreadBoundResource`: delegate lifetime on one thread;
  priority affects queued work only. GPU sessions share an owner. CPU sessions
  have independent owners. The idle image-session pool keeps one reusable session.
- `DepthFilters`: separable centered box mean with clipped edge normalization.
  Constant-depth maps remain constant, including corners and large radii.
- `VersionedIndex`: startup/explicit refresh scans disk; job completion and deletion
  update the in-memory catalog. Ordinary lookup no longer decodes cache headers.
- `BufferedTextFile`: queue snapshots in memory, 150 ms coalescing, atomic replacement,
  flush at owner shutdown. A process kill can lose the most recent coalescing window;
  generated content is not affected. Write errors are retained and flush reports them.
- `ViewerImageLoader` / `WeightedLru`: shared in-flight decodes, two concurrent
  decodes, a heap-relative 24-96 MiB cache budget. Visible bitmaps can outlive the
  LRU and are never recycled behind Compose. Previews do not change saved originals.
- `OrderedFramePipeline`: bounded decode-to-consumer lifetime, parallel CPU depth,
  ordered temporal/stereo processing, independent cache writer and encoder.
  Shared leases keep both consumers safe. All pipeline stages propagate failures.
- `SequentialVideoDecoder` / `VideoFrameReader`: one sequential codec with source
  PTS, YUV crop/stride/range/matrix handling and rotation. The first uncached frame
  is compared with Android's retriever. Mean channel error above 1.5/255 or excessive
  RGB conversion cost selects the compatibility path. Unsupported HDR/raw formats
  also use the existing Android retriever path, which is not a new HDR-preservation
  guarantee. Runtime logs identify the selected path and reason.
- `VideoTimeline`: retains presentation-order timestamps, including VFR and >60 FPS.
  Audio retains its original sample timestamps. Duplicate video timestamps fail
  explicitly rather than assigning the wrong frame-cache indices.
- SBS GL playback: dirty rendering driven by frame arrival, viewport and zoom
  changes; no second player and no continuous redraw of a paused video.

## Diagnostics

Video debug keeps decode, depth, temporal, post-process, stereo and encode timing.
Cache writes now have their own queue and last-completed duration. That duration
is intentionally not added to same-frame generation latency because writing can
overlap encoding. The log records effective depth workers, maximum in-flight
frames, decoder validation/fallback, and final cache-writer average time.

GPU depth work is serialized on its owner even when CPU depth worker count is two.
This preserves thread affinity and avoids loading a redundant GPU interpreter.
It is not a hardware GPU-utilization cap. Native inference cannot be interrupted
halfway through a frame; pause is checked before/after pipeline stages.

## Automated checks

Run with JDK 17 and Android SDK 36:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Tests cover thread identity/priority, centered filter reference values, active
resource-pool eviction, generated-viewer prefetch exclusion, album ordering,
byte-budget eviction, atomic queue persistence, index replacement/deletion,
VFR/high-FPS PTS, YUV range/matrix choice, out-of-order inference, bounded in-flight
resources, encoder/writer overlap, and exact-once cleanup after stage failures.

GitHub Actions repeats build/tests/lint and stores reports. CI's ephemeral signing
key is not used to publish upgrades. Published APKs keep the existing local signer.

Local verification on 2026-09-28: all 18 JVM tests passed, `lintDebug` completed
with 0 errors and 46 warnings, and `assembleDebug` succeeded. APK metadata:
`com.local.parallelvrgallerypro`, versionName `1.0.07`, versionCode `10007`.
The APK signer matches the downloaded v1.0.06 release certificate.
APK SHA-256: `62B683DE644CC2367AD11858EAFCBA46FF4BA29C84B6D98C28D2DBAB63ADDC57`.

## Device acceptance still required

No Android device was connected during implementation. JVM tests and lint cannot
prove a vendor MediaCodec/TFLite/GL path or touch interaction works on that phone.

1. All/album viewers: verify original order, source boundaries, 2/4/8 tiers,
   no new prefetch after leaving Viewer, and no prefetch in generated Viewer.
2. Rapidly alternate cached/uncached photos, zoom both eyes, reset and page again;
   watch process heap rather than LRU size alone.
3. Generate a landscape, portrait-rotation, 29.97 FPS, 120 FPS and VFR clip;
   verify decoder log, input/output duration, rotation, audio sync and last frame.
4. Pause/resume at decode/depth/cache/encoding, background the app, and simulate
   an incomplete cache pair. Only pairs with the V15 completion marker are reused.
5. Playback: rapid paging, pause, zoom across the center line, rotate and return.
   Confirm paused video no longer repeatedly redraws an unchanged GL frame.
6. Compare at least three cold/warm runs using throughput p50/p95, peak heap,
   cache-write queue, source/output visual comparisons and thermal state.
   Do not infer speedup from stage timings added together when stages overlap.
