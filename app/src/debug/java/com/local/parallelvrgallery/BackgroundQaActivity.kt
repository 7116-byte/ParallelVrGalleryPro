package com.local.parallelvrgallerypro

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Explicit adb-only synthetic lifecycle harness. Never included in the production APK. */
class BackgroundQaActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var attempts = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = File(getExternalFilesDir(null), "PVG_QA_iteration1100")
        check(File(root, "AUTHORIZED").readText().trim() == "synthetic-only-1100")
        val engine = GalleryRuntime.obtain(application, resumeFromService = intent.getStringExtra("command") == "restore")
        fun execute() {
            val state = engine.uiState.value
            if (state.loading) { check(attempts++ < 200); handler.postDelayed({ execute() }, 100); return }
            val allowed = setOf("PVG_QA_background1100_A.mp4", "PVG_QA_background1100_B.mp4", "PVG_QA_background1100_C.mp4")
            check(state.videoJobs.none { VideoCommandPolicy.canPause(it.state) && it.item.displayName !in allowed }) { "Non-QA work present" }
            val suffix = intent.getStringExtra("suffix")
            val indexes = state.photos.mapIndexedNotNull { index, item -> index.takeIf { item.displayName in allowed && (suffix == null || item.displayName == "PVG_QA_background1100_$suffix.mp4") } }
            when (intent.getStringExtra("command")) {
                "start" -> { check(indexes.isNotEmpty()); engine.startVideos(indexes) }
                "pause" -> engine.pauseVideos(indexes)
                "timeout" -> engine.onBackgroundTimeLimit()
                "trim" -> engine.trimIdleGenerationMemory()
                "scale" -> engine.updateSettings(state.settings.copy(depthScale = 37f))
                "snapshot" -> Unit
                "restore" -> Unit // Explicitly exercise the same headless admission path if a vendor blocks restart.
                "dismiss" -> {
                    val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                    manager.activeNotifications.filter {
                        it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() in allowed
                    }.forEach { manager.cancel(it.id) }
                }
                else -> error("Unknown QA command")
            }
            engine.flushWorkForBackground()
            handler.postDelayed({
                val current = engine.uiState.value
                val rows = JSONArray()
                current.photos.filter { it.displayName in allowed }.forEach { item ->
                    val job = current.videoJobs.firstOrNull { it.item.cacheKey == item.cacheKey }
                    rows.put(JSONObject().put("key", item.cacheKey).put("name", item.displayName).put("state", (current.videoStates[item.cacheKey] ?: VideoVrState.NORMAL).name).put("progress", job?.progress ?: 0f).put("frame", job?.currentFrame ?: 0).put("version", job?.cacheVersion.orEmpty()).put("error", job?.error.orEmpty()))
                }
                File(root, "snapshot.json").writeText(JSONObject().put("pid", android.os.Process.myPid()).put("rows", rows).put("message", current.message.orEmpty()).toString(2))
                finish()
            }, 400)
        }
        execute()
    }
}
