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
}
