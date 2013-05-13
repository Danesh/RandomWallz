package com.danesh.randomwallz;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.text.TextUtils;
import android.util.Base64;

public final class WallBase {

    public static enum OrderBy {
        DATE("date"),
        RELEVANCE("relevance"),
        VIEWS("views"),
        FAVORITES("favs"),
        RANDOM("random");
        private final String value;
        private OrderBy (String v) {
            value = v;
        }
        @Override
        public String toString() {
            return value;
        }
    }

    public static enum SortOrder {
        ASC("asc"),
        DESC("desc");
        private final String value;
        private SortOrder (String v) {
            value = v;
        }
        @Override
        public String toString() {
            return value;
        }
    }

    public static enum ResFilter {
        GREATER_OR_EQUAL("gteq"),
        EQUAL("eqeq");
        private final String value;
        private ResFilter (String v) {
            value = v;
        }
        @Override
        public String toString() {
            return value;
        }
    }

    public static enum WallTypes {
        GENERAL("2"),
        ANIME("1"),
        HIGH_QUALITY("3"),
        ALL("123");
        private final String value;
        private WallTypes (String v) {
            value = v;
        }
        @Override
        public String toString() {
            return value;
        }
    }

    private static final List<Integer> NUM_RESULTS_SUPPORTED = Arrays.asList(20,32,40,60);
    private static final String SITE_BASE = "http://wallbase.cc";
    private static final String SITE_SEARCH = SITE_BASE + "/search";
    private static final String SITE_WALLPAPER = SITE_BASE + "/wallpaper/";

    private URL SITE_URL;
    private String mSearchTerm;
    private String mOrderBy;
    private String mSortOrder;
    private String mNumberOfResults;
    private String mResolution;
    private String mResolutionFilter;
    private String mWallpaperTypes;
    private String mSafeMode;

    public WallBase() {
        try {
            SITE_URL = new URL(SITE_SEARCH);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        // Initialise defaults
        mSortOrder = SortOrder.ASC.value;
        mOrderBy = OrderBy.RANDOM.value;
        mSearchTerm = "android";
        setResolution(0,0);
        mResolutionFilter = ResFilter.EQUAL.value;
        mWallpaperTypes = WallTypes.ALL.value;
        setSafeMode(true);
        setNumberOfResults(20);
    }

    /**
     * Sets the search term to use.
     * @param term must be of length > 0
     */
    public void setSearchTerm(String term) {
        if (!TextUtils.isEmpty(term)) {
            mSearchTerm = term;
        }
    }

    /**
     * Sets which filter to sort by.
     * @param order
     */
    public void setOrderBy(OrderBy order) {
        mOrderBy = order.value;
    }

    /**
     * Sets the sort order
     * Note : Ignored if OrderBy is {@link OrderBy}
     * @param sort
     */
    public void setSortOrder(SortOrder sort) {
        mSortOrder = sort.value;
    }

    /**
     * Sets number of results to store
     * @param number must be one of (20,32,40,60)
     * @throws IllegalArgumentException
     */
    public void setNumberOfResults(int number) throws IllegalArgumentException {
        if (NUM_RESULTS_SUPPORTED.contains(number)) {
            mNumberOfResults = String.valueOf(number);
        } else {
            throw new IllegalArgumentException("Not a valid NUM_RESULTS_SUPPORTED element");
        }
    }

    /**
     * Sets the resolution of images.
     * A width or height of <= 0 signifies to return any resolution image
     * @param width
     * @param height
     */
    public void setResolution(int width, int height) {
        if (width > 0 && height > 0) {
            mResolution = width + "x" + height;
        } else {
            mResolution = "0";
        }
    }

    /**
     * Sets the filter for resolution filtering
     * @param filter
     */
    public void setResolutionFilter(ResFilter filter) {
        mResolutionFilter = filter.value;
    }

    /**
     * Set the wallpaper types to search
     * @param types cannot be null
     * @throws IllegalArgumentException
     */
    public void setWallpaperType(WallTypes...types) throws IllegalArgumentException {
        if (types != null) {
            List<WallTypes> inTypes = Arrays.asList(types);
            if (inTypes.contains(WallTypes.ALL)) {
                mWallpaperTypes = WallTypes.ALL.value;
            } else {
                mWallpaperTypes = TextUtils.join("", inTypes);
            }       
        } else {
            throw new IllegalArgumentException("Type argument cannot be null");
        }
    }

    /**
     * Enables/Disables safe mode
     * Enabled -> SafeForWork
     * Disabled -> NotSafeForWork + Sketchy
     * @param enable
     */
    public void setSafeMode(boolean enable) {
        mSafeMode = enable ? "100" : "010";
    }

    private static final String getQuery(List<NameValuePair> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (NameValuePair pair : params) {
            if (first) {
                first = false;
            } else {
                result.append("&");
            }
            result.append(URLEncoder.encode(pair.getName(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(pair.getValue(), "UTF-8"));
        }
        return result.toString();
    }

    private class WallpaperParser implements Runnable {

        private JSONObject mSelectedImage;

        WallpaperParser(JSONObject selectedImage) {
            mSelectedImage = selectedImage;
        }

        @Override
        public void run() {
            try {
                String id = mSelectedImage.getString("id");
                URLConnection connection = new URL(SITE_WALLPAPER + id).openConnection();
                StringBuilder response = new StringBuilder();
                InputStreamReader inputStream = new InputStreamReader(connection.getInputStream());
                BufferedReader reader = new BufferedReader(inputStream);
                String line;
                while((line = reader.readLine()) != null) {
                    response.append(line);
                }
                inputStream.close();
                reader.close();
                line = response.toString();
                int start = line.indexOf("src=\"\'+B('");
                if (start != -1) {
                    line = line.substring(start + 10);
                    int stop = line.indexOf("'");
                    if (stop != -1) {
                        line = line.substring(0, stop);
                        mSelectedImage.put("url", new String(Base64.decode(line, Base64.DEFAULT)));                            
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public JSONArray query() {
        try {
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
            nameValuePairs.add(new BasicNameValuePair("query", mSearchTerm));
            nameValuePairs.add(new BasicNameValuePair("orderby", mOrderBy));
            nameValuePairs.add(new BasicNameValuePair("orderby_opt", mSortOrder));
            nameValuePairs.add(new BasicNameValuePair("thpp", mNumberOfResults));
            nameValuePairs.add(new BasicNameValuePair("res", mResolution));
            nameValuePairs.add(new BasicNameValuePair("res_opt", mResolutionFilter));
            nameValuePairs.add(new BasicNameValuePair("board", mWallpaperTypes));
            //nameValuePairs.add(new BasicNameValuePair("aspect", "1.125"));
            nameValuePairs.add(new BasicNameValuePair("nsfw", mSafeMode));

            String param = getQuery(nameValuePairs);
            HttpURLConnection conn = (HttpURLConnection) SITE_URL.openConnection();

            conn.setDoOutput(true);
            conn.setRequestMethod("POST");

            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            conn.setFixedLengthStreamingMode(param.getBytes().length);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            PrintWriter out = new PrintWriter(conn.getOutputStream());
            out.print(param);
            out.close();

            StringBuilder response = new StringBuilder();
            Scanner inStream = new Scanner(conn.getInputStream());

            while (inStream.hasNextLine()) {
                response.append(inStream.nextLine());
            }

            conn.disconnect();
            inStream.close();

            JSONArray jsonResponse = new JSONArray(response.toString());
            if (jsonResponse.isNull(0)) {
                return null;
            }

            ExecutorService executor = Executors.newFixedThreadPool(10);
            for (int i = 0; i < jsonResponse.length(); i++) {
                executor.execute(new WallpaperParser(jsonResponse.getJSONObject(i)));
            }

            executor.shutdown();

            try {
                executor.awaitTermination(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return null;
            }
            return jsonResponse;
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
