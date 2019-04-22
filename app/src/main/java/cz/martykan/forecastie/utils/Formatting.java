package cz.martykan.forecastie.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.util.Log;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.models.Weather;

public class Formatting {

    private static final Map<String, Integer> SPEED_UNITS;
    private static final Map<String, Integer> PRESSURE_UNITS;

    static {
        HashMap<String, Integer> speedUnits = new HashMap<>(4);
        speedUnits.put("m/s", R.string.speed_unit_mps);
        speedUnits.put("kph", R.string.speed_unit_kph);
        speedUnits.put("mph", R.string.speed_unit_mph);
        speedUnits.put("kn", R.string.speed_unit_kn);

        SPEED_UNITS = Collections.unmodifiableMap(speedUnits);

        HashMap<String, Integer> pressUnits = new HashMap<>(3);
        pressUnits.put("hPa", R.string.pressure_unit_hpa);
        pressUnits.put("kPa", R.string.pressure_unit_kpa);
        pressUnits.put("mm Hg", R.string.pressure_unit_mmhg);

        PRESSURE_UNITS = Collections.unmodifiableMap(pressUnits);
    }

    // TODO: This is only used in one place
    public static String getWeatherIcon(int actualId, int hourOfDay, Resources resources) {
        int id = actualId / 100;
        String icon = "";
        if (actualId == 800) {
            if (hourOfDay >= 7 && hourOfDay < 20) {
                icon = resources.getString(R.string.weather_sunny);
            } else {
                icon = resources.getString(R.string.weather_clear_night);
            }
        } else {
            switch (id) {
                case 2:
                    icon = resources.getString(R.string.weather_thunder);
                    break;
                case 3:
                    icon = resources.getString(R.string.weather_drizzle);
                    break;
                case 7:
                    icon = resources.getString(R.string.weather_foggy);
                    break;
                case 8:
                    icon = resources.getString(R.string.weather_cloudy);
                    break;
                case 6:
                    icon = resources.getString(R.string.weather_snowy);
                    break;
                case 5:
                    icon = resources.getString(R.string.weather_rainy);
                    break;
            }
        }
        return icon;
    }

    public static String capitalize(@NonNull String text) {
        if (text.length() <= 1) {
            return text.toUpperCase();
        } else {
            return text.substring(0, 1).toUpperCase() + text.substring(1);
        }
    }

    public static String localizeSpeedUnit(Preferences preferences, Resources resources) {
        String speedUnit = preferences.getSpeedUnit();
        if (SPEED_UNITS.containsKey(speedUnit)) {
            return resources.getString(SPEED_UNITS.get(speedUnit));
        } else {
            Log.w("Formatting", "Unknown speed unit \"" + speedUnit + "\"");
            return speedUnit;
        }
    }

    public static String localizePressureUnit(Preferences preferences, Resources resources) {
        String pressureUnit = preferences.getPressureUnit();
        if (PRESSURE_UNITS.containsKey(pressureUnit)) {
            return resources.getString(PRESSURE_UNITS.get(pressureUnit));
        } else {
            Log.w("Formatting", "Unknown pressure unit \"" + pressureUnit + "\"");
            return pressureUnit;
        }
    }

    public static String getWindDirectionString(Preferences preferences, Resources resources, Weather weather) {
        if (weather.getWindDirectionDegree() == null) {
            return "";
        }

        try {
            if (weather.getWind() != 0) {
                String pref = preferences.getWindDirectionFormat();
                if ("arrow".equals(pref)) {
                    return weather.getWindDirection(8).getArrow(resources);
                } else if ("abbr".equals(pref)) {
                    return weather.getWindDirection().getLocalizedString(resources);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    // TODO: This is only used in one place
    public static String formatTimeWithDayIfNotToday(long timeInMillis) {
        Calendar now = Calendar.getInstance();

        Date lastCheckedDate = new Date(timeInMillis);

        Calendar lastCheckedCal = Calendar.getInstance();
        lastCheckedCal.setTime(lastCheckedDate);

        if (now.get(Calendar.YEAR) == lastCheckedCal.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == lastCheckedCal.get(Calendar.DAY_OF_YEAR)) {
            return DateFormat.getTimeInstance(DateFormat.SHORT).format(lastCheckedDate);
        } else {
            return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(lastCheckedDate);
        }
    }
}
