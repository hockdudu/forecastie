package cz.martykan.forecastie.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.Map;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.utils.Formatting;
import cz.martykan.forecastie.utils.Response;

public abstract class AbstractRepository {
    protected Context context;

    AbstractRepository(Context context) {
        this.context = context;
    }

    protected Response downloadJson(URL url) {
        Response response = new Response();
        StringBuilder responseString = new StringBuilder();

        try {
            Log.i("URL", url.toString());

            url.openStream();
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStreamReader inputStreamReader = new InputStreamReader(urlConnection.getInputStream());
                BufferedReader r = new BufferedReader(inputStreamReader);

                // TODO: Remove this one line here, and maybe REFACTOR THIS WHOLE FUNCTION
                int responseCode = urlConnection.getResponseCode();
                String line;
                while ((line = r.readLine()) != null) {
                    responseString.append(line).append("\n");
                }
                r.close();
                urlConnection.disconnect();

                // Background work finished successfully
                Log.i("Task", "done successfully");
                response.setStatus(Response.Status.SUCCESS);
                response.setDataString(responseString.toString());

            } else if (urlConnection.getResponseCode() == 429) {
                // Too many requests
                Log.i("Task", "too many requests");
                response.setStatus(Response.Status.TOO_MANY_REQUESTS);
            } else {
                // Bad response from server
                Log.i("Task", "bad response " + urlConnection.getResponseCode());
                response.setStatus(Response.Status.BAD_RESPONSE);
            }
        } catch (IOException e) {
            // Exception while reading data from url connection
            Log.e("IOException Data", responseString.toString());
            e.printStackTrace();
            response.setStatus(Response.Status.IO_EXCEPTION);
        }

        return response;
    }


    protected URL provideUrl(String apiName, Map<String, String> params) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String apiKey = sp.getString("apiKey", context.getResources().getString(R.string.apiKey));

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

    protected String getLanguage() {
        String language = Locale.getDefault().getLanguage();
        if (language.equals("cs")) {
            language = "cz";
        }
        return language;
    }
}
