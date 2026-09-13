package io.github.yisus.nexo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class NexoWidgetProvider : AppWidgetProvider() {
    
    companion object {
        const val ACTION_PLAY_PAUSE = "io.github.yisus.avenor.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "io.github.yisus.avenor.ACTION_NEXT"
        const val ACTION_PREV = "io.github.yisus.avenor.ACTION_PREV"
        const val ACTION_OPEN_NOW_PLAYING = "io.github.yisus.avenor.ACTION_OPEN_NOW_PLAYING"
        const val ACTION_OPEN_LIBRARY = "io.github.yisus.avenor.ACTION_OPEN_LIBRARY"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.nexo_widget_layout)
            
            views.setOnClickPendingIntent(R.id.widget_btn_play, getPendingIntent(context, ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_btn_next, getPendingIntent(context, ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.widget_btn_prev, getPendingIntent(context, ACTION_PREV))
            
            // Open Now Playing when tapping Album Art
            val npIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_NOW_PLAYING
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(R.id.widget_album_art, PendingIntent.getActivity(context, 1, npIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            
            // Open Library when tapping Title
            val libIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_LIBRARY
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(R.id.widget_title, PendingIntent.getActivity(context, 2, libIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_PLAY_PAUSE || action == ACTION_NEXT || action == ACTION_PREV) {
            val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                this.action = action
            }
            context.startForegroundService(serviceIntent)
        }
    }

    private fun getPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, NexoWidgetProvider::class.java)
        intent.action = action
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
