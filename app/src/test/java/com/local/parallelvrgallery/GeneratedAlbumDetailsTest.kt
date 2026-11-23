package com.local.parallelvrgallerypro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedAlbumDetailsTest {
    @Test fun showsSavedQnnPrecisionBudgetAspectAndDirection() {
        val mode = DepthEdgeMode.COMFY_LEGACY_V10
        val params = VrGenerationParams(
            depthModel = qnnModelId(QnnModelConfig(QnnDepthVariant.FP32_HEAD, 1036, QnnDepthAspect.ALIGNED)),
            edgeMode = mode,
            useGpu = true,
            forceGpuNoFallback = true,
            blurRadius = 7,
            invertDepth = ModeVisualProfile.defaults(mode).invert,
            invertModelDepth = true,
            depthUpscale = DepthUpscaleSettings(enabled = true),
            resultVariant = "abcd0123",
        )
        val details = generatedAlbumParameterDetails(generatedAlbumKey(params.cacheVersion()), AppLanguage.ZH)
        assertTrue(details.startsWith("QNN GPU 末端 FP32 · 1036² 预算 · 14 像素分块"))
        assertTrue(details.contains("平滑 7"))
        assertTrue(details.endsWith("反转开"))
    }

    @Test fun preservesHistoricalPaddedModeAndIdentifiesKnownTfliteModels() {
        val padded = AppSettings(imageModelId = qnnModelId(QnnModelConfig(QnnDepthVariant.FP16, 770, QnnDepthAspect.FIT))).toParams()
        assertTrue(generatedAlbumParameterDetails(padded.cacheVersion(), AppLanguage.EN).contains("770² budget · Aspect padding"))
        val legacy = VrGenerationParams(depthModel = "depth_anything_v2.tflite", useGpu = false)
        assertTrue(generatedAlbumParameterDetails(legacy.cacheVersion(), AppLanguage.EN).startsWith("TFLite CPU · 518×518"))
    }

    @Test fun fixedLandscapeAndPortraitShowActualInferenceSizes() {
        val modelId = qnnModelId(QnnModelConfig(QnnDepthVariant.FP32_HEAD, 1036, QnnDepthAspect.WIDE_TALL))
        val details = generatedAlbumParameterDetails(AppSettings(imageModelId = modelId).toParams().cacheVersion(), AppLanguage.ZH)
        assertTrue(details.startsWith("QNN GPU 末端 FP32 · 固定横竖屏 1344×756 / 756×1344"))
    }

    @Test fun portraitPairReplacesTheActivePreferenceAndShowsBothActualShapes() {
        val legacy = QnnModelConfig(QnnDepthVariant.FP32_HEAD, 1036, QnnDepthAspect.WIDE_TALL)
        val modelId = qnnModelId(legacy.copy(aspect = QnnDepthAspect.PORTRAIT_PAIR))
        assertEquals(modelId, activeDepthModelId(qnnModelId(legacy)))
        assertEquals(legacy, qnnModelConfig(qnnModelId(legacy)))
        val details = generatedAlbumParameterDetails(AppSettings(imageModelId = modelId).toParams().cacheVersion(), AppLanguage.ZH)
        assertTrue(details.startsWith("QNN GPU 末端 FP32 · 双竖版 756×1344 / 882×1176"))
    }

    @Test fun unknownVersionOrModelDoesNotInventDetails() {
        assertEquals("", generatedAlbumParameterDetails("default", AppLanguage.ZH))
        assertEquals("", generatedAlbumParameterDetails(VrGenerationParams(depthModel = "unknown").cacheVersion(), AppLanguage.ZH))
    }
}
