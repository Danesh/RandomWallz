package com.danesh.randomwallz;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

class PreferenceHelper {

    static final String SEARCH_TERM = "pref_search_term";
    private static final String DEFAULT_SEARCH_TERM = "android";
    static final String SAFE_MODE = "pref_safe_search";
    private static final boolean DEFAULT_SAFE_MODE = true;
    static final String LAST_ID = "pref_last_id";
    static final String TIMER_INTERVAL = "pref_timer_interval";
    private static final String DEFAULT_TIMER_INTERVAL = "0";
    private static final String FAILED_ATTEMPTS = "failed_attempts";
    static final String WALLPAPER_CHANGED = "wallpaper_changed";
    
    private final SharedPreferences mSharedPreferences;

    PreferenceHelper(Context ctx) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    public Editor getEditor() {
        return mSharedPreferences.edit();
    }

    public boolean getSafeMode() {
        return mSharedPreferences.getBoolean(SAFE_MODE, DEFAULT_SAFE_MODE);
    }

    public String getSearchTerm() {
        return mSharedPreferences.getString(SEARCH_TERM, DEFAULT_SEARCH_TERM);
    }

    public String getLastWallpaperId() {
        return mSharedPreferences.getString(LAST_ID, "");
    }

    public String getTimerInterval() {
        return mSharedPreferences.getString(TIMER_INTERVAL, DEFAULT_TIMER_INTERVAL);
    }
    
    public int getFailedAttempts() {
        return mSharedPreferences.getInt(FAILED_ATTEMPTS, 0);
    }
    
    public void incFailedAttempts() {
        mSharedPreferences.edit().putInt(FAILED_ATTEMPTS, getFailedAttempts() + 1).apply();
    }
    
    public void resetFailedAttempts() {
        mSharedPreferences.edit().putInt(FAILED_ATTEMPTS, 0).apply();
    }

    public long getWallpaperChanged() {
        return mSharedPreferences.getLong(WALLPAPER_CHANGED, 0);
    }
}
