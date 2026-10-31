package com.local.parallelvrgallerypro

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import java.util.Locale
import kotlin.math.roundToInt

data class ModeComparisonImage(val mode: DepthEdgeMode, val left: Bitmap, val right: Bitmap,
    val params: VrGenerationParams, val actualScale: Float, val elapsedMs: Long,
    val modelDepth: Bitmap, val projectionDepth: Bitmap)

data class ModeComparisonState(val id: String, val photoKey: String, val name: String,
    val requestedLongEdge: Int, val progress: Float = 0f, val message: String = "",
    val images: List<ModeComparisonImage> = emptyList(), val error: String? = null)

/** All six pairs stay decoded; changing a button only swaps image references.
 * The stereo gesture key belongs to the comparison session, never to a mode.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModeComparisonScreen(state: ModeComparisonState, onBack: () -> Unit,
    onRestart: (Int, Boolean) -> Unit) {
    var selectedName by rememberSaveable(state.id) { mutableStateOf(DepthEdgeMode.COMFY_LEGACY_V10.name) }
    var resolution by rememberSaveable { mutableIntStateOf(state.requestedLongEdge) }
    var viewKind by rememberSaveable(state.id) { mutableStateOf("SBS") }
    var zoom by remember(state.id) { mutableFloatStateOf(1f) }
    var pan by remember(state.id) { mutableStateOf(Offset.Zero) }
    var showHelp by remember { mutableStateOf(false) }
    val config = LocalConfiguration.current
    val selected = state.images.firstOrNull { it.mode.name == selectedName }
    Surface(color = Color.Black, contentColor = MaterialTheme.colorScheme.onSurface) {
    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, "返回调试") }
            Text(state.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("临时对比", style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(960, 1280, 1920, 6000).forEach { edge ->
                FilterChip(selected = resolution == edge, onClick = { resolution = edge },
                    label = { Text(if (edge == 6000) "原图上限" else "${edge}px") })
            }
            OutlinedButton(onClick = { onRestart(resolution, false) }) { Text("按各模式已存参数生成") }
            OutlinedButton(onClick = { onRestart(resolution, true) }) { Text("按默认参数生成") }
        }
        Text("仅当前图片 · 不写入成品库 · 不改已存参数 · 不替换旧结果。实际尺寸受原图和内存限制。",
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("SBS", "模型深度", "投影深度").forEach { kind ->
                FilterChip(selected = viewKind == kind, onClick = { viewKind = kind }, label = { Text(kind) })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (selected != null) {
                // No key(selected.mode), Crossfade, image loader or intermediate blank frame.
                val depth = if (viewKind == "模型深度") selected.modelDepth else selected.projectionDepth
                SyncStereoZoomContent((if (viewKind == "SBS") selected.left else depth).asImageBitmap(),
                    (if (viewKind == "SBS") selected.right else depth).asImageBitmap(),
                    state.id, Modifier.fillMaxSize(), onTap = {}, onTransformChanged = { scale, offset -> zoom = scale; pan = offset })
            } else if (state.error != null) {
                Text("对比失败：${state.error}\n旧结果和原图均保留。", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(progress = { state.progress })
                    Spacer(Modifier.height(8.dp))
                    Text(state.message.ifBlank { "准备六模式对比…" })
                }
            }
        }
        if (selected != null) {
            val p = selected.params
            Text("${selected.mode.shortLabel()} · 单眼 ${selected.left.width}×${selected.left.height} · 强度 ${String.format(Locale.US, "%.3f", selected.actualScale)} · 窗口 ${p.blurRadius} · 输入反转 ${if (p.invertModelDepth) "开" else "关"} · 投影反转 ${if (p.invertDepth) "开" else "关"} · ${selected.elapsedMs}ms",
                Modifier.padding(horizontal = 12.dp, vertical = 3.dp).horizontalScroll(rememberScrollState()),
                maxLines = 1, style = MaterialTheme.typography.labelSmall)
            Text("缩放 ${(zoom * 100).roundToInt()}% · 位置 ${pan.x.roundToInt()}, ${pan.y.roundToInt()}",
                Modifier.padding(horizontal = 12.dp).semantics { contentDescription = "对比视口 $zoom ${pan.x} ${pan.y}" },
                maxLines = 1, style = MaterialTheme.typography.labelSmall)
        }
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = if (config.screenWidthDp < 400 || (config.screenWidthDp < 720 && config.fontScale > 1.4f)) 3 else 6) {
            DepthEdgeMode.entries.forEach { mode ->
                FilterChip(selected = selectedName == mode.name, enabled = state.images.any { it.mode == mode },
                    modifier = Modifier.weight(1f).semantics { contentDescription = mode.shortLabel() },
                    onClick = { selectedName = mode.name }, label = { Text(mode.name.substringAfterLast('_'), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1) })
            }
        }
        Text(if (viewKind == "SBS") "点按钮直接切换，保持双眼缩放与位置；双击画面复位。"
            else if (viewKind == "模型深度") "模型深度应近白远黑。若相反，在该模式设置中反转模型输入。"
            else "投影深度包含眼序/模式反转。External 默认近黑以配合右眼投影；不要只凭这张图判断立体方向。",
            Modifier.clickable { showHelp = true }.padding(horizontal = 12.dp, vertical = 6.dp), maxLines = 2,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        if (showHelp) AlertDialog(onDismissRequest = { showHelp = false }, title = { Text("对比与深度方向") },
            text = { Text("切换六种模式不会改变当前缩放和位置；双击画面复位。\n\n默认参数以 V10 深度 50 的 P90−P10 深度跨度换算，并不保证各模式的轮廓和遮挡相同。\n\n模型深度应近白远黑，输入反转单独保存。投影深度还包含模式/眼序的反转；External 默认投影近黑，可配合其右眼投影产生相同的相对远近。\n\n实际 RGB 尺寸显示在下方，受原图及内存约束。深度预览只用于查看；生成始终使用浮点深度。") },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("知道了") } })
    }
    }
}
