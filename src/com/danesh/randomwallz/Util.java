package com.danesh.randomwallz;

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
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

class Util {

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
            while ((s = reader.readLine()) != null) {
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
        ConnectivityManager connectivityManager =
                (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
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

    public static void setWidgetProgress(final Context ctx, final int progress) {
        // Set refresh intent
        Intent intent = new Intent(ctx, RandomWallpaper.class);
        //Action is needed for extra's to be preserved
        intent.setAction("UPDATE_WIDGET");
        intent.putExtra(WidgetProvider.FORCED_REFRESH, true);
        PendingIntent pendingIntent = PendingIntent.getService(ctx,
                0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        RemoteViews remoteViews = new RemoteViews(ctx.getPackageName(), R.layout.widget_layout);
        remoteViews.setOnClickPendingIntent(R.id.refresh, pendingIntent);
        // Set configuration intent
        intent = new Intent(ctx, Configuration.class);
        pendingIntent = PendingIntent.getActivity(ctx,
                0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.config, pendingIntent);
        remoteViews.setProgressBar(R.id.progress, 100, progress, false);
        ComponentName comp = new ComponentName(ctx, WidgetProvider.class);
        AppWidgetManager.getInstance(ctx).updateAppWidget(comp, remoteViews);
    }

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted", "SameParameterValue"})
    public static boolean downloadImage(Context ctx, URL url, OutputStream out,
                                        int currentProgress, int allocatedProgress) {
        HttpURLConnection connection = null;
        InputStream in = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            int totalLength = connection.getContentLength();
            connection.connect();

            // input stream to read file - with 8k buffer
            in = new BufferedInputStream(connection.getInputStream(), 8192);

            byte buffer[] = new byte[1024];
            int bytesRead, currentLength = 0, barProgress, lastProgress = 0;
            while ((bytesRead = in.read(buffer)) != -1) {
                if (allocatedProgress != 0) {
                    currentLength += bytesRead;
                    barProgress = (int) (currentProgress + ((float) currentLength / totalLength) * allocatedProgress);
                    if (barProgress - lastProgress >= 10) {
                        Util.setWidgetProgress(ctx, barProgress);
                        lastProgress = barProgress;
                    }
                }
                // writing buffer to file
                out.write(buffer, 0, bytesRead);
            }

            // flushing output
            out.flush();

            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
        return false;
    }

    public static boolean copyFile(File src, File dst) {
        if (src.exists()) {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = new FileInputStream(src);
                out = new FileOutputStream(dst);

                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        return false;
    }
}
