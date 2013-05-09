package com.danesh.randomwallz;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;
import android.widget.Toast;

public class Util {
    
    public static File getCacheFile(Context ctx) {
        return new File(ctx.getFilesDir(), "cached_results");
    }

    public static void saveCacheResults(Context ctx, JSONObject results) {
        try {
            FileWriter writer = new FileWriter(getCacheFile(ctx));
            writer.write(results.toString());
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static JSONObject readCacheResults(Context ctx) {
        StringBuilder result = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(getCacheFile(ctx)));
            String s; 
            while((s = reader.readLine()) != null) { 
                result.append(s); 
            } 
            reader.close();
            return new JSONObject(result.toString());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean isNetworkAvailable(Context ctx) {
        ConnectivityManager connectivityManager 
              = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public static void showToast(final Context ctx, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
            }
        });        
    }
    
    public static void updateWidgetProgress(final Context ctx, final int progress) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(ctx);
                ComponentName comp = new ComponentName(ctx, WidgetProvider.class);
                for (int widgetId : appWidgetManager.getAppWidgetIds(comp)) {
                    RemoteViews remoteViews = new RemoteViews(ctx.getPackageName(), R.layout.widget_layout);
                    remoteViews.setProgressBar(R.id.progress, 100, progress, false);
                    // Set refresh intent
                    Intent intent = new Intent(ctx, RandomWallpaper.class);
                    PendingIntent pendingIntent = PendingIntent.getService(ctx,
                            0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                    remoteViews.setOnClickPendingIntent(R.id.refresh, pendingIntent);
                    // Set configuration intent
                    intent = new Intent(ctx, Configuration.class);
                    intent.putExtra(WidgetProvider.FORCED_EXTRA, true);
                    pendingIntent = PendingIntent.getActivity(ctx,
                            0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                    remoteViews.setOnClickPendingIntent(R.id.config, pendingIntent);
                    appWidgetManager.updateAppWidget(widgetId, remoteViews);
                }
            }
            
        }).start();
        
    }
}
