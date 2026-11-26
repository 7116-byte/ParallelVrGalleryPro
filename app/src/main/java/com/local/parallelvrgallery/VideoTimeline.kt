package com.local.parallelvrgallerypro

/** Decode-order samples can contain B-frames; output is indexed in presentation order (Rust core). */
internal class VideoTimeline(samples: LongArray) {
    val timestampsUs: LongArray = RustCore.sortVideoTimeline(samples)
    val size: Int get() = timestampsUs.size
    operator fun get(index: Int): Long = timestampsUs[index]
    fun indexOf(timestampUs: Long): Int = timestampsUs.binarySearch(timestampUs)
}
