package cz.martykan.forecastie.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.Map;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.utils.Response;

public abstract class AbstractRepository {
    protected Resources resources;
    protected SharedPreferences sharedPreferences;

    AbstractRepository(Context context) {
        this.resources = context.getResources();
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @SuppressWarnings("WeakerAccess")
    protected Response downloadJson(URL url) {
        Response response = new Response();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            // TODO: This is called way too many times when starting the main activity
            Log.i("URL", url.toString());

            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream urlConnectionStream = urlConnection.getInputStream();
                InputStream in = new BufferedInputStream(urlConnectionStream);

                byte[] buffer = new byte[1024];
                int length;

                while ((length = in.read(buffer)) != -1) {
                    out.write(buffer, 0, length);
                }

                // Background work finished successfully
                Log.v("URL", "Downloaded successfully, url: " + url.toString());
                response.setStatus(Response.Status.SUCCESS);
                response.setDataString(out.toString("UTF-8"));
                urlConnectionStream.close();
                in.close();
            } else if (urlConnection.getResponseCode() == 429) {
                // Too many requests
                Log.w("URL", "Too many requests, url: " + url.toString());
                response.setStatus(Response.Status.TOO_MANY_REQUESTS);
            } else {
                // Bad response from server
                Log.w("URL", String.format("Bad response: %d, url: %s", urlConnection.getResponseCode(), url.toString()));
                response.setStatus(Response.Status.BAD_RESPONSE);
            }

            urlConnection.disconnect();
        } catch (IOException e) {
            // Exception while reading data from url connection
            Log.e("IOException Data", out.toString(), e);
            response.setStatus(Response.Status.IO_EXCEPTION);
        }

        return response;
    }

    @SuppressWarnings("WeakerAccess")
    protected URL provideUrl(String apiName, Map<String, String> params) {
        String apiKey = sharedPreferences.getString("apiKey", resources.getString(R.string.apiKey));

        StringBuilder urlBuilder = new StringBuilder(String.format("https://api.openweathermap.org/data/2.5/%s?appid=%s&lang=%s&mode=json", apiName, apiKey, getLanguage()));

        for (Map.Entry<String, String> entry : params.entrySet()) {
            try {
                urlBuilder
                        .append("&")
                        .append(entry.getKey())
                        .append("=")
                        .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                Log.e("URL Formation", "Error encoding URL", e);
            }
        }

        try {
            return new URL(urlBuilder.toString());
        } catch (MalformedURLException e) {
            e.printStackTrace();
            Log.e("URL Formation", "Error forming URL", e);
        }

        return null;
    }

    @SuppressWarnings("WeakerAccess")
    protected String getLanguage() {
        String language = Locale.getDefault().getLanguage();
        if (language.equals("cs")) {
            language = "cz";
        }
        return language;
    }
}
