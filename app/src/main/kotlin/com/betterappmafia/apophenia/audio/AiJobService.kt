package com.betterappmafia.apophenia.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.betterappmafia.apophenia.MainActivity
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps the process in the foreground while an AI job runs (plan §14.2: stems finish in the background
 * without Android killing them). It does no work itself; the job workers do. [AiWork.begin] is called when
 * a job is queued, which only happens from a tap in the app, as Android requires for starting it.
 */
class AiJobService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "AI jobs", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Working on the phone")
            .setContentText("Apophenia is separating or transcribing. Tap to return.")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        // Android 15 has a type for exactly this; older versions use data sync. If it's refused, the job still runs while the app is open.
        val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        runCatching { startForeground(ID, n, type) }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "ai_jobs"
        private const val ID = 2
        private const val ACTION_STOP = "com.betterappmafia.apophenia.STOP_AI_JOB_SERVICE"
        fun intent(context: Context) = Intent(context, AiJobService::class.java)
        fun stopIntent(context: Context) = intent(context).setAction(ACTION_STOP)
    }
}

/** How many AI jobs are queued or running across the workers; the service runs while there are any. */
object AiWork {
    private val count = AtomicInteger()

    fun begin(context: Context) {
        if (count.getAndIncrement() == 0) runCatching { context.startForegroundService(AiJobService.intent(context)) }
    }

    fun end(context: Context) {
        if (count.decrementAndGet() <= 0) {
            count.set(0)
            runCatching { context.startService(AiJobService.stopIntent(context)) }
        }
    }
}
