package com.danesh.randomwallz;

import android.app.Service;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import com.danesh.randomwallz.WallBase.ResFilter;
import org.apache.http.ParseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RandomWallpaper extends Service {

    private static final String TAG = "RandomWallpaper";
    private PreferenceHelper mPrefHelper;
    private WallpaperManager mWallpaperManager;
    private DiskLruCache mDiskLruCache;

    /**
     * Holds information regarding current image
     */
    private ImageInfo mImageInfo;

    /**
     * Whether we were invoked via refresh button or a timer event.
     */
    private boolean mForcedRefresh;

    private ThreadPoolExecutor mExecutor;

    @Override
    public void onCreate() {
        super.onCreate();

        //Initialize instances
        mImageInfo = new ImageInfo();
        mExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        mPrefHelper = new PreferenceHelper(this);
        mWallpaperManager = WallpaperManager.getInstance(this);

        try {
            // Enable disk cache
            File cacheDir = new File(getCacheDir(), "http");
            mDiskLruCache = DiskLruCache.open(cacheDir, 0, 1, 10l * 1024l * 1024l);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mExecutor.getActiveCount() != 0) {
            Log.d(TAG, "Cancelling request");
            mExecutor.shutdownNow();
            try {
                mExecutor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                if (mForcedRefresh) {
                    TimerUpdate.setTimer(this);
                }
                stopSelf();
            }
        } else {
            Log.d(TAG, "Processing request");
            mForcedRefresh = intent.hasExtra(WidgetProvider.FORCED_REFRESH);
            if (mForcedRefresh) {
                TimerUpdate.cancelAllAlarms(this);
            }
            mExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    processRequest();
                    stopSelf();
                }
            });
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Shutting down thread");
        if (mDiskLruCache != null && !mDiskLruCache.isClosed()) {
            try {
                mDiskLruCache.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Given a URL, sets the wallpaper to the image it points to.
     *
     * @param url - url to wallpaper
     * @throws IOException
     */
    private void setUrlWallpaper(URL url) throws IOException {
        Bitmap origBitmap = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        File cacheResizedBitmap = new File(getExternalCacheDir(), "wallpaper");
        FileOutputStream cacheBitmapOutputStream = new FileOutputStream(cacheResizedBitmap);
        FileInputStream cacheBitmapInputStream = new FileInputStream(cacheResizedBitmap);
        try {
            //Reduce bitmap dimensions closest to preferred wallpaper dimensions
            options.inSampleSize = calculateInSampleSize();

            //Check if image is found in cache
            if (mDiskLruCache.get(mImageInfo.id) == null) {
                final DiskLruCache.Editor editor = mDiskLruCache.edit(mImageInfo.id);
                OutputStream out = editor.newOutputStream(0);
                Log.d(TAG, "Fetching image : " + url.toString());
                if (!Util.downloadImage(this, url, out, 20, 60)) {
                    if (!Thread.interrupted()) {
                        mPrefHelper.incFailedAttempts();
                        Util.showToast(this, getString(R.string.unable_retrieve_wallpaper));
                    }
                    out.close();
                    editor.abort();
                    return;
                }
                out.close();
                editor.commit();
            } else {
                Log.d(TAG, "Image found in cache");
            }

            //Save resized version of image to temporary cache
            Log.d(TAG, "Creating resized image with sample size : " + options.inSampleSize);
            InputStream fullSizeImageStream = mDiskLruCache.get(mImageInfo.id).getInputStream(0);
            origBitmap = BitmapFactory.decodeStream(fullSizeImageStream, null, options);
            origBitmap.compress(Bitmap.CompressFormat.JPEG, 100, cacheBitmapOutputStream);
            fullSizeImageStream.close();

            Util.setWidgetProgress(this, 85);

            if (origBitmap == null) {
                Util.showToast(this, getString(R.string.unable_retrieve_wallpaper));
            } else {

                Log.d(TAG, "Setting wallpaper");
                mWallpaperManager.setStream(cacheBitmapInputStream);

                final Editor edit = mPrefHelper.getEditor();

                // Save wallpaper id. Used for filename when saving wallpaper
                edit.putString(PreferenceHelper.LAST_ID, mImageInfo.id);

                // Save wallpaper url. Used for debugging purposes
                edit.putString(Configuration.LAST_URL, url.toString());

                // Sets the time of when we changed the wallpaper
                edit.putLong(PreferenceHelper.WALLPAPER_CHANGED_TIME, System.currentTimeMillis());

                edit.apply();

                Util.setWidgetProgress(this, 95);

                //Differentiate between wallpapers set by our app and those set
                //by 3rd party apps.
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        edit.putBoolean(PreferenceHelper.WALLPAPER_CHANGED, true).apply();
                    }
                }, 1000);
            }
        } finally {
            if (origBitmap != null) {
                origBitmap.recycle();
            }
            if (cacheBitmapInputStream != null) {
                cacheBitmapInputStream.close();
            }
            if (cacheBitmapOutputStream != null) {
                cacheBitmapOutputStream.close();
            }
            if (cacheResizedBitmap.exists()) {
                cacheResizedBitmap.delete();
            }
        }
    }

    /**
     * Calculates inSampleSize value based on bitmap height/width and required
     * width/height of wallpaper manager.
     *
     * @return an integer for the inSampleSize property of BitmapOptions
     */
    int calculateInSampleSize() {
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

    /**
     * Filters query results into properties we are interested in
     * This aids in a smaller on-disk file and in memory json object
     *
     * @param array
     * @return
     */
    private static JSONArray filterResults(JSONArray array) {
        JSONArray result = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject selectedObj = array.getJSONObject(i);
                JSONObject newObj = new JSONObject();
                newObj.put("id", selectedObj.getInt("id"));
                newObj.put("url", selectedObj.getString("url"));
                JSONObject selectedAttrs = selectedObj.getJSONObject("attrs");
                JSONObject newAttrs = new JSONObject();
                newAttrs.put("wall_h", selectedAttrs.getInt("wall_h"));
                newAttrs.put("wall_w", selectedAttrs.getInt("wall_w"));
                newObj.put("attrs", newAttrs);
                result.put(newObj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    protected void processRequest() {
        if (Util.isNetworkAvailable(this)) {
            JSONObject storedCache = null;
            JSONArray jsonResponse = null;
            int index = 0;
            Util.setWidgetProgress(this, 5);
            try {
                //Check if cached results exist and number of failed attempts at retrieving
                //a result has been less than 2
                if (Util.getCacheFile(this).exists() && mPrefHelper.getFailedAttempts() < 2) {
                    storedCache = Util.readCacheResults(this);
                    if (storedCache.has("index") && storedCache.has("results")) {
                        index = storedCache.getInt("index");
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
                    Log.d(TAG, "Fetching new wallpapers : " + wBase.getQueryString());
                    jsonResponse = wBase.query();
                    if (jsonResponse != null) {
                        storedCache = new JSONObject();
                        storedCache.put("index", 0);
                        storedCache.put("results", filterResults(jsonResponse));
                        mPrefHelper.resetFailedAttempts();
                        index = 0;
                    }
                } else {
                    Log.d(TAG, "Using cache");
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
                    } catch (IOException e) {
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
                } else {
                    Util.showToast(this, getString(R.string.unable_retrieve_wallpaper));

                }
                Util.setWidgetProgress(this, 100);
            }
        } else {
            Log.d(TAG, "No network connection found");
            Util.showToast(this, getString(R.string.unable_retrieve_wallpaper));
        }
        // Reset the timer if user forced a refresh
        if (mForcedRefresh) {
            TimerUpdate.setTimer(this);
        }
    }

    private final static class ImageInfo {
        String id;
        int width;
        int height;
    }
}
