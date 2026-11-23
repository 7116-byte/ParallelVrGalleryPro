package com.local.parallelvrgallerypro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QnnInputGeometryTest {
    @Test fun squareKeepsLegacyStretchForLandscapeAndPortrait() {
        for ((width, height) in listOf(1920 to 1080, 1080 to 1920)) {
            assertEquals(QnnInputGeometry(1036, 1036, 1036, 1036, 0, 0),
                QnnInputGeometry.forSource(1036, width, height, QnnDepthAspect.SQUARE))
        }
    }

    @Test fun fullHdUsesAlmostAllPixelBudgetWhileFitCentersUnstretchedContent() {
        val aligned = QnnInputGeometry.forSource(1036, 1920, 1080, QnnDepthAspect.ALIGNED)
        assertEquals(QnnInputGeometry(1386, 770, 1386, 770, 0, 0), aligned)
        val fit = QnnInputGeometry.forSource(1036, 1920, 1080, QnnDepthAspect.FIT)
        assertEquals(QnnInputGeometry(1386, 770, 1369, 770, 8, 0), fit)
        assertEquals(fit, QnnInputGeometry.forSource(1036, 1280, 720, QnnDepthAspect.FIT))
        assertEquals(QnnInputGeometry(770, 1386, 770, 1369, 0, 8),
            QnnInputGeometry.forSource(1036, 1080, 1920, QnnDepthAspect.FIT))
    }

    @Test fun adaptiveTensorsStayWithinBudgetForAllSelectableSizesAndExtremeRatios() {
        val sources = listOf(100 to 100, 4032 to 3024, 1024 to 1536, 1920 to 800, Int.MAX_VALUE to 1, 1 to Int.MAX_VALUE)
        for (size in 518..1036 step 14) for (aspect in listOf(QnnDepthAspect.FIT, QnnDepthAspect.ALIGNED)) {
            for ((width, height) in sources) {
                val geometry = QnnInputGeometry.forSource(size, width, height, aspect)
                val rotated = QnnInputGeometry.forSource(size, height, width, aspect)
                assertTrue(geometry.tensorWidth in 14..(2 * size) && geometry.tensorWidth % 14 == 0)
                assertTrue(geometry.tensorHeight in 14..(2 * size) && geometry.tensorHeight % 14 == 0)
                assertTrue(geometry.tensorWidth * geometry.tensorHeight <= size * size)
                assertTrue(geometry.contentWidth in 1..geometry.tensorWidth)
                assertTrue(geometry.contentHeight in 1..geometry.tensorHeight)
                assertTrue(geometry.matchesLayout(size, aspect))
                assertEquals((geometry.tensorWidth - geometry.contentWidth) / 2, geometry.left)
                assertEquals((geometry.tensorHeight - geometry.contentHeight) / 2, geometry.top)
                assertEquals(QnnInputGeometry(geometry.tensorHeight, geometry.tensorWidth, geometry.contentHeight,
                    geometry.contentWidth, geometry.top, geometry.left), rotated)
            }
        }
    }

    @Test fun malformedBudgetsAndSourceSizesAreRejectedBeforeAllocation() {
        assertThrows(IllegalArgumentException::class.java) { QnnInputGeometry.forSource(1037, 1920, 1080, QnnDepthAspect.ALIGNED) }
        assertThrows(IllegalArgumentException::class.java) { QnnInputGeometry.forSource(1036, 1920, 0, QnnDepthAspect.FIT) }
    }

    @Test fun recordedCropsAreValidatedAgainstTheirModelBudgetAndLayout() {
        val fit = QnnInputGeometry(1386, 770, 1369, 770, 8, 0)
        assertTrue(fit.matchesLayout(1036, QnnDepthAspect.FIT))
        assertFalse(fit.matchesLayout(1022, QnnDepthAspect.FIT))
        assertFalse(fit.matchesLayout(1036, QnnDepthAspect.ALIGNED))
        assertFalse(fit.matchesLayout(1036, QnnDepthAspect.SQUARE))
        assertFalse(fit.copy(left = 9).matchesLayout(1036, QnnDepthAspect.FIT))
        assertThrows(IllegalArgumentException::class.java) { fit.copy(left = Int.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { fit.copy(tensorWidth = 1387) }
    }

    @Test fun wideTallWarmsExactlyTwoWholeTensorsAndRoutesBySourceOrientation() {
        val landscape = QnnInputGeometry(1344, 756, 1344, 756, 0, 0)
        val portrait = QnnInputGeometry(756, 1344, 756, 1344, 0, 0)
        val wideTall = QnnDepthAspect.WIDE_TALL

        assertEquals(listOf(landscape, portrait), QnnInputGeometry.warmupGeometries(1036, wideTall))
        assertEquals(landscape, QnnInputGeometry.forSource(1036, 4032, 3024, wideTall))
        assertEquals(landscape, QnnInputGeometry.forSource(1036, 1024, 1024, wideTall))
        assertEquals(portrait, QnnInputGeometry.forSource(1036, 3024, 4032, wideTall))
        assertEquals(listOf(QnnInputGeometry(1036, 1036, 1036, 1036, 0, 0)),
            QnnInputGeometry.warmupGeometries(1036, QnnDepthAspect.SQUARE))
        assertTrue(QnnInputGeometry.warmupGeometries(1036, QnnDepthAspect.FIT).isEmpty())
        assertTrue(QnnInputGeometry.warmupGeometries(1036, QnnDepthAspect.ALIGNED).isEmpty())
    }

    @Test fun wideTallUsesTheLargestExactRatioKThatFitsEveryBudget() {
        val wideTall = QnnDepthAspect.WIDE_TALL
        for (size in 518..1036 step 14) {
            val (landscape, portrait) = QnnInputGeometry.warmupGeometries(size, wideTall)
            assertEquals(landscape.tensorWidth * 9, landscape.tensorHeight * 16)
            assertEquals(landscape.tensorWidth, portrait.tensorHeight)
            assertEquals(landscape.tensorHeight, portrait.tensorWidth)
            assertTrue(landscape.tensorWidth * landscape.tensorHeight <= size * size)
            assertTrue((landscape.tensorWidth + 16 * 14) * (landscape.tensorHeight + 9 * 14) > size * size)
            assertTrue(landscape.matchesLayout(size, wideTall))
            assertTrue(portrait.matchesLayout(size, wideTall))
        }

        assertFalse(QnnInputGeometry(1386, 770, 1386, 770, 0, 0).matchesLayout(1036, wideTall))
        assertFalse(QnnInputGeometry(1120, 630, 1120, 630, 0, 0).matchesLayout(1036, wideTall))
        assertFalse(QnnInputGeometry(1344, 756, 1320, 756, 12, 0).matchesLayout(1036, wideTall))
    }

    @Test fun wideTallModelIdRemainsReadableWhileActivePreferencesMigrate() {
        val config = QnnModelConfig(QnnDepthVariant.FP32_HEAD, 1036, QnnDepthAspect.WIDE_TALL)
        val id = qnnModelId(config)

        assertEquals("depth_anything_v2_small_qnn_fp32head_1036_widetall_v1", id)
        assertEquals(config, qnnModelConfig(id))
        assertEquals(qnnModelId(config.copy(aspect = QnnDepthAspect.PORTRAIT_PAIR)), activeDepthModelId(id))
        assertEquals("depth_anything_v2_small_qnn_fp32head_1036_aligned_v1", qnnModelId(config.copy(aspect = QnnDepthAspect.ALIGNED)))
        assertEquals("depth_anything_v2_small_qnn_fp32head_1036_fit_v1", qnnModelId(config.copy(aspect = QnnDepthAspect.FIT)))
    }

    @Test fun portraitPairWarmsBothPortraitShapesAndRotatesOnlyLandscapeSources() {
        val portraitPair = QnnDepthAspect.PORTRAIT_PAIR
        val slim = QnnInputGeometry(756, 1344, 756, 1344, 0, 0)
        val standard = QnnInputGeometry(882, 1176, 882, 1176, 0, 0)

        assertEquals(listOf(slim, standard), QnnInputGeometry.warmupGeometries(1036, portraitPair))
        assertEquals(slim, QnnInputGeometry.forSource(1036, 1080, 1920, portraitPair))
        assertEquals(slim.copy(rotation = 90), QnnInputGeometry.forSource(1036, 1920, 1080, portraitPair))
        assertEquals(standard, QnnInputGeometry.forSource(1036, 3024, 4032, portraitPair))
        assertEquals(standard.copy(rotation = 90), QnnInputGeometry.forSource(1036, 4032, 3024, portraitPair))
        assertEquals(standard, QnnInputGeometry.forSource(1036, 1024, 1024, portraitPair))
        assertEquals(slim, QnnInputGeometry.forSource(1036, 64, 100, portraitPair))
        assertEquals(standard, QnnInputGeometry.forSource(1036, 65, 100, portraitPair))
    }

    @Test fun portraitPairUsesFullPatchAlignedTensorsWithLargestRepresentableRatios() {
        val portraitPair = QnnDepthAspect.PORTRAIT_PAIR
        for (size in 518..1036 step 14) {
            val (slim, standard) = QnnInputGeometry.warmupGeometries(size, portraitPair)
            for ((layout, dimensions) in listOf(slim to (9 to 16), standard to (3 to 4))) {
                val (proportionWidth, proportionHeight) = dimensions
                assertEquals(layout.tensorWidth * proportionHeight, layout.tensorHeight * proportionWidth)
                assertTrue(layout.tensorWidth * layout.tensorHeight <= size * size)
                assertTrue((layout.tensorWidth + proportionWidth * 14) * (layout.tensorHeight + proportionHeight * 14) > size * size)
                assertTrue(layout.matchesLayout(size, portraitPair))
                assertEquals(0, layout.rotation)
            }
        }
    }

    @Test fun nativeLandscapeOverrideRequiresAnExplicitFlagAndKeepsTheChosenProportions() {
        val portraitPair = QnnDepthAspect.PORTRAIT_PAIR
        val portrait = QnnInputGeometry.forSource(1036, 4032, 3024, portraitPair)
        val native = portrait.withoutRotation()

        assertTrue(portrait.matchesLayout(1036, portraitPair))
        assertFalse(portrait.matchesLayout(1036, QnnDepthAspect.ALIGNED))
        assertEquals(QnnInputGeometry(1176, 882, 1176, 882, 0, 0), native)
        assertEquals(native, native.withoutRotation())
        assertFalse(native.matchesLayout(1036, portraitPair))
        assertTrue(native.matchesLayout(1036, portraitPair, allowNativeLandscapeOverride = true))
        assertFalse(native.copy(rotation = 90).matchesLayout(1036, portraitPair, allowNativeLandscapeOverride = true))
        assertFalse(native.copy(contentWidth = 1175).matchesLayout(1036, portraitPair, allowNativeLandscapeOverride = true))
        assertThrows(IllegalArgumentException::class.java) { native.copy(rotation = 180) }
    }
}
