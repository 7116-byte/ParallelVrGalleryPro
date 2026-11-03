package com.local.parallelvrgallerypro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.Closeable

/** The notification belongs to real application-owned work, including sticky system recreation. */
class VideoGenerationForegroundService : Service() {
    private var expectedStop = false
    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "VR 后台生成", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            expectedStop = true; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("VR 生成运行中")
            .setContentText("正在处理已开始的任务；点击查看进度")
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).build()
        try {
            val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            startForeground(SERVICE_NOTIFICATION_ID, notification, type)
        } catch (error: RuntimeException) {
            GalleryRuntime.current()?.onForegroundUnavailable(error.message.orEmpty())
            expectedStop = true; stopSelf(); return START_NOT_STICKY
        }
        // Promote first; model, cache and queue loading must never delay the foreground deadline.
        try {
            val engine = GalleryRuntime.obtain(application, resumeFromService = intent == null)
            if (intent == null) engine.stopServiceIfIdleAfterRestore()
        } catch (error: RuntimeException) {
            expectedStop = true; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY
        }
        return START_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        GalleryRuntime.current()?.onBackgroundTimeLimit()
        expectedStop = true; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        GalleryRuntime.current()?.flushWorkForBackground()
        super.onTaskRemoved(rootIntent)
    }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == TRIM_MEMORY_RUNNING_LOW || level == TRIM_MEMORY_RUNNING_CRITICAL || level >= TRIM_MEMORY_BACKGROUND)
            GalleryRuntime.current()?.trimIdleGenerationMemory()
    }
    override fun onDestroy() {
        if (!expectedStop) GalleryRuntime.current()?.onForegroundServiceDestroyed()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val ACTION_START = "com.local.parallelvrgallerypro.video.START"
        const val ACTION_STOP = "com.local.parallelvrgallerypro.video.STOP"
        private const val CHANNEL_ID = "video_vr_foreground"
        private const val SERVICE_NOTIFICATION_ID = 9117
    }
}

internal class VideoGenerationKeepAlive(context: Context) {
    private val app = context.applicationContext
    private val lock = (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ParallelVrGalleryPro:Generation").apply { setReferenceCounted(false) }
    private val leases = WorkLease(start = {
        // Fail visibly instead of silently running without foreground protection.
        ContextCompat.startForegroundService(app, Intent(app, VideoGenerationForegroundService::class.java).setAction(VideoGenerationForegroundService.ACTION_START))
        try { if (!lock.isHeld) lock.acquire(12 * 60 * 60 * 1000L) }
        catch (error: RuntimeException) { app.stopService(Intent(app, VideoGenerationForegroundService::class.java)); throw error }
    }, stop = {
        if (lock.isHeld) lock.release()
        app.stopService(Intent(app, VideoGenerationForegroundService::class.java))
    })
    fun acquire(): Closeable = leases.acquire()
    fun active(): Boolean = leases.active()
    fun stopAll() = leases.stopAll()
}
