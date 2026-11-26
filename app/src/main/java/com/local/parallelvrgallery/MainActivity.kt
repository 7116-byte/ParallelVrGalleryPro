package com.local.parallelvrgallerypro

import android.app.Application
import android.os.Bundle
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        GalleryRuntime.obtain(application).setUiVisible(true)
    }
    override fun onStop() {
        GalleryRuntime.current()?.setUiVisible(false)
        super.onStop()
    }
    override fun onDestroy() {
        if (!isChangingConfigurations) GalleryRuntime.current()?.onUiOwnerRemoved()
        super.onDestroy()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudioTheme {
                Surface(Modifier.fillMaxSize()) {
                    GalleryApp(GalleryRuntime.obtain(application))
                }
            }
        }
    }
}
