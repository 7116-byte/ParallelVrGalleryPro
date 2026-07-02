# ParallelVrGallery Pro v1.0.00

- 新建 Pro 版本线，应用名、包名、版本号与旧项目独立。
- 图片缓存改为左右眼分离：`left.webp`、`right.webp`、`depth.png`、`params.json`、`job.log`。
- Viewer 优先直接读取左右眼缓存；旧 `vr_sbs.jpg` 缓存仍可兼容浏览。
- 保存生成图片时导出无损 SBS AVIF；替换原图时临时生成兼容 JPEG SBS。
- 视频生成阶段改为左右眼 WebP Lossless 帧缓存，最终播放仍使用单个 `vr_sbs.mp4`。
- 调试包包含左右眼缓存、深度图、参数和日志，便于排查缺失文件。
