package com.local.parallelvrgallerypro

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QnnFp16DepthRuntimeTest {
    @Test fun arbitraryQnnGridIdsAreCanonicalAndOldIdsStayExact() {
        assertEquals(QNN_FP16_MODEL_ID, qnnFp16ModelId(518))
        assertEquals(QNN_FP16_770_MODEL_ID, qnnFp16ModelId(770))
        assertEquals(QNN_FP16_1036_MODEL_ID, qnnFp16ModelId(1036))
        assertEquals("depth_anything_v2_small_qnn_fp16_532_v1", qnnFp16ModelId(532))
        assertEquals("depth_anything_v2_small_qnn_fp16_840_v1", qnnFp16ModelId(840))
        for (tiles in 37..74) {
            val pixels = tiles * 14
            assertEquals(pixels, qnnFp16Size(qnnFp16ModelId(pixels)))
            assertEquals(1, AvailableModels.count { it.id == qnnFp16ModelId(pixels) })
        }
        for (invalid in listOf(0, 504, 519, 1050, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { qnnFp16ModelId(invalid) }
        }
        for (variant in listOf("0504_v1", "0518_v1", "+518_v1", "518_v2", "519_v1", "1050_v1", "2147483648_v1", "${'$'}_v1")) {
            assertNull(qnnFp16Size("depth_anything_v2_small_qnn_fp16_$variant"))
        }
    }

    @Test fun differentImageAndVideoGridChoicesSurviveSeparateSnapshots() {
        val imageId = qnnFp16ModelId(532)
        val videoId = qnnFp16ModelId(840)
        val settings = AppSettings(imageModelId = imageId, videoModelId = videoId, depthResolution = 532)
        val image = settings.toParams()
        val video = settings.toVideoParams()
        assertEquals(imageId, image.depthModel)
        assertEquals(videoId, video.vr.depthModel)
        assertNotEquals(image.depthSessionKey(), video.toVrParams().depthSessionKey())
        val restored = requireNotNull(decodeVideoParams(video.encodeForQueue()))
        assertEquals(videoId, restored.toVrParams().depthModel)
        assertEquals(video.cacheVersion(), restored.cacheVersion())
    }

    @Test fun qnnPrecisionAndAspectHaveSeparateCanonicalModelIds() {
        val modelIds = mutableSetOf<String>()
        val versions = mutableSetOf<String>()
        for (variant in QnnDepthVariant.entries) for (aspect in QnnDepthAspect.entries) {
            val config = QnnModelConfig(variant, 1036, aspect)
            val id = qnnModelId(config)
            assertTrue(modelIds.add(id))
            assertEquals(config, qnnModelConfig(id))
            assertEquals(variant.sha256, AvailableModels.single { it.id == id }.sha256)
            assertTrue(versions.add(AppSettings(imageModelId = id).toParams().cacheVersion()))
        }
        assertEquals(QNN_FP16_1036_MODEL_ID, qnnModelId(QnnModelConfig(QnnDepthVariant.FP16, 1036, QnnDepthAspect.SQUARE)))
        assertNull(qnnModelConfig("depth_anything_v2_small_qnn_fp16_1036_square_v1"))
        assertNull(qnnModelConfig("depth_anything_v2_small_qnn_fp32head_01036_fit_v1"))
        assertNull(qnnModelConfig("depth_anything_v2_small_qnn_fp8_1036_fit_v1"))
        assertNull(qnnModelConfig("depth_anything_v2_small_qnn_fp32head_1036_unknown_v1"))
    }

    @Test fun adaptiveHeadModelPreservesStrictGpuAndItsAspectInVideoQueue() {
        val config = QnnModelConfig(QnnDepthVariant.FP32_HEAD, 770, QnnDepthAspect.FIT)
        val id = qnnModelId(config)
        val settings = AppSettings(imageModelId = id, videoModelId = id, videoDepthWorkers = 2)
        val image = settings.toParams()
        val video = settings.toVideoParams()
        assertTrue(image.useGpu && image.forceGpuNoFallback)
        assertTrue(video.toVrParams().useGpu && video.toVrParams().forceGpuNoFallback)
        assertEquals(1, video.depthWorkers)
        assertEquals(image.depthSessionKey(), video.toVrParams().depthSessionKey())
        val restored = requireNotNull(decodeVideoParams(video.encodeForQueue()))
        assertEquals(config, qnnModelConfig(restored.vr.depthModel))
        assertEquals(video.cacheVersion(), restored.cacheVersion())
    }

    @Test fun retiredFitPreferenceMigratesWithoutChangingItsHistoricalIdentity() {
        for (variant in QnnDepthVariant.entries) for (size in listOf(518, 770, 1036)) {
            val fit = QnnModelConfig(variant, size, QnnDepthAspect.FIT)
            val fitId = qnnModelId(fit)
            val alignedId = qnnModelId(fit.copy(aspect = QnnDepthAspect.ALIGNED))
            val squareId = qnnModelId(fit.copy(aspect = QnnDepthAspect.SQUARE))
            assertEquals(alignedId, activeDepthModelId(fitId))
            assertEquals(alignedId, preferredDepthModelId(fitId, squareId, qnnAvailable = true))
            assertEquals(alignedId, preferredDepthModelId(null, fitId, qnnAvailable = false))
            assertEquals(alignedId, activeDepthModelId(alignedId))
            assertEquals(squareId, preferredDepthModelId(squareId, fitId, qnnAvailable = true))
            assertEquals(fit, qnnModelConfig(fitId))
            assertEquals(variant.sha256, AvailableModels.single { it.id == fitId }.sha256)
        }
        val tflite = AppSettings().imageModelId
        assertEquals(tflite, activeDepthModelId(tflite))
    }

    @Test fun newPreferencesDefaultByDeviceAndSavedChoicesWin() {
        val tflite = AppSettings().imageModelId
        val tflite770 = "depth_anything_v2_small_770_v1"
        assertEquals(QNN_FP16_MODEL_ID, preferredDepthModelId(null, null, qnnAvailable = true))
        assertEquals(tflite, preferredDepthModelId(null, null, qnnAvailable = false))
        assertEquals(tflite, preferredDepthModelId(tflite, null, qnnAvailable = true))
        assertEquals(tflite, preferredDepthModelId(null, tflite, qnnAvailable = true))
        assertEquals(QNN_FP16_1036_MODEL_ID, preferredDepthModelId(QNN_FP16_1036_MODEL_ID, tflite, qnnAvailable = false))
        assertEquals(tflite770, preferredDepthModelId(tflite770, QNN_FP16_MODEL_ID, qnnAvailable = true))
    }

    @Test fun eachQnnInputSizeHasItsOwnImageAndVideoCache() {
        val expected = mapOf(QNN_FP16_MODEL_ID to 518, qnnFp16ModelId(532) to 532,
            QNN_FP16_770_MODEL_ID to 770, qnnFp16ModelId(840) to 840, QNN_FP16_1036_MODEL_ID to 1036)
        val imageVersions = mutableSetOf<String>()
        val videoVersions = mutableSetOf<String>()
        for ((id, size) in expected) {
            val spec = AvailableModels.single { it.id == id }
            assertEquals(size, spec.inputSize)
            assertEquals(size, qnnFp16Size(id))
            assertEquals(QNN_FP16_MODEL_SHA256, spec.sha256)
            val settings = AppSettings(imageModelId = id, videoModelId = id, depthResolution = size)
            val image = settings.toParams()
            val video = settings.toVideoParams()
            assertTrue(image.useGpu && image.forceGpuNoFallback)
            assertTrue(video.toVrParams().useGpu && video.toVrParams().forceGpuNoFallback)
            assertTrue(imageVersions.add(image.cacheVersion()))
            assertTrue(videoVersions.add(video.cacheVersion()))
            assertEquals(image.depthSessionKey(), video.toVrParams().depthSessionKey())
            val restored = requireNotNull(decodeVideoParams(video.encodeForQueue()))
            assertEquals(id, restored.vr.depthModel)
            assertEquals(video.cacheVersion(), restored.cacheVersion())
        }
    }

    @Test fun warmupWaitsForOrdinaryVisibleIdleGalleryAndRestoredQueues() {
        val idle = UiState(hasPermission = true)
        assertTrue(idle.canWarmImageModel(visible = true, queueRestored = true))
        assertFalse(idle.canWarmImageModel(visible = true, queueRestored = false))
        assertFalse(idle.canWarmImageModel(visible = false, queueRestored = true))
        for (busy in listOf(
            idle.copy(hasPermission = false), idle.copy(loading = true), idle.copy(allLoading = true),
            idle.copy(albumLoading = true), idle.copy(settingsOpen = true), idle.copy(manageOpen = true),
            idle.copy(homeTab = "generated"), idle.copy(selectedIndex = 0), idle.copy(blockingMessage = "saving"),
            idle.copy(states = mapOf("image" to VrState.QUEUED)),
            idle.copy(videoStates = mapOf("video" to VideoVrState.GENERATING)),
        )) {
            assertFalse(busy.canWarmImageModel(visible = true, queueRestored = true))
        }
        assertTrue(idle.copy(videoStates = mapOf("video" to VideoVrState.PAUSED))
            .canWarmImageModel(visible = true, queueRestored = true))
    }

    @Test fun selectedQnnIsStrictAndHasAnIndependentSharedSession() {
        val tflite = AppSettings(modelThreads = 8, videoModelThreads = 2, gpuTestMode = GpuTestMode.ACCURATE_OPENGL)
        val settings = tflite.copy(imageModelId = QNN_FP16_MODEL_ID, videoModelId = QNN_FP16_MODEL_ID)
        val image = settings.toParams()
        val video = settings.toVideoParams()
        val videoDepth = video.toVrParams()
        assertTrue(image.useGpu && image.forceGpuNoFallback)
        assertTrue(videoDepth.useGpu && videoDepth.forceGpuNoFallback)
        assertEquals(1, video.depthWorkers)
        assertEquals(image.depthSessionKey(), videoDepth.depthSessionKey())
        assertNotEquals(tflite.toParams().cacheVersion(), image.cacheVersion())
        assertNotEquals(tflite.toVideoParams().cacheVersion(), video.cacheVersion())
        assertEquals(tflite.toVideoParams().toVrParams(), settings.copy(videoModelId = tflite.videoModelId).toVideoParams().toVrParams())
        assertEquals(tflite.toParams(), settings.copy(imageModelId = tflite.imageModelId).toParams())
    }

    @Test fun queueRoundTripPreservesQnnModelAndStrictGpu() {
        val settings = AppSettings(imageModelId = QNN_FP16_MODEL_ID, videoModelId = QNN_FP16_MODEL_ID)
        val saved = settings.toVideoParams()
        val restored = requireNotNull(decodeVideoParams(saved.encodeForQueue()))
        assertEquals(QNN_FP16_MODEL_ID, restored.vr.depthModel)
        assertEquals(saved.cacheVersion(), restored.cacheVersion())
        assertEquals(saved.toVrParams(), restored.toVrParams())
        assertTrue(restored.toVrParams().forceGpuNoFallback)
    }

    @Test fun normalizationPreservesInverseDepthOrder() {
        val depth = floatArrayOf(7f, 1f, 4f)
        normalizeQnnDepth(depth)
        assertArrayEquals(floatArrayOf(1f, 0f, 0.5f), depth, 0f)
    }

    @Test fun flatAndNonfiniteDepthFailBeforeNormalization() {
        for (invalid in listOf(floatArrayOf(2f, 2f), floatArrayOf(1f, Float.NaN), floatArrayOf(Float.POSITIVE_INFINITY, 3f))) {
            assertThrows(IllegalStateException::class.java) { normalizeQnnDepth(invalid) }
        }
    }
}
