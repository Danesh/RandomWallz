package com.danesh.randomwallz;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class TimerUpdate extends BroadcastReceiver {

    private static final String TAG = "TimerUpdate";

    @Override
    public void onReceive(Context context, Intent intent) {
        setTimer(context);
    }

    /**
     * Sets the timer according to timerinterval configured by user
     * @param ctx
     */
    static void setTimer(Context ctx) {
        String storedTimer = new PreferenceHelper(ctx).getTimerInterval();
        try {
            int time = Integer.parseInt(storedTimer) * 1000;
            cancelAllAlarms(ctx);
            if (time != 0) {
                PendingIntent pIntent = getPendingIntent(ctx);
                AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
                alarmManager.setRepeating(AlarmManager.RTC, System.currentTimeMillis() + time, time, pIntent);
                Log.d(TAG, "Setting alarm for every " + (time / 1000) + " seconds");
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the pending intent used to refresh wallpaper on intervals
     * @param ctx
     * @return
     */
    private static PendingIntent getPendingIntent(Context ctx) {
        Intent intent = new Intent(ctx, RandomWallpaper.class);
        return PendingIntent.getService(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Cancel all pending alarms
     * @param ctx
     */
    static void cancelAllAlarms(Context ctx) {
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(getPendingIntent(ctx));
        Log.d(TAG, "Cancelling all alarms");
    }
}
