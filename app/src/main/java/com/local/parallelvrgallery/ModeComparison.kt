package com.local.parallelvrgallerypro

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

data class ModeComparisonImage(
    val mode: DepthEdgeMode,
    val left: Bitmap,
    val right: Bitmap,
    val params: VrGenerationParams,
    val actualScale: Float,
    val elapsedMs: Long,
    val modelDepth: Bitmap,
    val projectionDepth: Bitmap,
)

data class ModeComparisonState(
    val id: String,
    val photoKey: String,
    val name: String,
    val requestedLongEdge: Int,
    val progress: Float = 0f,
    val message: String = "",
    val images: List<ModeComparisonImage> = emptyList(),
    val error: String? = null,
)

/** All six pairs stay decoded; changing a button only swaps image references.
 * The stereo gesture key belongs to the comparison session, never to a mode.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModeComparisonScreen(state: ModeComparisonState, onBack: () -> Unit) {
    val apple = AppleTheme.colors
    var selectedName by rememberSaveable(state.id) { mutableStateOf(DepthEdgeMode.COMFY_LEGACY_V10.name) }
    var zoom by remember(state.id) { mutableFloatStateOf(1f) }
    var pan by remember(state.id) { mutableStateOf(Offset.Zero) }
    val selected = state.images.firstOrNull { it.mode.name == selectedName }
    val modes = DepthEdgeMode.entries

    Surface(color = Color.Black, contentColor = Color.White) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            // Floating Dark Liquid Glass top bar
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(22.dp),
                darkHud = true,
                shadowElevation = 8.dp,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, "返回调试", tint = Color.White)
                    }
                    Text(
                        state.name,
                        Modifier.weight(1f),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(shape = CircleShape, color = Color(0x28FFFFFF)) {
                        Text(
                            "临时对比",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (selected != null) {
                    // No key(selected.mode), Crossfade, image loader or intermediate blank frame.
                    SyncStereoZoomContent(
                        selected.left.asImageBitmap(),
                        selected.right.asImageBitmap(),
                        state.id,
                        Modifier.fillMaxSize(),
                        onTap = {},
                        onTransformChanged = { scale, offset -> zoom = scale; pan = offset },
                    )
                } else if (state.error != null) {
                    Text(
                        "对比失败：${state.error}\n旧结果和原图均保留。",
                        Modifier.padding(20.dp),
                        color = apple.systemRed,
                    )
                } else {
                    LiquidGlassSurface(shape = RoundedCornerShape(20.dp), darkHud = true) {
                        Column(
                            Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(progress = { state.progress }, color = apple.systemBlue, strokeWidth = 3.dp)
                            Text(state.message.ifBlank { "准备六模式对比…" }, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Bottom Dark Liquid Glass control deck
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                LiquidGlassSurface(
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    darkHud = true,
                    shadowElevation = 10.dp,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (selected != null) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${selected.mode.shortLabel()} · 强度 ${String.format(Locale.US, "%.3f", selected.actualScale)}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    modes.forEach { mode ->
                                        val isCurrent = mode.name == selectedName
                                        Box(
                                            Modifier
                                                .size(if (isCurrent) 8.dp else 5.dp)
                                                .clip(CircleShape)
                                                .background(if (isCurrent) apple.systemBlue else Color(0x55FFFFFF)),
                                        )
                                    }
                                }
                            }
                            Text(
                                "缩放 ${(zoom * 100).roundToInt()}% · 位置 ${pan.x.roundToInt()}, ${pan.y.roundToInt()}",
                                Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "对比视口 $zoom ${pan.x} ${pan.y}" },
                                maxLines = 1,
                                color = Color(0xCCFFFFFF),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        AppleCapsuleButton(
                            text = "${selected?.mode?.shortLabel() ?: "正在生成"} · 点击切换",
                            enabled = state.images.size == modes.size,
                            darkHud = true,
                            onClick = {
                                selectedName = modes[(modes.indexOfFirst { it.name == selectedName } + 1) % modes.size].name
                            },
                            modifier = Modifier.semantics { contentDescription = "切换对比模式" },
                        )
                    }
                }
            }
        }
    }
}
