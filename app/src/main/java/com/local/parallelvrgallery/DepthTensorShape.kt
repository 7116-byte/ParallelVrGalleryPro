package com.local.parallelvrgallerypro

/** Explicit model contract, separate from output-image sizing and depth interpolation. */
internal object DepthTensorShape {
    fun inputSize(input: IntArray, output: IntArray): Int {
        require(input.size == 4 && input[0] == 1 && input[3] == 3 && input[1] == input[2]) { "Expected square NHWC RGB model" }
        val size = input[1]
        require(size == 518 || size == 770) { "Unsupported inference size: $size" }
        require(output.contentEquals(intArrayOf(1, size, size, 1)) || output.contentEquals(intArrayOf(1, size, size))) { "Model output does not match inference size" }
        return size
    }

    fun map(values: FloatArray): DepthMap {
        val size = when (values.size) {
            518 * 518 -> 518
            770 * 770 -> 770
            else -> error("Unexpected model depth length: ${values.size}")
        }
        return DepthMap(values, size, size)
    }
}
