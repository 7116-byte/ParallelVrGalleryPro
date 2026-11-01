# 本机开发与交接

正式项目：`E:\AI-Programme\ParallelVrGalleryPro`。恢复日期：2026-10-31（Asia/Shanghai）。历史恢复基线为 **1.2.00 / 10200**；当前版本为 **1.7.00 / 10700**，详见 [performance-v1.7.00.md](performance-v1.7.00.md)。恢复历史来自离线 bundle，origin 为 `https://github.com/7116-byte/ParallelVrGalleryPro.git`。

先读 [生成性能与格式实测](performance-v1.7.00.md) 和 [本版验收](releases/v1.7.00.md)，再读 [固定透明顶栏与真实 770 推理](depth-resolution-v1.6.00.md) 及 [v1.6.00 验收](releases/v1.6.00.md)，再读 [人体多点复核](navigation-and-body-v1.5.02.md)，再读 [固定参数、反转与对比](mode-controls-v1.5.01.md)，再读 [主节点技术记录](comfy-main-v1.4.00.md)。历史发布与恢复资料保留；私密资产仍只在 `.local/`。

模式含义与画质证据需同时阅读 [六模式完整对比](mode-comparison-v1.4.00.md)。V6–V11 是内部算法/缓存标识，分属三组路线；同深度投影一致不代表整条桌面工作流或 JPEG 成品等效，更不构成六模式画质排名。

## 日常命令

在项目根目录双击 `Verify-Restoration.cmd`，或执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verify-LocalAssets.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build.ps1 -Clean
```

验收默认离线，执行 clean、testDebugUnitTest、lintDebug、assembleDebug。日常增量验收可省略 `-Clean`。新增依赖确需联网时才使用 `-Online`，本次恢复未升级任何依赖。

需要在当前 PowerShell 使用 Git/Java/ADB 时：

```powershell
. .\scripts\Use-Environment.ps1
git status --short --branch
java -version
```

点加载环境只为当前进程配置工具并更新被忽略的 local.properties；App 构建使用 Build.ps1，确保加载原签名 init 脚本。脚本执行完恢复调用进程原环境，不更改系统永久变量、全局密钥或全局 Git 配置。不需要 Android Studio，也不需要访问 U 盘或其他项目。

一键 .cmd 使用系统 Windows PowerShell 5.1，并仅在子进程内指定系统模块目录，兼容从 PowerShell 7 启动时的模块路径继承差异。keytool 的 Java 参数已按 Windows PowerShell 规则引用。原归档脚本保留作历史，不用于新正式根目录的日常构建。

## 目录与工具

| 路径 | 用途 |
| --- | --- |
| 根目录 `.git`、app、gradle、docs、tools | 正式项目及原有受版本管理的技术参考 |
| `.local/recovery/` | 完整备份归档，包括两个版本源码、bundle、原工具链、模型、APK、历史工作副本和历史报告；不要用于日常构建 |
| `.local/toolchain/` | 从归档另复制的独立可写工具链：JDK、SDK、Gradle 离线依赖、Git、GH、原签名 |
| `.local/verification/` | 本次备份验证、迁移 SHA-256 清单、Git 检查及验收证据 |
| `.local/verification/runs/<时间>/` | 每次构建的完整日志、测试 XML、Lint、APK、包信息、证书和 result.json |
| `.local/verification/latest-run.txt` | 最近一次构建证据目录；失败运行也记录，必须检查 passed 字段 |

固定工具版本为 JDK 17.0.19+10、Gradle 8.10.2、AGP 8.7.3、Kotlin 2.0.21、compileSdk 36。SDK 35 与 36、Build Tools 34.0.0 与 35.0.0 都保留，AGP 离线构建实际需要 34.0.0。Gradle 使用 `.local/toolchain/gradle-home`，不使用全局用户缓存。

`Verify-LocalAssets.ps1 -FullArchive` 按迁移清单重验完整本地归档，可在断开 U 盘后执行；默认仅检查独立构建与原签名/模型/bundle 的静态资产。迁移清单含原路径、目标路径、文件大小、SHA-256 和核对结果，另有清单自身的 SHA-256。若移动整个项目，校验按 relativePath 重定位归档，不依赖原路径。

## 签名、版本与 GitHub

包名、namespace、Kotlin package 保持 `com.local.parallelvrgallerypro`，旧源码目录名 `parallelvrgallery` 保持。原发布使用 debug 签名，Build.ps1 在构建前核对密钥文件与归档一致并检查证书，在构建后再次核对 APK 证书。证书 SHA-256：

`c51c08290903e65d2e99fe35ceffd44b637f04cc5bbdd5ea9ecb8847d4ea7359`

不把新系统自动生成的 debug.keystore 用于升级，不覆盖用户全局密钥。APK 文件 SHA-256 可以与旧发布包不同，证书必须相同。后续版本沿用“大版本.新增功能.修复序号”，恢复阶段不升版本。

恢复阶段已复用本机 GitHub 登录验证账号 7116-byte，未读取或打印 Token，当时未推送或发布。正式迭代的提交与发布另见 v1.3.00 验收记录。`.local/`、密钥、模型和缓存不入 Git。凭证由本机既有登录管理，归档不含账号登录态。Git 换行处理为仓库级 core.autocrlf=true，原快照字节保存在归档；不要将换行噪音误报为业务变更。

## 下一次继续开发

v1.7.00 当前有 22 类、136 项 JVM 测试。模式档案与个人默认只保存视觉参数，共用模型/GPU/队列偏好。旧缓存标识在新选项关闭时不变；自动匹配、模型输入反转和独立副本分别追加 `_eq1050`、`_di1`、`_take…`。图片队列保存明确参数和替换目标，启动先恢复 `image_work` 的发布事务，再扫描成品。不得把替换改回“先删后生成”。

Debug 的 `kind=modeProfiles` 对指定私密 QA 目录直接做六模式强制 GPU 对照，不启动图库；`kind=comparison` 只在指定 QA 相册检查实际加载完成后的缩放/位置及画面哈希；`kind=imageWrites` 只在合成 S1 副本验证新增、定向替换及实际读取失败。所有入口需要显式 `album/sample`，正式包不含测试入口。对比页必须等待可访问的“对比视口”出现后才注入手势，不能拿加载动画的哈希变化当验收通过。

先检查 AGENTS.md、实际 git diff/status、本次恢复验收记录，再读四份版本文档。`edge-and-queue-v1.1.00.md` 中删除预加载黑名单已被 v1.1.01 撤销，以较新的说明及代码为准。队列、暂停原因、GenerationGuard、GPU 线程归属、强制 GPU、单源 SBS、滤波模式及深度放大缓存规则不得因恢复而改变。

稳定模型在 `.local/recovery/models/depth_anything_v2.tflite`，98,920,480 字节，SHA-256 `B407F34F61750F31441E6F858A4BC48D8572F9EE5399FFD015CEE5FA1767083F`。不内置进 APK，不在恢复阶段运行媒体生成。Qualcomm 实验模型和旧 D 盘 `.pth` 没有作为可运行资产被备份，不得把历史路径当成现存依赖。

JVM 与 Lint 不证明真机画质或流畅度。恢复阶段未安装手机或生成媒体；正式迭代的两台真机测试与限制见 studio-v1.3.00.md。普通版仍是独立项目，其恢复与删除条件见恢复验收记录。

## 正式发布与真机调试（v1.3.00 起）

日常 Debug 验收命令保持不变；正式发布执行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build.ps1 -Clean -Variant Release`。正式包仍签原证书，但不可调试且不含测试 instrumentation。GitHub 资产名必须保持 app-debug.apk 与 app-debug.apk.sha256，供已发布更新器使用。

Debug 专用原生手势回归入口：`adb -s <serial> shell am instrument -w -e album PVG_QA_<explicit_album> -e sample PVG_QA_<explicit_file> com.local.parallelvrgallerypro/.StudioGestureInstrumentation`。仅在用户明确授权的测试副本上运行；Release 不包含该入口。设备覆盖安装始终用原包名与原证书，不能卸载或清空数据。生成、删除或私人媒体测试仍需先限定范围并得到授权。

新依赖只有 OpenCV Android 4.12.0，用于用户明确请求的参考 V2.2 补洞，不升级原 JDK/Gradle/AGP/Kotlin/TFLite。备份当前可离线构建的环境时，要包含日常工具链中新解析的依赖；不能用只含 1.2.00 的历史归档替代最新环境备份。
