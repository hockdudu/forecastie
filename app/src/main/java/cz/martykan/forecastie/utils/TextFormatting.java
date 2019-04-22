package cz.martykan.forecastie.utils;

import android.content.res.Resources;

import java.util.Calendar;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.models.Weather;

// TODO: Maybe merge with Formatting
public class TextFormatting {
    public static String getTemperature(Resources resources, Preferences preferences, Weather weather) {
        // TODO: Does it still show XX,0 <- ? The ZERO is what I mean
        if (preferences.isTemperatureInteger()) {
            return resources.getString(R.string.format_temperature, Math.round(UnitConverter.convertTemperature(weather.getTemperature(), preferences)), preferences.getTemperatureUnit());
        } else {
            return resources.getString(R.string.format_temperature, UnitConverter.convertTemperature(weather.getTemperature(), preferences), preferences.getTemperatureUnit());
        }

    }

    public static String getDescription(Resources resources, Preferences preferences, Weather weather) {
        String rainString = UnitConverter.getRainString(weather.getRain(), preferences);
        String capitalizedTodayDescription = Formatting.capitalize(weather.getDescription());
        return rainString.length() > 0 ? resources.getString(R.string.format_description_with_rain, capitalizedTodayDescription, rainString) : capitalizedTodayDescription;
    }

    public static String getWindSpeed(Resources resources, Preferences preferences, Weather weather) {
        double wind = UnitConverter.convertWind(weather.getWind(), preferences);
        if (preferences.getSpeedUnit().equals("bft")) {
            return resources.getString(R.string.format_wind_beaufort, UnitConverter.getBeaufortName((int) wind), Formatting.getWindDirectionString(preferences, resources, weather));
        } else {
            return resources.getString(R.string.format_wind, wind, Formatting.localizeSpeedUnit(preferences, resources), Formatting.getWindDirectionString(preferences, resources, weather));
        }
    }

    public static String getPressure(Resources resources, Preferences preferences, Weather weather) {
        return resources.getString(R.string.format_pressure, UnitConverter.convertPressure(weather.getPressure(), preferences), Formatting.localizePressureUnit(preferences, resources));
    }

    public static String getHumidity(Resources resources, Weather weather) {
        return resources.getString(R.string.format_humidity, weather.getHumidity());
    }

    public static String getSunrise(Resources resources, Weather weather) {
        return resources.getString(R.string.format_sunrise, weather.getSunrise());
    }

    public static String getSunset(Resources resources, Weather weather) {
        return resources.getString(R.string.format_sunset, weather.getSunset());
    }

    public static String getIcon(Resources resources, Weather weather) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(weather.getDate());
        return Formatting.getWeatherIcon(Integer.parseInt(weather.getId()), cal.get(Calendar.HOUR_OF_DAY), resources);
    }

    public static String getUvIndex(Resources resources, Weather weather) {
        return resources.getString(R.string.format_uv_index, UnitConverter.convertUvIndexToRiskLevel(weather.getUvIndex()));
    }

    public static String getLastUpdate(Resources resources, Weather weather, boolean forWidget) {
        return resources.getString(forWidget ? R.string.last_update_widget : R.string.last_update, Formatting.formatTimeWithDayIfNotToday(weather.getLastUpdated()));
    }
}
