# ParallelVrGalleryPro 开发与恢复规则

正式开发根目录为本文件所在目录。先读 README.md、docs/development.md、docs/recovery-acceptance-2026-10-31.md、docs/studio-v1.3.00.md 和原四份版本技术文档，再检查实际 Git 状态。历史恢复基线为 1.2.00 / 10200，HEAD `389798ab66f4dcef735dc2e22ce424cf9f18e59a`；当前版本与 HEAD 以源码及实际 Git 为准。恢复阶段的“不改功能、不升版本、不发布”限制只记录历史恢复范围。本次正式迭代已获用户授权实现、验证、提交、推送、打标签和发布，无需临发布重复确认。设备覆盖安装不卸载、不清空数据；媒体测试仍需明确范围授权。始终不删除旧备份。

## 开发入口与私密资产

- 源码及 `.git` 直接位于根目录；`.local/` 必须一直被 Git 忽略。原签名、稳定模型、便携工具、离线缓存、完整恢复材料和历史验收资料都在 `.local/`，禁止提交或上传。不要读取、打印、复制 GitHub Token；优先复用本机已有登录。
- 双击 `Verify-Restoration.cmd`：本地资产核验、离线 clean / JVM / Lint / APK 构建与包名、版本、证书核验。日常构建使用 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build.ps1`，默认离线。
- 显式使用 `.local/toolchain/android-user-home/debug.keystore`；历史发布使用这份 debug 签名。证书 SHA-256 必须是 `c51c08290903e65d2e99fe35ceffd44b637f04cc5bbdd5ea9ecb8847d4ea7359`。不得替换为新系统的密钥，不改用户全局密钥。
- applicationId / namespace / Kotlin package 均为 `com.local.parallelvrgallerypro`。源码目录仍是 `app/src/main/java/com/local/parallelvrgallery`，不得按目录名误改 package。
- JDK 17.0.19+10、Gradle 8.10.2、AGP 8.7.3、Kotlin 2.0.21、SDK 36、Build Tools 34.0.0 和 35.0.0 均保留。v1.3.00 仅为参考新版补洞新增 OpenCV Android 4.12.0，没有整套升级依赖。版本规则为“大版本.新增功能.修复序号”。

## 必须维持的行为

1. ALL / ALBUM Viewer 捕获进入时来源及顺序，只能在该来源浏览与预加载；生成 Viewer 捕获已有结果范围，不追加后台结果，不回落全局 photos。
2. 生成页、生成 Viewer、视频切换不新增普通图片预加载。只允许已有任务完成、恢复来源切换暂停任务，以及明确手动重新生成；不得全局复活失败、用户暂停、历史暂停或旧窗口暂停项。暂停原因必须区分。
3. 普通 Viewer 退出后停止扩窗，已接纳任务可完成。切图暂停旧窗口尚未开始项，新窗口可以接纳自动暂停项；运行中的单张原生推理不强行中断。
4. 当前图片独立最高优先级，实际进入推理后才放行邻图；当前图已缓存时可直接开放。A/B 前后工人保持前二后二、前四后四、前八后八。自动档某侧遇到缓存/已入队/视频/越界可从另一侧同档范围补位，随后继续原侧；不得超出来源前八后八，手动档不反向补位，视频不参加图片预加载。
5. 删除推进 GenerationGuard 修订 token；新结果先写扫描范围外 staging，只有完整且 token 有效才提交文件、索引、READY 和进度，旧结果不能回写。禁止恢复 v1.1.00 的全局删除预加载黑名单；删除后正常来源浏览仍可取得新 token。
6. GPU delegate 创建、推理、释放必须属于同一拥有者线程。保留用户要求的强制 GPU，不能私自 CPU fallback。启动只准备已下载的当前图片会话，不下载全部模型、不运行测试推理、不产生队列。
7. 双眼缩放/拖动使用同一眼内坐标；视频为单个 SBS 播放源，保留原始 PTS、音频和有界流水线。快速切换、旋转、跨中线、持续按住拖动必须分别真机验收。
8. LEGACY_V6 / CENTERED_V7 可切换，保留旧版非对称取样和幅度行为，depthV6 / depthV7 独立缓存，不能用“数学更标准”代替回归验证。历史文档冲突以 v1.1.01 及 v1.2.00 为准。
9. 模型仍为 518×518；滤波窗口不是插值次数。深度放大默认关闭；开启默认双线性、先平滑后放大，也支持最近邻/Catmull-Rom 和相反顺序，受实际解码尺寸/内存预算约束。投影用浮点深度，PNG 仅诊断；关闭保持旧缓存键，开启追加 `_du1_...`，视频队列持久化相关参数。
10. 图片/视频中间分眼 JPEG 100 仍有损，兼容旧缓存；导出无损 AVIF 失败不能偷换 JPEG。多相册重生成按当前设置对原图去重，只清理当前目标参数版本，保留其他版本。全选只限当前列表，操作栏/通知/版本随列表滚动规则保持。

## 验证与备份边界

恢复基线有 7 类、58 项 JVM 测试；v1.3.00 为 9 类、77 项，v1.3.01 为 10 类、81 项。实际通过数、Lint 数量、APK 哈希和签名以本次运行的 `.local/verification/runs/*/result.json` 为证，历史记录不能充当新验收。构建通过与真机通过分开记录。

`.local/recovery/` 是完整旧备份的逐文件校验副本，保持不变；`.local/toolchain/` 是日常可写工具链。普通版的 bundle、含未提交改动的快照、旧工作副本、3.0.08 APK、共享签名/模型、v2.32 基准及技术记录均需保留。Pro 恢复完成不代表整个旧备份可删；删除条件见恢复验收记录。

## v1.3.00 UI 与参考模式

当前还需先读 `docs/comfy-main-v1.4.00.md` 与 `docs/releases/v1.4.00.md`。COMFY_LEGACY_V10 / COMFY_SUBPIXEL_V11 对应 depthSSSMainV10 / depthSSSMainV11，均不自动迁移旧偏好、缓存或队列。主节点与 External 的眼序、强度单位、反转约定不同；测试预设为 60 / 3 / 反转开启、额外放大关闭。亚像素为本项目改良，不冒充官方版本或保证所有素材更优；CPU 投影更慢。原 58 项基线仍保留，当前新增测试以实际构建报告为准。Debug `kind=comfyRaster/comfyPipeline` 仅直接读写 `PVG_QA_comfy_1400`，不启动 Activity/ViewModel/MediaStore；使用缓冲流写浮点证据，PNG 对照导出标准 sRGB 像素，不能将色彩配置差异当作投影误差。

当前修正及证据还应先读 docs/studio-v1.3.01.md、docs/releases/v1.3.01.md。REFERENCE_V9 独立缓存 depthSSS22V9；当前参考设置从 V8 转 V9，旧缓存及已持久化的视频队列仍按原版本。不要把旧队列恢复改为偏好迁移入口。最终眼图浮点恢复/3×3 Gaussian 在量化前执行；V6/V7/V8 不变。参考 QA 可用 Debug kind=referenceRaster，只读写 PVG_QA_reference_1301 专属目录，不经过 MediaStore；所有私人素材及 .codex-attachments 均禁止提交。

- 深浅跟随系统，统一 StudioTheme；手机/平板按可用宽度和字体缩放计算显示列数，不改用户保存的列数偏好。列表标题/提示整体随滚动移出，浮动操作槽以实测高度给内容留底部空间。
- v1.3.00 引入的 REFERENCE_V8 仍可显式选择，cacheVersion 为 depthSSS22V8；当前参考设置使用上文 V9 修正。两者均采用左眼原图、右眼相对图宽投影及 Telea 3px 补洞；强度单位不同，建议 5。不开旧滤波与旧水平填充，保留原模型和可选深度放大。V6/V7 的当前选择、旧结果及已持久化任务不迁移。
- 参考模式的额外原生缓冲计入自身内存预算，并串行限制作补洞分配；不能改变 GPU 拥有者线程或强制 GPU。
- 正式发布使用 Build.ps1 -Variant Release，仍显式签原 debug 证书，但 APK 不可调试、无 Debug instrumentation。外部资产名继续 app-debug.apk，兼容所有历史更新器。不得用 CI 临时密钥发布。
- Debug 专用 StudioGestureInstrumentation 只接受明确的 PVG_QA_ 测试相册/素材；正式包不含该入口。私人验收素材与截图仅存 .local/，不能提交。
- 真机发现并修复生成网格长按后抬手触发点击、取消选中的问题；不能删除接管静止 MOVE/UP 的逻辑。kind=selection 回归先核验版本仅含 QA 六项，再检查长按保持、首次拖选三项、全选六项与取消。
- 真机 QA 必须先保存/隔离原队列并核验来源与媒体 ID 差集。本次曾生成 27 条非指定相册结果，经用户另行授权清理；原因不能仅凭核心代码未变推断。详见技术记录，不把测试范围外溢写成未发生。
- 新更新器在安装前核查包名、单一原证书、预期版本及递增 versionCode。旧原生库的 16 KB 对齐限制仍需单独迁移验证，不用 Release 构建不显示调试提示来宣称修复。
