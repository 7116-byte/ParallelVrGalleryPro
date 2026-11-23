package com.local.parallelvrgallerypro

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VideoSegmentTimestampsTest {
    private val timeline = VideoTimeline(longArrayOf(300L, 25_900L, 66_731L, 100_064L, 153_455L))

    @Test fun restoresGlobalVfrTimestampsAfterSegmentRebaseAndRounding() {
        val timestamps = VideoSegmentTimestamps(segment(2, 3), timeline)
        assertEquals(66_731L, timestamps.next(0L, sync = true))
        assertEquals(100_064L, timestamps.next(33_339L, sync = false))
        assertEquals(153_455L, timestamps.next(86_722L, sync = false))
        timestamps.checkComplete()
    }

    @Test fun rejectsGapsOverlapAndMissingTail() {
        checkVideoSegmentCoverage(listOf(segment(0, 2), segment(2, 3)), timeline.size)
        assertThrows(IllegalArgumentException::class.java) {
            checkVideoSegmentCoverage(listOf(segment(0, 2), segment(3, 2)), timeline.size)
        }
        assertThrows(IllegalArgumentException::class.java) {
            checkVideoSegmentCoverage(listOf(segment(0, 2), segment(1, 3)), timeline.size)
        }
        assertThrows(IllegalArgumentException::class.java) {
            checkVideoSegmentCoverage(listOf(segment(0, 2)), timeline.size)
        }
    }

    @Test fun rejectsUnsyncedStartsReorderedFramesAndWrongSampleCounts() {
        assertThrows(IllegalStateException::class.java) {
            VideoSegmentTimestamps(segment(0, 1), timeline).next(0L, sync = false)
        }
        val reordered = VideoSegmentTimestamps(segment(2, 3), timeline)
        reordered.next(66_731L, sync = true)
        reordered.next(100_064L, sync = false)
        assertThrows(IllegalStateException::class.java) { reordered.next(90_000L, sync = false) }
        assertThrows(IllegalStateException::class.java) { reordered.checkComplete() }

        val tooMany = VideoSegmentTimestamps(segment(0, 1), timeline)
        tooMany.next(0L, sync = true)
        assertThrows(IllegalStateException::class.java) { tooMany.next(25_600L, sync = false) }
    }

    @Test fun rejectsAFrameAtWrongPresentationTime() {
        val timestamps = VideoSegmentTimestamps(segment(0, 3), timeline)
        timestamps.next(0L, sync = true)
        assertThrows(IllegalStateException::class.java) { timestamps.next(26_000L, sync = false) }
    }

    private fun segment(first: Int, count: Int) = VideoSegment(File("segment-$first.mp4"), first, count)
}
