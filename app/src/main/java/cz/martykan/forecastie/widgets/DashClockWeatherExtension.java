package cz.martykan.forecastie.widgets;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;

import org.json.JSONObject;

import java.text.DecimalFormat;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.activities.SplashActivity;
import cz.martykan.forecastie.utils.Formatting;
import cz.martykan.forecastie.utils.UnitConverter;

public class DashClockWeatherExtension extends DashClockExtension {
    private static final Uri URI_BASE = Uri.parse("content://cz.martykan.forecastie.authority");
    private static final String UPDATE_URI_PATH_SEGMENT = "dashclock/update";

    @Override
    protected void onInitialize(boolean isReconnect) {
        super.onInitialize(isReconnect);

        // Watch for weather updates
        removeAllWatchContentUris();
        addWatchContentUris(new String[]{getUpdateUri().toString()});
    }

    // TODO: Use common parser
    @Override
    protected void onUpdateData(int reason) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String result = sp.getString("lastToday", "{}");
        try {
            JSONObject reader = new JSONObject(result);

            // Temperature
            double temperature = UnitConverter.convertTemperature(reader.optJSONObject("main").getDouble("temp"), sp);
            if (sp.getBoolean("temperatureInteger", false)) {
                temperature = Math.round(temperature);
            }

            // Wind
            double wind;
            try {
                wind = Double.parseDouble(reader.optJSONObject("wind").getString("speed"));
            } catch (Exception e) {
                e.printStackTrace();
                wind = 0;
            }
            wind = UnitConverter.convertWind(wind, sp);

            // Pressure
            double pressure = UnitConverter.convertPressure((float) Double.parseDouble(reader.optJSONObject("main").getString("pressure")), sp);

            publishUpdate(new ExtensionData()
                    .visible(true)
                    .icon(R.drawable.ic_cloud_white_18dp)
                    .status(getString(R.string.dash_clock_status, new DecimalFormat("0.#").format(temperature), localize(sp, "unit", "°C")))
                    .expandedTitle(getString(R.string.dash_clock_expanded_title, new DecimalFormat("0.#").format(temperature), localize(sp, "unit", "°C"), reader.optJSONArray("weather").getJSONObject(0).getString("description")))
                    .expandedBody(getString(R.string.dash_clock_expanded_body, reader.getString("name"), reader.optJSONObject("sys").getString("country"),
                            new DecimalFormat("0.0").format(wind), localize(sp, "speedUnit", "m/s"),
                            new DecimalFormat("0.0").format(pressure), localize(sp, "pressureUnit", "hPa"),
                            reader.optJSONObject("main").getString("humidity")))
                    .clickIntent(new Intent(this, SplashActivity.class)));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String localize(SharedPreferences sp, String preferenceKey, String defaultValueKey) {
        return Formatting.localize(sp, this, preferenceKey, defaultValueKey);
    }

    public static void updateDashClock(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        contentResolver.notifyChange(getUpdateUri(), null);
    }

    private static Uri getUpdateUri() {
        return Uri.withAppendedPath(URI_BASE, UPDATE_URI_PATH_SEGMENT);
    }

}
