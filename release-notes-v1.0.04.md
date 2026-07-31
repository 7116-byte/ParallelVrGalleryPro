# ParallelVrGallery Pro v1.0.04

- 图片左右眼运行缓存改为并行 JPEG 100，显著减少 WebP Lossless 带来的后台写文件耗时。
- 视频逐帧缓存改为左右眼 JPEG 100；最终 SBS MP4 编码质量、分眼 OpenGL 绘制和音频流程保持不变。
- JPEG 100 表示最高视觉质量，不是逐像素无损；用户保存图片时仍按需编码无损 AVIF。
- Viewer、生成页、调试包和断点续跑优先读取新 JPEG，同时继续兼容旧 WebP、V13/V12 分眼帧与旧 SBS JPEG。
- 图片缓存增加独立存储格式版本，视频编码缓存升级为 `encoderV14`，并记录左右眼写入耗时和实际帧缓存格式。
