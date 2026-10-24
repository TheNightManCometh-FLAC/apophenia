package com.betterappmafia.apophenia.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.betterappmafia.apophenia.MainActivity

/**
 * Keeps the process in the foreground while a take records, so it carries on with the screen off
 * or the app in the background (plan §10.1). It does no audio itself; the engine does.
 * Started only from the app while it's on screen, as Android 14+ requires for microphone services.
 */
class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Recording", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Recording")
            .setContentText("Apophenia is recording. Tap to return.")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        // If Android refuses (the app wasn't on screen), the take still records while the app stays open.
        runCatching { startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "recording"
        private const val ID = 1
        private const val ACTION_STOP = "com.betterappmafia.apophenia.STOP_RECORDING_SERVICE"
        fun intent(context: Context) = Intent(context, RecordingService::class.java)
        fun stopIntent(context: Context) = intent(context).setAction(ACTION_STOP)
    }
}
