package com.local.parallelvrgallerypro

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot

/**
 * Immutable stereo (or single-viewport) zoom & pan state.
 * [offset] is expressed in eye-local pixels relative to the eye viewport center.
 */
internal data class StereoTransformState(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
)

/**
 * Pure, side-effect-free gesture session for synchronized SBS stereo (and single-viewport)
 * two-finger pinch-to-zoom + pan.
 *
 * Key invariant:
 * - Scale factor is ALWAYS computed from the raw screen distance (span) between pointers,
 *   never from per-pointer eye-folded coordinates. This guarantees spreading fingers apart
 *   always zooms in and pinching inward always zooms out, even when pointers straddle the
 *   SBS center divider (e.g. left eye to right eye 1/2..3/4 region).
 * - Focal landing point is computed from the raw screen midpoint of the fingers, mapped ONCE
 *   into eye-local coordinates using the eye side locked when the pinch begins so drifting
 *   across the center divider mid-gesture never causes a ±halfWidth jump.
 * - Simultaneous two-finger translation keeps the exact image pixel under the fingers'
 *   midpoint glued beneath the midpoint throughout the gesture.
 */
internal class StereoPinchSession {
    private var activePointerIds: Set<Long>? = null
    private var anchorEyeRight: Boolean? = null
    private var lastCentroid: Offset? = null
    private var lastSpan: Float? = null

    fun reset() {
        activePointerIds = null
        anchorEyeRight = null
        lastCentroid = null
        lastSpan = null
    }

    fun updatePinch(
        state: StereoTransformState,
        pointerIds: Set<Long>,
        positions: List<Offset>,
        viewportWidth: Float,
        viewportHeight: Float,
        sbsMode: Boolean,
        minScale: Float = 1f,
        maxScale: Float = 8f,
    ): StereoTransformState {
        if (positions.size < 2 || viewportWidth <= 0f || viewportHeight <= 0f) {
            reset()
            return state
        }
        val eyeWidth = if (sbsMode) viewportWidth / 2f else viewportWidth
        if (eyeWidth <= 0f) return state

        val rawCentroid = rawCentroid(positions)
        val rawSpan = rawSpan(positions, rawCentroid)

        // When a pinch begins or the set of touching pointers changes (e.g., 2->3 or 3->2),
        // initialize the baseline cleanly without applying a spurious jump on that frame.
        if (activePointerIds != pointerIds || lastCentroid == null || lastSpan == null) {
            activePointerIds = pointerIds.toSet()
            if (anchorEyeRight == null) {
                anchorEyeRight = sbsMode && rawCentroid.x >= eyeWidth
            }
            lastCentroid = rawCentroid
            lastSpan = rawSpan
            return state
        }

        val prevCentroid = lastCentroid!!
        val prevSpan = lastSpan!!
        val eyeRight = anchorEyeRight == true
        lastCentroid = rawCentroid
        lastSpan = rawSpan

        val zoom = if (prevSpan >= MIN_VALID_SPAN_PX && rawSpan >= MIN_VALID_SPAN_PX) {
            (rawSpan / prevSpan).coerceIn(0.82f, 1.22f)
        } else {
            1f
        }
        val nextScale = (state.scale * zoom).coerceIn(minScale, maxScale)
        if (nextScale <= minScale + 0.0005f) {
            return StereoTransformState(scale = minScale, offset = Offset.Zero)
        }

        val effectiveZoom = if (state.scale <= 0f) 1f else nextScale / state.scale
        val eyeCenter = Offset(eyeWidth / 2f, viewportHeight / 2f)
        val currLocal = toEyeLocal(rawCentroid, eyeWidth, eyeRight)
        val prevLocal = toEyeLocal(prevCentroid, eyeWidth, eyeRight)
        val currFromCenter = currLocal - eyeCenter
        val prevFromCenter = prevLocal - eyeCenter

        val unboundedOffset = currFromCenter - (prevFromCenter - state.offset) * effectiveZoom
        val clampedOffset = clampStereoOffset(unboundedOffset, nextScale, eyeWidth, viewportHeight)
        return StereoTransformState(scale = nextScale, offset = clampedOffset)
    }

    companion object {
        private const val MIN_VALID_SPAN_PX = 8f

        internal fun rawCentroid(positions: List<Offset>): Offset {
            if (positions.isEmpty()) return Offset.Zero
            var sx = 0.0
            var sy = 0.0
            for (p in positions) {
                sx += p.x.toDouble()
                sy += p.y.toDouble()
            }
            val n = positions.size.toDouble()
            return Offset((sx / n).toFloat(), (sy / n).toFloat())
        }

        internal fun rawSpan(positions: List<Offset>, center: Offset): Float {
            if (positions.isEmpty()) return 0f
            var sum = 0.0
            for (p in positions) {
                sum += hypot((p.x - center.x).toDouble(), (p.y - center.y).toDouble())
            }
            return (sum / positions.size.toDouble()).toFloat()
        }

        internal fun toEyeLocal(rawPoint: Offset, eyeWidth: Float, rightEye: Boolean): Offset {
            return Offset(if (rightEye) rawPoint.x - eyeWidth else rawPoint.x, rawPoint.y)
        }

        internal fun clampStereoOffset(
            offset: Offset,
            scale: Float,
            eyeWidth: Float,
            viewportHeight: Float,
        ): Offset {
            if (scale <= 1f || eyeWidth <= 0f || viewportHeight <= 0f) return Offset.Zero
            val maxX = eyeWidth * (scale - 1f) / 2f
            val maxY = viewportHeight * (scale - 1f) / 2f
            return Offset(
                x = offset.x.coerceIn(-maxX, maxX),
                y = offset.y.coerceIn(-maxY, maxY),
            )
        }
    }
}
