package com.local.parallelvrgallerypro

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

private data class SettingsNavItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val badgeColor: Color,
)

@Composable
private fun StudioSettingsPager(
    items: List<SettingsNavItem>,
    selected: String,
    onSelect: (String) -> Unit,
    header: @Composable () -> Unit,
    content: LazyListScope.(String) -> Unit,
) {
    val apple = AppleTheme.colors
    val windowMetrics = rememberStudioWindowMetrics()
    val keys = remember(items) { items.map { it.key } }
    val options = remember(items) { items.map { it.key to it.label } }

    Box(
        Modifier
            .fillMaxSize()
            .background(apple.groupedBackground)
            .studioSafeArea()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (windowMetrics.isSplitPane) {
            // Authentic iPadOS Settings Split View on wide tablets (>= 840dp)
            Row(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(264.dp)
                        .fillMaxHeight()
                        .background(apple.cardBackground.copy(alpha = if (apple.isDark) 0.58f else 0.78f))
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    header()
                    Spacer(Modifier.height(10.dp))
                    items.forEach { nav ->
                        val active = nav.key == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (active) apple.systemBlue.copy(alpha = if (apple.isDark) 0.25f else 0.13f)
                                    else Color.Transparent,
                                )
                                .clickable { onSelect(nav.key) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(nav.badgeColor),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(nav.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                            }
                            Text(
                                text = nav.label,
                                color = if (active) apple.systemBlue else apple.labelPrimary,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Box(Modifier.width(0.5.dp).fillMaxHeight().background(apple.separator))
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    LinkedPager(selected, keys, onSelect, Modifier.widthIn(max = 680.dp).fillMaxSize()) { page ->
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) { content(page) }
                    }
                }
            }
        } else {
            val hPad = if (windowMetrics.isTablet) 24.dp else 16.dp
            Column(Modifier.widthIn(max = 680.dp).fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(apple.headerBackdrop),
                ) {
                    Box(Modifier.padding(horizontal = hPad, vertical = 10.dp)) { header() }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = hPad, end = hPad, bottom = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        RoundedPillTabs(
                            options = options,
                            selected = selected,
                            onSelect = onSelect,
                            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
                        )
                    }
                    AppleDivider()
                }
                LinkedPager(selected, keys, onSelect, Modifier.weight(1f).fillMaxWidth()) { page ->
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = hPad, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) { content(page) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(
    fromDebug: Boolean = false,
    initialSection: String = "general",
    settings: AppSettings,
    modelWarmupStatus: String?,
    updateStatus: String?,
    updateUrl: String?,
    updateAvailable: Boolean,
    updateDownloading: Boolean,
    updateDownloadProgress: Float,
    updateApkPath: String?,
    onBack: () -> Unit,
    onChange: (AppSettings) -> Unit,
    onSaveDefault: () -> Boolean,
    onResetDefault: () -> Unit,
    onFactoryDefault: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    val apple = AppleTheme.colors
    val lang = settings.language
    val context = LocalContext.current
    val qnnBackendAvailable = remember(context) { QnnFp16DepthRuntime.hasGpuBackend(context) }
    var section by rememberSaveable(initialSection) { mutableStateOf(initialSection) }
    var defaultSaved by remember(settings.edgeMode) { mutableStateOf(false) }
    var defaultSaveFailed by remember(settings.edgeMode) { mutableStateOf(false) }
    LaunchedEffect(settings) { defaultSaved = false; defaultSaveFailed = false }

    val navItems = remember(lang, apple) {
        listOf(
            SettingsNavItem("general", lang.t("通用", "General"), Icons.Rounded.Tune, apple.systemBlue),
            SettingsNavItem("depth", lang.t("深度与输出", "Depth & output"), Icons.Rounded.ViewInAr, Color(0xFF5856D6)),
            SettingsNavItem("images", lang.t("图片", "Images"), Icons.Rounded.PhotoLibrary, apple.systemGreen),
            SettingsNavItem("video", lang.t("视频", "Video"), Icons.Rounded.VideoLibrary, apple.systemOrange),
        )
    }

    StudioSettingsPager(
        items = navItems,
        selected = section,
        onSelect = { section = it },
        header = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppleGlassCircleButton(
                    icon = Icons.Rounded.ChevronLeft,
                    contentDescription = if (fromDebug) lang.t("返回调试", "Back to diagnostics") else lang.t("返回", "Back"),
                    onClick = onBack,
                    size = 36.dp,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(lang.t("创作设置", "Studio settings"), style = MaterialTheme.typography.titleLarge, color = apple.labelPrimary, fontWeight = FontWeight.Bold)
                    Text(lang.t("参数仅影响新任务，已有结果保留", "New jobs use these settings; existing results stay"), style = MaterialTheme.typography.labelSmall, color = apple.labelSecondary)
                }
            }
        },
    ) { page ->
        when (page) {
            "general" -> {
                item {
                    AppleGroupedList(
                        title = lang.t("偏好与权限", "Preferences & access"),
                        trailingHeader = lang.t("跟随系统深浅模式", "Follows system appearance"),
                    ) {
                        SettingChoice(lang.t("语言", "Language"), settings.language, AppLanguage.entries.toList(), { if (it == AppLanguage.ZH) "中文" else "English" }) { onChange(settings.copy(language = it)) }
                        AppleDivider(startIndent = 16.dp)
                        AppleInfoRow(
                            title = lang.t("媒体访问状态", "Media permissions"),
                            value = lang.t(
                                "图片${if (hasImagePermission(context)) "已允许" else "未允许"} · 视频${if (hasVideoPermission(context)) "已允许" else "未允许"}",
                                "Photos ${if (hasImagePermission(context)) "OK" else "off"} · Videos ${if (hasVideoPermission(context)) "OK" else "off"}",
                            ),
                        )
                        AppleDivider(startIndent = 16.dp)
                        AppleActionRow(
                            title = lang.t("管理媒体与通知权限", "Manage media & notifications"),
                            onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) },
                        )
                    }
                }
                item {
                    AppleGroupedList(
                        title = lang.t("应用更新", "App updates"),
                        trailingHeader = "$CURRENT_VERSION_TAG · $CURRENT_VERSION_CODE",
                        footer = {
                            SettingHelp(lang.t("覆盖更新会保留原有媒体、设置与生成结果；不要卸载旧版。", "Update in place to keep media, settings and generated results. Do not uninstall."))
                        },
                    ) {
                        updateStatus?.let {
                            Text(
                                lang.pickMixed(it),
                                style = MaterialTheme.typography.bodyMedium,
                                color = apple.labelPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                            AppleDivider(startIndent = 16.dp)
                        }
                        if (updateDownloading) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (updateDownloadProgress < 0) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.5.dp), color = apple.systemBlue, trackColor = apple.fillSecondary, strokeCap = StrokeCap.Round)
                                } else {
                                    LinearProgressIndicator(progress = { updateDownloadProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.5.dp), color = apple.systemBlue, trackColor = apple.fillSecondary, strokeCap = StrokeCap.Round, drawStopIndicator = {})
                                    Text("${(updateDownloadProgress * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = apple.labelSecondary)
                                }
                            }
                            AppleDivider(startIndent = 16.dp)
                        }
                        AppleActionRow(
                            title = lang.t("检查更新", "Check for updates"),
                            enabled = !updateDownloading,
                            onClick = onCheckUpdate,
                        )
                        if (updateAvailable && updateApkPath == null && !updateDownloading && updateUrl != null) {
                            AppleDivider(startIndent = 16.dp)
                            AppleActionRow(title = lang.t("下载更新", "Download update"), onClick = onDownloadUpdate)
                        }
                        if (updateApkPath != null && !updateDownloading) {
                            AppleDivider(startIndent = 16.dp)
                            AppleActionRow(title = lang.t("安装更新", "Install update"), onClick = onInstallUpdate)
                        }
                        AppleDivider(startIndent = 16.dp)
                        AppleActionRow(
                            title = lang.t("GitHub 项目", "GitHub project"),
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$GITHUB_REPO")))
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, lang.t("没有可打开 GitHub 链接的应用", "No app can open the GitHub link"), Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }
                item {
                    AppleGroupedList(
                        title = lang.t("后台生成", "Background generation"),
                        footer = {
                            SettingHelp(lang.t("运行中的整批任务保持前台通知与唤醒锁；普通系统进程回收后，只尝试恢复明确开始的视频。暂停、失败与历史任务不会自动开始。厂商清理、强行停止、重启或系统后台时限仍可能中止；重新打开后可从生成页续跑。部分设备还需在最近任务中锁定应用。", "Active work holds a foreground notification and wake lock. After ordinary system process reclamation only explicitly started videos may resume. Paused, failed and historical jobs stay stopped. Vendor cleanup, Force stop, reboot and system time limits can still interrupt work; reopen Generated to resume."))
                        },
                    ) {
                        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                        AppleInfoRow(
                            title = lang.t("电池优化", "Battery optimization"),
                            value = if (power.isIgnoringBatteryOptimizations(context.packageName)) lang.t("已不受限制", "Unrestricted") else lang.t("仍受系统管理", "System managed"),
                        )
                        AppleDivider(startIndent = 16.dp)
                        AppleActionRow(
                            title = lang.t("打开系统应用设置", "Open system app settings"),
                            onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) },
                        )
                    }
                }
                item {
                    AppleGroupedList(
                        title = lang.t("开始创作", "Getting started"),
                        footer = {
                            SettingHelp(lang.t("生成缓存不等于修改原图。删除与重新生成会明确说明作用范围。GPU 和画质以设备实际结果为准。", "Generating cache does not change originals. Delete and regenerate actions explain their scope. GPU and visual results depend on the device."))
                        },
                    ) {
                        Text(
                            lang.t(
                                "1  从全部或相册打开图片会自动开启 VR，可用 Viewer 开关关闭。\n2  当前图片先生成，再按该来源前后预加载；视频需要手动加入队列。\n3  在生成页查看参数版本、任务和结果，长按可多选。\n4  Viewer 中双击复位、双指缩放，左右眼保持同步。",
                                "1  Opening a photo from All or Albums enables VR; use its Viewer switch to turn it off.\n2  The current photo starts first, then nearby photos in that source. Videos are queued manually.\n3  Generated holds parameter versions, jobs and results. Long press to select.\n4  Double tap to reset and pinch to zoom; both eyes stay synchronized.",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = apple.labelPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        )
                    }
                }
            }
            "depth" -> {
                item {
                    val modeFootnote = buildString {
                        append(lang.t("各模式独立保存视觉参数，切换自动恢复。默认强度：V6 100、V7 45、V8/V9 3.5、V10/V11 50；点击数值可修改。", "Each mode remembers its visual controls (defaults: V6 100, V7 45, V8/V9 3.5, V10/V11 50). Tap numeric values to edit."))
                        append("\n")
                        when {
                            settings.edgeMode == DepthEdgeMode.COMFY_SUBPIXEL_V11 -> append(lang.t("亚像素保留浮点位移，按深度和覆盖率处理轮廓及补洞，边缘更平滑但计算较慢；切换不会重做旧结果。", "Subpixel retains floating shifts and uses depth/coverage for edges and filling. Smoother edges, slower CPU; old results stay."))
                            settings.edgeMode.isComfyMain -> append(lang.t("对照 ComfyUI 主节点 Legacy：左眼投影、右眼原图，最终眼图深度双三次恢复与 Gaussian，基准强度 50、窗口 3。", "Matches ComfyUI main Legacy: projected left eye, original right eye, cubic restoration + eye-size Gaussian (default scale 50, window 3)."))
                            settings.edgeMode == DepthEdgeMode.REFERENCE_V9 -> append(lang.t("先连续恢复浮点深度，再投影并用 Telea 3px 补洞。强度 5 约为图宽 1%。", "Restores continuous float depth before projection and Telea 3px filling. Scale 5 is ~1% width."))
                            settings.edgeMode.isReference -> append(lang.t("参考外部深度 V2.2：左眼原图，右眼按图宽比例右移并用 Telea 3px 修补。默认 3.5 近似 V10 50。", "Upstream external-depth V2.2: static left eye, width-relative right-eye projection + Telea 3px fill. Default 3.5 approximates V10 50."))
                            else -> append(lang.t("旧版保留历史非对称取样与位移幅度；居中模式相同强度下可能产生更大视差。缓存独立保存。", "Legacy keeps historical asymmetric sampling; Centered may yield wider disparity. Caches stay separate."))
                        }
                    }
                    AppleGroupedList(
                        title = lang.t("边缘与视差", "Edges & disparity"),
                        footer = { SettingHelp(modeFootnote) },
                    ) {
                        SettingChoice(
                            lang.t("投影与边缘模式", "Projection & edge mode"),
                            settings.edgeMode,
                            listOf(DepthEdgeMode.COMFY_LEGACY_V10, DepthEdgeMode.LEGACY_V6, DepthEdgeMode.COMFY_SUBPIXEL_V11, DepthEdgeMode.REFERENCE_V9, DepthEdgeMode.CENTERED_V7, DepthEdgeMode.REFERENCE_V8),
                            {
                                when (it) {
                                    DepthEdgeMode.LEGACY_V6 -> lang.t("旧版兼容 · depthV6", "Legacy · depthV6")
                                    DepthEdgeMode.CENTERED_V7 -> lang.t("居中平滑 · depthV7", "Centered · depthV7")
                                    DepthEdgeMode.REFERENCE_V8 -> lang.t("参考旧结果 · 1.3.00", "Reference · 1.3.00")
                                    DepthEdgeMode.REFERENCE_V9 -> lang.t("参考新版 · 平滑轮廓", "Reference · smooth contours")
                                    DepthEdgeMode.COMFY_LEGACY_V10 -> lang.t("ComfyUI 主节点 · 原样兼容", "ComfyUI main · compatible")
                                    DepthEdgeMode.COMFY_SUBPIXEL_V11 -> lang.t("ComfyUI 主节点 · 亚像素", "ComfyUI main · subpixel")
                                }
                            },
                        ) { onChange(settings.copy(edgeMode = it)) }
                        AppleDivider(startIndent = 16.dp)
                        SettingFloat(lang.t("深度强度", "Depth scale"), settings.depthScale, if (settings.edgeMode.isReference || settings.edgeMode.isComfyMain) 100f else 500f) { onChange(settings.copy(depthScale = it, matchV10Default = false)) }
                        if (!settings.edgeMode.isReference) {
                            AppleDivider(startIndent = 16.dp)
                            SettingInt(if (settings.edgeMode.isComfyMain) lang.t("最终深度平滑窗口", "Final depth Gaussian window") else lang.t("深度图滤波窗口", "Depth filter window"), settings.blurRadius, listOf(0, 1, 3, 5, 9, 15, 25)) { onChange(settings.copy(blurRadius = it)) }
                            if (!settings.edgeMode.isComfyMain) {
                                AppleDivider(startIndent = 16.dp)
                                SettingInt(lang.t("边缘填充（输出像素）", "Edge fill (output pixels)"), settings.fillRadius, listOf(0, 3, 5, 10, 15, 20, 30)) { onChange(settings.copy(fillRadius = it)) }
                            }
                        }
                        AppleDivider(startIndent = 16.dp)
                        val depthReversed = DepthDirection.reversed(settings.edgeMode, settings.invertModelDepth, settings.invertDepth)
                        StudioSwitch(
                            lang.t("反转深度", "Invert depth"),
                            lang.t("黑白互换，同时交换前后景", "Swap near/far depth in preview and stereo output"),
                            depthReversed,
                            inGroupedList = true,
                        ) {
                            onChange(settings.copy(invertModelDepth = it, invertDepth = ModeVisualProfile.defaults(settings.edgeMode).invert))
                        }
                        AppleDivider(startIndent = 16.dp)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppleCapsuleButton(text = lang.t("保存为默认值", "Save as default"), onClick = { defaultSaved = onSaveDefault(); defaultSaveFailed = !defaultSaved })
                            AppleCapsuleButton(text = lang.t("恢复此模式默认值", "Restore this mode's defaults"), primary = false, onClick = onResetDefault)
                            TextButton(onClick = onFactoryDefault) { Text(lang.t("恢复内置默认", "Restore built-in defaults"), color = apple.labelSecondary) }
                        }
                        if (defaultSaved) {
                            Text(
                                lang.t("已保存当前模式的视觉参数", "Saved this mode's visual controls"),
                                style = MaterialTheme.typography.bodySmall,
                                color = apple.systemGreen,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                            )
                        }
                        if (defaultSaveFailed) {
                            Text(
                                lang.t("默认值保存失败，请检查存储空间", "Could not save defaults; check device storage"),
                                style = MaterialTheme.typography.bodySmall,
                                color = apple.systemRed,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                            )
                        }
                    }
                }
                item {
                    DepthUpscaleControls(settings.depthUpscale, lang, settings.edgeMode.isComfyMain) { onChange(settings.copy(depthUpscale = it)) }
                }
                item {
                    AppleGroupedList(
                        title = lang.t("SBS 成品尺寸", "SBS output size"),
                        footer = {
                            SettingHelp(lang.t("日常预览使用 JPEG 100 缓存。保存副本到图库时会读取结果记录的模型、模式与参数，从原图重新推理并直接写出无损 PNG（保持记录尺寸，不改当前设置或队列）。视频保存直接复制已生成 MP4。", "Preview uses JPEG 100. Saving a copy re-renders the original using recorded parameters directly to lossless PNG without altering settings or queues. Video save copies the generated MP4."))
                        },
                    ) {
                        SettingInt(lang.t("单眼最大长边", "Maximum eye long edge"), settings.maxLongEdge, listOf(1280, 1920, 2048, 3072, 4096, 6000)) { onChange(settings.copy(maxLongEdge = it)) }
                        AppleDivider(startIndent = 16.dp)
                        AppleInfoRow(
                            title = lang.t("图库保存格式", "Gallery export"),
                            value = lang.t("原图重算 · 无损 PNG", "Re-rendered lossless PNG"),
                        )
                    }
                }
            }
            "images" -> {
                item {
                    ModelPicker(
                        sectionTitle = lang.t("图片模型", "Image model"),
                        statusBadge = modelWarmupStatus?.let(lang::pickMixed),
                        selectedModelId = settings.imageModelId,
                        lang = lang,
                        models = ImageModels,
                        qnnBackendAvailable = qnnBackendAvailable,
                    ) { onChange(settings.copy(imageModelId = it.id, depthResolution = it.inputSize)) }
                }
                item {
                    AppleGroupedList(
                        title = lang.t("来源预加载", "Source prefetch"),
                        footer = {
                            SettingHelp(lang.t("当前图优先，邻图仅来自进入时的全部/相册范围。自动档按 2→4→8 优先连续翻页方向；生成页不扩展预加载。GPU 推理仍在拥有者线程串行运行。", "Current image first within the captured All/Album scope. Auto tiers 2→4→8 favor travel direction. Generated never expands prefetch; GPU inference stays serialized on its owner thread."))
                        },
                    ) {
                        SettingChoice(lang.t("前后范围", "Neighbor range"), if (settings.autoPrefetch) "auto" else settings.prefetchWindow.toString(), listOf("auto", "2", "4", "8"), { if (it == "auto") lang.t("自动 · 2 → 4 → 8", "Auto · 2 → 4 → 8") else lang.t("前后各 $it", "$it each side") }) {
                            onChange(settings.copy(autoPrefetch = it == "auto", prefetchWindow = if (it == "auto") 2 else it.toInt()))
                        }
                        AppleDivider(startIndent = 16.dp)
                        SettingInt(lang.t("后台 worker 数", "Background workers"), settings.generationWorkers, listOf(1, 2, 3)) { onChange(settings.copy(generationWorkers = it)) }
                    }
                }
                item {
                    AppleGroupedList(
                        title = lang.t("图片推理", "Image inference"),
                        footer = {
                            if (qnnModelConfig(settings.imageModelId) != null) {
                                SettingHelp(lang.t("QNN 固定使用 GPU，失败会明确报错，不自动回退 CPU。", "QNN uses GPU only; failures are reported explicitly without CPU fallback."))
                            } else {
                                SettingHelp(lang.t("GPU 模式供图片和视频共用。解释器与 delegate 的创建、推理、释放都在同一拥有者线程；关闭 GPU 才使用 CPU。", "GPU mode is shared by images and video. Interpreter and delegate share one owner thread; disable GPU to run on CPU."))
                            }
                        },
                    ) {
                        if (qnnModelConfig(settings.imageModelId) != null) {
                            AppleInfoRow(lang.t("执行后端", "Execution backend"), "QNN GPU · Strict")
                        } else {
                            SettingInt(lang.t("每会话 CPU 线程", "CPU threads per session"), settings.modelThreads, listOf(1, 2, 4, 8)) { onChange(settings.copy(modelThreads = it)) }
                            AppleDivider(startIndent = 16.dp)
                            StudioSwitch(lang.t("强制 GPU", "Force GPU"), lang.t("失败报错，不自动回退 CPU", "Report failure; no silent CPU fallback"), settings.useGpu, inGroupedList = true) { onChange(settings.copy(useGpu = it)) }
                            AppleDivider(startIndent = 16.dp)
                            GpuModePicker(settings.gpuTestMode, lang) { onChange(settings.copy(gpuTestMode = it)) }
                        }
                    }
                }
            }
            "video" -> {
                item {
                    ModelPicker(
                        sectionTitle = lang.t("视频模型", "Video model"),
                        statusBadge = null,
                        selectedModelId = settings.videoModelId,
                        lang = lang,
                        models = VideoModels,
                        qnnBackendAvailable = qnnBackendAvailable,
                    ) { onChange(settings.copy(videoModelId = it.id)) }
                }
                item {
                    AppleGroupedList(
                        title = lang.t("视频流水线", "Video pipeline"),
                        trailingHeader = lang.t("独立排队", "Separate queue"),
                        footer = {
                            SettingHelp(lang.t("继续已有任务沿用原型号、尺寸和比例模式。解码、推理、写入、编码为有界流水线，保留原始时间线与音频。", "Resuming keeps each job's original model and geometry. Bounded pipeline preserves original timestamps and audio."))
                        },
                    ) {
                        if (qnnModelConfig(settings.videoModelId) != null) {
                            AppleInfoRow(lang.t("执行后端", "Execution backend"), "QNN GPU · Single worker")
                        } else {
                            SettingInt(lang.t("每会话 CPU 线程", "CPU threads per session"), settings.videoModelThreads, listOf(1, 2, 4, 8)) { onChange(settings.copy(videoModelThreads = it)) }
                            AppleDivider(startIndent = 16.dp)
                            SettingInt(lang.t("深度 worker 数", "Depth workers"), settings.videoDepthWorkers, listOf(1, 2)) { onChange(settings.copy(videoDepthWorkers = it)) }
                            AppleDivider(startIndent = 16.dp)
                            StudioSwitch(lang.t("视频强制 GPU", "Force GPU for video"), lang.t("GPU 单会话；失败不回退 CPU", "Single GPU session; no CPU fallback"), settings.videoUseGpu, inGroupedList = true) { onChange(settings.copy(videoUseGpu = it)) }
                            AppleDivider(startIndent = 16.dp)
                            GpuModePicker(settings.gpuTestMode, lang) { onChange(settings.copy(gpuTestMode = it)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleInfoRow(title: String, value: String) {
    val apple = AppleTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = apple.labelPrimary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = apple.labelSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AppleActionRow(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    val apple = AppleTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) apple.systemBlue else apple.labelTertiary,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = apple.labelTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun SettingHelp(text: String) {
    val apple = AppleTheme.colors
    var showDetails by remember { mutableStateOf(false) }
    var clipped by remember(text) { mutableStateOf(false) }
    val chinese = text.any { it in '\u4e00'..'\u9fff' }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = apple.labelSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (it.hasVisualOverflow) clipped = true },
        )
        if (text.length > 75 || clipped) {
            IconButton(onClick = { showDetails = true }, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Outlined.Info, if (chinese) "参数说明" else "Parameter details", tint = apple.systemBlue, modifier = Modifier.size(16.dp))
            }
        }
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            containerColor = apple.elevatedSurface,
            shape = RoundedCornerShape(22.dp),
            title = { Text(if (chinese) "参数说明" else "Parameter details", fontWeight = FontWeight.Bold) },
            text = { Text(text, Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), color = apple.labelSecondary) },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text(if (chinese) "知道了" else "Got it", color = apple.systemBlue, fontWeight = FontWeight.SemiBold) } },
        )
    }
}

@Composable
private fun GpuModePicker(selected: GpuTestMode, lang: AppLanguage, onSelect: (GpuTestMode) -> Unit) {
    val options = listOf(
        GpuTestMode.AUTO to lang.t("自动尝试全部", "Auto try all"),
        GpuTestMode.ACCURATE_UNSET to lang.t("精确模式", "Accurate"),
        GpuTestMode.ACCURATE_OPENGL to lang.t("强制 OpenGL", "Force OpenGL"),
        GpuTestMode.ACCURATE_OPENCL to lang.t("强制 OpenCL", "Force OpenCL"),
        GpuTestMode.BEST_COMPAT to lang.t("系统推荐", "System best"),
    )
    SettingChoice(lang.t("GPU 后端（图片/视频共用）", "GPU backend (shared)"), selected, options.map { it.first }, { mode -> options.first { it.first == mode }.second }, onSelect)
}

@Composable
private fun ModelPicker(
    sectionTitle: String,
    statusBadge: String?,
    selectedModelId: String,
    lang: AppLanguage,
    models: List<ModelSpec>,
    qnnBackendAvailable: Boolean,
    onSelect: (ModelSpec) -> Unit,
) {
    val apple = AppleTheme.colors
    val families = remember(models) { models.groupBy(ModelSpec::selectionFamily) }
    val activeModelId = activeDepthModelId(selectedModelId)
    val selected = models.firstOrNull { it.id == activeModelId }
    val selectedFamily = selected?.selectionFamily()
    val selectedQnn = qnnModelConfig(activeModelId)

    val footnote = buildString {
        if (selectedQnn != null && qnnBackendAvailable) {
            when (selectedQnn.aspect) {
                QnnDepthAspect.SQUARE -> append(lang.t("固定按方形尺寸推理。", "Always uses a square input."))
                QnnDepthAspect.WIDE_TALL -> append(lang.t("横图/方图缩放到 16:9，竖图缩放到 9:16。", "Landscape/square resize to 16:9; portrait to 9:16."))
                QnnDepthAspect.PORTRAIT_PAIR -> append(lang.t("横向素材旋转 90° 后按 9:16 或 3:4 推理，深度图再转回原方向。", "Landscape media rotates 90° into 9:16 or 3:4 GPU graphs, then rotates back."))
                QnnDepthAspect.ALIGNED, QnnDepthAspect.FIT -> append(lang.t("按素材比例分为整数 × 14 像素格，受像素预算上限约束。", "Sized by aspect in 14-pixel patches within the pixel budget."))
            }
            if (selectedQnn.variant == QnnDepthVariant.FP32_HEAD) {
                append(" ")
                append(lang.t("最后三层深度卷积使用 FP32，主体使用 FP16。", "Last three depth convolutions run FP32; body runs FP16."))
            }
            append(" ")
        }
        append(lang.t("QNN 模型随应用提供，其他可用模型首次生成时下载。", "QNN models are bundled; others download on first generation."))
    }

    AppleGroupedList(
        title = sectionTitle,
        trailingHeader = statusBadge,
        footer = { SettingHelp(footnote) },
    ) {
        val entries = families.entries.toList()
        entries.forEachIndexed { idx, (family, variants) ->
            val spec = variants.first()
            val variant = qnnModelConfig(spec.id)?.variant
            val label = when {
                variant == QnnDepthVariant.FP16 -> "Depth Anything V2 · QNN GPU FP16"
                variant == QnnDepthVariant.FP32_HEAD -> lang.t("Depth Anything V2 · QNN GPU 末端 FP32", "Depth Anything V2 · QNN GPU FP32 head")
                family == DEFAULT_TFLITE_MODEL_ID -> "Depth Anything V2 · TFLite"
                else -> spec.displayName
            }
            val qnnUnavailable = variant != null && !qnnBackendAvailable
            val disabled = !spec.downloadable || qnnUnavailable
            val active = !disabled && family == selectedFamily
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .clickable(enabled = !disabled) {
                        if (!active) {
                            val aspect = selectedQnn?.aspect ?: QnnDepthAspect.SQUARE
                            onSelect(
                                variants.firstOrNull {
                                    it.inputSize == selected?.inputSize && (variant == null || qnnModelConfig(it.id)?.aspect == aspect)
                                } ?: spec,
                            )
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        color = if (disabled) apple.labelTertiary else apple.labelPrimary,
                    )
                    if (disabled) {
                        val reason = if (qnnUnavailable) lang.t("当前 Android 进程无法加载 QNN GPU", "QNN GPU is unavailable in this Android process") else lang.t(spec.noteZh, spec.noteEn)
                        Text(reason, style = MaterialTheme.typography.labelSmall, color = apple.labelTertiary)
                    }
                }
                if (active) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = apple.systemBlue, modifier = Modifier.size(20.dp))
                }
            }
            if (idx < entries.lastIndex) AppleDivider(startIndent = 16.dp)
        }

        if (selected != null && selectedFamily != null && selected.downloadable) {
            val variants = families.getValue(selectedFamily)
            val size = selected.inputSize
            AppleDivider(startIndent = 16.dp)
            when {
                selectedQnn != null && qnnBackendAvailable -> {
                    fun select(config: QnnModelConfig) = onSelect(variants.first { it.id == qnnModelId(config) })
                    SettingChoice(
                        lang.t("输入比例", "Input aspect ratio"),
                        selectedQnn.aspect,
                        listOf(QnnDepthAspect.SQUARE, QnnDepthAspect.ALIGNED, QnnDepthAspect.PORTRAIT_PAIR),
                        {
                            when (it) {
                                QnnDepthAspect.SQUARE -> lang.t("固定方形", "Fixed square")
                                QnnDepthAspect.FIT -> lang.t("等比例补齐", "Aspect padding")
                                QnnDepthAspect.ALIGNED -> lang.t("14 像素分块", "14-pixel patches")
                                QnnDepthAspect.WIDE_TALL -> lang.t("固定横竖屏（16:9 / 9:16）", "Fixed landscape / portrait (16:9 / 9:16)")
                                QnnDepthAspect.PORTRAIT_PAIR -> lang.t("双竖版（9:16 / 3:4）", "Two portrait shapes (9:16 / 3:4)")
                            }
                        },
                    ) {
                        select(selectedQnn.copy(aspect = it))
                    }
                    AppleDivider(startIndent = 16.dp)
                    QnnResolutionEditor(size, selectedQnn.aspect, lang) { select(selectedQnn.copy(size = it)) }
                }
                variants.size > 1 && selectedQnn == null -> SettingChoice(
                    lang.t("推理分辨率", "Inference resolution"),
                    size,
                    variants.map { it.inputSize },
                    { "${it}×$it" },
                ) { newSize -> onSelect(variants.first { it.inputSize == newSize }) }
                selectedQnn?.aspect == QnnDepthAspect.PORTRAIT_PAIR -> {
                    AppleInfoRow(lang.t("推理分辨率", "Inference resolution"), qnnFixedPairSizes(size, selectedQnn.aspect))
                }
                else -> AppleInfoRow(lang.t("推理分辨率", "Inference resolution"), "${size}×$size")
            }
        }
    }
}

private fun ModelSpec.selectionFamily(): String {
    val variant = qnnModelConfig(id)?.variant
    if (variant != null) return qnnModelId(QnnModelConfig(variant, QNN_FP16_MODEL_SIZE, QnnDepthAspect.SQUARE))
    return if (id == HIGH_TFLITE_MODEL_ID) DEFAULT_TFLITE_MODEL_ID else id
}

internal fun qnnFixedPairSizes(size: Int, aspect: QnnDepthAspect): String =
    QnnInputGeometry.warmupGeometries(size, aspect).joinToString(" / ") { "${it.tensorWidth}×${it.tensorHeight}" }

/** Clean Apple inline numeric editor (no Material 3 floating-notch outline). */
@Composable
private fun QnnResolutionEditor(size: Int, aspect: QnnDepthAspect, lang: AppLanguage, onSelect: (Int) -> Unit) {
    val apple = AppleTheme.colors
    var input by rememberSaveable(size) { mutableStateOf((size / QNN_FP16_PATCH_SIZE).toString()) }
    val focus = LocalFocusManager.current
    val tiles = input.toIntOrNull()?.takeIf { it in QNN_FP16_MIN_TOKENS..QNN_FP16_MAX_TOKENS }
    val pendingSize = tiles?.times(QNN_FP16_PATCH_SIZE)
    val range = "$QNN_FP16_MIN_TOKENS–$QNN_FP16_MAX_TOKENS"
    val applySize: () -> Unit = {
        if (pendingSize != null) {
            focus.clearFocus()
            if (pendingSize != size) onSelect(pendingSize)
        }
    }
    val adaptive = aspect != QnnDepthAspect.SQUARE
    val portraitPair = aspect == QnnDepthAspect.PORTRAIT_PAIR
    val title = if (adaptive) lang.t("推理像素预算", "Inference pixel budget") else lang.t("推理分辨率", "Inference resolution")
    val detail = if (pendingSize == null) {
        lang.t("请输入 $range 之间的整数（每块 ${QNN_FP16_PATCH_SIZE}px）", "Enter an integer in $range (${QNN_FP16_PATCH_SIZE}px/patch)")
    } else if (portraitPair) {
        "9:16 / 3:4 · ${qnnFixedPairSizes(pendingSize, aspect)}"
    } else if (adaptive) {
        val pixels = String.format(Locale.US, "%,d", pendingSize * pendingSize)
        "${pendingSize}×${pendingSize} · ${lang.t("上限 $pixels 像素", "up to $pixels px")}"
    } else {
        "${pendingSize}×${pendingSize} px"
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = apple.labelPrimary)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (input.isNotEmpty() && pendingSize == null) apple.systemRed else apple.labelSecondary,
                )
            }
            // Compact Apple filled numeric field
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(apple.fillTertiary)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) input = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = if (input.isNotEmpty() && pendingSize == null) apple.systemRed else apple.labelPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(apple.systemBlue),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { applySize() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AppleCapsuleButton(
                text = lang.t("应用", "Apply"),
                enabled = pendingSize != null && pendingSize != size,
                onClick = applySize,
            )
        }
    }
}

@Composable
private fun DepthUpscaleControls(settings: DepthUpscaleSettings, lang: AppLanguage, fixedFinalGaussian: Boolean = false, onChange: (DepthUpscaleSettings) -> Unit) {
    val apple = AppleTheme.colors
    val footerText = if (!settings.enabled) {
        lang.t("关闭时保持原有深度处理与缓存兼容。", "Off preserves existing depth processing and cache compatibility.")
    } else if (fixedFinalGaussian) {
        lang.t("主节点先执行额外重采样，再恢复到实际单眼尺寸并做最终 Gaussian；不增加模型推理分辨率。", "Main resamples before final eye-size Gaussian; does not increase model inference resolution.")
    } else {
        lang.t("图片与视频共用。放大不增加模型推理分辨率，开启可能增加耗时。", "Shared by images and videos. Does not increase model resolution and may add processing time.")
    }

    AppleGroupedList(
        title = lang.t("深度后处理", "Depth post-processing"),
        footer = { SettingHelp(footerText) },
    ) {
        StudioSwitch(
            title = lang.t("插值放大深度图", "Upsample depth map"),
            detail = "",
            checked = settings.enabled,
            onChange = { onChange(settings.copy(enabled = it)) },
            inGroupedList = true,
        )
        if (!settings.enabled) return@AppleGroupedList
        AppleDivider(startIndent = 16.dp)
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            AppleSegmentedControl(
                options = listOf("source" to lang.t("原图尺寸", "Source size"), "custom" to lang.t("自定上限", "Custom limit")),
                selected = if (settings.boundedLongEdge == 0) "source" else "custom",
                onSelect = { key -> onChange(settings.copy(maxLongEdge = if (key == "source") 0 else 2048)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (settings.boundedLongEdge != 0) {
            AppleDivider(startIndent = 16.dp)
            var value by remember(settings.boundedLongEdge) { mutableStateOf(settings.boundedLongEdge.toString()) }
            val valid = (value.toIntOrNull() ?: 0) in 256..8192
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(lang.t("深度图长边上限（px）", "Depth long-edge limit (px)"), style = MaterialTheme.typography.bodyLarge, color = apple.labelPrimary)
                    Text("256–8192", style = MaterialTheme.typography.bodySmall, color = if (valid) apple.labelSecondary else apple.systemRed)
                }
                Box(
                    modifier = Modifier
                        .width(88.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(apple.fillTertiary)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = { next ->
                            value = next.filter(Char::isDigit).take(5)
                            value.toIntOrNull()?.takeIf { it in 256..8192 }?.let { onChange(settings.copy(maxLongEdge = it)) }
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = if (valid) apple.labelPrimary else apple.systemRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        ),
                        cursorBrush = SolidColor(apple.systemBlue),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        AppleDivider(startIndent = 16.dp)
        SettingChoice(
            lang.t("插值方法", "Interpolation"),
            settings.interpolation,
            DepthInterpolation.entries.toList(),
            { method ->
                when (method) {
                    DepthInterpolation.NEAREST -> lang.t("最近邻", "Nearest")
                    DepthInterpolation.BILINEAR -> lang.t("双线性", "Bilinear")
                    DepthInterpolation.BICUBIC -> lang.t("双三次", "Bicubic")
                }
            },
        ) { onChange(settings.copy(interpolation = it)) }
        if (!fixedFinalGaussian) {
            AppleDivider(startIndent = 16.dp)
            SettingChoice(
                lang.t("处理顺序", "Processing order"),
                settings.order,
                DepthResizeOrder.entries.toList(),
                { order ->
                    when (order) {
                        DepthResizeOrder.SMOOTH_THEN_RESIZE -> lang.t("先平滑，再放大（默认）", "Smooth then resize (default)")
                        DepthResizeOrder.RESIZE_THEN_SMOOTH -> lang.t("先放大，再平滑", "Resize then smooth")
                    }
                },
            ) { onChange(settings.copy(order = it)) }
        }
    }
}

/** Flat borderless iOS Settings disclosure row (NO nested box inside card). */
@Composable
private fun <T> SettingChoice(title: String, value: T, options: List<T>, label: (T) -> String, onChange: (T) -> Unit) {
    val apple = AppleTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = apple.labelPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = label(value),
                style = MaterialTheme.typography.bodyMedium,
                color = apple.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Rounded.UnfoldMore, contentDescription = null, tint = apple.labelTertiary, modifier = Modifier.size(17.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = apple.elevatedSurface,
            shape = RoundedCornerShape(14.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == value
                DropdownMenuItem(
                    text = {
                        Text(
                            label(option),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) apple.systemBlue else apple.labelPrimary,
                        )
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, tint = apple.systemBlue, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = { expanded = false; onChange(option) },
                )
            }
        }
    }
}

@Composable
private fun SettingInt(title: String, value: Int, options: List<Int>, onChange: (Int) -> Unit) {
    SettingChoice(title, value, options, { it.toString() }, onChange)
}

/** Flat borderless iOS numeric row with sleek editable value pill. */
@Composable
private fun SettingFloat(title: String, value: Float, maximum: Float, onChange: (Float) -> Unit) {
    val apple = AppleTheme.colors
    var editing by remember { mutableStateOf(false) }
    var input by remember(value) { mutableStateOf(value.toString()) }
    val formatted = String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clickable { input = value.toString(); editing = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = apple.labelPrimary,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = apple.systemBlue.copy(alpha = if (apple.isDark) 0.20f else 0.10f),
        ) {
            Text(
                text = formatted,
                color = apple.systemBlue,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
            )
        }
    }
    if (editing) {
        val parsed = input.toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..maximum }
        AlertDialog(
            onDismissRequest = { editing = false },
            containerColor = apple.elevatedSurface,
            shape = RoundedCornerShape(22.dp),
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("0–${maximum.toInt()}，可输入小数", style = MaterialTheme.typography.bodySmall, color = if (parsed == null) apple.systemRed else apple.labelSecondary)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(apple.fillTertiary)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            singleLine = true,
                            textStyle = TextStyle(color = apple.labelPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                            cursorBrush = SolidColor(apple.systemBlue),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                AppleCapsuleButton(text = "确定", enabled = parsed != null, onClick = { parsed?.let(onChange); editing = false })
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("取消") } },
        )
    }
}
