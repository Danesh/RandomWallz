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
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;

import com.danesh.randomwallz.WallBase.ResFilter;
import com.danesh.randomwallz.WallBase.WallTypes;

public class RandomWallpaper extends IntentService {

    private static final String TEMP_FILE_NAME = "wallpaper";
    private PreferenceHelper mPrefHelper;
    private WallpaperManager mWallpaperManager;
    private int mWallpaperDesiredWidth;
    private int mWallpaperDesiredHeight;
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
            mWallpaperDesiredWidth = mWallpaperManager.getDesiredMinimumWidth();
            mWallpaperDesiredHeight = mWallpaperManager.getDesiredMinimumHeight();
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
        BufferedInputStream ins;
        Bitmap origBitmap = null, scaledBitmap = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        try {

            options.inPreferredConfig = Config.RGB_565;
            options.inSampleSize = calculateInSampleSize();
            options.inPurgeable = true;
            options.inTempStorage = new byte[3145728];

            // Calculate new height according to ratio
            int newImageHeight = (int) (((double) mImageInfo.height / mImageInfo.width) * mWallpaperDesiredWidth);

            // Get the bitmap from URL
            ins = new BufferedInputStream(url.openStream(), 3145728);

            Util.updateWidgetProgress(this, 40);
            origBitmap = BitmapFactory.decodeStream(ins, null ,options);

            if (origBitmap == null) {
                Util.showToast(this, "Unable to retrieve wallpaper");
                if (ins != null) {
                    ins.close();
                }
                return;
            }

            // Create temporary file
            FileOutputStream out = openFileOutput(TEMP_FILE_NAME, Context.MODE_PRIVATE);

            Util.updateWidgetProgress(this, 70);
            // Resize bitmap according to WallpaperManager desired dimensions
            scaledBitmap = Bitmap.createScaledBitmap(origBitmap, mWallpaperDesiredWidth, newImageHeight, true);

            // Save scaled bitmap to temporary file
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);

            Util.updateWidgetProgress(this, 85);

            out.close();

            // Read temporary file
            FileInputStream in = openFileInput(TEMP_FILE_NAME);

            Util.updateWidgetProgress(this, 92);
            // Set wallpaper to temporary file
            mWallpaperManager.setStream(in);

            in.close();

            // Delete temporary file
            new File(getFilesDir(), "wallpaper").delete();

            Editor edit = mPrefHelper.getEditor();

            // Save wallpaper id. Used for filename when saving wallpaper
            edit.putString(PreferenceHelper.LAST_ID, mImageInfo.id);

            // Save wallpaper url. Used for debugging purposes
            edit.putString(Configuration.LAST_URL, url.toString());

            edit.apply();
        } catch (IOException e) {
            // Error occurred while decoding bitmap
            e.printStackTrace();
            mPrefHelper.incFailedAttempts();
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
        } finally {
            if (origBitmap != null) origBitmap.recycle();
            if (scaledBitmap != null) scaledBitmap.recycle();
        }
    }

    /**
     * Calculates inSampleSize value based on bitmap height/width and required
     * width/height of wallpaper manager.
     * @return an integer for the inSampleSize property of BitmapOptions
     */
    public int calculateInSampleSize() {
        int inSampleSize = 1;
        if (mImageInfo.height > mWallpaperDesiredHeight || mImageInfo.width > mWallpaperDesiredWidth) {
            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) mImageInfo.height / (float) mWallpaperDesiredHeight);
            final int widthRatio = Math.round((float) mImageInfo.width / (float) mWallpaperDesiredWidth);

            // Choose the smallest ratio as inSampleSize value, this will guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }
        return inSampleSize;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (Util.isNetworkAvailable(this)) {
            JSONObject storedCache = null;
            JSONArray jsonResponse = null;
            int index = 0;
            try {

                // Check if cached urls exist
                if (Util.getCacheFile(this).exists() && mPrefHelper.getFailedAttempts() < 2) {
                    storedCache = Util.readCacheResults(this);
                    index = storedCache.getInt("index");
                    jsonResponse = storedCache.getJSONArray("results");
                    Util.updateWidgetProgress(this, 5);
                }

                // If no cache was found or if all entries are used, do a new query
                if (jsonResponse == null || index == jsonResponse.length()) {
                    WallBase wBase = new WallBase();
                    wBase.setSafeMode(mPrefHelper.getSafeMode());
                    wBase.setResolution(mWallpaperManager.getDesiredMinimumWidth(), mWallpaperManager.getDesiredMinimumHeight());
                    wBase.setResolutionFilter(ResFilter.GREATER_OR_EQUAL);
                    wBase.setWallpaperType(WallTypes.ANIME, WallTypes.GENERAL);
                    wBase.setSearchTerm(mPrefHelper.getSearchTerm());
                    jsonResponse = wBase.query();
                    if (jsonResponse != null) {
                        storedCache = new JSONObject();
                        storedCache.put("index", 0);
                        storedCache.put("results", jsonResponse);
                        mPrefHelper.resetFailedAttempts();
                        index = 0;
                    }
                    Util.updateWidgetProgress(this, 5);
                }

                if (jsonResponse != null) {
                    JSONObject selectedImage = jsonResponse.getJSONObject(index);
                    JSONObject imageAttrs = selectedImage.getJSONObject("attrs");

                    mImageInfo.id = selectedImage.getString("id");
                    mImageInfo.height = imageAttrs.getInt("wall_h");
                    mImageInfo.width = imageAttrs.getInt("wall_w");

                    Util.updateWidgetProgress(this, 15);

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
        HAS_JOBS = false;
        if (intent.hasExtra(WidgetProvider.FORCED_EXTRA)) {
            TimerUpdate.setTimer(this);
        }
    }

    private final static class ImageInfo {
        String id;
        int width;
        int height;
    }
}
