package com.danesh.randomwallz;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;

public class WallpaperChanged extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Editor edit  = new PreferenceHelper(context).getEditor();
        edit.putBoolean(PreferenceHelper.WALLPAPER_CHANGED, false).apply();
    }

}
