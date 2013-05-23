package com.danesh.randomwallz;

import android.app.IntentService;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.danesh.randomwallz.WallBase.ResFilter;
import org.apache.http.ParseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;

public class RandomWallpaper extends IntentService {

    static boolean sUpdateProgress;
    private PreferenceHelper mPrefHelper;
    private WallpaperManager mWallpaperManager;
    private ImageInfo mImageInfo;
    private DiskLruCache mDiskLruCache;

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
            // Enable disk cache
            try {
                File cacheDir = new File(getCacheDir(), "http");
                mDiskLruCache = DiskLruCache.open(cacheDir, 0, 1, 10l * 1024l * 1024l);
            } catch (IOException e) {
                e.printStackTrace();
            }
            sUpdateProgress = intent.hasExtra(WidgetProvider.FORCED_REFRESH);
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

    @Override
    public void onDestroy() {
        if (mDiskLruCache != null && !mDiskLruCache.isClosed()) {
            try {
                mDiskLruCache.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        super.onDestroy();
    }

    /**
     * Given a URL, sets the wallpaper to the image it points to.
     * @param url - url to wallpaper
     * @throws IOException
     */
    private void setUrlWallpaper(URL url) throws IOException {
        Bitmap origBitmap = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        File cachedBitmap = new File(getExternalCacheDir(), "wallpaper");
        FileOutputStream cacheBitmapOutputStream = new FileOutputStream(cachedBitmap);
        FileInputStream cacheBitmapInputStream = new FileInputStream(cachedBitmap);
        try {

            options.inSampleSize = calculateInSampleSize();
            options.inPreferQualityOverSpeed = true;

            if (mDiskLruCache.get(mImageInfo.id) == null) {
                final DiskLruCache.Editor editor = mDiskLruCache.edit(mImageInfo.id);
                OutputStream out = editor.newOutputStream(0);
                if (!Util.downloadImage(this, url, out, 20, 60)) {
                    mPrefHelper.incFailedAttempts();
                    Util.showToast(this, getString(R.string.unable_set_wallpaper_toast));
                    out.close();
                    editor.abort();
                    return;
                }
                out.close();
                editor.commit();
            }

            InputStream fullSizeImageStream = mDiskLruCache.get(mImageInfo.id).getInputStream(0);
            origBitmap = BitmapFactory.decodeStream(fullSizeImageStream, null, options);
            origBitmap.compress(Bitmap.CompressFormat.JPEG, 100, cacheBitmapOutputStream);
            fullSizeImageStream.close();

            Util.setWidgetProgress(this, 85);

            if (origBitmap == null) {
                Util.showToast(this, getString(R.string.unable_retrieve_wallpaper));
            } else {
                mWallpaperManager.setStream(cacheBitmapInputStream);

                Editor edit = mPrefHelper.getEditor();

                // Save wallpaper id. Used for filename when saving wallpaper
                edit.putString(PreferenceHelper.LAST_ID, mImageInfo.id);

                // Save wallpaper url. Used for debugging purposes
                edit.putString(Configuration.LAST_URL, url.toString());

                // Sets the time of when we changed the wallpaper
                edit.putLong(PreferenceHelper.WALLPAPER_CHANGED, System.currentTimeMillis());

                edit.apply();

                Util.setWidgetProgress(this, 95);
            }
        } finally {
            if (origBitmap != null) {
                origBitmap.recycle();
            }
            // Helps to reclaim bitmap memory in preparation for next cycle.
            System.gc();
            if(cacheBitmapInputStream != null) {
                cacheBitmapInputStream.close();
            }
            if(cacheBitmapOutputStream != null) {
                cacheBitmapOutputStream.close();
            }
            if (cachedBitmap.exists()) {
                cachedBitmap.delete();
            }
        }
    }

    /**
     * Scales one side of a rectangle to fit aspect ratio.
     *
     * @param maxPrimary Maximum size of the primary dimension (i.e. width for
     *        max width), or zero to maintain aspect ratio with secondary
     *        dimension
     * @param maxSecondary Maximum size of the secondary dimension, or zero to
     *        maintain aspect ratio with primary dimension
     * @param actualPrimary Actual size of the primary dimension
     * @param actualSecondary Actual size of the secondary dimension
     */
    private static int getResizedDimension(int maxPrimary, int maxSecondary, int actualPrimary,
                                           int actualSecondary) {
        // If no dominant value at all, just return the actual.
        if (maxPrimary == 0 && maxSecondary == 0) {
            return actualPrimary;
        }

        // If primary is unspecified, scale primary to match secondary's scaling ratio.
        if (maxPrimary == 0) {
            double ratio = (double) maxSecondary / (double) actualSecondary;
            return (int) (actualPrimary * ratio);
        }

        if (maxSecondary == 0) {
            return maxPrimary;
        }

        double ratio = (double) actualSecondary / (double) actualPrimary;
        int resized = maxPrimary;
        if (resized * ratio > maxSecondary) {
            resized = (int) (maxSecondary / ratio);
        }
        return resized;
    }

    /**
     * Calculates inSampleSize value based on bitmap height/width and required
     * width/height of wallpaper manager.
     * @return an integer for the inSampleSize property of BitmapOptions
     */
    int calculateInSampleSize() {
        int desiredWidth = getResizedDimension(mWallpaperManager.getDesiredMinimumWidth(),
                mWallpaperManager.getDesiredMinimumHeight(), mImageInfo.width, mImageInfo.height);
        int desiredHeight = getResizedDimension(mWallpaperManager.getDesiredMinimumHeight(),
                mWallpaperManager.getDesiredMinimumWidth(), mImageInfo.height, mImageInfo.width);
        double wr = (double) mImageInfo.width / desiredWidth;
        double hr = (double) mImageInfo.height / desiredHeight;
        double ratio = Math.min(wr, hr);
        float n = 1.0f;
        while ((n * 2) <= ratio) {
            n *= 2;
        }
        return (int) n;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (Util.isNetworkAvailable(this)) {
            JSONObject storedCache = null;
            JSONArray jsonResponse = null;
            int index = 0;
            Util.setWidgetProgress(this, 5);
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
                }

                // If no cache was found or if all entries are used, do a new query
                if (jsonResponse == null || index == jsonResponse.length()) {
                    WallBase wBase = new WallBase();
                    wBase.setSafeMode(mPrefHelper.getSafeMode());
                    wBase.setResolution(mWallpaperManager.getDesiredMinimumWidth(),
                            mWallpaperManager.getDesiredMinimumHeight());
                    wBase.setResolutionFilter(ResFilter.GREATER_OR_EQUAL);
                    wBase.setSearchTerm(mPrefHelper.getSearchTerm());
                    jsonResponse = wBase.query();
                    if (jsonResponse != null) {
                        storedCache = new JSONObject();
                        storedCache.put("index", 0);
                        storedCache.put("results", jsonResponse);
                        mPrefHelper.resetFailedAttempts();
                        index = 0;
                    }
                }

                Util.setWidgetProgress(this, 15);

                if (jsonResponse != null) {
                    JSONObject selectedImage = jsonResponse.getJSONObject(index);
                    JSONObject imageAttrs = selectedImage.getJSONObject("attrs");

                    mImageInfo.id = selectedImage.getString("id");
                    mImageInfo.height = imageAttrs.getInt("wall_h");
                    mImageInfo.width = imageAttrs.getInt("wall_w");

                    Util.setWidgetProgress(this, 20);

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
                    Util.showToast(this, getString(R.string.unable_retrieve_wallpaper));

                }
                Util.setWidgetProgress(this, 100);
            }
        } else {
            Util.showToast(this, getString(R.string.unable_retrieve_wallpaper));
        }
        // Reset the timer if user forced a refresh
        if (intent.hasExtra(WidgetProvider.FORCED_REFRESH)) {
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
