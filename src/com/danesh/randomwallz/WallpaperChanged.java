package com.danesh.randomwallz;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WallpaperChanged extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        PreferenceHelper prefHelper = new PreferenceHelper(context);
        long curTime = System.currentTimeMillis();
        if (curTime - prefHelper.getWallpaperChanged() > 10) {
            Util.getWallpaperFile(context).delete();
        }
    }

}
