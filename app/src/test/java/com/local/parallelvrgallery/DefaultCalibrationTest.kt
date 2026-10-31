package com.local.parallelvrgallerypro

import org.junit.Assert.*
import org.junit.Test

class DefaultCalibrationTest {
    @Test fun defaultScalarMatchesMeasuredV10SpanAfterNormalizationChangesContrast() {
        val raw = FloatArray(20001) { it / 20000f }
        val baseline = FloatArray(raw.size) { 0.12f + raw[it] * 0.76f }
        for (mode in listOf(DepthEdgeMode.LEGACY_V6, DepthEdgeMode.CENTERED_V7, DepthEdgeMode.REFERENCE_V8, DepthEdgeMode.REFERENCE_V9)) {
            val params = ModeVisualProfile.defaults(mode).applyTo(AppSettings(edgeMode = mode)).toParams()
            val prepared = FloatArray(raw.size) { if (mode.isReference) 1f - raw[it] else if (mode == DepthEdgeMode.LEGACY_V6) raw[it] * 4f / 9f else raw[it] }
            for (width in listOf(560, 1280, 1920)) {
                val strength = DefaultDisparityCalibration.scale(params, width, prepared, baseline)
                val unit = if (mode.isReference) width / 500f else 255f / width
                assertEquals(DefaultDisparityCalibration.span(baseline) * 50f * 255f / width,
                    DefaultDisparityCalibration.span(prepared) * strength * unit, 0.0001f)
            }
        }
    }

    @Test fun handSetNativeStrengthBypassesAllDefaultCalibration() {
        val params = VrGenerationParams(depthScale = 73f, edgeMode = DepthEdgeMode.LEGACY_V6)
        assertEquals(73f, DefaultDisparityCalibration.scale(params, 1280, floatArrayOf(0.2f, 0.4f), floatArrayOf(0f, 1f)), 0f)
    }

    @Test fun flatAndInvalidDepthCannotProduceNanOrDivideByZero() {
        val params = ModeVisualProfile.defaults(DepthEdgeMode.REFERENCE_V9).applyTo(AppSettings(edgeMode = DepthEdgeMode.REFERENCE_V9)).toParams()
        val actual = DefaultDisparityCalibration.scale(params, 560, FloatArray(32) { Float.NaN }, FloatArray(32))
        assertTrue(actual.isFinite())
        assertTrue(actual in 0f..100f)
    }

    @Test fun actualCalibratedScalarTravelsWithPreparedDepthWithoutChangingTheMap() {
        val values = floatArrayOf(0f, 0.5f, 1f)
        val map = DepthMap(values, 3, 1, 89.25f)
        val params = ModeVisualProfile.defaults(DepthEdgeMode.LEGACY_V6).applyTo(AppSettings()).toParams()
        assertSame(values, map.values)
        assertEquals(89.25f, params.effectiveScale(1280, map), 0f)
    }
}
