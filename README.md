# ParallelVrGallery Pro

在 Android 设备上浏览图片和视频，并在本地将素材生成为平行眼 SBS（左右并排）立体图或视频。原始素材留在设备上。

## 下载与升级

需要 Android 11 或更高版本。在稳定版发布页的资产列表中下载 `app-debug.apk`，用系统安装器打开。已安装 Pro 的用户可直接覆盖升级，保留设置、任务和生成结果，也可以从「设置 → 通用 → 检查更新」进入安装流程。 [下载最新稳定版](https://github.com/7116-byte/ParallelVrGalleryPro/releases/latest)

## 使用

- **浏览与播放** · 全部、相册和生成分别记住浏览位置。打开图片可启用平行眼预览。双眼同步缩放、拖动，视频使用单个 SBS 源播放。
- **生成与管理** · 当前图片优先处理，随后从进入时的来源预加载邻图。视频手动加入队列。生成页按完整参数归并图片相册，视频支持批量开始、暂停与续跑。
- **保留自己的参数** · 六种立体投影模式各自保存参数和默认值，调试页可临时生成当前图片的六种结果，通过一个按钮循环对比并保持缩放及位置。
- **保存成品** · 图片视图使用 JPEG 100 预览缓存。保存副本时按选中结果记录的参数从原图重做，写入无损 PNG。视频保存复制已完成的 SBS MP4。

## 模型与分辨率

图片和视频分别设置推理模型、分辨率和比例。兼容的 64 位高通设备首用默认选择内置 QNN FP16 518，已保存的模型选择继续有效。QNN 也提供末端 FP32 型号，可设 37 至 74 格，每格 14 像素。其他设备可选适用的 TFLite 模型，部分模型在首次使用时下载并校验。

「深度与输出」中的深度放大和成品尺寸独立于模型输入。已排队任务保留自己的参数，续跑使用原任务设置。

## 从源码构建

使用 JDK 17、Android SDK Platform 36 和仓库自带的 Gradle Wrapper。首次构建需联网解析依赖。Windows 在项目根目录运行下列命令，Linux 可用 `bash ./gradlew` 替换 `.\gradlew.bat`。

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/`。本地开发包和 GitHub 正式包分别使用开发签名和发布签名。在已有恢复资产的 Windows 工作区，可使用 `Verify-Restoration.cmd` 做离线验收。 [开发与构建说明](docs/development.md)

## 版本与来源

- [v1.15.00 变更与验证范围](docs/releases/v1.15.00.md)
- [完整版本记录](CHANGELOG.md)
- [第三方依赖与许可](NOTICE.md)
