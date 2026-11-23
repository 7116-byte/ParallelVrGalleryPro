package com.local.parallelvrgallerypro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class VideoDepthGeometryTest {
    @Test fun nativeSizeChangesEvenWhenPreparedVideoDepthSizeMatchesTheFrame() {
        val prepared = DepthMap(FloatArray(1280 * 720), 1280, 720)
        for (size in listOf(518, 770, 1036)) {
            val native = DepthTensorShape.map(FloatArray(size * size))
            val measured = VideoDepthGeometry.measured(native, prepared)
            assertEquals(VideoDepthGeometry(size, size, 1280, 720), measured)
            val metadata = measured.metadataLines().associate { it.substringBefore('=') to it.substringAfter('=') }
            assertEquals(measured, VideoDepthGeometry.read(metadata))
        }
    }

    @Test fun cachedFramesRetainObservedGeometryWithoutCopyingTheInferenceTime() {
        val fresh = VideoFrameTimings(depthMs = 80, depthGeometry = VideoDepthGeometry(1036, 1036, 1280, 720))
        val cacheHit = VideoFrameTimings(decodeMs = 4, encodeMs = 8)
        val display = cacheHit.withPreviousFrame(fresh)
        assertEquals(fresh.depthGeometry, display.depthGeometry)
        assertEquals(0L, display.depthMs)
        assertEquals(cacheHit.totalMs, display.totalMs)
        assertNull(cacheHit.withPreviousFrame(null).depthGeometry)
        val zeroMillis = VideoFrameTimings().withPreviousFrame(fresh)
        assertEquals(0L, zeroMillis.totalMs)
        assertEquals(fresh.depthGeometry, zeroMillis.depthGeometry)
    }

    @Test fun oldEncodedVideoDimensionsDoNotBecomeInferredDepthDimensions() {
        assertNull(VideoDepthGeometry.read(mapOf("width" to "2560", "height" to "720", "depthModel" to QNN_FP16_MODEL_ID)))
    }

    @Test fun adaptiveVideoRetainsBothTheTensorAndItsCroppedDepth() {
        val geometry = VideoDepthGeometry(1386, 770, 1280, 720, 1369, 770)
        val metadata = geometry.metadataLines().associate { it.substringBefore('=') to it.substringAfter('=') }
        assertEquals(geometry, VideoDepthGeometry.read(metadata))
        assertEquals(geometry, VideoDepthGeometry.read(metadata - "modelInputRotation"))
        assertNull(VideoDepthGeometry.read(metadata + ("depthContentWidth" to "1400")))
        assertNull(VideoDepthGeometry.read(metadata + ("depthContentWidth" to "0")))
    }

    @Test fun rotatedInputKeepsNativeTensorAndUprightContentBoundsAfterRestoring() {
        val geometry = VideoDepthGeometry(756, 1344, 1280, 720, 1344, 756, inputRotation = 90)
        val metadata = geometry.metadataLines().associate { it.substringBefore('=') to it.substringAfter('=') }

        assertEquals("90", metadata["modelInputRotation"])
        assertEquals(geometry, VideoDepthGeometry.read(metadata))
        assertNull(VideoDepthGeometry.read(metadata - "modelInputRotation"))
        assertNull(VideoDepthGeometry.read(metadata + ("depthContentWidth" to "1345")))
        assertNull(VideoDepthGeometry.read(metadata + ("depthContentHeight" to "757")))
        assertNull(VideoDepthGeometry.read(metadata + ("modelInputRotation" to "180")))
        assertNull(VideoDepthGeometry.read(metadata + ("modelInputRotation" to "invalid")))
    }

    @Test fun equalPixelCountsDoNotMixTemporalDepthAcrossOrientationChanges() {
        val smoother = DepthTemporalSmoother()
        smoother.smooth(DepthMap(floatArrayOf(0.45f, 0.45f, 0.45f, 0.45f, 0.45f, 0.45f), 3, 2))
        val orientationChanged = DepthMap(FloatArray(6) { 0.5f }, 2, 3)
        assertArrayEquals(FloatArray(6) { 0.5f }, smoother.smooth(orientationChanged).values, 0f)
    }
}
