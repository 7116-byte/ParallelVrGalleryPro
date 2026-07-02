# 第三方项目与依赖 / Third-Party Projects and Dependencies

本项目是一个新的 Android App，没有复制旧 APK 的源码或资源。

## Android / Google

- Android SDK, Android Gradle Plugin, Kotlin Android plugin
- Jetpack Compose, AndroidX Activity/Core/Lifecycle, Material3
- 用途：Android 构建、原生 UI、MediaStore 相册权限、生命周期和界面组件。
- 官方网站：
  - https://developer.android.com/
  - https://developer.android.com/compose

## TensorFlow Lite

- 依赖：`org.tensorflow:tensorflow-lite:2.16.1`
- 依赖：`org.tensorflow:tensorflow-lite-gpu:2.16.1`
- 用途：在手机本地运行深度估计 TFLite 模型，并尝试使用 GPU delegate 加速。
- 官方网站：https://www.tensorflow.org/lite

## Depth Anything V2

- 上游项目：https://github.com/DepthAnything/Depth-Anything-V2
- 用途：当前图片和视频逐帧深度估计模型来源。
- 说明：模型文件不提交到 Git；App 首次生成前从 Release 资产下载并校验 SHA-256。

## Video Depth Anything

- 上游项目：https://github.com/DepthAnything/Video-Depth-Anything
- 论文页面：https://arxiv.org/abs/2501.12375
- 用途：视频时序一致性深度估计方向。v2.31 先在视频模型选择中作为“待导入”模型显示。
- 说明：当前 Android 推理链路还需要 TFLite/ONNX/MNN/QNN 等移动端模型资产后才能本地运行；不会把 PyTorch checkpoint 标记为 App 可运行模型。
- v2.32 转换记录：Small 权重已下载到本机临时目录，SHA-256 为 `13379300B739E659F076A59D52E9801BD8D38C541A7E71F73BBCA4DCFB013609`；因本机缺少 Python/PyTorch/TensorFlow 转换链，未上传模型资产。
- 许可提醒：官方 Small 模型为 Apache-2.0；Base/Large 为 CC-BY-NC-4.0，后续导入模型时需要按实际权重许可处理。

## SamSeenX/ComfyUI_SSStereoscope

- 项目：https://github.com/SamSeenX/ComfyUI_SSStereoscope
- 用途：SBS 生成算法思路参考。
- 参考点：深度图平滑、相对视差、按列位移、边缘填充和调试输出思路。
- 说明：本项目没有直接复制 ComfyUI 节点代码，而是在 Android/Kotlin 中按相同思路实现本地生成流程。

## Build Tools

- Gradle：https://gradle.org/
- Eclipse Temurin JDK：https://adoptium.net/temurin/
- GitHub CLI：https://cli.github.com/

## License Notes

分发 APK、模型或派生版本时，请同时遵守 Android SDK、TensorFlow Lite、Depth Anything V2、Video Depth Anything、模型权重以及参考项目各自的许可证条款。
