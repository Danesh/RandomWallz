package com.danesh.randomwallz;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

import org.apache.http.ParseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.IntentService;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.danesh.randomwallz.WallBase.ResFilter;
import com.danesh.randomwallz.WallBase.WallTypes;

public class RandomWallpaper extends IntentService {

    private static final String TEMP_FILE_NAME = "wallpaper";
    private PreferenceHelper mPrefHelper;
    private WallpaperManager mWallpaperManager;
    private ImageInfo mImageInfo;

    /**
     * Prevent simultaneous requests
     */
    private static boolean HAS_JOBS = false;

    public RandomWallpaper() {
        super("RandomWallpaper");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!HAS_JOBS) {
            TimerUpdate.cancelAllAlarms(this);
            mPrefHelper = new PreferenceHelper(this);
            mWallpaperManager = WallpaperManager.getInstance(this);
            HAS_JOBS = true;
            mImageInfo = new ImageInfo();
            return super.onStartCommand(intent, flags, startId);
        } else {
            return 0;
        }
    }

    /**
     * Given a URL, sets the wallpaper to the image it points to.
     * @param url - url to wallpaper
     * @throws IOException
     */
    private void setUrlWallpaper(URL url) throws IOException {
        BufferedInputStream urlInputStream = null;
        FileInputStream tmpFileInputStream = null;
        FileOutputStream tmpFileOutputStream = null;
        Bitmap origBitmap = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        try {
            options.inSampleSize = calculateInSampleSize();
            options.inTempStorage = new byte[32 * 1024];

            // Get the bitmap from URL
            urlInputStream = new BufferedInputStream(url.openStream());

            Util.updateWidgetProgress(this, 40);

            origBitmap = BitmapFactory.decodeStream(urlInputStream, null ,options);

            Util.updateWidgetProgress(this, 60);

            if (origBitmap == null) {
                Util.showToast(this, "Unable to retrieve wallpaper");
            } else {
                // Create temporary file
                tmpFileOutputStream = openFileOutput(TEMP_FILE_NAME, Context.MODE_PRIVATE);

                Util.updateWidgetProgress(this, 70);

                // Save scaled bitmap to temporary file
                origBitmap.compress(Bitmap.CompressFormat.JPEG, 100, tmpFileOutputStream);

                Util.updateWidgetProgress(this, 85);

                // Read temporary file
                tmpFileInputStream = openFileInput(TEMP_FILE_NAME);

                Util.updateWidgetProgress(this, 90);

                // Set wallpaper to temporary file
                mWallpaperManager.setStream(tmpFileInputStream);

                // Delete temporary file
                new File(getFilesDir(), TEMP_FILE_NAME).delete();

                Editor edit = mPrefHelper.getEditor();

                // Save wallpaper id. Used for filename when saving wallpaper
                edit.putString(PreferenceHelper.LAST_ID, mImageInfo.id);

                // Save wallpaper url. Used for debugging purposes
                edit.putString(Configuration.LAST_URL, url.toString());

                edit.apply();
            }
        } catch (IOException e) {
            // Error occurred while decoding bitmap
            e.printStackTrace();
            mPrefHelper.incFailedAttempts();
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
        } finally {
            if (origBitmap != null) {
                origBitmap.recycle();
            }
            if (urlInputStream != null) {
                urlInputStream.close();
            }
            if (tmpFileOutputStream != null) {
                tmpFileOutputStream.close();
            }
            if (tmpFileInputStream != null) {
                tmpFileInputStream.close();
            }
        }
    }

    /**
     * Calculates inSampleSize value based on bitmap height/width and required
     * width/height of wallpaper manager.
     * @return an integer for the inSampleSize property of BitmapOptions
     */
    public int calculateInSampleSize() {
        int maxWidth = mWallpaperManager.getDesiredMinimumWidth();
        int maxHeight = mWallpaperManager.getDesiredMinimumHeight();
        // Give maxWidth and maxHeight some leeway
        maxWidth *= 1.25;
        maxHeight *= 1.25;
        int bmWidth = mImageInfo.width;
        int bmHeight = mImageInfo.height;
        int scale = 1;
        while (bmWidth > maxWidth || bmHeight > maxHeight) {
            scale <<= 1;
            bmWidth >>= 1;
            bmHeight >>= 1;
        }
        return scale;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (Util.isNetworkAvailable(this)) {
            JSONObject storedCache = null;
            JSONArray jsonResponse = null;
            int index = 0;
            Util.updateWidgetProgress(this, 5);
            try {
                // Check if cached urls exist
                if (Util.getCacheFile(this).exists() && mPrefHelper.getFailedAttempts() < 2) {
                    storedCache = Util.readCacheResults(this);
                    if (storedCache.has("index")) {
                        index = storedCache.getInt("index");
                    }
                    if (storedCache.has("results")) {
                        jsonResponse = storedCache.getJSONArray("results");
                    }
                    Util.updateWidgetProgress(this, 15);
                }

                // If no cache was found or if all entries are used, do a new query
                if (jsonResponse == null || index == jsonResponse.length()) {
                    WallBase wBase = new WallBase();
                    wBase.setSafeMode(mPrefHelper.getSafeMode());
                    wBase.setResolution(mWallpaperManager.getDesiredMinimumWidth(),
                            mWallpaperManager.getDesiredMinimumHeight());
                    wBase.setResolutionFilter(ResFilter.GREATER_OR_EQUAL);
                    wBase.setNumberOfResults(32);
                    wBase.setWallpaperType(WallTypes.GENERAL);
                    wBase.setSearchTerm(mPrefHelper.getSearchTerm());
                    jsonResponse = wBase.query();
                    if (jsonResponse != null) {
                        storedCache = new JSONObject();
                        storedCache.put("index", 0);
                        storedCache.put("results", jsonResponse);
                        mPrefHelper.resetFailedAttempts();
                        index = 0;
                    }
                    Util.updateWidgetProgress(this, 15);
                }

                if (jsonResponse != null) {
                    JSONObject selectedImage = jsonResponse.getJSONObject(index);
                    JSONObject imageAttrs = selectedImage.getJSONObject("attrs");

                    mImageInfo.id = selectedImage.getString("id");
                    mImageInfo.height = imageAttrs.getInt("wall_h");
                    mImageInfo.width = imageAttrs.getInt("wall_w");

                    Util.updateWidgetProgress(this, 20);

                    try {
                        setUrlWallpaper(new URL(selectedImage.getString("url")));
                    } catch (IOException e){
                        e.printStackTrace();
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            } finally {
                if (storedCache != null) {
                    try {
                        storedCache.put("index", index + 1);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    Util.saveCacheResults(this, storedCache);
                    TimerUpdate.setTimer(this);
                } else {
                    Util.showToast(this, "Unable to retrieve wallpaper");

                }
                Util.updateWidgetProgress(this, 100);
            }
        } else {
            Util.showToast(this, "Unable to retrieve wallpaper");
        }
        if (intent.hasExtra(WidgetProvider.FORCED_EXTRA)) {
            TimerUpdate.setTimer(this);
        }
        HAS_JOBS = false;
    }

    private final static class ImageInfo {
        String id;
        int width;
        int height;
    }
}
