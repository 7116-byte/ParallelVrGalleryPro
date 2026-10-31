package com.local.parallelvrgallerypro

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private fun validate(tag: String = "v1.3.00", code: Long = 10300, packageName: String = "com.local.parallelvrgallerypro", signers: List<String> = listOf(UpdatePolicy.CERTIFICATE_SHA256), expected: String? = "v1.3.00") =
        UpdatePolicy.validate(packageName, "com.local.parallelvrgallerypro", tag, code, "v1.2.00", 10200, expected, signers)

    @Test fun versionSegmentsAreNumericAndPreserveZeroPaddedConvention() {
        assertTrue(UpdatePolicy.compare("v1.10.00", "v1.9.99") > 0)
        assertEquals(0, UpdatePolicy.compare("1.3.00", "v1.3.0"))
        assertTrue(UpdatePolicy.compare("v1.3.01", "v1.3.00") > 0)
        assertEquals("app-debug.apk", UpdatePolicy.APK_NAME)
    }
    @Test fun expectedReleaseAndOriginalSignerAreAccepted() = validate()
    @Test fun downloadProgressIsPerFileRatherThanPerMirror() {
        assertEquals(0.5f, UpdatePolicy.downloadFraction(50, 100), 0f)
        assertEquals(1f, UpdatePolicy.downloadFraction(101, 100), 0f)
    }
    @Test fun unknownDownloadLengthStaysIndeterminate() {
        assertEquals(-1f, UpdatePolicy.downloadFraction(100, -1), 0f)
        assertEquals(-1f, UpdatePolicy.downloadFraction(100, 0), 0f)
    }
    @Test(expected = IllegalArgumentException::class) fun oldVersionCannotBeInstalled() = validate("v1.2.00", 10200)
    @Test(expected = IllegalArgumentException::class) fun ordinaryPackageCannotReplacePro() = validate(packageName = "com.local.parallelvrgallery")
    @Test(expected = IllegalArgumentException::class) fun anotherReleaseCannotBeSubstituted() = validate(expected = "v1.4.00")
    @Test(expected = IllegalArgumentException::class) fun newSystemDebugSignerCannotReplaceOriginal() = validate(signers = listOf("00"))
    @Test(expected = IllegalArgumentException::class) fun unsignedOrMultiSignerApkIsRejected() = validate(signers = emptyList())
    @Test(expected = IllegalArgumentException::class) fun prereleaseCannotMasqueradeAsStable() { UpdatePolicy.compare("v1.3.00-beta", "v1.2.00") }
}
