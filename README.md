# ParallelVrGallery Pro

一个本地 Android 图库应用：按“全部 / 相册 / 生成”浏览系统图片和视频，并在手机本地生成平行眼 SBS VR 缓存。

## v1.0.03 更新

- 视频帧从生成阶段直接保留左右眼，OpenGL 编码时分别画入左右半屏，不再先合成大 SBS 再切开缓存。
- 左右眼 WebP Lossless 改为并行编码，压缩努力调整为 40；像素仍完全无损，仅用略大的缓存换取更快写入。
- 视频恢复时先读取第 0 帧缓存，避免命中缓存后仍重复执行一次深度推理。
- 调试页拆分吞吐间隔、端到端延迟、队列等待、缓存命中读取和深度结果队列，修复“当前有效帧”混入排队时间的问题。
- 最终 AVC 编码优先使用硬件支持的 VBR/High Profile，目标码率提高到至少 0.10 bpp/帧或源视频码率的两倍；v1.0.02 成品继续可浏览，升级时正在生成的 V12 帧也可供 V13 续跑。

## v2.37 更新

- 恢复 GPU 强制模式：开启 GPU 时继续按强制 GPU 路径运行，不自动回退 CPU。
- 模型下载请求增加 User-Agent、二进制 Accept、重定向支持和更长读取超时。
- 模型下载失败信息改为按下载源显示，便于判断是直连、代理还是校验失败。

## v2.36 更新

- 恢复图片 VR 当前图最高优先级生成机制：当前 Viewer 图片进入 `currentPending`，前后图片只做普通预加载。
- 修复调试页可能出现 `State=QUEUED` 但 `Job=-` 的问题，当前图入队时同步写入可见 job。
- 恢复图片 VR 按钮语义：VR 模式下显示 `关闭 VR`，`QUEUED` 只表示排队等待生成。

## v2.35 更新

- 检查更新改为多源 fallback：GitHub API 失败时会继续尝试代理 API 和 Release 页面解析。
- APK 下载支持直连、`gh-proxy.com`、`ghproxy.net` 多线路重试，并在进度里显示当前下载源。
- 如果只能打开 Release 页面，App 会提示用户在浏览器中手动下载，避免只显示“检查更新失败”。

## v2.34 更新

- 全部页、相册页顶部栏改为覆盖在内容上的半透明层，状态栏也随页面透明，不再留下实心白块。
- 图片 VR 按钮在未生成、队列中、暂停或失败时会重新启动当前图生成，只有已生成时才关闭 VR。
- GPU 开启时允许自动回退到 CPU，避免强制 GPU delegate 导致 SBS VR 生成卡住或失败。

## v2.33 更新

- 全部页、相册页顶部 `相册 / 设置 / 刷新` 区域的白底改为半透明，和底部导航的轻遮罩风格保持一致。
- 顶层 Scaffold 背景改为透明，让页面浅灰底色透出，减少顶部实心白块割裂感。

## v2.32 更新

- 状态角标改为无背景文字变色，减少对缩略图的遮挡。
- 生成页视频卡片缩短状态文案并减小字体，例如 `暂停 0% 当前0ms 均0ms`。
- 底部 `全部 / 相册 / 生成` 导航改为半透明白底，选中紫色也改为半透明。
- 生成页图片/视频滚动位置会保存，切页、进 Viewer、进设置后返回仍回到原位置。
- 检查更新改为 App 内下载 APK；下载完成后显示安装按钮并拉起系统安装界面。
- Video Depth Anything Small 权重已下载到本机临时目录并记录 SHA-256，但当前机器缺少 Python/PyTorch/TensorFlow 转换链，未上传不可运行模型。

## v2.31 更新

- 视频队列改为最多同时生成 1 个视频，其他视频保持“队列中”，避免两个视频同时生成导致整机卡顿。
- 视频模型选择里新增 `Video Depth Anything Small（视频专用，待导入）` 条目。
- `Video Depth Anything` 当前只作为待导入模型显示；手机端实际运行仍需要先提供 TFLite/ONNX/MNN/QNN 等移动端模型资产和 SHA-256。
- 修复生成视频在生成页可能不显示缩略图的问题：私有缓存视频现在用 `MediaMetadataRetriever` 直接从文件抽封面。
- 缩略图角标改为状态色：已生成绿色、失败红色、队列中/生成中/暂停黄色、原图/未生成白色，并缩小背景贴近文字。

## v2.30 更新

- 视频生成新增“视频深度 worker 数”，默认 2 个独立深度模型会话，用于并行跑原始深度推理。
- 视频流水线调整为：解码 -> 多 depth worker 原始深度 -> 按帧序时序平滑/后处理/SBS -> 编码。
- 时序平滑仍严格按帧号顺序执行，避免多 worker 破坏连续帧稳定性。
- 视频缓存版本升级到 `encoderV12`，并在缓存名里加入 `dw1/dw2`，避免不同 worker 设置混用旧结果。

## 功能

- 系统图库式首页：`全部 / 相册 / 生成` 三个入口。
- 全部页按时间浏览图片和视频；相册页按系统相册/文件夹浏览。
- 图片 VR：本地生成深度图和 SBS 平行眼图，支持缓存、调试页和调试包分享。
- 视频 VR：手动加入队列后逐帧生成 SBS 视频，并尽量保留原音频。
- 生成页：按图片/视频管理已生成、生成中、暂停和失败的缓存任务。
- 模型按需下载：APK 不内置大模型，首次生成前下载并校验 SHA-256。

## 诊断说明

如果视频调试页里显示：

```text
深度推理 360ms
SBS 39ms
缓存写入 21ms
编码提交 17ms
```

说明瓶颈主要在深度模型推理。`视频深度 worker 数 = 2` 会尝试并行跑两个独立模型会话；如果显存或内存吃紧，可以在设置里降回 1。

## 构建

```powershell
$env:JAVA_HOME='D:\AndroidBuild\jdk17\jdk-17.0.19+10'
$env:ANDROID_SDK_ROOT='D:\Android\Sdk'
D:\AndroidBuild\gradle\gradle-8.10.2\bin\gradle.bat assembleDebug
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Release 上传包

普通 Debug APK 同时包含真机和模拟器的四种 CPU 架构，文件较大。发布到 GitHub
供 Android 真机安装时，可构建仅含 `arm64-v8a` 的上传包：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\build-github-apk.ps1
```

输出文件：

```text
_workspace_artifacts/github-release/app-debug.apk
```

该参数不会改变普通 `assembleDebug` 的通用 APK。上传包适用于 64 位 ARM Android
真机，不适用于 x86/x86_64 模拟器或仅支持 32 位 ARM 的旧设备。

## 模型资产

APK 不内置 `depth_anything_v2.tflite`。首次生成 VR 前，App 会从 GitHub Release 下载模型并校验 SHA-256。

覆盖安装升级通常不会清理 App 私有外部目录，所以本地已下载过模型时不需要重新下载；卸载 App 会由系统清理该目录。

## 第三方项目与依赖

本项目是新的 Android App，没有复制旧 APK 的源码或资源。主要依赖和参考包括：

- Android SDK / Jetpack Compose / AndroidX / Material3：App 框架和 UI。
- TensorFlow Lite / TFLite GPU Delegate：本地深度模型推理。
- Depth Anything V2：当前图片/视频逐帧深度估计模型来源。
- Video Depth Anything：视频时序一致性深度模型方向，当前作为待导入移动端模型选项。
- SamSeenX/ComfyUI_SSStereoscope：SBS 生成思路参考，包括深度平滑、相对视差和边缘填充。

更详细说明见 [NOTICE.md](NOTICE.md)。
