# 2026-10-31 重装后恢复验收

当前状态：**Pro 重装后电脑端恢复与离线构建验收通过；真机验收未执行；旧备份保留。** 下列本次结果均来自 2026-10-31 的新运行，历史备份报告单独保留。

## 恢复来源与迁移

- 正式根目录：E:\AI-Programme\ParallelVrGalleryPro；开始时已检查为空，没有覆盖原有项目内容。
- 原备份：U:\备份\ParallelVrGallery-完整恢复备份-2026-10-28；没有删除或清理。
- 基线：1.2.00 / 10200，Git HEAD 389798ab66f4dcef735dc2e22ce424cf9f18e59a；从 git/ParallelVrGalleryPro.bundle 恢复历史，覆盖 sources/ParallelVrGalleryPro 快照，origin 恢复为原 GitHub。
- 2026-10-31 备份清单及清单自身 SHA-256 验证通过，35,530 个静态文件、2,728,324,975 字节，0 缺失、0 大小或哈希不符。两个 bundle 完整性验证通过。
- 整个实际备份的 46,506 个文件、4,623,358,495 字节复制到 .local/recovery，逐文件重新读取来源及目标 SHA-256，全部相同。包括旧工作副本、历史报告和可重建缓存，范围大于原静态清单。详见 .local/verification/migration-manifest.json 及同名 .sha256；清单 SHA-256 为 85D3318937671A3F7F826C7BD587D2BB10572FDF1468DBC2C0CBF787C0B98F10。
- 75 个 sources 快照文件与旧 workspace 对照全部相同。旧工作副本 Git 的大量状态噪音为换行符配置，Pro 无实质未提交修改；普通版 .gitignore 增量及 .codex/、wrapper 等未跟踪文件实际存在并已保留。原记录中列出的 .codex-remote-attachments 聊天图片没有出现在 sources 快照，不凭空宣称补齐。
- 正式 Pro 快照 54 个文件逐字节对照，仅 .gitignore 与 README 是本次预期恢复维护修改，App / 测试 / Gradle 依赖 / 业务文档未改；完整原字节仍存归档。归档之外的历史 release notes 亦由 bundle 恢复。
- 普通版镜像仓库 .local/history/ParallelVrGallery.git 已实际从 bundle 恢复，94 个命名 refs（bundle 另有 HEAD），git fsck 通过，v2.32 基准 5b6ed8b 可读取。Pro git fsck 通过。命令记录与旧工作树状态在 .local/verification。
- GitHub 现有登录可用，账号 7116-byte；只读查询确认远端 main 与基线一致。没有读取或打印 Token，没有推送、发布或上传资产。

## 本次电脑端构建证据

本次成功运行：2026-10-31 14:14:17 至 14:15:29（+08:00）。命令为 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build.ps1 -Clean`，内部使用 `--offline --no-daemon --no-build-cache --init-script scripts/portable-signing.init.gradle clean testDebugUnitTest lintDebug assembleDebug`。

本次构建从空的 app/build 开始，没有复用旧工作副本的构建输出。Gradle 报告 BUILD SUCCESSFUL in 1m 10s；54 项 actionable tasks，53 项实际执行、1 项 clean 因原本不存在输出而 UP-TO-DATE。运行目录为：

`.local/verification/runs/20261031-141417-386/`

| 检查 | 本次结果 |
| --- | --- |
| JVM | 58 项，0 失败、0 错误、0 跳过 |
| Lint | 0 错误、37 警告、12 信息 |
| assembleDebug | 成功，新 APK 138,782,653 字节 |
| 包名 | com.local.parallelvrgallerypro |
| versionName / versionCode | 1.2.00 / 10200，未升版本 |
| APK SHA-256 | 4EFE4FB786E24BD106EDEB5D35DB094E700D72FC9E3C27CC7A4AA78FCD7853B8 |
| APK 证书 SHA-256 | c51c08290903e65d2e99fe35ceffd44b637f04cc5bbdd5ea9ecb8847d4ea7359，与两份原 APK 一致 |
| 签名验证 | apksigner verify 通过，单签名者，v2 验证通过 |
| 用户全局 debug.keystore | 构建前后 SHA-256 相同；本次显式使用归档原密钥的工具链副本，没有使用或覆盖全局密钥 |

7 个测试类实际报告数量为 CacheAndTimelineTest 8、CoreRegressionTest 11、DeletionAndStartupTest 11、DepthUpscaleTest 13、GeneratedSelectionScopeTest 3、PipelineRegressionTest 4、StartupAndProgressTest 8，合计 58。

保留的证据包括 result.json、build.log、7 份 JVM XML、Lint XML/HTML、APK、apk-metadata.txt、apk-signature.txt、Git HEAD/状态、工作树补丁与构建输入 SHA-256 清单。证据目录内 APK 已再次读取 SHA-256，与正式构建输出一致。旧报告仍在 .local/recovery/verification，没有作为本次通过证据。

本次还重新读取两份保留 APK 的包名、版本及证书，并检查稳定模型字节数/哈希，结果见 .local/verification/preserved-assets.json。静态本地资产预检核对 35,517 个归档文件及相应工具链输入，记录为 .local/verification/local-static-verify.json。

构建结束后，又从 C:\Windows\Temp 调用新根目录的 `Verify-LocalAssets.ps1 -FullArchive`，仅依据本地迁移清单重新读取全部 46,506 个归档文件，并复核工具链静态输入，全部通过；此次没有读取 U 盘来源文件。记录为 .local/verification/last-local-verify.json。归档在日常工具链构建后仍与迁移时 SHA-256 完全一致。汇总入口为 .local/verification/COMPLETED.json，最终源码与证据文件的 SHA-256 清单也保存在该目录。

JDK、SDK、Gradle 用户目录、Android 用户目录及签名均使用新根目录下 .local/toolchain；local.properties 同样指向本地 SDK，脚本没有执行依赖 U 盘的构建命令。未物理拔除 U 盘；这是按实际脚本、环境路径、离线执行及本地哈希核验得到的独立性证据，不宣称做过拔盘操作。

保留的 Lint 警告：27 个 ObsoleteSdkInt、6 个 FrequentlyChangedStateReadInComposition、2 个 SelectedPhotoAccess、1 个 ModifierParameter、1 个 OldTargetApi；12 个信息项均为 AutoboxingStateCreation。本次不以恢复为由修改功能或升级依赖来消除这些已有问题。编译另有弃用 API 和未剥离原生库符号提示，不影响本次构建成功。

已修复恢复命令的两个兼容问题：从 PowerShell 7 调用 .cmd 时为子进程显式指定 Windows PowerShell 系统模块目录；为 keytool 的 Java 语言参数加完整引号，避免 Windows PowerShell 参数拆分。首次签名前置失败的 result.json 保留在 runs/20261031-141355-510；最新成功结果是上面的 141417-386，不覆盖失败记录。修正没有改动 App、依赖、系统永久环境或全局密钥。

## 真机验收：未执行

本次未安装手机、未运行图片或视频生成，不能宣称以下通过：

- ALL / ALBUM 来源范围、相册顺序、前后 2/4/8 工人补位、暂停原因区分、生成页禁止扩窗和生成 Viewer 固定范围。
- 删结果后旧任务不可回写、之后正常来源浏览可重新生成、GenerationGuard 和 staging 的真实取消/发布竞争。
- 强制 GPU、创建/推理/释放同线程、模型启动预加载、不同设备 GPU delegate 的实际行为。
- 单源 SBS 播放、原 PTS/音频同步、视频暂停续跑、旋转、快速切页、跨中线缩放、持续按住拖动。
- LEGACY_V6 / CENTERED_V7 视觉差异、深度放大插值和顺序、内存预算、图像/视频缓存隔离与 AVIF 导出。
- 首页多相册选择去重、仅清理当前目标版本、全选范围与顶栏随列表滚动。

上述项目待用户安排后按原四份 docs 文档进行真机验收；JVM/Lint/APK 签名证据不代替这些结果。

## 普通版资产及已知缺口

普通版并未成为本次正式 Pro 根目录的子项目。其 3.0.08 / 30008 源码快照、原未提交 .gitignore、未跟踪 .codex/AGENTS.md、Gradle wrapper、完整 Git bundle/镜像和 v2.32 基准、旧 workspace、当前 APK 以及来源状态/补丁均保存在 .local/recovery 和 .local/history。共享 JDK/SDK（尤其 Build Tools 34.0.0）、离线依赖、原 debug 签名和稳定模型也必须继续保留。

本次没有替普通版建立新的独立正式开发根目录，也没有重跑普通版本机构建/真机验收。旧备份报告的普通版构建成功只是历史结果。

稳定 depth_anything_v2.tflite 为 98,920,480 字节，SHA-256 B407F34F61750F31441E6F858A4BC48D8572F9EE5399FFD015CEE5FA1767083F。Qualcomm 实验模型在备份时未找到，旧 D 盘 Video Depth Anything .pth 也不在可用备份中，只有脚本/说明/已记录哈希。历史 URL 404 是 2026-10-28 的记录，本次没有重新判断其线上状态。手机照片、设置与私有生成缓存从来不在此电脑备份范围。

## 将来删除旧备份的条件

**本次保留整个 U 盘备份，不因 Pro 通过就认定整份备份可删。** 删除前需同时满足：

1. 在新根目录再次运行 Verify-Restoration.cmd 成功；检查最近运行 result.json 的 passed 为 true，而不是只看旧 APK 或历史日志。核对完整迁移清单与归档（Verify-LocalAssets.ps1 -FullArchive），最好实际断开旧备份后再执行一次。
2. 为 .local/recovery、普通版完整历史/未提交内容、原签名、稳定模型与最新源码/恢复脚本建立另一份独立存储副本并回读 SHA-256；同一 E 盘里的重复目录不构成独立备份，GitHub 也没有私密签名、模型、离线依赖与普通版未提交内容。
3. 明确普通版后续保管位置；若还要继续开发，先从其 bundle + 快照恢复独立根目录，保留原未提交内容，再重跑它的离线构建及包名 com.local.parallelvrgallery、3.0.08 / 30008、原证书核验。若只归档，需由用户明确接受归档保管方式，不能把 Pro 验收替代普通版验收。
4. 检查 U 盘是否在本次迁移后又新增了文件，及是否还有此备份目录之外的独有资产。处理已知缺口，不承诺找回原本未备份的聊天图片/模型/手机数据。
5. 保留本次来源清单、SHA-256、Git/签名/APK/测试/Lint 证据及未完成真机清单；最后由用户明确决定删除范围。当前没有执行任何删除。

本次不发布、不升版本、不清理旧备份。后续迭代从根目录 AGENTS.md 和 docs/development.md 开始。
