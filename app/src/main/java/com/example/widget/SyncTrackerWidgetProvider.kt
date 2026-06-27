package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class SyncTrackerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Build updates on a background thread using coroutines
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val tasks = database.activityDao().getAllTasks().firstOrNull() ?: emptyList()
                val dailyTasks = tasks.filter { it.category == "DAILY_TRACKER" }

                val total = dailyTasks.size
                val completed = dailyTasks.count { it.isCompleted }
                val pct = if (total > 0) (completed * 100) / total else 0

                for (appWidgetId in appWidgetIds) {
                    val remoteViews = RemoteViews(context.packageName, R.layout.sync_tracker_widget)

                    // 1. Update Title and Progress
                    remoteViews.setTextViewText(R.id.widget_progress_text, "$completed/$total ($pct%)")
                    remoteViews.setProgressBar(R.id.widget_progress_bar, 100, pct, false)

                    // 2. Hide or show empty states
                    if (total > 0) {
                        remoteViews.setViewVisibility(R.id.widget_empty_text, View.GONE)
                    } else {
                        remoteViews.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
                    }

                    // 3. Populate rows (Max 4 for size constraints)
                    val rowIds = listOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3, R.id.widget_row_4)
                    val textIds = listOf(R.id.widget_row_1_text, R.id.widget_row_2_text, R.id.widget_row_3_text, R.id.widget_row_4_text)
                    val checkIds = listOf(R.id.widget_row_1_check, R.id.widget_row_2_check, R.id.widget_row_3_check, R.id.widget_row_4_check)

                    for (i in 0 until 4) {
                        if (i < dailyTasks.size) {
                            val task = dailyTasks[i]
                            remoteViews.setViewVisibility(rowIds[i], View.VISIBLE)
                            remoteViews.setTextViewText(textIds[i], task.title)
                            remoteViews.setTextViewText(checkIds[i], if (task.isCompleted) "☑" else "☐")
                        } else {
                            remoteViews.setViewVisibility(rowIds[i], View.GONE)
                        }
                    }

                    // 4. Show more text indicator if there are > 4 tasks
                    if (dailyTasks.size > 4) {
                        remoteViews.setViewVisibility(R.id.widget_more_text, View.VISIBLE)
                        remoteViews.setTextViewText(R.id.widget_more_text, "+ ${dailyTasks.size - 4} more trackers in app")
                    } else {
                        remoteViews.setViewVisibility(R.id.widget_more_text, View.GONE)
                    }

                    // 5. Click intent to launch MainActivity
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    remoteViews.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /**
         * Helper utility to force update all active widgets from anywhere in the app
         */
        fun triggerUpdate(context: Context) {
            val intent = Intent(context, SyncTrackerWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, SyncTrackerWidgetProvider::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
