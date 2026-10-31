package com.local.parallelvrgallerypro

/** Shared release identity checks; Android signing certificates are inspected before install. */
internal object UpdatePolicy {
    const val APK_NAME = "app-debug.apk" // Kept for every previously released updater.
    const val CERTIFICATE_SHA256 = "c51c08290903e65d2e99fe35ceffd44b637f04cc5bbdd5ea9ecb8847d4ea7359"

    fun downloadFraction(read: Long, total: Long): Float =
        if (total <= 0L) -1f else (read.toDouble() / total).toFloat().coerceIn(0f, 1f)

    fun compare(left: String, right: String): Int {
        fun parts(value: String): List<Int> {
            val clean = value.removePrefix("v").removePrefix("V")
            require(Regex("[0-9]+(?:\\.[0-9]+)*").matches(clean)) { "Invalid stable version: $value" }
            return clean.split('.').map { it.toInt() }
        }
        val a = parts(left); val b = parts(right)
        repeat(maxOf(a.size, b.size)) { index ->
            val comparison = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    fun validate(packageName: String, expectedPackage: String, tag: String, code: Long, currentTag: String, currentCode: Long, expectedTag: String?, certificateDigests: List<String>) {
        require(packageName == expectedPackage) { "APK package mismatch" }
        require(compare(tag, currentTag) > 0 && code > currentCode) { "APK is not newer" }
        require(expectedTag == null || compare(tag, expectedTag) == 0) { "APK does not match the selected release" }
        require(certificateDigests.size == 1 && certificateDigests.single().lowercase() == CERTIFICATE_SHA256) { "APK signing certificate mismatch" }
    }
}
