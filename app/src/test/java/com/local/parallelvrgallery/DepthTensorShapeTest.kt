package com.local.parallelvrgallerypro

import org.junit.Assert.*
import org.junit.Test

class DepthTensorShapeTest {
    @Test fun highResolutionModelHasIndependentCacheAndSurvivesVisualModeSwitch() {
        val old = AppSettings()
        val high = old.copy(imageModelId = "depth_anything_v2_small_770_v1", depthResolution = 770)
        val switched = ModeVisualProfile.defaults(DepthEdgeMode.COMFY_SUBPIXEL_V11).applyTo(high)
        assertEquals(high.imageModelId, switched.imageModelId)
        assertEquals(old.videoModelId, switched.videoModelId)
        assertNotEquals(old.toParams().cacheVersion(), high.toParams().cacheVersion())
        assertEquals(old.toVideoParams().cacheVersion(), high.toVideoParams().cacheVersion())
    }
    @Test fun acceptsExistingAndHighResolutionContracts() {
        for (size in listOf(518, 770)) {
            assertEquals(size, DepthTensorShape.inputSize(intArrayOf(1,size,size,3), intArrayOf(1,size,size,1)))
            assertEquals(size, DepthTensorShape.inputSize(intArrayOf(1,size,size,3), intArrayOf(1,size,size)))
        }
    }
    @Test fun rejectsResizedInputWithOldOutput() {
        assertThrows(IllegalArgumentException::class.java) { DepthTensorShape.inputSize(intArrayOf(1,770,770,3), intArrayOf(1,518,518,1)) }
    }
    @Test fun rejectsDifferentLayoutAndUnsupportedSizes() {
        for (input in listOf(intArrayOf(1,3,770,770),intArrayOf(1,768,768,3),intArrayOf(1,518,770,3))) {
            assertThrows(IllegalArgumentException::class.java) { DepthTensorShape.inputSize(input,intArrayOf(1,770,770,1)) }
        }
    }
    @Test fun retainsActualDepthSizeAndFloatValuesWithoutResampling() {
        for (size in listOf(518,770)) {
            val values = FloatArray(size*size) { it.toFloat() / (size*size) }
            val map = DepthTensorShape.map(values)
            assertEquals(size,map.width);assertEquals(size,map.height);assertSame(values,map.values)
        }
        assertThrows(IllegalStateException::class.java) { DepthTensorShape.map(FloatArray(768*768)) }
    }
}
