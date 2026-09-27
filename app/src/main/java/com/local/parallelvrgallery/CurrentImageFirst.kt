package com.local.parallelvrgallerypro

internal class CurrentImageFirst {
    private var center: String? = null
    private var started = false

    fun select(key: String?) { center = key; started = false }
    fun started(key: String) { if (center == key) started = true }
    fun permits(key: String, cached: Boolean): Boolean = center == key && (cached || started)
}
