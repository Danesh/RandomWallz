package com.danesh.randomwallz;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TimerUpdate extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        setTimer(context);
    }

    static void setTimer(Context ctx) {
        String storedTimer = new PreferenceHelper(ctx).getTimerInterval();
        try {
            int time = Integer.parseInt(storedTimer) * 1000;
            if (time != 0) {
                PendingIntent pIntent = getPendingIntent(ctx);
                AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
                alarmManager.cancel(pIntent);
                alarmManager.setRepeating(AlarmManager.RTC, System.currentTimeMillis() + time, time, pIntent);
            } else {
                cancelAllAlarms(ctx);
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    private static PendingIntent getPendingIntent(Context ctx) {
        Intent intent = new Intent(ctx, RandomWallpaper.class);
        return PendingIntent.getService(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void cancelAllAlarms(Context ctx) {
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(getPendingIntent(ctx));
    }
}
