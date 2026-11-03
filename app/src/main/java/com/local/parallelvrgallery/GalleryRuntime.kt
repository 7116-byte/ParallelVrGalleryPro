package com.local.parallelvrgallerypro

import android.app.Application

/** Application-owned work state: closing an Activity no longer destroys the active queue owner. */
internal object GalleryRuntime {
    @Volatile private var instance: GalleryViewModel? = null
    @Synchronized fun obtain(app: Application, resumeFromService: Boolean = false): GalleryViewModel {
        instance?.let { return it }
        val admitted = if (resumeFromService) BackgroundRunLedger(
            java.io.File(app.getExternalFilesDir(null), "queue_index/background_run.txt")
        ).claimSystemRestart(System.currentTimeMillis()) else emptySet()
        return GalleryViewModel(app, admitted).also { instance = it }
    }
    fun current(): GalleryViewModel? = instance
}
