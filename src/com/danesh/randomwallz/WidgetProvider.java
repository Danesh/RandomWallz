package com.danesh.randomwallz;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class WidgetProvider extends AppWidgetProvider {

    public static final String FORCED_REFRESH = "forced";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        // Set refresh intent
        Intent intent = new Intent(context, RandomWallpaper.class);
        intent.setAction("UPDATE_WIDGET");
        intent.putExtra(FORCED_REFRESH, true);
        PendingIntent pendingIntent = PendingIntent.getService(context,
                0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.refresh, pendingIntent);
        // Set configuration intent
        intent = new Intent(context, Configuration.class);
        pendingIntent = PendingIntent.getActivity(context,
                0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.config, pendingIntent);
        appWidgetManager.updateAppWidget(appWidgetIds, remoteViews);
    }
}
