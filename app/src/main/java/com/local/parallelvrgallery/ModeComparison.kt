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
internal fun ModeComparisonScreen(state: ModeComparisonState, onBack: () -> Unit) {
    var selectedName by rememberSaveable(state.id) { mutableStateOf(DepthEdgeMode.COMFY_LEGACY_V10.name) }
    var zoom by remember(state.id) { mutableFloatStateOf(1f) }
    var pan by remember(state.id) { mutableStateOf(Offset.Zero) }
    val selected = state.images.firstOrNull { it.mode.name == selectedName }
    Surface(color = Color.Black, contentColor = MaterialTheme.colorScheme.onSurface) {
    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, "返回调试") }
            Text(state.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("临时对比", style = MaterialTheme.typography.labelMedium)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (selected != null) {
                // No key(selected.mode), Crossfade, image loader or intermediate blank frame.
                SyncStereoZoomContent(selected.left.asImageBitmap(), selected.right.asImageBitmap(),
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
            Text("${selected.mode.shortLabel()} · 强度 ${String.format(Locale.US, "%.3f", selected.actualScale)}",
                Modifier.padding(horizontal = 12.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
            Text("缩放 ${(zoom * 100).roundToInt()}% · 位置 ${pan.x.roundToInt()}, ${pan.y.roundToInt()}",
                Modifier.padding(horizontal = 12.dp).semantics { contentDescription = "对比视口 $zoom ${pan.x} ${pan.y}" },
                maxLines = 1, style = MaterialTheme.typography.labelSmall)
        }
        Button(onClick = {
            val modes = DepthEdgeMode.entries
            selectedName = modes[(modes.indexOfFirst { it.name == selectedName } + 1) % modes.size].name
        }, enabled = state.images.size == DepthEdgeMode.entries.size,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp).semantics {
                contentDescription = "切换对比模式"
            }) {
            Text("${selected?.mode?.shortLabel() ?: "正在生成"} · 点击切换", maxLines = 1)
        }
    }
    }
}
