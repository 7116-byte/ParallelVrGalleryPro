# Video Depth Anything mobile conversion

This folder records the v2.32 conversion path for Video Depth Anything Small.

Status:

- The official `video_depth_anything_vits.pth` checkpoint was downloaded locally to:
  `D:\AndroidBuild\model_conversion\VideoDepthAnything\video_depth_anything_vits.pth`
- SHA-256:
  `13379300B739E659F076A59D52E9801BD8D38C541A7E71F73BBCA4DCFB013609`
- The local machine currently has no real Python runtime on PATH. `python.exe` is the Microsoft Store stub, and `uv`, `pip`, and `conda` were not found.
- Because PyTorch/ONNX/TensorFlow conversion could not run, no converted model asset was uploaded to GitHub.

Required conversion target:

1. PyTorch checkpoint -> static ONNX with input shape `1 x 32 x 3 x 518 x 518`.
2. ONNX -> TensorFlow SavedModel or equivalent bridge.
3. TensorFlow -> float16 TFLite.
4. Validate that Android TFLite can load the file and output non-flat depth before marking the App model as downloadable.

Do not upload the raw `.pth` as an App-ready model. The Android app cannot run PyTorch checkpoints directly.
