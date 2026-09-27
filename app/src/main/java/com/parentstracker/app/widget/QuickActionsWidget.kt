package com.parentstracker.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.parentstracker.app.R
import com.parentstracker.app.appContainer
import com.parentstracker.app.notify.ActionReceiver
import com.parentstracker.app.ui.MainActivity
import com.parentstracker.app.util.Geo
import kotlinx.coroutines.launch

/**
 * Home-screen widget with two big buttons: "I'm OK" (checks in right away) and "SOS"
 * (opens the app's SOS confirmation, so a pocket tap can't send a false alarm).
 */
class QuickActionsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        context.appContainer.appScope.launch {
            try {
                render(context, manager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Redraws every placed widget, e.g. after a check-in. */
        suspend fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, QuickActionsWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val settings = context.appContainer.settings.current()
            val status = if (settings.checkInMessage != null && settings.checkInAt > 0) {
                "Last check-in: ${settings.checkInMessage} · ${Geo.timeAgo(settings.checkInAt, System.currentTimeMillis())}"
            } else {
                context.getString(R.string.widget_status_default)
            }
            val sos = PendingIntent.getActivity(
                context,
                1,
                Intent(Intent.ACTION_VIEW, Uri.parse("parentstracker://sos"), context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val open = PendingIntent.getActivity(
                context,
                2,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_quick_actions).apply {
                setTextViewText(R.id.widget_status, status)
                setOnClickPendingIntent(R.id.widget_ok, ActionReceiver.quickOk(context))
                setOnClickPendingIntent(R.id.widget_sos, sos)
                setOnClickPendingIntent(R.id.widget_title, open)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
