package com.local.parallelvrgallerypro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GeneratedAlbumGroupingTest {
    private val parameters = "depth_anything_v2.tflite_depthV6_s40_b3_f10_inv0_m6000_t4_gpu0_ACCURATE_UNSET_force0"
    private val encoder = "_eyesJpegQ100V1"

    @Test fun historicalImageCopiesShareOneAlbumWithTheBaseResult() {
        val base = parameters + encoder
        val versions = listOf(base, "${parameters}_take0123abcd$encoder", "${parameters}_takedef45678$encoder")

        assertEquals(mapOf(base to versions), versions.groupBy(::generatedAlbumKey))
        assertEquals(3, versions.toSet().size)
    }

    @Test fun differencesInGenerationParametersRemainInTheirAlbumKeys() {
        val otherParameters = listOf(
            parameters,
            parameters.replace("_s40_", "_s41_"),
            parameters.replace("depthV6", "depthV7"),
            parameters.replace("depth_anything_v2.tflite", "depth_anything_takeabc01234_qnn_v1"),
            "${parameters}_du1_m1024_ic_ors",
            "${parameters}_eq1050",
            "${parameters}_di1",
            "${parameters}_du1_m0_il_osr_eq1050_di1",
        )
        val shared = otherParameters.map { generatedAlbumKey("${it}_takeab12cd34$encoder") }

        assertEquals(otherParameters.map { it + encoder }, shared)
        assertEquals(otherParameters.size, shared.toSet().size)
    }

    @Test fun nonWriterSuffixesAreLeftUntouched() {
        val unrelated = listOf(
            "default",
            "legacy_takeab12cd34$encoder",
            "${parameters}_takeAB12CD34$encoder",
            "${parameters}_takeab12cd3$encoder",
            "${parameters}_takeab12cd34e$encoder",
            "${parameters}_takeab12cd3g$encoder",
            "${parameters}_takeab12cd34_takedeadbeef$encoder",
            "${parameters}_takeab12cd34_dw2_encoderV15",
            "${parameters}_takeab12cd34$encoder.extra",
        )

        unrelated.forEach { assertEquals(it, generatedAlbumKey(it)) }
        assertNotEquals(generatedAlbumKey(unrelated[1]), generatedAlbumKey("${parameters}_takeab12cd34$encoder"))
    }
}
