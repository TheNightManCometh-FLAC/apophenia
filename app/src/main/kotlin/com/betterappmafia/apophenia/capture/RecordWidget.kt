package com.betterappmafia.apophenia.capture

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.betterappmafia.apophenia.MainActivity
import com.betterappmafia.apophenia.R

/**
 * The home-screen ● REC (plan §2.2). It opens the app, which starts the take itself: Android 14+
 * only lets a microphone foreground service start from something the user is looking at.
 */
class RecordWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_record)
        views.setOnClickPendingIntent(R.id.widget_record, PendingIntent.getActivity(
            context, 1, recordIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ))
        manager.updateAppWidget(ids, views)
    }

    companion object {
        const val ACTION_RECORD = "com.betterappmafia.apophenia.RECORD"
        fun recordIntent(context: Context) = Intent(context, MainActivity::class.java)
            .setAction(ACTION_RECORD)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
