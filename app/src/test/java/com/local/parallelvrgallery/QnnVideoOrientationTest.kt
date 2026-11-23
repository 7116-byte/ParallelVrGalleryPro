package com.local.parallelvrgallerypro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QnnVideoOrientationTest {
    private val landscape = QnnInputGeometry.forSource(1036, 1280, 720, QnnDepthAspect.PORTRAIT_PAIR)

    @Test fun rotationAtTheLimitKeepsTheSharedPortraitSession() {
        val plan = QnnVideoOrientation(landscape, 20_000_000L)
        assertFalse(plan.useNativeLandscape)
        assertEquals(landscape, plan.inferenceInput)
    }

    @Test fun slowerRotationKeepsItsDedicatedLandscapeShapeWhenResuming() {
        val legacyRecord = "756\t1344\t90\t20000001"
        val restored = QnnVideoOrientation.restore(legacyRecord, 1036)
        assertEquals(legacyRecord, restored.encode())
        assertEquals(20_000_001L, restored.rotationNanos)
        assertTrue(restored.useNativeLandscape)
        assertEquals(QnnInputGeometry(1344, 756, 1344, 756, 0, 0), restored.inferenceInput)
    }

    @Test fun newVideoPersistsItsFixedPortraitPlanWithoutAProbe() {
        val plan = QnnVideoOrientation.forNewVideo(landscape)
        assertEquals("756\t1344\t90\tportrait-v2", plan.encode())
        assertEquals(plan, QnnVideoOrientation.restore(plan.encode(), 1036))
        assertNull(plan.rotationNanos)
        assertFalse(plan.useNativeLandscape)
        assertEquals(landscape, plan.inferenceInput)

        val upright = QnnInputGeometry.forSource(1036, 720, 960, QnnDepthAspect.PORTRAIT_PAIR)
        val uprightPlan = QnnVideoOrientation.restore(QnnVideoOrientation.forNewVideo(upright).encode(), 1036)
        assertEquals(upright, uprightPlan.inferenceInput)
        assertNull(uprightPlan.rotationNanos)
    }

    @Test fun rejectsOtherShapesAndTimingsOutsideTheRotationCase() {
        assertThrows(IllegalArgumentException::class.java) {
            QnnVideoOrientation.restore("1036\t1036\t90\t400000", 1036)
        }
        assertThrows(IllegalArgumentException::class.java) {
            QnnVideoOrientation.restore("756\t1344\t90\tunknown-policy", 1036)
        }
        val portrait = QnnInputGeometry.forSource(1036, 720, 1280, QnnDepthAspect.PORTRAIT_PAIR)
        assertThrows(IllegalArgumentException::class.java) { QnnVideoOrientation(portrait, 1L) }
    }
}
