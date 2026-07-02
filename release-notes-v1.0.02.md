# ParallelVrGallery Pro v1.0.02

- 图片生成时左右眼 WebP Lossless 缓存改为并行编码，减少写入阶段墙钟等待。
- `Write` 仍表示总写入墙钟耗时；`Left WebP` / `Right WebP` 表示各自编码任务耗时，二者会重叠。
- 不改变缓存格式、生成算法、导出格式。
