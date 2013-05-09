package com.danesh.randomwallz;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Random;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;

public class Configuration extends Activity {

    private final static String FOLDER_NAME = "RandomWallz";
    private final static String FILE_BASE_NAME = "walls-%s.jpeg";
    public static final String LAST_URL = "last_url";

    private static final int SAVE_WALLPAPER = 0;
    private static final int SAVE_WALLPAPER_SUCCESS_TOAST = 1;
    private static final int SAVE_WALLPAPER_FAILURE_TOAST = 2;

    private PreferenceHelper mPrefHelper;
    private SaveWallpaperHandler mSaveWallpaperHandler;
    private EditText mSearchTerm;
    private ToggleButton mSafeMode;
    private Spinner mTimerInterval;
    private String[] mTimerValues;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_layout);
        setTitle(R.string.configuration_dialog_title);
        mPrefHelper = new PreferenceHelper(this);
        mSaveWallpaperHandler = new SaveWallpaperHandler(this);

        mSearchTerm = (EditText) findViewById(R.id.search_term);
        mSafeMode = (ToggleButton) findViewById(R.id.safe_mode);
        mTimerInterval = (Spinner) findViewById(R.id.timer_interval);

        mSearchTerm.append(mPrefHelper.getSearchTerm());
        mSafeMode.setChecked(mPrefHelper.getSafeMode());

        mTimerValues = getResources().getStringArray(R.array.timer_values);
        String storedInterval = mPrefHelper.getTimerInterval();
        int indexOfStoredValue = Arrays.asList(mTimerValues).indexOf(storedInterval);
        mTimerInterval.setSelection(indexOfStoredValue);
    }

    private boolean shouldClearCache() {
        if ((!mPrefHelper.getSearchTerm().equals(mSearchTerm.getText().toString()))
                || (mPrefHelper.getSafeMode() != mSafeMode.isChecked())) {
            return true;
        }
        return false;
    }

    
    @Override
    protected void onStop() {
        super.onStop();
        mSaveWallpaperHandler.removeMessages(SAVE_WALLPAPER);
    }

    /**
     * Dialog cancel button
     * Just finish the activity
     * @param v
     */
    public void cancel(View v) {
        finish();
    }

    /**
     * Dialog save button
     * Clears cache if search term or safe mode was changed
     * @param v
     */
    public void save(View v) {
        if (mSearchTerm.getText().toString().isEmpty()) {
            Toast.makeText(this, R.string.search_empty_error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (shouldClearCache()) {
            Util.getCacheFile(this).delete();
        }
        Editor editor = mPrefHelper.getEditor();
        editor.putString(PreferenceHelper.SEARCH_TERM, mSearchTerm.getText().toString());
        editor.putBoolean(PreferenceHelper.SAFE_MODE, mSafeMode.isChecked());
        editor.putString(PreferenceHelper.TIMER_INTERVAL, mTimerValues[mTimerInterval.getSelectedItemPosition()]);
        editor.apply();
        cancel(null);
    }

    private static class SaveWallpaperHandler extends Handler {

        private final WeakReference<Context> mContext;

        SaveWallpaperHandler(Context context) {
            mContext = new WeakReference<Context>(context);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch(msg.what) {
            case SAVE_WALLPAPER:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        boolean wasSuccessful = false;
                        if (mContext.get() != null && Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                            String folder = Environment.getExternalStorageDirectory() + "/" + FOLDER_NAME;
                            File rootFolder = new File(folder);
                            if (rootFolder.isDirectory() || rootFolder.mkdir()) {
                                BitmapDrawable curWallpaper = (BitmapDrawable) WallpaperManager.getInstance(mContext.get()).getDrawable();
                                String lastId = new PreferenceHelper(mContext.get()).getLastWallpaperId();
                                String newFileName = String.format(FILE_BASE_NAME, lastId.isEmpty() ? new Random().nextInt(Integer.MAX_VALUE) : lastId);
                                while (lastId.isEmpty() && new File(newFileName).exists()) {
                                    newFileName = String.format(FILE_BASE_NAME, lastId.isEmpty() ? new Random().nextInt(Integer.MAX_VALUE) : lastId);
                                }
                                String fullPath = rootFolder + "/" + newFileName;
                                FileOutputStream saveImg = null;
                                try {
                                    saveImg = new FileOutputStream(new File(fullPath));
                                    curWallpaper.getBitmap().compress(Bitmap.CompressFormat.JPEG, 100, saveImg);
                                    wasSuccessful = true;
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        saveImg.close();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                        sendEmptyMessage(wasSuccessful ? SAVE_WALLPAPER_SUCCESS_TOAST : SAVE_WALLPAPER_FAILURE_TOAST);
                    }
                }).start();
                break;
            case SAVE_WALLPAPER_SUCCESS_TOAST:
                if (mContext.get() != null) {
                    Toast.makeText(mContext.get(), R.string.image_saved_toast, Toast.LENGTH_SHORT).show();
                }
                break;
            case SAVE_WALLPAPER_FAILURE_TOAST:
                if (mContext.get() != null) {
                    Toast.makeText(mContext.get(), R.string.image_not_saved_toast, Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    /**
     * Dialog save wallpaper button
     * @param v
     */
    public void saveWallpaper(View v) {
        if (!mSaveWallpaperHandler.hasMessages(SAVE_WALLPAPER)) {
            mSaveWallpaperHandler.sendEmptyMessage(SAVE_WALLPAPER);
        }
    }
}
