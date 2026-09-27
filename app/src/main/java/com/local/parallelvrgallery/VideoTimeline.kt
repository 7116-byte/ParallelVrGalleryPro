package com.local.parallelvrgallerypro

/** Decode-order samples can contain B-frames; output is indexed in presentation order. */
internal class VideoTimeline(samples: LongArray) {
    val timestampsUs: LongArray = samples.sortedArray()
    init {
        require(timestampsUs.isNotEmpty()) { "Video has no samples" }
        require(timestampsUs[0] >= 0L) { "Negative presentation timestamp" }
        require(timestampsUs.asSequence().zipWithNext().all { (a, b) -> b > a }) {
            "Duplicate video timestamps cannot be mapped safely to cached frames"
        }
    }
    val size: Int get() = timestampsUs.size
    operator fun get(index: Int): Long = timestampsUs[index]
    fun indexOf(timestampUs: Long): Int = timestampsUs.binarySearch(timestampUs)
}
