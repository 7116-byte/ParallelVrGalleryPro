package com.local.parallelvrgallerypro

import java.io.File
import java.util.concurrent.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VideoSegmentStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun open(forceDirectory: Boolean = true) =
        VideoSegmentStore(File(tmp.root, "segments"), File(tmp.root, "frames"), 7, 3) { forceDirectory }

    private fun checkpoint(value: Float) = VideoSegmentStore.Checkpoint(2, 2, FloatArray(4) { value })

    private fun legacyFile(name: String): File = File(File(tmp.root, "frames").also { it.mkdirs() }, name).also { it.writeText(name) }

    @Test fun planHasFixedRangesAndAShortLastSegment() {
        val store = open()
        assertEquals(listOf(VideoSegmentStore.Range(0, 0, 3), VideoSegmentStore.Range(1, 3, 6), VideoSegmentStore.Range(2, 6, 7)), store.ranges)
        assertThrows(IllegalStateException::class.java) { store.begin(store.ranges[1]) }
        assertThrows(IllegalArgumentException::class.java) { store.begin(VideoSegmentStore.Range(0, 0, 4)) }
    }

    @Test fun cancellationAfterPublishIsRecoverableBeforeAnyJpegsWereReclaimed() {
        val reclaimedLeft = legacyFile("left_000002.jpg")
        val reclaimedRight = legacyFile("right_000002.jpg")
        val nextRange = legacyFile("left_000003.jpg")
        val legacyWebp = legacyFile("left_000002.webp")
        val legacySbs = legacyFile("frame_000002.jpg")
        val marker = legacyFile("000002.complete")
        val store = open()
        val diagnostic = VideoDepthGeometry(756, 1344, 1280, 720, 1344, 756, inputRotation = 90, workingLongEdge = 1280)
        var checked = 0
        store.begin(store.ranges.first()).use { stage ->
            stage.mediaFile.writeText("encoded MP4")
            assertThrows(CancellationException::class.java) {
                store.publish(stage, 2560, 720, checkpoint(0.5f), diagnostic) {
                    if (++checked == 2) throw CancellationException()
                }
            }
        }
        assertTrue(reclaimedLeft.isFile && reclaimedRight.isFile)

        val resumed = open()
        val segment = resumed.recover().single()
        assertEquals("segment_000000", segment.mediaFile.parentFile?.name)
        assertEquals(2560 to 720, segment.width to segment.height)
        assertEquals(diagnostic, segment.geometry)
        assertArrayEquals(FloatArray(4) { 0.5f }, resumed.readCheckpoint(segment)?.values, 0f)
        assertFalse(reclaimedLeft.exists() || reclaimedRight.exists())
        assertTrue(nextRange.isFile && legacyWebp.isFile && legacySbs.isFile && marker.isFile)
    }

    @Test fun onlyTheNewestCommittedSegmentRetainsACheckpoint() {
        val store = open()
        val first = store.begin(store.ranges[0]).use { stage ->
            stage.mediaFile.writeText("a")
            store.publish(stage, 2560, 720, checkpoint(0.25f))
        }
        val oldPlane = File(first.mediaFile.parentFile, "temporal-depth.bin")
        assertTrue(oldPlane.exists())
        val second = store.begin(store.ranges[1]).use { stage ->
            stage.mediaFile.writeText("b")
            store.publish(stage, 2560, 720, checkpoint(0.75f))
        }
        assertFalse(oldPlane.exists())
        assertThrows(IllegalArgumentException::class.java) { store.readCheckpoint(first) }
        val resumed = open()
        assertEquals(listOf(first, second), resumed.recover())
        assertArrayEquals(FloatArray(4) { 0.75f }, resumed.readCheckpoint(second)?.values, 0f)
    }

    @Test fun invalidMediaOrNewestCheckpointNeverAuthorizesReclaim() {
        val firstFrame = legacyFile("left_000000.jpg")
        val store = open(forceDirectory = false)
        val completed = store.begin(store.ranges.first()).use { stage ->
            stage.mediaFile.writeText("complete")
            store.publish(stage, 2560, 720, checkpoint(0.25f))
        }
        assertTrue(firstFrame.isFile)
        val checkpoint = File(completed.mediaFile.parentFile, "temporal-depth.bin")
        val bytes = checkpoint.readBytes()
        checkpoint.writeBytes(bytes.copyOf().also { it[it.lastIndex] = 1 })
        assertThrows(IllegalStateException::class.java) { open().recover() }
        assertTrue(firstFrame.isFile)
        checkpoint.writeBytes(bytes)
        completed.mediaFile.writeText("modified")
        assertThrows(IllegalStateException::class.java) { open().recover() }
        assertTrue(firstFrame.isFile)
    }

    @Test fun unfinishedStagingDoesNotCountAsACompletedSegment() {
        val store = open()
        val unfinished = store.begin(store.ranges.first())
        unfinished.mediaFile.writeText("partial")
        val cached = legacyFile("right_000000.jpg")
        val resumed = open()
        assertTrue(resumed.recover().isEmpty())
        assertFalse(unfinished.mediaFile.exists())
        assertTrue(cached.isFile)
        assertThrows(IllegalArgumentException::class.java) { VideoSegmentStore.Checkpoint(2, 2, FloatArray(3)) }
        assertThrows(IllegalArgumentException::class.java) { VideoSegmentStore.Checkpoint(2, 2, FloatArray(4) { Float.NaN }) }
    }
}
