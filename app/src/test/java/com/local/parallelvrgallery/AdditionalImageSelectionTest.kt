package com.local.parallelvrgallerypro

import org.junit.Assert.assertEquals
import org.junit.Test

class AdditionalImageSelectionTest {
    private val parameters = "depth_anything_v2.tflite_depthV6_s40_b3_f10_inv0_m6000_t4_gpu0_ACCURATE_UNSET_force0"
    private val encoder = "_eyesJpegQ100V1"
    private val target = parameters + encoder

    @Test fun existingBaseOrCopyOnlyResultCoversItsSourceInTheTargetAlbum() {
        val sources = listOf("copy", "different", "base", "missing", "different")
        val results = listOf(
            "copy" to "${parameters}_takeff0042ab$encoder",
            "different" to target.replace("_s40_", "_s42_"),
            "base" to target,
            "not-selected" to target,
        )

        assertEquals(listOf("different", "missing"), additionalImageSourceKeys(sources, target, results))
    }

    @Test fun pendingResultsCoverOnlyTheirOwnSourceAndParameterAlbum() {
        val pending = listOf(
            "pending-base" to target,
            "pending-copy" to "${parameters}_take002ab3cc$encoder",
            "pending-elsewhere" to target.replace("depthV6", "depthV7"),
            "pending-elsewhere" to "${parameters}_take002ab3cc_dw2_encoderV15",
        )
        val sources = listOf("pending-elsewhere", "pending-base", "new", "pending-copy", "new")

        assertEquals(listOf("pending-elsewhere", "new"), additionalImageSourceKeys(sources, target, emptyList(), pending))
    }

    @Test fun differentNewParametersCanReuseAnOriginalWithoutDuplicateJobs() {
        val sources = listOf("first", "second", "first")
        val existing = listOf("first" to target, "second" to "${parameters}_take002ab3cc$encoder")
        val pending = listOf("first" to target)
        val changedTarget = target.replace("_inv0_", "_inv1_")

        assertEquals(listOf("first", "second"), additionalImageSourceKeys(sources, changedTarget, existing, pending))
    }
}
