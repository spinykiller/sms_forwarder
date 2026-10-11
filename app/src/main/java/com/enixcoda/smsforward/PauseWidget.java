package com.enixcoda.smsforward;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Home-screen widget: shows whether forwarding is on; tap to pause or resume. */
public class PauseWidget extends AppWidgetProvider {
    private static final String ACTION_TOGGLE = "com.enixcoda.smsforward.TOGGLE_PAUSE";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        updateAll(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_TOGGLE.equals(intent.getAction())) Pause.toggle(context);
        else super.onReceive(context, intent);
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, PauseWidget.class));
        if (ids.length == 0) return;
        boolean paused = Dispatcher.isPaused(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_pause);
        views.setTextViewText(R.id.widget_text, context.getString(paused ? R.string.widget_paused : R.string.widget_on));
        views.setFloat(R.id.widget_icon, "setAlpha", paused ? 0.4f : 1f);
        Intent toggle = new Intent(context, PauseWidget.class).setAction(ACTION_TOGGLE);
        views.setOnClickPendingIntent(R.id.widget_root,
                PendingIntent.getBroadcast(context, 0, toggle, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.updateAppWidget(ids, views);
    }
}
