package cz.martykan.forecastie.utils;

import android.content.SharedPreferences;
import android.content.res.Resources;

import cz.martykan.forecastie.Constants;
import cz.martykan.forecastie.R;

public class Preferences {

    private SharedPreferences sp;
    private Resources res;

    private static Preferences INSTANCE;

    private Preferences(SharedPreferences sharedPreferences, Resources resources) {
        this.sp = sharedPreferences;
        this.res = resources;
    }

    public static Preferences getInstance(SharedPreferences sharedPreferences, Resources resources) {
        if (INSTANCE == null) {
            synchronized (Preferences.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Preferences(sharedPreferences, resources);
                }
            }
        }

        return INSTANCE;
    }

    //----------//
    // Settings //
    //----------//

    public String getTemperatureUnit() {
        return sp.getString("unit", "°C");
    }

    public boolean isTemperatureInteger() {
        return sp.getBoolean("temperatureInteger", false);
    }

    public String getLengthUnit() {
        return sp.getString("lengthUnit", "mm");
    }

    public String getSpeedUnit() {
        return sp.getString("speedUnit", "m/s");
    }

    public String getPressureUnit() {
        return sp.getString("pressureUnit", "hPa");
    }

    public String getDateFormat() {
        String defaultDateFormat = res.getStringArray(R.array.dateFormatsValues)[0];
        String dateFormat = sp.getString("dateFormat", defaultDateFormat);

        if ("custom".equals(dateFormat)) {
            dateFormat = sp.getString("dateFormatCustom", defaultDateFormat);
        }

        return dateFormat;
    }

    public String getTheme() {
        return sp.getString("theme", "fresh");
    }

    public boolean isWidgetTransparent() {
        return sp.getBoolean("transparentWidget", false);
    }

    public String getWindDirectionFormat() {
        return sp.getString("windDirectionFormat", "arrow");
    }

    public boolean displayDecimalZeroes() {
        return sp.getBoolean("displayDecimalZeroes", false);
    }

    public boolean areDaysDifferentiatedWithColor() {
        return sp.getBoolean("differentiateDaysByTint", false);
    }

    public int getBackgroundRefreshInterval() {
        String refreshInterval = sp.getString("refreshInterval", "1");

        if ("none".equals(refreshInterval)) {
            return 0;
        } else {
            return Integer.parseInt(refreshInterval);
        }
    }

    public String getApiKey() {
        return sp.getString("apiKey", res.getString(R.string.apiKey));
    }

    public void updateLocationInBackground() {

    }

    //------//
    // Misc //
    //------//

    public int getLastCityId() {
        return sp.getInt("cityId", Constants.DEFAULT_CITY_ID);
    }

    public void setLastCityId(int cityId) {
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt("cityId", cityId);
        editor.apply();
    }
}
