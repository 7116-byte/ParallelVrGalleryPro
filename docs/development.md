# 开发和构建

当前源码版本为 **1.15.00 / 11500**。行为约束见 [AGENTS.md](../AGENTS.md)，版本变更及设备验证范围见 [v1.15.00 说明](releases/v1.15.00.md)。项目使用 `com.local.parallelvrgallerypro` 作为包名、命名空间和 Kotlin package，源码目录仍为 `app/src/main/java/com/local/parallelvrgallery`。

## 普通源码构建

安装 JDK 17 和 Android SDK Platform 36，在项目根目录用仓库自带的 Gradle Wrapper 运行。项目固定 Gradle 8.10.2、AGP 8.7.3、Kotlin 2.0.21。Android 最低支持 API 30。

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Linux 和 macOS 可用 `bash ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`。首次需要连接依赖仓库。测试报告在 `app/build/test-results/`，Lint 报告在 `app/build/reports/`，调试包在 `app/build/outputs/apk/debug/`。 [CI 工作流](../.github/workflows/android-checks.yml)

调试 APK 使用本机构建签名。需要在同一设备保留已安装正式版的数据时，使用同发布证书的正式 APK。

## 已恢复工作区的离线验收

Windows 已配置恢复资产时，可双击根目录 `Verify-Restoration.cmd`，也可分别执行资产检查与构建。日常增量验收省略 `-Clean`。新增依赖须联网时可显式使用 `-Online`。

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verify-LocalAssets.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build.ps1 -Clean
```

恢复工作区的备份归档、工具链和验证证据集中在 Git 忽略的 `.local/`。可移植环境包含 JDK 17.0.19+10、SDK 36、Build Tools 34.0.0 和 35.0.0、Gradle 依赖以及 QNN 的离线 AAR 和 POM。`Verify-LocalAssets.ps1 -FullArchive` 按迁移清单核对归档。脚本使用项目内工具链，验证结束后恢复调用进程的环境设置。

`Build.ps1` 保存本次构建的源码清单、日志、测试计数、Lint 统计、APK 元数据和证书核验。读本次生成的 `result.json` 确认 `passed`，真机验证另行记录。恢复基线为 1.2.00 / 10200，普通版资产及将来删除旧备份的条件见 [恢复验收记录](recovery-acceptance-2026-10-31.md)。

## 推理和缓存

APK 内置 Depth Anything V2 Small 的 FP16 ONNX 和保留末端三层卷积为 FP32 的混合精度型号。ONNX Runtime Android QNN 1.29.0 与 Qualcomm QNN Runtime 2.42.0 来自 Maven Central。构建从所用 AAR 打包许可和通知。QNN 后端在已支持的 64 位高通设备上运行，创建、推理和释放都属于同一拥有者线程。QNN 禁止 CPU 回退，TFLite 保留单独的强制 GPU 设置。

图片与视频分别保存型号、名义边长和比例。名义边长为 37–74 格乘以 14 像素。任务、缓存、从原图导出都使用各自记录的模型身份和参数。旧方形 ID 继续有效，旧 `fit` 和 `widetall` 仅迁移当前设置，历史任务和结果继续读取原有 ID。

| 输入比例 | 形状和准备时机 |
| --- | --- |
| 固定方形 | 使用名义边长×名义边长。图库空闲时准备当前图片模型的固定尺寸。 |
| 14 像素分块 | 按素材比例选 14 的倍数作为宽高，像素数受名义边长平方限制，长边至多为名义边长两倍。素材尺寸已知后按实际张量形状准备。 |
| 双竖版（9:16 / 3:4） | 预先准备两个尺寸，1036 档为 756×1344 和 882×1176。按素材的短长边比选图，横向输入旋转 90°，推理后将浮点深度转回原方向。 |

启动预热在媒体和持久化队列恢复后，仅准备当前图片模型可提前确定的形状。14 像素分块的不同尺寸共用 ONNX 权重，GPU 编译图按模型、尺寸和运行环境缓存。磁盘中的编译图失效时，从原模型重新编译。设备样本中 1386×770 初次编译约 5.20 秒、磁盘载入约 0.40 秒，原生深度字节一致。

诊断分别记录模型实际输出、裁回深度、后处理深度与 SBS 尺寸。深度放大只作用于后处理。模型来源与 SHA-256 见 `QnnDepthModel.kt` 和 APK 随附的第三方通知。TFLite 可下载资产由 App 按模型校验值验证。

## 结果管理与视频流水线

- 生成相册按完整参数分组，归组仅去掉图片缓存键的 `_take` 副本标记。单个结果使用原图键与完整缓存版本，Viewer 捕获当次进入的结果范围。使用新参数生成会跳过同参数已有成品或任务的原图。重新生成成功后替换所选旧结果。
- 图片预览缓存保留 JPEG 100。普通保存使用 `OriginalImageExportSpec` 捕获结果参数，从原图重生成无损 PNG。保持该结果的实际单眼尺寸，原图不可用或参数不完整时明确失败。生成及导出发布继续由 `GenerationGuard` 与结果身份保护。
- 双竖版横屏视频使用独立的输入旋转缩放与深度恢复工位，两级队列各容量 4，总在途帧受内存限制。历史方向记录决定已有任务的推理方向。时序平滑、投影和单源 SBS 编码按原帧序执行。
- 没有旧逐帧缓存的新任务，每段最多 72 帧，原子提交完整分段与状态校验。续跑读取最后完整段保存的时序深度状态。合并按源 PTS 写入视频样本，复制原音频。只有登记成品后才清理本版本的临时分段或逐帧缓存。 [分段验证范围](releases/v1.15.00.md#已有验证)

## 发布和真机验收

正式包用 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build.ps1 -Clean -Variant Release` 生成。签名脚本显式使用历史发布证书对应的本地签名，并核对 APK 包名、版本及证书。Release 禁用调试且不含 QA 入口。版本规则为「大版本.新增功能.修复序号」。

GitHub Release 使用 `app-debug.apk` 和 `app-debug.apk.sha256`。更新器按数字段比较版本，安装前校验应用包名、原发布证书和递增的 `versionCode`。公开下载的文件大小、SHA-256 和签名与本地验收 APK 同时核对。凭证、签名材料和私人媒体均留在本地。

Debug 入口和 `scripts/Build-Benchmark.ps1` 构建的独立 APK 只用于显式授权的测试副本。性能对照使用相同素材、参数和尺寸，分别记录冷会话、已就绪会话与结果缓存；照片或视频的真实生成时间按完整任务计算。构建、真机、CI、公开下载及 App 内升级分别标记。设备验收只覆盖安装，保留数据、偏好与原有任务。

## 历史技术记录

- [队列和后台恢复](background-and-navigation-v1.10.00.md) · [来源预加载与预热](prefetch-and-startup-v1.1.01.md) · [删除与发布一致性](edge-and-queue-v1.1.00.md)
- [从原图保存 PNG](original-png-v1.9.00.md) · [新任务性能](fresh-generation-v1.8.00.md) · [预加载和格式](performance-v1.7.00.md)
- [独立 770 推理](depth-resolution-v1.6.00.md) · [人体多点分析](navigation-and-body-v1.5.02.md) · [六种投影算法](mode-comparison-v1.4.00.md) · [ComfyUI 主节点](comfy-main-v1.4.00.md) · [深度放大](depth-upscale-v1.2.00.md)
- [统一界面](studio-v1.3.00.md) · [恢复验收](recovery-acceptance-2026-10-31.md) · [完整版本记录](../CHANGELOG.md)
