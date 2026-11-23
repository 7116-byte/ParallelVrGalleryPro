package com.local.parallelvrgallerypro

import org.junit.Assert.*
import org.junit.Test

class OriginalImageExportSpecTest {
    private fun record(mode: DepthEdgeMode = DepthEdgeMode.COMFY_LEGACY_V10) = mapOf(
        "photoKey" to "17_200_300", "cacheVersion" to "selected-old-version", "depthModel" to "depth_anything_v2_small_770_v1",
        "depthScale" to "35.5", "blurRadius" to "5", "fillRadius" to "0", "invertDepth" to "true",
        "maxLongEdge" to "6000", "modelThreads" to "2", "useGpu" to "true", "gpuTestMode" to "ACCURATE_OPENCL",
        "forceGpuNoFallback" to "true", "inpaintMode" to "FOREGROUND_FILL", "quality" to "94", "edgeMode" to mode.name,
        "matchV10Default" to "true", "invertModelDepth" to "true", "sourceWidth" to "1280", "sourceHeight" to "960")
    private fun restore(fields: Map<String,String>, upscale: Map<String,String>? = null) =
        OriginalImageExportSpec.restore(fields, upscale, "17_200_300", "selected-old-version", 2560, 960)

    @Test fun recordedModelModeAndFractionalVisualParametersSurviveForAllSixModes() {
        DepthEdgeMode.entries.forEach { mode ->
            val snapshot = restore(record(mode)); val p = snapshot.params
            assertEquals(mode, p.edgeMode); assertEquals("depth_anything_v2_small_770_v1", p.depthModel)
            assertEquals(35.5f, p.depthScale, 0f); assertEquals(5, p.blurRadius); assertEquals(0, p.fillRadius)
            assertTrue(p.invertDepth && p.invertModelDepth && p.matchV10Default && p.useGpu && p.forceGpuNoFallback)
            assertEquals(GpuTestMode.ACCURATE_OPENCL, p.gpuTestMode); assertEquals(2, p.modelThreads)
            assertEquals(1280, snapshot.eyeWidth); assertEquals(960, snapshot.eyeHeight)
        }
    }
    @Test fun upscaleOrderAndInterpolationAreRestoredInsteadOfCurrentDefaults() {
        val settings = mapOf("enabled" to "true", "maxLongEdge" to "3072", "interpolation" to "BICUBIC", "order" to "RESIZE_THEN_SMOOTH")
        val p = restore(record(), settings).params.depthUpscale
        assertTrue(p.enabled); assertEquals(3072,p.maxLongEdge)
        assertEquals(DepthInterpolation.BICUBIC,p.interpolation); assertEquals(DepthResizeOrder.RESIZE_THEN_SMOOTH,p.order)
    }
    @Test fun missingCorruptOrUnknownParametersCannotSilentlyUseCurrentSettings() {
        for(key in listOf("depthModel","depthScale","blurRadius","fillRadius","invertDepth","maxLongEdge","modelThreads","useGpu")) {
            assertThrows(IllegalArgumentException::class.java) { restore(record()-key) }
        }
        for((key,value) in listOf("depthScale" to "NaN","depthScale" to "Infinity","useGpu" to "yes","edgeMode" to "FUTURE_V12","gpuTestMode" to "OTHER")) {
            assertThrows(IllegalArgumentException::class.java) { restore(record()+(key to value)) }
        }
    }
    @Test fun differentSourceVersionOrGeometryIsRejected() {
        for((key,value) in listOf("photoKey" to "other","cacheVersion" to "latest-wrong-version","sourceWidth" to "1279","sourceHeight" to "961")) {
            assertThrows(IllegalArgumentException::class.java) { restore(record()+(key to value)) }
        }
        assertThrows(IllegalArgumentException::class.java) { OriginalImageExportSpec.restore(record(),null,"17_200_300","selected-old-version",2559,960) }
    }
    @Test fun absentNewOptionsRetainHistoricalDefaultsAndRecordedCacheMode() {
        val old = record(DepthEdgeMode.CENTERED_V7)-setOf("cacheVersion","edgeMode","matchV10Default","invertModelDepth","forceGpuNoFallback","gpuTestMode")
        val snapshot=OriginalImageExportSpec.restore(old,null,"17_200_300","model_depthV7_s40",2560,960)
        assertEquals(DepthEdgeMode.CENTERED_V7,snapshot.params.edgeMode)
        assertFalse(snapshot.params.matchV10Default || snapshot.params.invertModelDepth || snapshot.params.depthUpscale.enabled)
        assertTrue(snapshot.params.forceGpuNoFallback)
    }

    @Test fun adaptivePngExportUsesRecordedTensorAndCropAndRejectsUnsafeDimensions() {
        val id = qnnModelId(QnnModelConfig(QnnDepthVariant.FP32_HEAD, 1036, QnnDepthAspect.FIT))
        val fields = record() + mapOf(
            "depthModel" to id, "modelInputWidth" to "1386", "modelInputHeight" to "770",
            "modelDepthContentWidth" to "1369", "modelDepthContentHeight" to "770",
            "modelDepthPadLeft" to "8", "modelDepthPadTop" to "0",
        )
        assertEquals(QnnInputGeometry(1386, 770, 1369, 770, 8, 0), restore(fields).inputGeometry)
        assertThrows(IllegalArgumentException::class.java) { restore(fields - "modelDepthPadLeft") }
        assertThrows(IllegalArgumentException::class.java) { restore(fields + ("modelInputWidth" to "1400")) }
    }

    @Test fun wideTallPngExportRestoresOnlyARecordedFullNativeShape() {
        val id = qnnModelId(QnnModelConfig(QnnDepthVariant.FP32_HEAD, 1036, QnnDepthAspect.WIDE_TALL))
        fun fields(width: Int, height: Int) = record() + mapOf(
            "depthModel" to id, "modelInputWidth" to width.toString(), "modelInputHeight" to height.toString(),
            "modelDepthContentWidth" to width.toString(), "modelDepthContentHeight" to height.toString(),
            "modelDepthPadLeft" to "0", "modelDepthPadTop" to "0",
        )
        for ((width, height) in listOf(1344 to 756, 756 to 1344)) {
            val restored = restore(fields(width, height))
            assertEquals(id, restored.params.depthModel)
            assertEquals(QnnInputGeometry(width, height, width, height, 0, 0), restored.inputGeometry)
        }

        assertThrows(IllegalArgumentException::class.java) { restore(fields(1386, 770)) }
        assertThrows(IllegalArgumentException::class.java) { restore(fields(1120, 630)) }
        assertThrows(IllegalArgumentException::class.java) {
            restore(fields(1344, 756) + mapOf("modelDepthContentWidth" to "1320", "modelDepthPadLeft" to "12"))
        }
    }

    @Test fun portraitPairPngExportRestoresRecordedRotationForTheOriginalSource() {
        val id = qnnModelId(QnnModelConfig(QnnDepthVariant.FP32_HEAD, 1036, QnnDepthAspect.PORTRAIT_PAIR))
        val landscape = QnnInputGeometry(756, 1344, 756, 1344, 0, 0, rotation = 90)
        val portrait = QnnInputGeometry(882, 1176, 882, 1176, 0, 0)
        fun fields(geometry: QnnInputGeometry, sourceWidth: Int, sourceHeight: Int) = record() + mapOf(
            "depthModel" to id, "sourceWidth" to sourceWidth.toString(), "sourceHeight" to sourceHeight.toString(),
            "modelInputWidth" to geometry.tensorWidth.toString(), "modelInputHeight" to geometry.tensorHeight.toString(),
            "modelDepthContentWidth" to geometry.contentWidth.toString(), "modelDepthContentHeight" to geometry.contentHeight.toString(),
            "modelDepthPadLeft" to "0", "modelDepthPadTop" to "0", "modelInputRotation" to geometry.rotation.toString(),
        )
        fun export(values: Map<String, String>, width: Int, height: Int) =
            OriginalImageExportSpec.restore(values, null, "17_200_300", "selected-old-version", width * 2, height)

        val landscapeFields = fields(landscape, 1920, 1080)
        assertEquals(landscape, export(landscapeFields, 1920, 1080).inputGeometry)
        assertEquals(portrait, export(fields(portrait, 1200, 1600), 1200, 1600).inputGeometry)
        assertEquals(portrait, export(fields(portrait, 1200, 1200), 1200, 1200).inputGeometry)

        assertThrows(IllegalArgumentException::class.java) { export(landscapeFields - "modelInputRotation", 1920, 1080) }
        assertThrows(IllegalArgumentException::class.java) { export(landscapeFields + ("modelInputRotation" to "0"), 1920, 1080) }
        assertThrows(IllegalArgumentException::class.java) { export(fields(portrait.copy(rotation = 90), 1920, 1080), 1920, 1080) }
        assertThrows(IllegalArgumentException::class.java) { export(fields(landscape.withoutRotation(), 1920, 1080), 1920, 1080) }
    }
}
